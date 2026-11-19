package com.xjtlu.campusspace.data.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/** 图书馆座位 */
@Entity(tableName = "library_seats")
public class LibrarySeat {

    public static final String AVAILABLE = "AVAILABLE";
    public static final String PART_FREE = "PART_FREE";
    public static final String RESERVED = "RESERVED";
    public static final String UNAVAILABLE = "UNAVAILABLE";

    @PrimaryKey
    @NonNull
    public String id = "";
    public String zone = "";
    public int row;
    public int col;
    public String seatNo = "";
    public String status = AVAILABLE;
    public String bookingUrl = "";
}
