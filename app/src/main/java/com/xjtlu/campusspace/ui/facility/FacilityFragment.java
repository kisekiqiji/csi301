package com.xjtlu.campusspace.ui.facility;

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
import androidx.appcompat.app.AlertDialog;
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
import com.xjtlu.campusspace.data.store.Rows;
import com.xjtlu.campusspace.ui.SpaceUi;
import java.util.ArrayList;
import java.util.List;

/** The equipment tab also links to public spaces stored in spaces. */
public class FacilityFragment extends Fragment {
    private static final String[] LABELS = {"饮水机", "打印机", "公共休息处", "开放座位区"};
    private Repo repo;
    private String building, floorCode;
    private int typeIndex;
    private Spinner buildingSpinner, floorSpinner;
    private final List<Rows.Floor> floors = new ArrayList<>();
    private TextView count;
    private View[] cells;
    private FacilityAdapter adapter;
    private NoteAdapter notes;

    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup parent,
                                                @Nullable Bundle state) {
        return inflater.inflate(R.layout.fragment_facility, parent, false);
    }
    @Override public void onViewCreated(@NonNull View v, @Nullable Bundle state) {
        repo = Repo.get(requireContext());
        if (building == null && !repo.buildings.isEmpty()) building = repo.buildings.get(0).code;
        buildingSpinner = v.findViewById(R.id.spinner_building);
        floorSpinner = v.findViewById(R.id.spinner_floor);
        count = v.findViewById(R.id.tv_count);
        cells = new View[]{v.findViewById(R.id.cell_drinking), v.findViewById(R.id.cell_printer),
                v.findViewById(R.id.cell_rest), v.findViewById(R.id.cell_open)};
        for (int n = 0; n < cells.length; n++) {
            final int selected = n;
            cells[n].setOnClickListener(view -> { typeIndex = selected; paintCells(); refresh(); });
        }
        RecyclerView rv = v.findViewById(R.id.rv);
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new FacilityAdapter(this::open);
        rv.setAdapter(adapter);
        RecyclerView rvNotes = v.findViewById(R.id.rv_notes);
        rvNotes.setLayoutManager(new LinearLayoutManager(requireContext()));
        notes = new NoteAdapter(false);
        rvNotes.setAdapter(notes);

        List<String> buildings = new ArrayList<>();
        for (Building b : repo.buildings) buildings.add(b.code);
        ArrayAdapter<String> choices = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item, buildings);
        choices.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        buildingSpinner.setAdapter(choices);
        buildingSpinner.setSelection(Math.max(0, buildings.indexOf(building)));
        buildingSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View view, int position, long id) {
                if (position >= repo.buildings.size()) return;
                String next = repo.buildings.get(position).code;
                if (!next.equals(building)) { building = next; floorCode = null; buildFloors(); }
            }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });
        floorSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View view, int position, long id) {
                floorCode = position < floors.size() ? floors.get(position).code : null; refresh();
            }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });
        buildFloors(); paintCells();
        repo.ensureLoaded(() -> {
            if (!isAdded() || getView() == null || !repo.isLoaded()) return;
            if (building == null && !repo.buildings.isEmpty()) building = repo.buildings.get(0).code;
            List<String> options = new ArrayList<>();
            for (Building b : repo.buildings) options.add(b.code);
            ArrayAdapter<String> loaded = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item, options);
            loaded.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            buildingSpinner.setAdapter(loaded);
            buildingSpinner.setSelection(Math.max(0, options.indexOf(building)));
            buildFloors();
        });
    }
    @Override public void onResume() {
        super.onResume();
        if (repo != null) repo.ensureLoaded(() -> { if (isAdded() && getView() != null) refresh(); });
    }
    @Override public void onHiddenChanged(boolean hidden) { super.onHiddenChanged(hidden); if (!hidden) refresh(); }

    private void buildFloors() {
        floors.clear(); floors.addAll(repo.floorsOf(building));
        List<String> labels = new ArrayList<>();
        for (Rows.Floor f : floors) labels.add(f.code);
        int selected = labels.indexOf(floorCode);
        if (selected < 0) selected = 0;
        floorCode = labels.isEmpty() ? null : labels.get(selected);
        ArrayAdapter<String> choices = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item, labels);
        choices.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        floorSpinner.setAdapter(choices);
        if (!labels.isEmpty()) floorSpinner.setSelection(selected);
        refresh();
    }
    private void paintCells() {
        for (int n = 0; n < cells.length; n++) {
            GradientDrawable bg = new GradientDrawable(); bg.setCornerRadius(dp(12));
            bg.setColor(n == typeIndex ? 0xFF2F6FED : 0xFFF1F3F7);
            cells[n].setBackground(bg);
            ((TextView)cells[n]).setTextColor(n == typeIndex ? 0xFFFFFFFF : 0xFF1F2430);
        }
    }
    private void refresh() {
        if (repo == null || getView() == null || !repo.isLoaded()) return;
        List<FacilityAdapter.Item> items = new ArrayList<>();
        if (floorCode != null) {
            if (typeIndex < 2) {
                for (Facility f : repo.facilities()) {
                    if (building.equals(f.building) && floorCode.equals(f.floorCode) && LABELS[typeIndex].equals(f.type))
                        items.add(new FacilityAdapter.Item(f.type, f.locationText, f.spaceId, f.id));
                }
            } else {
                String type = typeIndex == 2 ? "PUBLIC_REST" : "OPEN_SEATING";
                for (Space s : repo.publicSpacesOf(building)) {
                    if (floorCode.equals(s.floorCode) && type.equals(s.type))
                        items.add(new FacilityAdapter.Item(LABELS[typeIndex], s.name + " · " + s.locationHint, s.id, null));
                }
            }
        }
        adapter.setItems(items);
        count.setText((building == null ? "" : building) + " " + (floorCode == null ? "暂无楼层数据" : floorCode)
                + " · " + LABELS[typeIndex] + " " + items.size() + " 处");
        repo.latestNotes(100, rows -> {
            if (!isAdded() || getView() == null) return;
            List<Note> filtered = new ArrayList<>();
            for (Note note : rows) {
                if (!Note.C_REPAIR.equals(note.category) && (note.equipmentId == null || note.equipmentId.isEmpty())) continue;
                Space space = repo.spaceOf(note.spaceId);
                if (space != null && building != null && building.equals(space.building)
                        && floorCode != null && floorCode.equals(space.floorCode)) filtered.add(note);
            }
            notes.setItems(filtered);
        });
    }

    private void open(FacilityAdapter.Item item) {
        Space space = repo.spaceOf(item.spaceId);
        if (item.equipmentId == null) {
            if (space != null) SpaceUi.open(requireContext(), space);
            return;
        }
        new AlertDialog.Builder(requireContext()).setTitle(item.title + " · " + item.location)
                .setItems(new String[]{"查看设备纸条", "发布设备纸条", "查看所在空间"}, (dialog, which) -> {
                    if (which == 1) SpaceUi.publish(requireContext(), item.spaceId, item.equipmentId);
                    else if (which == 2 && space != null) SpaceUi.open(requireContext(), space);
                    else if (which == 0) repo.notesOf(Note.T_FACILITY, item.equipmentId, rows -> {
                        if (!isAdded() || getView() == null) return;
                        StringBuilder text = new StringBuilder();
                        for (Note note : rows) text.append(note.nickname).append(" · ").append(note.category)
                                .append("\n").append(note.content).append("\n\n");
                        new AlertDialog.Builder(requireContext()).setTitle("设备纸条")
                                .setMessage(text.length() == 0 ? "这里还没有纸条" : text.toString())
                                .setPositiveButton("发布纸条", (d, w) -> SpaceUi.publish(requireContext(), item.spaceId, item.equipmentId))
                                .setNegativeButton("关闭", null).show();
                    });
                }).setNegativeButton("取消", null).show();
    }
    private int dp(int value) { return (int)(value * getResources().getDisplayMetrics().density); }
}
