package com.xjtlu.campusspace.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import com.xjtlu.campusspace.data.store.Rows;
import java.util.List;

/** Duplicate identities abort instead of replacing and deleting referenced rows. */
@Dao
public interface AppDao {
    @Query("SELECT COUNT(*) FROM spaces") int countSpaces();
    @Query("SELECT COUNT(*) FROM notes") int countNotes();
    @Query("SELECT * FROM buildings ORDER BY sort_order, code") List<Rows.Building> buildings();
    @Query("SELECT * FROM floors ORDER BY building_id, sort_order, code") List<Rows.Floor> floors();
    @Query("SELECT * FROM spaces ORDER BY floor_id, code") List<Rows.Space> spaces();
    @Query("SELECT * FROM space_facilities ORDER BY space_id, facility_code") List<Rows.SpaceFacility> spaceFacilities();
    @Query("SELECT * FROM equipment ORDER BY space_id, id") List<Rows.Equipment> equipment();
    @Query("SELECT * FROM classroom_events ORDER BY starts_at, id") List<Rows.ClassroomEvent> events();
    @Query("SELECT * FROM booking_groups ORDER BY sort_order, id") List<Rows.BookingGroup> bookingGroups();
    @Query("SELECT * FROM booking_resources ORDER BY group_id, grid_row, grid_col, label") List<Rows.BookingResource> bookingResources();
    @Query("SELECT * FROM booking_slots ORDER BY starts_at, id") List<Rows.BookingSlot> bookingSlots();
    @Query("SELECT * FROM seat_states ORDER BY resource_id, sync_run_id") List<Rows.SeatState> seatStates();
    @Query("SELECT * FROM data_sources ORDER BY code") List<Rows.DataSource> dataSources();
    @Query("SELECT * FROM sync_runs ORDER BY fetched_at DESC, id") List<Rows.SyncRun> syncRuns();
    @Query("SELECT * FROM users ORDER BY id") List<Rows.User> users();
    @Query("SELECT * FROM notes ORDER BY created_at DESC, id") List<Rows.Note> notes();
    @Query("SELECT * FROM note_images ORDER BY note_id, sort_order") List<Rows.NoteImage> noteImages();
    @Query("SELECT * FROM notes WHERE status = 'VISIBLE' ORDER BY created_at DESC, id LIMIT :limit") List<Rows.Note> latestNotes(int limit);
    @Query("SELECT * FROM notes WHERE space_id = :spaceId AND status = 'VISIBLE' ORDER BY created_at DESC, id") List<Rows.Note> notesOfSpace(String spaceId);
    @Query("SELECT * FROM notes WHERE equipment_id = :equipmentId AND status = 'VISIBLE' ORDER BY created_at DESC, id") List<Rows.Note> notesOfEquipment(String equipmentId);

    @Insert(onConflict = OnConflictStrategy.ABORT) void insertBuildings(List<Rows.Building> rows);
    @Insert(onConflict = OnConflictStrategy.ABORT) void insertFloors(List<Rows.Floor> rows);
    @Insert(onConflict = OnConflictStrategy.ABORT) void insertSpaces(List<Rows.Space> rows);
    @Insert(onConflict = OnConflictStrategy.ABORT) void insertSpaceFacilities(List<Rows.SpaceFacility> rows);
    @Insert(onConflict = OnConflictStrategy.ABORT) void insertEquipment(List<Rows.Equipment> rows);
    @Insert(onConflict = OnConflictStrategy.ABORT) void insertEvents(List<Rows.ClassroomEvent> rows);
    @Insert(onConflict = OnConflictStrategy.ABORT) void insertBookingGroups(List<Rows.BookingGroup> rows);
    @Insert(onConflict = OnConflictStrategy.ABORT) void insertBookingResources(List<Rows.BookingResource> rows);
    @Insert(onConflict = OnConflictStrategy.ABORT) void insertBookingSlots(List<Rows.BookingSlot> rows);
    @Insert(onConflict = OnConflictStrategy.ABORT) void insertSeatStates(List<Rows.SeatState> rows);
    @Insert(onConflict = OnConflictStrategy.ABORT) void insertDataSources(List<Rows.DataSource> rows);
    @Insert(onConflict = OnConflictStrategy.ABORT) void insertSyncRuns(List<Rows.SyncRun> rows);
    @Insert(onConflict = OnConflictStrategy.ABORT) void insertUsers(List<Rows.User> rows);
    @Insert(onConflict = OnConflictStrategy.ABORT) void insertNotes(List<Rows.Note> rows);
    @Insert(onConflict = OnConflictStrategy.ABORT) void insertNoteImages(List<Rows.NoteImage> rows);
    @Insert(onConflict = OnConflictStrategy.ABORT) void insertNote(Rows.Note row);
    @Insert(onConflict = OnConflictStrategy.ABORT) void insertUser(Rows.User row);
}
