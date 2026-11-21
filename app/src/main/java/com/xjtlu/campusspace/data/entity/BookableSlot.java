package com.xjtlu.campusspace.data.entity;

/**
 * UI projection of a dated booking slot. Absolute timestamps remain authoritative;
 * dayOffset and minute fields are derived for the existing timeline views.
 */
public class BookableSlot {

    public static final String TYPE_QUIET_POD = "QUIET_POD";
    public static final String TYPE_DISCUSSION_ROOM = "DISCUSSION_ROOM";

    public static final String FREE = "FREE";
    public static final String BOOKED = "BOOKED";
    public static final String UNKNOWN = "UNKNOWN";

    public String targetId = "";
    public String targetType = "";
    public int dayOffset;
    public int period;
    public int startMin;
    public int endMin;
    public long startsAt;
    public long endsAt;
    public String status = UNKNOWN;

    public boolean isFree() {
        return FREE.equals(status);
    }
}
