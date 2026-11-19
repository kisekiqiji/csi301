package com.xjtlu.campusspace.data.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/** 静音仓（可预约，跳官方系统）。单独一张表，不并入教室。 */
@Entity(tableName = "quiet_pods")
public class QuietPod {
    @PrimaryKey
    @NonNull
    public String id = "";
    public String zone = "";
    public String building = "";
    public int floor;
    public String name = "";
    public String category = "";
    public int capacity;
    public String facilities = "";
    public String bookingUrl = "";
}
