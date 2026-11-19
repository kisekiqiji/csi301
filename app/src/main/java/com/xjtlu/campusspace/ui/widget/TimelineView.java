package com.xjtlu.campusspace.ui.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;

import androidx.annotation.Nullable;

import com.xjtlu.campusspace.core.TimeProvider;
import com.xjtlu.campusspace.data.entity.Lecture;

import java.util.ArrayList;
import java.util.List;

/**
 * 教室时间轴色带：横轴 8:00-21:00。
 * 灰 = 空闲，蓝 = 有课，红线 = 现在。
 * 用法：setLectures() 之后调用 invalidate()，系统会回调 onDraw()。
 */
public class TimelineView extends View {

    private static final int START_H = 8;
    private static final int END_H = 21;

    private List<Lecture> lectures = new ArrayList<>();
    private boolean showNowLine = true;

    private final Paint paintFree = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintBusy = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintNow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintTick = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintLabel = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintBusyText = new Paint(Paint.ANTI_ALIAS_FLAG);

    public TimelineView(Context context) {
        this(context, null);
    }

    public TimelineView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        paintFree.setColor(0xFFE3E6EC);
        paintBusy.setColor(0xFF2F6FED);
        paintNow.setColor(0xFFEF4444);
        paintNow.setStrokeWidth(dp(2));
        paintTick.setColor(0x22000000);
        paintTick.setStrokeWidth(dp(1));
        paintLabel.setColor(0xFF6B7280);
        paintLabel.setTextSize(sp(10));
        paintBusyText.setColor(0xFFFFFFFF);
        paintBusyText.setTextSize(sp(10));
    }

    private float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
    }

    private float sp(float v) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v,
                getResources().getDisplayMetrics());
    }

    public void setLectures(List<Lecture> list) {
        this.lectures = (list == null) ? new ArrayList<>() : list;
        invalidate();
    }

    /** 看历史/未来日期时不需要"现在"红线 */
    public void setShowNowLine(boolean show) {
        this.showNowLine = show;
        invalidate();
    }

    private float pxPerMin() {
        return getWidth() / (float) ((END_H - START_H) * 60);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int w = resolveSize((int) dp(780), widthMeasureSpec);
        int h = resolveSize((int) dp(56), heightMeasureSpec);
        setMeasuredDimension(w, h);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float h = getHeight();
        float u = pxPerMin();
        float barTop = 0;
        float barBottom = h * 0.68f;

        canvas.drawRect(0, barTop, getWidth(), barBottom, paintFree);

        for (Lecture l : lectures) {
            float left = (l.startMin - START_H * 60) * u;
            float right = (l.endMin - START_H * 60) * u;
            left = Math.max(left, 0);
            right = Math.min(right, getWidth());
            if (right <= left) continue;
            canvas.drawRect(left, barTop, right, barBottom, paintBusy);

            String label = l.course;
            if (right - left > paintBusyText.measureText(label) + dp(8)) {
                float baseline = barBottom / 2f - (paintBusyText.descent() + paintBusyText.ascent()) / 2f;
                canvas.drawText(label, left + dp(4), baseline, paintBusyText);
            }
        }

        for (int hr = START_H; hr <= END_H; hr++) {
            float x = (hr - START_H) * 60 * u;
            canvas.drawLine(x, barTop, x, barBottom + dp(4), paintTick);
            if (hr % 2 == 0) {
                String label = hr + ":00";
                float tw = paintLabel.measureText(label);
                // 首尾标签别被画到视图外面
                float tx = x - tw / 2f;
                tx = Math.max(dp(1), Math.min(tx, getWidth() - tw - dp(1)));
                canvas.drawText(label, tx, barBottom + dp(18), paintLabel);
            }
        }

        if (showNowLine) {
            int nowMin = TimeProvider.minuteOfDay();
            if (nowMin >= START_H * 60 && nowMin <= END_H * 60) {
                float x = (nowMin - START_H * 60) * u;
                canvas.drawLine(x, barTop - dp(3), x, barBottom + dp(3), paintNow);
                canvas.drawCircle(x, barTop - dp(3), dp(3), paintNow);
            }
        }
    }
}
