package com.dvid.dcam.core.input.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Immutable collection of physical buttons available on one hardware layout. */
public final class HardwareButtonLayout {
    private static final HardwareButtonLayout EMPTY = new HardwareButtonLayout();
    private final List<HardwareButtonBinding> bindings;
    private final Map<Integer, HardwareButtonBinding> bindingsByKeyCode;
    private final Map<String, HardwareButtonBinding> bindingsByFirmwareDownAction;
    private final Map<String, HardwareButtonBinding> bindingsByFirmwareUpAction;

    public HardwareButtonLayout(HardwareButtonBinding... bindings) {
        List<HardwareButtonBinding> configured = new ArrayList<>();
        Map<Integer, HardwareButtonBinding> byKeyCode = new LinkedHashMap<>();
        Map<String, HardwareButtonBinding> byFirmwareDownAction = new LinkedHashMap<>();
        Map<String, HardwareButtonBinding> byFirmwareUpAction = new LinkedHashMap<>();
        if (bindings != null) {
            for (HardwareButtonBinding binding : bindings) {
                if (binding == null) throw new IllegalArgumentException("binding is required");
                configured.add(binding);
                if (binding.inputSource() == HardwareButtonInputSource.KEY_EVENT) {
                    if (binding.buttonKeyCode() < 0) continue;
                    if (byKeyCode.put(binding.buttonKeyCode(), binding) != null) {
                        throw new IllegalArgumentException(
                                "Duplicate buttonKeyCode " + binding.buttonKeyCode());
                    }
                } else if (binding.hasFirmwareBroadcast()) {
                    FirmwareButtonActionFamily family = binding.firmwareActionFamily();
                    if (byFirmwareDownAction.put(family.downAction(), binding) != null) {
                        throw new IllegalArgumentException(
                                "Duplicate firmware broadcast action " + family.downAction());
                    }
                    if (byFirmwareUpAction.put(family.upAction(), binding) != null) {
                        throw new IllegalArgumentException(
                                "Duplicate firmware broadcast action " + family.upAction());
                    }
                }
            }
        }
        this.bindings = Collections.unmodifiableList(configured);
        bindingsByKeyCode = Collections.unmodifiableMap(byKeyCode);
        bindingsByFirmwareDownAction = Collections.unmodifiableMap(byFirmwareDownAction);
        bindingsByFirmwareUpAction = Collections.unmodifiableMap(byFirmwareUpAction);
    }

    public HardwareButtonBinding findByButtonKeyCode(int buttonKeyCode) {
        return bindingsByKeyCode.get(buttonKeyCode);
    }

    public HardwareButtonBinding findByFirmwareBroadcastDownAction(String action) {
        return bindingsByFirmwareDownAction.get(action);
    }

    public HardwareButtonBinding findByFirmwareBroadcastUpAction(String action) {
        return bindingsByFirmwareUpAction.get(action);
    }

    public List<String> firmwareBroadcastActions() {
        List<String> actions = new ArrayList<>(
                bindingsByFirmwareDownAction.size() + bindingsByFirmwareUpAction.size());
        for (HardwareButtonBinding binding : bindings) {
            if (binding.inputSource() != HardwareButtonInputSource.FIRMWARE_BROADCAST
                    || !binding.hasFirmwareBroadcast()) continue;
            actions.add(binding.firmwareActionFamily().downAction());
            actions.add(binding.firmwareActionFamily().upAction());
        }
        return List.copyOf(actions);
    }

    public boolean has(ButtonRole role) {
        for (HardwareButtonBinding binding : bindings) {
            if (binding.role() == role) return true;
        }
        return false;
    }

    public boolean isEmpty() { return bindings.isEmpty(); }

    public List<HardwareButtonBinding> bindings() { return bindings; }

    public static HardwareButtonLayout empty() { return EMPTY; }
}
