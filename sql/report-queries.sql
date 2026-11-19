-- StaplerLabs Accounting System: report queries
-- The same SQL the Java service layer runs. Useful for checking results directly in MySQL Workbench.
-- USE staplerlabs_accounts;

-- 1. Account balances (all time). Balance = debit - credit; Liability, Equity and Income show as credit.
SELECT a.code, a.name, a.type,
       COALESCE(SUM(l.debit), 0)  AS total_debit,
       COALESCE(SUM(l.credit), 0) AS total_credit,
       CASE WHEN a.type IN ('Asset','Expense')
            THEN COALESCE(SUM(l.debit - l.credit), 0)
            ELSE COALESCE(SUM(l.credit - l.debit), 0) END AS balance
FROM accounts a
LEFT JOIN journal_lines l ON l.account_id = a.id
GROUP BY a.id, a.code, a.name, a.type
ORDER BY a.code;

-- 2. Trial balance check: the two totals must be equal.
SELECT SUM(debit) AS total_debit, SUM(credit) AS total_credit,
       SUM(debit) - SUM(credit) AS difference
FROM journal_lines;

-- 3. Entries that do not balance (should always return no rows).
SELECT e.entry_no, e.entry_date, SUM(l.debit) AS dr, SUM(l.credit) AS cr
FROM journal_entries e JOIN journal_lines l ON l.entry_id = e.id
GROUP BY e.id, e.entry_no, e.entry_date
HAVING SUM(l.debit) <> SUM(l.credit);

-- 4. Profit and loss for the financial year 2026-27.
SELECT a.type, a.name, SUM(l.credit - l.debit) AS amount
FROM journal_lines l
JOIN journal_entries e ON e.id = l.entry_id
JOIN accounts a ON a.id = l.account_id
WHERE a.type IN ('Income','Expense')
  AND e.entry_date BETWEEN '2026-04-01' AND '2027-03-31'
GROUP BY a.type, a.name
ORDER BY a.type DESC, amount DESC;

-- 5. Month-wise income and expenses (drives the dashboard chart).
SELECT YEAR(e.entry_date) AS yr, MONTH(e.entry_date) AS mon,
       SUM(CASE WHEN a.type = 'Income'  THEN l.credit - l.debit ELSE 0 END) AS income,
       SUM(CASE WHEN a.type = 'Expense' THEN l.debit - l.credit ELSE 0 END) AS expense
FROM journal_lines l
JOIN journal_entries e ON e.id = l.entry_id
JOIN accounts a ON a.id = l.account_id
GROUP BY YEAR(e.entry_date), MONTH(e.entry_date)
ORDER BY yr, mon;

-- 6. Outstanding amount per customer and vendor.
SELECT p.kind, p.name, COUNT(i.id) AS unpaid_documents, COALESCE(SUM(i.total), 0) AS outstanding
FROM parties p
LEFT JOIN invoices i ON i.party_id = p.id AND i.status = 'UNPAID'
GROUP BY p.id, p.kind, p.name
ORDER BY p.kind, outstanding DESC;

-- 7. GST summary: output tax on sales, input tax on purchases.
SELECT kind, COUNT(*) AS documents, SUM(sub_total) AS taxable_value, SUM(tax) AS gst, SUM(total) AS total
FROM invoices
WHERE status <> 'CANCELLED'
GROUP BY kind;

-- 8. Ledger of the bank account with running balance (MySQL 8 window function).
SELECT e.entry_date, e.entry_no, e.narration, l.debit, l.credit,
       SUM(l.debit - l.credit) OVER (ORDER BY e.entry_date, e.entry_no) AS running_balance
FROM journal_lines l
JOIN journal_entries e ON e.id = l.entry_id
JOIN accounts a ON a.id = l.account_id
WHERE a.code = '1002'
ORDER BY e.entry_date, e.entry_no;
