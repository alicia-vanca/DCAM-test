package com.dvid.dcam.core.input.domain;

/** One configurable hardware control binding. */
public final class HardwareButtonBinding {
    private final ButtonRole role;
    private final int buttonKeyCode;
    private final PhysicalButtonType type;
    private final HardwareButtonInputSource inputSource;
    private final FirmwareButtonActionFamily firmwareActionFamily;

    public HardwareButtonBinding(ButtonRole role, int buttonKeyCode, PhysicalButtonType type) {
        this(role, buttonKeyCode, type, HardwareButtonInputSource.KEY_EVENT,
                FirmwareButtonActionFamily.UNASSIGNED);
    }

    public HardwareButtonBinding(
            ButtonRole role,
            int buttonKeyCode,
            PhysicalButtonType type,
            FirmwareButtonActionFamily firmwareActionFamily) {
        this(role, buttonKeyCode, type, HardwareButtonInputSource.KEY_EVENT,
                firmwareActionFamily);
    }

    public HardwareButtonBinding(
            ButtonRole role,
            int buttonKeyCode,
            PhysicalButtonType type,
            HardwareButtonInputSource inputSource,
            FirmwareButtonActionFamily firmwareActionFamily) {
        if (role == null) throw new IllegalArgumentException("role is required");
        if (type == null) throw new IllegalArgumentException("type is required");
        if (inputSource == null) throw new IllegalArgumentException("inputSource is required");
        if (firmwareActionFamily == null) {
            throw new IllegalArgumentException("firmwareActionFamily is required");
        }
        this.role = role;
        this.buttonKeyCode = buttonKeyCode;
        this.type = type;
        this.inputSource = inputSource;
        this.firmwareActionFamily = firmwareActionFamily;
    }

    public ButtonRole role() { return role; }
    public int buttonKeyCode() { return buttonKeyCode; }
    public PhysicalButtonType type() { return type; }
    public HardwareButtonInputSource inputSource() { return inputSource; }
    public FirmwareButtonActionFamily firmwareActionFamily() { return firmwareActionFamily; }
    public boolean hasFirmwareBroadcast() { return firmwareActionFamily.isAssigned(); }
}
