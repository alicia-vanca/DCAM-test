package com.dvid.dcam.platform.recording;

import android.content.Context;
import java.util.Objects;

public final class DebugRecordingIdleProbe {
    private final Context context;

    public DebugRecordingIdleProbe(Context context) {
        Context applicationContext = Objects.requireNonNull(context, "context").getApplicationContext();
        this.context = applicationContext == null ? context : applicationContext;
    }

    public boolean isIdle() {
        return !new RecordingForegroundStateStore(context).snapshot().isActive();
    }
}
