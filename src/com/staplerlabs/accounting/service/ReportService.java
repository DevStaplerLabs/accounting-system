package com.staplerlabs.accounting.service;

import com.staplerlabs.accounting.AppException;
import com.staplerlabs.accounting.db.Database;
import com.staplerlabs.accounting.db.Sql;
import com.staplerlabs.accounting.model.Models.Account;
import com.staplerlabs.accounting.model.Models.JournalEntry;
import com.staplerlabs.accounting.model.Models.JournalLine;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.*;
import java.util.stream.Collectors;

/** Financial reports. All figures come from SQL aggregates over journal_lines. */
public class ReportService {
    private final Database db;

    public ReportService(Database db) { this.db = db; }

    /** Indian financial year: 1 April to 31 March. */
    public static LocalDate fyStart(LocalDate d) {
        int y = d.getMonthValue() >= 4 ? d.getYear() : d.getYear() - 1;
        return LocalDate.of(y, 4, 1);
    }

    private static BigDecimal sum(List<Account> list, String type) {
        return list.stream().filter(a -> a.type().equals(type)).map(Account::balance).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static List<Map<String, Object>> rows(List<Account> list, String type) {
        return list.stream().filter(a -> a.type().equals(type) && a.balance().signum() != 0)
                .map(a -> row("code", a.code(), "name", a.name(), "amount", a.balance())).collect(Collectors.toList());
    }

    private static Map<String, Object> row(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
        return m;
    }

    private static Account code(List<Account> list, String code) {
        return list.stream().filter(a -> a.code().equals(code)).findFirst().orElse(null);
    }

    public Map<String, Object> dashboard() {
        return db.tx(c -> {
            LocalDate today = LocalDate.now(), fy = fyStart(today);
            List<Account> all = LedgerService.accounts(c, null, today);
            List<Account> year = LedgerService.accounts(c, fy, today);
            BigDecimal cash = all.stream().filter(Account::cash).map(Account::balance).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal income = sum(year, "Income"), expense = sum(year, "Expense");

            // Month-wise income and expense for the financial year
            Map<String, BigDecimal[]> byMonth = new LinkedHashMap<>();
            for (LocalDate m = fy; !m.isAfter(today); m = m.plusMonths(1))
                byMonth.put(m.getYear() + "-" + m.getMonthValue(), new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            Sql.list(c, """
                SELECT YEAR(e.entry_date) AS y, MONTH(e.entry_date) AS m, a.type AS t,
                       SUM(l.credit - l.debit) AS amt
                FROM journal_lines l
                JOIN journal_entries e ON e.id = l.entry_id
                JOIN accounts a ON a.id = l.account_id
                WHERE a.type IN ('Income','Expense') AND e.entry_date BETWEEN ? AND ?
                GROUP BY YEAR(e.entry_date), MONTH(e.entry_date), a.type""", rs -> {
                BigDecimal[] slot = byMonth.get(rs.getInt("y") + "-" + rs.getInt("m"));
                if (slot != null) {
                    BigDecimal amt = Sql.money(rs, "amt");
                    if (rs.getString("t").equals("Income")) slot[0] = amt; else slot[1] = amt.negate();
                }
                return null;
            }, fy, today);
            List<Map<String, Object>> months = new ArrayList<>();
            byMonth.forEach((k, v) -> {
                String[] p = k.split("-");
                String label = LocalDate.of(Integer.parseInt(p[0]), Integer.parseInt(p[1]), 1).getMonth()
                        .getDisplayName(TextStyle.SHORT, Locale.ENGLISH);
                months.add(row("label", label, "income", v[0], "expense", v[1]));
            });

            List<Map<String, Object>> topExpenses = year.stream().filter(a -> a.type().equals("Expense") && a.balance().signum() > 0)
                    .sorted(Comparator.comparing(Account::balance).reversed()).limit(6)
                    .map(a -> row("name", a.name(), "amount", a.balance())).collect(Collectors.toList());

            List<Map<String, Object>> recent = new ArrayList<>();
            for (JournalEntry e : LedgerService.entries(c, null, null, null, 7))
                recent.add(row("entryNo", e.entryNo(), "date", e.date(), "narration", e.narration(), "source", e.source(),
                        "amount", e.lines().stream().map(JournalLine::debit).reduce(BigDecimal.ZERO, BigDecimal::add)));

            Map<String, Object> unpaid = new LinkedHashMap<>();
            for (String kind : List.of("SALE", "PURCHASE"))
                unpaid.put(kind, Sql.one(c, "SELECT COUNT(*) AS n, COALESCE(SUM(total),0) AS amt FROM invoices WHERE kind = ? AND status = 'UNPAID'",
                        rs -> row("count", rs.getInt("n"), "amount", Sql.money(rs, "amt")), kind));

            Account ar = code(all, LedgerService.AR), ap = code(all, LedgerService.AP);
            return row("fyStart", fy, "asOn", today, "cash", cash,
                    "receivable", ar == null ? BigDecimal.ZERO : ar.balance(),
                    "payable", ap == null ? BigDecimal.ZERO : ap.balance(),
                    "income", income, "expense", expense, "profit", income.subtract(expense),
                    "months", months, "topExpenses", topExpenses, "recent", recent, "unpaid", unpaid);
        });
    }

    public Map<String, Object> trialBalance(LocalDate asOn) {
        return db.tx(c -> {
            BigDecimal td = BigDecimal.ZERO, tc = BigDecimal.ZERO;
            List<Map<String, Object>> rows = new ArrayList<>();
            for (Account a : LedgerService.accounts(c, null, asOn)) {
                if (a.raw().signum() == 0) continue;
                BigDecimal dr = a.raw().signum() > 0 ? a.raw() : BigDecimal.ZERO;
                BigDecimal cr = a.raw().signum() < 0 ? a.raw().negate() : BigDecimal.ZERO;
                td = td.add(dr);
                tc = tc.add(cr);
                rows.add(row("id", a.id(), "code", a.code(), "name", a.name(), "type", a.type(), "debit", dr, "credit", cr));
            }
            return row("asOn", asOn, "rows", rows, "totalDebit", td, "totalCredit", tc, "balanced", td.compareTo(tc) == 0);
        });
    }

    public Map<String, Object> profitAndLoss(LocalDate from, LocalDate to) {
        if (from.isAfter(to)) throw new AppException("The From date must be on or before the To date.");
        return db.tx(c -> {
            List<Account> list = LedgerService.accounts(c, from, to);
            BigDecimal inc = sum(list, "Income"), exp = sum(list, "Expense");
            return row("from", from, "to", to, "income", rows(list, "Income"), "expense", rows(list, "Expense"),
                    "totalIncome", inc, "totalExpense", exp, "net", inc.subtract(exp));
        });
    }

    public Map<String, Object> balanceSheet(LocalDate asOn) {
        return db.tx(c -> {
            List<Account> list = LedgerService.accounts(c, null, asOn);
            BigDecimal assets = sum(list, "Asset"), liab = sum(list, "Liability"), equity = sum(list, "Equity");
            BigDecimal profit = sum(list, "Income").subtract(sum(list, "Expense"));
            BigDecimal right = liab.add(equity).add(profit);
            return row("asOn", asOn, "assets", rows(list, "Asset"), "liabilities", rows(list, "Liability"),
                    "equity", rows(list, "Equity"), "profit", profit, "totalAssets", assets,
                    "totalLiabilities", right, "balanced", assets.compareTo(right) == 0);
        });
    }

    public Map<String, Object> ledger(int accountId, LocalDate from, LocalDate to) {
        if (from.isAfter(to)) throw new AppException("The From date must be on or before the To date.");
        return db.tx(c -> {
            Account a = LedgerService.find(c, accountId);
            if (a == null) throw new AppException(404, "Account not found.");
            int sign = LedgerService.debitNormal(a.type()) ? 1 : -1;
            BigDecimal opening = LedgerService.accounts(c, null, from.minusDays(1)).stream()
                    .filter(x -> x.id() == accountId).findFirst().map(Account::balance).orElse(BigDecimal.ZERO);
            BigDecimal bal = opening, td = BigDecimal.ZERO, tc = BigDecimal.ZERO;
            List<Map<String, Object>> rows = new ArrayList<>();
            for (JournalEntry e : LedgerService.entries(c, from, to, null, 0)) {
                BigDecimal dr = BigDecimal.ZERO, cr = BigDecimal.ZERO;
                List<String> others = new ArrayList<>();
                boolean mine = false;
                for (JournalLine l : e.lines()) {
                    if (l.accountId() == accountId) { mine = true; dr = dr.add(l.debit()); cr = cr.add(l.credit()); }
                    else others.add(l.accountName());
                }
                if (!mine) continue;
                bal = bal.add(dr.subtract(cr).multiply(BigDecimal.valueOf(sign)));
                td = td.add(dr);
                tc = tc.add(cr);
                rows.add(row("date", e.date(), "entryNo", e.entryNo(), "particulars", String.join(", ", others),
                        "narration", e.narration(), "debit", dr, "credit", cr, "balance", bal));
            }
            return row("account", a, "from", from, "to", to, "opening", opening, "rows", rows,
                    "totalDebit", td, "totalCredit", tc, "closing", bal);
        });
    }

    public Map<String, Object> gstSummary(LocalDate from, LocalDate to) {
        if (from.isAfter(to)) throw new AppException("The From date must be on or before the To date.");
        return db.tx(c -> {
            List<Account> list = LedgerService.accounts(c, from, to);
            Account out = code(list, LedgerService.GST_OUT), in = code(list, LedgerService.GST_IN);
            BigDecimal output = out == null ? BigDecimal.ZERO : out.balance();
            BigDecimal input = in == null ? BigDecimal.ZERO : in.balance();
            List<Map<String, Object>> docs = Sql.list(c, """
                SELECT i.doc_no, i.kind, i.doc_date, p.name, p.gstin, i.gst_rate, i.sub_total, i.tax, i.total
                FROM invoices i JOIN parties p ON p.id = i.party_id
                WHERE i.status <> 'CANCELLED' AND i.doc_date BETWEEN ? AND ?
                ORDER BY i.doc_date, i.doc_no""", rs -> row("docNo", rs.getString("doc_no"), "kind", rs.getString("kind"),
                    "date", Sql.date(rs, "doc_date"), "party", rs.getString("name"), "gstin", rs.getString("gstin"),
                    "rate", Sql.money(rs, "gst_rate"), "taxable", Sql.money(rs, "sub_total"), "tax", Sql.money(rs, "tax"),
                    "total", Sql.money(rs, "total")), from, to);
            return row("from", from, "to", to, "output", output, "input", input, "net", output.subtract(input), "docs", docs);
        });
    }
}
