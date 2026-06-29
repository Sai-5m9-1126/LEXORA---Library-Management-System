package lexora.model;

import java.util.ArrayList;
import java.util.List;

public class User {

    public enum Role { ADMIN, LIBRARIAN, MEMBER, STUDENT }

    // ── Identity ──────────────────────────────────────────────────────────
    private String userId;
    private String name;
    private String email;
    private String phone;
    private String passwordHash;
    private Role   role;

    // ── Membership ────────────────────────────────────────────────────────
    private String memberSince;     // yyyy-MM-dd
    private String membershipId;
    private boolean active;

    // ── Borrow tracking ───────────────────────────────────────────────────
    private List<String> currentBorrows;   // transactionIds
    private List<String> borrowHistory;    // transactionIds
    private List<String> reservations;     // bookIsbns
    private List<String> favorites;        // bookIsbns
    private List<String> wishlist;         // bookIsbns
    private int totalBorrows;
    private double totalFinesPaid;

    // ── Gamification ──────────────────────────────────────────────────────
    private int readingStreak;
    private int readingGoal;        // books per month
    private int points;
    private List<String> badges;
    private int readingProgress;    // pages (optional tracking)

    // ── Notification flags ────────────────────────────────────────────────
    private List<String> notifications;

    public User() {
        currentBorrows  = new ArrayList<>();
        borrowHistory   = new ArrayList<>();
        reservations    = new ArrayList<>();
        favorites       = new ArrayList<>();
        wishlist        = new ArrayList<>();
        badges          = new ArrayList<>();
        notifications   = new ArrayList<>();
        active          = true;
    }

    public User(String userId, String name, String email, String phone,
                String passwordHash, Role role, String memberSince) {
        this();
        this.userId       = userId;
        this.name         = name;
        this.email        = email;
        this.phone        = phone;
        this.passwordHash = passwordHash;
        this.role         = role;
        this.memberSince  = memberSince;
        this.membershipId = "LX-" + userId.toUpperCase();
    }

    // ── Borrow limit logic ────────────────────────────────────────────────
    public int getBorrowLimit() {
        return switch (role) {
            case ADMIN     -> 20;
            case LIBRARIAN -> 10;
            case MEMBER    -> 5;
            case STUDENT   -> 3;
        };
    }

    public boolean canBorrow() {
        return active && currentBorrows.size() < getBorrowLimit();
    }

    public boolean hasReserved(String isbn) {
        return reservations.contains(isbn);
    }

    // ── CSV serialisation ─────────────────────────────────────────────────
    public String toCSV() {
        return String.join("|",
            userId, name, email, phone, passwordHash, role.name(),
            memberSince, membershipId, String.valueOf(active),
            String.join(",", currentBorrows),
            String.join(",", borrowHistory),
            String.join(",", reservations),
            String.join(",", favorites),
            String.join(",", wishlist),
            String.valueOf(totalBorrows),
            String.valueOf(totalFinesPaid),
            String.valueOf(readingStreak),
            String.valueOf(readingGoal),
            String.valueOf(points),
            String.join(",", badges),
            String.join(";", notifications)
        );
    }

    public static User fromCSV(String line) {
        String[] p = line.split("\\|", -1);
        User u = new User();
        u.userId       = p[0];
        u.name         = p[1];
        u.email        = p[2];
        u.phone        = p[3];
        u.passwordHash = p[4];
        u.role         = Role.valueOf(p[5]);
        u.memberSince  = p[6];
        u.membershipId = p[7];
        u.active       = Boolean.parseBoolean(p[8]);
        if (!p[9].isEmpty())  for (String x : p[9].split(","))  u.currentBorrows.add(x);
        if (!p[10].isEmpty()) for (String x : p[10].split(",")) u.borrowHistory.add(x);
        if (!p[11].isEmpty()) for (String x : p[11].split(",")) u.reservations.add(x);
        if (!p[12].isEmpty()) for (String x : p[12].split(",")) u.favorites.add(x);
        if (!p[13].isEmpty()) for (String x : p[13].split(",")) u.wishlist.add(x);
        u.totalBorrows   = Integer.parseInt(p[14]);
        u.totalFinesPaid = Double.parseDouble(p[15]);
        u.readingStreak  = Integer.parseInt(p[16]);
        u.readingGoal    = Integer.parseInt(p[17]);
        u.points         = Integer.parseInt(p[18]);
        if (!p[19].isEmpty()) for (String x : p[19].split(",")) u.badges.add(x);
        if (!p[20].isEmpty()) for (String x : p[20].split(";")) u.notifications.add(x);
        return u;
    }

    // ── Getters ───────────────────────────────────────────────────────────
    public String getUserId()            { return userId; }
    public String getName()             { return name; }
    public String getEmail()            { return email; }
    public String getPhone()            { return phone; }
    public String getPasswordHash()     { return passwordHash; }
    public Role   getRole()             { return role; }
    public String getMemberSince()      { return memberSince; }
    public String getMembershipId()     { return membershipId; }
    public boolean isActive()           { return active; }
    public List<String> getCurrentBorrows()  { return currentBorrows; }
    public List<String> getBorrowHistory()   { return borrowHistory; }
    public List<String> getReservations()    { return reservations; }
    public List<String> getFavorites()       { return favorites; }
    public List<String> getWishlist()        { return wishlist; }
    public int    getTotalBorrows()     { return totalBorrows; }
    public double getTotalFinesPaid()   { return totalFinesPaid; }
    public int    getReadingStreak()    { return readingStreak; }
    public int    getReadingGoal()      { return readingGoal; }
    public int    getPoints()           { return points; }
    public List<String> getBadges()     { return badges; }
    public List<String> getNotifications() { return notifications; }

    // ── Setters ───────────────────────────────────────────────────────────
    public void setName(String n)        { this.name  = n; }
    public void setEmail(String e)       { this.email = e; }
    public void setPhone(String p)       { this.phone = p; }
    public void setPasswordHash(String h){ this.passwordHash = h; }
    public void setActive(boolean a)     { this.active = a; }
    public void setReadingGoal(int g)    { this.readingGoal = g; }
    public void addPoints(int pts)       { this.points += pts; }
    public void incrementStreak()        { this.readingStreak++; }
    public void resetStreak()            { this.readingStreak = 0; }
    public void incrementTotalBorrows()  { this.totalBorrows++; }
    public void addFinePaid(double amt)  { this.totalFinesPaid += amt; }

    public void addNotification(String msg) { notifications.add(0, msg); }
    public void clearNotifications()        { notifications.clear(); }

    @Override
    public String toString() {
        return String.format("[%s] %s (%s) | %s | Borrows: %d/%d",
            userId, name, role, email, currentBorrows.size(), getBorrowLimit());
    }
}
