package com.xjtlu.campusspace.ui.booking;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.tabs.TabLayout;
import com.xjtlu.campusspace.R;
import com.xjtlu.campusspace.adapter.BookableAdapter;
import com.xjtlu.campusspace.adapter.BookableVM;
import com.xjtlu.campusspace.adapter.SpaceAdapter;
import com.xjtlu.campusspace.core.StatusCalculator;
import com.xjtlu.campusspace.core.TimeProvider;
import com.xjtlu.campusspace.data.Repo;
import com.xjtlu.campusspace.data.entity.DiscussionRoom;
import com.xjtlu.campusspace.data.entity.Lecture;
import com.xjtlu.campusspace.data.entity.LibrarySeat;
import com.xjtlu.campusspace.data.entity.QuietPod;
import com.xjtlu.campusspace.data.entity.Space;
import com.xjtlu.campusspace.ui.home.RoomDetailActivity;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Tab2：可预约空间 */
public class BookingFragment extends Fragment {

    private static final int CAT_DISCUSSION_ROOM = 0;
    private static final int CAT_QUIET_POD = 1;
    private static final int CAT_DISCUSSION_AREA = 2;
    private static final int CAT_SEAT = 3;

    private static final String ALL = "全部";

    private Repo repo;
    private int category = CAT_DISCUSSION_ROOM;
    private String zone = ALL;

    private RecyclerView rv;
    private Spinner spinner;
    private TextView tvEmpty;

    private BookableAdapter bookableAdapter;
    private SpaceAdapter areaAdapter;
    private boolean updatingSpinner = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_booking, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        repo = Repo.get(requireContext());

        TabLayout tabs = v.findViewById(R.id.tabs);
        tabs.addTab(tabs.newTab().setText("讨论室"));
        tabs.addTab(tabs.newTab().setText("静音仓"));
        tabs.addTab(tabs.newTab().setText("讨论区"));
        tabs.addTab(tabs.newTab().setText("图书馆座位"));
        tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                category = tab.getPosition();
                zone = ALL;
                rebuildZones();
                refresh();
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
            }
        });

        spinner = v.findViewById(R.id.spinner_zone);
        tvEmpty = v.findViewById(R.id.tv_empty);
        rv = v.findViewById(R.id.rv);
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));

        bookableAdapter = new BookableAdapter(this::openBookable);
        areaAdapter = new SpaceAdapter(space -> {
            Intent i = new Intent(requireContext(), RoomDetailActivity.class);
            i.putExtra(RoomDetailActivity.EXTRA_SPACE_ID, space.id);
            startActivity(i);
        });

        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (updatingSpinner) return;
                Object o = parent.getItemAtPosition(position);
                if (o == null) return;
                zone = o.toString();
                refresh();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        rebuildZones();
        refresh();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (repo != null) refresh();
    }

    private void openBookable(BookableVM vm) {
        Intent i = new Intent(requireContext(), BookingDetailActivity.class);
        i.putExtra(BookingDetailActivity.EXTRA_ID, vm.id);
        i.putExtra(BookingDetailActivity.EXTRA_TITLE, vm.title);
        i.putExtra(BookingDetailActivity.EXTRA_MODE, vm.mode);
        i.putExtra(BookingDetailActivity.EXTRA_BOOKING_URL, vm.bookingUrl);
        startActivity(i);
    }

    // -------------------------------------------------- 区域下拉

    private List<String> zoneOptions() {
        Set<String> zones = new LinkedHashSet<>();
        switch (category) {
            case CAT_DISCUSSION_ROOM:
                for (DiscussionRoom d : repo.discussionRooms()) zones.add(d.zone);
                break;
            case CAT_QUIET_POD:
                for (QuietPod p : repo.quietPods()) zones.add(p.zone);
                break;
            case CAT_DISCUSSION_AREA:
                for (Space s : repo.discussionAreas()) zones.add(s.building);
                break;
            case CAT_SEAT:
                zones.addAll(repo.seatsByZone().keySet());
                break;
            default:
                break;
        }
        List<String> out = new ArrayList<>();
        out.add(ALL);
        out.addAll(zones);
        return out;
    }

    private void rebuildZones() {
        List<String> options = zoneOptions();
        if (!options.contains(zone)) zone = ALL;

        ArrayAdapter<String> ad = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_item, options);
        ad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        updatingSpinner = true;
        spinner.setAdapter(ad);
        spinner.setSelection(options.indexOf(zone));
        updatingSpinner = false;
    }

    private boolean matchZone(String z) {
        return ALL.equals(zone) || zone.equals(z);
    }

    // -------------------------------------------------- 列表

    private void refresh() {
        if (repo == null || !repo.isLoaded()) return;

        if (category == CAT_DISCUSSION_AREA) {
            bindAreas();
            return;
        }

        List<BookableVM> vms = new ArrayList<>();
        if (category == CAT_DISCUSSION_ROOM) {
            for (DiscussionRoom d : repo.discussionRooms()) {
                if (!matchZone(d.zone)) continue;
                int[] st = repo.slotStats(d.id, 0);
                vms.add(new BookableVM(d.id, d.name,
                        d.zone + " · " + d.floor + "F · " + d.capacity + " 人",
                        "讨论室", slotText(st), slotColor(st),
                        d.bookingUrl, BookableVM.MODE_SLOTS));
            }
        } else if (category == CAT_QUIET_POD) {
            for (QuietPod p : repo.quietPods()) {
                if (!matchZone(p.zone)) continue;
                int[] st = repo.slotStats(p.id, 0);
                vms.add(new BookableVM(p.id, p.name,
                        p.zone + " · " + p.floor + "F · 单人",
                        "静音仓", slotText(st), slotColor(st),
                        p.bookingUrl, BookableVM.MODE_SLOTS));
            }
        } else if (category == CAT_SEAT) {
            for (Map.Entry<String, List<LibrarySeat>> e : repo.seatsByZone().entrySet()) {
                if (!matchZone(e.getKey())) continue;
                int free = 0;
                for (LibrarySeat s : e.getValue()) {
                    if (LibrarySeat.AVAILABLE.equals(s.status)
                            || LibrarySeat.PART_FREE.equals(s.status)) free++;
                }
                int total = e.getValue().size();
                String url = e.getValue().isEmpty() ? "" : e.getValue().get(0).bookingUrl;
                vms.add(new BookableVM(e.getKey(), e.getKey(),
                        "图书馆 · 共 " + total + " 个座位",
                        "图书馆座位", free + "/" + total + " 个座位可坐",
                        slotColor(new int[]{free, total}), url, BookableVM.MODE_SEATS));
            }
        }

        if (vms.isEmpty()) {
            rv.setVisibility(View.GONE);
            tvEmpty.setVisibility(View.VISIBLE);
        } else {
            tvEmpty.setVisibility(View.GONE);
            rv.setVisibility(View.VISIBLE);
        }
        rv.setAdapter(bookableAdapter);
        bookableAdapter.setItems(vms);
    }

    private void bindAreas() {
        List<Space> base = repo.discussionAreas();
        int day = TimeProvider.isoDayOfWeek();
        int now = TimeProvider.minuteOfDay();

        List<SpaceAdapter.Item> rows = new ArrayList<>();
        for (Space s : base) {
            if (!matchZone(s.building)) continue;
            List<Lecture> today = repo.lecturesOf(s.id, day);
            StatusCalculator.State st = StatusCalculator.of(today, now);
            String sub = s.building + " · " + s.floor + "F · 可容纳 " + s.capacity + " 人 · 讨论区";
            rows.add(new SpaceAdapter.Item(s, st, sub));
        }

        if (rows.isEmpty()) {
            rv.setVisibility(View.GONE);
            tvEmpty.setVisibility(View.VISIBLE);
        } else {
            tvEmpty.setVisibility(View.GONE);
            rv.setVisibility(View.VISIBLE);
        }
        rv.setAdapter(areaAdapter);
        areaAdapter.setItems(rows);
    }

    private String slotText(int[] st) {
        if (st[1] == 0) return "今天没有可预约时段";
        return "今天空闲 " + st[0] + "/" + st[1] + " 个时段";
    }

    private int slotColor(int[] st) {
        if (st[1] == 0) return 0xFF9E9E9E;
        double ratio = st[0] / (double) st[1];
        if (ratio >= 0.6) return 0xFF0E9F6E;
        if (ratio >= 0.2) return 0xFFF59E0B;
        return 0xFFEF4444;
    }
}
