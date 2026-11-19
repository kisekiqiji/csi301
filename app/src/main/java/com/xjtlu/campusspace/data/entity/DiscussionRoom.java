package com.xjtlu.campusspace.data.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/** 讨论室（可预约，跳官方系统） */
@Entity(tableName = "discussion_rooms")
public class DiscussionRoom {
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
