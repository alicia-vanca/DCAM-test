package com.dvid.dcam.app.ui.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.List;
import org.junit.jupiter.api.Test;

final class SettingItemTest {
    @Test void describedRadioPreservesOrderedLabelsAndDescriptions() {
        List<DescribedRadioOptionUiState> options = List.of(
                new DescribedRadioOptionUiState("Automatic", "Best available source"),
                new DescribedRadioOptionUiState("Satellite", "Outdoor GNSS", false));

        SettingItem item = SettingItem.describedRadio(
                SettingId.GPS_POSITIONING_MODE, "Location source", options, 1);

        assertEquals(SettingItem.Type.DESCRIBED_RADIO, item.getType());
        assertEquals(1, item.getSelectedIndex());
        assertEquals("Automatic", item.getDescribedRadioOptions().get(0).getLabel());
        assertEquals("Outdoor GNSS", item.getDescribedRadioOptions().get(1).getDescription());
        assertFalse(item.getDescribedRadioOptions().get(1).isEnabled());
    }

    @Test void describedRadioOptionOwnsItsNestedChoice() {
        SettingItem nestedChoice = SettingItem.choice(
                SettingId.DEV_BUTTON_RECORD_ACTION_FAMILY,
                "Action family",
                List.of("ACTION_CAMERA", "ACTION_VIDEO"),
                1);

        DescribedRadioOptionUiState option = new DescribedRadioOptionUiState(
                "Firmware broadcast",
                "Works on compatible vendor firmware.")
                .withNestedChoice(nestedChoice);

        assertSame(nestedChoice, option.getNestedChoice());
    }
}