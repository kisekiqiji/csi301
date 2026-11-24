package com.xjtlu.campusspace.ui.demo;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.xjtlu.campusspace.core.DemoClock;
import com.xjtlu.campusspace.data.AppDao;
import com.xjtlu.campusspace.data.AppDatabase;
import com.xjtlu.campusspace.data.Repo;
import com.xjtlu.campusspace.data.SeedLoader;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 演示时间与演示快照必须一起变化：只改时钟会让旧快照失效。
 * 这里统一执行「清除演示派生数据 → 按新时间生成纸条 → 重新生成课表/预约快照」。
 */
public final class DemoController {
    public interface Callback {
        void onDone(boolean success, String message);
    }

    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private DemoController() { }

    public static void apply(Context context, long timestamp, Callback callback) {
        DemoClock.apply(context, timestamp);
        rebuild(context, callback);
    }

    public static void restoreReal(Context context, Callback callback) {
        DemoClock.clear(context);
        rebuild(context, callback);
    }

    public static void rebuild(Context context, Callback callback) {
        final Context app = context.getApplicationContext();
        IO.execute(() -> {
            try {
                AppDatabase db = AppDatabase.get(app);
                db.runInTransaction(() -> {
                    AppDao dao = db.dao();
                    // 子表先删，sync_runs 被外键 RESTRICT 引用。
                    dao.deleteDemoEvents();
                    dao.deleteDemoSlots();
                    dao.deleteDemoSeats();
                    dao.deleteDemoRuns();
                });
                SeedLoader.rebuildDemoNotes(app, db);
                Repo repo = Repo.get(app);
                repo.reload(() -> MAIN.post(() -> {
                    if (repo.isLoaded()) callback.onDone(true, "演示数据已重建");
                    else callback.onDone(false, "演示数据重建后加载失败");
                }));
            } catch (Exception failure) {
                MAIN.post(() -> callback.onDone(false,
                        failure.getMessage() == null ? "重建演示数据失败" : failure.getMessage()));
            }
        });
    }
}
