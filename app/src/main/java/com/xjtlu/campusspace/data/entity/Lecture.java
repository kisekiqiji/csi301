package com.xjtlu.campusspace.data.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;

/**
 * 一节课。day 是 ISO 星期（1=周一 ... 7=周日），课表每周重复。
 * startMin / endMin 是当天第几分钟，480 = 08:00。
 */
@Entity(tableName = "lectures", primaryKeys = {"roomId", "day", "period"})
public class Lecture {
    @NonNull
    public String roomId = "";
    public int day;
    public int period;
    public int startMin;
    public int endMin;
    public String course = "";
    public String teacher = "";
}
