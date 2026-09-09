package com.dvid.dcam.platform.database;

import android.content.Context;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import com.dvid.dcam.platform.database.dao.CloudStateDao;
import com.dvid.dcam.platform.database.dao.MediaFileStateDao;
import com.dvid.dcam.platform.database.dao.OperatorAuthDao;
import com.dvid.dcam.platform.database.dao.PendingLogDao;
import com.dvid.dcam.platform.database.entities.DeviceIdentityEntity;
import com.dvid.dcam.platform.database.entities.OperationalSettingEntity;
import com.dvid.dcam.platform.database.entities.OperatorSessionEntity;
import com.dvid.dcam.platform.database.entities.PendingLogEntity;
import com.dvid.dcam.platform.database.entities.RemoteConfigEntity;
import com.dvid.dcam.platform.database.entities.MediaFileStateEntity;
import com.dvid.dcam.platform.database.entities.UserAuthMethodEntity;
import com.dvid.dcam.platform.database.entities.UserProfileEntity;
import com.dvid.dcam.platform.database.migrations.AppDatabaseMigrations;

/**
 * Central Room database manifest for the app.
 *
 * <p>This is the single authoritative list of tables Room creates on a fresh
 * install. Room requires entity class literals directly in {@link Database}, so
 * keep the entity list here instead of hiding it behind another registry.
 * {@code AppDatabaseManifestTest} fails if a new {@code @Entity} is not listed
 * below.
 *
 * <p>When adding a new table:
 * <ol>
 *     <li>Add its {@code @Entity} class to {@link Database#entities()} below.</li>
 *     <li>Add its DAO accessor to this class.</li>
 *     <li>Add a version and migration in {@link AppDatabaseMigrations} if the
 *     current database version has already shipped.</li>
 *     <li>Build so Room exports the new schema JSON under {@code app/schemas}.</li>
 * </ol>
 */
@Database(
        // App table manifest. Fresh installs are created from this list.
        entities = {
                PendingLogEntity.class,
                DeviceIdentityEntity.class,
                RemoteConfigEntity.class,
                OperationalSettingEntity.class,
                UserProfileEntity.class,
                UserAuthMethodEntity.class,
                OperatorSessionEntity.class,
                MediaFileStateEntity.class
        },
        version = AppDatabaseMigrations.LATEST_VERSION,
        exportSchema = true
)
public abstract class AppDatabase extends RoomDatabase {
    private static final String DATABASE_NAME = "dcam.db";
    private static volatile AppDatabase instance;

    // DAO manifest. Every app table should have its DAO exposed here.
    public abstract PendingLogDao pendingLogs();
    public abstract CloudStateDao cloudState();
    public abstract OperatorAuthDao operatorAuth();
    public abstract MediaFileStateDao mediaFileStates();

    public static AppDatabase get(Context context) {
        AppDatabase current = instance;
        if (current != null) return current;
        synchronized (AppDatabase.class) {
            if (instance == null) {
                Context appContext = context.getApplicationContext();
                instance = Room.databaseBuilder(appContext, AppDatabase.class, DATABASE_NAME)
                        .addMigrations(AppDatabaseMigrations.all())
                        .enableMultiInstanceInvalidation()
                        .build();
            }
            return instance;
        }
    }

    public static synchronized void reset(Context context) {
        if (instance != null) {
            instance.close();
            instance = null;
        }
        context.getApplicationContext().deleteDatabase(DATABASE_NAME);
    }
}
