package com.dvid.dcam.app.ui.settings.camera;

import com.dvid.dcam.app.ui.settings.DescribedRadioOptionUiState;
import com.dvid.dcam.app.ui.settings.SettingId;
import com.dvid.dcam.app.ui.settings.SettingItem;
import com.dvid.dcam.app.ui.settings.SettingsScreenModel;
import com.dvid.dcam.app.ui.settings.SettingsSection;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Stable developer presentation extension points; no pipeline backend wiring. */
public final class CameraDeveloperSettingsPresentation {
    private static final String CAMERA_PIPELINE_STABLE_ID_PREFIX =
            "developer:pipeline-mode-camera-";

    private CameraDeveloperSettingsPresentation() {}

    public record CameraPipelineSelectionUiState(String cameraId, String pipelineLabel,
            String description, List<DescribedRadioOptionUiState> options,
            int selectedIndex, boolean enabled) {
        public CameraPipelineSelectionUiState {
            if (cameraId == null || cameraId.isBlank()
                    || pipelineLabel == null || pipelineLabel.isBlank()
                    || description == null || description.isBlank()
                    || options == null || options.isEmpty()
                    || selectedIndex < 0 || selectedIndex >= options.size()) {
                throw new IllegalArgumentException("invalid camera pipeline selection UI state");
            }
            options = List.copyOf(options);
        }

        public CameraPipelineSelectionUiState(String cameraId, String pipelineLabel,
                String description) {
            this(cameraId, pipelineLabel, description,
                    List.of(new DescribedRadioOptionUiState(pipelineLabel, description)), 0, false);
        }

        public static String stableId(String cameraId) {
            if (cameraId == null || cameraId.isBlank()) {
                throw new IllegalArgumentException("cameraId is required");
            }
            return CAMERA_PIPELINE_STABLE_ID_PREFIX + cameraId;
        }

        public static Optional<String> cameraId(String stableId) {
            if (stableId == null || !stableId.startsWith(CAMERA_PIPELINE_STABLE_ID_PREFIX)) {
                return Optional.empty();
            }
            String cameraId = stableId.substring(CAMERA_PIPELINE_STABLE_ID_PREFIX.length());
            return cameraId.isBlank() ? Optional.empty() : Optional.of(cameraId);
        }
    }

    public static SettingsScreenModel screen(String sectionTitle,
            List<CameraPipelineSelectionUiState> selections,
            String recheckActionLabel, boolean recheckBlocked) {
        Objects.requireNonNull(selections, "selections");
        SettingItem recheck = SettingItem.action(SettingId.RECHECK_CAMERA_CAPABILITIES,
                "developer:recheck-camera-capabilities", recheckActionLabel)
                .withEnabled(!recheckBlocked);
        List<SettingItem> items = new ArrayList<>();
        for (CameraPipelineSelectionUiState selection : selections) {
            items.add(SettingItem.describedRadio(null,
                    CameraPipelineSelectionUiState.stableId(selection.cameraId()),
                    "Camera " + selection.cameraId() + " pipeline",
                    selection.options(), selection.selectedIndex())
                    .withEnabled(selection.enabled()));
        }
        items.add(recheck);
        return new SettingsScreenModel(List.of(new SettingsSection(sectionTitle, items)));
    }


    public static SettingsScreenModel screen(String sectionTitle, String pipelineModeLabel,
            List<DescribedRadioOptionUiState> pipelineModes, int selectedMode,
            boolean pipelineModeEnabled, String recheckActionLabel) {
        return screen(sectionTitle, pipelineModeLabel, pipelineModes, selectedMode,
                pipelineModeEnabled, List.of(), recheckActionLabel, false);
    }

    public static SettingsScreenModel screen(String sectionTitle, String pipelineModeLabel,
            List<DescribedRadioOptionUiState> pipelineModes, int selectedMode,
            boolean pipelineModeEnabled, String recheckActionLabel,
            boolean recheckBlocked) {
        return screen(sectionTitle, pipelineModeLabel, pipelineModes, selectedMode,
                pipelineModeEnabled, List.of(), recheckActionLabel, recheckBlocked);
    }

    public static SettingsScreenModel screen(String sectionTitle, String pipelineModeLabel,
            List<DescribedRadioOptionUiState> pipelineModes, int selectedMode,
            boolean pipelineModeEnabled, List<CameraPipelineSelectionUiState> selections,
            String recheckActionLabel, boolean recheckBlocked) {
        Objects.requireNonNull(pipelineModes, "pipelineModes");
        Objects.requireNonNull(selections, "selections");
        SettingItem recheck = SettingItem.action(SettingId.RECHECK_CAMERA_CAPABILITIES,
                "developer:recheck-camera-capabilities", recheckActionLabel)
                .withEnabled(!recheckBlocked);
        List<SettingItem> items = new ArrayList<>();
        if (selections.isEmpty()) {
            items.add(SettingItem.describedRadio(SettingId.DEV_PIPELINE_MODE,
                    "developer:pipeline-mode", pipelineModeLabel, pipelineModes, selectedMode)
                    .withEnabled(pipelineModeEnabled));
        } else {
            for (CameraPipelineSelectionUiState selection : selections) {
                items.add(SettingItem.describedRadio(null,
                        CameraPipelineSelectionUiState.stableId(selection.cameraId()),
                        "Camera " + selection.cameraId() + " pipeline",
                        selection.options(), selection.selectedIndex())
                        .withEnabled(selection.enabled()));
            }
        }
        items.add(recheck);
        return new SettingsScreenModel(List.of(new SettingsSection(sectionTitle, items)));
    }
}