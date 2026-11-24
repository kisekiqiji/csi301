package com.xjtlu.campusspace.ui.account;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.textfield.TextInputLayout;
import com.xjtlu.campusspace.R;
import com.xjtlu.campusspace.adapter.NoteAdapter;
import com.xjtlu.campusspace.data.LocalAuth;
import com.xjtlu.campusspace.data.Repo;
import com.xjtlu.campusspace.data.FavoritesStore;
import com.xjtlu.campusspace.data.store.Rows;
import com.xjtlu.campusspace.ui.demo.DemoConsoleActivity;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/** Current-account queries stay behind Repo; no credential columns are read by the UI. */
public class ProfileFragment extends Fragment {
    private LocalAuth auth;
    private Repo repo;
    private ProfileState state;
    private NoteAdapter notes;
    private TextView status, count;
    private AlertDialog nameDialog;
    private int generation;

    private final ActivityResultLauncher<Intent> account = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> refresh());

    public static final class Outcome {
        final String message;
        final boolean success;
        Outcome(boolean success, String message) { this.success = success; this.message = message; }
    }

    public static class ProfileState extends ViewModel {
        final MutableLiveData<Outcome> completion = new MutableLiveData<>();
        boolean busy;
        void rename(LocalAuth auth, String displayName) {
            if (busy) return;
            busy = true;
            auth.updateDisplayName(displayName, new LocalAuth.Callback<Rows.User>() {
                @Override public void onSuccess(Rows.User user) {
                    busy = false; completion.setValue(new Outcome(true, "昵称已更新"));
                }
                @Override public void onError(String error) {
                    busy = false; completion.setValue(new Outcome(false, error));
                }
            });
        }
        void logout(LocalAuth auth) {
            if (busy) return;
            busy = true;
            auth.logout(() -> { busy = false; completion.setValue(new Outcome(true, "已退出本机账号")); });
        }
    }

    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup parent,
                                                @Nullable Bundle saved) {
        return inflater.inflate(R.layout.fragment_profile, parent, false);
    }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle saved) {
        auth = LocalAuth.get(requireContext());
        repo = Repo.get(requireContext());
        state = new ViewModelProvider(this).get(ProfileState.class);
        status = view.findViewById(R.id.profile_status);
        count = view.findViewById(R.id.profile_note_count);
        RecyclerView list = view.findViewById(R.id.profile_notes);
        list.setLayoutManager(new LinearLayoutManager(requireContext()));
        notes = new NoteAdapter(true); list.setAdapter(notes);
        view.findViewById(R.id.profile_sign_in).setOnClickListener(v ->
                account.launch(new Intent(requireContext(), AccountActivity.class)));
        view.findViewById(R.id.profile_rules).setOnClickListener(v -> AccountRules.show(requireContext()));
        view.findViewById(R.id.profile_favorites).setOnClickListener(v ->
                startActivity(new Intent(requireContext(), FavoritesActivity.class)));
        view.findViewById(R.id.profile_demo).setOnClickListener(v ->
                startActivity(new Intent(requireContext(), DemoConsoleActivity.class)));
        view.findViewById(R.id.profile_edit_name).setOnClickListener(v -> editName());
        view.findViewById(R.id.profile_logout).setOnClickListener(v -> {
            if (state.busy) return;
            state.logout(auth); updateBusy();
        });
        state.completion.observe(getViewLifecycleOwner(), result -> {
            if (result == null) return;
            state.completion.setValue(null);
            if (!isAdded() || getView() == null) return;
            updateBusy();
            Toast.makeText(requireContext(), result.message == null ? "操作失败，请重试" : result.message,
                    Toast.LENGTH_SHORT).show();
            if (result.success) refresh();
            else {
                status.setText(result.message); status.setVisibility(View.VISIBLE);
                status.setTextColor(0xFFB42318);
            }
        });
        refresh();
    }

    @Override public void onResume() { super.onResume(); refresh(); }
    @Override public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden); if (!hidden) refresh();
    }

    private void refresh() {
        if (auth == null || getView() == null || !isAdded()) return;
        ((TextView) getView().findViewById(R.id.profile_favorites))
                .setText("我的收藏 · " + FavoritesStore.get(requireContext()).count());
        final int request = ++generation;
        auth.ensureLoaded(() -> {
            if (!active(request)) return;
            Rows.User user = auth.currentUser();
            bindUser(user);
            if (user == null) return;
            count.setText("已发布纸条：正在读取…");
            status.setText("正在读取我的纸条…"); status.setTextColor(0xFF6B7280);
            status.setVisibility(View.VISIBLE); status.setOnClickListener(null);
            repo.ensureLoaded(() -> {
                if (!sameUser(request, user.id)) return;
                if (!repo.isLoaded()) {
                    status.setText("暂时无法读取纸条，点击重试");
                    count.setText("已发布纸条：暂时无法读取");
                    status.setOnClickListener(v -> refresh()); return;
                }
                repo.myNotes(rows -> {
                    if (!sameUser(request, user.id)) return;
                    notes.setItems(rows);
                    status.setText(rows.isEmpty() ? "你还没有发布纸条。可以从具体空间或设备页面发布。" : "这里只显示当前本机账号发布的纸条。");
                    status.setVisibility(View.VISIBLE);
                });
                repo.noteCountForCurrentUser(total -> {
                    if (sameUser(request, user.id)) count.setText("已发布纸条：" + total + " 条");
                });
            });
        });
    }

    private void bindUser(@Nullable Rows.User user) {
        View view = getView();
        if (view == null) return;
        boolean signedIn = user != null;
        view.findViewById(R.id.profile_guest).setVisibility(signedIn ? View.GONE : View.VISIBLE);
        view.findViewById(R.id.profile_account).setVisibility(signedIn ? View.VISIBLE : View.GONE);
        view.findViewById(R.id.profile_notes_title).setVisibility(signedIn ? View.VISIBLE : View.GONE);
        view.findViewById(R.id.profile_notes).setVisibility(signedIn ? View.VISIBLE : View.GONE);
        notes.setItems(Collections.emptyList());
        status.setOnClickListener(null);
        status.setTextColor(0xFF6B7280);
        if (!signedIn) {
            status.setText("无需提供学号、手机号或邮箱。注册时需要阅读并同意使用规则。");
            status.setVisibility(View.VISIBLE); return;
        }
        ((TextView)view.findViewById(R.id.profile_display_name)).setText(user.displayName);
        ((TextView)view.findViewById(R.id.profile_username)).setText("账号名：" + user.username);
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA);
        format.setTimeZone(TimeZone.getTimeZone("Asia/Shanghai"));
        ((TextView)view.findViewById(R.id.profile_created)).setText("注册时间："
                + (user.createdAt > 0 ? format.format(new Date(user.createdAt)) : "未记录"));
        updateBusy();
    }

    private void updateBusy() {
        View view = getView();
        if (view == null || state == null) return;
        view.findViewById(R.id.profile_edit_name).setEnabled(!state.busy);
        view.findViewById(R.id.profile_logout).setEnabled(!state.busy);
        ((TextView)view.findViewById(R.id.profile_edit_name)).setText(state.busy ? "正在处理…" : "修改昵称");
    }

    private void editName() {
        Rows.User user = auth.currentUser();
        if (user == null || state.busy || nameDialog != null) return;
        View inputView = getLayoutInflater().inflate(R.layout.dialog_display_name, null);
        EditText input = inputView.findViewById(R.id.profile_name_input);
        TextInputLayout wrapper = inputView.findViewById(R.id.profile_name_wrap);
        input.setText(user.displayName); input.setSelection(input.length());
        nameDialog = new AlertDialog.Builder(requireContext()).setTitle("修改昵称").setView(inputView)
                .setPositiveButton("保存", null).setNegativeButton("取消", null).create();
        nameDialog.setOnDismissListener(dialog -> nameDialog = null);
        nameDialog.setOnShowListener(dialog -> nameDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String name = input.getText() == null ? "" : input.getText().toString().trim();
            if (name.isEmpty() || name.codePointCount(0, name.length()) > 24) {
                wrapper.setError("昵称需为 1–24 字"); return;
            }
            if (state.busy) return;
            state.rename(auth, name); updateBusy();
            nameDialog.dismiss();
        }));
        nameDialog.show();
    }

    private boolean active(int request) { return isAdded() && getView() != null && request == generation; }
    private boolean sameUser(int request, String userId) {
        Rows.User current = auth.currentUser();
        return active(request) && current != null && userId.equals(current.id);
    }
    @Override public void onDestroyView() {
        generation++;
        if (nameDialog != null) nameDialog.dismiss();
        super.onDestroyView();
    }
}
