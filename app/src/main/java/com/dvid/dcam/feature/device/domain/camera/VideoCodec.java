package com.dvid.dcam.feature.device.domain.camera;

public enum VideoCodec {
    H264("h264"),
    H265("h265");

    private final String id;

    VideoCodec(String id) { this.id = id; }

    public String id() { return id; }
}