package com.xjtlu.campusspace.data;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import com.xjtlu.campusspace.core.TimeProvider;
import com.xjtlu.campusspace.data.entity.BookableSlot;
import com.xjtlu.campusspace.data.entity.Building;
import com.xjtlu.campusspace.data.entity.DiscussionRoom;
import com.xjtlu.campusspace.data.entity.Facility;
import com.xjtlu.campusspace.data.entity.Lecture;
import com.xjtlu.campusspace.data.entity.LibrarySeat;
import com.xjtlu.campusspace.data.entity.Note;
import com.xjtlu.campusspace.data.entity.QuietPod;
import com.xjtlu.campusspace.data.entity.Space;
import com.xjtlu.campusspace.data.store.Rows;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/** Normalized persistence boundary with lightweight projections for existing screens. */
public final class Repo {
    public interface Callback<T> { void onResult(T data); }

    public static final long SNAPSHOT_MAX_AGE_MILLIS = SnapshotSelector.DEFAULT_MAX_AGE_MILLIS;
    private static final TimeZone CAMPUS_ZONE = TimeZone.getTimeZone("Asia/Shanghai");
    private static volatile Repo instance;
    private final Context app;
    private final AppDatabase db;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final AtomicBoolean loading = new AtomicBoolean(false);
    private final AtomicBoolean loaded = new AtomicBoolean(false);
    private final List<Runnable> waiters = new ArrayList<>();
    // Guarded by waiters. A reload requested during a load gets one follow-up read.
    private long loadedDay = Long.MIN_VALUE;
    private boolean reloadRequested;
    private volatile Catalog catalog = new Catalog();
    private volatile String loadError;
    public final List<Building> buildings = new ArrayList<>();
    public String pendingBuilding;

    private static final class Catalog {
        final List<Building> buildings = new ArrayList<>();
        final List<Rows.Floor> floors = new ArrayList<>();
        final Map<String, String> buildingCodes = new HashMap<>();
        final Map<String, Space> spaces = new LinkedHashMap<>();
        final Map<String, Facility> equipment = new LinkedHashMap<>();
        final Map<String, Rows.BookingGroup> groups = new LinkedHashMap<>();
        final Map<String, Rows.BookingResource> resources = new LinkedHashMap<>();
        final Map<String, Rows.DataSource> sources = new HashMap<>();
        final List<Rows.ClassroomEvent> events = new ArrayList<>();
        final List<Rows.BookingSlot> slots = new ArrayList<>();
        final List<Rows.SeatState> seatStates = new ArrayList<>();
        final List<Rows.SyncRun> runs = new ArrayList<>();
    }

    private Repo(Context context) {
        app = context.getApplicationContext();
        db = AppDatabase.get(app);
    }

    public static Repo get(Context context) {
        if (instance == null) {
            synchronized (Repo.class) {
                if (instance == null) instance = new Repo(context);
            }
        }
        return instance;
    }

    public boolean isLoaded() { return loaded.get(); }
    public String getLoadError() { return loadError; }

    /** Release every waiter on success or failure; failed loads remain retryable. */
    public void ensureLoaded(Runnable onReady) {
        requestLoad(onReady, false);
    }

    /** Re-read after a published import, coalescing concurrent reload requests. */
    public void reload(Runnable onReady) {
        requestLoad(onReady, true);
    }

    private void requestLoad(Runnable onReady, boolean force) {
        long day = dayWindow(0)[0];
        synchronized (waiters) {
            if (!force && loaded.get() && loadedDay == day) {
                if (onReady != null) main.post(onReady);
                return;
            }
            if (onReady != null) waiters.add(onReady);
            if (!loading.compareAndSet(false, true)) {
                if (force) reloadRequested = true;
                return;
            }
            loaded.set(false);
        }
        startLoad(day);
    }

    private void startLoad(long day) {
        io.execute(() -> {
            Catalog result = null;
            String error = null;
            try {
                SeedLoader.seedIfNeeded(app, db);
                result = db.runInTransaction((Callable<Catalog>) this::loadCatalog);
            } catch (Exception e) {
                Log.e("CampusSpace", "Database initialization failed", e);
                error = "数据加载失败，请重试";
            }
            Catalog ready = result;
            String failure = error;
            main.post(() -> {
                List<Runnable> callbacks;
                synchronized (waiters) {
                    long currentDay = dayWindow(0)[0];
                    if (reloadRequested || day != currentDay) {
                        reloadRequested = false;
                        startLoad(currentDay);
                        return;
                    }
                    if (ready != null) {
                        catalog = ready;
                        buildings.clear();
                        buildings.addAll(ready.buildings);
                        loadedDay = day;
                        loaded.set(true);
                    }
                    loadError = failure;
                    loading.set(false);
                    callbacks = new ArrayList<>(waiters);
                    waiters.clear();
                }
                for (Runnable callback : callbacks) {
                    try { callback.run(); }
                    catch (RuntimeException e) { Log.e("CampusSpace", "Screen callback failed", e); }
                }
            });
        });
    }

    private Catalog loadCatalog() {
        AppDao dao = db.dao();
        Catalog out = new Catalog();
        List<Rows.Building> buildingRows = dao.buildings();
        buildingRows.sort(Comparator.comparingInt(b -> b.sortOrder));
        out.floors.addAll(dao.floors());
        out.floors.sort(Comparator.comparingInt(f -> f.sortOrder));
        Map<String, Rows.Floor> floorById = new HashMap<>();
        for (Rows.Floor floor : out.floors) floorById.put(floor.id, floor);
        for (Rows.Building row : buildingRows) {
            out.buildingCodes.put(row.id, row.code);
            Building building = new Building();
            building.id = row.id;
            building.code = row.code;
            building.name = row.name;
            building.campus = row.campusZone;
            for (Rows.Floor floor : out.floors) {
                if (row.id.equals(floor.buildingId)) building.floors++;
            }
            out.buildings.add(building);
        }
        Map<String, List<String>> tags = new HashMap<>();
        for (Rows.SpaceFacility row : dao.spaceFacilities()) {
            tags.computeIfAbsent(row.spaceId, ignored -> new ArrayList<>()).add(row.facilityCode);
        }
        for (Rows.Space row : dao.spaces()) {
            Rows.Floor floor = floorById.get(row.floorId);
            Space space = new Space();
            space.id = row.id;
            space.building = floor == null ? "" : value(out.buildingCodes.get(floor.buildingId));
            space.floorCode = floor == null ? "" : floor.code;
            space.floor = floorNumber(space.floorCode);
            space.type = row.type;
            space.kind = isClassroomType(row.type, row.subType) ? "CLASSROOM" : "PUBLIC";
            space.name = row.name;
            space.locationHint = row.locationHint;
            space.capacity = row.capacity;
            space.size = row.size;
            space.subType = row.subType;
            space.showInPublicList = row.showInPublicList;
            space.facilities = TextUtils.join(",", tags.getOrDefault(row.id, Collections.emptyList()));
            out.spaces.put(space.id, space);
        }
        for (Rows.Equipment row : dao.equipment()) {
            Space space = out.spaces.get(row.spaceId);
            Facility equipment = new Facility();
            equipment.id = row.id;
            equipment.spaceId = row.spaceId;
            equipment.name = row.name;
            equipment.type = row.type;
            equipment.locationText = row.locationHint;
            if (space != null) {
                equipment.building = space.building;
                equipment.floorCode = space.floorCode;
                equipment.floor = space.floor;
            }
            out.equipment.put(equipment.id, equipment);
        }
        List<Rows.BookingGroup> groups = dao.bookingGroups();
        groups.sort(Comparator.comparingInt(g -> g.sortOrder));
        for (Rows.BookingGroup group : groups) out.groups.put(group.id, group);
        for (Rows.BookingResource resource : dao.bookingResources()) out.resources.put(resource.id, resource);
        for (Rows.DataSource dataSource : dao.dataSources()) out.sources.put(dataSource.id, dataSource);
        out.events.addAll(dao.events());
        out.slots.addAll(dao.bookingSlots());
        out.seatStates.addAll(dao.seatStates());
        out.runs.addAll(dao.syncRuns());
        return out;
    }

    public List<Rows.Floor> floorsOf(String buildingCode) {
        Catalog data = catalog;
        List<Rows.Floor> result = new ArrayList<>();
        for (Rows.Floor floor : data.floors) {
            if (buildingCode != null && buildingCode.equals(data.buildingCodes.get(floor.buildingId))) {
                result.add(floor);
            }
        }
        return result;
    }

    public List<Space> allSpaces() { return new ArrayList<>(catalog.spaces.values()); }
    public List<Space> spacesOf(String buildingCode) { return spacesMatching(buildingCode, true); }
    public List<Space> publicSpacesOf(String buildingCode) { return spacesMatching(buildingCode, false); }

    private List<Space> spacesMatching(String buildingCode, boolean classrooms) {
        List<Space> result = new ArrayList<>();
        for (Space space : catalog.spaces.values()) {
            if (buildingCode != null && !buildingCode.equals(space.building)) continue;
            if (classrooms ? isClassroomType(space.type, space.subType) : space.showInPublicList) result.add(space);
        }
        return result;
    }

    public Space spaceOf(String id) { return catalog.spaces.get(id); }
    public Facility facilityOf(String id) { return catalog.equipment.get(id); }
    public List<Facility> facilities() { return new ArrayList<>(catalog.equipment.values()); }

    public List<Facility> facilitiesOf(String buildingCode, int floor) {
        List<Facility> result = new ArrayList<>();
        for (Facility equipment : catalog.equipment.values()) {
            if (equipment.building.equals(buildingCode) && equipment.floor == floor) result.add(equipment);
        }
        return result;
    }

    public List<Space> discussionAreas() { return discussionAreasOf(null); }
    public List<Space> discussionAreasOf(String buildingCode) {
        List<Space> result = new ArrayList<>();
        for (Space space : catalog.spaces.values()) {
            if (space.isDiscussionArea() && (buildingCode == null || buildingCode.equals(space.building))) {
                result.add(space);
            }
        }
        return result;
    }

    public List<Rows.BookingGroup> bookingGroups(String category) {
        List<Rows.BookingGroup> result = new ArrayList<>();
        for (Rows.BookingGroup group : catalog.groups.values()) {
            if (category == null || category.equals(group.category)) result.add(group);
        }
        return result;
    }

    public Rows.BookingGroup groupOf(String id) { return catalog.groups.get(id); }
    public Rows.BookingResource resourceOf(String id) { return catalog.resources.get(id); }

    public List<Rows.BookingResource> bookingResources(String groupId) {
        List<Rows.BookingResource> result = new ArrayList<>();
        for (Rows.BookingResource resource : catalog.resources.values()) {
            if (groupId == null || groupId.equals(resource.groupId)) result.add(resource);
        }
        result.sort(Comparator.comparingInt((Rows.BookingResource r) -> r.gridRow)
                .thenComparingInt(r -> r.gridCol).thenComparing(r -> r.label));
        return result;
    }

    public Rows.BookingGroup bookingGroupForSpace(String spaceId) {
        for (Rows.BookingResource resource : catalog.resources.values()) {
            if (spaceId != null && spaceId.equals(resource.spaceId)) return groupOf(resource.groupId);
        }
        return null;
    }

    public Space spaceForTarget(String type, String id) {
        if (Note.T_FACILITY.equals(type)) {
            Facility equipment = facilityOf(id);
            return equipment == null ? null : spaceOf(equipment.spaceId);
        }
        if (Note.T_SPACE.equals(type) || type == null) return spaceOf(id);
        Rows.BookingResource resource = resourceOf(id);
        return resource == null ? null : spaceOf(resource.spaceId);
    }

    public boolean hasSchedule(String spaceId, int dayOffset) {
        return spaceOf(spaceId) != null && selectedRun(null, "EVENTS", spaceId, dayOffset) != null;
    }

    public List<Lecture> lecturesOn(String spaceId, int dayOffset) {
        Rows.SyncRun run = selectedRun(null, "EVENTS", spaceId, dayOffset);
        List<Lecture> result = new ArrayList<>();
        if (run == null) return result;
        long[] window = dayWindow(dayOffset);
        for (Rows.ClassroomEvent event : catalog.events) {
            if (!spaceId.equals(event.spaceId) || !run.id.equals(event.syncRunId)
                    || event.startsAt >= window[1] || event.endsAt <= window[0]) continue;
            Lecture lecture = new Lecture();
            lecture.roomId = event.spaceId;
            lecture.startsAt = event.startsAt;
            lecture.endsAt = event.endsAt;
            lecture.startMin = minuteWithinDay(Math.max(event.startsAt, window[0]), window);
            lecture.endMin = minuteWithinDay(Math.min(event.endsAt, window[1]), window);
            lecture.day = isoDay(window[0]);
            lecture.course = event.course;
            lecture.teacher = event.teacher;
            result.add(lecture);
        }
        result.sort(Comparator.comparingInt(l -> l.startMin));
        for (int i = 0; i < result.size(); i++) result.get(i).period = i;
        return result;
    }

    /** Compatibility only: chooses a dated day, never repeats a weekly schedule. */
    @Deprecated public List<Lecture> lecturesOf(String spaceId, int isoDay) {
        int offset = isoDay - TimeProvider.isoDayOfWeek();
        if (offset < -3) offset += 7;
        if (offset > 3) offset -= 7;
        return lecturesOn(spaceId, offset);
    }

    public boolean hasBookingSnapshot(String resourceId, int dayOffset) {
        Rows.BookingResource resource = resourceOf(resourceId);
        return resource != null && selectedRun(resource.sourceId, "SLOTS", resource.groupId, dayOffset) != null;
    }

    public List<BookableSlot> slotsOf(String resourceId, int dayOffset) {
        List<BookableSlot> result = new ArrayList<>();
        Rows.BookingResource resource = resourceOf(resourceId);
        if (resource == null) return result;
        Rows.SyncRun run = selectedRun(resource.sourceId, "SLOTS", resource.groupId, dayOffset);
        if (run == null) return result;
        Rows.BookingGroup group = groupOf(resource.groupId);
        long[] window = dayWindow(dayOffset);
        for (Rows.BookingSlot row : catalog.slots) {
            if (!resourceId.equals(row.resourceId) || !run.id.equals(row.syncRunId)
                    || row.startsAt >= window[1] || row.endsAt <= window[0]) continue;
            BookableSlot slot = new BookableSlot();
            slot.targetId = resourceId;
            slot.targetType = group == null ? "" : group.category;
            slot.dayOffset = dayOffset;
            slot.startsAt = row.startsAt;
            slot.endsAt = row.endsAt;
            slot.startMin = minuteWithinDay(Math.max(row.startsAt, window[0]), window);
            slot.endMin = minuteWithinDay(Math.min(row.endsAt, window[1]), window);
            slot.status = BookableSlot.FREE.equals(row.status) ? BookableSlot.FREE
                    : BookableSlot.BOOKED.equals(row.status) ? BookableSlot.BOOKED : BookableSlot.UNKNOWN;
            result.add(slot);
        }
        result.sort(Comparator.comparingLong(s -> s.startsAt));
        for (int i = 0; i < result.size(); i++) result.get(i).period = i;
        return result;
    }

    /** {-1,0} means unknown; {0,0} means a successful snapshot with no slots. */
    public int[] slotStats(String resourceId, int dayOffset) {
        if (!hasBookingSnapshot(resourceId, dayOffset)) return new int[]{-1, 0};
        List<BookableSlot> slots = slotsOf(resourceId, dayOffset);
        int free = 0, total = 0;
        boolean unknown = false;
        long now = TimeProvider.now();
        for (BookableSlot slot : slots) {
            if (dayOffset == 0 && slot.endsAt <= now) continue;
            total++;
            if (BookableSlot.UNKNOWN.equals(slot.status)) unknown = true;
            if (slot.isFree()) free++;
        }
        return new int[]{unknown ? -1 : free, total};
    }

    public List<LibrarySeat> seatsOfGroup(String groupId, int dayOffset) {
        List<LibrarySeat> result = new ArrayList<>();
        Rows.BookingGroup group = groupOf(groupId);
        if (group == null) return result;
        for (Rows.BookingResource resource : bookingResources(groupId)) {
            Rows.SyncRun run = selectedRun(resource.sourceId, "SEATS", groupId, dayOffset);
            LibrarySeat seat = new LibrarySeat();
            seat.id = resource.id;
            seat.resourceId = resource.id;
            seat.groupId = groupId;
            seat.spaceId = resource.spaceId;
            seat.zone = group.label;
            seat.row = resource.gridRow;
            seat.col = resource.gridCol;
            seat.seatNo = resource.label;
            seat.bookingUrl = bookingUrl(resource);
            if (run != null) {
                for (Rows.SeatState state : catalog.seatStates) {
                    if (run.id.equals(state.syncRunId) && resource.id.equals(state.resourceId)) {
                        seat.status = seatStatus(state.status);
                        break;
                    }
                }
            }
            result.add(seat);
        }
        return result;
    }

    public List<LibrarySeat> seats() {
        List<LibrarySeat> result = new ArrayList<>();
        for (Rows.BookingGroup group : catalog.groups.values()) {
            if (isSeatGroup(group)) result.addAll(seatsOfGroup(group.id, 0));
        }
        return result;
    }

    public Map<String, List<LibrarySeat>> seatsByZone() {
        Map<String, List<LibrarySeat>> result = new LinkedHashMap<>();
        for (Rows.BookingGroup group : catalog.groups.values()) {
            if (isSeatGroup(group)) result.put(group.label, seatsOfGroup(group.id, 0));
        }
        return result;
    }

    public List<LibrarySeat> seatsOfZone(String zone) {
        for (Rows.BookingGroup group : catalog.groups.values()) {
            if (group.label.equals(zone)) return seatsOfGroup(group.id, 0);
        }
        return new ArrayList<>();
    }

    public List<DiscussionRoom> discussionRooms() {
        List<DiscussionRoom> result = new ArrayList<>();
        for (Rows.BookingResource resource : catalog.resources.values()) {
            Rows.BookingGroup group = groupOf(resource.groupId);
            if (group == null || !"DISCUSSION_ROOM".equals(group.category)) continue;
            Space space = spaceOf(resource.spaceId);
            if (space == null) continue;
            DiscussionRoom row = new DiscussionRoom();
            row.id = resource.id;
            row.spaceId = space.id;
            row.groupId = group.id;
            row.zone = group.label;
            row.building = space.building;
            row.floor = space.floor;
            row.name = resource.label;
            row.category = group.category;
            row.capacity = space.capacity;
            row.facilities = space.facilities;
            row.bookingUrl = bookingUrl(resource);
            result.add(row);
        }
        return result;
    }

    public List<QuietPod> quietPods() {
        List<QuietPod> result = new ArrayList<>();
        for (Rows.BookingResource resource : catalog.resources.values()) {
            Rows.BookingGroup group = groupOf(resource.groupId);
            if (group == null || !"QUIET_POD".equals(group.category)) continue;
            Space space = spaceOf(resource.spaceId);
            if (space == null) continue;
            QuietPod row = new QuietPod();
            row.id = resource.id;
            row.spaceId = space.id;
            row.groupId = group.id;
            row.zone = group.label;
            row.building = space.building;
            row.floor = space.floor;
            row.name = resource.label;
            row.category = group.category;
            row.capacity = space.capacity;
            row.facilities = space.facilities;
            row.bookingUrl = bookingUrl(resource);
            result.add(row);
        }
        return result;
    }

    public String bookingUrl(Rows.BookingResource resource) {
        if (resource == null) return "";
        if (!blank(resource.officialUrl)) return resource.officialUrl;
        Rows.DataSource dataSource = catalog.sources.get(resource.sourceId);
        return dataSource == null ? "" : value(dataSource.defaultBookingUrl);
    }

    public String bookingUrlForResource(String resourceId) { return bookingUrl(resourceOf(resourceId)); }

    public String dataLabel() {
        Catalog data = catalog;
        Map<String, String[]> scopes = new LinkedHashMap<>();
        for (Space space : data.spaces.values()) {
            if (!isClassroomType(space.type, space.subType)) continue;
            String sourceId = SnapshotSelector.preferredSource(data.runs, data.sources, "EVENTS", space.id);
            scopes.put("EVENTS:" + space.id, new String[]{sourceId, "EVENTS", space.id});
        }
        for (Rows.BookingResource resource : data.resources.values()) {
            Rows.BookingGroup group = data.groups.get(resource.groupId);
            if (group == null || (!"SLOTS".equals(group.displayMode) && !"SEATS".equals(group.displayMode))) continue;
            scopes.put(group.displayMode + ":" + group.id + ":" + resource.sourceId,
                    new String[]{resource.sourceId, group.displayMode, group.id});
        }
        if (scopes.isEmpty()) return "数据未就绪";
        long now = System.currentTimeMillis();
        long[] today = dayWindow(0);
        int valid = 0;
        long lastFetchedAt = 0;
        boolean hasDemo = false, hasReal = false;
        for (String[] scope : scopes.values()) {
            Rows.DataSource dataSource = data.sources.get(scope[0]);
            if (dataSource == null) continue;
            if (dataSource.isDemo) hasDemo = true; else hasReal = true;
            Rows.SyncRun run = SnapshotSelector.latestSuccessful(data.runs, scope[0], scope[1], scope[2],
                    today[0], today[1], now, SNAPSHOT_MAX_AGE_MILLIS);
            if (run != null) {
                valid++;
                lastFetchedAt = Math.max(lastFetchedAt, run.fetchedAt);
            }
        }
        String prefix = hasDemo ? (hasReal ? "含演示数据" : "演示数据") : "数据快照";
        if (valid == 0) return prefix + " · 已过期或缺失，状态未知";
        SimpleDateFormat format = new SimpleDateFormat("MM-dd HH:mm", Locale.CHINA);
        format.setTimeZone(CAMPUS_ZONE);
        return prefix + " · 更新于 " + format.format(new Date(lastFetchedAt))
                + (valid < scopes.size() ? " · 部分状态未知" : "");
    }

    public void notesOf(String targetType, String targetId, Callback<List<Note>> callback) {
        io.execute(() -> {
            List<Note> result = new ArrayList<>();
            try {
                if (Note.T_FACILITY.equals(targetType)) {
                    if (facilityOf(targetId) != null) result = mapNotes(db.dao().notesOfEquipment(targetId));
                } else {
                    Space space = spaceForTarget(targetType, targetId);
                    if (space != null) result = mapNotes(db.dao().notesOfSpace(space.id));
                }
            } catch (Exception e) { Log.e("CampusSpace", "Cannot load notes", e); }
            List<Note> ready = result;
            main.post(() -> callback.onResult(ready));
        });
    }

    public void latestNotes(int limit, Callback<List<Note>> callback) {
        io.execute(() -> {
            List<Note> result = new ArrayList<>();
            try { result = mapNotes(db.dao().latestNotes(Math.max(0, Math.min(limit, 1000)))); }
            catch (Exception e) { Log.e("CampusSpace", "Cannot load latest notes", e); }
            List<Note> ready = result;
            main.post(() -> callback.onResult(ready));
        });
    }

    private List<Note> mapNotes(List<Rows.Note> rows) {
        Map<String, Rows.User> authors = new HashMap<>();
        for (Rows.User user : db.dao().users()) authors.put(user.id, user);
        Map<String, List<Rows.NoteImage>> images = new HashMap<>();
        for (Rows.NoteImage image : db.dao().noteImages()) {
            images.computeIfAbsent(image.noteId, ignored -> new ArrayList<>()).add(image);
        }
        List<Note> result = new ArrayList<>();
        for (Rows.Note row : rows) {
            if (!"VISIBLE".equals(row.status)) continue;
            Note note = new Note();
            note.id = row.id;
            note.spaceId = row.spaceId;
            note.equipmentId = row.equipmentId;
            note.authorId = row.authorId;
            note.targetType = row.equipmentId == null ? Note.T_SPACE : Note.T_FACILITY;
            note.targetId = row.equipmentId == null ? row.spaceId : row.equipmentId;
            note.category = row.type;
            note.content = row.body;
            note.createdAt = row.createdAt;
            Rows.User author = authors.get(row.authorId);
            note.nickname = author == null ? "匿名同学" : author.displayName;
            List<Rows.NoteImage> attached = new ArrayList<>(images.getOrDefault(row.id, Collections.emptyList()));
            attached.sort(Comparator.comparingInt(image -> image.sortOrder));
            for (Rows.NoteImage image : attached) note.imagePaths.add(imagePath(image.objectKey));
            note.imagePath = note.imagePaths.isEmpty() ? null : note.imagePaths.get(0);
            result.add(note);
        }
        return result;
    }

    public void addNote(Note note, Runnable onDone) {
        addNote(note, onDone, error -> Toast.makeText(app, error, Toast.LENGTH_LONG).show());
    }

    public void addNote(Note note, Runnable onDone, Callback<String> onError) {
        io.execute(() -> {
            try {
                db.runInTransaction(() -> saveNote(note));
                main.post(() -> { if (onDone != null) onDone.run(); });
            } catch (Exception e) {
                Log.e("CampusSpace", "Cannot save note", e);
                String error = e instanceof IllegalArgumentException ? e.getMessage() : "纸条保存失败，请重试";
                main.post(() -> { if (onError != null) onError.onResult(error); });
            }
        });
    }

    private void saveNote(Note note) {
        if (note == null || blank(note.content)) throw new IllegalArgumentException("请填写纸条内容");
        if (note.content.trim().length() > 2000) throw new IllegalArgumentException("纸条内容最多2000字");
        if (!Note.C_LOST.equals(note.category) && !Note.C_CHAT.equals(note.category)
                && !Note.C_REPAIR.equals(note.category)) throw new IllegalArgumentException("请选择有效的纸条分类");
        String equipmentId = blank(note.equipmentId)
                ? (Note.T_FACILITY.equals(note.targetType) ? note.targetId : null) : note.equipmentId;
        Rows.Equipment equipment = null;
        if (!blank(equipmentId)) {
            for (Rows.Equipment candidate : db.dao().equipment()) {
                if (equipmentId.equals(candidate.id)) { equipment = candidate; break; }
            }
            if (equipment == null) throw new IllegalArgumentException("所选设备不存在");
        }
        String spaceId = note.spaceId;
        if (blank(spaceId)) {
            if (equipment != null) spaceId = equipment.spaceId;
            else {
                Space space = spaceForTarget(note.targetType, note.targetId);
                spaceId = space == null ? null : space.id;
            }
        }
        boolean exists = false;
        for (Rows.Space space : db.dao().spaces()) {
            if (space.id.equals(spaceId)) { exists = true; break; }
        }
        if (!exists) throw new IllegalArgumentException("请选择一个具体且有效的空间");
        if (equipment != null && !equipment.spaceId.equals(spaceId)) {
            throw new IllegalArgumentException("所选设备不属于当前空间");
        }
        LinkedHashSet<String> paths = new LinkedHashSet<>();
        if (note.imagePaths != null) for (String path : note.imagePaths) if (!blank(path)) paths.add(path);
        if (paths.isEmpty() && !blank(note.imagePath)) paths.add(note.imagePath);
        if (paths.size() > 4) throw new IllegalArgumentException("每条纸条最多添加4张图片");
        Rows.User author = anonymousAuthor();
        Rows.Note row = new Rows.Note();
        row.id = blank(note.id) ? newNoteId() : note.id;
        row.spaceId = spaceId;
        row.equipmentId = blank(equipmentId) ? null : equipmentId;
        row.authorId = author.id;
        row.type = note.category;
        row.body = note.content.trim();
        row.createdAt = note.createdAt > 0 ? note.createdAt : TimeProvider.now();
        row.status = "VISIBLE";
        db.dao().insertNote(row);
        List<Rows.NoteImage> images = new ArrayList<>();
        for (String path : paths) {
            Rows.NoteImage image = new Rows.NoteImage();
            image.id = "IMAGE-" + UUID.randomUUID();
            image.noteId = row.id;
            image.objectKey = imageKey(path);
            image.sortOrder = images.size();
            images.add(image);
        }
        if (!images.isEmpty()) db.dao().insertNoteImages(images);
        note.id = row.id;
        note.spaceId = row.spaceId;
        note.equipmentId = row.equipmentId;
        note.authorId = row.authorId;
        note.nickname = author.displayName;
    }

    private Rows.User anonymousAuthor() {
        String userId = app.getSharedPreferences("campusspace_identity", Context.MODE_PRIVATE)
                .getString("anonymous_user_id", null);
        if (userId == null) {
            userId = "USER-" + UUID.randomUUID();
            if (!app.getSharedPreferences("campusspace_identity", Context.MODE_PRIVATE).edit()
                    .putString("anonymous_user_id", userId).commit()) {
                throw new IllegalStateException("Unable to persist anonymous identity");
            }
        }
        for (Rows.User user : db.dao().users()) if (userId.equals(user.id)) return user;
        Rows.User author = new Rows.User();
        author.id = userId;
        author.authSubject = null;
        author.displayName = "匿名同学" + userId.substring(userId.length() - 4).toUpperCase(Locale.ROOT);
        db.dao().insertUser(author);
        return author;
    }

    public static String newNoteId() { return "NOTE-" + UUID.randomUUID().toString().toUpperCase(Locale.ROOT); }

    private Rows.SyncRun selectedRun(String sourceId, String kind, String scope, int dayOffset) {
        if (scope == null) return null;
        long[] window = dayWindow(dayOffset);
        List<Rows.SyncRun> candidates = catalog.runs;
        if (sourceId == null && "EVENTS".equals(kind)) {
            // Once a real source is configured for this space, a fresh demo must not mask its failure.
            sourceId = SnapshotSelector.preferredSource(candidates, catalog.sources, kind, scope);
            if (sourceId == null) return null;
        }
        return SnapshotSelector.latestSuccessful(candidates, sourceId, kind, scope,
                window[0], window[1], System.currentTimeMillis(), SNAPSHOT_MAX_AGE_MILLIS);
    }

    private static long[] dayWindow(int dayOffset) {
        return SnapshotSelector.dayWindow(TimeProvider.now(), dayOffset, CAMPUS_ZONE);
    }

    private static int minuteWithinDay(long timestamp, long[] window) {
        if (timestamp <= window[0]) return 0;
        if (timestamp >= window[1]) return 1440;
        Calendar calendar = Calendar.getInstance(CAMPUS_ZONE);
        calendar.setTimeInMillis(timestamp);
        return calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE);
    }

    private static int isoDay(long timestamp) {
        Calendar calendar = Calendar.getInstance(CAMPUS_ZONE);
        calendar.setTimeInMillis(timestamp);
        int day = calendar.get(Calendar.DAY_OF_WEEK);
        return day == Calendar.SUNDAY ? 7 : day - 1;
    }

    private static boolean isClassroomType(String type, String subType) {
        return "CLASSROOM".equals(type) || "DISCUSSION_AREA".equals(type)
                || Space.SUB_DISCUSSION_AREA.equals(subType);
    }

    private static boolean isSeatGroup(Rows.BookingGroup group) {
        return "SEAT_GRID".equals(group.displayMode) || "SEAT".equals(group.category)
                || "LIBRARY_SEAT".equals(group.category);
    }

    private static String seatStatus(String status) {
        if (LibrarySeat.AVAILABLE.equals(status) || LibrarySeat.PART_FREE.equals(status)
                || LibrarySeat.RESERVED.equals(status) || LibrarySeat.UNAVAILABLE.equals(status)) return status;
        return LibrarySeat.UNKNOWN;
    }

    private static int floorNumber(String code) {
        if (blank(code)) return 0;
        try {
            String normalized = code.toUpperCase(Locale.ROOT).replace("F", "");
            if (normalized.startsWith("B")) return -Integer.parseInt(normalized.substring(1));
            return Integer.parseInt(normalized);
        } catch (NumberFormatException ignored) { return 0; }
    }

    private String imageKey(String path) {
        String prefix = app.getFilesDir().getAbsolutePath() + File.separator;
        return path.startsWith(prefix) ? path.substring(prefix.length()).replace(File.separatorChar, '/') : path;
    }

    private String imagePath(String key) {
        if (blank(key)) return null;
        return new File(key).isAbsolute() ? key : new File(app.getFilesDir(), key).getAbsolutePath();
    }

    private static boolean blank(String value) { return value == null || value.trim().isEmpty(); }
    private static String value(String value) { return value == null ? "" : value; }
}
