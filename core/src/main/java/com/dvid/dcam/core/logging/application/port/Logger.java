package com.dvid.dcam.core.logging.application.port;

/** Local-first logger capability. Cloud upload remains an adapter concern. */
public interface Logger {
    void debug(String message);
    void info(String message);
    void info(String message, Throwable error);
    void warn(String message, Throwable error);
    void error(String message, Throwable error);
}
