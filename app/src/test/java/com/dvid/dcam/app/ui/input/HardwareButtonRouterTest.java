package com.dvid.dcam.app.ui.input;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import android.view.KeyEvent;
import com.dvid.dcam.core.featuregate.application.FeatureGates;
import com.dvid.dcam.core.featuregate.domain.FeatureGate;
import com.dvid.dcam.core.featuregate.application.port.FeatureGateStore;
import com.dvid.dcam.core.input.domain.ButtonRole;
import com.dvid.dcam.core.input.domain.FirmwareButtonActionFamily;
import com.dvid.dcam.core.input.domain.HardwareButtonBinding;
import com.dvid.dcam.core.input.domain.HardwareButtonInputSource;
import com.dvid.dcam.core.input.domain.HardwareButtonLayout;
import com.dvid.dcam.core.input.domain.PhysicalButtonType;
import com.dvid.dcam.feature.capture.application.port.AudioRecorder;
import com.dvid.dcam.platform.input.HardwareButtonProfiles;
import com.dvid.dcam.platform.input.HardwareDeviceIdentity;
import com.dvid.dcam.feature.capture.application.port.CameraGateway;
import com.dvid.dcam.feature.capture.application.usecase.AudioRecordingUseCase;
import com.dvid.dcam.feature.capture.application.usecase.PhotoCaptureUseCase;
import com.dvid.dcam.feature.capture.application.usecase.RecordingCommands;
import com.dvid.dcam.feature.capture.application.usecase.SerializedRecordingCoordinator;
import com.dvid.dcam.feature.auth.application.repository.OperatorSessionMemory;
import com.dvid.dcam.feature.auth.application.usecase.OperatorSessionUseCase;
import com.dvid.dcam.feature.auth.domain.OperatorSession;
import com.dvid.dcam.feature.capture.domain.RecordingMode;
import java.util.EnumMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class HardwareButtonRouterTest {
    @Test public void cameraKeyTakesPhoto() {
        FakePhotoCapture photos = new FakePhotoCapture();
        HardwareButtonRouter router = router(photos, new FakeRecordingCommands());
        assertTrue(router.onKeyDown(KeyEvent.KEYCODE_CAMERA, 0, 0L));
        assertEquals(1, photos.photos);
    }

    @Test public void f2TakesPhoto() {
        FakePhotoCapture photos = new FakePhotoCapture();
        HardwareButtonRouter router = router(photos, new FakeRecordingCommands());
        assertTrue(router.onKeyDown(KeyEvent.KEYCODE_F2, 0, 0L));
        assertEquals(1, photos.photos);
    }

    @Test public void firmwarePhotoBindingIgnoresKeyEventAndHandlesBroadcast() {
        FakePhotoCapture photos = new FakePhotoCapture();
        HardwareButtonRouter router = firmwareRouter(
                ButtonRole.PHOTO_CAPTURE,
                KeyEvent.KEYCODE_F2,
                PhysicalButtonType.BUTTON,
                FirmwareButtonActionFamily.ACTION_CAMERA,
                photos,
                new FakeRecordingCommands());

        assertFalse(router.onKeyDown(KeyEvent.KEYCODE_F2, 0, 1000L));
        assertTrue(router.onFirmwareBroadcastDown(
                FirmwareButtonActionFamily.ACTION_CAMERA.downAction(), 0, 1000L));
        assertTrue(router.onFirmwareBroadcastUp(
                FirmwareButtonActionFamily.ACTION_CAMERA.upAction(), false));
        assertEquals(1, photos.photos);
    }

    @Test public void firmwareButtonRecordTogglesOnDownAndIgnoresUp() {
        FakeRecordingCommands videos = new FakeRecordingCommands();
        HardwareButtonRouter router = firmwareRouter(
                ButtonRole.RECORD,
                KeyEvent.KEYCODE_F5,
                PhysicalButtonType.BUTTON,
                FirmwareButtonActionFamily.ACTION_VIDEO,
                new FakePhotoCapture(),
                videos);

        assertTrue(router.onFirmwareBroadcastDown(
                FirmwareButtonActionFamily.ACTION_VIDEO.downAction(), 0, 1000L));
        assertTrue(router.onFirmwareBroadcastUp(
                FirmwareButtonActionFamily.ACTION_VIDEO.upAction(), false));
        assertEquals(1, videos.starts);
        assertEquals(0, videos.stops);

        videos.mode = RecordingMode.VIDEO;
        assertTrue(router.onFirmwareBroadcastDown(
                FirmwareButtonActionFamily.ACTION_VIDEO.downAction(), 0, 2000L));
        assertEquals(1, videos.stops);
    }

    @Test public void firmwareSwitchRecordStartsOnDownAndStopsOnUp() {
        FakeRecordingCommands videos = new FakeRecordingCommands();
        HardwareButtonRouter router = firmwareRouter(
                ButtonRole.RECORD,
                KeyEvent.KEYCODE_F10,
                PhysicalButtonType.SWITCH,
                FirmwareButtonActionFamily.ACTION_VIDEO,
                new FakePhotoCapture(),
                videos);

        assertTrue(router.onFirmwareBroadcastDown(
                FirmwareButtonActionFamily.ACTION_VIDEO.downAction(), 0, 1000L));
        assertTrue(router.onFirmwareBroadcastDown(
                FirmwareButtonActionFamily.ACTION_VIDEO.downAction(), 0, 1001L));
        assertTrue(router.onFirmwareBroadcastUp(
                FirmwareButtonActionFamily.ACTION_VIDEO.upAction(), false));
        assertEquals(1, videos.starts);
        assertEquals(1, videos.stops);
    }

    @Test public void keySourceBindingIgnoresFirmwareBroadcast() {
        FakeRecordingCommands videos = new FakeRecordingCommands();
        HardwareButtonRouter router = bodyCameraRouter(new FakePhotoCapture(), videos);

        assertFalse(router.onFirmwareBroadcastDown(
                FirmwareButtonActionFamily.ACTION_VIDEO.downAction(), 0, 1000L));
        assertTrue(router.onKeyDown(KeyEvent.KEYCODE_F5, 0, 1000L));
        assertEquals(1, videos.starts);
    }

    @Test public void f10SwitchStartsOnDownAndStopsOnUp() {
        FakeRecordingCommands videos = new FakeRecordingCommands();
        HardwareButtonRouter router = router(new FakePhotoCapture(), videos);
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
        FakeRecordingCommands videos = new FakeRecordingCommands();
        HardwareButtonRouter router = router(new FakePhotoCapture(), videos);

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
        FakeRecordingCommands videos = new FakeRecordingCommands();
        HardwareButtonRouter router = router(new FakePhotoCapture(), videos);

        assertTrue(router.onKeyDown(KeyEvent.KEYCODE_F10, 1, 1000L));
        assertTrue(router.onKeyUp(KeyEvent.KEYCODE_F10));
        assertTrue(router.onKeyDown(KeyEvent.KEYCODE_F10, 2, 2000L));
        assertTrue(router.onKeyUp(KeyEvent.KEYCODE_F10));

        assertEquals(2, videos.starts);
        assertEquals(2, videos.stops);
    }

    @Test public void focusLossKeepsSwitchLatchedUntilRelease() {
        FakeRecordingCommands videos = new FakeRecordingCommands();
        HardwareButtonRouter router = router(new FakePhotoCapture(), videos);

        assertTrue(router.onKeyDown(KeyEvent.KEYCODE_F10, 0, 1000L));
        router.clearFocusTransientState();
        assertTrue(router.onKeyDown(KeyEvent.KEYCODE_F10, 0, 1001L));
        assertTrue(router.onKeyUp(KeyEvent.KEYCODE_F10));

        assertEquals(1, videos.starts);
        assertEquals(1, videos.stops);
    }

    @Test public void realSwitchUpStopsAfterFocusLoss() {
        FakeRecordingCommands videos = new FakeRecordingCommands();
        HardwareButtonRouter router = router(new FakePhotoCapture(), videos);

        assertTrue(router.onKeyDown(KeyEvent.KEYCODE_F10, 0, 1000L));
        router.clearFocusTransientState();
        assertTrue(router.onKeyUp(KeyEvent.KEYCODE_F10));
        assertEquals(1, videos.stops);
    }

    @Test public void canceledSwitchUpDoesNotStopRecording() {
        FakeRecordingCommands videos = new FakeRecordingCommands();
        HardwareButtonRouter router = router(new FakePhotoCapture(), videos);

        assertTrue(router.onKeyDown(KeyEvent.KEYCODE_F10, 0, 1000L));
        assertTrue(router.onKeyUp(KeyEvent.KEYCODE_F10, true));
        assertEquals(0, videos.stops);
        assertTrue(router.onKeyUp(KeyEvent.KEYCODE_F10));
        assertEquals(1, videos.stops);
    }

    @Test public void clearingTransientStateRecoversFromMissingSwitchUp() {
        FakeRecordingCommands videos = new FakeRecordingCommands();
        HardwareButtonRouter router = router(new FakePhotoCapture(), videos);

        assertTrue(router.onKeyDown(KeyEvent.KEYCODE_F10, 0, 1000L));
        router.clearTransientState();
        assertTrue(router.onKeyDown(KeyEvent.KEYCODE_F10, 0, 2000L));

        assertEquals(2, videos.starts);
    }

    @Test public void f7LongPressStartsImpOnceAfterTwoSeconds() {
        FakeRecordingCommands videos = new FakeRecordingCommands();
        HardwareButtonRouter router = router(new FakePhotoCapture(), videos);
        assertTrue(router.onKeyDown(KeyEvent.KEYCODE_F7, 0, 1000L));
        assertTrue(router.onKeyDown(KeyEvent.KEYCODE_F7, 1, 2999L));
        assertEquals(0, videos.impToggles);
        assertTrue(router.onKeyDown(KeyEvent.KEYCODE_F7, 2, 3000L));
        assertTrue(router.onKeyDown(KeyEvent.KEYCODE_F7, 3, 4500L));
        assertTrue(router.onKeyUp(KeyEvent.KEYCODE_F7));
        assertEquals(1, videos.impStarts);
    }

    @Test public void f7HoldTimerStartsImpWithoutKeyRepeat() {
        FakeRecordingCommands videos = new FakeRecordingCommands();
        HardwareButtonRouter router = router(new FakePhotoCapture(), videos);

        assertTrue(router.onKeyDown(KeyEvent.KEYCODE_F7, 0, 1000L));
        assertTrue(router.onSosHoldThreshold(KeyEvent.KEYCODE_F7));
        assertFalse(router.onSosHoldThreshold(KeyEvent.KEYCODE_F7));

        assertEquals(1, videos.impStarts);
    }

    @Test public void f7HoldStopsImpThroughCommonStopPath() {
        FakeRecordingCommands videos = new FakeRecordingCommands();
        videos.mode = RecordingMode.IMP;
        HardwareButtonRouter router = new HardwareButtonRouter(
                new FakePhotoCapture().useCase, videos,
                new FakeAudioRecording().useCase, enabledGates(), activeOperatorSession(), bwcLayout());

        assertTrue(router.onKeyDown(KeyEvent.KEYCODE_F7, 0, 1000L));
        assertTrue(router.onSosHoldThreshold(KeyEvent.KEYCODE_F7));

        assertEquals(1, videos.stops);
    }

    @Test public void unknownKeyIgnored() {
        HardwareButtonRouter router = router(new FakePhotoCapture(), new FakeRecordingCommands());
        assertFalse(router.onKeyDown(KeyEvent.KEYCODE_A, 0, 0L));
    }

    @Test public void audioButtonStartsDuringVideo() {
        FakeRecordingCommands videos = new FakeRecordingCommands();
        videos.mode = RecordingMode.VIDEO;
        FakeAudioRecording audio = new FakeAudioRecording();
        HardwareButtonRouter router = new HardwareButtonRouter(
                new FakePhotoCapture().useCase, videos, audio.useCase, enabledGates(),
                activeOperatorSession(), bwcLayout());

        assertTrue(router.onKeyDown(KeyEvent.KEYCODE_F3, 0, 0L));

        assertEquals(1, audio.toggles);
        assertTrue(audio.recording);
    }

    @Test public void bodyCameraUsesFKeysWithoutBwcReleaseStop() {
        FakePhotoCapture photos = new FakePhotoCapture();
        FakeRecordingCommands videos = new FakeRecordingCommands();
        FakeAudioRecording audio = new FakeAudioRecording();
        HardwareButtonRouter router = new HardwareButtonRouter(
                photos.useCase, videos, audio.useCase, enabledGates(), activeOperatorSession(), HardwareButtonProfiles.resolve(
                        new HardwareDeviceIdentity("BodyCamera", "k69v1_64_k419", "mt6768")));

        assertTrue(router.onKeyDown(KeyEvent.KEYCODE_F5, 0, 0L));
        assertTrue(router.onKeyUp(KeyEvent.KEYCODE_F5));
        assertTrue(router.onKeyDown(KeyEvent.KEYCODE_F1, 0, 0L));
        assertTrue(router.onKeyDown(KeyEvent.KEYCODE_F2, 0, 0L));
        assertTrue(router.onKeyDown(KeyEvent.KEYCODE_F3, 0, 0L));
        assertTrue(audio.recording);
        assertTrue(router.onKeyUp(KeyEvent.KEYCODE_F3));
        assertTrue(router.onKeyDown(KeyEvent.KEYCODE_F3, 0, 1L));
        assertFalse(router.onKeyDown(KeyEvent.KEYCODE_F4, 0, 0L));
        assertEquals(1, videos.starts);
        assertEquals(0, videos.stops);
        assertEquals(1, videos.impStarts);
        assertEquals(1, photos.photos);
        assertEquals(2, audio.toggles);
        assertFalse(audio.recording);
    }

    @Test public void recordButtonStopsImportantRecordingWithoutStartingVideo() {
        FakeRecordingCommands videos = new FakeRecordingCommands();
        videos.mode = RecordingMode.IMP;
        HardwareButtonRouter router = bodyCameraRouter(videos);

        assertTrue(router.onKeyDown(KeyEvent.KEYCODE_F5, 0, 0L));

        assertEquals(1, videos.stops);
        assertEquals(0, videos.starts);
    }

    @Test public void importantButtonStopsVideoWithoutStartingImportantRecording() {
        FakeRecordingCommands videos = new FakeRecordingCommands();
        videos.mode = RecordingMode.VIDEO;
        HardwareButtonRouter router = bodyCameraRouter(videos);

        assertTrue(router.onKeyDown(KeyEvent.KEYCODE_F1, 0, 0L));

        assertEquals(0, videos.impStarts);
        assertEquals(1, videos.stops);
    }

    @Test public void recordAndImportantPressesDoNotHapticWhenPendingStartIsIgnored() {
        FakeRecordingCommands videos = new FakeRecordingCommands();
        videos.mode = RecordingMode.VIDEO;
        videos.startPending = true;
        int[] haptics = {0};
        HardwareButtonRouter router = new HardwareButtonRouter(
                new FakePhotoCapture().useCase, videos, new FakeAudioRecording().useCase,
                enabledGates(), activeOperatorSession(),
                HardwareButtonProfiles.resolve(new HardwareDeviceIdentity(
                        "BodyCamera", "k69v1_64_k419", "mt6768")),
                () -> {}, () -> haptics[0]++);

        assertTrue(router.onKeyDown(KeyEvent.KEYCODE_F5, 0, 0L));
        assertTrue(router.onKeyDown(KeyEvent.KEYCODE_F1, 0, 1L));

        assertEquals(0, videos.starts);
        assertEquals(0, videos.impStarts);
        assertEquals(0, videos.stops);
        assertEquals(0, haptics[0]);
    }

    @Test public void commandHapticRunsForCommandsNotRawButtonEvents() {
        int[] haptics = {0};
        HardwareButtonRouter router = router(
                new FakePhotoCapture(), new FakeRecordingCommands(), () -> haptics[0]++);

        assertTrue(router.onKeyDown(KeyEvent.KEYCODE_CAMERA, 0, 0L));
        assertEquals(1, haptics[0]);
        assertTrue(router.onKeyDown(KeyEvent.KEYCODE_CAMERA, 1, 1L));
        assertEquals(1, haptics[0]);
        assertTrue(router.onKeyDown(KeyEvent.KEYCODE_F10, 1, 1000L));
        assertEquals(2, haptics[0]);
        assertTrue(router.onKeyDown(KeyEvent.KEYCODE_F10, 0, 1001L));
        assertEquals(2, haptics[0]);
        assertTrue(router.onKeyUp(KeyEvent.KEYCODE_F10));
        assertEquals(3, haptics[0]);
        assertTrue(router.onKeyUp(KeyEvent.KEYCODE_F10));
        assertEquals(3, haptics[0]);
        assertTrue(router.onKeyDown(KeyEvent.KEYCODE_F3, 0, 2000L));
        assertEquals(4, haptics[0]);
    }

    @Test public void sosHapticRunsOnlyWhenHoldCommandInitializes() {
        int[] haptics = {0};
        HardwareButtonRouter router = router(
                new FakePhotoCapture(), new FakeRecordingCommands(), () -> haptics[0]++);

        assertTrue(router.onKeyDown(KeyEvent.KEYCODE_F7, 0, 1000L));
        assertEquals(0, haptics[0]);
        assertTrue(router.onKeyDown(KeyEvent.KEYCODE_F7, 1, 2999L));
        assertEquals(0, haptics[0]);
        assertTrue(router.onSosHoldThreshold(KeyEvent.KEYCODE_F7));
        assertEquals(1, haptics[0]);
        assertFalse(router.onSosHoldThreshold(KeyEvent.KEYCODE_F7));
        assertEquals(1, haptics[0]);
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

    @Test public void disabledAudioGateStillAllowsStoppingActiveAudio() {
        FakeAudioRecording audio = new FakeAudioRecording();
        audio.recording = true;
        FeatureGates gates = new FeatureGates(new InMemoryFeatureGateStore());
        gates.setEnabled(FeatureGate.AUDIO_CAPTURE, false);
        HardwareButtonRouter router = new HardwareButtonRouter(
                new FakePhotoCapture().useCase, new FakeRecordingCommands(), audio.useCase,
                gates, activeOperatorSession(), bwcLayout());

        assertTrue(router.onKeyDown(KeyEvent.KEYCODE_F3, 0, 0L));
        assertFalse(audio.recording);
        assertEquals(1, audio.toggles);
    }

    @Test public void disabledVideoGateStillAllowsStoppingActiveRecording() {
        FakeRecordingCommands videos = new FakeRecordingCommands();
        videos.mode = RecordingMode.VIDEO;
        FeatureGates gates = new FeatureGates(new InMemoryFeatureGateStore());
        gates.setEnabled(FeatureGate.VIDEO_CAPTURE, false);
        HardwareButtonRouter router = new HardwareButtonRouter(
                new FakePhotoCapture().useCase, videos, new FakeAudioRecording().useCase,
                gates, activeOperatorSession(), HardwareButtonProfiles.resolve(new HardwareDeviceIdentity(
                        "BodyCamera", "k69v1_64_k419", "mt6768")));

        assertTrue(router.onKeyDown(KeyEvent.KEYCODE_F5, 0, 0L));
        assertEquals(1, videos.stops);
    }

    @Test public void disabledVideoGateStillAllowsStoppingActiveSosRecording() {
        FakeRecordingCommands videos = new FakeRecordingCommands();
        videos.mode = RecordingMode.IMP;
        FeatureGates gates = new FeatureGates(new InMemoryFeatureGateStore());
        gates.setEnabled(FeatureGate.VIDEO_CAPTURE, false);
        HardwareButtonRouter router = new HardwareButtonRouter(
                new FakePhotoCapture().useCase, videos, new FakeAudioRecording().useCase,
                gates, activeOperatorSession(), bwcLayout());

        assertTrue(router.onKeyDown(KeyEvent.KEYCODE_F7, 0, 0L));
        assertTrue(router.onKeyDown(KeyEvent.KEYCODE_F7, 1, HardwareButtonRouter.SOS_HOLD_MS));
        assertEquals(1, videos.stops);
    }

    @Test public void disabledCameraKeyIsConsumedWithoutTakingPhoto() {
        FakePhotoCapture photos = new FakePhotoCapture();
        FeatureGates gates = new FeatureGates(new InMemoryFeatureGateStore());
        gates.setEnabled(FeatureGate.IMAGE_CAPTURE, false);
        int[] haptics = {0};
        HardwareButtonRouter router = new HardwareButtonRouter(
                photos.useCase, new FakeRecordingCommands(), new FakeAudioRecording().useCase,
                gates, activeOperatorSession(), bwcLayout(), () -> {}, () -> haptics[0]++);

        assertTrue(router.onKeyDown(KeyEvent.KEYCODE_CAMERA, 0, 0L));
        assertEquals(0, photos.photos);
        assertEquals(0, haptics[0]);
    }

    private static HardwareButtonRouter router(
            FakePhotoCapture photos, FakeRecordingCommands videos) {
        return router(photos, videos, () -> {});
    }

    private static HardwareButtonRouter router(
            FakePhotoCapture photos, FakeRecordingCommands videos, Runnable commandHaptic) {
        return new HardwareButtonRouter(
                photos.useCase, videos, new FakeAudioRecording().useCase,
                enabledGates(), activeOperatorSession(), bwcLayout(), () -> {}, commandHaptic);
    }

    private static HardwareButtonRouter bodyCameraRouter(FakeRecordingCommands videos) {
        return bodyCameraRouter(new FakePhotoCapture(), videos);
    }

    private static HardwareButtonRouter bodyCameraRouter(
            FakePhotoCapture photos, FakeRecordingCommands videos) {
        return new HardwareButtonRouter(
                photos.useCase, videos, new FakeAudioRecording().useCase,
                enabledGates(), activeOperatorSession(),
                HardwareButtonProfiles.resolve(new HardwareDeviceIdentity(
                        "BodyCamera", "k69v1_64_k419", "mt6768")));
    }

    private static HardwareButtonRouter firmwareRouter(
            ButtonRole role,
            int keyCode,
            PhysicalButtonType type,
            FirmwareButtonActionFamily family,
            FakePhotoCapture photos,
            FakeRecordingCommands videos) {
        HardwareButtonBinding binding = new HardwareButtonBinding(
                role, keyCode, type, HardwareButtonInputSource.FIRMWARE_BROADCAST, family);
        return new HardwareButtonRouter(
                photos.useCase, videos, new FakeAudioRecording().useCase,
                enabledGates(), activeOperatorSession(), new HardwareButtonLayout(binding));
    }

    @Test public void missingFeatureDependenciesAreRejected() {
        assertThrows(NullPointerException.class, () -> new HardwareButtonRouter(
                new FakePhotoCapture().useCase, new FakeRecordingCommands(),
                new FakeAudioRecording().useCase, null, activeOperatorSession(),
                bwcLayout()));
        assertThrows(NullPointerException.class, () -> new HardwareButtonRouter(
                new FakePhotoCapture().useCase, new FakeRecordingCommands(),
                new FakeAudioRecording().useCase, enabledGates(), null,
                bwcLayout()));
    }

    private static FeatureGates enabledGates() {
        return new FeatureGates(new InMemoryFeatureGateStore());
    }

    private static OperatorSessionUseCase activeOperatorSession() {
        OperatorSessionMemory memory = new OperatorSessionMemory();
        memory.set(new OperatorSession("session", "000001", "000001", "Test", "boot", 1L));
        return new OperatorSessionUseCase(null, null, memory);
    }

    private static HardwareButtonLayout bwcLayout() {
        return HardwareButtonProfiles.resolve(
                new HardwareDeviceIdentity("BWC", "k69v1_64_k419", "mt6768"));
    }

    private static final class FakePhotoCapture {
        private int photos;
        private final PhotoCaptureUseCase useCase = new PhotoCaptureUseCase(new CameraGateway() {
            @Override public void takePhoto() { photos++; }
            @Override public void startVideo() {}
            @Override public void startImp() {}
            @Override public void stopRecording() {}
        });
    }

    private static final class FakeRecordingCommands implements RecordingCommands {
        int starts;
        int stops;
        int impStarts;
        int impToggles;
        int videoToggles;
        RecordingMode mode = RecordingMode.IDLE;
        boolean startPending;

        @Override public void toggleVideo() { videoToggles++; }
        @Override public void startVideo() { starts++; }
        @Override public void startImp() { impStarts++; }
        @Override public void stopRecording() { stops++; }
        @Override public void toggleImp() { impToggles++; }
        @Override public RecordingMode currentMode() { return mode; }
        @Override public boolean isRecordingStartPending() { return startPending; }
    }

    private static final class FakeAudioRecording {
        private int toggles;
        private boolean recording;
        private final AudioRecordingUseCase useCase = new AudioRecordingUseCase(
                new AudioRecorder() {
                    @Override public String toggle() {
                        toggles++;
                        recording = !recording;
                        return null;
                    }
                    @Override public boolean isRecording() { return recording; }
                    @Override public long recordingStartedAtMillis() {
                        return recording ? 1L : -1L;
                    }
                    @Override public void release() {}
                }, new SerializedRecordingCoordinator(Runnable::run));
    }

    private static final class InMemoryFeatureGateStore implements FeatureGateStore {
        private final Map<FeatureGate, Boolean> enabled = new EnumMap<>(FeatureGate.class);

        @Override public boolean isEnabled(FeatureGate feature) {
            return enabled.getOrDefault(feature, feature.defaultEnabled());
        }

        @Override public void setEnabled(FeatureGate feature, boolean enabled) {
            this.enabled.put(feature, enabled);
        }
    }
}
