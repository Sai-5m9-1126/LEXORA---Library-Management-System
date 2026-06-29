package lexora.storage;

import lexora.model.Book;
import lexora.model.Transaction;
import lexora.model.User;

import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * FileStorageManager
 * Single responsibility: read and write all data files.
 * Every other service calls this — nothing writes files directly.
 */
public class FileStorageManager {

    private static final String DATA_DIR    = "data/";
    private static final String BACKUP_DIR  = "data/backups/";
    private static final String BOOKS_FILE  = DATA_DIR + "books.txt";
    private static final String USERS_FILE  = DATA_DIR + "members.txt";
    private static final String TXN_FILE    = DATA_DIR + "transactions.txt";
    private static final String LOG_FILE    = DATA_DIR + "logs.txt";
    private static final String UNDO_FILE   = DATA_DIR + "undo.txt";

    // ── Initialisation ────────────────────────────────────────────────────
    public static void init() throws IOException {
        Files.createDirectories(Paths.get(DATA_DIR));
        Files.createDirectories(Paths.get(BACKUP_DIR));
        for (String f : new String[]{BOOKS_FILE, USERS_FILE, TXN_FILE, LOG_FILE, UNDO_FILE}) {
            Path p = Paths.get(f);
            if (!Files.exists(p)) Files.createFile(p);
        }
    }

    // ── Book persistence ──────────────────────────────────────────────────
    public static List<Book> loadBooks() {
        return loadEntities(BOOKS_FILE, Book::fromCSV);
    }

    public static void saveBooks(Collection<Book> books) {
        saveEntities(BOOKS_FILE, books, Book::toCSV);
    }

    // ── User persistence ──────────────────────────────────────────────────
    public static List<User> loadUsers() {
        return loadEntities(USERS_FILE, User::fromCSV);
    }

    public static void saveUsers(Collection<User> users) {
        saveEntities(USERS_FILE, users, User::toCSV);
    }

    // ── Transaction persistence ───────────────────────────────────────────
    public static List<Transaction> loadTransactions() {
        return loadEntities(TXN_FILE, Transaction::fromCSV);
    }

    public static void saveTransactions(Collection<Transaction> txns) {
        saveEntities(TXN_FILE, txns, Transaction::toCSV);
    }

    // ── Activity log ──────────────────────────────────────────────────────
    public static void appendLog(String logLine) {
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(LOG_FILE, true))) {
            bw.write(logLine);
            bw.newLine();
        } catch (IOException e) {
            System.err.println("[WARN] Could not write log: " + e.getMessage());
        }
    }

    public static List<String> loadLogs() {
        try {
            return Files.readAllLines(Paths.get(LOG_FILE));
        } catch (IOException e) {
            return new ArrayList<>();
        }
    }

    // ── Undo stack ────────────────────────────────────────────────────────
    public static void pushUndo(String snapshotLine) {
        List<String> lines = new ArrayList<>();
        try { lines = new ArrayList<>(Files.readAllLines(Paths.get(UNDO_FILE))); }
        catch (IOException ignored) {}
        lines.add(snapshotLine);
        if (lines.size() > 20) lines.remove(0); // keep last 20
        try { Files.write(Paths.get(UNDO_FILE), lines); }
        catch (IOException e) { System.err.println("[WARN] Undo write failed"); }
    }

    public static String popUndo() {
        try {
            List<String> lines = new ArrayList<>(Files.readAllLines(Paths.get(UNDO_FILE)));
            if (lines.isEmpty()) return null;
            String last = lines.remove(lines.size() - 1);
            Files.write(Paths.get(UNDO_FILE), lines);
            return last;
        } catch (IOException e) { return null; }
    }

    // ── Auto backup ───────────────────────────────────────────────────────
    public static void backup() {
        String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        try {
            for (String src : new String[]{BOOKS_FILE, USERS_FILE, TXN_FILE}) {
                Path srcPath = Paths.get(src);
                String filename = srcPath.getFileName().toString().replace(".txt", "");
                Files.copy(srcPath,
                    Paths.get(BACKUP_DIR + filename + "_" + ts + ".txt"),
                    StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            System.err.println("[WARN] Backup failed: " + e.getMessage());
        }
    }

    public static void restoreFromBackup(String timestamp) throws IOException {
        for (String base : new String[]{"books", "members", "transactions"}) {
            Path backup = Paths.get(BACKUP_DIR + base + "_" + timestamp + ".txt");
            Path target = Paths.get(DATA_DIR + base + ".txt");
            if (Files.exists(backup)) {
                Files.copy(backup, target, StandardCopyOption.REPLACE_EXISTING);
            } else {
                throw new IOException("Backup file not found: " + backup);
            }
        }
    }

    public static List<String> listBackups() {
        try {
            return Files.list(Paths.get(BACKUP_DIR))
                .map(p -> p.getFileName().toString())
                .filter(n -> n.endsWith(".txt"))
                .sorted(Comparator.reverseOrder())
                .toList();
        } catch (IOException e) {
            return new ArrayList<>();
        }
    }

    // ── Export ────────────────────────────────────────────────────────────
    public static void exportReport(String filename, String content) throws IOException {
        Files.writeString(Paths.get(DATA_DIR + filename), content);
    }

    // ── Generic helpers ───────────────────────────────────────────────────
    @FunctionalInterface interface Parser<T> { T parse(String line); }
    @FunctionalInterface interface Serializer<T> { String serialize(T obj); }

    private static <T> List<T> loadEntities(String path, Parser<T> parser) {
        List<T> list = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(path))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty() && !line.startsWith("#")) {
                    try { list.add(parser.parse(line)); }
                    catch (Exception e) {
                        System.err.println("[WARN] Skipping corrupt line in " + path + ": " + e.getMessage());
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("[WARN] Could not read " + path + ": " + e.getMessage());
        }
        return list;
    }

    private static <T> void saveEntities(String path, Collection<T> items, Serializer<T> s) {
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(path, false))) {
            for (T item : items) {
                bw.write(s.serialize(item));
                bw.newLine();
            }
        } catch (IOException e) {
            System.err.println("[ERROR] Could not save to " + path + ": " + e.getMessage());
        }
    }
}
