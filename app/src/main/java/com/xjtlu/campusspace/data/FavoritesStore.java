package com.xjtlu.campusspace.data;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 本机收藏。收藏只是个人标记，不参与课表/预约状态计算，
 * 因此用 SharedPreferences 保存，避免额外升级 Room 结构。
 */
public final class FavoritesStore {
    private static final String PREFS = "favorites";
    private static final String KEY_SPACES = "space_ids";

    private static volatile FavoritesStore instance;
    private final SharedPreferences prefs;

    private FavoritesStore(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static FavoritesStore get(Context context) {
        if (instance == null) {
            synchronized (FavoritesStore.class) {
                if (instance == null) instance = new FavoritesStore(context);
            }
        }
        return instance;
    }

    public synchronized Set<String> ids() {
        return new LinkedHashSet<>(prefs.getStringSet(KEY_SPACES, Collections.emptySet()));
    }

    public synchronized boolean isFavorite(String spaceId) {
        return spaceId != null && ids().contains(spaceId);
    }

    public synchronized int count() {
        return ids().size();
    }

    public synchronized boolean toggle(String spaceId) {
        Set<String> next = ids();
        boolean favorite;
        if (!next.remove(spaceId)) {
            next.add(spaceId);
            favorite = true;
        } else {
            favorite = false;
        }
        save(next);
        return favorite;
    }

    public synchronized void set(String spaceId, boolean favorite) {
        if (spaceId == null) return;
        Set<String> next = ids();
        if (favorite) next.add(spaceId); else next.remove(spaceId);
        save(next);
    }

    private void save(Set<String> ids) {
        prefs.edit().putStringSet(KEY_SPACES, new HashSet<>(ids)).apply();
    }
}
