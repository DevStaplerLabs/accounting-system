package com.staplerlabs.accounting.service;

import com.staplerlabs.accounting.db.Sql;
import com.staplerlabs.accounting.model.Models.InvoiceItem;
import com.staplerlabs.accounting.model.Models.LineInput;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

/** Default chart of accounts, company profile and (optionally) sample transactions for the current financial year. */
public final class DemoData {
    private DemoData() {}

    private static final String BY = "system";

    public static void ensureCompany(Connection c) throws SQLException {
        if (Sql.count(c, "SELECT COUNT(*) FROM company") == 0)
            Sql.update(c, "INSERT INTO company(id, name, address, email, phone, gstin) VALUES (1,?,?,?,?,?)",
                    "StaplerLabs Private Limited", "293, Lane-2, Westend Marg, Saket, New Delhi 110030",
                    "work@staplerlabs.com", "+91 82925 11007", "");
    }

    private static void acc(Connection c, String code, String name, String type, boolean cash, boolean system) throws SQLException {
        Sql.update(c, "INSERT INTO accounts(code, name, type, is_cash, is_system) VALUES (?,?,?,?,?)", code, name, type, cash, system);
    }

    private static int id(Connection c, String code) throws SQLException {
        return LedgerService.byCode(c, code).id();
    }

    private static BigDecimal r(long v) { return BigDecimal.valueOf(v); }

    private static void je(Connection c, LocalDate d, String nar, String drCode, String crCode, long amt) throws SQLException {
        LedgerService.post(c, d, nar, "JOURNAL", null,
                List.of(new LineInput(id(c, drCode), r(amt), null), new LineInput(id(c, crCode), null, r(amt))), BY);
    }

    public static void seed(Connection c, boolean withTransactions) throws SQLException {
        ensureCompany(c);
        acc(c, "1001", "Cash in Hand", "Asset", true, false);
        acc(c, "1002", "Bank Account", "Asset", true, false);
        acc(c, "1100", "Accounts Receivable (Debtors)", "Asset", false, true);
        acc(c, "1200", "Office Equipment", "Asset", false, false);
        acc(c, "1300", "GST Input Credit", "Asset", false, true);
        acc(c, "2001", "Accounts Payable (Creditors)", "Liability", false, true);
        acc(c, "2100", "GST Output Payable", "Liability", false, true);
        acc(c, "3001", "Capital Account", "Equity", false, false);
        acc(c, "4001", "Consulting Fees", "Income", false, false);
        acc(c, "4002", "Software Development Income", "Income", false, false);
        acc(c, "4100", "Interest Received", "Income", false, false);
        acc(c, "5001", "Salaries", "Expense", false, false);
        acc(c, "5002", "Office Rent", "Expense", false, false);
        acc(c, "5003", "Software Subscriptions", "Expense", false, false);
        acc(c, "5004", "Travel and Conveyance", "Expense", false, false);
        acc(c, "5005", "Electricity and Internet", "Expense", false, false);
        acc(c, "5006", "Bank Charges", "Expense", false, false);
        acc(c, "5007", "Printing and Stationery", "Expense", false, false);
        if (!withTransactions) return;

        LocalDate today = LocalDate.now(), fy = ReportService.fyStart(today);
        int c1 = PartyService.insert(c, "Demo Client One", "CUSTOMER", "Gurugram, Haryana", "", "", "");
        int c2 = PartyService.insert(c, "Demo Client Two", "CUSTOMER", "Noida, Uttar Pradesh", "", "", "");
        int c3 = PartyService.insert(c, "Demo Client Three", "CUSTOMER", "Jaipur, Rajasthan", "", "", "");
        int v1 = PartyService.insert(c, "Demo Cloud Services", "VENDOR", "Bengaluru, Karnataka", "", "", "");
        int v2 = PartyService.insert(c, "Demo Office Supplies", "VENDOR", "New Delhi", "", "", "");
        int bank = id(c, "1002");

        je(c, fy, "Capital introduced by promoters", "1002", "3001", 800000);
        je(c, fy.plusDays(4), "Laptops purchased for the team", "1200", "1002", 120000);
        je(c, fy.plusDays(9), "Cash withdrawn for office use", "1001", "1002", 20000);

        // Monthly running costs, April up to the current month
        for (LocalDate m = fy; !m.isAfter(today); m = m.plusMonths(1)) {
            String mon = m.getMonth().getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.ENGLISH);
            if (!m.withDayOfMonth(5).isAfter(today)) je(c, m.withDayOfMonth(5), "Office rent for " + mon, "5002", "1002", 40000);
            if (!m.withDayOfMonth(12).isAfter(today)) je(c, m.withDayOfMonth(12), "Electricity and internet for " + mon, "5005", "1002", 4200);
            if (!m.withDayOfMonth(18).isAfter(today)) je(c, m.withDayOfMonth(18), "Local travel for client visits", "5004", "1001", 1800);
            LocalDate end = m.withDayOfMonth(m.lengthOfMonth());
            if (!end.isAfter(today)) je(c, end, "Salaries for " + mon, "5001", "1002", 150000);
        }

        int inc1 = id(c, "4001"), inc2 = id(c, "4002"), soft = id(c, "5003"), stat = id(c, "5007");
        Object[][] sales = {
            {c1, inc1, 20L,  "Business process study and solution design", 1, 240000L, true},
            {c2, inc2, 45L,  "Appointment booking web app, phase 1", 1, 320000L, true},
            {c1, inc2, 80L,  "Internal dashboard for operations team", 1, 280000L, true},
            {c3, inc1, 110L, "Process review workshops", 3, 60000L, true},
            {c2, inc2, 150L, "Appointment booking web app, phase 2", 1, 340000L, true},
            {c3, inc2, 190L, "Inventory tracking tool", 1, 300000L, false},
            {c1, inc1, 215L, "Monthly advisory retainer", 1, 120000L, false},
        };
        for (Object[] s : sales) {
            LocalDate d = fy.plusDays((Long) s[2]);
            if (d.isAfter(today)) continue;
            int inv = InvoiceService.create(c, "SALE", d, (Integer) s[0], (Integer) s[1], 18, null,
                    List.of(new InvoiceItem((String) s[3], r((Integer) s[4]), r((Long) s[5]), null)), BY);
            if ((Boolean) s[6] && !d.plusDays(20).isAfter(today)) InvoiceService.pay(c, inv, bank, d.plusDays(20), BY);
        }

        Object[][] bills = {
            {v1, soft, 15L, "Cloud hosting and design tools, quarter 1", 1, 24000L, true},
            {v2, stat, 60L, "Stationery and printer cartridges", 1, 6500L, true},
            {v1, soft, 105L, "Cloud hosting and design tools, quarter 2", 1, 24000L, true},
            {v1, soft, 195L, "Cloud hosting and design tools, quarter 3", 1, 26000L, false},
        };
        for (Object[] b : bills) {
            LocalDate d = fy.plusDays((Long) b[2]);
            if (d.isAfter(today)) continue;
            int bill = InvoiceService.create(c, "PURCHASE", d, (Integer) b[0], (Integer) b[1], 18, null,
                    List.of(new InvoiceItem((String) b[3], r((Integer) b[4]), r((Long) b[5]), null)), BY);
            if ((Boolean) b[6] && !d.plusDays(10).isAfter(today)) InvoiceService.pay(c, bill, bank, d.plusDays(10), BY);
        }
    }
}
