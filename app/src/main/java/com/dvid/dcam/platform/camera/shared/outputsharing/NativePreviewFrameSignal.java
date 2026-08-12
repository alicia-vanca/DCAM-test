package com.dvid.dcam.platform.camera.shared.outputsharing;

import android.os.Handler;

public interface NativePreviewFrameSignal {
    void start(Handler handler, Runnable onFrame);

    void stop();
}