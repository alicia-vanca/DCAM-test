package com.dvid.dcam.app.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import androidx.arch.core.executor.ArchTaskExecutor;
import androidx.arch.core.executor.TaskExecutor;
import com.dvid.dcam.feature.auth.application.port.OperatorAuthRepository;
import com.dvid.dcam.feature.auth.application.repository.OperatorSessionMemory;
import com.dvid.dcam.feature.auth.application.usecase.AuthenticateOperatorUseCase;
import com.dvid.dcam.feature.auth.application.usecase.ManageOperatorUsersUseCase;
import com.dvid.dcam.feature.auth.application.usecase.OperatorSessionUseCase;
import com.dvid.dcam.feature.auth.domain.OperatorAccount;
import com.dvid.dcam.feature.auth.domain.OperatorSession;
import com.dvid.dcam.feature.device.application.port.DeviceRepository;
import com.dvid.dcam.feature.device.application.usecase.RefreshDeviceStatusUseCase;
import com.dvid.dcam.core.device.domain.DeviceInfo;
import com.dvid.dcam.feature.device.domain.DeviceStatus;
import com.dvid.dcam.feature.media.application.usecase.BrowseMediaUseCase;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
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
        AuthFixture auth = new AuthFixture();
        MainViewModel viewModel = viewModel(auth, () -> false);

        MainUiState state = awaitReady(viewModel);

        assertEquals(MainScreen.CAMERA, state.getScreen());
        assertNotNull(state.getOperatorSession());
        assertEquals(ManageOperatorUsersUseCase.DEFAULT_USER_ID,
                state.getOperatorSession().getUserId());
        viewModel.onCleared();
    }

    @Test void navigationDuringInitialDisabledAuthenticationDoesNotCancelDefaultLogin()
            throws Exception {
        AuthFixture auth = new AuthFixture();
        auth.repository.blockDefaultUserInsert();
        MainViewModel viewModel = viewModel(auth, () -> false);
        try {
            auth.repository.awaitDefaultUserInsert();

            viewModel.show(MainScreen.MENU);
            auth.repository.releaseDefaultUserInsert();
            MainUiState state = awaitReady(viewModel);

            assertEquals(MainScreen.CAMERA, state.getScreen());
            assertNotNull(state.getOperatorSession());
        } finally {
            auth.repository.releaseDefaultUserInsert();
            viewModel.onCleared();
        }
    }
    @Test void logoutWhileAuthenticationDisabledReturnsToDefaultUser() throws Exception {
        AuthFixture auth = new AuthFixture();
        MainViewModel viewModel = viewModel(auth, () -> false);
        awaitReady(viewModel);

        viewModel.logout();
        MainUiState state = awaitReady(viewModel);

        assertEquals(MainScreen.CAMERA, state.getScreen());
        assertNotNull(state.getOperatorSession());
        assertEquals(0, auth.repository.logoutCalls);
        viewModel.onCleared();
    }

    @Test void disablingAuthenticationReplacesSessionWithoutUnauthenticatedGap()
            throws Exception {
        AuthFixture auth = new AuthFixture();
        auth.setCurrent(new OperatorSession(
                "operator-session", "123456", "123456", "Operator", "boot", 1L));
        AtomicBoolean authenticationEnabled = new AtomicBoolean(true);
        MainViewModel viewModel = viewModel(auth, authenticationEnabled::get);
        awaitReady(viewModel);
        viewModel.show(MainScreen.DEVELOPER_SETTINGS);
        auth.repository.blockDefaultLogin();
        try {
            authenticationEnabled.set(false);
            viewModel.onAuthenticationSettingChanged();
            auth.repository.awaitDefaultLogin();

            MainUiState transitioning = viewModel.state().getValue();
            assertFalse(transitioning.isAuthenticationBusy());
            assertEquals(MainScreen.DEVELOPER_SETTINGS, transitioning.getScreen());
            assertEquals("123456", transitioning.getOperatorSession().getUserId());
            assertEquals("123456", auth.repository.activeSession.getUserId());
            assertEquals(0, auth.repository.logoutCalls);

            viewModel.show(MainScreen.MENU);
            assertEquals(MainScreen.MENU, viewModel.state().getValue().getScreen());
            auth.repository.releaseDefaultLogin();
            MainUiState state = awaitDefaultUser(viewModel);
            assertEquals(MainScreen.MENU, state.getScreen());
            assertEquals(ManageOperatorUsersUseCase.DEFAULT_USER_ID,
                    state.getOperatorSession().getUserId());
        } finally {
            auth.repository.releaseDefaultLogin();
            viewModel.onCleared();
        }
    }

    @Test void enablingAuthenticationEndsDefaultSessionAndShowsLogin() throws Exception {
        AtomicBoolean authenticationEnabled = new AtomicBoolean(false);
        AuthFixture auth = new AuthFixture();
        MainViewModel viewModel = viewModel(auth, authenticationEnabled::get);
        awaitReady(viewModel);

        authenticationEnabled.set(true);
        viewModel.onAuthenticationSettingChanged();
        MainUiState state = awaitReady(viewModel);

        assertEquals(MainScreen.LOGIN, state.getScreen());
        org.junit.jupiter.api.Assertions.assertNull(state.getOperatorSession());
        assertEquals(1, auth.repository.logoutCalls);
        viewModel.onCleared();
    }

    private static MainViewModel viewModel(
            AuthFixture auth, BooleanSupplier authenticationEnabled) {
        RefreshDeviceStatusUseCase refreshDevice = new RefreshDeviceStatusUseCase(
                new DeviceRepository() {
                    @Override public DeviceInfo readInfo() { return null; }
                    @Override public DeviceStatus readStatus() { return DeviceStatus.unknown(); }
                });
        BrowseMediaUseCase browseMedia = new BrowseMediaUseCase(
                relativePath -> Collections.emptyList());
        return new MainViewModel(
                DeviceStatus.unknown(), refreshDevice, browseMedia,
                auth.authenticate, auth.sessions, auth.users, authenticationEnabled, () -> true);
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

    private static MainUiState awaitDefaultUser(MainViewModel viewModel) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (System.nanoTime() < deadline) {
            MainUiState state = viewModel.state().getValue();
            if (state != null && !state.isAuthenticationBusy()
                    && state.getOperatorSession() != null
                    && ManageOperatorUsersUseCase.DEFAULT_USER_ID.equals(
                            state.getOperatorSession().getUserId())) {
                return state;
            }
            Thread.sleep(10);
        }
        throw new AssertionError("Default authentication did not finish");
    }

    private static final class AuthFixture {
        private final FakeRepository repository = new FakeRepository();
        private final OperatorSessionMemory memory = new OperatorSessionMemory();
        private final AuthenticateOperatorUseCase authenticate = new AuthenticateOperatorUseCase(
                repository, () -> "boot", memory);
        private final OperatorSessionUseCase sessions = new OperatorSessionUseCase(
                repository, () -> "boot", memory);
        private final ManageOperatorUsersUseCase users = new ManageOperatorUsersUseCase(repository);

        private void setCurrent(OperatorSession session) {
            repository.activeSession = session;
            memory.set(session);
        }
    }

    private static final class FakeRepository implements OperatorAuthRepository {
        private final Map<String, OperatorAccount> accounts = new HashMap<>();
        private final Map<String, String> passwords = new HashMap<>();
        private OperatorSession activeSession;
        private int logoutCalls;
        private CountDownLatch defaultUserInsertStarted;
        private CountDownLatch allowDefaultUserInsert;
        private CountDownLatch defaultLoginStarted;
        private CountDownLatch allowDefaultLogin;

        private void blockDefaultUserInsert() {
            defaultUserInsertStarted = new CountDownLatch(1);
            allowDefaultUserInsert = new CountDownLatch(1);
        }

        private void awaitDefaultUserInsert() throws InterruptedException {
            if (!defaultUserInsertStarted.await(2, TimeUnit.SECONDS))
                throw new AssertionError("Default user initialization did not start");
        }

        private void releaseDefaultUserInsert() {
            if (allowDefaultUserInsert != null) allowDefaultUserInsert.countDown();
        }

        private void blockDefaultLogin() {
            defaultLoginStarted = new CountDownLatch(1);
            allowDefaultLogin = new CountDownLatch(1);
        }

        private void awaitDefaultLogin() throws InterruptedException {
            if (!defaultLoginStarted.await(2, TimeUnit.SECONDS))
                throw new AssertionError("Default login did not start");
        }

        private void releaseDefaultLogin() {
            if (allowDefaultLogin != null) allowDefaultLogin.countDown();
        }

        @Override public void insertIfMissing(OperatorAccount account, String passwordText) {
            if (defaultUserInsertStarted != null) {
                defaultUserInsertStarted.countDown();
                try {
                    if (!allowDefaultUserInsert.await(2, TimeUnit.SECONDS))
                        throw new IllegalStateException("Default user initialization remained blocked");
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Default user initialization interrupted", error);
                }
            }
            if (!accounts.containsKey(account.getUserId())) upsert(account, passwordText);
        }

        @Override public void upsert(OperatorAccount account, String passwordText) {
            accounts.put(account.getUserId(), account);
            passwords.put(account.getUserId(), passwordText);
        }

        @Override public List<OperatorAccount> findCredentialMatches(
                String normalizedIdentifier, String passwordText) {
            if (defaultLoginStarted != null) {
                defaultLoginStarted.countDown();
                try {
                    if (!allowDefaultLogin.await(2, TimeUnit.SECONDS))
                        throw new IllegalStateException("Default login remained blocked");
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Default login interrupted", error);
                }
            }
            List<OperatorAccount> matches = new ArrayList<>();
            for (OperatorAccount account : accounts.values()) {
                if (!account.isActive()) continue;
                if (!passwordText.equals(passwords.get(account.getUserId()))) continue;
                if (normalizedIdentifier != null
                        && !account.getUserId().toLowerCase(Locale.ROOT)
                                .equals(normalizedIdentifier)) continue;
                matches.add(account);
            }
            return matches;
        }

        @Override public OperatorSession replaceActiveSession(
                OperatorAccount account, String bootId, long nowEpochMillis) {
            activeSession = new OperatorSession(
                    "session-" + account.getUserId(),
                    account.getUserId(),
                    account.getFileUserId(),
                    account.getDisplayName(),
                    bootId,
                    nowEpochMillis);
            return activeSession;
        }

        @Override public OperatorSession activeSessionForBoot(
                String bootId, long nowEpochMillis) {
            return activeSession != null && bootId.equals(activeSession.getBootId())
                    ? activeSession
                    : null;
        }

        @Override public void endActiveSession(long nowEpochMillis, String reason) {
            logoutCalls++;
            activeSession = null;
        }

        @Override public List<OperatorAccount> listUsers() {
            return List.copyOf(accounts.values());
        }
    }
}
