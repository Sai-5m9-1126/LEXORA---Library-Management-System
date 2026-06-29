package lexora.ui;

import lexora.auth.AuthSystem;
import lexora.model.Book;
import lexora.model.Library;
import lexora.model.Transaction;
import lexora.model.User;
import lexora.util.LexoraUtils;

import java.util.*;

/**
 * ConsoleUI
 * ──────────
 * All terminal rendering: banner, tables, menus, input, ANSI colours.
 */
public class ConsoleUI {

    // ── ANSI colour codes ─────────────────────────────────────────────────
    public static final String RESET   = "\033[0m";
    public static final String BOLD    = "\033[1m";
    public static final String RED     = "\033[31m";
    public static final String GREEN   = "\033[32m";
    public static final String YELLOW  = "\033[33m";
    public static final String BLUE    = "\033[34m";
    public static final String PURPLE  = "\033[35m";
    public static final String CYAN    = "\033[36m";
    public static final String WHITE   = "\033[37m";

    // ── Banner ────────────────────────────────────────────────────────────
    public static void printBanner() {
        System.out.println(PURPLE + BOLD);
        System.out.println("  ██╗     ███████╗██╗  ██╗ ██████╗ ██████╗  █████╗ ");
        System.out.println("  ██║     ██╔════╝╚██╗██╔╝██╔═══██╗██╔══██╗██╔══██╗");
        System.out.println("  ██║     █████╗   ╚███╔╝ ██║   ██║██████╔╝███████║");
        System.out.println("  ██║     ██╔══╝   ██╔██╗ ██║   ██║██╔══██╗██╔══██║");
        System.out.println("  ███████╗███████╗██╔╝ ██╗╚██████╔╝██║  ██║██║  ██║");
        System.out.println("  ╚══════╝╚══════╝╚═╝  ╚═╝ ╚═════╝ ╚═╝  ╚═╝╚═╝  ╚═╝");
        System.out.println(RESET);
        System.out.println(CYAN + "  Library Management System  " + YELLOW + "v1.0" + RESET);
        System.out.println(WHITE + "  " + "─".repeat(50) + RESET);
        System.out.println(WHITE + "  📅 " + LexoraUtils.today() + "   |   Developed by Sai Haarshith" + RESET);
        System.out.println();
    }

    // ── Header bar ────────────────────────────────────────────────────────
    public static void printHeader(String title) {
        System.out.println();
        System.out.println(PURPLE + BOLD + "  ╔══ " + title.toUpperCase() + " ══╗" + RESET);
        if (AuthSystem.isLoggedIn()) {
            User u = AuthSystem.getCurrentUser();
            System.out.println(CYAN + "  👤 " + u.getName() + "  [" + u.getRole() + "]" + RESET);
        }
        System.out.println();
    }

    // ── Status messages ───────────────────────────────────────────────────
    public static void success(String msg) { System.out.println(GREEN  + "  ✅ " + msg + RESET); }
    public static void error(String msg)   { System.out.println(RED    + "  ❌ " + msg + RESET); }
    public static void warn(String msg)    { System.out.println(YELLOW + "  ⚠️  " + msg + RESET); }
    public static void info(String msg)    { System.out.println(BLUE   + "  ℹ️  " + msg + RESET); }
    public static void print(String msg)   { System.out.println("  " + msg); }

    // ── Divider ───────────────────────────────────────────────────────────
    public static void divider() { System.out.println(WHITE + "  " + "─".repeat(72) + RESET); }
    public static void thinDivider() { System.out.println(WHITE + "  " + "·".repeat(72) + RESET); }

    // ── Books table ───────────────────────────────────────────────────────
    public static void printBooksTable(Collection<Book> books) {
        if (books.isEmpty()) { warn("No books found."); return; }

        divider();
        System.out.printf(BOLD + "  %-14s %-32s %-18s %-12s %-5s %s%n" + RESET,
            "ISBN", "TITLE", "AUTHOR", "CATEGORY", "AVAIL", "RATING");
        divider();

        for (Book b : books) {
            String statusColor = b.isAvailable() ? GREEN : RED;
            System.out.printf("  %-14s " + CYAN + "%-32s" + RESET + " %-18s %-12s "
                + statusColor + "%-5s" + RESET + " ⭐%.1f%n",
                b.getIsbn(),
                truncate(b.getTitle(), 30),
                truncate(b.getAuthor(), 16),
                truncate(b.getCategory(), 10),
                b.getAvailableCopies() + "/" + b.getTotalCopies(),
                b.getRating());
        }
        divider();
        System.out.println("  " + YELLOW + "Total: " + books.size() + " books" + RESET);
    }

    // ── Users table ───────────────────────────────────────────────────────
    public static void printUsersTable(Collection<User> users) {
        if (users.isEmpty()) { warn("No members found."); return; }

        divider();
        System.out.printf(BOLD + "  %-10s %-22s %-26s %-12s %-8s%n" + RESET,
            "ID", "NAME", "EMAIL", "ROLE", "BORROWS");
        divider();

        for (User u : users) {
            String statusColor = u.isActive() ? GREEN : RED;
            System.out.printf("  " + statusColor + "%-10s" + RESET + " %-22s %-26s %-12s %d/%d%n",
                u.getUserId(),
                truncate(u.getName(), 20),
                truncate(u.getEmail(), 24),
                u.getRole(),
                u.getCurrentBorrows().size(),
                u.getBorrowLimit());
        }
        divider();
    }

    // ── Transaction table ─────────────────────────────────────────────────
    public static void printTransactionsTable(Collection<Transaction> txns) {
        if (txns.isEmpty()) { warn("No transactions found."); return; }

        divider();
        System.out.printf(BOLD + "  %-10s %-10s %-14s %-10s %-12s %-10s%n" + RESET,
            "TXN_ID", "USER", "ISBN", "TYPE", "STATUS", "FINE");
        divider();

        for (Transaction t : txns) {
            String statusColor = switch (t.getStatus()) {
                case ACTIVE   -> GREEN;
                case RETURNED -> BLUE;
                case OVERDUE  -> RED;
                case CANCELLED -> YELLOW;
            };
            System.out.printf("  %-10s %-10s %-14s %-10s "
                + statusColor + "%-12s" + RESET + " ₹%.2f%n",
                t.getTransactionId(),
                t.getUserId(),
                t.getIsbn(),
                t.getType(),
                t.getStatus(),
                t.getFineAmount());
        }
        divider();
    }

    // ── Notifications panel ───────────────────────────────────────────────
    public static void printNotifications(User user) {
        List<String> notes = user.getNotifications();
        if (notes.isEmpty()) return;

        System.out.println();
        System.out.println(YELLOW + BOLD + "  🔔 NOTIFICATIONS (" + notes.size() + ")" + RESET);
        thinDivider();
        int show = Math.min(5, notes.size());
        for (int i = 0; i < show; i++) {
            System.out.println("  " + notes.get(i));
        }
        if (notes.size() > 5)
            info("...and " + (notes.size() - 5) + " more. View all in Notifications menu.");
        thinDivider();
    }

    // ── Dashboard widget ──────────────────────────────────────────────────
    public static void printDashboard(Map<String, Object> stats) {
        System.out.println();
        System.out.println(PURPLE + BOLD + "  ┌─────────────────────────────────────────────┐" + RESET);
        System.out.println(PURPLE + BOLD + "  │              LIBRARY DASHBOARD              │" + RESET);
        System.out.println(PURPLE + BOLD + "  ├───────────────────┬─────────────────────────┤" + RESET);
        stat("📚 Total Books",      stats.get("totalBooks"));
        stat("✅ Available",         stats.get("availableBooks"));
        stat("📖 Borrowed",          stats.get("borrowedBooks"));
        stat("🚨 Overdue",           stats.get("overdueBooks"));
        stat("👥 Active Members",    stats.get("activeMembers"));
        System.out.printf(PURPLE + "  │  💰 Fines Collected │  %-23s│%n" + RESET,
            String.format("₹%.2f", stats.get("finesCollected")));
        System.out.printf(PURPLE + "  │  🏥 Health Score    │  %-23s│%n" + RESET,
            stats.get("healthScore") + "/100");
        System.out.println(PURPLE + BOLD + "  └───────────────────┴─────────────────────────┘" + RESET);
    }

    private static void stat(String label, Object value) {
        System.out.printf(PURPLE + "  │  %-19s│  %-23s│%n" + RESET, label, value);
    }

    // ── Confirm prompt ────────────────────────────────────────────────────
    public static boolean confirm(String question, Scanner sc) {
        System.out.print(YELLOW + "  ❓ " + question + " (y/n): " + RESET);
        String ans = sc.nextLine().trim().toLowerCase();
        return ans.equals("y") || ans.equals("yes");
    }

    // ── Input prompt ──────────────────────────────────────────────────────
    public static String prompt(String label, Scanner sc) {
        System.out.print(CYAN + "  ➤ " + label + ": " + RESET);
        return sc.nextLine().trim();
    }

    public static int promptInt(String label, Scanner sc) {
        System.out.print(CYAN + "  ➤ " + label + ": " + RESET);
        try { return Integer.parseInt(sc.nextLine().trim()); }
        catch (NumberFormatException e) { return -1; }
    }

    // ── Loading animation ─────────────────────────────────────────────────
    public static void loading(String msg) {
        System.out.print(YELLOW + "  ⏳ " + msg);
        for (int i = 0; i < 3; i++) {
            try { Thread.sleep(300); } catch (InterruptedException ignored) {}
            System.out.print(".");
        }
        System.out.println(RESET);
    }

    // ── Footer ────────────────────────────────────────────────────────────
    public static void printFooter() {
        System.out.println();
        System.out.println(WHITE + "  " + "─".repeat(60) + RESET);
        System.out.println(PURPLE + "  Lexora v1.0 – Developed by Sai Haarshith" + RESET);
        System.out.println(WHITE + "  " + "─".repeat(60) + RESET);
    }

    // ── Membership card ───────────────────────────────────────────────────
    public static void printMembershipCard(User u) {
        System.out.println(CYAN + BOLD);
        System.out.println("  ╔══════════════════════════════════════╗");
        System.out.println("  ║       LEXORA DIGITAL MEMBER CARD     ║");
        System.out.println("  ╠══════════════════════════════════════╣");
        System.out.printf ("  ║  Name    : %-26s║%n", u.getName());
        System.out.printf ("  ║  ID      : %-26s║%n", u.getMembershipId());
        System.out.printf ("  ║  Role    : %-26s║%n", u.getRole());
        System.out.printf ("  ║  Since   : %-26s║%n", u.getMemberSince());
        System.out.printf ("  ║  Points  : %-26s║%n", u.getPoints() + " pts");
        System.out.printf ("  ║  Streak  : %-26s║%n", u.getReadingStreak() + " days 🔥");
        System.out.printf ("  ║  Badges  : %-26s║%n", u.getBadges().size() + " earned");
        System.out.println("  ╚══════════════════════════════════════╝" + RESET);
    }

    // ── Utility ───────────────────────────────────────────────────────────
    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max - 1) + "…" : s;
    }

    public static void clearScreen() {
        System.out.print("\033[H\033[2J");
        System.out.flush();
    }
}
