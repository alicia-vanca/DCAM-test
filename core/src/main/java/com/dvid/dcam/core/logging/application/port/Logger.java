package com.dvid.dcam.core.logging.application.port;

import com.dvid.dcam.core.logging.domain.LogCategory;

/** Local-first logger capability. Cloud upload remains an adapter concern. */
public interface Logger {
    void debug(LogCategory category, String eventName, String message);
    void info(LogCategory category, String eventName, String message);
    void info(LogCategory category, String eventName, String reasonCode,
            String message, Throwable error);
    void warn(LogCategory category, String eventName, String reasonCode,
            String message, Throwable error);
    void error(LogCategory category, String eventName, String reasonCode,
            String message, Throwable error);
}
