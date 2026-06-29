# 📚 LEXORA v1.0 — Library Management System
**Developed by Sai Haarshith** | Core Java | Production-Grade Architecture

---

## 🏗️ System Architecture

```
lexora/
├── src/
│   └── lexora/
│       ├── Main.java                    ← Entry point + bootstrap
│       ├── DemoDataSeeder.java          ← First-run seed data
│       │
│       ├── model/                       ← Domain entities (pure data + state)
│       │   ├── Book.java
│       │   ├── User.java
│       │   ├── Transaction.java
│       │   └── Library.java             ← Central in-memory registry (Singleton)
│       │
│       ├── service/                     ← Business logic (one responsibility each)
│       │   ├── BorrowService.java       ← borrowBook() — full 12-step flow
│       │   ├── ReturnService.java       ← returnBook() — fine + auto-assign
│       │   ├── ReservationService.java  ← FIFO queue reserve/cancel
│       │   ├── FineManager.java         ← Calculate, record, pay fines
│       │   ├── AchievementService.java  ← Badge/gamification logic
│       │   └── RecommendationService.java ← Smart book recommendations
│       │
│       ├── storage/
│       │   └── FileStorageManager.java  ← ALL file I/O in one place
│       │
│       ├── auth/
│       │   └── AuthSystem.java          ← Login, logout, session, RBAC
│       │
│       ├── report/
│       │   └── ReportGenerator.java     ← Analytics, receipts, exports
│       │
│       ├── ui/
│       │   ├── ConsoleUI.java           ← ANSI rendering, tables, prompts
│       │   └── MenuController.java      ← All menu routing & user interactions
│       │
│       └── util/
│           ├── LexoraUtils.java         ← ISBN validation, dates, hashing, IDs
│           └── ActivityLogger.java      ← Timestamped audit trail
│
├── data/                                ← Auto-created at runtime
│   ├── books.txt
│   ├── members.txt
│   ├── transactions.txt
│   ├── logs.txt
│   ├── undo.txt
│   └── backups/
│
└── run.sh                               ← Build & launch script
```

---

## ⚙️ Setup & Run

```bash
# Requirements: Java 17+ JDK
# Install on Ubuntu/Debian:
sudo apt install default-jdk

# Install on Mac:
brew install openjdk@21

# Build and run:
cd lexora
chmod +x run.sh
./run.sh
```

**Or manually:**
```bash
mkdir -p out
javac -d out -sourcepath src $(find src -name "*.java")
java -cp out lexora.Main
```

---

## 🔐 Default Login Credentials

| Role      | User ID   | Password  |
|-----------|-----------|-----------|
| Admin     | admin001  | admin123  |
| Librarian | lib001    | lib123    |
| Student   | stu001    | pass123   |
| Student   | stu002    | pass123   |
| Member    | mem001    | pass123   |

---

## 🔄 Critical Logic Flows

### borrowBook(userId, isbn)
```
Step 1  → Validate user exists + is active
Step 2  → Validate book exists + not archived
Step 3  → Check borrow limit (STUDENT=3, MEMBER=5, LIBRARIAN=10, ADMIN=20)
Step 4  → Check no duplicate active borrow
Step 5  → Check book.availableCopies > 0  ← synchronized block
Step 6  → Compute dueDate = today + 14 days
Step 7  → Create Transaction (ACTIVE)
Step 8  → Book: availableCopies--, borrowedBy=userId, dueDate=dueDate
Step 9  → User: currentBorrows+=txnId, totalBorrows++, points+=10
Step 10 → Persist: memory → disk (Library.persist())
Step 11 → Push undo snapshot
Step 12 → Audit log
```

### returnBook(userId, isbn)
```
Step 1  → Validate user + book
Step 2  → Find active BORROW transaction for user+isbn
Step 3  → Calculate overdueDays = max(0, today - dueDate)
          fineAmount = overdueDays × ₹2.00
Step 4  → Transaction: status=RETURNED, returnDate, overdueDays, fineAmount
Step 5  → Book: availableCopies++, borrowedBy=null, dueDate=null
Step 6  → User: currentBorrows.remove(txnId), streak update
Step 7  → Fine notification if fineAmount > 0
Step 8  → Check reservationQueue[isbn] → auto-borrow for next user
Step 9  → Persist all
Step 10 → Audit log
```

### reserveBook(userId, isbn)
```
Step 1  → Validate user + book
Step 2  → Book must NOT be available (else: borrow directly)
Step 3  → User must NOT already have reservation
Step 4  → Max 3 active reservations per user
Step 5  → reservationQueues[isbn].offer(userId)  ← FIFO Deque
Step 6  → Create RESERVE Transaction
Step 7  → User: reservations+=isbn, notification with queue position
Step 8  → Persist
Step 9  → Log
```

### calculateFine(dueDate)
```
overdueDays = DAYS.between(dueDate, today)
if overdueDays <= 0 → ₹0.00 (on time or early)
else                → overdueDays × ₹2.00

Edge cases:
  Returned on exact due date → 0 days → ₹0
  Returned 1 day early       → negative days → clamped to 0 → ₹0
  Returned 5 days late       → 5 × ₹2 = ₹10
```

---

## 📊 State Transition Tables

### Book State
| Event         | availableCopies | status    | borrowedBy | dueDate  |
|---------------|----------------|-----------|------------|----------|
| Initial       | N              | AVAILABLE | null       | null     |
| borrowBook()  | N-1            | BORROWED* | userId     | +14 days |
| returnBook()  | N              | AVAILABLE | null       | null     |
| archiveBook() | unchanged      | ARCHIVED  | unchanged  | unchanged|

*BORROWED only if availableCopies reaches 0

### Transaction State
| Event           | status    | returnDate | fineAmount |
|-----------------|-----------|------------|------------|
| borrowBook()    | ACTIVE    | null       | 0          |
| (overdue check) | OVERDUE   | null       | accumulating|
| returnBook()    | RETURNED  | today      | calculated |
| payFine()       | RETURNED  | today      | paid=true  |

---

## 🌟 Features Implemented

### Core (100%)
- [x] Book CRUD (add/update/archive/search/sort)
- [x] Member CRUD (register/update/deactivate)
- [x] Borrow system with full validation
- [x] Return system with fine calculation
- [x] Reservation queue (FIFO)
- [x] Fine management + payment

### Advanced
- [x] ISBN-13 & ISBN-10 validation with check-digit
- [x] Duplicate detection (book + borrow)
- [x] Borrow limits by role
- [x] Borrow history tracking
- [x] Activity logger (every action)
- [x] Auto backup on login
- [x] Data restore from backup
- [x] Undo snapshot stack
- [x] Advanced search (ISBN/author/title/category/year)
- [x] Multi-field sorting
- [x] Library health score (0-100)
- [x] Session history tracking
- [x] Overdue auto-detection on login

### Premium
- [x] Smart recommendations (category + history)
- [x] Book ratings (1-5 stars, averaged)
- [x] Favourites system
- [x] Wishlist system
- [x] Achievement/badge system
- [x] Notification center
- [x] Monthly analytics reports
- [x] Export reports to file
- [x] Login system (Admin/Librarian/Student/Member, RBAC)
- [x] Digital membership card
- [x] Hall of Fame (top readers)
- [x] Reading goals
- [x] Borrow receipt generator
- [x] Book archiving system
- [x] Reading streak tracking

---

## 🎨 Console UI
- ANSI color system (Green=success, Red=error, Yellow=warn, Blue=info, Purple=header)
- ASCII banner on startup
- Beautiful ASCII tables for books, members, transactions
- Dashboard widget with live stats
- Notification panel on login
- Membership card display
- Confirmation dialogs
- Breadcrumb-style headers
- Footer branding

---

*Lexora v1.0 — Developed by Sai Haarshith*
