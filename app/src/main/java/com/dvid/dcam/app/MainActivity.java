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
import android.content.SharedPreferences;
import android.database.ContentObserver;
import android.graphics.Color;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
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
import android.widget.TextView;
import androidx.activity.ComponentActivity;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;
import com.dvid.dcam.R;
import com.dvid.dcam.app.feature.DeveloperFeatureToggles;
import com.dvid.dcam.app.shell.ActivityChromeController;
import com.dvid.dcam.app.shell.FloatingNotice;
import com.dvid.dcam.app.shell.MediaBrowserRenderer;
import com.dvid.dcam.app.shell.RecordingStatusRenderer;
import com.dvid.dcam.app.navigation.MainScreen;
import com.dvid.dcam.app.presentation.MainMenuModel;
import com.dvid.dcam.app.presentation.MainMenuTile;
import com.dvid.dcam.app.presentation.GpsPermissionRetryScheduler;
import com.dvid.dcam.app.presentation.MainUiState;
import com.dvid.dcam.app.presentation.MainViewModel;
import com.dvid.dcam.app.presentation.MainViewModelFactory;
import com.dvid.dcam.app.presentation.LocationTrackingCoordinator;
import com.dvid.dcam.core.config.domain.DcamConfig;
import com.dvid.dcam.core.feature.domain.FeatureGate;
import com.dvid.dcam.databinding.ActivityMainBinding;
import com.dvid.dcam.databinding.ScreenCameraBinding;
import com.dvid.dcam.databinding.ScreenDeveloperUsersBinding;
import com.dvid.dcam.databinding.ScreenFileExplorerBinding;
import com.dvid.dcam.databinding.ScreenLoginBinding;
import com.dvid.dcam.databinding.ScreenMenuBinding;
import com.dvid.dcam.databinding.ScreenSettingsDetailBinding;
import com.dvid.dcam.feature.auth.domain.UserProvisioningRequest;
import com.dvid.dcam.feature.auth.domain.UserSource;
import com.dvid.dcam.feature.capture.domain.RecordingMode;
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
import com.dvid.dcam.feature.storage.domain.StorageWarningStatus;
import com.dvid.dcam.feature.settings.presentation.SettingsUiState;
import com.dvid.dcam.feature.settings.presentation.StorageOptionUiState;
import com.dvid.dcam.feature.settings.presentation.DeveloperButtonBindingsScreen;
import com.dvid.dcam.feature.settings.presentation.DescribedRadioOptionUiState;
import com.dvid.dcam.feature.settings.presentation.SettingId;
import com.dvid.dcam.feature.settings.presentation.SettingItem;
import com.dvid.dcam.feature.settings.presentation.SettingsControlRenderer;
import com.dvid.dcam.feature.settings.presentation.SettingsScreenModel;
import com.dvid.dcam.feature.settings.presentation.SettingsSection;
import com.dvid.dcam.platform.config.AndroidLanguagePreferenceStoreImpl;
import com.dvid.dcam.platform.camera.CameraXCameraGatewayImpl;
import com.dvid.dcam.platform.config.DeviceSerialNumberStore;
import com.dvid.dcam.platform.device.DcamKioskController;
import com.dvid.dcam.platform.device.AndroidDeviceSettings;
import com.dvid.dcam.platform.device.AndroidDeviceCapabilities;
import com.dvid.dcam.platform.input.HardwareButtonLayout;
import com.dvid.dcam.platform.input.HardwareButtonRouter;
import com.dvid.dcam.platform.logging.DcamLogger;
import com.dvid.dcam.platform.database.AppDatabase;
import com.dvid.dcam.platform.permission.DcamPermissions;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Android entry point and ViewBinding presentation shell. */
public final class MainActivity extends ComponentActivity {
    private static final int DEV_MODE_UNLOCK_TAPS = 7;
    private static final long DEV_MODE_UNLOCK_WINDOW_MS = 5_000L;
    private static final long GPS_PERMISSION_RETRY_DELAY_MS = 5 * 60_000L;
    private static final String STATE_DEFAULT_HOME_REQUESTED = "default_home_requested";
    private static final String STATE_STARTUP_GPS_PERMISSION_HANDLED = "startup_gps_permission_handled";
    private static final float RECORDING_BADGE_ACTIVE_ALPHA = 1f;
    private final Handler cameraClock = new Handler(Looper.getMainLooper());
    private final Runnable cameraClockTick = new Runnable() {
        @Override public void run() {
            recordingStatusRenderer.updateCameraClock();
            activityChrome.updateManagedTopBar();
            updateStorageWarning();
            cameraClock.postDelayed(this, 1_000L);
        }
    };
    private FrameLayout root;
    private ActivityMainBinding activityBinding;
    private AppComposition composition;
    private AppComposition.CaptureRuntime captureRuntime;
    private MainViewModel viewModel;
    private HardwareButtonRouter hardwareButtons;
    private OpenMediaUseCase openMedia;
    private LanguageSettingsUseCase languageSettings;
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
    private AndroidDeviceCapabilities deviceCapabilities;
    private MainMenuModel menuModel;
    private ActivityResultLauncher<String[]> permissionLauncher;
    private ActivityResultLauncher<String[]> locationPermissionLauncher;
    private ActivityResultLauncher<Intent> defaultHomeLauncher;
    private boolean locationPermissionRequestInFlight;
    private boolean locationPermissionSettingsFallbackPending;
    private GpsPermissionRetryScheduler gpsPermissionRetryScheduler;
    private boolean startupGpsPermissionHandled;
    private boolean defaultHomeRequestStarted;
    private Boolean pendingLocationEnabled;
    private DcamKioskController kioskController;
    private ActivityChromeController activityChrome;
    private RecordingStatusRenderer recordingStatusRenderer;
    private MediaBrowserRenderer mediaBrowserRenderer;
    private AndroidDeviceSettings deviceSettings;
    private ContentObserver autoRotateObserver;
    private boolean writeSettingsRequestInFlight;
    private Boolean pendingAutoRotateValue;
    private AlertDialog writeSettingsDialog;
    private MainUiState latestState;
    private MainScreen renderedScreen;
    private ScreenCameraBinding cameraScreen;
    private ScreenLoginBinding loginScreen;
    private ScreenDeveloperUsersBinding developerUsersScreen;
    private ScreenFileExplorerBinding fileExplorerScreen;
    private ScreenMenuBinding menuScreen;
    private LinearLayout renderedSettingsList;
    private AppLanguage[] renderedLanguages = new AppLanguage[0];
    private int devModeTapCount;
    private long devModeTapWindowStartedAtMs;
    private boolean audioRecording;
    private Boolean storageWarningVisible;
    private Long audioStartedAtMillis;
    private Runnable sosHoldAction;
    private final BroadcastReceiver wifiStateReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            int state = intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE, WifiManager.WIFI_STATE_UNKNOWN);
            if (state == WifiManager.WIFI_STATE_ENABLED || state == WifiManager.WIFI_STATE_DISABLED) {
                refreshWifiSetting();
            }
        }
    };
    private final BroadcastReceiver storageMountedReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (!Intent.ACTION_MEDIA_MOUNTED.equals(intent.getAction())
                    || latestState == null
                    || latestState.getScreen() != MainScreen.STORAGE_SETTINGS) return;
            renderedScreen = null;
            render(latestState);
        }
    };

    @Override protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(AndroidLanguagePreferenceStoreImpl.localizedContext(newBase));
    }

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        defaultHomeRequestStarted = savedInstanceState != null
                && savedInstanceState.getBoolean(STATE_DEFAULT_HOME_REQUESTED);
        startupGpsPermissionHandled = savedInstanceState != null
                && savedInstanceState.getBoolean(STATE_STARTUP_GPS_PERMISSION_HANDLED);
        composition = AppComposition.create(this);
        deviceCapabilities = new AndroidDeviceCapabilities(this);
        languageSettings = composition.languageSettingsUseCase();
        developerFeatureToggles = composition.developerFeatureToggles();
        developerButtonBindingsScreen = new DeveloperButtonBindingsScreen(
                this,
                composition.developerHardwareButtonSettings(),
                composition::applyDeveloperHardwareButtonLayout,
                composition::hasDeveloperHardwareButtonDefaults,
                composition::resetDeveloperHardwareButtonLayout,
                this::updateHardwareButtonLayout,
                message -> FloatingNotice.show(this, message));
        mediaEncryptionSettings = composition.mediaEncryptionSettingsUseCase();
        storageSettings = composition.storageSettingsUseCase();
        locationSettings = composition.locationSettingsUseCase();
        locationControl = composition.locationControlUseCase();
        locationTracking = composition.locationTrackingUseCase();
        locationTrackingCoordinator = new LocationTrackingCoordinator(
                locationSettings,
                locationControl,
                locationTracking,
                () -> developerFeatureToggles.isEffectivelyEnabled(FeatureGate.GPS),
                () -> DcamPermissions.locationGranted(this),
                () -> DcamPermissions.fineLocationGranted(this),
                this::onLocationTrackingStateChanged);
        deviceSettings = composition.deviceSettings();
        settingsRenderer = new SettingsControlRenderer(this);
        settingsUiState = new SettingsUiState(mediaEncryptionSettings.isMediaEncryptionEnabled(),
                storageSettings.supportedModes().indexOf(storageSettings.currentMode()),
                deviceSettings.isAutoRotateEnabled(),
                deviceSettings.isWifiEnabled());
        settingsUiState.setSupportedRecordResolutions(deviceCapabilities.supportedRecordQualities());
        settingsUiState.setRecordResolution(deviceCapabilities.selectedRecordQuality());
        applyAutoRotate(deviceSettings.isAutoRotateEnabled());
        menuModel = new MainMenuModel();
        kioskController = new DcamKioskController(this);
        kioskController.applyActiveKioskPolicy();

        getWindow().setNavigationBarColor(Color.BLACK);
        activityBinding = ActivityMainBinding.inflate(getLayoutInflater());
        activityChrome = new ActivityChromeController(this, activityBinding, kioskController::isDeviceOwner);
        recordingStatusRenderer = new RecordingStatusRenderer(
                activityBinding, () -> cameraScreen, () -> latestState,
                () -> audioRecording,
                () -> audioStartedAtMillis == null ? -1L : audioStartedAtMillis,
                () -> renderedScreen);
        root = activityBinding.contentRoot;
        setContentView(activityBinding.getRoot());
        activityChrome.updateManagedTopBar();
        activityChrome.bindSystemNavigationInset();
        activityChrome.hideSystemStatusBar();

        permissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                result -> {
                    if (DcamPermissions.cameraGranted(this)) {
                        CameraXCameraGatewayImpl.warmUp(this);
                        if (captureRuntime != null) captureRuntime.bindCameraIfPermitted();
                    }
                    refreshLocationTracking();
                    ensureConfigFileAccess();
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
                    if (latestState != null) render(latestState);
                    refreshGpsPermissionRetry();
                    if (openSettings) openLocationPermissionSettings();
                });
        gpsPermissionRetryScheduler = new GpsPermissionRetryScheduler(
                GPS_PERMISSION_RETRY_DELAY_MS,
                new GpsPermissionRetryScheduler.Scheduler() {
                    @Override public void schedule(Runnable action, long delayMillis) {
                        cameraClock.postDelayed(action, delayMillis);
                    }

                    @Override public void cancel(Runnable action) {
                        cameraClock.removeCallbacks(action);
                    }
                },
                this::shouldRetryGpsPermission,
                this::launchLocationPermissionRequest);
        defaultHomeLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> { });
        requestDefaultHomeIfNeeded();
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() { navigateBack(); }
        });

        if (!DcamPermissions.coreRuntimeGranted(this)) {
            permissionLauncher.launch(DcamPermissions.coreRuntime());
        } else {
            CameraXCameraGatewayImpl.warmUp(this);
        }
        openMedia = composition.createOpenMediaUseCase(this);
        DcamLogger.i("AUTH_TRACE auth-bootstrap-start composition=" + identity(composition)
                + " authEnabled="
                + developerFeatureToggles.isEffectivelyEnabled(FeatureGate.AUTHENTICATION));
        viewModel = new ViewModelProvider(
                this, new MainViewModelFactory(
                composition.config(), composition.initialDeviceStatus(),
                composition.refreshDeviceStatusUseCase(), composition.browseMediaUseCase(),
                composition.authenticateOperatorUseCase(),
                composition.operatorSessionUseCase(),
                composition.manageOperatorUsersUseCase(),
                () -> developerFeatureToggles.isEffectivelyEnabled(FeatureGate.AUTHENTICATION)))
                .get(MainViewModel.class);
        DcamLogger.i("AUTH_TRACE auth-bootstrap-viewmodel-created");
        DcamLogger.i("AUTH_TRACE activity-composition=" + identity(composition)
                + " sessionUseCase=" + identity(composition.operatorSessionUseCase())
                + " authEnabled=" + developerFeatureToggles.isEffectivelyEnabled(FeatureGate.AUTHENTICATION));
        viewModel.state().observe(this, state -> {
            DcamLogger.i("AUTH_TRACE vm-state screen=" + state.getScreen()
                    + " busy=" + state.isAuthenticationBusy()
                    + " vmSession=" + sessionSummary(state.getOperatorSession())
                    + " authoritySession=" + sessionSummary(composition.operatorSessionUseCase().current()));
            render(state);
        });
        if (DcamPermissions.coreRuntimeGranted(this)) ensureConfigFileAccess();
        ContextCompat.registerReceiver(this, wifiStateReceiver,
                new IntentFilter(WifiManager.WIFI_STATE_CHANGED_ACTION),
                ContextCompat.RECEIVER_NOT_EXPORTED);
        IntentFilter storageFilter = new IntentFilter(Intent.ACTION_MEDIA_MOUNTED);
        storageFilter.addDataScheme("file");
        ContextCompat.registerReceiver(this, storageMountedReceiver, storageFilter,
                ContextCompat.RECEIVER_NOT_EXPORTED);
    }

    private void ensureConfigFileAccess() { showSerialNumberDialogIfNeeded(); }
    private void resetCompositionAndRecreate() {
        AppComposition.reset();
        getViewModelStore().clear();
        recreate();
    }
    private void showSerialNumberDialogIfNeeded() {
        String saved = composition.deviceSerialNumberStore().load();
        DcamLogger.i("SERIAL_TRACE dialog-check composition=" + identity(composition)
                + " savedPresent=" + !saved.isBlank()
                + " savedHash=" + shortId(saved));
        if (!saved.isBlank()) return;
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        input.setHint(R.string.device_serial_hint);
        input.setContentDescription(getString(R.string.device_serial_hint));
        input.setSelectAllOnFocus(true);
        input.setFilters(new InputFilter[] { new InputFilter.LengthFilter(10) });
        FrameLayout inputContainer = new FrameLayout(this);
        inputContainer.setPadding(dp(24), 0, dp(24), 0);
        inputContainer.addView(input, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        String adbSerial = composition.defaultDeviceSerial();
        boolean hasDefault = adbSerial != null && !adbSerial.isBlank();
        DcamLogger.i("SERIAL_TRACE dialog-shown defaultPresent=" + hasDefault
                + " defaultHash=" + shortId(adbSerial));
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.device_serial_title)
                .setMessage(hasDefault
                        ? getString(R.string.device_serial_message_with_default, adbSerial)
                        : getString(R.string.device_serial_message))
                .setView(inputContainer)
                .setPositiveButton(R.string.device_serial_save, null)
                .setNeutralButton(R.string.device_serial_use_default, null)
                .setCancelable(false)
                .create();
        dialog.setOnShowListener(d -> {
            Button save = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            Button useDefault = dialog.getButton(AlertDialog.BUTTON_NEUTRAL);
            useDefault.setEnabled(hasDefault);
            save.setOnClickListener(v -> {
                String value = input.getText().toString().trim();
                try {
                    DcamLogger.i("SERIAL_TRACE save-request length=" + value.length()
                            + " valueHash=" + shortId(value));
                    composition.deviceSerialNumberStore().save(value);
                    DcamLogger.i("SERIAL_TRACE save-success valueHash=" + shortId(value));
                    dialog.dismiss();
                    resetCompositionAndRecreate();
                } catch (Exception error) {
                    DcamLogger.e("SERIAL_TRACE save-failed", error);
                    input.setError(getString(R.string.device_serial_invalid));
                }
            });
            useDefault.setOnClickListener(v -> {
                try {
                    DcamLogger.i("SERIAL_TRACE default-save-request valueHash=" + shortId(adbSerial));
                    composition.deviceSerialNumberStore().saveDefault(adbSerial);
                    DcamLogger.i("SERIAL_TRACE default-save-success valueHash=" + shortId(adbSerial));
                    dialog.dismiss();
                    resetCompositionAndRecreate();
                } catch (Exception error) {
                    DcamLogger.e("SERIAL_TRACE default-save-failed", error);
                    input.setError(getString(R.string.device_serial_default_unavailable));
                }
            });
            input.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence text, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence text, int start, int before, int count) {
                    boolean valid = DeviceSerialNumberStore.isValid(text.toString());
                    DcamLogger.i("SERIAL_TRACE input-changed length=" + text.length()
                            + " valid=" + valid);
                    save.setEnabled(valid);
                    input.setError(null);
                }
                @Override public void afterTextChanged(Editable text) {}
            });
            save.setEnabled(false);
        });
        dialog.show();
    }
    @Override protected void onResume() {
        super.onResume();
        writeSettingsRequestInFlight = false;
        if (pendingAutoRotateValue != null && deviceSettings.canWriteSystemSettings()) {
            boolean requested = pendingAutoRotateValue;
            if (deviceSettings.setAutoRotateEnabled(requested)) pendingAutoRotateValue = null;
        }
        syncAutoRotateFromDevice();
        activityChrome.hideSystemStatusBar();
        refreshWifiSetting();
        if (kioskController != null) {
            kioskController.applyActiveKioskPolicy();
            kioskController.enterLockTaskIfAllowed(this);
        }
        if (viewModel != null) viewModel.refreshDeviceStatus();
        if (captureRuntime != null) captureRuntime.refreshCameraState();
        if (pendingLocationEnabled != null) {
            pendingLocationEnabled = null;
            renderedScreen = null;
        }
        refreshLocationTracking();
        refreshGpsPermissionRetry();
        cameraClock.removeCallbacks(cameraClockTick);
        cameraClock.post(cameraClockTick);
    }

    @Override
    protected void onStart() {
        super.onStart();
        registerAutoRotateObserver();
    }

    @Override
    protected void onStop() {
        DcamLogger.i("MainActivity onStop captureMode="
                + (latestState == null ? "unknown" : latestState.getCapture().getMode()));
        stopLocationTracking();
        unregisterAutoRotateObserver();
        super.onStop();
    }

    private void registerAutoRotateObserver() {
        if (autoRotateObserver != null) return;
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
        if (autoRotateObserver == null) return;
        getContentResolver().unregisterContentObserver(autoRotateObserver);
        autoRotateObserver = null;
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (!hasFocus && hardwareButtons != null) hardwareButtons.clearFocusTransientState();
        if (hasFocus && activityChrome != null) activityChrome.hideSystemStatusBar();
    }

    private void refreshWifiSetting() {
        if (deviceSettings == null || settingsUiState == null) return;
        settingsUiState.updateBoolean(SettingId.WIFI_ENABLED, deviceSettings.isWifiEnabled());
        if (latestState != null && latestState.getScreen() == MainScreen.DEVICE_SETTINGS) {
            renderedScreen = null;
            render(latestState);
        }
    }

    @Override protected void onPause() {
        cameraClock.removeCallbacks(cameraClockTick);
        if (gpsPermissionRetryScheduler != null) gpsPermissionRetryScheduler.stop();
        stopLocationTracking();
        super.onPause();
    }



    private void render(MainUiState state) {
        MainUiState previousState = latestState;
        latestState = state;
        if (renderedScreen == null
                && state.getScreen() == MainScreen.LOGIN
                && state.isAuthenticationBusy()) return;
        MainScreen screen = safeScreen(state.getScreen());
        if (screen != state.getScreen()) {
            viewModel.show(screen);
            return;
        }
        if (renderedScreen != screen) {
            renderedScreen = screen;
            recordingStatusRenderer.updateFloatingRecordingStatus(state);
            if (screen == MainScreen.LOGIN) renderLogin();
            else if (screen == MainScreen.CAMERA) renderCamera();
            else if (screen == MainScreen.MENU) renderMenu();
            else if (screen == MainScreen.FILES) renderFileExplorer();
            else if (screen == MainScreen.DEVELOPER_USERS) renderDeveloperUsers();
            else renderSettingsDetail(screen);
        }
        updateStatus(state);
        refreshGpsPermissionRetry();
        updateSavingNotice(previousState, state);
        showSavedNotice(previousState, state);
        showLowStorageRecordingNotice(previousState, state);
    }

    private void updateSavingNotice(MainUiState previousState, MainUiState state) {
        if (previousState != null && previousState.getCapture().isSaving()
                && !state.getCapture().isSaving()) {
            FloatingNotice.hidePersistent();
        }
        if ((previousState == null || !previousState.getCapture().isSaving())
                && state.getCapture().isSaving()) {
            FloatingNotice.showPersistent(this, R.string.media_saving);
        }
    }

    private void showSavedNotice(MainUiState previousState, MainUiState state) {
        if (previousState == null) return;
        String message = state.getMessage();
        if (message == null || !message.startsWith("Saved ")
                || message.equals(previousState.getMessage())) return;
        FloatingNotice.show(this, getString(R.string.media_saved, message.substring(6)));
    }

    private void showLowStorageRecordingNotice(MainUiState previousState, MainUiState state) {
        String message = state.getMessage();
        String expected = "Storage failed: " + getString(R.string.low_storage_recording_blocked);
        if (!expected.equals(message)
                || previousState != null && message.equals(previousState.getMessage())) return;
        FloatingNotice.show(this, R.string.low_storage_recording_blocked);
    }

    private void renderCamera() {
        ensureCaptureRuntime();
        if (locationTrackingCoordinator != null) locationTrackingCoordinator.requestCurrentLocation();
        clearScreenBindings();
        removeNonCameraScreens();
        ensureCameraScreen();
        cameraScreen.getRoot().setAlpha(1f);
        recordingStatusRenderer.updateCameraClock();
        requestStartupLocationPermissionAfterCameraReady();
    }

    private void requestStartupLocationPermissionAfterCameraReady() {
        if (startupGpsPermissionHandled || locationPermissionRequestInFlight
                || !developerFeatureToggles.isEffectivelyEnabled(FeatureGate.GPS)
                || hasRequiredLocationPermission() || cameraScreen == null) return;
        startupGpsPermissionHandled = true;
        cameraScreen.getRoot().post(() -> {
            if (isFinishing() || isDestroyed() || hasRequiredLocationPermission()) return;
            launchLocationPermissionRequest();
        });
    }

    private void ensureCameraScreen() {
        if (cameraScreen == null) {
            cameraScreen = ScreenCameraBinding.inflate(getLayoutInflater(), root, false);
            root.addView(cameraScreen.getRoot(), 0);
        }
        DcamConfig config = viewModel.getConfig();
        DcamLogger.i("SERIAL_TRACE preview-config composition=" + identity(composition)
                + " vmConfig=" + identity(viewModel.getConfig())
                + " accountHash=" + shortId(config.getAccountUserId())
                + " operatorHash=" + shortId(config.getPoliceUserId()));
        cameraScreen.accountId.setText("CAM " + config.getAccountUserId());
        cameraScreen.operatorId.setText("USER " + config.getPoliceUserId());
        updateGpsStatusLine();
        bindFeatureAction(
                cameraScreen.captureAction,
                FeatureGate.IMAGE_CAPTURE,
                () -> {
                    if (locationTrackingCoordinator != null) locationTrackingCoordinator.requestCurrentLocation();
                    DcamLogger.i("AUTH_TRACE capture-button session="
                            + sessionSummary(composition.operatorSessionUseCase().current())
                            + " vmSession=" + sessionSummary(latestState == null
                            ? null : latestState.getOperatorSession()));
                    captureRuntime.photoCapture().takePhoto();
                });
        if (captureRuntime.cameraPreview().getParent() == null) {
            cameraScreen.previewContainer.addView(captureRuntime.cameraPreview(), new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }
        if (cameraScreen.getRoot().getParent() == null) {
            root.addView(cameraScreen.getRoot(), 0);
        }
        /*
         * Keep CameraXPreviewView attached while other screens cover it. Reparenting
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
        View.OnClickListener login = view ->
                viewModel.loginPassword(loginScreen.password.getText().toString());
        loginScreen.loginAction.setOnClickListener(login);
        loginScreen.resetDatabaseAction.setOnClickListener(view -> new AlertDialog.Builder(this)
                .setTitle(R.string.reset_login_database_title)
                .setMessage(R.string.reset_login_database_message)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.reset, (dialog, which) -> {
                    AppDatabase.reset(this);
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
        }
        root.addView(detail.getRoot());
    }

    private void renderSettingsControls(MainScreen screen, LinearLayout settingsList) {
        settingsRenderer.render(
                settingsList, settingsModel(screen),
                this::selectSetting, this::updateNumberSetting, this::updateBooleanSetting,
                this::performSettingAction);
    }

    private SettingsScreenModel settingsModel(MainScreen screen) {
        if (screen == MainScreen.DEVELOPER_SETTINGS) {
            return developerSettingsModel();
        }
        SettingsScreenModel model;
        if (screen == MainScreen.RECORD_SETTINGS) model = settingsUiState.recording(getString(R.string.record_settings), getString(R.string.record_resolution), getString(R.string.video_segment_length), getString(R.string.minute_short));
        else if (screen == MainScreen.STORAGE_SETTINGS) {
            model = settingsUiState.storageWithVolumes(storageOptions(), getString(R.string.storage_settings),
                    getString(R.string.default_storage), getString(R.string.low_storage_warning));
        }
        else if (screen == MainScreen.USER_SETTINGS) {
            settingsUiState.setVideoEncryptionEnabled(mediaEncryptionSettings.isMediaEncryptionEnabled());
            model = settingsUiState.security();
        }
        else if (screen == MainScreen.DEVICE_SETTINGS) {
            model = withLanguage(settingsUiState.device(
                    getString(R.string.auto_rotate), getString(R.string.wifi),
                    getString(R.string.connect_wifi)));
        }
        else if (screen == MainScreen.GPS_SETTINGS) model = locationSettingsModel();
        else model = settingsUiState.readOnly(visibleReadOnlySettings(screen));
        return filterUnavailableSettings(screen, model);
    }

    private List<StorageOptionUiState> storageOptions() {
        List<StorageVolumeStatus> volumes = storageSettings.storageVolumes();
        List<StorageOptionUiState> options = new ArrayList<>();
        for (MediaPartitionLocation mode : storageSettings.supportedModes()) {
            if (mode == MediaPartitionLocation.AUTO) {
                options.add(new StorageOptionUiState(getString(R.string.storage_auto),
                        getString(R.string.storage_auto_description), 0, true));
            } else {
                int label = mode == MediaPartitionLocation.INTERNAL
                        ? R.string.storage_internal : R.string.storage_external;
                options.add(storageOption(getString(label), storageVolume(volumes, mode)));
            }
        }
        return List.copyOf(options);
    }

    private void requestDefaultHomeIfNeeded() {
        if (defaultHomeRequestStarted || kioskController == null
                || kioskController.isDeviceOwner()
                || kioskController.isDefaultHome()) return;
        Intent intent;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            RoleManager roleManager = getSystemService(RoleManager.class);
            if (roleManager == null || !roleManager.isRoleAvailable(RoleManager.ROLE_HOME)
                    || roleManager.isRoleHeld(RoleManager.ROLE_HOME)) return;
            intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME);
        } else {
            intent = new Intent(Settings.ACTION_HOME_SETTINGS);
        }
        if (intent.resolveActivity(getPackageManager()) == null) return;
        defaultHomeRequestStarted = true;
        try {
            defaultHomeLauncher.launch(intent);
        } catch (ActivityNotFoundException error) {
            defaultHomeRequestStarted = false;
        }
    }

    @Override protected void onSaveInstanceState(Bundle outState) {
        outState.putBoolean(STATE_DEFAULT_HOME_REQUESTED, defaultHomeRequestStarted);
        outState.putBoolean(STATE_STARTUP_GPS_PERMISSION_HANDLED, startupGpsPermissionHandled);
        super.onSaveInstanceState(outState);
    }

    private SettingsScreenModel developerSettingsModel() {
        GpsSettings current = locationSettings.currentSettings();
        List<GpsMode> modes = locationSettings.supportedModes();
        List<DescribedRadioOptionUiState> options = new ArrayList<>();
        for (GpsMode mode : modes) options.add(gpsModeOption(mode));
        SettingItem provider = SettingItem.describedRadio(SettingId.GPS_POSITIONING_MODE,
                getString(R.string.gps_positioning_mode), options,
                Math.max(0, modes.indexOf(current.getMode())))
                .withEnabled(developerFeatureToggles.isEffectivelyEnabled(FeatureGate.GPS))
                .withIndentLevel(1);
        return developerFeatureToggles.developerSettings(SettingId.FEATURE_GPS, provider);
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
                                && hasRequiredLocationPermission(current.getMode()))
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

    private DescribedRadioOptionUiState gpsModeOption(GpsMode mode) {
        switch (mode) {
            case AUTOMATIC:
                return new DescribedRadioOptionUiState(getString(R.string.location_mode_automatic),
                        getString(R.string.location_mode_automatic_description));
            case SATELLITE:
                return new DescribedRadioOptionUiState(getString(R.string.location_mode_satellite),
                        getString(R.string.location_mode_satellite_description));
            case NETWORK:
                return new DescribedRadioOptionUiState(getString(R.string.location_mode_network),
                        getString(R.string.location_mode_network_description));
            default: throw new IllegalArgumentException("Unsupported location mode " + mode);
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
            if (volume.getMode() == mode) return volume;
        }
        return null;
    }
    private static String storageSize(long bytes) {
        double gib = bytes / (1024.0 * 1024.0 * 1024.0);
        return String.format(Locale.US, "%.1f GB", gib);
    }

    private String[] visibleReadOnlySettings(MainScreen screen) {
        String[] labels = getResources().getStringArray(settingsItems(screen));
        List<String> visible = new ArrayList<>();
        for (int i = 0; i < labels.length; i++) {
            if (isReadOnlySettingVisible(screen, i)) visible.add(labels[i]);
        }
        return visible.toArray(new String[0]);
    }

    private SettingsScreenModel filterUnavailableSettings(MainScreen screen, SettingsScreenModel model) {
        List<SettingsSection> sections = new ArrayList<>();
        for (SettingsSection section : model.getSections()) {
            List<SettingItem> visible = new ArrayList<>();
            for (SettingItem item : section.getItems()) {
                if (isSettingVisible(screen, item)) visible.add(item);
            }
            if (!visible.isEmpty()) sections.add(new SettingsSection(section.getTitle(), visible));
        }
        return new SettingsScreenModel(sections);
    }

    private boolean isSettingVisible(MainScreen screen, SettingItem item) {
        if ((item.getId() == SettingId.WIFI_ENABLED || item.getId() == SettingId.WIFI_CONNECT)
                && !deviceSettings.isDeviceOwner()) return false;
        for (FeatureGate gate : requiredGatesForSetting(screen, item)) {
            if (!developerFeatureToggles.isEffectivelyEnabled(gate)) return false;
        }
        return true;
    }

    private boolean isReadOnlySettingVisible(MainScreen screen, int index) {
        for (FeatureGate gate : requiredGatesForReadOnlySetting(screen, index)) {
            if (!developerFeatureToggles.isEffectivelyEnabled(gate)) return false;
        }
        return true;
    }

    private static FeatureGate[] requiredGatesForSetting(MainScreen screen, SettingItem item) {
        if (screen == MainScreen.DEVELOPER_SETTINGS
                && item.getId() == SettingId.GPS_POSITIONING_MODE) return noGates();
        if (item.getId() == SettingId.GPS_LOCATION_ENABLED
                || item.getId() == SettingId.GPS_POSITIONING_MODE
                || item.getId() == SettingId.GPS_UPDATE_DISTANCE_METERS
                || item.getId() == SettingId.GPS_REPORT_INTERVAL_SECONDS) {
            return gates(FeatureGate.GPS);
        }
        if (item.getId() == SettingId.LANGUAGE
                || item.getId() == SettingId.AUTO_ROTATE
                || item.getId() == SettingId.WIFI_ENABLED
                || item.getId() == SettingId.WIFI_CONNECT) return noGates();
        if (item.getId() == SettingId.ENCRYPT_VIDEO_FILES) {
            return gates(FeatureGate.MEDIA_ENCRYPTION);
        }
        switch (screen) {
            case RECORD_SETTINGS:
                return gates(FeatureGate.VIDEO_CAPTURE);
            case STORAGE_SETTINGS:
                return gates(FeatureGate.STORAGE_SETTINGS);
            case USER_SETTINGS:
                return gates(FeatureGate.SECURITY_SETTINGS);
            case DEVICE_SETTINGS:
                return gates(FeatureGate.DEVICE_SETTINGS);
            default:
                return noGates();
        }
    }

    private static FeatureGate[] requiredGatesForReadOnlySetting(MainScreen screen, int index) {
        switch (screen) {
            case CAMERA_SETTINGS:
                return gates(FeatureGate.IMAGE_CAPTURE);
            case VIDEO_STREAM_SETTINGS:
                return gates(FeatureGate.VIDEO_STREAMING);
            case AUDIO_SETTINGS:
                return gates(FeatureGate.AUDIO_CAPTURE);
            case GPS_SETTINGS:
                return gates(FeatureGate.GPS);
            case SERVER_SETTINGS:
                return gates(FeatureGate.CLOUD_SETTINGS);
            case TRANSFER_SETTINGS:
                return index < 4
                        ? gates(FeatureGate.VIDEO_STREAMING)
                        : gates(FeatureGate.TRANSFER);
            case ABOUT:
                return noGates();
            default:
                return noGates();
        }
    }

    private static FeatureGate[] gates(FeatureGate... gates) {
        return gates;
    }

    private static FeatureGate[] noGates() {
        return new FeatureGate[0];
    }

    private static boolean hasSettings(SettingsScreenModel model) {
        for (SettingsSection section : model.getSections()) {
            if (!section.getItems().isEmpty()) return true;
        }
        return false;
    }

    private boolean isMenuTileVisible(MainScreen screen, FeatureGate gate) {
        if (gate != null && !developerFeatureToggles.isEffectivelyEnabled(gate)) return false;
        if (screen == MainScreen.FILES) {
            return true;
        }
        if (isSettingsScreen(screen)) return hasSettings(settingsModel(screen));
        return true;
    }

    private static boolean isSettingsScreen(MainScreen screen) {
        switch (screen) {
            case RECORD_SETTINGS:
            case CAMERA_SETTINGS:
            case VIDEO_STREAM_SETTINGS:
            case AUDIO_SETTINGS:
            case STORAGE_SETTINGS:
            case GPS_SETTINGS:
            case DEVICE_SETTINGS:
            case USER_SETTINGS:
            case SERVER_SETTINGS:
            case TRANSFER_SETTINGS:
            case ABOUT:
                return true;
            default:
                return false;
        }
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
            if (renderedLanguages[i] == current) selectedIndex = i;
        }
        return new SettingsSection(getString(R.string.language_section_title),
                List.of(SettingItem.choice(SettingId.LANGUAGE,
                        getString(R.string.language_section_title), labels, selectedIndex)));
    }

    private void selectSetting(SettingId id, int selectedIndex) {
        if (id == SettingId.RECORD_RESOLUTION) {
            settingsUiState.select(id, selectedIndex);
            deviceCapabilities.selectRecordQuality(settingsUiState.recordResolution());
            composition.reloadRecordingQuality();
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
                    renderedScreen = null;
                    render(latestState);
                    FloatingNotice.show(this, R.string.external_storage_not_supported);
                    return;
                }
                storageSettings.changeMode(mode);
                recreate();
            }
            return;
        }
        if (id == SettingId.GPS_POSITIONING_MODE) {
            List<GpsMode> modes = locationSettings.supportedModes();
            if (selectedIndex >= 0 && selectedIndex < modes.size()) {
                GpsMode mode = modes.get(selectedIndex);
                locationSettings.changeMode(mode);
                if (!hasRequiredLocationPermission(mode)) launchLocationPermissionRequest();
                else restartLocationTracking();
            }
            return;
        }
        settingsUiState.select(id, selectedIndex);
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (Intent.ACTION_MAIN.equals(intent.getAction())
                && intent.hasCategory(Intent.CATEGORY_HOME)
                && viewModel != null) {
            viewModel.show(MainScreen.CAMERA);
        }
    }

    private void updateHardwareButtonLayout(HardwareButtonLayout layout) {
        if (hardwareButtons != null) hardwareButtons.updateLayout(layout);
    }

    private void updateNumberSetting(SettingId id, int value) {
        if (id == SettingId.GPS_UPDATE_DISTANCE_METERS) {
            locationSettings.changeUpdateDistanceMeters(value);
            restartLocationTracking();
            return;
        }
        if (id == SettingId.GPS_REPORT_INTERVAL_SECONDS) {
            locationSettings.changeReportIntervalSeconds(value);
            restartLocationTracking();
            return;
        }
        settingsUiState.updateNumber(id, value);
        if (id == SettingId.LOW_STORAGE_WARNING_GB) {
            storageSettings.changeWarningGb(value);
        }
    }

    private void updateBooleanSetting(SettingId id, boolean checked) {
        if (developerFeatureToggles.setEnabled(id, checked)) {
            if (id == SettingId.FEATURE_AUTHENTICATION) {
                viewModel.onAuthenticationSettingChanged();
                return;
            }
            if (id == SettingId.FEATURE_GPS) {
                if (checked) {
                    refreshLocationTracking();
                } else {
                    stopLocationTracking();
                }
            }
            if (renderedSettingsList != null
                    && latestState != null
                    && latestState.getScreen() == MainScreen.DEVELOPER_SETTINGS) {
                settingsRenderer.refreshEnabledStates(developerSettingsModel());
            }
            return;
        }
        if (id == SettingId.GPS_LOCATION_ENABLED) {
            handleLocationSwitchChanged(checked);
            return;
        }
        settingsUiState.updateBoolean(id, checked);
        if (id == SettingId.ENCRYPT_VIDEO_FILES && mediaEncryptionSettings != null) {
            mediaEncryptionSettings.setMediaEncryptionEnabled(checked);
        }
        if (id == SettingId.AUTO_ROTATE) {
            if (deviceSettings.setAutoRotateEnabled(checked)) {
                applyAutoRotate(checked);
            } else {
                pendingAutoRotateValue = checked;
                syncAutoRotateFromDevice();
                requestWriteSystemSettingsAccess();
            }
        }
        if (id == SettingId.WIFI_ENABLED && !deviceSettings.setWifiEnabled(checked)) {
            settingsUiState.updateBoolean(id, deviceSettings.isWifiEnabled());
            renderedScreen = null;
            render(latestState);
            FloatingNotice.show(this, getString(R.string.wifi_requires_device_owner));
        }
    }

    private void applyAutoRotate(boolean enabled) {
        setRequestedOrientation(enabled
                ? ActivityInfo.SCREEN_ORIENTATION_FULL_USER
                : ActivityInfo.SCREEN_ORIENTATION_LOCKED);
    }

    private void syncAutoRotateFromDevice() {
        if (deviceSettings == null || settingsUiState == null) return;
        boolean enabled = deviceSettings.isAutoRotateEnabled();
        settingsUiState.updateBoolean(SettingId.AUTO_ROTATE, enabled);
        applyAutoRotate(enabled);
        if (latestState != null) {
            renderedScreen = null;
            render(latestState);
        }
    }

    private void requestWriteSystemSettingsAccess() {
        if (deviceSettings == null || deviceSettings.canWriteSystemSettings()) {
            syncAutoRotateFromDevice();
            return;
        }
        if (writeSettingsRequestInFlight || isFinishing() || isDestroyed()
                || writeSettingsDialog != null && writeSettingsDialog.isShowing()) return;
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
                .setPositiveButton(android.R.string.ok, (dialog, which) ->
                        openWriteSystemSettings(intent))
                .setOnDismissListener(dialog -> writeSettingsDialog = null)
                .show();
    }

    private void openWriteSystemSettings(Intent intent) {
        if (writeSettingsRequestInFlight || isFinishing() || isDestroyed()) return;
        writeSettingsRequestInFlight = true;
        try {
            startActivity(intent);
        } catch (RuntimeException failure) {
            writeSettingsRequestInFlight = false;
            DcamLogger.e("Unable to open modify system settings", failure);
            FloatingNotice.show(this, R.string.write_settings_unavailable);
        }
    }

    private void performSettingAction(SettingId id) {
        if (id == SettingId.WIFI_CONNECT) {
            Intent intent = new Intent(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                    ? Settings.Panel.ACTION_WIFI : Settings.ACTION_WIFI_SETTINGS);
            startActivity(intent);
            return;
        }
        if (id == SettingId.LOGOUT) {
            viewModel.logout();
            return;
        }
        if (id == SettingId.CHANGE_OPERATOR_ID || id == SettingId.CHANGE_OPERATOR_PASSWORD) {
            FloatingNotice.show(this, R.string.account_change_pending);
            return;
        }
        throw new IllegalArgumentException("Setting " + id + " is not an action");
    }

    private void changeLanguage(AppLanguage language) {
        if (languageSettings == null || language == languageSettings.currentLanguage()) return;
        languageSettings.changeLanguage(language);
        FloatingNotice.show(this, R.string.language_changed);
        recreate();
    }

    private String languageName(AppLanguage language) {
        switch (language) {
            case SYSTEM: return getString(R.string.language_system);
            case ENGLISH: return getString(R.string.language_english);
            case VIETNAMESE: return getString(R.string.language_vietnamese);
            default: throw new IllegalArgumentException("Unsupported language " + language);
        }
    }

    private void handleAboutSecretTap() {
        long now = System.currentTimeMillis();
        if (now - devModeTapWindowStartedAtMs > DEV_MODE_UNLOCK_WINDOW_MS) {
            devModeTapWindowStartedAtMs = now;
            devModeTapCount = 0;
        }
        devModeTapCount++;
        if (devModeTapCount < DEV_MODE_UNLOCK_TAPS) return;

        devModeTapCount = 0;
        devModeTapWindowStartedAtMs = 0L;
        FloatingNotice.show(this, R.string.developer_mode_unlocked);
        viewModel.show(MainScreen.DEVELOPER_SETTINGS);
    }

    private MainScreen safeScreen(MainScreen screen) {
        if (screen == MainScreen.LOGIN
                || screen == MainScreen.DEVELOPER_USERS
                || screen == MainScreen.DEVELOPER_BUTTON_BINDINGS) return screen;
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
            case RECORD_SETTINGS: return R.string.record_settings;
            case CAMERA_SETTINGS: return R.string.camera_settings_short;
            case VIDEO_STREAM_SETTINGS: return R.string.video_stream_settings;
            case AUDIO_SETTINGS: return R.string.audio_settings;
            case STORAGE_SETTINGS: return R.string.storage_settings;
            case GPS_SETTINGS: return R.string.gps_settings;
            case DEVICE_SETTINGS: return R.string.device_settings_short;
            case USER_SETTINGS: return R.string.security_settings;
            case SERVER_SETTINGS: return R.string.network_settings;
            case TRANSFER_SETTINGS: return R.string.transfer_settings;
            case ABOUT: return R.string.about;
            case DEVELOPER_SETTINGS: return R.string.developer_mode;
            case DEVELOPER_BUTTON_BINDINGS: return R.string.button_role_bindings;
            case DEVELOPER_USERS: return R.string.developer_users;
            default: throw new IllegalArgumentException("No settings title for " + screen);
        }
    }

    private static int settingsItems(MainScreen screen) {
        switch (screen) {
            case RECORD_SETTINGS: return R.array.record_settings_items;
            case CAMERA_SETTINGS: return R.array.camera_settings_items;
            case VIDEO_STREAM_SETTINGS: return R.array.video_stream_settings_items;
            case AUDIO_SETTINGS: return R.array.audio_settings_items;
            case STORAGE_SETTINGS: return R.array.storage_settings_items;
            case GPS_SETTINGS: return R.array.gps_settings_items;
            case DEVICE_SETTINGS: return R.array.device_settings_items;
            case USER_SETTINGS: return R.array.security_settings_items;
            case SERVER_SETTINGS: return R.array.network_settings_items;
            case TRANSFER_SETTINGS: return R.array.transfer_settings_items;
            case ABOUT: return R.array.about_settings_items;
            default: throw new IllegalArgumentException("No settings list for " + screen);
        }
    }

    private void updateStatus(MainUiState state) {
        recordingStatusRenderer.updateFloatingRecordingStatus(state);
        if (cameraScreen != null) {
            boolean videoRecording = (state.getCapture().getMode() != RecordingMode.IDLE
                    && !state.getCapture().isSaving());
            cameraScreen.recordingBadge.setAlpha(RECORDING_BADGE_ACTIVE_ALPHA);
            updateGpsStatusLine();
            if (state.getOperatorSession() != null) {
                cameraScreen.operatorId.setText("USER " + state.getOperatorSession().getFileUserId());
            } else {
                cameraScreen.operatorId.setText("USER " + viewModel.getConfig().getPoliceUserId());
                DcamLogger.i("SERIAL_TRACE preview-state-fallback config=" + identity(viewModel.getConfig())
                        + " operatorHash=" + shortId(viewModel.getConfig().getPoliceUserId()));
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
        if (fileExplorerScreen != null) mediaBrowserRenderer.render(state.getMediaBrowser());
    }

    private String localizedMessage(String message) {
        if (message == null) return "";
        return message.startsWith("Saved ")
                ? getString(R.string.media_saved, message.substring(6))
                : message;
    }

    private void updateStorageWarning() {
        if (captureRuntime == null || cameraScreen == null) return;
        StorageWarningStatus warning = storageSettings.warningStatus();
        long free = warning.getFreeBytes();
        boolean warningVisible = warning.isVisible();
        if (warningVisible) {
            captureRuntime.showStorageWarning(
                    getString(R.string.low_storage_preview_warning, storageSize(free)));
        } else if (storageWarningVisible == null || storageWarningVisible) {
            captureRuntime.clearStorageWarning();
        }
        storageWarningVisible = warningVisible;
    }
    private String gpsCoordinatesText() {
        if (currentGpsCoordinate != null) return currentGpsCoordinate.toString();
        switch (locationTrackingState) {
            case PERMISSION_REQUIRED: return getString(R.string.gps_permission_required);
            case LOCATION_DISABLED: return getString(R.string.gps_location_disabled);
            case LOCATION_UNAVAILABLE:
            case NO_PROVIDER: return getString(R.string.gps_unavailable);
            case ERROR: return getString(R.string.gps_provider_error);
            case WAITING_FOR_FIX:
            case AVAILABLE: return getString(R.string.gps_waiting_for_fix);
            case STOPPED:
            default: return getString(R.string.gps_unavailable);
        }
    }

    private void updateGpsStatusLine() {
        if (cameraScreen == null) return;
        boolean visible = developerFeatureToggles.isEffectivelyEnabled(FeatureGate.GPS)
                && locationTrackingCoordinator != null
                && locationTrackingCoordinator.shouldShowOnCamera();
        cameraScreen.gpsStatus.setVisibility(visible ? View.VISIBLE : View.GONE);
        cameraScreen.gpsStatus.setText(visible ? gpsCoordinatesText() : "");
    }

    private void handleLocationSwitchChanged(boolean enabled) {
        if (locationControl == null) return;
        if (enabled && !hasRequiredLocationPermission()) {
            requestLocationPermission();
            renderedScreen = null;
            render(latestState);
            return;
        }
        boolean changed = locationControl.setEnabledIfPermitted(enabled);
        renderedScreen = null;
        render(latestState);
        if (!changed) {
            pendingLocationEnabled = enabled;
            try {
                startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
            } catch (RuntimeException ignored) {
                FloatingNotice.show(this, R.string.gps_settings_unavailable);
            }
            return;
        }
        refreshLocationTracking();
    }

    private void refreshLocationTracking() {
        if (locationTrackingCoordinator == null) return;
        locationTrackingCoordinator.refresh();
        syncLocationTrackingPresentation();
    }

    private void restartLocationTracking() {
        if (locationTracking == null) return;
        refreshLocationTracking();
    }

    private void stopLocationTracking() {
        if (locationTrackingCoordinator != null) locationTrackingCoordinator.stop();
        else if (locationTracking != null) locationTracking.stop();
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
                || hasRequiredLocationPermission()) return false;
        locationPermissionRequestInFlight = true;
        locationPermissionLauncher.launch(DcamPermissions.locationRuntime());
        return true;
    }

    private boolean canShowRequiredLocationPermissionRationale() {
        GpsMode mode = locationSettings == null
                ? GpsMode.SATELLITE : locationSettings.currentSettings().getMode();
        if (mode == GpsMode.SATELLITE) {
            return shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION);
        }
        return shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)
                || shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_COARSE_LOCATION);
    }

    private void refreshGpsPermissionRetry() {
        if (gpsPermissionRetryScheduler != null) gpsPermissionRetryScheduler.refresh();
    }

    private boolean shouldRetryGpsPermission() {
        return developerFeatureToggles != null
                && developerFeatureToggles.isEffectivelyEnabled(FeatureGate.GPS)
                && !hasRequiredLocationPermission()
                && !locationPermissionRequestInFlight
                && latestState != null
                && latestState.getCapture().getMode() == RecordingMode.IDLE
                && !audioRecording
                && !isFinishing()
                && !isDestroyed()
                && getLifecycle().getCurrentState().isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED);
    }
    private boolean hasRequiredLocationPermission() {
        return hasRequiredLocationPermission(locationSettings == null
                ? GpsMode.SATELLITE : locationSettings.currentSettings().getMode());
    }

    private boolean hasRequiredLocationPermission(GpsMode mode) {
        return mode == GpsMode.SATELLITE
                ? DcamPermissions.fineLocationGranted(this)
                : DcamPermissions.locationGranted(this);
    }

    private void navigateBack() {
        MainScreen screen = latestState == null ? MainScreen.CAMERA : latestState.getScreen();
        if (screen == MainScreen.LOGIN) return;
        if (screen == MainScreen.CAMERA) viewModel.show(MainScreen.MENU);
        else if (screen == MainScreen.MENU) viewModel.show(MainScreen.CAMERA);
        else if (screen == MainScreen.FILES && viewModel.navigateMediaUp()) return;
        else if (screen == MainScreen.DEVELOPER_USERS
                || screen == MainScreen.DEVELOPER_BUTTON_BINDINGS) {
            viewModel.show(MainScreen.DEVELOPER_SETTINGS);
        }
        else viewModel.show(MainScreen.MENU);
    }

    @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
        DcamLogger.i("Hardware key DOWN code=" + keyCode + " repeat="
                + event.getRepeatCount() + " time=" + event.getEventTime());
        if (renderedScreen == MainScreen.DEVELOPER_BUTTON_BINDINGS) {
            if (developerButtonBindingsScreen.onKeyDown(keyCode)) return true;
        }
        boolean handled = hardwareButtons != null
                && hardwareButtons.onKeyDown(keyCode, event.getRepeatCount(), event.getEventTime());
        if (handled && event.getRepeatCount() == 0 && hardwareButtons.isSosButton(keyCode)) {
            sosHoldAction = () -> {
                hardwareButtons.onSosHoldThreshold(keyCode);
            };
            cameraClock.postDelayed(sosHoldAction, HardwareButtonRouter.SOS_HOLD_MS);
        }
        return handled || super.onKeyDown(keyCode, event);
    }

    @Override public boolean onKeyUp(int keyCode, KeyEvent event) {
        DcamLogger.i("Hardware key UP code=" + keyCode + " time=" + event.getEventTime()
                + " focused=" + hasWindowFocus()
                + " canceled=" + event.isCanceled()
                + " flags=" + event.getFlags()
                + " device=" + event.getDeviceId()
                + " source=" + event.getSource()
                + " activeSwitch=" + (hardwareButtons == null
                        ? "none" : hardwareButtons.activeRecordSwitchKeyCodeForDebug()));
        if (!hasWindowFocus() && hardwareButtons != null) {
            hardwareButtons.clearFocusTransientState();
        }
        if (renderedScreen == MainScreen.DEVELOPER_BUTTON_BINDINGS) {
            if (developerButtonBindingsScreen.onKeyUp(keyCode)) {
                if (hardwareButtons != null) hardwareButtons.clearTransientState();
                return true;
            }
        }
        if (hardwareButtons != null && hardwareButtons.isSosButton(keyCode)
                && sosHoldAction != null) {
            cameraClock.removeCallbacks(sosHoldAction);
            sosHoldAction = null;
        }
        return hardwareButtons != null && hardwareButtons.onKeyUp(keyCode, event.isCanceled())
                || super.onKeyUp(keyCode, event);
    }

    @Override protected void onDestroy() {
        unregisterReceiver(wifiStateReceiver);
        unregisterReceiver(storageMountedReceiver);
        DcamLogger.i("MainActivity destroyed");
        FloatingNotice.clear(this);
        cameraClock.removeCallbacks(cameraClockTick);
        if (gpsPermissionRetryScheduler != null) gpsPermissionRetryScheduler.stop();
        stopLocationTracking();
        releaseCaptureRuntime();
        super.onDestroy();
    }

    private static String identity(Object value) {
        return value == null ? "null" : value.getClass().getSimpleName() + "@"
                + Integer.toHexString(System.identityHashCode(value));
    }

    private static String shortId(String value) {
        if (value == null) return "null";
        return Integer.toHexString(value.hashCode());
    }

    private static String sessionSummary(com.dvid.dcam.feature.auth.domain.OperatorSession session) {
        return session == null ? "null" : shortId(session.getSessionId())
                + "/bootHash=" + shortId(session.getBootId());
    }

    private void ensureCaptureRuntime() {
        DcamLogger.i("AUTH_TRACE ensure-capture-runtime composition=" + identity(composition)
                + " sessionUseCase=" + identity(composition.operatorSessionUseCase())
                + " session=" + sessionSummary(composition.operatorSessionUseCase().current()));
        if (captureRuntime != null) return;
        captureRuntime = composition.createCaptureRuntime(this);
        viewModel.bindCaptureEvents(captureRuntime.captureEvents());
        hardwareButtons = composition.createHardwareButtonRouter(
                captureRuntime.photoCapture(), captureRuntime.videoRecording(),
                captureRuntime.audioRecording(), this::setAudioRecording,
                () -> {
                    if (locationTrackingCoordinator != null) locationTrackingCoordinator.requestCurrentLocation();
                });
    }

    private void setAudioRecording(boolean recording, String fileName) {
        if (recording && !audioRecording) {
            audioStartedAtMillis = System.currentTimeMillis();
        }
        audioRecording = recording;
        if (!recording) {
            audioStartedAtMillis = null;
        }
        if (latestState != null) updateStatus(latestState);
        refreshGpsPermissionRetry();
        if (!recording && fileName != null) {
            FloatingNotice.show(this, getString(R.string.media_saved, fileName));
        }
    }

    private void releaseCaptureRuntime() {
        DcamLogger.i("AUTH_TRACE release-capture-runtime session="
                + sessionSummary(composition == null ? null
                : composition.operatorSessionUseCase().current()));
        if (captureRuntime == null) return;
        if (viewModel != null) {
            viewModel.unbindCaptureEvents(captureRuntime.captureEvents());
        }
        captureRuntime.release();
        captureRuntime = null;
        cameraScreen = null;
        hardwareButtons = null;
        if (sosHoldAction != null) cameraClock.removeCallbacks(sosHoldAction);
        sosHoldAction = null;
    }

    private void clearScreenBindings() {
        loginScreen = null;
        developerUsersScreen = null;
        fileExplorerScreen = null;
        menuScreen = null;
        renderedSettingsList = null;
    }

    private void removeNonCameraScreens() {
        if (cameraScreen != null) cameraScreen.getRoot().setAlpha(0f);
        for (int index = root.getChildCount() - 1; index >= 0; index--) {
            View child = root.getChildAt(index);
            if (cameraScreen == null || child != cameraScreen.getRoot()) root.removeViewAt(index);
        }
    }
}





