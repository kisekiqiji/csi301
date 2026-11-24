package com.xjtlu.campusspace.core;

import android.content.Context;
import android.content.SharedPreferences;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * 演示时钟。把虚拟时间持久化到本机，进程重启后演示状态仍然有效。
 * 真正的时钟钩子仍然是 {@link TimeProvider#setFake(long)}。
 */
public final class DemoClock {
    private static final String PREFS = "demo_clock";
    private static final String KEY_NOW = "virtual_now";
    private static final TimeZone CAMPUS_ZONE = TimeZone.getTimeZone("Asia/Shanghai");

    private DemoClock() { }

    /** 应用启动时调用；没有保存的演示时间就保持真实时间。 */
    public static void restore(Context context) {
        long saved = prefs(context).getLong(KEY_NOW, Long.MIN_VALUE);
        if (saved != Long.MIN_VALUE) TimeProvider.setFake(saved);
    }

    public static void apply(Context context, long timestamp) {
        TimeProvider.setFake(timestamp);
        prefs(context).edit().putLong(KEY_NOW, timestamp).apply();
    }

    public static void clear(Context context) {
        TimeProvider.clearFake();
        prefs(context).edit().remove(KEY_NOW).apply();
    }

    public static boolean isDemo(Context context) {
        return prefs(context).contains(KEY_NOW) || TimeProvider.isFake();
    }

    /** 演示时间可读文本，例如 "11月24日 周二 10:30"。 */
    public static String label(long timestamp) {
        SimpleDateFormat format = new SimpleDateFormat("M月d日 E HH:mm", Locale.CHINA);
        format.setTimeZone(CAMPUS_ZONE);
        return format.format(new Date(timestamp));
    }

    public static String label(Context context) {
        return label(TimeProvider.now());
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
