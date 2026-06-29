package lexora.util;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

/**
 * Utility helpers — date math, ISBN validation, password hashing, ID gen.
 */
public class LexoraUtils {

    public static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    public static final double FINE_RATE_PER_DAY = 2.0; // ₹2 per overdue day

    // ── Date helpers ──────────────────────────────────────────────────────
    public static String today() {
        return LocalDate.now().format(DATE_FMT);
    }

    public static String dueDateFrom(String fromDate, int days) {
        LocalDate date = LocalDate.parse(fromDate, DATE_FMT);
        return date.plusDays(days).format(DATE_FMT);
    }

    /** Returns negative if before due date (early), positive if overdue */
    public static long daysDiff(String fromDate, String toDate) {
        LocalDate from = LocalDate.parse(fromDate, DATE_FMT);
        LocalDate to   = LocalDate.parse(toDate,   DATE_FMT);
        return ChronoUnit.DAYS.between(from, to);
    }

    public static long overdueDays(String dueDate) {
        long diff = daysDiff(dueDate, today());
        return Math.max(0, diff);
    }

    // ── ISBN validation ───────────────────────────────────────────────────
    /**
     * Validates ISBN-13 using check-digit algorithm.
     * Also accepts ISBN-10 and converts for storage.
     */
    public static boolean isValidISBN(String isbn) {
        if (isbn == null) return false;
        String cleaned = isbn.replaceAll("[\\s-]", "");
        if (cleaned.length() == 13) return validateISBN13(cleaned);
        if (cleaned.length() == 10) return validateISBN10(cleaned);
        return false;
    }

    private static boolean validateISBN13(String isbn) {
        try {
            int sum = 0;
            for (int i = 0; i < 12; i++) {
                int digit = Character.getNumericValue(isbn.charAt(i));
                sum += (i % 2 == 0) ? digit : digit * 3;
            }
            int check = (10 - (sum % 10)) % 10;
            return check == Character.getNumericValue(isbn.charAt(12));
        } catch (Exception e) { return false; }
    }

    private static boolean validateISBN10(String isbn) {
        try {
            int sum = 0;
            for (int i = 0; i < 9; i++) {
                sum += (10 - i) * Character.getNumericValue(isbn.charAt(i));
            }
            char last = isbn.charAt(9);
            sum += (last == 'X' || last == 'x') ? 10 : Character.getNumericValue(last);
            return sum % 11 == 0;
        } catch (Exception e) { return false; }
    }

    // ── Fine calculation ──────────────────────────────────────────────────
    public static double calculateFine(String dueDate) {
        long overdue = overdueDays(dueDate);
        return overdue * FINE_RATE_PER_DAY;
    }

    // ── Password hashing (SHA-256) ────────────────────────────────────────
    public static String hashPassword(String plain) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(plain.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            return plain; // fallback (should never happen)
        }
    }

    public static boolean checkPassword(String plain, String hash) {
        return hashPassword(plain).equals(hash);
    }

    // ── ID generation ─────────────────────────────────────────────────────
    private static int txnCounter = 1000;

    public static String generateUserId(String name) {
        String base = name.replaceAll("\\s+", "").toLowerCase();
        if (base.length() > 6) base = base.substring(0, 6);
        return base + (int)(Math.random() * 900 + 100);
    }

    public static String generateTransactionId() {
        return "TXN" + (++txnCounter);
    }

    // ── Email validation ──────────────────────────────────────────────────
    public static boolean isValidEmail(String email) {
        return email != null && email.matches("^[\\w.+-]+@[\\w-]+\\.[a-zA-Z]{2,}$");
    }

    // ── Phone validation ──────────────────────────────────────────────────
    public static boolean isValidPhone(String phone) {
        return phone != null && phone.matches("^[6-9]\\d{9}$"); // Indian mobile
    }
}
