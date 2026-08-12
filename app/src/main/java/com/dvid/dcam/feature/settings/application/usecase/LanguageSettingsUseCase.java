package com.dvid.dcam.feature.settings.application.usecase;

import com.dvid.dcam.feature.settings.application.port.LanguagePreferenceStore;
import com.dvid.dcam.feature.settings.domain.AppLanguage;

public final class LanguageSettingsUseCase {
    private final LanguagePreferenceStore languagePreferences;

    public LanguageSettingsUseCase(LanguagePreferenceStore languagePreferences) {
        this.languagePreferences = languagePreferences;
    }

    public AppLanguage currentLanguage() {
        return languagePreferences.currentLanguage();
    }

    public AppLanguage[] supportedLanguages() {
        return AppLanguage.values();
    }

    public void changeLanguage(AppLanguage language) {
        languagePreferences.selectLanguage(language == null ? AppLanguage.SYSTEM : language);
    }
}
