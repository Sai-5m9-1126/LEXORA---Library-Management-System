package lexora.model;

import lexora.storage.FileStorageManager;
import lexora.util.ActivityLogger;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Library
 * Central in-memory store. Owns all Books, Users, Transactions.
 * Loads from disk on startup; all mutations go through here, then flush to disk.
 */
public class Library {

    // ── In-memory maps (primary keys) ─────────────────────────────────────
    private final Map<String, Book>        books        = new LinkedHashMap<>();
    private final Map<String, User>        users        = new LinkedHashMap<>();
    private final Map<String, Transaction> transactions = new LinkedHashMap<>();

    // ── Reservation queues: isbn → FIFO queue of userIds ──────────────────
    private final Map<String, Deque<String>> reservationQueues = new HashMap<>();

    // ── Singleton ─────────────────────────────────────────────────────────
    private static Library instance;
    private Library() {}
    public static Library getInstance() {
        if (instance == null) instance = new Library();
        return instance;
    }

    // ── Bootstrap ─────────────────────────────────────────────────────────
    public void load() throws IOException {
        FileStorageManager.init();

        FileStorageManager.loadBooks().forEach(b -> books.put(b.getIsbn(), b));
        FileStorageManager.loadUsers().forEach(u -> users.put(u.getUserId(), u));
        FileStorageManager.loadTransactions().forEach(t -> transactions.put(t.getTransactionId(), t));

        // Rebuild reservation queues from active RESERVE transactions
        transactions.values().stream()
            .filter(t -> t.getType() == Transaction.Type.RESERVE
                      && t.getStatus() == Transaction.Status.ACTIVE)
            .sorted(Comparator.comparing(Transaction::getBorrowDate))
            .forEach(t -> reservationQueues
                .computeIfAbsent(t.getIsbn(), k -> new ArrayDeque<>())
                .offer(t.getUserId()));

        ActivityLogger.info("Library loaded: "
            + books.size() + " books, " + users.size() + " users, "
            + transactions.size() + " transactions.");
    }

    // ── Persist ───────────────────────────────────────────────────────────
    public void persist() {
        FileStorageManager.saveBooks(books.values());
        FileStorageManager.saveUsers(users.values());
        FileStorageManager.saveTransactions(transactions.values());
    }

    // ── Book CRUD ─────────────────────────────────────────────────────────
    public void addBook(Book b)           { books.put(b.getIsbn(), b);  persist(); }
    public void updateBook(Book b)        { books.put(b.getIsbn(), b);  persist(); }
    public void removeBook(String isbn)   { books.remove(isbn);          persist(); }
    public Book getBook(String isbn)      { return books.get(isbn); }
    public boolean bookExists(String isbn){ return books.containsKey(isbn); }

    public Collection<Book> getAllBooks()      { return books.values(); }
    public List<Book> getAvailableBooks()      {
        return books.values().stream().filter(Book::isAvailable).collect(Collectors.toList());
    }

    // ── User CRUD ─────────────────────────────────────────────────────────
    public void addUser(User u)            { users.put(u.getUserId(), u); persist(); }
    public void updateUser(User u)         { users.put(u.getUserId(), u); persist(); }
    public void removeUser(String uid)     { users.remove(uid);           persist(); }
    public User getUser(String uid)        { return users.get(uid); }
    public boolean userExists(String uid)  { return users.containsKey(uid); }
    public Collection<User> getAllUsers()   { return users.values(); }

    public Optional<User> findUserByEmail(String email) {
        return users.values().stream()
            .filter(u -> u.getEmail().equalsIgnoreCase(email))
            .findFirst();
    }

    // ── Transaction CRUD ──────────────────────────────────────────────────
    public void addTransaction(Transaction t)  { transactions.put(t.getTransactionId(), t); persist(); }
    public void updateTransaction(Transaction t){ transactions.put(t.getTransactionId(), t); persist(); }
    public Transaction getTransaction(String id){ return transactions.get(id); }
    public Collection<Transaction> getAllTransactions(){ return transactions.values(); }

    public List<Transaction> getTransactionsForUser(String uid) {
        return transactions.values().stream()
            .filter(t -> t.getUserId().equals(uid))
            .collect(Collectors.toList());
    }

    public Optional<Transaction> getActiveBorrowForUserAndBook(String uid, String isbn) {
        return transactions.values().stream()
            .filter(t -> t.getUserId().equals(uid)
                      && t.getIsbn().equals(isbn)
                      && t.getType()   == Transaction.Type.BORROW
                      && t.getStatus() == Transaction.Status.ACTIVE)
            .findFirst();
    }

    // ── Reservation queue ─────────────────────────────────────────────────
    public void enqueueReservation(String isbn, String uid) {
        reservationQueues.computeIfAbsent(isbn, k -> new ArrayDeque<>()).offer(uid);
    }

    public String dequeueNextReservation(String isbn) {
        Deque<String> q = reservationQueues.get(isbn);
        return (q == null || q.isEmpty()) ? null : q.poll();
    }

    public boolean hasReservations(String isbn) {
        Deque<String> q = reservationQueues.get(isbn);
        return q != null && !q.isEmpty();
    }

    public List<String> getReservationQueue(String isbn) {
        Deque<String> q = reservationQueues.get(isbn);
        return q == null ? new ArrayList<>() : new ArrayList<>(q);
    }

    public void removeFromQueue(String isbn, String uid) {
        Deque<String> q = reservationQueues.get(isbn);
        if (q != null) q.remove(uid);
    }

    // ── Search ────────────────────────────────────────────────────────────
    public List<Book> searchBooks(String query) {
        String q = query.toLowerCase();
        return books.values().stream()
            .filter(b -> b.getIsbn().toLowerCase().contains(q)
                      || b.getTitle().toLowerCase().contains(q)
                      || b.getAuthor().toLowerCase().contains(q)
                      || b.getCategory().toLowerCase().contains(q)
                      || String.valueOf(b.getYear()).contains(q))
            .collect(Collectors.toList());
    }

    public List<Book> searchByCategory(String category) {
        return books.values().stream()
            .filter(b -> b.getCategory().equalsIgnoreCase(category))
            .collect(Collectors.toList());
    }

    // ── Sorting ───────────────────────────────────────────────────────────
    public List<Book> booksSortedBy(String field) {
        List<Book> list = new ArrayList<>(books.values());
        return switch (field.toLowerCase()) {
            case "title"    -> list.stream().sorted(Comparator.comparing(Book::getTitle)).toList();
            case "author"   -> list.stream().sorted(Comparator.comparing(Book::getAuthor)).toList();
            case "year"     -> list.stream().sorted(Comparator.comparingInt(Book::getYear).reversed()).toList();
            case "rating"   -> list.stream().sorted(Comparator.comparingDouble(Book::getRating).reversed()).toList();
            case "popular"  -> list.stream().sorted(Comparator.comparingInt(Book::getTotalBorrows).reversed()).toList();
            default         -> list;
        };
    }

    // ── Health score ─────────────────────────────────────────────────────
    /** Returns 0–100 score representing how healthy the library's circulation is */
    public int healthScore() {
        if (books.isEmpty()) return 0;
        long available = books.values().stream().filter(Book::isAvailable).count();
        long overdue   = transactions.values().stream()
            .filter(t -> t.getStatus() == Transaction.Status.OVERDUE).count();
        long activeU   = users.values().stream().filter(User::isActive).count();

        double availRatio  = (double) available / books.size();        // want high
        double overdueRatio = transactions.isEmpty() ? 0
            : (double) overdue / transactions.size();                   // want low

        int score = (int)((availRatio * 60) + ((1 - overdueRatio) * 30) + Math.min(10, activeU));
        return Math.min(100, Math.max(0, score));
    }
}
