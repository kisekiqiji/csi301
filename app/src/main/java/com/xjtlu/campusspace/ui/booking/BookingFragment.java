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
import com.xjtlu.campusspace.core.StatusCalculator;
import com.xjtlu.campusspace.core.TimeProvider;
import com.xjtlu.campusspace.data.Repo;
import com.xjtlu.campusspace.data.entity.LibrarySeat;
import com.xjtlu.campusspace.data.entity.Space;
import com.xjtlu.campusspace.data.store.Rows;
import com.xjtlu.campusspace.ui.SpaceUi;
import java.util.ArrayList;
import java.util.List;

/** Category labels are presentation; available zones and resources come from the database. */
public class BookingFragment extends Fragment {
    private static final String[] CATEGORIES = {"DISCUSSION_ROOM", "QUIET_POD", "DISCUSSION_AREA", "LIBRARY_SEAT"};
    private static final String[] LABELS = {"讨论室", "静音仓", "讨论区", "图书馆座位"};
    private Repo repo;
    private int category;
    private String groupId;
    private final List<Rows.BookingGroup> groups = new ArrayList<>();
    private Spinner spinner;
    private RecyclerView rv;
    private TextView empty;
    private BookableAdapter adapter;

    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup parent,
                                                @Nullable Bundle state) {
        return inflater.inflate(R.layout.fragment_booking, parent, false);
    }

    @Override public void onViewCreated(@NonNull View v, @Nullable Bundle state) {
        repo = Repo.get(requireContext());
        spinner = v.findViewById(R.id.spinner_zone);
        empty = v.findViewById(R.id.tv_empty);
        rv = v.findViewById(R.id.rv);
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new BookableAdapter(this::open);
        rv.setAdapter(adapter);
        TabLayout tabs = v.findViewById(R.id.tabs);
        for (String label : LABELS) tabs.addTab(tabs.newTab().setText(label));
        tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override public void onTabSelected(TabLayout.Tab tab) {
                category = tab.getPosition(); groupId = null; buildGroups();
            }
            @Override public void onTabUnselected(TabLayout.Tab tab) {}
            @Override public void onTabReselected(TabLayout.Tab tab) {}
        });
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View child, int position, long id) {
                groupId = position > 0 && position <= groups.size() ? groups.get(position - 1).id : null;
                refresh();
            }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });
        buildGroups();
        repo.ensureLoaded(() -> {
            if (isAdded() && getView() != null && repo.isLoaded()) buildGroups();
        });
    }

    @Override public void onResume() {
        super.onResume();
        if (repo != null) repo.ensureLoaded(() -> {
            if (isAdded() && getView() != null && repo.isLoaded()) buildGroups();
        });
    }
    @Override public void onHiddenChanged(boolean hidden) { super.onHiddenChanged(hidden); if (!hidden) refresh(); }

    private void buildGroups() {
        groups.clear(); groups.addAll(repo.bookingGroups(CATEGORIES[category]));
        List<String> labels = new ArrayList<>(); labels.add("全部");
        int selected = 0;
        for (Rows.BookingGroup group : groups) {
            labels.add(group.label);
            if (group.id.equals(groupId)) selected = labels.size() - 1;
        }
        ArrayAdapter<String> choices = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item, labels);
        choices.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(choices); spinner.setSelection(selected);
        refresh();
    }

    private void open(BookableVM vm) {
        Intent i = new Intent(requireContext(), BookingDetailActivity.class);
        i.putExtra(BookingDetailActivity.EXTRA_ID, vm.id);
        i.putExtra(BookingDetailActivity.EXTRA_GROUP_ID, vm.groupId);
        startActivity(i);
    }

    private void refresh() {
        if (repo == null || getView() == null || !repo.isLoaded()) return;
        List<BookableVM> items = new ArrayList<>();
        for (Rows.BookingGroup group : groups) {
            if (groupId != null && !groupId.equals(group.id)) continue;
            List<Rows.BookingResource> resources = repo.bookingResources(group.id);
            if (BookableVM.MODE_SEATS.equals(group.displayMode)) {
                List<LibrarySeat> seats = repo.seatsOfGroup(group.id, 0);
                int free = 0, known = 0;
                for (LibrarySeat seat : seats) {
                    if (!LibrarySeat.UNKNOWN.equals(seat.status)) known++;
                    if (LibrarySeat.AVAILABLE.equals(seat.status) || LibrarySeat.PART_FREE.equals(seat.status)) free++;
                }
                String stat = known == 0 ? "座位状态未知"
                        : free + "/" + seats.size() + " 个座位有空余时段" + (known < seats.size() ? " · 部分未知" : "");
                BookableVM vm = new BookableVM(group.id, group.label, "图书馆 · 共 " + seats.size() + " 个座位",
                        LABELS[category], stat, known == 0 ? 0xFF7D8592 : color(free, seats.size()),
                        resources.isEmpty() ? "" : repo.bookingUrlForResource(resources.get(0).id), group.displayMode);
                vm.groupId = group.id;
                if (!resources.isEmpty()) vm.spaceId = resources.get(0).spaceId;
                items.add(vm);
                continue;
            }
            for (Rows.BookingResource resource : resources) {
                Space space = repo.spaceOf(resource.spaceId);
                String stat;
                int statusColor = 0xFF7D8592;
                if (BookableVM.MODE_TIMELINE.equals(group.displayMode)) {
                    StatusCalculator.State status = StatusCalculator.of(repo.lecturesOn(resource.spaceId, 0),
                            TimeProvider.minuteOfDay(), repo.hasSchedule(resource.spaceId, 0));
                    stat = status == StatusCalculator.State.FREE ? "课表空档" : status == StatusCalculator.State.SOON
                            ? "即将上课" : status == StatusCalculator.State.IN_CLASS ? "上课中" : "课表状态未知";
                    statusColor = status == StatusCalculator.State.FREE ? 0xFF0E9F6E
                            : status == StatusCalculator.State.UNKNOWN ? 0xFF7D8592 : 0xFFE5A000;
                } else {
                    int[] st = repo.slotStats(resource.id, 0);
                    stat = st[0] < 0 ? "预约状态未知" : st[1] == 0 ? "今天未提供预约时段"
                            : st[0] + "/" + st[1] + " 个时段空闲";
                    if (st[0] >= 0 && st[1] > 0) statusColor = color(st[0], st[1]);
                }
                BookableVM vm = new BookableVM(resource.id, resource.label,
                        group.label + (space == null ? "" : " · " + SpaceUi.subtitle(space)),
                        LABELS[category], stat, statusColor, repo.bookingUrlForResource(resource.id), group.displayMode);
                vm.groupId = group.id; vm.spaceId = resource.spaceId;
                items.add(vm);
            }
        }
        adapter.setItems(items);
        rv.setVisibility(items.isEmpty() ? View.GONE : View.VISIBLE);
        empty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
        empty.setText("这个区域暂无资源数据");
    }

    private int color(int free, int total) {
        if (total == 0) return 0xFF7D8592;
        if (free == 0) return 0xFFE05252;
        return free * 2 >= total ? 0xFF0E9F6E : 0xFFE5A000;
    }
}
