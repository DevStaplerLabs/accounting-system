-- StaplerLabs Accounting System: database schema
-- Runs on MySQL 8 and on H2 (MySQL mode). Executed automatically at start-up.

CREATE TABLE IF NOT EXISTS company (
  id       INT PRIMARY KEY,
  name     VARCHAR(150) NOT NULL,
  address  VARCHAR(255),
  email    VARCHAR(120),
  phone    VARCHAR(40),
  gstin    VARCHAR(20)
);

CREATE TABLE IF NOT EXISTS users (
  id            INT AUTO_INCREMENT PRIMARY KEY,
  username      VARCHAR(50)  NOT NULL UNIQUE,
  full_name     VARCHAR(100) NOT NULL,
  password_hash VARCHAR(100) NOT NULL,
  salt          VARCHAR(40)  NOT NULL,
  role          VARCHAR(12)  NOT NULL,
  active        BOOLEAN      NOT NULL DEFAULT TRUE,
  created_at    TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT chk_user_role CHECK (role IN ('ADMIN','ACCOUNTANT','VIEWER'))
);

CREATE TABLE IF NOT EXISTS accounts (
  id        INT AUTO_INCREMENT PRIMARY KEY,
  code      VARCHAR(10)  NOT NULL UNIQUE,
  name      VARCHAR(100) NOT NULL,
  type      VARCHAR(10)  NOT NULL,
  is_cash   BOOLEAN      NOT NULL DEFAULT FALSE,
  is_system BOOLEAN      NOT NULL DEFAULT FALSE,
  CONSTRAINT chk_account_type CHECK (type IN ('Asset','Liability','Equity','Income','Expense'))
);

CREATE TABLE IF NOT EXISTS parties (
  id      INT AUTO_INCREMENT PRIMARY KEY,
  name    VARCHAR(120) NOT NULL,
  kind    VARCHAR(10)  NOT NULL,
  address VARCHAR(255),
  gstin   VARCHAR(20),
  phone   VARCHAR(40),
  email   VARCHAR(120),
  CONSTRAINT chk_party_kind CHECK (kind IN ('CUSTOMER','VENDOR'))
);

CREATE TABLE IF NOT EXISTS journal_entries (
  id         INT AUTO_INCREMENT PRIMARY KEY,
  entry_no   INT          NOT NULL UNIQUE,
  entry_date DATE         NOT NULL,
  narration  VARCHAR(255),
  source     VARCHAR(12)  NOT NULL DEFAULT 'JOURNAL',
  ref_no     VARCHAR(30),
  created_by VARCHAR(50),
  created_at TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS journal_lines (
  id         INT AUTO_INCREMENT PRIMARY KEY,
  entry_id   INT           NOT NULL,
  account_id INT           NOT NULL,
  debit      DECIMAL(14,2) NOT NULL DEFAULT 0,
  credit     DECIMAL(14,2) NOT NULL DEFAULT 0,
  CONSTRAINT fk_line_entry   FOREIGN KEY (entry_id)   REFERENCES journal_entries(id) ON DELETE CASCADE,
  CONSTRAINT fk_line_account FOREIGN KEY (account_id) REFERENCES accounts(id),
  CONSTRAINT chk_line_amount CHECK (debit >= 0 AND credit >= 0 AND (debit = 0 OR credit = 0))
);

CREATE TABLE IF NOT EXISTS invoices (
  id               INT AUTO_INCREMENT PRIMARY KEY,
  kind             VARCHAR(10)   NOT NULL,
  doc_no           VARCHAR(20)   NOT NULL UNIQUE,
  doc_date         DATE          NOT NULL,
  party_id         INT           NOT NULL,
  account_id       INT           NOT NULL,
  gst_rate         DECIMAL(5,2)  NOT NULL DEFAULT 0,
  sub_total        DECIMAL(14,2) NOT NULL,
  tax              DECIMAL(14,2) NOT NULL,
  total            DECIMAL(14,2) NOT NULL,
  status           VARCHAR(10)   NOT NULL DEFAULT 'UNPAID',
  paid_on          DATE,
  entry_id         INT,
  payment_entry_id INT,
  notes            VARCHAR(255),
  CONSTRAINT fk_inv_party   FOREIGN KEY (party_id)         REFERENCES parties(id),
  CONSTRAINT fk_inv_account FOREIGN KEY (account_id)       REFERENCES accounts(id),
  CONSTRAINT fk_inv_entry   FOREIGN KEY (entry_id)         REFERENCES journal_entries(id),
  CONSTRAINT fk_inv_payment FOREIGN KEY (payment_entry_id) REFERENCES journal_entries(id),
  CONSTRAINT chk_inv_kind   CHECK (kind IN ('SALE','PURCHASE')),
  CONSTRAINT chk_inv_status CHECK (status IN ('UNPAID','PAID','CANCELLED'))
);

CREATE TABLE IF NOT EXISTS invoice_items (
  id          INT AUTO_INCREMENT PRIMARY KEY,
  invoice_id  INT           NOT NULL,
  description VARCHAR(255)  NOT NULL,
  qty         DECIMAL(10,2) NOT NULL,
  rate        DECIMAL(14,2) NOT NULL,
  amount      DECIMAL(14,2) NOT NULL,
  CONSTRAINT fk_item_invoice FOREIGN KEY (invoice_id) REFERENCES invoices(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS audit_log (
  id         INT AUTO_INCREMENT PRIMARY KEY,
  user_name  VARCHAR(50),
  action     VARCHAR(40) NOT NULL,
  details    VARCHAR(255),
  created_at TIMESTAMP   DEFAULT CURRENT_TIMESTAMP
);
