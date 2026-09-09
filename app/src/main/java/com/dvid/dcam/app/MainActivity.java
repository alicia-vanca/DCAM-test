package com.dvid.dcam.app;

import android.Manifest;
import android.app.AlertDialog;
import android.app.role.RoleManager;
import android.content.Context;
import android.content.BroadcastReceiver;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
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
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.EditText;
import android.text.InputFilter;
import android.text.InputType;
import android.text.Editable;
import android.text.TextWatcher;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.fragment.app.FragmentActivity;
import com.dvid.dcam.R;
import com.dvid.dcam.app.devmode.DeveloperFeatureToggles;
import com.dvid.dcam.app.resourcemonitor.ResourceMonitorController;
import com.dvid.dcam.app.ui.ActivityChromeController;
import com.dvid.dcam.app.ui.CameraIdentityPresentation;
import com.dvid.dcam.app.ui.CameraScreenView;
import com.dvid.dcam.app.ui.CaptureFailureNoticePolicy;
import com.dvid.dcam.app.ui.camera.CameraFlowCoordinator;
import com.dvid.dcam.app.ui.FloatingNotice;
import com.dvid.dcam.app.ui.RecordingStatusRenderer;
import com.dvid.dcam.app.ui.MainScreen;
import com.dvid.dcam.app.ui.MainMenuModel;
import com.dvid.dcam.app.ui.GpsPermissionRetryScheduler;
import com.dvid.dcam.app.ui.MainUiState;
import com.dvid.dcam.app.ui.MainUiScope;
import com.dvid.dcam.app.ui.MainViewModel;
import com.dvid.dcam.app.ui.MainViewModelFactory;
import com.dvid.dcam.app.ui.LocationTrackingCoordinator;
import com.dvid.dcam.app.ui.StorageWarningNoticePolicy;
import com.dvid.dcam.app.ui.StorageSizeFormatter;
import com.dvid.dcam.core.featuregate.domain.FeatureGate;
import com.dvid.dcam.databinding.ActivityMainBinding;
import com.dvid.dcam.feature.location.application.usecase.LocationControlUseCase;
import com.dvid.dcam.feature.location.application.usecase.LocationSettingsUseCase;
import com.dvid.dcam.feature.location.application.usecase.LocationTrackingUseCase;
import com.dvid.dcam.feature.location.domain.GpsCoordinate;
import com.dvid.dcam.feature.location.domain.LocationTrackingState;
import com.dvid.dcam.feature.device.application.usecase.DeviceSerialNumberUseCase;
import com.dvid.dcam.app.ui.settings.SettingsScreenCatalog;
import com.dvid.dcam.app.ui.settings.SettingsScreenCoordinator;
import com.dvid.dcam.app.ui.settings.DeveloperButtonBindingsController;
import com.dvid.dcam.app.ui.navigation.MainNavigationCoordinator;
import com.dvid.dcam.app.ui.navigation.MainNavigationGraph;
import com.dvid.dcam.app.ui.navigation.FragmentRouteRenderer;
import com.dvid.dcam.app.ui.navigation.MainScreenRouter;
import com.dvid.dcam.app.ui.navigation.NavigationGraph;
import com.dvid.dcam.app.ui.navigation.PersistentCameraLayer;
import com.dvid.dcam.core.input.domain.HardwareButtonLayout;
import com.dvid.dcam.app.ui.input.HardwareButtonRouter;
import com.dvid.dcam.core.logging.domain.LogCategory;
import com.dvid.dcam.core.logging.application.port.Logger;
import java.util.List;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/** Android entry point and ViewBinding presentation shell. */
public final class MainActivity extends FragmentActivity implements MainUiScope.Provider {
    private static final long GPS_PERMISSION_RETRY_DELAY_MS = 5 * 60_000L;
    private static final long CAPTURE_VIBRATION_MS = 150L;
    private static final long SAVED_NOTICE_MAX_AGE_MS = FloatingNotice.TRANSIENT_DURATION_MS;
    private static final long SCREEN_OFF_CAMERA_RELEASE_DELAY_MS = 60_000L;
    private static final long STORAGE_WARNING_REFRESH_INTERVAL_MS = 5_000L;
    private static int activityInstanceCount;
    private static final int CAPTURE_VIBRATION_AMPLITUDE = 255;
    private static final String STATE_DEFAULT_HOME_REQUESTED = "default_home_requested";
    private static final String STATE_CAPTURE_PERMISSION_FLOW_STARTED = "capture_permission_flow_started";
    private static final String STATE_CAPTURE_PERMISSION_POLICY_APPLIED = "capture_permission_policy_applied";
    private static final String STATE_CAPTURE_PERMISSION_REQUEST_IN_FLIGHT = "capture_permission_request_in_flight";
    private static final String STATE_CAPTURE_PERMISSION_SETTINGS_IN_FLIGHT = "capture_permission_settings_in_flight";
    private static final String STATE_STARTUP_GPS_PERMISSION_HANDLED = "startup_gps_permission_handled";
    private static final String STATE_DEVICE_IDENTITY_PENDING = "device_identity_pending";
    private static final String PACKAGE_URI_PREFIX = "package:";
    private static final String UNKNOWN_VALUE = "unknown";
    private static final String LOG_SCREEN_SUFFIX = " screen=";
    private static final String LOG_CAPTURE_MODE_SUFFIX = " captureMode=";
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
    private long nextStorageWarningRefreshAtMillis;
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
    private DeviceSerialNumberUseCase deviceSerialNumbers;
    private DeveloperFeatureToggles developerFeatureToggles;
    private LocationSettingsUseCase locationSettings;
    private LocationControlUseCase locationControl;
    private LocationTrackingUseCase locationTracking;
    private LocationTrackingCoordinator locationTrackingCoordinator;
    private GpsCoordinate currentGpsCoordinate;
    private LocationTrackingState locationTrackingState = LocationTrackingState.STOPPED;
    private SettingsScreenCoordinator settingsCoordinator;
    private AppComposition.AndroidRuntime androidRuntime;
    private MainMenuModel menuModel;
    private final SettingsScreenCatalog settingsScreenCatalog = new SettingsScreenCatalog();
    private MainNavigationCoordinator navigation;
    private MainUiScope mainUiScope;
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
    private ActivityChromeController activityChrome;
    private ResourceMonitorController resourceMonitorController;
    private RecordingStatusRenderer recordingStatusRenderer;
    private ContentObserver autoRotateObserver;
    private DisplayManager displayManager;
    private boolean writeSettingsRequestInFlight;
    private AlertDialog writeSettingsDialog;
    private AlertDialog cameraCapabilityResultDialog;
    private MainUiState latestState;
    private CameraScreenView cameraScreen;
    private Boolean storageWarningVisible;
    private final AtomicBoolean storageWarningRefreshInFlight = new AtomicBoolean();

    private Runnable sosHoldAction;
    private final DisplayManager.DisplayListener displayRotationListener = new DisplayManager.DisplayListener() {
        @Override
        public void onDisplayAdded(int displayId) {
            // Only changes to the active display affect this activity's rotation.
        }

        @Override
        public void onDisplayRemoved(int displayId) {
            // The active display is not removed while this activity is attached.
        }

        @Override
        public void onDisplayChanged(int displayId) {
            Display display = currentActivityDisplay();
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
                if (settingsCoordinator != null) {
                    settingsCoordinator.onLocationSystemStateChanged();
                }
            }
        }
    };
    private final BroadcastReceiver storageMountedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (settingsCoordinator != null) settingsCoordinator.onStorageMounted();
            composition.refreshCaptureStorageNotice();
            if (latestState == null) return;
            if (latestState.getScreen() == MainScreen.FILES) {
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
            if (committedScreen() == MainScreen.DEVELOPER_BUTTON_BINDINGS) {
                hardwareButtons.clearTransientState();
                return;
            }
            String action = intent.getAction();
            if (action == null)
                return;
            boolean activityResumed = getLifecycle().getCurrentState().isAtLeast(
                    androidx.lifecycle.Lifecycle.State.RESUMED);
            boolean screenInteractive = androidRuntime == null
                    || androidRuntime.isScreenInteractive();
            boolean cameraRecording = captureRuntime != null && captureRuntime.isRecording();
            if (!shouldAcceptFirmwareHardwareInput(
                    activityResumed, screenInteractive, cameraRecording)) {
                hardwareButtons.clearTransientState();
                if (logger != null) {
                    logger.info(LogCategory.APP,
                            "firmware_hardware_input_ignored",
                            "Ignored firmware hardware button press for action " + action
                            + " because DCAM is not foreground, the screen is interactive, and no"
                            + " video or IMP recording is active.");
                }
                return;
            }
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
        deviceSerialNumbers = composition.deviceSerialNumberUseCase();
        developerFeatureToggles = composition.developerFeatureToggles();
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
        recordingRotationLocked = composition.cameraRecordingActiveForUi();
        applyAutoRotate(androidRuntime.isAutoRotateEnabled());
        menuModel = new MainMenuModel();

        getWindow().setNavigationBarColor(Color.BLACK);
        ActivityMainBinding activityBinding = ActivityMainBinding.inflate(getLayoutInflater());
        activityChrome = new ActivityChromeController(this, activityBinding, androidRuntime::isDeviceOwner);
        recordingStatusRenderer = new RecordingStatusRenderer(
                activityBinding, () -> cameraScreen, () -> latestState, this::committedScreen);
        resourceMonitorController = new ResourceMonitorController(this,
                activityBinding.getRoot(), androidRuntime::isDeviceOwner, logger,
                composition.resourceMonitorEnabled());
        activityChrome.updateManagedTopBar();
        activityChrome.bindSystemNavigationInset();

        permissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                result -> {
                    capturePermissionRequestInFlight = false;
                    boolean captureGranted = androidRuntime.capturePermissionsGranted();
                    logger.info(LogCategory.SECURITY, "unspecified", "PERMISSION_TRACE runtime-result captureGranted=" + captureGranted
                            + " missingCapturePermissions="
                            + androidRuntime.missingCapturePermissions().length);
                    prepareCameraProfilesIfCameraGranted();
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
                    prepareCameraProfilesIfCameraGranted();
                    if (androidRuntime.capturePermissionsGranted()) {
                        completeCapturePermissionGate("settings-result");
                    } else {
                        logger.warn(LogCategory.SECURITY, "unspecified", null, "PERMISSION_TRACE settings-result captureGranted=false", null);
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
                    settingsCoordinator.onLocationSystemStateChanged();
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
                if (navigation != null) navigation.back();
            }
        });

        logger.info(LogCategory.AUTH, "unspecified", "Authentication is "
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

        NavigationGraph<MainScreen> mainNavigationGraph =
                MainNavigationGraph.create(settingsScreenCatalog);
        FragmentRouteRenderer<MainScreen> foregroundRenderer = new FragmentRouteRenderer<>(
                getSupportFragmentManager(), R.id.foreground_layer);
        MainScreenRouter screenRouter = new MainScreenRouter(
                mainNavigationGraph, foregroundRenderer,
                new PersistentCameraLayer(getSupportFragmentManager(), R.id.camera_layer,
                        mainNavigationGraph.require(MainScreen.CAMERA).tag(),
                        activityBinding.foregroundLayer, () -> cameraScreen,
                        this::attachCameraView),
                this::onDestinationReady);
        navigation = new MainNavigationCoordinator(
                mainNavigationGraph,
                () -> latestState == null ? MainScreen.CAMERA : latestState.getScreen(),
                viewModel::show,
                menuModel, this::isMenuTileVisible,
                screenRouter,
                this::onRouteAccepted, logger);
        settingsCoordinator = new SettingsScreenCoordinator(this, settingsScreenCatalog,
                composition, composition.languageSettingsUseCase(),
                composition.mediaEncryptionSettings(), composition.storageSettingsUseCase(),
                locationSettings, locationControl, developerFeatureToggles,
                new DeveloperButtonBindingsController(
                        composition.configureHardwareButtonsUseCase()),
                logger, new SettingsScreenCoordinator.PlatformActions() {
                    @Override public boolean isVideoRecording() {
                        return latestState != null
                                && latestState.getCapture().isVideoRecording();
                    }

                    @Override public boolean canOpenCameraSetting(String stableId) {
                        return captureRuntime != null
                                && captureRuntime.canOpenCameraSetting(stableId);
                    }

                    @Override public boolean selectCameraSetting(
                            String stableId, int selectedIndex) {
                        return captureRuntime != null
                                && captureRuntime.selectCameraSetting(stableId, selectedIndex);
                    }

                    @Override public boolean isCameraRecording() {
                        return captureRuntime != null && captureRuntime.isRecording();
                    }

                    @Override public boolean hasRequiredLocationPermission() {
                        return MainActivity.this.hasRequiredLocationPermission();
                    }

                    @Override public void requestLocationPermission() {
                        MainActivity.this.requestLocationPermission();
                    }

                    @Override public void openLocationSettings() {
                        try {
                            startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
                        } catch (RuntimeException ignored) {
                            FloatingNotice.show(MainActivity.this,
                                    R.string.gps_settings_unavailable);
                        }
                    }

                    @Override public void refreshLocationTracking() {
                        MainActivity.this.refreshLocationTracking();
                    }

                    @Override public void stopLocationTracking() {
                        MainActivity.this.stopLocationTracking();
                    }

                    @Override public void onAuthenticationSettingChanged() {
                        viewModel.onAuthenticationSettingChanged();
                    }

                    @Override public void setResourceMonitorEnabled(boolean enabled) {
                        resourceMonitorController.setEnabled(enabled);
                    }

                    @Override public void onReleaseCameraWhenScreenOffChanged(boolean enabled) {
                        if (enabled) {
                            requestIdleCameraReleaseIfScreenOff();
                        } else {
                            cancelIdleCameraRelease();
                            composition.bindCameraIfPermitted();
                        }
                    }

                    @Override public void setFullScreenDisplayEnabled(boolean enabled) {
                        activityChrome.setFullScreenDisplayEnabled(enabled);
                    }

                    @Override public boolean setAutoRotateEnabled(boolean enabled) {
                        return androidRuntime.setAutoRotateEnabled(enabled);
                    }

                    @Override public boolean isAutoRotateEnabled() {
                        return androidRuntime.isAutoRotateEnabled();
                    }

                    @Override public boolean canWriteSystemSettings() {
                        return androidRuntime.canWriteSystemSettings();
                    }

                    @Override public void applyAutoRotate(boolean enabled) {
                        MainActivity.this.applyAutoRotate(enabled);
                    }

                    @Override public void requestWriteSystemSettingsAccess() {
                        MainActivity.this.requestWriteSystemSettingsAccess();
                    }

                    @Override public boolean setWifiEnabled(boolean enabled) {
                        return androidRuntime.setWifiEnabled(enabled);
                    }

                    @Override public boolean isWifiEnabled() {
                        return androidRuntime.isWifiEnabled();
                    }

                    @Override public void openWifiSettings() {
                        Intent intent = new Intent(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                                ? Settings.Panel.ACTION_WIFI
                                : Settings.ACTION_WIFI_SETTINGS);
                        startActivity(intent);
                    }

                    @Override public void logout() { viewModel.logout(); }
                    @Override public void recreateActivity() { recreate(); }
                    @Override public void navigate(MainScreen screen) {
                        navigation.navigate(screen);
                    }
                    @Override public void applyHardwareButtonLayout(
                            HardwareButtonLayout layout) {
                        updateHardwareButtonLayout(layout);
                    }
                    @Override public void showNotice(int messageResource) {
                        FloatingNotice.show(MainActivity.this, messageResource);
                    }
                    @Override public void showNotice(String message) {
                        FloatingNotice.show(MainActivity.this, message);
                    }
                    @Override public boolean isDeviceOwner() {
                        return androidRuntime != null && androidRuntime.isDeviceOwner();
                    }
                    @Override public SettingsScreenCoordinator.RemoveDeviceOwnerResult
                            removeDeviceOwner() {
                        if (androidRuntime == null || !androidRuntime.isDeviceOwner()) {
                            return SettingsScreenCoordinator.RemoveDeviceOwnerResult.UNAVAILABLE;
                        }
                        try {
                            if (!androidRuntime.removeDeviceOwner()) {
                                return SettingsScreenCoordinator.RemoveDeviceOwnerResult.UNAVAILABLE;
                            }
                            logger.info(LogCategory.PROVISIONING, "unspecified", "Removed device-owner management from developer settings.");
                            return SettingsScreenCoordinator.RemoveDeviceOwnerResult.REMOVED;
                        } catch (RuntimeException error) {
                            logger.error(LogCategory.POLICY, "unspecified", null, "KIOSK_TRACE remove-device-owner failed", error);
                            return SettingsScreenCoordinator.RemoveDeviceOwnerResult.FAILED;
                        }
                    }
                    @Override public void runOnUiThread(Runnable action) {
                        MainActivity.this.runOnUiThread(action);
                    }
                    @Override public boolean isFinishingOrDestroyed() {
                        return isFinishing() || isDestroyed();
                    }
                });
        activityChrome.setFullScreenDisplayEnabled(
                settingsCoordinator.isFullScreenDisplayEnabled());
        mainUiScope = new MainUiScope(navigation,
                () -> {
                    androidRuntime.resetDatabase();
                    android.os.Process.killProcess(android.os.Process.myPid());
                },
                entry -> composition.createOpenMediaUseCase(MainActivity.this).execute(entry),
                () -> menuModel.visibleTiles(MainActivity.this::isMenuTileVisible),
                settingsCoordinator, new MainUiScope.CameraActions() {
                    @Override public void onCameraViewAttached(CameraScreenView view) {
                        attachCameraView(view);
                    }

                    @Override public void onCameraViewDetached(CameraScreenView view) {
                        if (cameraScreen == view) cameraScreen = null;
                    }

                    @Override public void onCameraSwitchRequested() {
                        boolean accepted = captureRuntime != null && captureRuntime.switchCamera();
                        if (captureRuntime != null) {
                            logger.info(LogCategory.CAMERA, "unspecified", "CAMERA_TRACE preview-switch-click accepted=" + accepted
                                    + " " + captureRuntime.cameraSwitchState());
                        }
                        requestCameraSwitchActionUpdate();
                    }
                });

        setContentView(activityBinding.getRoot());
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
        prepareCameraProfilesIfCameraGranted();
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
                logger.warn(LogCategory.STORAGE, "unspecified", null, "STORAGE_TRACE legacy-storage-access-required", null);
                requestLegacyStoragePermissions();
            }
            return false;
        }
        if (!allFilesAccessRequestInFlight && !allFilesAccessDialogShowing) {
            logger.warn(LogCategory.STORAGE, "unspecified", null, "STORAGE_TRACE all-files-access-required", null);
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
                    Uri.parse(PACKAGE_URI_PREFIX + getPackageName())));
        } catch (RuntimeException error) {
            allFilesAccessRequestInFlight = false;
            logger.error(LogCategory.STORAGE, "unspecified", null, "STORAGE_TRACE all-files-settings-failed", error);
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
            logger.info(LogCategory.SECURITY, "unspecified", "PERMISSION_TRACE runtime-request missingCorePermissions="
                    + missingCorePermissions.length + " missingCapturePermissions="
                    + androidRuntime.missingCapturePermissions().length);
            try {
                permissionLauncher.launch(missingCorePermissions);
            } catch (RuntimeException error) {
                capturePermissionRequestInFlight = false;
                logger.error(LogCategory.SECURITY, "unspecified", null, "PERMISSION_TRACE runtime-request-failed", error);
                if (androidRuntime.capturePermissionsGranted()) {
                    completeCapturePermissionGate("runtime-request-failed");
                } else {
                    showCapturePermissionRequired();
                }
            }
        }));
    }

    private void prepareCameraProfilesIfCameraGranted() {
        if (!androidRuntime.cameraPermissionGranted())
            return;
        composition.prepareCameraProfilesIfPermitted();
    }

    private void completeCapturePermissionGate(String source) {
        if (!androidRuntime.capturePermissionsGranted()) {
            showCapturePermissionRequired();
            return;
        }
        logger.info(LogCategory.SECURITY, "unspecified", "PERMISSION_TRACE capture-ready source=" + source);
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
        logger.info(LogCategory.SECURITY, "unspecified", "PERMISSION_TRACE open-app-settings");
        try {
            capturePermissionSettingsLauncher.launch(new Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse(PACKAGE_URI_PREFIX + getPackageName())));
        } catch (RuntimeException error) {
            capturePermissionSettingsRequestInFlight = false;
            logger.error(LogCategory.SECURITY, "unspecified", null, "PERMISSION_TRACE app-settings-failed", error);
            showCapturePermissionRequired();
        }
    }

    private void showCapturePermissionRequired() {
        if (capturePermissionDialogShowing || isFinishing() || isDestroyed())
            return;
        logger.warn(LogCategory.SECURITY, "unspecified", null, "PERMISSION_TRACE blocker-shown missingCapturePermissions="
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
                    Uri.parse(PACKAGE_URI_PREFIX + getPackageName())));
        } catch (RuntimeException error) {
            legacyStorageSettingsRequestInFlight = false;
            logger.error(LogCategory.STORAGE, "unspecified", null, "STORAGE_TRACE legacy-storage-settings-failed", error);
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
                    logger.info(LogCategory.IDENTITY, "unspecified", "SERIAL_TRACE precheck-complete restored=" + restored);
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
                        logger.error(LogCategory.IDENTITY, "unspecified", null, "Device identity mirror synchronization failed; continuing with the configured serial",
                                error);
                    } else {
                        logger.error(LogCategory.IDENTITY, "unspecified", null, "SERIAL_TRACE restore-failed", error);
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
            logger.info(LogCategory.IDENTITY, "unspecified", "SERIAL_TRACE restore-success");
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
        logger.info(LogCategory.IDENTITY, "unspecified", "SERIAL_TRACE dialog-check composition=" + identity(composition)
                + " savedPresent=" + savedValid
                + " savedHash=" + shortId(saved));
        if (savedValid)
            return;
        logger.warn(LogCategory.IDENTITY, "QA-CSON-002: identity_input_required", null,
                "SD identity backup was unavailable; requesting user serial identity input.", null);
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
                    // Validation is handled from onTextChanged.
                }

                @Override
                public void onTextChanged(CharSequence text, int start, int before, int count) {
                    boolean valid = deviceSerialNumbers.isValid(text.toString());
                    logger.info(LogCategory.IDENTITY, "unspecified", "SERIAL_TRACE input-changed length=" + text.length()
                            + " valid=" + valid);
                    save.setEnabled(valid);
                    input.setError(null);
                }

                @Override
                public void afterTextChanged(Editable text) {
                    // No post-edit work is needed after onTextChanged.
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
        logger.info(LogCategory.IDENTITY, "unspecified", "SERIAL_TRACE save-request length=" + serial.length()
                + " valueHash=" + shortId(serial));
        identityIoExecutor.execute(() -> {
            try {
                deviceSerialNumbers.save(serial);
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed())
                        return;
                    logger.info(LogCategory.IDENTITY, "QA-CSON-002: identity_input_saved",
                            "User serial identity input was saved successfully.");
                    dialog.dismiss();
                    applyDeviceIdentityUpdate();
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed())
                        return;
                    logger.error(LogCategory.IDENTITY, "QA-CSON-002: identity_input_save_failed", null,
                            "User serial identity input could not be saved.", error);
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
        refreshDisplayRotationIfNeeded(currentActivityDisplay());
    }

    @Override
    protected void onResume() {
        super.onResume();
        String currentScreen = latestState == null ? UNKNOWN_VALUE
                : MainNavigationGraph.logName(latestState.getScreen());
        String captureMode = latestState == null ? UNKNOWN_VALUE
                : String.valueOf(latestState.getCapture().getMode())
                        .toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
        logger.info(LogCategory.APP, "unspecified", "MainActivity resumed. Screen: " + currentScreen
                + ". Capture is " + captureMode
                + ". Camera runtime is "
                + (captureRuntime == null ? "unavailable" : "available")
                + ". Window is " + (hasWindowFocus() ? "focused" : "not focused")
                + ". Configuration change is "
                + (isChangingConfigurations() ? "in progress" : "not in progress") + ".");
        writeSettingsRequestInFlight = false;
        settingsCoordinator.onResume();
        syncRecordingRotationLock();
        activityChrome.applyFullScreenDisplay();
        if (androidRuntime != null) {
            androidRuntime.enterLockTaskIfAllowed(this, () -> {
                activityChrome.applyFullScreenDisplay();
                activityChrome.updateManagedTopBar();
            });
        }
        if (viewModel != null)
            viewModel.refreshDeviceStatus();
        if (captureRuntime != null && androidRuntime.capturePermissionsGranted()) {
            captureRuntime.refreshCameraState();
            refreshDisplayRotationIfNeeded(currentActivityDisplay());
        }
        if (capturePermissionPolicyApplied && !capturePermissionRequestInFlight
                && !capturePermissionSettingsRequestInFlight
                && !androidRuntime.capturePermissionsGranted()) {
            logger.warn(LogCategory.SECURITY, "unspecified", null, "PERMISSION_TRACE onResume captureGranted=false", null);
            showCapturePermissionRequired();
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
        logger.info(LogCategory.APP, "unspecified", "LIFECYCLE_TRACE onStop activity=" + identity(this)
                + LOG_SCREEN_SUFFIX + (latestState == null ? UNKNOWN_VALUE : latestState.getScreen())
                + LOG_CAPTURE_MODE_SUFFIX + (latestState == null
                        ? UNKNOWN_VALUE
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

    private Display currentActivityDisplay() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
            return getDisplay();
        WindowManager windowManager = getSystemService(WindowManager.class);
        return windowManager == null ? null : windowManager.getDefaultDisplay();
    }

    private void registerDisplayRotationListener() {
        if (displayManager != null)
            return;
        displayManager = getSystemService(DisplayManager.class);
        if (displayManager == null) {
            logger.warn(LogCategory.APP, "unspecified", null, "LIFECYCLE_TRACE displayListener registration_failed", null);
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
            logger.info(LogCategory.APP, "unspecified", "LIFECYCLE_TRACE windowFocus=" + hasFocus
                    + " activity=" + identity(this)
                    + LOG_SCREEN_SUFFIX + (latestState == null ? UNKNOWN_VALUE : latestState.getScreen())
                    + LOG_CAPTURE_MODE_SUFFIX + (latestState == null
                            ? UNKNOWN_VALUE
                            : latestState.getCapture().getMode()));
        }
        if (!hasFocus && hardwareButtons != null)
            hardwareButtons.clearFocusTransientState();
        if (hasFocus && activityChrome != null)
            activityChrome.applyFullScreenDisplay();
        if (hasFocus && composition != null && androidRuntime != null
                && androidRuntime.capturePermissionsGranted()) {
            composition.bindCameraIfPermitted();
        }
    }

    private void refreshWifiSetting() {
        if (androidRuntime == null || settingsCoordinator == null) return;
        settingsCoordinator.onWifiStateChanged(androidRuntime.isWifiEnabled());
    }

    @Override
    protected void onPause() {
        logger.info(LogCategory.APP, "unspecified", "LIFECYCLE_TRACE onPause activity=" + identity(this)
                + LOG_SCREEN_SUFFIX + (latestState == null ? UNKNOWN_VALUE : latestState.getScreen())
                + LOG_CAPTURE_MODE_SUFFIX + (latestState == null
                        ? UNKNOWN_VALUE
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

    @Override
    protected void onPostResume() {
        super.onPostResume();
        if (navigation != null) navigation.reconcileAfterStateSave();
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

    static boolean shouldAcceptFirmwareHardwareInput(
            boolean activityResumed, boolean screenInteractive, boolean cameraRecording) {
        return activityResumed || !screenInteractive || cameraRecording;
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
        if (committedScreen() == null
                && state.getScreen() == MainScreen.LOGIN
                && state.isAuthenticationBusy())
            return;
        MainScreen screen = state.getScreen();
        if (!navigation.onRouteChanged(screen)) return;
        settingsCoordinator.onCaptureStateChanged(
                state.getCapture().isVideoRecording());
        requestCameraSwitchActionUpdate();
        updateStatus(state);
        refreshGpsPermissionRetry();
        updateSavingNotice(previousState, state);
        showSavedNotice(previousState, state);
        showCaptureFailureNotice();
        requestIdleCameraReleaseIfScreenOff();
    }

    private void onRouteAccepted(MainScreen previous, MainScreen current) {
        if (previous == MainScreen.CAMERA && current != MainScreen.CAMERA) {
            FloatingNotice.hideLowPriorityPersistent();
            storageWarningVisible = null;
        }
        settingsCoordinator.onScreenEntered(current);
        ensureCaptureRuntime();
        if (current == MainScreen.CAMERA) prepareCameraRoute();
    }

    private void onDestinationReady(MainScreen previous, MainScreen screen) {
        if (navigation != null) navigation.onDestinationReady(previous, screen);
        if (latestState != null) recordingStatusRenderer.updateFloatingRecordingStatus(latestState);
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
        boolean storageStopped = isStorageCaptureStoppedMessage(message);
        if (message == null || (!message.startsWith("Saved ") && !storageStopped)
                || message.equals(previousState.getMessage()))
            return;
        long savedNoticeAgeMillis =
                System.currentTimeMillis() - viewModel.lastSavedNoticeAtMillis();
        if (savedNoticeAgeMillis < 0L
                || savedNoticeAgeMillis >= SAVED_NOTICE_MAX_AGE_MS)
            return;

        if (storageStopped) {
            FloatingNotice.show(this, R.string.storage_capture_stopped);
        } else {
            FloatingNotice.show(this, getString(R.string.media_saved, message.substring(6)));
        }
    }

    private void showCaptureFailureNotice() {
        CaptureFailureNoticePolicy.Notice notice = CaptureFailureNoticePolicy.notice(
                viewModel.takeCaptureFailureNotice(),
                getString(R.string.sd_card_unavailable),
                getString(R.string.low_storage_recording_blocked, "%1$s")).orElse(null);
        if (notice == null)
            return;
        switch (notice.kind()) {
            case CAPTURE_FAILED -> FloatingNotice.show(
                    this, notice.detail(), FloatingNotice.ERROR_TEXT_COLOR);
            case FINALIZATION_FAILED -> FloatingNotice.show(this, R.string.media_save_failed);
            case LOW_STORAGE_RECORDING_BLOCKED -> FloatingNotice.show(this, notice.detail(),
                    FloatingNotice.ERROR_TEXT_COLOR);
            case SD_CARD_UNAVAILABLE -> FloatingNotice.show(this, R.string.sd_card_unavailable);
            case STORAGE_UNAVAILABLE -> FloatingNotice.show(this,
                    R.string.capture_storage_unavailable);
        }
    }

    private void prepareCameraRoute() {
        captureRuntime.bindCameraIfPermitted();
        if (locationTrackingCoordinator != null)
            locationTrackingCoordinator.requestCurrentLocation();
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
        cameraScreen.setCameraSwitch(multipleCameras, enabled);
    }

    private void requestStartupLocationPermissionAfterCameraReady() {
        if (startupGpsPermissionHandled || locationPermissionRequestInFlight
                || !androidRuntime.capturePermissionsGranted() || cameraScreen == null)
            return;
        cameraScreen.post(() -> {
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
        cameraScreen.setIdentity(CameraIdentityPresentation.cameraLabel(
                savedSerial, deviceSerialNumbers.isConfigured(savedSerial)));
    }

    private void attachCameraView(CameraScreenView view) {
        cameraScreen = view;
        updateCameraIdentity();
        updateGpsStatusLine();
        updateCameraSwitchAction();
        attachCameraPreview();
        if (captureRuntime != null) captureRuntime.bindCameraIfPermitted();
        recordingStatusRenderer.updateCameraClock();
        requestStartupLocationPermissionAfterCameraReady();
    }

    private void attachCameraPreview() {
        if (cameraScreen == null || captureRuntime == null) return;
        if (captureRuntime.cameraPreview().getParent() == null) {
            cameraScreen.previewContainer().addView(captureRuntime.cameraPreview(), new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }
        /*
         * Keep shared preview attached while other screens cover it. Reparenting
         * destroys its surface and causes black-screen startup delay on return.
         */
        if (captureRuntime.cameraPreview().getParent() instanceof ViewGroup
                && captureRuntime.cameraPreview().getParent() != cameraScreen.previewContainer()) {
            ((ViewGroup) captureRuntime.cameraPreview().getParent()).removeView(captureRuntime.cameraPreview());
            cameraScreen.previewContainer().addView(captureRuntime.cameraPreview(), new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
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

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (!isHomeLaunch(intent)) return;
        setIntent(intent);
        if (viewModel != null) {
            MainUiState current = viewModel.state().getValue();
            logger.info(LogCategory.APP, "unspecified", "Home button requested camera preview from "
                    + (current == null ? "unknown screen"
                            : MainNavigationGraph.logName(current.getScreen())) + ".");
            navigation.navigate(MainScreen.CAMERA);
        }
    }

    private void updateHardwareButtonLayout(HardwareButtonLayout layout) {
        composition.updateHardwareButtonLayout(layout);
        if (hardwareButtons != null) {
            hardwareButtons.updateLayout(layout);
            refreshFirmwareHardwareButtonReceiver();
        }
        logger.info(LogCategory.CONFIG, "unspecified", "Changed hardware button bindings. Active bindings: "
                + layout.bindings().size() + ".");
    }

    private void syncRecordingRotationLock() {
        boolean locked = composition != null && composition.cameraRecordingActiveForUi();
        if (recordingRotationLocked == locked)
            return;
        recordingRotationLocked = locked;
        refreshedDisplayRotationRuntime = null;
        refreshedDisplayRotation = -1;
        applyAutoRotate(androidRuntime.isAutoRotateEnabled());
        if (!locked) cameraClock.post(
                () -> refreshDisplayRotationIfNeeded(currentActivityDisplay()));
    }

    private void applyAutoRotate(boolean enabled) {
        boolean locked = recordingRotationLocked
                || composition != null && composition.cameraRecordingActiveForUi();
        int requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LOCKED;
        if (!locked && enabled)
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_FULL_USER;
        setRequestedOrientation(requestedOrientation);
    }

    private void syncAutoRotateFromDevice() {
        if (androidRuntime == null || settingsCoordinator == null) return;
        settingsCoordinator.onAutoRotateStateChanged(androidRuntime.isAutoRotateEnabled());
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
                Uri.parse(PACKAGE_URI_PREFIX + getPackageName()));
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
            logger.error(LogCategory.CONFIG, "unspecified", null, "Unable to open modify system settings", failure);
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
            logger.info(LogCategory.CAPABILITY, "unspecified", "Completed camera capability check successfully.");
            showCameraCapabilityResult(update);
            return false;
        }
        logger.warn(LogCategory.CAPABILITY, "unspecified", null, "Camera capability check failed. Reason: " + update.detail() + ".", null);
        showCameraCapabilityResult(update);
        return false;
    }

    private void refreshCapabilitySensitiveSettings() {
        settingsCoordinator.onCameraCapabilityChanged();
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

    private boolean isMenuTileVisible(MainScreen screen, FeatureGate gate) {
        return gate == null || developerFeatureToggles.isEffectivelyEnabled(gate);
    }

    private void updateStatus(MainUiState state) {
        recordingStatusRenderer.updateFloatingRecordingStatus(state);
        if (cameraScreen != null) {
            cameraScreen.setRecordingBadgeAlpha(RECORDING_BADGE_ACTIVE_ALPHA);
            updateGpsStatusLine();
            recordingStatusRenderer.updateCameraClock();
        }
    }

    private static boolean isStorageCaptureStoppedMessage(String message) {
        return message != null
                && message.startsWith(MainViewModel.STORAGE_STOPPED_MESSAGE_PREFIX);
    }

    private void updateStorageWarning() {
        if (captureRuntime == null || cameraScreen == null
                || latestState == null || latestState.getScreen() != MainScreen.CAMERA) {
            FloatingNotice.hideLowPriorityPersistent();
            storageWarningVisible = null;
            nextStorageWarningRefreshAtMillis = 0L;
            return;
        }
        long nowMillis = SystemClock.uptimeMillis();
        if (nowMillis < nextStorageWarningRefreshAtMillis) return;
        nextStorageWarningRefreshAtMillis = nowMillis + STORAGE_WARNING_REFRESH_INTERVAL_MS;
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
            var notice = StorageWarningNoticePolicy.notice(warning,
                    getString(R.string.low_storage_preview_warning,
                            StorageSizeFormatter.gibibytes(free)));
            if (notice.isPresent()) {
                StorageWarningNoticePolicy.Notice value = notice.orElseThrow();
                FloatingNotice.showLowPriorityPersistent(this,
                        value.message(), value.severity() == StorageWarningNoticePolicy.Severity.ERROR
                                ? FloatingNotice.ERROR_TEXT_COLOR : FloatingNotice.WARNING_TEXT_COLOR);
            } else if (storageWarningVisible == null || storageWarningVisible) {
                FloatingNotice.hideLowPriorityPersistent();
            }
            storageWarningVisible = warning.isVisible();
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
            case LOCATION_UNAVAILABLE, NO_PROVIDER:
                return getString(R.string.gps_unavailable);
            case ERROR:
                return getString(R.string.gps_provider_error);
            case WAITING_FOR_LOCATION_INFO, AVAILABLE:
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
        cameraScreen.setGps(visible, visible ? gpsCoordinatesText() : "");
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
                    Uri.parse(PACKAGE_URI_PREFIX + getPackageName()));
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
        if (committedScreen() == MainScreen.DEVELOPER_BUTTON_BINDINGS
                && settingsCoordinator.onKeyDown(keyCode))
            return true;
        long deliveredAtMs = SystemClock.uptimeMillis();
        boolean handled = hardwareButtons != null
                && hardwareButtons.onKeyDown(keyCode, repeatCount, eventTimeMs);
        if (handled) restartIdleCameraReleaseDelayAfterHardwareCommandIfScreenOff();
        if (handled && repeatCount == 0 && logger != null) {
            logger.info(LogCategory.APP, "unspecified", "Accepted hardware button press. Key code: " + keyCode
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
        if (committedScreen() == MainScreen.DEVELOPER_BUTTON_BINDINGS
                && settingsCoordinator.onKeyUp(keyCode)) {
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
            logger.info(LogCategory.APP, "unspecified", "Accepted firmware hardware button press. Broadcast action: "
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
        logger.info(LogCategory.APP, "unspecified", "MainActivity destroyed");
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
        if (navigation != null) navigation.close();
        if (settingsCoordinator != null) settingsCoordinator.close();
        resourceMonitorController.close();
        super.onDestroy();
    }

    private static String identity(Object value) {
        return value == null ? "null"
                : value.getClass().getSimpleName() + "@"
                        + Integer.toHexString(System.identityHashCode(value));
    }

    @Override
    public MainUiScope mainUiScope() {
        if (mainUiScope == null) {
            throw new IllegalStateException("Main UI scope is not ready");
        }
        return mainUiScope;
    }

    private MainScreen committedScreen() {
        return navigation == null ? null : navigation.committedRoute();
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
        attachCameraPreview();
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

}
