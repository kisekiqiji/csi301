package com.xjtlu.campusspace.ui.home;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.xjtlu.campusspace.R;
import com.xjtlu.campusspace.adapter.NoteAdapter;
import com.xjtlu.campusspace.adapter.SpaceAdapter;
import com.xjtlu.campusspace.core.StatusCalculator;
import com.xjtlu.campusspace.core.TimeProvider;
import com.xjtlu.campusspace.core.TimeText;
import com.xjtlu.campusspace.data.Repo;
import com.xjtlu.campusspace.data.entity.Building;
import com.xjtlu.campusspace.data.entity.Space;
import com.xjtlu.campusspace.ui.SpaceUi;
import java.util.ArrayList;
import java.util.List;

/** Classroom and public-space views both use the canonical spaces table. */
public class HomeFragment extends Fragment {
    private Repo repo;
    private SpaceAdapter adapter;
    private NoteAdapter notes;
    private TextView summary;
    private ChipGroup buildingChips, filterChips;
    private MaterialButtonToggleGroup toggle;
    private String building;
    private boolean publicScope;
    private int filter;

    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup parent,
                                                @Nullable Bundle state) {
        return inflater.inflate(R.layout.fragment_home, parent, false);
    }

    @Override public void onViewCreated(@NonNull View v, @Nullable Bundle state) {
        repo = Repo.get(requireContext());
        summary = v.findViewById(R.id.tv_summary);
        buildingChips = v.findViewById(R.id.chips_building);
        filterChips = v.findViewById(R.id.chips_filter);
        RecyclerView rv = v.findViewById(R.id.rv_spaces);
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new SpaceAdapter(space -> SpaceUi.open(requireContext(), space));
        rv.setAdapter(adapter);
        RecyclerView rvNotes = v.findViewById(R.id.rv_notes);
        rvNotes.setLayoutManager(new LinearLayoutManager(requireContext()));
        notes = new NoteAdapter(false);
        rvNotes.setAdapter(notes);
        toggle = v.findViewById(R.id.toggle_scope);
        toggle.check(publicScope ? R.id.btn_scope_public : R.id.btn_scope_room);
        toggle.addOnButtonCheckedListener((group, id, checked) -> {
            if (!checked) return;
            publicScope = id == R.id.btn_scope_public;
            refresh();
        });
        v.findViewById(R.id.fab_note).setOnClickListener(view -> SpaceUi.publish(requireContext(), null, null));
        if (building == null && !repo.buildings.isEmpty()) building = repo.buildings.get(0).code;
        buildBuildings();
        buildFilters();
        refresh();
        repo.ensureLoaded(() -> {
            if (!isAdded() || getView() == null || !repo.isLoaded()) return;
            if (building == null && !repo.buildings.isEmpty()) building = repo.buildings.get(0).code;
            buildBuildings(); refresh();
        });
    }

    @Override public void onResume() {
        super.onResume();
        if (repo != null) repo.ensureLoaded(() -> { if (isAdded() && getView() != null) refresh(); });
    }
    @Override public void onHiddenChanged(boolean hidden) { super.onHiddenChanged(hidden); if (!hidden) refresh(); }

    private void buildBuildings() {
        buildingChips.removeAllViews();
        for (Building b : repo.buildings) {
            Chip chip = new Chip(requireContext());
            chip.setText(b.code);
            chip.setContentDescription(b.campus + " · " + b.name);
            chip.setCheckable(true);
            chip.setChecked(b.code.equals(building));
            chip.setOnClickListener(v -> { building = b.code; buildBuildings(); refresh(); });
            buildingChips.addView(chip);
        }
    }

    private void buildFilters() {
        filterChips.removeAllViews();
        String[] labels = {"全部", "当前空闲", "即将上课"};
        for (int n = 0; n < labels.length; n++) {
            final int choice = n;
            Chip chip = new Chip(requireContext());
            chip.setText(labels[n]); chip.setCheckable(true); chip.setChecked(filter == n);
            chip.setOnClickListener(v -> { filter = choice; buildFilters(); refresh(); });
            filterChips.addView(chip);
        }
    }

    private void refresh() {
        if (repo == null || getView() == null || !repo.isLoaded()) return;
        if (repo.pendingBuilding != null) {
            building = repo.pendingBuilding; repo.pendingBuilding = null;
            publicScope = true; toggle.check(R.id.btn_scope_public); buildBuildings();
        }
        List<Space> spaces = publicScope ? repo.publicSpacesOf(building) : repo.spacesOf(building);
        List<SpaceAdapter.Item> items = new ArrayList<>();
        int free = 0;
        for (Space s : spaces) {
            StatusCalculator.State status = SpaceUi.state(repo, s);
            if (status == StatusCalculator.State.FREE) free++;
            if (filter == 1 && status != StatusCalculator.State.FREE) continue;
            if (filter == 2 && status != StatusCalculator.State.SOON) continue;
            String subtitle = SpaceUi.subtitle(s);
            if (repo.bookingGroupForSpace(s.id) != null) subtitle += " · 可预约";
            items.add(new SpaceAdapter.Item(s, status, subtitle));
        }
        adapter.setItems(items);
        summary.setText((building == null ? "校园" : building) + " · " + spaces.size() + " 处 · "
                + free + " 处空闲 · " + TimeProvider.todayLabel() + " " + TimeText.hhmm(TimeProvider.minuteOfDay())
                + (items.isEmpty() ? "\n当前筛选下暂无空间数据" : ""));
        repo.latestNotes(20, rows -> { if (isAdded() && getView() != null) notes.setItems(rows); });
    }
}
