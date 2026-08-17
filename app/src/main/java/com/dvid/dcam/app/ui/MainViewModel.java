package com.dvid.dcam.app.ui;

import androidx.arch.core.executor.ArchTaskExecutor;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import com.dvid.dcam.app.ui.MainScreen;
import com.dvid.dcam.feature.auth.application.usecase.AuthenticateOperatorUseCase;
import com.dvid.dcam.feature.auth.application.usecase.ManageOperatorUsersUseCase;
import com.dvid.dcam.feature.auth.application.usecase.OperatorSessionUseCase;
import com.dvid.dcam.feature.auth.domain.LoginCredentials;
import com.dvid.dcam.feature.auth.domain.LoginResult;
import com.dvid.dcam.feature.auth.domain.OperatorSession;
import com.dvid.dcam.feature.auth.domain.UserProvisioningRequest;
import com.dvid.dcam.feature.auth.domain.UserProvisioningResult;
import com.dvid.dcam.feature.capture.application.usecase.CaptureEvents;
import com.dvid.dcam.feature.capture.domain.CaptureEvent;
import com.dvid.dcam.feature.capture.domain.CaptureState;
import com.dvid.dcam.feature.capture.domain.RecordingMode;
import com.dvid.dcam.feature.device.application.usecase.RefreshDeviceStatusUseCase;
import com.dvid.dcam.feature.device.domain.DeviceStatus;
import com.dvid.dcam.feature.media.application.usecase.BrowseMediaUseCase;
import com.dvid.dcam.feature.media.domain.MediaEntry;
import com.dvid.dcam.app.ui.media.MediaBrowserState;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

public final class MainViewModel extends ViewModel {
    private final MutableLiveData<MainUiState> state;
    private CaptureEvents captureEvents;
    private volatile long lastSavedNoticeAtMillis;
    private final RefreshDeviceStatusUseCase refreshDeviceStatus;
    private final BrowseMediaUseCase browseMedia;
    private final AuthenticateOperatorUseCase authenticateOperator;
    private final OperatorSessionUseCase operatorSession;
    private final ManageOperatorUsersUseCase manageUsers;
    private final BooleanSupplier authenticationEnabled;
    private final BooleanSupplier mediaBrowserEnabled;
    private final AtomicLong authenticationTransition = new AtomicLong();
    private final ExecutorService mediaIo = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "media-browser");
        thread.setDaemon(true);
        return thread;
    });
    private final AtomicInteger mediaRequestVersion = new AtomicInteger();
    private volatile Future<?> mediaTask;
    private final ExecutorService authIo = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "operator-auth");
        thread.setDaemon(true);
        return thread;
    });

    MainViewModel(
            DeviceStatus deviceStatus,
            RefreshDeviceStatusUseCase refreshDeviceStatus,
            BrowseMediaUseCase browseMedia) {
        this(deviceStatus, refreshDeviceStatus, browseMedia, () -> true);
    }

    MainViewModel(
            DeviceStatus deviceStatus,
            RefreshDeviceStatusUseCase refreshDeviceStatus,
            BrowseMediaUseCase browseMedia,
            BooleanSupplier mediaBrowserEnabled) {
        this.refreshDeviceStatus = refreshDeviceStatus;
        this.browseMedia = browseMedia;
        this.authenticateOperator = null;
        this.operatorSession = null;
        this.manageUsers = null;
        this.authenticationEnabled = () -> true;
        this.mediaBrowserEnabled = mediaBrowserEnabled;
        state = new MutableLiveData<>(new MainUiState(
                MainScreen.CAMERA, new CaptureState(), deviceStatus, MediaBrowserState.root(), null));
    }

    public MainViewModel(
            DeviceStatus deviceStatus,
            RefreshDeviceStatusUseCase refreshDeviceStatus,
            BrowseMediaUseCase browseMedia,
            AuthenticateOperatorUseCase authenticateOperator,
            OperatorSessionUseCase operatorSession,
            ManageOperatorUsersUseCase manageUsers,
            BooleanSupplier authenticationEnabled,
            BooleanSupplier mediaBrowserEnabled) {
        this.refreshDeviceStatus = refreshDeviceStatus;
        this.browseMedia = browseMedia;
        this.authenticateOperator = authenticateOperator;
        this.operatorSession = operatorSession;
        this.manageUsers = manageUsers;
        this.authenticationEnabled = authenticationEnabled;
        this.mediaBrowserEnabled = mediaBrowserEnabled;
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

    public long lastSavedNoticeAtMillis() { return lastSavedNoticeAtMillis; }

    public void bindCaptureEvents(CaptureEvents events) {
        if (captureEvents == events) return;
        if (captureEvents != null) captureEvents.clearListener();
        captureEvents = events;
        if (captureEvents != null) captureEvents.setListener(this::onCaptureEvent);
    }

    public void unbindCaptureEvents(CaptureEvents events) {
        if (captureEvents != events) return;
        captureEvents.clearListener();
        captureEvents = null;
    }

    public void onCapturePlatformReleased(CaptureEvents events) {
        if (captureEvents == events && events.currentMode() != RecordingMode.IDLE) {
            onCaptureEvent(CaptureEvent.error("Recording", "camera lifecycle ended"));
        }
    }

    public void show(MainScreen screen) {
        MainUiState current = current();
        if (screen == MainScreen.FILES && !mediaBrowserEnabled.getAsBoolean()) return;
        if (operatorSession != null
                && screen != MainScreen.LOGIN
                && !operatorSession.hasActiveSession()) {
            if (!authenticationEnabled.getAsBoolean()) {
                if (!current.isAuthenticationBusy())
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
                ManageOperatorUsersUseCase.DEFAULT_USER_ID, passwordText));
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
            if (!authenticationEnabled.getAsBoolean()) {
                authenticateDefaultUser(transition);
                return;
            }
            operatorSession.logout();
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
        MainUiState initial = current();
        if (initial.isAuthenticationBusy() && !preserveScreen) return;
        boolean sessionActive = operatorSession != null && operatorSession.hasActiveSession();
        if (!sessionActive) {
            state.setValue(initial.withAuthentication(null, true, MainScreen.LOGIN, null));
        }
        authIo.execute(() -> authenticateDefaultUser(
                transition, preserveScreen ? initial.getScreen() : MainScreen.CAMERA,
                preserveScreen && sessionActive));
    }

    private void authenticateDefaultUser(long transition) {
        authenticateDefaultUser(transition, MainScreen.CAMERA, false);
    }

    private void authenticateDefaultUser(
            long transition, MainScreen targetScreen, boolean preserveCurrentScreen) {
        try {
            LoginResult result = authenticateOperator.execute(LoginCredentials.usernameAndPassword(
                    ManageOperatorUsersUseCase.DEFAULT_USER_ID,
                    ManageOperatorUsersUseCase.DEFAULT_PASSWORD));
            postDefaultAuthentication(transition, () -> {
                MainUiState current = current();
                MainScreen nextScreen = preserveCurrentScreen ? current.getScreen() : targetScreen;
                if (result.isSuccess()) {
                    return current.withAuthentication(
                            result.getSession(), false, nextScreen, null);
                }
                return defaultAuthenticationFailure(nextScreen, loginMessage(result));
            });
        } catch (RuntimeException error) {
            postDefaultAuthentication(transition, () -> defaultAuthenticationFailure(
                    preserveCurrentScreen ? current().getScreen() : targetScreen,
                    "Authentication storage unavailable"));
        }
    }

    private MainUiState defaultAuthenticationFailure(MainScreen targetScreen, String message) {
        OperatorSession active = operatorSession == null ? null : operatorSession.current();
        return current().withAuthentication(active, false,
                active == null ? MainScreen.LOGIN : targetScreen, message);
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
        if (!mediaBrowserEnabled.getAsBoolean()) return;
        String path = relativePath == null ? "" : relativePath;
        int requestVersion = mediaRequestVersion.incrementAndGet();
        Future<?> previousTask = mediaTask;
        if (previousTask != null) previousTask.cancel(true);
        state.setValue(current().withMediaBrowser(new MediaBrowserState(
                path, Collections.emptyList(), true, null)));
        mediaTask = mediaIo.submit(() -> {
            try {
                List<MediaEntry> entries = browseMedia.executeWithoutCounts(path);
                if (requestVersion != mediaRequestVersion.get()) return;
                state.postValue(current().withMediaBrowser(
                        new MediaBrowserState(path, entries, false, null)));
                boolean countsPending = entries.stream().anyMatch(entry ->
                        entry.isDirectory() && !entry.hasChildFileCount());
                if (!countsPending) {
                    if (path.isEmpty()) warmMediaCounts(entries, requestVersion);
                    return;
                }
                List<MediaEntry> countedEntries = browseMedia.execute(path);
                if (requestVersion != mediaRequestVersion.get()) return;
                state.postValue(current().withMediaBrowser(
                        new MediaBrowserState(path, countedEntries, false, null)));
            } catch (Exception error) {
                if (requestVersion != mediaRequestVersion.get()
                        || error instanceof InterruptedException) return;
                state.postValue(current().withMediaBrowser(
                        new MediaBrowserState(path, Collections.emptyList(), false, error.getMessage())));
            }
        });
    }

    private void warmMediaCounts(List<MediaEntry> roots, int requestVersion)
            throws InterruptedException {
        for (MediaEntry root : roots) {
            if (requestVersion != mediaRequestVersion.get()) return;
            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedException("Media prewarm cancelled");
            }
            if (!root.isDirectory()) continue;
            try {
                browseMedia.execute(root.getRelativePath());
            } catch (InterruptedException interrupted) {
                throw interrupted;
            } catch (Exception ignored) { }
        }
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
                state.setValue(current().withMessage("Starting", event.isReplay()));
                break;
            case RECORDING_START_CANCELLED:
                state.setValue(current().withCapture(
                        current().getCapture().withoutVideo(), null, event.isReplay()));
                break;
            case RECORDING_STARTED:
                CaptureState starting = current().getCapture();
                state.setValue(current().withCapture(starting.withVideo(
                        event.getMode(), event.getFileName(),
                        starting.getStartedAtMillis() == null
                                ? System.currentTimeMillis() : starting.getStartedAtMillis(),
                        false, null), "Recording", event.isReplay()));
                break;
            case RECORDING_INTERRUPTED:
                CaptureState interrupted = current().getCapture();
                state.setValue(current().withCapture(interrupted.withVideo(
                        interrupted.getMode(), interrupted.getCurrentFileName(),
                        interrupted.getStartedAtMillis(), false, System.currentTimeMillis()),
                        event.getMessage(), event.isReplay()));
                break;
            case RECORDING_RESUMED:
                CaptureState paused = current().getCapture();
                long resumedAt = System.currentTimeMillis();
                Long adjustedStart = paused.getStartedAtMillis();
                if (adjustedStart != null && paused.getInterruptedAtMillis() != null) {
                    adjustedStart += resumedAt - paused.getInterruptedAtMillis();
                }
                state.setValue(current().withCapture(paused.withVideo(
                        paused.getMode(), paused.getCurrentFileName(), adjustedStart, false, null),
                        "Recording", event.isReplay()));
                break;
            case RECORDING_STOPPING:
                CaptureState recording = current().getCapture();
                state.setValue(current().withCapture(recording.withVideo(
                        event.getMode(), recording.getCurrentFileName(),
                        recording.getStartedAtMillis(), true, null), "Saving",
                        event.isReplay()));
                break;
            case RECORDING_COMPLETED:
                if (!event.isReplay()) lastSavedNoticeAtMillis = System.currentTimeMillis();
                state.setValue(current().withCapture(current().getCapture().withoutVideo(),
                        "Saved " + event.getFileName(), event.isReplay()));
                break;
            case RECORDING_STOPPED_FOR_STORAGE:
                state.setValue(current().withCapture(current().getCapture().withoutVideo(),
                        "Storage stopped " + event.getFileName(), event.isReplay()));
                break;
            case AUDIO_RECORDING_STARTED:
                MainUiState audioStarting = current();
                Long audioStartedAt = event.getStartedAtMillis();
                state.setValue(audioStarting.withCapture(audioStarting.getCapture().withAudio(true,
                                audioStartedAt == null || audioStartedAt < 0L
                                        ? System.currentTimeMillis() : audioStartedAt),
                        audioStarting.getMessage(), event.isReplay()));
                break;
            case AUDIO_RECORDING_STOPPING:
                MainUiState audioStopping = current();
                state.setValue(audioStopping.withCapture(
                        audioStopping.getCapture().withAudio(false, null, true), "Saving",
                        event.isReplay()));
                break;
            case AUDIO_RECORDING_STOPPED:
                MainUiState audioStopped = current();
                String audioMessage = event.getFileName() == null
                        ? audioStopped.getMessage() : "Saved " + event.getFileName();
                if (event.getFileName() != null && !event.isReplay())
                    lastSavedNoticeAtMillis = System.currentTimeMillis();
                state.setValue(audioStopped.withCapture(
                        audioStopped.getCapture().withAudio(false, null), audioMessage,
                        event.isReplay()));
                break;
            case PHOTO_SAVING:
                MainUiState photoSaving = current();
                state.setValue(photoSaving.withCapture(
                        photoSaving.getCapture().withPhotoSaving(true), "Saving",
                        event.isReplay()));
                break;
            case PHOTO_SAVED:
                MainUiState photoSaved = current();
                if (!event.isReplay()) lastSavedNoticeAtMillis = System.currentTimeMillis();
                state.setValue(photoSaved.withCapture(
                        photoSaved.getCapture().withPhotoSaving(false),
                        "Saved " + event.getFileName(), event.isReplay()));
                break;
            case PHOTO_FAILED:
                MainUiState photoFailed = current();
                String photoDetail = event.getMessage() == null || event.getMessage().isBlank()
                        ? "unknown error" : event.getMessage();
                state.setValue(photoFailed.withCapture(
                        photoFailed.getCapture().withPhotoSaving(false),
                        event.getOperation() + " failed: " + photoDetail, event.isReplay()));
                break;
            case ERROR:
                String detail = event.getMessage() == null || event.getMessage().isBlank()
                        ? "unknown error" : event.getMessage();
                state.setValue(current().withCapture(
                        current().getCapture().withoutVideo(),
                        event.getOperation() + " failed: " + detail, event.isReplay()));
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
