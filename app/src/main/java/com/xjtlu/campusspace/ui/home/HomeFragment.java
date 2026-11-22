package com.xjtlu.campusspace.ui.home;

import android.app.Activity;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.content.DialogInterface;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;
import android.widget.EditText;
import android.widget.DatePicker;
import android.widget.TimePicker;
import android.widget.TextView;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.ActivityResult;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.ConcatAdapter;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.xjtlu.campusspace.R;
import com.xjtlu.campusspace.adapter.NoteAdapter;
import com.xjtlu.campusspace.adapter.SpaceAdapter;
import com.xjtlu.campusspace.core.ClassroomSearch;
import com.xjtlu.campusspace.core.StatusCalculator;
import com.xjtlu.campusspace.core.TimeProvider;
import com.xjtlu.campusspace.core.TimeText;
import com.xjtlu.campusspace.data.Repo;
import com.xjtlu.campusspace.data.entity.BookableSlot;
import com.xjtlu.campusspace.data.entity.Building;
import com.xjtlu.campusspace.data.entity.LibrarySeat;
import com.xjtlu.campusspace.data.entity.Note;
import com.xjtlu.campusspace.data.entity.Space;
import com.xjtlu.campusspace.data.store.Rows;
import com.xjtlu.campusspace.ui.SpaceUi;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;

/** Find rooms at a chosen calendar time, keeping the application clock untouched. */
public class HomeFragment extends Fragment {
    private static final TimeZone CAMPUS_ZONE = TimeZone.getTimeZone("Asia/Shanghai");
    private static final String ALL_BUILDINGS = "__ALL_BUILDINGS__";
    private Repo repo;
    private SpaceAdapter adapter;
    private NoteAdapter notes;
    private View header;
    private TextView summary, empty;
    private MaterialButton dateButton, timeButton;
    private ChipGroup buildingChips, filterChips, advancedChips;
    private Chip nowChip, tomorrowChip;
    private MaterialButtonToggleGroup toggle;
    private EditText search;
    private String building, floorCode, keyword = "";
    private boolean publicScope, whiteboard, projector, queryNow = true, binding;
    private int filter = 1, minimumCapacity, queryMinute;
    private long selectedDate;
    private List<String> floorOptions = new ArrayList<>();
    private final Runnable delayedSearch = this::refresh;

    private final ActivityResultLauncher<Intent> buildingPicker = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), this::onBuildingSelected);

    @Override public void onCreate(@Nullable Bundle state) {
        super.onCreate(state);
        if (state != null) {
            building = state.getString("building"); publicScope = state.getBoolean("public_scope");
            floorCode = state.getString("floor"); keyword = state.getString("keyword", "");
            whiteboard = state.getBoolean("whiteboard"); projector = state.getBoolean("projector");
            filter = state.getInt("filter", 1); minimumCapacity = state.getInt("capacity");
            queryNow = state.getBoolean("query_now", true); queryMinute = state.getInt("query_minute");
            selectedDate = state.getLong("query_date", ClassroomSearch.dayStart(TimeProvider.now(), 0));
        } else selectedDate = ClassroomSearch.dayStart(TimeProvider.now(), 0);
    }

    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup parent,
                                                @Nullable Bundle state) {
        return inflater.inflate(R.layout.fragment_home, parent, false);
    }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        repo = Repo.get(requireContext());
        RecyclerView list = view.findViewById(R.id.rv_spaces);
        list.setLayoutManager(new LinearLayoutManager(requireContext()));
        list.setItemAnimator(null);
        header = getLayoutInflater().inflate(R.layout.home_search_header, list, false);
        summary = header.findViewById(R.id.tv_summary); empty = header.findViewById(R.id.tv_search_empty);
        buildingChips = header.findViewById(R.id.chips_building);
        filterChips = header.findViewById(R.id.chips_filter);
        advancedChips = header.findViewById(R.id.chips_advanced_filter);
        dateButton = header.findViewById(R.id.btn_query_date); timeButton = header.findViewById(R.id.btn_query_time);
        nowChip = header.findViewById(R.id.chip_query_now); tomorrowChip = header.findViewById(R.id.chip_query_tomorrow);
        search = header.findViewById(R.id.et_room_search); search.setText(keyword);
        adapter = new SpaceAdapter(this::openSpace);
        notes = new NoteAdapter(false);
        TextView notesHeading = new TextView(requireContext());
        notesHeading.setText("最新纸条"); notesHeading.setTextSize(14); notesHeading.setTypeface(null, android.graphics.Typeface.BOLD);
        int pad = Math.round(16 * getResources().getDisplayMetrics().density);
        notesHeading.setPadding(pad, pad, pad, pad / 2);
        list.setAdapter(new ConcatAdapter(new SingleViewAdapter(header), adapter, new SingleViewAdapter(notesHeading), notes));
        toggle = header.findViewById(R.id.toggle_scope);
        syncControls();
        toggle.addOnButtonCheckedListener(this::selectScope);
        header.findViewById(R.id.btn_select_building).setOnClickListener(this::openBuildingPicker);
        dateButton.setOnClickListener(this::pickDate); timeButton.setOnClickListener(this::pickTime);
        nowChip.setOnClickListener(this::selectNow);
        tomorrowChip.setOnClickListener(this::selectTomorrow);
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                keyword = s.toString();
                if (header != null) { header.removeCallbacks(delayedSearch); header.postDelayed(delayedSearch, 180); }
            }
            @Override public void afterTextChanged(Editable editable) { }
        });
        search.setOnEditorActionListener(this::submitSearch);
        view.findViewById(R.id.fab_note).setOnClickListener(this::publishNote);
        summary.setText("正在读取校园空间…");
        load();
    }

    @Override public void onResume() { super.onResume(); if (repo != null) load(); }
    @Override public void onHiddenChanged(boolean hidden) { super.onHiddenChanged(hidden); if (!hidden && repo != null) load(); }

    private void load() {
        repo.ensureLoaded(this::onLoaded);
    }

    private void onLoaded() {
        if (!isAdded() || getView() == null) return;
        if (!repo.isLoaded()) {
            summary.setText("空间数据加载失败，点击此处重试");
            summary.setOnClickListener(this::retryLoad);
            return;
        }
        summary.setOnClickListener(null); syncControls(); refresh();
        repo.latestNotes(6, this::showLatestNotes);
    }

    private void showLatestNotes(List<Note> rows) {
        if (isAdded() && getView() != null) notes.setItems(rows);
    }

    private void retryLoad(View view) { load(); }

    private void onBuildingSelected(ActivityResult result) {
        if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null) return;
        Intent data = result.getData();
        building = data.getStringExtra(BuildingSelectionActivity.EXTRA_BUILDING);
        boolean nextScope = data.getBooleanExtra(BuildingSelectionActivity.EXTRA_PUBLIC_SCOPE, false);
        if (publicScope != nextScope) filter = nextScope ? 0 : 1;
        publicScope = nextScope; floorCode = null;
        if (getView() != null) { syncControls(); refresh(); }
    }

    private void openSpace(Space space) {
        search.clearFocus();
        InputMethodManager keyboard = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (keyboard != null) keyboard.hideSoftInputFromWindow(search.getWindowToken(), 0);
        SpaceUi.open(requireContext(), space, selectedOffset(), selectedMinute());
    }

    private void openBuildingPicker(View view) {
        buildingPicker.launch(new Intent(requireContext(), BuildingSelectionActivity.class)
                .putExtra(BuildingSelectionActivity.EXTRA_BUILDING, building)
                .putExtra(BuildingSelectionActivity.EXTRA_PUBLIC_SCOPE, publicScope));
    }

    private void selectScope(MaterialButtonToggleGroup group, int id, boolean checked) {
        if (!checked || binding) return;
        publicScope = id == R.id.btn_scope_public; filter = publicScope ? 0 : 1;
        floorCode = null; syncControls(); refresh();
    }

    private void selectNow(View view) {
        queryNow = true; filter = 1; syncControls(); refresh();
    }

    private void selectTomorrow(View view) {
        queryNow = false; selectedDate = ClassroomSearch.dayStart(TimeProvider.now(), 1); queryMinute = 840;
        filter = 1; syncControls(); refresh();
    }

    private boolean submitSearch(TextView view, int action, KeyEvent event) {
        if (action != EditorInfo.IME_ACTION_SEARCH) return false;
        InputMethodManager keyboard = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (keyboard != null) keyboard.hideSoftInputFromWindow(search.getWindowToken(), 0);
        search.clearFocus(); header.removeCallbacks(delayedSearch); refresh(); return true;
    }

    private void publishNote(View view) { SpaceUi.publish(requireContext(), null, null); }

    private void selectBuilding(View view) {
        String code = (String) view.getTag();
        building = ALL_BUILDINGS.equals(code) ? null : code;
        floorCode = null; syncControls(); refresh();
    }

    private void selectFilter(View view) {
        filter = (Integer) view.getTag(); buildFilters(); refresh();
    }

    private void selectCapacity(View view) {
        int capacity = (Integer) view.getTag();
        minimumCapacity = minimumCapacity == capacity ? 0 : capacity; buildAdvanced(); refresh();
    }

    private void selectWhiteboard(View view) { whiteboard = !whiteboard; buildAdvanced(); refresh(); }
    private void selectProjector(View view) { projector = !projector; buildAdvanced(); refresh(); }
    private void resetConditions(View view) {
        minimumCapacity = 0; whiteboard = projector = false; floorCode = null; buildAdvanced(); refresh();
    }

    private void syncControls() {
        if (header == null) return;
        binding = true;
        toggle.check(publicScope ? R.id.btn_scope_public : R.id.btn_scope_room);
        nowChip.setChecked(queryNow);
        tomorrowChip.setChecked(!queryNow && selectedOffset() == 1 && queryMinute == 840);
        dateButton.setText(dateLabel() + "  ▾");
        timeButton.setText(TimeText.hhmm(selectedMinute()) + (queryNow ? " · 现在" : "") + "  ▾");
        buildBuildings(); buildFilters(); buildAdvanced();
        binding = false;
    }

    private void buildBuildings() {
        buildingChips.removeAllViews();
        addBuildingChip(null, "全部楼栋");
        Set<String> quick = new LinkedHashSet<>();
        for (String code : new String[]{"FB", "CB", "SA", "LIB"}) {
            for (Building row : repo.buildings) if (code.equals(row.code)) quick.add(code);
        }
        if (building != null) quick.add(building);
        for (String code : quick) addBuildingChip(code, code);
    }

    private void addBuildingChip(String code, String label) {
        Chip chip = chip(label, code == null ? building == null : code.equals(building));
        chip.setTag(code == null ? ALL_BUILDINGS : code);
        chip.setOnClickListener(this::selectBuilding);
        buildingChips.addView(chip);
    }

    private void buildFilters() {
        filterChips.removeAllViews();
        String[] labels = {"全部状态", "该时刻空闲", "即将上课"};
        for (int i = 0; i < labels.length; i++) {
            Chip chip = chip(labels[i], filter == i);
            chip.setTag(i); chip.setOnClickListener(this::selectFilter);
            filterChips.addView(chip);
        }
    }

    private void buildAdvanced() {
        advancedChips.removeAllViews();
        Chip capacity20 = chip("20人+", minimumCapacity == 20);
        capacity20.setTag(20); capacity20.setOnClickListener(this::selectCapacity);
        Chip capacity50 = chip("50人+", minimumCapacity == 50);
        capacity50.setTag(50); capacity50.setOnClickListener(this::selectCapacity);
        Chip board = chip("白板", whiteboard);
        board.setOnClickListener(this::selectWhiteboard);
        Chip projection = chip("投影", projector);
        projection.setOnClickListener(this::selectProjector);
        Chip floors = chip((floorCode == null ? "全部楼层" : floorCode) + " ▾", floorCode != null);
        floors.setCheckable(false); floors.setOnClickListener(this::pickFloor);
        Chip clear = chip("重置条件", false); clear.setCheckable(false);
        clear.setOnClickListener(this::resetConditions);
        for (Chip chip : new Chip[]{capacity20, capacity50, board, projection, floors, clear}) advancedChips.addView(chip);
    }

    private Chip chip(String label, boolean checked) {
        Chip chip = new Chip(requireContext());
        chip.setText(label); chip.setCheckable(true); chip.setChecked(checked); return chip;
    }

    private void pickDate(View view) {
        Calendar calendar = Calendar.getInstance(CAMPUS_ZONE);
        calendar.setTimeInMillis(queryNow ? TimeProvider.now() : selectedDate);
        new DatePickerDialog(requireContext(), this::selectDate, calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH), calendar.get(Calendar.DATE)).show();
    }

    private void selectDate(DatePicker picker, int year, int month, int day) {
        Calendar selected = Calendar.getInstance(CAMPUS_ZONE); selected.clear(); selected.set(year, month, day);
        if (queryNow) queryMinute = TimeProvider.minuteOfDay();
        selectedDate = selected.getTimeInMillis(); queryNow = false; syncControls(); refresh();
    }

    private void pickTime(View view) {
        int minute = selectedMinute();
        new TimePickerDialog(requireContext(), this::selectTime, minute / 60, minute % 60, true).show();
    }

    private void selectTime(TimePicker picker, int hour, int min) {
        if (queryNow) selectedDate = ClassroomSearch.dayStart(TimeProvider.now(), 0);
        queryMinute = hour * 60 + min; queryNow = false; syncControls(); refresh();
    }

    private void pickFloor(View view) {
        Set<String> codes = new LinkedHashSet<>();
        for (Building row : repo.buildings) {
            if (building != null && !building.equals(row.code)) continue;
            for (Rows.Floor floor : repo.floorsOf(row.code)) codes.add(floor.code);
        }
        List<String> options = new ArrayList<>(); options.add("全部楼层"); options.addAll(codes);
        floorOptions = options;
        int selected = floorCode == null ? 0 : Math.max(0, options.indexOf(floorCode));
        new AlertDialog.Builder(requireContext()).setTitle("筛选楼层")
                .setSingleChoiceItems(options.toArray(new String[0]), selected, this::selectFloor)
                .setNegativeButton("取消", null).show();
    }

    private void selectFloor(DialogInterface dialog, int which) {
        floorCode = which == 0 ? null : floorOptions.get(which); dialog.dismiss(); buildAdvanced(); refresh();
    }

    private void refresh() {
        if (repo == null || getView() == null || !repo.isLoaded()) return;
        if (repo.pendingBuilding != null) {
            building = repo.pendingBuilding; repo.pendingBuilding = null;
            publicScope = true; filter = 0; floorCode = null; syncControls();
        }
        dateButton.setText(dateLabel() + "  ▾");
        timeButton.setText(TimeText.hhmm(selectedMinute()) + (queryNow ? " · 现在" : "") + "  ▾");
        ClassroomSearch.Filters filters = new ClassroomSearch.Filters();
        filters.building = building; filters.keyword = keyword; filters.minimumCapacity = minimumCapacity; filters.floorCode = floorCode;
        if (whiteboard) filters.facilities.add("白板"); if (projector) filters.facilities.add("投影");
        filters.status = filter == 1 ? ClassroomSearch.StatusFilter.FREE : filter == 2 ? ClassroomSearch.StatusFilter.SOON : ClassroomSearch.StatusFilter.ALL;
        List<Space> spaces = publicScope ? repo.publicSpacesOf(building) : repo.spacesOf(building);
        final int day = selectedOffset(), minute = selectedMinute();
        ClassroomSearch.Results results = ClassroomSearch.search(spaces, filters, space -> stateAt(space, day, minute));
        List<SpaceAdapter.Item> items = new ArrayList<>();
        for (ClassroomSearch.Match match : results.matches) {
            String subtitle = SpaceUi.subtitle(match.space) + " · " + dateLabel() + " " + TimeText.hhmm(minute);
            if (match.state == StatusCalculator.State.SOON) {
                com.xjtlu.campusspace.data.entity.Lecture next = StatusCalculator.nextLecture(repo.lecturesOn(match.space.id, day), minute);
                if (next != null) subtitle += " · 可用至 " + TimeText.hhmm(next.startMin);
            }
            items.add(new SpaceAdapter.Item(match.space, match.state, subtitle));
        }
        adapter.setItems(items);
        String scope = publicScope ? "公共空间" : "教室";
        String message = (building == null ? "全部楼栋" : building) + " · " + dateLabel() + " " + TimeText.hhmm(minute)
                + "\n符合条件 " + results.candidateCount + " 处 · 该时刻空闲 " + results.freeCount + " 处";
        if (results.unknownCount > 0) message += " · " + results.unknownCount + " 处状态未知";
        if ((day < -1 || day > 7) && results.unknownCount > 0) message += "\n超出演示课表范围（昨天至未来7天），无有效数据不计入空闲。";
        if (publicScope && !queryNow) message += "\n座位仅有当前快照，指定时刻无数据时显示未知。";
        summary.setText(message);
        empty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
        if (items.isEmpty()) {
            if (spaces.isEmpty()) empty.setText("此楼栋暂无" + scope + "数据。\n可从「选择楼栋」切换，或试试全部楼栋。");
            else if (results.candidateCount == 0) empty.setText("没有符合名称、容量、设施或楼层条件的" + scope + "。\n试试清除搜索词或重置条件。");
            else if (results.unknownCount == results.candidateCount) empty.setText("所选日期或时刻暂无有效状态，无法确认空闲。\n切换「全部状态」可查看空间；也可以改选其他日期。");
            else empty.setText("所选时刻没有符合筛选条件的" + scope + "。\n试试其他时间或切换全部状态。");
        }
    }

    private StatusCalculator.State stateAt(Space space, int day, int minute) {
        if ("CLASSROOM".equals(space.type) || space.isDiscussionArea() || repo.hasSchedule(space.id, day)) {
            return ClassroomSearch.stateAt(repo.lecturesOn(space.id, day), minute, repo.hasSchedule(space.id, day));
        }
        Rows.BookingGroup group = repo.bookingGroupForSpace(space.id);
        if (group == null) return StatusCalculator.State.UNKNOWN;
        if ("SLOTS".equals(group.displayMode)) {
            boolean busy = false, unknown = false, found = false;
            for (Rows.BookingResource resource : repo.bookingResources(group.id)) {
                if (!space.id.equals(resource.spaceId)) continue;
                if (!repo.hasBookingSnapshot(resource.id, day)) { unknown = true; continue; }
                for (BookableSlot slot : repo.slotsOf(resource.id, day)) {
                    if (minute < slot.startMin || minute >= slot.endMin) continue;
                    found = true;
                    if (BookableSlot.FREE.equals(slot.status)) return StatusCalculator.State.FREE;
                    if (BookableSlot.BOOKED.equals(slot.status)) busy = true; else unknown = true;
                }
            }
            return found && busy && !unknown ? StatusCalculator.State.IN_CLASS : StatusCalculator.State.UNKNOWN;
        }
        if ("SEATS".equals(group.displayMode) && queryNow) {
            boolean unknown = false; int count = 0;
            for (LibrarySeat seat : repo.seatsOfGroup(group.id, day)) {
                if (!space.id.equals(seat.spaceId)) continue;
                count++;
                if (LibrarySeat.AVAILABLE.equals(seat.status) || LibrarySeat.PART_FREE.equals(seat.status)) return StatusCalculator.State.FREE;
                if (LibrarySeat.UNKNOWN.equals(seat.status)) unknown = true;
            }
            if (count > 0 && !unknown) return StatusCalculator.State.IN_CLASS;
        }
        return StatusCalculator.State.UNKNOWN;
    }

    private int selectedOffset() { return queryNow ? 0 : ClassroomSearch.dayOffset(selectedDate, TimeProvider.now()); }
    private int selectedMinute() { return queryNow ? TimeProvider.minuteOfDay() : queryMinute; }
    private String dateLabel() {
        long date = queryNow ? TimeProvider.now() : selectedDate;
        SimpleDateFormat format = new SimpleDateFormat("M月d日 E", Locale.CHINA); format.setTimeZone(CAMPUS_ZONE);
        return format.format(new Date(date));
    }

    @Override public void onSaveInstanceState(@NonNull Bundle state) {
        super.onSaveInstanceState(state);
        state.putString("building", building); state.putBoolean("public_scope", publicScope);
        state.putString("floor", floorCode); state.putString("keyword", keyword); state.putInt("filter", filter);
        state.putInt("capacity", minimumCapacity); state.putBoolean("whiteboard", whiteboard); state.putBoolean("projector", projector);
        state.putBoolean("query_now", queryNow); state.putLong("query_date", selectedDate); state.putInt("query_minute", queryMinute);
    }

    @Override public void onDestroyView() {
        if (header != null) header.removeCallbacks(delayedSearch);
        header = null; super.onDestroyView();
    }

    private static final class SingleViewAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private final View view;
        SingleViewAdapter(View view) { this.view = view; }
        @NonNull @Override public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
            return new RecyclerView.ViewHolder(view) { };
        }
        @Override public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) { }
        @Override public int getItemCount() { return 1; }
    }
}
