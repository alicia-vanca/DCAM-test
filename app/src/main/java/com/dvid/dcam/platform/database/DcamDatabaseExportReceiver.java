package com.dvid.dcam.platform.database;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.dvid.dcam.core.logging.application.port.Logger;
import com.dvid.dcam.core.logging.domain.LogCategory;
import com.dvid.dcam.platform.logging.app.AppLogger;

/** Internal ADB endpoint used by VC Toolbox when run-as cannot access the database. */
public final class DcamDatabaseExportReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null
                || !AppDatabaseExporter.ACTION_EXPORT_DATABASE.equals(intent.getAction())) {
            return;
        }

        PendingResult pendingResult = goAsync();
        Context appContext = context.getApplicationContext();
        Thread worker = new Thread(() -> {
            Logger logger = AppLogger.get();
            try {
                logger.info(LogCategory.DB, "database_export_requested",
                        "ADB requested a Dcam database export.");
                AppDatabaseExporter.export(appContext);
                logger.info(LogCategory.DB, "database_export_completed",
                        "Dcam database export completed for VC Toolbox.");
            } catch (Exception error) {
                logger.error(LogCategory.DB, "database_export_failed", null,
                        "Dcam database export failed.", error);
            } finally {
                pendingResult.finish();
            }
        }, "dcam-database-export");
        worker.start();
    }
}
