package com.xjtlu.campusspace.data;

import android.content.Context;
import android.util.Log;

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

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * 首次启动把 assets/seed.json 灌进 Room。
 * 判断依据：spaces 表为空才灌，已经灌过就跳过。
 */
public final class SeedLoader {

    private static final String TAG = "SeedLoader";
    private static final String ASSET = "seed.json";

    private SeedLoader() {
    }

    public static void seedIfNeeded(Context context, AppDatabase db) {
        if (db.dao().countSpaces() > 0) {
            Log.i(TAG, "数据库已有数据，跳过灌种子");
            return;
        }
        String json = readAsset(context, ASSET);
        if (json == null) {
            Log.e(TAG, "读不到 assets/" + ASSET);
            return;
        }
        try {
            JSONObject root = new JSONObject(json);

            List<Building> buildings = new ArrayList<>();
            List<Space> spaces = new ArrayList<>();
            List<Lecture> lectures = new ArrayList<>();
            List<DiscussionRoom> discussionRooms = new ArrayList<>();
            List<QuietPod> quietPods = new ArrayList<>();
            List<BookableSlot> slots = new ArrayList<>();
            List<LibrarySeat> seats = new ArrayList<>();
            List<Facility> facilities = new ArrayList<>();
            List<Note> notes = new ArrayList<>();

            JSONArray ja = root.optJSONArray("buildings");
            if (ja != null) {
                for (int i = 0; i < ja.length(); i++) {
                    JSONObject o = ja.getJSONObject(i);
                    Building b = new Building();
                    b.code = o.optString("code");
                    b.name = o.optString("name");
                    b.campus = o.optString("campus");
                    b.floors = o.optInt("floors", 5);
                    buildings.add(b);
                }
            }

            ja = root.optJSONArray("rooms");
            if (ja != null) {
                for (int i = 0; i < ja.length(); i++) {
                    JSONObject o = ja.getJSONObject(i);
                    Space s = new Space();
                    s.id = o.optString("id");
                    s.building = o.optString("building");
                    s.floor = o.optInt("floor");
                    s.name = o.optString("name");
                    s.size = o.optString("size");
                    s.kind = o.optString("kind");
                    s.subType = o.optString("subType", Space.SUB_CLASSROOM);
                    s.capacity = o.optInt("capacity");
                    s.facilities = joinArray(o.optJSONArray("facilities"));
                    spaces.add(s);
                }
            }

            ja = root.optJSONArray("lectures");
            if (ja != null) {
                for (int i = 0; i < ja.length(); i++) {
                    JSONObject o = ja.getJSONObject(i);
                    Lecture l = new Lecture();
                    l.roomId = o.optString("roomId");
                    l.day = o.optInt("day");
                    l.period = o.optInt("period");
                    l.startMin = o.optInt("startMin");
                    l.endMin = o.optInt("endMin");
                    l.course = o.optString("course");
                    l.teacher = o.optString("teacher");
                    lectures.add(l);
                }
            }

            ja = root.optJSONArray("discussionRooms");
            if (ja != null) {
                for (int i = 0; i < ja.length(); i++) {
                    JSONObject o = ja.getJSONObject(i);
                    DiscussionRoom d = new DiscussionRoom();
                    d.id = o.optString("id");
                    d.zone = o.optString("zone");
                    d.building = o.optString("building");
                    d.floor = o.optInt("floor");
                    d.name = o.optString("name");
                    d.category = o.optString("category");
                    d.capacity = o.optInt("capacity");
                    d.facilities = joinArray(o.optJSONArray("facilities"));
                    d.bookingUrl = o.optString("bookingUrl");
                    discussionRooms.add(d);
                }
            }

            ja = root.optJSONArray("quietPods");
            if (ja != null) {
                for (int i = 0; i < ja.length(); i++) {
                    JSONObject o = ja.getJSONObject(i);
                    QuietPod p = new QuietPod();
                    p.id = o.optString("id");
                    p.zone = o.optString("zone");
                    p.building = o.optString("building");
                    p.floor = o.optInt("floor");
                    p.name = o.optString("name");
                    p.category = o.optString("category");
                    p.capacity = o.optInt("capacity");
                    p.facilities = joinArray(o.optJSONArray("facilities"));
                    p.bookingUrl = o.optString("bookingUrl");
                    quietPods.add(p);
                }
            }

            ja = root.optJSONArray("bookableSlots");
            if (ja != null) {
                for (int i = 0; i < ja.length(); i++) {
                    JSONObject o = ja.getJSONObject(i);
                    BookableSlot s = new BookableSlot();
                    s.targetId = o.optString("targetId");
                    s.targetType = o.optString("targetType");
                    s.dayOffset = o.optInt("dayOffset");
                    s.period = o.optInt("period");
                    s.startMin = o.optInt("startMin");
                    s.endMin = o.optInt("endMin");
                    s.status = o.optString("status", BookableSlot.FREE);
                    slots.add(s);
                }
            }

            ja = root.optJSONArray("librarySeats");
            if (ja != null) {
                for (int i = 0; i < ja.length(); i++) {
                    JSONObject o = ja.getJSONObject(i);
                    LibrarySeat s = new LibrarySeat();
                    s.id = o.optString("id");
                    s.zone = o.optString("zone");
                    s.row = o.optInt("row");
                    s.col = o.optInt("col");
                    s.seatNo = o.optString("seatNo");
                    s.status = o.optString("status", LibrarySeat.AVAILABLE);
                    s.bookingUrl = o.optString("bookingUrl");
                    seats.add(s);
                }
            }

            ja = root.optJSONArray("facilities");
            if (ja != null) {
                for (int i = 0; i < ja.length(); i++) {
                    JSONObject o = ja.getJSONObject(i);
                    Facility f = new Facility();
                    f.id = o.optString("id");
                    f.building = o.optString("building");
                    f.floor = o.optInt("floor");
                    f.type = o.optString("type");
                    f.locationText = o.optString("locationText");
                    facilities.add(f);
                }
            }

            ja = root.optJSONArray("notes");
            if (ja != null) {
                long now = TimeProvider.now();
                for (int i = 0; i < ja.length(); i++) {
                    JSONObject o = ja.getJSONObject(i);
                    Note n = new Note();
                    n.id = o.optString("id");
                    n.targetType = o.optString("targetType", Note.T_SPACE);
                    n.targetId = o.optString("targetId");
                    n.category = o.optString("category", Note.C_CHAT);
                    n.content = o.optString("content");
                    n.imagePath = o.isNull("imagePath") ? null : o.optString("imagePath");
                    // 种子里存的是"多少分钟前"，灌库时换算成绝对时间
                    n.createdAt = now - o.optLong("createdAtMinutesAgo", 60) * 60_000L;
                    n.nickname = o.optString("nickname");
                    notes.add(n);
                }
            }

            final List<Building> fB = buildings;
            final List<Space> fS = spaces;
            final List<Lecture> fL = lectures;
            final List<DiscussionRoom> fD = discussionRooms;
            final List<QuietPod> fQ = quietPods;
            final List<BookableSlot> fSlot = slots;
            final List<LibrarySeat> fSeat = seats;
            final List<Facility> fFac = facilities;
            final List<Note> fNote = notes;

            db.runInTransaction(() -> {
                AppDao dao = db.dao();
                dao.insertBuildings(fB);
                dao.insertSpaces(fS);
                dao.insertLectures(fL);
                dao.insertDiscussionRooms(fD);
                dao.insertQuietPods(fQ);
                dao.insertSlots(fSlot);
                dao.insertSeats(fSeat);
                dao.insertFacilities(fFac);
                dao.insertNotes(fNote);
            });

            Log.i(TAG, "灌种子完成：楼栋 " + fB.size() + "，教室 " + fS.size()
                    + "，课表 " + fL.size() + "，讨论室 " + fD.size()
                    + "，静音仓 " + fQ.size() + "，时段 " + fSlot.size()
                    + "，座位 " + fSeat.size() + "，设备 " + fFac.size()
                    + "，纸条 " + fNote.size());
        } catch (Exception e) {
            Log.e(TAG, "灌种子失败", e);
        }
    }

    private static String joinArray(JSONArray arr) {
        if (arr == null || arr.length() == 0) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < arr.length(); i++) {
            if (i > 0) sb.append(',');
            sb.append(arr.optString(i));
        }
        return sb.toString();
    }

    private static String readAsset(Context context, String name) {
        try (InputStream in = context.getAssets().open(name);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return new String(out.toByteArray(), "UTF-8");
        } catch (Exception e) {
            Log.e(TAG, "读取 asset 失败: " + name, e);
            return null;
        }
    }
}
