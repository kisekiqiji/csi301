package com.xjtlu.campusspace.ui;

import android.content.Context;
import android.content.Intent;
import com.xjtlu.campusspace.core.StatusCalculator;
import com.xjtlu.campusspace.core.TimeProvider;
import com.xjtlu.campusspace.data.Repo;
import com.xjtlu.campusspace.data.entity.Space;
import com.xjtlu.campusspace.data.entity.LibrarySeat;
import com.xjtlu.campusspace.data.store.Rows;
import com.xjtlu.campusspace.ui.booking.BookingDetailActivity;
import com.xjtlu.campusspace.ui.home.RoomDetailActivity;
import com.xjtlu.campusspace.ui.note.NotePublishActivity;

/** Shared navigation uses stored identifiers, never guesses an object's type from its ID. */
public final class SpaceUi {
    private SpaceUi() {}

    public static String typeLabel(Space s) {
        if (s.isDiscussionArea()) return "讨论区";
        switch (s.type == null ? "" : s.type) {
            case "CLASSROOM": return "LARGE".equals(s.size) ? "大教室" : "教室";
            case "DISCUSSION_ROOM": return "讨论室";
            case "QUIET_POD": return "静音仓";
            case "PUBLIC_REST": return "公共休息处";
            case "OPEN_SEATING": case "OPEN_SEAT_AREA": return "开放座位区";
            case "LIBRARY_AREA": return "图书馆座位区";
            default: return "公共空间";
        }
    }

    public static String subtitle(Space s) {
        String floor = s.floorCode == null || s.floorCode.isEmpty() ? s.floor + "F" : s.floorCode;
        return s.building + " · " + floor + " · " + typeLabel(s)
                + (s.capacity > 0 ? " · " + s.capacity + " 人" : "");
    }

    public static StatusCalculator.State state(Repo repo, Space s) {
        if (repo.hasSchedule(s.id, 0)) {
            return StatusCalculator.of(repo.lecturesOn(s.id, 0), TimeProvider.minuteOfDay());
        }
        Rows.BookingGroup group = repo.bookingGroupForSpace(s.id);
        if (group != null && "SLOTS".equals(group.displayMode)) {
            int free = 0, total = 0;
            for (Rows.BookingResource resource : repo.bookingResources(group.id)) {
                if (!s.id.equals(resource.spaceId)) continue;
                int[] stats = repo.slotStats(resource.id, 0);
                if (stats[0] < 0) return StatusCalculator.State.UNKNOWN;
                free += stats[0]; total += stats[1];
            }
            if (total > 0) return free > 0 ? StatusCalculator.State.FREE : StatusCalculator.State.IN_CLASS;
        }
        if (group != null && "SEATS".equals(group.displayMode)) {
            int known = 0, total = 0;
            for (LibrarySeat seat : repo.seatsOfGroup(group.id, 0)) {
                if (!s.id.equals(seat.spaceId)) continue;
                total++;
                if (LibrarySeat.AVAILABLE.equals(seat.status) || LibrarySeat.PART_FREE.equals(seat.status))
                    return StatusCalculator.State.FREE;
                if (!LibrarySeat.UNKNOWN.equals(seat.status)) known++;
            }
            if (total > 0 && known == total) return StatusCalculator.State.IN_CLASS;
        }
        return StatusCalculator.State.UNKNOWN;
    }

    public static void open(Context context, Space space) {
        open(context, space, 0, -1);
    }

    public static void open(Context context, Space space, int dayOffset, int queryMinute) {
        Repo repo = Repo.get(context);
        Rows.BookingGroup group = repo.bookingGroupForSpace(space.id);
        if (group != null) {
            for (Rows.BookingResource resource : repo.bookingResources(group.id)) {
                if (space.id.equals(resource.spaceId)) {
                    Intent i = new Intent(context, BookingDetailActivity.class);
                    i.putExtra(BookingDetailActivity.EXTRA_ID, resource.id);
                    i.putExtra(BookingDetailActivity.EXTRA_GROUP_ID, group.id);
                    i.putExtra(RoomDetailActivity.EXTRA_DAY_OFFSET, dayOffset);
                    i.putExtra(RoomDetailActivity.EXTRA_QUERY_MINUTE, queryMinute);
                    context.startActivity(i);
                    return;
                }
            }
        }
        Intent i = new Intent(context, RoomDetailActivity.class);
        i.putExtra(RoomDetailActivity.EXTRA_SPACE_ID, space.id);
        i.putExtra(RoomDetailActivity.EXTRA_DAY_OFFSET, dayOffset);
        i.putExtra(RoomDetailActivity.EXTRA_QUERY_MINUTE, queryMinute);
        context.startActivity(i);
    }

    public static void publish(Context context, String spaceId, String equipmentId) {
        Intent i = new Intent(context, NotePublishActivity.class);
        i.putExtra(NotePublishActivity.EXTRA_SPACE_ID, spaceId);
        i.putExtra(NotePublishActivity.EXTRA_EQUIPMENT_ID, equipmentId);
        context.startActivity(i);
    }
}
