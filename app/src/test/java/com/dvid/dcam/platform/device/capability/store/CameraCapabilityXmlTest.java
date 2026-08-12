package com.dvid.dcam.platform.device.capability.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dvid.dcam.feature.device.application.port.CameraCapabilityStore.CameraSnapshot;
import com.dvid.dcam.feature.device.application.port.CameraCapabilityStore.CodecSnapshot;
import com.dvid.dcam.feature.device.application.port.CameraCapabilityStore.CodecState;
import com.dvid.dcam.feature.device.application.port.CameraCapabilityStore.SelectedRecordingProfile;
import com.dvid.dcam.feature.device.application.port.CameraCapabilityStore.Freshness;
import com.dvid.dcam.feature.device.application.port.CameraCapabilityStore.InitializationState;
import com.dvid.dcam.feature.device.application.port.CameraCapabilityStore.PipelineComparisonSnapshot;
import com.dvid.dcam.feature.device.application.port.CameraCapabilityStore.PipelineDecisionReason;
import com.dvid.dcam.feature.device.application.port.CameraCapabilityStore.PipelineDecisionStatus;
import com.dvid.dcam.feature.device.application.port.CameraCapabilityStore.PipelineIdentity;
import com.dvid.dcam.feature.device.application.port.CameraCapabilityStore.SelectedPipeline;
import com.dvid.dcam.feature.device.application.port.CameraCapabilityStore.Snapshot;
import com.dvid.dcam.feature.device.application.port.CameraCapabilityStore.UnsupportedCodecReason;
import com.dvid.dcam.feature.device.domain.camera.CameraId;
import com.dvid.dcam.feature.device.domain.camera.CameraResolution;
import com.dvid.dcam.feature.device.domain.camera.CandidateEvidence;
import com.dvid.dcam.feature.device.domain.camera.CandidateKey;
import com.dvid.dcam.feature.device.domain.camera.CaptureModeTuple;
import com.dvid.dcam.feature.device.domain.camera.ImageMode;
import com.dvid.dcam.feature.device.domain.camera.PipelineAvailability;
import com.dvid.dcam.feature.device.domain.camera.PipelineComparison;
import com.dvid.dcam.feature.device.domain.camera.PipelineEvidence;
import com.dvid.dcam.feature.device.domain.camera.StandardResolution;
import com.dvid.dcam.feature.device.domain.camera.StandardResolutionLabel;
import com.dvid.dcam.feature.device.domain.camera.VerificationOutcome;
import com.dvid.dcam.feature.device.domain.camera.VerificationPipelineId;
import com.dvid.dcam.feature.device.domain.camera.VideoCodec;
import com.dvid.dcam.feature.device.domain.camera.VideoMode;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class CameraCapabilityXmlTest {
    private final SnapshotXmlCodec codec = new SnapshotXmlCodec();

    @Test void formatOneFixtureDecodesCurrentSchema() throws Exception {
        Snapshot snapshot = codec.decode(CapabilityStoreFixtures.FORMAT_1_XML.getBytes(
                StandardCharsets.UTF_8));

        assertEquals(1, snapshot.format());
        assertEquals(InitializationState.READY_REUSABLE, snapshot.initializationState());
        assertEquals("signature-1", snapshot.hardwareSignature());
        assertEquals(List.of(new CameraId("1"), new CameraId("0")),
                snapshot.cameraOrderOverride());
        assertEquals(2, snapshot.cameras().size());
        assertEquals(0, snapshot.cameras().stream()
                .filter(camera -> camera.cameraId().equals(new CameraId("0")))
                .findFirst().orElseThrow().sensorOrientationDegrees());
        assertEquals(270, snapshot.cameras().stream()
                .filter(camera -> camera.cameraId().equals(new CameraId("1")))
                .findFirst().orElseThrow().sensorOrientationDegrees());
        SelectedPipeline selected = snapshot.cameras().get(0)
                .selectedPipeline().orElseThrow();
        assertEquals(CapabilityStoreFixtures.PIPELINE_A, selected.pipelineId());
        assertEquals(com.dvid.dcam.feature.device.application.port.CameraCapabilityStore
                .PipelineSelectionMode.AUTO, selected.mode());
        assertEquals(com.dvid.dcam.feature.device.application.port.CameraCapabilityStore
                .PipelineDecisionStatus.VERIFIED_COMPLETE, selected.decisionStatus());
        assertEquals(com.dvid.dcam.feature.device.application.port.CameraCapabilityStore
                .PipelineDecisionReason.STRICT_SUPERSET, selected.decisionReason());

        String encoded = new String(codec.encode(snapshot), StandardCharsets.UTF_8);
        assertTrue(encoded.contains("<capabilities format=\"1\""));
        assertTrue(encoded.contains("<selectedPipeline mode=\"auto\""));
        assertTrue(encoded.contains("pipelineId=\"pipeline-a\""));
        assertTrue(encoded.contains("decisionStatus=\"verified_complete\""));
        assertTrue(encoded.contains("<selectedRecordingProfile codecId=\"h264\""));
    }

    @Test void missingSensorOrientationRequiresCapabilityRebuild() {
        String missingOrientation = CapabilityStoreFixtures.FORMAT_1_XML.replace(
                " sensorOrientationDegrees=\"270\"", "");

        assertThrows(InvalidSnapshotXmlException.class,
                () -> codec.decode(missingOrientation.getBytes(StandardCharsets.UTF_8)));
    }

    @Test void sensorOrientationRoundTripsWithoutChangingFormat() throws Exception {
        Snapshot source = CapabilityStoreFixtures.snapshot("signature-1");

        String encoded = new String(codec.encode(source), StandardCharsets.UTF_8);
        Snapshot recreated = codec.decode(encoded.getBytes(StandardCharsets.UTF_8));

        assertTrue(encoded.contains("<capabilities format=\"1\""));
        assertTrue(encoded.contains("sensorOrientationDegrees=\"270\""));
        CameraSnapshot front = recreated.cameras().stream()
                .filter(camera -> camera.cameraId().equals(new CameraId("1")))
                .findFirst().orElseThrow();
        assertEquals(270, front.sensorOrientationDegrees());
    }

    @Test void profileVerifiedPipelineAndRecordingProfileRoundTripTogether() throws Exception {
        Snapshot source = CapabilityStoreFixtures.snapshot("signature-1");
        CameraSnapshot camera = source.cameras().get(0);
        SelectedRecordingProfile profile = camera.selectedRecordingProfile().orElseThrow();
        CameraSnapshot updatedCamera = new CameraSnapshot(camera.cameraId(),
                camera.hardwareSignature(), camera.codecs(), Optional.of(
                SelectedPipeline.autoProfileVerified(profile.verificationPipelineId())),
                Optional.of(profile));
        List<CameraSnapshot> cameras = new java.util.ArrayList<>(source.cameras());
        cameras.set(0, updatedCamera);
        Snapshot updated = new Snapshot(source.format(), source.initializationState(),
                source.hardwareSignature(), source.cameraOrderOverride(), cameras);

        Snapshot recreated = codec.decode(codec.encode(updated));

        CameraSnapshot recreatedCamera = recreated.cameras().get(0);
        assertEquals(PipelineDecisionStatus.PROFILE_VERIFIED,
                recreatedCamera.selectedPipeline().orElseThrow().decisionStatus());
        assertEquals(PipelineDecisionReason.VERIFIED_RECORDING_PROFILE,
                recreatedCamera.selectedPipeline().orElseThrow().decisionReason());
        assertEquals(profile, recreatedCamera.selectedRecordingProfile().orElseThrow());
    }

    @Test void rejectsLegacyPipelineOverrideTag() {
        String xml = """
                <capabilities format="1" initializationState="incomplete" hardwareSignature="fresh-release">
                  <camera id="0" hardwareSignature="camera">
                    <pipelineOverride mode="auto" />
                  </camera>
                </capabilities>
                """;

        assertThrows(InvalidSnapshotXmlException.class,
                () -> codec.decode(xml.getBytes(StandardCharsets.UTF_8)));
    }

    @Test void rejectsLegacySelectionTag() {
        String xml = """
                <capabilities format="1" initializationState="incomplete" hardwareSignature="fresh-release">
                  <camera id="0" hardwareSignature="camera">
                    <selection codec="h264" pipeline="pipeline-a" videoLabel="fhd" videoWidth="1920" videoHeight="1080" fps="30" imageLabel="fhd" imageWidth="1920" imageHeight="1080" />
                  </camera>
                </capabilities>
                """;

        assertThrows(InvalidSnapshotXmlException.class,
                () -> codec.decode(xml.getBytes(StandardCharsets.UTF_8)));
    }

    @Test void rejectsMissingInitializationState() {
        String xml = "<capabilities format=\"1\" hardwareSignature=\"fresh-release\" />";

        assertThrows(InvalidSnapshotXmlException.class,
                () -> codec.decode(xml.getBytes(StandardCharsets.UTF_8)));
    }

    @Test void roundTripKeepsPipelineEvidenceSeparateAndRecommendationComplete()
            throws Exception {
        Snapshot snapshot = codec.decode(codec.encode(CapabilityStoreFixtures.snapshot(
                "signature-1")));
        CameraSnapshot camera = snapshot.cameras().get(0);
        CodecSnapshot h264 = camera.codecs().get(0);
        CaptureModeTuple tuple = CapabilityStoreFixtures.cameraZeroTuple();

        assertEquals(VerificationOutcome.VERIFIED_PASS,
                h264.pipelines().get(0).outcome(tuple));
        assertEquals(VerificationOutcome.DEFINITIVE_UNSUPPORTED,
                h264.pipelines().get(1).outcome(tuple));
        assertEquals(PipelineComparison.Recommendation.PIPELINE_A,
                h264.comparison().orElseThrow().comparison().recommendation());
        assertEquals(PipelineComparison.Status.COMPLETE,
                h264.comparison().orElseThrow().comparison().status());
    }

    @Test void acceptsVerifiedTupleDespiteIndependentSoloFailure() throws Exception {
        CameraSnapshot camera = CapabilityStoreFixtures.snapshot("signature-1")
                .cameras().get(0);
        PipelineEvidence original = camera.codecs().get(0).pipelines().get(0);
        CandidateKey video = original.rawFastCandidates().stream()
                .filter(candidate -> candidate.kind() == CandidateKey.Kind.VIDEO)
                .findFirst().orElseThrow();
        CandidateKey tuple = original.rawFastCandidates().stream()
                .filter(candidate -> candidate.kind() == CandidateKey.Kind.TUPLE)
                .findFirst().orElseThrow();
        PipelineEvidence contradictory = new PipelineEvidence(
                original.cameraId(), original.codec(),
                original.verificationPipelineId(), original.availability(),
                original.rawFastCandidates(), List.of(
                new CandidateEvidence(
                        video, VerificationOutcome.DEFINITIVE_UNSUPPORTED),
                new CandidateEvidence(tuple, VerificationOutcome.VERIFIED_PASS)));
        CodecSnapshot h264Codec = new CodecSnapshot(VideoCodec.H264, CodecState.ACTIVE,
                Optional.empty(), List.of(contradictory), Optional.empty());

        CameraSnapshot persisted = new CameraSnapshot(camera.cameraId(), camera.hardwareSignature(),
                List.of(h264Codec), Optional.of(SelectedPipeline.autoFast(
                        original.verificationPipelineId(),
                        com.dvid.dcam.feature.device.application.port
                                .CameraCapabilityStore.PipelineDecisionReason
                                .ONLY_AVAILABLE)),
                camera.selectedRecordingProfile());
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> new CameraSnapshot(
                camera.cameraId(), camera.hardwareSignature(), List.of(h264Codec),
                persisted.selectedPipeline(), persisted.selectedRecordingProfile()));
        Snapshot source = CapabilityStoreFixtures.snapshot("signature-1");
        Snapshot withFailure = new Snapshot(
                source.format(), source.initializationState(), source.hardwareSignature(),
                List.of(camera.cameraId()), List.of(persisted));
        Snapshot decoded = this.codec.decode(this.codec.encode(withFailure));
        PipelineEvidence decodedEvidence = decoded.cameras().get(0).codecs().get(0)
                .pipelines().get(0);
        assertEquals(VerificationOutcome.DEFINITIVE_UNSUPPORTED,
                decodedEvidence.outcome(video));
    }
    @Test void rejectsRecommendationThatConflictsWithPipelineEvidence() {
        String xml = CapabilityStoreFixtures.FORMAT_1_XML.replace(
                "recommendation=\"pipeline_a\"",
                "recommendation=\"pipeline_b\"");

        assertThrows(InvalidSnapshotXmlException.class,
                () -> codec.decode(xml.getBytes(StandardCharsets.UTF_8)));
    }
    @Test void legacyFormatZeroRequiresRebuild() {
        String xml = "<capabilities format=\"0\" hardwareSignature=\"legacy\" />";

        assertThrows(UnsupportedSnapshotFormatException.class,
                () -> codec.decode(xml.getBytes(StandardCharsets.UTF_8)));
    }

    @Test void legacyFormatTwoRequiresRebuild() {
        String xml = "<capabilities format=\"2\" hardwareSignature=\"legacy\" />";

        assertThrows(UnsupportedSnapshotFormatException.class,
                () -> codec.decode(xml.getBytes(StandardCharsets.UTF_8)));
    }

    @Test void rejectsUnknownFormat() {
        String xml = "<capabilities format=\"4\" hardwareSignature=\"x\" />";

        assertThrows(UnsupportedSnapshotFormatException.class,
                () -> codec.decode(xml.getBytes(StandardCharsets.UTF_8)));
    }

    @Test void rejectsCorruptXml() {
        assertThrows(InvalidSnapshotXmlException.class,
                () -> codec.decode("<capabilities".getBytes(StandardCharsets.UTF_8)));
    }

    @Test void rejectsDuplicateCameraKeys() {
        String xml = """
                <capabilities format="1" initializationState="incomplete" hardwareSignature="x">
                  <camera id="0" />
                  <camera id="0" />
                </capabilities>
                """;

        assertThrows(InvalidSnapshotXmlException.class,
                () -> codec.decode(xml.getBytes(StandardCharsets.UTF_8)));
    }

    @Test void rejectsDuplicatePipelineKeys() {
        String pipeline = """
                <pipeline id="p" availability="available">
                  <rawFastCandidates />
                  <verifiedFacts />
                </pipeline>
                """;
        String xml = "<capabilities format=\"1\" initializationState=\"incomplete\" hardwareSignature=\"x\">"
                + "<camera id=\"0\"><codec id=\"h264\" state=\"active\">"
                + pipeline + pipeline + "</codec></camera></capabilities>";

        assertThrows(InvalidSnapshotXmlException.class,
                () -> codec.decode(xml.getBytes(StandardCharsets.UTF_8)));
    }

    @Test void rejectsDuplicateTupleKeys() {
        String tuple = "<candidate kind=\"tuple\" videoLabel=\"fhd\""
                + " videoWidth=\"1920\" videoHeight=\"1080\" fps=\"30\""
                + " imageLabel=\"fhd\" imageWidth=\"1920\" imageHeight=\"1080\" />";
        String xml = "<capabilities format=\"1\" initializationState=\"incomplete\" hardwareSignature=\"x\">"
                + "<camera id=\"0\"><codec id=\"h264\" state=\"active\">"
                + "<pipeline id=\"p\" availability=\"available\">"
                + "<rawFastCandidates>" + tuple + tuple + "</rawFastCandidates>"
                + "<verifiedFacts /></pipeline></codec></camera></capabilities>";

        assertThrows(InvalidSnapshotXmlException.class,
                () -> codec.decode(xml.getBytes(StandardCharsets.UTF_8)));
    }
}

final class CapabilityStoreFixtures {
    static final VerificationPipelineId PIPELINE_A =
            new VerificationPipelineId("pipeline-a");
    static final VerificationPipelineId PIPELINE_B =
            new VerificationPipelineId("pipeline-b");
    static final String FORMAT_1_XML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <capabilities format="1" initializationState="ready_reusable" hardwareSignature="signature-1">
              <cameraOrderOverride>
                <cameraRef id="1" />
                <cameraRef id="0" />
              </cameraOrderOverride>
              <camera id="0" hardwareSignature="camera-signature-0" sensorOrientationDegrees="0">
                <codec id="h264" state="active">
                  <pipeline id="pipeline-a" availability="available">
                    <rawFastCandidates>
                      <candidate kind="video" videoLabel="fhd" videoWidth="1920" videoHeight="1080" fps="30" />
                      <candidate kind="image" imageLabel="fhd" imageWidth="1920" imageHeight="1080" />
                      <candidate kind="tuple" videoLabel="fhd" videoWidth="1920" videoHeight="1080" fps="30" imageLabel="fhd" imageWidth="1920" imageHeight="1080" />
                    </rawFastCandidates>
                    <verifiedFacts>
                      <fact kind="tuple" videoLabel="fhd" videoWidth="1920" videoHeight="1080" fps="30" imageLabel="fhd" imageWidth="1920" imageHeight="1080" outcome="verified_pass" />
                    </verifiedFacts>
                  </pipeline>
                  <pipeline id="pipeline-b" availability="available">
                    <rawFastCandidates>
                      <candidate kind="video" videoLabel="fhd" videoWidth="1920" videoHeight="1080" fps="30" />
                      <candidate kind="image" imageLabel="fhd" imageWidth="1920" imageHeight="1080" />
                      <candidate kind="tuple" videoLabel="fhd" videoWidth="1920" videoHeight="1080" fps="30" imageLabel="fhd" imageWidth="1920" imageHeight="1080" />
                    </rawFastCandidates>
                    <verifiedFacts>
                      <fact kind="tuple" videoLabel="fhd" videoWidth="1920" videoHeight="1080" fps="30" imageLabel="fhd" imageWidth="1920" imageHeight="1080" outcome="definitive_unsupported" />
                    </verifiedFacts>
                  </pipeline>
                  <pipelineComparison status="complete" coverage="a_broader" recommendation="pipeline_a" recommendationReason="strict_superset" pipelineA="pipeline-a" pipelineB="pipeline-b">
                    <tupleSet kind="intersection" />
                    <tupleSet kind="aOnly">
                      <tuple videoLabel="fhd" videoWidth="1920" videoHeight="1080" fps="30" imageLabel="fhd" imageWidth="1920" imageHeight="1080" />
                    </tupleSet>
                    <tupleSet kind="bOnly" />
                    <tupleSet kind="bothFail" />
                    <tupleSet kind="unknown" />
                  </pipelineComparison>
                </codec>
                <codec id="h265" state="unsupported" reason="not_implemented" />
                <selectedPipeline mode="auto" pipelineId="pipeline-a" decisionStatus="verified_complete" decisionReason="strict_superset" />
                <selectedRecordingProfile codecId="h264" pipelineId="pipeline-a" videoLabel="fhd" videoWidth="1920" videoHeight="1080" fps="30" imageLabel="fhd" imageWidth="1920" imageHeight="1080" />
              </camera>
              <camera id="1" hardwareSignature="camera-signature-1" sensorOrientationDegrees="270">
                <codec id="h264" state="active">
                  <pipeline id="pipeline-a" availability="available">
                    <rawFastCandidates>
                      <candidate kind="video" videoLabel="hd" videoWidth="1280" videoHeight="720" fps="30" />
                      <candidate kind="image" imageLabel="fhd" imageWidth="1920" imageHeight="1080" />
                      <candidate kind="tuple" videoLabel="hd" videoWidth="1280" videoHeight="720" fps="30" imageLabel="fhd" imageWidth="1920" imageHeight="1080" />
                    </rawFastCandidates>
                    <verifiedFacts>
                      <fact kind="tuple" videoLabel="hd" videoWidth="1280" videoHeight="720" fps="30" imageLabel="fhd" imageWidth="1920" imageHeight="1080" outcome="verified_pass" />
                    </verifiedFacts>
                  </pipeline>
                </codec>
                <codec id="h265" state="unsupported" reason="not_implemented" />
                <selectedPipeline mode="fixed" pipelineId="pipeline-a" decisionStatus="fixed" decisionReason="fixed" />
                <selectedRecordingProfile codecId="h264" pipelineId="pipeline-a" videoLabel="hd" videoWidth="1280" videoHeight="720" fps="30" imageLabel="fhd" imageWidth="1920" imageHeight="1080" />
              </camera>
            </capabilities>
            """;

    private CapabilityStoreFixtures() {}

    static Snapshot snapshot(String signature) {
        CameraId cameraZero = new CameraId("0");
        CameraId cameraOne = new CameraId("1");
        CaptureModeTuple cameraZeroTuple = cameraZeroTuple();
        CaptureModeTuple cameraOneTuple = tuple(StandardResolutionLabel.HD, 1280, 720,
                StandardResolutionLabel.FHD, 1920, 1080);
        PipelineEvidence cameraZeroA = evidence(cameraZero, PIPELINE_A,
                cameraZeroTuple, VerificationOutcome.VERIFIED_PASS);
        PipelineEvidence cameraZeroB = evidence(cameraZero, PIPELINE_B,
                cameraZeroTuple, VerificationOutcome.DEFINITIVE_UNSUPPORTED);
        PipelineComparison comparison = new PipelineComparison(
                PipelineComparison.Status.COMPLETE,
                PipelineComparison.Coverage.A_BROADER,
                PipelineComparison.Recommendation.PIPELINE_A,
                PipelineComparison.RecommendationReason.STRICT_SUPERSET,
                PIPELINE_A, PIPELINE_B, Set.of(), Set.of(cameraZeroTuple),
                Set.of(), Set.of(), Set.of());
        CameraSnapshot zero = camera(cameraZero, List.of(cameraZeroA, cameraZeroB),
                Optional.of(comparison), SelectedPipeline.autoVerified(PIPELINE_A,
                        com.dvid.dcam.feature.device.application.port.CameraCapabilityStore
                                .PipelineDecisionReason.STRICT_SUPERSET), cameraZeroTuple);
        CameraSnapshot one = camera(cameraOne,
                List.of(evidence(cameraOne, PIPELINE_A, cameraOneTuple,
                        VerificationOutcome.VERIFIED_PASS)),
                Optional.empty(), SelectedPipeline.fixed(PIPELINE_A), cameraOneTuple);
        return Snapshot.current(signature, List.of(cameraOne, cameraZero),
                List.of(zero, one)).withInitializationState(
                        InitializationState.READY_REUSABLE);
    }

    static Freshness freshness() {
        return new Freshness("signature-1", Map.of(
                new CameraId("0"), "camera-signature-0",
                new CameraId("1"), "camera-signature-1"), Set.of(
                new PipelineIdentity(new CameraId("0"), VideoCodec.H264, PIPELINE_A),
                new PipelineIdentity(new CameraId("0"), VideoCodec.H264, PIPELINE_B),
                new PipelineIdentity(new CameraId("1"), VideoCodec.H264, PIPELINE_A)));
    }

    static CaptureModeTuple cameraZeroTuple() {
        return tuple(StandardResolutionLabel.FHD, 1920, 1080,
                StandardResolutionLabel.FHD, 1920, 1080);
    }

    private static CameraSnapshot camera(CameraId cameraId,
            List<PipelineEvidence> pipelines,
            Optional<PipelineComparison> comparison,
            SelectedPipeline selectedPipeline,
            CaptureModeTuple selection) {
        CodecSnapshot h264 = new CodecSnapshot(VideoCodec.H264, CodecState.ACTIVE,
                Optional.empty(), pipelines,
                comparison.map(PipelineComparisonSnapshot::withoutPerformance));
        CodecSnapshot h265 = new CodecSnapshot(VideoCodec.H265, CodecState.UNSUPPORTED,
                Optional.of(UnsupportedCodecReason.NOT_IMPLEMENTED),
                List.of(), Optional.empty());
        return new CameraSnapshot(cameraId,
                "camera-signature-" + cameraId.value(), List.of(h264, h265),
                Optional.of(selectedPipeline),
                Optional.of(new SelectedRecordingProfile(VideoCodec.H264,
                        pipelines.get(0).verificationPipelineId(), selection)),
                "1".equals(cameraId.value()) ? 270 : 0);
    }

    private static PipelineEvidence evidence(CameraId cameraId,
            VerificationPipelineId pipelineId, CaptureModeTuple tuple,
            VerificationOutcome outcome) {
        CandidateKey video = CandidateKey.forVideo(cameraId, VideoCodec.H264,
                pipelineId, tuple.videoMode());
        CandidateKey image = CandidateKey.forImage(cameraId, VideoCodec.H264,
                pipelineId, tuple.imageMode());
        CandidateKey combined = CandidateKey.forTuple(cameraId, VideoCodec.H264,
                pipelineId, tuple);
        return new PipelineEvidence(cameraId, VideoCodec.H264, pipelineId,
                PipelineAvailability.AVAILABLE, List.of(video, image, combined),
                List.of(new CandidateEvidence(combined, outcome)));
    }

    private static CaptureModeTuple tuple(StandardResolutionLabel videoLabel,
            int videoWidth, int videoHeight, StandardResolutionLabel imageLabel,
            int imageWidth, int imageHeight) {
        VideoMode video = new VideoMode(new StandardResolution(videoLabel,
                new CameraResolution(videoWidth, videoHeight)), 30);
        ImageMode image = new ImageMode(new StandardResolution(imageLabel,
                new CameraResolution(imageWidth, imageHeight)));
        return new CaptureModeTuple(video, image);
    }
}