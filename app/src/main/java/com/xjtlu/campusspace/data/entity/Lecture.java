package com.xjtlu.campusspace.data.entity;

/**
 * A dated classroom event projected into local minutes for the timeline.
 * The weekday is display metadata; events do not repeat automatically.
 */
public class Lecture {
    public String roomId = "";
    public int day;
    public int period;
    public int startMin;
    public int endMin;
    public long startsAt;
    public long endsAt;
    public String course = "";
    public String teacher = "";
}
