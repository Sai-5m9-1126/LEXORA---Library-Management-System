package lexora.service;

import lexora.model.*;
import lexora.util.ActivityLogger;
import lexora.util.LexoraUtils;

/**
 * ReturnService
 * ─────────────
 * Owns the complete returnBook() flow including:
 *   - Overdue detection & fine calculation
 *   - Book state reset
 *   - Reservation queue auto-assignment
 *
 * STATE TRANSITION:
 *   BEFORE : Transaction.status=ACTIVE | Book.availableCopies=N
 *   DURING : Validate → Calculate fine → Reset → AutoAssign → Persist → Log
 *   AFTER  : Transaction.status=RETURNED | Book.availableCopies=N+1
 *            (or next user auto-assigned if reservations exist)
 */
public class ReturnService {

    private final Library      lib     = Library.getInstance();
    private final FineManager  fineManager  = new FineManager();
    private final BorrowService borrowSvc   = new BorrowService();

    public record ReturnResult(boolean success, String message, double fineAmount, String nextUser) {}

    /**
     * returnBook()
     *
     * Step 1 — Validate user and book exist
     * Step 2 — Find the active borrow transaction for this user+book
     * Step 3 — Calculate overdue days and fine
     * Step 4 — Mark transaction as RETURNED, set returnDate, overdueDays, fineAmount
     * Step 5 — Update Book: increment available copy, clear borrowedBy and dueDate
     * Step 6 — Update User: remove txnId from currentBorrows
     * Step 7 — Check if fine > 0 → store fine, notify user
     * Step 8 — Check reservation queue → auto-assign to next user (FIFO)
     * Step 9 — Persist all state changes
     * Step 10 — Log audit trail
     */
    public synchronized ReturnResult returnBook(String userId, String isbn) {

        // ── Step 1: Validate ──────────────────────────────────────────────
        User user = lib.getUser(userId);
        if (user == null)
            return fail("User not found: " + userId);

        Book book = lib.getBook(isbn);
        if (book == null)
            return fail("Book not found: " + isbn);

        // ── Step 2: Find active borrow transaction ────────────────────────
        Transaction txn = lib.getActiveBorrowForUserAndBook(userId, isbn)
            .orElse(null);
        if (txn == null)
            return fail("No active borrow found for this user and book.");

        // ── Step 3: Fine calculation ──────────────────────────────────────
        String today      = LexoraUtils.today();
        long   overdueDays = LexoraUtils.overdueDays(txn.getDueDate());
        double fineAmount  = fineManager.calculateFine(txn.getDueDate());

        // ── Step 4: Update transaction ────────────────────────────────────
        txn.setStatus(Transaction.Status.RETURNED);
        txn.setReturnDate(today);
        txn.setOverdueDays(overdueDays);
        txn.setFineAmount(fineAmount);
        if (fineAmount > 0) txn.setFinePaid(false); // awaiting payment

        // ── Step 5: Reset book state ──────────────────────────────────────
        book.incrementCopy();
        book.setBorrowedBy(null);
        book.setDueDate(null);

        // ── Step 6: Update user ───────────────────────────────────────────
        user.getCurrentBorrows().remove(txn.getTransactionId());
        user.addPoints(5); // return points
        if (overdueDays == 0) user.incrementStreak();
        else                  user.resetStreak();

        // ── Step 7: Fine notification ─────────────────────────────────────
        String fineMsg = "";
        if (fineAmount > 0) {
            fineManager.recordFine(userId, isbn, fineAmount);
            user.addNotification(String.format(
                "⚠️ Fine of ₹%.2f for overdue return of \"%s\" (%d days late)",
                fineAmount, book.getTitle(), overdueDays));
            fineMsg = String.format(" | Fine: ₹%.2f (%d days overdue)", fineAmount, overdueDays);
        } else {
            user.addNotification("✅ Returned \"" + book.getTitle() + "\" on time. Well done!");
        }

        // ── Step 8: Auto-assign from reservation queue ────────────────────
        String nextUser = null;
        if (lib.hasReservations(isbn)) {
            nextUser = lib.dequeueNextReservation(isbn);
            if (nextUser != null) {
                BorrowService.BorrowResult autoResult = borrowSvc.borrowBook(nextUser, isbn);
                if (autoResult.success()) {
                    User next = lib.getUser(nextUser);
                    if (next != null) {
                        next.getReservations().remove(isbn);
                        next.addNotification("📬 Your reserved book \"" + book.getTitle()
                            + "\" is now borrowed. Due: " + autoResult.transaction().getDueDate());
                        lib.updateUser(next);
                    }
                    ActivityLogger.info("AUTO_ASSIGN | isbn=" + isbn + " → user=" + nextUser);
                } else {
                    // re-enqueue if auto-borrow failed
                    lib.enqueueReservation(isbn, nextUser);
                    nextUser = null;
                }
            }
        }

        // ── Step 9: Persist ───────────────────────────────────────────────
        lib.updateTransaction(txn);
        lib.updateBook(book);
        lib.updateUser(user);

        // ── Step 10: Audit log ────────────────────────────────────────────
        ActivityLogger.audit(String.format(
            "RETURN | user=%s | isbn=%s | title=%s | returnDate=%s | overdue=%d | fine=%.2f",
            userId, isbn, book.getTitle(), today, overdueDays, fineAmount));

        String summary = "✅ Returned \"" + book.getTitle() + "\"" + fineMsg
            + (nextUser != null ? " | Auto-assigned to: " + nextUser : "");

        return new ReturnResult(true, summary, fineAmount, nextUser);
    }

    private ReturnResult fail(String msg) {
        ActivityLogger.warn("RETURN_FAIL | " + msg);
        return new ReturnResult(false, msg, 0, null);
    }
}
