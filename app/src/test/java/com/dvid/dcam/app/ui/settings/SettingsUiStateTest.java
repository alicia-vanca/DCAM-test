package com.dvid.dcam.app.ui.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.dvid.dcam.app.ui.settings.camera.CameraResolutionOption;
import com.dvid.dcam.app.ui.settings.camera.CameraSelection;
import com.dvid.dcam.app.ui.settings.camera.CameraSettingsCamera;
import com.dvid.dcam.app.ui.settings.camera.CameraSettingsPresentationState;
import com.dvid.dcam.app.ui.settings.camera.CameraSettingsStatus;
import com.dvid.dcam.app.ui.settings.camera.CameraVideoOption;
import com.dvid.dcam.feature.capture.domain.AudioFileFormat;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class SettingsUiStateTest {
        @Test
        void cameraModelConsumesGenericNCameraPresentation() {
                SettingsUiState state = new SettingsUiState(false, 0, false, false);
                CameraSettingsPresentationState presentation = CameraSettingsPresentationState.from(
                                () -> List.of(new CameraSettingsCamera("camera-7",
                                                List.of(new CameraVideoOption(
                                                                new CameraResolutionOption("FHD", 1920, 1080),
                                                                List.of(30))),
                                                List.of(new CameraResolutionOption("FHD", 1920, 1080)),
                                                Optional.of(new CameraSelection("FHD", 30, "FHD")),
                                                Optional.empty(), CameraSettingsStatus.READY)),
                                false, false);

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

        @Test
        void deviceModelMapsFullScreenAndOmitsStatusLights() {
                SettingsUiState state = new SettingsUiState(false, 0, false, false);
                state.updateBoolean(SettingId.FULL_SCREEN_DISPLAY, true);

                SettingsScreenModel model = state.device(
                                "Device", "Auto-rotate", "Wi-Fi", "Connect Wi-Fi");

                assertEquals(true, state.isFullScreenDisplayEnabled());
                assertEquals(List.of(
                                SettingId.FULL_SCREEN_DISPLAY,
                                SettingId.AUTO_ROTATE,
                                SettingId.WIFI_ENABLED,
                                SettingId.WIFI_CONNECT),
                                model.getSections().get(0).getItems().stream().map(SettingItem::getId).toList());
        }

        @Test
        void audioModelMapsCurrentRuntimeValuesToSingleOptionChoices() {
                SettingsUiState state = new SettingsUiState(false, 0, false, false);
                SettingsScreenModel model = state.audio("Available", new String[] {
                                "Audio file format", "Sample rate", "Audio bitrate", "Audio channels",
                                "Alert volume" }, AudioFileFormat.DEFAULT, "48 kHz", "64 kbps", "Mono", "5/7");

                List<SettingItem> items = model.getSections().get(0).getItems();
                assertEquals(List.of(
                                SettingId.AUDIO_FILE_FORMAT,
                                SettingId.AUDIO_SAMPLE_RATE,
                                SettingId.AUDIO_BIT_RATE,
                                SettingId.AUDIO_CHANNEL_COUNT,
                                SettingId.AUDIO_ALERT_VOLUME),
                                items.stream().map(SettingItem::getId).toList());
                assertEquals(List.of("AAC", "M4A"), items.get(0).getOptions());
                assertEquals(List.of("48 kHz"), items.get(1).getOptions());
                assertEquals(List.of("64 kbps"), items.get(2).getOptions());
                assertEquals(List.of("Mono"), items.get(3).getOptions());
                assertEquals(List.of("5/7"), items.get(4).getOptions());
        }

        @Test
        void readOnlyUsesLocalizedHeaderAndValues() {
                SettingsUiState state = new SettingsUiState(false, 0, false, false);
                SettingsScreenModel model = state.readOnly(
                                "Cài đặt khả dụng", "Không khả dụng",
                                new String[] { "Phiên bản ứng dụng", "Phiên bản firmware" },
                                new String[] { "dev", "firmware-42" });
                assertEquals("Cài đặt khả dụng", model.getSections().get(0).getTitle());
                assertEquals("dev", model.getSections().get(0).getItems().get(0).getValue());
                assertEquals("firmware-42", model.getSections().get(0).getItems().get(1).getValue());
        }
}