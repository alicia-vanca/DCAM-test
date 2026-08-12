package com.dvid.dcam.feature.device.domain.camera;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class CameraCandidateIdentityTest {
    private static final CameraId CAMERA = new CameraId("0");
    private static final VerificationPipelineId PIPELINE =
            new VerificationPipelineId("a-camera2-native-surface-sharing-v1");

    @Test void actualDimensionsRemainPartOfCandidateIdentity() {
        VideoMode canonical = video(StandardResolutionLabel.FHD, 1920, 1080, 30);
        VideoMode aligned = video(StandardResolutionLabel.FHD, 1920, 1088, 30);

        CandidateKey canonicalKey = CandidateKey.forVideo(
                CAMERA, VideoCodec.H264, PIPELINE, canonical);
        CandidateKey alignedKey = CandidateKey.forVideo(
                CAMERA, VideoCodec.H264, PIPELINE, aligned);

        assertNotEquals(canonicalKey, alignedKey);
        assertTrue(canonicalKey.compareTo(alignedKey) < 0);
    }

    @Test void exactTupleKeyCarriesCameraCodecPipelineVideoAndImage() {
        VideoMode video = video(StandardResolutionLabel.FHD, 1920, 1088, 30);
        ImageMode image = image(StandardResolutionLabel.MAX, 4000, 3000);
        CaptureModeTuple tuple = new CaptureModeTuple(video, image);

        CandidateKey key = CandidateKey.forTuple(
                CAMERA, VideoCodec.H264, PIPELINE, tuple);

        assertEquals(CandidateKey.Kind.TUPLE, key.kind());
        assertEquals(Optional.of(video), key.videoMode());
        assertEquals(Optional.of(image), key.imageMode());
        assertEquals(Optional.of(tuple), key.tuple());
    }

    @Test void valueOrderingIsStableAcrossIdentityTypes() {
        CameraId cameraOne = new CameraId("1");
        VerificationPipelineId pipelineB = new VerificationPipelineId("pipeline-b");

        assertEquals(List.of(CAMERA, cameraOne),
                List.of(cameraOne, CAMERA).stream().sorted().toList());
        assertEquals(List.of(PIPELINE, pipelineB),
                List.of(pipelineB, PIPELINE).stream().sorted().toList());
        assertTrue(VideoCodec.H264.compareTo(VideoCodec.H265) < 0);
    }

    @Test void invalidIdentityValuesFailFast() {
        assertThrows(IllegalArgumentException.class, () -> new CameraId(" "));
        assertThrows(IllegalArgumentException.class,
                () -> new VerificationPipelineId(""));
        assertThrows(IllegalArgumentException.class,
                () -> new CameraResolution(0, 1080));
        assertThrows(IllegalArgumentException.class,
                () -> new StandardResolution(
                        StandardResolutionLabel.QHD, new CameraResolution(2560, 1920)));
        assertThrows(IllegalArgumentException.class,
                () -> new VideoMode(new StandardResolution(
                        StandardResolutionLabel.HD, new CameraResolution(1280, 720)), 0));
        assertThrows(IllegalArgumentException.class, () -> new CandidateKey(
                CAMERA, VideoCodec.H264, PIPELINE, CandidateKey.Kind.VIDEO,
                Optional.empty(), Optional.empty()));
    }

    private static VideoMode video(StandardResolutionLabel label,
            int width, int height, int framesPerSecond) {
        return new VideoMode(new StandardResolution(
                label, new CameraResolution(width, height)), framesPerSecond);
    }

    private static ImageMode image(StandardResolutionLabel label, int width, int height) {
        return new ImageMode(new StandardResolution(
                label, new CameraResolution(width, height)));
    }
}