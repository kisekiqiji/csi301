package com.xjtlu.campusspace.data.entity;

import java.util.ArrayList;
import java.util.List;

/**
 * UI projection of notes, authors and images. Persisted notes always reference
 * a real space; targetType/targetId are retained for older screen arguments.
 */
public class Note {

    public static final String T_SPACE = "SPACE";
    public static final String T_FACILITY = "FACILITY";
    public static final String T_QUIET_POD = "QUIET_POD";
    public static final String T_DISCUSSION_ROOM = "DISCUSSION_ROOM";

    public static final String C_LOST = "失物招领";
    public static final String C_CHAT = "空间碎碎念";
    public static final String C_REPAIR = "设备报修";

    public String id = "";
    public String spaceId = "";
    public String equipmentId;
    public String authorId = "";
    public String targetType = T_SPACE;
    public String targetId = "";
    public String category = C_CHAT;
    public String content = "";
    /** 图片在私有目录里的绝对路径，没有图就是 null */
    public String imagePath;
    public List<String> imagePaths = new ArrayList<>();
    public long createdAt;
    public String nickname = "";
}
