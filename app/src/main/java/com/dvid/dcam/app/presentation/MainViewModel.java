package com.dvid.dcam.app.presentation;

import androidx.arch.core.executor.ArchTaskExecutor;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import com.dvid.dcam.app.navigation.MainScreen;
import com.dvid.dcam.core.config.domain.DcamConfig;
import com.dvid.dcam.feature.auth.application.usecase.AuthenticateOperatorUseCase;
import com.dvid.dcam.feature.auth.application.usecase.ManageOperatorUsersUseCase;
import com.dvid.dcam.feature.auth.application.usecase.ManageOperatorUsersUseCaseImpl;
import com.dvid.dcam.feature.auth.application.usecase.OperatorSessionUseCase;
import com.dvid.dcam.feature.auth.domain.LoginCredentials;
import com.dvid.dcam.feature.auth.domain.LoginResult;
import com.dvid.dcam.feature.auth.domain.OperatorSession;
import com.dvid.dcam.feature.auth.domain.UserProvisioningRequest;
import com.dvid.dcam.feature.auth.domain.UserProvisioningResult;
import com.dvid.dcam.feature.capture.application.usecase.CaptureEventUseCase;
import com.dvid.dcam.feature.capture.domain.CaptureEvent;
import com.dvid.dcam.feature.capture.domain.CaptureState;
import com.dvid.dcam.feature.capture.domain.RecordingMode;
import com.dvid.dcam.feature.device.application.usecase.RefreshDeviceStatusUseCase;
import com.dvid.dcam.feature.device.domain.DeviceStatus;
import com.dvid.dcam.feature.media.application.usecase.BrowseMediaUseCase;
import com.dvid.dcam.feature.media.domain.MediaEntry;
import com.dvid.dcam.feature.media.presentation.MediaBrowserState;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

public final class MainViewModel extends ViewModel {
    private final DcamConfig config;
    private final MutableLiveData<MainUiState> state;
    private CaptureEventUseCase captureEvents;
    private final RefreshDeviceStatusUseCase refreshDeviceStatus;
    private final BrowseMediaUseCase browseMedia;
    private final AuthenticateOperatorUseCase authenticateOperator;
    private final OperatorSessionUseCase operatorSession;
    private final ManageOperatorUsersUseCase manageUsers;
    private final BooleanSupplier authenticationEnabled;
    private final AtomicLong authenticationTransition = new AtomicLong();
    private final ExecutorService mediaIo = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "media-browser");
        thread.setDaemon(true);
        return thread;
    });
    private final AtomicInteger mediaRequestVersion = new AtomicInteger();
    private final ExecutorService authIo = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "operator-auth");
        thread.setDaemon(true);
        return thread;
    });

    MainViewModel(
            DcamConfig config,
            DeviceStatus deviceStatus,
            RefreshDeviceStatusUseCase refreshDeviceStatus,
            BrowseMediaUseCase browseMedia) {
        this.config = config;
        this.refreshDeviceStatus = refreshDeviceStatus;
        this.browseMedia = browseMedia;
        this.authenticateOperator = null;
        this.operatorSession = null;
        this.manageUsers = null;
        this.authenticationEnabled = () -> true;
        state = new MutableLiveData<>(new MainUiState(
                MainScreen.CAMERA, new CaptureState(), deviceStatus, MediaBrowserState.root(), null));
    }

    public MainViewModel(
            DcamConfig config,
            DeviceStatus deviceStatus,
            RefreshDeviceStatusUseCase refreshDeviceStatus,
            BrowseMediaUseCase browseMedia,
            AuthenticateOperatorUseCase authenticateOperator,
            OperatorSessionUseCase operatorSession,
            ManageOperatorUsersUseCase manageUsers,
            BooleanSupplier authenticationEnabled) {
        this.config = config;
        this.refreshDeviceStatus = refreshDeviceStatus;
        this.browseMedia = browseMedia;
        this.authenticateOperator = authenticateOperator;
        this.operatorSession = operatorSession;
        this.manageUsers = manageUsers;
        this.authenticationEnabled = authenticationEnabled;
        state = new MutableLiveData<>(new MainUiState(
                MainScreen.LOGIN,
                new CaptureState(),
                deviceStatus,
                MediaBrowserState.root(),
                null,
                null,
                true));
        initializeAuthentication();
    }

    public LiveData<MainUiState> state() { return state; }
    public DcamConfig getConfig() { return config; }

    public void bindCaptureEvents(CaptureEventUseCase events) {
        if (captureEvents == events) return;
        if (captureEvents != null) captureEvents.clearListener();
        captureEvents = events;
        if (captureEvents != null) captureEvents.setListener(this::onCaptureEvent);
    }

    public void unbindCaptureEvents(CaptureEventUseCase events) {
        if (captureEvents != events) return;
        captureEvents.clearListener();
        captureEvents = null;
    }

    public void onCapturePlatformReleased(CaptureEventUseCase events) {
        if (captureEvents == events && events.currentMode() != RecordingMode.IDLE) {
            onCaptureEvent(CaptureEvent.error("Recording", "camera lifecycle ended"));
        }
    }

    public void show(MainScreen screen) {
        MainUiState current = current();
        if (operatorSession != null
                && screen != MainScreen.LOGIN
                && !operatorSession.hasActiveSession()) {
            if (!authenticationEnabled.getAsBoolean()) {
                autoLoginDefaultUser(authenticationTransition.incrementAndGet(), false);
                return;
            }
            state.setValue(current.withAuthentication(null, false, MainScreen.LOGIN, null));
            return;
        }
        state.setValue(current.withScreen(screen));
        if (screen == MainScreen.FILES) openMediaFolder("");
    }

    public void loginPassword(String passwordText) {
        login(LoginCredentials.usernameAndPassword(
                ManageOperatorUsersUseCaseImpl.DEFAULT_USER_ID, passwordText));
    }

    public void login(LoginCredentials credentials) {
        if (authenticateOperator == null || current().isAuthenticationBusy()) return;
        long transition = authenticationTransition.get();
        state.setValue(current().withAuthenticationBusy(true, null));
        authIo.execute(() -> {
            LoginResult result = authenticateOperator.execute(credentials);
            postAuthentication(transition, () -> result.isSuccess()
                    ? current().withAuthentication(
                            result.getSession(), false, MainScreen.CAMERA, null)
                    : current().withAuthentication(
                            null, false, MainScreen.LOGIN, loginMessage(result)));
        });
    }

    public void provisionUser(UserProvisioningRequest request) {
        if (manageUsers == null || current().isAuthenticationBusy()) return;
        long transition = authenticationTransition.get();
        state.setValue(current().withAuthenticationBusy(true, null));
        authIo.execute(() -> {
            UserProvisioningResult result = manageUsers.upsert(request);
            String message = result.isSuccessful()
                    ? "User saved"
                    : provisioningMessage(result.getErrorCode());
            postAuthentication(transition,
                    () -> current().withAuthenticationBusy(false, message));
        });
    }

    public void logout() {
        if (operatorSession == null || current().isAuthenticationBusy()) return;
        long transition = authenticationTransition.incrementAndGet();
        state.setValue(current().withAuthenticationBusy(true, null));
        authIo.execute(() -> {
            operatorSession.logout();
            if (!authenticationEnabled.getAsBoolean()) {
                authenticateDefaultUser(transition);
                return;
            }
            postAuthentication(transition, () -> current().withAuthentication(
                    null, false, MainScreen.LOGIN, "Logged out"));
        });
    }

    public void onAuthenticationSettingChanged() {
        long transition = authenticationTransition.incrementAndGet();
        if (!authenticationEnabled.getAsBoolean()) {
            autoLoginDefaultUser(transition, true);
            return;
        }
        if (operatorSession == null || !operatorSession.hasActiveSession()) {
            state.setValue(current().withAuthentication(null, false, MainScreen.LOGIN, null));
            return;
        }
        state.setValue(current().withAuthenticationBusy(true, null));
        authIo.execute(() -> {
            operatorSession.logout();
            postAuthentication(transition, () -> current().withAuthentication(
                    null, false, MainScreen.LOGIN, "Logged out"));
        });
    }

    private void initializeAuthentication() {
        long transition = authenticationTransition.get();
        authIo.execute(() -> {
            try {
                manageUsers.ensureDefaultUser();
                if (authenticationTransition.get() != transition) return;
                if (!authenticationEnabled.getAsBoolean()) {
                    authenticateDefaultUser(authenticationTransition.get());
                    return;
                }
                OperatorSession restored = operatorSession.restore();
                postAuthentication(transition, () -> current().withAuthentication(
                        restored,
                        false,
                        restored == null ? MainScreen.LOGIN : MainScreen.CAMERA,
                        null));
            } catch (RuntimeException error) {
                postAuthentication(transition, () -> current().withAuthentication(
                        null, false, MainScreen.LOGIN, "Authentication storage unavailable"));
            }
        });
    }

    private void autoLoginDefaultUser(long transition, boolean preserveScreen) {
        if (current().isAuthenticationBusy() && !preserveScreen) return;
        MainScreen targetScreen = preserveScreen ? current().getScreen() : MainScreen.CAMERA;
        state.setValue(current().withAuthenticationBusy(true, null));
        authIo.execute(() -> {
            operatorSession.logout();
            authenticateDefaultUser(transition, targetScreen);
        });
    }

    private void authenticateDefaultUser(long transition) {
        authenticateDefaultUser(transition, MainScreen.CAMERA);
    }

    private void authenticateDefaultUser(long transition, MainScreen targetScreen) {
        try {
            LoginResult result = authenticateOperator.execute(LoginCredentials.usernameAndPassword(
                    ManageOperatorUsersUseCaseImpl.DEFAULT_USER_ID,
                    ManageOperatorUsersUseCaseImpl.DEFAULT_PASSWORD));
            postDefaultAuthentication(transition, () -> result.isSuccess()
                    ? current().withAuthentication(result.getSession(), false, targetScreen, null)
                    : current().withAuthentication(null, false, MainScreen.LOGIN, loginMessage(result)));
        } catch (RuntimeException error) {
            postDefaultAuthentication(transition, () -> current().withAuthentication(
                    null, false, MainScreen.LOGIN, "Authentication storage unavailable"));
        }
    }

    @android.annotation.SuppressLint("RestrictedApi")
    private void postAuthentication(long transition, Supplier<MainUiState> nextState) {
        ArchTaskExecutor.getInstance().postToMainThread(() -> {
            if (authenticationTransition.get() != transition) return;
            state.setValue(nextState.get());
        });
    }

    @android.annotation.SuppressLint("RestrictedApi")
    private void postDefaultAuthentication(long transition, Supplier<MainUiState> nextState) {
        ArchTaskExecutor.getInstance().postToMainThread(() -> {
            if (authenticationTransition.get() != transition
                    || authenticationEnabled.getAsBoolean()) return;
            state.setValue(nextState.get());
        });
    }

    private static String loginMessage(LoginResult result) {
        switch (result.getFailure()) {
            case USERNAME_REQUIRED:
                return "This password belongs to multiple users; username is required";
            case INVALID_INPUT:
                return "Enter a password";
            case STORAGE_ERROR:
                return "Authentication storage unavailable";
            case INVALID_CREDENTIALS:
            default:
                return "Invalid password";
        }
    }

    private static String provisioningMessage(String code) {
        if ("USER_ID_MUST_BE_SIX_DIGITS".equals(code)) return "User ID must be exactly 6 digits";
        if ("PASSWORD_REQUIRED".equals(code)) return "Password is required";
        if ("USER_OR_LOGIN_NAME_ALREADY_EXISTS".equals(code)) {
            return "User ID is already assigned";
        }
        return "Could not save user";
    }

    public void openMediaFolder(String relativePath) {
        String path = relativePath == null ? "" : relativePath;
        int requestVersion = mediaRequestVersion.incrementAndGet();
        state.setValue(current().withMediaBrowser(new MediaBrowserState(
                path, Collections.emptyList(), true, null)));
        mediaIo.execute(() -> {
            try {
                List<MediaEntry> entries = browseMedia.execute(path);
                if (requestVersion != mediaRequestVersion.get()) return;
                state.postValue(current().withMediaBrowser(
                        new MediaBrowserState(path, entries, false, null)));
            } catch (Exception error) {
                if (requestVersion != mediaRequestVersion.get()) return;
                state.postValue(current().withMediaBrowser(
                        new MediaBrowserState(path, Collections.emptyList(), false, error.getMessage())));
            }
        });
    }

    public boolean navigateMediaUp() {
        String path = current().getMediaBrowser().getRelativePath();
        if (path.isEmpty()) return false;
        int slash = path.lastIndexOf('/');
        openMediaFolder(slash < 0 ? "" : path.substring(0, slash));
        return true;
    }

    public void refreshDeviceStatus() {
        state.setValue(current().withDeviceStatus(refreshDeviceStatus.execute()));
    }

    private void onCaptureEvent(CaptureEvent event) {
        switch (event.getType()) {
            case RECORDING_STARTING:
                state.setValue(current().withCapture(
                        new CaptureState(event.getMode(), null, System.currentTimeMillis()),
                        "Recording"));
                break;
            case RECORDING_STARTED:
                CaptureState starting = current().getCapture();
                state.setValue(current().withCapture(
                        new CaptureState(event.getMode(), event.getFileName(),
                                starting.getStartedAtMillis() == null
                                        ? System.currentTimeMillis() : starting.getStartedAtMillis()),
                        "Recording"));
                break;
            case RECORDING_INTERRUPTED:
                CaptureState interrupted = current().getCapture();
                state.setValue(current().withCapture(new CaptureState(
                        interrupted.getMode(), interrupted.getCurrentFileName(),
                        interrupted.getStartedAtMillis(), false, System.currentTimeMillis()),
                        event.getMessage()));
                break;
            case RECORDING_RESUMED:
                CaptureState paused = current().getCapture();
                long resumedAt = System.currentTimeMillis();
                Long adjustedStart = paused.getStartedAtMillis();
                if (adjustedStart != null && paused.getInterruptedAtMillis() != null) {
                    adjustedStart += resumedAt - paused.getInterruptedAtMillis();
                }
                state.setValue(current().withCapture(new CaptureState(
                        paused.getMode(), paused.getCurrentFileName(), adjustedStart, false),
                        "Recording"));
                break;            case RECORDING_STOPPING:
                CaptureState recording = current().getCapture();
                state.setValue(current().withCapture(new CaptureState(
                        event.getMode(), recording.getCurrentFileName(),
                        recording.getStartedAtMillis(), true), "Saving"));
                break;
            case RECORDING_COMPLETED:
                state.setValue(current().withCapture(new CaptureState(), "Saved " + event.getFileName()));
                break;
            case RECORDING_STOPPED_FOR_STORAGE:
                state.setValue(current().withCapture(new CaptureState(),
                        "Storage stopped " + event.getFileName()));
                break;
            case PHOTO_SAVED:
                state.setValue(current().withMessage("Saved " + event.getFileName()));
                break;
            case ERROR:
                String detail = event.getMessage() == null || event.getMessage().isBlank()
                        ? "unknown error" : event.getMessage();
                state.setValue(current().withCapture(
                        new CaptureState(), event.getOperation() + " failed: " + detail));
                break;
            default:
                throw new IllegalArgumentException("Unsupported capture event " + event.getType());
        }
    }

    private MainUiState current() {
        MainUiState current = state.getValue();
        return current == null
                ? new MainUiState(MainScreen.CAMERA, new CaptureState(), DeviceStatus.unknown(),
                        MediaBrowserState.root(), null)
                : current;
    }

    @Override protected void onCleared() {
        if (captureEvents != null) captureEvents.clearListener();
        captureEvents = null;
        mediaIo.shutdownNow();
        authIo.shutdownNow();
        super.onCleared();
    }
}
