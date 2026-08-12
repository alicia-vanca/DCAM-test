package com.dvid.dcam.platform.cloud;

import com.dvid.dcam.feature.cloud.application.port.DeviceIdentityRepository;
import com.dvid.dcam.feature.cloud.domain.DeviceCloudIdentity;
import com.dvid.dcam.feature.cloud.domain.ProvisioningState;
import com.dvid.dcam.core.device.domain.DeviceInfo;
import com.dvid.dcam.platform.database.dao.CloudStateDao;
import com.dvid.dcam.platform.database.entities.DeviceIdentityEntity;

/** Room-backed local identity store for provisioning and recovery state. */
public final class RoomDeviceIdentityRepositoryImpl implements DeviceIdentityRepository {
    private static final int SINGLETON_ID = 1;

    private final CloudStateDao dao;

    public RoomDeviceIdentityRepositoryImpl(CloudStateDao dao) {
        this.dao = dao;
    }

    @Override public DeviceCloudIdentity restoreOrCreate(DeviceInfo deviceInfo) {
        DeviceIdentityEntity existing = dao.deviceIdentity();
        if (existing == null) {
            DeviceIdentityEntity created = new DeviceIdentityEntity(
                    SINGLETON_ID, null, deviceInfo.getHardwareId(), null,
                    ProvisioningState.PROVISIONING_REQUIRED.name(), null, System.currentTimeMillis());
            dao.saveDeviceIdentity(created);
            return toDomain(created);
        }
        if (!same(existing.hardwareId, deviceInfo.getHardwareId())) {
            existing.hardwareId = deviceInfo.getHardwareId();
            existing.updatedAt = System.currentTimeMillis();
            if (existing.dcamCloudDeviceId == null || existing.dcamCloudDeviceId.isBlank()) {
                existing.provisioningState = ProvisioningState.RECOVERY_REQUIRED.name();
            }
            dao.saveDeviceIdentity(existing);
        }
        return toDomain(existing);
    }

    @Override public DeviceCloudIdentity current() {
        DeviceIdentityEntity entity = dao.deviceIdentity();
        return entity == null ? null : toDomain(entity);
    }

    private static DeviceCloudIdentity toDomain(DeviceIdentityEntity entity) {
        return new DeviceCloudIdentity(
                entity.dcamCloudDeviceId,
                entity.hardwareId,
                entity.serialNumber,
                state(entity.provisioningState),
                entity.firebaseInstallationId);
    }

    private static ProvisioningState state(String value) {
        try {
            return ProvisioningState.valueOf(value);
        } catch (Exception ignored) {
            return ProvisioningState.PROVISIONING_REQUIRED;
        }
    }

    private static boolean same(String first, String second) {
        if (first == null) return second == null;
        return first.equals(second);
    }
}
