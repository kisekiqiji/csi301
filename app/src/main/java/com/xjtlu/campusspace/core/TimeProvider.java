package com.xjtlu.campusspace.core;

import java.util.Calendar;
import java.util.TimeZone;

/**
 * 当前时间抽象。
 * 演示时可以"拨表"：TimeProvider.setFake(ts)，让界面显示指定时刻的状态。
 */
public final class TimeProvider {

    private static Long fakeNow = null;

    private TimeProvider() {
    }

    public static long now() {
        return fakeNow != null ? fakeNow : System.currentTimeMillis();
    }

    /** 演示用：把"现在"固定到某个时刻 */
    public static void setFake(long timestamp) {
        fakeNow = timestamp;
    }

    public static void clearFake() {
        fakeNow = null;
    }

    public static boolean isFake() {
        return fakeNow != null;
    }

    private static Calendar cal() {
        Calendar c = Calendar.getInstance(TimeZone.getTimeZone("Asia/Shanghai"));
        c.setTimeInMillis(now());
        return c;
    }

    /** 今天第几分钟，如 10:30 -> 630 */
    public static int minuteOfDay() {
        Calendar c = cal();
        return c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE);
    }

    /** ISO 星期：1=周一 ... 7=周日 */
    public static int isoDayOfWeek() {
        int d = cal().get(Calendar.DAY_OF_WEEK);
        return d == Calendar.SUNDAY ? 7 : d - 1;
    }

    /** 相对今天偏移 off 天之后的 ISO 星期 */
    public static int dayOfWeekWithOffset(int off) {
        int d = isoDayOfWeek() + off;
        while (d < 1) d += 7;
        while (d > 7) d -= 7;
        return d;
    }

    /** "昨天/今天/明天/后天"，其他情况回退到"周X" */
    public static String dayLabel(int off) {
        switch (off) {
            case -1: return "昨天";
            case 0:  return "今天";
            case 1:  return "明天";
            case 2:  return "后天";
            default: return weekLabel(dayOfWeekWithOffset(off));
        }
    }

    public static String weekLabel(int isoDay) {
        String[] w = {"周一", "周二", "周三", "周四", "周五", "周六", "周日"};
        if (isoDay < 1 || isoDay > 7) return "";
        return w[isoDay - 1];
    }

    public static String todayLabel() {
        return weekLabel(isoDayOfWeek());
    }
}
