package com.dvid.dcam.platform.database.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import com.dvid.dcam.platform.database.entities.DeviceIdentityEntity;
import com.dvid.dcam.platform.database.entities.OperationalSettingEntity;
import com.dvid.dcam.platform.database.entities.RemoteConfigEntity;

@Dao
public interface CloudStateDao {
    @Query("SELECT * FROM device_identity WHERE id = 1")
    DeviceIdentityEntity deviceIdentity();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void saveDeviceIdentity(DeviceIdentityEntity identity);

    @Query("DELETE FROM device_identity WHERE id = 1")
    void deleteDeviceIdentity();

    @Query("SELECT * FROM remote_config_cache WHERE id = 1")
    RemoteConfigEntity remoteConfig();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void saveRemoteConfig(RemoteConfigEntity config);

    @Query("SELECT value FROM operational_settings WHERE `key` = :key")
    String operationalSetting(String key);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void saveOperationalSetting(OperationalSettingEntity setting);
}
