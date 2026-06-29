package lexora.service;

import lexora.model.*;
import lexora.util.ActivityLogger;
import lexora.util.LexoraUtils;

/**
 * BorrowService
 * ─────────────
 * Owns the complete borrowBook() flow.
 *
 * STATE TRANSITION:
 *   BEFORE : Book.availableCopies > 0  |  User.currentBorrows.size() < limit
 *   DURING : Validation → State lock → Transaction create → Persist → Log
 *   AFTER  : Book.availableCopies--    |  User.currentBorrows += txnId
 */
public class BorrowService {

    private static final int BORROW_DAYS = 14;
    private final Library lib = Library.getInstance();

    public record BorrowResult(boolean success, String message, Transaction transaction) {}

    /**
     * borrowBook()
     *
     * Step 1 — Validate user exists and is active
     * Step 2 — Validate book exists and is not archived
     * Step 3 — Check user borrow limit
     * Step 4 — Check user doesn't already hold this book
     * Step 5 — Check book availability (concurrency-safe read)
     * Step 6 — Compute due date (today + 14 days)
     * Step 7 — Create Transaction record
     * Step 8 — Update Book state (decrement available copy, set borrowedBy, dueDate)
     * Step 9 — Update User state (add txnId to currentBorrows, increment stats)
     * Step 10 — Persist everything (atomic-style: memory first, then disk)
     * Step 11 — Push undo snapshot
     * Step 12 — Log audit trail
     */
    public synchronized BorrowResult borrowBook(String userId, String isbn) {

        // ── Step 1: User validation ───────────────────────────────────────
        User user = lib.getUser(userId);
        if (user == null)
            return fail("User not found: " + userId);
        if (!user.isActive())
            return fail("Account is inactive. Please contact the librarian.");

        // ── Step 2: Book validation ───────────────────────────────────────
        Book book = lib.getBook(isbn);
        if (book == null)
            return fail("Book not found: " + isbn);
        if (book.isArchived())
            return fail("This book has been archived and is not available for borrowing.");

        // ── Step 3: Borrow limit ──────────────────────────────────────────
        if (!user.canBorrow())
            return fail("Borrow limit reached (" + user.getBorrowLimit() + " books max for " + user.getRole() + ").");

        // ── Step 4: Duplicate borrow check ────────────────────────────────
        boolean alreadyBorrowing = lib.getActiveBorrowForUserAndBook(userId, isbn).isPresent();
        if (alreadyBorrowing)
            return fail("You already have an active borrow for this book.");

        // ── Step 5: Availability check (synchronized to prevent race condition) ─
        if (!book.isAvailable())
            return fail("Book is not available. You can reserve it instead.");

        // ── Step 6: Due date ──────────────────────────────────────────────
        String today   = LexoraUtils.today();
        String dueDate = LexoraUtils.dueDateFrom(today, BORROW_DAYS);

        // ── Step 7: Transaction ───────────────────────────────────────────
        String txnId = LexoraUtils.generateTransactionId();
        Transaction txn = new Transaction(txnId, userId, isbn,
            Transaction.Type.BORROW, today, dueDate);
        txn.setStatus(Transaction.Status.ACTIVE);

        // ── Step 8: Book state update ─────────────────────────────────────
        book.decrementCopy();
        book.setBorrowedBy(userId);
        book.setDueDate(dueDate);
        book.incrementBorrows();

        // ── Step 9: User state update ─────────────────────────────────────
        user.getCurrentBorrows().add(txnId);
        user.getBorrowHistory().add(txnId);
        user.incrementTotalBorrows();
        user.addPoints(10); // gamification
        AchievementService.checkAndAward(user); // check for new badges

        // Notify user
        user.addNotification("📖 You borrowed \"" + book.getTitle() + "\". Due: " + dueDate);

        // ── Step 10: Persist ──────────────────────────────────────────────
        lib.addTransaction(txn);
        lib.updateBook(book);
        lib.updateUser(user);

        // ── Step 11: Undo snapshot ────────────────────────────────────────
        lexora.storage.FileStorageManager.pushUndo("BORROW|" + txnId + "|" + isbn + "|" + userId);

        // ── Step 12: Audit log ────────────────────────────────────────────
        ActivityLogger.audit(String.format("BORROW | user=%s | isbn=%s | title=%s | due=%s | txn=%s",
            userId, isbn, book.getTitle(), dueDate, txnId));

        return new BorrowResult(true,
            "✅ Successfully borrowed \"" + book.getTitle() + "\". Return by: " + dueDate,
            txn);
    }

    // ── Helper ────────────────────────────────────────────────────────────
    private BorrowResult fail(String msg) {
        ActivityLogger.warn("BORROW_FAIL | " + msg);
        return new BorrowResult(false, msg, null);
    }
}
