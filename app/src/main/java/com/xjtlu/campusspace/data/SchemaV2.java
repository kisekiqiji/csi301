package com.xjtlu.campusspace.data;

import androidx.sqlite.db.SupportSQLiteDatabase;

/** SQL shared by migration and integrity tests; column/index definitions mirror Rows. */
public final class SchemaV2 {
    private SchemaV2() { }

    public static final String[] TABLE_DDL = {
            "CREATE TABLE IF NOT EXISTS `buildings` (`id` TEXT NOT NULL, `code` TEXT NOT NULL, `name` TEXT NOT NULL, `campus_zone` TEXT NOT NULL, `sort_order` INTEGER NOT NULL, PRIMARY KEY(`id`))",
            "CREATE TABLE IF NOT EXISTS `floors` (`id` TEXT NOT NULL, `building_id` TEXT NOT NULL, `code` TEXT NOT NULL, `sort_order` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`building_id`) REFERENCES `buildings`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT)",
            "CREATE TABLE IF NOT EXISTS `spaces` (`id` TEXT NOT NULL, `floor_id` TEXT NOT NULL, `code` TEXT NOT NULL, `name` TEXT NOT NULL, `type` TEXT NOT NULL, `location_hint` TEXT NOT NULL, `show_in_public_list` INTEGER NOT NULL, `capacity` INTEGER NOT NULL, `size` TEXT NOT NULL, `sub_type` TEXT NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`floor_id`) REFERENCES `floors`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT)",
            "CREATE TABLE IF NOT EXISTS `space_facilities` (`space_id` TEXT NOT NULL, `facility_code` TEXT NOT NULL, PRIMARY KEY(`space_id`, `facility_code`), FOREIGN KEY(`space_id`) REFERENCES `spaces`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)",
            "CREATE TABLE IF NOT EXISTS `equipment` (`id` TEXT NOT NULL, `space_id` TEXT NOT NULL, `type` TEXT NOT NULL, `name` TEXT NOT NULL, `location_hint` TEXT NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`space_id`) REFERENCES `spaces`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT)",
            "CREATE TABLE IF NOT EXISTS `data_sources` (`id` TEXT NOT NULL, `code` TEXT NOT NULL, `name` TEXT NOT NULL, `default_booking_url` TEXT NOT NULL, `is_demo` INTEGER NOT NULL, PRIMARY KEY(`id`))",
            "CREATE TABLE IF NOT EXISTS `sync_runs` (`id` TEXT NOT NULL, `source_id` TEXT NOT NULL, `data_kind` TEXT NOT NULL, `scope_key` TEXT NOT NULL, `window_start` INTEGER NOT NULL, `window_end` INTEGER NOT NULL, `fetched_at` INTEGER NOT NULL, `status` TEXT NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`source_id`) REFERENCES `data_sources`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT)",
            "CREATE TABLE IF NOT EXISTS `classroom_events` (`id` TEXT NOT NULL, `space_id` TEXT NOT NULL, `sync_run_id` TEXT NOT NULL, `external_occurrence_id` TEXT NOT NULL, `starts_at` INTEGER NOT NULL, `ends_at` INTEGER NOT NULL, `course` TEXT NOT NULL, `teacher` TEXT NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`space_id`) REFERENCES `spaces`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT, FOREIGN KEY(`sync_run_id`) REFERENCES `sync_runs`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT)",
            "CREATE TABLE IF NOT EXISTS `booking_groups` (`id` TEXT NOT NULL, `category` TEXT NOT NULL, `label` TEXT NOT NULL, `display_mode` TEXT NOT NULL, `sort_order` INTEGER NOT NULL, PRIMARY KEY(`id`))",
            "CREATE TABLE IF NOT EXISTS `booking_resources` (`id` TEXT NOT NULL, `group_id` TEXT NOT NULL, `space_id` TEXT NOT NULL, `source_id` TEXT NOT NULL, `external_id` TEXT NOT NULL, `label` TEXT NOT NULL, `grid_row` INTEGER NOT NULL, `grid_col` INTEGER NOT NULL, `official_url` TEXT NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`group_id`) REFERENCES `booking_groups`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT, FOREIGN KEY(`space_id`) REFERENCES `spaces`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT, FOREIGN KEY(`source_id`) REFERENCES `data_sources`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT)",
            "CREATE TABLE IF NOT EXISTS `booking_slots` (`id` TEXT NOT NULL, `resource_id` TEXT NOT NULL, `sync_run_id` TEXT NOT NULL, `starts_at` INTEGER NOT NULL, `ends_at` INTEGER NOT NULL, `status` TEXT NOT NULL, `raw_status` TEXT NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`resource_id`) REFERENCES `booking_resources`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT, FOREIGN KEY(`sync_run_id`) REFERENCES `sync_runs`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT)",
            "CREATE TABLE IF NOT EXISTS `seat_states` (`id` TEXT NOT NULL, `resource_id` TEXT NOT NULL, `sync_run_id` TEXT NOT NULL, `status` TEXT NOT NULL, `raw_status` TEXT NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`resource_id`) REFERENCES `booking_resources`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT, FOREIGN KEY(`sync_run_id`) REFERENCES `sync_runs`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT)",
            "CREATE TABLE IF NOT EXISTS `users` (`id` TEXT NOT NULL, `auth_subject` TEXT, `display_name` TEXT NOT NULL, PRIMARY KEY(`id`))",
            "CREATE TABLE IF NOT EXISTS `notes` (`id` TEXT NOT NULL, `space_id` TEXT NOT NULL, `author_id` TEXT NOT NULL, `equipment_id` TEXT, `type` TEXT NOT NULL, `body` TEXT NOT NULL, `created_at` INTEGER NOT NULL, `status` TEXT NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`space_id`) REFERENCES `spaces`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT, FOREIGN KEY(`author_id`) REFERENCES `users`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT, FOREIGN KEY(`equipment_id`, `space_id`) REFERENCES `equipment`(`id`, `space_id`) ON UPDATE NO ACTION ON DELETE RESTRICT)",
            "CREATE TABLE IF NOT EXISTS `note_images` (`id` TEXT NOT NULL, `note_id` TEXT NOT NULL, `object_key` TEXT NOT NULL, `sort_order` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`note_id`) REFERENCES `notes`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)"
    };

    public static final String[] INDEX_DDL = {
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_buildings_code` ON `buildings` (`code`)",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_floors_building_id_code` ON `floors` (`building_id`, `code`)",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_spaces_floor_id_code` ON `spaces` (`floor_id`, `code`)",
            "CREATE INDEX IF NOT EXISTS `index_spaces_type` ON `spaces` (`type`)",
            "CREATE INDEX IF NOT EXISTS `index_equipment_space_id` ON `equipment` (`space_id`)",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_equipment_id_space_id` ON `equipment` (`id`, `space_id`)",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_data_sources_code` ON `data_sources` (`code`)",
            "CREATE INDEX IF NOT EXISTS `index_sync_runs_source_id_data_kind_scope_key_window_start_window_end_fetched_at` ON `sync_runs` (`source_id`, `data_kind`, `scope_key`, `window_start`, `window_end`, `fetched_at`)",
            "CREATE INDEX IF NOT EXISTS `index_classroom_events_space_id_starts_at_ends_at` ON `classroom_events` (`space_id`, `starts_at`, `ends_at`)",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_classroom_events_sync_run_id_external_occurrence_id` ON `classroom_events` (`sync_run_id`, `external_occurrence_id`)",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_booking_groups_category_label` ON `booking_groups` (`category`, `label`)",
            "CREATE INDEX IF NOT EXISTS `index_booking_resources_group_id` ON `booking_resources` (`group_id`)",
            "CREATE INDEX IF NOT EXISTS `index_booking_resources_space_id` ON `booking_resources` (`space_id`)",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_booking_resources_source_id_external_id` ON `booking_resources` (`source_id`, `external_id`)",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_booking_slots_resource_id_sync_run_id_starts_at_ends_at` ON `booking_slots` (`resource_id`, `sync_run_id`, `starts_at`, `ends_at`)",
            "CREATE INDEX IF NOT EXISTS `index_booking_slots_sync_run_id` ON `booking_slots` (`sync_run_id`)",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_seat_states_resource_id_sync_run_id` ON `seat_states` (`resource_id`, `sync_run_id`)",
            "CREATE INDEX IF NOT EXISTS `index_seat_states_sync_run_id` ON `seat_states` (`sync_run_id`)",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_users_auth_subject` ON `users` (`auth_subject`)",
            "CREATE INDEX IF NOT EXISTS `index_notes_space_id_created_at` ON `notes` (`space_id`, `created_at`)",
            "CREATE INDEX IF NOT EXISTS `index_notes_author_id` ON `notes` (`author_id`)",
            "CREATE INDEX IF NOT EXISTS `index_notes_equipment_id_space_id` ON `notes` (`equipment_id`, `space_id`)",
            "CREATE INDEX IF NOT EXISTS `index_notes_created_at` ON `notes` (`created_at`)",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_note_images_note_id_sort_order` ON `note_images` (`note_id`, `sort_order`)"
    };

    public static void createTables(SupportSQLiteDatabase db) {
        for (String sql : TABLE_DDL) db.execSQL(sql);
        for (String sql : INDEX_DDL) db.execSQL(sql);
    }

    /** Room has no CHECK annotation; install equivalent guards on new and migrated databases. */
    public static void installTriggers(SupportSQLiteDatabase db) {
        String[] ids = {"buildings", "floors", "spaces", "equipment", "data_sources", "sync_runs",
                "classroom_events", "booking_groups", "booking_resources", "booking_slots", "seat_states",
                "users", "notes", "note_images"};
        for (String table : ids) guard(db, table, "identity", "length(trim(NEW.id)) = 0");
        guard(db, "spaces", "values", "NEW.type NOT IN ('CLASSROOM','PUBLIC_REST','OPEN_SEATING','DISCUSSION_ROOM','QUIET_POD','LIBRARY_AREA','PUBLIC_AREA') OR NEW.sub_type NOT IN ('CLASSROOM','DISCUSSION_AREA') OR NEW.show_in_public_list NOT IN (0,1) OR NEW.capacity < 0");
        guard(db, "space_facilities", "values", "length(trim(NEW.facility_code)) = 0");
        guard(db, "data_sources", "values", "NEW.is_demo NOT IN (0,1)");
        guard(db, "sync_runs", "values", "NEW.window_end <= NEW.window_start OR NEW.fetched_at < 0 OR NEW.data_kind NOT IN ('EVENTS','SLOTS','SEATS') OR NEW.status NOT IN ('RUNNING','SUCCESS','FAILED')");
        guard(db, "booking_groups", "values", "NOT ((NEW.category IN ('DISCUSSION_ROOM','QUIET_POD') AND NEW.display_mode = 'SLOTS') OR (NEW.category = 'LIBRARY_SEAT' AND NEW.display_mode = 'SEATS') OR (NEW.category = 'DISCUSSION_AREA' AND NEW.display_mode = 'TIMELINE'))");
        guard(db, "booking_resources", "values", "NEW.grid_row < 0 OR NEW.grid_col < 0 OR length(trim(NEW.external_id)) = 0 OR NOT EXISTS (SELECT 1 FROM booking_groups g JOIN spaces s ON s.id = NEW.space_id WHERE g.id = NEW.group_id AND ((g.category = 'DISCUSSION_ROOM' AND s.type = 'DISCUSSION_ROOM') OR (g.category = 'QUIET_POD' AND s.type = 'QUIET_POD') OR (g.category = 'LIBRARY_SEAT' AND s.type = 'LIBRARY_AREA' AND NEW.grid_row > 0 AND NEW.grid_col > 0) OR (g.category = 'DISCUSSION_AREA' AND s.type = 'CLASSROOM' AND s.sub_type = 'DISCUSSION_AREA')))");
        guard(db, "booking_resources", "grid", "EXISTS (SELECT 1 FROM booking_groups g JOIN booking_resources r ON r.group_id = g.id WHERE g.id = NEW.group_id AND g.display_mode = 'SEATS' AND r.id != NEW.id AND r.grid_row = NEW.grid_row AND r.grid_col = NEW.grid_col)");
        guard(db, "booking_resources", "references", "EXISTS (SELECT 1 FROM booking_slots b JOIN sync_runs run ON run.id = b.sync_run_id WHERE b.resource_id = NEW.id AND (run.source_id != NEW.source_id OR run.scope_key != NEW.group_id)) OR EXISTS (SELECT 1 FROM seat_states s JOIN sync_runs run ON run.id = s.sync_run_id WHERE s.resource_id = NEW.id AND (run.source_id != NEW.source_id OR run.scope_key != NEW.group_id))");
        guard(db, "booking_groups", "references", "EXISTS (SELECT 1 FROM booking_resources r JOIN spaces s ON s.id = r.space_id WHERE r.group_id = NEW.id AND NOT ((NEW.category = 'DISCUSSION_ROOM' AND s.type = 'DISCUSSION_ROOM') OR (NEW.category = 'QUIET_POD' AND s.type = 'QUIET_POD') OR (NEW.category = 'LIBRARY_SEAT' AND s.type = 'LIBRARY_AREA' AND r.grid_row > 0 AND r.grid_col > 0) OR (NEW.category = 'DISCUSSION_AREA' AND s.type = 'CLASSROOM' AND s.sub_type = 'DISCUSSION_AREA')))");
        guard(db, "spaces", "references", "(NEW.type != 'CLASSROOM' AND EXISTS (SELECT 1 FROM classroom_events e WHERE e.space_id = NEW.id)) OR EXISTS (SELECT 1 FROM booking_resources r JOIN booking_groups g ON g.id = r.group_id WHERE r.space_id = NEW.id AND NOT ((g.category = 'DISCUSSION_ROOM' AND NEW.type = 'DISCUSSION_ROOM') OR (g.category = 'QUIET_POD' AND NEW.type = 'QUIET_POD') OR (g.category = 'LIBRARY_SEAT' AND NEW.type = 'LIBRARY_AREA') OR (g.category = 'DISCUSSION_AREA' AND NEW.type = 'CLASSROOM' AND NEW.sub_type = 'DISCUSSION_AREA')))");
        guard(db, "classroom_events", "values", "NEW.ends_at <= NEW.starts_at OR NOT EXISTS (SELECT 1 FROM sync_runs r JOIN spaces s ON s.id = NEW.space_id WHERE r.id = NEW.sync_run_id AND r.data_kind = 'EVENTS' AND r.scope_key = NEW.space_id AND s.type = 'CLASSROOM' AND r.window_start <= NEW.starts_at AND r.window_end >= NEW.ends_at)");
        guard(db, "booking_slots", "values", "NEW.ends_at <= NEW.starts_at OR NEW.status NOT IN ('FREE','BOOKED','UNKNOWN') OR NOT EXISTS (SELECT 1 FROM sync_runs run JOIN booking_resources r ON r.source_id = run.source_id JOIN booking_groups g ON g.id = r.group_id WHERE run.id = NEW.sync_run_id AND r.id = NEW.resource_id AND run.data_kind = 'SLOTS' AND run.scope_key = r.group_id AND g.display_mode = 'SLOTS' AND run.window_start <= NEW.starts_at AND run.window_end >= NEW.ends_at)");
        guard(db, "seat_states", "values", "NEW.status NOT IN ('AVAILABLE','PART_FREE','RESERVED','UNAVAILABLE','UNKNOWN') OR NOT EXISTS (SELECT 1 FROM sync_runs run JOIN booking_resources r ON r.source_id = run.source_id JOIN booking_groups g ON g.id = r.group_id WHERE run.id = NEW.sync_run_id AND r.id = NEW.resource_id AND run.data_kind = 'SEATS' AND run.scope_key = r.group_id AND g.display_mode = 'SEATS')");
        guard(db, "notes", "values", "NEW.type NOT IN ('失物招领','空间碎碎念','设备报修') OR NEW.status NOT IN ('VISIBLE','HIDDEN') OR length(trim(NEW.body)) = 0");
        guard(db, "note_images", "values", "NEW.sort_order < 0 OR length(trim(NEW.object_key)) = 0");
        guard(db, "sync_runs", "references", "EXISTS (SELECT 1 FROM classroom_events e WHERE e.sync_run_id = NEW.id AND (NEW.data_kind != 'EVENTS' OR NEW.scope_key != e.space_id OR NEW.window_start > e.starts_at OR NEW.window_end < e.ends_at)) OR EXISTS (SELECT 1 FROM booking_slots b JOIN booking_resources r ON r.id = b.resource_id WHERE b.sync_run_id = NEW.id AND (NEW.data_kind != 'SLOTS' OR NEW.scope_key != r.group_id OR NEW.source_id != r.source_id OR NEW.window_start > b.starts_at OR NEW.window_end < b.ends_at)) OR EXISTS (SELECT 1 FROM seat_states s JOIN booking_resources r ON r.id = s.resource_id WHERE s.sync_run_id = NEW.id AND (NEW.data_kind != 'SEATS' OR NEW.scope_key != r.group_id OR NEW.source_id != r.source_id))");
    }

    private static void guard(SupportSQLiteDatabase db, String table, String name, String condition) {
        for (String operation : new String[]{"INSERT", "UPDATE"}) {
            db.execSQL("CREATE TRIGGER IF NOT EXISTS `guard_" + table + "_" + name + "_" + operation.toLowerCase(java.util.Locale.ROOT)
                    + "` BEFORE " + operation + " ON `" + table + "` WHEN " + condition
                    + " BEGIN SELECT RAISE(ABORT, 'Invalid " + table + " " + name + "'); END");
        }
    }
}
