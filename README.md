# StaplerLabs Accounts

**Accounting System for StaplerLabs Private Limited**
Software Engineering Practical | Java backend, SQL database, web front end

---

## 1. Problem statement

StaplerLabs Private Limited is a consulting-led technology and AI solutions company based in New Delhi. It earns consulting fees and software development income, buys cloud services and office supplies, and pays salaries and rent. It needs one system to:

- record every transaction once, under double-entry rules, so the books always balance
- raise GST sales invoices and record purchase bills
- track who owes the company money and whom the company owes
- produce the ledger, trial balance, profit and loss account, balance sheet and GST summary on demand
- control who can change the books

## 2. Technology

| Layer | Technology |
|---|---|
| Front end | HTML, CSS, JavaScript (no framework). Fonts and colours follow staplerlabs.com: DM Sans, Playfair Display, StaplerLabs gold |
| Backend | Java 21, built-in `com.sun.net.httpserver` HTTP server, REST API with JSON |
| Database access | JDBC with `PreparedStatement` on every query, one transaction per business operation |
| Database | **MySQL 8** (production) or **H2** in MySQL mode (zero-setup option) |
| Security | PBKDF2-SHA256 salted password hashes, HttpOnly session cookie, role-based access |

There are no build tools or frameworks. The project compiles with `javac` alone, so every line of the backend can be explained in a viva.

## 3. How to run

**Requirements:** JDK 17 or newer (tested on JDK 21).

1. Open the `accounting-system` folder.
2. Double-click **`run.bat`**. It compiles the code and starts the server.
3. Open **http://localhost:8080** in a browser.
4. Sign in with the first-login username and password listed in `config/app.properties`, then change the password from the user menu (top right).

On first start, the system creates the tables, a default chart of accounts and demo data for the current financial year.

### Using MySQL

Edit `config/app.properties`:

```properties
db.type=mysql
mysql.user=root
mysql.password=<your MySQL password>
```

Run `run.bat` again. The database `staplerlabs_accounts` and all tables are created automatically.
To use the built-in H2 database instead, set `db.type=h2`. H2 stores data in the `data` folder.

## 4. Features

| Module | What it does |
|---|---|
| Sign in and users | Sign-in, change password, add users as Administrator, Accountant or Viewer, deactivate users |
| Dashboard | Cash and bank balance, receivables, payables, net profit, monthly income and expense chart, top expense heads, recent activity, quick actions |
| Sales invoices | GST invoices (0, 5, 12, 18, 28%) with several items, printable tax invoice with logo and amount in words, receive payment, cancel |
| Purchase bills | Vendor bills with input GST, make payment, cancel |
| Customers and vendors | Party master with GSTIN validation and outstanding balance |
| Payments and receipts | One-step voucher entry against a cash or bank account |
| Journal entries | Multi-line double-entry form with a live debit and credit difference check |
| Day book | All entries for a period, with search, CSV export and print |
| Chart of accounts | Add, rename and delete accounts; system accounts and accounts with transactions are protected |
| Ledger | Any account, with opening balance, running balance and closing balance |
| Trial balance | As on any date, with a balanced or not-balanced check |
| Profit and loss | Any period, with net profit as a percentage of income |
| Balance sheet | As on any date, with an assets = capital + liabilities check |
| GST summary | Output GST, input GST, net payable and a document-wise register |
| Company and settings | Company details for documents, audit log of every action, reset to demo data or empty books |
| Interface | Light and dark themes, mobile layout, pop-up forms, notifications, print-ready reports |

## 5. Software Requirements Specification (summary)

### 5.1 Users and roles

| Role | Can do |
|---|---|
| Administrator | Everything, including users, company details, audit log and reset |
| Accountant | Record and cancel transactions, manage accounts and parties, view all reports |
| Viewer | View and print reports only |

### 5.2 Functional requirements

| ID | Requirement |
|---|---|
| FR1 | The system shall allow access only after sign-in with a valid username and password. |
| FR2 | The system shall store passwords only as salted hashes. |
| FR3 | The system shall block every change by a Viewer, and block user and settings screens for non-administrators. |
| FR4 | The user shall be able to create accounts with a unique 3 to 10 digit code, a name and one of five types. |
| FR5 | The system shall refuse to delete a system account or any account that has transactions. |
| FR6 | The user shall be able to post a journal entry with two or more lines. |
| FR7 | The system shall reject an entry whose total debit does not equal total credit, a line with both a debit and a credit, and negative amounts. |
| FR8 | The user shall be able to record a payment or receipt against a cash or bank account in one step. |
| FR9 | Saving a sales invoice shall post Dr Debtors, Cr Income, Cr GST Output Payable. |
| FR10 | Saving a purchase bill shall post Dr Expense or Asset, Dr GST Input Credit, Cr Creditors. |
| FR11 | Recording payment shall post Dr Bank, Cr Debtors (sales) or Dr Creditors, Cr Bank (purchases) and mark the document Paid. |
| FR12 | Cancelling an unpaid document shall remove its journal entry but keep the document number reserved. |
| FR13 | The system shall produce the ledger, trial balance, profit and loss account, balance sheet and GST summary for a chosen date or period. |
| FR14 | The system shall record every change in an audit log with the user, the action and the time. |

### 5.3 Non-functional requirements

- **Integrity:** each business operation runs in one database transaction. If any step fails, nothing is saved. Foreign keys and CHECK constraints guard the data in the database itself.
- **Security:** parameterised SQL (no SQL injection), HttpOnly SameSite session cookie, 8-hour session expiry, server binds to localhost only, path checks on static files.
- **Usability:** Indian number format (Rs. 1,00,000.00), Indian financial year (April to March), plain-language error messages, keyboard-friendly forms.
- **Portability:** runs on any OS with Java; MySQL or H2 by changing one setting.
- **Maintainability:** layered design (presentation, API, service, data access) with one class per responsibility.

## 6. Design

### 6.1 Architecture (three tiers)

```
 Browser (web/index.html, app.js, app.css)
        |  HTTP + JSON  (fetch /api/...)
        v
 ApiServer  ->  routes, sign-in check, role check, JSON in/out
        |
        v
 Services   ->  LedgerService, InvoiceService, PartyService, ReportService,
                AuthService, CompanyService, Audit, DemoData
        |  JDBC (PreparedStatement, transactions)
        v
 MySQL 8  /  H2 (MySQL mode)    <- sql/schema.sql
```

### 6.2 Project structure

```
accounting-system/
  run.bat                     compile and start
  config/app.properties       port, database choice, first-login user
  lib/                        MySQL Connector/J and H2 JDBC drivers
  sql/schema.sql              CREATE TABLE statements (run automatically)
  sql/report-queries.sql      report queries to run by hand in MySQL
  src/com/staplerlabs/accounting/
    Main.java                 start-up
    AppException.java         business-rule errors shown to the user
    db/Database.java          connections, transactions, script runner
    db/Sql.java               JDBC helper
    model/Models.java         domain records (Account, JournalEntry, Invoice, ...)
    service/*.java            business rules and reports
    web/ApiServer.java        REST API and static files
    web/Json.java             JSON reader and writer
  web/                        front end (index.html, app.css, app.js, logo.png)
```

### 6.3 Database design (ER summary)

| Table | Key columns | Relationships |
|---|---|---|
| `company` | id, name, address, gstin | single row |
| `users` | id, username (unique), password_hash, salt, role, active | |
| `accounts` | id, code (unique), name, type, is_cash, is_system | |
| `parties` | id, name, kind (CUSTOMER / VENDOR), gstin | |
| `journal_entries` | id, entry_no (unique), entry_date, narration, source, ref_no | 1 to many `journal_lines` |
| `journal_lines` | id, entry_id, account_id, debit, credit | many to 1 `journal_entries` (cascade delete), many to 1 `accounts` |
| `invoices` | id, kind (SALE / PURCHASE), doc_no (unique), party_id, account_id, gst_rate, sub_total, tax, total, status, entry_id, payment_entry_id | many to 1 `parties`, `accounts`; 1 to 1 sales entry and payment entry |
| `invoice_items` | id, invoice_id, description, qty, rate, amount | many to 1 `invoices` (cascade delete) |
| `audit_log` | id, user_name, action, details, created_at | |

Constraints in the database: `CHECK (debit >= 0 AND credit >= 0 AND (debit = 0 OR credit = 0))`, account type and status lists, unique codes and numbers, foreign keys.

### 6.4 Accounting rules

- Asset and Expense accounts have a normal debit balance. Liability, Equity and Income have a normal credit balance.
- Balance = total debit minus total credit, shown on the account's normal side.
- Net profit = Income minus Expenses.
- Balance sheet check: Assets = Capital + Liabilities + Profit.
- GST is split equally into CGST and SGST (intra-state supply).

### 6.5 Main REST endpoints

| Method | Path | Purpose |
|---|---|---|
| POST | `/api/login`, `/api/logout` | Sign in and out |
| GET, POST | `/api/accounts` | List or create accounts |
| GET, POST | `/api/parties` | List or create customers and vendors |
| GET, POST | `/api/entries` | Day book, post a journal entry |
| POST | `/api/vouchers` | Payment or receipt |
| GET, POST | `/api/invoices` | List or create invoices and bills |
| POST | `/api/invoices/{id}/pay`, `/api/invoices/{id}/cancel` | Record payment, cancel |
| GET | `/api/reports/dashboard`, `trial-balance`, `profit-loss`, `balance-sheet`, `ledger`, `gst` | Reports |
| GET, POST | `/api/users`, `/api/audit`, `/api/reset` | Administration |

### 6.6 Use cases

- **Accountant:** sign in, create invoice or bill, record payment or receipt, post journal entry, manage accounts and parties, view and export reports.
- **Viewer:** sign in, view and print reports.
- **Administrator:** everything above, plus manage users, edit company details, view the audit log, reset data.

## 7. Test cases

All tests below were run against the H2 database through the API and the browser.

| ID | Input | Expected result | Result |
|---|---|---|---|
| TC1 | Open any API without signing in | "Please sign in." (401) | Pass |
| TC2 | Sign in with a wrong password | "Incorrect username or password." | Pass |
| TC3 | Journal: Dr Travel 1,000, Cr Cash 900 | Live "Difference Rs. 100.00", then rejected: debits and credits do not match | Pass |
| TC4 | Same entry with Cr Cash 1,000 | Shows "Balanced", entry posted | Pass |
| TC5 | Sales invoice: 2 x Rs. 5,000 at 18% GST | Taxable 10,000, CGST 900, SGST 900, total 11,800, "Rupees Eleven Thousand Eight Hundred Only" | Pass |
| TC6 | Receive payment on that invoice | Status Paid, receipt entry posted | Pass |
| TC7 | Trial balance after all of the above | Debits equal credits | Pass |
| TC8 | Balance sheet after all of the above | Assets equal capital plus liabilities | Pass |
| TC9 | Viewer tries to create an account | "Your role is view-only." (403) | Pass |
| TC10 | Viewer opens the users list | "Only an administrator can do this." (403) | Pass |
| TC11 | Create a user with password "short" | Rejected: at least 8 characters | Pass |
| TC12 | Delete system account 1100 Debtors | Rejected: system account | Pass |
| TC13 | Add account with an existing code | Rejected: code already exists | Pass |
| TC14 | Open all 15 screens in light and dark theme | Every screen loads, no errors | Pass |

## 8. Limitations and future scope

- No year-end closing entries; profit is carried in the balance sheet as the Profit and Loss Account.
- IGST for inter-state supplies, TDS, inventory and bank reconciliation are not included.
- Sessions are kept in server memory, so a server restart signs everyone out.
- A packaged installer and automated unit tests (JUnit) can be added.
