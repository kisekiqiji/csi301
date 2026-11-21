package com.xjtlu.campusspace.ui;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.Lifecycle;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.xjtlu.campusspace.R;
import com.xjtlu.campusspace.data.Repo;
import com.xjtlu.campusspace.ui.booking.BookingFragment;
import com.xjtlu.campusspace.ui.facility.FacilityFragment;
import com.xjtlu.campusspace.ui.home.HomeFragment;

public class MainActivity extends AppCompatActivity {
    private BottomNavigationView nav;
    private TextView dataLabel, loadState;
    private Repo repo;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_main);
        repo = Repo.get(this);
        nav = findViewById(R.id.bottom_nav);
        dataLabel = findViewById(R.id.tv_data_label);
        loadState = findViewById(R.id.tv_load_state);
        nav.setOnItemSelectedListener(item -> {
            if (!repo.isLoaded()) return false;
            show(item.getItemId());
            return true;
        });
        loadState.setOnClickListener(v -> load());
        load();
    }

    private void load() {
        for (int n = 0; n < nav.getMenu().size(); n++) nav.getMenu().getItem(n).setEnabled(false);
        dataLabel.setText("正在加载校园空间数据…");
        loadState.setVisibility(View.GONE);
        repo.ensureLoaded(() -> {
            if (isFinishing() || isDestroyed()) return;
            String error = repo.getLoadError();
            if (error != null || !repo.isLoaded()) {
                dataLabel.setText("数据加载失败");
                loadState.setText((error == null ? "暂时无法读取校园空间数据" : error) + "\n点击重试");
                loadState.setVisibility(View.VISIBLE);
                return;
            }
            dataLabel.setText(repo.dataLabel());
            for (int n = 0; n < nav.getMenu().size(); n++) nav.getMenu().getItem(n).setEnabled(true);
            show(nav.getSelectedItemId());
        });
    }

    private void show(int id) {
        String tag = id == R.id.tab_booking ? "booking" : id == R.id.tab_facility ? "facility" : "home";
        Fragment target = getSupportFragmentManager().findFragmentByTag(tag);
        if (target == null) target = "booking".equals(tag) ? new BookingFragment()
                : "facility".equals(tag) ? new FacilityFragment() : new HomeFragment();
        FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
        for (Fragment fragment : getSupportFragmentManager().getFragments()) {
            if (fragment != target && fragment.isAdded()) {
                ft.hide(fragment);
                ft.setMaxLifecycle(fragment, Lifecycle.State.STARTED);
            }
        }
        if (!target.isAdded()) ft.add(R.id.container, target, tag);
        else ft.show(target);
        ft.setMaxLifecycle(target, Lifecycle.State.RESUMED);
        ft.commitNowAllowingStateLoss();
    }

    @Override protected void onResume() {
        super.onResume();
        if (repo != null && repo.isLoaded()) repo.ensureLoaded(() -> {
            if (!isFinishing() && !isDestroyed()) dataLabel.setText(repo.dataLabel());
        });
    }

    public void selectTab(int index) {
        nav.setSelectedItemId(index == 0 ? R.id.tab_home : index == 1 ? R.id.tab_booking : R.id.tab_facility);
    }

    public void toast(@NonNull String message) { Toast.makeText(this, message, Toast.LENGTH_SHORT).show(); }
}
