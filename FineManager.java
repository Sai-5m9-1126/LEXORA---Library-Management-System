package lexora.service;

import lexora.model.Library;
import lexora.model.Transaction;
import lexora.model.User;
import lexora.util.ActivityLogger;
import lexora.util.LexoraUtils;

import java.util.List;
import java.util.stream.Collectors;

/**
 * FineManager
 * ────────────
 * Calculates, records, and manages fines.
 *
 * Fine policy:
 *   - ₹2.00 per overdue day
 *   - Early / on-time return  → ₹0.00
 *   - Grace period            → 0 days (strict)
 *
 * Edge cases handled:
 *   1. Returned exactly on due date → ₹0
 *   2. Returned before due date     → ₹0
 *   3. Returned after due date      → overdueDays × rate
 */
public class FineManager {

    private final Library lib = Library.getInstance();

    // ── Core calculation ──────────────────────────────────────────────────
    public double calculateFine(String dueDate) {
        if (dueDate == null) return 0.0;
        long overdue = LexoraUtils.overdueDays(dueDate);
        return overdue * LexoraUtils.FINE_RATE_PER_DAY;
    }

    public double calculateFineForTransaction(Transaction txn) {
        if (txn.getReturnDate() != null) {
            // Already returned — calculate based on actual return date
            long diff = LexoraUtils.daysDiff(txn.getDueDate(), txn.getReturnDate());
            return Math.max(0, diff) * LexoraUtils.FINE_RATE_PER_DAY;
        }
        // Still active — calculate based on today
        return calculateFine(txn.getDueDate());
    }

    // ── Record fine after return ──────────────────────────────────────────
    public void recordFine(String userId, String isbn, double amount) {
        User user = lib.getUser(userId);
        if (user == null) return;

        ActivityLogger.audit(String.format(
            "FINE_RECORDED | user=%s | isbn=%s | amount=₹%.2f", userId, isbn, amount));
    }

    // ── Pay fine ──────────────────────────────────────────────────────────
    public boolean payFine(String transactionId) {
        Transaction txn = lib.getTransaction(transactionId);
        if (txn == null || txn.isFinePaid()) return false;

        txn.setFinePaid(true);
        lib.updateTransaction(txn);

        User user = lib.getUser(txn.getUserId());
        if (user != null) {
            user.addFinePaid(txn.getFineAmount());
            user.addNotification(String.format(
                "💳 Fine of ₹%.2f paid for ISBN %s", txn.getFineAmount(), txn.getIsbn()));
            lib.updateUser(user);
        }

        ActivityLogger.audit(String.format(
            "FINE_PAID | txn=%s | user=%s | amount=₹%.2f",
            transactionId, txn.getUserId(), txn.getFineAmount()));
        return true;
    }

    // ── Overdue detection ─────────────────────────────────────────────────
    public void markOverdueTransactions() {
        String today = LexoraUtils.today();
        lib.getAllTransactions().stream()
            .filter(t -> t.getType() == Transaction.Type.BORROW
                      && t.getStatus() == Transaction.Status.ACTIVE
                      && t.getDueDate() != null
                      && t.getDueDate().compareTo(today) < 0)
            .forEach(t -> {
                t.setStatus(Transaction.Status.OVERDUE);
                lib.updateTransaction(t);

                User u = lib.getUser(t.getUserId());
                if (u != null) {
                    u.addNotification(String.format(
                        "🚨 OVERDUE: Book ISBN %s was due on %s. Fine accumulating at ₹%.0f/day.",
                        t.getIsbn(), t.getDueDate(), LexoraUtils.FINE_RATE_PER_DAY));
                    lib.updateUser(u);
                }
            });
    }

    // ── Reports ───────────────────────────────────────────────────────────
    public double getTotalFinesCollected() {
        return lib.getAllTransactions().stream()
            .filter(Transaction::isFinePaid)
            .mapToDouble(Transaction::getFineAmount)
            .sum();
    }

    public double getTotalFinesPending() {
        return lib.getAllTransactions().stream()
            .filter(t -> t.getFineAmount() > 0 && !t.isFinePaid())
            .mapToDouble(Transaction::getFineAmount)
            .sum();
    }

    public List<Transaction> getUnpaidFines() {
        return lib.getAllTransactions().stream()
            .filter(t -> t.getFineAmount() > 0 && !t.isFinePaid())
            .collect(Collectors.toList());
    }
}
