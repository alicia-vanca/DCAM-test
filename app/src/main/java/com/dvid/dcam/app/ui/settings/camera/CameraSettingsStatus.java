package com.dvid.dcam.app.ui.settings.camera;

/** User-visible camera settings lifecycle state. */
public enum CameraSettingsStatus {
    LOADING("Loading"),
    VERIFYING("Verifying"),
    READY("Ready"),
    RECOVERING("Recovering"),
    UNAVAILABLE("Unavailable"),
    PIPELINE_INCOMPLETE("Pipeline incomplete");

    private final String label;

    CameraSettingsStatus(String label) {
        this.label = label;
    }

    public String label() { return label; }
}