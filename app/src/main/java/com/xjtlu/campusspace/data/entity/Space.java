package com.xjtlu.campusspace.data.entity;

/**
 * 教室 / 公共空间。
 * subType = DISCUSSION_AREA 表示这间小教室同时属于「讨论区」分类。
 */
public class Space {

    public static final String SUB_CLASSROOM = "CLASSROOM";
    public static final String SUB_DISCUSSION_AREA = "DISCUSSION_AREA";

    public String id = "";
    public String building = "";
    public int floor;
    public String floorCode = "";
    public String type = "";
    public String locationHint = "";
    public boolean showInPublicList;
    public String name = "";
    /** LARGE / SMALL */
    public String size = "";
    /** CLASSROOM / PUBLIC */
    public String kind = "";
    public String subType = SUB_CLASSROOM;
    public int capacity;
    /** 逗号分隔，如 "多媒体,投影,空调,白板" */
    public String facilities = "";

    public boolean isDiscussionArea() {
        return SUB_DISCUSSION_AREA.equals(subType) || SUB_DISCUSSION_AREA.equals(type);
    }

    public String[] facilityList() {
        if (facilities == null || facilities.isEmpty()) return new String[0];
        return facilities.split(",");
    }
}
