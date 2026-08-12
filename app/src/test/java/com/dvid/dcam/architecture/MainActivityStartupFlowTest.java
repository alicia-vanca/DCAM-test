package com.dvid.dcam.architecture;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

final class MainActivityStartupFlowTest {
    @Test void serialEntryUsesAllCapsInputFilter() throws IOException {
        String activity = source("app/MainActivity.java");

        assertTrue(activity.contains("new InputFilter.AllCaps()"));
    }

    @Test void serialFlowRemovesHardwareDefaultAndKeepsRoom() throws IOException {
        String activity = source("app/MainActivity.java");
        String composition = source("app/AppComposition.java");
        String store = source("platform/config/FileDeviceSerialNumberStore.java");
        String androidDevice = source("platform/device/AndroidDeviceRepositoryImpl.java");
        String roomIdentity = source("platform/cloud/RoomDeviceIdentityRepositoryImpl.java");
        String deviceInfo = coreSource("device/domain/DeviceInfo.java");
        String english = resource("values/strings.xml");
        String vietnamese = resource("values-vi/strings.xml");

        assertTrue(activity.contains(".setMessage(R.string.device_serial_message)"));
        assertTrue(activity.contains("deviceSerialNumbers.save(serial);"));
        assertFalse(activity.contains("defaultDeviceSerial"));
        assertFalse(activity.contains("saveDefault"));
        assertFalse(activity.contains("device_serial_use_default"));
        assertFalse(composition.contains("defaultDeviceSerial"));
        assertTrue(store.contains("CloudStateDao"));
        assertTrue(store.contains("DeviceIdentityEntity"));
        assertTrue(store.contains("private final String hardwareId;"));
        assertFalse(store.contains("identity.serialNumber = hardwareId"));
        assertFalse(deviceInfo.contains("serialNumber"));
        assertFalse(androidDevice.contains("new DeviceInfo(hardwareId, model.trim(), platformSerial)"));
        assertFalse(roomIdentity.contains("deviceInfo.getSerialNumber()"));
        assertFalse(english.contains("device_serial_message_with_default"));
        assertFalse(english.contains("device_serial_use_default"));
        assertFalse(vietnamese.contains("device_serial_message_with_default"));
        assertFalse(vietnamese.contains("device_serial_use_default"));
    }

    @Test void configuredSerialDoesNotBlockOnMirrorSynchronizationFailure() throws IOException {
        String activity = source("app/MainActivity.java");
        String english = resource("values/strings.xml");
        String vietnamese = resource("values-vi/strings.xml");

        int precheck = activity.indexOf(
                "private void ensureConfigFileAccess(boolean interactionReady)");
        int failure = activity.indexOf("} catch (Exception error)", precheck);
        int configured = activity.indexOf(
                "boolean serialConfigured = deviceSerialNumbers.isConfigured(", failure);
        int nonBlocking = activity.indexOf(
                "identityRestoreError = serialConfigured ? null : error;", configured);
        int warning = activity.indexOf(
                "configured serial remains available", nonBlocking);
        int completion = activity.indexOf("completeDeviceIdentityCheck();", warning);
        assertTrue(precheck >= 0);
        assertTrue(failure > precheck);
        assertTrue(configured > failure);
        assertTrue(nonBlocking > configured);
        assertTrue(warning > nonBlocking);
        assertTrue(completion > warning);
        assertTrue(english.contains(
                "<string name=\"device_identity_restore_retry\">Retry</string>"));
        assertTrue(vietnamese.contains(
                "<string name=\"device_identity_restore_retry\">Thử lại</string>"));
    }

    @Test void cameraLabelWaitsForSerialAndGpsPromptRunsFirst() throws IOException {
        String activity = source("app/MainActivity.java");

        assertTrue(activity.contains("String savedSerial = deviceSerialNumbers.load();"));
        assertTrue(activity.contains("? \"CAM \" + savedSerial")
                && activity.contains(": \"CAM —\""));
        assertTrue(activity.contains("deviceSerialNumbers.isConfigured(savedSerial)"));
        assertTrue(activity.contains("startupGpsPermissionHandled = launchLocationPermissionRequest();"));
        assertFalse(activity.contains("hasValidSavedSerial()"));

        int coreCallback = activity.indexOf("permissionLauncher = registerForActivityResult");
        int locationCallback = activity.indexOf("locationPermissionLauncher = registerForActivityResult");
        int gpsRequest = activity.indexOf("requestStartupLocationPermissionAfterCameraReady();", coreCallback);
        String coreFlow = activity.substring(coreCallback, locationCallback);
        assertTrue(gpsRequest > coreCallback);
        assertTrue(coreFlow.contains("capturePermissionRequestInFlight = false;"));
        int runtimeProfilePrepare = coreFlow.indexOf(
                "prepareCameraProfilesIfCameraGranted(\"runtime-result\");");
        int deniedCapture = coreFlow.indexOf("if (!captureGranted)");
        assertTrue(runtimeProfilePrepare >= 0);
        assertTrue(deniedCapture > runtimeProfilePrepare);
        assertTrue(coreFlow.contains("showCapturePermissionRequired();"));
        assertTrue(activity.contains("capturePermissionSettingsLauncher = registerForActivityResult"));
        assertTrue(activity.contains("R.string.capture_permission_required_message"));
        assertTrue(activity.contains(".setCancelable(false)"));
        assertFalse(activity.contains("androidRuntime.corePermissionsGranted()"));
        assertTrue(activity.indexOf("startDeviceIdentityPrecheck();", locationCallback)
                > locationCallback);
        int locationIdentityReady = activity.indexOf("ensureConfigFileAccess();", locationCallback);
                int locationSettingsGuard = activity.indexOf("if (openSettings)", locationCallback);
        int locationSettings = activity.indexOf(
                "openLocationPermissionSettings();", locationSettingsGuard);
        assertTrue(locationIdentityReady > locationCallback);
                assertTrue(locationSettingsGuard > locationIdentityReady);
        assertTrue(locationSettings > locationSettingsGuard);
    }

    @Test void cameraSwitchStateUsesEventsInsteadOfClockPolling() throws IOException {
        String activity = source("app/MainActivity.java");
        int tick = activity.indexOf("private final Runnable cameraClockTick");
        int nextField = activity.indexOf("private FrameLayout root", tick);

        assertTrue(tick >= 0);
        assertTrue(nextField > tick);
        assertFalse(activity.substring(tick, nextField).contains("updateCameraSwitchAction();"));
        int idleReleaseObserver = activity.indexOf(
                "idleCameraReleaseStateSubscription = composition.observeCameraStateChanges(");
        int screenReceiverRegistration = activity.indexOf(
                "ContextCompat.registerReceiver(this, screenStateReceiver", idleReleaseObserver);
        int refreshedScreenState = activity.indexOf(
                "screenOff = !androidRuntime.isScreenInteractive();",
                screenReceiverRegistration);
        int startupFlow = activity.indexOf("startStartupPermissionFlow();", refreshedScreenState);
        assertTrue(idleReleaseObserver >= 0);
        assertTrue(screenReceiverRegistration > idleReleaseObserver);
        assertTrue(refreshedScreenState > screenReceiverRegistration);
        assertTrue(startupFlow > refreshedScreenState);
        assertTrue(activity.contains(
                "private void releaseIdleCameraIfScreenOffNow() {\n"
                + "        if (androidRuntime == null) return;\n"
                + "        screenOff = !androidRuntime.isScreenInteractive();"));
        assertTrue(activity.contains(
                "captureRuntime.observeCameraSwitchState(this::requestCameraSwitchActionUpdate);"));
    }

    @Test void recordingDurationsShareOneTickerWithoutSecondStartReset() throws IOException {
        String activity = source("app/MainActivity.java");
        String renderer = source("app/ui/RecordingStatusRenderer.java");
        int render = activity.indexOf("private void render(MainUiState state)");
        int screenRender = activity.indexOf("if (renderedScreen == null", render);
        String transition = activity.substring(render, screenRender);

        assertTrue(transition.contains("boolean wasRecording = hasActiveRecording(previousState);"));
        assertTrue(transition.contains("boolean recording = hasActiveRecording(state);"));
        assertTrue(transition.contains("if (!wasRecording && recording)"));
        assertTrue(transition.contains("startRecordingDurationTicker();"));
        assertTrue(transition.contains("else if (wasRecording && !recording)"));
        assertTrue(transition.contains("stopRecordingDurationTicker();"));
        assertFalse(transition.contains("removeCallbacks(cameraClockTick)"));
        assertTrue(activity.contains(
                "nextRecordingDurationTickAtMillis = SystemClock.uptimeMillis() + 1_000L;"));
        assertTrue(activity.contains(
                "cameraClock.postAtTime(recordingDurationTick, nextRecordingDurationTickAtMillis);"));
        assertTrue(renderer.contains("videoDurationSeconds++;"));
        assertTrue(renderer.contains("if (audioDurationActive) audioDurationSeconds++;"));
    }

    @Test void photoFinalizationUsesPersistentSavingNotice() throws IOException {
        String activity = source("app/MainActivity.java");
        int start = activity.indexOf("private void updateSavingNotice");
        int end = activity.indexOf("private void showSavedNotice", start);
        String savingNotice = activity.substring(start, end);

        assertTrue(savingNotice.contains("isPhotoSaving()"));
        assertTrue(savingNotice.contains("FloatingNotice.showPersistent"));
        assertTrue(savingNotice.contains("FloatingNotice.hidePersistent"));
    }

    @Test void expiredOrReplayedCaptureCompletionDoesNotShowSavedNotice() throws IOException {
        String activity = source("app/MainActivity.java");
        String floatingNotice = source("app/ui/FloatingNotice.java");
        int start = activity.indexOf("private void showSavedNotice");
        int end = activity.indexOf("private void showCaptureFailureNotice", start);
        String savedNotice = activity.substring(start, end);

        assertTrue(activity.contains("private static final long SAVED_NOTICE_MAX_AGE_MS = "
                + "FloatingNotice.TRANSIENT_DURATION_MS;"));
        assertTrue(floatingNotice.contains(
                "public static final long TRANSIENT_DURATION_MS = 2_000L;"));
        assertTrue(floatingNotice.contains(
                "HANDLER.postDelayed(transientHide, TRANSIENT_DURATION_MS);"));
        assertTrue(savedNotice.contains("state.isCaptureHapticSuppressed()"));
        assertTrue(savedNotice.contains("!androidRuntime.isScreenInteractive()"));
        assertTrue(savedNotice.contains("viewModel.lastSavedNoticeAtMillis()"));
        assertTrue(savedNotice.contains("savedNoticeAgeMillis < 0L"));
        assertTrue(savedNotice.contains("savedNoticeAgeMillis >= SAVED_NOTICE_MAX_AGE_MS"));
    }

    @Test void captureHapticFollowsCommandInitialization() throws IOException {
        String activity = source("app/MainActivity.java");
        int render = activity.indexOf("private void render(MainUiState state)");
        int savingNotice = activity.indexOf("private void updateSavingNotice", render);
        int keyDown = activity.indexOf("private boolean handleHardwareKeyDown");
        int destroy = activity.indexOf("protected void onDestroy", keyDown);

        assertFalse(activity.substring(render, savingNotice)
                .contains("vibrateCaptureCommandStart();"));
        assertFalse(activity.substring(
                activity.indexOf("private void showSavedNotice"),
                activity.indexOf("private void showCaptureFailureNotice"))
                .contains("vibrateCaptureCommandStart();"));
        assertFalse(activity.substring(keyDown, destroy)
                .contains("vibrateCaptureCommandStart();"));
        assertTrue(activity.contains("this::vibrateCaptureCommandStart"));
    }

    @Test void capturePermissionGateGuardsCaptureAndAllowsCameraProfileWarmup() throws IOException {
        String activity = source("app/MainActivity.java");
        String composition = source("app/AppComposition.java");
        String permissions = source("platform/permission/DcamPermissions.java");
        String audio = source("feature/capture/application/usecase/AudioRecordingUseCase.java");

        int startup = activity.indexOf("private void startStartupPermissionFlow()");
        int complete = activity.indexOf("private void completeCapturePermissionGate", startup);
        int identity = activity.indexOf("startDeviceIdentityPrecheck();", complete);
        assertTrue(startup >= 0);
        assertTrue(complete > startup);
        assertTrue(identity > complete);
        assertTrue(activity.contains("capturePermissionFlowStarted"));
        assertTrue(activity.contains("capturePermissionPolicyRequestInFlight"));
        assertTrue(activity.contains(
                "if (capturePermissionFlowStarted && capturePermissionPolicyApplied)"));
        assertTrue(activity.contains("capturePermissionPolicyApplied"));
        assertTrue(activity.contains("capturePermissionSettingsRequestInFlight"));
        assertTrue(activity.contains("STATE_CAPTURE_PERMISSION_REQUEST_IN_FLIGHT"));
        assertTrue(activity.contains("STATE_CAPTURE_PERMISSION_SETTINGS_IN_FLIGHT"));
        assertTrue(activity.contains("outState.putBoolean(STATE_CAPTURE_PERMISSION_FLOW_STARTED"));
        assertTrue(activity.contains("PERMISSION_TRACE blocker-shown"));
        assertTrue(activity.contains("PERMISSION_TRACE onResume captureGranted=false"));
        assertTrue(activity.contains("prepareCameraProfilesIfCameraGranted(\"startup\")"));
        assertTrue(activity.contains("prepareCameraProfilesIfCameraGranted(\"settings-result\")"));
        assertTrue(activity.contains("if (!androidRuntime.cameraPermissionGranted())"));
        assertFalse(activity.contains("PERMISSION_TRACE camera-profile-prepare"));
        int profilePrepare = composition.indexOf(
                "public void prepareCameraProfilesIfPermitted()");
        int captureBind = composition.indexOf("public void bindCameraIfPermitted()",
                profilePrepare);
        assertTrue(profilePrepare >= 0);
        assertTrue(captureBind > profilePrepare);
        String profilePreparation = composition.substring(profilePrepare, captureBind);
        assertTrue(profilePreparation.contains("DcamPermissions.cameraGranted(context)"));
        assertFalse(profilePreparation.contains("captureRuntimeGranted"));
        assertTrue(composition.contains("DcamPermissions.captureRuntimeGranted(context)"));
        int commandBlock = composition.indexOf("private boolean cameraCaptureCommandsBlocked()");
        int nextMethod = composition.indexOf("public OptionalInt", commandBlock);
        assertTrue(commandBlock >= 0);
        assertTrue(nextMethod > commandBlock);
        assertTrue(composition.substring(commandBlock, nextMethod).contains(
                "!DcamPermissions.captureRuntimeGranted(context)"));
        assertTrue(composition.contains("androidRuntime::capturePermissionsGranted"));
        assertTrue(audio.contains("recordingStartAllowed"));
        assertTrue(permissions.contains("public static String[] captureRuntime()"));
        assertTrue(permissions.contains("public static boolean cameraGranted(Context context)"));
        assertTrue(permissions.contains("Manifest.permission.CAMERA"));
        assertTrue(permissions.contains("Manifest.permission.RECORD_AUDIO"));
        assertTrue(permissions.contains("Arrays.asList(captureRuntime())"));
    }

    @Test void compositionUsesDeviceIdentityAndBuildCryptoSources() throws IOException {
        String composition = source("app/AppComposition.java");

        assertTrue(composition.contains("deviceSerialNumbers::load"));
        assertTrue(composition.contains("BuildConfig.BODYCAM_CRYPTO_PASSWORD"));
        assertFalse(composition.contains("legacyAccountConfigFile"));
        assertFalse(composition.contains("mediaEncryptionPreferences::mediaEncryptionPassword"));
        assertFalse(composition.contains("DcamConfig"));
        assertFalse(composition.contains("CsonConfigurationSourceImpl"));
    }

    @Test void allFilesAccessGatePrecedesStartupAndIdentityRestore() throws IOException {
        String activity = source("app/MainActivity.java");
        String permissions = source("platform/permission/DcamPermissions.java");

        int storageReceiver = activity.indexOf(
                "ContextCompat.registerReceiver(this, storageMountedReceiver");
        int startup = activity.indexOf("startStartupPermissionFlow();", storageReceiver);
        int gate = activity.indexOf("private void startStartupPermissionFlow()");
        int permissionCheck = activity.indexOf("if (!ensureAllFilesAccess())", gate);
        int defaultHome = activity.indexOf("requestDefaultHomeIfNeeded();", gate);
        int corePermissions = activity.indexOf(
                "applyKioskPolicyThenRequestCorePermissions();", defaultHome);
        int identityPrecheck = activity.indexOf("startDeviceIdentityPrecheck();", defaultHome);
        int restore = activity.indexOf(
                "private void ensureConfigFileAccess(boolean interactionReady)");
        int interactionGuard = activity.indexOf("if (interactionReady)", restore);
        int interactionState = activity.indexOf(
                "identityInteractionReady = true;", interactionGuard);
        int restorePermissionCheck = activity.indexOf(
                "if (!ensureAllFilesAccess())", restore);
        int configuredSerial = activity.indexOf(
                "boolean serialConfigured = deviceSerialNumbers.isConfigured(", restore);
        int restoreError = activity.indexOf(
                "identityRestoreError = serialConfigured ? null : error;", configuredSerial);
        int errorBranch = activity.indexOf("if (identityRestoreError != null)", restoreError);
        int manualDialog = activity.indexOf("showSerialNumberDialogIfNeeded();", errorBranch);

        assertTrue(startup > storageReceiver);
        assertTrue(permissionCheck > gate);
        assertTrue(defaultHome > permissionCheck);
        assertTrue(corePermissions > defaultHome);
        assertTrue(identityPrecheck > corePermissions);
        assertTrue(interactionGuard > restore);
        assertTrue(interactionState > interactionGuard);
        assertTrue(restorePermissionCheck > interactionState);
        assertTrue(configuredSerial > restorePermissionCheck);
        assertTrue(restoreError > configuredSerial);
        assertTrue(errorBranch > restoreError);
        assertTrue(manualDialog > errorBranch);
        assertTrue(activity.contains("Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION"));
        assertTrue(activity.contains("allFilesAccessLauncher = registerForActivityResult"));
        assertTrue(activity.contains("R.string.all_files_access_message"));
        assertTrue(activity.contains("R.string.device_identity_restore_failed_message"));
        assertTrue(activity.contains("STORAGE_TRACE legacy-storage-access-required"));
        assertTrue(activity.contains("legacyStoragePermissionLauncher = registerForActivityResult"));
        assertTrue(activity.contains("legacyStorageSettingsLauncher = registerForActivityResult"));
        assertTrue(activity.contains("Settings.ACTION_APPLICATION_DETAILS_SETTINGS"));
        assertTrue(activity.contains("missingLegacyStoragePermissions()"));
        assertTrue(activity.contains("R.string.legacy_storage_access_open_settings"));
        assertTrue(activity.contains("startDeviceIdentityPrecheck();"));
        assertTrue(activity.contains("capturePermissionsGranted()"));
        assertTrue(permissions.contains("Environment.isExternalStorageManager()"));
        assertTrue(permissions.contains("captureRuntime()"));
        assertTrue(permissions.contains("legacyStorageRuntime()"));
        assertTrue(permissions.contains("Manifest.permission.READ_EXTERNAL_STORAGE"));
        assertTrue(permissions.contains("Manifest.permission.WRITE_EXTERNAL_STORAGE"));
    }

    @Test void loggerStartsBeforeCapabilityLogsAndCompositionLoadsConfigSerial() throws IOException {
        String application = source("app/DcamApplication.java");
        String composition = source("app/AppComposition.java");

        int bootstrap = application.indexOf("AppLogger.bootstrap(this);");
        int identity = application.indexOf("new AndroidDeviceRepositoryImpl(this, getFilesDir()).readInfo();");
        int init = application.indexOf("AppLogger.init(this, deviceInfo);");
        int supervisor = application.indexOf("new LogglyProcessSupervisor(this)");
        int serial = application.indexOf("AppComposition.loadConfiguredDeviceSerial(this);");
        int capability = application.indexOf("AppComposition.startCameraCapabilities(this, AppLogger.get());");
        assertTrue(bootstrap >= 0);
        assertTrue(identity > bootstrap);
        assertTrue(init > identity);
        assertTrue(supervisor > init);
        assertTrue(serial > supervisor);
        assertTrue(capability > serial);

        int earlySerial = composition.indexOf("public static void loadConfiguredDeviceSerial");
        int databaseSerial = composition.indexOf(
                "() -> AppDatabase.get(context).cloudState().deviceIdentity()", earlySerial);
        int csonSerial = composition.indexOf(
                "new FileDeviceSerialNumberStore(storage.configsFile())", earlySerial);
        assertTrue(earlySerial >= 0);
        assertTrue(databaseSerial > earlySerial);
        assertTrue(csonSerial > databaseSerial);
        assertTrue(composition.contains("CompletableFuture.supplyAsync("));
        assertTrue(composition.contains("serialNumbers.isConfigured(databaseSerial)"));

        int load = composition.indexOf("String serialNumber = deviceSerialNumbers.load();");
        int fallback = composition.indexOf(
                "if (!AppLogger.hasDeviceSerial()"
                        + " && deviceSerialNumbers.isConfigured(serialNumber))");
        int set = composition.indexOf("AppLogger.setDeviceSerial(serialNumber);");
        assertTrue(load >= 0);
        assertTrue(fallback > load);
        assertTrue(set > fallback);
        assertFalse(composition.contains("AppLogger.init("));
    }

    @Test void loggerCrashHandlerOwnsFirstAndLastLifecycle() throws IOException {
        String logger = source("platform/logging/app/AppLogger.java");

        int bootstrap = logger.indexOf("public static synchronized void bootstrap");
        int install = logger.indexOf("installCrashHandler();", bootstrap);
        int localFile = logger.indexOf("if (logFile == null)", bootstrap);
        int room = logger.indexOf("if (roomWriter == null)", bootstrap);
        assertTrue(install > bootstrap);
        assertTrue(localFile > install);
        assertTrue(room > localFile);
        int recordCrash = logger.indexOf("recordCrash(thread, error);");
        int finallyBlock = logger.indexOf("} finally {", recordCrash);
        int previousGuard = logger.indexOf("if (previous != null)", finallyBlock);
        int previousHandler = logger.indexOf(
                "previous.uncaughtException(thread, error);", previousGuard);
        assertTrue(recordCrash >= 0);
        assertTrue(finallyBlock > recordCrash);
        assertTrue(previousGuard > finallyBlock);
        assertTrue(previousHandler > previousGuard);
        assertTrue(logger.contains("catch (Throwable error)"));
    }

    @Test void identityPersistenceRunsOffMainThreadAndRefreshesInPlace() throws IOException {
        String activity = source("app/MainActivity.java");
        String composition = source("app/AppComposition.java");

        int check = activity.indexOf("private void ensureConfigFileAccess(boolean interactionReady)");
        int executor = activity.indexOf("identityIoExecutor.execute", check);
        int restore = activity.indexOf("deviceSerialNumbers.restoreIfAvailable()", executor);
        int dialog = activity.indexOf("showSerialNumberDialogIfNeeded();", restore);
        assertTrue(check >= 0);
        assertTrue(executor > check);
        assertTrue(restore > executor);
        assertTrue(dialog > restore);
        assertTrue(activity.contains("input.setEnabled(false);"));
        assertTrue(activity.contains("identityIoExecutor.shutdown();"));
        assertTrue(activity.contains("ensureConfigFileAccess(false);"));
        int interactionGuard = activity.indexOf("if (interactionReady)", check);
        int interactionState = activity.indexOf(
                "identityInteractionReady = true;", interactionGuard);
        assertTrue(interactionGuard > check);
        assertTrue(interactionState > interactionGuard);
        assertTrue(activity.contains("if (!identityInteractionReady"));
        assertTrue(activity.contains("STATE_DEVICE_IDENTITY_PENDING"));
        assertTrue(activity.contains("&& !identityPending;"));
        assertTrue(activity.contains("outState.putBoolean(STATE_DEVICE_IDENTITY_PENDING"));
        int applyIdentity = activity.indexOf("private void applyDeviceIdentityUpdate()");
        int nextMethod = activity.indexOf("private void showSerialNumberDialogIfNeeded()", applyIdentity);
        assertTrue(applyIdentity >= 0);
        assertTrue(nextMethod > applyIdentity);
        String identityUpdate = activity.substring(applyIdentity, nextMethod);
        assertTrue(identityUpdate.contains("AppComposition.refreshDeviceIdentity();"));
        assertTrue(identityUpdate.contains("updateCameraIdentity();"));
        assertFalse(identityUpdate.contains("getViewModelStore().clear();"));
        assertFalse(identityUpdate.contains("recreate();"));
        assertFalse(activity.contains("resetCompositionAndRecreate();"));
        assertTrue(composition.contains("public static synchronized void refreshDeviceIdentity()"));
        assertTrue(composition.contains("AppLogger.setDeviceSerial(current.deviceSerialNumbers.load())"));
        assertFalse(composition.contains("instance = null;"));
    }

    @Test void sdCardUnavailableKeepsExactLocalizedNotice() throws IOException {
        String activity = source("app/MainActivity.java");
        String english = resource("values/strings.xml");
        String vietnamese = resource("values-vi/strings.xml");
        int exact = activity.indexOf(
                "message.equals(\"Storage failed: \" + getString(R.string.sd_card_unavailable))");
        int exactNotice = activity.indexOf("R.string.sd_card_unavailable", exact);
        int generic = activity.indexOf("message.startsWith(\"Storage failed:\")", exact);

        assertTrue(exact >= 0);
        assertTrue(exactNotice > exact);
        assertTrue(generic > exactNotice);
        assertTrue(english.contains("<string name=\"sd_card_unavailable\">SD card unavailable.</string>"));
        assertTrue(vietnamese.contains(
                "<string name=\"sd_card_unavailable\">Thẻ SD không khả dụng.</string>"));
    }

    @Test void screenOffCameraReleaseWaitsOneMinuteAndCaptureRestartsDelay()
            throws IOException {
        String activity = source("app/MainActivity.java");
        int screenReceiverStart = activity.indexOf(
                "private final BroadcastReceiver screenStateReceiver");
        int screenReceiverEnd = activity.indexOf(
                "private final BroadcastReceiver firmwareHardwareButtonReceiver",
                screenReceiverStart);
        String screenReceiver = activity.substring(screenReceiverStart, screenReceiverEnd);
        int schedulingStart = activity.indexOf(
                "private void releaseIdleCameraIfScreenOffNow()");
        int schedulingEnd = activity.indexOf(
                "protected void attachBaseContext", schedulingStart);
        String scheduling = activity.substring(schedulingStart, schedulingEnd);
        int hardwareDownStart = activity.indexOf("private boolean handleHardwareKeyDown");
        int hardwareUpStart = activity.indexOf(
                "private boolean handleHardwareKeyUp", hardwareDownStart);
        int firmwareDownStart = activity.indexOf(
                "private boolean handleFirmwareBroadcastDown", hardwareUpStart);
        int firmwareUpStart = activity.indexOf(
                "private boolean handleFirmwareBroadcastUp", firmwareDownStart);
        int receiverRefreshStart = activity.indexOf(
                "private void refreshFirmwareHardwareButtonReceiver", firmwareUpStart);

        assertTrue(activity.contains(
                "private static final long SCREEN_OFF_CAMERA_RELEASE_DELAY_MS = 60_000L;"));
        assertTrue(activity.contains("private long idleScreenOffCameraReleaseAtMillis;"));
        assertTrue(screenReceiver.contains("restartIdleCameraReleaseDelayIfScreenOff();"));
        assertTrue(screenReceiver.contains("cancelIdleCameraRelease();"));
        assertTrue(scheduling.contains(
                "SystemClock.uptimeMillis() + SCREEN_OFF_CAMERA_RELEASE_DELAY_MS"));
        assertTrue(scheduling.contains(
                "cameraClock.postAtTime(idleScreenOffCameraRelease,"));
        assertTrue(scheduling.contains("idleScreenOffCameraReleaseAtMillis = 0L;"));
        assertTrue(activity.substring(hardwareDownStart, hardwareUpStart).contains(
                "restartIdleCameraReleaseDelayAfterHardwareCommandIfScreenOff();"));
        assertTrue(activity.substring(hardwareUpStart, firmwareDownStart).contains(
                "restartIdleCameraReleaseDelayAfterHardwareCommandIfScreenOff();"));
        assertTrue(activity.substring(firmwareDownStart, firmwareUpStart).contains(
                "restartIdleCameraReleaseDelayAfterHardwareCommandIfScreenOff();"));
        assertTrue(activity.substring(firmwareUpStart, receiverRefreshStart).contains(
                "restartIdleCameraReleaseDelayAfterHardwareCommandIfScreenOff();"));
    }
    @Test void devModeIdleCameraReleaseIsDefaultOnAndCoordinatorAware() throws IOException {
        String activity = source("app/MainActivity.java");
        String composition = source("app/AppComposition.java");
        String coordinator = source("app/ui/camera/CameraFlowCoordinator.java");
        String store = source(
                "platform/device/capability/settings/SharedPreferencesDeveloperSettingsStore.java");
        String renderer = source("app/ui/settings/SettingsControlRenderer.java");
        String english = resource("values/strings.xml");
        String vietnamese = resource("values-vi/strings.xml");

        assertTrue(store.contains("releaseCameraWhenScreenOff()"));
        assertTrue(store.contains(".orElse(true)"));
        assertTrue(activity.contains("SettingId.DEV_RELEASE_CAMERA_WHEN_SCREEN_OFF"));
        assertTrue(activity.contains("R.string.release_camera_when_screen_off_description"));
        assertTrue(activity.contains("Intent.ACTION_SCREEN_OFF"));
        assertTrue(activity.contains("Intent.ACTION_SCREEN_ON"));
        assertTrue(activity.contains("screenOff = !androidRuntime.isScreenInteractive()"));
        assertTrue(activity.contains("composition.bindCameraIfPermitted();"));
        assertTrue(renderer.contains("item.getDescription()"));
        assertTrue(composition.contains("recordingCoordinator.currentMode() != RecordingMode.IDLE"));
        assertTrue(composition.contains("recordingCoordinator.hasPhotoWork()"));
        assertTrue(composition.contains("audioRecording.hasPendingWork()"));
        assertTrue(composition.contains("cameraFlow.releaseIdleCamera"));
        assertTrue(composition.contains("observeCameraStateChanges"));
        assertTrue(composition.contains("audioRecording.observeStateChanges(checked)"));
        assertTrue(composition.contains("releasedCameraRecoveryPending"));
        assertTrue(composition.contains("RecordingForegroundService.releaseCameraReady(context)"));
        assertTrue(coordinator.contains("runWhenReleasedCameraReady"));
        assertTrue(coordinator.contains("releaseWithoutTimeout"));
        assertTrue(coordinator.contains("if (!ready) start(true);"));
        assertTrue(coordinator.contains("Transition.BIND_COMMITTED"));
        assertTrue(english.contains(
                "<string name=\"release_camera_when_screen_off\">Release camera when screen is off</string>"));
        assertTrue(english.contains("Saves battery, but increases off-screen photo and video capture delay."));
        assertTrue(vietnamese.contains(
                "<string name=\"release_camera_when_screen_off\">Đóng camera khi màn hình tắt</string>"));
    }

    @Test void firmwareButtonBindingsStayExplicitAndWorkAcrossDisplayStates()
            throws IOException {
        String activity = source("app/MainActivity.java");
        String router = source("app/ui/input/HardwareButtonRouter.java");
        String layout = coreSource("input/domain/HardwareButtonLayout.java");
        String screen = source("app/ui/settings/DeveloperButtonBindingsScreen.java");
        String settingsRenderer = source("app/ui/settings/SettingsControlRenderer.java");
        String preferences = source(
                "platform/input/SharedPreferencesHardwareButtonSettings.java");

        assertTrue(screen.contains("\"Input type\""));
        assertTrue(screen.contains("\"Input source\""));
        assertTrue(preferences.contains("\"Firmware broadcast\""));
        assertTrue(screen.contains("\"Works on compatible vendor firmware.\""));
        assertTrue(preferences.contains("\"Key event\""));
        assertTrue(screen.contains("\"Unavailable while screen is off.\""));
        assertTrue(screen.contains("\"Action family\""));
        assertTrue(screen.contains("\"Keycode\""));
        assertFalse(screen.contains(".withEnabled(firmwareBroadcast)"));
        assertFalse(screen.contains(".withEnabled(!firmwareBroadcast)"));
        assertTrue(screen.contains(".withNestedChoice(SettingItem.choice("));
        int radioTextStart = settingsRenderer.lastIndexOf(
                "button.setText(describedRadioText(state))");
        int nestedChoiceStart = settingsRenderer.indexOf(
                "choice(group, nestedChoice.getLabel(), null");
        assertTrue(radioTextStart >= 0);
        assertTrue(nestedChoiceStart > radioTextStart);
        assertFalse(settingsRenderer.contains(
                "TextView description = value(state.getDescription())"));
        assertFalse(settingsRenderer.contains(
                "if (!row.isEnabled() || !view.isEnabled()"));
        assertFalse(settingsRenderer.contains(
                "if (!row.isEnabled() || !selectedValue.isEnabled()) return;"));

        int receiverStart = activity.indexOf(
                "private final BroadcastReceiver firmwareHardwareButtonReceiver");
        int receiverEnd = activity.indexOf("    };", receiverStart) + "    };".length();
        String receiver = activity.substring(receiverStart, receiverEnd);
        assertTrue(receiver.contains(
                "renderedScreen == MainScreen.DEVELOPER_BUTTON_BINDINGS"));
        assertTrue(receiver.contains("hardwareButtons.clearTransientState();"));
        assertTrue(receiver.contains("handleFirmwareBroadcastDown(action"));
        assertFalse(receiver.contains("PowerManager"));
        assertFalse(receiver.contains("isInteractive()"));
        assertTrue(activity.contains("ContextCompat.RECEIVER_NOT_EXPORTED"));
        assertTrue(activity.contains("refreshFirmwareHardwareButtonReceiver();"));
        assertTrue(layout.contains(
                "binding.inputSource() == HardwareButtonInputSource.KEY_EVENT"));
        assertTrue(layout.contains(
                "binding.inputSource() != HardwareButtonInputSource.FIRMWARE_BROADCAST"));
        assertTrue(router.contains("onFirmwareBroadcastDown"));
        assertFalse(activity.contains("screenOffHardwareButton"));
        assertFalse(activity.contains("inputAlias"));
        assertFalse(router.contains("InputAlias"));
        assertFalse(layout.contains("InputAlias"));
        assertFalse(activity.contains("ScreenOffHardwareButtonEvent"));
    }

    private static String resource(String relative) throws IOException {
        Path root = existingPath(Path.of("app/src/main/res"), Path.of("src/main/res"));
        return Files.readString(root.resolve(relative), StandardCharsets.UTF_8);
    }
    private static String source(String relative) throws IOException {
        Path root = existingPath(Path.of("app/src/main/java"), Path.of("src/main/java"));
        return Files.readString(root.resolve("com/dvid/dcam").resolve(relative),
                StandardCharsets.UTF_8).replace("\r\n", "\n");
    }

    private static String coreSource(String relative) throws IOException {
        Path root = existingPath(
                Path.of("core/src/main/java"), Path.of("../core/src/main/java"));
        return Files.readString(root.resolve("com/dvid/dcam/core").resolve(relative),
                StandardCharsets.UTF_8);
    }

    private static Path existingPath(Path... candidates) {
        for (Path candidate : candidates) {
            if (Files.exists(candidate)) return candidate.normalize();
        }
        throw new IllegalStateException("Source root not found: " + List.of(candidates));
    }
}
