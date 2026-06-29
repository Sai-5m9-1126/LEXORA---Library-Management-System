package lexora.service;

import lexora.model.Book;
import lexora.model.Library;
import lexora.model.Transaction;
import lexora.model.User;

import java.util.*;
import java.util.stream.Collectors;

/**
 * RecommendationService
 * ──────────────────────
 * Smart recommendations based on:
 *   1. User's borrow history → preferred categories
 *   2. Highly rated books in those categories
 *   3. Popular books the user hasn't read
 */
public class RecommendationService {

    private final Library lib = Library.getInstance();

    public List<Book> recommend(String userId, int limit) {
        User user = lib.getUser(userId);
        if (user == null) return List.of();

        // ── Determine preferred categories from history ───────────────────
        Map<String, Long> categoryFreq = user.getBorrowHistory().stream()
            .map(lib::getTransaction)
            .filter(Objects::nonNull)
            .map(Transaction::getIsbn)
            .map(lib::getBook)
            .filter(Objects::nonNull)
            .collect(Collectors.groupingBy(Book::getCategory, Collectors.counting()));

        Set<String> alreadyBorrowed = user.getBorrowHistory().stream()
            .map(lib::getTransaction)
            .filter(Objects::nonNull)
            .map(Transaction::getIsbn)
            .collect(Collectors.toSet());

        // ── Score each available book ─────────────────────────────────────
        return lib.getAllBooks().stream()
            .filter(Book::isAvailable)
            .filter(b -> !alreadyBorrowed.contains(b.getIsbn()))
            .sorted(Comparator.comparingDouble((Book b) ->
                    (categoryFreq.getOrDefault(b.getCategory(), 0L) * 2.0)
                    + b.getRating()
                    + (b.getTotalBorrows() * 0.1)
                ).reversed())
            .limit(limit)
            .collect(Collectors.toList());
    }

    /** Returns most popular books globally (Hall of Fame) */
    public List<Book> getPopular(int limit) {
        return lib.getAllBooks().stream()
            .sorted(Comparator.comparingInt(Book::getTotalBorrows).reversed())
            .limit(limit)
            .collect(Collectors.toList());
    }

    /** Returns top-rated books */
    public List<Book> getTopRated(int limit) {
        return lib.getAllBooks().stream()
            .filter(b -> b.getRatingCount() >= 3) // minimum ratings
            .sorted(Comparator.comparingDouble(Book::getRating).reversed())
            .limit(limit)
            .collect(Collectors.toList());
    }
}
