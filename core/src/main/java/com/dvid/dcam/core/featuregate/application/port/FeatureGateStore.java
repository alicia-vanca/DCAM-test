package com.dvid.dcam.core.featuregate.application.port;

import com.dvid.dcam.core.featuregate.domain.FeatureGate;

/** Persistence boundary for project-phase feature gates. */
public interface FeatureGateStore {
    boolean isEnabled(FeatureGate feature);
    void setEnabled(FeatureGate feature, boolean enabled);
}
