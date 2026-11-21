package com.xjtlu.campusspace.data;

import com.xjtlu.campusspace.data.store.Rows;

import java.util.Calendar;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

/** Selects complete snapshots, never individual rows from different imports. */
public final class SnapshotSelector {
    public static final long DEFAULT_MAX_AGE_MILLIS = 24L * 60 * 60 * 1000;

    private SnapshotSelector() { }

    /** A configured real timetable source takes precedence even if its import failed. */
    public static String preferredSource(List<Rows.SyncRun> runs, Map<String, Rows.DataSource> sources,
            String dataKind, String scopeKey) {
        Rows.SyncRun best = null;
        boolean bestIsReal = false;
        for (Rows.SyncRun run : runs) {
            Rows.DataSource source = sources.get(run.sourceId);
            if (source == null || !dataKind.equals(run.dataKind) || !scopeKey.equals(run.scopeKey)) continue;
            boolean real = !source.isDemo;
            if (best == null || (real && !bestIsReal) || (real == bestIsReal
                    && (run.fetchedAt > best.fetchedAt
                    || (run.fetchedAt == best.fetchedAt && run.id.compareTo(best.id) > 0)))) {
                best = run;
                bestIsReal = real;
            }
        }
        return best == null ? null : best.sourceId;
    }

    /** A null source allows the newest configured timetable source for a space. */
    public static Rows.SyncRun latestSuccessful(List<Rows.SyncRun> runs, String sourceId,
            String dataKind, String scopeKey, long start, long end, long now, long maxAge) {
        if (runs == null || end <= start || maxAge < 0) return null;
        Rows.SyncRun best = null;
        for (Rows.SyncRun run : runs) {
            if (!"SUCCESS".equals(run.status)
                    || (sourceId != null && !sourceId.equals(run.sourceId))
                    || !dataKind.equals(run.dataKind) || !scopeKey.equals(run.scopeKey)
                    || run.windowStart > start || run.windowEnd < end
                    || run.fetchedAt > now || now - run.fetchedAt > maxAge) continue;
            if (best == null || run.fetchedAt > best.fetchedAt
                    || (run.fetchedAt == best.fetchedAt && run.id.compareTo(best.id) > 0)) {
                best = run;
            }
        }
        return best;
    }

    /** Local calendar days have 23 or 25 hours in some zones; do not add 86400000. */
    public static long[] dayWindow(long now, int dayOffset, TimeZone zone) {
        Calendar calendar = Calendar.getInstance(zone);
        calendar.setTimeInMillis(now);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        calendar.add(Calendar.DATE, dayOffset);
        long start = calendar.getTimeInMillis();
        calendar.add(Calendar.DATE, 1);
        return new long[]{start, calendar.getTimeInMillis()};
    }
}
