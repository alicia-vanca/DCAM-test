package com.dvid.dcam.feature.auth.application.usecase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dvid.dcam.feature.auth.application.port.BootIdentitySource;
import com.dvid.dcam.feature.auth.application.port.OperatorAuthRepository;
import com.dvid.dcam.feature.auth.application.repository.OperatorSessionMemory;
import com.dvid.dcam.feature.auth.domain.LoginCredentials;
import com.dvid.dcam.feature.auth.domain.LoginFailure;
import com.dvid.dcam.feature.auth.domain.LoginResult;
import com.dvid.dcam.feature.auth.domain.OperatorAccount;
import com.dvid.dcam.feature.auth.domain.OperatorSession;
import com.dvid.dcam.feature.auth.domain.UserProvisioningRequest;
import com.dvid.dcam.feature.auth.domain.UserProvisioningResult;
import com.dvid.dcam.feature.auth.domain.UserSource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class OperatorAuthUseCaseTest {
    @Test void defaultUserIsSeededAndCanLoginWithMvpPin() {
        FakeRepository repository = new FakeRepository();
        MutableBootIdentity boot = new MutableBootIdentity("boot-a");
        OperatorSessionMemory memory = new OperatorSessionMemory();

        new ManageOperatorUsersUseCase(repository).ensureDefaultUser();
        LoginResult result = new AuthenticateOperatorUseCase(repository, boot, memory)
                .execute(LoginCredentials.usernameAndPassword("B01OPR", "000000"));

        assertTrue(result.isSuccess());
        assertEquals("B01OPR", result.getSession().getUserId());
        assertEquals("B01OPR", result.getSession().getFileUserId());
        assertSame(result.getSession(), memory.current());
    }

    @Test void wrongPinFailsWithoutOpeningSession() {
        FakeRepository repository = new FakeRepository();
        OperatorSessionMemory memory = new OperatorSessionMemory();
        new ManageOperatorUsersUseCase(repository).ensureDefaultUser();

        LoginResult result = new AuthenticateOperatorUseCase(
                repository, new MutableBootIdentity("boot-a"), memory)
                .execute(LoginCredentials.passwordOnly("111111"));

        assertFalse(result.isSuccess());
        assertEquals(LoginFailure.INVALID_CREDENTIALS, result.getFailure());
        assertFalse(memory.hasActiveSession());
    }

    @Test void duplicatePasswordRequiresUserId() {
        FakeRepository repository = new FakeRepository();
        repository.upsert(account("111111", UserSource.DEVELOPER), "123456");
        repository.upsert(account("222222", UserSource.CLOUD), "123456");
        AuthenticateOperatorUseCase authenticate = new AuthenticateOperatorUseCase(
                repository, new MutableBootIdentity("boot-a"), new OperatorSessionMemory());

        LoginResult ambiguous = authenticate.execute(LoginCredentials.passwordOnly("123456"));
        LoginResult byUserId = authenticate.execute(
                LoginCredentials.usernameAndPassword("222222", "123456"));

        assertFalse(ambiguous.isSuccess());
        assertEquals(LoginFailure.USERNAME_REQUIRED, ambiguous.getFailure());
        assertTrue(byUserId.isSuccess());
        assertEquals("222222", byUserId.getSession().getUserId());
    }

    @Test void provisioningValidatesAndKeepsDeveloperAndCloudUsersAtSameBoundary() {
        FakeRepository repository = new FakeRepository();
        ManageOperatorUsersUseCase users = new ManageOperatorUsersUseCase(repository);

        UserProvisioningResult badId = users.upsert(new UserProvisioningRequest(
                "12345", "Short", "123456", UserSource.DEVELOPER));
        UserProvisioningResult noPassword = users.upsert(new UserProvisioningRequest(
                "123456", "No Pass", "", UserSource.DEVELOPER));
        UserProvisioningResult developer = users.upsert(new UserProvisioningRequest(
                "123456", "Alice Dev", "111111", UserSource.DEVELOPER));
        UserProvisioningResult cloud = users.upsert(new UserProvisioningRequest(
                "654321", "Bob Cloud", "222222", UserSource.CLOUD));

        assertFalse(badId.isSuccessful());
        assertEquals("USER_ID_MUST_BE_SIX_DIGITS", badId.getErrorCode());
        assertFalse(noPassword.isSuccessful());
        assertEquals("PASSWORD_REQUIRED", noPassword.getErrorCode());
        assertTrue(developer.isSuccessful());
        assertTrue(cloud.isSuccessful());
        assertEquals(UserSource.DEVELOPER, repository.accounts.get("123456").getSource());
        assertEquals(UserSource.CLOUD, repository.accounts.get("654321").getSource());
    }

    @Test void sessionRestoreHonorsBootAndLogoutClearsActiveSession() {
        FakeRepository repository = new FakeRepository();
        MutableBootIdentity boot = new MutableBootIdentity("boot-a");
        OperatorSessionMemory loginMemory = new OperatorSessionMemory();
        new ManageOperatorUsersUseCase(repository).ensureDefaultUser();
        LoginResult login = new AuthenticateOperatorUseCase(repository, boot, loginMemory)
                .execute(LoginCredentials.passwordOnly("000000"));

        OperatorSessionMemory restoredMemory = new OperatorSessionMemory();
        OperatorSessionUseCase sessions =
                new OperatorSessionUseCase(repository, boot, restoredMemory);
        OperatorSession restored = sessions.restore();
        boot.bootId = "boot-b";
        OperatorSession wrongBoot = sessions.restore();
        LoginResult secondLogin = new AuthenticateOperatorUseCase(repository, boot, restoredMemory)
                .execute(LoginCredentials.passwordOnly("000000"));
        sessions.logout();

        assertTrue(login.isSuccess());
        assertEquals(login.getSession().getSessionId(), restored.getSessionId());
        assertNull(wrongBoot);
        assertTrue(secondLogin.isSuccess());
        assertFalse(sessions.hasActiveSession());
        assertNull(repository.activeSession);
        assertEquals("LOGOUT", repository.lastEndReason);
    }

    private static OperatorAccount account(String userId, UserSource source) {
        return new OperatorAccount(userId, userId, userId, source, true);
    }

    private static final class MutableBootIdentity implements BootIdentitySource {
        private String bootId;

        private MutableBootIdentity(String bootId) {
            this.bootId = bootId;
        }

        @Override public String currentBootId() {
            return bootId;
        }
    }

    private static final class FakeRepository implements OperatorAuthRepository {
        private final Map<String, OperatorAccount> accounts = new HashMap<>();
        private final Map<String, String> passwords = new HashMap<>();
        private OperatorSession activeSession;
        private String lastEndReason;

        @Override public void insertIfMissing(OperatorAccount account, String passwordText) {
            if (!accounts.containsKey(account.getUserId())) upsert(account, passwordText);
        }

        @Override public void upsert(OperatorAccount account, String passwordText) {
            for (OperatorAccount existing : accounts.values()) {
                if (existing.getUserId().equals(account.getUserId())) continue;
                if (existing.getFileUserId().equals(account.getFileUserId())) {
                    throw new IllegalStateException("duplicate file user id");
                }
            }
            accounts.put(account.getUserId(), account);
            passwords.put(account.getUserId(), passwordText);
        }

        @Override public List<OperatorAccount> findCredentialMatches(
                String normalizedIdentifier,
                String passwordText) {
            List<OperatorAccount> matches = new ArrayList<>();
            for (OperatorAccount account : accounts.values()) {
                if (!account.isActive()) continue;
                if (!passwordText.equals(passwords.get(account.getUserId()))) continue;
                if (!matchesIdentifier(account, normalizedIdentifier)) continue;
                matches.add(account);
            }
            return matches;
        }

        @Override public OperatorSession replaceActiveSession(
                OperatorAccount account,
                String bootId,
                long nowEpochMillis) {
            activeSession = new OperatorSession(
                    "session-" + account.getUserId() + "-" + bootId,
                    account.getUserId(),
                    account.getFileUserId(),
                    account.getDisplayName(),
                    bootId,
                    nowEpochMillis);
            return activeSession;
        }

        @Override public OperatorSession activeSessionForBoot(String bootId, long nowEpochMillis) {
            if (activeSession == null) return null;
            if (bootId.equals(activeSession.getBootId())) return activeSession;
            endActiveSession(nowEpochMillis, "BOOT_CHANGED");
            return null;
        }

        @Override public void endActiveSession(long nowEpochMillis, String reason) {
            activeSession = null;
            lastEndReason = reason;
        }

        @Override public List<OperatorAccount> listUsers() {
            return List.copyOf(accounts.values());
        }

        private static boolean matchesIdentifier(
                OperatorAccount account,
                String normalizedIdentifier) {
            if (normalizedIdentifier == null) return true;
            if (account.getUserId().toLowerCase(Locale.ROOT).equals(normalizedIdentifier)) return true;
            return false;
        }
    }
}
