package com.xjtlu.campusspace.core;

/** 时间显示格式工具。全程只用 int / long，避开 java.time（minSdk 24 不支持）。 */
public final class TimeText {

    private TimeText() {
    }

    /** 630 -> "10:30" */
    public static String hhmm(int minuteOfDay) {
        int m = ((minuteOfDay % 1440) + 1440) % 1440;
        return String.format("%02d:%02d", m / 60, m % 60);
    }

    /** 480, 590 -> "08:00-09:50" */
    public static String range(int startMin, int endMin) {
        return hhmm(startMin) + "-" + hhmm(endMin);
    }

    /** 相对时间："35 分钟前" / "3 小时前" / "2 天前" */
    public static String ago(long createdAt) {
        long diff = TimeProvider.now() - createdAt;
        if (diff < 60_000L) return "刚刚";
        long min = diff / 60_000L;
        if (min < 60) return min + " 分钟前";
        long hour = min / 60;
        if (hour < 24) return hour + " 小时前";
        return (hour / 24) + " 天前";
    }
}
