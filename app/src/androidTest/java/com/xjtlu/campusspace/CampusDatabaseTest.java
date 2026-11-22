package com.xjtlu.campusspace;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import androidx.annotation.NonNull;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.xjtlu.campusspace.data.*;
import com.xjtlu.campusspace.data.store.Rows;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

/** Exercises SQLite constraints and the actual Room migration validator on Android. */
@RunWith(AndroidJUnit4.class)
public class CampusDatabaseTest {
    private Context context;
    private AppDatabase db;
    private final String migrationName = "campus-migration-test.db";

    @Before public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase.class).allowMainThreadQueries()
                .addCallback(new RoomDatabase.Callback() {
                    @Override public void onCreate(@NonNull SupportSQLiteDatabase sql) { SchemaV2.installTriggers(sql); }
                }).build();
    }
    @After public void tearDown() {
        if (db != null) db.close();
        context.deleteDatabase(migrationName);
    }

    @Test public void freshSeedHasNormalizedResourcesAndNoDanglingReferences() {
        SeedLoader.seedIfNeeded(context, db);
        assertEquals(16, db.dao().buildings().size());
        assertEquals(225, countType("CLASSROOM"));
        assertEquals(40, db.dao().equipment().size());
        assertEquals(30, countType("PUBLIC_REST") + countType("OPEN_SEATING"));
        assertEquals(156, db.dao().bookingResources().size());
        assertEquals(50, db.dao().countNotes());
        assertEquals(100, db.dao().seatStates().size());
        assertEquals(720, db.dao().bookingSlots().size());
        try (android.database.Cursor c = db.getOpenHelper().getWritableDatabase().query("PRAGMA foreign_key_check")) {
            assertFalse("Every normalized row must resolve its foreign keys", c.moveToFirst());
        }
        int runs = db.dao().syncRuns().size();
        SeedLoader.seedIfNeeded(context, db);
        assertEquals("Second launch must not duplicate snapshots", runs, db.dao().syncRuns().size());
        assertEquals(50, db.dao().countNotes());
        for (Rows.BookingResource resource : db.dao().bookingResources()) assertEquals("", resource.officialUrl);
    }

    @Test public void paperCannotReferenceEquipmentInAnotherSpace() {
        SeedLoader.seedIfNeeded(context, db);
        Rows.Equipment equipment = db.dao().equipment().get(0);
        Rows.Note note = new Rows.Note(); note.id = "invalid-location"; note.spaceId = "FB-101";
        note.authorId = db.dao().users().get(0).id; note.equipmentId = equipment.id;
        note.type = "设备报修"; note.body = "位置一致性检查"; note.status = "VISIBLE";
        expectFailure(() -> db.dao().insertNote(note));
        note.spaceId = equipment.spaceId;
        db.dao().insertNote(note);
        assertEquals(51, db.dao().countNotes());
    }

    @Test public void duplicateResourcesAndInvalidTimesAreRejected() {
        SeedLoader.seedIfNeeded(context, db);
        Rows.BookingResource resource = db.dao().bookingResources().get(0);
        resource.id = "duplicate-external-id";
        expectFailure(() -> db.dao().insertBookingResources(Collections.singletonList(resource)));
        Rows.BookingSlot slot = db.dao().bookingSlots().get(0);
        slot.id = "invalid-range"; slot.endsAt = slot.startsAt;
        expectFailure(() -> db.dao().insertBookingSlots(Collections.singletonList(slot)));
    }

    @Test public void publicationRollsBackEveryBatchWhenOneBatchIsInvalid() {
        SeedLoader.seedIfNeeded(context, db);
        int count = db.dao().syncRuns().size();
        long now = System.currentTimeMillis();
        Rows.SyncRun valid = new Rows.SyncRun(); valid.id = "empty-success"; valid.sourceId = SeedLoader.TIMETABLE;
        valid.dataKind = "EVENTS"; valid.scopeKey = "FB-101"; valid.windowStart = SeedLoader.dayStart(now, 0);
        valid.windowEnd = SeedLoader.dayStart(now, 1); valid.fetchedAt = now;
        Rows.SyncRun invalid = new Rows.SyncRun(); invalid.id = "bad-snapshot"; invalid.sourceId = "missing-source";
        invalid.dataKind = "EVENTS"; invalid.scopeKey = "FB-101"; invalid.windowStart = valid.windowStart;
        invalid.windowEnd = valid.windowEnd; invalid.fetchedAt = now;
        expectFailure(() -> SnapshotImporter.publish(db, Arrays.asList(new SnapshotImporter.Snapshot(valid), new SnapshotImporter.Snapshot(invalid))));
        assertEquals(count, db.dao().syncRuns().size());
        SnapshotImporter.publish(db, Collections.singletonList(new SnapshotImporter.Snapshot(valid)));
        assertEquals(count + 1, db.dao().syncRuns().size());
        assertEquals("UNKNOWN", SnapshotImporter.normalizeSlotStatus("not-a-known-status"));
        assertEquals("UNKNOWN", SnapshotImporter.normalizeSeatStatus(null));
    }

    @Test public void versionOneUpgradePreservesNotesImagesAndRepairsBuildingTargets() {
        db.close(); context.deleteDatabase(migrationName);
        try (SQLiteDatabase old = context.openOrCreateDatabase(migrationName, Context.MODE_PRIVATE, null)) {
            for (String sql : LEGACY_DDL) old.execSQL(sql);
            old.execSQL("INSERT INTO buildings VALUES ('FB','Foundation Building','中校区',5)");
            old.execSQL("INSERT INTO spaces VALUES ('FB-101','FB',1,'FB-101','SMALL','CLASSROOM','CLASSROOM',20,'白板,空调')");
            old.execSQL("INSERT INTO lectures VALUES ('FB-101',1,1,480,590,'CSI301','Teacher')");
            old.execSQL("INSERT INTO notes VALUES ('old-room','SPACE','FB-101','空间碎碎念','保留正文','/private/old.jpg',123456,'原昵称')");
            old.execSQL("INSERT INTO notes VALUES ('old-building','SPACE','FB','失物招领','保留楼栋纸条',NULL,123457,'另一昵称')");
            old.setVersion(1);
        }
        db = Room.databaseBuilder(context, AppDatabase.class, migrationName).allowMainThreadQueries()
                .addMigrations(LegacyMigration.MIGRATION_1_2, AuthMigration.MIGRATION_2_3).build();
        assertEquals(2, db.dao().countNotes()); // Opening triggers Room's real schema comparison.
        Rows.Note roomNote = null, buildingNote = null;
        for (Rows.Note note : db.dao().notes()) {
            if ("old-room".equals(note.id)) roomNote = note; else buildingNote = note;
        }
        assertNotNull(roomNote); assertNotNull(buildingNote);
        assertEquals("保留正文", roomNote.body); assertEquals("FB-101", roomNote.spaceId);
        assertNotEquals("FB", buildingNote.spaceId);
        assertEquals("/private/old.jpg", db.dao().noteImages().get(0).objectKey);
        assertTrue(db.dao().users().stream().anyMatch(u -> "原昵称".equals(u.displayName)));
        try (android.database.Cursor c = db.getOpenHelper().getWritableDatabase().query("PRAGMA foreign_key_check")) { assertFalse(c.moveToFirst()); }
    }

    @Test public void repositoryProjectsDatedStatesAndSavesAccountImageNotes() throws Exception {
        Repo repo = Repo.get(context);
        CountDownLatch loaded = new CountDownLatch(1);
        repo.reload(loaded::countDown);
        assertTrue("Repository load must release its callback", loaded.await(20, TimeUnit.SECONDS));
        assertTrue(repo.getLoadError(), repo.isLoaded());
        assertEquals(75, repo.spacesOf("FB").size());
        assertTrue(repo.publicSpacesOf("FB").stream().anyMatch(s -> "PUBLIC_REST".equals(s.type)));
        assertTrue(repo.floorsOf("LIB").stream().anyMatch(f -> "10F".equals(f.code)));
        assertEquals(6, repo.bookingGroups("LIBRARY_SEAT").size());
        Rows.BookingGroup group = repo.bookingGroups("LIBRARY_SEAT").get(0);
        assertEquals(20, repo.seatsOfGroup(group.id, 0).size());
        assertTrue(repo.seatsOfGroup(group.id, 1).stream().allMatch(s -> "UNKNOWN".equals(s.status)));
        assertTrue(repo.hasSchedule("FB-101", 0));

        LocalAuth auth = LocalAuth.get(context);
        CountDownLatch accountReady = new CountDownLatch(1);
        AtomicReference<String> accountError = new AtomicReference<>();
        String username = "db_" + UUID.randomUUID().toString().substring(0, 12).replace("-", "");
        auth.register(username, "数据库测试同学", "TestPass987".toCharArray(), true, new LocalAuth.Callback<Rows.User>() {
            public void onSuccess(Rows.User user) { accountReady.countDown(); }
            public void onError(String error) { accountError.set(error); accountReady.countDown(); }
        });
        assertTrue(accountReady.await(30, TimeUnit.SECONDS)); assertNull(accountError.get());

        com.xjtlu.campusspace.data.entity.Note first = new com.xjtlu.campusspace.data.entity.Note();
        first.id = "TEST-REPOSITORY-" + UUID.randomUUID(); first.spaceId = "FB-101";
        first.category = "空间碎碎念"; first.content = "数据库与页面投影集成检查";
        first.imagePaths.add(context.getFilesDir() + "/notes/test-first.jpg");
        first.imagePaths.add(context.getFilesDir() + "/notes/test-second.jpg");
        CountDownLatch saved = new CountDownLatch(1); AtomicReference<String> error = new AtomicReference<>();
        repo.addNote(first, saved::countDown, message -> { error.set(message); saved.countDown(); });
        assertTrue(saved.await(10, TimeUnit.SECONDS)); assertNull(error.get());
        com.xjtlu.campusspace.data.entity.Note second = new com.xjtlu.campusspace.data.entity.Note();
        second.id = "TEST-REPOSITORY-" + UUID.randomUUID(); second.spaceId = "FB-101";
        second.category = "失物招领"; second.content = "登录账号归属保持稳定";
        CountDownLatch savedAgain = new CountDownLatch(1);
        repo.addNote(second, savedAgain::countDown, message -> { error.set(message); savedAgain.countDown(); });
        assertTrue(savedAgain.await(10, TimeUnit.SECONDS)); assertNull(error.get()); assertEquals(first.authorId, second.authorId);
        CountDownLatch read = new CountDownLatch(1);
        AtomicReference<List<com.xjtlu.campusspace.data.entity.Note>> notes = new AtomicReference<>();
        repo.notesOf("SPACE", "FB-101", rows -> { notes.set(rows); read.countDown(); });
        assertTrue(read.await(10, TimeUnit.SECONDS));
        com.xjtlu.campusspace.data.entity.Note restored = notes.get().stream().filter(n -> first.id.equals(n.id)).findFirst().get();
        assertEquals(first.imagePaths, restored.imagePaths);
        assertEquals(auth.currentUser().id, restored.authorId);
        CountDownLatch mineRead = new CountDownLatch(1);
        AtomicReference<List<com.xjtlu.campusspace.data.entity.Note>> mine = new AtomicReference<>();
        repo.myNotes(rows -> { mine.set(rows); mineRead.countDown(); });
        assertTrue(mineRead.await(10, TimeUnit.SECONDS)); assertEquals(2, mine.get().size());
        CountDownLatch loggedOut = new CountDownLatch(1); auth.logout(loggedOut::countDown);
        assertTrue(loggedOut.await(10, TimeUnit.SECONDS));
        CountDownLatch denied = new CountDownLatch(1); error.set(null);
        second.id = "TEST-DENIED-" + UUID.randomUUID();
        repo.addNote(second, denied::countDown, message -> { error.set(message); denied.countDown(); });
        assertTrue(denied.await(10, TimeUnit.SECONDS)); assertNotNull("Guest writes must be rejected", error.get());
        AppDatabase.get(context).getOpenHelper().getWritableDatabase().execSQL(
                "DELETE FROM notes WHERE author_id = ?", new Object[]{restored.authorId});
    }

    private int countType(String type) {
        int count = 0; for (Rows.Space row : db.dao().spaces()) if (type.equals(row.type)) count++; return count;
    }
    private static void expectFailure(Runnable action) {
        try { action.run(); fail("Expected integrity constraint failure"); } catch (RuntimeException expected) { /* expected */ }
    }
    private static final String[] LEGACY_DDL = {
        "CREATE TABLE buildings(code TEXT NOT NULL PRIMARY KEY,name TEXT,campus TEXT,floors INTEGER NOT NULL)",
        "CREATE TABLE spaces(id TEXT NOT NULL PRIMARY KEY,building TEXT,floor INTEGER NOT NULL,name TEXT,size TEXT,kind TEXT,subType TEXT,capacity INTEGER NOT NULL,facilities TEXT)",
        "CREATE TABLE lectures(roomId TEXT NOT NULL,day INTEGER NOT NULL,period INTEGER NOT NULL,startMin INTEGER NOT NULL,endMin INTEGER NOT NULL,course TEXT,teacher TEXT,PRIMARY KEY(roomId,day,period))",
        "CREATE TABLE discussion_rooms(id TEXT NOT NULL PRIMARY KEY,zone TEXT,building TEXT,floor INTEGER NOT NULL,name TEXT,category TEXT,capacity INTEGER NOT NULL,facilities TEXT,bookingUrl TEXT)",
        "CREATE TABLE quiet_pods(id TEXT NOT NULL PRIMARY KEY,zone TEXT,building TEXT,floor INTEGER NOT NULL,name TEXT,category TEXT,capacity INTEGER NOT NULL,facilities TEXT,bookingUrl TEXT)",
        "CREATE TABLE slots(targetId TEXT NOT NULL,targetType TEXT,dayOffset INTEGER NOT NULL,period INTEGER NOT NULL,startMin INTEGER NOT NULL,endMin INTEGER NOT NULL,status TEXT,PRIMARY KEY(targetId,dayOffset,period))",
        "CREATE TABLE library_seats(id TEXT NOT NULL PRIMARY KEY,zone TEXT,row INTEGER NOT NULL,col INTEGER NOT NULL,seatNo TEXT,status TEXT,bookingUrl TEXT)",
        "CREATE TABLE facilities(id TEXT NOT NULL PRIMARY KEY,building TEXT,floor INTEGER NOT NULL,type TEXT,locationText TEXT)",
        "CREATE TABLE notes(id TEXT NOT NULL PRIMARY KEY,targetType TEXT,targetId TEXT,category TEXT,content TEXT,imagePath TEXT,createdAt INTEGER NOT NULL,nickname TEXT)"
    };
}
