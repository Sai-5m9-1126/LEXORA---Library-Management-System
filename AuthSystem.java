package lexora.auth;

import lexora.model.Library;
import lexora.model.User;
import lexora.util.ActivityLogger;
import lexora.util.LexoraUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * AuthSystem
 * ───────────
 * Handles login, logout, and session tracking.
 */
public class AuthSystem {

    private static User     currentUser   = null;
    private static String   sessionStart  = null;
    private static final List<String> sessionHistory = new ArrayList<>();

    private final Library lib = Library.getInstance();

    // ── Login ─────────────────────────────────────────────────────────────
    public User login(String userId, String plainPassword) {
        User user = lib.getUser(userId);
        if (user == null) {
            ActivityLogger.warn("LOGIN_FAIL | userId=" + userId + " | reason=NOT_FOUND");
            return null;
        }
        if (!user.isActive()) {
            ActivityLogger.warn("LOGIN_FAIL | userId=" + userId + " | reason=INACTIVE");
            return null;
        }
        if (!LexoraUtils.checkPassword(plainPassword, user.getPasswordHash())) {
            ActivityLogger.warn("LOGIN_FAIL | userId=" + userId + " | reason=WRONG_PASSWORD");
            return null;
        }

        currentUser  = user;
        sessionStart = LexoraUtils.today();
        ActivityLogger.setCurrentUser(userId);
        ActivityLogger.audit("LOGIN | userId=" + userId + " | role=" + user.getRole());

        sessionHistory.add("Logged in as " + userId + " at " + LexoraUtils.today());
        return user;
    }

    // ── Logout ────────────────────────────────────────────────────────────
    public void logout() {
        if (currentUser != null) {
            ActivityLogger.audit("LOGOUT | userId=" + currentUser.getUserId());
            sessionHistory.add("Logged out: " + currentUser.getUserId());
            currentUser = null;
            sessionStart = null;
            ActivityLogger.setCurrentUser("SYSTEM");
        }
    }

    // ── Session ───────────────────────────────────────────────────────────
    public static User getCurrentUser()        { return currentUser; }
    public static boolean isLoggedIn()         { return currentUser != null; }
    public static boolean isAdmin()            { return isLoggedIn() && currentUser.getRole() == User.Role.ADMIN; }
    public static boolean isLibrarian()        { return isLoggedIn() && (currentUser.getRole() == User.Role.LIBRARIAN || isAdmin()); }
    public static List<String> getSessionHistory() { return sessionHistory; }

    public static void requireAuth() {
        if (!isLoggedIn()) throw new SecurityException("Not logged in. Please authenticate first.");
    }

    public static void requireLibrarian() {
        requireAuth();
        if (!isLibrarian()) throw new SecurityException("Librarian access required.");
    }

    public static void requireAdmin() {
        requireAuth();
        if (!isAdmin()) throw new SecurityException("Admin access required.");
    }
}
