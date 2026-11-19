package com.staplerlabs.accounting.service;

import com.staplerlabs.accounting.AppException;
import com.staplerlabs.accounting.db.Database;
import com.staplerlabs.accounting.db.Sql;
import com.staplerlabs.accounting.model.Models.AuditEntry;
import com.staplerlabs.accounting.model.Models.Company;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/** Company profile, audit log and data reset. */
public class CompanyService {
    private final Database db;

    public CompanyService(Database db) { this.db = db; }

    public Company company() {
        return db.tx(c -> Sql.one(c, "SELECT * FROM company WHERE id = 1", rs -> new Company(rs.getString("name"),
                rs.getString("address"), rs.getString("email"), rs.getString("phone"), rs.getString("gstin"))));
    }

    public Company update(String name, String address, String email, String phone, String gstin, String by) {
        if (name.isBlank()) throw new AppException("Company name is required.");
        if (!gstin.isBlank() && !gstin.toUpperCase().matches("[0-9]{2}[A-Z0-9]{13}"))
            throw new AppException("GSTIN should be 15 characters, starting with the 2-digit state code.");
        db.tx(c -> {
            Sql.update(c, "UPDATE company SET name=?, address=?, email=?, phone=?, gstin=? WHERE id = 1",
                    name.trim(), address, email, phone, gstin.toUpperCase());
            Audit.log(c, by, "COMPANY_UPDATE", name);
            return null;
        });
        return company();
    }

    public List<AuditEntry> audit(int limit) {
        return db.tx(c -> Sql.list(c, "SELECT * FROM audit_log ORDER BY id DESC LIMIT " + Math.max(1, Math.min(limit, 500)),
                rs -> new AuditEntry(rs.getString("user_name"), rs.getString("action"), rs.getString("details"),
                        String.valueOf(rs.getTimestamp("created_at")).replaceAll("\\.\\d+$", ""))));
    }

    /** Clears all transactions. With demo = true the sample data is loaded again. Users and company stay. */
    public void reset(boolean demo, String by) {
        db.tx(c -> {
            clear(c);
            DemoData.seed(c, demo);
            Audit.log(c, by, "DATA_RESET", demo ? "demo data loaded" : "empty books");
            return null;
        });
    }

    static void clear(Connection c) throws SQLException {
        for (String t : List.of("invoice_items", "invoices", "journal_lines", "journal_entries", "parties", "accounts"))
            Sql.update(c, "DELETE FROM " + t);
    }
}
