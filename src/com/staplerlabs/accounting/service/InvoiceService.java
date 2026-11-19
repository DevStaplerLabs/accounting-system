package com.staplerlabs.accounting.service;

import com.staplerlabs.accounting.AppException;
import com.staplerlabs.accounting.db.Database;
import com.staplerlabs.accounting.db.Sql;
import com.staplerlabs.accounting.model.Models.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Sales invoices (kind SALE) and purchase bills (kind PURCHASE).
 * Saving a document posts its journal entry; receiving or making payment posts a second one.
 */
public class InvoiceService {
    public static final Set<Integer> GST_RATES = Set.of(0, 5, 12, 18, 28);
    private final Database db;

    public InvoiceService(Database db) { this.db = db; }

    private static final String SELECT = """
        SELECT i.*, p.name AS party_name, p.address AS party_address, p.gstin AS party_gstin, a.name AS account_name
        FROM invoices i
        JOIN parties p  ON p.id = i.party_id
        JOIN accounts a ON a.id = i.account_id""";

    private static Sql.Mapper<Invoice> mapper(List<InvoiceItem> items) {
        return rs -> new Invoice(rs.getInt("id"), rs.getString("kind"), rs.getString("doc_no"), Sql.date(rs, "doc_date"),
                rs.getInt("party_id"), rs.getString("party_name"), rs.getString("party_address"), rs.getString("party_gstin"),
                rs.getInt("account_id"), rs.getString("account_name"), Sql.money(rs, "gst_rate"), Sql.money(rs, "sub_total"),
                Sql.money(rs, "tax"), Sql.money(rs, "total"), rs.getString("status"), Sql.date(rs, "paid_on"),
                rs.getString("notes"), items);
    }

    public List<Invoice> list(String kind) {
        return db.tx(c -> Sql.list(c, SELECT + " WHERE i.kind = ? ORDER BY i.doc_date DESC, i.id DESC",
                mapper(List.of()), kind.toUpperCase()));
    }

    public Invoice get(int id) { return db.tx(c -> get(c, id)); }

    public static Invoice get(Connection c, int id) throws SQLException {
        List<InvoiceItem> items = Sql.list(c, "SELECT * FROM invoice_items WHERE invoice_id = ? ORDER BY id",
                rs -> new InvoiceItem(rs.getString("description"), rs.getBigDecimal("qty").stripTrailingZeros(),
                        Sql.money(rs, "rate"), Sql.money(rs, "amount")), id);
        Invoice inv = Sql.one(c, SELECT + " WHERE i.id = ?", mapper(items), id);
        if (inv == null) throw new AppException(404, "Document not found.");
        return inv;
    }

    public Invoice create(String kind, LocalDate date, int partyId, int accountId, int gstRate, String notes,
                          List<InvoiceItem> items, String by) {
        return db.tx(c -> get(c, create(c, kind, date, partyId, accountId, gstRate, notes, items, by)));
    }

    public static int create(Connection c, String kind, LocalDate date, int partyId, int accountId, int gstRate,
                             String notes, List<InvoiceItem> rawItems, String by) throws SQLException {
        boolean sale = "SALE".equals(kind);
        if (!sale && !"PURCHASE".equals(kind)) throw new AppException("Unknown document type.");
        if (date == null) throw new AppException("Date is required.");
        Party party = PartyService.find(c, partyId);
        if (party == null) throw new AppException(sale ? "Choose a customer." : "Choose a vendor.");
        if (!party.kind().equals(sale ? "CUSTOMER" : "VENDOR"))
            throw new AppException(sale ? "Invoices can only be raised to customers." : "Bills can only be entered from vendors.");
        Account account = LedgerService.find(c, accountId);
        if (account == null) throw new AppException("Choose an account.");
        if (sale && !account.type().equals("Income")) throw new AppException("A sales invoice must use an Income account.");
        if (!sale && !(account.type().equals("Expense") || account.type().equals("Asset")) || account.cash())
            throw new AppException("A purchase bill must use an Expense or Asset account.");
        if (!GST_RATES.contains(gstRate)) throw new AppException("GST rate must be 0, 5, 12, 18 or 28 percent.");

        List<InvoiceItem> items = new ArrayList<>();
        for (InvoiceItem it : rawItems) {
            if (it.description() == null || it.description().isBlank()) continue;
            if (it.qty() == null || it.qty().signum() <= 0) throw new AppException("Quantity must be more than zero for \"" + it.description() + "\".");
            if (it.rate() == null || it.rate().signum() <= 0) throw new AppException("Rate must be more than zero for \"" + it.description() + "\".");
            BigDecimal amount = it.qty().multiply(it.rate()).setScale(2, RoundingMode.HALF_UP);
            items.add(new InvoiceItem(it.description().trim(), it.qty(), Audit.money(it.rate()), amount));
        }
        if (items.isEmpty()) throw new AppException("Add at least one item with a description, quantity and rate.");

        BigDecimal sub = items.stream().map(InvoiceItem::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal tax = sub.multiply(BigDecimal.valueOf(gstRate)).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        BigDecimal total = sub.add(tax);

        String prefix = sale ? "INV-" : "BILL-";
        String last = Sql.one(c, "SELECT doc_no FROM invoices WHERE kind = ? ORDER BY id DESC LIMIT 1", rs -> rs.getString(1), kind);
        int next = last == null ? 1 : Integer.parseInt(last.substring(prefix.length())) + 1;
        String docNo = prefix + String.format("%04d", next);

        int id = Sql.insert(c, """
            INSERT INTO invoices(kind, doc_no, doc_date, party_id, account_id, gst_rate, sub_total, tax, total, status, notes)
            VALUES (?,?,?,?,?,?,?,?,?,'UNPAID',?)""", kind, docNo, date, partyId, accountId, gstRate, sub, tax, total, notes);
        for (InvoiceItem it : items)
            Sql.update(c, "INSERT INTO invoice_items(invoice_id, description, qty, rate, amount) VALUES (?,?,?,?,?)",
                    id, it.description(), it.qty(), it.rate(), it.amount());

        List<LineInput> lines = new ArrayList<>();
        if (sale) {   // Dr Debtors, Cr Income, Cr GST Output
            lines.add(new LineInput(LedgerService.byCode(c, LedgerService.AR).id(), total, null));
            lines.add(new LineInput(accountId, null, sub));
            lines.add(new LineInput(LedgerService.byCode(c, LedgerService.GST_OUT).id(), null, tax));
        } else {      // Dr Expense/Asset, Dr GST Input, Cr Creditors
            lines.add(new LineInput(accountId, sub, null));
            lines.add(new LineInput(LedgerService.byCode(c, LedgerService.GST_IN).id(), tax, null));
            lines.add(new LineInput(LedgerService.byCode(c, LedgerService.AP).id(), null, total));
        }
        String nar = (sale ? "Sales invoice " + docNo + " to " : "Purchase bill " + docNo + " from ") + party.name();
        int entryId = LedgerService.post(c, date, nar, kind, docNo, lines, by);
        Sql.update(c, "UPDATE invoices SET entry_id = ? WHERE id = ?", entryId, id);
        return id;
    }

    public Invoice pay(int id, int cashId, LocalDate date, String by) {
        return db.tx(c -> { pay(c, id, cashId, date, by); return get(c, id); });
    }

    public static void pay(Connection c, int id, int cashId, LocalDate date, String by) throws SQLException {
        Invoice inv = get(c, id);
        if (!inv.status().equals("UNPAID")) throw new AppException(inv.docNo() + " is already " + inv.status().toLowerCase() + ".");
        Account cash = LedgerService.find(c, cashId);
        if (cash == null || !cash.cash()) throw new AppException("Choose a cash or bank account.");
        if (date == null) throw new AppException("Payment date is required.");
        if (date.isBefore(inv.date())) throw new AppException("Payment date cannot be before the document date.");
        boolean sale = inv.kind().equals("SALE");
        List<LineInput> lines = sale
                ? List.of(new LineInput(cashId, inv.total(), null), new LineInput(LedgerService.byCode(c, LedgerService.AR).id(), null, inv.total()))
                : List.of(new LineInput(LedgerService.byCode(c, LedgerService.AP).id(), inv.total(), null), new LineInput(cashId, null, inv.total()));
        String nar = (sale ? "Receipt against " + inv.docNo() + " from " : "Payment against " + inv.docNo() + " to ") + inv.partyName();
        int entryId = LedgerService.post(c, date, nar, sale ? "RECEIPT" : "PAYMENT", inv.docNo(), lines, by);
        Sql.update(c, "UPDATE invoices SET status = 'PAID', paid_on = ?, payment_entry_id = ? WHERE id = ?", date, entryId, id);
    }

    /** Cancels an unpaid document: keeps it (so the number is never reused) and removes its journal entry. */
    public Invoice cancel(int id, String by) {
        return db.tx(c -> {
            Invoice inv = get(c, id);
            if (!inv.status().equals("UNPAID")) throw new AppException("Only an unpaid document can be cancelled.");
            Integer entryId = Sql.one(c, "SELECT entry_id FROM invoices WHERE id = ?", rs -> rs.getInt(1), id);
            Sql.update(c, "UPDATE invoices SET status = 'CANCELLED', entry_id = NULL WHERE id = ?", id);
            if (entryId != null && entryId > 0) Sql.update(c, "DELETE FROM journal_entries WHERE id = ?", entryId);
            Audit.log(c, by, "DOC_CANCEL", inv.docNo());
            return get(c, id);
        });
    }
}
