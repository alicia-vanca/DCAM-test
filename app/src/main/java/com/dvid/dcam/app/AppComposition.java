package com.dvid.dcam.app;

import com.dvid.dcam.feature.settings.application.usecase.LanguageSettingsUseCase;
import com.dvid.dcam.feature.settings.application.usecase.MediaEncryptionSettingsUseCase;
import android.app.Activity;
import android.content.Context;
import android.os.Handler;
import android.os.PowerManager;
import android.view.View;
import androidx.activity.ComponentActivity;
import androidx.core.content.ContextCompat;
import com.dvid.dcam.BuildConfig;
import com.dvid.dcam.BuildSecrets;
import com.dvid.dcam.R;
import com.dvid.dcam.app.devmode.DeveloperFeatureToggles;
import com.dvid.dcam.app.ui.FloatingNotice;
import com.dvid.dcam.app.ui.camera.CameraFlowCoordinator;
import com.dvid.dcam.app.ui.settings.camera.CameraResolutionOption;
import com.dvid.dcam.app.ui.settings.camera.CameraSelection;
import com.dvid.dcam.app.ui.settings.camera.CameraSettingControlId;
import com.dvid.dcam.app.ui.settings.camera.CameraSettingsCamera;
import com.dvid.dcam.app.ui.settings.camera.CameraSettingsPresentationState;
import com.dvid.dcam.app.ui.settings.camera.CameraSettingsSource;
import com.dvid.dcam.app.ui.settings.camera.CameraSettingsStatus;
import com.dvid.dcam.app.ui.settings.camera.CameraVideoOption;
import com.dvid.dcam.core.featuregate.domain.FeatureGate;
import com.dvid.dcam.core.featuregate.application.FeatureGates;
import com.dvid.dcam.core.logging.application.port.Logger;
import com.dvid.dcam.feature.capture.application.port.AudioPreparationEvents;
import com.dvid.dcam.feature.capture.application.port.AudioRecorder;
import com.dvid.dcam.feature.auth.application.port.BootIdentitySource;
import com.dvid.dcam.feature.auth.application.port.OperatorAuthRepository;
import com.dvid.dcam.feature.auth.application.repository.OperatorSessionMemory;
import com.dvid.dcam.feature.auth.application.usecase.AuthenticateOperatorUseCase;
import com.dvid.dcam.feature.auth.application.usecase.ManageOperatorUsersUseCase;
import com.dvid.dcam.feature.auth.application.usecase.OperatorSessionUseCase;
import com.dvid.dcam.feature.auth.domain.OperatorSession;
import com.dvid.dcam.feature.capture.application.usecase.AudioPreparationNotifier;
import com.dvid.dcam.feature.capture.application.usecase.AudioRecordingUseCase;
import com.dvid.dcam.feature.capture.application.usecase.CaptureStorageNoticeMonitor;
import com.dvid.dcam.feature.capture.application.usecase.CaptureEvents;
import com.dvid.dcam.feature.capture.application.usecase.PhotoCaptureUseCase;
import com.dvid.dcam.feature.capture.application.usecase.SerializedRecordingCoordinator;
import com.dvid.dcam.feature.capture.application.usecase.RecordingCommands;
import com.dvid.dcam.feature.capture.domain.AudioFileFormat;
import com.dvid.dcam.feature.capture.domain.RecordingMode;
import com.dvid.dcam.feature.device.application.port.DeviceRepository;
import com.dvid.dcam.feature.device.application.port.DeveloperSettingsStore;
import com.dvid.dcam.feature.device.application.usecase.RefreshDeviceStatusUseCase;
import com.dvid.dcam.feature.device.application.usecase.FinalizeVerifiedCameraCapabilitiesUseCase.FailedTuple;
import com.dvid.dcam.feature.device.application.usecase.FinalizeVerifiedCameraCapabilitiesUseCase.Summary;
import com.dvid.dcam.feature.device.application.usecase.DeviceSerialNumberUseCase;
import com.dvid.dcam.feature.device.application.usecase.ResolveCameraRuntimeSelectionUseCase;
import com.dvid.dcam.feature.device.application.usecase.SelectCameraPipelineUseCase;
import com.dvid.dcam.core.device.domain.DeviceInfo;
import com.dvid.dcam.feature.device.domain.CaptureQuality;
import com.dvid.dcam.feature.device.domain.DeviceStatus;
import com.dvid.dcam.feature.device.domain.camera.CameraRuntimeState;
import com.dvid.dcam.feature.device.domain.camera.CandidateKey;
import com.dvid.dcam.feature.device.domain.camera.VerificationPipelineId;
import com.dvid.dcam.feature.media.application.port.MediaRepository;
import com.dvid.dcam.feature.media.application.usecase.BrowseMediaUseCase;
import com.dvid.dcam.feature.media.application.usecase.OpenMediaUseCase;
import com.dvid.dcam.feature.storage.application.usecase.StorageSettingsUseCase;
import com.dvid.dcam.feature.storage.application.port.MediaPartitionLocationPreferenceStore;
import com.dvid.dcam.feature.storage.domain.CaptureStorageCapacityPolicy;
import com.dvid.dcam.feature.storage.domain.CaptureStorageCheck;
import com.dvid.dcam.feature.storage.domain.StorageMode;
import com.dvid.dcam.feature.storage.domain.StorageRecoveryResult;
import com.dvid.dcam.feature.storage.domain.StorageVolumeStatus;
import com.dvid.dcam.feature.storage.domain.StorageWarningStatus;
import com.dvid.dcam.feature.storage.domain.MediaPartitionLocation;
import com.dvid.dcam.feature.location.application.port.GpsSettingsStore;
import com.dvid.dcam.feature.location.application.usecase.LocationControlUseCase;
import com.dvid.dcam.feature.location.application.usecase.LocationSettingsUseCase;
import com.dvid.dcam.feature.location.application.usecase.LocationTrackingUseCase;
import com.dvid.dcam.platform.audio.AndroidAudioRecorderImpl;
import com.dvid.dcam.platform.auth.AndroidBootIdentitySourceImpl;
import com.dvid.dcam.platform.auth.RoomOperatorAuthRepositoryImpl;
import com.dvid.dcam.platform.camera.shared.SharedCameraGateway;
import com.dvid.dcam.platform.camera.shared.CameraPipelineIds;
import com.dvid.dcam.platform.camera.shared.SharedCameraGatewayFactory;
import com.dvid.dcam.platform.camera.shared.SharedCameraPreviewView;
import com.dvid.dcam.platform.camera.shared.runtime.AndroidCameraAvailabilityMonitor;
import com.dvid.dcam.platform.camera.shared.runtime.CameraRuntimeSelection;
import com.dvid.dcam.platform.camera.shared.runtime.ProcessCameraRuntimeBackend;
import com.dvid.dcam.platform.camera.shared.runtime.ProcessCameraRuntimeOwner;
import com.dvid.dcam.platform.config.AndroidLanguagePreferenceStoreImpl;
import com.dvid.dcam.platform.config.AndroidMediaEncryptionPreferenceStoreImpl;
import com.dvid.dcam.platform.config.AndroidMediaPartitionLocationPreferenceStoreImpl;
import com.dvid.dcam.platform.config.AndroidStorageWarningPreferenceStoreImpl;
import com.dvid.dcam.platform.database.AppDatabase;
import com.dvid.dcam.platform.permission.DcamPermissions;
import com.dvid.dcam.platform.device.AndroidDeviceRepositoryImpl;
import com.dvid.dcam.platform.device.AndroidDeviceSettings;
import com.dvid.dcam.platform.device.capability.CameraCapabilityService;
import com.dvid.dcam.platform.device.capability.RequestedCameraSelectionStore;
import com.dvid.dcam.platform.device.capability.store.AtomicCameraCapabilityStore;
import com.dvid.dcam.platform.device.capability.settings.SharedPreferencesDeveloperSettingsStore;
import com.dvid.dcam.platform.device.DcamKioskController;
import com.dvid.dcam.platform.featuregate.AndroidFeatureGateStore;
import com.dvid.dcam.platform.input.AndroidHardwareDeviceIdentity;
import com.dvid.dcam.platform.input.SharedPreferencesHardwareButtonSettings;
import com.dvid.dcam.core.input.domain.HardwareButtonLayout;
import com.dvid.dcam.platform.input.HardwareButtonProfiles;
import com.dvid.dcam.app.ui.input.HardwareButtonRouter;
import com.dvid.dcam.platform.logging.app.AppLogger;
import com.dvid.dcam.platform.recording.RecordingForegroundService;
import com.dvid.dcam.feature.input.application.usecase.ConfigureHardwareButtonsUseCase;
import com.dvid.dcam.platform.location.AndroidLocationControlGatewayImpl;
import com.dvid.dcam.platform.location.AndroidLocationProviderCapabilities;
import com.dvid.dcam.platform.location.AndroidLocationSourceImpl;
import com.dvid.dcam.platform.location.OperationalGpsSettingsStoreImpl;
import com.dvid.dcam.platform.storage.AndroidMediaOpener;
import com.dvid.dcam.platform.storage.DcamMediaOutput;
import com.dvid.dcam.platform.storage.DcamMediaOutputImpl;
import com.dvid.dcam.platform.storage.AndroidStorageCapacitySourceImpl;
import com.dvid.dcam.platform.storage.DcamStorage;
import com.dvid.dcam.platform.storage.LocalMediaRepository;
import com.dvid.dcam.platform.storage.StagedMediaRecoveryReport;
import com.dvid.dcam.platform.config.FileDeviceSerialNumberStore;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.Objects;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Application composition root. This is the only place that selects concrete
 * adapters.
 */
public final class AppComposition {
    // ponytail: Pre-record stays disabled until SharedAvcEncoder gains disk-backed
    // GOP retention.
    private static final long PRE_RECORD_GOP_DURATION_MILLIS = 0L;

    public enum CameraCapabilityRecheckStatus {
        RUNNING, SUCCEEDED, FAILED
    }

    public record CameraPipelineSelection(String cameraId, String pipelineId, int tupleCount) {
        public CameraPipelineSelection {
            if (cameraId == null || cameraId.isBlank()
                    || pipelineId == null || pipelineId.isBlank() || tupleCount < 0) {
                throw new IllegalArgumentException("invalid camera pipeline selection");
            }
        }
    }

    public record CameraCapabilityRecheckFailure(String profile, String cameraId,
            String tuple, String reason) {
        public CameraCapabilityRecheckFailure {
            if (profile == null || profile.isBlank() || cameraId == null || cameraId.isBlank()
                    || tuple == null || tuple.isBlank() || reason == null || reason.isBlank()) {
                throw new IllegalArgumentException("invalid capability failure");
            }
        }
    }

    public record CameraCapabilityRecheckSummary(int pipelineATupleCount,
            int pipelineBTupleCount, long pipelineAElapsedMillis, long pipelineBElapsedMillis,
            List<CameraCapabilityRecheckFailure> failures) {
        public CameraCapabilityRecheckSummary {
            failures = List.copyOf(Objects.requireNonNull(failures, "failures"));
            if (pipelineATupleCount < 0 || pipelineBTupleCount < 0
                    || pipelineAElapsedMillis < 0 || pipelineBElapsedMillis < 0) {
                throw new IllegalArgumentException("invalid capability summary");
            }
        }
    }

    public record CameraCapabilityRecheckProgress(String profile, String cameraId,
            String stage, int completed, int total, String detail) {
        public CameraCapabilityRecheckProgress {
            profile = Objects.requireNonNull(profile, "profile");
            cameraId = Objects.requireNonNull(cameraId, "cameraId");
            stage = Objects.requireNonNull(stage, "stage");
            detail = Objects.requireNonNull(detail, "detail");
            if (profile.isBlank() || cameraId.isBlank() || stage.isBlank()
                    || detail.isBlank()) {
                throw new IllegalArgumentException("progress text is required");
            }
            if (completed < 0 || total <= 0 || completed > total) {
                throw new IllegalArgumentException("invalid progress range");
            }
        }
    }

    public record CameraCapabilityRecheckUpdate(
            CameraCapabilityRecheckStatus status,
            OptionalInt pipelineATupleCount,
            OptionalInt pipelineBTupleCount,
            OptionalLong pipelineAElapsedMillis,
            OptionalLong pipelineBElapsedMillis,
            Optional<CameraCapabilityRecheckProgress> progress,
            Optional<CameraCapabilityRecheckSummary> summary,
            String detail) {
        public CameraCapabilityRecheckUpdate {
            status = Objects.requireNonNull(status, "status");
            pipelineATupleCount = Objects.requireNonNull(
                    pipelineATupleCount, "pipelineATupleCount");
            pipelineBTupleCount = Objects.requireNonNull(
                    pipelineBTupleCount, "pipelineBTupleCount");
            pipelineAElapsedMillis = Objects.requireNonNull(
                    pipelineAElapsedMillis, "pipelineAElapsedMillis");
            pipelineBElapsedMillis = Objects.requireNonNull(
                    pipelineBElapsedMillis, "pipelineBElapsedMillis");
            progress = Objects.requireNonNull(progress, "progress");
            summary = Objects.requireNonNull(summary, "summary");
            if (detail == null || detail.isBlank()) {
                throw new IllegalArgumentException("detail is required");
            }
        }

        public CameraCapabilityRecheckUpdate(CameraCapabilityRecheckStatus status,
                OptionalInt pipelineATupleCount, OptionalInt pipelineBTupleCount,
                OptionalLong pipelineAElapsedMillis, OptionalLong pipelineBElapsedMillis,
                Optional<CameraCapabilityRecheckProgress> progress, String detail) {
            this(status, pipelineATupleCount, pipelineBTupleCount, pipelineAElapsedMillis,
                    pipelineBElapsedMillis, progress, Optional.empty(), detail);
        }
    }

    public interface CameraCapabilityRecheckObserver {
        boolean onUpdate(CameraCapabilityRecheckUpdate update);
    }

    public interface CameraCapabilityRecheckSubscription {
        void close();
    }

    private static AppComposition instance;
    private static CameraCapabilityService cameraCapabilityService;
    private final Context context;
    private final DeviceStatus initialDeviceStatus;
    private final DcamStorage storage;
    private final DcamMediaOutput mediaOutput;
    private volatile long cachedMinimumRecordingStartFreeBytes =
            CaptureStorageCapacityPolicy.MIN_CAPTURE_FREE_BYTES;
    private long cachedMinimumRecordingStartGeneration = -1L;
    private final Logger logger;
    private final RefreshDeviceStatusUseCase refreshDeviceStatus;
    private final BrowseMediaUseCase browseMedia;
    private final LanguageSettingsUseCase languageSettings;
    private final FeatureGates featureGates;
    private final DeveloperFeatureToggles developerFeatureToggles;
    private final MediaEncryptionSettingsUseCase mediaEncryptionSettings;
    private final StorageSettingsUseCase storageSettings;
    private final LocationSettingsUseCase locationSettings;
    private final LocationControlUseCase locationControl;
    private final LocationTrackingUseCase locationTracking;
    private final AuthenticateOperatorUseCase authenticateOperator;
    private final OperatorSessionUseCase operatorSession;
    private final ManageOperatorUsersUseCase manageUsers;
    private final SerializedRecordingCoordinator recordingCoordinator;
    private final SharedCameraGateway recordingCamera;
    private final SharedCameraGatewayFactory.Components sharedCameraComponents;
    private final CameraCapabilityService cameraCapabilities;
    private final DeveloperSettingsStore cameraPipelineSettings;
    private final CameraPipelineModeController cameraPipelineModes;
    private final CameraFlowCoordinator cameraFlow;
    private final CameraCapabilityRecheckNotifier cameraCapabilityRecheckNotifier;
    private final Handler cameraCapabilityReleaseHandler;
    private final Handler captureStorageNoticeHandler;
    private final AtomicReference<CameraCapabilityRecheckUpdate> activeCameraCapabilityRecheckUpdate = new AtomicReference<>();
    private final AudioRecorder audioRecorder;
    private volatile AudioFileFormat audioFileFormat = AudioFileFormat.DEFAULT;
    private final AudioPreparationNotifier audioPreparationNotifier;
    private final CaptureStorageNoticeMonitor captureStorageNoticeMonitor;
    private final AudioRecordingUseCase audioRecording;
    private final ExecutorService captureIoExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "dcam-capture-io");
        thread.setDaemon(true);
        return thread;
    });

    private volatile HardwareButtonLayout hardwareButtonLayout;
    private final ConfigureHardwareButtonsUseCase configureHardwareButtons;
    private final AndroidRuntime androidRuntime;
    private final DeviceSerialNumberUseCase deviceSerialNumbers;

    private AppComposition(Context context) {
        this.context = context.getApplicationContext();
        cameraCapabilityReleaseHandler = new Handler(this.context.getMainLooper());
        captureStorageNoticeHandler = new Handler(this.context.getMainLooper());
        cameraCapabilityRecheckNotifier = new CameraCapabilityRecheckNotifier(
                ContextCompat.getMainExecutor(this.context));
        MediaPartitionLocationPreferenceStore partitionPreferences = new AndroidMediaPartitionLocationPreferenceStoreImpl(
                context, "INTERNAL");
        MediaPartitionLocation initialPartition = partitionPreferences.currentMediaPartitionLocation();
        storage = DcamStorage.from(context, StorageMode.from(BuildConfig.DEFAULT_STORAGE_MODE),
                initialPartition);
        storageSettings = new StorageSettingsUseCase(
                partitionPreferences,
                new AndroidStorageCapacitySourceImpl(context),
                new AndroidStorageWarningPreferenceStoreImpl(context), storage, storage);

        DeviceRepository deviceRepository = new AndroidDeviceRepositoryImpl(context, storage::captureRoot);
        DeviceInfo deviceInfo = deviceRepository.readInfo();
        AppDatabase database = AppDatabase.get(context);
        deviceSerialNumbers = new DeviceSerialNumberUseCase(new FileDeviceSerialNumberStore(
                storage.configsFile(), database.cloudState(), deviceInfo.getHardwareId(),
                storage::identityBackupFiles));
        HardwareButtonLayout builtInHardwareButtons = HardwareButtonProfiles
                .resolve(AndroidHardwareDeviceIdentity.read());
        configureHardwareButtons = new ConfigureHardwareButtonsUseCase(
                new SharedPreferencesHardwareButtonSettings(context), builtInHardwareButtons);
        hardwareButtonLayout = configureHardwareButtons.initialize();
        initialDeviceStatus = deviceRepository.readStatus();
        featureGates = new FeatureGates(new AndroidFeatureGateStore(context));
        developerFeatureToggles = DeveloperFeatureToggles.createDefault(featureGates);
        String serialNumber = deviceSerialNumbers.load();
        if (!AppLogger.hasDeviceSerial() && deviceSerialNumbers.isConfigured(serialNumber)) {
            AppLogger.setDeviceSerial(serialNumber);
        }
        logger = AppLogger.get();
        androidRuntime = new AndroidRuntime(context, logger);

        refreshDeviceStatus = new RefreshDeviceStatusUseCase(deviceRepository);
        MediaRepository mediaRepository = new LocalMediaRepository(storage);
        browseMedia = new BrowseMediaUseCase(mediaRepository);
        languageSettings = new LanguageSettingsUseCase(new AndroidLanguagePreferenceStoreImpl(context));
        AndroidMediaEncryptionPreferenceStoreImpl mediaEncryptionPreferences = new AndroidMediaEncryptionPreferenceStoreImpl(
                context);
        String buildCryptoPassword = BuildSecrets.DCAM_CRYPTO_PASSWORD();
        if (buildCryptoPassword == null || buildCryptoPassword.isBlank()) {
            throw new IllegalStateException("Media encryption password is required");
        }
        Supplier<String> mediaEncryptionPassword = () -> buildCryptoPassword;
        mediaEncryptionSettings = new MediaEncryptionSettingsUseCase(mediaEncryptionPreferences,
                () -> featureGates.isEffectivelyEnabled(FeatureGate.MEDIA_ENCRYPTION));
        DcamMediaOutputImpl mediaOutputImpl = new DcamMediaOutputImpl(context, storage,
                () -> developerFeatureToggles.isEffectivelyEnabled(FeatureGate.VIDEO_MD5),
                mediaEncryptionPassword, logger);
        mediaOutput = mediaOutputImpl;
        captureIoExecutor.execute(() -> mediaOutputImpl.recoverStaged(this::handleStagedMediaRecovery));
        AndroidLocationProviderCapabilities locationCapabilities =
                new AndroidLocationProviderCapabilities(context);
        GpsSettingsStore gpsSettingsStore = new OperationalGpsSettingsStoreImpl(context);
        AndroidLocationControlGatewayImpl locationControlGateway =
                new AndroidLocationControlGatewayImpl(context, locationCapabilities);
        locationSettings = new LocationSettingsUseCase(
                gpsSettingsStore, locationControlGateway::isModeAvailable);
        locationControl = new LocationControlUseCase(locationControlGateway, gpsSettingsStore);
        locationTracking = new LocationTrackingUseCase(
                locationSettings, new AndroidLocationSourceImpl(context, locationCapabilities));
        OperatorAuthRepository authRepository = new RoomOperatorAuthRepositoryImpl(database.operatorAuth());
        BootIdentitySource bootIdentity = new AndroidBootIdentitySourceImpl(context);
        OperatorSessionMemory sessionMemory = new OperatorSessionMemory();
        authenticateOperator = new AuthenticateOperatorUseCase(authRepository, bootIdentity, sessionMemory);
        operatorSession = new OperatorSessionUseCase(
                authRepository, bootIdentity, sessionMemory);
        manageUsers = new ManageOperatorUsersUseCase(authRepository);
        Supplier<String> operatorFileUserId = () -> {
            OperatorSession session = operatorSession.current();
            return session == null ? null : session.getFileUserId();
        };
        recordingCoordinator = new SerializedRecordingCoordinator(
                ContextCompat.getMainExecutor(context),
                this::cameraSettingsAllowRecordingStart,
                this::cameraCaptureCommandsBlocked,
                this::prepareReleasedCameraForRecordingStart);
        cameraPipelineSettings = new SharedPreferencesDeveloperSettingsStore(context);
        cameraCapabilities = cameraCapabilities(context, logger);
        configureCameraPipelineSelection(cameraPipelineSettings.mode());
        sharedCameraComponents = SharedCameraGatewayFactory.create(
                context, cameraCapabilities.runtimeOwner(), cameraCapabilities,
                recordingCoordinator, mediaOutput,
                deviceSerialNumbers::load, operatorFileUserId,
                mediaEncryptionSettings::isMediaEncryptionEnabled,
                locationTracking::latestCoordinate,
                PRE_RECORD_GOP_DURATION_MILLIS, logger);
        recordingCamera = sharedCameraComponents.gateway();
        recordingCoordinator.bindCamera(recordingCamera);
        recordingCamera.observeRuntimeState(
                this::refreshCachedMinimumRecordingStartFreeBytes);
        audioPreparationNotifier = new AudioPreparationNotifier();
        captureStorageNoticeMonitor = new CaptureStorageNoticeMonitor(
                this::captureStorageNoticeStatus,
                (action, delayMillis) -> captureStorageNoticeHandler.postDelayed(
                        () -> captureIoExecutor.execute(action), delayMillis),
                audioPreparationNotifier, context.getString(R.string.sd_card_unavailable));
        recordingCamera.setStoragePreparationEvents(captureStorageNoticeMonitor);
        cameraFlow = new CameraFlowCoordinator(createCameraFlowBackend(),
                (action, delayMillis) -> {
                    cameraCapabilityReleaseHandler.postDelayed(action, delayMillis);
                    return () -> cameraCapabilityReleaseHandler.removeCallbacks(action);
                });
        cameraPipelineModes = new CameraPipelineModeController(
                createCameraPipelineModeBackend());

        audioRecorder = new AndroidAudioRecorderImpl(context, mediaOutput, logger,
                mediaEncryptionSettings::isMediaEncryptionEnabled, operatorFileUserId,
                deviceSerialNumbers::load, locationTracking::latestCoordinate,
                () -> audioFileFormat);
        audioRecording = new AudioRecordingUseCase(audioRecorder, captureIoExecutor,
                () -> DcamPermissions.captureRuntimeGranted(this.context)
                        && !captureStorageUnavailable(),
                recordingCoordinator,
                captureStorageNoticeMonitor);
    }

    public static synchronized void reset() {
        // ponytail: full reset needs coordinated process-owner shutdown; keep this API
        // in-place.
        refreshDeviceIdentity();
    }

    public static synchronized void refreshDeviceIdentity() {
        AppComposition current = instance;
        if (current == null)
            return;
        requireCaptureIdle(
                current.recordingCoordinator.currentMode(),
                current.audioRecorder.hasPendingWork(),
                current.recordingCamera.snapshot().inFlight()
                        .orElse(null) == ProcessCameraRuntimeBackend.Operation.CAPTURE_PHOTO);
        AppLogger.setDeviceSerial(current.deviceSerialNumbers.load());
    }

    static void requireCaptureIdle(RecordingMode videoMode, boolean audioRecording) {
        requireCaptureIdle(videoMode, audioRecording, false);
    }

    static void requireCaptureIdle(
            RecordingMode videoMode, boolean audioRecording, boolean photoWork) {
        if (videoMode != RecordingMode.IDLE || audioRecording || photoWork) {
            throw new IllegalStateException("Cannot reset composition while capture is active");
        }
    }

    private void handleStagedMediaRecovery(StagedMediaRecoveryReport report) {
        if (report.hasFailure()) {
            logger.error("Staged media recovery failed", report.getFailure());
        } else {
            logger.info("Staged media recovery completed. Recovered files: "
                    + report.getRecovered() + ". Preserved in Temp: "
                    + report.getPreserved() + ". Duplicates skipped: "
                    + report.getDuplicates() + ".");
        }
        completeInterruptedFinalizationIfRecovered(report);
    }

    private void completeInterruptedFinalizationIfRecovered(StagedMediaRecoveryReport report) {
        String videoFileName = RecordingForegroundService
                .interruptedVideoFinalizationFileName(context);
        if (isInterruptedFinalizationResolved(
                report.isResolved(videoFileName), mediaOutput.hasPublishedFile(videoFileName))) {
            RecordingForegroundService.completeVideoFinalization(context);
        }
        String audioFileName = RecordingForegroundService
                .interruptedAudioFinalizationFileName(context);
        if (isInterruptedFinalizationResolved(
                report.isResolved(audioFileName), mediaOutput.hasPublishedFile(audioFileName))) {
            RecordingForegroundService.completeAudioFinalization(context);
        }
    }

    static boolean isInterruptedFinalizationResolved(boolean recovered, boolean published) {
        return recovered || published;
    }

    public static Context localizedContext(Context context) {
        return AndroidLanguagePreferenceStoreImpl.localizedContext(context);
    }

    public static void loadConfiguredDeviceSerial(Context context) {
        String databaseSerial = "";
        try {
            var identity = CompletableFuture.supplyAsync(
                    () -> AppDatabase.get(context).cloudState().deviceIdentity()).join();
            if (identity != null)
                databaseSerial = identity.serialNumber;
        } catch (RuntimeException error) {
            AppLogger.get().warn(
                    "Could not load device serial from Room before feature startup", error);
        }
        MediaPartitionLocationPreferenceStore partitionPreferences = new AndroidMediaPartitionLocationPreferenceStoreImpl(
                context, "INTERNAL");
        MediaPartitionLocation initialPartition = partitionPreferences.currentMediaPartitionLocation();
        DcamStorage storage = DcamStorage.from(
                context, StorageMode.from(BuildConfig.DEFAULT_STORAGE_MODE), initialPartition);
        DeviceSerialNumberUseCase serialNumbers = new DeviceSerialNumberUseCase(
                new FileDeviceSerialNumberStore(storage.configsFile()));
        if (serialNumbers.isConfigured(databaseSerial)) {
            AppLogger.setDeviceSerial(databaseSerial.trim());
            return;
        }
        String serialNumber = serialNumbers.load();
        if (serialNumbers.isConfigured(serialNumber))
            AppLogger.setDeviceSerial(serialNumber);
    }

    public static synchronized CameraCapabilityService startCameraCapabilities(
            Context context, Logger logger) {
        CameraCapabilityService service = cameraCapabilities(context, logger);
        service.start();
        service.requestStartupFastCollection();
        return service;
    }

    public static synchronized CameraCapabilityService cameraCapabilities(
            Context context, Logger logger) {
        if (cameraCapabilityService != null)
            return cameraCapabilityService;
        Context checked = Objects.requireNonNull(context, "context");
        Context application = checked.getApplicationContext();
        Context applicationContext = application == null ? checked : application;
        Logger checkedLogger = Objects.requireNonNull(logger, "logger");
        ProcessCameraRuntimeOwner runtimeOwner = new ProcessCameraRuntimeOwner(checkedLogger,
                new AndroidCameraAvailabilityMonitor(applicationContext, checkedLogger));
        AtomicCameraCapabilityStore store = new AtomicCameraCapabilityStore(
                new File(applicationContext.getFilesDir(), "camera-capabilities.xml"),
                checkedLogger);
        RequestedCameraSelectionStore selections = new RequestedCameraSelectionStore(applicationContext);
        ResolveCameraRuntimeSelectionUseCase resolver = new ResolveCameraRuntimeSelectionUseCase(
                new SelectCameraPipelineUseCase(List.of(
                        CameraPipelineIds.NATIVE_SURFACE_SHARING,
                        CameraPipelineIds.EGL_FAN_OUT),
                        CameraPipelineIds.NATIVE_SURFACE_SHARING));
        ExecutorService scanExecutor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "dcam-production-capability");
            thread.setDaemon(true);
            return thread;
        });
        cameraCapabilityService = new CameraCapabilityService(applicationContext, checkedLogger,
                store, selections, runtimeOwner, resolver,
                ContextCompat.getMainExecutor(applicationContext), scanExecutor);
        return cameraCapabilityService;
    }

    public static synchronized AppComposition create(Context context) {
        if (instance == null)
            instance = new AppComposition(context.getApplicationContext());
        return instance;
    }

    public Logger logger() {
        return logger;
    }

    public DeviceSerialNumberUseCase deviceSerialNumberUseCase() {
        return deviceSerialNumbers;
    }

    public DeviceStatus initialDeviceStatus() {
        return initialDeviceStatus;
    }

    public RefreshDeviceStatusUseCase refreshDeviceStatusUseCase() {
        return refreshDeviceStatus;
    }

    public BrowseMediaUseCase browseMediaUseCase() {
        return browseMedia;
    }

    public LanguageSettingsUseCase languageSettingsUseCase() {
        return languageSettings;
    }

    public DeveloperFeatureToggles developerFeatureToggles() {
        return developerFeatureToggles;
    }

    public ConfigureHardwareButtonsUseCase configureHardwareButtonsUseCase() {
        return configureHardwareButtons;
    }

    public void updateHardwareButtonLayout(HardwareButtonLayout layout) {
        if (layout == null)
            throw new IllegalArgumentException("layout is required");
        hardwareButtonLayout = layout;
    }

    public MediaEncryptionSettingsUseCase mediaEncryptionSettings() {
        return mediaEncryptionSettings;
    }

    public StorageSettingsUseCase storageSettingsUseCase() {
        return storageSettings;
    }

    public LocationSettingsUseCase locationSettingsUseCase() {
        return locationSettings;
    }

    public LocationControlUseCase locationControlUseCase() {
        return locationControl;
    }

    public LocationTrackingUseCase locationTrackingUseCase() {
        return locationTracking;
    }

    public AndroidRuntime androidRuntime() {
        return androidRuntime;
    }

    public AudioFileFormat audioFileFormat() { return audioFileFormat; }

    public void setAudioFileFormat(AudioFileFormat format) {
        audioFileFormat = Objects.requireNonNull(format, "format");
    }

    public DeveloperSettingsStore.Mode cameraPipelineMode() {
        return activeCameraId().map(cameraPipelineSettings::mode)
                .orElseGet(cameraPipelineSettings::mode);
    }

    public DeveloperSettingsStore.Mode cameraPipelineMode(String cameraId) {
        return cameraPipelineSettings.mode(cameraId);
    }

    public boolean releaseCameraWhenScreenOff() {
        return cameraPipelineSettings.releaseCameraWhenScreenOff();
    }

    public void setReleaseCameraWhenScreenOff(boolean enabled) {
        cameraPipelineSettings.setReleaseCameraWhenScreenOff(enabled);
    }

    public boolean resourceMonitorEnabled() {
        return cameraPipelineSettings.resourceMonitorEnabled();
    }

    public void setResourceMonitorEnabled(boolean enabled) {
        cameraPipelineSettings.setResourceMonitorEnabled(enabled);
    }

    public CameraFlowCoordinator.StateSubscription observeCameraStateChanges(Runnable observer) {
        Runnable checked = Objects.requireNonNull(observer, "observer");
        CameraFlowCoordinator.StateSubscription flowSubscription = cameraFlow.observeStateChanges(checked);
        ProcessCameraRuntimeOwner.Attachment runtimeAttachment = recordingCamera
                .observeRuntimeState(snapshot -> checked.run());
        Runnable audioSubscription = audioRecording.observeStateChanges(checked);
        return () -> {
            audioSubscription.run();
            runtimeAttachment.close();
            flowSubscription.close();
        };
    }

    public List<CameraPipelineSelection> cameraPipelineSelections() {
        return cameraCapabilities.selectedPipelineSelections().stream()
                .map(value -> new CameraPipelineSelection(value.cameraId(), value.pipelineId(),
                        value.tupleCount()))
                .collect(java.util.stream.Collectors.toList());
    }

    public List<CameraPipelineSelection> cameraPipelineAutoSelections() {
        return cameraCapabilities.automaticPipelineSelections().stream()
                .map(value -> new CameraPipelineSelection(value.cameraId(), value.pipelineId(),
                        value.tupleCount()))
                .collect(java.util.stream.Collectors.toList());
    }

    public boolean cameraCapabilitiesFullyVerified() {
        return cameraCapabilities.hasFullyVerifiedCapabilities();
    }

    public List<String> cameraPipelineCameraIds() {
        return cameraCapabilities.cameraIds();
    }

    public OptionalInt cameraPipelineVerifiedTupleCount(String cameraId,
            DeveloperSettingsStore.Mode mode) {
        Optional<VerificationPipelineId> pipeline = forcedPipeline(mode);
        return pipeline.isEmpty() ? OptionalInt.empty()
                : cameraCapabilities.verifiedCaptureTupleCount(cameraId, pipeline.orElseThrow());
    }

    public boolean canSelectCameraPipelineMode() {
        return activeCameraId().map(this::canSelectCameraPipelineMode).orElse(false);
    }

    public boolean canSelectCameraPipelineMode(String cameraId) {
        if (cameraId == null || cameraId.isBlank() || cameraCapabilityRecheckInFlight()
                || cameraRecordingActive(cameraId))
            return false;
        return cameraPipelineModes.canSelect(cameraId);
    }

    private boolean cameraPipelineSelectionInFlight() {
        return cameraPipelineModes != null && cameraPipelineModes.transitionInFlight();
    }

    public boolean cameraPipelineModeControlEnabled() {
        if (cameraPipelineSelectionInFlight())
            return false;
        return cameraRecordingActive() || cameraPipelineModes.canSelect();
    }

    public boolean cameraPipelineModeControlEnabled(String cameraId) {
        return canSelectCameraPipelineMode(cameraId);
    }

    private boolean cameraSettingsAllowRecordingStart() {
        if (cameraFlow == null || cameraPipelineModes == null || recordingCamera == null) {
            return true;
        }
        if (cameraPipelineSelectionInFlight() || cameraFlow.transitionInFlight()
                || cameraFlow.state() != CameraFlowCoordinator.State.READY)
            return false;
        ProcessCameraRuntimeOwner.RuntimeSnapshot runtime = recordingCamera.snapshot();
        return runtime.inFlight().isEmpty() && runtime.state() == CameraRuntimeState.READY;
    }

    private boolean cameraPhotoCaptureAllowed() {
        if (!DcamPermissions.captureRuntimeGranted(context))
            return false;
        if (cameraFlow == null || cameraPipelineModes == null || recordingCamera == null) {
            return true;
        }
        if (cameraPipelineSelectionInFlight() || cameraFlow.transitionInFlight()
                || cameraFlow.state() != CameraFlowCoordinator.State.READY)
            return false;
        ProcessCameraRuntimeOwner.RuntimeSnapshot runtime = recordingCamera.snapshot();
        return runtime.inFlight().isEmpty()
                && (runtime.state() == CameraRuntimeState.READY
                        || runtime.state() == CameraRuntimeState.RECORDING)
                && !captureStorageUnavailable();
    }

    private boolean cameraIdleForScreenOffRelease() {
        if (recordingCoordinator.currentMode() != RecordingMode.IDLE
                || recordingCoordinator.hasPhotoWork()
                || audioRecording.hasPendingWork()
                || cameraPipelineSelectionInFlight()
                || cameraFlow.transitionInFlight()
                || cameraFlow.state() != CameraFlowCoordinator.State.READY)
            return false;
        ProcessCameraRuntimeOwner.RuntimeSnapshot runtime = recordingCamera.snapshot();
        return runtime.state() == CameraRuntimeState.READY
                && runtime.inFlight().isEmpty()
                && !runtime.pendingRecordStart()
                && !runtime.pendingPhoto();
    }

    private void prepareReleasedCameraForRecordingStart() {
        if (cameraFlow != null)
            cameraFlow.prepareReleasedCamera();
    }

    private boolean runPhotoWhenReleasedCameraReady(Runnable action) {
        return cameraFlow != null && cameraFlow.runWhenReleasedCameraReady(action);
    }

    public void refreshCaptureStorageNotice() {
        captureStorageNoticeMonitor.invalidate();
        captureIoExecutor.execute(captureStorageNoticeMonitor::evaluate);
    }

    public void readStorageVolumes(Consumer<List<StorageVolumeStatus>> callback) {
        Consumer<List<StorageVolumeStatus>> checked = Objects.requireNonNull(
                callback, "callback");
        captureIoExecutor.execute(() -> {
            List<StorageVolumeStatus> volumes;
            try {
                volumes = storageSettings.storageVolumes();
            } catch (RuntimeException error) {
                logger.warn("Read storage volumes failed; reporting unavailable volumes.",
                        error);
                volumes = List.of();
            }
            List<StorageVolumeStatus> result = List.copyOf(volumes);
            ContextCompat.getMainExecutor(context).execute(() -> checked.accept(result));
        });
    }

    public void readStorageWarning(Consumer<StorageWarningStatus> callback) {
        Consumer<StorageWarningStatus> checked = Objects.requireNonNull(callback, "callback");
        captureIoExecutor.execute(() -> {
            StorageWarningStatus warning;
            try {
                long minimumRecordingStartFreeBytes =
                        cachedMinimumRecordingStartFreeBytes;
                warning = storageSettings.warningStatus(
                        minimumRecordingStartFreeBytes,
                        recordingCamera.snapshot().state() == CameraRuntimeState.RECORDING);
            } catch (RuntimeException error) {
                logger.warn("Read active storage warning failed; reporting zero free bytes.",
                        error);
                warning = new StorageWarningStatus(true, true, 0L);
            }
            StorageWarningStatus result = warning;
            ContextCompat.getMainExecutor(context).execute(() -> checked.accept(result));
        });
    }

    private CaptureStorageNoticeMonitor.Status captureStorageNoticeStatus() {
        if (!mediaOutput.isExternalStorageRequested()) {
            return CaptureStorageNoticeMonitor.Status.ready();
        }
        return captureStorageNoticeStatus(mediaOutput.checkCaptureWritable(),
                context.getString(R.string.sd_card_preparing),
                context.getString(R.string.sd_card_unavailable));
    }

    static CaptureStorageNoticeMonitor.Status captureStorageNoticeStatus(
            CaptureStorageCheck check, String preparingMessage, String unavailableMessage) {
        if (check.isReady() || check.isLowCapacity()) {
            return CaptureStorageNoticeMonitor.Status.ready();
        }
        return check.isPreparing()
                ? CaptureStorageNoticeMonitor.Status.preparing(preparingMessage)
                : CaptureStorageNoticeMonitor.Status.unavailable(unavailableMessage);
    }


    private boolean cameraCaptureCommandsBlocked() {
        return !DcamPermissions.captureRuntimeGranted(context)
                || cameraCapabilityRecheckInFlight()
                || cameraPipelineSelectionInFlight()
                || cameraFlow != null
                        && cameraFlow.state() == CameraFlowCoordinator.State.UNAVAILABLE
                        && !cameraFlow.releasedCameraRecoveryPending()
                || captureStorageUnavailable();
    }

    private boolean captureStorageUnavailable() {
        return mediaOutput.isExternalStorageRequested()
                && captureStorageNoticeMonitor.isUnavailable();
    }

    private boolean cameraRecordingActive() {
        if (recordingCoordinator.currentMode() != RecordingMode.IDLE)
            return true;
        ProcessCameraRuntimeOwner.RuntimeSnapshot runtime = recordingCamera.snapshot();
        return runtime.state() == CameraRuntimeState.RECORDING
                || runtime.inFlight()
                        .filter(operation -> operation == ProcessCameraRuntimeBackend.Operation.START_RECORDING
                                || operation == ProcessCameraRuntimeBackend.Operation.STOP_RECORDING)
                        .isPresent();
    }

    boolean cameraRecordingActiveForUi() {
        return cameraRecordingActive();
    }

    private boolean cameraRecordingActive(String cameraId) {
        return cameraRecordingActive()
                && activeCameraId().filter(cameraId::equals).isPresent();
    }

    public OptionalInt cameraPipelineCaptureTupleCount(DeveloperSettingsStore.Mode mode) {
        return activeCameraId().map(id -> cameraPipelineCaptureTupleCount(id, mode))
                .orElseGet(OptionalInt::empty);
    }

    public OptionalInt cameraPipelineCaptureTupleCount(String cameraId,
            DeveloperSettingsStore.Mode mode) {
        Optional<VerificationPipelineId> pipeline = forcedPipeline(mode);
        if (pipeline.isEmpty() || cameraId == null || cameraId.isBlank()) {
            return OptionalInt.empty();
        }
        return cameraCapabilities.fastCaptureTupleCount(cameraId, pipeline.orElseThrow());
    }

    public boolean selectCameraPipelineMode(DeveloperSettingsStore.Mode mode,
            Consumer<CameraPipelineModeController.Result> completion) {
        return activeCameraId().map(id -> selectCameraPipelineMode(id, mode, completion))
                .orElse(false);
    }

    public boolean selectCameraPipelineMode(String cameraId, DeveloperSettingsStore.Mode mode,
            Consumer<CameraPipelineModeController.Result> completion) {
        return cameraPipelineModes.select(cameraId, mode, result -> {
            try {
                completion.accept(result);
            } finally {
                recordingCoordinator.resumePendingStart();
            }
        });
    }

    private void persistCameraPipelineSelection(
            String cameraId, DeveloperSettingsStore.Mode mode) {
        DeveloperSettingsStore.Mode previousMode = cameraPipelineSettings.mode(cameraId);
        Optional<VerificationPipelineId> previousPipeline = forcedPipeline(previousMode);
        Optional<VerificationPipelineId> pipeline = forcedPipeline(mode);
        try {
            cameraCapabilities.persistPipelineSelection(cameraId, pipeline);
            cameraPipelineSettings.setMode(cameraId, mode);
            configureCameraPipelineSelection(cameraId, pipeline);
        } catch (RuntimeException error) {
            try {
                cameraPipelineSettings.setMode(cameraId, previousMode);
            } catch (RuntimeException rollbackError) {
                logger.warn("camera_pipeline_selector preference_rollback_failed", rollbackError);
            }
            try {
                cameraCapabilities.persistPipelineSelection(cameraId, previousPipeline);
            } catch (RuntimeException rollbackError) {
                logger.warn("camera_pipeline_selector snapshot_rollback_failed", rollbackError);
            }
            try {
                configureCameraPipelineSelection(cameraId, previousPipeline);
            } catch (RuntimeException rollbackError) {
                logger.warn("camera_pipeline_selector configured_selection_rollback_failed",
                        rollbackError);
            }
            throw error;
        }
    }

    private Optional<String> activeCameraId() {
        return activeCameraCandidate().map(value -> value.cameraId().value())
                .or(() -> {
                    String primary = cameraCapabilities.primaryCameraId();
                    return primary.isBlank() ? Optional.empty() : Optional.of(primary);
                });
    }

    public boolean resetCameraCapabilities() {
        logger.info("camera_capability_recheck request"
                + " source=AppComposition.resetCameraCapabilities");
        if (!cameraCapabilityRecheckControlEnabled())
            return false;
        return cameraFlow.recheckCapabilities(this::handleCameraCapabilityRecheckUpdate);
    }

    public boolean cameraCapabilityRecheckControlEnabled() {
        return !cameraCapabilityRecheckInFlight() && !cameraRecordingActive();
    }

    public void acknowledgeCameraCapabilityRecheck() {
        cameraCapabilityRecheckNotifier.acknowledgeTerminal();
    }

    public boolean cameraCapabilityRecheckInFlight() {
        return cameraFlow != null && cameraFlow.recheckInFlight();
    }

    public CameraCapabilityRecheckSubscription observeCameraCapabilityRecheck(
            CameraCapabilityRecheckObserver observer) {
        CameraCapabilityRecheckNotifier.Subscription subscription = cameraCapabilityRecheckNotifier.observe(observer,
                this::activeCameraCapabilityRecheckUpdate);
        return subscription::close;
    }

    private CameraCapabilityRecheckUpdate activeCameraCapabilityRecheckUpdate() {
        CameraCapabilityRecheckUpdate active = activeCameraCapabilityRecheckUpdate.get();
        if (active != null)
            return active;
        return cameraCapabilityRecheckInFlight()
                ? new CameraCapabilityRecheckUpdate(
                        CameraCapabilityRecheckStatus.RUNNING,
                        OptionalInt.empty(), OptionalInt.empty(),
                        OptionalLong.empty(), OptionalLong.empty(),
                        Optional.of(capabilityRecheckPreparingProgress()),
                        "recheck_in_progress")
                : null;
    }

    private void handleCameraCapabilityRecheckProgress(
            CameraCapabilityService.RecheckProgress progress) {
        logger.info("camera_capability_recheck stage=progress_ui surface=preview_overlay"
                + " profile=" + progress.profile() + " camera=" + progress.cameraId()
                + " completed=" + progress.completed() + " total=" + progress.total());
        recordingCamera.beginCapabilityCheckPreview();
        recordingCamera.updateCapabilityCheckProgress(progress.profile(), progress.cameraId(),
                progress.stage(), progress.completed(), progress.total(), progress.detail());

        CameraCapabilityRecheckProgress visibleProgress = new CameraCapabilityRecheckProgress(progress.profile(),
                progress.cameraId(), progress.stage(), progress.completed(),
                progress.total(), progress.detail());
        CameraCapabilityRecheckUpdate update = new CameraCapabilityRecheckUpdate(
                CameraCapabilityRecheckStatus.RUNNING,
                OptionalInt.empty(), OptionalInt.empty(),
                OptionalLong.empty(), OptionalLong.empty(),
                Optional.of(visibleProgress), "recheck_in_progress");
        activeCameraCapabilityRecheckUpdate.set(update);
        cameraCapabilityRecheckNotifier.publish(update);
    }

    private void handleCameraCapabilityRecheckUpdate(
            CameraFlowCoordinator.RecheckUpdate update) {
        if (update.status() == CameraFlowCoordinator.RecheckStatus.RUNNING) {
            recordingCoordinator.discardPendingStart();
            recordingCamera.beginCapabilityCheckPreview();
        } else {
            activeCameraCapabilityRecheckUpdate.set(null);
            recordingCamera.completeCapabilityCheckPreview(
                    update.status() == CameraFlowCoordinator.RecheckStatus.SUCCEEDED);
        }
        logger.info("camera_capability_recheck stage=progress_ui surface=preview_overlay"
                + " action=terminal status=" + update.status().name());
        CameraCapabilityRecheckStatus status = switch (update.status()) {
            case RUNNING -> CameraCapabilityRecheckStatus.RUNNING;
            case SUCCEEDED -> CameraCapabilityRecheckStatus.SUCCEEDED;
            case FAILED -> CameraCapabilityRecheckStatus.FAILED;
        };
        Optional<Summary> summary = status == CameraCapabilityRecheckStatus.SUCCEEDED
                ? cameraCapabilities.lastRecheckSummary()
                : Optional.empty();
        Optional<CameraCapabilityRecheckSummary> visibleSummary = summary.map(
                this::cameraCapabilityRecheckSummary);
        Optional<CameraCapabilityRecheckProgress> progress = status == CameraCapabilityRecheckStatus.RUNNING
                ? Optional.of(capabilityRecheckPreparingProgress())
                : Optional.empty();
        CameraCapabilityRecheckUpdate visibleUpdate = new CameraCapabilityRecheckUpdate(
                status,
                summary.isPresent() ? OptionalInt.of(
                        summary.orElseThrow().pipelineATupleCount()) : OptionalInt.empty(),
                summary.isPresent() ? OptionalInt.of(
                        summary.orElseThrow().pipelineBTupleCount()) : OptionalInt.empty(),
                summary.isPresent() ? OptionalLong.of(
                        summary.orElseThrow().pipelineAElapsedMillis()) : OptionalLong.empty(),
                summary.isPresent() ? OptionalLong.of(
                        summary.orElseThrow().pipelineBElapsedMillis()) : OptionalLong.empty(),
                progress, visibleSummary, update.detail());
        if (status == CameraCapabilityRecheckStatus.RUNNING) {
            activeCameraCapabilityRecheckUpdate.set(visibleUpdate);
        }
        cameraCapabilityRecheckNotifier.publish(visibleUpdate);
    }

    private static CameraCapabilityRecheckProgress capabilityRecheckPreparingProgress() {
        return new CameraCapabilityRecheckProgress(
                "A/B", "all", "preparing", 0, 1, "recheck_started");
    }

    private CameraCapabilityRecheckSummary cameraCapabilityRecheckSummary(Summary summary) {
        List<CameraCapabilityRecheckFailure> failures = new java.util.ArrayList<>();
        appendFailures(failures, "A", summary.pipelineAFailedTuples());
        appendFailures(failures, "B", summary.pipelineBFailedTuples());
        return new CameraCapabilityRecheckSummary(summary.pipelineATupleCount(),
                summary.pipelineBTupleCount(), summary.pipelineAElapsedMillis(),
                summary.pipelineBElapsedMillis(), failures);
    }

    private static void appendFailures(List<CameraCapabilityRecheckFailure> target,
            String profile, List<FailedTuple> failures) {
        for (FailedTuple failure : failures) {
            target.add(new CameraCapabilityRecheckFailure(profile, failure.cameraId(),
                    failure.tuple().toString(), failure.reason()));
        }
    }

    public void prepareCameraProfilesIfPermitted() {
        if (DcamPermissions.cameraGranted(context)) {
            keepCameraReadyIfPermitted();
            cameraFlow.bindIfNeeded();
        }
    }

    public void bindCameraIfPermitted() {
        if (DcamPermissions.captureRuntimeGranted(context)) {
            keepCameraReadyIfPermitted();
            cameraFlow.bindIfNeeded();
        }
    }

    public boolean releaseIdleCameraForScreenOff() {
        if (!cameraPipelineSettings.releaseCameraWhenScreenOff()
                || !cameraIdleForScreenOffRelease())
            return false;
        return cameraFlow.releaseIdleCamera(released -> {
            if (released) {
                RecordingForegroundService.releaseCameraReady(context);
                logger.info("Released idle camera after screen turned off. "
                        + "Camera will rebind before next off-screen capture.");
            } else {
                logger.warn("Could not release idle camera after screen turned off; "
                        + "keeping camera-ready foreground state.", null);
            }
        });
    }

    public void releaseCameraForActivityFinish() {
        releaseCamera(released -> RecordingForegroundService.releaseCameraReady(context));
    }

    public void recoverMountedStorage(java.util.function.Consumer<StorageRecoveryResult> callback) {
        captureIoExecutor.execute(() -> {
            storage.refreshExternalRootsAfterMount();
            mediaOutput.recoverStaged(report -> {
                completeInterruptedFinalizationIfRecovered(report);
                callback.accept(new StorageRecoveryResult(
                        report.getRecovered(), report.getPreserved(), report.getDuplicates()));
            });
        });
    }

    public AuthenticateOperatorUseCase authenticateOperatorUseCase() {
        return authenticateOperator;
    }

    public OperatorSessionUseCase operatorSessionUseCase() {
        return operatorSession;
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

    private static String sessionSummary(com.dvid.dcam.feature.auth.domain.OperatorSession session) {
        return session == null ? "null"
                : shortId(session.getSessionId())
                        + "/bootHash=" + shortId(session.getBootId());
    }

    public ManageOperatorUsersUseCase manageOperatorUsersUseCase() {
        return manageUsers;
    }

    public OpenMediaUseCase createOpenMediaUseCase(ComponentActivity owner) {
        return new OpenMediaUseCase(new AndroidMediaOpener(owner, storage, logger));
    }

    public HardwareButtonRouter createHardwareButtonRouter(
            PhotoCaptureUseCase photos,
            RecordingCommands videos,
            AudioRecordingUseCase audio,
            Runnable currentLocationRequest,
            Runnable commandHaptic) {
        return new HardwareButtonRouter(
                photos, videos, audio, featureGates, operatorSession, hardwareButtonLayout,
                currentLocationRequest, commandHaptic);
    }

    private CameraPipelineModeController.Backend createCameraPipelineModeBackend() {
        return new CameraPipelineModeController.Backend() {
            @Override
            public boolean canSwitch(String cameraId) {
                if (cameraCapabilityRecheckInFlight() || cameraRecordingActive(cameraId)
                        || !cameraCapabilities.cameraIds().contains(cameraId))
                    return false;
                if (activeCameraId().filter(cameraId::equals).isEmpty())
                    return true;
                ProcessCameraRuntimeOwner.RuntimeSnapshot runtime = recordingCamera.snapshot();
                return cameraFlow.state() == CameraFlowCoordinator.State.READY
                        && !cameraFlow.transitionInFlight()
                        && runtime.state() == CameraRuntimeState.READY
                        && runtime.inFlight().isEmpty()
                        && runtime.settingsEnabled();
            }

            @Override
            public Optional<CandidateKey> activeCandidate() {
                return AppComposition.this.activeCameraCandidate();
            }

            @Override
            public void prepare(String cameraId, DeveloperSettingsStore.Mode mode,
                    boolean activeCamera, Consumer<Boolean> completion) {
                Optional<VerificationPipelineId> pipeline = forcedPipeline(mode);
                if (!activeCamera) {
                    cameraCapabilities.ensurePipelineEvidence(cameraId, pipeline, completion);
                    return;
                }
                cameraCapabilities.ensurePipelineEvidence(pipeline, completion);
            }

            @Override
            public Optional<CandidateKey> resolve(String cameraId,
                    DeveloperSettingsStore.Mode mode, CandidateKey currentProfile) {
                CandidateKey current = Objects.requireNonNull(currentProfile, "currentProfile");
                return cameraCapabilities.requestedProfileCandidate(
                        current, forcedPipeline(mode));
            }

            @Override
            public void verify(CandidateKey target,
                    Consumer<CameraPipelineModeController.TransitionResult> completion) {
                if (!cameraCapabilities.beginDeferredWrites()) {
                    completion.accept(new CameraPipelineModeController.TransitionResult(
                            false, activeCameraCandidate(), "evidence_transaction_busy"));
                    return;
                }
                try {
                    CameraFlowCoordinator.Transition transition = recordingCamera.snapshot()
                            .state() == CameraRuntimeState.CLOSED
                                    ? CameraFlowCoordinator.Transition.INITIALIZE
                                    : CameraFlowCoordinator.Transition.VERIFY_SETTING;
                    submitCameraTransition(transition, target, result -> completion.accept(
                            new CameraPipelineModeController.TransitionResult(
                                    completePipelineEvidenceTransaction(target, result),
                                    result.activeCandidate(), result.detail())));
                } catch (RuntimeException error) {
                    cameraCapabilities.completeDeferredWrites(false);
                    throw error;
                }
            }

            @Override
            public void restore(CandidateKey target,
                    Consumer<CameraPipelineModeController.TransitionResult> completion) {
                submitCameraTransition(CameraFlowCoordinator.Transition.BIND_COMMITTED, target,
                        result -> completion.accept(new CameraPipelineModeController.TransitionResult(
                                result.ready(), result.activeCandidate(), result.detail())));
            }

            @Override
            public void commit(String cameraId, DeveloperSettingsStore.Mode mode) {
                persistCameraPipelineSelection(cameraId, mode);
                try {
                    cameraCapabilities.markInitializationReusable();
                } catch (RuntimeException error) {
                    logger.warn("camera_pipeline_selector cameraId=" + cameraId
                            + " stage=initialization_state outcome=not_reusable", error);
                }
                logger.info("camera_pipeline_selector cameraId=" + cameraId
                        + " stage=commit outcome=applied mode=" + mode.name());
            }

            @Override
            public DeveloperSettingsStore.Mode mode(String cameraId) {
                return cameraPipelineSettings.mode(cameraId);
            }
        };
    }

    private void configureCameraPipelineSelection(DeveloperSettingsStore.Mode mode) {
        cameraCapabilities.configurePipelineSelection(forcedPipeline(mode));
    }

    private void configureCameraPipelineSelection(String cameraId,
            Optional<VerificationPipelineId> pipeline) {
        cameraCapabilities.configurePipelineSelection(cameraId, pipeline);
    }

    private void configureCameraPipelineSelections() {
        for (String cameraId : cameraCapabilities.cameraIds()) {
            configureCameraPipelineSelection(cameraId,
                    forcedPipeline(cameraPipelineSettings.mode(cameraId)));
        }
    }

    private boolean completePipelineEvidenceTransaction(CandidateKey target,
            CameraFlowCoordinator.TransitionResult result) {
        boolean active = result.ready() && result.activeCandidate()
                .filter(value -> value.cameraId().equals(target.cameraId())
                        && value.codec() == target.codec()
                        && value.verificationPipelineId().equals(target.verificationPipelineId()))
                .isPresent();
        cameraCapabilities.completeDeferredWrites(active);
        return active;
    }

    private static Optional<VerificationPipelineId> forcedPipeline(
            DeveloperSettingsStore.Mode mode) {
        return switch (Objects.requireNonNull(mode, "mode")) {
            case AUTO -> Optional.empty();
            case A -> Optional.of(CameraPipelineIds.NATIVE_SURFACE_SHARING);
            case B -> Optional.of(CameraPipelineIds.EGL_FAN_OUT);
        };
    }

    private CameraFlowCoordinator.Backend createCameraFlowBackend() {
        return new CameraFlowCoordinator.Backend() {
            @Override
            public void loadCapabilities(Consumer<List<CandidateKey>> ready,
                    Consumer<String> unavailable) {
                loadCapabilities(true, ready, unavailable);
            }

            @Override
            public void loadCapabilities(boolean deepVerify,
                    Consumer<List<CandidateKey>> ready, Consumer<String> unavailable) {
                if (!deepVerify) {
                    if (cameraCapabilities
                            .loadPersistedSnapshot(cameraId -> forcedPipeline(cameraPipelineSettings.mode(cameraId)))) {
                        List<CandidateKey> candidates = cameraCapabilities.startupCandidates();
                        if (!candidates.isEmpty()) {
                            ready.accept(candidates);
                            return;
                        }
                    }
                    logger.info("Saved camera capabilities could not be used. "
                            + "Running a fast camera capability scan.");
                }
                Runnable scanReady = () -> {
                    configureCameraPipelineSelections();
                    try {
                        cameraCapabilities.markInitializationReusable();
                    } catch (RuntimeException error) {
                        logger.warn("camera_pipeline_selector startup_sync_failed", error);
                    }
                    ready.accept(cameraCapabilities.startupCandidates());
                };
                Runnable scanComplete = () -> {
                    if (!cameraCapabilities.lastScanSuccessful()) {
                        String reason = cameraCapabilities.unavailableReason();
                        if (reason == null || reason.isBlank()) {
                            reason = "capability_scan_unavailable";
                        }
                        unavailable.accept(reason);
                    }
                };
                if (deepVerify) {
                    cameraCapabilities.requestScan(scanReady, scanComplete,
                            AppComposition.this::handleCameraCapabilityRecheckProgress);
                } else {
                    cameraCapabilities.requestBootstrapScan(scanReady, scanComplete);
                }
            }

            @Override
            public void releaseForCapabilityScan(Consumer<Boolean> completion) {
                releaseCamera(completion);
            }

            @Override
            public boolean invalidateCapabilities() {
                return cameraCapabilities.invalidate();
            }

            @Override
            public void submit(CameraFlowCoordinator.Transition transition,
                    CandidateKey candidate,
                    Consumer<CameraFlowCoordinator.TransitionResult> completion) {
                if (transition == CameraFlowCoordinator.Transition.BIND_COMMITTED) {
                    keepCameraReadyIfPermitted();
                }
                submitCameraTransition(transition, candidate, completion);
            }

            @Override
            public Optional<CandidateKey> resolveSetting(
                    String stableId, int selectedIndex) {
                return resolveCameraSetting(stableId, selectedIndex);
            }

            @Override
            public CameraSettingsSource settingsSource(
                    Optional<CandidateKey> target, CameraFlowCoordinator.State state) {
                return () -> cameraSettingsCameras(target, state);
            }

            @Override
            public Optional<CandidateKey> activeCandidate() {
                return activeCameraCandidate();
            }

            @Override
            public List<CandidateKey> switchCandidates() {
                List<CandidateKey> result = new ArrayList<>();
                for (String cameraId : cameraCapabilities.cameraIds()) {
                    cameraCapabilities.requestedCandidate(cameraId).ifPresent(result::add);
                }
                return List.copyOf(result);
            }

            @Override
            public boolean canRestoreExact(CandidateKey candidate) {
                return cameraCapabilities.committedCandidate(candidate.cameraId().value())
                        .filter(candidate::equals).isPresent();
            }

            @Override
            public int cameraCount() {
                return cameraCapabilities.cameraIds().size();
            }

            @Override
            public boolean recording() {
                return cameraRecordingActive();
            }

            @Override
            public boolean deepVerifyOnStartup() {
                return false;
            }

            @Override
            public boolean pipelineModeTransitionInFlight() {
                return cameraPipelineSelectionInFlight();
            }

            @Override
            public boolean reusableStartup() {
                return cameraCapabilities.isInitializationReusable();
            }

            @Override
            public void markCapabilitiesReusable() {
                try {
                    cameraCapabilities.markInitializationReusable();
                } catch (RuntimeException error) {
                    logger.warn("camera_capability_owner stage=initialization_state"
                            + " outcome=not_reusable", error);
                }
            }

            @Override
            public void setStartupPreviewReady(boolean ready) {
                if (ready)
                    recordingCamera.releaseStartupPreviewWhenFrameArrives();
                else
                    recordingCamera.holdStartupPreview();
            }

        };
    }

    private void releaseCamera(Consumer<Boolean> completion) {
        ProcessCameraRuntimeOwner owner = cameraCapabilities.runtimeOwner();
        AtomicBoolean submitted = new AtomicBoolean();
        AtomicBoolean completed = new AtomicBoolean();
        AtomicReference<ProcessCameraRuntimeOwner.Attachment> attachment = new AtomicReference<>();
        ProcessCameraRuntimeOwner.Listener observer = snapshot -> {
            if (!submitted.get() || snapshot.inFlight().isPresent())
                return;
            boolean released = snapshot.state() == CameraRuntimeState.CLOSED;
            boolean terminal = released || snapshot.state() == CameraRuntimeState.RECOVERING;
            if (!terminal || !completed.compareAndSet(false, true))
                return;
            ProcessCameraRuntimeOwner.Attachment current = attachment.get();
            if (current != null)
                current.close();
            completion.accept(released);
        };
        ProcessCameraRuntimeOwner.Attachment created = owner.attach(observer);
        attachment.set(created);
        ProcessCameraRuntimeOwner.Submission result = recordingCamera.releaseCamera();
        submitted.set(true);
        if (result == ProcessCameraRuntimeOwner.Submission.ACCEPTED
                || result == ProcessCameraRuntimeOwner.Submission.NO_OP) {
            observer.onStateChanged(owner.snapshot());
            return;
        }
        created.close();
        if (completed.compareAndSet(false, true))
            completion.accept(false);
    }

    static boolean cameraTransitionTerminal(
            ProcessCameraRuntimeOwner.RuntimeSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        return snapshot.inFlight().isEmpty()
                && snapshot.state() != CameraRuntimeState.RECOVERING;
    }

    private void submitCameraTransition(CameraFlowCoordinator.Transition transition,
            CandidateKey candidate,
            Consumer<CameraFlowCoordinator.TransitionResult> completion) {
        ProcessCameraRuntimeOwner owner = cameraCapabilities.runtimeOwner();

        AtomicBoolean submitted = new AtomicBoolean();
        AtomicBoolean completed = new AtomicBoolean();
        AtomicReference<ProcessCameraRuntimeOwner.Attachment> attachment = new AtomicReference<>();
        ProcessCameraRuntimeOwner.Listener observer = snapshot -> {
            if (!submitted.get() || !cameraTransitionTerminal(snapshot)
                    || !completed.compareAndSet(false, true))
                return;
            ProcessCameraRuntimeOwner.Attachment current = attachment.get();
            if (current != null)
                current.close();
            Optional<CandidateKey> active = activeCameraCandidate();
            boolean ready = snapshot.state() == CameraRuntimeState.READY;
            completeCameraTransition(transition, candidate, completion,
                    new CameraFlowCoordinator.TransitionResult(
                            ready, active, "runtime_"
                                    + snapshot.state().name().toLowerCase()));
        };
        attachment.set(owner.attach(observer));
        CameraRuntimeSelection selection = runtimeSelection(candidate);
        ProcessCameraRuntimeOwner.Submission result = switch (transition) {
            case INITIALIZE -> recordingCamera.initialize(selection);
            case SWITCH_CAMERA -> recordingCamera.switchCamera(selection);
            case VERIFY_SETTING -> recordingCamera.verifySetting(selection);
            case RESTORE_EXACT -> recordingCamera.restoreExact(selection);
            case BIND_COMMITTED -> recordingCamera.bindCommitted(selection);
        };
        submitted.set(true);
        if (result == ProcessCameraRuntimeOwner.Submission.ACCEPTED
                || result == ProcessCameraRuntimeOwner.Submission.COALESCED
                || result == ProcessCameraRuntimeOwner.Submission.NO_OP) {
            observer.onStateChanged(owner.snapshot());
            return;
        }
        ProcessCameraRuntimeOwner.Attachment current = attachment.get();
        if (current != null)
            current.close();
        if (completed.compareAndSet(false, true)) {
            completeCameraTransition(transition, candidate, completion,
                    new CameraFlowCoordinator.TransitionResult(false,
                            activeCameraCandidate(),
                            "submission_" + result.name().toLowerCase()));
        }
    }

    static boolean recordingMinimumRefreshRequired(
            ProcessCameraRuntimeOwner.RuntimeSnapshot snapshot, long lastGeneration) {
        Objects.requireNonNull(snapshot, "snapshot");
        return snapshot.state() == CameraRuntimeState.READY
                && snapshot.transitionGeneration() != lastGeneration;
    }

    private synchronized void refreshCachedMinimumRecordingStartFreeBytes(
            ProcessCameraRuntimeOwner.RuntimeSnapshot snapshot) {
        if (!recordingMinimumRefreshRequired(
                snapshot, cachedMinimumRecordingStartGeneration)) return;
        cachedMinimumRecordingStartGeneration = snapshot.transitionGeneration();
        try {
            long bitrateBitsPerSecond = recordingCamera.recordingBitrateBitsPerSecond();
            cachedMinimumRecordingStartFreeBytes = bitrateBitsPerSecond > 0L
                    ? mediaOutput.minimumRecordingStartFreeBytes(bitrateBitsPerSecond)
                    : CaptureStorageCapacityPolicy.MIN_CAPTURE_FREE_BYTES;
        } catch (RuntimeException error) {
            cachedMinimumRecordingStartFreeBytes =
                    CaptureStorageCapacityPolicy.MIN_CAPTURE_FREE_BYTES;
            logger.warn("Refresh recording storage minimum failed; using safety floor.", error);
        }
    }

    private void completeCameraTransition(
            CameraFlowCoordinator.Transition transition, CandidateKey candidate,
            Consumer<CameraFlowCoordinator.TransitionResult> completion,
            CameraFlowCoordinator.TransitionResult result) {
        try {
            completion.accept(result);
        } finally {
            recordingCoordinator.resumePendingStart();
        }
    }

    private Optional<CandidateKey> resolveCameraSetting(String stableId, int selectedIndex) {
        CameraSettingControlId control;
        try {
            control = CameraSettingControlId.parse(stableId);
        } catch (IllegalArgumentException error) {
            return Optional.empty();
        }
        Optional<CandidateKey> currentValue = cameraCapabilities.requestedCandidate(
                control.cameraId());
        if (currentValue.isEmpty())
            return Optional.empty();
        var tuple = currentValue.orElseThrow().tuple().orElseThrow();
        String videoId = tuple.videoMode().resolution().label().name();
        int frameRate = tuple.videoMode().framesPerSecond();
        String imageId = tuple.imageMode().resolution().label().name();
        switch (control.kind()) {
            case VIDEO_RESOLUTION -> {
                List<CaptureQuality> values = cameraCapabilities.supportedRecordQualities(
                        control.cameraId());
                if (selectedIndex < 0 || selectedIndex >= values.size())
                    return Optional.empty();
                videoId = values.get(selectedIndex).getId();
                List<Integer> rates = cameraCapabilities.supportedRecordFrameRates(
                        control.cameraId(), videoId);
                if (rates.isEmpty())
                    return Optional.empty();
                frameRate = rates.get(rates.size() - 1);
            }
            case VIDEO_FRAME_RATE -> {
                List<Integer> values = cameraCapabilities.supportedRecordFrameRates(
                        control.cameraId(), videoId);
                if (selectedIndex < 0 || selectedIndex >= values.size())
                    return Optional.empty();
                frameRate = values.get(selectedIndex);
            }
            case IMAGE_RESOLUTION -> {
                List<CaptureQuality> values = cameraCapabilities.supportedImageQualities(
                        control.cameraId());
                if (selectedIndex < 0 || selectedIndex >= values.size())
                    return Optional.empty();
                imageId = values.get(selectedIndex).getId();
            }
            case STATUS -> {
                return Optional.empty();
            }
        }
        Optional<CandidateKey> requested = cameraCapabilities.requestedCandidate(
                control.cameraId(), videoId, frameRate, imageId);
        requested.ifPresent(cameraCapabilities::saveRequestedCandidate);
        return requested;
    }

    private List<CameraSettingsCamera> cameraSettingsCameras(
            Optional<CandidateKey> target, CameraFlowCoordinator.State flowState) {
        List<CameraSettingsCamera> result = new ArrayList<>();
        ProcessCameraRuntimeOwner.RuntimeSnapshot runtime = recordingCamera.snapshot();
        for (String cameraId : cameraCapabilities.cameraIds()) {
            List<CameraVideoOption> videos = new ArrayList<>();
            for (CaptureQuality quality : cameraCapabilities.supportedRecordQualities(cameraId)) {
                videos.add(new CameraVideoOption(
                        new CameraResolutionOption(quality.getId(), quality.getWidth(),
                                quality.getHeight()),
                        cameraCapabilities.supportedRecordFrameRates(cameraId, quality.getId())));
            }
            List<CameraResolutionOption> images = new ArrayList<>();
            for (CaptureQuality quality : cameraCapabilities.supportedImageQualities(cameraId)) {
                images.add(new CameraResolutionOption(
                        quality.getId(), quality.getWidth(), quality.getHeight()));
            }
            Optional<CameraSelection> requestedSelection = cameraCapabilities
                    .requestedCandidate(cameraId).map(AppComposition::cameraSelection);
            boolean effectiveSelection = cameraCapabilities.committedCandidate(cameraId).isPresent();
            Optional<CameraSelection> targetSelection = target
                    .filter(value -> value.cameraId().value().equals(cameraId))
                    .map(AppComposition::cameraSelection);
            CameraSettingsStatus status = cameraStatus(flowState, runtime, cameraId,
                    targetSelection.isPresent(), videos.isEmpty() || images.isEmpty(),
                    requestedSelection.isPresent() || effectiveSelection);
            result.add(new CameraSettingsCamera(cameraId, videos, images, requestedSelection,
                    targetSelection, status));
        }
        return List.copyOf(result);
    }

    private static CameraSettingsStatus cameraStatus(CameraFlowCoordinator.State flowState,
            ProcessCameraRuntimeOwner.RuntimeSnapshot runtime, String cameraId,
            boolean targeted, boolean incomplete, boolean committed) {
        if (targeted || flowState == CameraFlowCoordinator.State.VERIFYING) {
            return CameraSettingsStatus.VERIFYING;
        }
        if (runtime.state() == CameraRuntimeState.RECOVERING) {
            return CameraSettingsStatus.RECOVERING;
        }
        if (flowState == CameraFlowCoordinator.State.LOADING
                || flowState == CameraFlowCoordinator.State.IDLE) {
            return CameraSettingsStatus.LOADING;
        }
        if (incomplete)
            return CameraSettingsStatus.PIPELINE_INCOMPLETE;
        if (flowState == CameraFlowCoordinator.State.UNAVAILABLE && !committed) {
            return CameraSettingsStatus.UNAVAILABLE;
        }
        return committed ? CameraSettingsStatus.READY : CameraSettingsStatus.LOADING;
    }

    private Optional<CandidateKey> activeCameraCandidate() {
        return recordingCamera.snapshot().activeSelection().map(value -> CandidateKey.forTuple(
                value.cameraId(), value.codec(), value.verificationPipelineId(), value.tuple()));
    }

    private static CameraRuntimeSelection runtimeSelection(CandidateKey candidate) {
        return new CameraRuntimeSelection(candidate.cameraId(),
                candidate.verificationPipelineId(), candidate.codec(),
                candidate.tuple().orElseThrow());
    }

    private static CameraSelection cameraSelection(CandidateKey candidate) {
        var tuple = candidate.tuple().orElseThrow();
        return new CameraSelection(tuple.videoMode().resolution().label().name(),
                tuple.videoMode().framesPerSecond(),
                tuple.imageMode().resolution().label().name());
    }

    private void keepCameraReadyIfPermitted() {
        if (!DcamPermissions.cameraGranted(context))
            return;
        if (!RecordingForegroundService.keepCameraReady(context)) {
            logger.warn("Could not keep hot camera runtime in foreground. "
                    + "Screen-off capture may require camera reinitialization.", null);
        }
    }

    public CaptureRuntime createCaptureRuntime(ComponentActivity owner) {
        keepCameraReadyIfPermitted();
        CaptureEvents captureEvents = recordingCoordinator;
        SharedCameraPreviewView cameraPreview = sharedCameraComponents.createPreview(
                owner, logger,
                message -> FloatingNotice.show(owner, message),
                message -> FloatingNotice.showPersistent(owner, message),
                FloatingNotice::hidePersistent);
        PhotoCaptureUseCase photos = new PhotoCaptureUseCase(
                recordingCamera, this::cameraPhotoCaptureAllowed,
                this::runPhotoWhenReleasedCameraReady);
        RecordingCommands videos = recordingCoordinator;
        AudioPreparationEvents.Binding audioPreparationBinding = audioPreparationNotifier
                .bind(new AudioPreparationEvents() {
                    @Override
                    public void onPreparing(String message) {
                        cameraPreview.showStorageWarning(message);
                    }

                    @Override
                    public void onCleared() {
                        cameraPreview.clearStorageWarning();
                    }

                    @Override
                    public void onUnavailable(String message) {
                        cameraPreview.showStorageWarning(message);
                    }
                });
        refreshCaptureStorageNotice();
        return new CaptureRuntime(recordingCamera, cameraPreview, cameraFlow,
                photos, videos, audioRecording, captureEvents, audioPreparationBinding,
                captureStorageNoticeMonitor::pause,
                androidRuntime::capturePermissionsGranted);
    }

    public CameraSettingsPresentationState cameraSettings(boolean recording) {
        return cameraFlow.settings(recording);
    }

    public boolean selectCameraSetting(String stableId, int selectedIndex) {
        return cameraFlow.select(stableId, selectedIndex);
    }

    /**
     * Activity-bound preview over process-owned recording adapters.
     *
     * Camera lifecycle intentionally remains RESUMED while screen is off so
     * recording stays
     * ready and active recording can continue across Activity and preview-surface
     * recreation.
     * Preview surface lifetime is separate from camera capture lifetime; do not
     * replace this
     * owner with MainActivity lifecycle or stop it from MainActivity.onStop().
     */
    public static final class AndroidRuntime {
        private final Context context;
        private final AndroidDeviceSettings settings;
        private final DcamKioskController kiosk;
        private final Logger logger;

        private AndroidRuntime(Context context, Logger logger) {
            this.context = context.getApplicationContext();
            this.logger = logger;
            settings = new AndroidDeviceSettings(this.context);
            kiosk = new DcamKioskController(this.context, logger);
        }

        public boolean isAutoRotateEnabled() {
            return settings.isAutoRotateEnabled();
        }

        public boolean canWriteSystemSettings() {
            return settings.canWriteSystemSettings();
        }

        public boolean setAutoRotateEnabled(boolean enabled) {
            return settings.setAutoRotateEnabled(enabled);
        }

        public boolean isWifiEnabled() {
            return settings.isWifiEnabled();
        }

        public boolean setWifiEnabled(boolean enabled) {
            return settings.setWifiEnabled(enabled);
        }

        public boolean isDeviceOwner() {
            return kiosk.isDeviceOwner();
        }

        public boolean isDefaultHome() {
            return kiosk.isDefaultHome();
        }

        public boolean removeDeviceOwner() {
            return kiosk.removeDeviceOwner();
        }

        public boolean corePermissionsGranted() {
            return DcamPermissions.coreRuntimeGranted(context);
        }

        public boolean cameraPermissionGranted() {
            return DcamPermissions.cameraGranted(context);
        }

        public boolean capturePermissionsGranted() {
            return DcamPermissions.captureRuntimeGranted(context);
        }

        public boolean isScreenInteractive() {
            PowerManager power = context.getSystemService(PowerManager.class);
            return power == null || power.isInteractive();
        }

        public boolean allFilesAccessGranted() {
            return DcamPermissions.allFilesAccessGranted(context);
        }

        public boolean fineLocationPermissionGranted() {
            return DcamPermissions.fineLocationGranted(context);
        }

        public String[] missingCorePermissions() {
            return DcamPermissions.missing(context, DcamPermissions.coreRuntime());
        }

        public String[] missingCapturePermissions() {
            return DcamPermissions.missing(context, DcamPermissions.captureRuntime());
        }

        public String[] missingLegacyStoragePermissions() {
            return DcamPermissions.missing(context, DcamPermissions.legacyStorageRuntime());
        }

        public String[] missingLocationPermissions() {
            return DcamPermissions.fineLocationGranted(context)
                    ? new String[0]
                    : DcamPermissions.locationRuntime();
        }

        public void resetDatabase() {
            AppDatabase.reset(context);
        }

        public void applyActiveKioskPolicyAsync(Runnable completion) {
            DcamKioskController.applyActiveKioskPolicyAsync(context, logger, completion);
        }

        public void enterLockTaskIfAllowed(Activity activity, Runnable completion) {
            kiosk.enterLockTaskIfAllowed(activity, completion);
        }
    }

    public static final class CaptureRuntime {
        private final SharedCameraGateway camera;
        private final SharedCameraPreviewView cameraPreview;
        private final CameraFlowCoordinator cameraFlow;
        private final PhotoCaptureUseCase photos;
        private final RecordingCommands videos;
        private final AudioRecordingUseCase audio;
        private final CaptureEvents captureEvents;
        private final AudioPreparationEvents.Binding audioPreparationBinding;
        private final Runnable storageNoticeRelease;
        private final BooleanSupplier capturePermissionsGranted;
        private CameraFlowCoordinator.StateSubscription cameraFlowStateSubscription;
        private ProcessCameraRuntimeOwner.Attachment cameraRuntimeStateAttachment;

        private CaptureRuntime(
                SharedCameraGateway camera,
                SharedCameraPreviewView cameraPreview,
                CameraFlowCoordinator cameraFlow,
                PhotoCaptureUseCase photos,
                RecordingCommands videos,
                AudioRecordingUseCase audio,
                CaptureEvents captureEvents,
                AudioPreparationEvents.Binding audioPreparationBinding,
                Runnable storageNoticeRelease,
                BooleanSupplier capturePermissionsGranted) {
            this.camera = camera;
            this.cameraPreview = cameraPreview;
            this.cameraFlow = cameraFlow;
            this.photos = photos;
            this.videos = videos;
            this.audio = audio;
            this.captureEvents = captureEvents;
            this.audioPreparationBinding = audioPreparationBinding;
            this.storageNoticeRelease = storageNoticeRelease;
            this.capturePermissionsGranted = capturePermissionsGranted;
        }

        public View cameraPreview() {
            return cameraPreview;
        }

        public PhotoCaptureUseCase photoCapture() {
            return photos;
        }

        public RecordingCommands videoRecording() {
            return videos;
        }

        public AudioRecordingUseCase audioRecording() {
            return audio;
        }

        public CaptureEvents captureEvents() {
            return captureEvents;
        }

        public void observeCameraSwitchState(Runnable observer) {
            Runnable checked = Objects.requireNonNull(observer, "observer");
            clearCameraSwitchStateObserver();
            cameraFlowStateSubscription = cameraFlow.observeStateChanges(checked);
            cameraRuntimeStateAttachment = camera.observeRuntimeState(snapshot -> checked.run());
        }

        public boolean isRecording() {
            return captureEvents.currentMode() != RecordingMode.IDLE;
        }

        public void bindCameraIfPermitted() {
            if (capturePermissionsGranted.getAsBoolean())
                cameraFlow.bindIfNeeded();
        }

        public void refreshCameraState() {
            if (capturePermissionsGranted.getAsBoolean())
                cameraFlow.bindIfNeeded();
        }

        public void refreshDisplayRotation() {
            camera.refreshDisplayRotation();
        }

        public CameraSettingsPresentationState cameraSettings(boolean recording) {
            return cameraFlow.settings(recording);
        }

        public boolean selectCameraSetting(String stableId, int selectedIndex) {
            return cameraFlow.select(stableId, selectedIndex);
        }

        public boolean canOpenCameraSetting(String stableId) {
            return cameraFlow.canOpenSetting(stableId);
        }

        public boolean hasMultipleCameras() {
            return cameraFlow.cameraCount() > 1;
        }

        public boolean canSwitchCamera() {
            return cameraFlow.canSwitchCamera();
        }

        public String cameraSwitchState() {
            ProcessCameraRuntimeOwner.RuntimeSnapshot snapshot = camera.snapshot();
            String flowState = cameraFlow.state().name().toLowerCase(java.util.Locale.ROOT);
            String runtimeState = snapshot.state().name().toLowerCase(java.util.Locale.ROOT);
            String operation = snapshot.inFlight()
                    .map(value -> value.name().toLowerCase(java.util.Locale.ROOT)
                            .replace('_', ' '))
                    .orElse(null);
            return "Camera flow is " + flowState
                    + (cameraFlow.transitionInFlight()
                            ? " with a transition in progress"
                            : " with no transition in progress")
                    + ". Camera runtime is " + runtimeState
                    + (operation == null
                            ? " with no operation in progress"
                            : " and is running " + operation)
                    + ". Camera settings are "
                    + (snapshot.settingsEnabled() ? "enabled" : "disabled")
                    + ", and photo capture is "
                    + (snapshot.photoEnabled() ? "enabled" : "disabled") + ".";
        }

        public boolean switchCamera() {
            return cameraFlow.switchToNextCamera();
        }

        public void release() {
            clearCameraSwitchStateObserver();
            storageNoticeRelease.run();
            audioPreparationBinding.close();
            camera.detachPreview(cameraPreview);
        }

        private void clearCameraSwitchStateObserver() {
            if (cameraFlowStateSubscription != null)
                cameraFlowStateSubscription.close();
            cameraFlowStateSubscription = null;
            if (cameraRuntimeStateAttachment != null)
                cameraRuntimeStateAttachment.close();
            cameraRuntimeStateAttachment = null;
        }
    }
}
