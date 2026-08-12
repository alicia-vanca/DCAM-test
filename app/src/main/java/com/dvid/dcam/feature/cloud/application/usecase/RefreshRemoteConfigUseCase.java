package com.dvid.dcam.feature.cloud.application.usecase;

import com.dvid.dcam.feature.cloud.application.port.RemoteConfigGateway;
import com.dvid.dcam.feature.cloud.application.port.RemoteConfigStore;
import com.dvid.dcam.feature.cloud.domain.DeviceCloudIdentity;
import com.dvid.dcam.feature.cloud.domain.RemoteConfigSnapshot;

public final class RefreshRemoteConfigUseCase {
    private final RemoteConfigGateway provider;
    private final RemoteConfigStore store;

    public RefreshRemoteConfigUseCase(RemoteConfigGateway provider, RemoteConfigStore store) {
        this.provider = provider;
        this.store = store;
    }

    public RemoteConfigSnapshot execute(DeviceCloudIdentity identity) {
        if (identity == null || identity.requiresProvisioning()) return store.cached();
        RemoteConfigSnapshot snapshot = provider.fetch(identity);
        if (snapshot == null || snapshot.getStatus() == RemoteConfigSnapshot.Status.NONE) {
            return store.cached();
        }
        store.save(snapshot);
        return snapshot;
    }
}
