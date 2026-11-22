package com.xjtlu.campusspace.ui.booking;

import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.GridLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.xjtlu.campusspace.R;
import com.xjtlu.campusspace.adapter.BookableVM;
import com.xjtlu.campusspace.adapter.NoteAdapter;
import com.xjtlu.campusspace.core.StatusCalculator;
import com.xjtlu.campusspace.core.TimeProvider;
import com.xjtlu.campusspace.core.TimeText;
import com.xjtlu.campusspace.data.Repo;
import com.xjtlu.campusspace.data.entity.BookableSlot;
import com.xjtlu.campusspace.data.entity.Lecture;
import com.xjtlu.campusspace.data.entity.LibrarySeat;
import com.xjtlu.campusspace.data.entity.Note;
import com.xjtlu.campusspace.data.store.Rows;
import com.xjtlu.campusspace.ui.SpaceUi;
import com.xjtlu.campusspace.ui.home.RoomDetailActivity;
import com.xjtlu.campusspace.ui.widget.Pills;
import com.xjtlu.campusspace.ui.widget.TimelineView;
import com.xjtlu.campusspace.util.UrlOpener;
import java.util.List;

/** Data-defined display modes: booking slots, seat grid, or classroom timeline. */
public class BookingDetailActivity extends AppCompatActivity {
    public static final String EXTRA_ID = "extra_id";
    public static final String EXTRA_GROUP_ID = "extra_group_id";
    public static final String EXTRA_TITLE = "extra_title";
    public static final String EXTRA_MODE = "extra_mode";
    public static final String EXTRA_BOOKING_URL = "extra_booking_url";
    private Repo repo;
    private Rows.BookingGroup group;
    private Rows.BookingResource resource;
    private String spaceId;
    private int dayOffset;
    private int queryMinute = -1;
    private LinearLayout slotsContainer;
    private GridLayout seatsGrid;
    private ChipGroup days;
    private TextView meta;
    private NoteAdapter notes;

    @Override protected void onCreate(@Nullable Bundle saved) {
        super.onCreate(saved);
        setContentView(R.layout.activity_booking_detail);
        repo = Repo.get(this);
        dayOffset = saved == null ? getIntent().getIntExtra(RoomDetailActivity.EXTRA_DAY_OFFSET, 0) : saved.getInt("day_offset", 0);
        queryMinute = saved == null ? getIntent().getIntExtra(RoomDetailActivity.EXTRA_QUERY_MINUTE, -1) : saved.getInt("query_minute", -1);
        ((MaterialToolbar)findViewById(R.id.toolbar)).setNavigationOnClickListener(v -> finish());
        meta = findViewById(R.id.tv_meta);
        slotsContainer = findViewById(R.id.container_slots);
        seatsGrid = findViewById(R.id.grid_seats);
        days = findViewById(R.id.chips_day);
        RecyclerView rv = findViewById(R.id.rv_notes);
        rv.setLayoutManager(new LinearLayoutManager(this));
        notes = new NoteAdapter(true); rv.setAdapter(notes);
        findViewById(R.id.btn_note).setOnClickListener(v -> {
            if (spaceId != null) SpaceUi.publish(this, spaceId, null);
        });
        meta.setText("正在加载资源数据…");
        findViewById(R.id.btn_reserve).setEnabled(false);
        load();
    }

    private void load() {
        repo.ensureLoaded(() -> {
            if (isFinishing() || isDestroyed()) return;
            if (!repo.isLoaded()) {
                meta.setText("资源数据加载失败，点击重试\n" + repo.getLoadError());
                meta.setOnClickListener(v -> load()); return;
            }
            meta.setOnClickListener(null);
            resource = repo.resourceOf(getIntent().getStringExtra(EXTRA_ID));
            String groupId = getIntent().getStringExtra(EXTRA_GROUP_ID);
            group = repo.groupOf(groupId != null ? groupId : resource == null ? null : resource.groupId);
            if (group == null) { meta.setText("找不到这个预约分组，请返回列表重选"); return; }
            if (resource == null) {
                List<Rows.BookingResource> resources = repo.bookingResources(group.id);
                if (!resources.isEmpty()) resource = resources.get(0);
            }
            spaceId = resource == null ? null : resource.spaceId;
            String title = BookableVM.MODE_SEATS.equals(group.displayMode) ? group.label
                    : resource == null ? group.label : resource.label;
            ((MaterialToolbar)findViewById(R.id.toolbar)).setTitle(title);
            ((TextView)findViewById(R.id.tv_title)).setText(title);
            MaterialButton reserve = findViewById(R.id.btn_reserve);
            String url = resource == null ? null : repo.bookingUrlForResource(resource.id);
            boolean configured = configuredUrl(url);
            reserve.setEnabled(configured);
            reserve.setText(configured ? "Go and make a reservation" : "预约入口待配置");
            reserve.setOnClickListener(v -> UrlOpener.open(this, url));
            findViewById(R.id.btn_note).setEnabled(spaceId != null && repo.spaceOf(spaceId) != null);
            buildDays(); render();
        });
    }

    private boolean configuredUrl(String url) {
        if (url == null || url.trim().isEmpty()) return false;
        Uri uri = Uri.parse(url);
        if (!"https".equals(uri.getScheme()) && !"http".equals(uri.getScheme())) return false;
        String host = uri.getHost();
        if (host == null) return false;
        return !("www.xjtlu.edu.cn".equals(host) && (uri.getPath() == null || uri.getPath().isEmpty() || "/".equals(uri.getPath())));
    }

    @Override protected void onResume() { super.onResume(); if (group != null) load(); }
    @Override protected void onSaveInstanceState(@NonNull Bundle out) {
        out.putInt("day_offset", dayOffset); out.putInt("query_minute", queryMinute); super.onSaveInstanceState(out);
    }
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
        meta.setText(group.label + " · " + TimeProvider.dayLabel(dayOffset) + "\n" + repo.dataLabel());
        boolean isSeats = BookableVM.MODE_SEATS.equals(group.displayMode);
        seatsGrid.setVisibility(isSeats ? View.VISIBLE : View.GONE);
        slotsContainer.setVisibility(isSeats ? View.GONE : View.VISIBLE);
        findViewById(R.id.tv_legend).setVisibility(isSeats ? View.VISIBLE : View.GONE);
        if (isSeats) renderSeats();
        else if (BookableVM.MODE_TIMELINE.equals(group.displayMode)) renderTimeline();
        else renderSlots();
        if (spaceId != null) repo.notesOf(Note.T_SPACE, spaceId, rows -> {
            if (!isFinishing() && !isDestroyed()) notes.setItems(rows);
        });
    }
    private void renderSlots() {
        slotsContainer.removeAllViews();
        if (resource == null) { message("暂无资源数据"); return; }
        List<BookableSlot> slots = repo.slotsOf(resource.id, dayOffset);
        if (!repo.hasBookingSnapshot(resource.id, dayOffset)) {
            message("这一天没有有效预约快照，状态未知。请以官方系统为准。"); return;
        }
        if (slots.isEmpty()) { message("这一天未提供预约时段"); return; }
        for (BookableSlot slot : slots) {
            View row = LayoutInflater.from(this).inflate(R.layout.item_slot, slotsContainer, false);
            ((TextView)row.findViewById(R.id.tv_time)).setText(TimeText.range(slot.startMin, slot.endMin));
            TextView state = row.findViewById(R.id.tv_status);
            String label;
            int color;
            if (BookableSlot.FREE.equals(slot.status)) { label = "空闲"; color = 0xFF0E9F6E; }
            else if (BookableSlot.BOOKED.equals(slot.status)) { label = "已预约"; color = 0xFF9E9E9E; }
            else { label = "状态未知"; color = 0xFF7D8592; }
            state.setText(label); Pills.apply(state, color); slotsContainer.addView(row);
        }
    }
    private void renderTimeline() {
        slotsContainer.removeAllViews();
        if (spaceId == null) { message("这个资源没有有效空间关联，状态未知"); return; }
        boolean known = spaceId != null && repo.hasSchedule(spaceId, dayOffset);
        List<Lecture> lectures = repo.lecturesOn(spaceId, dayOffset);
        if (!known) message("这一天暂无有效课表，状态未知");
        else if (dayOffset == 0 || queryMinute >= 0) {
            int minute = queryMinute >= 0 && queryMinute < 1440 ? queryMinute : TimeProvider.minuteOfDay();
            StatusCalculator.State state = StatusCalculator.of(lectures, minute);
            if (queryMinute >= 0) message(TimeProvider.dayLabel(dayOffset) + " " + TimeText.hhmm(minute));
            message(state == StatusCalculator.State.IN_CLASS ? "课表显示上课中"
                    : state == StatusCalculator.State.SOON ? "30 分钟内即将上课" : "当前为课表空档，请以现场情况为准");
        } else message(TimeProvider.dayLabel(dayOffset) + "共 " + lectures.size() + " 节课");
        HorizontalScrollView scroll = new HorizontalScrollView(this);
        TimelineView timeline = new TimelineView(this);
        timeline.setLectures(lectures); timeline.setDataKnown(known); timeline.setShowNowLine(dayOffset == 0);
        scroll.addView(timeline, new HorizontalScrollView.LayoutParams(dp(780), dp(60)));
        slotsContainer.addView(scroll);
        message("灰 = 课表空档　蓝 = 有课　斜线 = 未知");
    }
    private void message(String text) {
        TextView view = new TextView(this); view.setText(text); view.setTextColor(0xFF6B7280);
        view.setTextSize(13); view.setPadding(dp(14), dp(12), dp(14), dp(12));
        slotsContainer.addView(view);
    }
    private void renderSeats() {
        seatsGrid.removeAllViews();
        List<LibrarySeat> seats = repo.seatsOfGroup(group.id, dayOffset);
        if (seats.isEmpty()) {
            seatsGrid.setColumnCount(1);
            TextView empty = new TextView(this); empty.setText("这个区域暂无座位资源");
            empty.setPadding(dp(14), dp(16), dp(14), dp(16)); seatsGrid.addView(empty); return;
        }
        int minRow = Integer.MAX_VALUE, maxRow = Integer.MIN_VALUE;
        int minCol = Integer.MAX_VALUE, maxCol = Integer.MIN_VALUE;
        for (LibrarySeat seat : seats) {
            minRow = Math.min(minRow, seat.row); maxRow = Math.max(maxRow, seat.row);
            minCol = Math.min(minCol, seat.col); maxCol = Math.max(maxCol, seat.col);
        }
        seatsGrid.setColumnCount(maxCol - minCol + 1);
        seatsGrid.setRowCount(maxRow - minRow + 1);
        for (LibrarySeat seat : seats) {
            TextView cell = new TextView(this);
            String no = seat.seatNo == null ? seat.id : seat.seatNo;
            cell.setText(no.length() > 3 ? no.substring(no.length() - 3) : no);
            cell.setGravity(android.view.Gravity.CENTER); cell.setTextSize(10); cell.setTextColor(0xFFFFFFFF);
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams(GridLayout.spec(seat.row - minRow),
                    GridLayout.spec(seat.col - minCol, 1f));
            lp.width = 0; lp.height = dp(40); lp.setMargins(dp(3), dp(3), dp(3), dp(3)); cell.setLayoutParams(lp);
            GradientDrawable bg = new GradientDrawable(); bg.setCornerRadius(dp(6)); bg.setColor(seatColor(seat.status));
            cell.setBackground(bg);
            String description = no + " · " + seatLabel(seat.status) + " · " + TimeProvider.dayLabel(dayOffset);
            cell.setContentDescription(description);
            cell.setOnClickListener(v -> Toast.makeText(this, description, Toast.LENGTH_SHORT).show());
            seatsGrid.addView(cell);
        }
    }
    private String seatLabel(String status) {
        if (LibrarySeat.AVAILABLE.equals(status)) return "空闲";
        if (LibrarySeat.PART_FREE.equals(status)) return "部分时段空闲";
        if (LibrarySeat.RESERVED.equals(status)) return "已预约";
        if (LibrarySeat.UNAVAILABLE.equals(status)) return "不可用";
        return "状态未知（没有有效快照）";
    }
    private int seatColor(String status) {
        if (LibrarySeat.AVAILABLE.equals(status)) return 0xFF4CAF50;
        if (LibrarySeat.PART_FREE.equals(status)) return 0xFF8BC34A;
        if (LibrarySeat.RESERVED.equals(status)) return 0xFFBE8B00;
        if (LibrarySeat.UNAVAILABLE.equals(status)) return 0xFF9E9E9E;
        return 0xFF596579;
    }
    private int dp(int value) { return (int)(value * getResources().getDisplayMetrics().density); }
}
