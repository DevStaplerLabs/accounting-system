package com.staplerlabs.accounting.db;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;

/** Opens JDBC connections (MySQL or H2) and runs work inside a transaction. */
public class Database {
    public final String type;
    public final String url;
    private final String user;
    private final String password;

    public Database(Properties p) {
        type = p.getProperty("db.type", "h2").trim().toLowerCase();
        if (type.equals("mysql")) {
            url = p.getProperty("mysql.url").trim();
            user = p.getProperty("mysql.user", "root").trim();
            password = p.getProperty("mysql.password", "");
        } else {
            url = p.getProperty("h2.url", "jdbc:h2:./data/accounting;MODE=MySQL;DATABASE_TO_LOWER=TRUE").trim();
            user = "sa";
            password = "";
        }
    }

    public Connection connect() throws SQLException {
        return DriverManager.getConnection(url, user, password);
    }

    /** Runs a .sql file statement by statement (statements are separated by semicolons). */
    public void runScript(Path file) throws Exception {
        String sql = Files.readString(file).replaceAll("(?m)^\\s*--.*$", "");
        try (Connection c = connect(); Statement st = c.createStatement()) {
            for (String part : sql.split(";")) {
                if (!part.isBlank()) st.execute(part);
            }
        }
    }

    @FunctionalInterface
    public interface Work<T> { T run(Connection c) throws Exception; }

    /** Runs the work in one transaction: commit on success, roll back on any error. */
    public <T> T tx(Work<T> work) {
        try (Connection c = connect()) {
            c.setAutoCommit(false);
            try {
                T result = work.run(c);
                c.commit();
                return result;
            } catch (Exception e) {
                c.rollback();
                throw e;
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }
}
