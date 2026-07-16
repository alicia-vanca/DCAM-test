package com.dvid.dcam.feature.settings.presentation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

final class SettingItemTest {
    @Test void describedRadioPreservesOrderedLabelsAndDescriptions() {
        List<DescribedRadioOptionUiState> options = List.of(
                new DescribedRadioOptionUiState("Automatic", "Best available source"),
                new DescribedRadioOptionUiState("Satellite", "Outdoor GNSS"));

        SettingItem item = SettingItem.describedRadio(
                SettingId.GPS_POSITIONING_MODE, "Location source", options, 1);

        assertEquals(SettingItem.Type.DESCRIBED_RADIO, item.getType());
        assertEquals(1, item.getSelectedIndex());
        assertEquals("Automatic", item.getDescribedRadioOptions().get(0).getLabel());
        assertEquals("Outdoor GNSS", item.getDescribedRadioOptions().get(1).getDescription());
    }
}