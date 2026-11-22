package com.xjtlu.campusspace.ui.home;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.xjtlu.campusspace.R;
import com.xjtlu.campusspace.adapter.NoteAdapter;
import com.xjtlu.campusspace.core.StatusCalculator;
import com.xjtlu.campusspace.core.TimeProvider;
import com.xjtlu.campusspace.core.TimeText;
import com.xjtlu.campusspace.data.Repo;
import com.xjtlu.campusspace.data.entity.Lecture;
import com.xjtlu.campusspace.data.entity.Note;
import com.xjtlu.campusspace.data.entity.Space;
import com.xjtlu.campusspace.ui.SpaceUi;
import com.xjtlu.campusspace.ui.widget.TimelineView;
import java.util.List;

public class RoomDetailActivity extends AppCompatActivity {
    public static final String EXTRA_SPACE_ID = "space_id";
    public static final String EXTRA_DAY_OFFSET = "day_offset";
    public static final String EXTRA_QUERY_MINUTE = "query_minute";
    private Repo repo;
    private Space space;
    private int dayOffset;
    private int queryMinute = -1;
    private TimelineView timeline;
    private TextView status;
    private ChipGroup days;
    private NoteAdapter notes;

    @Override protected void onCreate(@Nullable Bundle saved) {
        super.onCreate(saved);
        setContentView(R.layout.activity_room_detail);
        dayOffset = saved == null ? getIntent().getIntExtra(EXTRA_DAY_OFFSET, 0) : saved.getInt("day_offset", 0);
        queryMinute = saved == null ? getIntent().getIntExtra(EXTRA_QUERY_MINUTE, -1) : saved.getInt(EXTRA_QUERY_MINUTE, -1);
        repo = Repo.get(this);
        ((MaterialToolbar)findViewById(R.id.toolbar)).setNavigationOnClickListener(v -> finish());
        timeline = findViewById(R.id.timeline);
        status = findViewById(R.id.tv_status);
        days = findViewById(R.id.chips_day);
        RecyclerView rv = findViewById(R.id.rv_notes);
        rv.setLayoutManager(new LinearLayoutManager(this));
        notes = new NoteAdapter(true); rv.setAdapter(notes);
        findViewById(R.id.tv_input).setOnClickListener(v -> publish());
        findViewById(R.id.tv_send).setOnClickListener(v -> publish());
        status.setText("正在加载空间数据…");
        load();
    }

    private void load() {
        repo.ensureLoaded(() -> {
            if (isFinishing() || isDestroyed()) return;
            if (!repo.isLoaded()) {
                status.setText("空间数据加载失败，点击重试\n" + repo.getLoadError());
                status.setOnClickListener(v -> load()); return;
            }
            status.setOnClickListener(null);
            space = repo.spaceOf(getIntent().getStringExtra(EXTRA_SPACE_ID));
            if (space == null) { status.setText("找不到这个空间，请返回列表重选"); return; }
            ((MaterialToolbar)findViewById(R.id.toolbar)).setTitle(space.name);
            ((TextView)findViewById(R.id.tv_title)).setText(space.name);
            ((TextView)findViewById(R.id.tv_meta)).setText(SpaceUi.subtitle(space) + "\n" + repo.dataLabel());
            ChipGroup facilities = findViewById(R.id.chips_fac);
            facilities.removeAllViews();
            for (String label : space.facilityList()) {
                Chip chip = new Chip(this); chip.setText(label); chip.setClickable(false);
                facilities.addView(chip);
            }
            buildDays(); render();
        });
    }

    @Override protected void onResume() { super.onResume(); if (space != null) load(); }
    @Override protected void onSaveInstanceState(@androidx.annotation.NonNull Bundle out) {
        out.putInt("day_offset", dayOffset); out.putInt(EXTRA_QUERY_MINUTE, queryMinute); super.onSaveInstanceState(out);
    }
    private void publish() { if (space != null) SpaceUi.publish(this, space.id, null); }

    private void buildDays() {
        days.removeAllViews();
        java.util.LinkedHashSet<Integer> offsets = new java.util.LinkedHashSet<>();
        for (int off : new int[]{-1, 0, 1, 2}) offsets.add(off);
        offsets.add(dayOffset);
        for (int off : offsets) {
            Chip chip = new Chip(this); chip.setText(TimeProvider.dayLabel(off));
            chip.setCheckable(true); chip.setChecked(off == dayOffset);
            chip.setOnClickListener(v -> { dayOffset = off; buildDays(); render(); });
            days.addView(chip);
        }
    }

    private void render() {
        boolean known = repo.hasSchedule(space.id, dayOffset);
        List<Lecture> lectures = repo.lecturesOn(space.id, dayOffset);
        timeline.setLectures(lectures); timeline.setDataKnown(known); timeline.setShowNowLine(dayOffset == 0);
        if (!known) status.setText(TimeProvider.dayLabel(dayOffset) + "暂无有效课表，状态未知");
        else if (queryMinute >= 0 && queryMinute < 1440) {
            StatusCalculator.State selected = StatusCalculator.of(lectures, queryMinute);
            status.setText(TimeProvider.dayLabel(dayOffset) + " " + TimeText.hhmm(queryMinute) + " · "
                    + (selected == StatusCalculator.State.IN_CLASS ? "有课" : selected == StatusCalculator.State.SOON ? "课表空档，30 分钟内有课" : "课表空档")
                    + " · 当天共 " + lectures.size() + " 节课");
        }
        else if (dayOffset != 0) status.setText(TimeProvider.dayLabel(dayOffset) + "共 " + lectures.size() + " 节课 · 仅反映课表安排");
        else {
            int now = TimeProvider.minuteOfDay();
            StatusCalculator.State state = StatusCalculator.of(lectures, now);
            Integer end = StatusCalculator.currentEndMin(lectures, now);
            Lecture next = StatusCalculator.nextLecture(lectures, now);
            if (state == StatusCalculator.State.IN_CLASS && end != null)
                status.setText("上课中，" + TimeText.hhmm(end) + " 下课（还有 " + (end - now) + " 分钟）");
            else if (next != null) status.setText((state == StatusCalculator.State.SOON ? "即将上课" : "当前为课表空档")
                    + " · " + TimeText.hhmm(next.startMin) + " " + next.course);
            else status.setText("今天余下时间未排课 · 请以现场使用情况为准");
        }
        repo.notesOf(Note.T_SPACE, space.id, rows -> {
            if (!isFinishing() && !isDestroyed()) notes.setItems(rows);
        });
    }
}
