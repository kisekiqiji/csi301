package com.xjtlu.campusspace.ui.account;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.textfield.TextInputLayout;
import com.xjtlu.campusspace.R;
import com.xjtlu.campusspace.data.LocalAuth;
import com.xjtlu.campusspace.data.store.Rows;
import java.util.Arrays;

/** Local credentials only. The Activity never receives password hashes or salts. */
public class AccountActivity extends AppCompatActivity {
    public static final String EXTRA_REGISTER = "show_registration";
    private LocalAuth auth;
    private AccountState state;
    private EditText username, display, password, confirmation;
    private MaterialCheckBox accepted;
    private MaterialButtonToggleGroup mode;
    private TextView submit, error;
    private boolean ready;

    public static final class Outcome {
        final boolean success;
        final String error;
        Outcome(boolean success, String error) { this.success = success; this.error = error; }
    }

    public static class AccountState extends ViewModel {
        final MutableLiveData<Outcome> completion = new MutableLiveData<>();
        boolean busy, registering;
        String error;

        void submit(LocalAuth auth, String username, String display, char[] password, boolean accepted) {
            if (busy) { Arrays.fill(password, '\0'); return; }
            busy = true;
            error = null;
            LocalAuth.Callback<Rows.User> callback = new LocalAuth.Callback<Rows.User>() {
                @Override public void onSuccess(Rows.User user) {
                    busy = false; completion.setValue(new Outcome(true, null));
                }
                @Override public void onError(String message) {
                    busy = false; error = message; completion.setValue(new Outcome(false, message));
                }
            };
            try {
                // LocalAuth clones the password immediately and owns the asynchronous copy.
                if (registering) auth.register(username, display, password, accepted, callback);
                else auth.login(username, password, callback);
            } catch (RuntimeException failure) {
                busy = false;
                error = "账号操作暂时失败，请重试";
                completion.setValue(new Outcome(false, error));
            } finally {
                Arrays.fill(password, '\0');
            }
        }
    }

    @Override protected void onCreate(@Nullable Bundle saved) {
        super.onCreate(saved);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        setContentView(R.layout.activity_account);
        auth = LocalAuth.get(this);
        state = new ViewModelProvider(this).get(AccountState.class);
        if (saved == null && !state.busy) state.registering = getIntent().getBooleanExtra(EXTRA_REGISTER, false);
        else if (saved != null) state.registering = saved.getBoolean("registering", state.registering);
        username = findViewById(R.id.account_username);
        display = findViewById(R.id.account_display);
        password = findViewById(R.id.account_password);
        confirmation = findViewById(R.id.account_confirm);
        accepted = findViewById(R.id.account_accept_rules);
        submit = findViewById(R.id.account_submit);
        error = findViewById(R.id.account_error);
        mode = findViewById(R.id.account_mode);
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() {
                if (state.busy) Toast.makeText(AccountActivity.this, "账号正在处理，请稍候", Toast.LENGTH_SHORT).show();
                else finish();
            }
        });
        ((MaterialToolbar)findViewById(R.id.account_toolbar)).setNavigationOnClickListener(v ->
                getOnBackPressedDispatcher().onBackPressed());
        mode.check(state.registering ? R.id.account_register_mode : R.id.account_login_mode);
        mode.addOnButtonCheckedListener((group, id, checked) -> {
            if (!checked || state.busy) return;
            state.registering = id == R.id.account_register_mode;
            state.error = null;
            password.setText(""); confirmation.setText("");
            clearFieldErrors(); updateUi();
        });
        findViewById(R.id.account_rules).setOnClickListener(v -> AccountRules.show(this));
        submit.setOnClickListener(v -> submit());
        state.completion.observe(this, result -> {
            if (result == null) return;
            state.completion.setValue(null);
            if (result.success) { setResult(RESULT_OK); finish(); }
            else { state.error = result.error; updateUi(); }
        });
        updateUi();
        auth.ensureLoaded(() -> {
            if (isFinishing() || isDestroyed()) return;
            ready = true; updateUi();
        });
    }

    private void updateUi() {
        boolean registration = state.registering;
        findViewById(R.id.account_display_wrap).setVisibility(registration ? View.VISIBLE : View.GONE);
        findViewById(R.id.account_confirm_wrap).setVisibility(registration ? View.VISIBLE : View.GONE);
        accepted.setVisibility(registration ? View.VISIBLE : View.GONE);
        boolean enabled = ready && !state.busy;
        username.setEnabled(enabled); display.setEnabled(enabled); password.setEnabled(enabled);
        confirmation.setEnabled(enabled); accepted.setEnabled(enabled);
        for (int n = 0; n < mode.getChildCount(); n++) mode.getChildAt(n).setEnabled(enabled);
        submit.setEnabled(enabled);
        submit.setText(!ready ? "正在读取本机账号…" : state.busy ? "正在处理…" : registration ? "同意规则并注册" : "登录");
        error.setText(state.error);
        error.setVisibility(state.error == null || state.error.isEmpty() ? View.GONE : View.VISIBLE);
        ((TextInputLayout)findViewById(R.id.account_password_wrap))
                .setHelperText(registration ? "8–64 位，空格也属于密码内容" : "请输入本机账号密码");
    }

    private void submit() {
        if (!ready || state.busy) return;
        clearFieldErrors(); state.error = null;
        error.setText(null); error.setVisibility(View.GONE);
        String name = text(username).trim();
        if (!name.matches("[A-Za-z0-9_]{3,24}")) {
            fieldError(R.id.account_username_wrap, "请输入 3–24 位字母、数字或下划线", username); return;
        }
        String nickname = text(display).trim();
        if (state.registering && (nickname.isEmpty() || nickname.codePointCount(0, nickname.length()) > 24)) {
            fieldError(R.id.account_display_wrap, "昵称需为 1–24 字", display); return;
        }
        char[] secret = chars(password);
        if (secret.length < 8 || secret.length > 64) {
            Arrays.fill(secret, '\0');
            fieldError(R.id.account_password_wrap, "密码需为 8–64 位", password); return;
        }
        if (state.registering) {
            char[] repeated = chars(confirmation);
            boolean matches = Arrays.equals(secret, repeated);
            Arrays.fill(repeated, '\0');
            if (!matches) {
                Arrays.fill(secret, '\0');
                fieldError(R.id.account_confirm_wrap, "两次输入的密码不一致", confirmation); return;
            }
            if (!accepted.isChecked()) {
                Arrays.fill(secret, '\0'); state.error = "注册前请阅读并勾选同意使用规则"; updateUi(); return;
            }
        }
        state.submit(auth, name, nickname, secret, accepted.isChecked());
        password.setText(""); confirmation.setText(""); updateUi();
    }

    private void clearFieldErrors() {
        for (int id : new int[]{R.id.account_username_wrap, R.id.account_display_wrap,
                R.id.account_password_wrap, R.id.account_confirm_wrap}) ((TextInputLayout)findViewById(id)).setError(null);
    }
    private void fieldError(int id, String message, EditText input) {
        ((TextInputLayout)findViewById(id)).setError(message); input.requestFocus();
    }
    private static String text(EditText input) { return input.getText() == null ? "" : input.getText().toString(); }
    private static char[] chars(EditText input) {
        CharSequence value = input.getText();
        char[] out = new char[value == null ? 0 : value.length()];
        if (value != null) TextUtils.getChars(value, 0, value.length(), out, 0);
        return out;
    }
    @Override protected void onSaveInstanceState(@NonNull Bundle out) {
        out.putBoolean("registering", state.registering);
        super.onSaveInstanceState(out);
    }
}
