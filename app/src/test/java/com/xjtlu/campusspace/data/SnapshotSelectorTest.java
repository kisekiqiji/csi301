package com.xjtlu.campusspace.data;

import com.xjtlu.campusspace.data.store.Rows;

import org.junit.Test;

import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;
import java.util.TimeZone;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

public class SnapshotSelectorTest {
    private Rows.SyncRun run(String id, long fetchedAt, String status) {
        Rows.SyncRun run = new Rows.SyncRun();
        run.id = id;
        run.sourceId = "SOURCE";
        run.dataKind = "SLOTS";
        run.scopeKey = "GROUP";
        run.windowStart = 100;
        run.windowEnd = 300;
        run.fetchedAt = fetchedAt;
        run.status = status;
        return run;
    }

    private Rows.SyncRun select(Rows.SyncRun... runs) {
        return SnapshotSelector.latestSuccessful(Arrays.asList(runs), "SOURCE", "SLOTS",
                "GROUP", 100, 300, 1000, 500);
    }

    @Test public void failedImportDoesNotReplaceLastCompleteSuccess() {
        Rows.SyncRun success = run("A", 700, "SUCCESS");
        assertSame(success, select(success, run("B", 990, "FAILED")));
    }

    @Test public void newestSuccessfulBatchIsUsedEvenWhenItContainsNoChildRows() {
        Rows.SyncRun newest = run("B", 900, "SUCCESS");
        assertSame(newest, select(run("A", 700, "SUCCESS"), newest));
    }

    @Test public void missingCoverageExpiredAndWrongScopeAreUnknown() {
        Rows.SyncRun partial = run("PARTIAL", 900, "SUCCESS");
        partial.windowEnd = 299;
        Rows.SyncRun otherScope = run("OTHER", 900, "SUCCESS");
        otherScope.scopeKey = "OTHER_GROUP";
        Rows.SyncRun otherSource = run("OTHER_SOURCE", 900, "SUCCESS");
        otherSource.sourceId = "OTHER";
        Rows.SyncRun otherKind = run("OTHER_KIND", 900, "SUCCESS");
        otherKind.dataKind = "SEATS";
        assertNull(select(partial, otherScope, otherSource, otherKind,
                run("EXPIRED", 499, "SUCCESS"), run("FUTURE", 1001, "SUCCESS")));
    }

    @Test public void calendarWindowsRespectDaylightSavingAndYearBoundary() {
        TimeZone zone = TimeZone.getTimeZone("America/New_York");
        Calendar calendar = Calendar.getInstance(zone);
        calendar.clear();
        calendar.set(2026, Calendar.MARCH, 8, 12, 0);
        long[] spring = SnapshotSelector.dayWindow(calendar.getTimeInMillis(), 0, zone);
        assertEquals(23L * 60 * 60 * 1000, spring[1] - spring[0]);

        calendar.clear();
        calendar.set(2026, Calendar.DECEMBER, 31, 12, 0);
        long[] next = SnapshotSelector.dayWindow(calendar.getTimeInMillis(), 1, zone);
        calendar.setTimeInMillis(next[0]);
        assertEquals(2027, calendar.get(Calendar.YEAR));
        assertEquals(Calendar.JANUARY, calendar.get(Calendar.MONTH));
        assertEquals(1, calendar.get(Calendar.DAY_OF_MONTH));
    }

    @Test public void freshDemoDoesNotMaskFailedOrExpiredRealSource() {
        Rows.DataSource demo = new Rows.DataSource();
        demo.id = "DEMO";
        demo.isDemo = true;
        Rows.DataSource real = new Rows.DataSource();
        real.id = "SOURCE";
        Map<String, Rows.DataSource> sources = new HashMap<>();
        sources.put(demo.id, demo);
        sources.put(real.id, real);
        Rows.SyncRun freshDemo = run("DEMO_RUN", 950, "SUCCESS");
        freshDemo.sourceId = demo.id;
        Rows.SyncRun failedReal = run("REAL_FAILED", 900, "FAILED");
        String preferred = SnapshotSelector.preferredSource(Arrays.asList(freshDemo, failedReal),
                sources, "SLOTS", "GROUP");
        assertEquals(real.id, preferred);
        assertNull(SnapshotSelector.latestSuccessful(Arrays.asList(freshDemo, failedReal), preferred,
                "SLOTS", "GROUP", 100, 300, 1000, 500));
    }
}
