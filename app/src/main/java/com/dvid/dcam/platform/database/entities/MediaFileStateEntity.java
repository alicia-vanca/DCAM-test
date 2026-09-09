package com.dvid.dcam.platform.database.entities;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "media_file_state")
public final class MediaFileStateEntity {
    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "file_name")
    public String fileName;

    @NonNull
    @ColumnInfo(name = "file_state")
    public String fileState;

    @NonNull
    @ColumnInfo(name = "storage_root")
    public String storageRoot;

    @ColumnInfo(name = "updated_at", defaultValue = "0")
    public long updatedAt;

    public MediaFileStateEntity(
            @NonNull String fileName, @NonNull String fileState,
            @NonNull String storageRoot, long updatedAt) {
        this.fileName = fileName;
        this.fileState = fileState;
        this.storageRoot = storageRoot;
        this.updatedAt = updatedAt;
    }
}
