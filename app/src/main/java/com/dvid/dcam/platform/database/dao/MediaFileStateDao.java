package com.dvid.dcam.platform.database.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import com.dvid.dcam.platform.database.entities.MediaFileStateEntity;
import java.util.List;

@Dao
public interface MediaFileStateDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(MediaFileStateEntity state);

    @Query("SELECT * FROM media_file_state")
    List<MediaFileStateEntity> findAll();

    @Query("DELETE FROM media_file_state WHERE file_name = :fileName")
    void delete(String fileName);
}
