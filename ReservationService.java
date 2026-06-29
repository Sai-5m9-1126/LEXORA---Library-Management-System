package lexora.service;

import lexora.model.*;
import lexora.util.ActivityLogger;
import lexora.util.LexoraUtils;

/**
 * ReservationService
 * ───────────────────
 * Owns the complete reserveBook() and cancelReservation() flows.
 *
 * STATE TRANSITION (reserve):
 *   BEFORE : Book.availableCopies=0 | User has NOT reserved this book
 *   AFTER  : User added to reservation queue | RESERVE transaction created
 */
public class ReservationService {

    private final Library lib = Library.getInstance();

    public record ReserveResult(boolean success, String message, int queuePosition) {}

    /**
     * reserveBook()
     *
     * Step 1 — Validate user and book
     * Step 2 — Prevent reservation if book is currently available (borrow instead)
     * Step 3 — Prevent duplicate reservation
     * Step 4 — Check user reservation limit (max 3)
     * Step 5 — Enqueue user in FIFO reservation queue for this ISBN
     * Step 6 — Create RESERVE transaction
     * Step 7 — Update user state
     * Step 8 — Persist
     * Step 9 — Log
     */
    public ReserveResult reserveBook(String userId, String isbn) {

        // ── Step 1: Validation ────────────────────────────────────────────
        User user = lib.getUser(userId);
        if (user == null)        return fail("User not found: " + userId);
        if (!user.isActive())    return fail("Account is inactive.");

        Book book = lib.getBook(isbn);
        if (book == null)        return fail("Book not found: " + isbn);
        if (book.isArchived())   return fail("Archived books cannot be reserved.");

        // ── Step 2: Book is available — no need to reserve ────────────────
        if (book.isAvailable())
            return fail("Book is currently available. Please borrow it directly.");

        // ── Step 3: Duplicate reservation ─────────────────────────────────
        if (user.hasReserved(isbn))
            return fail("You already have a reservation for this book.");

        // ── Step 4: Active reservation limit (max 3) ──────────────────────
        if (user.getReservations().size() >= 3)
            return fail("Reservation limit reached (max 3 active reservations).");

        // ── Step 5: Enqueue ───────────────────────────────────────────────
        lib.enqueueReservation(isbn, userId);
        int position = lib.getReservationQueue(isbn).size(); // after enqueue

        // ── Step 6: Transaction ───────────────────────────────────────────
        String txnId = LexoraUtils.generateTransactionId();
        Transaction txn = new Transaction(txnId, userId, isbn,
            Transaction.Type.RESERVE, LexoraUtils.today(), null);
        txn.setStatus(Transaction.Status.ACTIVE);

        // ── Step 7: User state ────────────────────────────────────────────
        user.getReservations().add(isbn);
        user.addNotification(String.format(
            "🔔 Reserved \"%s\". Queue position: #%d", book.getTitle(), position));

        // ── Step 8: Persist ───────────────────────────────────────────────
        lib.addTransaction(txn);
        lib.updateUser(user);

        // ── Step 9: Log ───────────────────────────────────────────────────
        ActivityLogger.audit(String.format(
            "RESERVE | user=%s | isbn=%s | title=%s | queuePos=%d",
            userId, isbn, book.getTitle(), position));

        return new ReserveResult(true,
            "✅ Reserved \"" + book.getTitle() + "\". Queue position: #" + position, position);
    }

    /**
     * cancelReservation()
     * Removes user from the queue and cancels the reservation transaction.
     */
    public ReserveResult cancelReservation(String userId, String isbn) {

        User user = lib.getUser(userId);
        if (user == null)                 return fail("User not found.");
        if (!user.hasReserved(isbn))      return fail("No active reservation found for this book.");

        Book book = lib.getBook(isbn);
        String title = (book != null) ? book.getTitle() : isbn;

        // Remove from queue
        lib.removeFromQueue(isbn, userId);
        user.getReservations().remove(isbn);

        // Update reservation transaction to CANCELLED
        lib.getAllTransactions().stream()
            .filter(t -> t.getUserId().equals(userId)
                      && t.getIsbn().equals(isbn)
                      && t.getType()   == Transaction.Type.RESERVE
                      && t.getStatus() == Transaction.Status.ACTIVE)
            .findFirst()
            .ifPresent(t -> {
                t.setStatus(Transaction.Status.CANCELLED);
                lib.updateTransaction(t);
            });

        lib.updateUser(user);

        ActivityLogger.audit("CANCEL_RESERVE | user=" + userId + " | isbn=" + isbn);

        return new ReserveResult(true, "✅ Reservation cancelled for \"" + title + "\".", 0);
    }

    private ReserveResult fail(String msg) {
        ActivityLogger.warn("RESERVE_FAIL | " + msg);
        return new ReserveResult(false, msg, -1);
    }
}
