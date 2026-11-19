package com.staplerlabs.accounting.service;

import com.staplerlabs.accounting.AppException;
import com.staplerlabs.accounting.db.Database;
import com.staplerlabs.accounting.db.Sql;
import com.staplerlabs.accounting.model.Models.Party;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/** Customers and vendors. */
public class PartyService {
    private final Database db;

    public PartyService(Database db) { this.db = db; }

    private static final String SELECT = """
        SELECT p.*, COALESCE((SELECT SUM(i.total) FROM invoices i
                              WHERE i.party_id = p.id AND i.status = 'UNPAID'), 0) AS outstanding
        FROM parties p""";

    private static final Sql.Mapper<Party> MAP = rs -> new Party(rs.getInt("id"), rs.getString("name"), rs.getString("kind"),
            rs.getString("address"), rs.getString("gstin"), rs.getString("phone"), rs.getString("email"), Sql.money(rs, "outstanding"));

    public List<Party> list(String kind) {
        return db.tx(c -> kind == null || kind.isBlank()
                ? Sql.list(c, SELECT + " ORDER BY p.name", MAP)
                : Sql.list(c, SELECT + " WHERE p.kind = ? ORDER BY p.name", MAP, kind.toUpperCase()));
    }

    public static Party find(Connection c, int id) throws SQLException {
        return Sql.one(c, SELECT + " WHERE p.id = ?", MAP, id);
    }

    public static int insert(Connection c, String name, String kind, String address, String gstin, String phone, String email) throws SQLException {
        return Sql.insert(c, "INSERT INTO parties(name, kind, address, gstin, phone, email) VALUES (?,?,?,?,?,?)",
                name.trim(), kind, address, gstin, phone, email);
    }

    private static void validate(String name, String kind, String gstin, String email) {
        if (name.isBlank()) throw new AppException("Name is required.");
        if (!"CUSTOMER".equals(kind) && !"VENDOR".equals(kind)) throw new AppException("Choose customer or vendor.");
        if (!gstin.isBlank() && !gstin.toUpperCase().matches("[0-9]{2}[A-Z0-9]{13}"))
            throw new AppException("GSTIN should be 15 characters, starting with the 2-digit state code.");
        if (!email.isBlank() && !email.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) throw new AppException("Enter a valid email address.");
    }

    public Party create(String name, String kind, String address, String gstin, String phone, String email, String by) {
        String k = kind.toUpperCase();
        validate(name, k, gstin, email);
        return db.tx(c -> {
            int id = insert(c, name, k, address, gstin.toUpperCase(), phone, email);
            Audit.log(c, by, "PARTY_CREATE", k + " " + name);
            return find(c, id);
        });
    }

    public Party update(int id, String name, String address, String gstin, String phone, String email, String by) {
        return db.tx(c -> {
            Party p = find(c, id);
            if (p == null) throw new AppException(404, "Party not found.");
            validate(name, p.kind(), gstin, email);
            Sql.update(c, "UPDATE parties SET name=?, address=?, gstin=?, phone=?, email=? WHERE id=?",
                    name.trim(), address, gstin.toUpperCase(), phone, email, id);
            Audit.log(c, by, "PARTY_UPDATE", name);
            return find(c, id);
        });
    }

    public void delete(int id, String by) {
        db.tx(c -> {
            Party p = find(c, id);
            if (p == null) throw new AppException(404, "Party not found.");
            if (Sql.count(c, "SELECT COUNT(*) FROM invoices WHERE party_id = ?", id) > 0)
                throw new AppException(p.name() + " has invoices or bills. It cannot be deleted.");
            Sql.update(c, "DELETE FROM parties WHERE id = ?", id);
            Audit.log(c, by, "PARTY_DELETE", p.name());
            return null;
        });
    }
}
