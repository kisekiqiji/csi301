package com.xjtlu.campusspace.core;

import com.xjtlu.campusspace.data.entity.Lecture;

import java.util.ArrayList;
import java.util.List;

/**
 * 教室状态判定。
 * 纯函数、不碰 UI、不碰数据库 —— 好写单元测试，也好在演示时伪造时间。
 */
public final class StatusCalculator {

    /** 空闲 / 即将上课（30 分钟内） / 上课中 */
    public enum State { FREE, SOON, IN_CLASS, UNKNOWN }

    private static final int SOON_WINDOW = 30;

    private StatusCalculator() {
    }

    public static State of(List<Lecture> todayLectures, int nowMin) {
        if (todayLectures == null) return State.UNKNOWN;
        if (todayLectures.isEmpty()) return State.FREE;

        for (Lecture l : todayLectures) {
            if (nowMin >= l.startMin && nowMin < l.endMin) return State.IN_CLASS;
        }
        for (Lecture l : todayLectures) {
            if (l.startMin > nowMin && l.startMin - nowMin <= SOON_WINDOW) return State.SOON;
        }
        return State.FREE;
    }

    public static State of(List<Lecture> lectures, int nowMin, boolean hasCoverage) {
        return hasCoverage ? of(lectures, nowMin) : State.UNKNOWN;
    }

    /** 现在这节课几点结束；没在上课返回 null */
    public static Integer currentEndMin(List<Lecture> todayLectures, int nowMin) {
        if (todayLectures == null) return null;
        for (Lecture l : todayLectures) {
            if (nowMin >= l.startMin && nowMin < l.endMin) return l.endMin;
        }
        return null;
    }

    /** 下一节课几点开始且最快要多久；今天没课返回 null */
    public static Lecture nextLecture(List<Lecture> todayLectures, int nowMin) {
        if (todayLectures == null) return null;
        Lecture best = null;
        for (Lecture l : todayLectures) {
            if (l.startMin > nowMin && (best == null || l.startMin < best.startMin)) best = l;
        }
        return best;
    }

    /** 今天还剩多少分钟空闲（用于"还能学多久"之类的文案） */
    public static int freeMinutesLeft(List<Lecture> todayLectures, int nowMin, int untilMin) {
        List<Lecture> rest = new ArrayList<>();
        if (todayLectures != null) {
            for (Lecture l : todayLectures) {
                if (l.endMin > nowMin && l.startMin < untilMin) rest.add(l);
            }
        }
        rest.sort((a, b) -> Integer.compare(a.startMin, b.startMin));
        int cursor = Math.max(nowMin, 0);
        int free = 0;
        for (Lecture l : rest) {
            if (l.startMin > cursor) free += l.startMin - cursor;
            cursor = Math.max(cursor, l.endMin);
        }
        if (cursor < untilMin) free += untilMin - cursor;
        return Math.max(free, 0);
    }
}
