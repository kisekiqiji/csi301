package com.xjtlu.campusspace.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDatabase;
import android.os.Looper;
import androidx.annotation.NonNull;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.xjtlu.campusspace.data.store.Rows;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class LocalAuthTest {
    private Context context;
    private AppDatabase db;
    private LocalAuth auth;
    private SharedPreferences preferences;
    private final AtomicLong clock = new AtomicLong(System.currentTimeMillis());
    private static final String MIGRATION_DB = "local-auth-v2-migration-test.db";

    @Before public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        preferences = context.getSharedPreferences("auth-test-" + UUID.randomUUID(), Context.MODE_PRIVATE);
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase.class).allowMainThreadQueries()
                .addCallback(new RoomDatabase.Callback() {
                    @Override public void onCreate(@NonNull SupportSQLiteDatabase sql) {
                        SchemaV2.installTriggers(sql); AuthMigration.installTriggers(sql);
                    }
                }).build();
        auth = new LocalAuth(context, db, preferences, clock::get);
    }

    @After public void tearDown() {
        if (auth != null) auth.closeForTests();
        if (db != null) db.close();
        preferences.edit().clear().commit();
        context.deleteDatabase(MIGRATION_DB);
    }

    @Test public void registrationRestoresOnlyAnIdSessionAndDoesNotNeedSeed() throws Exception {
        ready(auth);
        assertNull(auth.currentUser()); assertEquals(0, db.dao().countSpaces());
        Outcome registered = call(cb -> auth.register("My_User", "  同学  ", "simple password".toCharArray(), true, cb));
        assertNull(registered.error); assertNotNull(registered.user);
        String id = registered.user.id;
        assertEquals("my_user", registered.user.username); assertEquals("同学", registered.user.displayName);
        assertEquals(id, auth.currentUser().id); assertNull(auth.currentUser().passwordHash);
        assertEquals(1, preferences.getAll().size()); assertEquals(id, preferences.getString("user_id", null));
        Rows.User stored = db.dao().userById(id);
        assertNotEquals("simple password", stored.passwordHash);
        assertTrue(PasswordHasher.verify("simple password".toCharArray(), stored.salt,
                stored.passwordHash, stored.passwordAlgorithm, stored.passwordIterations));
        assertTrue(stored.rulesAcceptedAt > 0); assertEquals(stored.createdAt, stored.rulesAcceptedAt);
        auth.closeForTests();
        auth = new LocalAuth(context, db, preferences, clock::get); ready(auth);
        assertEquals(id, auth.currentUser().id);
        Outcome renamed = call(cb -> auth.updateDisplayName("新昵称", cb));
        assertNull(renamed.error); assertEquals("新昵称", auth.currentUser().displayName);
        assertEquals(id, renamed.user.id);
        assertEquals(id, auth.authenticatedUserForWrite().id);
        logout(auth); assertNull(auth.currentUser()); assertNull(auth.authenticatedUserForWrite());
        auth.closeForTests(); auth = new LocalAuth(context, db, preferences, clock::get); ready(auth);
        assertNull(auth.currentUser()); assertTrue(preferences.getAll().isEmpty());
    }

    @Test public void validatesRulesAndRejectsCaseInsensitiveDuplicatesWithoutClaimingLegacyAuthors() throws Exception {
        Rows.User old = new Rows.User(); old.id = "old-anonymous"; old.displayName = "旧昵称";
        db.dao().insertUser(old);
        assertNotNull(call(cb -> auth.register("valid_name", "同学", "password".toCharArray(), false, cb)).error);
        assertNotNull(call(cb -> auth.register("bad name", "同学", "password".toCharArray(), true, cb)).error);
        assertNotNull(call(cb -> auth.register("valid_name", " ", "password".toCharArray(), true, cb)).error);
        assertNotNull(call(cb -> auth.register("valid_name", "同学", "short".toCharArray(), true, cb)).error);
        assertEquals(1, db.dao().users().size());
        Outcome created = call(cb -> auth.register("Valid_Name", "旧昵称", "password".toCharArray(), true, cb));
        assertNull(created.error); assertNotEquals(old.id, created.user.id);
        assertNull(db.dao().userById(old.id).username);
        assertNotNull(call(cb -> auth.register("VALID_NAME", "另一个同学", "password".toCharArray(), true, cb)).error);
        assertEquals(2, db.dao().users().size());
        logout(auth);
        Outcome loggedIn = call(cb -> auth.login("VALID_NAME", "password".toCharArray(), cb));
        assertNull(loggedIn.error); assertEquals(created.user.id, loggedIn.user.id);
    }

    @Test public void fiveFailuresPersistCooldownAndSuccessResetsIt() throws Exception {
        Outcome created = call(cb -> auth.register("cooldown", "同学", "correct password".toCharArray(), true, cb));
        assertNull(created.error); logout(auth);
        for (int i = 1; i <= 5; i++) {
            Outcome failed = call(cb -> auth.login("cooldown", "wrong password".toCharArray(), cb));
            assertNotNull(failed.error); assertNull(auth.currentUser());
            assertEquals(i, db.dao().userById(created.user.id).failedAttempts);
        }
        Rows.User locked = db.dao().userById(created.user.id);
        assertEquals(clock.get() + LocalAuth.LOCK_MILLIS, locked.lockedUntil);
        assertNotNull(call(cb -> auth.login("cooldown", "correct password".toCharArray(), cb)).error);
        clock.addAndGet(LocalAuth.LOCK_MILLIS + 1);
        Outcome success = call(cb -> auth.login("cooldown", "correct password".toCharArray(), cb));
        assertNull(success.error); assertEquals(created.user.id, auth.currentUser().id);
        assertEquals(0, db.dao().userById(created.user.id).failedAttempts);
        assertEquals(0, db.dao().userById(created.user.id).lockedUntil);
    }

    @Test public void versionTwoUpgradePreservesAuthorsNotesAndImagesWithoutCreatingAccounts() {
        auth.closeForTests(); db.close(); context.deleteDatabase(MIGRATION_DB);
        try (SQLiteDatabase old = context.openOrCreateDatabase(MIGRATION_DB, Context.MODE_PRIVATE, null)) {
            for (String sql : SchemaV2.TABLE_DDL) old.execSQL(sql);
            for (String sql : SchemaV2.INDEX_DDL) old.execSQL(sql);
            old.execSQL("INSERT INTO buildings VALUES ('FB','FB','Foundation Building','中校区',0)");
            old.execSQL("INSERT INTO floors VALUES ('F','FB','1F',1)");
            old.execSQL("INSERT INTO spaces VALUES ('S','F','S','原空间','CLASSROOM','',0,20,'SMALL','CLASSROOM')");
            old.execSQL("INSERT INTO users VALUES ('legacy',NULL,'原作者')");
            old.execSQL("INSERT INTO notes VALUES ('N','S','legacy',NULL,'空间碎碎念','原正文',123456,'VISIBLE')");
            old.execSQL("INSERT INTO note_images VALUES ('I','N','notes/original.jpg',0)");
            old.setVersion(2);
        }
        db = Room.databaseBuilder(context, AppDatabase.class, MIGRATION_DB).allowMainThreadQueries()
                .addMigrations(AuthMigration.MIGRATION_2_3).build();
        assertEquals(1, db.dao().countNotes()); // Opens the real Room schema validator.
        Rows.User user = db.dao().userById("legacy");
        assertEquals("原作者", user.displayName); assertNull(user.username); assertNull(user.passwordHash);
        assertEquals(0, user.passwordIterations); assertEquals(0, user.createdAt);
        assertEquals("legacy", db.dao().notes().get(0).authorId);
        assertEquals("原正文", db.dao().notes().get(0).body);
        assertEquals("notes/original.jpg", db.dao().noteImages().get(0).objectKey);
        try (android.database.Cursor c = db.getOpenHelper().getWritableDatabase().query("PRAGMA foreign_key_check")) {
            assertFalse(c.moveToFirst());
        }
    }

    private static void ready(LocalAuth auth) throws Exception {
        CountDownLatch ready = new CountDownLatch(1); auth.ensureLoaded(ready::countDown);
        assertTrue(ready.await(20, TimeUnit.SECONDS));
    }
    private static void logout(LocalAuth auth) throws Exception {
        CountDownLatch done = new CountDownLatch(1); auth.logout(done::countDown);
        assertTrue(done.await(20, TimeUnit.SECONDS));
    }
    private interface Action { void run(LocalAuth.Callback<Rows.User> callback); }
    private static final class Outcome { Rows.User user; String error; }
    private static Outcome call(Action action) throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<Rows.User> user = new AtomicReference<>(); AtomicReference<String> error = new AtomicReference<>();
        AtomicBoolean mainThread = new AtomicBoolean();
        action.run(new LocalAuth.Callback<Rows.User>() {
            @Override public void onSuccess(Rows.User value) { user.set(value); finish(); }
            @Override public void onError(String value) { error.set(value); finish(); }
            private void finish() { mainThread.set(Looper.myLooper() == Looper.getMainLooper()); done.countDown(); }
        });
        assertTrue("Auth must complete its callback", done.await(45, TimeUnit.SECONDS));
        assertTrue("Auth callbacks must run on the UI thread", mainThread.get());
        Outcome result = new Outcome(); result.user = user.get(); result.error = error.get(); return result;
    }
}
