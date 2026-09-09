package com.dvid.dcam.platform.device;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import com.dvid.dcam.core.logging.domain.LogCategory;
import com.dvid.dcam.core.logging.application.port.Logger;
import com.dvid.dcam.platform.logging.app.AppLogger;

/** Re-applies kiosk policy after boot or app update and opens DCAM when policy permits it. */
public final class DcamBootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        if (!Intent.ACTION_BOOT_COMPLETED.equals(action)
                && !Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
            return;
        }

        Logger logger = AppLogger.get();
        PendingResult pending = goAsync();
        DcamKioskController.applyActiveKioskPolicyAsync(context, logger, () -> {
            try {
                DcamKioskController kiosk = new DcamKioskController(context, logger);
                if (kiosk.isDeviceOwner() || kiosk.isDefaultHome()) startDcam(context, action, logger);
            } finally {
                pending.finish();
            }
        });
    }

    private static void startDcam(Context context, String action, Logger logger) {
        try {
            Intent launch = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
            if (launch == null) {
                logger.info(LogCategory.APP, "unspecified", "No launch intent available after " + action);
                return;
            }
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_CLEAR_TOP
                    | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            context.startActivity(launch);
        } catch (RuntimeException error) {
            logger.warn(LogCategory.APP, "unspecified", null, "Could not open DCAM after " + action, error);
        }
    }
}