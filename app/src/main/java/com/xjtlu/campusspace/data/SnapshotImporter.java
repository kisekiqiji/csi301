package com.xjtlu.campusspace.data;

import com.xjtlu.campusspace.data.store.Rows;
import java.util.*;

/** Complete snapshots, including empty successes, become visible in one transaction. */
public final class SnapshotImporter {
    private SnapshotImporter() { }
    public static final class Snapshot {
        public final Rows.SyncRun run;
        public final List<Rows.ClassroomEvent> events = new ArrayList<>();
        public final List<Rows.BookingSlot> slots = new ArrayList<>();
        public final List<Rows.SeatState> seats = new ArrayList<>();
        public Snapshot(Rows.SyncRun run) { this.run = run; }
    }
    public static void publish(AppDatabase db, List<Snapshot> snapshots) {
        db.runInTransaction(() -> {
            Map<String, Rows.Space> spaces = new HashMap<>();
            for (Rows.Space r : db.dao().spaces()) spaces.put(r.id, r);
            Map<String, Rows.BookingResource> resources = new HashMap<>();
            for (Rows.BookingResource r : db.dao().bookingResources()) resources.put(r.id, r);
            Map<String, Rows.BookingGroup> groups = new HashMap<>();
            for (Rows.BookingGroup r : db.dao().bookingGroups()) groups.put(r.id, r);
            Set<String> sources = new HashSet<>();
            for (Rows.DataSource r : db.dao().dataSources()) sources.add(r.id);
            for (Snapshot s : snapshots) {
                Rows.SyncRun r = s.run;
                require(r != null && !r.id.isEmpty() && sources.contains(r.sourceId), "同步来源或批次无效");
                require(r.windowStart < r.windowEnd && r.fetchedAt > 0, "同步时间范围无效");
                if ("EVENTS".equals(r.dataKind)) {
                    require(spaces.containsKey(r.scopeKey) && s.slots.isEmpty() && s.seats.isEmpty(), "课表批次类型或空间无效");
                    for (Rows.ClassroomEvent e : s.events) {
                        require(r.id.equals(e.syncRunId) && r.scopeKey.equals(e.spaceId) && !e.externalOccurrenceId.isEmpty(), "课次归属无效");
                        range(e.startsAt, e.endsAt, r);
                    }
                } else {
                    Rows.BookingGroup g = groups.get(r.scopeKey);
                    require(g != null && r.dataKind.equals(g.displayMode) && s.events.isEmpty(), "预约批次类型或分组无效");
                    if ("SLOTS".equals(r.dataKind)) {
                        require(s.seats.isEmpty(), "时段批次混入座位状态");
                        for (Rows.BookingSlot slot : s.slots) {
                            resource(slot.resourceId, slot.syncRunId, r, resources); range(slot.startsAt, slot.endsAt, r);
                            slot.status = normalizeSlotStatus(slot.status);
                        }
                    } else if ("SEATS".equals(r.dataKind)) {
                        require(s.slots.isEmpty(), "座位批次混入时段");
                        for (Rows.SeatState seat : s.seats) {
                            resource(seat.resourceId, seat.syncRunId, r, resources); seat.status = normalizeSeatStatus(seat.status);
                        }
                    } else throw new IllegalArgumentException("未知同步数据类型");
                }
                r.status = "SUCCESS";
                db.dao().insertSyncRuns(Collections.singletonList(r));
                db.dao().insertEvents(s.events); db.dao().insertBookingSlots(s.slots); db.dao().insertSeatStates(s.seats);
            }
        });
    }
    private static void resource(String id, String runId, Rows.SyncRun r, Map<String, Rows.BookingResource> resources) {
        Rows.BookingResource item = resources.get(id);
        require(item != null && r.id.equals(runId) && r.sourceId.equals(item.sourceId) && r.scopeKey.equals(item.groupId), "预约资源来源或分组不一致");
    }
    private static void range(long start, long end, Rows.SyncRun r) {
        require(start < end && start >= r.windowStart && end <= r.windowEnd, "数据超出同步窗口");
    }
    public static String normalizeSlotStatus(String raw) {
        return "FREE".equals(raw) || "BOOKED".equals(raw) ? raw : "UNKNOWN";
    }
    public static String normalizeSeatStatus(String raw) {
        return "AVAILABLE".equals(raw) || "PART_FREE".equals(raw) || "RESERVED".equals(raw) || "UNAVAILABLE".equals(raw) ? raw : "UNKNOWN";
    }
    private static void require(boolean valid, String message) {
        if (!valid) throw new IllegalArgumentException(message);
    }
}
