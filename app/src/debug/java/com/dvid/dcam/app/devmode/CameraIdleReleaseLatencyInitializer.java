package com.dvid.dcam.app.devmode;

import android.Manifest;
import android.content.Context;
import android.content.IntentFilter;
import androidx.core.content.ContextCompat;
import androidx.startup.Initializer;
import java.util.List;

public final class CameraIdleReleaseLatencyInitializer implements Initializer<Void> {
    @Override public Void create(Context context) {
        IntentFilter filter = new IntentFilter();
        filter.addAction(CameraIdleReleaseLatencyReceiver.ACTION_PREPARE);
        filter.addAction(CameraIdleReleaseLatencyReceiver.ACTION_RESTORE);
        filter.addAction(CameraIdleReleaseLatencyReceiver.ACTION_RELEASE);
        filter.addAction(CameraIdleReleaseLatencyReceiver.ACTION_VIDEO);
        filter.addAction(CameraIdleReleaseLatencyReceiver.ACTION_PHOTO);
        ContextCompat.registerReceiver(context.getApplicationContext(),
                new CameraIdleReleaseLatencyReceiver(), filter, Manifest.permission.DUMP, null,
                ContextCompat.RECEIVER_EXPORTED);
        return null;
    }

    @Override public List<Class<? extends Initializer<?>>> dependencies() {
        return List.of();
    }
}
