package com.xjtlu.campusspace.ui.home;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
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
import com.xjtlu.campusspace.ui.note.NotePublishActivity;
import com.xjtlu.campusspace.ui.widget.TimelineView;

import java.util.List;

/** 页面 2：教室详情 */
public class RoomDetailActivity extends AppCompatActivity {

    public static final String EXTRA_SPACE_ID = "space_id";

    private static final int[] DAY_OFFSETS = {-1, 0, 1, 2};

    private Repo repo;
    private Space space;
    private int dayOffset = 0;

    private TimelineView timeline;
    private TextView tvStatus;
    private ChipGroup chipsDay;
    private NoteAdapter noteAdapter;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_room_detail);

        repo = Repo.get(this);
        String id = getIntent().getStringExtra(EXTRA_SPACE_ID);
        space = repo.spaceOf(id);
        if (space == null) {
            finish();
            return;
        }

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle(space.name);
        toolbar.setNavigationOnClickListener(v -> finish());

        TextView title = findViewById(R.id.tv_title);
        title.setText(space.name);

        TextView meta = findViewById(R.id.tv_meta);
        String size = "LARGE".equals(space.size) ? "大教室" : "小教室";
        String extra = space.isDiscussionArea() ? " · 讨论区" : "";
        meta.setText(String.format("%s · %dF · 可容纳 %d 人 · %s%s",
                space.building, space.floor, space.capacity, size, extra));

        ChipGroup chipsFac = findViewById(R.id.chips_fac);
        for (String f : space.facilityList()) {
            Chip c = new Chip(this);
            c.setText(f);
            c.setCheckable(false);
            c.setClickable(false);
            chipsFac.addView(c);
        }

        timeline = findViewById(R.id.timeline);
        tvStatus = findViewById(R.id.tv_status);
        chipsDay = findViewById(R.id.chips_day);

        RecyclerView rvNotes = findViewById(R.id.rv_notes);
        rvNotes.setLayoutManager(new LinearLayoutManager(this));
        noteAdapter = new NoteAdapter(true);
        rvNotes.setAdapter(noteAdapter);

        buildDayChips();

        findViewById(R.id.tv_input).setOnClickListener(v -> openPublish());
        findViewById(R.id.tv_send).setOnClickListener(v -> openPublish());

        render();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (space != null) render();
    }

    private void openPublish() {
        Intent i = new Intent(this, NotePublishActivity.class);
        i.putExtra(NotePublishActivity.EXTRA_TARGET_TYPE, Note.T_SPACE);
        i.putExtra(NotePublishActivity.EXTRA_TARGET_ID, space.id);
        i.putExtra(NotePublishActivity.EXTRA_TARGET_NAME, space.name);
        startActivity(i);
    }

    private void buildDayChips() {
        chipsDay.removeAllViews();
        for (int off : DAY_OFFSETS) {
            Chip c = new Chip(this);
            c.setText(TimeProvider.dayLabel(off));
            c.setCheckable(true);
            c.setChecked(off == dayOffset);
            c.setOnClickListener(v -> v.post(() -> {
                dayOffset = off;
                buildDayChips();
                render();
            }));
            chipsDay.addView(c);
        }
    }

    private void render() {
        int isoDay = TimeProvider.dayOfWeekWithOffset(dayOffset);
        List<Lecture> lectures = repo.lecturesOf(space.id, isoDay);

        timeline.setLectures(lectures);
        timeline.setShowNowLine(dayOffset == 0);

        int now = TimeProvider.minuteOfDay();
        if (dayOffset == 0) {
            StatusCalculator.State st = StatusCalculator.of(lectures, now);
            Integer endMin = StatusCalculator.currentEndMin(lectures, now);
            Lecture next = StatusCalculator.nextLecture(lectures, now);

            if (st == StatusCalculator.State.IN_CLASS && endMin != null) {
                tvStatus.setText(String.format("%s 上课中，%s 下课（还有 %d 分钟）",
                        TimeProvider.todayLabel(), TimeText.hhmm(endMin), endMin - now));
            } else if (next != null) {
                String when = st == StatusCalculator.State.SOON ? "即将上课" : "现在空闲";
                tvStatus.setText(String.format("%s，%s 有课（%s，%s）", when,
                        TimeText.hhmm(next.startMin), next.course, TimeProvider.todayLabel()));
            } else {
                tvStatus.setText("今天没有排课，全天可用");
            }
        } else {
            tvStatus.setText(String.format("%s 共 %d 节课", TimeProvider.dayLabel(dayOffset),
                    lectures.size()));
        }

        repo.notesOf(Note.T_SPACE, space.id, rows -> {
            if (isFinishing()) return;
            noteAdapter.setItems(rows);
        });
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
