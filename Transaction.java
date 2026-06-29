package lexora.model;

public class Transaction {

    public enum Type   { BORROW, RETURN, RESERVE, CANCEL_RESERVE }
    public enum Status { ACTIVE, RETURNED, OVERDUE, CANCELLED }

    private String transactionId;
    private String userId;
    private String isbn;
    private Type   type;
    private Status status;

    private String borrowDate;   // yyyy-MM-dd
    private String dueDate;      // yyyy-MM-dd
    private String returnDate;   // yyyy-MM-dd (null if not returned)

    private long   overdueDays;
    private double fineAmount;
    private boolean finePaid;

    public Transaction() {}

    public Transaction(String transactionId, String userId, String isbn,
                       Type type, String borrowDate, String dueDate) {
        this.transactionId = transactionId;
        this.userId        = userId;
        this.isbn          = isbn;
        this.type          = type;
        this.status        = Status.ACTIVE;
        this.borrowDate    = borrowDate;
        this.dueDate       = dueDate;
        this.fineAmount    = 0.0;
        this.finePaid      = false;
    }

    // ── CSV ───────────────────────────────────────────────────────────────
    public String toCSV() {
        return String.join("|",
            transactionId, userId, isbn, type.name(), status.name(),
            borrowDate,
            dueDate    == null ? "" : dueDate,
            returnDate == null ? "" : returnDate,
            String.valueOf(overdueDays),
            String.valueOf(fineAmount),
            String.valueOf(finePaid)
        );
    }

    public static Transaction fromCSV(String line) {
        String[] p = line.split("\\|", -1);
        Transaction t = new Transaction();
        t.transactionId = p[0];
        t.userId        = p[1];
        t.isbn          = p[2];
        t.type          = Type.valueOf(p[3]);
        t.status        = Status.valueOf(p[4]);
        t.borrowDate    = p[5];
        t.dueDate       = p[6].isEmpty() ? null : p[6];
        t.returnDate    = p[7].isEmpty() ? null : p[7];
        t.overdueDays   = Long.parseLong(p[8]);
        t.fineAmount    = Double.parseDouble(p[9]);
        t.finePaid      = Boolean.parseBoolean(p[10]);
        return t;
    }

    // ── Getters / Setters ─────────────────────────────────────────────────
    public String getTransactionId() { return transactionId; }
    public String getUserId()        { return userId; }
    public String getIsbn()          { return isbn; }
    public Type   getType()          { return type; }
    public Status getStatus()        { return status; }
    public String getBorrowDate()    { return borrowDate; }
    public String getDueDate()       { return dueDate; }
    public String getReturnDate()    { return returnDate; }
    public long   getOverdueDays()   { return overdueDays; }
    public double getFineAmount()    { return fineAmount; }
    public boolean isFinePaid()      { return finePaid; }

    public void setStatus(Status s)      { this.status     = s; }
    public void setReturnDate(String d)  { this.returnDate = d; }
    public void setOverdueDays(long d)   { this.overdueDays = d; }
    public void setFineAmount(double f)  { this.fineAmount  = f; }
    public void setFinePaid(boolean b)   { this.finePaid    = b; }

    @Override
    public String toString() {
        return String.format("TXN[%s] User:%s Book:%s | %s | Due:%s | Fine:₹%.2f",
            transactionId, userId, isbn, status, dueDate, fineAmount);
    }
}
