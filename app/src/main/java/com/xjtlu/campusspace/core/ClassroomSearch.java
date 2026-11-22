package com.xjtlu.campusspace.core;

import com.xjtlu.campusspace.data.entity.Lecture;
import com.xjtlu.campusspace.data.entity.Space;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;

/** Pure search rules; querying a future time never changes the application's clock. */
public final class ClassroomSearch {
    private static final TimeZone CAMPUS_ZONE = TimeZone.getTimeZone("Asia/Shanghai");
    public enum StatusFilter { ALL, FREE, SOON }
    public interface StateProvider { StatusCalculator.State stateOf(Space space); }

    public static final class Filters {
        public String building;
        public String keyword = "";
        public int minimumCapacity;
        public String floorCode;
        public final Set<String> facilities = new LinkedHashSet<>();
        public StatusFilter status = StatusFilter.ALL;
    }

    public static final class Match {
        public final Space space;
        public final StatusCalculator.State state;
        private Match(Space space, StatusCalculator.State state) { this.space = space; this.state = state; }
    }

    public static final class Results {
        public final List<Match> matches;
        public final int candidateCount, freeCount, unknownCount;
        private Results(List<Match> matches, int candidates, int free, int unknown) {
            this.matches = Collections.unmodifiableList(matches);
            candidateCount = candidates;
            freeCount = free;
            unknownCount = unknown;
        }
    }

    private ClassroomSearch() { }

    public static Results search(List<Space> spaces, Filters filters, StateProvider provider) {
        List<Match> matches = new ArrayList<>();
        int candidates = 0, free = 0, unknown = 0;
        for (Space space : spaces) {
            if (!matchesMetadata(space, filters)) continue;
            candidates++;
            StatusCalculator.State state = provider.stateOf(space);
            if (state == null) state = StatusCalculator.State.UNKNOWN;
            boolean available = state == StatusCalculator.State.FREE || state == StatusCalculator.State.SOON;
            if (available) free++;
            if (state == StatusCalculator.State.UNKNOWN) unknown++;
            if (filters.status == StatusFilter.FREE && !available) continue;
            if (filters.status == StatusFilter.SOON && state != StatusCalculator.State.SOON) continue;
            matches.add(new Match(space, state));
        }
        return new Results(matches, candidates, free, unknown);
    }

    public static boolean matchesMetadata(Space space, Filters filters) {
        if (space == null) return false;
        if (filters.building != null && !filters.building.equalsIgnoreCase(value(space.building))) return false;
        if (space.capacity < filters.minimumCapacity) return false;
        if (filters.floorCode != null && !filters.floorCode.equalsIgnoreCase(value(space.floorCode))) return false;
        String keyword = value(filters.keyword).trim().toLowerCase(Locale.ROOT);
        if (!keyword.isEmpty()) {
            String searchable = value(space.name) + " " + value(space.id) + " " + value(space.building);
            if (!searchable.toLowerCase(Locale.ROOT).contains(keyword)) return false;
        }
        for (String required : filters.facilities) {
            boolean found = false;
            for (String actual : space.facilityList()) {
                if (facilityKey(required).equals(facilityKey(actual))) { found = true; break; }
            }
            if (!found) return false;
        }
        return true;
    }

    public static StatusCalculator.State stateAt(List<Lecture> lectures, int minute, boolean covered) {
        if (minute < 0 || minute >= 1440) return StatusCalculator.State.UNKNOWN;
        return StatusCalculator.of(lectures, minute, covered);
    }

    public static long dayStart(long timestamp, int dayOffset) {
        Calendar calendar = Calendar.getInstance(CAMPUS_ZONE);
        calendar.setTimeInMillis(timestamp);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        calendar.add(Calendar.DATE, dayOffset);
        return calendar.getTimeInMillis();
    }

    public static int dayOffset(long selectedDate, long now) {
        return (int) ((dateKey(selectedDate) - dateKey(now)) / 86400000L);
    }

    // Compare calendar dates rather than device timezone or elapsed hours.
    private static long dateKey(long timestamp) {
        Calendar local = Calendar.getInstance(CAMPUS_ZONE);
        local.setTimeInMillis(timestamp);
        Calendar utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        utc.clear();
        utc.set(local.get(Calendar.YEAR), local.get(Calendar.MONTH), local.get(Calendar.DATE));
        return utc.getTimeInMillis();
    }

    private static String facilityKey(String facility) {
        String key = value(facility).trim().toUpperCase(Locale.ROOT);
        if ("投影仪".equals(key) || "PROJECTOR".equals(key)) return "投影";
        if ("WHITEBOARD".equals(key)) return "白板";
        return key;
    }

    private static String value(String text) { return text == null ? "" : text; }
}
