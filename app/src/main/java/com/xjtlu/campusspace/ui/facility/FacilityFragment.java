package com.xjtlu.campusspace.ui.facility;

import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
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

import com.xjtlu.campusspace.R;
import com.xjtlu.campusspace.adapter.FacilityAdapter;
import com.xjtlu.campusspace.adapter.NoteAdapter;
import com.xjtlu.campusspace.data.Repo;
import com.xjtlu.campusspace.data.entity.Building;
import com.xjtlu.campusspace.data.entity.Facility;
import com.xjtlu.campusspace.data.entity.Note;
import com.xjtlu.campusspace.data.entity.Space;
import com.xjtlu.campusspace.ui.MainActivity;
import com.xjtlu.campusspace.ui.note.NotePublishActivity;

import java.util.ArrayList;
import java.util.List;

/** Tab3：公共设备 */
public class FacilityFragment extends Fragment {

    private static final String[] TYPE_LABELS = {"饮水机", "打印机", "公共休息处", "开放座位区"};
    private static final String[] TYPE_VALUES = {"饮水机", "打印机", "休息处", "开放座位区"};

    private Repo repo;
    private String building;
    private int floor = 1;
    private int typeIndex = 0;

    private Spinner spinnerBuilding;
    private Spinner spinnerFloor;
    private TextView tvCount;
    private View[] cells;
    private FacilityAdapter adapter;
    private NoteAdapter noteAdapter;
    private boolean updatingSpinner = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_facility, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        repo = Repo.get(requireContext());
        if (!repo.buildings.isEmpty()) building = repo.buildings.get(0).code;

        spinnerBuilding = v.findViewById(R.id.spinner_building);
        spinnerFloor = v.findViewById(R.id.spinner_floor);
        tvCount = v.findViewById(R.id.tv_count);

        cells = new View[]{
                v.findViewById(R.id.cell_drinking),
                v.findViewById(R.id.cell_printer),
                v.findViewById(R.id.cell_rest),
                v.findViewById(R.id.cell_open)
        };
        for (int i = 0; i < cells.length; i++) {
            final int idx = i;
            cells[i].setOnClickListener(view -> view.post(() -> {
                typeIndex = idx;
                paintCells();
                refresh();
            }));
        }

        RecyclerView rv = v.findViewById(R.id.rv);
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new FacilityAdapter(f -> {
            // "查看空间详情" -> 切回 Tab1
            repo.pendingBuilding = f.building;
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).selectTab(0);
            }
        });
        rv.setAdapter(adapter);

        RecyclerView rvNotes = v.findViewById(R.id.rv_notes);
        rvNotes.setLayoutManager(new LinearLayoutManager(requireContext()));
        noteAdapter = new NoteAdapter(false);
        rvNotes.setAdapter(noteAdapter);

        setupBuildingSpinner();
        setupFloorSpinner();
        paintCells();
        refresh();
        loadNotes();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (repo != null) {
            refresh();
            loadNotes();
        }
    }

    private void setupBuildingSpinner() {
        List<String> options = new ArrayList<>();
        for (Building b : repo.buildings) options.add(b.code);
        ArrayAdapter<String> ad = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_item, options);
        ad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerBuilding.setAdapter(ad);
        spinnerBuilding.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (updatingSpinner) return;
                if (position < repo.buildings.size()) {
                    building = repo.buildings.get(position).code;
                    refresh();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }

    private void setupFloorSpinner() {
        List<String> options = new ArrayList<>();
        for (int f = 1; f <= 5; f++) options.add(f + "F");
        ArrayAdapter<String> ad = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_item, options);
        ad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerFloor.setAdapter(ad);
        spinnerFloor.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (updatingSpinner) return;
                floor = position + 1;
                refresh();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }

    private void paintCells() {
        for (int i = 0; i < cells.length; i++) {
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(dp(12));
            boolean on = i == typeIndex;
            bg.setColor(on ? 0xFF2F6FED : 0xFFF1F3F7);
            cells[i].setBackground(bg);
            ((TextView) cells[i]).setTextColor(on ? 0xFFFFFFFF : 0xFF1F2430);
        }
    }

    private void refresh() {
        if (repo == null || !repo.isLoaded()) return;
        String type = TYPE_VALUES[typeIndex];
        List<Facility> all = repo.facilitiesOf(building, floor);
        List<Facility> rows = new ArrayList<>();
        for (Facility f : all) {
            if (f.type.equals(type)) rows.add(f);
        }
        adapter.setItems(rows);
        tvCount.setText(String.format("%s %dF · %s %d 处",
                building == null ? "" : building, floor, TYPE_LABELS[typeIndex], rows.size()));
    }

    private void loadNotes() {
        if (repo == null) return;
        repo.latestNotes(60, rows -> {
            if (!isAdded()) return;
            List<Note> only = new ArrayList<>();
            for (Note n : rows) {
                if (Note.T_FACILITY.equals(n.targetType)) only.add(n);
            }
            noteAdapter.setItems(only);
        });
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }
}
