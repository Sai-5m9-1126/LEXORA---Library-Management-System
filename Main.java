package lexora;

import lexora.model.Library;
import lexora.ui.ConsoleUI;
import lexora.ui.MenuController;

/**
 * Lexora v1.0 — Main Entry Point
 * ────────────────────────────────
 * Boots the library system:
 *   1. Initialise storage directories
 *   2. Load all data into memory
 *   3. Seed demo data if first run
 *   4. Hand off to MenuController
 */
public class Main {

    public static void main(String[] args) {
        try {
            // Load library data from disk
            Library lib = Library.getInstance();
            lib.load();

            // Seed demo data if no users exist (first run)
            if (lib.getAllUsers().isEmpty()) {
                DemoDataSeeder.seed(lib);
                ConsoleUI.info("First run: demo data loaded.");
            }

            // Hand off to UI controller
            new MenuController().start();

        } catch (Exception e) {
            System.err.println("FATAL ERROR: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
