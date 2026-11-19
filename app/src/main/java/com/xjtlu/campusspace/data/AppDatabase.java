package com.xjtlu.campusspace.data;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import com.xjtlu.campusspace.data.entity.BookableSlot;
import com.xjtlu.campusspace.data.entity.Building;
import com.xjtlu.campusspace.data.entity.DiscussionRoom;
import com.xjtlu.campusspace.data.entity.Facility;
import com.xjtlu.campusspace.data.entity.Lecture;
import com.xjtlu.campusspace.data.entity.LibrarySeat;
import com.xjtlu.campusspace.data.entity.Note;
import com.xjtlu.campusspace.data.entity.QuietPod;
import com.xjtlu.campusspace.data.entity.Space;

@Database(
        entities = {
                Building.class, Space.class, Lecture.class,
                DiscussionRoom.class, QuietPod.class, BookableSlot.class,
                LibrarySeat.class, Facility.class, Note.class
        },
        version = 1,
        exportSchema = false
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
                            .fallbackToDestructiveMigration()
                            .build();
                }
            }
        }
        return instance;
    }
}
