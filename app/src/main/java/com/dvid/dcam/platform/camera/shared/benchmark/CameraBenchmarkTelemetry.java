package com.dvid.dcam.platform.camera.shared.benchmark;

import com.dvid.dcam.feature.device.domain.camera.CameraPipelineBenchmarkReport.ResourceSample;

public interface CameraBenchmarkTelemetry {
    Token begin();
    ResourceSample end(Token token);

    interface Token {}

    CameraBenchmarkTelemetry UNAVAILABLE = new CameraBenchmarkTelemetry() {
        @Override public Token begin() { return new Token() {}; }
        @Override public ResourceSample end(Token token) { return ResourceSample.unavailable(); }
    };
}