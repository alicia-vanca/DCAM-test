package com.dvid.dcam.app.ui;

import android.content.Context;
import com.dvid.dcam.R;

public final class MainUiMessageFormatter {
    private MainUiMessageFormatter() {}

    public static String localized(Context context, String message) {
        if (message == null) return "";
        if (message.startsWith(MainViewModel.STORAGE_STOPPED_MESSAGE_PREFIX)) {
            return context.getString(R.string.storage_capture_stopped);
        }
        return message.startsWith("Saved ")
                ? context.getString(R.string.media_saved, message.substring(6))
                : message;
    }
}
