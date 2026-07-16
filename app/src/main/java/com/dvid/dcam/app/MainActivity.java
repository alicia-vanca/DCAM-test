package com.dvid.dcam.app;

import com.dvid.dcam.feature.settings.domain.AppLanguage;
import com.dvid.dcam.feature.settings.application.usecase.LanguageSettingsUseCase;
import com.dvid.dcam.feature.settings.application.usecase.MediaEncryptionSettingsUseCase;
import com.dvid.dcam.feature.settings.application.usecase.VideoMd5SettingsUseCase;
import android.app.AlertDialog;
import android.content.Context;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.ContentObserver;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.content.pm.ActivityInfo;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
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
import com.dvid.dcam.app.navigation.MainScreen;
import com.dvid.dcam.app.presentation.MainMenuModel;
import com.dvid.dcam.app.presentation.MainMenuTile;
import com.dvid.dcam.app.presentation.MainUiState;
import com.dvid.dcam.app.presentation.MainViewModel;
import com.dvid.dcam.app.presentation.MainViewModelFactory;
import com.dvid.dcam.app.presentation.LocationTrackingCoordinator;
import com.dvid.dcam.core.config.domain.DcamConfig;
import com.dvid.dcam.core.feature.domain.FeatureGate;
import com.dvid.dcam.databinding.ActivityMainBinding;
import com.dvid.dcam.databinding.ItemMediaEntryBinding;
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
import com.dvid.dcam.feature.media.domain.MediaEntry;
import com.dvid.dcam.feature.storage.application.usecase.StorageSettingsUseCase;
import com.dvid.dcam.feature.storage.domain.MediaPartitionLocation;
import com.dvid.dcam.feature.storage.domain.StorageVolumeStatus;
import com.dvid.dcam.feature.storage.domain.StorageWarningStatus;
import com.dvid.dcam.feature.settings.presentation.SettingsUiState;
import com.dvid.dcam.feature.settings.presentation.StorageOptionUiState;
import com.dvid.dcam.feature.settings.presentation.DeveloperButtonBindingsScreen;
import com.dvid.dcam.feature.settings.presentation.SettingId;
import com.dvid.dcam.feature.settings.presentation.SettingItem;
import com.dvid.dcam.feature.settings.presentation.SettingsControlRenderer;
import com.dvid.dcam.feature.settings.presentation.SettingsScreenModel;
import com.dvid.dcam.feature.settings.presentation.SettingsSection;
import com.dvid.dcam.platform.config.AndroidLanguagePreferenceStoreImpl;
import com.dvid.dcam.platform.camera.CameraXCameraGatewayImpl;
import com.dvid.dcam.platform.device.DcamKioskController;
import com.dvid.dcam.platform.device.AndroidDeviceSettings;
import com.dvid.dcam.platform.device.AndroidDeviceCapabilities;
import com.dvid.dcam.platform.input.HardwareButtonLayout;
import com.dvid.dcam.platform.input.HardwareButtonRouter;
import com.dvid.dcam.platform.logging.DcamLogger;
import com.dvid.dcam.platform.database.AppDatabase;
import com.dvid.dcam.platform.permission.DcamPermissions;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Android entry point and ViewBinding presentation shell. */
public final class MainActivity extends ComponentActivity {
    private static final int DEV_MODE_UNLOCK_TAPS = 7;
    private static final long DEV_MODE_UNLOCK_WINDOW_MS = 5_000L;
    private static final float RECORDING_BADGE_ACTIVE_ALPHA = 1f;
    private final DateTimeFormatter clock = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final Handler cameraClock = new Handler(Looper.getMainLooper());
    private final Runnable cameraClockTick = new Runnable() {
        @Override public void run() {
            updateCameraClock();
            updateManagedTopBar();
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
    private VideoMd5SettingsUseCase videoMd5Settings;
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
    private boolean locationPermissionRequestInFlight;
    private Boolean pendingLocationEnabled;
    private DcamKioskController kioskController;
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
        videoMd5Settings = composition.videoMd5SettingsUseCase();
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
                videoMd5Settings.isVideoMd5Enabled(),
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
        root = activityBinding.contentRoot;
        setContentView(activityBinding.getRoot());
        updateManagedTopBar();
        bindSystemNavigationInset();
        hideSystemStatusBar();

        permissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                result -> {
                    if (DcamPermissions.cameraGranted(this)) {
                        CameraXCameraGatewayImpl.warmUp(this);
                        if (captureRuntime != null) captureRuntime.bindCameraIfPermitted();
                    }
                    refreshLocationTracking();
                });
        locationPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                result -> {
                    locationPermissionRequestInFlight = false;
                    refreshLocationTracking();
                    renderedScreen = null;
                    if (latestState != null) render(latestState);
                });
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() { navigateBack(); }
        });

        if (!DcamPermissions.coreRuntimeGranted(this)) {
            permissionLauncher.launch(DcamPermissions.coreRuntime());
        } else {
            CameraXCameraGatewayImpl.warmUp(this);
        }
        openMedia = composition.createOpenMediaUseCase(this);
        viewModel = new ViewModelProvider(
                this, new MainViewModelFactory(
                composition.config(), composition.initialDeviceStatus(),
                composition.refreshDeviceStatusUseCase(), composition.browseMediaUseCase(),
                composition.authenticateOperatorUseCase(),
                composition.operatorSessionUseCase(),
                composition.manageOperatorUsersUseCase()))
                .get(MainViewModel.class);
        viewModel.state().observe(this, this::render);
        ContextCompat.registerReceiver(this, wifiStateReceiver,
                new IntentFilter(WifiManager.WIFI_STATE_CHANGED_ACTION),
                ContextCompat.RECEIVER_NOT_EXPORTED);
        IntentFilter storageFilter = new IntentFilter(Intent.ACTION_MEDIA_MOUNTED);
        storageFilter.addDataScheme("file");
        ContextCompat.registerReceiver(this, storageMountedReceiver, storageFilter,
                ContextCompat.RECEIVER_NOT_EXPORTED);
    }

    @Override protected void onResume() {
        super.onResume();
        writeSettingsRequestInFlight = false;
        if (pendingAutoRotateValue != null && deviceSettings.canWriteSystemSettings()) {
            boolean requested = pendingAutoRotateValue;
            if (deviceSettings.setAutoRotateEnabled(requested)) pendingAutoRotateValue = null;
        }
        syncAutoRotateFromDevice();
        hideSystemStatusBar();
        refreshWifiSetting();
        if (kioskController != null) {
            kioskController.applyActiveKioskPolicy();
            kioskController.enterLockTaskIfAllowed(this);
        }
        if (viewModel != null) viewModel.refreshDeviceStatus();
        if (pendingLocationEnabled != null) {
            pendingLocationEnabled = null;
            renderedScreen = null;
        }
        refreshLocationTracking();
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
        if (hasFocus) hideSystemStatusBar();
    }

    private void hideSystemStatusBar() {
        if (kioskController == null || !kioskController.isDeviceOwner()) {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            return;
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            View decor = getWindow().peekDecorView();
            if (decor == null) return;
            WindowInsetsController controller = decor.getWindowInsetsController();
            if (controller != null) {
                controller.setSystemBarsBehavior(
                        WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                controller.hide(WindowInsets.Type.statusBars());
            }
            return;
        }
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }

    private void updateManagedTopBar() {
        if (activityBinding == null || kioskController == null) return;
        boolean visible = kioskController.isDeviceOwner();
        activityBinding.managedTopBar.setVisibility(visible ? View.VISIBLE : View.GONE);
        FrameLayout.LayoutParams contentLayout =
                (FrameLayout.LayoutParams) activityBinding.contentRoot.getLayoutParams();
        int topMargin = visible ? dp(24) : 0;
        if (contentLayout.topMargin != topMargin) {
            contentLayout.topMargin = topMargin;
            activityBinding.contentRoot.setLayoutParams(contentLayout);
        }
        if (!visible) return;

        BatteryManager battery = getSystemService(BatteryManager.class);
        int batteryPercent = battery == null ? -1
                : battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
        activityBinding.managedBatteryStatus.setPercent(batteryPercent);
        activityBinding.managedBatteryStatus.setContentDescription(batteryPercent < 0
                ? getString(R.string.battery_unknown)
                : getString(R.string.battery_percent, batteryPercent));

        ConnectivityManager connectivity = getSystemService(ConnectivityManager.class);
        NetworkCapabilities capabilities = connectivity == null ? null
                : connectivity.getNetworkCapabilities(connectivity.getActiveNetwork());
        boolean wifiConnected = capabilities != null
                && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
        int wifiLevel = 0;
        if (wifiConnected) {
            int rssi = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                    ? capabilities.getSignalStrength()
                    : NetworkCapabilities.SIGNAL_STRENGTH_UNSPECIFIED;
            if (rssi == NetworkCapabilities.SIGNAL_STRENGTH_UNSPECIFIED) {
                WifiManager wifi = getSystemService(WifiManager.class);
                if (wifi != null && wifi.getConnectionInfo() != null) {
                    rssi = wifi.getConnectionInfo().getRssi();
                }
            }
            wifiLevel = rssi == NetworkCapabilities.SIGNAL_STRENGTH_UNSPECIFIED
                    ? 1 : WifiManager.calculateSignalLevel(rssi, 4) + 1;
        }
        activityBinding.managedWifiStatus.setSignal(wifiConnected, wifiLevel);
        activityBinding.managedWifiStatus.setContentDescription(wifiConnected
                ? getString(R.string.wifi_signal_level, wifiLevel)
                : getString(R.string.wifi_disconnected));
    }

    private void bindSystemNavigationInset() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            activityBinding.getRoot().setOnApplyWindowInsetsListener((view, insets) -> {
                int bottom = insets.getInsets(WindowInsets.Type.navigationBars()).bottom;
                FrameLayout.LayoutParams layout = (FrameLayout.LayoutParams)
                        activityBinding.customStatusBar.getLayoutParams();
                layout.bottomMargin = bottom + dp(8);
                activityBinding.customStatusBar.setLayoutParams(layout);
                return insets;
            });
            activityBinding.getRoot().requestApplyInsets();
        }
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
            updateFloatingRecordingStatus(state);
            if (screen == MainScreen.LOGIN) renderLogin();
            else if (screen == MainScreen.CAMERA) renderCamera();
            else if (screen == MainScreen.MENU) renderMenu();
            else if (screen == MainScreen.FILES) renderFileExplorer();
            else if (screen == MainScreen.DEVELOPER_USERS) renderDeveloperUsers();
            else renderSettingsDetail(screen);
        }
        updateStatus(state);
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
        clearScreenBindings();
        removeNonCameraScreens();
        ensureCameraScreen();
        cameraScreen.getRoot().setAlpha(1f);
        updateCameraClock();
    }

    private void ensureCameraScreen() {
        if (cameraScreen == null) {
            cameraScreen = ScreenCameraBinding.inflate(getLayoutInflater(), root, false);
            root.addView(cameraScreen.getRoot(), 0);
        }
        DcamConfig config = viewModel.getConfig();
        cameraScreen.accountId.setText("CAM " + config.getAccountUserId());
        cameraScreen.operatorId.setText("USER " + config.getPoliceUserId());
        updateGpsStatusLine();
        bindFeatureAction(
                cameraScreen.captureAction,
                FeatureGate.IMAGE_CAPTURE,
                () -> captureRuntime.photoCapture().takePhoto());
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
            return developerFeatureToggles.developerSettings();
        }
        SettingsScreenModel model;
        if (screen == MainScreen.RECORD_SETTINGS) model = settingsUiState.recording();
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

    private SettingsScreenModel locationSettingsModel() {
        GpsSettings current = locationSettings.currentSettings();
        List<GpsMode> modes = locationSettings.supportedModes();
        LocationSystemState systemState = locationControl.currentState();
        boolean systemStateKnown = systemState == LocationSystemState.ENABLED
                || systemState == LocationSystemState.DISABLED;
        List<String> modeLabels = new ArrayList<>();
        for (GpsMode mode : modes) modeLabels.add(gpsModeLabel(mode));
        List<SettingItem> items = List.of(
                SettingItem.checkbox(SettingId.GPS_LOCATION_ENABLED,
                        getString(R.string.gps_use_location),
                        systemState == LocationSystemState.ENABLED
                                && hasRequiredLocationPermission(current.getMode()))
                        .withEnabled(systemStateKnown),
                SettingItem.choice(SettingId.GPS_POSITIONING_MODE,
                        getString(R.string.gps_positioning_mode), modeLabels,
                        Math.max(0, modes.indexOf(current.getMode()))),
                SettingItem.slider(SettingId.GPS_UPDATE_DISTANCE_METERS,
                        getString(R.string.gps_update_distance), 1, 30,
                        current.getUpdateDistanceMeters(), "m"),
                SettingItem.slider(SettingId.GPS_REPORT_INTERVAL_SECONDS,
                        getString(R.string.gps_report_interval), 1, 30,
                        current.getReportIntervalSeconds(), "s"));
        return new SettingsScreenModel(List.of(
                new SettingsSection(getString(R.string.gps_sampling_section), items)));
    }

    private String gpsModeLabel(GpsMode mode) {
        switch (mode) {
            case GPS: return getString(R.string.gps_mode_gps);
            case GPS_AGPS: return getString(R.string.gps_mode_gps_agps);
            case GMAP: return getString(R.string.gps_mode_gmap);
            default: throw new IllegalArgumentException("Unsupported GPS mode " + mode);
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
        if (item.getId() == SettingId.CREATE_VIDEO_MD5) {
            return gates(FeatureGate.VIDEO_MD5);
        }
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
                storageSettings.changeMode(modes.get(selectedIndex));
                recreate();
            }
            return;
        }
        if (id == SettingId.GPS_POSITIONING_MODE) {
            List<GpsMode> modes = locationSettings.supportedModes();
        LocationSystemState systemState = locationControl.currentState();
        boolean systemStateKnown = systemState == LocationSystemState.ENABLED
                || systemState == LocationSystemState.DISABLED;
            if (selectedIndex >= 0 && selectedIndex < modes.size()) {
                locationSettings.changeMode(modes.get(selectedIndex));
                restartLocationTracking();
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
            if (id == SettingId.FEATURE_GPS) {
                if (checked) {
                    if (!hasRequiredLocationPermission()) requestLocationPermission();
                    else refreshLocationTracking();
                } else {
                    stopLocationTracking();
                }
            }
            renderedScreen = null;
            render(latestState);
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
        if (id == SettingId.CREATE_VIDEO_MD5 && videoMd5Settings != null) {
            videoMd5Settings.setVideoMd5Enabled(checked);
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
        updateFloatingRecordingStatus(state);
        if (cameraScreen != null) {
            boolean videoRecording = (state.getCapture().getMode() != RecordingMode.IDLE
                    && !state.getCapture().isSaving());
            cameraScreen.recordingBadge.setAlpha(RECORDING_BADGE_ACTIVE_ALPHA);
            updateGpsStatusLine();
            if (state.getOperatorSession() != null) {
                cameraScreen.operatorId.setText("USER " + state.getOperatorSession().getFileUserId());
            } else {
                cameraScreen.operatorId.setText("USER " + viewModel.getConfig().getPoliceUserId());
            }
            updateCameraClock();
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
        if (fileExplorerScreen != null) updateFileExplorer(state);
    }

    private String localizedMessage(String message) {
        if (message == null) return "";
        return message.startsWith("Saved ")
                ? getString(R.string.media_saved, message.substring(6))
                : message;
    }

    private void updateFileExplorer(MainUiState state) {
        fileExplorerScreen.entries.removeAllViews();
        String path = state.getMediaBrowser().getRelativePath();
        fileExplorerScreen.path.setText(path.isEmpty()
                ? getString(R.string.media_root) : path.replace("/", " / "));
        if (state.getMediaBrowser().isLoading()) {
            fileExplorerScreen.status.setText(R.string.media_loading);
            return;
        }
        if (state.getMediaBrowser().getError() != null) {
            fileExplorerScreen.status.setText(state.getMediaBrowser().getError());
            return;
        }
        if (state.getMediaBrowser().getEntries().isEmpty()) {
            fileExplorerScreen.status.setText(R.string.media_empty);
            return;
        }
        fileExplorerScreen.status.setText("");
        for (MediaEntry entry : state.getMediaBrowser().getEntries()) {
            ItemMediaEntryBinding row = ItemMediaEntryBinding.inflate(
                    getLayoutInflater(), fileExplorerScreen.entries, false);
            row.icon.setImageResource(entry.isDirectory()
                    ? R.drawable.ic_settings_files : R.drawable.ic_media_file);
            row.name.setText(entry.getName());
            boolean mediaTypeFolder = entry.isDirectory()
                    && entry.getRelativePath().indexOf('/') > 0
                    && entry.getRelativePath().indexOf('/') == entry.getRelativePath().lastIndexOf('/');
            row.folderCounts.setVisibility(mediaTypeFolder ? View.VISIBLE : View.GONE);
            row.fileDetails.setVisibility(entry.isDirectory() ? View.GONE : View.VISIBLE);
            row.fileCount.setText(String.valueOf(entry.getChildFileCount()));
            row.fileDetails.setText(formatFileSize(entry.getSizeBytes()));
            row.getRoot().setOnClickListener(view -> {
                if (entry.isDirectory()) viewModel.openMediaFolder(entry.getRelativePath());
                else openMediaFile(entry);
            });
            fileExplorerScreen.entries.addView(row.getRoot());
        }
    }

    private void openMediaFile(MediaEntry entry) {
        if (openMedia == null || !openMedia.execute(entry)) {
            FloatingNotice.show(this, R.string.media_open_failed);
        }
    }

    private static String formatFileSize(long bytes) {
        if (bytes < 1024L) return bytes + " B";
        if (bytes < 1024L * 1024L) return String.format(Locale.ROOT, "%.1f KB", bytes / 1024d);
        if (bytes < 1024L * 1024L * 1024L) {
            return String.format(Locale.ROOT, "%.1f MB", bytes / (1024d * 1024d));
        }
        return String.format(Locale.ROOT, "%.1f GB", bytes / (1024d * 1024d * 1024d));
    }

    private void updateCameraClock() {
        LocalDateTime now = LocalDateTime.now();
        MainUiState state = latestState;
        updateFloatingRecordingStatus(state);
        if (cameraScreen == null) return;
        boolean videoRecording = state != null
                && state.getCapture().getMode() != RecordingMode.IDLE
                && !state.getCapture().isSaving();
        cameraScreen.currentTime.setText(clock.format(now));
        cameraScreen.videoRecordingStatus.setVisibility(videoRecording ? View.VISIBLE : View.GONE);
        cameraScreen.audioRecordingStatus.setVisibility(audioRecording ? View.VISIBLE : View.GONE);
        cameraScreen.recordingTimer.setText(videoRecording ? videoDurationText(state) : "");
        cameraScreen.audioRecordingTimer.setText(audioRecording ? audioDurationText() : "");
    }

    private void updateStorageWarning() {
        if (captureRuntime == null || cameraScreen == null) return;
        StorageWarningStatus warning = storageSettings.warningStatus();
        long free = warning.getFreeBytes();
        boolean warningVisible = warning.isVisible();
        if (storageWarningVisible != null && storageWarningVisible == warningVisible) return;
        storageWarningVisible = warningVisible;
        if (warningVisible) {
            captureRuntime.showStorageWarning(
                    getString(R.string.low_storage_preview_warning, storageSize(free)));
        } else {
            captureRuntime.clearStorageWarning();
        }
    }
    private void updateFloatingRecordingStatus(MainUiState state) {
        boolean videoRecording = state != null
                && state.getCapture().getMode() != RecordingMode.IDLE
                && !state.getCapture().isSaving();
        boolean recording = videoRecording || audioRecording;
        boolean showFloatingRecording = recording && renderedScreen != MainScreen.CAMERA;
        activityBinding.customStatusBar.setVisibility(
                showFloatingRecording ? View.VISIBLE : View.GONE);
        activityBinding.customStatusVideoRow.setVisibility(videoRecording ? View.VISIBLE : View.GONE);
        activityBinding.customStatusAudioRow.setVisibility(audioRecording ? View.VISIBLE : View.GONE);
        activityBinding.customStatusVideoDuration.setText(videoRecording ? videoDurationText(state) : "");
        activityBinding.customStatusAudioDuration.setText(audioRecording ? audioDurationText() : "");
    }

    private String videoDurationText(MainUiState state) {
        if (state == null) return "";
        return formatDuration(state.getCapture().getStartedAtMillis());
    }

    private String audioDurationText() {
        return formatDuration(audioStartedAtMillis);
    }

    private static String formatDuration(Long startedAtMillis) {
        if (startedAtMillis == null) return "";
        long elapsedMs = Math.max(0L, System.currentTimeMillis() - startedAtMillis);
        long totalSeconds = elapsedMs / 1_000L;
        long hours = totalSeconds / 3_600L;
        long minutes = (totalSeconds % 3_600L) / 60L;
        long seconds = totalSeconds % 60L;
        return String.format(Locale.ROOT, "%02d:%02d:%02d", hours, minutes, seconds);
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
        boolean enabled = developerFeatureToggles.isEffectivelyEnabled(FeatureGate.GPS);
        cameraScreen.gpsStatus.setVisibility(enabled ? View.VISIBLE : View.GONE);
        cameraScreen.gpsStatus.setText(enabled ? gpsCoordinatesText() : "");
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
        if (locationPermissionLauncher == null || locationPermissionRequestInFlight
                || hasRequiredLocationPermission()) return;
        locationPermissionRequestInFlight = true;
        locationPermissionLauncher.launch(DcamPermissions.locationRuntime());
    }

    private boolean hasRequiredLocationPermission() {
        return hasRequiredLocationPermission(locationSettings == null
                ? GpsMode.GPS : locationSettings.currentSettings().getMode());
    }

    private boolean hasRequiredLocationPermission(GpsMode mode) {
        return mode == GpsMode.GMAP
                ? DcamPermissions.locationGranted(this)
                : DcamPermissions.fineLocationGranted(this);
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
        if (renderedScreen == MainScreen.DEVELOPER_BUTTON_BINDINGS) {
            if (developerButtonBindingsScreen.onKeyUp(keyCode)) return true;
        }
        if (hardwareButtons != null && hardwareButtons.isSosButton(keyCode)
                && sosHoldAction != null) {
            cameraClock.removeCallbacks(sosHoldAction);
            sosHoldAction = null;
        }
        return hardwareButtons != null && hardwareButtons.onKeyUp(keyCode)
                || super.onKeyUp(keyCode, event);
    }

    @Override protected void onDestroy() {
        unregisterReceiver(wifiStateReceiver);
        unregisterReceiver(storageMountedReceiver);
        DcamLogger.i("MainActivity destroyed");
        cameraClock.removeCallbacks(cameraClockTick);
        stopLocationTracking();
        releaseCaptureRuntime();
        super.onDestroy();
    }

    private void ensureCaptureRuntime() {
        if (captureRuntime != null) return;
        captureRuntime = composition.createCaptureRuntime(this);
        viewModel.bindCaptureEvents(captureRuntime.captureEvents());
        hardwareButtons = composition.createHardwareButtonRouter(
                captureRuntime.photoCapture(), captureRuntime.videoRecording(),
                captureRuntime.audioRecording(), this::setAudioRecording);
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
        if (!recording && fileName != null) {
            FloatingNotice.show(this, getString(R.string.media_saved, fileName));
        }
    }

    private void releaseCaptureRuntime() {
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
    }

    private void removeNonCameraScreens() {
        if (cameraScreen != null) cameraScreen.getRoot().setAlpha(0f);
        for (int index = root.getChildCount() - 1; index >= 0; index--) {
            View child = root.getChildAt(index);
            if (cameraScreen == null || child != cameraScreen.getRoot()) root.removeViewAt(index);
        }
    }
}





