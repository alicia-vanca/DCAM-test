package com.dvid.dcam.platform.device;

import android.app.admin.DeviceAdminReceiver;
import android.content.Context;
import android.content.Intent;
import com.dvid.dcam.core.logging.domain.LogCategory;
import com.dvid.dcam.core.logging.application.port.Logger;
import com.dvid.dcam.platform.logging.app.AppLogger;

/** Device owner/admin entry point used for managed kiosk provisioning. */
public final class DcamDeviceAdminReceiver extends DeviceAdminReceiver {
    private final Logger logger = AppLogger.get();
    @Override public void onEnabled(Context context, Intent intent) {
        logger.info(LogCategory.PROVISIONING, "unspecified", "DCAM device admin enabled");
    }

    @Override public void onDisabled(Context context, Intent intent) {
        logger.info(LogCategory.PROVISIONING, "unspecified", "DCAM device admin disabled");
    }
}