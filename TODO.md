# 這個專案要練什麼

## 為什麼有這個專案

目標是準備台灣公部門/金融/保險的後端 Java 職缺。手上已經有兩個專案：一個是 Spring Boot + JPA
（傳統 CRUD 練習），一個是 JSP-practice（刻意走傳統 Spring MVC + 手刻 MyBatis 路線，練的是這些
雇主現有系統常見的老派寫法）。這兩個專案技術選型不同，但**業務網域都是「書籍申請/採購」**——
作品集攤開來看，講的是同一個故事講兩遍，只是換了框架，不是真的寬度不夠。

這個專案的目的是換一個業務網域（複式記帳），順便練一個前兩個專案都沒碰過的技術組合
（Spring Boot + JPA + MyBatis 混用 + Thymeleaf），解決的是「作品集網域單一」這個問題，
不是「技術深度不夠」——深度已經有了，缺的是廣度。

## 跟其他兩個專案的分工，避免重工

**JSP-practice 已經紮實練過、這裡不需要重新練的東西**（除非這個網域剛好有理由再練一次）：
- CSRF 防護、Spring Security（登入/RBAC/記住我）
- 悲觀鎖（`SELECT ... FOR UPDATE`）、樂觀鎖（version 欄位 + `OptimisticLockingFailureException`）
- 交易隔離層級對照（READ_COMMITTED vs REPEATABLE_READ 實測）
- Spring `@Transactional` 傳播行為（`REQUIRED` vs `REQUIRES_NEW`，剛好踩過一個因為傳播行為設錯、
  批次處理裡一筆失敗拖累整批 rollback 的真實 bug）
- 稽核軌跡（`audit_logs` 表 + 系統帳號歸因批次動作）
- 資料加密存放（AES-GCM）+ 盲索引查找（HMAC 雜湊）
- 冪等性設計（unique constraint + catch `DuplicateKeyException`）
- 簽核/審批流程（狀態機、CAS 式狀態轉換）
- 對帳補救機制（找出兩張表之間的資料落差、修復動作）
- 排程批次工作（`@Scheduled`）+ 手動觸發雙入口的設計模式
- SFTP 串接 + 固定長度電文解析（byte vs 字元編碼陷阱）
- MDC traceId 請求追蹤

**上一個 Boot/JPA 專案已經練過的東西**（細節不確定，等真的碰到再確認是否要重練）：
- Spring Boot 自動配置、JPA 基本 CRUD

**這個專案要練的新東西**：
1. **複式記帳網域建模**——這是這個專案最有價值的部分。核心不變量：一筆分錄（journal entry）底下
   多筆分錄明細（entry line），借方總額必須等於貸方總額，這個規則要在系統層級被強制保證，不能
   靠使用者自律。這個網域的「一致性規則」比書籍採購網域更硬、更適合拿來練資料完整性設計。
2. **JPA + MyBatis 混用的分工設計**——不是隨便挑，是要想清楚「這張表/這個查詢適合用 JPA 還是
   MyBatis」：簡單 entity CRUD（Account 基本資料維護）用 JPA 省力；複雜報表/餘額計算（例如某科目
   在某期間的餘額、試算表）用 MyBatis 手寫 SQL 比較好控制效能跟可讀性。這個分工本身就是一個值得
   在 README 或程式碼註解裡講清楚原因的設計決策，不是隨便混用。
3. **Thymeleaf**——跟 JSP 不同的樣板引擎，跟 Spring Boot 生態系整合得更自然。
4. **`jakarta.*` 命名空間**——Spring Boot 4.x 起跳，這個專案用的是 `jakarta.persistence`/
   `jakarta.validation`，不是 JSP-practice 那邊的 `javax.*`（JSP-practice 刻意停留在
   `javax.*`/Spring 5.3/Servlet 4.0 是為了貼近舊系統雇主環境，這個決定不適用在這個新專案——這裡
   用最新版本本身就是要展示「新舊都會」）。

## 技術骨架（已經用 Spring Initializr 建好）

Spring Boot 4.0.8、Java 21、Maven。依賴：`spring-boot-starter-data-jpa`、
`spring-boot-starter-thymeleaf`、`spring-boot-starter-validation`、`spring-boot-starter-webmvc`、
`mybatis-spring-boot-starter`、H2（dev/test 用）、Lombok。

## 第一階段規劃（先求一個端到端能動的垂直切片，不要一開始就鋪大）

1. 網域模型討論：`Account`（科目：資產/負債/權益/收入/費損）、`JournalEntry`（分錄，含日期/摘要）、
   `JournalEntryLine`（分錄明細：科目 + 借方金額或貸方金額 + 備註）。討論清楚借貸平衡驗證要放在
   哪一層（entity 自我驗證？service 層驗證？資料庫 CHECK constraint？可能要多層防禦，各自防的
   問題不一樣，值得展開討論）。
2. 用 JPA 建 `Account`/`JournalEntry`/`JournalEntryLine` 基本 CRUD，`Account` 先手動建幾筆種子
   科目資料（現金、應收帳款、銀行存款這類常見科目）。
3. 「新增一筆分錄」的表單/流程：使用者輸入多筆借貸明細，送出時後端驗證借貸平衡，不平衡就退回
   （比照 JSP-practice 編輯衝突時保留使用者輸入的做法，不平衡時不要清空使用者已經輸入的內容）。
4. 用 MyBatis 寫一個「科目餘額」或「試算表」查詢，作為 JPA/MyBatis 分工的第一個實際案例。
5. Thymeleaf 頁面：分錄列表、新增分錄表單、科目餘額檢視。

這個階段完成後再回頭決定要不要加：分期期別關帳、多幣別、複核簽核流程（可以直接借用
JSP-practice 學到的簽核狀態機經驗，但這次換成記帳網域的語意）。
