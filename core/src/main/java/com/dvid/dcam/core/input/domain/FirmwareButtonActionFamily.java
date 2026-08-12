package com.dvid.dcam.core.input.domain;

/** Compatible vendor-firmware broadcast action pairs. */
public enum FirmwareButtonActionFamily {
    UNASSIGNED("Unassigned", null, null),
    ACTION_CAMERA(
            "ACTION_CAMERA",
            "android.intent.action.ACTION_CAMERA_DOWN",
            "android.intent.action.ACTION_CAMERA_UP"),
    ACTION_LASER(
            "ACTION_LASER",
            "android.intent.action.ACTION_LASER_DOWN",
            "android.intent.action.ACTION_LASER_UP"),
    ACTION_PTTKEY(
            "ACTION_PTTKEY",
            "android.intent.action.ACTION_PTTKEY_DOWN",
            "android.intent.action.ACTION_PTTKEY_UP"),
    ACTION_RECORD(
            "ACTION_RECORD",
            "android.intent.action.ACTION_RECORD_DOWN",
            "android.intent.action.ACTION_RECORD_UP"),
    ACTION_SOS(
            "ACTION_SOS",
            "android.intent.action.ACTION_SOS_DOWN",
            "android.intent.action.ACTION_SOS_UP"),
    ACTION_VIDEO(
            "ACTION_VIDEO",
            "android.intent.action.ACTION_VIDEO_DOWN",
            "android.intent.action.ACTION_VIDEO_UP");

    private final String label;
    private final String downAction;
    private final String upAction;

    FirmwareButtonActionFamily(String label, String downAction, String upAction) {
        this.label = label;
        this.downAction = downAction;
        this.upAction = upAction;
    }

    public String label() { return label; }
    public String downAction() { return downAction; }
    public String upAction() { return upAction; }
    public boolean isAssigned() { return this != UNASSIGNED; }
}
