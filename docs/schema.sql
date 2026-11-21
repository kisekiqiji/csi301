-- CampusSpace v2: schema only, exported from the verified Android database.
-- Includes foreign keys, indexes, and the integrity triggers used by Room.
PRAGMA foreign_keys = ON;
BEGIN TRANSACTION;

CREATE TABLE `buildings` (`id` TEXT NOT NULL, `code` TEXT NOT NULL, `name` TEXT NOT NULL, `campus_zone` TEXT NOT NULL, `sort_order` INTEGER NOT NULL, PRIMARY KEY(`id`));

CREATE TABLE `floors` (`id` TEXT NOT NULL, `building_id` TEXT NOT NULL, `code` TEXT NOT NULL, `sort_order` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`building_id`) REFERENCES `buildings`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT );

CREATE TABLE `spaces` (`id` TEXT NOT NULL, `floor_id` TEXT NOT NULL, `code` TEXT NOT NULL, `name` TEXT NOT NULL, `type` TEXT NOT NULL, `location_hint` TEXT NOT NULL, `show_in_public_list` INTEGER NOT NULL, `capacity` INTEGER NOT NULL, `size` TEXT NOT NULL, `sub_type` TEXT NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`floor_id`) REFERENCES `floors`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT );

CREATE TABLE `space_facilities` (`space_id` TEXT NOT NULL, `facility_code` TEXT NOT NULL, PRIMARY KEY(`space_id`, `facility_code`), FOREIGN KEY(`space_id`) REFERENCES `spaces`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE );

CREATE TABLE `equipment` (`id` TEXT NOT NULL, `space_id` TEXT NOT NULL, `type` TEXT NOT NULL, `name` TEXT NOT NULL, `location_hint` TEXT NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`space_id`) REFERENCES `spaces`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT );

CREATE TABLE `classroom_events` (`id` TEXT NOT NULL, `space_id` TEXT NOT NULL, `sync_run_id` TEXT NOT NULL, `external_occurrence_id` TEXT NOT NULL, `starts_at` INTEGER NOT NULL, `ends_at` INTEGER NOT NULL, `course` TEXT NOT NULL, `teacher` TEXT NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`space_id`) REFERENCES `spaces`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT , FOREIGN KEY(`sync_run_id`) REFERENCES `sync_runs`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT );

CREATE TABLE `booking_groups` (`id` TEXT NOT NULL, `category` TEXT NOT NULL, `label` TEXT NOT NULL, `display_mode` TEXT NOT NULL, `sort_order` INTEGER NOT NULL, PRIMARY KEY(`id`));

CREATE TABLE `booking_resources` (`id` TEXT NOT NULL, `group_id` TEXT NOT NULL, `space_id` TEXT NOT NULL, `source_id` TEXT NOT NULL, `external_id` TEXT NOT NULL, `label` TEXT NOT NULL, `grid_row` INTEGER NOT NULL, `grid_col` INTEGER NOT NULL, `official_url` TEXT NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`group_id`) REFERENCES `booking_groups`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT , FOREIGN KEY(`space_id`) REFERENCES `spaces`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT , FOREIGN KEY(`source_id`) REFERENCES `data_sources`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT );

CREATE TABLE `booking_slots` (`id` TEXT NOT NULL, `resource_id` TEXT NOT NULL, `sync_run_id` TEXT NOT NULL, `starts_at` INTEGER NOT NULL, `ends_at` INTEGER NOT NULL, `status` TEXT NOT NULL, `raw_status` TEXT NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`resource_id`) REFERENCES `booking_resources`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT , FOREIGN KEY(`sync_run_id`) REFERENCES `sync_runs`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT );

CREATE TABLE `seat_states` (`id` TEXT NOT NULL, `resource_id` TEXT NOT NULL, `sync_run_id` TEXT NOT NULL, `status` TEXT NOT NULL, `raw_status` TEXT NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`resource_id`) REFERENCES `booking_resources`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT , FOREIGN KEY(`sync_run_id`) REFERENCES `sync_runs`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT );

CREATE TABLE `data_sources` (`id` TEXT NOT NULL, `code` TEXT NOT NULL, `name` TEXT NOT NULL, `default_booking_url` TEXT NOT NULL, `is_demo` INTEGER NOT NULL, PRIMARY KEY(`id`));

CREATE TABLE `sync_runs` (`id` TEXT NOT NULL, `source_id` TEXT NOT NULL, `data_kind` TEXT NOT NULL, `scope_key` TEXT NOT NULL, `window_start` INTEGER NOT NULL, `window_end` INTEGER NOT NULL, `fetched_at` INTEGER NOT NULL, `status` TEXT NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`source_id`) REFERENCES `data_sources`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT );

CREATE TABLE `users` (`id` TEXT NOT NULL, `auth_subject` TEXT, `display_name` TEXT NOT NULL, PRIMARY KEY(`id`));

CREATE TABLE `notes` (`id` TEXT NOT NULL, `space_id` TEXT NOT NULL, `author_id` TEXT NOT NULL, `equipment_id` TEXT, `type` TEXT NOT NULL, `body` TEXT NOT NULL, `created_at` INTEGER NOT NULL, `status` TEXT NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`space_id`) REFERENCES `spaces`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT , FOREIGN KEY(`author_id`) REFERENCES `users`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT , FOREIGN KEY(`equipment_id`, `space_id`) REFERENCES `equipment`(`id`, `space_id`) ON UPDATE NO ACTION ON DELETE RESTRICT );

CREATE TABLE `note_images` (`id` TEXT NOT NULL, `note_id` TEXT NOT NULL, `object_key` TEXT NOT NULL, `sort_order` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`note_id`) REFERENCES `notes`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE );

CREATE UNIQUE INDEX `index_buildings_code` ON `buildings` (`code`);

CREATE UNIQUE INDEX `index_floors_building_id_code` ON `floors` (`building_id`, `code`);

CREATE UNIQUE INDEX `index_spaces_floor_id_code` ON `spaces` (`floor_id`, `code`);

CREATE INDEX `index_spaces_type` ON `spaces` (`type`);

CREATE INDEX `index_equipment_space_id` ON `equipment` (`space_id`);

CREATE UNIQUE INDEX `index_equipment_id_space_id` ON `equipment` (`id`, `space_id`);

CREATE INDEX `index_classroom_events_space_id_starts_at_ends_at` ON `classroom_events` (`space_id`, `starts_at`, `ends_at`);

CREATE UNIQUE INDEX `index_classroom_events_sync_run_id_external_occurrence_id` ON `classroom_events` (`sync_run_id`, `external_occurrence_id`);

CREATE UNIQUE INDEX `index_booking_groups_category_label` ON `booking_groups` (`category`, `label`);

CREATE INDEX `index_booking_resources_group_id` ON `booking_resources` (`group_id`);

CREATE INDEX `index_booking_resources_space_id` ON `booking_resources` (`space_id`);

CREATE UNIQUE INDEX `index_booking_resources_source_id_external_id` ON `booking_resources` (`source_id`, `external_id`);

CREATE UNIQUE INDEX `index_booking_slots_resource_id_sync_run_id_starts_at_ends_at` ON `booking_slots` (`resource_id`, `sync_run_id`, `starts_at`, `ends_at`);

CREATE INDEX `index_booking_slots_sync_run_id` ON `booking_slots` (`sync_run_id`);

CREATE UNIQUE INDEX `index_seat_states_resource_id_sync_run_id` ON `seat_states` (`resource_id`, `sync_run_id`);

CREATE INDEX `index_seat_states_sync_run_id` ON `seat_states` (`sync_run_id`);

CREATE UNIQUE INDEX `index_data_sources_code` ON `data_sources` (`code`);

CREATE INDEX `index_sync_runs_source_id_data_kind_scope_key_window_start_window_end_fetched_at` ON `sync_runs` (`source_id`, `data_kind`, `scope_key`, `window_start`, `window_end`, `fetched_at`);

CREATE UNIQUE INDEX `index_users_auth_subject` ON `users` (`auth_subject`);

CREATE INDEX `index_notes_space_id_created_at` ON `notes` (`space_id`, `created_at`);

CREATE INDEX `index_notes_author_id` ON `notes` (`author_id`);

CREATE INDEX `index_notes_equipment_id_space_id` ON `notes` (`equipment_id`, `space_id`);

CREATE INDEX `index_notes_created_at` ON `notes` (`created_at`);

CREATE UNIQUE INDEX `index_note_images_note_id_sort_order` ON `note_images` (`note_id`, `sort_order`);

CREATE TRIGGER `guard_buildings_identity_insert` BEFORE INSERT ON `buildings` WHEN length(trim(NEW.id)) = 0 BEGIN SELECT RAISE(ABORT, 'Invalid buildings identity'); END;

CREATE TRIGGER `guard_buildings_identity_update` BEFORE UPDATE ON `buildings` WHEN length(trim(NEW.id)) = 0 BEGIN SELECT RAISE(ABORT, 'Invalid buildings identity'); END;

CREATE TRIGGER `guard_floors_identity_insert` BEFORE INSERT ON `floors` WHEN length(trim(NEW.id)) = 0 BEGIN SELECT RAISE(ABORT, 'Invalid floors identity'); END;

CREATE TRIGGER `guard_floors_identity_update` BEFORE UPDATE ON `floors` WHEN length(trim(NEW.id)) = 0 BEGIN SELECT RAISE(ABORT, 'Invalid floors identity'); END;

CREATE TRIGGER `guard_spaces_identity_insert` BEFORE INSERT ON `spaces` WHEN length(trim(NEW.id)) = 0 BEGIN SELECT RAISE(ABORT, 'Invalid spaces identity'); END;

CREATE TRIGGER `guard_spaces_identity_update` BEFORE UPDATE ON `spaces` WHEN length(trim(NEW.id)) = 0 BEGIN SELECT RAISE(ABORT, 'Invalid spaces identity'); END;

CREATE TRIGGER `guard_equipment_identity_insert` BEFORE INSERT ON `equipment` WHEN length(trim(NEW.id)) = 0 BEGIN SELECT RAISE(ABORT, 'Invalid equipment identity'); END;

CREATE TRIGGER `guard_equipment_identity_update` BEFORE UPDATE ON `equipment` WHEN length(trim(NEW.id)) = 0 BEGIN SELECT RAISE(ABORT, 'Invalid equipment identity'); END;

CREATE TRIGGER `guard_data_sources_identity_insert` BEFORE INSERT ON `data_sources` WHEN length(trim(NEW.id)) = 0 BEGIN SELECT RAISE(ABORT, 'Invalid data_sources identity'); END;

CREATE TRIGGER `guard_data_sources_identity_update` BEFORE UPDATE ON `data_sources` WHEN length(trim(NEW.id)) = 0 BEGIN SELECT RAISE(ABORT, 'Invalid data_sources identity'); END;

CREATE TRIGGER `guard_sync_runs_identity_insert` BEFORE INSERT ON `sync_runs` WHEN length(trim(NEW.id)) = 0 BEGIN SELECT RAISE(ABORT, 'Invalid sync_runs identity'); END;

CREATE TRIGGER `guard_sync_runs_identity_update` BEFORE UPDATE ON `sync_runs` WHEN length(trim(NEW.id)) = 0 BEGIN SELECT RAISE(ABORT, 'Invalid sync_runs identity'); END;

CREATE TRIGGER `guard_classroom_events_identity_insert` BEFORE INSERT ON `classroom_events` WHEN length(trim(NEW.id)) = 0 BEGIN SELECT RAISE(ABORT, 'Invalid classroom_events identity'); END;

CREATE TRIGGER `guard_classroom_events_identity_update` BEFORE UPDATE ON `classroom_events` WHEN length(trim(NEW.id)) = 0 BEGIN SELECT RAISE(ABORT, 'Invalid classroom_events identity'); END;

CREATE TRIGGER `guard_booking_groups_identity_insert` BEFORE INSERT ON `booking_groups` WHEN length(trim(NEW.id)) = 0 BEGIN SELECT RAISE(ABORT, 'Invalid booking_groups identity'); END;

CREATE TRIGGER `guard_booking_groups_identity_update` BEFORE UPDATE ON `booking_groups` WHEN length(trim(NEW.id)) = 0 BEGIN SELECT RAISE(ABORT, 'Invalid booking_groups identity'); END;

CREATE TRIGGER `guard_booking_resources_identity_insert` BEFORE INSERT ON `booking_resources` WHEN length(trim(NEW.id)) = 0 BEGIN SELECT RAISE(ABORT, 'Invalid booking_resources identity'); END;

CREATE TRIGGER `guard_booking_resources_identity_update` BEFORE UPDATE ON `booking_resources` WHEN length(trim(NEW.id)) = 0 BEGIN SELECT RAISE(ABORT, 'Invalid booking_resources identity'); END;

CREATE TRIGGER `guard_booking_slots_identity_insert` BEFORE INSERT ON `booking_slots` WHEN length(trim(NEW.id)) = 0 BEGIN SELECT RAISE(ABORT, 'Invalid booking_slots identity'); END;

CREATE TRIGGER `guard_booking_slots_identity_update` BEFORE UPDATE ON `booking_slots` WHEN length(trim(NEW.id)) = 0 BEGIN SELECT RAISE(ABORT, 'Invalid booking_slots identity'); END;

CREATE TRIGGER `guard_seat_states_identity_insert` BEFORE INSERT ON `seat_states` WHEN length(trim(NEW.id)) = 0 BEGIN SELECT RAISE(ABORT, 'Invalid seat_states identity'); END;

CREATE TRIGGER `guard_seat_states_identity_update` BEFORE UPDATE ON `seat_states` WHEN length(trim(NEW.id)) = 0 BEGIN SELECT RAISE(ABORT, 'Invalid seat_states identity'); END;

CREATE TRIGGER `guard_users_identity_insert` BEFORE INSERT ON `users` WHEN length(trim(NEW.id)) = 0 BEGIN SELECT RAISE(ABORT, 'Invalid users identity'); END;

CREATE TRIGGER `guard_users_identity_update` BEFORE UPDATE ON `users` WHEN length(trim(NEW.id)) = 0 BEGIN SELECT RAISE(ABORT, 'Invalid users identity'); END;

CREATE TRIGGER `guard_notes_identity_insert` BEFORE INSERT ON `notes` WHEN length(trim(NEW.id)) = 0 BEGIN SELECT RAISE(ABORT, 'Invalid notes identity'); END;

CREATE TRIGGER `guard_notes_identity_update` BEFORE UPDATE ON `notes` WHEN length(trim(NEW.id)) = 0 BEGIN SELECT RAISE(ABORT, 'Invalid notes identity'); END;

CREATE TRIGGER `guard_note_images_identity_insert` BEFORE INSERT ON `note_images` WHEN length(trim(NEW.id)) = 0 BEGIN SELECT RAISE(ABORT, 'Invalid note_images identity'); END;

CREATE TRIGGER `guard_note_images_identity_update` BEFORE UPDATE ON `note_images` WHEN length(trim(NEW.id)) = 0 BEGIN SELECT RAISE(ABORT, 'Invalid note_images identity'); END;

CREATE TRIGGER `guard_spaces_values_insert` BEFORE INSERT ON `spaces` WHEN NEW.type NOT IN ('CLASSROOM','PUBLIC_REST','OPEN_SEATING','DISCUSSION_ROOM','QUIET_POD','LIBRARY_AREA','PUBLIC_AREA') OR NEW.sub_type NOT IN ('CLASSROOM','DISCUSSION_AREA') OR NEW.show_in_public_list NOT IN (0,1) OR NEW.capacity < 0 BEGIN SELECT RAISE(ABORT, 'Invalid spaces values'); END;

CREATE TRIGGER `guard_spaces_values_update` BEFORE UPDATE ON `spaces` WHEN NEW.type NOT IN ('CLASSROOM','PUBLIC_REST','OPEN_SEATING','DISCUSSION_ROOM','QUIET_POD','LIBRARY_AREA','PUBLIC_AREA') OR NEW.sub_type NOT IN ('CLASSROOM','DISCUSSION_AREA') OR NEW.show_in_public_list NOT IN (0,1) OR NEW.capacity < 0 BEGIN SELECT RAISE(ABORT, 'Invalid spaces values'); END;

CREATE TRIGGER `guard_space_facilities_values_insert` BEFORE INSERT ON `space_facilities` WHEN length(trim(NEW.facility_code)) = 0 BEGIN SELECT RAISE(ABORT, 'Invalid space_facilities values'); END;

CREATE TRIGGER `guard_space_facilities_values_update` BEFORE UPDATE ON `space_facilities` WHEN length(trim(NEW.facility_code)) = 0 BEGIN SELECT RAISE(ABORT, 'Invalid space_facilities values'); END;

CREATE TRIGGER `guard_data_sources_values_insert` BEFORE INSERT ON `data_sources` WHEN NEW.is_demo NOT IN (0,1) BEGIN SELECT RAISE(ABORT, 'Invalid data_sources values'); END;

CREATE TRIGGER `guard_data_sources_values_update` BEFORE UPDATE ON `data_sources` WHEN NEW.is_demo NOT IN (0,1) BEGIN SELECT RAISE(ABORT, 'Invalid data_sources values'); END;

CREATE TRIGGER `guard_sync_runs_values_insert` BEFORE INSERT ON `sync_runs` WHEN NEW.window_end <= NEW.window_start OR NEW.fetched_at < 0 OR NEW.data_kind NOT IN ('EVENTS','SLOTS','SEATS') OR NEW.status NOT IN ('RUNNING','SUCCESS','FAILED') BEGIN SELECT RAISE(ABORT, 'Invalid sync_runs values'); END;

CREATE TRIGGER `guard_sync_runs_values_update` BEFORE UPDATE ON `sync_runs` WHEN NEW.window_end <= NEW.window_start OR NEW.fetched_at < 0 OR NEW.data_kind NOT IN ('EVENTS','SLOTS','SEATS') OR NEW.status NOT IN ('RUNNING','SUCCESS','FAILED') BEGIN SELECT RAISE(ABORT, 'Invalid sync_runs values'); END;

CREATE TRIGGER `guard_booking_groups_values_insert` BEFORE INSERT ON `booking_groups` WHEN NOT ((NEW.category IN ('DISCUSSION_ROOM','QUIET_POD') AND NEW.display_mode = 'SLOTS') OR (NEW.category = 'LIBRARY_SEAT' AND NEW.display_mode = 'SEATS') OR (NEW.category = 'DISCUSSION_AREA' AND NEW.display_mode = 'TIMELINE')) BEGIN SELECT RAISE(ABORT, 'Invalid booking_groups values'); END;

CREATE TRIGGER `guard_booking_groups_values_update` BEFORE UPDATE ON `booking_groups` WHEN NOT ((NEW.category IN ('DISCUSSION_ROOM','QUIET_POD') AND NEW.display_mode = 'SLOTS') OR (NEW.category = 'LIBRARY_SEAT' AND NEW.display_mode = 'SEATS') OR (NEW.category = 'DISCUSSION_AREA' AND NEW.display_mode = 'TIMELINE')) BEGIN SELECT RAISE(ABORT, 'Invalid booking_groups values'); END;

CREATE TRIGGER `guard_booking_resources_values_insert` BEFORE INSERT ON `booking_resources` WHEN NEW.grid_row < 0 OR NEW.grid_col < 0 OR length(trim(NEW.external_id)) = 0 OR NOT EXISTS (SELECT 1 FROM booking_groups g JOIN spaces s ON s.id = NEW.space_id WHERE g.id = NEW.group_id AND ((g.category = 'DISCUSSION_ROOM' AND s.type = 'DISCUSSION_ROOM') OR (g.category = 'QUIET_POD' AND s.type = 'QUIET_POD') OR (g.category = 'LIBRARY_SEAT' AND s.type = 'LIBRARY_AREA' AND NEW.grid_row > 0 AND NEW.grid_col > 0) OR (g.category = 'DISCUSSION_AREA' AND s.type = 'CLASSROOM' AND s.sub_type = 'DISCUSSION_AREA'))) BEGIN SELECT RAISE(ABORT, 'Invalid booking_resources values'); END;

CREATE TRIGGER `guard_booking_resources_values_update` BEFORE UPDATE ON `booking_resources` WHEN NEW.grid_row < 0 OR NEW.grid_col < 0 OR length(trim(NEW.external_id)) = 0 OR NOT EXISTS (SELECT 1 FROM booking_groups g JOIN spaces s ON s.id = NEW.space_id WHERE g.id = NEW.group_id AND ((g.category = 'DISCUSSION_ROOM' AND s.type = 'DISCUSSION_ROOM') OR (g.category = 'QUIET_POD' AND s.type = 'QUIET_POD') OR (g.category = 'LIBRARY_SEAT' AND s.type = 'LIBRARY_AREA' AND NEW.grid_row > 0 AND NEW.grid_col > 0) OR (g.category = 'DISCUSSION_AREA' AND s.type = 'CLASSROOM' AND s.sub_type = 'DISCUSSION_AREA'))) BEGIN SELECT RAISE(ABORT, 'Invalid booking_resources values'); END;

CREATE TRIGGER `guard_booking_resources_grid_insert` BEFORE INSERT ON `booking_resources` WHEN EXISTS (SELECT 1 FROM booking_groups g JOIN booking_resources r ON r.group_id = g.id WHERE g.id = NEW.group_id AND g.display_mode = 'SEATS' AND r.id != NEW.id AND r.grid_row = NEW.grid_row AND r.grid_col = NEW.grid_col) BEGIN SELECT RAISE(ABORT, 'Invalid booking_resources grid'); END;

CREATE TRIGGER `guard_booking_resources_grid_update` BEFORE UPDATE ON `booking_resources` WHEN EXISTS (SELECT 1 FROM booking_groups g JOIN booking_resources r ON r.group_id = g.id WHERE g.id = NEW.group_id AND g.display_mode = 'SEATS' AND r.id != NEW.id AND r.grid_row = NEW.grid_row AND r.grid_col = NEW.grid_col) BEGIN SELECT RAISE(ABORT, 'Invalid booking_resources grid'); END;

CREATE TRIGGER `guard_booking_resources_references_insert` BEFORE INSERT ON `booking_resources` WHEN EXISTS (SELECT 1 FROM booking_slots b JOIN sync_runs run ON run.id = b.sync_run_id WHERE b.resource_id = NEW.id AND (run.source_id != NEW.source_id OR run.scope_key != NEW.group_id)) OR EXISTS (SELECT 1 FROM seat_states s JOIN sync_runs run ON run.id = s.sync_run_id WHERE s.resource_id = NEW.id AND (run.source_id != NEW.source_id OR run.scope_key != NEW.group_id)) BEGIN SELECT RAISE(ABORT, 'Invalid booking_resources references'); END;

CREATE TRIGGER `guard_booking_resources_references_update` BEFORE UPDATE ON `booking_resources` WHEN EXISTS (SELECT 1 FROM booking_slots b JOIN sync_runs run ON run.id = b.sync_run_id WHERE b.resource_id = NEW.id AND (run.source_id != NEW.source_id OR run.scope_key != NEW.group_id)) OR EXISTS (SELECT 1 FROM seat_states s JOIN sync_runs run ON run.id = s.sync_run_id WHERE s.resource_id = NEW.id AND (run.source_id != NEW.source_id OR run.scope_key != NEW.group_id)) BEGIN SELECT RAISE(ABORT, 'Invalid booking_resources references'); END;

CREATE TRIGGER `guard_booking_groups_references_insert` BEFORE INSERT ON `booking_groups` WHEN EXISTS (SELECT 1 FROM booking_resources r JOIN spaces s ON s.id = r.space_id WHERE r.group_id = NEW.id AND NOT ((NEW.category = 'DISCUSSION_ROOM' AND s.type = 'DISCUSSION_ROOM') OR (NEW.category = 'QUIET_POD' AND s.type = 'QUIET_POD') OR (NEW.category = 'LIBRARY_SEAT' AND s.type = 'LIBRARY_AREA' AND r.grid_row > 0 AND r.grid_col > 0) OR (NEW.category = 'DISCUSSION_AREA' AND s.type = 'CLASSROOM' AND s.sub_type = 'DISCUSSION_AREA'))) BEGIN SELECT RAISE(ABORT, 'Invalid booking_groups references'); END;

CREATE TRIGGER `guard_booking_groups_references_update` BEFORE UPDATE ON `booking_groups` WHEN EXISTS (SELECT 1 FROM booking_resources r JOIN spaces s ON s.id = r.space_id WHERE r.group_id = NEW.id AND NOT ((NEW.category = 'DISCUSSION_ROOM' AND s.type = 'DISCUSSION_ROOM') OR (NEW.category = 'QUIET_POD' AND s.type = 'QUIET_POD') OR (NEW.category = 'LIBRARY_SEAT' AND s.type = 'LIBRARY_AREA' AND r.grid_row > 0 AND r.grid_col > 0) OR (NEW.category = 'DISCUSSION_AREA' AND s.type = 'CLASSROOM' AND s.sub_type = 'DISCUSSION_AREA'))) BEGIN SELECT RAISE(ABORT, 'Invalid booking_groups references'); END;

CREATE TRIGGER `guard_spaces_references_insert` BEFORE INSERT ON `spaces` WHEN (NEW.type != 'CLASSROOM' AND EXISTS (SELECT 1 FROM classroom_events e WHERE e.space_id = NEW.id)) OR EXISTS (SELECT 1 FROM booking_resources r JOIN booking_groups g ON g.id = r.group_id WHERE r.space_id = NEW.id AND NOT ((g.category = 'DISCUSSION_ROOM' AND NEW.type = 'DISCUSSION_ROOM') OR (g.category = 'QUIET_POD' AND NEW.type = 'QUIET_POD') OR (g.category = 'LIBRARY_SEAT' AND NEW.type = 'LIBRARY_AREA') OR (g.category = 'DISCUSSION_AREA' AND NEW.type = 'CLASSROOM' AND NEW.sub_type = 'DISCUSSION_AREA'))) BEGIN SELECT RAISE(ABORT, 'Invalid spaces references'); END;

CREATE TRIGGER `guard_spaces_references_update` BEFORE UPDATE ON `spaces` WHEN (NEW.type != 'CLASSROOM' AND EXISTS (SELECT 1 FROM classroom_events e WHERE e.space_id = NEW.id)) OR EXISTS (SELECT 1 FROM booking_resources r JOIN booking_groups g ON g.id = r.group_id WHERE r.space_id = NEW.id AND NOT ((g.category = 'DISCUSSION_ROOM' AND NEW.type = 'DISCUSSION_ROOM') OR (g.category = 'QUIET_POD' AND NEW.type = 'QUIET_POD') OR (g.category = 'LIBRARY_SEAT' AND NEW.type = 'LIBRARY_AREA') OR (g.category = 'DISCUSSION_AREA' AND NEW.type = 'CLASSROOM' AND NEW.sub_type = 'DISCUSSION_AREA'))) BEGIN SELECT RAISE(ABORT, 'Invalid spaces references'); END;

CREATE TRIGGER `guard_classroom_events_values_insert` BEFORE INSERT ON `classroom_events` WHEN NEW.ends_at <= NEW.starts_at OR NOT EXISTS (SELECT 1 FROM sync_runs r JOIN spaces s ON s.id = NEW.space_id WHERE r.id = NEW.sync_run_id AND r.data_kind = 'EVENTS' AND r.scope_key = NEW.space_id AND s.type = 'CLASSROOM' AND r.window_start <= NEW.starts_at AND r.window_end >= NEW.ends_at) BEGIN SELECT RAISE(ABORT, 'Invalid classroom_events values'); END;

CREATE TRIGGER `guard_classroom_events_values_update` BEFORE UPDATE ON `classroom_events` WHEN NEW.ends_at <= NEW.starts_at OR NOT EXISTS (SELECT 1 FROM sync_runs r JOIN spaces s ON s.id = NEW.space_id WHERE r.id = NEW.sync_run_id AND r.data_kind = 'EVENTS' AND r.scope_key = NEW.space_id AND s.type = 'CLASSROOM' AND r.window_start <= NEW.starts_at AND r.window_end >= NEW.ends_at) BEGIN SELECT RAISE(ABORT, 'Invalid classroom_events values'); END;

CREATE TRIGGER `guard_booking_slots_values_insert` BEFORE INSERT ON `booking_slots` WHEN NEW.ends_at <= NEW.starts_at OR NEW.status NOT IN ('FREE','BOOKED','UNKNOWN') OR NOT EXISTS (SELECT 1 FROM sync_runs run JOIN booking_resources r ON r.source_id = run.source_id JOIN booking_groups g ON g.id = r.group_id WHERE run.id = NEW.sync_run_id AND r.id = NEW.resource_id AND run.data_kind = 'SLOTS' AND run.scope_key = r.group_id AND g.display_mode = 'SLOTS' AND run.window_start <= NEW.starts_at AND run.window_end >= NEW.ends_at) BEGIN SELECT RAISE(ABORT, 'Invalid booking_slots values'); END;

CREATE TRIGGER `guard_booking_slots_values_update` BEFORE UPDATE ON `booking_slots` WHEN NEW.ends_at <= NEW.starts_at OR NEW.status NOT IN ('FREE','BOOKED','UNKNOWN') OR NOT EXISTS (SELECT 1 FROM sync_runs run JOIN booking_resources r ON r.source_id = run.source_id JOIN booking_groups g ON g.id = r.group_id WHERE run.id = NEW.sync_run_id AND r.id = NEW.resource_id AND run.data_kind = 'SLOTS' AND run.scope_key = r.group_id AND g.display_mode = 'SLOTS' AND run.window_start <= NEW.starts_at AND run.window_end >= NEW.ends_at) BEGIN SELECT RAISE(ABORT, 'Invalid booking_slots values'); END;

CREATE TRIGGER `guard_seat_states_values_insert` BEFORE INSERT ON `seat_states` WHEN NEW.status NOT IN ('AVAILABLE','PART_FREE','RESERVED','UNAVAILABLE','UNKNOWN') OR NOT EXISTS (SELECT 1 FROM sync_runs run JOIN booking_resources r ON r.source_id = run.source_id JOIN booking_groups g ON g.id = r.group_id WHERE run.id = NEW.sync_run_id AND r.id = NEW.resource_id AND run.data_kind = 'SEATS' AND run.scope_key = r.group_id AND g.display_mode = 'SEATS') BEGIN SELECT RAISE(ABORT, 'Invalid seat_states values'); END;

CREATE TRIGGER `guard_seat_states_values_update` BEFORE UPDATE ON `seat_states` WHEN NEW.status NOT IN ('AVAILABLE','PART_FREE','RESERVED','UNAVAILABLE','UNKNOWN') OR NOT EXISTS (SELECT 1 FROM sync_runs run JOIN booking_resources r ON r.source_id = run.source_id JOIN booking_groups g ON g.id = r.group_id WHERE run.id = NEW.sync_run_id AND r.id = NEW.resource_id AND run.data_kind = 'SEATS' AND run.scope_key = r.group_id AND g.display_mode = 'SEATS') BEGIN SELECT RAISE(ABORT, 'Invalid seat_states values'); END;

CREATE TRIGGER `guard_notes_values_insert` BEFORE INSERT ON `notes` WHEN NEW.type NOT IN ('失物招领','空间碎碎念','设备报修') OR NEW.status NOT IN ('VISIBLE','HIDDEN') OR length(trim(NEW.body)) = 0 BEGIN SELECT RAISE(ABORT, 'Invalid notes values'); END;

CREATE TRIGGER `guard_notes_values_update` BEFORE UPDATE ON `notes` WHEN NEW.type NOT IN ('失物招领','空间碎碎念','设备报修') OR NEW.status NOT IN ('VISIBLE','HIDDEN') OR length(trim(NEW.body)) = 0 BEGIN SELECT RAISE(ABORT, 'Invalid notes values'); END;

CREATE TRIGGER `guard_note_images_values_insert` BEFORE INSERT ON `note_images` WHEN NEW.sort_order < 0 OR length(trim(NEW.object_key)) = 0 BEGIN SELECT RAISE(ABORT, 'Invalid note_images values'); END;

CREATE TRIGGER `guard_note_images_values_update` BEFORE UPDATE ON `note_images` WHEN NEW.sort_order < 0 OR length(trim(NEW.object_key)) = 0 BEGIN SELECT RAISE(ABORT, 'Invalid note_images values'); END;

CREATE TRIGGER `guard_sync_runs_references_insert` BEFORE INSERT ON `sync_runs` WHEN EXISTS (SELECT 1 FROM classroom_events e WHERE e.sync_run_id = NEW.id AND (NEW.data_kind != 'EVENTS' OR NEW.scope_key != e.space_id OR NEW.window_start > e.starts_at OR NEW.window_end < e.ends_at)) OR EXISTS (SELECT 1 FROM booking_slots b JOIN booking_resources r ON r.id = b.resource_id WHERE b.sync_run_id = NEW.id AND (NEW.data_kind != 'SLOTS' OR NEW.scope_key != r.group_id OR NEW.source_id != r.source_id OR NEW.window_start > b.starts_at OR NEW.window_end < b.ends_at)) OR EXISTS (SELECT 1 FROM seat_states s JOIN booking_resources r ON r.id = s.resource_id WHERE s.sync_run_id = NEW.id AND (NEW.data_kind != 'SEATS' OR NEW.scope_key != r.group_id OR NEW.source_id != r.source_id)) BEGIN SELECT RAISE(ABORT, 'Invalid sync_runs references'); END;

CREATE TRIGGER `guard_sync_runs_references_update` BEFORE UPDATE ON `sync_runs` WHEN EXISTS (SELECT 1 FROM classroom_events e WHERE e.sync_run_id = NEW.id AND (NEW.data_kind != 'EVENTS' OR NEW.scope_key != e.space_id OR NEW.window_start > e.starts_at OR NEW.window_end < e.ends_at)) OR EXISTS (SELECT 1 FROM booking_slots b JOIN booking_resources r ON r.id = b.resource_id WHERE b.sync_run_id = NEW.id AND (NEW.data_kind != 'SLOTS' OR NEW.scope_key != r.group_id OR NEW.source_id != r.source_id OR NEW.window_start > b.starts_at OR NEW.window_end < b.ends_at)) OR EXISTS (SELECT 1 FROM seat_states s JOIN booking_resources r ON r.id = s.resource_id WHERE s.sync_run_id = NEW.id AND (NEW.data_kind != 'SEATS' OR NEW.scope_key != r.group_id OR NEW.source_id != r.source_id)) BEGIN SELECT RAISE(ABORT, 'Invalid sync_runs references'); END;

COMMIT;
