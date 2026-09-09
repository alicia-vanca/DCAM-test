package com.dvid.dcam.app.ui.settings;

import android.view.KeyEvent;
import com.dvid.dcam.core.input.domain.ButtonRole;
import com.dvid.dcam.core.input.domain.HardwareButtonLayout;
import com.dvid.dcam.feature.input.application.usecase.ConfigureHardwareButtonsUseCase;
import java.util.List;
import java.util.Objects;
import java.util.function.IntFunction;

/** View-independent state and actions for developer hardware-button bindings. */
public final class DeveloperButtonBindingsController {
    private final ConfigureHardwareButtonsUseCase buttons;
    private final KeyEventConsole keyEventConsole;
    private final IntFunction<String> keyName;

    public DeveloperButtonBindingsController(ConfigureHardwareButtonsUseCase buttons) {
        this(buttons, 12);
    }

    DeveloperButtonBindingsController(ConfigureHardwareButtonsUseCase buttons, int capacity) {
        this(buttons, capacity, KeyEvent::keyCodeToString);
    }

    DeveloperButtonBindingsController(ConfigureHardwareButtonsUseCase buttons, int capacity,
            IntFunction<String> keyName) {
        this.buttons = Objects.requireNonNull(buttons, "buttons");
        this.keyEventConsole = new KeyEventConsole(capacity);
        this.keyName = Objects.requireNonNull(keyName, "keyName");
    }

    public SettingsScreenModel model() {
        List<String> keyCodes = buttons.keyCodeLabels();
        List<String> types = buttons.typeLabels();
        List<String> actionFamilies = buttons.actionFamilyLabels();
        List<String> sourceLabels = buttons.sourceLabels();
        return new SettingsScreenModel(List.of(
                section("Record", ButtonRole.RECORD, SettingId.DEV_BUTTON_RECORD_TYPE,
                        SettingId.DEV_BUTTON_RECORD_SOURCE,
                        SettingId.DEV_BUTTON_RECORD_ACTION_FAMILY,
                        SettingId.DEV_BUTTON_RECORD_KEY_CODE, types, sourceLabels,
                        actionFamilies, keyCodes),
                section("Important recording", ButtonRole.IMPORTANT_RECORDING,
                        SettingId.DEV_BUTTON_IMPORTANT_RECORDING_TYPE,
                        SettingId.DEV_BUTTON_IMPORTANT_RECORDING_SOURCE,
                        SettingId.DEV_BUTTON_IMPORTANT_RECORDING_ACTION_FAMILY,
                        SettingId.DEV_BUTTON_IMPORTANT_RECORDING_KEY_CODE, types, sourceLabels,
                        actionFamilies, keyCodes),
                section("Photo capture", ButtonRole.PHOTO_CAPTURE,
                        SettingId.DEV_BUTTON_PHOTO_CAPTURE_TYPE,
                        SettingId.DEV_BUTTON_PHOTO_CAPTURE_SOURCE,
                        SettingId.DEV_BUTTON_PHOTO_CAPTURE_ACTION_FAMILY,
                        SettingId.DEV_BUTTON_PHOTO_CAPTURE_KEY_CODE, types, sourceLabels,
                        actionFamilies, keyCodes),
                section("Audio capture", ButtonRole.AUDIO_CAPTURE,
                        SettingId.DEV_BUTTON_AUDIO_CAPTURE_TYPE,
                        SettingId.DEV_BUTTON_AUDIO_CAPTURE_SOURCE,
                        SettingId.DEV_BUTTON_AUDIO_CAPTURE_ACTION_FAMILY,
                        SettingId.DEV_BUTTON_AUDIO_CAPTURE_KEY_CODE, types, sourceLabels,
                        actionFamilies, keyCodes),
                section("PTT", ButtonRole.PTT, SettingId.DEV_BUTTON_PTT_TYPE,
                        SettingId.DEV_BUTTON_PTT_SOURCE,
                        SettingId.DEV_BUTTON_PTT_ACTION_FAMILY,
                        SettingId.DEV_BUTTON_PTT_KEY_CODE, types, sourceLabels,
                        actionFamilies, keyCodes),
                section("SOS", ButtonRole.SOS, SettingId.DEV_BUTTON_SOS_TYPE,
                        SettingId.DEV_BUTTON_SOS_SOURCE,
                        SettingId.DEV_BUTTON_SOS_ACTION_FAMILY,
                        SettingId.DEV_BUTTON_SOS_KEY_CODE, types, sourceLabels,
                        actionFamilies, keyCodes)));
    }

    public HardwareButtonLayout select(SettingId id, int selectedIndex) {
        ButtonRole role = typeRole(id);
        if (role != null) return buttons.selectType(role, selectedIndex);
        role = sourceRole(id);
        if (role != null) return buttons.selectSource(role, selectedIndex);
        role = actionFamilyRole(id);
        if (role != null) return buttons.selectActionFamily(role, selectedIndex);
        role = keyCodeRole(id);
        if (role != null) return buttons.selectKeyCode(role, selectedIndex);
        throw new IllegalArgumentException("Unsupported developer button setting " + id);
    }

    public boolean hasDefaults() {
        return buttons.hasDefaults();
    }

    public HardwareButtonLayout resetDefaults() {
        if (!hasDefaults()) {
            throw new IllegalStateException("No default button preset exists for this device");
        }
        return buttons.resetDefaults();
    }

    public boolean onKeyDown(int keyCode) {
        append(keyCode, "DOWN");
        return keyCode != KeyEvent.KEYCODE_BACK;
    }

    public boolean onKeyUp(int keyCode) {
        append(keyCode, "UP");
        return keyCode != KeyEvent.KEYCODE_BACK;
    }

    public String consoleText() {
        return keyEventConsole.text();
    }

    private SettingsSection section(String title, ButtonRole role, SettingId typeId,
            SettingId sourceId, SettingId actionFamilyId, SettingId keyCodeId,
            List<String> types, List<String> sourceLabels, List<String> actionFamilies,
            List<String> keyCodes) {
        List<DescribedRadioOptionUiState> sources = List.of(
                new DescribedRadioOptionUiState(sourceLabels.get(0),
                        "Works on compatible vendor firmware.")
                        .withNestedChoice(SettingItem.choice(actionFamilyId, "Action family",
                                actionFamilies, buttons.selectedActionFamilyIndex(role))
                                .withIndentLevel(1)),
                new DescribedRadioOptionUiState(sourceLabels.get(1),
                        "Unavailable while screen is off.")
                        .withNestedChoice(SettingItem.choice(keyCodeId, "Keycode", keyCodes,
                                buttons.selectedKeyCodeIndex(role)).withIndentLevel(1)));
        return new SettingsSection(title, List.of(
                SettingItem.choice(typeId, "Input type", types,
                        buttons.selectedTypeIndex(role)),
                SettingItem.describedRadio(sourceId, "Input source", sources,
                        buttons.selectedSourceIndex(role))));
    }

    private static ButtonRole typeRole(SettingId id) {
        switch (id) {
            case DEV_BUTTON_RECORD_TYPE: return ButtonRole.RECORD;
            case DEV_BUTTON_IMPORTANT_RECORDING_TYPE: return ButtonRole.IMPORTANT_RECORDING;
            case DEV_BUTTON_PHOTO_CAPTURE_TYPE: return ButtonRole.PHOTO_CAPTURE;
            case DEV_BUTTON_AUDIO_CAPTURE_TYPE: return ButtonRole.AUDIO_CAPTURE;
            case DEV_BUTTON_PTT_TYPE: return ButtonRole.PTT;
            case DEV_BUTTON_SOS_TYPE: return ButtonRole.SOS;
            default: return null;
        }
    }

    private static ButtonRole sourceRole(SettingId id) {
        switch (id) {
            case DEV_BUTTON_RECORD_SOURCE: return ButtonRole.RECORD;
            case DEV_BUTTON_IMPORTANT_RECORDING_SOURCE: return ButtonRole.IMPORTANT_RECORDING;
            case DEV_BUTTON_PHOTO_CAPTURE_SOURCE: return ButtonRole.PHOTO_CAPTURE;
            case DEV_BUTTON_AUDIO_CAPTURE_SOURCE: return ButtonRole.AUDIO_CAPTURE;
            case DEV_BUTTON_PTT_SOURCE: return ButtonRole.PTT;
            case DEV_BUTTON_SOS_SOURCE: return ButtonRole.SOS;
            default: return null;
        }
    }

    private static ButtonRole actionFamilyRole(SettingId id) {
        switch (id) {
            case DEV_BUTTON_RECORD_ACTION_FAMILY: return ButtonRole.RECORD;
            case DEV_BUTTON_IMPORTANT_RECORDING_ACTION_FAMILY:
                return ButtonRole.IMPORTANT_RECORDING;
            case DEV_BUTTON_PHOTO_CAPTURE_ACTION_FAMILY: return ButtonRole.PHOTO_CAPTURE;
            case DEV_BUTTON_AUDIO_CAPTURE_ACTION_FAMILY: return ButtonRole.AUDIO_CAPTURE;
            case DEV_BUTTON_PTT_ACTION_FAMILY: return ButtonRole.PTT;
            case DEV_BUTTON_SOS_ACTION_FAMILY: return ButtonRole.SOS;
            default: return null;
        }
    }

    private static ButtonRole keyCodeRole(SettingId id) {
        switch (id) {
            case DEV_BUTTON_RECORD_KEY_CODE: return ButtonRole.RECORD;
            case DEV_BUTTON_IMPORTANT_RECORDING_KEY_CODE: return ButtonRole.IMPORTANT_RECORDING;
            case DEV_BUTTON_PHOTO_CAPTURE_KEY_CODE: return ButtonRole.PHOTO_CAPTURE;
            case DEV_BUTTON_AUDIO_CAPTURE_KEY_CODE: return ButtonRole.AUDIO_CAPTURE;
            case DEV_BUTTON_PTT_KEY_CODE: return ButtonRole.PTT;
            case DEV_BUTTON_SOS_KEY_CODE: return ButtonRole.SOS;
            default: return null;
        }
    }

    private void append(int keyCode, String action) {
        keyEventConsole.append(keyName.apply(keyCode).replace("KEYCODE_", ""), action);
    }
}
