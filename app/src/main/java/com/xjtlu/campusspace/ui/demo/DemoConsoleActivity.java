package com.xjtlu.campusspace.ui.demo;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.xjtlu.campusspace.R;
import com.xjtlu.campusspace.core.DemoClock;
import com.xjtlu.campusspace.core.TimeProvider;

import java.util.Calendar;
import java.util.TimeZone;

/** 答辩演示用：拨表、快速场景与演示数据重建。 */
public class DemoConsoleActivity extends AppCompatActivity {
    private static final TimeZone CAMPUS_ZONE = TimeZone.getTimeZone("Asia/Shanghai");
    private static final String STATE_CUSTOM = "custom_time";

    private final Calendar custom = Calendar.getInstance(CAMPUS_ZONE);
    private TextView mode, demoNow, realNow, hint, message;
    private ProgressBar progress;
    private View[] actions;
    private View customDate, customTime;
    private boolean busy;

    @Override protected void onCreate(@Nullable Bundle saved) {
        super.onCreate(saved);
        setContentView(R.layout.activity_demo_console);
        ((MaterialToolbar) findViewById(R.id.toolbar)).setNavigationOnClickListener(v -> finish());

        mode = findViewById(R.id.tv_mode);
        demoNow = findViewById(R.id.tv_demo_now);
        realNow = findViewById(R.id.tv_real_now);
        hint = findViewById(R.id.tv_hint);
        message = findViewById(R.id.tv_message);
        progress = findViewById(R.id.progress);
        customDate = findViewById(R.id.btn_custom_date);
        customTime = findViewById(R.id.btn_custom_time);
        actions = new View[]{
                findViewById(R.id.btn_preset_class), findViewById(R.id.btn_preset_noon),
                findViewById(R.id.btn_preset_afternoon), findViewById(R.id.btn_preset_evening),
                findViewById(R.id.btn_preset_tomorrow), findViewById(R.id.btn_preset_weekend),
                findViewById(R.id.btn_apply), findViewById(R.id.btn_restore), findViewById(R.id.btn_rebuild)
        };

        if (saved != null) custom.setTimeInMillis(saved.getLong(STATE_CUSTOM, custom.getTimeInMillis()));
        else resetCustomTo(TimeProvider.now());

        findViewById(R.id.btn_preset_class).setOnClickListener(v -> preset(0, 10, 30));
        findViewById(R.id.btn_preset_noon).setOnClickListener(v -> preset(0, 12, 30));
        findViewById(R.id.btn_preset_afternoon).setOnClickListener(v -> preset(0, 14, 30));
        findViewById(R.id.btn_preset_evening).setOnClickListener(v -> preset(0, 19, 30));
        findViewById(R.id.btn_preset_tomorrow).setOnClickListener(v -> preset(1, 10, 30));
        findViewById(R.id.btn_preset_weekend).setOnClickListener(v -> weekendPreset());
        customDate.setOnClickListener(v -> pickDate());
        customTime.setOnClickListener(v -> pickTime());
        findViewById(R.id.btn_apply).setOnClickListener(v -> applyCustom());
        findViewById(R.id.btn_restore).setOnClickListener(v -> restoreReal());
        findViewById(R.id.btn_rebuild).setOnClickListener(v -> rebuildSnapshots());

        refreshLabels();
        updateCustomLabels();
    }

    private void preset(int dayOffset, int hour, int minute) {
        if (busy) return;
        Calendar target = Calendar.getInstance(CAMPUS_ZONE);
        target.setTimeInMillis(TimeProvider.now());
        target.add(Calendar.DAY_OF_MONTH, dayOffset);
        target.set(Calendar.HOUR_OF_DAY, hour);
        target.set(Calendar.MINUTE, minute);
        target.set(Calendar.SECOND, 0);
        target.set(Calendar.MILLISECOND, 0);
        applyTime(target.getTimeInMillis());
    }

    private void weekendPreset() {
        if (busy) return;
        Calendar target = Calendar.getInstance(CAMPUS_ZONE);
        target.setTimeInMillis(TimeProvider.now());
        int dow = target.get(Calendar.DAY_OF_WEEK);
        int daysUntilSaturday = (Calendar.SATURDAY - dow + 7) % 7;
        target.add(Calendar.DAY_OF_MONTH, daysUntilSaturday);
        target.set(Calendar.HOUR_OF_DAY, 14);
        target.set(Calendar.MINUTE, 0);
        target.set(Calendar.SECOND, 0);
        target.set(Calendar.MILLISECOND, 0);
        applyTime(target.getTimeInMillis());
    }

    private void applyCustom() {
        if (busy) return;
        custom.set(Calendar.SECOND, 0);
        custom.set(Calendar.MILLISECOND, 0);
        applyTime(custom.getTimeInMillis());
    }

    private void applyTime(long timestamp) {
        resetCustomTo(timestamp);
        setBusy(true, "正在切换演示时间并重建快照…");
        DemoController.apply(this, timestamp, this::afterRebuild);
    }

    private void restoreReal() {
        if (busy) return;
        setBusy(true, "正在恢复真实时间并重建快照…");
        DemoController.restoreReal(this, (success, text) -> {
            if (success) resetCustomTo(System.currentTimeMillis());
            afterRebuild(success, text);
        });
    }

    private void rebuildSnapshots() {
        if (busy) return;
        setBusy(true, "正在重建演示快照…");
        DemoController.rebuild(this, this::afterRebuild);
    }

    private void afterRebuild(boolean success, String text) {
        if (isFinishing() || isDestroyed()) return;
        setBusy(false, text);
        refreshLabels();
        updateCustomLabels();
        if (success) Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
    }

    private void setBusy(boolean value, String text) {
        busy = value;
        progress.setVisibility(value ? View.VISIBLE : View.GONE);
        message.setText(text);
        for (View action : actions) action.setEnabled(!value);
        customDate.setEnabled(!value);
        customTime.setEnabled(!value);
    }

    private void pickDate() {
        if (busy) return;
        new DatePickerDialog(this, (picker, year, month, day) -> {
            custom.set(year, month, day);
            updateCustomLabels();
        }, custom.get(Calendar.YEAR), custom.get(Calendar.MONTH), custom.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void pickTime() {
        if (busy) return;
        new TimePickerDialog(this, (picker, hour, minute) -> {
            custom.set(Calendar.HOUR_OF_DAY, hour);
            custom.set(Calendar.MINUTE, minute);
            updateCustomLabels();
        }, custom.get(Calendar.HOUR_OF_DAY), custom.get(Calendar.MINUTE), true).show();
    }

    private void resetCustomTo(long timestamp) {
        custom.setTimeInMillis(timestamp);
        custom.set(Calendar.SECOND, 0);
        custom.set(Calendar.MILLISECOND, 0);
    }

    private void refreshLabels() {
        boolean demo = DemoClock.isDemo(this);
        long virtual = TimeProvider.now();
        mode.setText(demo ? "演示模式" : "真实时间");
        mode.setTextColor(demo ? 0xFFB45309 : 0xFF0E9F6E);
        demoNow.setText(demo ? "演示时间  " + DemoClock.label(virtual) : "当前使用真实时间");
        realNow.setText("真实时间  " + DemoClock.label(System.currentTimeMillis())
                + (demo ? "" : "（与演示时间一致）"));
        hint.setText(demo
                ? "正在使用演示时间；返回其他页面后，课表、预约和推荐都会按此时间计算。"
                : "演示快照覆盖昨天至未来 7 天；拨表后会自动重建。");
    }

    private void updateCustomLabels() {
        java.text.SimpleDateFormat date = new java.text.SimpleDateFormat("M月d日 E", java.util.Locale.CHINA);
        java.text.SimpleDateFormat time = new java.text.SimpleDateFormat("HH:mm", java.util.Locale.CHINA);
        date.setTimeZone(CAMPUS_ZONE);
        time.setTimeZone(CAMPUS_ZONE);
        ((TextView) customDate).setText(date.format(custom.getTime()) + "  ▾");
        ((TextView) customTime).setText(time.format(custom.getTime()) + "  ▾");
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        out.putLong(STATE_CUSTOM, custom.getTimeInMillis());
        super.onSaveInstanceState(out);
    }
}
