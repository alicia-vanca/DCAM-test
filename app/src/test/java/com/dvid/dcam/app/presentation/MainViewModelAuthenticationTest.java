package com.dvid.dcam.app.presentation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import androidx.arch.core.executor.ArchTaskExecutor;
import androidx.arch.core.executor.TaskExecutor;
import com.dvid.dcam.app.navigation.MainScreen;
import com.dvid.dcam.core.config.domain.DcamConfig;
import com.dvid.dcam.feature.auth.application.usecase.AuthenticateOperatorUseCase;
import com.dvid.dcam.feature.auth.application.usecase.ManageOperatorUsersUseCase;
import com.dvid.dcam.feature.auth.application.usecase.ManageOperatorUsersUseCaseImpl;
import com.dvid.dcam.feature.auth.application.usecase.OperatorSessionUseCase;
import com.dvid.dcam.feature.auth.domain.LoginCredentials;
import com.dvid.dcam.feature.auth.domain.LoginResult;
import com.dvid.dcam.feature.auth.domain.OperatorAccount;
import com.dvid.dcam.feature.auth.domain.OperatorSession;
import com.dvid.dcam.feature.auth.domain.UserProvisioningRequest;
import com.dvid.dcam.feature.auth.domain.UserProvisioningResult;
import com.dvid.dcam.feature.device.domain.DeviceStatus;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

final class MainViewModelAuthenticationTest {
    @BeforeEach void runLiveDataSynchronously() {
        ArchTaskExecutor.getInstance().setDelegate(new TaskExecutor() {
            @Override public void executeOnDiskIO(Runnable runnable) { runnable.run(); }
            @Override public void postToMainThread(Runnable runnable) { runnable.run(); }
            @Override public boolean isMainThread() { return true; }
        });
    }

    @AfterEach void restoreLiveDataExecutor() {
        ArchTaskExecutor.getInstance().setDelegate(null);
    }

    @Test void disabledAuthenticationStartsWithDefaultUserAndNoLoginScreen() throws Exception {
        FakeAuth auth = new FakeAuth();
        MainViewModel viewModel = viewModel(auth, () -> false);

        MainUiState state = awaitReady(viewModel);

        assertEquals(MainScreen.CAMERA, state.getScreen());
        assertNotNull(state.getOperatorSession());
        assertEquals(ManageOperatorUsersUseCaseImpl.DEFAULT_USER_ID,
                state.getOperatorSession().getUserId());
        viewModel.onCleared();
    }

    @Test void logoutWhileAuthenticationDisabledReturnsToDefaultUser() throws Exception {
        FakeAuth auth = new FakeAuth();
        MainViewModel viewModel = viewModel(auth, () -> false);
        awaitReady(viewModel);

        viewModel.logout();
        MainUiState state = awaitReady(viewModel);

        assertEquals(MainScreen.CAMERA, state.getScreen());
        assertNotNull(state.getOperatorSession());
        assertEquals(1, auth.logoutCalls);
        viewModel.onCleared();
    }

    @Test void disablingAuthenticationReplacesLoginScreenWithDefaultUser() throws Exception {
        FakeAuth auth = new FakeAuth();
        auth.current = new OperatorSession("operator-session", "123456", "123456",
                "Operator", "boot", 1L);
        AtomicBoolean authenticationEnabled = new AtomicBoolean(true);
        MainViewModel viewModel = viewModel(auth, authenticationEnabled::get);
        awaitReady(viewModel);
        viewModel.show(MainScreen.DEVELOPER_SETTINGS);

        authenticationEnabled.set(false);
        viewModel.onAuthenticationSettingChanged();
        MainUiState state = awaitReady(viewModel);

        assertEquals(MainScreen.DEVELOPER_SETTINGS, state.getScreen());
        assertEquals(ManageOperatorUsersUseCaseImpl.DEFAULT_USER_ID,
                state.getOperatorSession().getUserId());
        viewModel.onCleared();
    }

    @Test void enablingAuthenticationEndsDefaultSessionAndShowsLogin() throws Exception {
        java.util.concurrent.atomic.AtomicBoolean authenticationEnabled =
                new java.util.concurrent.atomic.AtomicBoolean(false);
        FakeAuth auth = new FakeAuth();
        MainViewModel viewModel = viewModel(auth, authenticationEnabled::get);
        awaitReady(viewModel);

        authenticationEnabled.set(true);
        viewModel.onAuthenticationSettingChanged();
        MainUiState state = awaitReady(viewModel);

        assertEquals(MainScreen.LOGIN, state.getScreen());
        org.junit.jupiter.api.Assertions.assertNull(state.getOperatorSession());
        assertEquals(1, auth.logoutCalls);
        viewModel.onCleared();
    }

    private static MainViewModel viewModel(FakeAuth auth, BooleanSupplier authenticationEnabled) {
        return new MainViewModel(
                new DcamConfig(), DeviceStatus.unknown(), DeviceStatus::unknown,
                relativePath -> Collections.emptyList(), auth, auth, auth, authenticationEnabled);
    }

    private static MainUiState awaitReady(MainViewModel viewModel) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (System.nanoTime() < deadline) {
            MainUiState state = viewModel.state().getValue();
            if (state != null && !state.isAuthenticationBusy()) return state;
            Thread.sleep(10);
        }
        throw new AssertionError("Authentication did not finish");
    }

    private static final class FakeAuth implements AuthenticateOperatorUseCase,
            OperatorSessionUseCase, ManageOperatorUsersUseCase {
        private int logoutCalls;
        private OperatorSession current;

        @Override public LoginResult execute(LoginCredentials credentials) {
            assertEquals(ManageOperatorUsersUseCaseImpl.DEFAULT_USER_ID, credentials.getIdentifier());
            assertEquals(ManageOperatorUsersUseCaseImpl.DEFAULT_PASSWORD, credentials.getPasswordText());
            current = new OperatorSession("session", credentials.getIdentifier(), "B01OPR",
                    "Default Operator", "boot", 1L);
            return LoginResult.success(current);
        }

        @Override public void ensureDefaultUser() {}
        @Override public UserProvisioningResult upsert(UserProvisioningRequest request) {
            return UserProvisioningResult.success();
        }
        @Override public List<OperatorAccount> users() { return Collections.emptyList(); }
        @Override public OperatorSession restore() { return current; }
        @Override public OperatorSession current() { return current; }
        @Override public boolean hasActiveSession() { return current != null; }
        @Override public void logout() { logoutCalls++; current = null; }
    }
}
