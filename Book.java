package lexora.model;

import java.util.ArrayList;
import java.util.List;

public class Book {
    // ── Identity ──────────────────────────────────────────────────────────
    private String isbn;
    private String title;
    private String author;
    private String category;
    private int year;
    private String publisher;
    private int totalCopies;

    // ── State ─────────────────────────────────────────────────────────────
    public enum Status { AVAILABLE, BORROWED, RESERVED, ARCHIVED }
    private Status status;
    private int availableCopies;
    private String borrowedBy;      // userId currently holding a copy
    private String dueDate;         // ISO date string yyyy-MM-dd

    // ── Metadata ──────────────────────────────────────────────────────────
    private double rating;          // average 1-5
    private int ratingCount;
    private int totalBorrows;
    private List<String> favorites; // userIds who favourited
    private List<String> wishlist;  // userIds on wishlist
    private boolean archived;

    public Book() {
        this.status = Status.AVAILABLE;
        this.favorites = new ArrayList<>();
        this.wishlist = new ArrayList<>();
        this.rating = 0.0;
        this.ratingCount = 0;
        this.totalBorrows = 0;
        this.archived = false;
    }

    public Book(String isbn, String title, String author, String category,
                int year, String publisher, int totalCopies) {
        this();
        this.isbn = isbn;
        this.title = title;
        this.author = author;
        this.category = category;
        this.year = year;
        this.publisher = publisher;
        this.totalCopies = totalCopies;
        this.availableCopies = totalCopies;
    }

    // ── State helpers ─────────────────────────────────────────────────────
    public boolean isAvailable()  { return availableCopies > 0 && !archived; }
    public boolean isArchived()   { return archived; }

    public void decrementCopy() {
        availableCopies--;
        if (availableCopies == 0) status = Status.BORROWED;
    }

    public void incrementCopy() {
        availableCopies++;
        if (availableCopies > 0) status = Status.AVAILABLE;
    }

    public void addRating(int stars) {
        rating = ((rating * ratingCount) + stars) / (++ratingCount);
    }

    // ── CSV serialisation ─────────────────────────────────────────────────
    public String toCSV() {
        return String.join("|",
            isbn, title, author, category,
            String.valueOf(year), publisher,
            String.valueOf(totalCopies),
            String.valueOf(availableCopies),
            status.name(),
            borrowedBy == null ? "" : borrowedBy,
            dueDate    == null ? "" : dueDate,
            String.valueOf(rating),
            String.valueOf(ratingCount),
            String.valueOf(totalBorrows),
            String.join(",", favorites),
            String.join(",", wishlist),
            String.valueOf(archived)
        );
    }

    public static Book fromCSV(String line) {
        String[] p = line.split("\\|", -1);
        Book b = new Book();
        b.isbn            = p[0];
        b.title           = p[1];
        b.author          = p[2];
        b.category        = p[3];
        b.year            = Integer.parseInt(p[4]);
        b.publisher       = p[5];
        b.totalCopies     = Integer.parseInt(p[6]);
        b.availableCopies = Integer.parseInt(p[7]);
        b.status          = Status.valueOf(p[8]);
        b.borrowedBy      = p[9].isEmpty()  ? null : p[9];
        b.dueDate         = p[10].isEmpty() ? null : p[10];
        b.rating          = Double.parseDouble(p[11]);
        b.ratingCount     = Integer.parseInt(p[12]);
        b.totalBorrows    = Integer.parseInt(p[13]);
        if (!p[14].isEmpty()) for (String f : p[14].split(",")) b.favorites.add(f);
        if (!p[15].isEmpty()) for (String w : p[15].split(",")) b.wishlist.add(w);
        b.archived        = Boolean.parseBoolean(p[16]);
        return b;
    }

    // ── Getters / Setters ─────────────────────────────────────────────────
    public String getIsbn()            { return isbn; }
    public String getTitle()           { return title; }
    public String getAuthor()          { return author; }
    public String getCategory()        { return category; }
    public int    getYear()            { return year; }
    public String getPublisher()       { return publisher; }
    public int    getTotalCopies()     { return totalCopies; }
    public int    getAvailableCopies() { return availableCopies; }
    public Status getStatus()          { return status; }
    public String getBorrowedBy()      { return borrowedBy; }
    public String getDueDate()         { return dueDate; }
    public double getRating()          { return rating; }
    public int    getRatingCount()     { return ratingCount; }
    public int    getTotalBorrows()    { return totalBorrows; }
    public List<String> getFavorites() { return favorites; }
    public List<String> getWishlist()  { return wishlist; }

    public void setTitle(String t)          { this.title     = t; }
    public void setAuthor(String a)         { this.author    = a; }
    public void setCategory(String c)       { this.category  = c; }
    public void setYear(int y)              { this.year      = y; }
    public void setPublisher(String p)      { this.publisher = p; }
    public void setTotalCopies(int n)       { this.totalCopies = n; }
    public void setAvailableCopies(int n)   { this.availableCopies = n; }
    public void setStatus(Status s)         { this.status    = s; }
    public void setBorrowedBy(String u)     { this.borrowedBy = u; }
    public void setDueDate(String d)        { this.dueDate   = d; }
    public void setArchived(boolean flag)   { this.archived  = flag; }
    public void incrementBorrows()          { this.totalBorrows++; }

    @Override
    public String toString() {
        return String.format("[%s] %s by %s (%d) — %s | Copies: %d/%d",
            isbn, title, author, year, status, availableCopies, totalCopies);
    }
}
