package com.xjtlu.campusspace.util;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;

/** 打开外部浏览器（跳官方预约系统） */
public final class UrlOpener {

    private UrlOpener() {
    }

    public static void open(Context context, String url) {
        if (url == null || url.trim().isEmpty()) {
            Toast.makeText(context, "暂无可用的预约入口", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (i.resolveActivity(context.getPackageManager()) != null) {
                context.startActivity(i);
            } else {
                Toast.makeText(context, "手机上找不到可用的浏览器", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(context, "打开预约系统失败", Toast.LENGTH_SHORT).show();
        }
    }
}
