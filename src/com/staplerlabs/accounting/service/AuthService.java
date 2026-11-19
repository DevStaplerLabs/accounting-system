package com.staplerlabs.accounting.service;

import com.staplerlabs.accounting.AppException;
import com.staplerlabs.accounting.db.Database;
import com.staplerlabs.accounting.db.Sql;
import com.staplerlabs.accounting.model.Models.User;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Sign-in, sessions and users. Passwords are stored as salted PBKDF2 hashes, never as plain text. */
public class AuthService {
    public static final Set<String> ROLES = Set.of("ADMIN", "ACCOUNTANT", "VIEWER");
    private static final long SESSION_MS = 8L * 60 * 60 * 1000;

    public record Session(int userId, String username, String fullName, String role, long expires) {
        public boolean admin() { return role.equals("ADMIN"); }
        public boolean canWrite() { return !role.equals("VIEWER"); }
    }

    private final Database db;
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();

    public AuthService(Database db) { this.db = db; }

    private String randomToken(int bytes) {
        byte[] b = new byte[bytes];
        random.nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    static String hash(String password, String salt) {
        try {
            PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), Base64.getDecoder().decode(salt), 65536, 256);
            byte[] h = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
            return Base64.getEncoder().encodeToString(h);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private String newSalt() {
        byte[] b = new byte[16];
        random.nextBytes(b);
        return Base64.getEncoder().encodeToString(b);
    }

    /** Creates the first administrator when the users table is empty. */
    public void ensureAdmin(String username, String password) {
        db.tx(c -> {
            if (Sql.count(c, "SELECT COUNT(*) FROM users") == 0) {
                String salt = newSalt();
                Sql.insert(c, "INSERT INTO users(username, full_name, password_hash, salt, role) VALUES (?,?,?,?, 'ADMIN')",
                        username, "Administrator", hash(password, salt), salt);
                System.out.println("Created first user '" + username + "' (see config/app.properties).");
            }
            return null;
        });
    }

    public String login(String username, String password) {
        record Row(int id, String username, String fullName, String hash, String salt, String role, boolean active) {}
        Row r = db.tx(c -> Sql.one(c, "SELECT * FROM users WHERE username = ?", rs -> new Row(rs.getInt("id"),
                rs.getString("username"), rs.getString("full_name"), rs.getString("password_hash"), rs.getString("salt"),
                rs.getString("role"), rs.getBoolean("active")), username.trim()));
        if (r == null || !MessageDigest.isEqual(hash(password, r.salt()).getBytes(), r.hash().getBytes()))
            throw new AppException(401, "Incorrect username or password.");
        if (!r.active()) throw new AppException(403, "This user has been deactivated. Ask the administrator.");
        String token = randomToken(32);
        sessions.put(token, new Session(r.id(), r.username(), r.fullName(), r.role(), System.currentTimeMillis() + SESSION_MS));
        db.tx(c -> { Audit.log(c, r.username(), "LOGIN", ""); return null; });
        return token;
    }

    public Session session(String token) {
        if (token == null) return null;
        Session s = sessions.get(token);
        if (s == null) return null;
        if (s.expires() < System.currentTimeMillis()) { sessions.remove(token); return null; }
        Session renewed = new Session(s.userId(), s.username(), s.fullName(), s.role(), System.currentTimeMillis() + SESSION_MS);
        sessions.put(token, renewed);
        return renewed;
    }

    public void logout(String token) { if (token != null) sessions.remove(token); }

    private static void checkStrength(String password) {
        if (password.length() < 8) throw new AppException("The new password must be at least 8 characters.");
        if (!password.matches(".*[A-Za-z].*") || !password.matches(".*\\d.*"))
            throw new AppException("The new password must contain letters and numbers.");
    }

    public void changePassword(int userId, String oldPassword, String newPassword) {
        checkStrength(newPassword);
        db.tx(c -> {
            String[] row = Sql.one(c, "SELECT password_hash, salt, username FROM users WHERE id = ?",
                    rs -> new String[]{rs.getString(1), rs.getString(2), rs.getString(3)}, userId);
            if (row == null || !hash(oldPassword, row[1]).equals(row[0])) throw new AppException("The current password is not correct.");
            String salt = newSalt();
            Sql.update(c, "UPDATE users SET password_hash = ?, salt = ? WHERE id = ?", hash(newPassword, salt), salt, userId);
            Audit.log(c, row[2], "PASSWORD_CHANGE", "");
            return null;
        });
    }

    public List<User> users() {
        return db.tx(c -> Sql.list(c, "SELECT * FROM users ORDER BY username", rs -> new User(rs.getInt("id"),
                rs.getString("username"), rs.getString("full_name"), rs.getString("role"), rs.getBoolean("active"))));
    }

    public void createUser(String username, String fullName, String role, String password, String by) {
        if (!username.matches("[a-zA-Z0-9._]{3,30}")) throw new AppException("Username: 3 to 30 letters, numbers, dots or underscores.");
        if (fullName.isBlank()) throw new AppException("Full name is required.");
        if (!ROLES.contains(role)) throw new AppException("Choose a valid role.");
        checkStrength(password);
        db.tx(c -> {
            if (Sql.count(c, "SELECT COUNT(*) FROM users WHERE username = ?", username) > 0)
                throw new AppException("Username " + username + " is already taken.");
            String salt = newSalt();
            Sql.insert(c, "INSERT INTO users(username, full_name, password_hash, salt, role) VALUES (?,?,?,?,?)",
                    username, fullName.trim(), hash(password, salt), salt, role);
            Audit.log(c, by, "USER_CREATE", username + " as " + role);
            return null;
        });
    }

    public void setActive(int id, boolean active, Session by) {
        if (id == by.userId()) throw new AppException("You cannot deactivate your own user.");
        db.tx(c -> {
            if (Sql.update(c, "UPDATE users SET active = ? WHERE id = ?", active, id) == 0) throw new AppException(404, "User not found.");
            Audit.log(c, by.username(), active ? "USER_ACTIVATE" : "USER_DEACTIVATE", "user id " + id);
            return null;
        });
        if (!active) sessions.values().removeIf(s -> s.userId() == id);
    }
}
