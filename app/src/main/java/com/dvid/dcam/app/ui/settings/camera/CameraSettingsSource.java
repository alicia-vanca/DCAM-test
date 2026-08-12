package com.dvid.dcam.app.ui.settings.camera;

import java.util.List;

/** Capability and selection projection consumed by settings presentation. */
@FunctionalInterface
public interface CameraSettingsSource {
    List<CameraSettingsCamera> cameras();
}