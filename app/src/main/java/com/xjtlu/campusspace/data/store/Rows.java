package com.xjtlu.campusspace.data.store;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/** Persistent v2 rows. Screen models in data.entity deliberately remain separate. */
public final class Rows {
    private Rows() { }

    @Entity(tableName = "buildings", indices = {@Index(value = "code", unique = true)})
    public static class Building {
        @PrimaryKey @NonNull public String id = "";
        @NonNull public String code = "";
        @NonNull public String name = "";
        @ColumnInfo(name = "campus_zone") @NonNull public String campusZone = "";
        @ColumnInfo(name = "sort_order") public int sortOrder;
    }

    @Entity(tableName = "floors", foreignKeys = @ForeignKey(entity = Building.class,
            parentColumns = "id", childColumns = "building_id", onDelete = ForeignKey.RESTRICT),
            indices = {@Index(value = {"building_id", "code"}, unique = true)})
    public static class Floor {
        @PrimaryKey @NonNull public String id = "";
        @ColumnInfo(name = "building_id") @NonNull public String buildingId = "";
        @NonNull public String code = "";
        @ColumnInfo(name = "sort_order") public int sortOrder;
    }

    @Entity(tableName = "spaces", foreignKeys = @ForeignKey(entity = Floor.class,
            parentColumns = "id", childColumns = "floor_id", onDelete = ForeignKey.RESTRICT),
            indices = {@Index(value = {"floor_id", "code"}, unique = true), @Index("type")})
    public static class Space {
        @PrimaryKey @NonNull public String id = "";
        @ColumnInfo(name = "floor_id") @NonNull public String floorId = "";
        @NonNull public String code = "";
        @NonNull public String name = "";
        @NonNull public String type = "CLASSROOM";
        @ColumnInfo(name = "location_hint") @NonNull public String locationHint = "";
        @ColumnInfo(name = "show_in_public_list") public boolean showInPublicList;
        public int capacity;
        @NonNull public String size = "";
        @ColumnInfo(name = "sub_type") @NonNull public String subType = "CLASSROOM";
    }

    @Entity(tableName = "space_facilities", primaryKeys = {"space_id", "facility_code"},
            foreignKeys = @ForeignKey(entity = Space.class, parentColumns = "id",
                    childColumns = "space_id", onDelete = ForeignKey.CASCADE))
    public static class SpaceFacility {
        @ColumnInfo(name = "space_id") @NonNull public String spaceId = "";
        @ColumnInfo(name = "facility_code") @NonNull public String facilityCode = "";
    }

    @Entity(tableName = "equipment", foreignKeys = @ForeignKey(entity = Space.class,
            parentColumns = "id", childColumns = "space_id", onDelete = ForeignKey.RESTRICT),
            indices = {@Index("space_id"), @Index(value = {"id", "space_id"}, unique = true)})
    public static class Equipment {
        @PrimaryKey @NonNull public String id = "";
        @ColumnInfo(name = "space_id") @NonNull public String spaceId = "";
        @NonNull public String type = "";
        @NonNull public String name = "";
        @ColumnInfo(name = "location_hint") @NonNull public String locationHint = "";
    }

    @Entity(tableName = "data_sources", indices = @Index(value = "code", unique = true))
    public static class DataSource {
        @PrimaryKey @NonNull public String id = "";
        @NonNull public String code = "";
        @NonNull public String name = "";
        @ColumnInfo(name = "default_booking_url") @NonNull public String defaultBookingUrl = "";
        @ColumnInfo(name = "is_demo") public boolean isDemo;
    }

    @Entity(tableName = "sync_runs", foreignKeys = @ForeignKey(entity = DataSource.class,
            parentColumns = "id", childColumns = "source_id", onDelete = ForeignKey.RESTRICT),
            indices = @Index(value = {"source_id", "data_kind", "scope_key", "window_start", "window_end", "fetched_at"}))
    public static class SyncRun {
        @PrimaryKey @NonNull public String id = "";
        @ColumnInfo(name = "source_id") @NonNull public String sourceId = "";
        @ColumnInfo(name = "data_kind") @NonNull public String dataKind = "";
        @ColumnInfo(name = "scope_key") @NonNull public String scopeKey = "";
        @ColumnInfo(name = "window_start") public long windowStart;
        @ColumnInfo(name = "window_end") public long windowEnd;
        @ColumnInfo(name = "fetched_at") public long fetchedAt;
        @NonNull public String status = "RUNNING";
    }

    @Entity(tableName = "classroom_events", foreignKeys = {
            @ForeignKey(entity = Space.class, parentColumns = "id", childColumns = "space_id", onDelete = ForeignKey.RESTRICT),
            @ForeignKey(entity = SyncRun.class, parentColumns = "id", childColumns = "sync_run_id", onDelete = ForeignKey.RESTRICT)},
            indices = {@Index(value = {"space_id", "starts_at", "ends_at"}),
                    @Index(value = {"sync_run_id", "external_occurrence_id"}, unique = true)})
    public static class ClassroomEvent {
        @PrimaryKey @NonNull public String id = "";
        @ColumnInfo(name = "space_id") @NonNull public String spaceId = "";
        @ColumnInfo(name = "sync_run_id") @NonNull public String syncRunId = "";
        @ColumnInfo(name = "external_occurrence_id") @NonNull public String externalOccurrenceId = "";
        @ColumnInfo(name = "starts_at") public long startsAt;
        @ColumnInfo(name = "ends_at") public long endsAt;
        @NonNull public String course = "";
        @NonNull public String teacher = "";
    }

    @Entity(tableName = "booking_groups", indices = @Index(value = {"category", "label"}, unique = true))
    public static class BookingGroup {
        @PrimaryKey @NonNull public String id = "";
        @NonNull public String category = "";
        @NonNull public String label = "";
        @ColumnInfo(name = "display_mode") @NonNull public String displayMode = "";
        @ColumnInfo(name = "sort_order") public int sortOrder;
    }

    @Entity(tableName = "booking_resources", foreignKeys = {
            @ForeignKey(entity = BookingGroup.class, parentColumns = "id", childColumns = "group_id", onDelete = ForeignKey.RESTRICT),
            @ForeignKey(entity = Space.class, parentColumns = "id", childColumns = "space_id", onDelete = ForeignKey.RESTRICT),
            @ForeignKey(entity = DataSource.class, parentColumns = "id", childColumns = "source_id", onDelete = ForeignKey.RESTRICT)},
            indices = {@Index("group_id"), @Index("space_id"),
                    @Index(value = {"source_id", "external_id"}, unique = true)})
    public static class BookingResource {
        @PrimaryKey @NonNull public String id = "";
        @ColumnInfo(name = "group_id") @NonNull public String groupId = "";
        @ColumnInfo(name = "space_id") @NonNull public String spaceId = "";
        @ColumnInfo(name = "source_id") @NonNull public String sourceId = "";
        @ColumnInfo(name = "external_id") @NonNull public String externalId = "";
        @NonNull public String label = "";
        @ColumnInfo(name = "grid_row") public int gridRow;
        @ColumnInfo(name = "grid_col") public int gridCol;
        @ColumnInfo(name = "official_url") @NonNull public String officialUrl = "";
    }

    @Entity(tableName = "booking_slots", foreignKeys = {
            @ForeignKey(entity = BookingResource.class, parentColumns = "id", childColumns = "resource_id", onDelete = ForeignKey.RESTRICT),
            @ForeignKey(entity = SyncRun.class, parentColumns = "id", childColumns = "sync_run_id", onDelete = ForeignKey.RESTRICT)},
            indices = {@Index(value = {"resource_id", "sync_run_id", "starts_at", "ends_at"}, unique = true), @Index("sync_run_id")})
    public static class BookingSlot {
        @PrimaryKey @NonNull public String id = "";
        @ColumnInfo(name = "resource_id") @NonNull public String resourceId = "";
        @ColumnInfo(name = "sync_run_id") @NonNull public String syncRunId = "";
        @ColumnInfo(name = "starts_at") public long startsAt;
        @ColumnInfo(name = "ends_at") public long endsAt;
        @NonNull public String status = "UNKNOWN";
        @ColumnInfo(name = "raw_status") @NonNull public String rawStatus = "";
    }

    @Entity(tableName = "seat_states", foreignKeys = {
            @ForeignKey(entity = BookingResource.class, parentColumns = "id", childColumns = "resource_id", onDelete = ForeignKey.RESTRICT),
            @ForeignKey(entity = SyncRun.class, parentColumns = "id", childColumns = "sync_run_id", onDelete = ForeignKey.RESTRICT)},
            indices = {@Index(value = {"resource_id", "sync_run_id"}, unique = true), @Index("sync_run_id")})
    public static class SeatState {
        @PrimaryKey @NonNull public String id = "";
        @ColumnInfo(name = "resource_id") @NonNull public String resourceId = "";
        @ColumnInfo(name = "sync_run_id") @NonNull public String syncRunId = "";
        @NonNull public String status = "UNKNOWN";
        @ColumnInfo(name = "raw_status") @NonNull public String rawStatus = "";
    }

    @Entity(tableName = "users", indices = @Index(value = "auth_subject", unique = true))
    public static class User {
        @PrimaryKey @NonNull public String id = "";
        @ColumnInfo(name = "auth_subject") @Nullable public String authSubject;
        @ColumnInfo(name = "display_name") @NonNull public String displayName = "";
    }

    @Entity(tableName = "notes", foreignKeys = {
            @ForeignKey(entity = Space.class, parentColumns = "id", childColumns = "space_id", onDelete = ForeignKey.RESTRICT),
            @ForeignKey(entity = User.class, parentColumns = "id", childColumns = "author_id", onDelete = ForeignKey.RESTRICT),
            @ForeignKey(entity = Equipment.class, parentColumns = {"id", "space_id"}, childColumns = {"equipment_id", "space_id"}, onDelete = ForeignKey.RESTRICT)},
            indices = {@Index(value = {"space_id", "created_at"}), @Index("author_id"),
                    @Index(value = {"equipment_id", "space_id"}), @Index("created_at")})
    public static class Note {
        @PrimaryKey @NonNull public String id = "";
        @ColumnInfo(name = "space_id") @NonNull public String spaceId = "";
        @ColumnInfo(name = "author_id") @NonNull public String authorId = "";
        @ColumnInfo(name = "equipment_id") @Nullable public String equipmentId;
        @NonNull public String type = "空间碎碎念";
        @NonNull public String body = "";
        @ColumnInfo(name = "created_at") public long createdAt;
        @NonNull public String status = "VISIBLE";
    }

    @Entity(tableName = "note_images", foreignKeys = @ForeignKey(entity = Note.class,
            parentColumns = "id", childColumns = "note_id", onDelete = ForeignKey.CASCADE),
            indices = @Index(value = {"note_id", "sort_order"}, unique = true))
    public static class NoteImage {
        @PrimaryKey @NonNull public String id = "";
        @ColumnInfo(name = "note_id") @NonNull public String noteId = "";
        @ColumnInfo(name = "object_key") @NonNull public String objectKey = "";
        @ColumnInfo(name = "sort_order") public int sortOrder;
    }
}
