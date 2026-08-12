package com.dvid.dcam.app.ui;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import com.dvid.dcam.feature.device.application.usecase.RefreshDeviceStatusUseCase;
import com.dvid.dcam.feature.auth.application.usecase.AuthenticateOperatorUseCase;
import com.dvid.dcam.feature.auth.application.usecase.ManageOperatorUsersUseCase;
import com.dvid.dcam.feature.auth.application.usecase.OperatorSessionUseCase;
import com.dvid.dcam.feature.device.domain.DeviceStatus;
import com.dvid.dcam.feature.media.application.usecase.BrowseMediaUseCase;
import java.util.function.BooleanSupplier;

public final class MainViewModelFactory implements ViewModelProvider.Factory {
    private final DeviceStatus deviceStatus;
    private final RefreshDeviceStatusUseCase refreshDeviceStatus;
    private final BrowseMediaUseCase browseMedia;
    private final AuthenticateOperatorUseCase authenticateOperator;
    private final OperatorSessionUseCase operatorSession;
    private final ManageOperatorUsersUseCase manageUsers;
    private final BooleanSupplier authenticationEnabled;
    private final BooleanSupplier mediaBrowserEnabled;

    public MainViewModelFactory(
            DeviceStatus deviceStatus,
            RefreshDeviceStatusUseCase refreshDeviceStatus,
            BrowseMediaUseCase browseMedia,
            AuthenticateOperatorUseCase authenticateOperator,
            OperatorSessionUseCase operatorSession,
            ManageOperatorUsersUseCase manageUsers,
            BooleanSupplier authenticationEnabled,
            BooleanSupplier mediaBrowserEnabled) {
        this.deviceStatus = deviceStatus;
        this.refreshDeviceStatus = refreshDeviceStatus;
        this.browseMedia = browseMedia;
        this.authenticateOperator = authenticateOperator;
        this.operatorSession = operatorSession;
        this.manageUsers = manageUsers;
        this.authenticationEnabled = authenticationEnabled;
        this.mediaBrowserEnabled = mediaBrowserEnabled;
    }

    @NonNull
    @Override public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        if (!modelClass.isAssignableFrom(MainViewModel.class)) {
            throw new IllegalArgumentException("Unsupported ViewModel " + modelClass.getName());
        }
        return modelClass.cast(new MainViewModel(
                deviceStatus, refreshDeviceStatus, browseMedia,
                authenticateOperator, operatorSession, manageUsers,
                authenticationEnabled, mediaBrowserEnabled));
    }
}
