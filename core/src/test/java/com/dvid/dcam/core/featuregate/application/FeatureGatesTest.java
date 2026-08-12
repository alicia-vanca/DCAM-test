package com.dvid.dcam.core.featuregate.application;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dvid.dcam.core.featuregate.application.port.FeatureGateStore;
import com.dvid.dcam.core.featuregate.domain.FeatureGate;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

final class FeatureGatesTest {
    @Test void delegatesReadsAndWritesToStore() {
        InMemoryFeatureGateStore store = new InMemoryFeatureGateStore();
        FeatureGates useCase = new FeatureGates(store);

        assertTrue(useCase.isEnabled(FeatureGate.AUDIO_CAPTURE));

        useCase.setEnabled(FeatureGate.AUDIO_CAPTURE, false);

        assertFalse(useCase.isEnabled(FeatureGate.AUDIO_CAPTURE));
    }

    @Test void disabledParentBlocksEnabledChild() {
        InMemoryFeatureGateStore store = new InMemoryFeatureGateStore();
        FeatureGates gates = new FeatureGates(store);
        store.setEnabled(FeatureGate.VIDEO_CAPTURE, false);
        store.setEnabled(FeatureGate.VIDEO_MD5, true);

        assertFalse(gates.isEffectivelyEnabled(FeatureGate.VIDEO_MD5));
    }

    @Test void runIfEnabledDoesNotRunBlockedAction() {
        InMemoryFeatureGateStore store = new InMemoryFeatureGateStore();
        FeatureGates gates = new FeatureGates(store);
        AtomicBoolean ran = new AtomicBoolean();
        store.setEnabled(FeatureGate.VIDEO_CAPTURE, false);

        assertFalse(gates.runIfEnabled(FeatureGate.VIDEO_MD5, () -> ran.set(true)));
        assertFalse(ran.get());
    }

    private static final class InMemoryFeatureGateStore implements FeatureGateStore {
        private final Map<FeatureGate, Boolean> values = new EnumMap<>(FeatureGate.class);

        @Override public boolean isEnabled(FeatureGate feature) {
            return values.getOrDefault(feature, feature.defaultEnabled());
        }

        @Override public void setEnabled(FeatureGate feature, boolean enabled) {
            values.put(feature, enabled);
        }
    }
}
