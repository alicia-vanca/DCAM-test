package com.dvid.dcam.app.ui.settings;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.KeyEvent;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.dvid.dcam.core.input.domain.ButtonRole;
import com.dvid.dcam.core.input.domain.HardwareButtonLayout;
import com.dvid.dcam.feature.input.application.usecase.ConfigureHardwareButtonsUseCase;
import java.util.List;
import java.util.function.Consumer;

/** Developer-only presentation for editing physical button bindings. */
public final class DeveloperButtonBindingsScreen {
    private final Context context;
    private final ConfigureHardwareButtonsUseCase buttons;
    private final SettingsControlRenderer renderer;
    private final Consumer<HardwareButtonLayout> onLayoutApplied;
    private final Consumer<String> showNotice;
    private final KeyEventConsole keyEventConsole = new KeyEventConsole(12);
    private TextView consoleView;

    public DeveloperButtonBindingsScreen(
            Context context,
            ConfigureHardwareButtonsUseCase buttons,
            Consumer<HardwareButtonLayout> onLayoutApplied,
            Consumer<String> showNotice) {
        this.context = context;
        this.buttons = buttons;
        this.renderer = new SettingsControlRenderer(context);
        this.onLayoutApplied = onLayoutApplied;
        this.showNotice = showNotice;
    }

    public void render(LinearLayout parent) {
        parent.removeAllViews();
        renderer.render(parent, model(), (id, selectedIndex) -> {
            select(id, selectedIndex);
            render(parent);
        }, (id, value) -> {}, (id, checked) -> {}, id -> {});
        Button reset = new Button(context);
        reset.setText("Reset device defaults");
        reset.setOnClickListener(view -> confirmReset(parent));
        parent.addView(reset);
        renderer.section(parent, "Key event console");
        consoleView = new TextView(context);
        consoleView.setBackgroundResource(com.dvid.dcam.R.drawable.bg_setting_card);
        consoleView.setMinHeight(dp(120));
        consoleView.setPadding(dp(14), dp(10), dp(14), dp(10));
        String consoleText = keyEventConsole.text();
        consoleView.setText(consoleText.isEmpty() ? "Waiting for key events" : consoleText);
        consoleView.setTextColor(Color.WHITE);
        consoleView.setTextSize(14);
        consoleView.setTypeface(Typeface.MONOSPACE);
        parent.addView(consoleView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
    }

    public boolean onKeyDown(int keyCode) {
        appendKeyEvent(keyCode, "DOWN");
        return keyCode != KeyEvent.KEYCODE_BACK;
    }

    public boolean onKeyUp(int keyCode) {
        appendKeyEvent(keyCode, "UP");
        return keyCode != KeyEvent.KEYCODE_BACK;
    }

    private SettingsScreenModel model() {
        List<String> keyCodes = buttons.keyCodeLabels();
        List<String> types = buttons.typeLabels();
        List<String> actionFamilies = buttons.actionFamilyLabels();
        List<String> sourceLabels = buttons.sourceLabels();
        return new SettingsScreenModel(List.of(
                section(
                        "Record", ButtonRole.RECORD,
                        SettingId.DEV_BUTTON_RECORD_TYPE,
                        SettingId.DEV_BUTTON_RECORD_SOURCE,
                        SettingId.DEV_BUTTON_RECORD_ACTION_FAMILY,
                        SettingId.DEV_BUTTON_RECORD_KEY_CODE,
                        types, sourceLabels, actionFamilies, keyCodes),
                section(
                        "Important recording", ButtonRole.IMPORTANT_RECORDING,
                        SettingId.DEV_BUTTON_IMPORTANT_RECORDING_TYPE,
                        SettingId.DEV_BUTTON_IMPORTANT_RECORDING_SOURCE,
                        SettingId.DEV_BUTTON_IMPORTANT_RECORDING_ACTION_FAMILY,
                        SettingId.DEV_BUTTON_IMPORTANT_RECORDING_KEY_CODE,
                        types, sourceLabels, actionFamilies, keyCodes),
                section(
                        "Photo capture", ButtonRole.PHOTO_CAPTURE,
                        SettingId.DEV_BUTTON_PHOTO_CAPTURE_TYPE,
                        SettingId.DEV_BUTTON_PHOTO_CAPTURE_SOURCE,
                        SettingId.DEV_BUTTON_PHOTO_CAPTURE_ACTION_FAMILY,
                        SettingId.DEV_BUTTON_PHOTO_CAPTURE_KEY_CODE,
                        types, sourceLabels, actionFamilies, keyCodes),
                section(
                        "Audio capture", ButtonRole.AUDIO_CAPTURE,
                        SettingId.DEV_BUTTON_AUDIO_CAPTURE_TYPE,
                        SettingId.DEV_BUTTON_AUDIO_CAPTURE_SOURCE,
                        SettingId.DEV_BUTTON_AUDIO_CAPTURE_ACTION_FAMILY,
                        SettingId.DEV_BUTTON_AUDIO_CAPTURE_KEY_CODE,
                        types, sourceLabels, actionFamilies, keyCodes),
                section(
                        "PTT", ButtonRole.PTT,
                        SettingId.DEV_BUTTON_PTT_TYPE,
                        SettingId.DEV_BUTTON_PTT_SOURCE,
                        SettingId.DEV_BUTTON_PTT_ACTION_FAMILY,
                        SettingId.DEV_BUTTON_PTT_KEY_CODE,
                        types, sourceLabels, actionFamilies, keyCodes),
                section(
                        "SOS", ButtonRole.SOS,
                        SettingId.DEV_BUTTON_SOS_TYPE,
                        SettingId.DEV_BUTTON_SOS_SOURCE,
                        SettingId.DEV_BUTTON_SOS_ACTION_FAMILY,
                        SettingId.DEV_BUTTON_SOS_KEY_CODE,
                        types, sourceLabels, actionFamilies, keyCodes)));
    }

    private SettingsSection section(
            String title,
            ButtonRole role,
            SettingId typeId,
            SettingId sourceId,
            SettingId actionFamilyId,
            SettingId keyCodeId,
            List<String> types,
            List<String> sourceLabels,
            List<String> actionFamilies,
            List<String> keyCodes) {
        List<DescribedRadioOptionUiState> sources = List.of(
                new DescribedRadioOptionUiState(
                        sourceLabels.get(0),
                        "Works on compatible vendor firmware.")
                        .withNestedChoice(SettingItem.choice(
                                        actionFamilyId,
                                        "Action family",
                                        actionFamilies,
                                        buttons.selectedActionFamilyIndex(role))
                                .withIndentLevel(1)),
                new DescribedRadioOptionUiState(
                        sourceLabels.get(1),
                        "Unavailable while screen is off.")
                        .withNestedChoice(SettingItem.choice(
                                        keyCodeId,
                                        "Keycode",
                                        keyCodes,
                                        buttons.selectedKeyCodeIndex(role))
                                .withIndentLevel(1)));
        return new SettingsSection(title, List.of(
                SettingItem.choice(
                        typeId, "Input type", types, buttons.selectedTypeIndex(role)),
                SettingItem.describedRadio(
                        sourceId, "Input source", sources, buttons.selectedSourceIndex(role))));
    }

    private void select(SettingId id, int selectedIndex) {
        ButtonRole role = typeRole(id);
        HardwareButtonLayout layout;
        if (role != null) {
            layout = buttons.selectType(role, selectedIndex);
        } else if ((role = sourceRole(id)) != null) {
            layout = buttons.selectSource(role, selectedIndex);
        } else if ((role = actionFamilyRole(id)) != null) {
            layout = buttons.selectActionFamily(role, selectedIndex);
        } else if ((role = keyCodeRole(id)) != null) {
            layout = buttons.selectKeyCode(role, selectedIndex);
        } else {
            throw new IllegalArgumentException("Unsupported developer button setting " + id);
        }
        onLayoutApplied.accept(layout);
        showNotice.accept("Button binding applied");
    }

    private void confirmReset(LinearLayout parent) {
        if (!buttons.hasDefaults()) {
            new AlertDialog.Builder(context)
                    .setTitle("No device defaults")
                    .setMessage("No default button preset exists for this device.")
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
            return;
        }
        new AlertDialog.Builder(context)
                .setTitle("Reset device defaults?")
                .setMessage("Replace current button bindings with device preset?")
                .setNegativeButton(android.R.string.no, null)
                .setPositiveButton(android.R.string.yes, (dialog, which) -> {
                    onLayoutApplied.accept(buttons.resetDefaults());
                    render(parent);
                    showNotice.accept("Device button defaults restored");
                })
                .show();
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

    private void appendKeyEvent(int keyCode, String action) {
        String keyName = KeyEvent.keyCodeToString(keyCode).replace("KEYCODE_", "");
        appendKeyEvent(keyName, action);
    }

    private void appendKeyEvent(String keyName, String action) {
        String text = keyEventConsole.append(keyName, action);
        if (consoleView != null) consoleView.setText(text);
    }

    private int dp(int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
