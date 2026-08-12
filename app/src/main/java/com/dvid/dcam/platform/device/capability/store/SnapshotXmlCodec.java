package com.dvid.dcam.platform.device.capability.store;

import com.dvid.dcam.feature.device.application.port.CameraCapabilityStore;
import com.dvid.dcam.feature.device.application.port.CameraCapabilityStore.CameraSnapshot;
import com.dvid.dcam.feature.device.application.port.CameraCapabilityStore.CodecSnapshot;
import com.dvid.dcam.feature.device.application.port.CameraCapabilityStore.CodecState;
import com.dvid.dcam.feature.device.application.port.CameraCapabilityStore.SelectedRecordingProfile;
import com.dvid.dcam.feature.device.application.port.CameraCapabilityStore.InitializationState;
import com.dvid.dcam.feature.device.application.port.CameraCapabilityStore.PipelineComparisonSnapshot;
import com.dvid.dcam.feature.device.application.port.CameraCapabilityStore.PipelineVerificationCoverage;
import com.dvid.dcam.feature.device.application.port.CameraCapabilityStore.PipelineDecisionReason;
import com.dvid.dcam.feature.device.application.port.CameraCapabilityStore.PipelineDecisionStatus;
import com.dvid.dcam.feature.device.application.port.CameraCapabilityStore.SelectedPipeline;
import com.dvid.dcam.feature.device.application.port.CameraCapabilityStore.PipelineSelectionMode;
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

import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

final class SnapshotXmlCodec {

    byte[] encode(Snapshot snapshot) {
        StringBuilder xml = new StringBuilder(4096);
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<capabilities");
        attribute(xml, "format", snapshot.format());
        attribute(xml, "initializationState", enumId(snapshot.initializationState()));
        attribute(xml, "hardwareSignature", snapshot.hardwareSignature());
        xml.append(">\n");
        if (!snapshot.cameraOrderOverride().isEmpty()) {
            xml.append("  <cameraOrderOverride>\n");
            for (CameraId cameraId : snapshot.cameraOrderOverride()) {
                xml.append("    <cameraRef");
                attribute(xml, "id", cameraId.value());
                xml.append(" />\n");
            }
            xml.append("  </cameraOrderOverride>\n");
        }
        for (CameraSnapshot camera : snapshot.cameras()) writeCamera(xml, camera);
        xml.append("</capabilities>\n");
        return xml.toString().getBytes(StandardCharsets.UTF_8);
    }

    Snapshot decode(byte[] bytes)
            throws InvalidSnapshotXmlException, UnsupportedSnapshotFormatException {
        try {
            String source = strictUtf8(bytes);
            if (source.contains("<!DOCTYPE") || source.contains("<!ENTITY")) {
                throw new InvalidSnapshotXmlException("document type is forbidden");
            }
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(false);
            factory.setExpandEntityReferences(false);
            setFeature(factory, "http://apache.org/xml/features/disallow-doctype-decl", true);
            setFeature(factory, "http://xml.org/sax/features/external-general-entities", false);
            setFeature(factory, "http://xml.org/sax/features/external-parameter-entities", false);
            setFeature(factory,
                    "http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            builder.setErrorHandler(new ThrowingErrorHandler());
            Document document = builder.parse(new ByteArrayInputStream(bytes));
            if (document.getDoctype() != null) {
                throw new InvalidSnapshotXmlException("document type is forbidden");
            }
            Element root = document.getDocumentElement();
            requireTag(root, "capabilities");
            int format = integer(root, "format");
            if (format != CameraCapabilityStore.FORMAT_VERSION) {
                throw new UnsupportedSnapshotFormatException(format);
            }
            InitializationState initializationState = enumValue(
                    InitializationState.class, required(root, "initializationState"));
            String hardwareSignature = required(root, "hardwareSignature");
            List<CameraId> cameraOrder = new ArrayList<>();
            List<CameraSnapshot> cameras = new ArrayList<>();
            Set<CameraId> cameraIds = new HashSet<>();
            boolean orderSeen = false;
            for (Element child : children(root)) {
                switch (child.getTagName()) {
                    case "cameraOrderOverride" -> {
                        if (orderSeen) throw invalid("duplicate camera order override");
                        orderSeen = true;
                        cameraOrder.addAll(parseCameraOrder(child));
                    }
                    case "camera" -> {
                        CameraSnapshot camera = parseCamera(child);
                        if (!cameraIds.add(camera.cameraId())) {
                            throw invalid("duplicate camera key");
                        }
                        cameras.add(camera);
                    }
                    default -> throw invalid("unknown capabilities child");
                }
            }
            return new Snapshot(CameraCapabilityStore.FORMAT_VERSION, initializationState,
                    hardwareSignature, cameraOrder, cameras);
        } catch (UnsupportedSnapshotFormatException | InvalidSnapshotXmlException error) {
            throw error;
        } catch (ParserConfigurationException | SAXException
                | IllegalArgumentException error) {
            throw new InvalidSnapshotXmlException("invalid capability XML", error);
        } catch (java.io.IOException error) {
            throw new InvalidSnapshotXmlException("cannot read capability XML", error);
        }
    }

    private static void writeCamera(StringBuilder xml, CameraSnapshot camera) {
        xml.append("  <camera");
        attribute(xml, "id", camera.cameraId().value());
        attribute(xml, "hardwareSignature", camera.hardwareSignature());
        attribute(xml, "sensorOrientationDegrees", camera.sensorOrientationDegrees());
        xml.append(">\n");
        for (CodecSnapshot codec : camera.codecs()) writeCodec(xml, codec);
        if (camera.selectedPipeline().isPresent()) {
            SelectedPipeline selected = camera.selectedPipeline().orElseThrow();
            xml.append("    <selectedPipeline");
            attribute(xml, "mode", enumId(selected.mode()));
            attribute(xml, "pipelineId", selected.pipelineId().value());
            attribute(xml, "decisionStatus", enumId(selected.decisionStatus()));
            attribute(xml, "decisionReason", enumId(selected.decisionReason()));
            xml.append(" />\n");
        }
        if (camera.selectedRecordingProfile().isPresent()) {
            SelectedRecordingProfile profile =
                    camera.selectedRecordingProfile().orElseThrow();
            xml.append("    <selectedRecordingProfile");
            attribute(xml, "codecId", profile.codec().id());
            attribute(xml, "pipelineId", profile.verificationPipelineId().value());
            writeTupleAttributes(xml, profile.tuple());
            xml.append(" />\n");
        }
        xml.append("  </camera>\n");
    }
    private static void writeCodec(StringBuilder xml, CodecSnapshot codec) {
        xml.append("    <codec");
        attribute(xml, "id", codec.codec().id());
        attribute(xml, "state", enumId(codec.state()));
        codec.unsupportedReason().ifPresent(
                value -> attribute(xml, "reason", enumId(value)));
        if (codec.pipelines().isEmpty() && codec.comparison().isEmpty()) {
            xml.append(" />\n");
            return;
        }
        xml.append(">\n");
        for (PipelineEvidence pipeline : codec.pipelines()) writePipeline(xml, pipeline);
        codec.comparison().ifPresent(value -> writeComparison(xml, value));
        xml.append("    </codec>\n");
    }

    private static void writePipeline(StringBuilder xml, PipelineEvidence pipeline) {
        xml.append("      <pipeline");
        attribute(xml, "id", pipeline.verificationPipelineId().value());
        attribute(xml, "availability", enumId(pipeline.availability()));
        xml.append(">\n        <rawFastCandidates>\n");
        for (CandidateKey candidate : pipeline.rawFastCandidates()) {
            xml.append("          <candidate");
            writeCandidateAttributes(xml, candidate);
            xml.append(" />\n");
        }
        xml.append("        </rawFastCandidates>\n        <verifiedFacts>\n");
        for (var fact : pipeline.candidateEvidence().entrySet()) {
            xml.append("          <fact");
            writeCandidateAttributes(xml, fact.getKey());
            attribute(xml, "outcome", enumId(fact.getValue()));
            xml.append(" />\n");
        }
        xml.append("        </verifiedFacts>\n      </pipeline>\n");
    }

    private static void writeComparison(
            StringBuilder xml, PipelineComparisonSnapshot snapshot) {
        PipelineComparison comparison = snapshot.comparison();
        xml.append("      <pipelineComparison");
        attribute(xml, "status", enumId(comparison.status()));
        attribute(xml, "coverage", enumId(comparison.coverage()));
        attribute(xml, "recommendation", enumId(comparison.recommendation()));
        attribute(xml, "recommendationReason",
                enumId(comparison.recommendationReason()));
        attribute(xml, "pipelineA", comparison.pipelineA().value());
        attribute(xml, "pipelineB", comparison.pipelineB().value());
        snapshot.pipelineAMedianTotalVerifyMillis().ifPresent(
                value -> attribute(xml, "pipelineAMedianMillis", value));
        snapshot.pipelineBMedianTotalVerifyMillis().ifPresent(
                value -> attribute(xml, "pipelineBMedianMillis", value));
        snapshot.pipelineACoverage().ifPresent(
                value -> writeCoverageAttributes(xml, "pipelineA", value));
        snapshot.pipelineBCoverage().ifPresent(
                value -> writeCoverageAttributes(xml, "pipelineB", value));
        xml.append(">\n");
        writeTupleSet(xml, "intersection", comparison.intersection());
        writeTupleSet(xml, "aOnly", comparison.aOnly());
        writeTupleSet(xml, "bOnly", comparison.bOnly());
        writeTupleSet(xml, "bothFail", comparison.bothFail());
        writeTupleSet(xml, "unknown", comparison.unknown());
        xml.append("      </pipelineComparison>\n");
    }

    private static void writeCoverageAttributes(StringBuilder xml, String prefix,
            PipelineVerificationCoverage coverage) {
        attribute(xml, prefix + "PlannedImages", coverage.plannedImageCount());
        attribute(xml, prefix + "VerifiedImages", coverage.verifiedImageCount());
        attribute(xml, prefix + "PlannedTuples", coverage.plannedTupleCount());
        attribute(xml, prefix + "VerifiedTuples", coverage.verifiedTupleCount());
    }

    private static void writeTupleSet(StringBuilder xml, String kind,
            Set<CaptureModeTuple> tuples) {
        xml.append("        <tupleSet");
        attribute(xml, "kind", kind);
        if (tuples.isEmpty()) {
            xml.append(" />\n");
            return;
        }
        xml.append(">\n");
        for (CaptureModeTuple tuple : tuples) {
            xml.append("          <tuple");
            writeTupleAttributes(xml, tuple);
            xml.append(" />\n");
        }
        xml.append("        </tupleSet>\n");
    }

    private static List<CameraId> parseCameraOrder(Element element)
            throws InvalidSnapshotXmlException {
        List<CameraId> result = new ArrayList<>();
        Set<CameraId> seen = new HashSet<>();
        for (Element child : children(element)) {
            requireTag(child, "cameraRef");
            CameraId cameraId = new CameraId(required(child, "id"));
            if (!seen.add(cameraId)) throw invalid("duplicate camera order key");
            result.add(cameraId);
        }
        return result;
    }

    private static CameraSnapshot parseCamera(Element element)
            throws InvalidSnapshotXmlException {
        CameraId cameraId = new CameraId(required(element, "id"));
        String hardwareSignature = required(element, "hardwareSignature");
        int sensorOrientationDegrees = integer(element, "sensorOrientationDegrees");
        List<CodecSnapshot> codecs = new ArrayList<>();
        Set<VideoCodec> codecIds = new HashSet<>();
        Optional<SelectedPipeline> selectedPipeline = Optional.empty();
        Optional<SelectedRecordingProfile> recordingProfile = Optional.empty();
        for (Element child : children(element)) {
            switch (child.getTagName()) {
                case "codec" -> {
                    CodecSnapshot codec = parseCodec(child, cameraId);
                    if (!codecIds.add(codec.codec())) throw invalid("duplicate codec key");
                    codecs.add(codec);
                }
                case "selectedPipeline" -> {
                    if (selectedPipeline.isPresent()) {
                        throw invalid("duplicate selected pipeline");
                    }
                    selectedPipeline = Optional.of(parseSelectedPipeline(child));
                }
                case "selectedRecordingProfile" -> {
                    if (recordingProfile.isPresent()) {
                        throw invalid("duplicate selected recording profile");
                    }
                    recordingProfile = Optional.of(parseSelectedRecordingProfile(child));
                }

                default -> throw invalid("unknown camera child");
            }
        }
        return new CameraSnapshot(cameraId, hardwareSignature,
                codecs, selectedPipeline, recordingProfile, sensorOrientationDegrees);
    }
    private static CodecSnapshot parseCodec(Element element, CameraId cameraId)
            throws InvalidSnapshotXmlException {
        VideoCodec codec = codec(required(element, "id"));
        CodecState state = enumValue(CodecState.class, required(element, "state"));
        Optional<UnsupportedCodecReason> reason = element.hasAttribute("reason")
                ? Optional.of(enumValue(UnsupportedCodecReason.class,
                required(element, "reason"))) : Optional.empty();
        List<PipelineEvidence> pipelines = new ArrayList<>();
        Set<VerificationPipelineId> pipelineIds = new HashSet<>();
        Optional<PipelineComparisonSnapshot> comparison = Optional.empty();
        for (Element child : children(element)) {
            switch (child.getTagName()) {
                case "pipeline" -> {
                    PipelineEvidence pipeline = parsePipeline(child, cameraId, codec);
                    if (!pipelineIds.add(pipeline.verificationPipelineId())) {
                        throw invalid("duplicate pipeline key");
                    }
                    pipelines.add(pipeline);
                }
                case "pipelineComparison" -> {
                    if (comparison.isPresent()) {
                        throw invalid("duplicate pipeline comparison");
                    }
                    comparison = Optional.of(parseComparison(child));
                }
                default -> throw invalid("unknown codec child");
            }
        }
        return new CodecSnapshot(codec, state, reason, pipelines, comparison);
    }

    private static PipelineEvidence parsePipeline(
            Element element, CameraId cameraId, VideoCodec codec)
            throws InvalidSnapshotXmlException {
        VerificationPipelineId pipelineId = pipelineId(required(element, "id"));
        PipelineAvailability availability = enumValue(PipelineAvailability.class,
                required(element, "availability"));
        List<CandidateKey> raw = new ArrayList<>();
        List<CandidateEvidence> facts = new ArrayList<>();
        Set<CandidateKey> rawKeys = new HashSet<>();
        Set<CandidateKey> factKeys = new HashSet<>();
        boolean rawSeen = false;
        boolean factsSeen = false;
        for (Element child : children(element)) {
            switch (child.getTagName()) {
                case "rawFastCandidates" -> {
                    if (rawSeen) throw invalid("duplicate raw candidate set");
                    rawSeen = true;
                    for (Element candidateElement : children(child)) {
                        requireTag(candidateElement, "candidate");
                        CandidateKey candidate = parseCandidate(
                                candidateElement, cameraId, codec, pipelineId);
                        if (!rawKeys.add(candidate)) {
                            throw invalid("duplicate candidate key");
                        }
                        raw.add(candidate);
                    }
                }
                case "verifiedFacts" -> {
                    if (factsSeen) throw invalid("duplicate verified fact set");
                    factsSeen = true;
                    for (Element factElement : children(child)) {
                        requireTag(factElement, "fact");
                        CandidateKey candidate = parseCandidate(
                                factElement, cameraId, codec, pipelineId);
                        if (!factKeys.add(candidate)) {
                            throw invalid("duplicate tuple evidence key");
                        }
                        VerificationOutcome outcome = enumValue(
                                VerificationOutcome.class,
                                required(factElement, "outcome"));
                        if (!outcome.isTerminalEvidence()) {
                            throw invalid("non-durable verification outcome");
                        }
                        facts.add(new CandidateEvidence(candidate, outcome));
                    }
                }
                default -> throw invalid("unknown pipeline child");
            }
        }
        if (!rawSeen || !factsSeen) throw invalid("pipeline evidence is incomplete");
        return new PipelineEvidence(cameraId, codec, pipelineId,
                availability, raw, facts);
    }

    private static PipelineComparisonSnapshot parseComparison(Element element)
            throws InvalidSnapshotXmlException {
        PipelineComparison.Status status = enumValue(PipelineComparison.Status.class,
                required(element, "status"));
        PipelineComparison.Coverage coverage = enumValue(PipelineComparison.Coverage.class,
                required(element, "coverage"));
        PipelineComparison.Recommendation recommendation = enumValue(
                PipelineComparison.Recommendation.class,
                required(element, "recommendation"));
        PipelineComparison.RecommendationReason reason = enumValue(
                PipelineComparison.RecommendationReason.class,
                required(element, "recommendationReason"));
        VerificationPipelineId pipelineA = pipelineId(required(element, "pipelineA"));
        VerificationPipelineId pipelineB = pipelineId(required(element, "pipelineB"));
        Map<String, List<CaptureModeTuple>> sets = new HashMap<>();
        Set<CaptureModeTuple> allKeys = new HashSet<>();
        for (Element child : children(element)) {
            requireTag(child, "tupleSet");
            String kind = required(child, "kind");
            if (!Set.of("intersection", "aOnly", "bOnly", "bothFail", "unknown")
                    .contains(kind) || sets.containsKey(kind)) {
                throw invalid("duplicate or unknown comparison set");
            }
            List<CaptureModeTuple> tuples = new ArrayList<>();
            for (Element tupleElement : children(child)) {
                requireTag(tupleElement, "tuple");
                CaptureModeTuple tuple = parseTuple(tupleElement);
                if (!allKeys.add(tuple)) throw invalid("duplicate comparison tuple key");
                tuples.add(tuple);
            }
            sets.put(kind, tuples);
        }
        if (sets.size() != 5) throw invalid("pipeline comparison is incomplete");
        PipelineComparison comparison = new PipelineComparison(
                status, coverage, recommendation, reason,
                pipelineA, pipelineB, Set.copyOf(sets.get("intersection")),
                Set.copyOf(sets.get("aOnly")), Set.copyOf(sets.get("bOnly")),
                Set.copyOf(sets.get("bothFail")), Set.copyOf(sets.get("unknown")));
        return new PipelineComparisonSnapshot(comparison,
                optionalLong(element, "pipelineAMedianMillis"),
                optionalLong(element, "pipelineBMedianMillis"),
                optionalCoverage(element, "pipelineA"),
                optionalCoverage(element, "pipelineB"));
    }

    private static SelectedPipeline parseSelectedPipeline(Element element)
            throws InvalidSnapshotXmlException {
        return new SelectedPipeline(
                enumValue(PipelineSelectionMode.class, required(element, "mode")),
                pipelineId(required(element, "pipelineId")),
                enumValue(PipelineDecisionStatus.class,
                        required(element, "decisionStatus")),
                enumValue(PipelineDecisionReason.class,
                        required(element, "decisionReason")));
    }

    private static SelectedRecordingProfile parseSelectedRecordingProfile(Element element)
            throws InvalidSnapshotXmlException {
        return new SelectedRecordingProfile(codec(required(element, "codecId")),
                pipelineId(required(element, "pipelineId")), parseTuple(element));
    }

    private static VerificationPipelineId pipelineId(String value) {
        return new VerificationPipelineId(value);
    }

    private static CandidateKey parseCandidate(Element element, CameraId cameraId,
            VideoCodec codec, VerificationPipelineId pipelineId)
            throws InvalidSnapshotXmlException {
        CandidateKey.Kind kind = enumValue(CandidateKey.Kind.class,
                required(element, "kind"));
        return switch (kind) {
            case VIDEO -> CandidateKey.forVideo(cameraId, codec, pipelineId,
                    parseVideoMode(element));
            case IMAGE -> CandidateKey.forImage(cameraId, codec, pipelineId,
                    parseImageMode(element));
            case TUPLE -> CandidateKey.forTuple(cameraId, codec, pipelineId,
                    parseTuple(element));
        };
    }

    private static CaptureModeTuple parseTuple(Element element)
            throws InvalidSnapshotXmlException {
        return new CaptureModeTuple(parseVideoMode(element), parseImageMode(element));
    }

    private static VideoMode parseVideoMode(Element element)
            throws InvalidSnapshotXmlException {
        return new VideoMode(parseResolution(element, "video"), integer(element, "fps"));
    }

    private static ImageMode parseImageMode(Element element)
            throws InvalidSnapshotXmlException {
        return new ImageMode(parseResolution(element, "image"));
    }

    private static StandardResolution parseResolution(Element element, String prefix)
            throws InvalidSnapshotXmlException {
        StandardResolutionLabel label = enumValue(StandardResolutionLabel.class,
                required(element, prefix + "Label"));
        CameraResolution actual = new CameraResolution(
                integer(element, prefix + "Width"),
                integer(element, prefix + "Height"));
        return new StandardResolution(label, actual);
    }

    private static void writeCandidateAttributes(StringBuilder xml, CandidateKey candidate) {
        attribute(xml, "kind", enumId(candidate.kind()));
        candidate.videoMode().ifPresent(value -> writeVideoAttributes(xml, value));
        candidate.imageMode().ifPresent(value -> writeImageAttributes(xml, value));
    }

    private static void writeTupleAttributes(StringBuilder xml, CaptureModeTuple tuple) {
        writeVideoAttributes(xml, tuple.videoMode());
        writeImageAttributes(xml, tuple.imageMode());
    }

    private static void writeVideoAttributes(StringBuilder xml, VideoMode video) {
        writeResolutionAttributes(xml, "video", video.resolution());
        attribute(xml, "fps", video.framesPerSecond());
    }

    private static void writeImageAttributes(StringBuilder xml, ImageMode image) {
        writeResolutionAttributes(xml, "image", image.resolution());
    }

    private static void writeResolutionAttributes(
            StringBuilder xml, String prefix, StandardResolution resolution) {
        attribute(xml, prefix + "Label", enumId(resolution.label()));
        attribute(xml, prefix + "Width", resolution.actual().width());
        attribute(xml, prefix + "Height", resolution.actual().height());
    }

    private static VideoCodec codec(String id) throws InvalidSnapshotXmlException {
        for (VideoCodec codec : VideoCodec.values()) {
            if (codec.id().equals(id)) return codec;
        }
        throw invalid("unknown codec ID");
    }

    private static <T extends Enum<T>> T enumValue(Class<T> type, String id)
            throws InvalidSnapshotXmlException {
        try {
            return Enum.valueOf(type, id.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException error) {
            throw new InvalidSnapshotXmlException("unknown enum value", error);
        }
    }

    private static String enumId(Enum<?> value) {
        return value.name().toLowerCase(Locale.ROOT);
    }

    private static String required(Element element, String name)
            throws InvalidSnapshotXmlException {
        if (!element.hasAttribute(name) || element.getAttribute(name).isBlank()) {
            throw invalid("missing XML attribute");
        }
        return element.getAttribute(name);
    }

    private static Optional<PipelineVerificationCoverage> optionalCoverage(
            Element element, String prefix) throws InvalidSnapshotXmlException {
        String plannedImages = prefix + "PlannedImages";
        String verifiedImages = prefix + "VerifiedImages";
        String plannedTuples = prefix + "PlannedTuples";
        String verifiedTuples = prefix + "VerifiedTuples";
        int present = 0;
        for (String name : List.of(
                plannedImages, verifiedImages, plannedTuples, verifiedTuples)) {
            if (element.hasAttribute(name)) present++;
        }
        if (present == 0) return Optional.empty();
        if (present != 4) throw invalid("pipeline verification coverage is incomplete");
        return Optional.of(new PipelineVerificationCoverage(
                integer(element, plannedImages), integer(element, verifiedImages),
                integer(element, plannedTuples), integer(element, verifiedTuples)));
    }

    private static OptionalLong optionalLong(Element element, String name)
            throws InvalidSnapshotXmlException {
        if (!element.hasAttribute(name)) return OptionalLong.empty();
        try {
            return OptionalLong.of(Long.parseLong(required(element, name)));
        } catch (NumberFormatException error) {
            throw new InvalidSnapshotXmlException("invalid long attribute", error);
        }
    }
    private static int integer(Element element, String name)
            throws InvalidSnapshotXmlException {
        try {
            return Integer.parseInt(required(element, name));
        } catch (NumberFormatException error) {
            throw new InvalidSnapshotXmlException("invalid integer attribute", error);
        }
    }

    private static List<Element> children(Element parent) {
        List<Element> result = new ArrayList<>();
        NodeList nodes = parent.getChildNodes();
        for (int index = 0; index < nodes.getLength(); index++) {
            Node node = nodes.item(index);
            if (node instanceof Element element) result.add(element);
        }
        return result;
    }

    private static void requireTag(Element element, String expected)
            throws InvalidSnapshotXmlException {
        if (!expected.equals(element.getTagName())) throw invalid("unexpected XML tag");
    }

    private static void attribute(StringBuilder xml, String name, Object value) {
        xml.append(' ').append(name).append("=\"");
        escape(xml, String.valueOf(value));
        xml.append('\"');
    }

    private static void escape(StringBuilder xml, String value) {
        for (int index = 0; index < value.length(); index++) {
            switch (value.charAt(index)) {
                case '&' -> xml.append("&amp;");
                case '<' -> xml.append("&lt;");
                case '"' -> xml.append("&quot;");
                case '\n' -> xml.append("&#10;");
                case '\r' -> xml.append("&#13;");
                case '\t' -> xml.append("&#9;");
                default -> xml.append(value.charAt(index));
            }
        }
    }

    private static String strictUtf8(byte[] bytes) throws InvalidSnapshotXmlException {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException error) {
            throw new InvalidSnapshotXmlException("capability XML is not UTF-8", error);
        }
    }

    private static void setFeature(
            DocumentBuilderFactory factory, String feature, boolean value) {
        try {
            factory.setFeature(feature, value);
        } catch (ParserConfigurationException ignored) {
        }
    }

    private static InvalidSnapshotXmlException invalid(String message) {
        return new InvalidSnapshotXmlException(message);
    }


    private static final class ThrowingErrorHandler implements ErrorHandler {
        @Override public void warning(SAXParseException error) throws SAXException {
            throw error;
        }

        @Override public void error(SAXParseException error) throws SAXException {
            throw error;
        }

        @Override public void fatalError(SAXParseException error) throws SAXException {
            throw error;
        }
    }
}

final class InvalidSnapshotXmlException extends Exception {
    InvalidSnapshotXmlException(String message) { super(message); }
    InvalidSnapshotXmlException(String message, Throwable cause) { super(message, cause); }
}

final class UnsupportedSnapshotFormatException extends Exception {
    UnsupportedSnapshotFormatException(int format) {
        super("unsupported capability format: " + format);
    }
}