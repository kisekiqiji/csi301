package com.xjtlu.campusspace.data;

import android.content.Context;
import com.xjtlu.campusspace.core.TimeProvider;
import com.xjtlu.campusspace.data.store.Rows;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.*;

/** Converts bundled demo templates into dated, normalized rows without replacing user data. */
public final class SeedLoader {
    public static final String TIMETABLE = "DEMO_TIMETABLE", BOOKING = "DEMO_BOOKING";
    private SeedLoader() { }

    public static void seedIfNeeded(Context context, AppDatabase db) {
        try {
            JSONObject seed = new JSONObject(read(context, "seed.json"));
            JSONArray catalog = new JSONObject(read(context, "catalog.json")).getJSONArray("buildings");
            db.runInTransaction(() -> {
                try {
                    supplementCatalog(db, catalog); ensureSources(db);
                    if (db.dao().countSpaces() == 0) insertMasterData(db, seed);
                    refreshDemoIfNeeded(db, seed);
                } catch (Exception e) { throw new IllegalStateException("初始化校园数据失败", e); }
            });
        } catch (Exception e) { throw new IllegalStateException("读取本地演示数据失败", e); }
    }

    private static void supplementCatalog(AppDatabase db, JSONArray catalog) throws Exception {
        Set<String> codes = new HashSet<>();
        for (Rows.Building b : db.dao().buildings()) codes.add(b.code);
        List<Rows.Building> rows = new ArrayList<>();
        for (int i = 0; i < catalog.length(); i++) {
            JSONObject item = catalog.getJSONObject(i); if (!codes.add(item.getString("code"))) continue;
            Rows.Building b = new Rows.Building(); b.id = item.getString("code"); b.code = b.id;
            b.name = item.getString("name"); b.campusZone = item.getString("campusZone"); b.sortOrder = i; rows.add(b);
        }
        db.dao().insertBuildings(rows);
    }

    private static void ensureSources(AppDatabase db) {
        Set<String> ids = new HashSet<>(); for (Rows.DataSource s : db.dao().dataSources()) ids.add(s.id);
        List<Rows.DataSource> missing = new ArrayList<>();
        for (String id : new String[]{TIMETABLE, BOOKING}) {
            if (ids.contains(id)) continue;
            Rows.DataSource s = new Rows.DataSource(); s.id = id; s.code = id; s.isDemo = true;
            s.name = TIMETABLE.equals(id) ? "演示课表" : "演示预约状态";
            s.defaultBookingUrl = ""; missing.add(s);
        }
        db.dao().insertDataSources(missing);
    }

    private static void insertMasterData(AppDatabase db, JSONObject seed) throws Exception {
        Map<String, Rows.Floor> floors = new LinkedHashMap<>();
        Map<String, Rows.Space> spaces = new LinkedHashMap<>();
        Map<String, Rows.BookingGroup> groups = new LinkedHashMap<>();
        List<Rows.SpaceFacility> tags = new ArrayList<>(); List<Rows.BookingResource> resources = new ArrayList<>();
        List<Rows.Equipment> devices = new ArrayList<>();
        for (JSONObject item : items(seed, "rooms")) {
            Rows.Space s = space(item.getString("id"), item.getString("building"), item.getInt("floor"), item.getString("name"), "CLASSROOM", floors);
            s.capacity = item.optInt("capacity"); s.size = item.optString("size"); s.subType = item.optString("subType", "CLASSROOM");
            s.showInPublicList = "DISCUSSION_AREA".equals(s.subType); spaces.put(s.id, s); tags(tags, s.id, item.optJSONArray("facilities"));
            if (s.showInPublicList) {
                Rows.BookingGroup g = group(groups, "DISCUSSION_AREA", item.getString("building") + "区", "TIMELINE");
                resources.add(resource(s.id, g.id, s.id, s.name, ""));
            }
        }
        for (String key : new String[]{"discussionRooms", "quietPods"}) {
            String type = "discussionRooms".equals(key) ? "DISCUSSION_ROOM" : "QUIET_POD";
            for (JSONObject item : items(seed, key)) {
                Rows.Space s = space(item.getString("id"), item.getString("building"), item.getInt("floor"), item.getString("name"), type, floors);
                s.capacity = item.optInt("capacity"); s.showInPublicList = true; spaces.put(s.id, s); tags(tags, s.id, item.optJSONArray("facilities"));
                Rows.BookingGroup g = group(groups, type, item.getString("zone"), "SLOTS");
                resources.add(resource(s.id, g.id, s.id, s.name, configuredUrl(item.optString("bookingUrl"))));
            }
        }
        for (JSONObject item : items(seed, "librarySeats")) {
            String zone = item.getString("zone"), id = "LIBRARY-AREA-" + zone;
            if (!spaces.containsKey(id)) {
                int floor = Integer.parseInt(zone.replaceAll("^.*?([0-9]+)F.*$", "$1"));
                Rows.Space s = space(id, "LIB", floor, zone + " 座位区", "LIBRARY_AREA", floors); s.showInPublicList = true; spaces.put(id, s);
            }
            Rows.BookingGroup g = group(groups, "LIBRARY_SEAT", zone, "SEATS");
            Rows.BookingResource r = resource(item.getString("id"), g.id, id, item.getString("seatNo"), configuredUrl(item.optString("bookingUrl")));
            r.gridRow = item.getInt("row"); r.gridCol = item.getInt("col"); resources.add(r);
        }
        for (JSONObject item : items(seed, "facilities")) {
            String type = item.getString("type"), building = item.getString("building"); int floor = item.getInt("floor");
            if ("休息处".equals(type) || "开放座位区".equals(type)) {
                Rows.Space s = space(item.getString("id"), building, floor, building + " " + floor + "F " + type,
                        "休息处".equals(type) ? "PUBLIC_REST" : "OPEN_SEATING", floors);
                s.locationHint = item.getString("locationText"); s.showInPublicList = true; spaces.put(s.id, s);
            } else {
                String parent = "FLOOR-AREA-" + building + "-" + floor;
                if (!spaces.containsKey(parent)) spaces.put(parent, space(parent, building, floor, building + " " + floor + "F 公共区域", "PUBLIC_AREA", floors));
                Rows.Equipment e = new Rows.Equipment(); e.id = item.getString("id"); e.spaceId = parent; e.type = type; e.name = type;
                e.locationHint = item.getString("locationText"); devices.add(e);
            }
        }
        db.dao().insertFloors(new ArrayList<>(floors.values())); db.dao().insertSpaces(new ArrayList<>(spaces.values()));
        db.dao().insertSpaceFacilities(tags); db.dao().insertEquipment(devices);
        db.dao().insertBookingGroups(new ArrayList<>(groups.values())); db.dao().insertBookingResources(resources);
        Map<String, Rows.Equipment> byId = new HashMap<>(); for (Rows.Equipment e : devices) byId.put(e.id, e);
        List<Rows.User> users = new ArrayList<>(); List<Rows.Note> notes = new ArrayList<>();
        for (JSONObject item : items(seed, "notes")) {
            Rows.User u = new Rows.User(); u.id = "SEED-AUTHOR-" + item.getString("id"); u.displayName = item.optString("nickname", "匿名同学"); users.add(u);
            String target = item.getString("targetId"); Rows.Equipment e = byId.get(target);
            Rows.Note n = new Rows.Note(); n.id = item.getString("id"); n.authorId = u.id; n.spaceId = e == null ? target : e.spaceId;
            n.equipmentId = e == null ? null : target; n.type = item.getString("category"); n.body = item.getString("content"); n.status = "VISIBLE";
            n.createdAt = TimeProvider.now() - item.optLong("createdAtMinutesAgo", 60) * 60_000L; notes.add(n);
        }
        db.dao().insertUsers(users); db.dao().insertNotes(notes);
    }

    private static Rows.Space space(String id, String building, int floor, String name, String type, Map<String, Rows.Floor> floors) {
        String floorId = building + ":F:" + floor;
        if (!floors.containsKey(floorId)) {
            Rows.Floor f = new Rows.Floor(); f.id = floorId; f.buildingId = building; f.code = floor < 0 ? "B" + (-floor) : floor + "F";
            f.sortOrder = floor; floors.put(floorId, f);
        }
        Rows.Space s = new Rows.Space(); s.id = id; s.floorId = floorId; s.code = id; s.name = name; s.type = type; return s;
    }
    private static void tags(List<Rows.SpaceFacility> rows, String spaceId, JSONArray values) throws Exception {
        if (values == null) return; Set<String> seen = new HashSet<>();
        for (int i = 0; i < values.length(); i++) {
            String value = values.getString(i); if (!seen.add(value)) continue;
            Rows.SpaceFacility tag = new Rows.SpaceFacility(); tag.spaceId = spaceId; tag.facilityCode = value; rows.add(tag);
        }
    }
    private static Rows.BookingGroup group(Map<String, Rows.BookingGroup> groups, String category, String label, String mode) {
        String id = category + ":" + label;
        if (!groups.containsKey(id)) {
            Rows.BookingGroup g = new Rows.BookingGroup(); g.id = id; g.category = category; g.label = label; g.displayMode = mode; g.sortOrder = groups.size(); groups.put(id, g);
        }
        return groups.get(id);
    }
    private static Rows.BookingResource resource(String id, String group, String space, String label, String url) {
        Rows.BookingResource r = new Rows.BookingResource(); r.id = id; r.externalId = id; r.groupId = group; r.spaceId = space;
        r.sourceId = BOOKING; r.label = label; r.officialUrl = url; return r;
    }
    private static String configuredUrl(String value) { return value.matches("https?://(www\\.)?xjtlu\\.edu\\.cn/?") ? "" : value; }

    private static void refreshDemoIfNeeded(AppDatabase db, JSONObject seed) throws Exception {
        long now = TimeProvider.now(), today = dayStart(now, 0);
        List<Rows.SyncRun> existingRuns = db.dao().syncRuns();
        Map<String, List<JSONObject>> lectures = new HashMap<>(), slots = new HashMap<>();
        Set<String> seedRooms = new HashSet<>(); for (JSONObject r : items(seed, "rooms")) seedRooms.add(r.getString("id"));
        for (JSONObject t : items(seed, "lectures")) lectures.computeIfAbsent(t.getString("roomId"), x -> new ArrayList<>()).add(t);
        for (JSONObject t : items(seed, "bookableSlots")) slots.computeIfAbsent(t.getString("targetId"), x -> new ArrayList<>()).add(t);
        Map<String, JSONObject> seats = new HashMap<>(); for (JSONObject t : items(seed, "librarySeats")) seats.put(t.getString("id"), t);
        List<SnapshotImporter.Snapshot> snapshots = new ArrayList<>();
        for (Rows.Space space : db.dao().spaces()) {
            if (!"CLASSROOM".equals(space.type) || !seedRooms.contains(space.id)) continue;
            if (fresh(existingRuns, TIMETABLE, "EVENTS", space.id, now, -1, 8)) continue;
            SnapshotImporter.Snapshot s = new SnapshotImporter.Snapshot(run(TIMETABLE, "EVENTS", space.id, now));
            for (int offset = -1; offset <= 7; offset++) {
                Calendar c = Calendar.getInstance(TimeZone.getTimeZone("Asia/Shanghai")); c.setTimeInMillis(dayStart(now, offset)); int day = c.get(Calendar.DAY_OF_WEEK); day = day == Calendar.SUNDAY ? 7 : day - 1;
                for (JSONObject t : lectures.getOrDefault(space.id, Collections.emptyList())) {
                    if (t.getInt("day") != day) continue;
                    Rows.ClassroomEvent e = new Rows.ClassroomEvent(); e.syncRunId = s.run.id; e.spaceId = space.id;
                    e.externalOccurrenceId = space.id + ":" + dayStart(now, offset) + ":" + t.getInt("period"); e.id = s.run.id + ":" + e.externalOccurrenceId;
                    e.startsAt = minuteAt(now, offset, t.getInt("startMin")); e.endsAt = minuteAt(now, offset, t.getInt("endMin"));
                    e.course = t.optString("course"); e.teacher = t.optString("teacher"); s.events.add(e);
                }
            }
            snapshots.add(s);
        }
        List<Rows.BookingResource> resources = db.dao().bookingResources();
        for (Rows.BookingGroup group : db.dao().bookingGroups()) {
            if (!"SLOTS".equals(group.displayMode) && !"SEATS".equals(group.displayMode)) continue;
            boolean seatGroup = "SEATS".equals(group.displayMode);
            if (fresh(existingRuns, BOOKING, group.displayMode, group.id, now, seatGroup ? 0 : -1, seatGroup ? 1 : 3)) continue;
            SnapshotImporter.Snapshot s = new SnapshotImporter.Snapshot(run(BOOKING, group.displayMode, group.id, now)); boolean populated = false;
            for (Rows.BookingResource r : resources) {
                if (!group.id.equals(r.groupId) || !BOOKING.equals(r.sourceId)) continue;
                if ("SLOTS".equals(group.displayMode)) {
                    if (!slots.containsKey(r.id)) continue; populated = true;
                    for (JSONObject t : slots.get(r.id)) {
                        int off = t.getInt("dayOffset"); Rows.BookingSlot slot = new Rows.BookingSlot();
                        slot.id = s.run.id + ":" + r.id + ":" + off + ":" + t.getInt("period"); slot.resourceId = r.id; slot.syncRunId = s.run.id;
                        slot.startsAt = minuteAt(now, off, t.getInt("startMin")); slot.endsAt = minuteAt(now, off, t.getInt("endMin"));
                        slot.rawStatus = t.optString("status"); slot.status = SnapshotImporter.normalizeSlotStatus(slot.rawStatus); s.slots.add(slot);
                    }
                } else {
                    JSONObject t = seats.get(r.id); if (t == null) continue; populated = true;
                    Rows.SeatState seat = new Rows.SeatState(); seat.id = s.run.id + ":" + r.id; seat.resourceId = r.id; seat.syncRunId = s.run.id;
                    seat.rawStatus = t.optString("status"); seat.status = SnapshotImporter.normalizeSeatStatus(seat.rawStatus); s.seats.add(seat);
                }
            }
            if (populated) {
                if ("SEATS".equals(group.displayMode)) { s.run.windowStart = today; s.run.windowEnd = dayStart(now, 1); }
                snapshots.add(s);
            }
        }
        SnapshotImporter.publish(db, snapshots);
    }
    private static boolean fresh(List<Rows.SyncRun> runs, String source, String kind, String scope, long now, int from, int until) {
        for (Rows.SyncRun r : runs) {
            if (source.equals(r.sourceId) && kind.equals(r.dataKind) && scope.equals(r.scopeKey)
                    && "SUCCESS".equals(r.status) && r.fetchedAt >= dayStart(now, 0) && r.fetchedAt <= now
                    && r.windowStart <= dayStart(now, from) && r.windowEnd >= dayStart(now, until)) return true;
        }
        return false;
    }
    private static Rows.SyncRun run(String source, String kind, String scope, long now) {
        Rows.SyncRun r = new Rows.SyncRun(); r.id = "DEMO-" + UUID.randomUUID(); r.sourceId = source; r.dataKind = kind; r.scopeKey = scope;
        r.windowStart = dayStart(now, -1); r.windowEnd = dayStart(now, "EVENTS".equals(kind) ? 8 : 3); r.fetchedAt = now; return r;
    }
    public static long dayStart(long now, int offset) {
        Calendar c = Calendar.getInstance(TimeZone.getTimeZone("Asia/Shanghai")); c.setTimeInMillis(now); c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0);
        c.add(Calendar.DAY_OF_MONTH, offset); return c.getTimeInMillis();
    }
    private static long minuteAt(long now, int offset, int minute) {
        Calendar c = Calendar.getInstance(TimeZone.getTimeZone("Asia/Shanghai")); c.setTimeInMillis(dayStart(now, offset)); c.add(Calendar.MINUTE, minute); return c.getTimeInMillis();
    }
    private static List<JSONObject> items(JSONObject root, String key) throws Exception {
        JSONArray array = root.getJSONArray(key); List<JSONObject> list = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) list.add(array.getJSONObject(i)); return list;
    }
    private static String read(Context context, String name) throws Exception {
        try (InputStream in = context.getAssets().open(name); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192]; int n; while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n); return out.toString("UTF-8");
        }
    }
}
