package com.dvid.dcam.app.ui.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.dvid.dcam.app.ui.settings.camera.CameraResolutionOption;
import com.dvid.dcam.app.ui.settings.camera.CameraSelection;
import com.dvid.dcam.app.ui.settings.camera.CameraSettingsCamera;
import com.dvid.dcam.app.ui.settings.camera.CameraSettingsPresentationState;
import com.dvid.dcam.app.ui.settings.camera.CameraSettingsStatus;
import com.dvid.dcam.app.ui.settings.camera.CameraVideoOption;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class SettingsUiStateTest {
    @Test void cameraModelConsumesGenericNCameraPresentation() {
        SettingsUiState state = new SettingsUiState(false, 0, false, false);
        CameraSettingsPresentationState presentation = CameraSettingsPresentationState.from(
                () -> List.of(new CameraSettingsCamera("camera-7",
                        List.of(new CameraVideoOption(
                                new CameraResolutionOption("FHD", 1920, 1080), List.of(30))),
                        List.of(new CameraResolutionOption("FHD", 1920, 1080)),
                        Optional.of(new CameraSelection("FHD", 30, "FHD")),
                        Optional.empty(), CameraSettingsStatus.READY)), false, false);

        SettingsScreenModel cameraScreen = state.camera(presentation);
        SettingsScreenModel recordingScreen = state.recording(presentation,
                "Video", "FPS");

        assertEquals("camera-7", cameraScreen.getSections().get(0).getItems().get(0)
                .getStableId().split(":")[2]);
        assertEquals(SettingId.CAMERA_IMAGE_RESOLUTION,
                cameraScreen.getSections().get(0).getItems().get(0).getId());
        assertEquals("FHD (1920×1080)", cameraScreen.getSections().get(0).getItems().get(0)
                .getOptions().get(0));
        assertEquals("Photo quality may vary while recording.",
                cameraScreen.getSections().get(0).getItems().get(0).getDescription());
        assertEquals(1, cameraScreen.getSections().get(0).getItems().size());
        assertEquals(2, recordingScreen.getSections().get(0).getItems().size());
        assertEquals(SettingId.CAMERA_VIDEO_RESOLUTION,
                recordingScreen.getSections().get(0).getItems().get(0).getId());
        assertEquals(SettingId.CAMERA_VIDEO_FRAME_RATE,
                recordingScreen.getSections().get(0).getItems().get(1).getId());
    }
    @Test void readOnlyUsesProvidedValues() {
        SettingsUiState state = new SettingsUiState(false, 0, false, false);
        SettingsScreenModel model = state.readOnly(
                new String[]{"Application version", "Device information"},
                new String[]{"dev", "Pending"});
        assertEquals("dev", model.getSections().get(0).getItems().get(0).getValue());
        assertEquals("Pending", model.getSections().get(0).getItems().get(1).getValue());
    }
}