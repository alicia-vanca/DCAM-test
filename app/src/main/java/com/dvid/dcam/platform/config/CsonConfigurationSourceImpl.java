package com.dvid.dcam.platform.config;

import com.dvid.dcam.core.config.application.port.ConfigurationSource;
import com.dvid.dcam.core.config.domain.DcamConfig;
import com.dvid.dcam.BuildConfig;
import com.dvid.dcam.platform.storage.DcamStorage;

/** Local CSON implementation of the configuration source boundary. */
public final class CsonConfigurationSourceImpl implements ConfigurationSource {
    private final DcamStorage storage;

    public CsonConfigurationSourceImpl(DcamStorage storage) {
        this.storage = storage;
    }

    @Override public DcamConfig load(String defaultAccountUserId) throws Exception {
        return new CsonConfigStore(
                storage.configsFile(), defaultAccountUserId, BuildConfig.BODYCAM_CRYPTO_PASSWORD).load();
    }
}
