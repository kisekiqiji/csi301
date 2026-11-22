package com.xjtlu.campusspace.core;

import com.xjtlu.campusspace.data.entity.Lecture;
import com.xjtlu.campusspace.data.entity.Space;
import org.junit.Test;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.TimeZone;
import static org.junit.Assert.*;

public class ClassroomSearchTest {
    private Space room(String id, String building, int capacity, String floor, String facilities) {
        Space space = new Space(); space.id = id; space.name = id; space.building = building;
        space.capacity = capacity; space.floorCode = floor; space.facilities = facilities;
        return space;
    }

    @Test public void combinesBuildingNameCapacityFloorAndAllRequiredFacilities() {
        ClassroomSearch.Filters filters = new ClassroomSearch.Filters();
        filters.keyword = " fb-101 "; filters.building = "fb"; filters.minimumCapacity = 50;
        filters.floorCode = "1F"; filters.facilities.add("白板"); filters.facilities.add("投影");
        assertTrue(ClassroomSearch.matchesMetadata(room("FB-101", "FB", 50, "1F", "白板,投影仪"), filters));
        assertFalse(ClassroomSearch.matchesMetadata(room("FB-101", "FB", 49, "1F", "白板,投影"), filters));
        assertFalse(ClassroomSearch.matchesMetadata(room("FB-101", "FB", 70, "2F", "白板,投影"), filters));
        assertFalse(ClassroomSearch.matchesMetadata(room("FB-101", "FB", 70, "1F", "无白板,投影"), filters));
    }

    @Test public void allBuildingsSupportsCodeSearchAndUnknownIsNeverFree() {
        Space fb = room("FB-101", "FB", 20, "1F", "白板");
        Space cb = room("CB-202", "CB", 60, "2F", "投影");
        ClassroomSearch.Filters filters = new ClassroomSearch.Filters();
        filters.status = ClassroomSearch.StatusFilter.FREE;
        ClassroomSearch.Results result = ClassroomSearch.search(Arrays.asList(fb, cb), filters,
                space -> space == fb ? StatusCalculator.State.UNKNOWN : StatusCalculator.State.FREE);
        assertEquals(2, result.candidateCount); assertEquals(1, result.unknownCount);
        assertEquals(1, result.matches.size()); assertSame(cb, result.matches.get(0).space);
        filters.keyword = "cb";
        assertEquals(1, ClassroomSearch.search(Arrays.asList(fb, cb), filters,
                ignored -> StatusCalculator.State.FREE).matches.size());
    }

    @Test public void chosenMinuteUsesHalfOpenLectureTimesAndCoverage() {
        Lecture lecture = new Lecture(); lecture.startMin = 840; lecture.endMin = 900;
        assertEquals(StatusCalculator.State.SOON, ClassroomSearch.stateAt(Collections.singletonList(lecture), 810, true));
        assertEquals(StatusCalculator.State.IN_CLASS, ClassroomSearch.stateAt(Collections.singletonList(lecture), 840, true));
        assertEquals(StatusCalculator.State.FREE, ClassroomSearch.stateAt(Collections.singletonList(lecture), 900, true));
        assertEquals(StatusCalculator.State.UNKNOWN, ClassroomSearch.stateAt(Collections.emptyList(), 840, false));
        assertEquals(StatusCalculator.State.UNKNOWN, ClassroomSearch.stateAt(Collections.emptyList(), 1440, true));
    }

    @Test public void selectedDateSurvivesMidnightAndYearBoundaryInCampusTimezone() {
        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("Asia/Shanghai"));
        calendar.clear(); calendar.set(2026, Calendar.DECEMBER, 31, 23, 50);
        long now = calendar.getTimeInMillis();
        long tomorrow = ClassroomSearch.dayStart(now, 1);
        assertEquals(1, ClassroomSearch.dayOffset(tomorrow, now));
        assertEquals(0, ClassroomSearch.dayOffset(tomorrow, now + 20 * 60000L));
        assertEquals(-1, ClassroomSearch.dayOffset(ClassroomSearch.dayStart(now, 0), tomorrow));
        assertEquals(9, ClassroomSearch.dayOffset(ClassroomSearch.dayStart(now, 9), now));
    }

    @Test public void soonIsAvailableAtQueryTimeButNotAfterTheClassStarts() {
        Space room = room("FB-101", "FB", 30, "1F", "白板");
        Lecture lecture = new Lecture(); lecture.startMin = 855; lecture.endMin = 900;
        ClassroomSearch.Filters filters = new ClassroomSearch.Filters();
        filters.status = ClassroomSearch.StatusFilter.FREE;
        assertEquals(1, ClassroomSearch.search(Collections.singletonList(room), filters,
                ignored -> ClassroomSearch.stateAt(Collections.singletonList(lecture), 840, true)).matches.size());
        assertEquals(0, ClassroomSearch.search(Collections.singletonList(room), filters,
                ignored -> ClassroomSearch.stateAt(Collections.singletonList(lecture), 855, true)).matches.size());
    }
}
