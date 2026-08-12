package com.dvid.dcam.feature.auth.application.usecase;

import com.dvid.dcam.feature.auth.application.port.OperatorAuthRepository;
import com.dvid.dcam.feature.auth.domain.OperatorAccount;
import com.dvid.dcam.feature.auth.domain.UserProvisioningRequest;
import com.dvid.dcam.feature.auth.domain.UserProvisioningResult;
import com.dvid.dcam.feature.auth.domain.UserSource;
import java.util.List;

public final class ManageOperatorUsersUseCase {
    public static final String DEFAULT_USER_ID = "B01OPR";
    public static final String DEFAULT_PASSWORD = "000000";

    private final OperatorAuthRepository repository;

    public ManageOperatorUsersUseCase(OperatorAuthRepository repository) {
        this.repository = repository;
    }
    public void ensureDefaultUser() {
        repository.insertIfMissing(
                new OperatorAccount(
                        DEFAULT_USER_ID,
                        DEFAULT_USER_ID,
                        "Default Operator",
                        UserSource.DEFAULT,
                        true),
                DEFAULT_PASSWORD);
    }
    public UserProvisioningResult upsert(UserProvisioningRequest request) {
        if (request == null) return UserProvisioningResult.failure("INVALID_INPUT");
        String userId = trim(request.getUserId());
        String password = request.getPasswordText();
        if (userId == null || !userId.matches("\\d{6}")) {
            return UserProvisioningResult.failure("USER_ID_MUST_BE_SIX_DIGITS");
        }
        if (password == null || password.isEmpty()) {
            return UserProvisioningResult.failure("PASSWORD_REQUIRED");
        }
        String displayName = trim(request.getDisplayName());
        if (displayName == null) displayName = userId;
        UserSource source = request.getSource() == null ? UserSource.DEVELOPER : request.getSource();
        try {
            repository.upsert(
                    new OperatorAccount(userId, userId, displayName, source, true),
                    password);
            return UserProvisioningResult.success();
        } catch (RuntimeException error) {
            return UserProvisioningResult.failure("USER_OR_LOGIN_NAME_ALREADY_EXISTS");
        }
    }
    public List<OperatorAccount> users() {
        return repository.listUsers();
    }

    private static String trim(String value) {
        if (value == null || value.trim().isEmpty()) return null;
        return value.trim();
    }

}
