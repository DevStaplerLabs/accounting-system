package com.staplerlabs.accounting.db;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Small JDBC helper. Every query uses PreparedStatement parameters (no string-built SQL values). */
public final class Sql {
    private Sql() {}

    @FunctionalInterface
    public interface Mapper<T> { T map(ResultSet rs) throws SQLException; }

    private static PreparedStatement prepare(Connection c, String sql, boolean keys, Object... params) throws SQLException {
        PreparedStatement ps = keys ? c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS) : c.prepareStatement(sql);
        for (int i = 0; i < params.length; i++) ps.setObject(i + 1, params[i]);
        return ps;
    }

    public static <T> List<T> list(Connection c, String sql, Mapper<T> mapper, Object... params) throws SQLException {
        try (PreparedStatement ps = prepare(c, sql, false, params); ResultSet rs = ps.executeQuery()) {
            List<T> out = new ArrayList<>();
            while (rs.next()) out.add(mapper.map(rs));
            return out;
        }
    }

    public static <T> T one(Connection c, String sql, Mapper<T> mapper, Object... params) throws SQLException {
        List<T> rows = list(c, sql, mapper, params);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public static int count(Connection c, String sql, Object... params) throws SQLException {
        return one(c, sql, rs -> rs.getInt(1), params);
    }

    public static int update(Connection c, String sql, Object... params) throws SQLException {
        try (PreparedStatement ps = prepare(c, sql, false, params)) {
            return ps.executeUpdate();
        }
    }

    /** Inserts a row and returns its AUTO_INCREMENT id. */
    public static int insert(Connection c, String sql, Object... params) throws SQLException {
        try (PreparedStatement ps = prepare(c, sql, true, params)) {
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    public static BigDecimal money(ResultSet rs, String col) throws SQLException {
        BigDecimal b = rs.getBigDecimal(col);
        return (b == null ? BigDecimal.ZERO : b).setScale(2, RoundingMode.HALF_UP);
    }

    public static LocalDate date(ResultSet rs, String col) throws SQLException {
        return rs.getObject(col, LocalDate.class);
    }
}
