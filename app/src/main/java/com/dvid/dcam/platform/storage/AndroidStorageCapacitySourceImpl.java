package com.dvid.dcam.platform.storage;

import android.content.Context;
import android.os.Environment;
import android.os.StatFs;
import com.dvid.dcam.feature.storage.application.port.StorageCapacitySource;
import com.dvid.dcam.feature.storage.domain.MediaPartitionLocation;
import com.dvid.dcam.feature.storage.domain.StorageVolumeStatus;
import java.io.File;
import java.util.List;

/** Android filesystem adapter for storage capacity snapshots. */
public final class AndroidStorageCapacitySourceImpl implements StorageCapacitySource {
    private final Context context;

    public AndroidStorageCapacitySourceImpl(Context context) {
        this.context = context.getApplicationContext();
    }

    @Override public List<StorageVolumeStatus> readStorageVolumes() {
        File[] roots = context.getExternalFilesDirs(null);
        File internal = roots.length == 0 ? context.getFilesDir() : roots[0];
        File external = roots.length < 2 ? null : roots[1];
        return List.of(read(MediaPartitionLocation.INTERNAL, internal, false),
                read(MediaPartitionLocation.EXTERNAL, external, true));
    }

    private static StorageVolumeStatus read(MediaPartitionLocation mode, File root, boolean requireMounted) {
        if (root == null || requireMounted
                && !Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState(root))) {
            return new StorageVolumeStatus(mode, false, 0L, 0L);
        }
        try {
            StatFs stats = new StatFs(root.getAbsolutePath());
            return new StorageVolumeStatus(mode, true, stats.getTotalBytes(), stats.getAvailableBytes());
        } catch (RuntimeException failure) {
            return new StorageVolumeStatus(mode, false, 0L, 0L);
        }
    }
}
