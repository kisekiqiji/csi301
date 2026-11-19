package com.xjtlu.campusspace.data.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "buildings")
public class Building {
    @PrimaryKey
    @NonNull
    public String code = "";
    public String name = "";
    public String campus = "";
    public int floors;
}
