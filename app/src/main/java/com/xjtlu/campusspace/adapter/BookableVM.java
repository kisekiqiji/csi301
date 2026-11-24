package com.xjtlu.campusspace.adapter;

/** 可预约列表的一行（讨论室 / 静音仓 / 图书馆区域 通用） */
public class BookableVM {

    public static final String MODE_SLOTS = "SLOTS";
    public static final String MODE_SEATS = "SEATS";
    public static final String MODE_TIMELINE = "TIMELINE";

    public String id = "";
    public String title = "";
    public String subtitle = "";
    public String tag = "";
    public String statText = "";
    public int statColor = 0xFF0E9F6E;
    public String bookingUrl = "";
    public String groupId = "";
    public String spaceId = "";
    /** SLOTS = 时段列表，SEATS = 座位网格 */
    public String mode = MODE_SLOTS;
    /** 演示用「现在可去」排序：此刻是否可用、今天剩余可用量。 */
    public boolean freeNow;
    public int freeUnits;
    public int totalUnits;
    /** 课表型资源的当前空档结束分钟（无下一节课时为 22:00）。 */
    public int freeUntilMin = -1;

    public BookableVM() {
    }

    public BookableVM(String id, String title, String subtitle, String tag,
                      String statText, int statColor, String bookingUrl, String mode) {
        this.id = id;
        this.title = title;
        this.subtitle = subtitle;
        this.tag = tag;
        this.statText = statText;
        this.statColor = statColor;
        this.bookingUrl = bookingUrl;
        this.mode = mode;
    }
}
