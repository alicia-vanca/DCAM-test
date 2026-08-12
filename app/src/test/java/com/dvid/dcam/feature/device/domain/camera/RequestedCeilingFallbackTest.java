package com.dvid.dcam.feature.device.domain.camera;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

final class RequestedCeilingFallbackTest {
    private static final CameraId CAMERA = new CameraId("0");
    private static final VerificationPipelineId PIPELINE =
            new VerificationPipelineId("pipeline-a");

    @Test void fallbackLowersImageThenFpsThenVideoAndResetsImageCeiling() {
        CandidateKey requested = tuple(
                StandardResolutionLabel.UHD, 3840, 2160, 30,
                StandardResolutionLabel.HD, 1280, 720);
        CandidateKey uhd30Sd = tuple(
                StandardResolutionLabel.UHD, 3840, 2160, 30,
                StandardResolutionLabel.SD, 720, 480);
        CandidateKey uhd20Hd = tuple(
                StandardResolutionLabel.UHD, 3840, 2160, 20,
                StandardResolutionLabel.HD, 1280, 720);
        CandidateKey uhd20Sd = tuple(
                StandardResolutionLabel.UHD, 3840, 2160, 20,
                StandardResolutionLabel.SD, 720, 480);
        CandidateKey qhd30Hd = tuple(
                StandardResolutionLabel.QHD, 2560, 1440, 30,
                StandardResolutionLabel.HD, 1280, 720);
        CandidateKey qhd30Sd = tuple(
                StandardResolutionLabel.QHD, 2560, 1440, 30,
                StandardResolutionLabel.SD, 720, 480);
        CandidateKey upwardImage = tuple(
                StandardResolutionLabel.UHD, 3840, 2160, 20,
                StandardResolutionLabel.FHD, 1920, 1080);
        CandidateKey upwardUhdImage = tuple(
                StandardResolutionLabel.UHD, 3840, 2160, 20,
                StandardResolutionLabel.UHD, 3840, 2160);
        CandidateKey upwardFps = tuple(
                StandardResolutionLabel.UHD, 3840, 2160, 60,
                StandardResolutionLabel.HD, 1280, 720);
        CandidateKey previousHigherSelection = tuple(
                StandardResolutionLabel.MAX, 4000, 3000, 60,
                StandardResolutionLabel.MAX, 4000, 3000);
        List<CandidateKey> effective = new ArrayList<>(List.of(
                qhd30Sd, upwardImage, uhd20Sd, requested,
                previousHigherSelection, uhd30Sd, upwardUhdImage, upwardFps,
                qhd30Hd, uhd20Hd));

        List<CandidateKey> ordered = RequestedCeilingFallback.orderedCandidates(
                requested, effective);

        assertEquals(List.of(
                requested,
                uhd30Sd,
                uhd20Hd,
                uhd20Sd,
                qhd30Hd,
                qhd30Sd), ordered);
        assertFalse(ordered.contains(upwardImage));
        assertFalse(ordered.contains(upwardUhdImage));
        assertFalse(ordered.contains(upwardFps));
        assertFalse(ordered.contains(previousHigherSelection));
    }

    @Test void maxRanksAboveStandardModesEvenWithSmallerActualArea() {
        CandidateKey requested = tuple(
                StandardResolutionLabel.MAX, 2320, 1740, 30,
                StandardResolutionLabel.MAX, 2320, 1740);
        CandidateKey uhd = tuple(
                StandardResolutionLabel.UHD, 3840, 2160, 30,
                StandardResolutionLabel.UHD, 3840, 2160);

        assertEquals(List.of(requested, uhd),
                RequestedCeilingFallback.orderedCandidates(
                        requested, List.of(uhd, requested)));
    }

    private static CandidateKey tuple(
            StandardResolutionLabel videoLabel,
            int videoWidth,
            int videoHeight,
            int framesPerSecond,
            StandardResolutionLabel imageLabel,
            int imageWidth,
            int imageHeight) {
        VideoMode video = new VideoMode(new StandardResolution(
                videoLabel, new CameraResolution(videoWidth, videoHeight)),
                framesPerSecond);
        ImageMode image = new ImageMode(new StandardResolution(
                imageLabel, new CameraResolution(imageWidth, imageHeight)));
        return CandidateKey.forTuple(CAMERA, VideoCodec.H264, PIPELINE,
                new CaptureModeTuple(video, image));
    }
}