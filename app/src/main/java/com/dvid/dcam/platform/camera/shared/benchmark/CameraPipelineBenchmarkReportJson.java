package com.dvid.dcam.platform.camera.shared.benchmark;

import android.util.AtomicFile;
import android.util.JsonWriter;
import com.dvid.dcam.feature.device.domain.camera.CameraPipelineBenchmarkReport;
import com.dvid.dcam.feature.device.domain.camera.CameraPipelineBenchmarkReport.CameraComparison;
import com.dvid.dcam.feature.device.domain.camera.CameraPipelineBenchmarkReport.MetricSummary;
import com.dvid.dcam.feature.device.domain.camera.CameraPipelineBenchmarkReport.PerformanceSummary;
import com.dvid.dcam.feature.device.domain.camera.CameraPipelineBenchmarkReport.PipelineCoverage;
import com.dvid.dcam.feature.device.domain.camera.CameraPipelineBenchmarkReport.TupleOutcome;
import com.dvid.dcam.feature.device.domain.camera.CaptureModeTuple;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

public final class CameraPipelineBenchmarkReportJson {
    private CameraPipelineBenchmarkReportJson() {}

    public static File writeAtomic(File target, CameraPipelineBenchmarkReport report)
            throws IOException {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(report, "report");
        File parent = target.getParentFile();
        if (parent == null || (!parent.isDirectory() && !parent.mkdirs() && !parent.isDirectory())) {
            throw new IOException("Could not create report directory");
        }
        AtomicFile atomic = new AtomicFile(target);
        FileOutputStream output = atomic.startWrite();
        JsonWriter json = new JsonWriter(new OutputStreamWriter(
                output, StandardCharsets.UTF_8));
        try {
            json.setIndent("  ");
            write(json, report);
            json.flush();
            atomic.finishWrite(output);
        } catch (IOException | RuntimeException error) {
            atomic.failWrite(output);
            throw error;
        }
        return target;
    }

    private static void write(JsonWriter json, CameraPipelineBenchmarkReport report)
            throws IOException {
        json.beginObject();
        json.name("status").value(report.status().name().toLowerCase());
        json.name("publishedDurably").value(report.publishedDurably());
        json.name("elapsedMillis").value(report.elapsedMillis());
        json.name("detail").value(report.detail());
        json.name("hardwareSignature").value(report.plan().environment().hardwareSignature());
        json.name("codec").value(report.plan().environment().codec().id());
        json.name("h264Configuration").value(report.plan().environment().h264Configuration());
        json.name("cropRotationPolicy").value(report.plan().environment().cropRotationPolicy());
        json.name("candidateTimeoutMillis").value(
                report.plan().environment().candidateTimeoutMillis());
        json.name("storagePath").value(report.plan().environment().storagePath());
        json.name("thermalGate").value(report.plan().environment().thermalGate());
        json.name("protocol").beginObject();
        json.name("measuredBlocks").value(report.plan().protocol().measuredBlocks());
        json.name("initialOrder").value(report.plan().protocol().initialOrder().name());
        json.name("cooldownMillis").value(report.plan().protocol().cooldownMillis());
        json.endObject();
        json.name("cameras").beginArray();
        for (CameraComparison camera : report.cameras()) writeCamera(json, camera);
        json.endArray();
        json.endObject();
    }

    private static void writeCamera(JsonWriter json, CameraComparison camera) throws IOException {
        json.beginObject();
        json.name("cameraId").value(camera.cameraId().value());
        json.name("candidateUniverse");
        writeTuples(json, camera.candidateUniverse());
        json.name("comparison").beginObject();
        json.name("status").value(camera.comparison().status().name());
        json.name("coverage").value(camera.comparison().coverage().name());
        json.name("recommendation").value(camera.comparison().recommendation().name());
        json.name("recommendationReason").value(
                camera.comparison().recommendationReason().name());
        json.name("intersection"); writeTuples(json, camera.comparison().intersection());
        json.name("aOnly"); writeTuples(json, camera.comparison().aOnly());
        json.name("bOnly"); writeTuples(json, camera.comparison().bOnly());
        json.name("bothFail"); writeTuples(json, camera.comparison().bothFail());
        json.name("unknown"); writeTuples(json, camera.comparison().unknown());
        json.endObject();
        json.name("pipelineA"); writeCoverage(json, camera.pipelineA());
        json.name("pipelineB"); writeCoverage(json, camera.pipelineB());
        json.name("performance").beginArray();
        for (PerformanceSummary performance : camera.performance()) {
            json.beginObject();
            json.name("tuple").value(performance.tuple().toString());
            json.name("fastScanA"); writeMetric(json, performance.fastScanA());
            json.name("fastScanB"); writeMetric(json, performance.fastScanB());
            json.name("totalVerifyA"); writeMetric(json, performance.totalVerifyA());
            json.name("totalVerifyB"); writeMetric(json, performance.totalVerifyB());
            json.name("bindToPreviewA"); writeMetric(json, performance.bindToPreviewA());
            json.name("bindToPreviewB"); writeMetric(json, performance.bindToPreviewB());
            json.name("firstEncodedSampleA"); writeMetric(json, performance.firstEncodedSampleA());
            json.name("firstEncodedSampleB"); writeMetric(json, performance.firstEncodedSampleB());
            json.name("jpegCaptureA"); writeMetric(json, performance.jpegCaptureA());
            json.name("jpegCaptureB"); writeMetric(json, performance.jpegCaptureB());
            json.name("stopFinalizeA"); writeMetric(json, performance.stopFinalizeA());
            json.name("stopFinalizeB"); writeMetric(json, performance.stopFinalizeB());
            json.name("measuredFpsA"); writeMetric(json, performance.measuredFpsA());
            json.name("measuredFpsB"); writeMetric(json, performance.measuredFpsB());
            json.name("droppedFramesA"); writeMetric(json, performance.droppedFramesA());
            json.name("droppedFramesB"); writeMetric(json, performance.droppedFramesB());
            json.name("resourcesA"); writeResources(json, performance.resourcesA());
            json.name("resourcesB"); writeResources(json, performance.resourcesB());
            json.endObject();
        }
        json.endArray();
        json.endObject();
    }

    private static void writeCoverage(JsonWriter json, PipelineCoverage coverage)
            throws IOException {
        json.beginObject();
        json.name("pipelineId").value(coverage.pipelineId().value());
        json.name("status").value(coverage.status().name());
        json.name("availability").value(coverage.evidence().availability().name());
        json.name("fastScanMillis").value(coverage.fastScanMillis());
        json.name("realVerifyMillis").value(coverage.realVerifyMillis());
        json.name("cleanupComplete").value(coverage.cleanupComplete());
        json.name("passCount").value(coverage.passCount());
        json.name("failCount").value(coverage.failCount());
        json.name("unknownCount").value(coverage.unknownCount());
        json.name("bestVerifiedTuple").value(
                coverage.bestVerifiedTuple().map(Object::toString).orElse(null));
        json.name("outcomes").beginArray();
        for (TupleOutcome outcome : coverage.outcomes()) {
            json.beginObject();
            json.name("tuple").value(outcome.tuple().toString());
            json.name("outcome").value(outcome.outcome().name());
            json.name("stage").value(outcome.stage().name());
            json.name("reason").value(outcome.reason());
            json.endObject();
        }
        json.endArray();
        json.endObject();
    }

    private static void writeResources(JsonWriter json,
            CameraPipelineBenchmarkReport.ResourceSummary resources) throws IOException {
        json.beginObject();
        json.name("cpuLoad"); writeMetric(json, resources.cpuLoad());
        json.name("gpuLoad"); writeMetric(json, resources.gpuLoad());
        json.name("memoryBytes"); writeMetric(json, resources.memoryBytes());
        json.name("thermalStatus"); writeMetric(json, resources.thermalStatus());
        json.endObject();
    }
    private static void writeMetric(JsonWriter json, MetricSummary metric) throws IOException {
        json.beginObject();
        json.name("sampleCount").value(metric.sampleCount());
        if (metric.median().isPresent()) json.name("median").value(metric.median().orElseThrow());
        else json.name("median").nullValue();
        if (metric.p95().isPresent()) json.name("p95").value(metric.p95().orElseThrow());
        else json.name("p95").nullValue();
        json.endObject();
    }

    private static void writeTuples(JsonWriter json, Iterable<CaptureModeTuple> tuples)
            throws IOException {
        json.beginArray();
        for (CaptureModeTuple tuple : tuples) json.value(tuple.toString());
        json.endArray();
    }
}