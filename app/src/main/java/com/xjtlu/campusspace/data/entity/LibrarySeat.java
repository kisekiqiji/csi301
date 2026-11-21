package com.xjtlu.campusspace.data.entity;

/** 图书馆座位界面投影，按当前日期选择完整座位快照。 */
public class LibrarySeat {

    public static final String AVAILABLE = "AVAILABLE";
    public static final String PART_FREE = "PART_FREE";
    public static final String RESERVED = "RESERVED";
    public static final String UNAVAILABLE = "UNAVAILABLE";
    public static final String UNKNOWN = "UNKNOWN";

    public String id = "";
    public String resourceId = "";
    public String spaceId = "";
    public String groupId = "";
    public String zone = "";
    public int row;
    public int col;
    public String seatNo = "";
    public String status = UNKNOWN;
    public String bookingUrl = "";
}
