package lexora.util;

import lexora.storage.FileStorageManager;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * ActivityLogger
 * Writes a timestamped entry for every system action.
 * Format: [yyyy-MM-dd HH:mm:ss] [LEVEL] [USER] MESSAGE
 */
public class ActivityLogger {

    private static final DateTimeFormatter FMT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public enum Level { INFO, WARN, ERROR, AUDIT }

    private static String currentUser = "SYSTEM";

    public static void setCurrentUser(String userId) {
        currentUser = userId;
    }

    public static void log(Level level, String message) {
        String entry = String.format("[%s] [%s] [%s] %s",
            LocalDateTime.now().format(FMT), level, currentUser, message);
        FileStorageManager.appendLog(entry);
    }

    public static void info(String msg)  { log(Level.INFO,  msg); }
    public static void warn(String msg)  { log(Level.WARN,  msg); }
    public static void error(String msg) { log(Level.ERROR, msg); }
    public static void audit(String msg) { log(Level.AUDIT, msg); }

    public static List<String> getLogs()  { return FileStorageManager.loadLogs(); }

    /** Returns last N log lines */
    public static List<String> getRecentLogs(int n) {
        List<String> all = getLogs();
        int from = Math.max(0, all.size() - n);
        return all.subList(from, all.size());
    }
}
