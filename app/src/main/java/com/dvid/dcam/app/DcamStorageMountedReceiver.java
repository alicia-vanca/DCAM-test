package com.dvid.dcam.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import com.dvid.dcam.platform.logging.DcamLogger;

/** Recovers staged media immediately after Android regains access to removable storage. */
public final class DcamStorageMountedReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_MEDIA_MOUNTED.equals(intent.getAction())) return;

        PendingResult pendingResult = goAsync();
        AppComposition.create(context).recoverMountedStorage(report -> {
            DcamLogger.i("Mounted-storage recovery: recovered=" + report.getRecovered()
                    + ", preserved=" + report.getPreserved()
                    + ", duplicates=" + report.getDuplicates());
            pendingResult.finish();
        });
    }
}

