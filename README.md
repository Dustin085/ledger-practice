# ledger-practice

雙分錄記帳練習專案。細節設計動機另見程式碼註解；這份 README 目前先記錄壓測相關的操作方式與環境陷阱。

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

（port 不要用 8080，避免跟本機其他服務衝突。）

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
