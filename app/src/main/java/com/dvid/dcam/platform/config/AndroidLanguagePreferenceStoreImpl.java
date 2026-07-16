package com.dvid.dcam.platform.config;

import com.dvid.dcam.feature.settings.application.port.LanguagePreferenceStore;
import com.dvid.dcam.feature.settings.domain.AppLanguage;
import android.app.LocaleManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Build;
import android.os.LocaleList;
import java.util.Locale;

/** Android SharedPreferences/Locale implementation of language preference. */
public final class AndroidLanguagePreferenceStoreImpl implements LanguagePreferenceStore {
    private static final String PREFS_NAME = "dcam_language";
    private static final String KEY_LANGUAGE_TAG = "language_tag";

    private final Context context;

    public AndroidLanguagePreferenceStoreImpl(Context context) {
        this.context = context.getApplicationContext();
    }

    @Override public AppLanguage currentLanguage() {
        return readLanguage(context);
    }

    @Override public void selectLanguage(AppLanguage language) {
        AppLanguage next = language == null ? AppLanguage.SYSTEM : language;
        prefs(context).edit().putString(KEY_LANGUAGE_TAG, next.getLanguageTag()).apply();
        applySystemAppLocale(context, next);
    }

    public static Context localizedContext(Context base) {
        AppLanguage language = readLanguage(base);
        if (language.isSystemDefault()) return base;
        Locale locale = language.toLocale();
        Locale.setDefault(locale);
        Configuration configuration = new Configuration(base.getResources().getConfiguration());
        configuration.setLocale(locale);
        configuration.setLocales(new LocaleList(locale));
        return base.createConfigurationContext(configuration);
    }

    private static AppLanguage readLanguage(Context context) {
        return AppLanguage.fromLanguageTag(prefs(context).getString(KEY_LANGUAGE_TAG, ""));
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    private static void applySystemAppLocale(Context context, AppLanguage language) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return;
        LocaleManager localeManager = context.getSystemService(LocaleManager.class);
        if (localeManager == null) return;
        LocaleList locales = language.isSystemDefault()
                ? LocaleList.getEmptyLocaleList()
                : LocaleList.forLanguageTags(language.getLanguageTag());
        localeManager.setApplicationLocales(locales);
    }
}
