package com.xjtlu.campusspace.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.xjtlu.campusspace.data.entity.BookableSlot;
import com.xjtlu.campusspace.data.entity.Building;
import com.xjtlu.campusspace.data.entity.DiscussionRoom;
import com.xjtlu.campusspace.data.entity.Facility;
import com.xjtlu.campusspace.data.entity.Lecture;
import com.xjtlu.campusspace.data.entity.LibrarySeat;
import com.xjtlu.campusspace.data.entity.Note;
import com.xjtlu.campusspace.data.entity.QuietPod;
import com.xjtlu.campusspace.data.entity.Space;

import java.util.List;

@Dao
public interface AppDao {

    @Query("SELECT COUNT(*) FROM spaces")
    int countSpaces();

    @Query("SELECT * FROM buildings")
    List<Building> buildings();

    @Query("SELECT * FROM spaces")
    List<Space> spaces();

    @Query("SELECT * FROM lectures")
    List<Lecture> lectures();

    @Query("SELECT * FROM discussion_rooms")
    List<DiscussionRoom> discussionRooms();

    @Query("SELECT * FROM quiet_pods")
    List<QuietPod> quietPods();

    @Query("SELECT * FROM slots")
    List<BookableSlot> slots();

    @Query("SELECT * FROM library_seats")
    List<LibrarySeat> seats();

    @Query("SELECT * FROM facilities")
    List<Facility> facilities();

    @Query("SELECT * FROM notes ORDER BY createdAt DESC LIMIT :limit")
    List<Note> latestNotes(int limit);

    @Query("SELECT * FROM notes WHERE targetType = :targetType AND targetId = :targetId ORDER BY createdAt DESC")
    List<Note> notesOf(String targetType, String targetId);

    @Query("SELECT COUNT(*) FROM notes")
    int countNotes();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertBuildings(List<Building> rows);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertSpaces(List<Space> rows);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertLectures(List<Lecture> rows);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertDiscussionRooms(List<DiscussionRoom> rows);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertQuietPods(List<QuietPod> rows);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertSlots(List<BookableSlot> rows);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertSeats(List<LibrarySeat> rows);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertFacilities(List<Facility> rows);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertNotes(List<Note> rows);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertNote(Note row);
}
