package com.dvid.dcam.app.ui;

import com.dvid.dcam.app.ui.MainScreen;
import com.dvid.dcam.feature.capture.domain.CaptureState;
import com.dvid.dcam.feature.device.domain.DeviceStatus;
import com.dvid.dcam.feature.auth.domain.OperatorSession;
import com.dvid.dcam.app.ui.media.MediaBrowserState;

/** Immutable state rendered by MainActivity. */
public final class MainUiState {
    private final MainScreen screen;
    private final CaptureState capture;
    private final DeviceStatus deviceStatus;
    private final MediaBrowserState mediaBrowser;
    private final String message;
    private final OperatorSession operatorSession;
    private final boolean authenticationBusy;
    private final boolean captureHapticSuppressed;

    public MainUiState(MainScreen screen, CaptureState capture, DeviceStatus deviceStatus,
                       MediaBrowserState mediaBrowser, String message) {
        this(screen, capture, deviceStatus, mediaBrowser, message, null, false);
    }

    public MainUiState(
            MainScreen screen,
            CaptureState capture,
            DeviceStatus deviceStatus,
            MediaBrowserState mediaBrowser,
            String message,
            OperatorSession operatorSession,
            boolean authenticationBusy) {
        this(screen, capture, deviceStatus, mediaBrowser, message, operatorSession,
                authenticationBusy, false);
    }

    private MainUiState(
            MainScreen screen,
            CaptureState capture,
            DeviceStatus deviceStatus,
            MediaBrowserState mediaBrowser,
            String message,
            OperatorSession operatorSession,
            boolean authenticationBusy,
            boolean captureHapticSuppressed) {
        this.screen = screen;
        this.capture = capture;
        this.deviceStatus = deviceStatus;
        this.mediaBrowser = mediaBrowser;
        this.message = message;
        this.operatorSession = operatorSession;
        this.authenticationBusy = authenticationBusy;
        this.captureHapticSuppressed = captureHapticSuppressed;
    }

    public MainScreen getScreen() { return screen; }
    public CaptureState getCapture() { return capture; }
    public DeviceStatus getDeviceStatus() { return deviceStatus; }
    public MediaBrowserState getMediaBrowser() { return mediaBrowser; }
    public String getMessage() { return message; }
    public OperatorSession getOperatorSession() { return operatorSession; }
    public boolean isAuthenticationBusy() { return authenticationBusy; }
    public boolean isCaptureHapticSuppressed() { return captureHapticSuppressed; }

    public MainUiState withScreen(MainScreen next) {
        return copy(next, capture, deviceStatus, mediaBrowser, message, operatorSession,
                authenticationBusy, captureHapticSuppressed);
    }

    public MainUiState withCapture(CaptureState next) {
        return copy(screen, next, deviceStatus, mediaBrowser, message, operatorSession,
                authenticationBusy, captureHapticSuppressed);
    }

    public MainUiState withCapture(CaptureState next, String nextMessage) {
        return copy(screen, next, deviceStatus, mediaBrowser, nextMessage, operatorSession,
                authenticationBusy, captureHapticSuppressed);
    }

    public MainUiState withCapture(
            CaptureState next, String nextMessage, boolean hapticSuppressed) {
        return copy(screen, next, deviceStatus, mediaBrowser, nextMessage, operatorSession,
                authenticationBusy, hapticSuppressed);
    }

    public MainUiState withMessage(String nextMessage) {
        return copy(screen, capture, deviceStatus, mediaBrowser, nextMessage, operatorSession,
                authenticationBusy, captureHapticSuppressed);
    }

    public MainUiState withMessage(String nextMessage, boolean hapticSuppressed) {
        return copy(screen, capture, deviceStatus, mediaBrowser, nextMessage, operatorSession,
                authenticationBusy, hapticSuppressed);
    }

    public MainUiState withDeviceStatus(DeviceStatus nextStatus) {
        return copy(screen, capture, nextStatus, mediaBrowser, message, operatorSession,
                authenticationBusy, captureHapticSuppressed);
    }

    public MainUiState withMediaBrowser(MediaBrowserState nextBrowser) {
        return copy(screen, capture, deviceStatus, nextBrowser, message, operatorSession,
                authenticationBusy, captureHapticSuppressed);
    }

    public MainUiState withAuthentication(
            OperatorSession nextSession,
            boolean busy,
            MainScreen nextScreen,
            String nextMessage) {
        return copy(nextScreen, capture, deviceStatus, mediaBrowser, nextMessage, nextSession, busy,
                captureHapticSuppressed);
    }

    public MainUiState withAuthenticationBusy(boolean busy, String nextMessage) {
        return copy(screen, capture, deviceStatus, mediaBrowser, nextMessage, operatorSession, busy,
                captureHapticSuppressed);
    }

    private static MainUiState copy(
            MainScreen screen,
            CaptureState capture,
            DeviceStatus deviceStatus,
            MediaBrowserState mediaBrowser,
            String message,
            OperatorSession operatorSession,
            boolean authenticationBusy,
            boolean captureHapticSuppressed) {
        return new MainUiState(screen, capture, deviceStatus, mediaBrowser, message,
                operatorSession, authenticationBusy, captureHapticSuppressed);
    }
}
