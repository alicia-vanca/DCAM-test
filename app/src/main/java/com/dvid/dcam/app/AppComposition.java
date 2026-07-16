package com.dvid.dcam.app;

import com.dvid.dcam.feature.settings.application.usecase.LanguageSettingsUseCase;
import com.dvid.dcam.feature.settings.application.usecase.LanguageSettingsUseCaseImpl;
import com.dvid.dcam.feature.settings.application.usecase.MediaEncryptionSettingsUseCase;
import com.dvid.dcam.feature.settings.application.usecase.MediaEncryptionSettingsUseCaseImpl;
import com.dvid.dcam.feature.settings.application.usecase.VideoMd5SettingsUseCase;
import com.dvid.dcam.feature.settings.application.usecase.VideoMd5SettingsUseCaseImpl;
import android.content.Context;
import android.view.View;
import androidx.activity.ComponentActivity;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LifecycleRegistry;
import com.dvid.dcam.BuildConfig;
import com.dvid.dcam.app.feature.DeveloperFeatureToggles;
import com.dvid.dcam.app.feature.GatedMediaEncryptionSettingsUseCase;
import com.dvid.dcam.core.feature.domain.FeatureGate;
import com.dvid.dcam.core.config.application.port.ConfigurationRepository;
import com.dvid.dcam.core.config.application.repository.ConfigurationRepositoryImpl;
import com.dvid.dcam.core.config.domain.DcamConfig;
import com.dvid.dcam.core.feature.application.usecase.FeatureGateSettingsUseCase;
import com.dvid.dcam.core.logging.application.port.LogSink;
import com.dvid.dcam.feature.capture.application.port.AudioRecorder;
import com.dvid.dcam.feature.auth.application.port.BootIdentitySource;
import com.dvid.dcam.feature.auth.application.port.OperatorAuthRepository;
import com.dvid.dcam.feature.auth.application.repository.OperatorSessionMemory;
import com.dvid.dcam.feature.auth.application.usecase.AuthenticateOperatorUseCase;
import com.dvid.dcam.feature.auth.application.usecase.AuthenticateOperatorUseCaseImpl;
import com.dvid.dcam.feature.auth.application.usecase.ManageOperatorUsersUseCase;
import com.dvid.dcam.feature.auth.application.usecase.ManageOperatorUsersUseCaseImpl;
import com.dvid.dcam.feature.auth.application.usecase.OperatorSessionUseCase;
import com.dvid.dcam.feature.auth.application.usecase.OperatorSessionUseCaseImpl;
import com.dvid.dcam.feature.capture.application.usecase.AudioRecordingUseCase;
import com.dvid.dcam.feature.capture.application.usecase.AudioRecordingUseCaseImpl;
import com.dvid.dcam.feature.capture.application.usecase.CaptureEventUseCase;
import com.dvid.dcam.feature.capture.application.usecase.PhotoCaptureUseCase;
import com.dvid.dcam.feature.capture.application.usecase.PhotoCaptureUseCaseImpl;
import com.dvid.dcam.feature.capture.application.usecase.SerializedRecordingCoordinator;
import com.dvid.dcam.feature.capture.application.usecase.VideoRecordingUseCase;
import com.dvid.dcam.feature.device.application.port.DeviceRepository;
import com.dvid.dcam.feature.device.application.usecase.RefreshDeviceStatusUseCase;
import com.dvid.dcam.feature.device.application.usecase.RefreshDeviceStatusUseCaseImpl;
import com.dvid.dcam.feature.device.domain.DeviceInfo;
import com.dvid.dcam.feature.device.domain.DeviceStatus;
import com.dvid.dcam.feature.media.application.port.MediaRepository;
import com.dvid.dcam.feature.media.application.usecase.BrowseMediaUseCase;
import com.dvid.dcam.feature.media.application.usecase.BrowseMediaUseCaseImpl;
import com.dvid.dcam.feature.media.application.usecase.OpenMediaUseCase;
import com.dvid.dcam.feature.media.application.usecase.OpenMediaUseCaseImpl;
import com.dvid.dcam.feature.storage.application.usecase.StorageSettingsUseCase;
import com.dvid.dcam.feature.storage.application.usecase.StorageSettingsUseCaseImpl;
import com.dvid.dcam.feature.storage.application.port.MediaPartitionLocationPreferenceStore;
import com.dvid.dcam.feature.storage.domain.StorageMode;
import com.dvid.dcam.feature.storage.domain.StorageRecoveryResult;
import com.dvid.dcam.feature.storage.domain.MediaPartitionLocation;
import com.dvid.dcam.platform.audio.AndroidAudioRecorderImpl;
import com.dvid.dcam.platform.auth.AndroidBootIdentitySourceImpl;
import com.dvid.dcam.platform.auth.RoomOperatorAuthRepositoryImpl;
import com.dvid.dcam.platform.camera.CameraXCameraGatewayImpl;
import com.dvid.dcam.platform.camera.CameraXPreviewView;
import com.dvid.dcam.platform.config.AndroidLanguagePreferenceStoreImpl;
import com.dvid.dcam.platform.config.AndroidMediaEncryptionPreferenceStoreImpl;
import com.dvid.dcam.platform.config.AndroidVideoMd5PreferenceStoreImpl;
import com.dvid.dcam.platform.config.AndroidMediaPartitionLocationPreferenceStoreImpl;
import com.dvid.dcam.platform.config.AndroidStorageWarningPreferenceStoreImpl;
import com.dvid.dcam.platform.config.CsonConfigurationSourceImpl;
import com.dvid.dcam.platform.database.AppDatabase;
import com.dvid.dcam.platform.device.AndroidDeviceRepositoryImpl;
import com.dvid.dcam.platform.device.AndroidDeviceSettings;
import com.dvid.dcam.platform.feature.AndroidFeatureGateSettingsFactory;
import com.dvid.dcam.platform.input.AndroidHardwareDeviceIdentity;
import com.dvid.dcam.platform.input.DeveloperHardwareButtonSettings;
import com.dvid.dcam.platform.input.HardwareButtonLayout;
import com.dvid.dcam.platform.input.HardwareButtonProfiles;
import com.dvid.dcam.platform.input.HardwareButtonRouter;
import com.dvid.dcam.platform.logging.DcamLogSinkImpl;
import com.dvid.dcam.platform.logging.DcamLogger;
import com.dvid.dcam.platform.storage.AndroidMediaOpenerImpl;
import com.dvid.dcam.platform.storage.DcamMediaOutput;
import com.dvid.dcam.platform.storage.DcamMediaOutputImpl;
import com.dvid.dcam.platform.storage.AndroidStorageCapacitySourceImpl;
import com.dvid.dcam.platform.storage.DcamStorage;
import com.dvid.dcam.platform.storage.LocalMediaRepositoryImpl;
import java.util.function.BiConsumer;

/** Application composition root. This is the only place that selects concrete adapters. */
public final class AppComposition {
    private static AppComposition instance;
    private final DcamConfig config;
    private final DeviceStatus initialDeviceStatus;
    private final DcamStorage storage;
    private final DcamMediaOutput mediaOutput;
    private final LogSink logSink;
    private final RefreshDeviceStatusUseCase refreshDeviceStatus;
    private final BrowseMediaUseCase browseMedia;
    private final LanguageSettingsUseCase languageSettings;
    private final FeatureGateSettingsUseCase featureGateSettings;
    private final DeveloperFeatureToggles developerFeatureToggles;
    private final MediaEncryptionSettingsUseCase mediaEncryptionSettings;
    private final VideoMd5SettingsUseCase videoMd5Settings;
    private final StorageSettingsUseCase storageSettings;
    private final AuthenticateOperatorUseCase authenticateOperator;
    private final OperatorSessionUseCase operatorSession;
    private final ManageOperatorUsersUseCase manageUsers;
    private final SerializedRecordingCoordinator recordingCoordinator;
    private final CameraXCameraGatewayImpl recordingCamera;
    private volatile HardwareButtonLayout hardwareButtonLayout;
    private final HardwareButtonLayout builtInHardwareButtons;
    private final DeveloperHardwareButtonSettings developerHardwareButtons;
    private final AndroidDeviceSettings deviceSettings;

    private AppComposition(Context context) {
        deviceSettings = new AndroidDeviceSettings(context);
        MediaPartitionLocationPreferenceStore partitionPreferences =
                new AndroidMediaPartitionLocationPreferenceStoreImpl(context, "AUTO");
        MediaPartitionLocation initialPartition = partitionPreferences.currentMediaPartitionLocation();
        storage = DcamStorage.from(context, StorageMode.from(BuildConfig.DEFAULT_STORAGE_MODE),
                initialPartition);
        storageSettings = new StorageSettingsUseCaseImpl(
                partitionPreferences,
                new AndroidStorageCapacitySourceImpl(context),
                new AndroidStorageWarningPreferenceStoreImpl(context), storage, storage);

        DeviceRepository deviceRepository = new AndroidDeviceRepositoryImpl(context, storage::captureRoot);
        DeviceInfo deviceInfo = deviceRepository.readInfo();
        developerHardwareButtons = new DeveloperHardwareButtonSettings(context);
        builtInHardwareButtons = HardwareButtonProfiles.resolve(AndroidHardwareDeviceIdentity.read());
        developerHardwareButtons.initializeIfEmpty(builtInHardwareButtons);
        hardwareButtonLayout = developerHardwareButtons.loadLayout();
        initialDeviceStatus = deviceRepository.readStatus();
        featureGateSettings = AndroidFeatureGateSettingsFactory.create(context);
        developerFeatureToggles = DeveloperFeatureToggles.createDefault(featureGateSettings);
        DcamLogger.setRemoteUploadsEnabled(true);
        DcamLogger.init(context, deviceInfo);
        logSink = new DcamLogSinkImpl();

        ConfigurationRepository configurationRepository = new ConfigurationRepositoryImpl(
                new CsonConfigurationSourceImpl(storage), logSink);
        config = configurationRepository.load(deviceInfo.getHardwareId());
        DcamLogger.setCamId(config.getAccountUserId());

        refreshDeviceStatus = new RefreshDeviceStatusUseCaseImpl(deviceRepository);
        MediaRepository mediaRepository = new LocalMediaRepositoryImpl(storage);
        browseMedia = new BrowseMediaUseCaseImpl(mediaRepository);
        languageSettings = new LanguageSettingsUseCaseImpl(new AndroidLanguagePreferenceStoreImpl(context));
        MediaEncryptionSettingsUseCase rawMediaEncryptionSettings = new MediaEncryptionSettingsUseCaseImpl(
                new AndroidMediaEncryptionPreferenceStoreImpl(context, config));
        mediaEncryptionSettings = new GatedMediaEncryptionSettingsUseCase(rawMediaEncryptionSettings,
                () -> developerFeatureToggles.isEffectivelyEnabled(FeatureGate.MEDIA_ENCRYPTION));
        videoMd5Settings = new VideoMd5SettingsUseCaseImpl(new AndroidVideoMd5PreferenceStoreImpl(context));
        mediaOutput = new DcamMediaOutputImpl(storage,
                () -> developerFeatureToggles.isEffectivelyEnabled(FeatureGate.VIDEO_MD5)
                        && videoMd5Settings.isVideoMd5Enabled());
        mediaOutput.recoverStaged(report -> logSink.info(
                "Staged media recovery: recovered=" + report.getRecovered()
                        + ", preserved=" + report.getPreserved()
                        + ", duplicates=" + report.getDuplicates()));
        AppDatabase database = AppDatabase.get(context);
        OperatorAuthRepository authRepository =
                new RoomOperatorAuthRepositoryImpl(database.operatorAuth());
        BootIdentitySource bootIdentity = new AndroidBootIdentitySourceImpl(context);
        OperatorSessionMemory sessionMemory = new OperatorSessionMemory();
        authenticateOperator =
                new AuthenticateOperatorUseCaseImpl(authRepository, bootIdentity, sessionMemory);
        operatorSession = new OperatorSessionUseCaseImpl(
                authRepository, bootIdentity, sessionMemory);
        manageUsers = new ManageOperatorUsersUseCaseImpl(authRepository);
        recordingCoordinator = new SerializedRecordingCoordinator(ContextCompat.getMainExecutor(context));
        recordingCamera = new CameraXCameraGatewayImpl(
                context, new ProcessCaptureLifecycleOwner(), config, mediaOutput, logSink,
                recordingCoordinator, mediaEncryptionSettings, operatorSession, null);
        recordingCoordinator.bindCamera(recordingCamera);
    }

    public static synchronized AppComposition create(Context context) {
        if (instance == null) instance = new AppComposition(context.getApplicationContext());
        return instance;
    }

    public DcamConfig config() { return config; }
    public DeviceStatus initialDeviceStatus() { return initialDeviceStatus; }
    public RefreshDeviceStatusUseCase refreshDeviceStatusUseCase() { return refreshDeviceStatus; }
    public BrowseMediaUseCase browseMediaUseCase() { return browseMedia; }
    public LanguageSettingsUseCase languageSettingsUseCase() { return languageSettings; }
    public DeveloperFeatureToggles developerFeatureToggles() {
        return developerFeatureToggles;
    }
    public DeveloperHardwareButtonSettings developerHardwareButtonSettings() {
        return developerHardwareButtons;
    }
    public HardwareButtonLayout applyDeveloperHardwareButtonLayout() {
        hardwareButtonLayout = developerHardwareButtons.loadLayout();
        return hardwareButtonLayout;
    }
    public HardwareButtonLayout resetDeveloperHardwareButtonLayout() {
        if (!developerHardwareButtons.resetToDefaults(builtInHardwareButtons)) return null;
        return applyDeveloperHardwareButtonLayout();
    }
    public boolean hasDeveloperHardwareButtonDefaults() {
        return !builtInHardwareButtons.isEmpty();
    }
    public MediaEncryptionSettingsUseCase mediaEncryptionSettingsUseCase() { return mediaEncryptionSettings; }
    public VideoMd5SettingsUseCase videoMd5SettingsUseCase() { return videoMd5Settings; }
    public StorageSettingsUseCase storageSettingsUseCase() { return storageSettings; }
    public AndroidDeviceSettings deviceSettings() { return deviceSettings; }
    public void reloadRecordingQuality() { recordingCamera.reloadVideoQuality(); }
    public void recoverMountedStorage(java.util.function.Consumer<StorageRecoveryResult> callback) {
        mediaOutput.recoverStaged(report -> callback.accept(new StorageRecoveryResult(
                report.getRecovered(), report.getPreserved(), report.getDuplicates())));
    }
    public AuthenticateOperatorUseCase authenticateOperatorUseCase() { return authenticateOperator; }
    public OperatorSessionUseCase operatorSessionUseCase() { return operatorSession; }
    public ManageOperatorUsersUseCase manageOperatorUsersUseCase() { return manageUsers; }


    public OpenMediaUseCase createOpenMediaUseCase(ComponentActivity owner) {
        return new OpenMediaUseCaseImpl(new AndroidMediaOpenerImpl(owner, storage, logSink));
    }

    public HardwareButtonRouter createHardwareButtonRouter(
            PhotoCaptureUseCase photos,
            VideoRecordingUseCase videos,
            AudioRecordingUseCase audio,
            BiConsumer<Boolean, String> audioRecordingChanged) {
        return new HardwareButtonRouter(
                photos, videos, audio, featureGateSettings, operatorSession, hardwareButtonLayout,
                audioRecordingChanged);
    }

    public CaptureRuntime createCaptureRuntime(ComponentActivity owner) {
        CaptureEventUseCase captureEvents = recordingCoordinator;
        AudioRecorder audioRecorder = new AndroidAudioRecorderImpl(owner, mediaOutput, logSink,
                mediaEncryptionSettings, operatorSession);
        CameraXPreviewView cameraPreview = new CameraXPreviewView(owner);
        recordingCamera.attachPreview(cameraPreview);
        PhotoCaptureUseCase photos = new PhotoCaptureUseCaseImpl(recordingCamera);
        VideoRecordingUseCase videos = recordingCoordinator;
        AudioRecordingUseCase audio = new AudioRecordingUseCaseImpl(audioRecorder, config);
        return new CaptureRuntime(recordingCamera, cameraPreview, audioRecorder, photos, videos, audio, captureEvents);
    }

    /** Lifecycle-bound Android capture adapters created for one Activity instance. */
    public static final class CaptureRuntime {
        private final CameraXCameraGatewayImpl camera;
        private final CameraXPreviewView cameraPreview;
        private final AudioRecorder audioRecorder;
        private final PhotoCaptureUseCase photos;
        private final VideoRecordingUseCase videos;
        private final AudioRecordingUseCase audio;
        private final CaptureEventUseCase captureEvents;

        private CaptureRuntime(
                CameraXCameraGatewayImpl camera,
                CameraXPreviewView cameraPreview,
                AudioRecorder audioRecorder,
                PhotoCaptureUseCase photos,
                VideoRecordingUseCase videos,
                AudioRecordingUseCase audio,
                CaptureEventUseCase captureEvents) {
            this.camera = camera;
            this.cameraPreview = cameraPreview;
            this.audioRecorder = audioRecorder;
            this.photos = photos;
            this.videos = videos;
            this.audio = audio;
            this.captureEvents = captureEvents;
        }

        public View cameraPreview() { return cameraPreview; }
        public void showStorageWarning(String message) { cameraPreview.showStorageWarning(message); }
        public void clearStorageWarning() { cameraPreview.clearStorageWarning(); }
        public PhotoCaptureUseCase photoCapture() { return photos; }
        public VideoRecordingUseCase videoRecording() { return videos; }
        public AudioRecordingUseCase audioRecording() { return audio; }
        public CaptureEventUseCase captureEvents() { return captureEvents; }
        public void bindCameraIfPermitted() { camera.bindIfPermitted(); }

        public void release() {
            camera.detachPreview(cameraPreview);
            audioRecorder.release();
        }
    }

    private static final class ProcessCaptureLifecycleOwner implements LifecycleOwner {
        private final LifecycleRegistry lifecycle = new LifecycleRegistry(this);

        private ProcessCaptureLifecycleOwner() {
            lifecycle.setCurrentState(Lifecycle.State.RESUMED);
        }

        @Override public Lifecycle getLifecycle() { return lifecycle; }
    }
}













