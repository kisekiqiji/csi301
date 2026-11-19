package com.xjtlu.campusspace.ui.widget;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.widget.TextView;

import androidx.annotation.ColorInt;

/** 圆角状态标签。用代码画，省掉一堆 shape drawable。 */
public final class Pills {

    private Pills() {
    }

    public static void apply(TextView tv, @ColorInt int color) {
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(tv.getResources().getDisplayMetrics().density * 99f);
        bg.setColor(color);
        tv.setBackground(bg);
        tv.setTextColor(Color.WHITE);
        int padH = (int) (tv.getResources().getDisplayMetrics().density * 10);
        int padV = (int) (tv.getResources().getDisplayMetrics().density * 3);
        tv.setPadding(padH, padV, padH, padV);
    }

    /** 浅色底 + 深色字，用于次要标签 */
    public static void applySoft(TextView tv, @ColorInt int color) {
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(tv.getResources().getDisplayMetrics().density * 99f);
        bg.setColor(color);
        tv.setBackground(bg);
        tv.setTextColor(Color.parseColor("#1F2430"));
        int padH = (int) (tv.getResources().getDisplayMetrics().density * 10);
        int padV = (int) (tv.getResources().getDisplayMetrics().density * 3);
        tv.setPadding(padH, padV, padH, padV);
    }

    public static void setColor(Context ctx, TextView tv, @ColorInt int color) {
        apply(tv, color);
    }
}
