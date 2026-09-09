package com.dvid.dcam.app.ui.settings;

import com.dvid.dcam.app.ui.settings.camera.CameraSettingsPresentationState;
import com.dvid.dcam.feature.capture.domain.AudioFileFormat;
import com.dvid.dcam.feature.device.application.port.DeveloperSettingsStore;
import com.dvid.dcam.feature.storage.domain.StorageVolumeStatus;
import java.util.List;
import java.util.OptionalInt;
import java.util.function.Consumer;

/** App-composed runtime data and commands consumed by settings presentation. */
public interface SettingsRuntime {
    enum CameraPipelineModeResult { APPLIED, REJECTED_BUSY, REJECTED_UNAVAILABLE, FAILED }

    record CameraPipelineSelection(String cameraId, String pipelineId, int tupleCount) {
        public CameraPipelineSelection {
            if (cameraId == null || cameraId.isBlank()
                    || pipelineId == null || pipelineId.isBlank() || tupleCount < 0) {
                throw new IllegalArgumentException("invalid camera pipeline selection");
            }
        }
    }

    AudioFileFormat audioFileFormat();
    void setAudioFileFormat(AudioFileFormat format);
    DeveloperSettingsStore.Mode cameraPipelineMode(String cameraId);
    boolean releaseCameraWhenScreenOff();
    void setReleaseCameraWhenScreenOff(boolean enabled);
    boolean resourceMonitorEnabled();
    void setResourceMonitorEnabled(boolean enabled);
    List<CameraPipelineSelection> cameraPipelineSelections();
    List<CameraPipelineSelection> cameraPipelineAutoSelections();
    boolean cameraCapabilitiesFullyVerified();
    List<String> cameraPipelineCameraIds();
    OptionalInt cameraPipelineVerifiedTupleCount(
            String cameraId, DeveloperSettingsStore.Mode mode);
    boolean canSelectCameraPipelineMode();
    boolean canSelectCameraPipelineMode(String cameraId);
    boolean cameraPipelineModeControlEnabled(String cameraId);
    OptionalInt cameraPipelineCaptureTupleCount(
            String cameraId, DeveloperSettingsStore.Mode mode);
    boolean selectCameraPipelineModeForSettings(
            DeveloperSettingsStore.Mode mode, Consumer<CameraPipelineModeResult> completion);
    boolean selectCameraPipelineModeForSettings(String cameraId,
            DeveloperSettingsStore.Mode mode, Consumer<CameraPipelineModeResult> completion);
    boolean resetCameraCapabilities();
    boolean cameraCapabilityRecheckControlEnabled();
    boolean cameraCapabilityRecheckInFlight();
    void readStorageVolumes(Consumer<List<StorageVolumeStatus>> callback);
    CameraSettingsPresentationState cameraSettings(boolean recording);
}
