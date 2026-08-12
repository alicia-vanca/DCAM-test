package com.dvid.dcam.feature.auth.application.usecase;

import com.dvid.dcam.feature.auth.application.port.BootIdentitySource;
import com.dvid.dcam.feature.auth.application.port.OperatorAuthRepository;
import com.dvid.dcam.feature.auth.application.repository.OperatorSessionMemory;
import com.dvid.dcam.feature.auth.domain.LoginCredentials;
import com.dvid.dcam.feature.auth.domain.LoginFailure;
import com.dvid.dcam.feature.auth.domain.LoginResult;
import com.dvid.dcam.feature.auth.domain.OperatorAccount;
import com.dvid.dcam.feature.auth.domain.OperatorSession;
import java.util.List;
import java.util.Locale;

public final class AuthenticateOperatorUseCase {
    private final OperatorAuthRepository repository;
    private final BootIdentitySource bootIdentity;
    private final OperatorSessionMemory memory;

    public AuthenticateOperatorUseCase(
            OperatorAuthRepository repository,
            BootIdentitySource bootIdentity,
            OperatorSessionMemory memory) {
        this.repository = repository;
        this.bootIdentity = bootIdentity;
        this.memory = memory;
    }
    public LoginResult execute(LoginCredentials credentials) {
        if (credentials == null || credentials.getPasswordText().isEmpty()) {
            return LoginResult.failure(LoginFailure.INVALID_INPUT);
        }
        String identifier = normalizeOptional(credentials.getIdentifier());
        try {
            List<OperatorAccount> matches =
                    repository.findCredentialMatches(identifier, credentials.getPasswordText());
            if (matches.isEmpty()) return LoginResult.failure(LoginFailure.INVALID_CREDENTIALS);
            if (matches.size() > 1) return LoginResult.failure(LoginFailure.USERNAME_REQUIRED);
            OperatorSession session = repository.replaceActiveSession(
                    matches.get(0), bootIdentity.currentBootId(), System.currentTimeMillis());
            memory.set(session);
            return LoginResult.success(session);
        } catch (RuntimeException error) {
            return LoginResult.failure(LoginFailure.STORAGE_ERROR);
        }
    }

    private static String normalizeOptional(String value) {
        if (value == null || value.trim().isEmpty()) return null;
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
