package com.xjtlu.campusspace.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteConstraintException;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import com.xjtlu.campusspace.data.store.Rows;

import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.LongSupplier;

/** Local-device accounts. This does not establish a school identity or a server session. */
public final class LocalAuth {
    public interface Callback<T> {
        void onSuccess(T value);
        void onError(String message);
    }

    public static final int MIN_PASSWORD_LENGTH = 8, MAX_PASSWORD_LENGTH = 64;
    public static final int MAX_FAILED_ATTEMPTS = 5;
    public static final long LOCK_MILLIS = 60_000L;
    private static final String SESSION_KEY = "user_id";
    private static volatile LocalAuth instance;
    private final AppDatabase db;
    private final SharedPreferences preferences;
    private final LongSupplier clock;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private volatile Rows.User sessionUser;
    private boolean loaded;

    private LocalAuth(Context context) {
        this(context, AppDatabase.get(context), context.getApplicationContext()
                .getSharedPreferences("campusspace_session", Context.MODE_PRIVATE), System::currentTimeMillis);
    }

    // An isolated database and clock let instrumentation tests exercise cooldown without sleeping.
    LocalAuth(Context context, AppDatabase database, SharedPreferences preferences, LongSupplier clock) {
        this.db = database;
        this.preferences = preferences;
        this.clock = clock;
    }

    public static LocalAuth get(Context context) {
        if (instance == null) {
            synchronized (LocalAuth.class) {
                if (instance == null) instance = new LocalAuth(context.getApplicationContext());
            }
        }
        return instance;
    }

    /** Returns a detached profile, never cached password material. Call ensureLoaded first. */
    @Nullable public Rows.User currentUser() {
        Rows.User user = sessionUser;
        return user == null ? null : profile(user);
    }

    public void ensureLoaded(Runnable onReady) {
        io.execute(() -> {
            try { loadIfNeeded(); }
            catch (RuntimeException unavailable) { sessionUser = null; loaded = false; }
            main.post(() -> { if (onReady != null) onReady.run(); });
        });
    }

    /** Copies the supplied password immediately; the caller retains ownership of its buffer. */
    public void register(String username, String displayName, char[] password, boolean accepted,
                         Callback<Rows.User> callback) {
        final char[] secret = password == null ? null : password.clone();
        execute(callback, () -> {
            try {
                loadIfNeeded();
                String normalized = normalizeUsername(username), name = validateDisplayName(displayName);
                validatePassword(secret);
                if (!accepted) throw new AuthFailure("请先阅读并同意使用规则");
                if (db.dao().userByUsername(normalized) != null) throw new AuthFailure("该用户名已被注册");
                PasswordHasher.Encoded encoded = PasswordHasher.hash(secret);
                Rows.User user = new Rows.User();
                user.id = "USER-" + UUID.randomUUID();
                user.username = normalized;
                user.displayName = name;
                user.passwordHash = encoded.hash;
                user.salt = encoded.salt;
                user.passwordAlgorithm = encoded.algorithm;
                user.passwordIterations = encoded.iterations;
                user.createdAt = Math.max(1L, clock.getAsLong());
                user.rulesAcceptedAt = user.createdAt;
                try { db.dao().insertUser(user); }
                catch (SQLiteConstraintException duplicate) {
                    if (db.dao().userByUsername(normalized) != null) throw new AuthFailure("该用户名已被注册");
                    throw duplicate;
                }
                saveSession(user);
                return profile(user);
            } finally { clear(secret); }
        });
    }

    public void login(String username, char[] password, Callback<Rows.User> callback) {
        final char[] secret = password == null ? null : password.clone();
        execute(callback, () -> {
            try {
                loadIfNeeded();
                String normalized = normalizeUsername(username);
                validatePassword(secret);
                Rows.User user = db.dao().userByUsername(normalized);
                if (!registered(user)) {
                    // Keep unknown-user attempts comparable to a real password derivation.
                    PasswordHasher.hash(secret);
                    throw new AuthFailure("用户名或密码不正确");
                }
                checkLocked(user, clock.getAsLong());
                boolean matches = PasswordHasher.verify(secret, user.salt, user.passwordHash,
                        user.passwordAlgorithm, user.passwordIterations);
                final String[] failure = {null};
                final Rows.User[] authenticated = {null};
                db.runInTransaction(() -> {
                    Rows.User latest = db.dao().userById(user.id);
                    if (!registered(latest) || !sameCredential(user, latest)) {
                        throw new AuthFailure("账号信息已更新，请重新登录");
                    }
                    long now = clock.getAsLong();
                    checkLocked(latest, now);
                    if (!matches) {
                        int previous = latest.lockedUntil > 0 && latest.lockedUntil <= now ? 0 : latest.failedAttempts;
                        int attempts = Math.min(MAX_FAILED_ATTEMPTS, previous + 1);
                        long until = attempts >= MAX_FAILED_ATTEMPTS ? now + LOCK_MILLIS : 0;
                        db.dao().updateLoginState(latest.id, attempts, until);
                        failure[0] = until > now ? "登录失败次数过多，请60秒后重试" : "用户名或密码不正确";
                    } else {
                        db.dao().updateLoginState(latest.id, 0, 0);
                        latest.failedAttempts = 0;
                        latest.lockedUntil = 0;
                        authenticated[0] = latest;
                    }
                });
                // Throw after commit: throwing inside the transaction would undo the failure counter.
                if (failure[0] != null) throw new AuthFailure(failure[0]);
                saveSession(authenticated[0]);
                return profile(authenticated[0]);
            } finally { clear(secret); }
        });
    }

    public void logout(Runnable onDone) {
        sessionUser = null;
        io.execute(() -> {
            preferences.edit().remove(SESSION_KEY).commit();
            sessionUser = null;
            loaded = true;
            main.post(() -> { if (onDone != null) onDone.run(); });
        });
    }

    public void updateDisplayName(String displayName, Callback<Rows.User> callback) {
        execute(callback, () -> {
            loadIfNeeded();
            String name = validateDisplayName(displayName);
            Rows.User user = authenticatedUserForWrite();
            if (user == null) throw new AuthFailure("请先登录");
            if (db.dao().updateAccountDisplayName(user.id, name) != 1) throw new AuthFailure("账号不存在，请重新登录");
            Rows.User fresh = db.dao().userById(user.id);
            if (!registered(fresh)) throw new AuthFailure("账号不存在，请重新登录");
            sessionUser = profile(fresh);
            return profile(fresh);
        });
    }

    /** Rechecks the account for a write transaction; it never waits for the UI thread. */
    @WorkerThread @Nullable public Rows.User authenticatedUserForWrite() {
        Rows.User captured = sessionUser;
        if (captured == null) return null;
        Rows.User stored = db.dao().userById(captured.id);
        if (sessionUser != captured || !registered(stored)) return null;
        return profile(stored);
    }

    private void loadIfNeeded() {
        if (loaded) return;
        db.getOpenHelper().getWritableDatabase(); // Runs all migrations, without requiring seed data.
        String id = preferences.getString(SESSION_KEY, null);
        Rows.User user = id == null ? null : db.dao().userById(id);
        sessionUser = registered(user) ? profile(user) : null;
        if (id != null && sessionUser == null) preferences.edit().remove(SESSION_KEY).commit();
        loaded = true;
    }

    private void saveSession(Rows.User user) {
        if (!preferences.edit().putString(SESSION_KEY, user.id).commit()) {
            throw new AuthFailure("账号验证成功，但登录状态未能保存，请重新登录");
        }
        sessionUser = profile(user);
    }

    private <T> void execute(Callback<T> callback, Work<T> work) {
        io.execute(() -> {
            try {
                T value = work.run();
                main.post(() -> { if (callback != null) callback.onSuccess(value); });
            } catch (AuthFailure expected) {
                postError(callback, expected.getMessage());
            } catch (GeneralSecurityException unavailable) {
                postError(callback, "当前设备无法完成密码验证，请稍后重试");
            } catch (Exception unavailable) {
                postError(callback, "账号操作失败，请重试");
            }
        });
    }

    private <T> void postError(Callback<T> callback, String message) {
        main.post(() -> { if (callback != null) callback.onError(message); });
    }

    private static String normalizeUsername(String input) {
        String value = input == null ? "" : input.trim();
        if (!value.matches("[A-Za-z0-9_]{3,24}")) throw new AuthFailure("用户名需为3至24位字母、数字或下划线");
        return value.toLowerCase(Locale.ROOT);
    }

    private static String validateDisplayName(String input) {
        String value = input == null ? "" : input.trim();
        int length = value.codePointCount(0, value.length());
        if (length < 1 || length > 24) throw new AuthFailure("昵称需为1至24个字符");
        return value;
    }

    private static void validatePassword(char[] password) {
        if (password == null || password.length < MIN_PASSWORD_LENGTH || password.length > MAX_PASSWORD_LENGTH) {
            throw new AuthFailure("密码长度需为8至64位");
        }
    }

    private static void checkLocked(Rows.User user, long now) {
        if (user.lockedUntil > now) {
            long seconds = (user.lockedUntil - now + 999L) / 1000L;
            throw new AuthFailure("登录失败次数过多，请" + seconds + "秒后重试");
        }
    }

    private static boolean registered(@Nullable Rows.User user) {
        return user != null && user.username != null && !user.username.isEmpty() && user.passwordHash != null
                && user.salt != null && user.passwordAlgorithm != null && user.passwordIterations > 0
                && user.rulesAcceptedAt > 0;
    }

    private static boolean sameCredential(Rows.User a, Rows.User b) {
        return a.passwordHash.equals(b.passwordHash) && a.salt.equals(b.salt)
                && a.passwordAlgorithm.equals(b.passwordAlgorithm) && a.passwordIterations == b.passwordIterations;
    }

    private static Rows.User profile(Rows.User user) {
        Rows.User copy = new Rows.User();
        copy.id = user.id;
        copy.authSubject = user.authSubject;
        copy.username = user.username;
        copy.displayName = user.displayName;
        copy.createdAt = user.createdAt;
        copy.rulesAcceptedAt = user.rulesAcceptedAt;
        return copy;
    }

    private static void clear(char[] secret) { if (secret != null) Arrays.fill(secret, '\0'); }
    private interface Work<T> { T run() throws Exception; }
    private static final class AuthFailure extends RuntimeException {
        AuthFailure(String message) { super(message); }
    }
    void closeForTests() { io.shutdownNow(); }
}
