package com.xjtlu.campusspace.ui.booking;

import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

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
import com.xjtlu.campusspace.core.TimeProvider;
import com.xjtlu.campusspace.data.Repo;
import com.xjtlu.campusspace.data.entity.BookableSlot;
import com.xjtlu.campusspace.data.entity.LibrarySeat;
import com.xjtlu.campusspace.data.entity.Note;
import com.xjtlu.campusspace.ui.widget.Pills;
import com.xjtlu.campusspace.util.UrlOpener;

import java.util.List;

/** 页面 4：可预约空间详情（讨论室/静音仓 = 时段列表；图书馆 = 座位网格） */
public class BookingDetailActivity extends AppCompatActivity {

    public static final String EXTRA_ID = "extra_id";
    public static final String EXTRA_TITLE = "extra_title";
    public static final String EXTRA_MODE = "extra_mode";
    public static final String EXTRA_BOOKING_URL = "extra_booking_url";

    private static final int[] DAY_OFFSETS = {-1, 0, 1, 2};

    private Repo repo;
    private String id;
    private String title;
    private String mode;
    private String bookingUrl;
    private int dayOffset = 0;

    private LinearLayout containerSlots;
    private GridLayout gridSeats;
    private ChipGroup chipsDay;
    private TextView tvMeta;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_booking_detail);

        repo = Repo.get(this);
        id = getIntent().getStringExtra(EXTRA_ID);
        title = getIntent().getStringExtra(EXTRA_TITLE);
        mode = getIntent().getStringExtra(EXTRA_MODE);
        bookingUrl = getIntent().getStringExtra(EXTRA_BOOKING_URL);
        if (mode == null) mode = BookableVM.MODE_SLOTS;

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle(title == null ? "" : title);
        toolbar.setNavigationOnClickListener(v -> finish());

        TextView tvTitle = findViewById(R.id.tv_title);
        tvTitle.setText(title == null ? "" : title);

        tvMeta = findViewById(R.id.tv_meta);
        containerSlots = findViewById(R.id.container_slots);
        gridSeats = findViewById(R.id.grid_seats);
        chipsDay = findViewById(R.id.chips_day);

        RecyclerView rvNotes = findViewById(R.id.rv_notes);
        rvNotes.setLayoutManager(new LinearLayoutManager(this));
        NoteAdapter noteAdapter = new NoteAdapter(true);
        rvNotes.setAdapter(noteAdapter);

        MaterialButton btn = findViewById(R.id.btn_reserve);
        btn.setOnClickListener(v -> UrlOpener.open(this, bookingUrl));

        View scrollDay = findViewById(R.id.scroll_day);
        boolean isSeats = BookableVM.MODE_SEATS.equals(mode);

        if (isSeats) {
            scrollDay.setVisibility(View.GONE);
            containerSlots.setVisibility(View.GONE);
            gridSeats.setVisibility(View.VISIBLE);
            findViewById(R.id.tv_legend).setVisibility(View.VISIBLE);
            findViewById(R.id.tv_notes_title).setVisibility(View.GONE);
            rvNotes.setVisibility(View.GONE);
            tvMeta.setText("图书馆 · " + TimeProvider.todayLabel() + " 当前座位状态");
            renderSeats();
        } else {
            scrollDay.setVisibility(View.VISIBLE);
            containerSlots.setVisibility(View.VISIBLE);
            gridSeats.setVisibility(View.GONE);
            buildDayChips();
            renderSlots();
            loadNotes(noteAdapter);
        }
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
                renderSlots();
            }));
            chipsDay.addView(c);
        }
    }

    private void renderSlots() {
        tvMeta.setText(String.format("%s · %s", title == null ? "" : title,
                TimeProvider.dayLabel(dayOffset)));

        containerSlots.removeAllViews();
        List<BookableSlot> slots = repo.slotsOf(id, dayOffset);
        if (slots.isEmpty()) {
            TextView tv = new TextView(this);
            tv.setText("这一天没有可预约时段");
            tv.setPadding(dp(16), dp(16), dp(16), dp(16));
            tv.setTextColor(0xFF6B7280);
            containerSlots.addView(tv);
            return;
        }

        LayoutInflater inflater = LayoutInflater.from(this);
        for (BookableSlot s : slots) {
            View row = inflater.inflate(R.layout.item_slot, containerSlots, false);
            TextView time = row.findViewById(R.id.tv_time);
            TextView status = row.findViewById(R.id.tv_status);
            time.setText(com.xjtlu.campusspace.core.TimeText.range(s.startMin, s.endMin));
            if (s.isFree()) {
                Pills.apply(status, 0xFF0E9F6E);
                status.setText("空闲");
            } else {
                Pills.apply(status, 0xFFBFBFBF);
                status.setText("已预约");
            }
            containerSlots.addView(row);
        }
    }

    private void renderSeats() {
        gridSeats.removeAllViews();
        List<LibrarySeat> seats = repo.seatsOfZone(id);
        if (seats.isEmpty()) {
            TextView tv = new TextView(this);
            tv.setText("这个区域暂无座位数据");
            tv.setPadding(dp(16), dp(16), dp(16), dp(16));
            gridSeats.addView(tv);
            return;
        }

        LayoutInflater inflater = LayoutInflater.from(this);
        for (LibrarySeat s : seats) {
            TextView cell = (TextView) inflater.inflate(R.layout.item_seat, gridSeats, false);
            String no = s.seatNo;
            cell.setText(no.length() > 3 ? no.substring(no.length() - 3) : no);

            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(dp(6));
            bg.setColor(seatColor(s.status));
            cell.setBackground(bg);

            cell.setOnClickListener(v -> {
                String state;
                switch (s.status) {
                    case LibrarySeat.PART_FREE: state = "部分时段空闲"; break;
                    case LibrarySeat.RESERVED: state = "已被预约"; break;
                    case LibrarySeat.UNAVAILABLE: state = "不可用"; break;
                    default: state = "完全空闲"; break;
                }
                com.google.android.material.snackbar.Snackbar
                        .make(v, s.seatNo + " · " + state, com.google.android.material.snackbar.Snackbar.LENGTH_SHORT)
                        .show();
            });
            gridSeats.addView(cell);
        }
    }

    private int seatColor(String status) {
        switch (status) {
            case LibrarySeat.PART_FREE:
                return 0xFF8BC34A;
            case LibrarySeat.RESERVED:
                return 0xFFFFC107;
            case LibrarySeat.UNAVAILABLE:
                return 0xFF9E9E9E;
            default:
                return 0xFF4CAF50;
        }
    }

    private void loadNotes(NoteAdapter adapter) {
        String targetType = null;
        if (id != null && id.startsWith("QP-")) {
            targetType = Note.T_QUIET_POD;
        } else if (id != null && id.startsWith("DR-")) {
            targetType = Note.T_DISCUSSION_ROOM;
        }
        if (targetType == null) return;
        repo.notesOf(targetType, id, rows -> {
            if (isFinishing()) return;
            adapter.setItems(rows);
        });
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }
}
