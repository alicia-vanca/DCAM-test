package com.dvid.dcam.app;

import com.dvid.dcam.feature.settings.domain.AppLanguage;
import com.dvid.dcam.feature.settings.application.usecase.LanguageSettingsUseCase;
import com.dvid.dcam.feature.settings.application.usecase.MediaEncryptionSettingsUseCase;
import android.Manifest;
import android.app.AlertDialog;
import android.app.role.RoleManager;
import android.content.Context;
import android.content.BroadcastReceiver;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.content.SharedPreferences;
import android.database.ContentObserver;
import android.graphics.Color;
import android.hardware.display.DisplayManager;
import android.location.LocationManager;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.Settings;
import android.view.Display;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.EditText;
import android.text.InputFilter;
import android.text.InputType;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import androidx.activity.ComponentActivity;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;
import com.dvid.dcam.BuildConfig;
import com.dvid.dcam.R;
import com.dvid.dcam.app.devmode.DeveloperFeatureToggles;
import com.dvid.dcam.app.resourcemonitor.ResourceMonitorController;
import com.dvid.dcam.app.ui.ActivityChromeController;
import com.dvid.dcam.app.ui.camera.CameraFlowCoordinator;
import com.dvid.dcam.app.ui.FloatingNotice;
import com.dvid.dcam.app.ui.MediaBrowserRenderer;
import com.dvid.dcam.app.ui.RecordingStatusRenderer;
import com.dvid.dcam.app.ui.MainScreen;
import com.dvid.dcam.app.ui.MainMenuModel;
import com.dvid.dcam.app.ui.MainMenuTile;
import com.dvid.dcam.app.ui.GpsPermissionRetryScheduler;
import com.dvid.dcam.app.ui.MainUiState;
import com.dvid.dcam.app.ui.MainViewModel;
import com.dvid.dcam.app.ui.MainViewModelFactory;
import com.dvid.dcam.app.ui.LocationTrackingCoordinator;
import com.dvid.dcam.core.featuregate.domain.FeatureGate;
import com.dvid.dcam.databinding.ActivityMainBinding;
import com.dvid.dcam.databinding.ScreenCameraBinding;
import com.dvid.dcam.databinding.ScreenDeveloperUsersBinding;
import com.dvid.dcam.databinding.ScreenFileExplorerBinding;
import com.dvid.dcam.databinding.ScreenLoginBinding;
import com.dvid.dcam.databinding.ScreenMenuBinding;
import com.dvid.dcam.databinding.ScreenSettingsDetailBinding;
import com.dvid.dcam.feature.auth.domain.UserProvisioningRequest;
import com.dvid.dcam.feature.auth.domain.UserSource;
import com.dvid.dcam.feature.location.application.usecase.LocationControlUseCase;
import com.dvid.dcam.feature.location.application.usecase.LocationSettingsUseCase;
import com.dvid.dcam.feature.location.application.usecase.LocationTrackingUseCase;
import com.dvid.dcam.feature.location.domain.GpsCoordinate;
import com.dvid.dcam.feature.location.domain.GpsMode;
import com.dvid.dcam.feature.location.domain.GpsSettings;
import com.dvid.dcam.feature.location.domain.LocationSystemState;
import com.dvid.dcam.feature.location.domain.LocationTrackingState;
import com.dvid.dcam.feature.media.application.usecase.OpenMediaUseCase;
import com.dvid.dcam.feature.storage.application.usecase.StorageSettingsUseCase;
import com.dvid.dcam.feature.storage.domain.MediaPartitionLocation;
import com.dvid.dcam.feature.storage.domain.StorageVolumeStatus;
import com.dvid.dcam.feature.device.application.usecase.DeviceSerialNumberUseCase;
import com.dvid.dcam.feature.device.application.port.DeveloperSettingsStore;
import com.dvid.dcam.app.ui.settings.SettingsUiState;
import com.dvid.dcam.app.ui.settings.StorageOptionUiState;
import com.dvid.dcam.app.ui.settings.DeveloperButtonBindingsScreen;
import com.dvid.dcam.app.ui.settings.DescribedRadioOptionUiState;
import com.dvid.dcam.app.ui.settings.SettingId;
import com.dvid.dcam.app.ui.settings.SettingItem;
import com.dvid.dcam.app.ui.settings.SettingsControlRenderer;

import com.dvid.dcam.app.ui.settings.camera.CameraDeveloperSettingsPresentation;
import com.dvid.dcam.app.ui.settings.camera.CameraSettingControlId;
import com.dvid.dcam.app.ui.settings.SettingsScreenModel;
import com.dvid.dcam.app.ui.settings.SettingsSection;
import com.dvid.dcam.core.input.domain.HardwareButtonLayout;
import com.dvid.dcam.app.ui.input.HardwareButtonRouter;
import com.dvid.dcam.core.logging.application.port.Logger;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.function.Consumer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/** Android entry point and ViewBinding presentation shell. */
public final class MainActivity extends ComponentActivity {
    private static final int DEV_MODE_UNLOCK_TAPS = 7;
    private static final long DEV_MODE_UNLOCK_WINDOW_MS = 5_000L;
    private static final long GPS_PERMISSION_RETRY_DELAY_MS = 5 * 60_000L;
    private static final long CAPTURE_VIBRATION_MS = 150L;
    private static final long SAVED_NOTICE_MAX_AGE_MS = FloatingNotice.TRANSIENT_DURATION_MS;
    private static final long SCREEN_OFF_CAMERA_RELEASE_DELAY_MS = 60_000L;
    private static int activityInstanceCount;
    private static final int CAPTURE_VIBRATION_AMPLITUDE = 255;
    private static final List<DeveloperSettingsStore.Mode> CAMERA_PIPELINE_MODES = List.of(
            DeveloperSettingsStore.Mode.AUTO, DeveloperSettingsStore.Mode.A,
            DeveloperSettingsStore.Mode.B);
    private static final String STATE_DEFAULT_HOME_REQUESTED = "default_home_requested";
    private static final String STATE_CAPTURE_PERMISSION_FLOW_STARTED = "capture_permission_flow_started";
    private static final String STATE_CAPTURE_PERMISSION_POLICY_APPLIED = "capture_permission_policy_applied";
    private static final String STATE_CAPTURE_PERMISSION_REQUEST_IN_FLIGHT = "capture_permission_request_in_flight";
    private static final String STATE_CAPTURE_PERMISSION_SETTINGS_IN_FLIGHT = "capture_permission_settings_in_flight";
    private static final String STATE_STARTUP_GPS_PERMISSION_HANDLED = "startup_gps_permission_handled";
    private static final String STATE_DEVICE_IDENTITY_PENDING = "device_identity_pending";
    private static final float RECORDING_BADGE_ACTIVE_ALPHA = 1f;
    private final Handler cameraClock = new Handler(Looper.getMainLooper());
    private final ExecutorService identityIoExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "dcam-identity-io");
        thread.setDaemon(true);
        return thread;
    });
    private final Runnable cameraClockTick = new Runnable() {
        @Override
        public void run() {
            recordingStatusRenderer.updateCameraClock();
            activityChrome.updateManagedTopBar();
            updateStorageWarning();
            cameraClock.postDelayed(this, 1_000L);
        }
    };
    private long nextRecordingDurationTickAtMillis;
    private long idleScreenOffCameraReleaseAtMillis;
    private final Runnable recordingDurationTick = new Runnable() {
        @Override
        public void run() {
            if (!hasActiveRecording(latestState)) return;
            recordingStatusRenderer.tickRecordingDurations();
            nextRecordingDurationTickAtMillis += 1_000L;
            long nowMillis = SystemClock.uptimeMillis();
            if (nextRecordingDurationTickAtMillis <= nowMillis) {
                nextRecordingDurationTickAtMillis = nowMillis + 1_000L;
            }
            cameraClock.postAtTime(this, nextRecordingDurationTickAtMillis);
        }
    };
    private final Runnable cameraSwitchActionRefresh = this::updateCameraSwitchAction;
    private final Runnable idleScreenOffCameraRelease = this::releaseIdleCameraIfScreenOffNow;
    private FrameLayout root;
    private ActivityMainBinding activityBinding;
    private AppComposition composition;
    private AppComposition.CameraCapabilityRecheckSubscription cameraCapabilityRecheckSubscription;
    private CameraFlowCoordinator.StateSubscription idleCameraReleaseStateSubscription;
    private Logger logger;
    private AppComposition.CaptureRuntime captureRuntime;
    private AppComposition.CaptureRuntime refreshedDisplayRotationRuntime;
    private int refreshedDisplayRotation = -1;
    private boolean recordingRotationLocked;
    private MainViewModel viewModel;
    private HardwareButtonRouter hardwareButtons;
    private boolean redirectingToHomeTask;
    private boolean firmwareHardwareButtonReceiverRegistered;
    private volatile boolean screenOff;
    private OpenMediaUseCase openMedia;
    private LanguageSettingsUseCase languageSettings;
    private DeviceSerialNumberUseCase deviceSerialNumbers;
    private DeveloperFeatureToggles developerFeatureToggles;
    private DeveloperButtonBindingsScreen developerButtonBindingsScreen;
    private MediaEncryptionSettingsUseCase mediaEncryptionSettings;
    private StorageSettingsUseCase storageSettings;
    private LocationSettingsUseCase locationSettings;
    private LocationControlUseCase locationControl;
    private LocationTrackingUseCase locationTracking;
    private LocationTrackingCoordinator locationTrackingCoordinator;
    private GpsCoordinate currentGpsCoordinate;
    private LocationTrackingState locationTrackingState = LocationTrackingState.STOPPED;
    private SettingsControlRenderer settingsRenderer;
    private SettingsUiState settingsUiState;
    private AppComposition.AndroidRuntime androidRuntime;
    private MainMenuModel menuModel;
    private ActivityResultLauncher<String[]> permissionLauncher;
    private ActivityResultLauncher<Intent> capturePermissionSettingsLauncher;
    private ActivityResultLauncher<String[]> locationPermissionLauncher;
    private ActivityResultLauncher<String[]> legacyStoragePermissionLauncher;
    private ActivityResultLauncher<Intent> legacyStorageSettingsLauncher;
    private ActivityResultLauncher<Intent> defaultHomeLauncher;
    private ActivityResultLauncher<Intent> allFilesAccessLauncher;
    private boolean capturePermissionFlowStarted;
    private boolean capturePermissionPolicyRequestInFlight;
    private boolean capturePermissionPolicyApplied;
    private boolean capturePermissionRequestInFlight;
    private boolean capturePermissionSettingsRequestInFlight;
    private boolean capturePermissionDialogShowing;
    private boolean locationPermissionRequestInFlight;
    private boolean locationPermissionSettingsFallbackPending;
    private GpsPermissionRetryScheduler gpsPermissionRetryScheduler;
    private boolean startupGpsPermissionHandled;
    private boolean identityCheckInFlight;
    private boolean identityCheckComplete;
    private boolean identityInteractionReady;
    private boolean identityRestored;
    private Exception identityRestoreError;
    private boolean identityRestoreErrorDialogShowing;
    private boolean serialNumberDialogShowing;
    private boolean allFilesAccessRequestInFlight;
    private boolean allFilesAccessDialogShowing;
    private boolean legacyStorageAccessDialogShowing;
    private boolean legacyStoragePermissionRequestInFlight;
    private boolean legacyStorageSettingsRequestInFlight;
    private boolean defaultHomeRequestStarted;
    private Boolean pendingLocationEnabled;
    private ActivityChromeController activityChrome;
    private ResourceMonitorController resourceMonitorController;
    private RecordingStatusRenderer recordingStatusRenderer;
    private MediaBrowserRenderer mediaBrowserRenderer;
    private ContentObserver autoRotateObserver;
    private DisplayManager displayManager;
    private boolean writeSettingsRequestInFlight;
    private Boolean pendingAutoRotateValue;
    private AlertDialog writeSettingsDialog;
    private AlertDialog cameraCapabilityResultDialog;
    private MainUiState latestState;
    private MainScreen renderedScreen;
    private MainScreen operationLoggedScreen;
    private ScreenCameraBinding cameraScreen;
    private ScreenLoginBinding loginScreen;
    private ScreenDeveloperUsersBinding developerUsersScreen;
    private ScreenFileExplorerBinding fileExplorerScreen;
    private ScreenMenuBinding menuScreen;
    private LinearLayout renderedSettingsList;
    private ScrollView renderedSettingsScroll;
    private AppLanguage[] renderedLanguages = new AppLanguage[0];
    private int devModeTapCount;
    private long devModeTapWindowStartedAtMs;
    private Boolean storageWarningVisible;
    private final AtomicBoolean storageWarningRefreshInFlight = new AtomicBoolean();
    private List<StorageVolumeStatus> storageVolumes = List.of();
    private boolean storageVolumesStale = true;
    private long storageVolumesGeneration;
    private final AtomicBoolean storageVolumesRefreshInFlight = new AtomicBoolean();

    private Runnable sosHoldAction;
    private final DisplayManager.DisplayListener displayRotationListener = new DisplayManager.DisplayListener() {
        @Override
        public void onDisplayAdded(int displayId) {
        }

        @Override
        public void onDisplayRemoved(int displayId) {
        }

        @Override
        public void onDisplayChanged(int displayId) {
            Display display = getDisplay();
            if (display == null || display.getDisplayId() != displayId)
                return;
            refreshDisplayRotationIfNeeded(display);
        }
    };
    private final BroadcastReceiver wifiStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            int state = intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE, WifiManager.WIFI_STATE_UNKNOWN);
            if (state == WifiManager.WIFI_STATE_ENABLED || state == WifiManager.WIFI_STATE_DISABLED) {
                refreshWifiSetting();
            }
        }
    };
    private final BroadcastReceiver locationModeChangedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null) return;
            String action = intent.getAction();
            if (LocationManager.MODE_CHANGED_ACTION.equals(action)
                    || LocationManager.PROVIDERS_CHANGED_ACTION.equals(action)) {
                refreshLocationTracking();
                refreshDeveloperSettingsRows();
            }
        }
    };
    private final BroadcastReceiver storageMountedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            storageVolumesGeneration++;
            storageVolumesStale = true;
            composition.refreshCaptureStorageNotice();
            if (latestState == null) return;
            if (latestState.getScreen() == MainScreen.STORAGE_SETTINGS) {
                refreshCurrentSettingsControls();
            } else if (latestState.getScreen() == MainScreen.FILES) {
                viewModel.openMediaFolder(latestState.getMediaBrowser().getRelativePath());
            }
        }
    };
    private final BroadcastReceiver screenStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null) return;
            if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
                screenOff = true;
                restartIdleCameraReleaseDelayIfScreenOff();
            } else if (Intent.ACTION_SCREEN_ON.equals(intent.getAction())) {
                screenOff = false;
                cancelIdleCameraRelease();
                if (composition != null) composition.bindCameraIfPermitted();
            }
        }
    };
    private final BroadcastReceiver firmwareHardwareButtonReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null || hardwareButtons == null)
                return;
            if (renderedScreen == MainScreen.DEVELOPER_BUTTON_BINDINGS) {
                hardwareButtons.clearTransientState();
                return;
            }
            String action = intent.getAction();
            if (action == null)
                return;
            long eventTimeMs = SystemClock.uptimeMillis();
            if (!handleFirmwareBroadcastDown(action, eventTimeMs)) {
                handleFirmwareBroadcastUp(action, false);
            }
        }
    };
    private void releaseIdleCameraIfScreenOffNow() {
        if (androidRuntime == null) return;
        screenOff = !androidRuntime.isScreenInteractive();
        idleScreenOffCameraReleaseAtMillis = 0L;
        if (!screenOff || composition == null) return;
        if (composition.releaseIdleCameraForScreenOff()) cancelIdleCameraRelease();
    }

    private void requestIdleCameraReleaseIfScreenOff() {
        if (!screenOff) {
            cancelIdleCameraRelease();
            return;
        }
        if (idleScreenOffCameraReleaseAtMillis == 0L) {
            idleScreenOffCameraReleaseAtMillis =
                    SystemClock.uptimeMillis() + SCREEN_OFF_CAMERA_RELEASE_DELAY_MS;
        }
        cameraClock.removeCallbacks(idleScreenOffCameraRelease);
        cameraClock.postAtTime(idleScreenOffCameraRelease,
                idleScreenOffCameraReleaseAtMillis);
    }

    private void restartIdleCameraReleaseDelayIfScreenOff() {
        idleScreenOffCameraReleaseAtMillis = 0L;
        requestIdleCameraReleaseIfScreenOff();
    }

    private void restartIdleCameraReleaseDelayAfterHardwareCommandIfScreenOff() {
        if (androidRuntime != null) {
            screenOff = !androidRuntime.isScreenInteractive();
        }
        restartIdleCameraReleaseDelayIfScreenOff();
    }

    private void cancelIdleCameraRelease() {
        cameraClock.removeCallbacks(idleScreenOffCameraRelease);
        idleScreenOffCameraReleaseAtMillis = 0L;
    }

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(AppComposition.localizedContext(newBase));
    }

    private static boolean isHomeLaunch(Intent intent) {
        return intent != null
                && Intent.ACTION_MAIN.equals(intent.getAction())
                && intent.hasCategory(Intent.CATEGORY_HOME);
    }

    private boolean redirectToHomeTaskIfNeeded() {
        if (isHomeLaunch(getIntent())) return false;
        redirectingToHomeTask = true;
        startActivity(new Intent(this, DcamLauncherActivity.class));
        finish();
        return true;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (redirectToHomeTaskIfNeeded()) return;
        activityInstanceCount++;
        defaultHomeRequestStarted = savedInstanceState != null
                && savedInstanceState.getBoolean(STATE_DEFAULT_HOME_REQUESTED);
        capturePermissionFlowStarted = savedInstanceState != null
                && savedInstanceState.getBoolean(STATE_CAPTURE_PERMISSION_FLOW_STARTED);
        capturePermissionPolicyApplied = savedInstanceState != null
                && savedInstanceState.getBoolean(STATE_CAPTURE_PERMISSION_POLICY_APPLIED);
        capturePermissionRequestInFlight = savedInstanceState != null
                && savedInstanceState.getBoolean(STATE_CAPTURE_PERMISSION_REQUEST_IN_FLIGHT);
        capturePermissionSettingsRequestInFlight = savedInstanceState != null
                && savedInstanceState.getBoolean(STATE_CAPTURE_PERMISSION_SETTINGS_IN_FLIGHT);
        boolean identityPending = savedInstanceState != null
                && savedInstanceState.getBoolean(STATE_DEVICE_IDENTITY_PENDING);
        startupGpsPermissionHandled = savedInstanceState != null
                && savedInstanceState.getBoolean(STATE_STARTUP_GPS_PERMISSION_HANDLED)
                && !identityPending;
        composition = AppComposition.create(this);
        logger = composition.logger();
        androidRuntime = composition.androidRuntime();
        screenOff = !androidRuntime.isScreenInteractive();
        idleCameraReleaseStateSubscription = composition.observeCameraStateChanges(() -> {
            requestIdleCameraReleaseIfScreenOff();
            cameraClock.post(this::syncRecordingRotationLock);
        });
        languageSettings = composition.languageSettingsUseCase();
        deviceSerialNumbers = composition.deviceSerialNumberUseCase();
        developerFeatureToggles = composition.developerFeatureToggles();
        developerButtonBindingsScreen = new DeveloperButtonBindingsScreen(
                this,
                composition.configureHardwareButtonsUseCase(),
                this::updateHardwareButtonLayout,
                message -> FloatingNotice.show(this, message));
        mediaEncryptionSettings = composition.mediaEncryptionSettings();
        storageSettings = composition.storageSettingsUseCase();
        locationSettings = composition.locationSettingsUseCase();
        locationControl = composition.locationControlUseCase();
        locationTracking = composition.locationTrackingUseCase();
        locationTrackingCoordinator = new LocationTrackingCoordinator(
                locationSettings,
                locationControl,
                locationTracking,
                () -> developerFeatureToggles.isEffectivelyEnabled(FeatureGate.GPS),
                () -> androidRuntime.fineLocationPermissionGranted(),
                this::onLocationTrackingStateChanged);
        settingsRenderer = new SettingsControlRenderer(this);
        settingsUiState = new SettingsUiState(mediaEncryptionSettings.isMediaEncryptionEnabled(),
                storageSettings.supportedModes().indexOf(storageSettings.currentMode()),
                androidRuntime.isAutoRotateEnabled(),
                androidRuntime.isWifiEnabled());
        settingsUiState.setLowStorageWarningGb(storageSettings.warningGb());

        recordingRotationLocked = composition.cameraRecordingActiveForUi();
        applyAutoRotate(androidRuntime.isAutoRotateEnabled());
        menuModel = new MainMenuModel();

        getWindow().setNavigationBarColor(Color.BLACK);
        activityBinding = ActivityMainBinding.inflate(getLayoutInflater());
        activityChrome = new ActivityChromeController(this, activityBinding, androidRuntime::isDeviceOwner);
        recordingStatusRenderer = new RecordingStatusRenderer(
                activityBinding, () -> cameraScreen, () -> latestState, () -> renderedScreen);
        root = activityBinding.contentRoot;
        setContentView(activityBinding.getRoot());
        resourceMonitorController = new ResourceMonitorController(this,
                activityBinding.getRoot(), androidRuntime::isDeviceOwner, logger,
                composition.resourceMonitorEnabled());
        activityChrome.updateManagedTopBar();
        activityChrome.bindSystemNavigationInset();
        activityChrome.hideSystemStatusBar();

        permissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                result -> {
                    capturePermissionRequestInFlight = false;
                    boolean captureGranted = androidRuntime.capturePermissionsGranted();
                    logger.info("PERMISSION_TRACE runtime-result captureGranted=" + captureGranted
                            + " missingCapturePermissions="
                            + androidRuntime.missingCapturePermissions().length);
                    prepareCameraProfilesIfCameraGranted("runtime-result");
                    if (!captureGranted) {
                        showCapturePermissionRequired();
                        return;
                    }
                    completeCapturePermissionGate("runtime-result");
                });
        capturePermissionSettingsLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    capturePermissionSettingsRequestInFlight = false;
                    prepareCameraProfilesIfCameraGranted("settings-result");
                    if (androidRuntime.capturePermissionsGranted()) {
                        completeCapturePermissionGate("settings-result");
                    } else {
                        logger.warn("PERMISSION_TRACE settings-result captureGranted=false", null);
                        showCapturePermissionRequired();
                    }
                });
        locationPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                result -> {
                    locationPermissionRequestInFlight = false;
                    boolean openSettings = locationPermissionSettingsFallbackPending
                            && !result.isEmpty()
                            && !hasRequiredLocationPermission()
                            && !canShowRequiredLocationPermissionRationale();
                    locationPermissionSettingsFallbackPending = false;
                    refreshLocationTracking();
                    renderedScreen = null;
                    if (latestState != null)
                        render(latestState);
                    refreshGpsPermissionRetry();
                    ensureConfigFileAccess();
                    if (openSettings)
                        openLocationPermissionSettings();
                });
        legacyStoragePermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                result -> {
                    legacyStoragePermissionRequestInFlight = false;
                    if (androidRuntime.allFilesAccessGranted())
                        startStartupPermissionFlow();
                    else
                        showLegacyStorageAccessRequired();
                });
        legacyStorageSettingsLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    legacyStorageSettingsRequestInFlight = false;
                    if (androidRuntime.allFilesAccessGranted())
                        startStartupPermissionFlow();
                    else
                        showLegacyStorageAccessRequired();
                });
        gpsPermissionRetryScheduler = new GpsPermissionRetryScheduler(
                GPS_PERMISSION_RETRY_DELAY_MS,
                new GpsPermissionRetryScheduler.Scheduler() {
                    @Override
                    public void schedule(Runnable action, long delayMillis) {
                        cameraClock.postDelayed(action, delayMillis);
                    }

                    @Override
                    public void cancel(Runnable action) {
                        cameraClock.removeCallbacks(action);
                    }
                },
                this::shouldRetryGpsPermission,
                this::launchLocationPermissionRequest);
        defaultHomeLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                });
        allFilesAccessLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    allFilesAccessRequestInFlight = false;
                    if (androidRuntime.allFilesAccessGranted())
                        startStartupPermissionFlow();
                    else
                        showAllFilesAccessRequired();
                });
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                navigateBack();
            }
        });

        openMedia = composition.createOpenMediaUseCase(this);
        logger.info("Authentication is "
                + (developerFeatureToggles.isEffectivelyEnabled(FeatureGate.AUTHENTICATION)
                        ? "enabled"
                        : "disabled")
                + ".");
        viewModel = new ViewModelProvider(
                this, new MainViewModelFactory(
                        composition.initialDeviceStatus(),
                        composition.refreshDeviceStatusUseCase(), composition.browseMediaUseCase(),
                        composition.authenticateOperatorUseCase(),
                        composition.operatorSessionUseCase(),
                        composition.manageOperatorUsersUseCase(),
                        () -> developerFeatureToggles.isEffectivelyEnabled(FeatureGate.AUTHENTICATION),
                        () -> developerFeatureToggles.isEffectivelyEnabled(FeatureGate.MEDIA_BROWSER)))
                .get(MainViewModel.class);

        viewModel.state().observe(this, this::render);
        if (androidRuntime.capturePermissionsGranted())
            requestStartupLocationPermissionAfterCameraReady();
        IntentFilter screenFilter = new IntentFilter(Intent.ACTION_SCREEN_OFF);
        screenFilter.addAction(Intent.ACTION_SCREEN_ON);
        ContextCompat.registerReceiver(this, screenStateReceiver, screenFilter,
                ContextCompat.RECEIVER_NOT_EXPORTED);
        screenOff = !androidRuntime.isScreenInteractive();
        ContextCompat.registerReceiver(this, wifiStateReceiver,
                new IntentFilter(WifiManager.WIFI_STATE_CHANGED_ACTION),
                ContextCompat.RECEIVER_NOT_EXPORTED);
        IntentFilter storageFilter = new IntentFilter(Intent.ACTION_MEDIA_MOUNTED);
        storageFilter.addAction(Intent.ACTION_MEDIA_CHECKING);
        storageFilter.addAction(Intent.ACTION_MEDIA_SHARED);
        storageFilter.addAction(Intent.ACTION_MEDIA_UNMOUNTED);
        storageFilter.addAction(Intent.ACTION_MEDIA_EJECT);
        storageFilter.addAction(Intent.ACTION_MEDIA_REMOVED);
        storageFilter.addAction(Intent.ACTION_MEDIA_BAD_REMOVAL);
        storageFilter.addDataScheme("file");
        ContextCompat.registerReceiver(this, storageMountedReceiver, storageFilter,
                ContextCompat.RECEIVER_NOT_EXPORTED);
        startStartupPermissionFlow();
        requestIdleCameraReleaseIfScreenOff();
    }

    private void startStartupPermissionFlow() {
        if (!ensureAllFilesAccess())
            return;
        prepareCameraProfilesIfCameraGranted("startup");
        requestDefaultHomeIfNeeded();
        applyKioskPolicyThenRequestCorePermissions();
    }

    private boolean ensureAllFilesAccess() {
        if (androidRuntime.allFilesAccessGranted())
            return true;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            if (!legacyStoragePermissionRequestInFlight
                    && !legacyStorageSettingsRequestInFlight
                    && !legacyStorageAccessDialogShowing) {
                logger.warn("STORAGE_TRACE legacy-storage-access-required", null);
                requestLegacyStoragePermissions();
            }
            return false;
        }
        if (!allFilesAccessRequestInFlight && !allFilesAccessDialogShowing) {
            logger.warn("STORAGE_TRACE all-files-access-required", null);
            requestAllFilesAccessSettings();
        }
        return false;
    }

    private void requestAllFilesAccessSettings() {
        if (allFilesAccessLauncher == null || allFilesAccessRequestInFlight)
            return;
        allFilesAccessRequestInFlight = true;
        try {
            allFilesAccessLauncher.launch(new Intent(
                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    Uri.parse("package:" + getPackageName())));
        } catch (RuntimeException error) {
            allFilesAccessRequestInFlight = false;
            logger.error("STORAGE_TRACE all-files-settings-failed", error);
            showAllFilesAccessRequired();
        }
    }

    private void showAllFilesAccessRequired() {
        if (allFilesAccessDialogShowing || isFinishing() || isDestroyed())
            return;
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.all_files_access_title)
                .setMessage(R.string.all_files_access_message)
                .setPositiveButton(R.string.all_files_access_open_settings,
                        (dismissed, which) -> requestAllFilesAccessSettings())
                .setCancelable(false)
                .create();
        dialog.setOnDismissListener(dismissed -> allFilesAccessDialogShowing = false);
        allFilesAccessDialogShowing = true;
        dialog.show();
    }

    private void applyKioskPolicyThenRequestCorePermissions() {
        if (capturePermissionPolicyRequestInFlight)
            return;
        if (capturePermissionFlowStarted && capturePermissionPolicyApplied) {
            if (!androidRuntime.capturePermissionsGranted()
                    && !capturePermissionRequestInFlight
                    && !capturePermissionSettingsRequestInFlight) {
                showCapturePermissionRequired();
            }
            return;
        }
        capturePermissionFlowStarted = true;
        capturePermissionPolicyRequestInFlight = true;
        androidRuntime.applyActiveKioskPolicyAsync(() -> runOnUiThread(() -> {
            capturePermissionPolicyRequestInFlight = false;
            if (isFinishing() || isDestroyed())
                return;
            capturePermissionPolicyApplied = true;
            String[] missingCorePermissions = androidRuntime.missingCorePermissions();
            if (missingCorePermissions.length == 0) {
                completeCapturePermissionGate("kiosk-policy");
                return;
            }
            capturePermissionRequestInFlight = true;
            logger.info("PERMISSION_TRACE runtime-request missingCorePermissions="
                    + missingCorePermissions.length + " missingCapturePermissions="
                    + androidRuntime.missingCapturePermissions().length);
            try {
                permissionLauncher.launch(missingCorePermissions);
            } catch (RuntimeException error) {
                capturePermissionRequestInFlight = false;
                logger.error("PERMISSION_TRACE runtime-request-failed", error);
                if (androidRuntime.capturePermissionsGranted()) {
                    completeCapturePermissionGate("runtime-request-failed");
                } else {
                    showCapturePermissionRequired();
                }
            }
        }));
    }

    private void prepareCameraProfilesIfCameraGranted(String source) {
        if (!androidRuntime.cameraPermissionGranted())
            return;
        composition.prepareCameraProfilesIfPermitted();
    }

    private void completeCapturePermissionGate(String source) {
        if (!androidRuntime.capturePermissionsGranted()) {
            showCapturePermissionRequired();
            return;
        }
        logger.info("PERMISSION_TRACE capture-ready source=" + source);
        if (captureRuntime != null)
            captureRuntime.bindCameraIfPermitted();
        refreshLocationTracking();
        requestStartupLocationPermissionAfterCameraReady();
        startDeviceIdentityPrecheck();
    }

    private void requestCapturePermissionSettings() {
        if (capturePermissionSettingsLauncher == null
                || capturePermissionSettingsRequestInFlight)
            return;
        capturePermissionSettingsRequestInFlight = true;
        logger.info("PERMISSION_TRACE open-app-settings");
        try {
            capturePermissionSettingsLauncher.launch(new Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + getPackageName())));
        } catch (RuntimeException error) {
            capturePermissionSettingsRequestInFlight = false;
            logger.error("PERMISSION_TRACE app-settings-failed", error);
            showCapturePermissionRequired();
        }
    }

    private void showCapturePermissionRequired() {
        if (capturePermissionDialogShowing || isFinishing() || isDestroyed())
            return;
        logger.warn("PERMISSION_TRACE blocker-shown missingCapturePermissions="
                + androidRuntime.missingCapturePermissions().length, null);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.camera_permission_required)
                .setMessage(R.string.capture_permission_required_message)
                .setPositiveButton(R.string.capture_permission_open_settings,
                        (dismissed, which) -> {
                            capturePermissionDialogShowing = false;
                            requestCapturePermissionSettings();
                        })
                .setCancelable(false)
                .create();
        dialog.setOnDismissListener(dismissed -> capturePermissionDialogShowing = false);
        capturePermissionDialogShowing = true;
        dialog.show();
    }

    private void requestLegacyStoragePermissions() {
        if (legacyStoragePermissionRequestInFlight)
            return;
        String[] missingPermissions = androidRuntime.missingLegacyStoragePermissions();
        if (missingPermissions.length == 0) {
            startStartupPermissionFlow();
            return;
        }
        legacyStoragePermissionRequestInFlight = true;
        legacyStoragePermissionLauncher.launch(missingPermissions);
    }

    private void requestLegacyStorageSettings() {
        if (legacyStorageSettingsRequestInFlight)
            return;
        legacyStorageSettingsRequestInFlight = true;
        try {
            legacyStorageSettingsLauncher.launch(new Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + getPackageName())));
        } catch (RuntimeException error) {
            legacyStorageSettingsRequestInFlight = false;
            logger.error("STORAGE_TRACE legacy-storage-settings-failed", error);
            showLegacyStorageAccessRequired();
        }
    }

    private void showLegacyStorageAccessRequired() {
        if (legacyStorageAccessDialogShowing || isFinishing() || isDestroyed())
            return;
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.legacy_storage_access_title)
                .setMessage(R.string.legacy_storage_access_message)
                .setPositiveButton(R.string.legacy_storage_access_open_settings,
                        (dismissed, which) -> {
                            legacyStorageAccessDialogShowing = false;
                            requestLegacyStorageSettings();
                        })
                .setCancelable(false)
                .create();
        dialog.setOnDismissListener(dismissed -> legacyStorageAccessDialogShowing = false);
        legacyStorageAccessDialogShowing = true;
        dialog.show();
    }

    private void startDeviceIdentityPrecheck() {
        ensureConfigFileAccess(false);
    }

    private void ensureConfigFileAccess() {
        ensureConfigFileAccess(true);
    }

    private void ensureConfigFileAccess(boolean interactionReady) {
        if (interactionReady)
            identityInteractionReady = true;
        if (!ensureAllFilesAccess())
            return;
        if (isFinishing() || isDestroyed())
            return;
        if (identityCheckComplete) {
            completeDeviceIdentityCheck();
            return;
        }
        if (identityCheckInFlight || serialNumberDialogShowing)
            return;
        identityCheckInFlight = true;
        identityIoExecutor.execute(() -> {
            try {
                boolean restored = deviceSerialNumbers.restoreIfAvailable();
                runOnUiThread(() -> {
                    identityCheckInFlight = false;
                    identityCheckComplete = true;
                    identityRestored = restored;
                    identityRestoreError = null;
                    logger.info("SERIAL_TRACE precheck-complete restored=" + restored);
                    completeDeviceIdentityCheck();
                });
            } catch (Exception error) {
                boolean serialConfigured = deviceSerialNumbers.isConfigured(deviceSerialNumbers.load());
                runOnUiThread(() -> {
                    identityCheckInFlight = false;
                    identityCheckComplete = true;
                    identityRestored = false;
                    identityRestoreError = serialConfigured ? null : error;
                    if (serialConfigured) {
                        logger.warn(
                                "Device identity mirror synchronization failed, but configured serial remains available",
                                error);
                    } else {
                        logger.error("SERIAL_TRACE restore-failed", error);
                    }
                    completeDeviceIdentityCheck();
                });
            }
        });
    }

    private void completeDeviceIdentityCheck() {
        if (!identityInteractionReady || isFinishing() || isDestroyed())
            return;
        if (identityRestoreError != null) {
            showDeviceIdentityRestoreFailed();
            return;
        }
        if (identityRestored) {
            identityRestored = false;
            logger.info("SERIAL_TRACE restore-success");
            applyDeviceIdentityUpdate();
        } else {
            showSerialNumberDialogIfNeeded();
        }
    }

    private void showDeviceIdentityRestoreFailed() {
        if (identityRestoreErrorDialogShowing || isFinishing() || isDestroyed())
            return;
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.device_identity_restore_failed_title)
                .setMessage(R.string.device_identity_restore_failed_message)
                .setPositiveButton(R.string.device_identity_restore_retry,
                        (dismissed, which) -> retryDeviceIdentityRestore())
                .setCancelable(false)
                .create();
        dialog.setOnDismissListener(dismissed -> identityRestoreErrorDialogShowing = false);
        identityRestoreErrorDialogShowing = true;
        dialog.show();
    }

    private void retryDeviceIdentityRestore() {
        identityRestoreErrorDialogShowing = false;
        identityRestoreError = null;
        identityRestored = false;
        identityCheckComplete = false;
        ensureConfigFileAccess();
    }

    private void applyDeviceIdentityUpdate() {
        AppComposition.refreshDeviceIdentity();
        updateCameraIdentity();
    }

    private void showSerialNumberDialogIfNeeded() {
        if (serialNumberDialogShowing)
            return;
        String saved = deviceSerialNumbers.load();
        boolean savedValid = deviceSerialNumbers.isConfigured(saved);
        logger.info("SERIAL_TRACE dialog-check composition=" + identity(composition)
                + " savedPresent=" + savedValid
                + " savedHash=" + shortId(saved));
        if (savedValid)
            return;
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        input.setHint(R.string.device_serial_hint);
        input.setContentDescription(getString(R.string.device_serial_hint));
        input.setSelectAllOnFocus(true);
        input.setFilters(new InputFilter[] {
                new InputFilter.AllCaps(), new InputFilter.LengthFilter(10)
        });
        FrameLayout inputContainer = new FrameLayout(this);
        inputContainer.setPadding(dp(24), 0, dp(24), 0);
        inputContainer.addView(input, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.device_serial_title)
                .setMessage(R.string.device_serial_message)
                .setView(inputContainer)
                .setPositiveButton(R.string.device_serial_save, null)
                .setCancelable(false)
                .create();
        dialog.setOnShowListener(d -> {
            Button save = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            save.setOnClickListener(v -> {
                String value = input.getText().toString().trim();
                persistSerialNumberAsync(dialog, input, save, value);
            });
            input.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence text, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence text, int start, int before, int count) {
                    boolean valid = deviceSerialNumbers.isValid(text.toString());
                    logger.info("SERIAL_TRACE input-changed length=" + text.length()
                            + " valid=" + valid);
                    save.setEnabled(valid);
                    input.setError(null);
                }

                @Override
                public void afterTextChanged(Editable text) {
                }
            });
            save.setEnabled(false);
        });
        dialog.setOnDismissListener(d -> serialNumberDialogShowing = false);
        serialNumberDialogShowing = true;
        dialog.show();
    }

    private void persistSerialNumberAsync(
            AlertDialog dialog,
            EditText input,
            Button saveButton,
            String serial) {
        if (!deviceSerialNumbers.isValid(serial)) {
            input.setError(getString(R.string.device_serial_invalid));
            return;
        }
        input.setEnabled(false);
        saveButton.setEnabled(false);
        logger.info("SERIAL_TRACE save-request length=" + serial.length()
                + " valueHash=" + shortId(serial));
        identityIoExecutor.execute(() -> {
            try {
                deviceSerialNumbers.save(serial);
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed())
                        return;
                    logger.info("SERIAL_TRACE save-success valueHash=" + shortId(serial));
                    dialog.dismiss();
                    applyDeviceIdentityUpdate();
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed())
                        return;
                    logger.error("SERIAL_TRACE save-failed", error);
                    int message = error instanceof IllegalArgumentException
                            ? R.string.device_serial_invalid
                            : R.string.device_serial_save_failed;
                    input.setError(getString(message));
                    input.setEnabled(true);
                    saveButton.setEnabled(deviceSerialNumbers.isValid(input.getText().toString()));
                });
            }
        });
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        refreshDisplayRotationIfNeeded(getDisplay());
    }

    @Override
    protected void onResume() {
        super.onResume();
        String currentScreen = latestState == null ? "unknown"
                : String.valueOf(latestState.getScreen())
                        .toLowerCase(Locale.ROOT).replace('_', ' ');
        String captureMode = latestState == null ? "unknown"
                : String.valueOf(latestState.getCapture().getMode())
                        .toLowerCase(Locale.ROOT).replace('_', ' ');
        logger.info("MainActivity resumed. Screen: " + currentScreen
                + ". Capture is " + captureMode
                + ". Camera runtime is "
                + (captureRuntime == null ? "unavailable" : "available")
                + ". Window is " + (hasWindowFocus() ? "focused" : "not focused")
                + ". Configuration change is "
                + (isChangingConfigurations() ? "in progress" : "not in progress") + ".");
        writeSettingsRequestInFlight = false;
        if (pendingAutoRotateValue != null && androidRuntime.canWriteSystemSettings()) {
            boolean requested = pendingAutoRotateValue;
            if (androidRuntime.setAutoRotateEnabled(requested)) {
                pendingAutoRotateValue = null;
                logSettingChanged(currentSetting(SettingId.AUTO_ROTATE),
                        SettingId.AUTO_ROTATE, enabledValue(requested));
            }
        }
        syncRecordingRotationLock();
        syncAutoRotateFromDevice();
        activityChrome.hideSystemStatusBar();
        refreshWifiSetting();
        if (androidRuntime != null) {
            androidRuntime.enterLockTaskIfAllowed(this, () -> {
                activityChrome.hideSystemStatusBar();
                activityChrome.updateManagedTopBar();
            });
        }
        if (viewModel != null)
            viewModel.refreshDeviceStatus();
        if (captureRuntime != null && androidRuntime.capturePermissionsGranted()) {
            captureRuntime.refreshCameraState();
            refreshDisplayRotationIfNeeded(getDisplay());
        }
        if (capturePermissionPolicyApplied && !capturePermissionRequestInFlight
                && !capturePermissionSettingsRequestInFlight
                && !androidRuntime.capturePermissionsGranted()) {
            logger.warn("PERMISSION_TRACE onResume captureGranted=false", null);
            showCapturePermissionRequired();
        }
        if (pendingLocationEnabled != null) {
            boolean requested = pendingLocationEnabled;
            pendingLocationEnabled = null;
            if (locationControl != null) {
                LocationSystemState state = locationControl.currentState();
                if (state == (requested
                        ? LocationSystemState.ENABLED : LocationSystemState.DISABLED)) {
                    logSettingChanged(currentSetting(SettingId.GPS_LOCATION_ENABLED),
                            SettingId.GPS_LOCATION_ENABLED, enabledValue(requested));
                }
            }
            renderedScreen = null;
        }
        refreshLocationTracking();
        refreshGpsPermissionRetry();
        recordingStatusRenderer.resyncRecordingDurations();
        if (hasActiveRecording(latestState)) startRecordingDurationTicker();
        else stopRecordingDurationTicker();
        cameraClock.removeCallbacks(cameraClockTick);
        cameraClock.post(cameraClockTick);
        requestCameraSwitchActionUpdate();
    }

    @Override
    protected void onStart() {
        super.onStart();
        resourceMonitorController.onStart();
        subscribeCameraCapabilityRecheck();
        IntentFilter locationFilter = new IntentFilter(LocationManager.MODE_CHANGED_ACTION);
        locationFilter.addAction(LocationManager.PROVIDERS_CHANGED_ACTION);
        ContextCompat.registerReceiver(this, locationModeChangedReceiver, locationFilter,
                ContextCompat.RECEIVER_NOT_EXPORTED);
        registerAutoRotateObserver();
        registerDisplayRotationListener();
    }

    @Override
    protected void onStop() {
        resourceMonitorController.onStop();
        unsubscribeCameraCapabilityRecheck();
        unregisterReceiver(locationModeChangedReceiver);
        logger.info("LIFECYCLE_TRACE onStop activity=" + identity(this)
                + " screen=" + (latestState == null ? "unknown" : latestState.getScreen())
                + " captureMode=" + (latestState == null
                        ? "unknown"
                        : latestState.getCapture().getMode())
                + " captureRuntime=" + identity(captureRuntime)
                + " windowFocus=" + hasWindowFocus()
                + " finishing=" + isFinishing()
                + " changingConfig=" + isChangingConfigurations()
                + " processCameraLifecycle=RESUMED");
        stopLocationTracking();
        unregisterAutoRotateObserver();
        unregisterDisplayRotationListener();
        super.onStop();
    }

    private void refreshDisplayRotationIfNeeded(Display display) {
        AppComposition.CaptureRuntime runtime = captureRuntime;
        if (runtime == null || display == null
                || recordingRotationLocked
                || composition != null && composition.cameraRecordingActiveForUi())
            return;
        int rotation = display.getRotation();
        if (runtime == refreshedDisplayRotationRuntime
                && rotation == refreshedDisplayRotation)
            return;
        runtime.refreshDisplayRotation();
        refreshedDisplayRotationRuntime = runtime;
        refreshedDisplayRotation = rotation;
    }

    private void registerDisplayRotationListener() {
        if (displayManager != null)
            return;
        displayManager = getSystemService(DisplayManager.class);
        if (displayManager == null) {
            logger.warn("LIFECYCLE_TRACE displayListener registration_failed", null);
            return;
        }
        displayManager.registerDisplayListener(displayRotationListener, cameraClock);
    }

    private void unregisterDisplayRotationListener() {
        if (displayManager == null)
            return;
        displayManager.unregisterDisplayListener(displayRotationListener);
        displayManager = null;
    }

    private void subscribeCameraCapabilityRecheck() {
        if (cameraCapabilityRecheckSubscription != null)
            return;
        cameraCapabilityRecheckSubscription = composition.observeCameraCapabilityRecheck(
                this::handleCameraCapabilityRecheckUpdate);
    }

    private void unsubscribeCameraCapabilityRecheck() {
        if (cameraCapabilityRecheckSubscription == null)
            return;
        cameraCapabilityRecheckSubscription.close();
        cameraCapabilityRecheckSubscription = null;
    }

    private void registerAutoRotateObserver() {
        if (autoRotateObserver != null)
            return;
        autoRotateObserver = new ContentObserver(new Handler(Looper.getMainLooper())) {
            @Override
            public void onChange(boolean selfChange) {
                syncAutoRotateFromDevice();
            }
        };
        getContentResolver().registerContentObserver(
                Settings.System.getUriFor(Settings.System.ACCELEROMETER_ROTATION),
                false, autoRotateObserver);
    }

    private void unregisterAutoRotateObserver() {
        if (autoRotateObserver == null)
            return;
        getContentResolver().unregisterContentObserver(autoRotateObserver);
        autoRotateObserver = null;
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (logger != null) {
            logger.info("LIFECYCLE_TRACE windowFocus=" + hasFocus
                    + " activity=" + identity(this)
                    + " screen=" + (latestState == null ? "unknown" : latestState.getScreen())
                    + " captureMode=" + (latestState == null
                            ? "unknown"
                            : latestState.getCapture().getMode()));
        }
        if (!hasFocus && hardwareButtons != null)
            hardwareButtons.clearFocusTransientState();
        if (hasFocus && activityChrome != null)
            activityChrome.hideSystemStatusBar();
        if (hasFocus && composition != null && androidRuntime != null
                && androidRuntime.capturePermissionsGranted()) {
            composition.bindCameraIfPermitted();
        }
    }

    private void refreshWifiSetting() {
        if (androidRuntime == null || settingsUiState == null)
            return;
        settingsUiState.updateBoolean(SettingId.WIFI_ENABLED, androidRuntime.isWifiEnabled());
        if (latestState != null && latestState.getScreen() == MainScreen.DEVICE_SETTINGS) {
            refreshCurrentSettingsControls();
        }
    }

    @Override
    protected void onPause() {
        logger.info("LIFECYCLE_TRACE onPause activity=" + identity(this)
                + " screen=" + (latestState == null ? "unknown" : latestState.getScreen())
                + " captureMode=" + (latestState == null
                        ? "unknown"
                        : latestState.getCapture().getMode())
                + " captureRuntime=" + identity(captureRuntime)
                + " windowFocus=" + hasWindowFocus()
                + " finishing=" + isFinishing());
        cameraClock.removeCallbacks(cameraClockTick);
        cameraClock.removeCallbacks(recordingDurationTick);
        cameraClock.removeCallbacks(cameraSwitchActionRefresh);
        if (gpsPermissionRetryScheduler != null)
            gpsPermissionRetryScheduler.stop();
        stopLocationTracking();
        super.onPause();
    }

    private void startRecordingDurationTicker() {
        cameraClock.removeCallbacks(recordingDurationTick);
        nextRecordingDurationTickAtMillis = SystemClock.uptimeMillis() + 1_000L;
        cameraClock.postAtTime(recordingDurationTick, nextRecordingDurationTickAtMillis);
    }

    private void stopRecordingDurationTicker() {
        cameraClock.removeCallbacks(recordingDurationTick);
    }

    private static boolean hasActiveRecording(MainUiState state) {
        return state != null
                && (state.getCapture().isAudioRecording()
                        || state.getCapture().isVideoRecording()
                                && !state.getCapture().isSaving());
    }

    private void render(MainUiState state) {
        MainUiState previousState = latestState;
        latestState = state;
        syncRecordingRotationLock();
        boolean wasRecording = hasActiveRecording(previousState);
        boolean recording = hasActiveRecording(state);
        if (!wasRecording && recording) {
            startRecordingDurationTicker();
        } else if (wasRecording && !recording) {
            stopRecordingDurationTicker();
        }
        if (renderedScreen == null
                && state.getScreen() == MainScreen.LOGIN
                && state.isAuthenticationBusy())
            return;
        MainScreen screen = safeScreen(state.getScreen());
        if (screen != state.getScreen()) {
            viewModel.show(screen);
            return;
        }
        if (renderedScreen != screen) {
            if (renderedScreen == MainScreen.CAMERA && screen != MainScreen.CAMERA) {
                FloatingNotice.hideLowPriorityPersistent();
                storageWarningVisible = null;
            }
            renderedScreen = screen;
            recordingStatusRenderer.updateFloatingRecordingStatus(state);
            if (screen == MainScreen.LOGIN)
                renderLogin();
            else if (screen == MainScreen.CAMERA)
                renderCamera();
            else if (screen == MainScreen.MENU)
                renderMenu();
            else if (screen == MainScreen.FILES)
                renderFileExplorer();
            else if (screen == MainScreen.DEVELOPER_USERS)
                renderDeveloperUsers();
            else
                renderSettingsDetail(screen);
            if (operationLoggedScreen != screen) {
                logScreenNavigation(operationLoggedScreen, screen);
                operationLoggedScreen = screen;
            }
        }
        if (screen == MainScreen.DEVELOPER_SETTINGS && renderedSettingsList != null) {
            refreshDeveloperSettingsRows();
        }
        updateStatus(state);
        refreshGpsPermissionRetry();
        updateSavingNotice(previousState, state);
        showSavedNotice(previousState, state);
        showCaptureFailureNotice(previousState, state);
        requestIdleCameraReleaseIfScreenOff();
    }

    private void vibrateCaptureCommandStart() {
        Vibrator vibrator = getSystemService(Vibrator.class);
        if (vibrator == null || !vibrator.hasVibrator())
            return;
        vibrator.vibrate(VibrationEffect.createOneShot(
                CAPTURE_VIBRATION_MS, CAPTURE_VIBRATION_AMPLITUDE));
    }

    private void updateSavingNotice(MainUiState previousState, MainUiState state) {
        boolean wasSaving = previousState != null
                && (previousState.getCapture().isSaving()
                        || previousState.getCapture().isPhotoSaving()
                        || previousState.getCapture().isAudioSaving());
        boolean saving = state.getCapture().isSaving()
                || state.getCapture().isPhotoSaving()
                || state.getCapture().isAudioSaving();
        if (wasSaving && !saving) FloatingNotice.hidePersistent();
        if (!wasSaving && saving) {
            FloatingNotice.showPersistent(this, R.string.media_saving);
        }
    }

    private void showSavedNotice(MainUiState previousState, MainUiState state) {
        if (previousState == null || state.isCaptureHapticSuppressed()
                || !androidRuntime.isScreenInteractive())
            return;
        String message = state.getMessage();
        if (message == null || !message.startsWith("Saved ")
                || message.equals(previousState.getMessage()))
            return;
        long savedNoticeAgeMillis =
                System.currentTimeMillis() - viewModel.lastSavedNoticeAtMillis();
        if (savedNoticeAgeMillis < 0L
                || savedNoticeAgeMillis >= SAVED_NOTICE_MAX_AGE_MS)
            return;

        FloatingNotice.show(this, getString(R.string.media_saved, message.substring(6)));
    }

    private void showCaptureFailureNotice(
            MainUiState previousState, MainUiState state) {
        if (previousState == null)
            return;
        String message = state.getMessage();
        if (message == null || message.equals(previousState.getMessage()))
            return;
        if (message.startsWith("Finalization failed:")) {
            FloatingNotice.show(this, R.string.media_save_failed);
        } else if (isLowStorageRecordingBlockedMessage(message)) {
            FloatingNotice.show(this, message.substring("Storage failed: ".length()),
                    FloatingNotice.ERROR_TEXT_COLOR);
        } else if (message.equals("Storage failed: " + getString(R.string.sd_card_unavailable))) {
            FloatingNotice.show(this, R.string.sd_card_unavailable);
        } else if (message.startsWith("Storage failed:")) {
            FloatingNotice.show(this, R.string.capture_storage_unavailable);
        }
    }

    private boolean isLowStorageRecordingBlockedMessage(String message) {
        String marker = "%1$s";
        String template = getString(R.string.low_storage_recording_blocked, marker);
        int markerIndex = template.indexOf(marker);
        if (markerIndex < 0 || !message.startsWith("Storage failed: "))
            return false;
        String payload = message.substring("Storage failed: ".length());
        return payload.startsWith(template.substring(0, markerIndex))
                && payload.endsWith(template.substring(markerIndex + marker.length()));
    }

    private void renderCamera() {
        ensureCaptureRuntime();
        captureRuntime.bindCameraIfPermitted();
        if (locationTrackingCoordinator != null)
            locationTrackingCoordinator.requestCurrentLocation();
        clearScreenBindings();
        removeNonCameraScreens();
        ensureCameraScreen();
        cameraScreen.getRoot().setAlpha(1f);
        recordingStatusRenderer.updateCameraClock();
        requestStartupLocationPermissionAfterCameraReady();
    }

    private void requestCameraSwitchActionUpdate() {
        cameraClock.removeCallbacks(cameraSwitchActionRefresh);
        cameraClock.post(cameraSwitchActionRefresh);
    }

    private void updateCameraSwitchAction() {
        if (cameraScreen == null || captureRuntime == null)
            return;
        boolean multipleCameras = captureRuntime.hasMultipleCameras();
        boolean canSwitch = captureRuntime.canSwitchCamera();
        boolean enabled = multipleCameras && canSwitch;
        int visibility = multipleCameras ? View.VISIBLE : View.GONE;

        cameraScreen.cameraSwitchAction.setVisibility(visibility);
        cameraScreen.cameraSwitchAction.setEnabled(enabled);
        cameraScreen.cameraSwitchAction.setAlpha(enabled ? 1f : 0.45f);
    }

    private void requestStartupLocationPermissionAfterCameraReady() {
        if (startupGpsPermissionHandled || locationPermissionRequestInFlight
                || !androidRuntime.capturePermissionsGranted() || cameraScreen == null)
            return;
        cameraScreen.getRoot().post(() -> {
            if (startupGpsPermissionHandled || locationPermissionRequestInFlight
                    || isFinishing() || isDestroyed()
                    || !androidRuntime.capturePermissionsGranted())
                return;
            if (!developerFeatureToggles.isEffectivelyEnabled(FeatureGate.GPS)
                    || hasRequiredLocationPermission()) {
                startupGpsPermissionHandled = true;
                ensureConfigFileAccess();
                return;
            }
            startupGpsPermissionHandled = launchLocationPermissionRequest();
            if (!startupGpsPermissionHandled)
                ensureConfigFileAccess();
        });
    }

    private void updateCameraIdentity() {
        if (cameraScreen == null)
            return;
        String savedSerial = deviceSerialNumbers.load();
        cameraScreen.accountId.setText(deviceSerialNumbers.isConfigured(savedSerial)
                ? "CAM " + savedSerial
                : "CAM —");
    }

    private void ensureCameraScreen() {
        if (cameraScreen == null) {
            cameraScreen = ScreenCameraBinding.inflate(getLayoutInflater(), root, false);
            root.addView(cameraScreen.getRoot(), 0);
        }
        updateCameraIdentity();
        cameraScreen.operatorId.setText("USER —");
        updateGpsStatusLine();
        cameraScreen.cameraSwitchAction.setOnClickListener(view -> {
            boolean accepted = captureRuntime.switchCamera();
            logger.info("CAMERA_TRACE preview-switch-click accepted=" + accepted + " "
                    + captureRuntime.cameraSwitchState());
            requestCameraSwitchActionUpdate();
        });
        updateCameraSwitchAction();
        if (captureRuntime.cameraPreview().getParent() == null) {
            cameraScreen.previewContainer.addView(captureRuntime.cameraPreview(), new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }
        if (cameraScreen.getRoot().getParent() == null) {
            root.addView(cameraScreen.getRoot(), 0);
        }
        /*
         * Keep shared preview attached while other screens cover it. Reparenting
         * destroys its surface and causes black-screen startup delay on return.
         */
        if (captureRuntime.cameraPreview().getParent() instanceof ViewGroup
                && captureRuntime.cameraPreview().getParent() != cameraScreen.previewContainer) {
            ((ViewGroup) captureRuntime.cameraPreview().getParent()).removeView(captureRuntime.cameraPreview());
            cameraScreen.previewContainer.addView(captureRuntime.cameraPreview(), new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }
    }

    private void renderLogin() {
        ensureCaptureRuntime();
        ensureCameraScreen();
        clearScreenBindings();
        removeNonCameraScreens();
        loginScreen = ScreenLoginBinding.inflate(getLayoutInflater(), root, false);
        View.OnClickListener login = view -> viewModel.loginPassword(loginScreen.password.getText().toString());
        loginScreen.loginAction.setOnClickListener(login);
        loginScreen.resetDatabaseAction.setOnClickListener(view -> new AlertDialog.Builder(this)
                .setTitle(R.string.reset_login_database_title)
                .setMessage(R.string.reset_login_database_message)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.reset, (dialog, which) -> {
                    androidRuntime.resetDatabase();
                    android.os.Process.killProcess(android.os.Process.myPid());
                })
                .show());
        loginScreen.password.setOnEditorActionListener((view, actionId, event) -> {
            login.onClick(view);
            return true;
        });
        root.addView(loginScreen.getRoot());
    }

    private void renderMenu() {
        clearScreenBindings();
        removeNonCameraScreens();
        menuScreen = ScreenMenuBinding.inflate(getLayoutInflater(), root, false);
        List<View> visibleTiles = new ArrayList<>();
        for (MainMenuTile tile : menuModel.visibleTiles(this::isMenuTileVisible)) {
            View tileView = menuScreen.getRoot().findViewById(tile.getViewId());
            tileView.setVisibility(View.VISIBLE);
            tileView.setOnClickListener(view -> viewModel.show(tile.getScreen()));
            visibleTiles.add(tileView);
        }
        layoutVisibleMenuTiles(visibleTiles);
        root.addView(menuScreen.getRoot());
    }

    private void layoutVisibleMenuTiles(List<View> visibleTiles) {
        GridLayout grid = menuScreen.settingsGrid;
        grid.removeAllViews();
        grid.setColumnCount(Math.max(1, Math.min(3, visibleTiles.size())));
        for (View tile : visibleTiles) {
            grid.addView(tile, menuTileLayoutParams());
        }
    }

    private GridLayout.LayoutParams menuTileLayoutParams() {
        GridLayout.LayoutParams params = new GridLayout.LayoutParams();
        params.width = 0;
        params.height = dp(124);
        params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1, 1f);
        params.setMargins(dp(6), dp(6), dp(6), dp(6));
        return params;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void renderFileExplorer() {
        clearScreenBindings();
        removeNonCameraScreens();
        fileExplorerScreen = ScreenFileExplorerBinding.inflate(getLayoutInflater(), root, false);
        mediaBrowserRenderer = new MediaBrowserRenderer(this, getLayoutInflater(), fileExplorerScreen,
                relativePath -> viewModel.openMediaFolder(relativePath),
                entry -> openMedia != null && openMedia.execute(entry));
        root.addView(fileExplorerScreen.getRoot());
    }

    private void renderDeveloperUsers() {
        clearScreenBindings();
        removeNonCameraScreens();
        developerUsersScreen = ScreenDeveloperUsersBinding.inflate(getLayoutInflater(), root, false);
        developerUsersScreen.saveAction.setOnClickListener(view -> viewModel.provisionUser(
                new UserProvisioningRequest(
                        developerUsersScreen.userId.getText().toString(),
                        developerUsersScreen.displayName.getText().toString(),
                        developerUsersScreen.password.getText().toString(),
                        UserSource.DEVELOPER)));
        root.addView(developerUsersScreen.getRoot());
    }

    private void renderSettingsDetail(MainScreen screen) {
        clearScreenBindings();
        removeNonCameraScreens();
        ScreenSettingsDetailBinding detail = ScreenSettingsDetailBinding.inflate(
                getLayoutInflater(), root, false);
        renderedSettingsList = detail.settingsList;
        renderedSettingsScroll = detail.getRoot();
        detail.title.setText(settingsTitle(screen));
        if (screen == MainScreen.ABOUT) {
            detail.title.setClickable(true);
            detail.title.setFocusable(true);
            detail.title.setOnClickListener(view -> handleAboutSecretTap());
        }
        if (screen == MainScreen.DEVELOPER_BUTTON_BINDINGS) {
            developerButtonBindingsScreen.render(detail.settingsList);
        } else {
            renderSettingsControls(screen, detail.settingsList);
        }
        if (screen == MainScreen.DEVELOPER_SETTINGS) {
            Button bindings = new Button(this);
            bindings.setText(R.string.edit_key_bindings);
            bindings.setOnClickListener(view -> viewModel.show(MainScreen.DEVELOPER_BUTTON_BINDINGS));
            detail.settingsList.addView(bindings);
            Button users = new Button(this);
            users.setText(R.string.manage_developer_users);
            users.setOnClickListener(view -> viewModel.show(MainScreen.DEVELOPER_USERS));
            detail.settingsList.addView(users);
            Button removeDeviceOwner = new Button(this);
            removeDeviceOwner.setText(R.string.remove_device_owner);
            removeDeviceOwner.setEnabled(androidRuntime != null && androidRuntime.isDeviceOwner());
            removeDeviceOwner.setOnClickListener(view -> showRemoveDeviceOwnerConfirmation(removeDeviceOwner));
            detail.settingsList.addView(removeDeviceOwner);
        }
        root.addView(detail.getRoot());
    }

    private void showRemoveDeviceOwnerConfirmation(Button button) {
        if (androidRuntime == null || !androidRuntime.isDeviceOwner()) {
            button.setEnabled(false);
            FloatingNotice.show(this, R.string.remove_device_owner_unavailable);
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.remove_device_owner_title)
                .setMessage(R.string.remove_device_owner_message)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.remove_device_owner, (dialog, which) -> {
                    try {
                        if (androidRuntime.removeDeviceOwner()) {
                            button.setEnabled(false);
                            logger.info("Removed device-owner management from developer settings.");
                            FloatingNotice.show(this, R.string.remove_device_owner_success);
                        } else {
                            FloatingNotice.show(this, R.string.remove_device_owner_unavailable);
                        }
                    } catch (RuntimeException error) {
                        logger.error("KIOSK_TRACE remove-device-owner failed", error);
                        FloatingNotice.show(this, R.string.remove_device_owner_failed);
                    }
                })
                .show();
    }

    private void renderSettingsControls(MainScreen screen, LinearLayout settingsList) {
        SettingsScreenModel model = settingsModel(screen);
        if (screen == MainScreen.RECORD_SETTINGS || screen == MainScreen.CAMERA_SETTINGS) {
            settingsRenderer.renderByStableId(settingsList, model,
                    this::selectCameraSetting, (id, value) -> {
                    },
                    (id, checked) -> {
                    }, null, this::canOpenCameraSetting);
            return;
        }
        if (screen == MainScreen.DEVELOPER_SETTINGS) {
            settingsRenderer.renderByStableId(settingsList, model,
                    this::selectDeveloperSetting, this::updateDeveloperNumberSetting,
                    this::updateDeveloperBooleanSetting, this::performDeveloperSettingAction,
                    this::canSelectDeveloperSetting);
            return;
        }
        settingsRenderer.render(settingsList, model,
                this::selectSetting, this::updateNumberSetting, this::updateBooleanSetting,
                this::performSettingAction, this::canSelectSetting);
    }

    private void refreshDeveloperSettingsRows() {
        if (renderedSettingsList == null || latestState == null
                || latestState.getScreen() != MainScreen.DEVELOPER_SETTINGS)
            return;
        settingsRenderer.refreshRows(developerSettingsModel());
    }

    private void refreshCurrentSettingsControls() {
        if (renderedSettingsList == null || renderedSettingsScroll == null
                || latestState == null)
            return;
        MainScreen screen = latestState.getScreen();
        if (screen == MainScreen.DEVELOPER_BUTTON_BINDINGS)
            return;
        settingsRenderer.refreshRows(settingsModel(screen));
    }

    private SettingsScreenModel settingsModel(MainScreen screen) {
        if (screen == MainScreen.DEVELOPER_SETTINGS)
            return developerSettingsModel();
        SettingsScreenModel model;
        if (screen == MainScreen.RECORD_SETTINGS || screen == MainScreen.CAMERA_SETTINGS) {
            boolean recording = latestState != null
                    && latestState.getCapture().isVideoRecording();
            var cameraPresentation = composition.cameraSettings(recording);
            model = screen == MainScreen.RECORD_SETTINGS
                    ? settingsUiState.recording(cameraPresentation,
                            getString(R.string.record_resolution),
                            getString(R.string.frame_rate))
                    : settingsUiState.camera(cameraPresentation,
                            getString(R.string.photo_resolution),
                            getString(R.string.photo_recording_quality_notice),
                            getString(R.string.camera_status));
        } else if (screen == MainScreen.STORAGE_SETTINGS) {
            model = settingsUiState.storageWithVolumes(storageOptions(), getString(R.string.storage_settings),
                    getString(R.string.default_storage), getString(R.string.low_storage_warning));
        } else if (screen == MainScreen.USER_SETTINGS) {
            settingsUiState.setVideoEncryptionEnabled(mediaEncryptionSettings.isMediaEncryptionEnabled());
            model = settingsUiState.security();
        } else if (screen == MainScreen.DEVICE_SETTINGS) {
            model = withLanguage(settingsUiState.device(
                    getString(R.string.auto_rotate), getString(R.string.wifi),
                    getString(R.string.connect_wifi)));
        } else if (screen == MainScreen.GPS_SETTINGS)
            model = locationSettingsModel();
        else if (screen == MainScreen.ABOUT)
            model = aboutSettingsModel();
        else
            model = settingsUiState.readOnly(visibleReadOnlySettings(screen));
        return filterUnavailableSettings(screen, model);
    }

    private List<StorageOptionUiState> storageOptions() {
        refreshStorageVolumes();
        List<StorageVolumeStatus> volumes = storageVolumes;
        List<StorageOptionUiState> options = new ArrayList<>();
        for (MediaPartitionLocation mode : storageSettings.supportedModes()) {
            if (mode == MediaPartitionLocation.AUTO) {
                options.add(new StorageOptionUiState(getString(R.string.storage_auto),
                        getString(R.string.storage_auto_description), 0, true));
            } else {
                int label = mode == MediaPartitionLocation.INTERNAL
                        ? R.string.storage_internal
                        : R.string.storage_external;
                options.add(storageOption(getString(label), storageVolume(volumes, mode)));
            }
        }
        return List.copyOf(options);
    }

    private void requestDefaultHomeIfNeeded() {
        if (defaultHomeRequestStarted || androidRuntime == null
                || androidRuntime.isDeviceOwner()
                || androidRuntime.isDefaultHome())
            return;
        Intent intent;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            RoleManager roleManager = getSystemService(RoleManager.class);
            if (roleManager == null || !roleManager.isRoleAvailable(RoleManager.ROLE_HOME)
                    || roleManager.isRoleHeld(RoleManager.ROLE_HOME))
                return;
            intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME);
        } else {
            intent = new Intent(Settings.ACTION_HOME_SETTINGS);
        }
        if (intent.resolveActivity(getPackageManager()) == null)
            return;
        defaultHomeRequestStarted = true;
        try {
            defaultHomeLauncher.launch(intent);
        } catch (ActivityNotFoundException error) {
            defaultHomeRequestStarted = false;
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putBoolean(STATE_DEFAULT_HOME_REQUESTED, defaultHomeRequestStarted);
        outState.putBoolean(STATE_CAPTURE_PERMISSION_FLOW_STARTED, capturePermissionFlowStarted);
        outState.putBoolean(STATE_CAPTURE_PERMISSION_POLICY_APPLIED, capturePermissionPolicyApplied);
        outState.putBoolean(STATE_CAPTURE_PERMISSION_REQUEST_IN_FLIGHT,
                capturePermissionRequestInFlight);
        outState.putBoolean(STATE_CAPTURE_PERMISSION_SETTINGS_IN_FLIGHT,
                capturePermissionSettingsRequestInFlight);
        outState.putBoolean(STATE_STARTUP_GPS_PERMISSION_HANDLED, startupGpsPermissionHandled);
        outState.putBoolean(STATE_DEVICE_IDENTITY_PENDING, identityCheckInFlight
                || legacyStorageAccessDialogShowing
                || identityRestoreError != null
                || identityRestoreErrorDialogShowing
                || serialNumberDialogShowing
                || deviceSerialNumbers == null
                || !deviceSerialNumbers.isConfigured(deviceSerialNumbers.load()));
        super.onSaveInstanceState(outState);
    }

    private SettingsScreenModel developerSettingsModel() {
        GpsSettings current = locationSettings.currentSettings();
        List<GpsMode> modes = locationSettings.supportedModes();
        List<DescribedRadioOptionUiState> options = new ArrayList<>();
        for (GpsMode mode : modes) {
            options.add(gpsModeOption(mode, locationControl.isModeAvailable(mode)));
        }
        SettingItem provider = SettingItem.describedRadio(SettingId.GPS_POSITIONING_MODE,
                getString(R.string.gps_positioning_mode), options,
                Math.max(0, modes.indexOf(current.getMode())))
                .withEnabled(developerFeatureToggles.isEffectivelyEnabled(FeatureGate.GPS))
                .withIndentLevel(1);
        SettingsScreenModel base = developerFeatureToggles.developerSettings(
                SettingId.FEATURE_GPS, provider);
        List<SettingsSection> sections = new ArrayList<>(base.getSections());
        sections.add(new SettingsSection(getString(R.string.resource_monitor_section), List.of(
                SettingItem.checkbox(SettingId.RESOURCE_MONITOR,
                        getString(R.string.resource_monitor),
                        composition.resourceMonitorEnabled())
                        .withDescription(getString(R.string.resource_monitor_description)))));
        sections.add(new SettingsSection(getString(R.string.camera_lifecycle_section), List.of(
                SettingItem.checkbox(SettingId.DEV_RELEASE_CAMERA_WHEN_SCREEN_OFF,
                        getString(R.string.release_camera_when_screen_off),
                        composition.releaseCameraWhenScreenOff())
                        .withDescription(getString(
                                R.string.release_camera_when_screen_off_description)))));
        List<CameraDeveloperSettingsPresentation.CameraPipelineSelectionUiState> selections = composition
                .cameraPipelineCameraIds().stream()
                .map(this::cameraPipelineSelection)
                .collect(java.util.stream.Collectors.toList());
        boolean recheckBlocked = !composition.cameraCapabilityRecheckControlEnabled()
                && !composition.cameraCapabilityRecheckInFlight();
        sections.addAll(CameraDeveloperSettingsPresentation.screen(
                getString(R.string.camera_pipeline_section), selections,
                getString(R.string.recheck_camera_capabilities), recheckBlocked).getSections());
        return new SettingsScreenModel(sections);
    }

    private String cameraPipelineLabel(String pipelineId) {
        if (pipelineId != null && pipelineId.startsWith("a-")) {
            return getString(R.string.camera_pipeline_a);
        }
        if (pipelineId != null && pipelineId.startsWith("b-")) {
            return getString(R.string.camera_pipeline_b);
        }
        return pipelineId == null || pipelineId.isBlank()
                ? getString(R.string.camera_pipeline_unknown)
                : pipelineId;
    }

    private CameraDeveloperSettingsPresentation.CameraPipelineSelectionUiState cameraPipelineSelection(
            String cameraId) {
        DeveloperSettingsStore.Mode selectedMode = composition.cameraPipelineMode(cameraId);
        boolean fullyVerified = composition.cameraCapabilitiesFullyVerified();
        List<DescribedRadioOptionUiState> options = List.of(
                cameraPipelineAutoOption(cameraId),
                cameraPipelineOption(cameraId, DeveloperSettingsStore.Mode.A,
                        R.string.camera_pipeline_a),
                cameraPipelineOption(cameraId, DeveloperSettingsStore.Mode.B,
                        R.string.camera_pipeline_b));
        Optional<MainActivityCameraPipelineSelection> selected = composition.cameraPipelineSelections()
                .stream()
                .filter(value -> value.cameraId().equals(cameraId))
                .findFirst()
                .map(value -> new MainActivityCameraPipelineSelection(
                        cameraPipelineLabel(value.pipelineId()), value.tupleCount()));
        String detail = selected.map(value -> cameraPipelineTupleDescription(
                value.tupleCount(), fullyVerified))
                .orElseGet(() -> getString(R.string.camera_pipeline_unknown));
        return new CameraDeveloperSettingsPresentation.CameraPipelineSelectionUiState(
                cameraId, selected.map(MainActivityCameraPipelineSelection::label)
                        .orElseGet(() -> getString(R.string.camera_pipeline_unknown)),
                detail,
                options, Math.max(0, CAMERA_PIPELINE_MODES.indexOf(selectedMode)),
                !fullyVerified || composition.cameraCapabilityRecheckInFlight()
                        || composition.cameraPipelineModeControlEnabled(cameraId));
    }

    private DescribedRadioOptionUiState cameraPipelineAutoOption(String cameraId) {
        Optional<MainActivityCameraPipelineSelection> selected = composition.cameraPipelineAutoSelections()
                .stream()
                .filter(value -> value.cameraId().equals(cameraId))
                .findFirst()
                .map(value -> new MainActivityCameraPipelineSelection(
                        cameraPipelineLabel(value.pipelineId()), value.tupleCount()));
        String description = selected.map(MainActivityCameraPipelineSelection::label)
                .orElseGet(() -> getString(R.string.camera_pipeline_auto_description));
        return new DescribedRadioOptionUiState(getString(R.string.camera_pipeline_auto), description);
    }

    private DescribedRadioOptionUiState cameraPipelineOption(String cameraId,
            DeveloperSettingsStore.Mode mode, int labelResource) {
        boolean fullyVerified = composition.cameraCapabilitiesFullyVerified();
        OptionalInt verifiedTupleCount = composition.cameraPipelineVerifiedTupleCount(cameraId, mode);
        OptionalInt fastTupleCount = composition.cameraPipelineCaptureTupleCount(cameraId, mode);
        OptionalInt displayedTupleCount = fullyVerified ? verifiedTupleCount : fastTupleCount;
        String description = displayedTupleCount.isPresent()
                ? cameraPipelineTupleDescription(displayedTupleCount.getAsInt(), fullyVerified)
                : getString(R.string.camera_pipeline_unknown);
        boolean optionEnabled = !fullyVerified || verifiedTupleCount.isPresent()
                && verifiedTupleCount.getAsInt() > 0;
        return new DescribedRadioOptionUiState(
                getString(labelResource), description, optionEnabled);
    }

    private String cameraPipelineTupleDescription(int tupleCount, boolean fullyVerified) {
        return getString(fullyVerified ? R.string.camera_pipeline_selection_detail
                : R.string.camera_pipeline_fast_build_detail, tupleCount);
    }

    private record MainActivityCameraPipelineSelection(String label, int tupleCount) {
    }

    private SettingsScreenModel locationSettingsModel() {
        GpsSettings current = locationSettings.currentSettings();
        LocationSystemState systemState = locationControl.currentState();
        boolean systemStateKnown = systemState == LocationSystemState.ENABLED
                || systemState == LocationSystemState.DISABLED;
        List<SettingItem> items = List.of(
                SettingItem.checkbox(SettingId.GPS_LOCATION_ENABLED,
                        getString(R.string.gps_use_location),
                        systemState == LocationSystemState.ENABLED
                                && hasRequiredLocationPermission())
                        .withEnabled(systemStateKnown),
                SettingItem.slider(SettingId.GPS_UPDATE_DISTANCE_METERS,
                        getString(R.string.gps_update_distance), 1, 30,
                        current.getUpdateDistanceMeters(), "m"),
                SettingItem.slider(SettingId.GPS_REPORT_INTERVAL_SECONDS,
                        getString(R.string.gps_report_interval), 1, 30,
                        current.getReportIntervalSeconds(), "s"));
        return new SettingsScreenModel(List.of(
                new SettingsSection(getString(R.string.gps_sampling_section), items)));
    }

    private DescribedRadioOptionUiState gpsModeOption(GpsMode mode, boolean enabled) {
        switch (mode) {
            case FUSED:
                return new DescribedRadioOptionUiState(getString(R.string.location_mode_fused),
                        getString(R.string.location_mode_fused_description), enabled);
            case SATELLITE:
                return new DescribedRadioOptionUiState(getString(R.string.location_mode_gnss),
                        getString(R.string.location_mode_gnss_description), enabled);
            default:
                throw new IllegalArgumentException("Unsupported location mode " + mode);
        }
    }

    private StorageOptionUiState storageOption(String label, StorageVolumeStatus volume) {
        if (volume == null || !volume.isAvailable()) {
            return new StorageOptionUiState(label, getString(R.string.storage_unavailable), 0, false);
        }
        long total = volume.getTotalBytes();
        int usedPercent = total <= 0L ? 0
                : (int) Math.round(volume.getUsedBytes() * 100.0 / total);
        String detail = getString(R.string.storage_usage,
                storageSize(volume.getUsedBytes()), storageSize(total));
        return new StorageOptionUiState(label, detail, usedPercent, true);
    }

    private static StorageVolumeStatus storageVolume(
            List<StorageVolumeStatus> volumes, MediaPartitionLocation mode) {
        for (StorageVolumeStatus volume : volumes) {
            if (volume.getMode() == mode)
                return volume;
        }
        return null;
    }

    private static String storageSize(long bytes) {
        double gib = bytes / (1024.0 * 1024.0 * 1024.0);
        return String.format(Locale.US, "%.1f GB", gib);
    }

    private SettingsScreenModel aboutSettingsModel() {
        String[] labels = visibleReadOnlySettings(MainScreen.ABOUT);
        String appVersionLabel = getResources().getStringArray(R.array.about_settings_items)[0];
        String[] values = new String[labels.length];
        for (int i = 0; i < labels.length; i++) {
            values[i] = labels[i].equals(appVersionLabel)
                    ? BuildConfig.VERSION_NAME : "Pending";
        }
        return settingsUiState.readOnly(labels, values);
    }
    private String[] visibleReadOnlySettings(MainScreen screen) {
        String[] labels = getResources().getStringArray(settingsItems(screen));
        List<String> visible = new ArrayList<>();
        for (int i = 0; i < labels.length; i++) {
            if (isReadOnlySettingVisible(screen, i))
                visible.add(labels[i]);
        }
        return visible.toArray(new String[0]);
    }

    private SettingsScreenModel filterUnavailableSettings(MainScreen screen, SettingsScreenModel model) {
        List<SettingsSection> sections = new ArrayList<>();
        for (SettingsSection section : model.getSections()) {
            List<SettingItem> visible = new ArrayList<>();
            for (SettingItem item : section.getItems()) {
                if (isSettingVisible(screen, item))
                    visible.add(item);
            }
            if (!visible.isEmpty())
                sections.add(new SettingsSection(section.getTitle(), visible));
        }
        return new SettingsScreenModel(sections);
    }

    private boolean isSettingVisible(MainScreen screen, SettingItem item) {
        if ((item.getId() == SettingId.WIFI_ENABLED || item.getId() == SettingId.WIFI_CONNECT)
                && !androidRuntime.isDeviceOwner())
            return false;
        return developerFeatureToggles.isSettingEnabled(screen, item);
    }

    private boolean isReadOnlySettingVisible(MainScreen screen, int index) {
        return developerFeatureToggles.isReadOnlySettingEnabled(screen, index);
    }

    private boolean isMenuTileVisible(MainScreen screen, FeatureGate gate) {
        return gate == null || developerFeatureToggles.isEffectivelyEnabled(gate);
    }

    private SettingsScreenModel withLanguage(SettingsScreenModel base) {
        List<SettingsSection> sections = new ArrayList<>(base.getSections());
        sections.add(languageSection());
        return new SettingsScreenModel(sections);
    }

    private SettingsSection languageSection() {
        if (languageSettings == null) {
            renderedLanguages = new AppLanguage[0];
            return new SettingsSection(getString(R.string.language_section_title),
                    List.of(SettingItem.text(getString(R.string.language_section_title), "Unavailable")));
        }
        AppLanguage current = languageSettings.currentLanguage();
        renderedLanguages = languageSettings.supportedLanguages();
        List<String> labels = new ArrayList<>();
        int selectedIndex = 0;
        for (int i = 0; i < renderedLanguages.length; i++) {
            labels.add(languageName(renderedLanguages[i]));
            if (renderedLanguages[i] == current)
                selectedIndex = i;
        }
        return new SettingsSection(getString(R.string.language_section_title),
                List.of(SettingItem.choice(SettingId.LANGUAGE,
                        getString(R.string.language_section_title), labels, selectedIndex)));
    }

    private boolean canOpenCameraSetting(String stableId) {
        if (captureRuntime != null && captureRuntime.canOpenCameraSetting(stableId))
            return true;
        FloatingNotice.show(this, cameraSettingBlockedNotice());
        return false;
    }

    private int cameraSettingBlockedNotice() {
        if (captureRuntime != null && captureRuntime.isRecording()) {
            return R.string.recording_camera_setting_change_blocked;
        }
        return R.string.camera_capabilities_recheck_busy;
    }

    private void selectCameraSetting(String stableId, int selectedIndex) {
        SettingItem item = currentSetting(stableId);
        if (captureRuntime == null || !captureRuntime.selectCameraSetting(stableId, selectedIndex)) {
            refreshCurrentSettingsControls();
            FloatingNotice.show(this, cameraSettingBlockedNotice());
            return;
        }
        refreshCurrentSettingsControls();
        logSelectedSettingChanged(item, stableId, selectedIndex);
    }

    private boolean canSelectDeveloperSetting(String stableId) {
        Optional<String> cameraId = CameraDeveloperSettingsPresentation.CameraPipelineSelectionUiState
                .cameraId(stableId);
        if (cameraId.isEmpty()
                || composition.canSelectCameraPipelineMode(cameraId.orElseThrow()))
            return true;
        FloatingNotice.show(this, R.string.camera_pipeline_busy);
        return false;
    }

    private void selectDeveloperSetting(String stableId, int selectedIndex) {
        Optional<String> cameraId = CameraDeveloperSettingsPresentation.CameraPipelineSelectionUiState
                .cameraId(stableId);
        if (cameraId.isPresent()) {
            selectCameraPipelineMode(cameraId.orElseThrow(), selectedIndex);
            return;
        }
        SettingId id = settingId(stableId);
        if (id != null)
            selectSetting(id, selectedIndex);
    }

    private void updateDeveloperNumberSetting(String stableId, int value) {
        SettingId id = settingId(stableId);
        if (id != null)
            updateNumberSetting(id, value);
    }

    private void updateDeveloperBooleanSetting(String stableId, boolean checked) {
        SettingId id = settingId(stableId);
        if (id != null)
            updateBooleanSetting(id, checked);
    }

    private void performDeveloperSettingAction(String stableId) {
        SettingId id = settingId(stableId);
        if (id != null)
            performSettingAction(id);
    }

    private SettingId settingId(String stableId) {
        if ("developer:recheck-camera-capabilities".equals(stableId)) {
            return SettingId.RECHECK_CAMERA_CAPABILITIES;
        }
        if (stableId == null || stableId.isBlank())
            return null;
        try {
            return SettingId.valueOf(stableId);
        } catch (IllegalArgumentException error) {
            return null;
        }
    }

    private SettingItem currentSetting(SettingId id) {
        return currentSetting(id, null);
    }

    private SettingItem currentSetting(String stableId) {
        return currentSetting(null, stableId);
    }

    private SettingItem currentSetting(SettingId id, String stableId) {
        if (latestState == null)
            return null;
        MainScreen screen = latestState.getScreen();
        if (screen == MainScreen.LOGIN || screen == MainScreen.CAMERA
                || screen == MainScreen.MENU || screen == MainScreen.FILES
                || screen == MainScreen.DEVELOPER_BUTTON_BINDINGS
                || screen == MainScreen.DEVELOPER_USERS) {
            return null;
        }
        for (SettingsSection section : settingsModel(screen).getSections()) {
            for (SettingItem item : section.getItems()) {
                if (id != null && item.getId() == id
                        || stableId != null && stableId.equals(item.getStableId())) {
                    return item;
                }
            }
        }
        return null;
    }

    private void logScreenNavigation(MainScreen previous, MainScreen current) {
        logger.info("Displayed " + screenName(current) + " screen. Previous screen: "
                + (previous == null ? "none" : screenName(previous)) + ".");
    }

    private static String screenName(MainScreen screen) {
        return switch (screen) {
            case LOGIN -> "login";
            case CAMERA -> "camera";
            case MENU -> "main menu";
            case FILES -> "files";
            case RECORD_SETTINGS -> "recording settings";
            case VIDEO_STREAM_SETTINGS -> "video streaming settings";
            case GPS_SETTINGS -> "GPS settings";
            case TRANSFER_SETTINGS -> "transfer settings";
            case USER_SETTINGS -> "user settings";
            case SERVER_SETTINGS -> "server settings";
            case STORAGE_SETTINGS -> "storage settings";
            case DEVICE_SETTINGS -> "device settings";
            case AUDIO_SETTINGS -> "audio settings";
            case CAMERA_SETTINGS -> "camera settings";
            case ABOUT -> "about";
            case DEVELOPER_SETTINGS -> "developer settings";
            case DEVELOPER_BUTTON_BINDINGS -> "developer button bindings";
            case DEVELOPER_USERS -> "developer users";
        };
    }

    private void logSelectedSettingChanged(
            SettingItem item, SettingId id, int selectedIndex) {
        if (item != null && item.getSelectedIndex() == selectedIndex)
            return;
        logSettingChanged(item, id, selectedSettingValue(item, selectedIndex));
    }

    private void logSelectedSettingChanged(
            SettingItem item, String stableId, int selectedIndex) {
        if (item != null && item.getSelectedIndex() == selectedIndex)
            return;
        logSettingChanged(item, stableId, selectedSettingValue(item, selectedIndex));
    }

    private void logNumberSettingChanged(SettingItem item, SettingId id, int value) {
        if (item != null && item.getNumberValue() == value)
            return;
        logSettingChanged(item, id, numberSettingValue(item, value));
    }

    private void logBooleanSettingChanged(SettingItem item, SettingId id, boolean checked) {
        if (item != null && item.isChecked() == checked)
            return;
        logSettingChanged(item, id, enabledValue(checked));
    }

    private void logSettingChanged(SettingItem item, SettingId id, String value) {
        logSettingChanged(item, item == null ? null : item.getStableId(), settingName(id), value);
    }

    private void logSettingChanged(SettingItem item, String stableId, String value) {
        logSettingChanged(item, stableId, settingName(stableId), value);
    }

    private void logSettingChanged(
            SettingItem item, String stableId, String fallbackName, String value) {
        String name = item == null ? fallbackName : item.getLabel();
        String previousValue = currentSettingValue(item);
        logger.info("Changed setting " + name + cameraSettingContext(stableId)
                + (previousValue == null || previousValue.equals(value)
                        ? "" : " from " + previousValue)
                + " to " + value + ".");
    }

    private static String currentSettingValue(SettingItem item) {
        if (item == null)
            return null;
        return switch (item.getType()) {
            case CHECKBOX -> enabledValue(item.isChecked());
            case CHOICE, RADIO, DESCRIBED_RADIO, STORAGE_RADIO ->
                    selectedSettingValue(item, item.getSelectedIndex());
            case SLIDER -> numberSettingValue(item, item.getNumberValue());
            default -> null;
        };
    }

    private static String selectedSettingValue(SettingItem item, int selectedIndex) {
        if (item == null)
            return "option " + selectedIndex;
        if (item.getType() == SettingItem.Type.DESCRIBED_RADIO
                && selectedIndex >= 0
                && selectedIndex < item.getDescribedRadioOptions().size()) {
            return item.getDescribedRadioOptions().get(selectedIndex).getLabel();
        }
        if (item.getType() == SettingItem.Type.STORAGE_RADIO
                && selectedIndex >= 0
                && selectedIndex < item.getStorageOptions().size()) {
            return item.getStorageOptions().get(selectedIndex).getLabel();
        }
        if (selectedIndex >= 0 && selectedIndex < item.getOptions().size()) {
            return item.getOptions().get(selectedIndex);
        }
        return "option " + selectedIndex;
    }

    private static String numberSettingValue(SettingItem item, int value) {
        if (item == null || item.getUnit() == null || item.getUnit().isBlank())
            return Integer.toString(value);
        return value + " " + item.getUnit();
    }

    private static String enabledValue(boolean enabled) {
        return enabled ? "enabled" : "disabled";
    }

    private static String settingName(SettingId id) {
        return settingName(id == null ? null : id.name());
    }

    private static String settingName(String stableId) {
        if (stableId == null || stableId.isBlank())
            return "unknown";
        return stableId.toLowerCase(Locale.ROOT)
                .replace('_', ' ').replace('-', ' ').replace(':', ' ');
    }

    private static String cameraSettingContext(String stableId) {
        if (stableId == null)
            return "";
        try {
            return " for camera " + CameraSettingControlId.parse(stableId).cameraId();
        } catch (IllegalArgumentException ignored) {
        }
        return CameraDeveloperSettingsPresentation.CameraPipelineSelectionUiState
                .cameraId(stableId)
                .map(cameraId -> " for camera " + cameraId)
                .orElse("");
    }
    private boolean canSelectSetting(SettingId id) {
        if (id != SettingId.DEV_PIPELINE_MODE || composition.canSelectCameraPipelineMode()) {
            return true;
        }
        FloatingNotice.show(this, R.string.camera_pipeline_busy);
        return false;
    }

    private void selectCameraPipelineMode(String cameraId, int selectedIndex) {
        if (selectedIndex < 0 || selectedIndex >= CAMERA_PIPELINE_MODES.size())
            return;
        if (cameraId == null && !composition.canSelectCameraPipelineMode()) {
            refreshCurrentSettingsControls();
            FloatingNotice.show(this, R.string.camera_pipeline_busy);
            return;
        }
        String stableId = cameraId == null
                ? "developer:pipeline-mode"
                : CameraDeveloperSettingsPresentation.CameraPipelineSelectionUiState
                        .stableId(cameraId);
        SettingItem item = currentSetting(stableId);
        DeveloperSettingsStore.Mode mode = CAMERA_PIPELINE_MODES.get(selectedIndex);
        Consumer<CameraPipelineModeController.Result> completion = result -> runOnUiThread(() -> {
            refreshCurrentSettingsControls();
            if (result == CameraPipelineModeController.Result.APPLIED) {
                logSelectedSettingChanged(item, stableId, selectedIndex);
            }
            FloatingNotice.show(this, cameraPipelineNotice(result));
        });
        if (cameraId == null)
            composition.selectCameraPipelineMode(mode, completion);
        else
            composition.selectCameraPipelineMode(cameraId, mode, completion);
    }

    static int cameraPipelineNotice(CameraPipelineModeController.Result result) {
        return switch (result) {
            case APPLIED -> R.string.camera_pipeline_applied;
            case REJECTED_BUSY -> R.string.camera_pipeline_busy;
            case REJECTED_UNAVAILABLE -> R.string.camera_pipeline_unknown;
            case FAILED -> R.string.camera_pipeline_failed;
        };
    }

    private void selectSetting(SettingId id, int selectedIndex) {
        SettingItem item = currentSetting(id);
        if (id == SettingId.DEV_PIPELINE_MODE) {
            selectCameraPipelineMode(null, selectedIndex);
            return;
        }
        if (id == SettingId.LANGUAGE) {
            if (selectedIndex >= 0 && selectedIndex < renderedLanguages.length) {
                changeLanguage(renderedLanguages[selectedIndex]);
            }
            return;
        }
        if (id == SettingId.DEFAULT_STORAGE) {
            List<MediaPartitionLocation> modes = storageSettings.supportedModes();
            if (selectedIndex >= 0 && selectedIndex < modes.size()) {
                MediaPartitionLocation mode = modes.get(selectedIndex);
                if (mode != MediaPartitionLocation.INTERNAL) {
                    refreshCurrentSettingsControls();
                    FloatingNotice.show(this, R.string.external_storage_not_supported);
                    return;
                }
                storageSettings.changeMode(mode);
                logSelectedSettingChanged(item, id, selectedIndex);
                recreate();
            }
            return;
        }
        if (id == SettingId.GPS_POSITIONING_MODE) {
            List<GpsMode> modes = locationSettings.supportedModes();
            if (selectedIndex >= 0 && selectedIndex < modes.size()) {
                GpsMode mode = modes.get(selectedIndex);
                if (!locationControl.isModeAvailable(mode))
                    return;
                locationSettings.changeMode(mode);
                logSelectedSettingChanged(item, id, selectedIndex);
                if (!hasRequiredLocationPermission())
                    launchLocationPermissionRequest();
                else
                    restartLocationTracking();
            }
            return;
        }
        settingsUiState.select(id, selectedIndex);
        logSelectedSettingChanged(item, id, selectedIndex);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (!isHomeLaunch(intent)) return;
        setIntent(intent);
        if (viewModel != null) {
            MainUiState current = viewModel.state().getValue();
            logger.info("Home button requested camera preview from "
                    + (current == null ? "unknown screen" : screenName(current.getScreen())) + ".");
            viewModel.show(MainScreen.CAMERA);
        }
    }

    private void updateHardwareButtonLayout(HardwareButtonLayout layout) {
        composition.updateHardwareButtonLayout(layout);
        if (hardwareButtons != null) {
            hardwareButtons.updateLayout(layout);
            refreshFirmwareHardwareButtonReceiver();
        }
        logger.info("Changed hardware button bindings. Active bindings: "
                + layout.bindings().size() + ".");
    }

    private void updateNumberSetting(SettingId id, int value) {
        SettingItem item = currentSetting(id);
        if (id == SettingId.GPS_UPDATE_DISTANCE_METERS) {
            locationSettings.changeUpdateDistanceMeters(value);
            restartLocationTracking();
            logNumberSettingChanged(item, id, value);
            return;
        }
        if (id == SettingId.GPS_REPORT_INTERVAL_SECONDS) {
            locationSettings.changeReportIntervalSeconds(value);
            restartLocationTracking();
            logNumberSettingChanged(item, id, value);
            return;
        }
        settingsUiState.updateNumber(id, value);
        if (id == SettingId.LOW_STORAGE_WARNING_GB) {
            storageSettings.changeWarningGb(value);
        }
        logNumberSettingChanged(item, id, value);
    }

    private void updateBooleanSetting(SettingId id, boolean checked) {
        SettingItem item = currentSetting(id);
        if (developerFeatureToggles.setEnabled(id, checked)) {
            if (id == SettingId.FEATURE_AUTHENTICATION) {
                viewModel.onAuthenticationSettingChanged();
                logBooleanSettingChanged(item, id, checked);
                return;
            }
            if (id == SettingId.FEATURE_GPS) {
                if (checked) {
                    refreshLocationTracking();
                } else {
                    stopLocationTracking();
                }
            }
            refreshDeveloperSettingsRows();
            logBooleanSettingChanged(item, id, checked);
            return;
        }
        if (id == SettingId.RESOURCE_MONITOR) {
            composition.setResourceMonitorEnabled(checked);
            resourceMonitorController.setEnabled(checked);
            logBooleanSettingChanged(item, id, checked);
            return;
        }
        if (id == SettingId.DEV_RELEASE_CAMERA_WHEN_SCREEN_OFF) {
            composition.setReleaseCameraWhenScreenOff(checked);
            if (checked) {
                requestIdleCameraReleaseIfScreenOff();
            } else {
                cancelIdleCameraRelease();
                composition.bindCameraIfPermitted();
            }
            logBooleanSettingChanged(item, id, checked);
            return;
        }
        if (id == SettingId.GPS_LOCATION_ENABLED) {
            if (handleLocationSwitchChanged(checked)) {
                logBooleanSettingChanged(item, id, checked);
            }
            return;
        }
        settingsUiState.updateBoolean(id, checked);
        if (id == SettingId.ENCRYPT_VIDEO_FILES && mediaEncryptionSettings != null) {
            mediaEncryptionSettings.setMediaEncryptionEnabled(checked);
        }
        if (id == SettingId.AUTO_ROTATE) {
            if (androidRuntime.setAutoRotateEnabled(checked)) {
                applyAutoRotate(checked);
                logBooleanSettingChanged(item, id, checked);
            } else {
                pendingAutoRotateValue = checked;
                syncAutoRotateFromDevice();
                requestWriteSystemSettingsAccess();
            }
            return;
        }
        if (id == SettingId.WIFI_ENABLED) {
            if (!androidRuntime.setWifiEnabled(checked)) {
                settingsUiState.updateBoolean(id, androidRuntime.isWifiEnabled());
                refreshCurrentSettingsControls();
                FloatingNotice.show(this, getString(R.string.wifi_requires_device_owner));
                return;
            }
            logBooleanSettingChanged(item, id, checked);
            return;
        }
        logBooleanSettingChanged(item, id, checked);
    }

    private void syncRecordingRotationLock() {
        boolean locked = composition != null && composition.cameraRecordingActiveForUi();
        if (recordingRotationLocked == locked)
            return;
        recordingRotationLocked = locked;
        refreshedDisplayRotationRuntime = null;
        refreshedDisplayRotation = -1;
        applyAutoRotate(androidRuntime.isAutoRotateEnabled());
        if (!locked) cameraClock.post(() -> refreshDisplayRotationIfNeeded(getDisplay()));
    }

    private void applyAutoRotate(boolean enabled) {
        boolean locked = recordingRotationLocked
                || composition != null && composition.cameraRecordingActiveForUi();
        setRequestedOrientation(locked
                ? ActivityInfo.SCREEN_ORIENTATION_LOCKED
                : enabled ? ActivityInfo.SCREEN_ORIENTATION_FULL_USER
                        : ActivityInfo.SCREEN_ORIENTATION_LOCKED);
    }

    private void syncAutoRotateFromDevice() {
        if (androidRuntime == null || settingsUiState == null)
            return;
        boolean enabled = androidRuntime.isAutoRotateEnabled();
        settingsUiState.updateBoolean(SettingId.AUTO_ROTATE, enabled);
        applyAutoRotate(enabled);
        if (latestState != null) {
            refreshCurrentSettingsControls();
        }
    }

    private void requestWriteSystemSettingsAccess() {
        if (androidRuntime == null || androidRuntime.canWriteSystemSettings()) {
            syncAutoRotateFromDevice();
            return;
        }
        if (writeSettingsRequestInFlight || isFinishing() || isDestroyed()
                || writeSettingsDialog != null && writeSettingsDialog.isShowing())
            return;
        Intent intent = new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS,
                Uri.parse("package:" + getPackageName()));
        if (intent.resolveActivity(getPackageManager()) == null) {
            FloatingNotice.show(this, R.string.write_settings_unavailable);
            return;
        }
        writeSettingsDialog = new AlertDialog.Builder(this)
                .setTitle(R.string.write_settings_title)
                .setMessage(R.string.write_settings_required)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> openWriteSystemSettings(intent))
                .setOnDismissListener(dialog -> writeSettingsDialog = null)
                .show();
    }

    private void openWriteSystemSettings(Intent intent) {
        if (writeSettingsRequestInFlight || isFinishing() || isDestroyed())
            return;
        writeSettingsRequestInFlight = true;
        try {
            startActivity(intent);
        } catch (RuntimeException failure) {
            writeSettingsRequestInFlight = false;
            logger.error("Unable to open modify system settings", failure);
            FloatingNotice.show(this, R.string.write_settings_unavailable);
        }
    }

    private boolean handleCameraCapabilityRecheckUpdate(
            AppComposition.CameraCapabilityRecheckUpdate update) {
        if (isFinishing() || isDestroyed())
            return false;
        if (update.status() == AppComposition.CameraCapabilityRecheckStatus.RUNNING) {
            refreshCapabilitySensitiveSettings();
            return true;
        }
        refreshCapabilitySensitiveSettings();
        if (update.status() == AppComposition.CameraCapabilityRecheckStatus.SUCCEEDED) {
            logger.info("Completed camera capability check successfully.");
            showCameraCapabilityResult(update);
            return false;
        }
        logger.warn("Camera capability check failed. Reason: " + update.detail() + ".", null);
        showCameraCapabilityResult(update);
        return false;
    }

    private void refreshCapabilitySensitiveSettings() {
        refreshDeveloperSettingsRows();
        refreshCurrentSettingsControls();
    }

    private void showCameraCapabilityResult(AppComposition.CameraCapabilityRecheckUpdate update) {
        if (isFinishing() || isDestroyed())
            return;
        if (cameraCapabilityResultDialog != null && cameraCapabilityResultDialog.isShowing()) {
            cameraCapabilityResultDialog.dismiss();
        }
        String message = update.status() == AppComposition.CameraCapabilityRecheckStatus.SUCCEEDED
                ? cameraCapabilityResultMessage(update)
                : getString(R.string.camera_capabilities_result_failed, update.detail());
        cameraCapabilityResultDialog = new AlertDialog.Builder(this)
                .setTitle(R.string.camera_capabilities_result_title)
                .setMessage(message)
                .setPositiveButton(android.R.string.ok,
                        (dialog, which) -> composition.acknowledgeCameraCapabilityRecheck())
                .setCancelable(false)
                .setOnDismissListener(dialog -> cameraCapabilityResultDialog = null)
                .create();
        cameraCapabilityResultDialog.show();
    }

    private String cameraCapabilityResultMessage(
            AppComposition.CameraCapabilityRecheckUpdate update) {
        StringBuilder message = new StringBuilder(getString(
                R.string.camera_capabilities_recheck_finished_summary,
                cameraVerificationSummary(update.pipelineATupleCount(),
                        update.pipelineAElapsedMillis()),
                cameraVerificationSummary(update.pipelineBTupleCount(),
                        update.pipelineBElapsedMillis())));
        if (update.summary().isEmpty() || update.summary().orElseThrow().failures().isEmpty()) {
            message.append("\n\n").append(getString(
                    R.string.camera_capabilities_no_failed_tuples));
            return message.toString();
        }
        message.append("\n\n").append(getString(
                R.string.camera_capabilities_failed_tuples_title));
        for (AppComposition.CameraCapabilityRecheckFailure failure : update.summary().orElseThrow().failures()) {
            message.append("\n\n").append(getString(
                    R.string.camera_capabilities_failure_header,
                    failure.profile(), failure.cameraId()));
            message.append("\n").append(getString(
                    R.string.camera_capabilities_tuple_failure,
                    failure.tuple(), failure.reason()));
        }
        return message.toString();
    }

    private String cameraVerificationSummary(OptionalInt count, OptionalLong elapsedMillis) {
        if (count.isEmpty() || elapsedMillis.isEmpty()) {
            return getString(R.string.camera_pipeline_unknown);
        }
        return getString(R.string.camera_pipeline_verified_summary, count.getAsInt(),
                elapsedMillis.getAsLong() / 1000.0);
    }

    private void performSettingAction(SettingId id) {
        if (id == SettingId.RECHECK_CAMERA_CAPABILITIES) {
            if (!composition.resetCameraCapabilities()) {
                FloatingNotice.show(this, R.string.camera_capabilities_recheck_busy);
                return;
            }
            logger.info("Started camera capability check from developer settings.");
            return;
        }
        if (id == SettingId.WIFI_CONNECT) {
            Intent intent = new Intent(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                    ? Settings.Panel.ACTION_WIFI
                    : Settings.ACTION_WIFI_SETTINGS);
            startActivity(intent);
            logger.info("Opened Android Wi-Fi settings from device settings.");
            return;
        }
        if (id == SettingId.LOGOUT) {
            viewModel.logout();
            logger.info("Logged out current operator from security settings.");
            return;
        }
        if (id == SettingId.CHANGE_OPERATOR_ID || id == SettingId.CHANGE_OPERATOR_PASSWORD) {
            FloatingNotice.show(this, R.string.account_change_pending);
            return;
        }
        throw new IllegalArgumentException("Setting " + id + " is not an action");
    }

    private void changeLanguage(AppLanguage language) {
        if (languageSettings == null || language == languageSettings.currentLanguage())
            return;
        languageSettings.changeLanguage(language);
        logSettingChanged(currentSetting(SettingId.LANGUAGE), SettingId.LANGUAGE,
                languageName(language));
        FloatingNotice.show(this, R.string.language_changed);
        recreate();
    }

    private String languageName(AppLanguage language) {
        switch (language) {
            case SYSTEM:
                return getString(R.string.language_system);
            case ENGLISH:
                return getString(R.string.language_english);
            case VIETNAMESE:
                return getString(R.string.language_vietnamese);
            default:
                throw new IllegalArgumentException("Unsupported language " + language);
        }
    }

    private void handleAboutSecretTap() {
        long now = System.currentTimeMillis();
        if (now - devModeTapWindowStartedAtMs > DEV_MODE_UNLOCK_WINDOW_MS) {
            devModeTapWindowStartedAtMs = now;
            devModeTapCount = 0;
        }
        devModeTapCount++;
        if (devModeTapCount < DEV_MODE_UNLOCK_TAPS)
            return;

        devModeTapCount = 0;
        devModeTapWindowStartedAtMs = 0L;
        FloatingNotice.show(this, R.string.developer_mode_unlocked);
        viewModel.show(MainScreen.DEVELOPER_SETTINGS);
    }

    private MainScreen safeScreen(MainScreen screen) {
        if (screen == MainScreen.LOGIN
                || screen == MainScreen.DEVELOPER_USERS
                || screen == MainScreen.DEVELOPER_BUTTON_BINDINGS)
            return screen;
        return menuModel.safeScreen(screen, this::isMenuTileVisible);
    }

    private void bindFeatureAction(View view, FeatureGate feature, Runnable action) {
        boolean enabled = developerFeatureToggles.isEffectivelyEnabled(feature);
        view.setVisibility(enabled ? View.VISIBLE : View.GONE);
        view.setOnClickListener(enabled
                ? ignored -> developerFeatureToggles.runIfEnabled(feature, action)
                : null);
    }

    private static int settingsTitle(MainScreen screen) {
        switch (screen) {
            case RECORD_SETTINGS:
                return R.string.record_settings;
            case CAMERA_SETTINGS:
                return R.string.camera_settings_short;
            case VIDEO_STREAM_SETTINGS:
                return R.string.video_stream_settings;
            case AUDIO_SETTINGS:
                return R.string.audio_settings;
            case STORAGE_SETTINGS:
                return R.string.storage_settings;
            case GPS_SETTINGS:
                return R.string.gps_settings;
            case DEVICE_SETTINGS:
                return R.string.device_settings_short;
            case USER_SETTINGS:
                return R.string.security_settings;
            case SERVER_SETTINGS:
                return R.string.network_settings;
            case TRANSFER_SETTINGS:
                return R.string.transfer_settings;
            case ABOUT:
                return R.string.about;
            case DEVELOPER_SETTINGS:
                return R.string.developer_mode;
            case DEVELOPER_BUTTON_BINDINGS:
                return R.string.button_role_bindings;
            case DEVELOPER_USERS:
                return R.string.developer_users;
            default:
                throw new IllegalArgumentException("No settings title for " + screen);
        }
    }

    private static int settingsItems(MainScreen screen) {
        switch (screen) {
            case RECORD_SETTINGS:
                return R.array.record_settings_items;
            case CAMERA_SETTINGS:
                return R.array.camera_settings_items;
            case VIDEO_STREAM_SETTINGS:
                return R.array.video_stream_settings_items;
            case AUDIO_SETTINGS:
                return R.array.audio_settings_items;
            case STORAGE_SETTINGS:
                return R.array.storage_settings_items;
            case GPS_SETTINGS:
                return R.array.gps_settings_items;
            case DEVICE_SETTINGS:
                return R.array.device_settings_items;
            case USER_SETTINGS:
                return R.array.security_settings_items;
            case SERVER_SETTINGS:
                return R.array.network_settings_items;
            case TRANSFER_SETTINGS:
                return R.array.transfer_settings_items;
            case ABOUT:
                return R.array.about_settings_items;
            default:
                throw new IllegalArgumentException("No settings list for " + screen);
        }
    }

    private void updateStatus(MainUiState state) {
        recordingStatusRenderer.updateFloatingRecordingStatus(state);
        if (cameraScreen != null) {
            boolean videoRecording = (state.getCapture().isVideoRecording()
                    && !state.getCapture().isSaving());
            cameraScreen.recordingBadge.setAlpha(RECORDING_BADGE_ACTIVE_ALPHA);
            updateGpsStatusLine();
            if (state.getOperatorSession() != null) {
                cameraScreen.operatorId.setText("USER " + state.getOperatorSession().getFileUserId());
            } else {
                cameraScreen.operatorId.setText("USER —");
            }
            recordingStatusRenderer.updateCameraClock();
        }
        if (loginScreen != null) {
            loginScreen.loginAction.setEnabled(!state.isAuthenticationBusy());
            loginScreen.password.setEnabled(!state.isAuthenticationBusy());
            loginScreen.status.setText(state.isAuthenticationBusy()
                    ? "Loading..."
                    : localizedMessage(state.getMessage()));
        }
        if (developerUsersScreen != null) {
            developerUsersScreen.saveAction.setEnabled(!state.isAuthenticationBusy());
            developerUsersScreen.status.setText(state.isAuthenticationBusy()
                    ? "Saving..."
                    : localizedMessage(state.getMessage()));
        }
        if (fileExplorerScreen != null)
            mediaBrowserRenderer.render(state.getMediaBrowser());
    }

    private String localizedMessage(String message) {
        if (message == null)
            return "";
        return message.startsWith("Saved ")
                ? getString(R.string.media_saved, message.substring(6))
                : message;
    }

    private void refreshStorageVolumes() {
        if (!storageVolumesStale
                || !storageVolumesRefreshInFlight.compareAndSet(false, true)) return;
        long expectedGeneration = storageVolumesGeneration;
        composition.readStorageVolumes(volumes -> {
            storageVolumesRefreshInFlight.set(false);
            if (expectedGeneration != storageVolumesGeneration) {
                refreshStorageVolumes();
                return;
            }
            storageVolumes = volumes;
            storageVolumesStale = false;
            if (!isFinishing() && !isDestroyed() && latestState != null
                    && latestState.getScreen() == MainScreen.STORAGE_SETTINGS) {
                refreshCurrentSettingsControls();
            }
        });
    }

    private void updateStorageWarning() {
        if (captureRuntime == null || cameraScreen == null
                || latestState == null || latestState.getScreen() != MainScreen.CAMERA) {
            FloatingNotice.hideLowPriorityPersistent();
            storageWarningVisible = null;
            return;
        }
        if (!storageWarningRefreshInFlight.compareAndSet(false, true)) return;
        composition.readStorageWarning(warning -> {
            storageWarningRefreshInFlight.set(false);
            if (captureRuntime == null || cameraScreen == null
                    || latestState == null || latestState.getScreen() != MainScreen.CAMERA) {
                FloatingNotice.hideLowPriorityPersistent();
                storageWarningVisible = null;
                return;
            }
            long free = warning.getFreeBytes();
            boolean warningVisible = warning.isVisible();
            if (warningVisible) {
                FloatingNotice.showLowPriorityPersistent(this,
                        getString(R.string.low_storage_preview_warning, storageSize(free)),
                        warning.isRecordingBlocked() ? FloatingNotice.ERROR_TEXT_COLOR : FloatingNotice.WARNING_TEXT_COLOR);
            } else if (storageWarningVisible == null || storageWarningVisible) {
                FloatingNotice.hideLowPriorityPersistent();
            }
            storageWarningVisible = warningVisible;
        });
    }

    private String gpsCoordinatesText() {
        if (currentGpsCoordinate != null)
            return currentGpsCoordinate.toString();
        switch (locationTrackingState) {
            case PERMISSION_REQUIRED:
                return getString(R.string.gps_permission_required);
            case LOCATION_DISABLED:
                return getString(R.string.gps_location_disabled);
            case LOCATION_UNAVAILABLE:
            case NO_PROVIDER:
                return getString(R.string.gps_unavailable);
            case ERROR:
                return getString(R.string.gps_provider_error);
            case WAITING_FOR_LOCATION_INFO:
            case AVAILABLE:
                return getString(R.string.gps_waiting_for_location_info);
            case STOPPED:
            default:
                return getString(R.string.gps_unavailable);
        }
    }

    private void updateGpsStatusLine() {
        if (cameraScreen == null)
            return;
        boolean visible = developerFeatureToggles.isEffectivelyEnabled(FeatureGate.GPS)
                && locationTrackingCoordinator != null
                && locationTrackingCoordinator.shouldShowOnCamera();
        cameraScreen.gpsStatus.setVisibility(visible ? View.VISIBLE : View.GONE);
        cameraScreen.gpsStatus.setText(visible ? gpsCoordinatesText() : "");
    }

    private boolean handleLocationSwitchChanged(boolean enabled) {
        if (locationControl == null)
            return false;
        if (enabled && !hasRequiredLocationPermission()) {
            requestLocationPermission();
            refreshCurrentSettingsControls();
            return false;
        }
        boolean changed = locationControl.setEnabledIfPermitted(enabled);
        refreshCurrentSettingsControls();
        if (!changed) {
            pendingLocationEnabled = enabled;
            try {
                startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
            } catch (RuntimeException ignored) {
                FloatingNotice.show(this, R.string.gps_settings_unavailable);
            }
            return false;
        }
        refreshLocationTracking();
        return true;
    }

    private void refreshLocationTracking() {
        if (locationTrackingCoordinator == null)
            return;
        locationTrackingCoordinator.refresh();
        syncLocationTrackingPresentation();
    }

    private void restartLocationTracking() {
        if (locationTracking == null)
            return;
        refreshLocationTracking();
    }

    private void stopLocationTracking() {
        if (locationTrackingCoordinator != null)
            locationTrackingCoordinator.stop();
        else if (locationTracking != null)
            locationTracking.stop();
        syncLocationTrackingPresentation();
    }

    private void onLocationTrackingStateChanged() {
        runOnUiThread(() -> {
            syncLocationTrackingPresentation();
            updateGpsStatusLine();
        });
    }

    private void syncLocationTrackingPresentation() {
        if (locationTrackingCoordinator == null) {
            currentGpsCoordinate = null;
            locationTrackingState = LocationTrackingState.STOPPED;
            return;
        }
        currentGpsCoordinate = locationTrackingCoordinator.currentCoordinate();
        locationTrackingState = locationTrackingCoordinator.currentState();
    }

    private void requestLocationPermission() {
        stopLocationTracking();
        locationPermissionSettingsFallbackPending = true;
        if (!launchLocationPermissionRequest()) {
            locationPermissionSettingsFallbackPending = false;
            openLocationPermissionSettings();
        }
    }

    private void openLocationPermissionSettings() {
        try {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        } catch (RuntimeException ignored) {
            FloatingNotice.show(this, R.string.gps_permission_required);
        }
    }

    private boolean launchLocationPermissionRequest() {
        if (locationPermissionLauncher == null || locationPermissionRequestInFlight
                || hasRequiredLocationPermission())
            return false;
        String[] missingLocationPermissions = androidRuntime.missingLocationPermissions();
        if (missingLocationPermissions.length == 0)
            return false;
        locationPermissionRequestInFlight = true;
        locationPermissionLauncher.launch(missingLocationPermissions);
        return true;
    }

    private boolean canShowRequiredLocationPermissionRationale() {
        return shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION);
    }

    private void refreshGpsPermissionRetry() {
        if (gpsPermissionRetryScheduler != null)
            gpsPermissionRetryScheduler.refresh();
    }

    private boolean shouldRetryGpsPermission() {
        return developerFeatureToggles != null
                && developerFeatureToggles.isEffectivelyEnabled(FeatureGate.GPS)
                && !hasRequiredLocationPermission()
                && !locationPermissionRequestInFlight
                && latestState != null
                && !latestState.getCapture().isVideoRecording()
                && !latestState.getCapture().isAudioRecording()
                && !isFinishing()
                && !isDestroyed()
                && getLifecycle().getCurrentState().isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED);
    }

    private boolean hasRequiredLocationPermission() {
        return androidRuntime.fineLocationPermissionGranted();
    }

    private void navigateBack() {
        MainScreen screen = latestState == null ? MainScreen.CAMERA : latestState.getScreen();
        if (screen == MainScreen.LOGIN)
            return;
        if (screen == MainScreen.CAMERA)
            viewModel.show(MainScreen.MENU);
        else if (screen == MainScreen.MENU)
            viewModel.show(MainScreen.CAMERA);
        else if (screen == MainScreen.FILES && viewModel.navigateMediaUp())
            return;
        else if (screen == MainScreen.DEVELOPER_USERS
                || screen == MainScreen.DEVELOPER_BUTTON_BINDINGS) {
            viewModel.show(MainScreen.DEVELOPER_SETTINGS);
        } else
            viewModel.show(MainScreen.MENU);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        return handleHardwareKeyDown(
                keyCode, event.getRepeatCount(), event.getEventTime())
                || super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        return handleHardwareKeyUp(keyCode, event.isCanceled())
                || super.onKeyUp(keyCode, event);
    }

    private boolean handleHardwareKeyDown(int keyCode, int repeatCount, long eventTimeMs) {
        if (renderedScreen == MainScreen.DEVELOPER_BUTTON_BINDINGS
                && developerButtonBindingsScreen.onKeyDown(keyCode))
            return true;
        long deliveredAtMs = SystemClock.uptimeMillis();
        boolean handled = hardwareButtons != null
                && hardwareButtons.onKeyDown(keyCode, repeatCount, eventTimeMs);
        if (handled) restartIdleCameraReleaseDelayAfterHardwareCommandIfScreenOff();
        if (handled && repeatCount == 0 && logger != null) {
            logger.info("Accepted hardware button press. Key code: " + keyCode
                    + ". Main-thread delivery lag: "
                    + Math.max(0L, deliveredAtMs - eventTimeMs) + " ms.");
        }
        if (handled && repeatCount == 0 && hardwareButtons.isSosButton(keyCode)) {
            sosHoldAction = () -> hardwareButtons.onSosHoldThreshold(keyCode);
            cameraClock.postDelayed(sosHoldAction, HardwareButtonRouter.SOS_HOLD_MS);
        }
        return handled;
    }

    private boolean handleHardwareKeyUp(int keyCode, boolean canceled) {
        if (!hasWindowFocus() && hardwareButtons != null) {
            hardwareButtons.clearFocusTransientState();
        }
        if (renderedScreen == MainScreen.DEVELOPER_BUTTON_BINDINGS
                && developerButtonBindingsScreen.onKeyUp(keyCode)) {
            if (hardwareButtons != null)
                hardwareButtons.clearTransientState();
            return true;
        }
        if (hardwareButtons != null && hardwareButtons.isSosButton(keyCode)
                && sosHoldAction != null) {
            cameraClock.removeCallbacks(sosHoldAction);
            sosHoldAction = null;
        }
        boolean handled = hardwareButtons != null && hardwareButtons.onKeyUp(keyCode, canceled);
        if (handled) restartIdleCameraReleaseDelayAfterHardwareCommandIfScreenOff();
        return handled;
    }

    private boolean handleFirmwareBroadcastDown(String action, long eventTimeMs) {
        boolean handled = hardwareButtons != null
                && hardwareButtons.onFirmwareBroadcastDown(action, 0, eventTimeMs);
        if (handled) restartIdleCameraReleaseDelayAfterHardwareCommandIfScreenOff();
        if (handled && logger != null) {
            logger.info("Accepted firmware hardware button press. Broadcast action: "
                    + action + ".");
        }
        if (handled && hardwareButtons.isSosFirmwareBroadcastDownAction(action)) {
            sosHoldAction =
                    () -> hardwareButtons.onSosHoldThresholdForFirmwareBroadcast(action);
            cameraClock.postDelayed(sosHoldAction, HardwareButtonRouter.SOS_HOLD_MS);
        }
        return handled;
    }

    private boolean handleFirmwareBroadcastUp(String action, boolean canceled) {
        if (hardwareButtons == null)
            return false;
        hardwareButtons.clearFocusTransientState();
        if (hardwareButtons.isSosFirmwareBroadcastUpAction(action) && sosHoldAction != null) {
            cameraClock.removeCallbacks(sosHoldAction);
            sosHoldAction = null;
        }
        boolean handled = hardwareButtons.onFirmwareBroadcastUp(action, canceled);
        if (handled) restartIdleCameraReleaseDelayAfterHardwareCommandIfScreenOff();
        return handled;
    }

    private void refreshFirmwareHardwareButtonReceiver() {
        unregisterFirmwareHardwareButtonReceiver();
        if (hardwareButtons == null)
            return;
        List<String> actions = hardwareButtons.firmwareBroadcastActions();
        if (actions.isEmpty())
            return;
        IntentFilter filter = new IntentFilter();
        for (String action : actions) filter.addAction(action);
        ContextCompat.registerReceiver(this, firmwareHardwareButtonReceiver, filter,
                ContextCompat.RECEIVER_NOT_EXPORTED);
        firmwareHardwareButtonReceiverRegistered = true;
    }

    private void unregisterFirmwareHardwareButtonReceiver() {
        if (!firmwareHardwareButtonReceiverRegistered)
            return;
        unregisterReceiver(firmwareHardwareButtonReceiver);
        firmwareHardwareButtonReceiverRegistered = false;
    }

    @Override
    protected void onDestroy() {
        if (redirectingToHomeTask) {
            super.onDestroy();
            return;
        }
        activityInstanceCount--;
        unsubscribeCameraCapabilityRecheck();
        if (idleCameraReleaseStateSubscription != null) {
            idleCameraReleaseStateSubscription.close();
            idleCameraReleaseStateSubscription = null;
        }
        unregisterReceiver(wifiStateReceiver);
        unregisterReceiver(storageMountedReceiver);
        unregisterReceiver(screenStateReceiver);
        logger.info("MainActivity destroyed");
        FloatingNotice.clear(this);
        cameraClock.removeCallbacks(cameraClockTick);
        cameraClock.removeCallbacks(recordingDurationTick);
        cameraClock.removeCallbacks(cameraSwitchActionRefresh);
        cancelIdleCameraRelease();
        if (gpsPermissionRetryScheduler != null)
            gpsPermissionRetryScheduler.stop();
        stopLocationTracking();
        releaseCaptureRuntime();
        if (isFinishing() && activityInstanceCount == 0 && composition != null) {
            composition.releaseCameraForActivityFinish();
        }
        identityIoExecutor.shutdown();
        resourceMonitorController.close();
        super.onDestroy();
    }

    private static String identity(Object value) {
        return value == null ? "null"
                : value.getClass().getSimpleName() + "@"
                        + Integer.toHexString(System.identityHashCode(value));
    }

    private static String shortId(String value) {
        if (value == null)
            return "null";
        return Integer.toHexString(value.hashCode());
    }

    private void ensureCaptureRuntime() {
        if (captureRuntime != null)
            return;
        captureRuntime = composition.createCaptureRuntime(this);
        captureRuntime.observeCameraSwitchState(this::requestCameraSwitchActionUpdate);
        viewModel.bindCaptureEvents(captureRuntime.captureEvents());
        hardwareButtons = composition.createHardwareButtonRouter(
                captureRuntime.photoCapture(), captureRuntime.videoRecording(),
                captureRuntime.audioRecording(),
                () -> {
                    if (locationTrackingCoordinator != null)
                        locationTrackingCoordinator.requestCurrentLocation();
                },
                this::vibrateCaptureCommandStart);
        refreshFirmwareHardwareButtonReceiver();
    }

    private void releaseCaptureRuntime() {
        unregisterFirmwareHardwareButtonReceiver();
        if (captureRuntime == null)
            return;
        if (viewModel != null) {
            viewModel.unbindCaptureEvents(captureRuntime.captureEvents());
        }
        captureRuntime.release();
        FloatingNotice.hideLowPriorityPersistent();
        storageWarningVisible = null;
        captureRuntime = null;
        refreshedDisplayRotationRuntime = null;
        refreshedDisplayRotation = -1;
        cameraScreen = null;
        hardwareButtons = null;
        if (sosHoldAction != null)
            cameraClock.removeCallbacks(sosHoldAction);
        sosHoldAction = null;
    }

    private void clearScreenBindings() {
        loginScreen = null;
        developerUsersScreen = null;
        fileExplorerScreen = null;
        menuScreen = null;
        renderedSettingsList = null;
        renderedSettingsScroll = null;
    }

    private void removeNonCameraScreens() {
        if (cameraScreen != null)
            cameraScreen.getRoot().setAlpha(0f);
        for (int index = root.getChildCount() - 1; index >= 0; index--) {
            View child = root.getChildAt(index);
            if (cameraScreen == null || child != cameraScreen.getRoot())
                root.removeViewAt(index);
        }
    }
}
