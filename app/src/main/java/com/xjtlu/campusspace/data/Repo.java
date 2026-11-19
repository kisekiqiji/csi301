package com.xjtlu.campusspace.data;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.xjtlu.campusspace.data.entity.BookableSlot;
import com.xjtlu.campusspace.data.entity.Building;
import com.xjtlu.campusspace.data.entity.DiscussionRoom;
import com.xjtlu.campusspace.data.entity.Facility;
import com.xjtlu.campusspace.data.entity.Lecture;
import com.xjtlu.campusspace.data.entity.LibrarySeat;
import com.xjtlu.campusspace.data.entity.Note;
import com.xjtlu.campusspace.data.entity.QuietPod;
import com.xjtlu.campusspace.data.entity.Space;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 全局数据入口。
 * 200 多间教室 + 1800 条课表 + 720 个时段，数据量很小，一次性读进内存，
 * 之后所有界面都是同步读取，不用到处写异步回调。
 */
public final class Repo {

    /** 界面回调：保证在主线程执行 */
    public interface Callback<T> {
        void onResult(T data);
    }

    private static volatile Repo instance;

    private final Context app;
    private final AppDatabase db;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final AtomicBoolean loading = new AtomicBoolean(false);
    private final AtomicBoolean loaded = new AtomicBoolean(false);
    private final List<Runnable> waiters = new ArrayList<>();

    // ---- 内存缓存 ----
    public final List<Building> buildings = new ArrayList<>();
    private final List<Space> spaces = new ArrayList<>();
    private final List<DiscussionRoom> discussionRooms = new ArrayList<>();
    private final List<QuietPod> quietPods = new ArrayList<>();
    private final List<LibrarySeat> seats = new ArrayList<>();
    private final List<Facility> facilities = new ArrayList<>();
    private final List<BookableSlot> slots = new ArrayList<>();
    private final Map<String, List<Lecture>> lecturesByRoom = new HashMap<>();

    /** Tab3 点"查看空间详情"后，要让 Tab1 切到指定楼栋用 */
    public String pendingBuilding;

    private Repo(Context context) {
        this.app = context.getApplicationContext();
        this.db = AppDatabase.get(app);
    }

    public static Repo get(Context context) {
        if (instance == null) {
            synchronized (Repo.class) {
                if (instance == null) instance = new Repo(context);
            }
        }
        return instance;
    }

    public boolean isLoaded() {
        return loaded.get();
    }

    /** 第一次进入时灌种子 + 读进内存；已经就绪时立刻回调 */
    public void ensureLoaded(Runnable onReady) {
        if (loaded.get()) {
            onReady.run();
            return;
        }
        synchronized (waiters) {
            waiters.add(onReady);
            if (!loading.compareAndSet(false, true)) return;
        }
        io.execute(() -> {
            SeedLoader.seedIfNeeded(app, db);
            List<Building> b = db.dao().buildings();
            List<Space> s = db.dao().spaces();
            List<Lecture> lec = db.dao().lectures();
            List<DiscussionRoom> d = db.dao().discussionRooms();
            List<QuietPod> q = db.dao().quietPods();
            List<BookableSlot> sl = db.dao().slots();
            List<LibrarySeat> se = db.dao().seats();
            List<Facility> fa = db.dao().facilities();

            Map<String, List<Lecture>> byRoom = new HashMap<>();
            for (Lecture l : lec) {
                List<Lecture> list = byRoom.get(l.roomId);
                if (list == null) {
                    list = new ArrayList<>();
                    byRoom.put(l.roomId, list);
                }
                list.add(l);
            }

            main.post(() -> {
                buildings.clear();
                buildings.addAll(b);
                spaces.clear();
                spaces.addAll(s);
                discussionRooms.clear();
                discussionRooms.addAll(d);
                quietPods.clear();
                quietPods.addAll(q);
                seats.clear();
                seats.addAll(se);
                facilities.clear();
                facilities.addAll(fa);
                slots.clear();
                slots.addAll(sl);
                lecturesByRoom.clear();
                lecturesByRoom.putAll(byRoom);
                loaded.set(true);

                List<Runnable> toRun;
                synchronized (waiters) {
                    toRun = new ArrayList<>(waiters);
                    waiters.clear();
                }
                for (Runnable r : toRun) r.run();
            });
        });
    }

    // ------------------------------------------------ 同步读取

    public List<Space> spacesOf(String buildingCode) {
        if (buildingCode == null) return new ArrayList<>(spaces);
        List<Space> out = new ArrayList<>();
        for (Space s : spaces) {
            if (buildingCode.equals(s.building)) out.add(s);
        }
        Collections.sort(out, (a, b) -> {
            if (a.floor != b.floor) return Integer.compare(a.floor, b.floor);
            return a.name.compareTo(b.name);
        });
        return out;
    }

    public List<Space> discussionAreas() {
        List<Space> out = new ArrayList<>();
        for (Space s : spaces) if (s.isDiscussionArea()) out.add(s);
        return out;
    }

    public List<Space> discussionAreasOf(String buildingCode) {
        List<Space> out = new ArrayList<>();
        for (Space s : spaces) {
            if (s.isDiscussionArea() && buildingCode.equals(s.building)) out.add(s);
        }
        return out;
    }

    public Space spaceOf(String id) {
        for (Space s : spaces) if (s.id.equals(id)) return s;
        return null;
    }

    /** 某间教室在某天的课表（已按开始时间排好序） */
    public List<Lecture> lecturesOf(String roomId, int isoDay) {
        List<Lecture> all = lecturesByRoom.get(roomId);
        if (all == null) return new ArrayList<>();
        List<Lecture> out = new ArrayList<>();
        for (Lecture l : all) if (l.day == isoDay) out.add(l);
        Collections.sort(out, (a, b) -> Integer.compare(a.startMin, b.startMin));
        return out;
    }

    public List<DiscussionRoom> discussionRooms() {
        return new ArrayList<>(discussionRooms);
    }

    public List<QuietPod> quietPods() {
        return new ArrayList<>(quietPods);
    }

    public List<LibrarySeat> seats() {
        return new ArrayList<>(seats);
    }

    public List<Facility> facilities() {
        return new ArrayList<>(facilities);
    }

    /** 座位按区域分组，保持种子里的顺序 */
    public Map<String, List<LibrarySeat>> seatsByZone() {
        Map<String, List<LibrarySeat>> map = new LinkedHashMap<>();
        for (LibrarySeat s : seats) {
            List<LibrarySeat> list = map.get(s.zone);
            if (list == null) {
                list = new ArrayList<>();
                map.put(s.zone, list);
            }
            list.add(s);
        }
        return map;
    }

    public List<Facility> facilitiesOf(String buildingCode, int floor) {
        List<Facility> out = new ArrayList<>();
        for (Facility f : facilities) {
            if (f.building.equals(buildingCode) && f.floor == floor) out.add(f);
        }
        return out;
    }

    /** 某个图书馆区域的座位 */
    public List<LibrarySeat> seatsOfZone(String zone) {
        List<LibrarySeat> out = new ArrayList<>();
        for (LibrarySeat s : seats) {
            if (s.zone.equals(zone)) out.add(s);
        }
        return out;
    }

    public List<BookableSlot> slotsOf(String targetId, int dayOffset) {
        List<BookableSlot> out = new ArrayList<>();
        for (BookableSlot s : slots) {
            if (s.targetId.equals(targetId) && s.dayOffset == dayOffset) out.add(s);
        }
        Collections.sort(out, (a, b) -> Integer.compare(a.period, b.period));
        return out;
    }

    /** 某个区域今天还剩多少空闲时段，用于列表卡片的绿/黄/红 */
    public int[] slotStats(String targetId, int dayOffset) {
        int free = 0, total = 0;
        for (BookableSlot s : slots) {
            if (s.targetId.equals(targetId) && s.dayOffset == dayOffset) {
                total++;
                if (s.isFree()) free++;
            }
        }
        return new int[]{free, total};
    }

    // ------------------------------------------------ 纸条（走数据库）

    public void notesOf(String targetType, String targetId, Callback<List<Note>> cb) {
        io.execute(() -> {
            List<Note> rows = db.dao().notesOf(targetType, targetId);
            main.post(() -> cb.onResult(rows));
        });
    }

    public void latestNotes(int limit, Callback<List<Note>> cb) {
        io.execute(() -> {
            List<Note> rows = db.dao().latestNotes(limit);
            main.post(() -> cb.onResult(rows));
        });
    }

    public void addNote(Note note, Runnable onDone) {
        io.execute(() -> {
            db.dao().insertNote(note);
            main.post(() -> {
                if (onDone != null) onDone.run();
            });
        });
    }

    public static String newNoteId() {
        return "NOTE-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}
