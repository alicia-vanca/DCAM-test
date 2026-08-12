package com.dvid.dcam.feature.cloud.application.usecase;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.dvid.dcam.feature.cloud.application.port.RemoteConfigGateway;
import com.dvid.dcam.feature.cloud.application.port.RemoteConfigStore;
import com.dvid.dcam.feature.cloud.domain.DeviceCloudIdentity;
import com.dvid.dcam.feature.cloud.domain.ProvisioningState;
import com.dvid.dcam.feature.cloud.domain.RemoteConfigSnapshot;
import org.junit.jupiter.api.Test;

final class RefreshRemoteConfigUseCaseTest {
    @Test void provisioningRequiredDevicesUseCachedConfigOnly() {
        FakeProvider provider = new FakeProvider();
        FakeStore store = new FakeStore();
        store.saved = new RemoteConfigSnapshot("cached", "{}", RemoteConfigSnapshot.Status.ACCEPTED, null);
        RefreshRemoteConfigUseCase useCase = new RefreshRemoteConfigUseCase(provider, store);

        RemoteConfigSnapshot result = useCase.execute(new DeviceCloudIdentity(
                null, "hash", "serial", ProvisioningState.PROVISIONING_REQUIRED, null));

        assertEquals("cached", result.getRevision());
        assertEquals(0, provider.fetches);
    }

    @Test void provisionedDevicesPersistFetchedAcceptedConfig() {
        FakeProvider provider = new FakeProvider();
        FakeStore store = new FakeStore();
        provider.next = new RemoteConfigSnapshot(
                "remote-1", "{\"safe\":true}", RemoteConfigSnapshot.Status.ACCEPTED, null);
        RefreshRemoteConfigUseCase useCase = new RefreshRemoteConfigUseCase(provider, store);

        RemoteConfigSnapshot result = useCase.execute(new DeviceCloudIdentity(
                "cloud-id", "hash", "serial", ProvisioningState.PROVISIONED, null));

        assertEquals("remote-1", result.getRevision());
        assertEquals("remote-1", store.saved.getRevision());
    }

    private static final class FakeProvider implements RemoteConfigGateway {
        int fetches;
        RemoteConfigSnapshot next = RemoteConfigSnapshot.none();

        @Override public RemoteConfigSnapshot fetch(DeviceCloudIdentity identity) {
            fetches++;
            return next;
        }
    }

    private static final class FakeStore implements RemoteConfigStore {
        RemoteConfigSnapshot saved = RemoteConfigSnapshot.none();

        @Override public RemoteConfigSnapshot cached() { return saved; }
        @Override public void save(RemoteConfigSnapshot snapshot) { saved = snapshot; }
    }
}
