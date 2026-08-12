package com.dvid.dcam.platform.camera.shared;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dvid.dcam.feature.storage.domain.CaptureStorageCheck;
import java.io.IOException;
import java.nio.file.FileSystemException;
import org.junit.jupiter.api.Test;

final class AndroidSharedCameraMediaLifecycleStorageFailureTest {
    @Test void externalPreparingReservationFailureRetries() {
        IOException cause = new IOException("not mounted");

        SharedCameraMediaLifecycle.PreparationException error =
                AndroidSharedCameraMediaLifecycle.externalStoragePreparationException(
                        CaptureStorageCheck.preparing(0L, 1L, "checking"), true,
                        "preparing", "unavailable", cause);

        assertTrue(error.retryable());
        assertEquals("preparing", error.getMessage());
        assertEquals("unavailable", error.terminalMessage());
        assertEquals(cause, error.getCause());
    }

    @Test void externalUnavailableReservationFailureIsTerminal() {
        SharedCameraMediaLifecycle.PreparationException error =
                AndroidSharedCameraMediaLifecycle.externalStoragePreparationException(
                        CaptureStorageCheck.unavailable(0L, 1L, "removed"), true,
                        "preparing", "unavailable", new IOException());

        assertFalse(error.retryable());
        assertTrue(error.unavailable());
        assertEquals("unavailable", error.getMessage());
    }

    @Test void readyExternalReservationFailureKeepsNormalOneShotBehavior() {
        assertNull(AndroidSharedCameraMediaLifecycle.externalStoragePreparationException(
                CaptureStorageCheck.ready(2L, 1L), true,
                "preparing", "unavailable", new IOException()));
    }

    @Test void readyExternalOperationNotPermittedRetries() {
        FileSystemException cause = new FileSystemException("/storage/sd/file.mp4", null,
                "Operation not permitted");

        SharedCameraMediaLifecycle.PreparationException error =
                AndroidSharedCameraMediaLifecycle.externalStoragePreparationException(
                        CaptureStorageCheck.ready(2L, 1L), true,
                        "preparing", "unavailable", cause);

        assertTrue(error.retryable());
        assertEquals(cause, error.getCause());
    }

    @Test void autoStoragePreparingKeepsNormalOneShotBehavior() {
        assertNull(AndroidSharedCameraMediaLifecycle.externalStoragePreparationException(
                CaptureStorageCheck.preparing(0L, 1L, "checking"), false,
                "preparing", "unavailable", new IOException()));
    }
}