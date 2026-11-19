package com.staplerlabs.accounting.web;

import com.staplerlabs.accounting.AppException;
import com.staplerlabs.accounting.model.Models.InvoiceItem;
import com.staplerlabs.accounting.model.Models.LineInput;
import com.staplerlabs.accounting.service.*;
import com.staplerlabs.accounting.service.AuthService.Session;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** REST API (JSON over HTTP) plus static file serving for the web front end. */
public class ApiServer {

    @FunctionalInterface
    interface Handler { Object handle(Req r) throws Exception; }

    private record Route(String method, Pattern path, Handler handler) {}

    /** One API request: path parameters, query string, JSON body and the signed-in user. */
    static final class Req {
        final HttpExchange ex;
        final Matcher match;
        final Map<String, String> query;
        final Map<String, Object> body;
        final Session user;

        Req(HttpExchange ex, Matcher match, Map<String, String> query, Map<String, Object> body, Session user) {
            this.ex = ex; this.match = match; this.query = query; this.body = body; this.user = user;
        }

        int pathId() { return Integer.parseInt(match.group(1)); }

        String str(String key) { Object v = body.get(key); return v == null ? "" : v.toString().trim(); }

        boolean bool(String key) { Object v = body.get(key); return v instanceof Boolean b ? b : "true".equals(String.valueOf(v)); }

        int integer(String key) {
            String v = str(key);
            if (v.isEmpty()) return 0;
            try { return new BigDecimal(v).intValueExact(); } catch (Exception e) { throw new AppException("Invalid number for " + key + "."); }
        }

        static BigDecimal money(Object o) {
            if (o == null || o.toString().isBlank()) return BigDecimal.ZERO;
            try { return new BigDecimal(o.toString().trim()); } catch (NumberFormatException e) { throw new AppException("Enter amounts as numbers."); }
        }

        BigDecimal money(String key) { return money(body.get(key)); }

        static LocalDate parseDate(String v) {
            if (v == null || v.isBlank()) return null;
            try { return LocalDate.parse(v.trim()); } catch (DateTimeParseException e) { throw new AppException("Use a valid date (YYYY-MM-DD)."); }
        }

        LocalDate date(String key) { return parseDate(str(key)); }

        LocalDate q(String key, LocalDate fallback) { LocalDate d = parseDate(query.get(key)); return d == null ? fallback : d; }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> list(String key) {
            Object v = body.get(key);
            if (!(v instanceof List<?> l)) return List.of();
            List<Map<String, Object>> out = new ArrayList<>();
            for (Object o : l) if (o instanceof Map<?, ?> m) out.add((Map<String, Object>) m);
            return out;
        }

        String by() { return user == null ? null : user.username(); }

        void admin() { if (user == null || !user.admin()) throw new AppException(403, "Only an administrator can do this."); }
    }

    private final List<Route> routes = new ArrayList<>();
    private final Path webRoot;
    private final AuthService auth;

    public ApiServer(Path webRoot, AuthService auth, LedgerService ledger, PartyService parties, InvoiceService invoices,
                     ReportService reports, CompanyService company) {
        this.webRoot = webRoot.toAbsolutePath().normalize();
        this.auth = auth;

        /* ---- Auth ---- */
        route("POST", "/api/login", r -> {
            String token = auth.login(r.str("username"), r.str("password"));
            r.ex.getResponseHeaders().add("Set-Cookie", "sid=" + token + "; Path=/; HttpOnly; SameSite=Strict");
            return auth.session(token);
        });
        route("POST", "/api/logout", r -> {
            auth.logout(cookie(r.ex));
            r.ex.getResponseHeaders().add("Set-Cookie", "sid=; Path=/; Max-Age=0; HttpOnly; SameSite=Strict");
            return true;
        });
        route("GET", "/api/me", r -> r.user);
        route("POST", "/api/change-password", r -> { auth.changePassword(r.user.userId(), r.str("oldPassword"), r.str("newPassword")); return true; });

        /* ---- Users (admin) ---- */
        route("GET", "/api/users", r -> { r.admin(); return auth.users(); });
        route("POST", "/api/users", r -> { r.admin(); auth.createUser(r.str("username"), r.str("fullName"), r.str("role"), r.str("password"), r.by()); return auth.users(); });
        route("PUT", "/api/users/(\\d+)/active", r -> { r.admin(); auth.setActive(r.pathId(), r.bool("active"), r.user); return auth.users(); });

        /* ---- Company, audit, reset ---- */
        route("GET", "/api/company", r -> company.company());
        route("PUT", "/api/company", r -> { r.admin(); return company.update(r.str("name"), r.str("address"), r.str("email"), r.str("phone"), r.str("gstin"), r.by()); });
        route("GET", "/api/audit", r -> { r.admin(); return company.audit(200); });
        route("POST", "/api/reset", r -> { r.admin(); company.reset(r.bool("demo"), r.by()); return true; });

        /* ---- Accounts ---- */
        route("GET", "/api/accounts", r -> ledger.accounts());
        route("POST", "/api/accounts", r -> ledger.createAccount(r.str("code"), r.str("name"), r.str("type"), r.bool("cash"), r.by()));
        route("PUT", "/api/accounts/(\\d+)", r -> { ledger.renameAccount(r.pathId(), r.str("name"), r.by()); return true; });
        route("DELETE", "/api/accounts/(\\d+)", r -> { ledger.deleteAccount(r.pathId(), r.by()); return true; });

        /* ---- Parties ---- */
        route("GET", "/api/parties", r -> parties.list(r.query.get("kind")));
        route("POST", "/api/parties", r -> parties.create(r.str("name"), r.str("kind"), r.str("address"), r.str("gstin"), r.str("phone"), r.str("email"), r.by()));
        route("PUT", "/api/parties/(\\d+)", r -> parties.update(r.pathId(), r.str("name"), r.str("address"), r.str("gstin"), r.str("phone"), r.str("email"), r.by()));
        route("DELETE", "/api/parties/(\\d+)", r -> { parties.delete(r.pathId(), r.by()); return true; });

        /* ---- Journal ---- */
        route("GET", "/api/entries", r -> ledger.entries(r.q("from", null), r.q("to", null), r.query.get("q")));
        route("POST", "/api/entries", r -> {
            List<LineInput> lines = new ArrayList<>();
            for (Map<String, Object> l : r.list("lines")) {
                Object acc = l.get("accountId");
                int accId = acc == null || acc.toString().isBlank() ? 0 : new BigDecimal(acc.toString()).intValue();
                lines.add(new LineInput(accId, Req.money(l.get("debit")), Req.money(l.get("credit"))));
            }
            return ledger.postJournal(r.date("date"), r.str("narration"), lines, r.by());
        });
        route("POST", "/api/vouchers", r -> ledger.postVoucher(r.str("type"), r.date("date"), r.integer("cashAccountId"),
                r.integer("accountId"), r.money("amount"), r.str("narration"), r.by()));
        route("DELETE", "/api/entries/(\\d+)", r -> { ledger.deleteEntry(r.pathId(), r.by()); return true; });

        /* ---- Invoices and bills ---- */
        route("GET", "/api/invoices", r -> invoices.list(r.query.getOrDefault("kind", "SALE")));
        route("GET", "/api/invoices/(\\d+)", r -> invoices.get(r.pathId()));
        route("POST", "/api/invoices", r -> {
            List<InvoiceItem> items = new ArrayList<>();
            for (Map<String, Object> it : r.list("items")) {
                Object d = it.get("description");
                items.add(new InvoiceItem(d == null ? "" : d.toString(), Req.money(it.get("qty")), Req.money(it.get("rate")), null));
            }
            return invoices.create(r.str("kind").toUpperCase(), r.date("date"), r.integer("partyId"), r.integer("accountId"),
                    r.integer("gstRate"), r.str("notes"), items, r.by());
        });
        route("POST", "/api/invoices/(\\d+)/pay", r -> invoices.pay(r.pathId(), r.integer("cashAccountId"), r.date("date"), r.by()));
        route("POST", "/api/invoices/(\\d+)/cancel", r -> invoices.cancel(r.pathId(), r.by()));

        /* ---- Reports ---- */
        route("GET", "/api/reports/dashboard", r -> reports.dashboard());
        route("GET", "/api/reports/trial-balance", r -> reports.trialBalance(r.q("asOn", LocalDate.now())));
        route("GET", "/api/reports/profit-loss", r -> reports.profitAndLoss(r.q("from", ReportService.fyStart(LocalDate.now())), r.q("to", LocalDate.now())));
        route("GET", "/api/reports/balance-sheet", r -> reports.balanceSheet(r.q("asOn", LocalDate.now())));
        route("GET", "/api/reports/ledger", r -> reports.ledger(Integer.parseInt(r.query.getOrDefault("accountId", "0")),
                r.q("from", ReportService.fyStart(LocalDate.now())), r.q("to", LocalDate.now())));
        route("GET", "/api/reports/gst", r -> reports.gstSummary(r.q("from", ReportService.fyStart(LocalDate.now())), r.q("to", LocalDate.now())));
    }

    private void route(String method, String path, Handler h) {
        routes.add(new Route(method, Pattern.compile("^" + path + "$"), h));
    }

    public void start(int port) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        server.createContext("/", this::handle);
        server.setExecutor(Executors.newFixedThreadPool(8));
        server.start();
    }

    private static String cookie(HttpExchange ex) {
        for (String header : ex.getRequestHeaders().getOrDefault("Cookie", List.of()))
            for (String part : header.split(";")) {
                String p = part.trim();
                if (p.startsWith("sid=")) return p.substring(4);
            }
        return null;
    }

    private static Map<String, String> parseQuery(String q) {
        Map<String, String> m = new HashMap<>();
        if (q == null) return m;
        for (String pair : q.split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0) m.put(URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8),
                    URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8));
        }
        return m;
    }

    private void handle(HttpExchange ex) throws IOException {
        try {
            String path = ex.getRequestURI().getPath();
            if (path.startsWith("/api/")) api(ex, path);
            else file(ex, path);
        } catch (Exception e) {
            e.printStackTrace();
            send(ex, 500, "application/json", Json.write(Map.of("ok", false, "error", "Something went wrong on the server.")).getBytes(StandardCharsets.UTF_8));
        } finally {
            ex.close();
        }
    }

    private void api(HttpExchange ex, String path) throws IOException {
        String method = ex.getRequestMethod();
        int status = 200;
        Map<String, Object> out = new LinkedHashMap<>();
        try {
            Route route = null;
            Matcher m = null;
            boolean pathFound = false;
            for (Route r : routes) {
                Matcher mm = r.path().matcher(path);
                if (mm.matches()) {
                    pathFound = true;
                    if (r.method().equals(method)) { route = r; m = mm; break; }
                }
            }
            if (route == null) throw new AppException(pathFound ? 405 : 404, pathFound ? "Method not allowed." : "Not found.");

            Session user = auth.session(cookie(ex));
            boolean open = path.equals("/api/login");
            if (!open && user == null) throw new AppException(401, "Please sign in.");
            boolean selfService = path.equals("/api/logout") || path.equals("/api/change-password");
            if (!method.equals("GET") && user != null && !user.canWrite() && !selfService)
                throw new AppException(403, "Your role is view-only. Ask an administrator for access.");

            String raw = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            Object parsed = Json.parse(raw);
            @SuppressWarnings("unchecked")
            Map<String, Object> body = parsed instanceof Map<?, ?> ? (Map<String, Object>) parsed : new HashMap<>();

            Object data = route.handler().handle(new Req(ex, m, parseQuery(ex.getRequestURI().getRawQuery()), body, user));
            out.put("ok", true);
            out.put("data", data);
        } catch (AppException e) {
            status = e.status;
            out.put("ok", false);
            out.put("error", e.getMessage());
        } catch (IllegalArgumentException e) {
            status = 400;
            out.put("ok", false);
            out.put("error", e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            status = 500;
            out.put("ok", false);
            out.put("error", "Server error: " + e.getMessage());
        }
        ex.getResponseHeaders().add("Cache-Control", "no-store");
        send(ex, status, "application/json; charset=utf-8", Json.write(out).getBytes(StandardCharsets.UTF_8));
    }

    private static final Map<String, String> TYPES = Map.of(
            "html", "text/html; charset=utf-8", "css", "text/css; charset=utf-8", "js", "text/javascript; charset=utf-8",
            "svg", "image/svg+xml", "png", "image/png", "ico", "image/x-icon", "json", "application/json");

    private void file(HttpExchange ex, String path) throws IOException {
        if (path.equals("/")) path = "/index.html";
        Path f = webRoot.resolve(path.substring(1)).normalize();
        if (!f.startsWith(webRoot) || !Files.isRegularFile(f)) {
            send(ex, 404, "text/plain", "Not found".getBytes(StandardCharsets.UTF_8));
            return;
        }
        String name = f.getFileName().toString();
        String ext = name.substring(name.lastIndexOf('.') + 1).toLowerCase();
        ex.getResponseHeaders().add("Cache-Control", "no-cache");
        send(ex, 200, TYPES.getOrDefault(ext, "application/octet-stream"), Files.readAllBytes(f));
    }

    private static void send(HttpExchange ex, int status, String type, byte[] bytes) throws IOException {
        ex.getResponseHeaders().set("Content-Type", type);
        ex.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
    }
}
