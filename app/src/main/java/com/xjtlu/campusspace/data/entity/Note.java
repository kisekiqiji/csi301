package com.xjtlu.campusspace.data.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * 空间纸条。targetType 是多态外键：SPACE / FACILITY / QUIET_POD / DISCUSSION_ROOM。
 * createdAt 存绝对毫秒，显示时再算"多久之前"。
 */
@Entity(tableName = "notes")
public class Note {

    public static final String T_SPACE = "SPACE";
    public static final String T_FACILITY = "FACILITY";
    public static final String T_QUIET_POD = "QUIET_POD";
    public static final String T_DISCUSSION_ROOM = "DISCUSSION_ROOM";

    public static final String C_LOST = "失物招领";
    public static final String C_CHAT = "空间碎碎念";
    public static final String C_REPAIR = "设备报修";

    @PrimaryKey
    @NonNull
    public String id = "";
    public String targetType = T_SPACE;
    public String targetId = "";
    public String category = C_CHAT;
    public String content = "";
    /** 图片在私有目录里的绝对路径，没有图就是 null */
    public String imagePath;
    public long createdAt;
    public String nickname = "";
}
