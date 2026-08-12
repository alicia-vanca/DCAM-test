package com.dvid.dcam.platform.device.capability;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.dvid.dcam.feature.device.application.port.CameraCapabilityStore;
import com.dvid.dcam.feature.device.application.port.CameraCapabilityStore.CameraSnapshot;
import com.dvid.dcam.feature.device.application.port.CameraCapabilityStore.CodecSnapshot;
import com.dvid.dcam.feature.device.application.port.CameraCapabilityStore.CodecState;
import com.dvid.dcam.feature.device.application.port.CameraCapabilityStore.SelectedRecordingProfile;
import com.dvid.dcam.feature.device.application.port.CameraCapabilityStore.Snapshot;
import com.dvid.dcam.feature.device.domain.CaptureQuality;
import com.dvid.dcam.feature.device.domain.camera.CameraId;
import com.dvid.dcam.feature.device.domain.camera.CameraResolution;
import com.dvid.dcam.feature.device.domain.camera.CandidateEvidence;
import com.dvid.dcam.feature.device.domain.camera.CandidateKey;
import com.dvid.dcam.feature.device.domain.camera.CaptureModeTuple;
import com.dvid.dcam.feature.device.domain.camera.ImageMode;
import com.dvid.dcam.feature.device.domain.camera.PipelineAvailability;
import com.dvid.dcam.feature.device.domain.camera.PipelineEvidence;
import com.dvid.dcam.feature.device.domain.camera.StandardResolution;
import com.dvid.dcam.feature.device.domain.camera.StandardResolutionLabel;
import com.dvid.dcam.feature.device.domain.camera.VerificationOutcome;
import com.dvid.dcam.feature.device.domain.camera.VerificationPipelineId;
import com.dvid.dcam.feature.device.domain.camera.VideoCodec;
import com.dvid.dcam.feature.device.domain.camera.VideoMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class CameraCapabilityOptionsTest {
    private static final CameraId CAMERA = new CameraId("0");
    private static final VerificationPipelineId PIPELINE =
            new VerificationPipelineId("a-camera2-native-surface-sharing-v1");
    private static final VideoMode FHD_20 = video(StandardResolutionLabel.FHD, 1920, 1080, 20);
    private static final VideoMode FHD_30 = video(StandardResolutionLabel.FHD, 1920, 1080, 30);
    private static final VideoMode FHD_ALIGNED_60 = video(
            StandardResolutionLabel.FHD, 1920, 1088, 60);
    private static final VideoMode HD_30 = video(StandardResolutionLabel.HD, 1280, 720, 30);
    private static final ImageMode FHD = image(StandardResolutionLabel.FHD, 1920, 1080);
    private static final ImageMode FHD_IMAGE_ALIGNED = image(
            StandardResolutionLabel.FHD, 1920, 1088);
    private static final ImageMode HD = image(StandardResolutionLabel.HD, 1280, 720);
    private static final ImageMode SD = image(StandardResolutionLabel.SD, 720, 480);

    @Test void videoFallbackLowersFpsBeforeVideoResolution() {
        List<CaptureQuality> fallbacks = CameraCapabilityOptions.videoFallbacks(
                snapshot(), "0", "FHD", 30, Set.of());

        assertEquals(List.of(
                new CaptureQuality("FHD", 1920, 1080, 30),
                new CaptureQuality("FHD", 1920, 1080, 20),
                new CaptureQuality("HD", 1280, 720, 30)), fallbacks);
    }

    @Test void comboFailureDoesNotRemoveStandaloneImageOption() {
        Snapshot snapshot = snapshot();

        assertEquals(List.of("SD", "HD", "FHD"),
                CameraCapabilityOptions.imageQualities(snapshot, "0", Set.of()).stream()
                        .map(CaptureQuality::getId)
                        .toList());
        assertEquals(List.of("HD", "SD"),
                CameraCapabilityOptions.imageFallbacks(snapshot, "0", "FHD", 30,
                        "FHD", Set.of()).stream().map(CaptureQuality::getId).toList());
    }

    @Test void runtimeBridgeRejectionDoesNotBecomeCapabilityEvidence() {
        Snapshot snapshot = snapshot();
        Set<String> runtimeRejections = Set.of("0|FHD|30|HD");

        assertEquals(List.of("SD"),
                CameraCapabilityOptions.imageFallbacks(snapshot, "0", "FHD", 30,
                        "FHD", runtimeRejections).stream()
                        .map(CaptureQuality::getId).toList());
        assertEquals(VerificationOutcome.UNKNOWN, pipeline(snapshot).outcome(
                CandidateKey.forTuple(CAMERA, VideoCodec.H264, PIPELINE,
                        new CaptureModeTuple(FHD_30, HD))));
    }


    @Test void uiFirstUsesPinnedVideoActualAfterSoloInventory() {
        CandidateKey exact = CandidateKey.forVideo(
                CAMERA, VideoCodec.H264, PIPELINE, FHD_30);
        CandidateKey aligned = CandidateKey.forVideo(
                CAMERA, VideoCodec.H264, PIPELINE, FHD_ALIGNED_60);
        Snapshot pinned = alignedSnapshot(List.of(
                new CandidateEvidence(exact,
                        VerificationOutcome.DEFINITIVE_UNSUPPORTED),
                new CandidateEvidence(aligned,
                        VerificationOutcome.VERIFIED_PASS)));

        assertEquals(List.of(new CaptureQuality("FHD", 1920, 1088, 60)),
                CameraCapabilityOptions.recordQualities(pinned, "0", Set.of()));
        assertEquals(List.of(60), CameraCapabilityOptions.frameRates(
                pinned, "0", "FHD", Set.of()));
    }

    @Test void imageUiFirstUsesPinnedActualAfterSoloInventory() {
        assertEquals(List.of(
                        new CaptureQuality("SD", 720, 480),
                        new CaptureQuality("FHD", 1920, 1088)),
                CameraCapabilityOptions.imageQualities(
                        imageFamilySnapshot(false), "0", Set.of()));
    }

    @Test void failedImageLabelDisappearsWithoutRemovingOtherLabels() {
        assertEquals(List.of(new CaptureQuality("SD", 720, 480)),
                CameraCapabilityOptions.imageQualities(
                        imageFamilySnapshot(true), "0", Set.of()));
    }

    @Test void frameRateAndVideoRemainUntilRecordingTupleGraphIsExhausted() {
        Snapshot partial = recordingExhaustionSnapshot(0);
        Snapshot frameRateExhausted = recordingExhaustionSnapshot(1);
        Snapshot videoExhausted = recordingExhaustionSnapshot(2);

        assertEquals(List.of(20, 30), CameraCapabilityOptions.frameRates(
                partial, "0", "FHD", Set.of()));
        assertEquals(List.of(20), CameraCapabilityOptions.frameRates(
                frameRateExhausted, "0", "FHD", Set.of()));
        assertEquals(List.of("HD", "FHD"), CameraCapabilityOptions.recordQualities(
                frameRateExhausted, "0", Set.of()).stream()
                        .map(CaptureQuality::getId).toList());
        assertEquals(List.of("HD"), CameraCapabilityOptions.recordQualities(
                videoExhausted, "0", Set.of()).stream()
                        .map(CaptureQuality::getId).toList());
    }
    @Test void requestedPreferencesRemainUiAuthorityAfterRestart() {
        Snapshot snapshot = snapshot(Optional.of(new SelectedRecordingProfile(VideoCodec.H264, PIPELINE,
                new CaptureModeTuple(HD_30, SD))));

        assertEquals(new CaptureQuality("FHD", 1920, 1080, 20),
                CameraCapabilityOptions.selectedVideo(
                        snapshot, "0", "FHD", 20, Set.of()));
        assertEquals(new CaptureQuality("FHD", 1920, 1080),
                CameraCapabilityOptions.selectedImage(
                        snapshot, "0", "FHD", Set.of()));
    }
    private static Snapshot snapshot() {
        return snapshot(Optional.empty());
    }

    private static Snapshot snapshot(Optional<SelectedRecordingProfile> selectedRecordingProfile) {
        List<CandidateKey> raw = new ArrayList<>();
        for (VideoMode video : List.of(FHD_20, FHD_30, HD_30)) {
            raw.add(CandidateKey.forVideo(CAMERA, VideoCodec.H264, PIPELINE, video));
        }
        for (ImageMode image : List.of(SD, HD, FHD)) {
            raw.add(CandidateKey.forImage(CAMERA, VideoCodec.H264, PIPELINE, image));
        }
        for (VideoMode video : List.of(FHD_20, FHD_30, HD_30)) {
            for (ImageMode image : List.of(SD, HD, FHD)) {
                raw.add(CandidateKey.forTuple(CAMERA, VideoCodec.H264, PIPELINE,
                        new CaptureModeTuple(video, image)));
            }
        }
        CandidateKey failed = CandidateKey.forTuple(CAMERA, VideoCodec.H264, PIPELINE,
                new CaptureModeTuple(FHD_30, FHD));
        List<CandidateEvidence> evidence = new ArrayList<>();
        evidence.add(new CandidateEvidence(failed,
                VerificationOutcome.DEFINITIVE_UNSUPPORTED));
        selectedRecordingProfile.ifPresent(selection -> evidence.add(new CandidateEvidence(
                CandidateKey.forTuple(CAMERA, selection.codec(),
                        selection.verificationPipelineId(), selection.tuple()),
                VerificationOutcome.VERIFIED_PASS)));
        PipelineEvidence pipeline = new PipelineEvidence(CAMERA, VideoCodec.H264, PIPELINE,
                PipelineAvailability.AVAILABLE, raw, evidence);
        CodecSnapshot codec = new CodecSnapshot(VideoCodec.H264, CodecState.ACTIVE,
                Optional.empty(), List.of(pipeline), Optional.empty());
        CameraSnapshot camera = new CameraSnapshot(CAMERA, "camera-signature",
                List.of(codec), Optional.of(
                        CameraCapabilityStore.SelectedPipeline.fixed(PIPELINE)),
                selectedRecordingProfile);
        return Snapshot.current("hardware-signature", List.of(CAMERA), List.of(camera));
    }

    private static Snapshot recordingExhaustionSnapshot(int exhaustionStage) {
        CandidateKey fhd30Fhd = CandidateKey.forTuple(CAMERA, VideoCodec.H264, PIPELINE,
                new CaptureModeTuple(FHD_30, FHD));
        CandidateKey fhd30Hd = CandidateKey.forTuple(CAMERA, VideoCodec.H264, PIPELINE,
                new CaptureModeTuple(FHD_30, HD));
        CandidateKey fhd20Hd = CandidateKey.forTuple(CAMERA, VideoCodec.H264, PIPELINE,
                new CaptureModeTuple(FHD_20, HD));
        CandidateKey hd30Hd = CandidateKey.forTuple(CAMERA, VideoCodec.H264, PIPELINE,
                new CaptureModeTuple(HD_30, HD));
        List<CandidateKey> raw = List.of(
                CandidateKey.forVideo(CAMERA, VideoCodec.H264, PIPELINE, FHD_30),
                CandidateKey.forVideo(CAMERA, VideoCodec.H264, PIPELINE, FHD_20),
                CandidateKey.forVideo(CAMERA, VideoCodec.H264, PIPELINE, HD_30),
                CandidateKey.forImage(CAMERA, VideoCodec.H264, PIPELINE, FHD),
                CandidateKey.forImage(CAMERA, VideoCodec.H264, PIPELINE, HD),
                fhd30Fhd, fhd30Hd, fhd20Hd, hd30Hd);
        List<CandidateEvidence> evidence = new ArrayList<>();
        evidence.add(new CandidateEvidence(
                fhd30Fhd, VerificationOutcome.DEFINITIVE_UNSUPPORTED));
        if (exhaustionStage >= 1) {
            evidence.add(new CandidateEvidence(
                    fhd30Hd, VerificationOutcome.DEFINITIVE_UNSUPPORTED));
        }
        if (exhaustionStage >= 2) {
            evidence.add(new CandidateEvidence(
                    fhd20Hd, VerificationOutcome.DEFINITIVE_UNSUPPORTED));
        }
        PipelineEvidence pipeline = new PipelineEvidence(CAMERA, VideoCodec.H264, PIPELINE,
                PipelineAvailability.AVAILABLE, raw, evidence);
        CodecSnapshot codec = new CodecSnapshot(VideoCodec.H264, CodecState.ACTIVE,
                Optional.empty(), List.of(pipeline), Optional.empty());
        CameraSnapshot camera = new CameraSnapshot(CAMERA, "camera-signature",
                List.of(codec), Optional.of(
                        CameraCapabilityStore.SelectedPipeline.fixed(PIPELINE)),
                Optional.empty());
        return Snapshot.current("hardware-signature", List.of(CAMERA), List.of(camera));
    }
    private static Snapshot alignedSnapshot(List<CandidateEvidence> evidence) {
        List<CandidateKey> raw = List.of(
                CandidateKey.forVideo(CAMERA, VideoCodec.H264, PIPELINE, FHD_30),
                CandidateKey.forVideo(CAMERA, VideoCodec.H264, PIPELINE,
                        FHD_ALIGNED_60),
                CandidateKey.forImage(CAMERA, VideoCodec.H264, PIPELINE, HD),
                CandidateKey.forTuple(CAMERA, VideoCodec.H264, PIPELINE,
                        new CaptureModeTuple(FHD_30, HD)),
                CandidateKey.forTuple(CAMERA, VideoCodec.H264, PIPELINE,
                        new CaptureModeTuple(FHD_ALIGNED_60, HD)));
        PipelineEvidence pipeline = new PipelineEvidence(CAMERA, VideoCodec.H264, PIPELINE,
                PipelineAvailability.AVAILABLE, raw, evidence);
        CodecSnapshot codec = new CodecSnapshot(VideoCodec.H264, CodecState.ACTIVE,
                Optional.empty(), List.of(pipeline), Optional.empty());
        CameraSnapshot camera = new CameraSnapshot(CAMERA, "camera-signature",
                List.of(codec), Optional.of(
                        CameraCapabilityStore.SelectedPipeline.fixed(PIPELINE)),
                Optional.empty());
        return Snapshot.current("hardware-signature", List.of(CAMERA), List.of(camera));
    }

    private static Snapshot imageFamilySnapshot(boolean rejectAlignedTuple) {
        CandidateKey video = CandidateKey.forVideo(
                CAMERA, VideoCodec.H264, PIPELINE, HD_30);
        CandidateKey exactImage = CandidateKey.forImage(
                CAMERA, VideoCodec.H264, PIPELINE, FHD);
        CandidateKey alignedImage = CandidateKey.forImage(
                CAMERA, VideoCodec.H264, PIPELINE, FHD_IMAGE_ALIGNED);
        CandidateKey sdImage = CandidateKey.forImage(
                CAMERA, VideoCodec.H264, PIPELINE, SD);
        CandidateKey exactTuple = CandidateKey.forTuple(
                CAMERA, VideoCodec.H264, PIPELINE,
                new CaptureModeTuple(HD_30, FHD));
        CandidateKey alignedTuple = CandidateKey.forTuple(
                CAMERA, VideoCodec.H264, PIPELINE,
                new CaptureModeTuple(HD_30, FHD_IMAGE_ALIGNED));
        CandidateKey sdTuple = CandidateKey.forTuple(
                CAMERA, VideoCodec.H264, PIPELINE,
                new CaptureModeTuple(HD_30, SD));
        List<CandidateEvidence> evidence = new ArrayList<>(List.of(
                new CandidateEvidence(video, VerificationOutcome.VERIFIED_PASS),
                new CandidateEvidence(sdImage, VerificationOutcome.VERIFIED_PASS),
                new CandidateEvidence(exactImage,
                        VerificationOutcome.DEFINITIVE_UNSUPPORTED),
                new CandidateEvidence(alignedImage, rejectAlignedTuple
                        ? VerificationOutcome.DEFINITIVE_UNSUPPORTED
                        : VerificationOutcome.VERIFIED_PASS)));
        PipelineEvidence pipeline = new PipelineEvidence(CAMERA, VideoCodec.H264, PIPELINE,
                PipelineAvailability.AVAILABLE,
                List.of(video, sdImage, exactImage, alignedImage,
                        sdTuple, exactTuple, alignedTuple), evidence);
        CodecSnapshot codec = new CodecSnapshot(VideoCodec.H264, CodecState.ACTIVE,
                Optional.empty(), List.of(pipeline), Optional.empty());
        CameraSnapshot camera = new CameraSnapshot(CAMERA, "camera-signature",
                List.of(codec), Optional.of(
                        CameraCapabilityStore.SelectedPipeline.fixed(PIPELINE)),
                Optional.empty());
        return Snapshot.current("hardware-signature", List.of(CAMERA), List.of(camera));
    }

    private static PipelineEvidence pipeline(Snapshot snapshot) {
        return snapshot.cameras().get(0).codecs().get(0).pipelines().get(0);
    }

    private static VideoMode video(StandardResolutionLabel label,
            int width, int height, int frameRate) {
        return new VideoMode(new StandardResolution(label,
                new CameraResolution(width, height)), frameRate);
    }

    private static ImageMode image(StandardResolutionLabel label, int width, int height) {
        return new ImageMode(new StandardResolution(label, new CameraResolution(width, height)));
    }
}
