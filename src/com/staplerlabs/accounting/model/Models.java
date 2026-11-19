package com.staplerlabs.accounting.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Domain model. Each record maps to a table (or a joined view) in sql/schema.sql. */
public final class Models {
    private Models() {}

    public record Company(String name, String address, String email, String phone, String gstin) {}

    public record User(int id, String username, String fullName, String role, boolean active) {}

    /** balance = on the account's normal side; raw = total debit minus total credit. */
    public record Account(int id, String code, String name, String type, boolean cash, boolean system,
                          BigDecimal balance, BigDecimal raw) {}

    public record Party(int id, String name, String kind, String address, String gstin, String phone,
                        String email, BigDecimal outstanding) {}

    public record LineInput(int accountId, BigDecimal debit, BigDecimal credit) {}

    public record JournalLine(int accountId, String accountCode, String accountName,
                              BigDecimal debit, BigDecimal credit) {}

    /** locked = created by an invoice or bill, so it can only be changed through that document. */
    public record JournalEntry(int id, int entryNo, LocalDate date, String narration, String source,
                               String refNo, boolean locked, List<JournalLine> lines) {}

    public record InvoiceItem(String description, BigDecimal qty, BigDecimal rate, BigDecimal amount) {}

    public record Invoice(int id, String kind, String docNo, LocalDate date, int partyId, String partyName,
                          String partyAddress, String partyGstin, int accountId, String accountName,
                          BigDecimal gstRate, BigDecimal subTotal, BigDecimal tax, BigDecimal total,
                          String status, LocalDate paidOn, String notes, List<InvoiceItem> items) {}

    public record AuditEntry(String userName, String action, String details, String at) {}
}
