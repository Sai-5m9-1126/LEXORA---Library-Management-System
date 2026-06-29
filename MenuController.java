package lexora.ui;

import lexora.auth.AuthSystem;
import lexora.model.*;
import lexora.report.ReportGenerator;
import lexora.service.*;
import lexora.storage.FileStorageManager;
import lexora.util.ActivityLogger;
import lexora.util.LexoraUtils;

import java.io.IOException;
import java.util.*;

/**
 * MenuController
 * ───────────────
 * Wires user input to the correct service call.
 * Each menu section is a private method for clean separation.
 */
public class MenuController {

    private final Scanner           sc      = new Scanner(System.in);
    private final Library           lib     = Library.getInstance();
    private final AuthSystem        auth    = new AuthSystem();
    private final BorrowService     borrowSvc  = new BorrowService();
    private final ReturnService     returnSvc  = new ReturnService();
    private final ReservationService resSvc = new ReservationService();
    private final FineManager       fineMgr = new FineManager();
    private final ReportGenerator   reports = new ReportGenerator();
    private final RecommendationService recSvc = new RecommendationService();

    // ── Entry point ───────────────────────────────────────────────────────
    public void start() {
        ConsoleUI.printBanner();
        loginLoop();
    }

    // ── Login ─────────────────────────────────────────────────────────────
    private void loginLoop() {
        while (true) {
            ConsoleUI.printHeader("LOGIN");
            String userId = ConsoleUI.prompt("User ID", sc);
            String pass   = ConsoleUI.prompt("Password", sc);

            ConsoleUI.loading("Authenticating");
            User user = auth.login(userId, pass);

            if (user == null) {
                ConsoleUI.error("Invalid credentials. Try again.");
                continue;
            }

            ConsoleUI.success("Welcome back, " + user.getName() + "! 👋");
            ConsoleUI.printNotifications(user);
            fineMgr.markOverdueTransactions(); // check overdue on login
            FileStorageManager.backup();       // auto backup on login

            mainMenu();

            auth.logout();
            ConsoleUI.info("Session ended. Goodbye!");
            ConsoleUI.printFooter();

            if (!ConsoleUI.confirm("Login again?", sc)) break;
        }
    }

    // ── Main menu ─────────────────────────────────────────────────────────
    private void mainMenu() {
        while (true) {
            ConsoleUI.printHeader("MAIN MENU");
            User u = AuthSystem.getCurrentUser();

            ConsoleUI.print("1. 📚 Books");
            ConsoleUI.print("2. 👥 Members");
            ConsoleUI.print("3. 📖 Borrow a Book");
            ConsoleUI.print("4. 📤 Return a Book");
            ConsoleUI.print("5. 🔖 Reserve a Book");
            ConsoleUI.print("6. 💰 Fines");
            ConsoleUI.print("7. 🌟 My Profile");
            ConsoleUI.print("8. 📊 Reports & Analytics");
            if (AuthSystem.isAdmin()) ConsoleUI.print("9. ⚙️  Admin Panel");
            ConsoleUI.print("0. 🚪 Logout");
            ConsoleUI.divider();

            int choice = ConsoleUI.promptInt("Choice", sc);
            switch (choice) {
                case 1 -> booksMenu();
                case 2 -> membersMenu();
                case 3 -> borrowMenu();
                case 4 -> returnMenu();
                case 5 -> reserveMenu();
                case 6 -> finesMenu();
                case 7 -> profileMenu(u);
                case 8 -> reportsMenu();
                case 9 -> { if (AuthSystem.isAdmin()) adminMenu(); }
                case 0 -> { return; }
                default -> ConsoleUI.error("Invalid choice.");
            }
        }
    }

    // ── Books menu ────────────────────────────────────────────────────────
    private void booksMenu() {
        while (true) {
            ConsoleUI.printHeader("BOOKS");
            ConsoleUI.print("1. View All Books");
            ConsoleUI.print("2. Search Books");
            ConsoleUI.print("3. View by Category");
            ConsoleUI.print("4. Sort Books");
            if (AuthSystem.isLibrarian()) {
                ConsoleUI.print("5. Add Book");
                ConsoleUI.print("6. Update Book");
                ConsoleUI.print("7. Delete / Archive Book");
            }
            ConsoleUI.print("8. Rate a Book");
            ConsoleUI.print("9. Add to Favourites");
            ConsoleUI.print("0. Back");
            ConsoleUI.divider();

            int ch = ConsoleUI.promptInt("Choice", sc);
            switch (ch) {
                case 1 -> ConsoleUI.printBooksTable(lib.getAllBooks());
                case 2 -> searchBooks();
                case 3 -> viewByCategory();
                case 4 -> sortBooks();
                case 5 -> { if (AuthSystem.isLibrarian()) addBook(); }
                case 6 -> { if (AuthSystem.isLibrarian()) updateBook(); }
                case 7 -> { if (AuthSystem.isLibrarian()) archiveBook(); }
                case 8 -> rateBook();
                case 9 -> addFavourite();
                case 0 -> { return; }
                default -> ConsoleUI.error("Invalid choice.");
            }
        }
    }

    private void searchBooks() {
        String q = ConsoleUI.prompt("Search (ISBN / title / author / category / year)", sc);
        List<Book> results = lib.searchBooks(q);
        ConsoleUI.info("Found " + results.size() + " result(s).");
        ConsoleUI.printBooksTable(results);
    }

    private void viewByCategory() {
        String cat = ConsoleUI.prompt("Category", sc);
        ConsoleUI.printBooksTable(lib.searchByCategory(cat));
    }

    private void sortBooks() {
        ConsoleUI.print("Sort by: title / author / year / rating / popular");
        String field = ConsoleUI.prompt("Field", sc);
        ConsoleUI.printBooksTable(lib.booksSortedBy(field));
    }

    private void addBook() {
        ConsoleUI.printHeader("ADD BOOK");
        String isbn      = ConsoleUI.prompt("ISBN", sc);
        if (!LexoraUtils.isValidISBN(isbn)) { ConsoleUI.error("Invalid ISBN."); return; }
        if (lib.bookExists(isbn))            { ConsoleUI.error("Book already exists."); return; }

        String title     = ConsoleUI.prompt("Title", sc);
        String author    = ConsoleUI.prompt("Author", sc);
        String category  = ConsoleUI.prompt("Category", sc);
        int    year      = ConsoleUI.promptInt("Year", sc);
        String publisher = ConsoleUI.prompt("Publisher", sc);
        int    copies    = ConsoleUI.promptInt("Number of copies", sc);

        if (!ConsoleUI.confirm("Add this book?", sc)) return;

        Book book = new Book(isbn, title, author, category, year, publisher, copies);
        lib.addBook(book);
        ActivityLogger.audit("ADD_BOOK | isbn=" + isbn + " | title=" + title);
        ConsoleUI.success("Book added successfully!");
    }

    private void updateBook() {
        String isbn = ConsoleUI.prompt("ISBN to update", sc);
        Book book = lib.getBook(isbn);
        if (book == null) { ConsoleUI.error("Book not found."); return; }

        ConsoleUI.print("Leave blank to keep current value.");
        String title  = ConsoleUI.prompt("Title [" + book.getTitle() + "]", sc);
        String author = ConsoleUI.prompt("Author [" + book.getAuthor() + "]", sc);
        String cat    = ConsoleUI.prompt("Category [" + book.getCategory() + "]", sc);

        if (!title.isEmpty())  book.setTitle(title);
        if (!author.isEmpty()) book.setAuthor(author);
        if (!cat.isEmpty())    book.setCategory(cat);

        lib.updateBook(book);
        ConsoleUI.success("Book updated.");
    }

    private void archiveBook() {
        String isbn = ConsoleUI.prompt("ISBN to archive", sc);
        Book book = lib.getBook(isbn);
        if (book == null) { ConsoleUI.error("Book not found."); return; }
        if (!ConsoleUI.confirm("Archive \"" + book.getTitle() + "\"?", sc)) return;
        book.setArchived(true);
        lib.updateBook(book);
        ConsoleUI.success("Book archived.");
    }

    private void rateBook() {
        String isbn = ConsoleUI.prompt("ISBN", sc);
        Book book = lib.getBook(isbn);
        if (book == null) { ConsoleUI.error("Book not found."); return; }
        int stars = ConsoleUI.promptInt("Rating (1-5)", sc);
        if (stars < 1 || stars > 5) { ConsoleUI.error("Rating must be 1-5."); return; }
        book.addRating(stars);
        lib.updateBook(book);
        ConsoleUI.success("Rated ⭐".repeat(stars));
    }

    private void addFavourite() {
        String isbn = ConsoleUI.prompt("ISBN", sc);
        Book book = lib.getBook(isbn);
        if (book == null) { ConsoleUI.error("Book not found."); return; }
        User u = AuthSystem.getCurrentUser();
        if (!u.getFavorites().contains(isbn)) {
            u.getFavorites().add(isbn);
            book.getFavorites().add(u.getUserId());
            lib.updateUser(u);
            lib.updateBook(book);
            ConsoleUI.success("Added to favourites ❤️");
        } else {
            ConsoleUI.warn("Already in favourites.");
        }
    }

    // ── Members menu ──────────────────────────────────────────────────────
    private void membersMenu() {
        AuthSystem.requireLibrarian();
        while (true) {
            ConsoleUI.printHeader("MEMBERS");
            ConsoleUI.print("1. View All Members");
            ConsoleUI.print("2. Search Member");
            ConsoleUI.print("3. Register Member");
            ConsoleUI.print("4. Update Member");
            ConsoleUI.print("5. Deactivate Member");
            ConsoleUI.print("0. Back");
            ConsoleUI.divider();

            int ch = ConsoleUI.promptInt("Choice", sc);
            switch (ch) {
                case 1 -> ConsoleUI.printUsersTable(lib.getAllUsers());
                case 2 -> searchMember();
                case 3 -> registerMember();
                case 4 -> updateMember();
                case 5 -> deactivateMember();
                case 0 -> { return; }
                default -> ConsoleUI.error("Invalid choice.");
            }
        }
    }

    private void searchMember() {
        String q = ConsoleUI.prompt("Enter user ID or name", sc).toLowerCase();
        lib.getAllUsers().stream()
            .filter(u -> u.getUserId().toLowerCase().contains(q)
                      || u.getName().toLowerCase().contains(q))
            .forEach(u -> ConsoleUI.print(u.toString()));
    }

    private void registerMember() {
        ConsoleUI.printHeader("REGISTER MEMBER");
        String name  = ConsoleUI.prompt("Full Name", sc);
        String email = ConsoleUI.prompt("Email", sc);
        if (!LexoraUtils.isValidEmail(email)) { ConsoleUI.error("Invalid email."); return; }
        String phone = ConsoleUI.prompt("Phone", sc);
        if (!LexoraUtils.isValidPhone(phone)) { ConsoleUI.error("Invalid phone."); return; }
        String roleStr = ConsoleUI.prompt("Role (STUDENT/MEMBER/LIBRARIAN)", sc);
        User.Role role;
        try { role = User.Role.valueOf(roleStr.toUpperCase()); }
        catch (Exception e) { ConsoleUI.error("Invalid role."); return; }

        String password = ConsoleUI.prompt("Password", sc);
        String userId = LexoraUtils.generateUserId(name);
        String hash   = LexoraUtils.hashPassword(password);

        User user = new User(userId, name, email, phone, hash, role, LexoraUtils.today());
        lib.addUser(user);

        ConsoleUI.success("Member registered! User ID: " + userId);
        ConsoleUI.printMembershipCard(user);
    }

    private void updateMember() {
        String uid  = ConsoleUI.prompt("User ID", sc);
        User user = lib.getUser(uid);
        if (user == null) { ConsoleUI.error("Not found."); return; }

        String name  = ConsoleUI.prompt("Name [" + user.getName() + "]", sc);
        String email = ConsoleUI.prompt("Email [" + user.getEmail() + "]", sc);
        String phone = ConsoleUI.prompt("Phone [" + user.getPhone() + "]", sc);

        if (!name.isEmpty())  user.setName(name);
        if (!email.isEmpty()) user.setEmail(email);
        if (!phone.isEmpty()) user.setPhone(phone);

        lib.updateUser(user);
        ConsoleUI.success("Member updated.");
    }

    private void deactivateMember() {
        String uid = ConsoleUI.prompt("User ID to deactivate", sc);
        User user = lib.getUser(uid);
        if (user == null) { ConsoleUI.error("Not found."); return; }
        if (ConsoleUI.confirm("Deactivate " + user.getName() + "?", sc)) {
            user.setActive(false);
            lib.updateUser(user);
            ConsoleUI.success("Member deactivated.");
        }
    }

    // ── Borrow menu ───────────────────────────────────────────────────────
    private void borrowMenu() {
        ConsoleUI.printHeader("BORROW A BOOK");
        User u    = AuthSystem.getCurrentUser();
        String isbn = ConsoleUI.prompt("ISBN", sc);

        Book book = lib.getBook(isbn);
        if (book != null) {
            ConsoleUI.print("Book: " + book.getTitle() + " by " + book.getAuthor());
            ConsoleUI.print("Available: " + book.getAvailableCopies() + "/" + book.getTotalCopies());
        }

        if (!ConsoleUI.confirm("Borrow this book?", sc)) return;

        ConsoleUI.loading("Processing");
        BorrowService.BorrowResult result = borrowSvc.borrowBook(u.getUserId(), isbn);

        if (result.success()) {
            ConsoleUI.success(result.message());
            System.out.println(reports.generateBorrowReceipt(result.transaction()));
        } else {
            ConsoleUI.error(result.message());
            // Suggest reservation if not available
            if (book != null && !book.isAvailable()) {
                if (ConsoleUI.confirm("Would you like to reserve this book instead?", sc)) {
                    var rr = resSvc.reserveBook(u.getUserId(), isbn);
                    if (rr.success()) ConsoleUI.success(rr.message());
                    else              ConsoleUI.error(rr.message());
                }
            }
        }
    }

    // ── Return menu ───────────────────────────────────────────────────────
    private void returnMenu() {
        ConsoleUI.printHeader("RETURN A BOOK");
        User u = AuthSystem.getCurrentUser();

        // Show current borrows
        if (!u.getCurrentBorrows().isEmpty()) {
            ConsoleUI.info("Your current borrows:");
            u.getCurrentBorrows().forEach(txnId -> {
                Transaction t = lib.getTransaction(txnId);
                if (t != null) ConsoleUI.print("  ISBN: " + t.getIsbn() + " | Due: " + t.getDueDate());
            });
        }

        String isbn = ConsoleUI.prompt("ISBN to return", sc);
        if (!ConsoleUI.confirm("Return this book?", sc)) return;

        ConsoleUI.loading("Processing return");
        ReturnService.ReturnResult result = returnSvc.returnBook(u.getUserId(), isbn);

        if (result.success()) {
            ConsoleUI.success(result.message());
            if (result.fineAmount() > 0)
                ConsoleUI.warn("Please pay your fine at the counter or via the Fines menu.");
        } else {
            ConsoleUI.error(result.message());
        }
    }

    // ── Reserve menu ──────────────────────────────────────────────────────
    private void reserveMenu() {
        ConsoleUI.printHeader("RESERVATIONS");
        ConsoleUI.print("1. Reserve a Book");
        ConsoleUI.print("2. Cancel Reservation");
        ConsoleUI.print("3. My Reservations");
        ConsoleUI.print("0. Back");

        int ch = ConsoleUI.promptInt("Choice", sc);
        User u = AuthSystem.getCurrentUser();

        switch (ch) {
            case 1 -> {
                String isbn = ConsoleUI.prompt("ISBN to reserve", sc);
                var r = resSvc.reserveBook(u.getUserId(), isbn);
                if (r.success()) ConsoleUI.success(r.message());
                else             ConsoleUI.error(r.message());
            }
            case 2 -> {
                String isbn = ConsoleUI.prompt("ISBN to cancel", sc);
                var r = resSvc.cancelReservation(u.getUserId(), isbn);
                if (r.success()) ConsoleUI.success(r.message());
                else             ConsoleUI.error(r.message());
            }
            case 3 -> {
                ConsoleUI.info("Your reservations:");
                if (u.getReservations().isEmpty()) ConsoleUI.print("  None.");
                u.getReservations().forEach(isbn -> {
                    Book b = lib.getBook(isbn);
                    List<String> q = lib.getReservationQueue(isbn);
                    int pos = q.indexOf(u.getUserId()) + 1;
                    ConsoleUI.print("  " + (b != null ? b.getTitle() : isbn) + " — Queue #" + pos);
                });
            }
        }
    }

    // ── Fines menu ────────────────────────────────────────────────────────
    private void finesMenu() {
        ConsoleUI.printHeader("FINES");
        User u = AuthSystem.getCurrentUser();

        ConsoleUI.print("1. View My Fines");
        ConsoleUI.print("2. Pay Fine");
        if (AuthSystem.isLibrarian()) ConsoleUI.print("3. All Unpaid Fines");
        ConsoleUI.print("0. Back");

        int ch = ConsoleUI.promptInt("Choice", sc);
        switch (ch) {
            case 1 -> {
                lib.getTransactionsForUser(u.getUserId()).stream()
                    .filter(t -> t.getFineAmount() > 0)
                    .forEach(t -> ConsoleUI.print(String.format(
                        "  %s | ISBN: %s | Fine: ₹%.2f | Paid: %s",
                        t.getTransactionId(), t.getIsbn(), t.getFineAmount(), t.isFinePaid())));
            }
            case 2 -> {
                String txnId = ConsoleUI.prompt("Transaction ID", sc);
                boolean paid = fineMgr.payFine(txnId);
                if (paid) ConsoleUI.success("Fine paid!");
                else      ConsoleUI.error("Transaction not found or already paid.");
            }
            case 3 -> {
                if (AuthSystem.isLibrarian()) {
                    ConsoleUI.printTransactionsTable(fineMgr.getUnpaidFines());
                    ConsoleUI.warn(String.format("Total pending: ₹%.2f", fineMgr.getTotalFinesPending()));
                }
            }
        }
    }

    // ── Profile menu ──────────────────────────────────────────────────────
    private void profileMenu(User u) {
        while (true) {
            ConsoleUI.printHeader("MY PROFILE");
            ConsoleUI.printMembershipCard(u);
            ConsoleUI.print("1. View Borrow History");
            ConsoleUI.print("2. My Favourites");
            ConsoleUI.print("3. My Wishlist");
            ConsoleUI.print("4. Recommendations");
            ConsoleUI.print("5. Set Reading Goal");
            ConsoleUI.print("6. View Notifications");
            ConsoleUI.print("7. Clear Notifications");
            ConsoleUI.print("8. View Badges");
            ConsoleUI.print("0. Back");
            ConsoleUI.divider();

            int ch = ConsoleUI.promptInt("Choice", sc);
            switch (ch) {
                case 1 -> {
                    ConsoleUI.info("Borrow history:");
                    u.getBorrowHistory().stream()
                        .map(lib::getTransaction).filter(Objects::nonNull)
                        .forEach(t -> ConsoleUI.print("  " + t.toString()));
                }
                case 2 -> {
                    ConsoleUI.info("Your favourites:");
                    u.getFavorites().stream().map(lib::getBook).filter(Objects::nonNull)
                        .forEach(b -> ConsoleUI.print("  " + b.getTitle() + " — " + b.getAuthor()));
                }
                case 3 -> {
                    ConsoleUI.info("Your wishlist:");
                    u.getWishlist().stream().map(lib::getBook).filter(Objects::nonNull)
                        .forEach(b -> ConsoleUI.print("  " + b.getTitle()));
                }
                case 4 -> {
                    ConsoleUI.printHeader("RECOMMENDATIONS FOR YOU");
                    ConsoleUI.printBooksTable(recSvc.recommend(u.getUserId(), 5));
                }
                case 5 -> {
                    int goal = ConsoleUI.promptInt("Monthly reading goal (books)", sc);
                    u.setReadingGoal(goal);
                    lib.updateUser(u);
                    ConsoleUI.success("Reading goal set: " + goal + " books/month!");
                }
                case 6 -> {
                    ConsoleUI.info("All notifications:");
                    u.getNotifications().forEach(n -> ConsoleUI.print("  " + n));
                }
                case 7 -> {
                    u.clearNotifications();
                    lib.updateUser(u);
                    ConsoleUI.success("Notifications cleared.");
                }
                case 8 -> {
                    ConsoleUI.info("Your badges:");
                    if (u.getBadges().isEmpty()) ConsoleUI.print("  No badges yet. Keep reading!");
                    u.getBadges().forEach(b -> ConsoleUI.print("  🏅 " + b));
                }
                case 0 -> { return; }
            }
        }
    }

    // ── Reports menu ──────────────────────────────────────────────────────
    private void reportsMenu() {
        ConsoleUI.printHeader("REPORTS & ANALYTICS");
        ConsoleUI.print("1. Dashboard Overview");
        ConsoleUI.print("2. Monthly Report");
        ConsoleUI.print("3. Top Readers (Hall of Fame)");
        ConsoleUI.print("4. Popular Categories");
        ConsoleUI.print("5. Popular Books");
        ConsoleUI.print("6. Top Rated Books");
        ConsoleUI.print("7. Export Report to File");
        ConsoleUI.print("8. View Activity Logs");
        ConsoleUI.print("0. Back");
        ConsoleUI.divider();

        int ch = ConsoleUI.promptInt("Choice", sc);
        switch (ch) {
            case 1 -> ConsoleUI.printDashboard(reports.getDashboardStats());
            case 2 -> System.out.println(reports.generateMonthlyReport());
            case 3 -> {
                ConsoleUI.printHeader("🏆 HALL OF FAME");
                reports.getTopReaders(10).forEach(u ->
                    ConsoleUI.print(String.format("  %-20s %d books | %d pts",
                        u.getName(), u.getTotalBorrows(), u.getPoints())));
            }
            case 4 -> {
                ConsoleUI.info("Popular categories:");
                reports.getPopularCategories().forEach((cat, cnt) ->
                    ConsoleUI.print(String.format("  %-20s %d borrows", cat, cnt)));
            }
            case 5 -> ConsoleUI.printBooksTable(recSvc.getPopular(10));
            case 6 -> ConsoleUI.printBooksTable(recSvc.getTopRated(10));
            case 7 -> {
                try {
                    reports.exportMonthlyReport();
                    ConsoleUI.success("Report exported to data/ folder.");
                } catch (IOException e) { ConsoleUI.error("Export failed: " + e.getMessage()); }
            }
            case 8 -> {
                ConsoleUI.info("Recent activity log:");
                ActivityLogger.getRecentLogs(20).forEach(l -> ConsoleUI.print("  " + l));
            }
        }
    }

    // ── Admin panel ───────────────────────────────────────────────────────
    private void adminMenu() {
        ConsoleUI.printHeader("⚙️  ADMIN PANEL");
        ConsoleUI.print("1. Backup Data");
        ConsoleUI.print("2. Restore from Backup");
        ConsoleUI.print("3. List Backups");
        ConsoleUI.print("4. Undo Last Operation");
        ConsoleUI.print("5. View Full Log");
        ConsoleUI.print("0. Back");
        ConsoleUI.divider();

        int ch = ConsoleUI.promptInt("Choice", sc);
        switch (ch) {
            case 1 -> { FileStorageManager.backup(); ConsoleUI.success("Backup created."); }
            case 2 -> {
                List<String> backups = FileStorageManager.listBackups();
                if (backups.isEmpty()) { ConsoleUI.warn("No backups found."); return; }
                ConsoleUI.info("Available backups:");
                backups.forEach(b -> ConsoleUI.print("  " + b));
                String ts = ConsoleUI.prompt("Enter timestamp (yyyyMMdd_HHmmss)", sc);
                try {
                    FileStorageManager.restoreFromBackup(ts);
                    lib.load();
                    ConsoleUI.success("Restored from backup: " + ts);
                } catch (IOException e) { ConsoleUI.error("Restore failed: " + e.getMessage()); }
            }
            case 3 -> FileStorageManager.listBackups().forEach(b -> ConsoleUI.print("  " + b));
            case 4 -> {
                String undo = FileStorageManager.popUndo();
                if (undo == null) { ConsoleUI.warn("Nothing to undo."); return; }
                ConsoleUI.info("Last operation: " + undo);
                ConsoleUI.warn("Full undo requires manual data correction (safety feature).");
            }
            case 5 -> ActivityLogger.getLogs().forEach(l -> ConsoleUI.print(l));
        }
    }
}
