package com.xjtlu.campusspace.data;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import androidx.sqlite.db.SupportSQLiteDatabase;
import com.xjtlu.campusspace.data.store.Rows;

@Database(
        entities = {
                Rows.Building.class, Rows.Floor.class, Rows.Space.class, Rows.SpaceFacility.class,
                Rows.Equipment.class, Rows.ClassroomEvent.class, Rows.BookingGroup.class,
                Rows.BookingResource.class, Rows.BookingSlot.class, Rows.SeatState.class,
                Rows.DataSource.class, Rows.SyncRun.class, Rows.User.class, Rows.Note.class,
                Rows.NoteImage.class
        },
        version = 2,
        exportSchema = true
)
public abstract class AppDatabase extends RoomDatabase {

    public abstract AppDao dao();

    private static volatile AppDatabase instance;

    public static AppDatabase get(Context context) {
        if (instance == null) {
            synchronized (AppDatabase.class) {
                if (instance == null) {
                    instance = Room.databaseBuilder(
                                    context.getApplicationContext(),
                                    AppDatabase.class,
                                    "campus.db")
                            .addMigrations(LegacyMigration.MIGRATION_1_2)
                            .addCallback(new Callback() {
                                @Override public void onCreate(@NonNull SupportSQLiteDatabase db) {
                                    SchemaV2.installTriggers(db);
                                }
                                @Override public void onOpen(@NonNull SupportSQLiteDatabase db) {
                                    SchemaV2.installTriggers(db);
                                }
                            })
                            .build();
                }
            }
        }
        return instance;
    }
}
