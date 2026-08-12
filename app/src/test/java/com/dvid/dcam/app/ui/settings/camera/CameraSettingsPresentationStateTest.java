package com.dvid.dcam.app.ui.settings.camera;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dvid.dcam.app.ui.settings.DescribedRadioOptionUiState;
import com.dvid.dcam.app.ui.settings.SettingId;
import com.dvid.dcam.app.ui.settings.SettingItem;
import com.dvid.dcam.app.ui.settings.SettingsScreenModel;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

final class CameraSettingsPresentationStateTest {
    @Test void derivesStableRoleLabelsForOneTwoAndThreePlusCameras() {
        CameraSettingsPresentationState one = CameraSettingsPresentationState.from(
                () -> List.of(camera("0", CameraSettingsStatus.READY)), false, false);
        CameraSettingsPresentationState two = CameraSettingsPresentationState.from(
                () -> List.of(
                        camera("main", CameraSettingsStatus.READY),
                        camera("aux", CameraSettingsStatus.READY)), false, false);
        CameraSettingsPresentationState three = CameraSettingsPresentationState.from(
                () -> List.of(
                        camera("main", CameraSettingsStatus.READY),
                        camera("aux-a", CameraSettingsStatus.READY),
                        camera("aux-b", CameraSettingsStatus.READY)), false, false);

        assertEquals(List.of("Camera chính"), one.cameras().stream()
                .map(CameraSettingsRow::label).toList());
        assertEquals(List.of("Camera chính", "Camera phụ"), two.cameras().stream()
                .map(CameraSettingsRow::label).toList());
        assertEquals(List.of("Camera chính", "Camera phụ 1", "Camera phụ 2"), three.cameras().stream()
                .map(CameraSettingsRow::label).toList());
        assertEquals(List.of("main", "aux-a", "aux-b"), three.cameras().stream()
                .map(CameraSettingsRow::cameraId).toList());
    }

    @Test void frameRatesFollowDisplayedVideoTarget() {
        CameraSettingsCamera camera = new CameraSettingsCamera("0",
                List.of(
                        video("FHD", 1920, 1080, 30, 60),
                        video("HD", 1280, 720, 20)),
                List.of(resolution("FHD", 1920, 1080)),
                new CameraSelection("FHD", 60, "FHD"),
                new CameraSelection("HD", 20, "FHD"),
                CameraSettingsStatus.READY);
        CameraSettingsPresentationState state = CameraSettingsPresentationState.from(
                () -> List.of(camera), false, false);

        CameraSettingsRow row = state.cameras().get(0);
        SettingsScreenModel screen = state.screen();

        assertEquals(new CameraSelection("FHD", 60, "FHD"),
                row.committedSelection().orElseThrow());
        assertEquals(new CameraSelection("HD", 20, "FHD"),
                row.targetSelection().orElseThrow());
        assertEquals(List.of(20), row.frameRateOptions());
        assertEquals(List.of("20 FPS"), screen.getSections().get(0).getItems().get(2).getOptions());
        assertEquals(0, screen.getSections().get(0).getItems().get(2).getSelectedIndex());
        assertEquals("HD (1280×720)", screen.getSections().get(0).getItems().get(1)
                .getOptions().get(1));
    }

    @Test void recordingAndCameraScreensOwnOnlyTheirMediaSettings() {
        CameraSettingsPresentationState state = CameraSettingsPresentationState.from(
                () -> List.of(new CameraSettingsCamera("0",
                        List.of(video("FHD", 1920, 1080, 30)),
                        List.of(resolution("FHD", 1920, 1080)),
                        new CameraSelection("FHD", 30, "FHD"), null,
                        CameraSettingsStatus.READY)), false, false);

        List<SettingItem> recordingItems = state.recordingScreen(
                "Video", "FPS").getSections().get(0).getItems();
        List<SettingItem> cameraItems = state.cameraScreen(
                "Photo", "Photo quality may vary while recording.", "Status")
                .getSections().get(0).getItems();

        assertEquals(2, recordingItems.size());
        assertEquals(SettingId.CAMERA_VIDEO_RESOLUTION, recordingItems.get(0).getId());
        assertEquals(SettingId.CAMERA_VIDEO_FRAME_RATE, recordingItems.get(1).getId());
        assertEquals(1, cameraItems.size());
        assertEquals(SettingId.CAMERA_IMAGE_RESOLUTION, cameraItems.get(0).getId());
        assertEquals("Photo quality may vary while recording.",
                cameraItems.get(0).getDescription());
    }

    @Test void sourceOptionsAndSelectionsPassThroughWithoutLegacyReconstruction() {
        AtomicInteger calls = new AtomicInteger();
        CameraSettingsCamera supplied = new CameraSettingsCamera("external-7",
                List.of(video("RAW-17", 1273, 719, 27)),
                List.of(resolution("JPEG-9", 901, 503)),
                new CameraSelection("RAW-17", 27, "JPEG-9"), null,
                CameraSettingsStatus.READY);

        CameraSettingsPresentationState state = CameraSettingsPresentationState.from(() -> {
            calls.incrementAndGet();
            return List.of(supplied);
        }, false, false);
        SettingItem video = state.screen().getSections().get(0).getItems().get(1);
        SettingItem image = state.screen().getSections().get(0).getItems().get(3);

        assertEquals(1, calls.get());
        assertEquals(List.of("RAW-17 (1273×719)"), video.getOptions());
        assertEquals(List.of("JPEG-9 (901×503)"), image.getOptions());
        assertEquals(0, video.getSelectedIndex());
        assertEquals(0, image.getSelectedIndex());
        assertTrue(video.getStableId().contains("external-7"));
        assertNotNull(supplied.committedSelection());
    }

    @Test void unavailableAndRecoveringRowsRemainVisibleButControlsDisabled() {
        CameraSettingsCamera unavailable = camera("unavailable", CameraSettingsStatus.UNAVAILABLE);
        CameraSettingsCamera recovering = new CameraSettingsCamera("recovering",
                List.of(video("FHD", 1920, 1080, 30)),
                List.of(resolution("FHD", 1920, 1080)),
                new CameraSelection("FHD", 30, "FHD"), null,
                CameraSettingsStatus.RECOVERING);

        SettingsScreenModel screen = CameraSettingsPresentationState.from(
                () -> List.of(unavailable, recovering), false, false).screen();

        assertEquals(2, screen.getSections().size());
        assertFalse(screen.getSections().get(0).getItems().get(1).isEnabled());
        assertFalse(screen.getSections().get(1).getItems().get(1).isEnabled());
        assertEquals("Unavailable", screen.getSections().get(0).getItems().get(0).getValue());
        assertEquals("Recovering", screen.getSections().get(1).getItems().get(0).getValue());
    }

    @Test void recordingKeepsAllVfiControlsNormalAndLazyVerificationDisablesAll() {
        CameraSettingsCamera ready = new CameraSettingsCamera("0",
                List.of(video("FHD", 1920, 1080, 30)),
                List.of(resolution("FHD", 1920, 1080)),
                new CameraSelection("FHD", 30, "FHD"), null,
                CameraSettingsStatus.READY);

        CameraSettingsPresentationState recording = CameraSettingsPresentationState.from(
                () -> List.of(ready), true, false);
        CameraSettingsPresentationState verifying = CameraSettingsPresentationState.from(
                () -> List.of(ready), false, true);

        assertEquals(CameraSettingsStatus.READY, recording.cameras().get(0).status());
        assertEquals(CameraSettingsStatus.VERIFYING, verifying.cameras().get(0).status());
        List<SettingItem> recordingItems = recording.screen().getSections().get(0).getItems();
        assertTrue(recordingItems.get(1).isEnabled());
        assertTrue(recordingItems.get(2).isEnabled());
        assertTrue(recordingItems.get(3).isEnabled());
        for (SettingItem item : verifying.screen().getSections().get(0).getItems()) {
            if (item.getId() != null) assertFalse(item.isEnabled());
        }
    }


    @Test void recordingKeepsAllVfiControlsNormalForEveryCamera() {
        CameraSettingsCamera main = new CameraSettingsCamera("main",
                List.of(video("FHD", 1920, 1080, 30, 60)),
                List.of(resolution("FHD", 1920, 1080)),
                new CameraSelection("FHD", 30, "FHD"), null,
                CameraSettingsStatus.READY);
        CameraSettingsCamera auxiliary = new CameraSettingsCamera("aux",
                List.of(video("HD", 1280, 720, 24, 30)),
                List.of(resolution("HD", 1280, 720)),
                new CameraSelection("HD", 30, "HD"), null,
                CameraSettingsStatus.READY);

        CameraSettingsPresentationState state = CameraSettingsPresentationState.from(
                () -> List.of(main, auxiliary), true, false, true, Optional.of("main"));

        SettingsScreenModel screen = state.screen();
        List<SettingItem> mainItems = screen.getSections().get(0).getItems();
        List<SettingItem> auxiliaryItems = screen.getSections().get(1).getItems();

        assertEquals(CameraSettingsStatus.READY, state.cameras().get(0).status());
        assertEquals(CameraSettingsStatus.READY, state.cameras().get(1).status());
        for (int itemIndex = 1; itemIndex <= 3; itemIndex++) {
            assertTrue(mainItems.get(itemIndex).isEnabled());
            assertTrue(auxiliaryItems.get(itemIndex).isEnabled());
        }
    }

    @Test void targetedRuntimeVerificationKeepsCameraControlsEnabled() {
        CameraSettingsCamera targeted = new CameraSettingsCamera("0",
                List.of(video("FHD", 1920, 1080, 30, 60)),
                List.of(resolution("FHD", 1920, 1080)),
                new CameraSelection("FHD", 30, "FHD"),
                new CameraSelection("FHD", 60, "FHD"),
                CameraSettingsStatus.VERIFYING);

        CameraSettingsPresentationState state = CameraSettingsPresentationState.from(
                () -> List.of(targeted), false, false);

        assertEquals(CameraSettingsStatus.VERIFYING, state.cameras().get(0).status());
        for (SettingItem item : state.screen().getSections().get(0).getItems()) {
            if (item.getId() != null) assertTrue(item.isEnabled());
        }
    }

    @Test void developerExtensionUsesStableIdsWithoutBackendSelection() {
        SettingsScreenModel screen = CameraDeveloperSettingsPresentation.screen(
                "Developer", "Pipeline", List.of(
                        new DescribedRadioOptionUiState("Auto", "Recommended"),
                        new DescribedRadioOptionUiState("A", "44 capture tuple"),
                        new DescribedRadioOptionUiState("B", "Not determined")), 0,
                false, "Recheck camera capabilities");

        assertEquals(SettingId.DEV_PIPELINE_MODE,
                screen.getSections().get(0).getItems().get(0).getId());
        SettingItem pipeline = screen.getSections().get(0).getItems().get(0);
        assertEquals(SettingItem.Type.DESCRIBED_RADIO, pipeline.getType());
        assertFalse(pipeline.isEnabled());
        assertEquals("44 capture tuple", screen.getSections().get(0).getItems().get(0)
                .getDescribedRadioOptions().get(1).getDescription());
        assertEquals("developer:pipeline-mode",
                screen.getSections().get(0).getItems().get(0).getStableId());
        assertEquals(SettingId.RECHECK_CAMERA_CAPABILITIES,
                screen.getSections().get(0).getItems().get(1).getId());
        assertEquals("developer:recheck-camera-capabilities",
                screen.getSections().get(0).getItems().get(1).getStableId());
    }

    @Test void developerExtensionShowsOneSelectorPerCamera() {
        List<DescribedRadioOptionUiState> options = List.of(
                new DescribedRadioOptionUiState("Auto", "Recommended"),
                new DescribedRadioOptionUiState("A", "28 verified tuples"),
                new DescribedRadioOptionUiState("B", "Unavailable", false));
        SettingsScreenModel screen = CameraDeveloperSettingsPresentation.screen(
                "Developer", List.of(
                        new CameraDeveloperSettingsPresentation.CameraPipelineSelectionUiState(
                                "0", "Pipeline A", "28 verified tuples", options, 1, true),
                        new CameraDeveloperSettingsPresentation.CameraPipelineSelectionUiState(
                                "1", "Pipeline B", "12 verified tuples", options, 2, true)),
                "Recheck camera capabilities", false);

        assertEquals(3, screen.getSections().get(0).getItems().size());
        SettingItem camera0 = screen.getSections().get(0).getItems().get(0);
        assertEquals(SettingItem.Type.DESCRIBED_RADIO, camera0.getType());
        assertEquals("Camera 0 pipeline", camera0.getLabel());
        assertEquals("developer:pipeline-mode-camera-0", camera0.getStableId());
        assertEquals(1, camera0.getSelectedIndex());
        assertTrue(camera0.isEnabled());
        assertEquals("28 verified tuples", camera0.getDescribedRadioOptions().get(1).getDescription());
        assertFalse(camera0.getDescribedRadioOptions().get(2).isEnabled());

        SettingItem camera1 = screen.getSections().get(0).getItems().get(1);
        assertEquals("Camera 1 pipeline", camera1.getLabel());
        assertEquals("developer:pipeline-mode-camera-1", camera1.getStableId());
        assertEquals(2, camera1.getSelectedIndex());
        assertEquals(SettingId.RECHECK_CAMERA_CAPABILITIES,
                screen.getSections().get(0).getItems().get(2).getId());
    }

    @Test void cameraPipelineStableIdRoundTripsThroughSharedParser() {
        String stableId = CameraDeveloperSettingsPresentation.CameraPipelineSelectionUiState
                .stableId("rear");

        assertEquals(Optional.of("rear"),
                CameraDeveloperSettingsPresentation.CameraPipelineSelectionUiState
                        .cameraId(stableId));
        assertEquals(Optional.empty(),
                CameraDeveloperSettingsPresentation.CameraPipelineSelectionUiState
                        .cameraId("developer:pipeline-mode"));
    }

    @Test void developerExtensionKeepsNormalActionDisabledDuringCapabilityRecheck() {
        SettingsScreenModel screen = CameraDeveloperSettingsPresentation.screen(
                "Developer", "Pipeline", List.of(
                        new DescribedRadioOptionUiState("Auto", "Recommended"),
                        new DescribedRadioOptionUiState("A", "44 capture tuple"),
                        new DescribedRadioOptionUiState("B", "41 capture tuple")), 0,
                false, "Recheck camera capabilities", true);

        SettingItem recheck = screen.getSections().get(0).getItems().get(1);
        assertEquals(SettingItem.Type.ACTION, recheck.getType());
        assertFalse(recheck.isEnabled());
        assertEquals("Recheck camera capabilities", recheck.getLabel());
        assertEquals("developer:recheck-camera-capabilities", recheck.getStableId());
    }

    private static CameraSettingsCamera camera(String id, CameraSettingsStatus status) {
        return new CameraSettingsCamera(id, List.of(), List.of(), Optional.empty(), Optional.empty(), status);
    }

    private static CameraVideoOption video(String id, int width, int height, int... frameRates) {
        return new CameraVideoOption(resolution(id, width, height),
                java.util.Arrays.stream(frameRates).boxed().toList());
    }

    private static CameraResolutionOption resolution(String id, int width, int height) {
        return new CameraResolutionOption(id, width, height);
    }
}