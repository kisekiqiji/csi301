package com.xjtlu.campusspace.adapter;

/** 可预约列表的一行（讨论室 / 静音仓 / 图书馆区域 通用） */
public class BookableVM {

    public static final String MODE_SLOTS = "SLOTS";
    public static final String MODE_SEATS = "SEATS";

    public String id = "";
    public String title = "";
    public String subtitle = "";
    public String tag = "";
    public String statText = "";
    public int statColor = 0xFF0E9F6E;
    public String bookingUrl = "";
    /** SLOTS = 时段列表，SEATS = 座位网格 */
    public String mode = MODE_SLOTS;

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
