package com.xjtlu.campusspace.util;

import android.content.Context;
import android.net.Uri;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

/**
 * 图片存储。
 * 注意：content:// 的 Uri 授权是临时的，App 重启后就失效，
 * 所以必须复制到应用私有目录，数据库里只存文件路径。
 */
public final class ImageStore {

    private ImageStore() {
    }

    public static String copyToPrivate(Context context, Uri src) {
        if (src == null) return null;
        File dir = new File(context.getFilesDir(), "notes");
        if (!dir.exists() && !dir.mkdirs()) return null;
        File dst = new File(dir, UUID.randomUUID().toString().replace("-", "") + ".jpg");
        try (InputStream in = context.getContentResolver().openInputStream(src);
             OutputStream out = new FileOutputStream(dst)) {
            if (in == null) return null;
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return dst.getAbsolutePath();
        } catch (Exception e) {
            return null;
        }
    }

    /** 列表里用小图，避免把整张照片解到内存里导致 OOM */
    public static android.graphics.Bitmap loadThumb(String path, int targetPx) {
        if (path == null) return null;
        File f = new File(path);
        if (!f.exists()) return null;

        android.graphics.BitmapFactory.Options bounds = new android.graphics.BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        android.graphics.BitmapFactory.decodeFile(path, bounds);
        if (bounds.outWidth <= 0) return null;

        int sample = 1;
        while (bounds.outWidth / (sample * 2) >= targetPx) sample *= 2;

        android.graphics.BitmapFactory.Options opts = new android.graphics.BitmapFactory.Options();
        opts.inSampleSize = sample;
        try {
            return android.graphics.BitmapFactory.decodeFile(path, opts);
        } catch (Throwable t) {
            return null;
        }
    }
}
