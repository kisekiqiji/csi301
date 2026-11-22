package com.xjtlu.campusspace.ui.home;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.xjtlu.campusspace.R;
import com.xjtlu.campusspace.adapter.BuildingGridAdapter;
import com.xjtlu.campusspace.data.Repo;
import com.xjtlu.campusspace.data.entity.Building;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** A campus directory; buildings without loaded rooms remain selectable. */
public final class BuildingSelectionActivity extends AppCompatActivity {
    public static final String EXTRA_BUILDING = "building_code";
    public static final String EXTRA_PUBLIC_SCOPE = "public_scope";
    private Repo repo;
    private BuildingGridAdapter adapter;
    private TextView status;
    private boolean publicScope;
    private String selectedBuilding;

    @Override protected void onCreate(@Nullable Bundle saved) {
        super.onCreate(saved);
        setContentView(R.layout.activity_building_selection);
        repo = Repo.get(this);
        publicScope = saved == null ? getIntent().getBooleanExtra(EXTRA_PUBLIC_SCOPE, false) : saved.getBoolean(EXTRA_PUBLIC_SCOPE);
        selectedBuilding = saved == null ? getIntent().getStringExtra(EXTRA_BUILDING) : saved.getString(EXTRA_BUILDING);
        ((MaterialToolbar) findViewById(R.id.toolbar)).setNavigationOnClickListener(v -> finish());
        status = findViewById(R.id.tv_building_status);
        adapter = new BuildingGridAdapter(building -> choose(building.code));
        RecyclerView grid = findViewById(R.id.rv_buildings);
        GridLayoutManager layout = new GridLayoutManager(this, 2);
        layout.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override public int getSpanSize(int position) { return adapter.spanAt(position); }
        });
        grid.setLayoutManager(layout); grid.setAdapter(adapter);
        MaterialButtonToggleGroup toggle = findViewById(R.id.toggle_building_scope);
        toggle.check(publicScope ? R.id.btn_building_public : R.id.btn_building_rooms);
        toggle.addOnButtonCheckedListener((group, id, checked) -> {
            if (checked) { publicScope = id == R.id.btn_building_public; render(); }
        });
        findViewById(R.id.btn_all_buildings).setOnClickListener(v -> choose(null));
        status.setOnClickListener(v -> load());
        load();
    }

    private void load() {
        status.setText("正在读取楼栋目录…");
        repo.ensureLoaded(() -> {
            if (isFinishing() || isDestroyed()) return;
            render();
        });
    }

    private void render() {
        if (!repo.isLoaded()) {
            status.setText("楼栋目录加载失败，点此重试");
            findViewById(R.id.btn_all_buildings).setEnabled(false);
            return;
        }
        findViewById(R.id.btn_all_buildings).setEnabled(true);
        Map<String, List<Building>> campuses = new LinkedHashMap<>();
        for (Building building : repo.buildings) {
            String campus = building.campus == null || building.campus.isEmpty() ? "其他楼栋" : building.campus;
            campuses.computeIfAbsent(campus, ignored -> new ArrayList<>()).add(building);
        }
        List<BuildingGridAdapter.Entry> rows = new ArrayList<>();
        for (Map.Entry<String, List<Building>> campus : campuses.entrySet()) {
            rows.add(new BuildingGridAdapter.Entry(campus.getKey()));
            for (Building building : campus.getValue()) {
                rows.add(new BuildingGridAdapter.Entry(building, repo.spacesOf(building.code).size(), repo.publicSpacesOf(building.code).size()));
            }
        }
        adapter.setItems(rows, selectedBuilding, publicScope);
        status.setText("共 " + repo.buildings.size() + " 栋 · 标注数量为已加载数据，空数据楼栋也可选择");
    }

    private void choose(String code) {
        if (!repo.isLoaded()) return;
        Intent result = new Intent().putExtra(EXTRA_BUILDING, code).putExtra(EXTRA_PUBLIC_SCOPE, publicScope);
        setResult(RESULT_OK, result); finish();
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putBoolean(EXTRA_PUBLIC_SCOPE, publicScope); state.putString(EXTRA_BUILDING, selectedBuilding);
    }
}
