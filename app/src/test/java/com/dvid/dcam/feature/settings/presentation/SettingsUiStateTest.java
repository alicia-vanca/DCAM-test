package com.dvid.dcam.feature.settings.presentation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import java.util.List;
import org.junit.jupiter.api.Test;

final class SettingsUiStateTest {
    @Test void recordResolutionUsesOnlySupportedHardwareQualities() {
        SettingsUiState state = new SettingsUiState(false, 0, false, false);
        state.setSupportedRecordResolutions(List.of("SD", "HD"));

        state.select(SettingId.RECORD_RESOLUTION, 1);

        assertEquals("HD", state.recordResolution());
        assertEquals(List.of("SD", "HD"), state.recording("Recording", "Resolution", "Segment", "min").getSections().get(0)
                .getItems().get(0).getOptions());
    }
}
