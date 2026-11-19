package com.xjtlu.campusspace.data.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;

/**
 * 预约时段。dayOffset 是相对"今天"的偏移（-1=昨天 0=今天 1=明天 2=后天），
 * 运行时才换算成真实日期，这样种子数据不会随日期变旧。
 */
@Entity(tableName = "slots", primaryKeys = {"targetId", "dayOffset", "period"})
public class BookableSlot {

    public static final String TYPE_QUIET_POD = "QUIET_POD";
    public static final String TYPE_DISCUSSION_ROOM = "DISCUSSION_ROOM";

    public static final String FREE = "FREE";
    public static final String BOOKED = "BOOKED";

    @NonNull
    public String targetId = "";
    public String targetType = "";
    public int dayOffset;
    public int period;
    public int startMin;
    public int endMin;
    public String status = FREE;

    public boolean isFree() {
        return FREE.equals(status);
    }
}
