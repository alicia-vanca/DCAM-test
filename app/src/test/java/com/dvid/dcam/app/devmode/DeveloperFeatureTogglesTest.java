package com.dvid.dcam.app.devmode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dvid.dcam.core.featuregate.application.FeatureGates;
import com.dvid.dcam.core.featuregate.domain.FeatureGate;
import com.dvid.dcam.core.featuregate.application.port.FeatureGateStore;
import com.dvid.dcam.app.ui.settings.SettingId;
import com.dvid.dcam.app.ui.settings.SettingItem;
import com.dvid.dcam.app.ui.settings.SettingsSection;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class DeveloperFeatureTogglesTest {
    @Test void freshInstallDefaultsToTheMinimalMvpSurface() {
        EnumSet<FeatureGate> enabled = EnumSet.noneOf(FeatureGate.class);
        for (FeatureGate gate : FeatureGate.values()) {
            if (gate.defaultEnabled()) enabled.add(gate);
        }

        assertEquals(
                EnumSet.of(
                        FeatureGate.IMAGE_CAPTURE,
                        FeatureGate.VIDEO_CAPTURE,
                        FeatureGate.AUDIO_CAPTURE,
                        FeatureGate.VIDEO_MD5,
                        FeatureGate.MEDIA_BROWSER,
                        FeatureGate.STORAGE_SETTINGS,
                        FeatureGate.DEVICE_SETTINGS,
                        FeatureGate.GPS),
                enabled);
    }

    @Test void optedInFeatureGetsDeveloperControlAndBoundaryGuard() {
        FeatureGates gates = disabledGates();
        DeveloperFeatureToggles toggles = DeveloperFeatureToggles.createDefault(gates);

        assertTrue(toggles.setEnabled(SettingId.FEATURE_MEDIA_BROWSER, true));

        SettingItem filesToggle = toggles.developerSettings().getSections().stream()
                .flatMap(section -> section.getItems().stream())
                .filter(item -> item.getId() == SettingId.FEATURE_MEDIA_BROWSER)
                .findFirst()
                .orElseThrow();
        assertTrue(filesToggle.isChecked());

        toggles.setEnabled(SettingId.FEATURE_MEDIA_BROWSER, false);
        assertFalse(toggles.runIfEnabled(FeatureGate.MEDIA_BROWSER,
                () -> { throw new AssertionError("Disabled action ran"); }));
    }

    @Test void mediaEncryptionSettingRequiresSecurityAndEncryptionGates() {
        FeatureGates gates = disabledGates();
        DeveloperFeatureToggles toggles = DeveloperFeatureToggles.createDefault(gates);
        SettingItem encryption = SettingItem.checkbox(
                SettingId.ENCRYPT_VIDEO_FILES, "Encryption", false);

        gates.setEnabled(FeatureGate.MEDIA_ENCRYPTION, true);
        assertFalse(toggles.isSettingEnabled(com.dvid.dcam.app.ui.MainScreen.USER_SETTINGS,
                encryption));
        assertFalse(toggles.isEffectivelyEnabled(FeatureGate.MEDIA_ENCRYPTION));

        gates.setEnabled(FeatureGate.SECURITY_SETTINGS, true);
        assertTrue(toggles.isSettingEnabled(com.dvid.dcam.app.ui.MainScreen.USER_SETTINGS,
                encryption));
        assertTrue(toggles.isEffectivelyEnabled(FeatureGate.MEDIA_ENCRYPTION));
    }

    @Test void mediaEncryptionIsDisabledChildDirectlyBelowSecuritySettings() {
        FeatureGates gates = disabledGates();
        gates.setEnabled(FeatureGate.MEDIA_ENCRYPTION, true);
        DeveloperFeatureToggles toggles = DeveloperFeatureToggles.createDefault(gates);

        SettingsSection section = toggles.developerSettings().getSections().stream()
                .filter(candidate -> candidate.getItems().stream()
                        .anyMatch(item -> item.getId() == SettingId.FEATURE_SECURITY_SETTINGS))
                .findFirst()
                .orElseThrow();
        int securityIndex = java.util.stream.IntStream.range(0, section.getItems().size())
                .filter(index -> section.getItems().get(index).getId()
                        == SettingId.FEATURE_SECURITY_SETTINGS)
                .findFirst()
                .orElseThrow();
        SettingItem encryption = section.getItems().get(securityIndex + 1);

        assertEquals(SettingId.FEATURE_MEDIA_ENCRYPTION, encryption.getId());
        assertEquals(1, encryption.getIndentLevel());
        assertTrue(encryption.isChecked());
        assertFalse(encryption.isEnabled());

        gates.setEnabled(FeatureGate.SECURITY_SETTINGS, true);
        SettingItem enabledEncryption = toggles.developerSettings().getSections().stream()
                .flatMap(candidate -> candidate.getItems().stream())
                .filter(item -> item.getId() == SettingId.FEATURE_MEDIA_ENCRYPTION)
                .findFirst()
                .orElseThrow();
        assertTrue(enabledEncryption.isEnabled());
    }

    @Test void nonFeatureSettingsAreLeftForTheirOwningSettingsModel() {
        DeveloperFeatureToggles toggles =
                DeveloperFeatureToggles.createDefault(new FeatureGates(new InMemoryFeatureGateStore()));

        assertFalse(toggles.setEnabled(SettingId.LANGUAGE, true));
    }

    @Test void disabledMediaParentGreysAndDisablesChildBehavior() {
        FeatureGates gates = disabledGates();
        gates.setEnabled(FeatureGate.VIDEO_MD5, true);
        DeveloperFeatureToggles toggles = DeveloperFeatureToggles.createDefault(gates);

        SettingItem md5 = toggles.developerSettings().getSections().stream()
                .flatMap(section -> section.getItems().stream())
                .filter(item -> item.getId() == SettingId.FEATURE_VIDEO_MD5)
                .findFirst()
                .orElseThrow();

        assertTrue(md5.isChecked());
        assertFalse(md5.isEnabled());
        assertFalse(toggles.isEffectivelyEnabled(FeatureGate.VIDEO_MD5));

        toggles.setEnabled(SettingId.FEATURE_VIDEO_CAPTURE, true);
        assertTrue(toggles.isEffectivelyEnabled(FeatureGate.VIDEO_MD5));
    }

    @Test void everyOptInGateIsDeclaredOnceOnDeveloperScreen() {
        DeveloperFeatureToggles toggles =
                DeveloperFeatureToggles.createDefault(new FeatureGates(new InMemoryFeatureGateStore()));

        long toggleCount = toggles.developerSettings().getSections().stream()
                .flatMap(section -> section.getItems().stream())
                .count();

        assertEquals(FeatureGate.values().length, toggleCount);
    }

    @Test void childSettingStaysBesideItsFeatureToggle() {
        DeveloperFeatureToggles toggles =
                DeveloperFeatureToggles.createDefault(new FeatureGates(new InMemoryFeatureGateStore()));
        SettingItem provider = SettingItem.radio(
                SettingId.GPS_POSITIONING_MODE, "Location source", java.util.List.of("Automatic"), 0)
                .withIndentLevel(1);

        SettingsSection section = toggles.developerSettings(SettingId.FEATURE_GPS, provider)
                .getSections().stream()
                .filter(candidate -> candidate.getItems().stream()
                        .anyMatch(item -> item.getId() == SettingId.FEATURE_GPS))
                .findFirst()
                .orElseThrow();
        int locationIndex = java.util.stream.IntStream.range(0, section.getItems().size())
                .filter(index -> section.getItems().get(index).getId() == SettingId.FEATURE_GPS)
                .findFirst()
                .orElseThrow();

        assertEquals(SettingId.GPS_POSITIONING_MODE,
                section.getItems().get(locationIndex + 1).getId());
        assertEquals(1, section.getItems().get(locationIndex + 1).getIndentLevel());
    }

    private static FeatureGates disabledGates() {
        FeatureGates gates = new FeatureGates(new InMemoryFeatureGateStore());
        for (FeatureGate gate : FeatureGate.values()) gates.setEnabled(gate, false);
        return gates;
    }

    private static final class InMemoryFeatureGateStore implements FeatureGateStore {
        private final Map<FeatureGate, Boolean> enabled = new EnumMap<>(FeatureGate.class);

        @Override public boolean isEnabled(FeatureGate feature) {
            return enabled.getOrDefault(feature, feature.defaultEnabled());
        }

        @Override public void setEnabled(FeatureGate feature, boolean enabled) {
            this.enabled.put(feature, enabled);
        }
    }
}

