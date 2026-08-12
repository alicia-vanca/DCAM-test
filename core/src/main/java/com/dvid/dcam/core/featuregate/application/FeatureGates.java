package com.dvid.dcam.core.featuregate.application;

import com.dvid.dcam.core.featuregate.application.port.FeatureGateStore;
import com.dvid.dcam.core.featuregate.domain.FeatureGate;
import java.util.Objects;

/** Runtime policy and persisted state for developer-controlled feature gates. */
public final class FeatureGates {
    private final FeatureGateStore store;

    public FeatureGates(FeatureGateStore store) {
        this.store = Objects.requireNonNull(store);
    }

    public boolean isEnabled(FeatureGate feature) {
        return store.isEnabled(Objects.requireNonNull(feature));
    }

    public boolean isEffectivelyEnabled(FeatureGate feature) {
        FeatureGate current = Objects.requireNonNull(feature);
        return isEnabled(current)
                && (current.parent() == null || isEffectivelyEnabled(current.parent()));
    }

    public void setEnabled(FeatureGate feature, boolean enabled) {
        store.setEnabled(Objects.requireNonNull(feature), enabled);
    }

    public boolean runIfEnabled(FeatureGate feature, Runnable action) {
        Objects.requireNonNull(action);
        if (!isEffectivelyEnabled(feature)) return false;
        action.run();
        return true;
    }
}
