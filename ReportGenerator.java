package lexora.report;

import lexora.model.Book;
import lexora.model.Library;
import lexora.model.Transaction;
import lexora.model.User;
import lexora.service.FineManager;
import lexora.storage.FileStorageManager;
import lexora.util.LexoraUtils;

import java.io.IOException;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * ReportGenerator
 * ────────────────
 * Generates analytics: dashboard stats, monthly reports, exports.
 */
public class ReportGenerator {

    private final Library     lib     = Library.getInstance();
    private final FineManager fineMgr = new FineManager();

    // ── Dashboard summary ─────────────────────────────────────────────────
    public Map<String, Object> getDashboardStats() {
        Map<String, Object> stats = new LinkedHashMap<>();

        long total      = lib.getAllBooks().size();
        long available  = lib.getAllBooks().stream().filter(Book::isAvailable).count();
        long borrowed   = lib.getAllTransactions().stream()
            .filter(t -> t.getType() == Transaction.Type.BORROW
                      && t.getStatus() == Transaction.Status.ACTIVE).count();
        long overdue    = lib.getAllTransactions().stream()
            .filter(t -> t.getStatus() == Transaction.Status.OVERDUE).count();
        long members    = lib.getAllUsers().stream().filter(User::isActive).count();

        stats.put("totalBooks",       total);
        stats.put("availableBooks",   available);
        stats.put("borrowedBooks",    borrowed);
        stats.put("overdueBooks",     overdue);
        stats.put("activeMembers",    members);
        stats.put("finesCollected",   fineMgr.getTotalFinesCollected());
        stats.put("finesPending",     fineMgr.getTotalFinesPending());
        stats.put("healthScore",      lib.healthScore());

        return stats;
    }

    // ── Top readers ───────────────────────────────────────────────────────
    public List<User> getTopReaders(int limit) {
        return lib.getAllUsers().stream()
            .sorted(Comparator.comparingInt(User::getTotalBorrows).reversed())
            .limit(limit)
            .collect(Collectors.toList());
    }

    // ── Popular categories ────────────────────────────────────────────────
    public Map<String, Long> getPopularCategories() {
        return lib.getAllTransactions().stream()
            .filter(t -> t.getType() == Transaction.Type.BORROW)
            .map(t -> lib.getBook(t.getIsbn()))
            .filter(Objects::nonNull)
            .collect(Collectors.groupingBy(Book::getCategory, Collectors.counting()))
            .entrySet().stream()
            .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
            .collect(Collectors.toMap(
                Map.Entry::getKey, Map.Entry::getValue,
                (e1, e2) -> e1, LinkedHashMap::new));
    }

    // ── Monthly analytics ─────────────────────────────────────────────────
    public String generateMonthlyReport() {
        String month = LocalDate.now().getMonth().name() + " " + LocalDate.now().getYear();
        Map<String, Object> stats = getDashboardStats();

        StringBuilder sb = new StringBuilder();
        sb.append("═".repeat(60)).append("\n");
        sb.append("       LEXORA MONTHLY REPORT — ").append(month).append("\n");
        sb.append("═".repeat(60)).append("\n\n");

        sb.append("📚 COLLECTION OVERVIEW\n");
        sb.append("  Total Books      : ").append(stats.get("totalBooks")).append("\n");
        sb.append("  Available        : ").append(stats.get("availableBooks")).append("\n");
        sb.append("  Currently Issued : ").append(stats.get("borrowedBooks")).append("\n");
        sb.append("  Overdue          : ").append(stats.get("overdueBooks")).append("\n\n");

        sb.append("👥 MEMBERSHIP\n");
        sb.append("  Active Members   : ").append(stats.get("activeMembers")).append("\n\n");

        sb.append("💰 FINANCIALS\n");
        sb.append(String.format("  Fines Collected  : ₹%.2f%n", stats.get("finesCollected")));
        sb.append(String.format("  Fines Pending    : ₹%.2f%n%n", stats.get("finesPending")));

        sb.append("🏥 LIBRARY HEALTH SCORE: ").append(stats.get("healthScore")).append("/100\n\n");

        sb.append("📊 TOP CATEGORIES\n");
        getPopularCategories().forEach((cat, count) ->
            sb.append(String.format("  %-20s: %d borrows%n", cat, count)));
        sb.append("\n");

        sb.append("🏆 TOP READERS\n");
        getTopReaders(5).forEach(u ->
            sb.append(String.format("  %-20s: %d books%n", u.getName(), u.getTotalBorrows())));
        sb.append("\n");

        sb.append("─".repeat(60)).append("\n");
        sb.append("  Lexora v1.0 — Developed by Sai Haarshith\n");
        sb.append("─".repeat(60)).append("\n");

        return sb.toString();
    }

    // ── Borrow receipt ────────────────────────────────────────────────────
    public String generateBorrowReceipt(Transaction txn) {
        Book book = lib.getBook(txn.getIsbn());
        User user = lib.getUser(txn.getUserId());
        if (book == null || user == null) return "Receipt data unavailable.";

        return String.format("""
            ╔══════════════════════════════════════╗
            ║        LEXORA BORROW RECEIPT         ║
            ╠══════════════════════════════════════╣
            ║  Transaction ID : %-18s║
            ║  Member         : %-18s║
            ║  Membership ID  : %-18s║
            ╠══════════════════════════════════════╣
            ║  Book           : %-18s║
            ║  ISBN           : %-18s║
            ║  Author         : %-18s║
            ╠══════════════════════════════════════╣
            ║  Borrow Date    : %-18s║
            ║  Due Date       : %-18s║
            ╠══════════════════════════════════════╣
            ║  Fine (if late) : ₹%.2f/day         ║
            ╚══════════════════════════════════════╝
            """,
            txn.getTransactionId(),
            user.getName(),
            user.getMembershipId(),
            truncate(book.getTitle(), 18),
            book.getIsbn(),
            truncate(book.getAuthor(), 18),
            txn.getBorrowDate(),
            txn.getDueDate(),
            LexoraUtils.FINE_RATE_PER_DAY);
    }

    // ── Export ────────────────────────────────────────────────────────────
    public void exportMonthlyReport() throws IOException {
        String report = generateMonthlyReport();
        String filename = "report_" + LocalDate.now().toString() + ".txt";
        FileStorageManager.exportReport(filename, report);
        System.out.println("Report exported to data/" + filename);
    }

    private String truncate(String s, int max) {
        return s.length() > max ? s.substring(0, max - 1) + "…" : s;
    }
}
