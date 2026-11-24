package com.xjtlu.campusspace.ui.account;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.xjtlu.campusspace.R;
import com.xjtlu.campusspace.adapter.SpaceAdapter;
import com.xjtlu.campusspace.core.StatusCalculator;
import com.xjtlu.campusspace.data.FavoritesStore;
import com.xjtlu.campusspace.data.Repo;
import com.xjtlu.campusspace.data.entity.Space;
import com.xjtlu.campusspace.ui.SpaceUi;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/** 本机收藏列表，状态仍按当前时间实时计算。 */
public class FavoritesActivity extends AppCompatActivity {
    private Repo repo;
    private SpaceAdapter adapter;
    private TextView empty;

    @Override protected void onCreate(@Nullable Bundle saved) {
        super.onCreate(saved);
        setContentView(R.layout.activity_favorites);
        ((MaterialToolbar) findViewById(R.id.toolbar)).setNavigationOnClickListener(v -> finish());
        empty = findViewById(R.id.empty);
        RecyclerView list = findViewById(R.id.rv);
        list.setLayoutManager(new LinearLayoutManager(this));
        adapter = new SpaceAdapter(this::open, this::toggleFavorite);
        list.setAdapter(adapter);
        repo = Repo.get(this);
        load();
    }

    @Override protected void onResume() {
        super.onResume();
        if (repo != null) load();
    }

    private void load() {
        repo.ensureLoaded(() -> {
            if (isFinishing() || isDestroyed()) return;
            render();
        });
    }

    private void render() {
        if (!repo.isLoaded()) {
            adapter.setItems(new ArrayList<>());
            empty.setText("空间数据加载失败，点击重试");
            empty.setOnClickListener(v -> load());
            empty.setVisibility(View.VISIBLE);
            return;
        }
        empty.setOnClickListener(null);
        Set<String> ids = FavoritesStore.get(this).ids();
        List<SpaceAdapter.Item> items = new ArrayList<>();
        for (String id : ids) {
            Space space = repo.spaceOf(id);
            if (space == null) continue;
            StatusCalculator.State state = SpaceUi.state(repo, space);
            items.add(new SpaceAdapter.Item(space, state,
                    SpaceUi.subtitle(space) + " · " + stateLabel(space, state)));
        }
        items.sort(Comparator.comparing((SpaceAdapter.Item item) -> item.space.building)
                .thenComparingInt(item -> item.space.floor)
                .thenComparing(item -> item.space.name));
        adapter.setFavoriteIds(ids);
        adapter.setItems(items);
        empty.setText("还没有收藏的空间。\n在教室或空间详情页点击 ☆ 收藏。");
        empty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void toggleFavorite(Space space, boolean favorite) {
        FavoritesStore.get(this).set(space.id, favorite);
        render();
        Toast.makeText(this, favorite ? "已收藏 " + space.name : "已取消收藏 " + space.name,
                Toast.LENGTH_SHORT).show();
    }

    private void open(Space space) {
        SpaceUi.open(this, space);
    }

    private String stateLabel(Space space, StatusCalculator.State state) {
        boolean room = "CLASSROOM".equals(space.type) || space.isDiscussionArea();
        if (state == StatusCalculator.State.IN_CLASS) return room ? "上课中" : "暂无空余";
        if (state == StatusCalculator.State.SOON) return "即将上课";
        if (state == StatusCalculator.State.FREE) return room ? "空闲中" : "有空余";
        return "状态未知";
    }
}
