package com.staplerlabs.accounting.service;

import com.staplerlabs.accounting.AppException;
import com.staplerlabs.accounting.db.Database;
import com.staplerlabs.accounting.db.Sql;
import com.staplerlabs.accounting.model.Models.Account;
import com.staplerlabs.accounting.model.Models.JournalEntry;
import com.staplerlabs.accounting.model.Models.JournalLine;
import com.staplerlabs.accounting.model.Models.LineInput;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.*;

/** Chart of accounts and the double-entry journal. Every posting in the system goes through post(). */
public class LedgerService {
    public static final String AR = "1100", GST_IN = "1300", AP = "2001", GST_OUT = "2100";
    public static final LocalDate MIN = LocalDate.of(1900, 1, 1), MAX = LocalDate.of(9999, 12, 31);
    public static final List<String> TYPES = List.of("Asset", "Liability", "Equity", "Income", "Expense");

    private final Database db;

    public LedgerService(Database db) { this.db = db; }

    public static boolean debitNormal(String type) {
        return "Asset".equals(type) || "Expense".equals(type);
    }

    private static final Sql.Mapper<Account> PLAIN = rs -> new Account(rs.getInt("id"), rs.getString("code"),
            rs.getString("name"), rs.getString("type"), rs.getBoolean("is_cash"), rs.getBoolean("is_system"),
            BigDecimal.ZERO, BigDecimal.ZERO);

    /* ---------------- Accounts ---------------- */

    /** All accounts with balances from the journal lines dated between from and to. */
    public static List<Account> accounts(Connection c, LocalDate from, LocalDate to) throws SQLException {
        String sql = """
            SELECT a.id, a.code, a.name, a.type, a.is_cash, a.is_system,
                   COALESCE(t.dr, 0) AS dr, COALESCE(t.cr, 0) AS cr
            FROM accounts a
            LEFT JOIN (
                SELECT l.account_id, SUM(l.debit) AS dr, SUM(l.credit) AS cr
                FROM journal_lines l
                JOIN journal_entries e ON e.id = l.entry_id
                WHERE e.entry_date BETWEEN ? AND ?
                GROUP BY l.account_id
            ) t ON t.account_id = a.id
            ORDER BY a.code""";
        return Sql.list(c, sql, rs -> {
            String type = rs.getString("type");
            BigDecimal raw = Sql.money(rs, "dr").subtract(Sql.money(rs, "cr"));
            return new Account(rs.getInt("id"), rs.getString("code"), rs.getString("name"), type,
                    rs.getBoolean("is_cash"), rs.getBoolean("is_system"), debitNormal(type) ? raw : raw.negate(), raw);
        }, from == null ? MIN : from, to == null ? MAX : to);
    }

    public List<Account> accounts() { return db.tx(c -> accounts(c, null, null)); }

    public static Account find(Connection c, int id) throws SQLException {
        return Sql.one(c, "SELECT * FROM accounts WHERE id = ?", PLAIN, id);
    }

    public static Account byCode(Connection c, String code) throws SQLException {
        Account a = Sql.one(c, "SELECT * FROM accounts WHERE code = ?", PLAIN, code);
        if (a == null) throw new AppException("System account " + code + " is missing from the chart of accounts.");
        return a;
    }

    public Account createAccount(String code, String name, String type, boolean cash, String by) {
        if (code.isBlank() || name.isBlank()) throw new AppException("Code and name are both required.");
        if (!code.matches("\\d{3,10}")) throw new AppException("Account code should be 3 to 10 digits.");
        if (!TYPES.contains(type)) throw new AppException("Choose a valid account type.");
        if (cash && !type.equals("Asset")) throw new AppException("Only an Asset account can be a cash or bank account.");
        return db.tx(c -> {
            if (Sql.count(c, "SELECT COUNT(*) FROM accounts WHERE code = ?", code) > 0)
                throw new AppException("Account code " + code + " already exists.");
            int id = Sql.insert(c, "INSERT INTO accounts(code, name, type, is_cash, is_system) VALUES (?,?,?,?,FALSE)",
                    code, name.trim(), type, cash);
            Audit.log(c, by, "ACCOUNT_CREATE", code + " " + name);
            return find(c, id);
        });
    }

    public void renameAccount(int id, String name, String by) {
        if (name.isBlank()) throw new AppException("Account name is required.");
        db.tx(c -> {
            if (Sql.update(c, "UPDATE accounts SET name = ? WHERE id = ?", name.trim(), id) == 0)
                throw new AppException(404, "Account not found.");
            Audit.log(c, by, "ACCOUNT_RENAME", id + " -> " + name);
            return null;
        });
    }

    public void deleteAccount(int id, String by) {
        db.tx(c -> {
            Account a = find(c, id);
            if (a == null) throw new AppException(404, "Account not found.");
            if (a.system()) throw new AppException("This is a system account used by invoices and bills. It cannot be deleted.");
            if (Sql.count(c, "SELECT COUNT(*) FROM journal_lines WHERE account_id = ?", id) > 0
                    || Sql.count(c, "SELECT COUNT(*) FROM invoices WHERE account_id = ?", id) > 0)
                throw new AppException("This account has transactions. It cannot be deleted.");
            Sql.update(c, "DELETE FROM accounts WHERE id = ?", id);
            Audit.log(c, by, "ACCOUNT_DELETE", a.code() + " " + a.name());
            return null;
        });
    }

    /* ---------------- Journal ---------------- */

    /** Validates and saves a balanced journal entry. Returns the new entry id. */
    public static int post(Connection c, LocalDate date, String narration, String source, String ref,
                           List<LineInput> input, String by) throws SQLException {
        if (date == null) throw new AppException("Date is required.");
        List<LineInput> lines = new ArrayList<>();
        for (LineInput l : input) {
            BigDecimal dr = Audit.money(l.debit()), cr = Audit.money(l.credit());
            if (dr.signum() == 0 && cr.signum() == 0) continue;
            if (dr.signum() < 0 || cr.signum() < 0) throw new AppException("Amounts cannot be negative.");
            if (dr.signum() > 0 && cr.signum() > 0) throw new AppException("A line can have a debit or a credit, not both.");
            if (find(c, l.accountId()) == null) throw new AppException("Every line needs a valid account.");
            lines.add(new LineInput(l.accountId(), dr, cr));
        }
        if (lines.size() < 2) throw new AppException("An entry needs at least two lines with amounts.");
        BigDecimal td = lines.stream().map(LineInput::debit).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal tc = lines.stream().map(LineInput::credit).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (td.compareTo(tc) != 0)
            throw new AppException("Debits (" + Audit.fmt(td) + ") and credits (" + Audit.fmt(tc) + ") do not match.");

        int no = Sql.count(c, "SELECT COALESCE(MAX(entry_no), 0) + 1 FROM journal_entries");
        int id = Sql.insert(c, "INSERT INTO journal_entries(entry_no, entry_date, narration, source, ref_no, created_by) VALUES (?,?,?,?,?,?)",
                no, date, narration == null ? "" : narration.trim(), source, ref, by);
        for (LineInput l : lines)
            Sql.update(c, "INSERT INTO journal_lines(entry_id, account_id, debit, credit) VALUES (?,?,?,?)",
                    id, l.accountId(), l.debit(), l.credit());
        Audit.log(c, by, "ENTRY_POST", "#" + no + " " + source + " " + Audit.fmt(td));
        return id;
    }

    public int postJournal(LocalDate date, String narration, List<LineInput> lines, String by) {
        return db.tx(c -> post(c, date, narration, "JOURNAL", null, lines, by));
    }

    /** Payment: money goes out of a cash/bank account. Receipt: money comes in. */
    public int postVoucher(String type, LocalDate date, int cashId, int otherId, BigDecimal amount, String narration, String by) {
        if (amount == null || amount.signum() <= 0) throw new AppException("Enter an amount greater than zero.");
        boolean payment = "PAYMENT".equalsIgnoreCase(type);
        if (!payment && !"RECEIPT".equalsIgnoreCase(type)) throw new AppException("Voucher type must be Payment or Receipt.");
        return db.tx(c -> {
            Account cash = find(c, cashId);
            if (cash == null || !cash.cash()) throw new AppException("Choose a cash or bank account.");
            if (cashId == otherId) throw new AppException("Choose two different accounts.");
            List<LineInput> lines = payment
                    ? List.of(new LineInput(otherId, amount, null), new LineInput(cashId, null, amount))
                    : List.of(new LineInput(cashId, amount, null), new LineInput(otherId, null, amount));
            String nar = narration == null || narration.isBlank() ? (payment ? "Payment" : "Receipt") : narration;
            return post(c, date, nar, payment ? "PAYMENT" : "RECEIPT", null, lines, by);
        });
    }

    public List<JournalEntry> entries(LocalDate from, LocalDate to, String q) {
        return db.tx(c -> entries(c, from, to, q, 0));
    }

    public static List<JournalEntry> entries(Connection c, LocalDate from, LocalDate to, String q, int limit) throws SQLException {
        from = from == null ? MIN : from;
        to = to == null ? MAX : to;
        List<Object> params = new ArrayList<>(List.of(from, to));
        String where = "e.entry_date BETWEEN ? AND ?";
        if (q != null && !q.isBlank()) {
            String like = "%" + q.trim().toLowerCase() + "%";
            int no = q.trim().matches("\\d{1,9}") ? Integer.parseInt(q.trim()) : -1;
            where += " AND (LOWER(e.narration) LIKE ? OR LOWER(COALESCE(e.ref_no,'')) LIKE ? OR e.entry_no = ?)";
            params.addAll(List.of(like, like, no));
        }
        String order = limit > 0 ? " ORDER BY e.entry_date DESC, e.entry_no DESC LIMIT " + limit : " ORDER BY e.entry_date, e.entry_no";
        String sql = "SELECT e.*, (SELECT COUNT(*) FROM invoices i WHERE i.entry_id = e.id OR i.payment_entry_id = e.id) AS links "
                + "FROM journal_entries e WHERE " + where + order;

        record Head(int id, int no, LocalDate date, String narration, String source, String ref, boolean locked) {}
        List<Head> heads = Sql.list(c, sql, rs -> new Head(rs.getInt("id"), rs.getInt("entry_no"), Sql.date(rs, "entry_date"),
                rs.getString("narration"), rs.getString("source"), rs.getString("ref_no"), rs.getInt("links") > 0), params.toArray());

        Map<Integer, List<JournalLine>> lines = new HashMap<>();
        Sql.list(c, """
            SELECT l.entry_id, l.account_id, a.code, a.name, l.debit, l.credit
            FROM journal_lines l
            JOIN accounts a ON a.id = l.account_id
            JOIN journal_entries e ON e.id = l.entry_id
            WHERE e.entry_date BETWEEN ? AND ?
            ORDER BY l.id""", rs -> lines.computeIfAbsent(rs.getInt("entry_id"), k -> new ArrayList<>())
                .add(new JournalLine(rs.getInt("account_id"), rs.getString("code"), rs.getString("name"),
                        Sql.money(rs, "debit"), Sql.money(rs, "credit"))), from, to);

        List<JournalEntry> out = new ArrayList<>();
        for (Head h : heads) {
            List<JournalLine> ls = new ArrayList<>(lines.getOrDefault(h.id(), List.of()));
            ls.sort(Comparator.comparing((JournalLine l) -> l.debit().signum() == 0)); // debit lines first
            out.add(new JournalEntry(h.id(), h.no(), h.date(), h.narration(), h.source(), h.ref(), h.locked(), ls));
        }
        return out;
    }

    public void deleteEntry(int id, String by) {
        db.tx(c -> {
            Integer no = Sql.one(c, "SELECT entry_no FROM journal_entries WHERE id = ?", rs -> rs.getInt(1), id);
            if (no == null) throw new AppException(404, "Entry not found.");
            if (Sql.count(c, "SELECT COUNT(*) FROM invoices WHERE entry_id = ? OR payment_entry_id = ?", id, id) > 0)
                throw new AppException("This entry belongs to an invoice or bill. Cancel the document instead.");
            Sql.update(c, "DELETE FROM journal_entries WHERE id = ?", id);
            Audit.log(c, by, "ENTRY_DELETE", "#" + no);
            return null;
        });
    }
}
