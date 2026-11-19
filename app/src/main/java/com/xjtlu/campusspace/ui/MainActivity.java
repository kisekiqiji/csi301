package com.xjtlu.campusspace.ui;

import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import com.xjtlu.campusspace.R;
import com.xjtlu.campusspace.data.Repo;
import com.xjtlu.campusspace.ui.booking.BookingFragment;
import com.xjtlu.campusspace.ui.facility.FacilityFragment;
import com.xjtlu.campusspace.ui.home.HomeFragment;

import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity {

    private static final String TAG_HOME = "home";
    private static final String TAG_BOOKING = "booking";
    private static final String TAG_FACILITY = "facility";

    private BottomNavigationView nav;
    private Fragment current;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        nav = findViewById(R.id.bottom_nav);
        nav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.tab_home) {
                show(TAG_HOME, HomeFragment::new);
            } else if (id == R.id.tab_booking) {
                show(TAG_BOOKING, BookingFragment::new);
            } else if (id == R.id.tab_facility) {
                show(TAG_FACILITY, FacilityFragment::new);
            }
            return true;
        });

        // 等数据灌好再显示第一个页面，避免界面先空一下再跳
        Repo.get(this).ensureLoaded(() -> {
            if (current == null) {
                // 直接 show，而不是 setSelectedItemId —— 后者在"该项本来就被选中"时
                // 不一定会回调监听器，会导致首屏空白
                show(TAG_HOME, HomeFragment::new);
                nav.getMenu().findItem(R.id.tab_home).setChecked(true);
            }
        });
    }

    private interface FragmentFactory {
        Fragment create();
    }

    private void show(String tag, FragmentFactory factory) {
        androidx.fragment.app.FragmentManager fm = getSupportFragmentManager();
        Fragment target = fm.findFragmentByTag(tag);
        if (target == null) target = factory.create();

        FragmentTransaction ft = fm.beginTransaction();
        if (current != null && current != target) ft.hide(current);
        if (target.isAdded()) {
            ft.show(target);
        } else {
            ft.add(R.id.container, target, tag);
        }
        ft.commitAllowingStateLoss();
        current = target;
    }

    /** Tab3 里"查看空间详情 >"用得到 */
    public void selectTab(int index) {
        if (nav == null) return;
        int id = index == 0 ? R.id.tab_home : index == 1 ? R.id.tab_booking : R.id.tab_facility;
        nav.setSelectedItemId(id);
    }

    public void toast(@NonNull String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }
}
