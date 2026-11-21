package com.xjtlu.campusspace.data;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import androidx.annotation.NonNull;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Converts the offline v1 catalogue without discarding locally published notes or image paths. */
public final class LegacyMigration {
    private LegacyMigration() { }

    public static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override public void migrate(@NonNull SupportSQLiteDatabase db) {
            LegacyMigration.migrate(db, System.currentTimeMillis());
        }
    };

    /** The explicit clock makes the date expansion reproducible in migration tests. */
    public static void migrate(SupportSQLiteDatabase db, long now) {
        new Converter(db, now).run();
    }

    private static final class Converter {
        private static final String[] OLD_TABLES = {"buildings", "spaces", "lectures", "discussion_rooms",
                "quiet_pods", "slots", "library_seats", "facilities", "notes"};
        private static final String TIMETABLE = "DEMO_TIMETABLE";
        private static final String BOOKING = "DEMO_BOOKING";
        private final SupportSQLiteDatabase db;
        private final long now;
        private final Calendar today;
        private final Map<String, List<ContentValues>> old = new LinkedHashMap<>();
        private final Set<String> buildings = new HashSet<>();
        private final Set<String> floors = new HashSet<>();
        private final Set<String> spaces = new HashSet<>();
        private final Set<String> groups = new HashSet<>();
        private final Set<String> resources = new HashSet<>();
        private final Set<String> runs = new HashSet<>();
        private final Set<String> tags = new HashSet<>();
        private final Map<String, String> targetSpaces = new HashMap<>();
        private final Map<String, String> targetResources = new HashMap<>();
        private final Map<String, String> equipmentSpaces = new HashMap<>();
        private final Map<String, String> resourceGroups = new HashMap<>();
        private final Map<String, String> classroomRuns = new HashMap<>();

        Converter(SupportSQLiteDatabase db, long now) {
            this.db = db;
            this.now = now;
            today = Calendar.getInstance(TimeZone.getTimeZone("Asia/Shanghai"));
            today.setTimeInMillis(now);
            today.set(Calendar.HOUR_OF_DAY, 0);
            today.set(Calendar.MINUTE, 0);
            today.set(Calendar.SECOND, 0);
            today.set(Calendar.MILLISECOND, 0);
        }

        void run() {
            for (String table : OLD_TABLES) {
                old.put(table, read(table));
                db.execSQL("ALTER TABLE `" + table + "` RENAME TO `legacy_v1_" + table + "`");
            }
            SchemaV2.createTables(db);
            source(TIMETABLE, "旧版演示课表");
            source(BOOKING, "旧版演示预约");
            for (ContentValues row : old.get("buildings")) {
                String code = nonempty(text(row, "code"), "UNKNOWN");
                building(code, text(row, "name"), text(row, "campus"));
                int count = Math.max(0, integer(row, "floors"));
                for (int floor = 1; floor <= count; floor++) floor(code, floor);
            }
            for (ContentValues row : old.get("spaces")) migrateSpace(row);
            for (ContentValues row : old.get("discussion_rooms")) migrateRoom(row, "DISCUSSION_ROOM");
            for (ContentValues row : old.get("quiet_pods")) migrateRoom(row, "QUIET_POD");
            for (ContentValues row : old.get("library_seats")) migrateSeat(row);
            for (ContentValues row : old.get("facilities")) migrateFacility(row);
            migrateLectures();
            migrateSlots();
            for (ContentValues row : old.get("notes")) migrateNote(row);
            // No v2 row references these tables. Drop only after all conversions succeeded.
            for (int i = OLD_TABLES.length - 1; i >= 0; i--) {
                db.execSQL("DROP TABLE `legacy_v1_" + OLD_TABLES[i] + "`");
            }
            try (Cursor violations = db.query("PRAGMA foreign_key_check")) {
                if (violations.moveToFirst()) throw new IllegalStateException("v1 migration produced an invalid relation");
            }
            SchemaV2.installTriggers(db);
        }

        private List<ContentValues> read(String table) {
            List<ContentValues> rows = new ArrayList<>();
            try (Cursor cursor = db.query("SELECT * FROM `" + table + "`")) {
                String[] names = cursor.getColumnNames();
                while (cursor.moveToNext()) {
                    ContentValues row = new ContentValues();
                    for (int i = 0; i < names.length; i++) {
                        if (cursor.isNull(i)) row.putNull(names[i]);
                        else if (cursor.getType(i) == Cursor.FIELD_TYPE_INTEGER) row.put(names[i], cursor.getLong(i));
                        else row.put(names[i], cursor.getString(i));
                    }
                    rows.add(row);
                }
            }
            return rows;
        }

        private void source(String id, String name) {
            insert("data_sources", values("id", id, "code", id, "name", name,
                    "default_booking_url", "", "is_demo", 1));
        }

        private String building(String code, String name, String campus) {
            code = nonempty(code, "UNKNOWN");
            if (buildings.add(code)) {
                String label = nonempty(name, "LIB".equals(code) ? "图书馆" : code);
                // v1 omitted LIB from its directory; retain the catalogue's known campus on upgrade.
                String campusZone = campus.isEmpty() && "LIB".equals(code) ? "中校区" : campus;
                insert("buildings", values("id", code, "code", code, "name", label,
                        "campus_zone", campusZone, "sort_order", buildings.size() - 1));
            }
            return code;
        }

        private String floor(String buildingCode, int number) {
            String b = building(buildingCode, "", "");
            String id = b + ":F:" + number;
            if (floors.add(id)) {
                String code = number < 0 ? "B" + (-number) : number + "F";
                insert("floors", values("id", id, "building_id", b, "code", code, "sort_order", number));
            }
            return id;
        }

        private String unspecifiedFloor(String buildingCode) {
            String b = building(buildingCode, "", "");
            String id = b + ":F:UNSPECIFIED";
            if (floors.add(id)) insert("floors", values("id", id, "building_id", b,
                    "code", "未指定楼层", "sort_order", Integer.MAX_VALUE));
            return id;
        }

        private String addSpace(String requestedId, String floorId, String name, String type,
                                String location, boolean visible, int capacity, String size, String subType) {
            String id = nonempty(requestedId, "LEGACY-SPACE-" + spaces.size());
            if (spaces.contains(id)) {
                int suffix = 1;
                String base = type + ":" + id;
                id = base;
                while (spaces.contains(id)) id = base + ":" + (++suffix);
            }
            spaces.add(id);
            insert("spaces", values("id", id, "floor_id", floorId, "code", id,
                    "name", nonempty(name, requestedId), "type", type, "location_hint", location,
                    "show_in_public_list", visible ? 1 : 0, "capacity", Math.max(0, capacity),
                    "size", size, "sub_type", subType));
            return id;
        }

        private void migrateSpace(ContentValues row) {
            String original = text(row, "id");
            String subType = "DISCUSSION_AREA".equals(text(row, "subType")) ? "DISCUSSION_AREA" : "CLASSROOM";
            String type = "PUBLIC".equals(text(row, "kind")) ? "OPEN_SEATING" : "CLASSROOM";
            String id = addSpace(original, floor(text(row, "building"), integer(row, "floor")),
                    text(row, "name"), type, "", !"CLASSROOM".equals(type) || "DISCUSSION_AREA".equals(subType),
                    integer(row, "capacity"), text(row, "size"), subType);
            targetSpaces.put(key("SPACE", original), id);
            facilities(id, text(row, "facilities"));
            if ("CLASSROOM".equals(type) && "DISCUSSION_AREA".equals(subType)) {
                String label = nonempty(text(row, "building"), "UNKNOWN") + "区";
                String group = group("DISCUSSION_AREA", label, "TIMELINE");
                resource("AREA-" + original, group, id, "AREA:" + original, text(row, "name"), 0, 0, "");
            }
        }

        private void facilities(String spaceId, String csv) {
            for (String part : csv.split(",")) {
                String tag = part.trim();
                if (!tag.isEmpty() && tags.add(spaceId + "\u0000" + tag)) {
                    insert("space_facilities", values("space_id", spaceId, "facility_code", tag));
                }
            }
        }

        private String group(String category, String label, String displayMode) {
            label = nonempty(label, "未分类");
            String id = category + ":" + label;
            if (groups.add(id)) insert("booking_groups", values("id", id, "category", category,
                    "label", label, "display_mode", displayMode, "sort_order", groups.size() - 1));
            return id;
        }

        private String resource(String requestedId, String groupId, String spaceId, String externalId,
                                String label, int gridRow, int gridCol, String url) {
            String id = requestedId;
            int suffix = 1;
            while (resources.contains(id)) id = requestedId + ":" + (++suffix);
            resources.add(id);
            insert("booking_resources", values("id", id, "group_id", groupId, "space_id", spaceId,
                    "source_id", BOOKING, "external_id", externalId, "label", label,
                    "grid_row", Math.max(0, gridRow), "grid_col", Math.max(0, gridCol), "official_url", configuredUrl(url)));
            resourceGroups.put(id, groupId);
            return id;
        }

        private void migrateRoom(ContentValues row, String category) {
            String original = text(row, "id");
            String id = addSpace(original, floor(text(row, "building"), integer(row, "floor")),
                    text(row, "name"), category, "", true, integer(row, "capacity"), "", "CLASSROOM");
            targetSpaces.put(key(category, original), id);
            facilities(id, text(row, "facilities"));
            String groupId = group(category, text(row, "zone"), "SLOTS");
            String resourceId = resource(original, groupId, id, category + ":" + original,
                    text(row, "name"), 0, 0, text(row, "bookingUrl"));
            targetResources.put(key(category, original), resourceId);
        }

        private void migrateSeat(ContentValues row) {
            String zone = nonempty(text(row, "zone"), "图书馆未分类区域");
            String areaId = "LIBRARY-AREA-" + zone;
            if (!spaces.contains(areaId)) {
                Matcher match = Pattern.compile("(\\d+)F", Pattern.CASE_INSENSITIVE).matcher(zone);
                String floorId = match.find() ? floor("LIB", Integer.parseInt(match.group(1))) : unspecifiedFloor("LIB");
                addSpace(areaId, floorId, zone, "LIBRARY_AREA", "", true, 0, "", "CLASSROOM");
            }
            String groupId = group("LIBRARY_SEAT", zone, "SEATS");
            String resourceId = resource(text(row, "id"), groupId, areaId, "SEAT:" + text(row, "id"),
                    text(row, "seatNo"), integer(row, "row"), integer(row, "col"), text(row, "bookingUrl"));
            String runId = sync("LEGACY-SEATS-" + groupId, BOOKING, "SEATS", groupId, at(0, 0), at(1, 0));
            String raw = text(row, "status");
            String status = oneOf(raw, "AVAILABLE", "PART_FREE", "RESERVED", "UNAVAILABLE") ? raw : "UNKNOWN";
            insert("seat_states", values("id", "LEGACY-STATE-" + resourceId, "resource_id", resourceId,
                    "sync_run_id", runId, "status", status, "raw_status", raw));
        }

        private String floorArea(String buildingCode, int number) {
            String b = nonempty(buildingCode, "UNKNOWN");
            String id = "FLOOR-AREA-" + b + "-" + number;
            if (!spaces.contains(id)) addSpace(id, floor(b, number), b + " " + (number < 0 ? "B" + (-number) : number + "F")
                    + " 公共区域", "PUBLIC_AREA", "由旧版仅有楼层的设备位置迁移", false, 0, "", "CLASSROOM");
            return id;
        }

        private void migrateFacility(ContentValues row) {
            String id = text(row, "id");
            String type = text(row, "type");
            String location = text(row, "locationText");
            if ("休息处".equals(type) || "开放座位区".equals(type)) {
                String spaceId = addSpace(id, floor(text(row, "building"), integer(row, "floor")),
                        type + " · " + location, "休息处".equals(type) ? "PUBLIC_REST" : "OPEN_SEATING",
                        location, true, 0, "", "CLASSROOM");
                targetSpaces.put(key("FACILITY", id), spaceId);
            } else {
                String spaceId = floorArea(text(row, "building"), integer(row, "floor"));
                insert("equipment", values("id", id, "space_id", spaceId, "type", type,
                        "name", type, "location_hint", location));
                equipmentSpaces.put(id, spaceId);
                targetSpaces.put(key("FACILITY", id), spaceId);
            }
        }

        private String sync(String id, String sourceId, String kind, String scope, long start, long end) {
            if (runs.add(id)) insert("sync_runs", values("id", id, "source_id", sourceId,
                    "data_kind", kind, "scope_key", scope, "window_start", start, "window_end", end,
                    "fetched_at", now, "status", "SUCCESS"));
            return id;
        }

        private void migrateLectures() {
            // Every classroom gets coverage, including legitimately empty weekly timetables.
            for (ContentValues row : old.get("spaces")) {
                String id = targetSpaces.get(key("SPACE", text(row, "id")));
                if (!"PUBLIC".equals(text(row, "kind"))) classroomRun(id);
            }
            for (ContentValues row : old.get("lectures")) {
                String original = text(row, "roomId");
                String spaceId = targetSpaces.get(key("SPACE", original));
                if (spaceId == null) {
                    spaceId = addSpace(original, unspecifiedFloor("UNKNOWN"), "旧版教室 " + original,
                            "CLASSROOM", "旧版课表引用的教室", false, 0, "", "CLASSROOM");
                    targetSpaces.put(key("SPACE", original), spaceId);
                }
                String runId = classroomRun(spaceId);
                for (int offset = -7; offset < 21; offset++) {
                    Calendar day = (Calendar) today.clone();
                    day.add(Calendar.DAY_OF_YEAR, offset);
                    int weekday = day.get(Calendar.DAY_OF_WEEK);
                    int iso = weekday == Calendar.SUNDAY ? 7 : weekday - 1;
                    if (iso != integer(row, "day")) continue;
                    long startsAt = at(offset, integer(row, "startMin"));
                    long endsAt = at(offset, integer(row, "endMin"));
                    String occurrence = original + ":" + startsAt + ":" + integer(row, "period");
                    insert("classroom_events", values("id", "LEGACY-EVENT-" + occurrence,
                            "space_id", spaceId, "sync_run_id", runId, "external_occurrence_id", occurrence,
                            "starts_at", startsAt, "ends_at", endsAt,
                            "course", text(row, "course"), "teacher", text(row, "teacher")));
                }
            }
        }

        private String classroomRun(String spaceId) {
            String id = classroomRuns.get(spaceId);
            if (id == null) {
                id = sync("LEGACY-EVENTS-" + spaceId, TIMETABLE, "EVENTS", spaceId, at(-7, 0), at(21, 0));
                classroomRuns.put(spaceId, id);
            }
            return id;
        }

        private void migrateSlots() {
            int minOffset = -1, maxOffset = 2;
            for (ContentValues row : old.get("slots")) {
                minOffset = Math.min(minOffset, integer(row, "dayOffset"));
                maxOffset = Math.max(maxOffset, integer(row, "dayOffset"));
            }
            for (ContentValues row : old.get("slots")) {
                String category = text(row, "targetType");
                String original = text(row, "targetId");
                String resourceId = targetResources.get(key(category, original));
                if (resourceId == null) {
                    String type = "DISCUSSION_ROOM".equals(category) ? "DISCUSSION_ROOM" : "QUIET_POD";
                    String spaceId = addSpace(original, unspecifiedFloor("UNKNOWN"), "旧版预约资源 " + original,
                            type, "旧版时段引用的资源", false, 0, "", "CLASSROOM");
                    String groupId = group(type, "旧版未分类", "SLOTS");
                    resourceId = resource(original, groupId, spaceId, type + ":" + original, original, 0, 0, "");
                    targetResources.put(key(category, original), resourceId);
                    targetSpaces.put(key(category, original), spaceId);
                }
                String groupId = resourceGroups.get(resourceId);
                String runId = sync("LEGACY-SLOTS-" + groupId, BOOKING, "SLOTS", groupId,
                        at(minOffset, 0), at(maxOffset + 1, 0));
                int offset = integer(row, "dayOffset");
                String raw = text(row, "status");
                String status = oneOf(raw, "FREE", "BOOKED") ? raw : "UNKNOWN";
                insert("booking_slots", values("id", "LEGACY-SLOT-" + category + ":" + original + ":" + offset + ":" + integer(row, "period"),
                        "resource_id", resourceId, "sync_run_id", runId,
                        "starts_at", at(offset, integer(row, "startMin")), "ends_at", at(offset, integer(row, "endMin")),
                        "status", status, "raw_status", raw));
            }
        }

        private String fallbackNoteSpace(String targetType, String targetId) {
            String buildingCode = buildings.contains(targetId) ? targetId : "UNKNOWN";
            if ("UNKNOWN".equals(buildingCode)) {
                for (String candidate : buildings) {
                    if (targetId.startsWith(candidate + "-")) { buildingCode = candidate; break; }
                }
            }
            String id = buildings.contains(targetId) ? "BUILDING-AREA-" + buildingCode
                    : "LEGACY-LOCATION-" + targetType + ":" + targetId;
            if (!spaces.contains(id)) addSpace(id, unspecifiedFloor(buildingCode),
                    buildings.contains(targetId) ? buildingCode + " 楼栋公共区域" : "旧版未指定空间",
                    "PUBLIC_AREA", "保留旧版纸条位置：" + targetType + " / " + targetId,
                    false, 0, "", "CLASSROOM");
            return id;
        }

        private void migrateNote(ContentValues row) {
            String id = text(row, "id");
            String targetType = text(row, "targetType");
            String targetId = text(row, "targetId");
            String spaceId = targetSpaces.get(key(targetType, targetId));
            if (spaceId == null) spaceId = fallbackNoteSpace(targetType, targetId);
            String authorId = "LEGACY-AUTHOR-" + id;
            // An old random nickname is not evidence that two notes belong to one person.
            insert("users", values("id", authorId, "auth_subject", null,
                    "display_name", text(row, "nickname")));
            String equipmentId = "FACILITY".equals(targetType) && equipmentSpaces.containsKey(targetId) ? targetId : null;
            String type = text(row, "category");
            if (!oneOf(type, "失物招领", "空间碎碎念", "设备报修")) type = "空间碎碎念";
            insert("notes", values("id", id, "space_id", spaceId, "author_id", authorId,
                    "equipment_id", equipmentId, "type", type, "body", text(row, "content"),
                    "created_at", longValue(row, "createdAt"), "status", "VISIBLE"));
            String image = text(row, "imagePath");
            if (!image.isEmpty()) insert("note_images", values("id", "LEGACY-IMAGE-" + id,
                    "note_id", id, "object_key", image, "sort_order", 0));
        }

        private long at(int dayOffset, int minute) {
            Calendar time = (Calendar) today.clone();
            time.add(Calendar.DAY_OF_YEAR, dayOffset);
            time.add(Calendar.MINUTE, minute);
            return time.getTimeInMillis();
        }

        private void insert(String table, ContentValues row) {
            db.insert(table, SQLiteDatabase.CONFLICT_ABORT, row);
        }
    }

    private static String key(String type, String id) { return type + "\u0000" + id; }
    private static String text(ContentValues row, String name) {
        String value = row.getAsString(name);
        return value == null ? "" : value;
    }
    private static int integer(ContentValues row, String name) {
        Integer value = row.getAsInteger(name);
        return value == null ? 0 : value;
    }
    private static long longValue(ContentValues row, String name) {
        Long value = row.getAsLong(name);
        return value == null ? 0L : value;
    }
    private static String nonempty(String value, String fallback) { return value.isEmpty() ? fallback : value; }
    private static String configuredUrl(String value) {
        return value.matches("https?://(www\\.)?xjtlu\\.edu\\.cn/?") ? "" : value;
    }
    private static boolean oneOf(String value, String... allowed) {
        for (String candidate : allowed) if (candidate.equals(value)) return true;
        return false;
    }
    private static ContentValues values(Object... pairs) {
        ContentValues result = new ContentValues();
        for (int i = 0; i < pairs.length; i += 2) {
            String key = (String) pairs[i];
            Object value = pairs[i + 1];
            if (value == null) result.putNull(key);
            else if (value instanceof Integer) result.put(key, (Integer) value);
            else if (value instanceof Long) result.put(key, (Long) value);
            else result.put(key, value.toString());
        }
        return result;
    }
}
