package com.xjtlu.campusspace.ui.home;

import android.content.Intent;
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
import com.xjtlu.campusspace.data.entity.Lecture;
import com.xjtlu.campusspace.data.entity.Note;
import com.xjtlu.campusspace.data.entity.Space;
import com.xjtlu.campusspace.ui.note.NotePublishActivity;

import java.util.ArrayList;
import java.util.List;

/** Tab1：教学楼 */
public class HomeFragment extends Fragment {

    private static final int SCOPE_ROOM = 0;
    private static final int SCOPE_PUBLIC = 1;

    private static final int FILTER_ALL = 0;
    private static final int FILTER_FREE = 1;
    private static final int FILTER_SOON = 2;

    private Repo repo;
    private SpaceAdapter adapter;
    private NoteAdapter notesAdapter;
    private TextView tvSummary;

    private ChipGroup chipsBuilding;
    private ChipGroup chipsFilter;

    private String selectedBuilding;
    private int scope = SCOPE_ROOM;
    private int filter = FILTER_ALL;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        repo = Repo.get(requireContext());
        tvSummary = v.findViewById(R.id.tv_summary);
        chipsBuilding = v.findViewById(R.id.chips_building);
        chipsFilter = v.findViewById(R.id.chips_filter);

        RecyclerView rvSpaces = v.findViewById(R.id.rv_spaces);
        rvSpaces.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new SpaceAdapter(space -> {
            Intent i = new Intent(requireContext(), RoomDetailActivity.class);
            i.putExtra(RoomDetailActivity.EXTRA_SPACE_ID, space.id);
            startActivity(i);
        });
        rvSpaces.setAdapter(adapter);

        RecyclerView rvNotes = v.findViewById(R.id.rv_notes);
        rvNotes.setLayoutManager(new LinearLayoutManager(requireContext()));
        notesAdapter = new NoteAdapter(false);
        rvNotes.setAdapter(notesAdapter);

        MaterialButtonToggleGroup toggle = v.findViewById(R.id.toggle_scope);
        toggle.check(R.id.btn_scope_room);
        toggle.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;
            scope = checkedId == R.id.btn_scope_public ? SCOPE_PUBLIC : SCOPE_ROOM;
            rebuildBuildingChips();
            refresh();
        });

        v.findViewById(R.id.fab_note).setOnClickListener(view -> {
            Intent i = new Intent(requireContext(), NotePublishActivity.class);
            i.putExtra(NotePublishActivity.EXTRA_TARGET_TYPE, Note.T_SPACE);
            i.putExtra(NotePublishActivity.EXTRA_TARGET_ID, selectedBuilding);
            i.putExtra(NotePublishActivity.EXTRA_TARGET_NAME,
                    selectedBuilding == null ? "校园" : selectedBuilding);
            startActivity(i);
        });

        if (repo.buildings.isEmpty()) {
            selectedBuilding = null;
        } else {
            selectedBuilding = repo.buildings.get(0).code;
        }

        rebuildBuildingChips();
        rebuildFilterChips();
        refresh();
        loadNotes();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (repo == null) return;
        // Tab3 的"查看空间详情"会设置这个字段
        String pending = repo.pendingBuilding;
        if (pending != null) {
            repo.pendingBuilding = null;
            selectedBuilding = pending;
            rebuildBuildingChips();
        }
        refresh();
        loadNotes();
    }

    private void rebuildBuildingChips() {
        chipsBuilding.removeAllViews();
        List<Building> list = repo.buildings;
        if (list.isEmpty()) return;
        for (Building b : list) {
            if (selectedBuilding == null) selectedBuilding = b.code;
            Chip c = new Chip(requireContext());
            c.setText(b.code);
            c.setCheckable(true);
            c.setChecked(b.code.equals(selectedBuilding));
            c.setOnClickListener(view -> view.post(() -> {
                selectedBuilding = b.code;
                rebuildBuildingChips();
                refresh();
            }));
            chipsBuilding.addView(c);
        }
        if (scope == SCOPE_PUBLIC && repo.discussionAreasOf(selectedBuilding).isEmpty()) {
            // 该楼没有讨论区时退到第一个有讨论区的楼
            for (Building b : list) {
                if (!repo.discussionAreasOf(b.code).isEmpty()) {
                    selectedBuilding = b.code;
                    break;
                }
            }
        }
    }

    private void rebuildFilterChips() {
        chipsFilter.removeAllViews();
        String[] labels = {"全部", "当前空闲", "即将上课"};
        for (int i = 0; i < labels.length; i++) {
            final int value = i;
            Chip c = new Chip(requireContext());
            c.setText(labels[i]);
            c.setCheckable(true);
            c.setChecked(filter == i);
            c.setOnClickListener(view -> view.post(() -> {
                filter = value;
                rebuildFilterChips();
                refresh();
            }));
            chipsFilter.addView(c);
        }
    }

    private void refresh() {
        if (repo == null || !repo.isLoaded()) return;

        List<Space> base;
        if (scope == SCOPE_PUBLIC) {
            base = repo.discussionAreasOf(selectedBuilding);
        } else {
            base = repo.spacesOf(selectedBuilding);
        }

        int day = TimeProvider.isoDayOfWeek();
        int now = TimeProvider.minuteOfDay();

        List<SpaceAdapter.Item> rows = new ArrayList<>();
        int freeCount = 0;
        for (Space s : base) {
            List<Lecture> today = repo.lecturesOf(s.id, day);
            StatusCalculator.State st = StatusCalculator.of(today, now);
            if (st == StatusCalculator.State.FREE) freeCount++;
            if (filter == FILTER_FREE && st != StatusCalculator.State.FREE) continue;
            if (filter == FILTER_SOON && st != StatusCalculator.State.SOON) continue;
            rows.add(new SpaceAdapter.Item(s, st, subtitleOf(s)));
        }
        adapter.setItems(rows);

        if (tvSummary != null) {
            tvSummary.setText(String.format("%s · %d 间 · %d 间空闲 · %s %s",
                    selectedBuilding == null ? "校园" : selectedBuilding,
                    base.size(), freeCount, TimeProvider.todayLabel(),
                    TimeText.hhmm(now)));
        }
    }

    private String subtitleOf(Space s) {
        String size = "LARGE".equals(s.size) ? "大教室" : "小教室";
        String extra = s.isDiscussionArea() ? " · 讨论区" : "";
        return s.floor + "F · 可容纳 " + s.capacity + " 人 · " + size + extra;
    }

    private void loadNotes() {
        if (repo == null) return;
        repo.latestNotes(20, rows -> {
            if (!isAdded()) return;
            notesAdapter.setItems(rows);
        });
    }
}
