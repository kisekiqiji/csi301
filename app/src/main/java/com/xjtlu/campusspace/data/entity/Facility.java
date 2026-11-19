package com.xjtlu.campusspace.data.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/** 公共设备：饮水机 / 打印机 / 休息处 / 开放座位区 */
@Entity(tableName = "facilities")
public class Facility {
    @PrimaryKey
    @NonNull
    public String id = "";
    public String building = "";
    public int floor;
    public String type = "";
    public String locationText = "";
}
