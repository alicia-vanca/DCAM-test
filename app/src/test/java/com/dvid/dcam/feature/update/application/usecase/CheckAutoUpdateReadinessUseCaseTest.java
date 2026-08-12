package com.dvid.dcam.feature.update.application.usecase;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dvid.dcam.feature.update.domain.AutoUpdatePreconditions;
import org.junit.jupiter.api.Test;

final class CheckAutoUpdateReadinessUseCaseTest {
    @Test void readyOnlyWhenAllPreconditionsPass() {
        CheckAutoUpdateReadinessUseCase useCase = new CheckAutoUpdateReadinessUseCase();

        assertTrue(useCase.canCheckForUpdate(new AutoUpdatePreconditions(
                true, true, true, true, true, true, true)));
    }

    @Test void blocksWhenRecordingOrEmergencyIsActive() {
        CheckAutoUpdateReadinessUseCase useCase = new CheckAutoUpdateReadinessUseCase();

        assertFalse(useCase.canCheckForUpdate(new AutoUpdatePreconditions(
                true, false, true, true, true, true, true)));
        assertFalse(useCase.canCheckForUpdate(new AutoUpdatePreconditions(
                true, true, false, true, true, true, true)));
    }
}
