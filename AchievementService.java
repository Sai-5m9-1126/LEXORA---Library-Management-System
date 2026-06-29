package lexora.service;

import lexora.model.User;

/**
 * AchievementService
 * ───────────────────
 * Awards badges based on user milestones.
 */
public class AchievementService {

    public static void checkAndAward(User user) {
        int total = user.getTotalBorrows();
        int streak = user.getReadingStreak();

        awardIf(user, "FIRST_BORROW",   total >= 1,   "📖 First Borrow");
        awardIf(user, "BOOKWORM",        total >= 10,  "🐛 Bookworm");
        awardIf(user, "AVID_READER",     total >= 25,  "📚 Avid Reader");
        awardIf(user, "BIBLIOPHILE",     total >= 50,  "🏆 Bibliophile");
        awardIf(user, "STREAK_3",        streak >= 3,  "🔥 3-Day Streak");
        awardIf(user, "STREAK_7",        streak >= 7,  "⚡ Week Streak");
        awardIf(user, "CENTURY",         total >= 100, "💯 Century Reader");
    }

    private static void awardIf(User user, String badge, boolean condition, String label) {
        if (condition && !user.getBadges().contains(badge)) {
            user.getBadges().add(badge);
            user.addNotification("🏅 Badge earned: " + label + "!");
        }
    }
}
