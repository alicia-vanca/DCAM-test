package com.dvid.dcam.app.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.dvid.dcam.feature.device.application.port.DeviceRepository;
import com.dvid.dcam.feature.device.application.usecase.RefreshDeviceStatusUseCase;
import com.dvid.dcam.feature.device.domain.DeviceStatus;
import com.dvid.dcam.feature.media.application.usecase.BrowseMediaUseCase;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

final class MainViewModelMediaGateTest {
    @Test void disabledMediaBrowserDoesNotCallRepositoryOrEnterFilesScreen() {
        AtomicInteger browseCalls = new AtomicInteger();
        BrowseMediaUseCase browseMedia = new BrowseMediaUseCase(path -> {
            browseCalls.incrementAndGet();
            return Collections.emptyList();
        });
        MainViewModel viewModel = new MainViewModel(
                DeviceStatus.unknown(),
                new RefreshDeviceStatusUseCase(new DeviceRepository() {
                    @Override public com.dvid.dcam.core.device.domain.DeviceInfo readInfo() {
                        return null;
                    }
                    @Override public DeviceStatus readStatus() { return DeviceStatus.unknown(); }
                }),
                browseMedia,
                () -> false);

        viewModel.show(MainScreen.FILES);
        viewModel.openMediaFolder("");

        assertEquals(MainScreen.CAMERA, viewModel.state().getValue().getScreen());
        assertEquals(0, browseCalls.get());
        viewModel.onCleared();
    }
}
