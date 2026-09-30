# ledger-practice

雙分錄記帳練習專案，模擬「公司自己的內部帳本」串接「外部資金結算服務」的情境。目標是練習傳統
CRUD 之外的東西：最終一致性、Saga、Outbox/Inbox pattern、訊息佇列，以及圍繞這些概念的測試與壓測。
細節設計動機另見各檔案的程式碼註解；這份 README 記錄整體架構、怎麼跑起來、怎麼測試。

## 這個專案在練什麼

- **複式記帳網域建模**：一筆分錄（`JournalEntry`）底下多筆明細（`JournalEntryLine`），借方總額必須
  等於貸方總額，這個不變量在寫入當下由系統強制保證。
- **Saga + 補償**：內部帳本與外部資金服務無法用同一個資料庫交易涵蓋，所以拆成多步驟
  （送出 → 等待確認 → 確認或補償），任一步失敗就用沖銷分錄把內部帳本補回一致，不需要外部同意。
- **Outbox pattern**：業務資料寫入與「通知別人」這兩件事無法保證同時成功（dual write 問題），
  所以把事件先跟業務資料寫進同一個交易，再由背景排程可靠地送到 MQ。
- **Inbox pattern**：外部系統的 callback 可能重複投遞（at-least-once），靠
  `InboxEvent.externalEventId` 的 unique constraint 去重，同一個事件只會被處理一次。
- **RabbitMQ**：Outbox 側用 topic exchange 做 fan-out（同一事件同時給「送出轉帳」跟「稽核紀錄」兩個
  消費者）；失敗訊息有退避重試與死信佇列（DLX/DLQ）。這個專案的規模其實不需要 MQ 也能滿足
  Outbox/Inbox 的正確性保證，接上 MQ純粹是為了練習這個 pattern 本身——這個取捨在程式碼註解與
  對話記錄裡有更完整的討論。
- **Webhook + 簽章驗證**：外部金流系統的回覆用 HMAC-SHA256 簽章驗證來源，模擬綠界那類金流商的
  callback 機制。
- **對帳**：外部 callback 遺失時，排程主動查詢外部狀態補回結果；查詢結果不確定時絕不自動補償
  （錢可能已經真的轉出去了）。

## 技術棧

Spring Boot 4 / Java 21 / Maven，JPA 與 MyBatis 混用（簡單 CRUD 用 JPA，試算表這類報表查詢用
MyBatis 手寫 SQL），Thymeleaf 樣板，H2（開發/測試用記憶體資料庫），RabbitMQ，Actuator + Micrometer。

## 套件結構

| package | 內容 |
|---|---|
| `account` | 科目（`Account`），複式記帳的最基本維度 |
| `journal` | 分錄（`JournalEntry`/`JournalEntryLine`）與其查詢頁面 |
| `ledger` | 核心業務邏輯：`LedgerService`（建立分錄）、Saga 相關 service（送出/確認/補償/對帳） |
| `transfer` | 資金轉帳請求（`FundTransferRequest`），Saga 的持久化狀態機 |
| `payment` | 外部金流閘道的介面與 mock 實作 |
| `outbox` | Outbox/Inbox 事件、RabbitMQ topology 設定、`OutboxRelay`、`DeadLetterQueueService` |
| `webhook` | 外部系統 callback 入口與簽章驗證 |
| `report` | MyBatis 寫的試算表查詢 |
| `metrics` | 自訂 Actuator 指標（Outbox 積壓、轉帳狀態統計） |
| `admin` | DLQ 管理頁面（peek / 重新處理 / 丟棄） |
| `config` | 種子資料 |

## 快速開始

```bash
docker compose up -d                 # 啟動 RabbitMQ
./mvnw.cmd spring-boot:run
```

預設用 8080。開機後可以看：

- `http://localhost:8080/journal-entries`：分錄列表
- `http://localhost:8080/transfers/pending`：等待外部確認的轉帳，有按鈕可以模擬外部回覆
- `http://localhost:8080/report/trial-balance`：試算表
- `http://localhost:8080/admin/dead-letters`：DLQ 管理頁面
- `http://localhost:15672`（帳密 `guest`/`guest`）：RabbitMQ 管理介面

## 測試

```bash
./mvnw.cmd test
```

**不需要先啟動 RabbitMQ**：測試用 `test` profile（[`application-test.yml`](src/test/resources/application-test.yml)）
關掉 `OutboxRelay` 排程與 `@RabbitListener` 自動啟動，整合測試也不會真的連上 broker。

測試分幾層：
- **單元測試**（Mockito）：`ledger`、`outbox` package 底下的 service/relay。
- **Controller 測試**（`@WebMvcTest` + MockMvc）：`LedgerControllerTest`、
  [`SettlementWebhookControllerTest`](src/test/java/com/example/ledgerpractice/webhook/SettlementWebhookControllerTest.java)——
  後者驗證簽章正確/錯誤/被竄改、格式錯誤各種情境，不需要真的連 MQ。
- **整合測試**（`@SpringBootTest`）：`TransferSagaIntegrationTest`（走完整條 saga）、
  `TransferReconciliationIntegrationTest`（對帳）、`TransferResultConcurrencyTest`
  （併發送同一個 callback 兩次，斷言只處理一次、不會有其中一邊丟例外）。

**例外：`DeadLetterQueueServiceTest` 標了 `@Tag("requires-rabbitmq")`，預設不會跑**（`pom.xml` 的
`maven-surefire-plugin` 設了 `excludedGroups`）。這支測試會直接用 `basicGet`/`basicAck`/`basicNack`
操作真正的 DLQ，`mvn test` 對大多數人不該要求先開 Docker，所以排除在預設範圍外；要跑它，先
`docker compose up -d`，再：

```bash
./mvnw.cmd test -Dsurefire.excludedGroups=
```

**注意：這支測試會清空 `fund-transfer.requested.submission` / `.submission.dlq` 這兩個 queue**（每個
測試前後都會 purge，確保測試互相獨立）。如果當下正在手動測試 DLQ 管理頁面、佇列裡有想保留的訊息，
先不要跑這支測試，會被清空。

### 手動測試 Inbox webhook

`SettlementWebhookController`（`POST /webhooks/settlement`）扮演的是外部金流系統打進來的 callback
入口，`TransferSimulationController` 平常用 HTTP 模擬這個角色（畫面上「模擬成功/失敗」按鈕）。想直接
手動送一個真的簽章正確的請求驗證行為，簽章密鑰預設是 `application.yml` 裡的
`settlement.webhook.secret`（`dev-shared-secret`）。

**簽章要對「時間戳記 + body」一起算**（防重放：時間戳記本身也要被簽進去保護，不然可以把舊請求的
時間戳記直接換成現在的值繞過過期檢查），格式是 `<epoch 秒數>.<body>`，時間戳記另外用
`X-Signature-Timestamp` 標頭帶，容許誤差是 `settlement.webhook.timestamp-tolerance`（預設 5 分鐘）：

```bash
REF=MOCK-xxx   # 從 /transfers/pending 頁面複製一筆真實存在的 externalReferenceId
BODY="{\"externalReferenceId\":\"$REF\",\"externalEventId\":\"evt-1\",\"result\":\"CONFIRMED\"}"
TS=$(date +%s)
SIG=$(printf '%s.%s' "$TS" "$BODY" | openssl dgst -sha256 -hmac dev-shared-secret | awk '{print $NF}')
curl -X POST http://localhost:8080/webhooks/settlement \
  -H "Content-Type: application/json" \
  -H "X-Signature-Timestamp: $TS" -H "X-Signature: $SIG" -d "$BODY"
```

用同一個 `externalEventId` 再送一次（`TS`/`SIG` 都保持不變，在容許誤差內），可以驗證 Inbox 去重真的
擋住重複處理（第二次一樣回 200，但轉帳狀態不會被動第二次）；改用一個超過 5 分鐘前的 `TS` 重算簽章
再送，則會直接被拒絕成 401，驗證防重放生效。`externalReferenceId` 如果是隨機亂填、不存在的值，訊息會在
`TransferResultListener` 重試耗盡後進 `transfer.result.inbox.dlq`。

### DLQ 重新處理工具

`fund-transfer.requested.submission.dlq`、`transfer.result.inbox.dlq` 這兩個死信佇列，原本只能用
RabbitMQ 管理介面看，現在 `http://localhost:8080/admin/dead-letters` 提供三個操作，兩個 DLQ 共用同一套
邏輯（`DeadLetterQueueService`）：

- **筆數 + 預覽下一筆**：直接查佇列目前積了幾筆，並且不拿走地看一眼最前面那筆的內容跟 `x-death`
  標頭（原因、死幾次）。
- **重新處理**：把最前面那筆送回原本的 queue（從 `x-death` 自動判斷要送回哪裡，不用自己對照），
  重新走一次正常流程。
- **丟棄**：直接永久移除，不重送。

**重新處理不保證成功**：這個工具只是「再給訊息一次機會」，不會、也沒辦法修正當初讓它死掉的根本原因。
如果原因還在（例如 `externalReferenceId` 本來就是隨機亂填、資料庫裡根本不存在），重新處理只會讓它照
一樣的路徑再失敗一次，被丟回 DLQ——這是預期行為，不是 bug。只有在根本原因已經解決的情況下（例如外部
系統當時暫時斷線，現在已經恢復），重新處理才會真的成功。

## 壓測（Load Testing）

腳本放在 [`loadtest/`](loadtest/)：

- [`create-journal-entry.jmx`](loadtest/create-journal-entry.jmx)：打 `POST /journal-entries`，量「建立分錄」這條寫資料庫的路徑。
- [`call-webhook.jmx`](loadtest/call-webhook.jmx)：打 `POST /webhooks/settlement`，量外部金流 callback 進來這條路徑，每筆請求會用 Groovy 現場算 HMAC 簽章。

### 執行前準備

```bash
docker compose up -d   # 啟動 RabbitMQ
```

啟動 app 用 `loadtest` profile，關掉 `show-sql`、Thymeleaf 快取等開發用設定，量到的數字才不會被這些干擾：

```bash
./mvnw.cmd spring-boot:run "-Dspring-boot.run.arguments=--server.port=8111 --spring.profiles.active=loadtest"
```

### 已知環境陷阱：JMeter 的 Groovy 引擎跑不動本機的 Java

本機預設的 Java 版本太新，JMeter 5.6.3 內建的 Groovy／ASM 看不懂它編出來的位元碼格式，執行 `call-webhook.jmx` 時會直接噴：

```
Uncaught Exception BUG! ... Unsupported class file major version 70
```

**解法：另外裝一個 Java 21 只給 JMeter 用**（不影響這個專案本身——`pom.xml` 已經鎖定 `java.version=21`，跟系統預設的 `java` 是幾版無關）：

```bash
scoop install corretto21-jdk
```

啟動 JMeter 時把它排到 PATH 最前面：

```bash
PATH="$(scoop prefix corretto21-jdk)/bin:$PATH" jmeter -n -t loadtest/call-webhook.jmx -l results.jtl -Jthreads=20 -Jport=8111
```

**注意:`JAVA_HOME` 對這裡沒用。** `jmeter.bat` 找 `java.exe` 只靠 PATH 解析,不會去讀 `JAVA_HOME`,所以只設 `JAVA_HOME` 不會生效,一定要改 PATH。

### 兩支腳本共通的參數（都用 `-Jxxx=yyy` 傳）

| 參數 | 預設 | 意義 |
|---|---|---|
| `threads` | 依腳本而定 | 並發執行緒數 |
| `ramp` | 5 | 爬升秒數 |
| `port` / `host` | 8111 / localhost | 目標位址 |

`create-journal-entry.jmx` 另外用 `duration`（跑多少秒）、`payrollAccountId`/`bankAccountId`（科目 id）。
`call-webhook.jmx` 另外用 `loops`（每個執行緒跑幾次迭代，不是跑多少秒）。

### 怎麼看有沒有積壓（單看 JMeter 報表看不到)

建立分錄那支測完之後,請求端的回應時間再快,也看不出 `OutboxRelay` 有沒有消化得完。要另外查 Actuator：

```bash
curl http://localhost:8111/actuator/metrics/ledger.outbox.unpublished
curl http://localhost:8111/actuator/metrics/ledger.outbox.oldest.unpublished.age.seconds
```

### 判斷「瓶頸是伺服器還是壓測工具自己」的方法

如果**單筆回應時間很快、但整體吞吐量卻很低**,兩者方向矛盾,通常代表瓶頸出在 JMeter 自己身上（例如用了很慢的直譯式腳本引擎),而不是被測系統。這個專案第一次量 webhook 吞吐量時就踩過這個坑：改用 BeanShell 繞過上面的 Groovy 問題後,量到 430 req/s、但單筆回應時間中位數只要 1ms；換回修好的 Groovy 之後,同樣條件下吞吐量變成 2,658 req/s——證實了之前的數字是被 BeanShell 直譯器拖累的假象,不是伺服器真正的極限。

### 案例：`create-journal-entry.jmx` 量到的 2.7 秒尖峰,追到 HikariCP 連線池

用 `create-journal-entry.jmx`（50 threads、60 秒）量「建立分錄（含外部轉帳）」這條路徑時,量到少數請求飆到 2.7 秒,但大多數都在幾十毫秒內完成——這種「極端值很極端、但中位數正常」的形狀,不會是程式邏輯本身變慢,通常是某個共用資源被搶光,一部分請求在排隊。

**驗證方法**：寫一支小腳本,壓測的同時每 200ms 輪詢一次 Actuator 的 `hikaricp.connections.{active,idle,pending}`,把時間戳跟 JMeter 的 `results.jtl` 對齊。結果 `pending`（排隊等連線的數量）在整段測試期間都不是 0,一度衝到 21——代表 HikariCP 的連線池被打滿了。專案從來沒設定過 `spring.datasource.hikari.maximum-pool-size`,吃的是預設值 **10**。

**修正**：在 `loadtest` profile 把 `maximum-pool-size` 調到 **30**,同條件重測：

| | pool=10 | pool=30 |
|---|---|---|
| 60 秒總請求數 | 29,720 | **171,276**（5.8 倍） |
| 平均回應時間 | 96ms | **33.5ms** |
| 最慢回應 | 2927ms | **980ms** |
| HikariCP `pending` | 一路都有,最高 21 | 幾乎全程 0 |

拿掉連線池瓶頸後吞吐量不是線性提升、是跳了快 6 倍,證實原本大部分時間都花在「排隊等連線」，不是真的在做事。

**跟 HikariCP 官方建議的公式對一下**：HikariCP wiki 給的經驗公式是

```
pool size = (core_count × 2) + effective_spindle_count
```

這台機器是 14 核（不算 hyperthreading）,H2 是純記憶體資料庫、沒有真正的磁碟 I/O，`effective_spindle_count` 可以視為 0，算出來是 `14 × 2 + 0 = 28`。我們憑經驗調的 30 幾乎正好卡在公式算出來的數字附近——但這不是嚴謹驗證：我們只測了 10 跟 30 兩個點,沒有掃過 20、25、28、40 這些中間值,不能說 28~30 就是真正的最佳值,只能說「大幅調大」這個方向是對的、而且湊巧跟公式對得上。等專案换成真的有磁碟 I/O 的資料庫（例如 Postgres）時，`effective_spindle_count` 就不會是 0 了，這條公式要重新算。

**這支腳本也順便修了一個 bug**：PRG 上線後 `POST /journal-entries` 成功會回 302,但 sampler 沒開 `follow_redirects`,導致 assertion 找不到內容、100% 判定失敗——是這次診斷 pool size 問題時才發現壓測腳本早就跟著 PRG 一起壞掉了,已經修好。
