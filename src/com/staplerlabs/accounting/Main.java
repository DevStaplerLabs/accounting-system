package com.staplerlabs.accounting;

import com.staplerlabs.accounting.db.Database;
import com.staplerlabs.accounting.db.Sql;
import com.staplerlabs.accounting.service.*;
import com.staplerlabs.accounting.web.ApiServer;

import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;

/** Entry point: reads config, prepares the database, starts the web server. */
public class Main {
    public static void main(String[] args) throws Exception {
        Path base = Path.of("").toAbsolutePath();
        Properties config = new Properties();
        try (Reader in = Files.newBufferedReader(base.resolve("config/app.properties"))) {
            config.load(in);
        }

        Database db = new Database(config);
        System.out.println("StaplerLabs Accounting System");
        System.out.println("Database : " + db.type + "  (" + db.url.replaceAll("\\?.*", "") + ")");
        try (Connection c = db.connect()) {
            System.out.println("Connected: " + c.getMetaData().getDatabaseProductName() + " " + c.getMetaData().getDatabaseProductVersion());
        } catch (SQLException e) {
            System.err.println("\nCould not connect to the database: " + e.getMessage());
            if (db.type.equals("mysql"))
                System.err.println("Check that MySQL is running and that mysql.user and mysql.password in config/app.properties are correct.");
            System.exit(1);
        }

        db.runScript(base.resolve("sql/schema.sql"));

        AuthService auth = new AuthService(db);
        LedgerService ledger = new LedgerService(db);
        PartyService parties = new PartyService(db);
        InvoiceService invoices = new InvoiceService(db);
        ReportService reports = new ReportService(db);
        CompanyService company = new CompanyService(db);

        auth.ensureAdmin(config.getProperty("admin.username", "admin").trim(),
                config.getProperty("admin.default.password", "admin123").trim());
        db.tx(c -> {
            DemoData.ensureCompany(c);
            if (Sql.count(c, "SELECT COUNT(*) FROM accounts") == 0) {
                System.out.println("Empty database: loading default accounts and demo data...");
                DemoData.seed(c, true);
            }
            return null;
        });

        int port = Integer.parseInt(config.getProperty("server.port", "8080").trim());
        new ApiServer(base.resolve("web"), auth, ledger, parties, invoices, reports, company).start(port);
        System.out.println("\nReady. Open http://localhost:" + port + " in your browser.  (Ctrl+C to stop)");
    }
}
