package com.staplerlabs.accounting.service;

import com.staplerlabs.accounting.db.Sql;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.SQLException;
import java.text.NumberFormat;
import java.util.Locale;

/** Audit trail and shared money helpers. */
public final class Audit {
    private Audit() {}

    public static void log(Connection c, String by, String action, String details) throws SQLException {
        String d = details == null ? "" : details;
        Sql.update(c, "INSERT INTO audit_log(user_name, action, details) VALUES (?,?,?)",
                by == null ? "system" : by, action, d.length() > 250 ? d.substring(0, 250) : d);
    }

    public static BigDecimal money(BigDecimal v) {
        return (v == null ? BigDecimal.ZERO : v).setScale(2, RoundingMode.HALF_UP);
    }

    /** Indian number format, for example Rs. 1,25,000.00 */
    public static String fmt(BigDecimal v) {
        NumberFormat f = NumberFormat.getNumberInstance(Locale.of("en", "IN"));
        f.setMinimumFractionDigits(2);
        f.setMaximumFractionDigits(2);
        return "Rs. " + f.format(money(v));
    }
}
