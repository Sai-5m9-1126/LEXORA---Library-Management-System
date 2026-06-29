package lexora;

import lexora.model.Book;
import lexora.model.Library;
import lexora.model.User;
import lexora.util.LexoraUtils;

/**
 * DemoDataSeeder
 * ───────────────
 * Seeds realistic books and users on first launch.
 * Admin login: admin001 / admin123
 */
public class DemoDataSeeder {

    public static void seed(Library lib) {
        // ── Admin user ────────────────────────────────────────────────────
        User admin = new User("admin001", "Sai Haarshith", "admin@lexora.in",
            "9876543210", LexoraUtils.hashPassword("admin123"),
            User.Role.ADMIN, LexoraUtils.today());
        lib.addUser(admin);

        // ── Librarian ─────────────────────────────────────────────────────
        User librarian = new User("lib001", "Priya Sharma", "priya@lexora.in",
            "9123456789", LexoraUtils.hashPassword("lib123"),
            User.Role.LIBRARIAN, LexoraUtils.today());
        lib.addUser(librarian);

        // ── Student members ───────────────────────────────────────────────
        lib.addUser(new User("stu001", "Arjun Reddy", "arjun@mail.com",
            "9000000001", LexoraUtils.hashPassword("pass123"),
            User.Role.STUDENT, LexoraUtils.today()));

        lib.addUser(new User("stu002", "Meera Iyer", "meera@mail.com",
            "9000000002", LexoraUtils.hashPassword("pass123"),
            User.Role.STUDENT, LexoraUtils.today()));

        lib.addUser(new User("mem001", "Rahul Gupta", "rahul@mail.com",
            "9000000003", LexoraUtils.hashPassword("pass123"),
            User.Role.MEMBER, LexoraUtils.today()));

        // ── Books ─────────────────────────────────────────────────────────
        lib.addBook(new Book("9780132350884", "Clean Code", "Robert C. Martin",
            "Computer Science", 2008, "Prentice Hall", 3));

        lib.addBook(new Book("9780201616224", "The Pragmatic Programmer", "Andrew Hunt",
            "Computer Science", 1999, "Addison-Wesley", 2));

        lib.addBook(new Book("9780743273565", "The Great Gatsby", "F. Scott Fitzgerald",
            "Fiction", 1925, "Scribner", 5));

        lib.addBook(new Book("9780451524935", "1984", "George Orwell",
            "Dystopian Fiction", 1949, "Signet Classic", 4));

        lib.addBook(new Book("9780062316097", "The Alchemist", "Paulo Coelho",
            "Fiction", 1988, "HarperOne", 3));

        lib.addBook(new Book("9780316769174", "The Catcher in the Rye", "J.D. Salinger",
            "Fiction", 1951, "Little, Brown", 2));

        lib.addBook(new Book("9780375842207", "Atomic Habits", "James Clear",
            "Self Help", 2018, "Avery", 4));

        lib.addBook(new Book("9780525559474", "The Psychology of Money", "Morgan Housel",
            "Finance", 2020, "Harriman House", 3));

        lib.addBook(new Book("9780307474278", "The Power of Now", "Eckhart Tolle",
            "Spirituality", 1997, "New World Library", 2));

        lib.addBook(new Book("9780679720201", "One Hundred Years of Solitude", "Gabriel Garcia Marquez",
            "Literary Fiction", 1967, "Vintage", 2));

        lib.addBook(new Book("9780385737951", "The Fault in Our Stars", "John Green",
            "Young Adult", 2012, "Dutton Books", 3));

        lib.addBook(new Book("9780439023481", "The Hunger Games", "Suzanne Collins",
            "Young Adult", 2008, "Scholastic", 4));

        System.out.println("[SEED] Demo data loaded: 5 users, 12 books.");
        System.out.println("[SEED] Admin login → ID: admin001 | Password: admin123");
        System.out.println("[SEED] Student login → ID: stu001  | Password: pass123");
    }
}
