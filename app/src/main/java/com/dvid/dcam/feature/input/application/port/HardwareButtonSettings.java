package com.dvid.dcam.feature.input.application.port;

import com.dvid.dcam.core.input.domain.ButtonRole;
import com.dvid.dcam.core.input.domain.HardwareButtonLayout;
import java.util.List;

public interface HardwareButtonSettings {
    List<String> keyCodeLabels();
    List<String> typeLabels();
    List<String> sourceLabels();
    List<String> actionFamilyLabels();
    int selectedKeyCodeIndex(ButtonRole role);
    int selectedTypeIndex(ButtonRole role);
    int selectedSourceIndex(ButtonRole role);
    int selectedActionFamilyIndex(ButtonRole role);
    void initialize(HardwareButtonLayout defaults);
    boolean resetToDefaults(HardwareButtonLayout defaults);
    void selectKeyCode(ButtonRole role, int selectedIndex);
    void selectType(ButtonRole role, int selectedIndex);
    void selectSource(ButtonRole role, int selectedIndex);
    void selectActionFamily(ButtonRole role, int selectedIndex);
    HardwareButtonLayout loadLayout();
}
