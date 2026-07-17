package com.dvid.dcam.platform.input;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import android.view.KeyEvent;
import com.dvid.dcam.core.feature.application.usecase.FeatureGateSettingsUseCase;
import com.dvid.dcam.core.feature.domain.FeatureGate;
import com.dvid.dcam.core.input.domain.ButtonRole;
import com.dvid.dcam.core.input.domain.PhysicalButtonType;
import com.dvid.dcam.feature.capture.application.usecase.AudioRecordingUseCase;
import com.dvid.dcam.feature.capture.application.usecase.PhotoCaptureUseCase;
import com.dvid.dcam.feature.capture.application.usecase.VideoRecordingUseCase;
import com.dvid.dcam.feature.capture.domain.RecordingMode;
import java.util.EnumMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class HardwareButtonHandlerTest {
    @Test public void cameraKeyTakesPhoto() {
        FakePhotoCaptureUseCaseImpl photos = new FakePhotoCaptureUseCaseImpl();
        HardwareButtonRouter router = router(photos, new FakeVideoRecordingUseCaseImpl());
        assertTrue(router.onKeyDown(KeyEvent.KEYCODE_CAMERA, 0, 0L));
        assertEquals(1, photos.photos);
    }

    @Test public void f2TakesPhoto() {
        FakePhotoCaptureUseCaseImpl photos = new FakePhotoCaptureUseCaseImpl();
        HardwareButtonRouter router = router(photos, new FakeVideoRecordingUseCaseImpl());
        assertTrue(router.onKeyDown(KeyEvent.KEYCODE_F2, 0, 0L));
        assertEquals(1, photos.photos);
    }

    @Test public void f10SwitchStartsOnDownAndStopsOnUp() {
        FakeVideoRecordingUseCaseImpl videos = new FakeVideoRecordingUseCaseImpl();
        HardwareButtonRouter router = router(new FakePhotoCaptureUseCaseImpl(), videos);
        assertTrue(router.onKeyDown(KeyEvent.KEYCODE_F10, 0, 0L));
        assertTrue(router.onKeyUp(KeyEvent.KEYCODE_F10));
        assertEquals(1, videos.starts);
        assertEquals(1, videos.stops);

        assertTrue(router.onKeyDown(KeyEvent.KEYCODE_F10, 0, 1L));
        assertTrue(router.onKeyUp(KeyEvent.KEYCODE_F10));
        assertEquals(2, videos.starts);
        assertEquals(2, videos.stops);
    }

    @Test public void switchIgnoresDuplicateZeroRepeatDownUntilRelease() {
        FakeVideoRecordingUseCaseImpl videos = new FakeVideoRecordingUseCaseImpl();
        HardwareButtonRouter router = router(new FakePhotoCaptureUseCaseImpl(), videos);

        assertTrue(router.onKeyDown(KeyEvent.KEYCODE_F10, 0, 1000L));
        assertTrue(router.onKeyDown(KeyEvent.KEYCODE_F10, 0, 1001L));
        assertTrue(router.onKeyDown(KeyEvent.KEYCODE_F10, 0, 1002L));
        assertTrue(router.onKeyUp(KeyEvent.KEYCODE_F10));
        assertTrue(router.onKeyUp(KeyEvent.KEYCODE_F10));
        assertTrue(router.onKeyDown(KeyEvent.KEYCODE_F10, 0, 2000L));
        assertTrue(router.onKeyUp(KeyEvent.KEYCODE_F10));

        assertEquals(2, videos.starts);
        assertEquals(2, videos.stops);
    }

    @Test public void switchStartsWhenFirstDownHasRepeatCount() {
        FakeVideoRecordingUseCaseImpl videos = new FakeVideoRecordingUseCaseImpl();
        HardwareButtonRouter router = router(new FakePhotoCaptureUseCaseImpl(), videos);

        assertTrue(router.onKeyDown(KeyEvent.KEYCODE_F10, 1, 1000L));
        assertTrue(router.onKeyUp(KeyEvent.KEYCODE_F10));
        assertTrue(router.onKeyDown(KeyEvent.KEYCODE_F10, 2, 2000L));
        assertTrue(router.onKeyUp(KeyEvent.KEYCODE_F10));

        assertEquals(2, videos.starts);
        assertEquals(2, videos.stops);
    }

    @Test public void focusLossKeepsSwitchLatchedUntilRelease() {
        FakeVideoRecordingUseCaseImpl videos = new FakeVideoRecordingUseCaseImpl();
        HardwareButtonRouter router = router(new FakePhotoCaptureUseCaseImpl(), videos);

        assertTrue(router.onKeyDown(KeyEvent.KEYCODE_F10, 0, 1000L));
        router.clearFocusTransientState();
        assertTrue(router.onKeyDown(KeyEvent.KEYCODE_F10, 0, 1001L));
        assertTrue(router.onKeyUp(KeyEvent.KEYCODE_F10));

        assertEquals(1, videos.starts);
        assertEquals(1, videos.stops);
    }

    @Test public void realSwitchUpStopsAfterFocusLoss() {
        FakeVideoRecordingUseCaseImpl videos = new FakeVideoRecordingUseCaseImpl();
        HardwareButtonRouter router = router(new FakePhotoCaptureUseCaseImpl(), videos);

        assertTrue(router.onKeyDown(KeyEvent.KEYCODE_F10, 0, 1000L));
        router.clearFocusTransientState();
        assertTrue(router.onKeyUp(KeyEvent.KEYCODE_F10));
        assertEquals(1, videos.stops);
    }

    @Test public void canceledSwitchUpDoesNotStopRecording() {
        FakeVideoRecordingUseCaseImpl videos = new FakeVideoRecordingUseCaseImpl();
        HardwareButtonRouter router = router(new FakePhotoCaptureUseCaseImpl(), videos);

        assertTrue(router.onKeyDown(KeyEvent.KEYCODE_F10, 0, 1000L));
        assertTrue(router.onKeyUp(KeyEvent.KEYCODE_F10, true));
        assertEquals(0, videos.stops);
        assertTrue(router.onKeyUp(KeyEvent.KEYCODE_F10));
        assertEquals(1, videos.stops);
    }

    @Test public void clearingTransientStateRecoversFromMissingSwitchUp() {
        FakeVideoRecordingUseCaseImpl videos = new FakeVideoRecordingUseCaseImpl();
        HardwareButtonRouter router = router(new FakePhotoCaptureUseCaseImpl(), videos);

        assertTrue(router.onKeyDown(KeyEvent.KEYCODE_F10, 0, 1000L));
        router.clearTransientState();
        assertTrue(router.onKeyDown(KeyEvent.KEYCODE_F10, 0, 2000L));

        assertEquals(2, videos.starts);
    }

    @Test public void f7LongPressTogglesSosOnceAfterThreeSeconds() {
        FakeVideoRecordingUseCaseImpl videos = new FakeVideoRecordingUseCaseImpl();
        HardwareButtonRouter router = router(new FakePhotoCaptureUseCaseImpl(), videos);
        assertTrue(router.onKeyDown(KeyEvent.KEYCODE_F7, 0, 1000L));
        assertTrue(router.onKeyDown(KeyEvent.KEYCODE_F7, 1, 3999L));
        assertEquals(0, videos.sosToggles);
        assertTrue(router.onKeyDown(KeyEvent.KEYCODE_F7, 2, 4000L));
        assertTrue(router.onKeyDown(KeyEvent.KEYCODE_F7, 3, 4500L));
        assertTrue(router.onKeyUp(KeyEvent.KEYCODE_F7));
        assertEquals(1, videos.sosStarts);
    }

    @Test public void f7HoldTimerTogglesSosWithoutKeyRepeat() {
        FakeVideoRecordingUseCaseImpl videos = new FakeVideoRecordingUseCaseImpl();
        HardwareButtonRouter router = router(new FakePhotoCaptureUseCaseImpl(), videos);

        assertTrue(router.onKeyDown(KeyEvent.KEYCODE_F7, 0, 1000L));
        assertTrue(router.onSosHoldThreshold(KeyEvent.KEYCODE_F7));
        assertFalse(router.onSosHoldThreshold(KeyEvent.KEYCODE_F7));

        assertEquals(1, videos.sosStarts);
    }

    @Test public void f7HoldStopsSosThroughCommonStopPath() {
        FakeVideoRecordingUseCaseImpl videos = new FakeVideoRecordingUseCaseImpl();
        videos.mode = RecordingMode.SOS;
        HardwareButtonRouter router = new HardwareButtonRouter(
                new FakePhotoCaptureUseCaseImpl(), videos,
                new FakeAudioRecordingUseCaseImpl(), null, null, bwcLayout(),
                (recording, fileName) -> {});

        assertTrue(router.onKeyDown(KeyEvent.KEYCODE_F7, 0, 1000L));
        assertTrue(router.onSosHoldThreshold(KeyEvent.KEYCODE_F7));

        assertEquals(1, videos.stops);
    }

    @Test public void unknownKeyIgnored() {
        HardwareButtonRouter router = router(new FakePhotoCaptureUseCaseImpl(), new FakeVideoRecordingUseCaseImpl());
        assertFalse(router.onKeyDown(KeyEvent.KEYCODE_A, 0, 0L));
    }

    @Test public void bodyCameraUsesFKeysWithoutBwcReleaseStop() {
        FakePhotoCaptureUseCaseImpl photos = new FakePhotoCaptureUseCaseImpl();
        FakeVideoRecordingUseCaseImpl videos = new FakeVideoRecordingUseCaseImpl();
        FakeAudioRecordingUseCaseImpl audio = new FakeAudioRecordingUseCaseImpl();
        boolean[] audioRecording = { false };
        HardwareButtonRouter router = new HardwareButtonRouter(
                photos, videos, audio, null, null, HardwareButtonProfiles.resolve(
                        new HardwareDeviceIdentity("BodyCamera", "k69v1_64_k419", "mt6768")),
                (recording, fileName) -> audioRecording[0] = recording);

        assertTrue(router.onKeyDown(KeyEvent.KEYCODE_F5, 0, 0L));
        assertTrue(router.onKeyUp(KeyEvent.KEYCODE_F5));
        assertTrue(router.onKeyDown(KeyEvent.KEYCODE_F1, 0, 0L));
        assertTrue(router.onKeyDown(KeyEvent.KEYCODE_F2, 0, 0L));
        assertTrue(router.onKeyDown(KeyEvent.KEYCODE_F3, 0, 0L));
        assertTrue(audioRecording[0]);
        assertTrue(router.onKeyUp(KeyEvent.KEYCODE_F3));
        assertTrue(router.onKeyDown(KeyEvent.KEYCODE_F3, 0, 1L));
        assertFalse(router.onKeyDown(KeyEvent.KEYCODE_F4, 0, 0L));
        assertEquals(1, videos.starts);
        assertEquals(0, videos.stops);
        assertEquals(1, videos.sosStarts);
        assertEquals(1, photos.photos);
        assertEquals(2, audio.toggles);
        assertFalse(audioRecording[0]);
    }

    @Test public void recordButtonStopsImportantRecordingWithoutStartingVideo() {
        FakeVideoRecordingUseCaseImpl videos = new FakeVideoRecordingUseCaseImpl();
        videos.mode = RecordingMode.SOS;
        HardwareButtonRouter router = bodyCameraRouter(videos);

        assertTrue(router.onKeyDown(KeyEvent.KEYCODE_F5, 0, 0L));

        assertEquals(1, videos.stops);
        assertEquals(0, videos.starts);
    }

    @Test public void importantButtonStopsVideoWithoutStartingImportantRecording() {
        FakeVideoRecordingUseCaseImpl videos = new FakeVideoRecordingUseCaseImpl();
        videos.mode = RecordingMode.VIDEO;
        HardwareButtonRouter router = bodyCameraRouter(videos);

        assertTrue(router.onKeyDown(KeyEvent.KEYCODE_F1, 0, 0L));

        assertEquals(0, videos.sosStarts);
        assertEquals(1, videos.stops);
    }

    @Test public void allThreePropertiesMustMatchDeviceProfile() {
        HardwareButtonLayout bodyCamera = HardwareButtonProfiles.resolve(
                new HardwareDeviceIdentity("BodyCamera", "k69v1_64_k419", "mt6768"));
        HardwareButtonLayout wrongPlatform = HardwareButtonProfiles.resolve(
                new HardwareDeviceIdentity("BodyCamera", "k69v1_64_k419", "other"));
        HardwareButtonLayout bwc = HardwareButtonProfiles.resolve(
                new HardwareDeviceIdentity("BWC", "k69v1_64_k419", "mt6768"));

        HardwareButtonBinding bodyCameraRecord =
                bodyCamera.findByButtonKeyCode(KeyEvent.KEYCODE_F5);
        HardwareButtonBinding bwcRecord = bwc.findByButtonKeyCode(KeyEvent.KEYCODE_F10);
        assertEquals(ButtonRole.RECORD, bodyCameraRecord.role());
        assertEquals(PhysicalButtonType.BUTTON, bodyCameraRecord.type());
        assertEquals(ButtonRole.RECORD, bwcRecord.role());
        assertEquals(PhysicalButtonType.SWITCH, bwcRecord.type());
        assertTrue(bodyCamera.has(ButtonRole.IMPORTANT_RECORDING));
        assertFalse(bwc.has(ButtonRole.IMPORTANT_RECORDING));
        assertTrue(wrongPlatform.findByButtonKeyCode(KeyEvent.KEYCODE_F5) == null);
    }

    @Test public void disabledCameraKeyIsConsumedWithoutTakingPhoto() {
        FakePhotoCaptureUseCaseImpl photos = new FakePhotoCaptureUseCaseImpl();
        FakeFeatureGateSettingsUseCaseImpl gates = new FakeFeatureGateSettingsUseCaseImpl();
        gates.setEnabled(FeatureGate.IMAGE_CAPTURE, false);
        HardwareButtonRouter router = new HardwareButtonRouter(
                photos, new FakeVideoRecordingUseCaseImpl(), new FakeAudioRecordingUseCaseImpl(),
                gates, null, bwcLayout(), (recording, fileName) -> {});

        assertTrue(router.onKeyDown(KeyEvent.KEYCODE_CAMERA, 0, 0L));
        assertEquals(0, photos.photos);
    }

    private static HardwareButtonRouter router(
            FakePhotoCaptureUseCaseImpl photos, FakeVideoRecordingUseCaseImpl videos) {
        return new HardwareButtonRouter(
                photos, videos, new FakeAudioRecordingUseCaseImpl(), null, null, bwcLayout(),
                (recording, fileName) -> {});
    }

    private static HardwareButtonRouter bodyCameraRouter(FakeVideoRecordingUseCaseImpl videos) {
        return new HardwareButtonRouter(
                new FakePhotoCaptureUseCaseImpl(), videos, new FakeAudioRecordingUseCaseImpl(),
                null, null, HardwareButtonProfiles.resolve(new HardwareDeviceIdentity(
                        "BodyCamera", "k69v1_64_k419", "mt6768")),
                (recording, fileName) -> {});
    }

    private static HardwareButtonLayout bwcLayout() {
        return HardwareButtonProfiles.resolve(
                new HardwareDeviceIdentity("BWC", "k69v1_64_k419", "mt6768"));
    }

    private static final class FakePhotoCaptureUseCaseImpl implements PhotoCaptureUseCase {
        int photos;

        @Override public void takePhoto() { photos++; }
    }

    private static final class FakeVideoRecordingUseCaseImpl implements VideoRecordingUseCase {
        int starts;
        int stops;
        int sosStarts;
        int sosToggles;
        int videoToggles;
        RecordingMode mode = RecordingMode.IDLE;

        @Override public void toggleVideo() { videoToggles++; }
        @Override public void startVideo() { starts++; }
        @Override public void startSos() { sosStarts++; }
        @Override public void stopRecording() { stops++; }
        @Override public void toggleSos() { sosToggles++; }
        @Override public RecordingMode currentMode() { return mode; }
    }

    private static final class FakeAudioRecordingUseCaseImpl implements AudioRecordingUseCase {
        int toggles;
        boolean recording;

        @Override public String toggleAudio() { toggles++; recording = !recording; return null; }
        @Override public boolean isAudioRecording() { return recording; }
    }

    private static final class FakeFeatureGateSettingsUseCaseImpl implements FeatureGateSettingsUseCase {
        private final Map<FeatureGate, Boolean> enabled = new EnumMap<>(FeatureGate.class);

        @Override public boolean isEnabled(FeatureGate feature) {
            return enabled.getOrDefault(feature, true);
        }

        @Override public void setEnabled(FeatureGate feature, boolean enabled) {
            this.enabled.put(feature, enabled);
        }
    }
}
