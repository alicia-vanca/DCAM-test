package com.dvid.dcam.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import com.dvid.dcam.core.logging.domain.LogCategory;

/** Recovers staged media immediately after Android regains access to removable storage. */
public final class DcamStorageMountedReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_MEDIA_MOUNTED.equals(intent.getAction())) return;

        PendingResult pendingResult = goAsync();
        AppComposition composition = AppComposition.create(context);
        composition.recoverMountedStorage(report -> {
            composition.logger().info(LogCategory.STORAGE, "staged_media_recovery_completed", "Mounted-storage recovery completed. Recovered files: "
                    + report.getRecovered() + ". Preserved in Temp: "
                    + report.getPreserved() + ". Duplicates skipped: "
                    + report.getDuplicates() + ".");
            pendingResult.finish();
        });
    }
}

