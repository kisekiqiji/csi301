package com.xjtlu.campusspace.data;

import androidx.annotation.NonNull;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

/** Adds local account credentials without altering legacy authors or their notes. */
public final class AuthMigration {
    private AuthMigration() { }

    public static final String[] ALTER_DDL = {
            "ALTER TABLE `users` ADD COLUMN `username` TEXT COLLATE NOCASE",
            "ALTER TABLE `users` ADD COLUMN `password_hash` TEXT",
            "ALTER TABLE `users` ADD COLUMN `password_salt` TEXT",
            "ALTER TABLE `users` ADD COLUMN `password_algorithm` TEXT",
            "ALTER TABLE `users` ADD COLUMN `password_iterations` INTEGER NOT NULL DEFAULT 0",
            "ALTER TABLE `users` ADD COLUMN `created_at` INTEGER NOT NULL DEFAULT 0",
            "ALTER TABLE `users` ADD COLUMN `failed_attempts` INTEGER NOT NULL DEFAULT 0",
            "ALTER TABLE `users` ADD COLUMN `locked_until` INTEGER NOT NULL DEFAULT 0",
            "ALTER TABLE `users` ADD COLUMN `rules_accepted_at` INTEGER NOT NULL DEFAULT 0",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_users_username` ON `users` (`username`)"
    };

    public static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override public void migrate(@NonNull SupportSQLiteDatabase db) {
            for (String sql : ALTER_DDL) db.execSQL(sql);
            installTriggers(db);
        }
    };

    public static void installTriggers(SupportSQLiteDatabase db) {
        String invalid = "NEW.password_iterations < 0 OR NEW.failed_attempts < 0 OR NEW.failed_attempts > 5"
                + " OR NEW.created_at < 0 OR NEW.locked_until < 0 OR NEW.rules_accepted_at < 0"
                + " OR (NEW.username IS NULL AND (NEW.password_hash IS NOT NULL OR NEW.password_salt IS NOT NULL"
                + " OR NEW.password_algorithm IS NOT NULL OR NEW.password_iterations != 0))"
                + " OR (NEW.username IS NOT NULL AND (length(NEW.username) < 3 OR length(NEW.username) > 24"
                + " OR NEW.username GLOB '*[^A-Za-z0-9_]*' OR length(trim(NEW.display_name)) < 1"
                + " OR length(trim(NEW.display_name)) > 24 OR NEW.password_hash IS NULL OR length(NEW.password_hash) != 64"
                + " OR NEW.password_hash GLOB '*[^0-9a-f]*' OR NEW.password_salt IS NULL OR length(NEW.password_salt) != 32"
                + " OR NEW.password_salt GLOB '*[^0-9a-f]*' OR NEW.password_algorithm IS NULL"
                + " OR NEW.password_algorithm NOT IN ('PBKDF2WithHmacSHA256','PBKDF2WithHmacSHA1')"
                + " OR NEW.password_iterations < 1 OR NEW.password_iterations > 2000000"
                + " OR NEW.created_at <= 0 OR NEW.rules_accepted_at <= 0))";
        for (String operation : new String[]{"INSERT", "UPDATE"}) {
            db.execSQL("CREATE TRIGGER IF NOT EXISTS guard_users_account_" + operation.toLowerCase(java.util.Locale.ROOT)
                    + " BEFORE " + operation + " ON users WHEN " + invalid
                    + " BEGIN SELECT RAISE(ABORT, 'Invalid local account'); END");
        }
        db.execSQL("CREATE TRIGGER IF NOT EXISTS guard_users_preserve_legacy_author BEFORE UPDATE ON users"
                + " WHEN OLD.username IS NULL AND NEW.username IS NOT NULL"
                + " BEGIN SELECT RAISE(ABORT, 'Legacy authors cannot become login accounts'); END");
    }
}
