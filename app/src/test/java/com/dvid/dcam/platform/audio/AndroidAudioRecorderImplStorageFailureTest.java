package com.dvid.dcam.platform.audio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dvid.dcam.feature.capture.application.port.AudioRecorder;
import com.dvid.dcam.feature.storage.domain.CaptureStorageCheck;
import java.io.IOException;
import java.nio.file.FileSystemException;
import org.junit.jupiter.api.Test;

final class AndroidAudioRecorderImplStorageFailureTest {
    @Test void externalPreparingFailureRetries() {
        IOException cause = new IOException("not mounted");

        AudioRecorder.PreparationException error =
                AndroidAudioRecorderImpl.externalStoragePreparationException(
                        CaptureStorageCheck.preparing(0L, 1L, "checking"), true,
                        "preparing", "unavailable", cause);

        assertTrue(error.retryable());
        assertEquals("preparing", error.getMessage());
        assertEquals("unavailable", error.terminalMessage());
        assertEquals(cause, error.getCause());
    }

    @Test void externalUnavailableFailureIsTerminal() {
        AudioRecorder.PreparationException error =
                AndroidAudioRecorderImpl.externalStoragePreparationException(
                        CaptureStorageCheck.unavailable(0L, 1L, "removed"), true,
                        "preparing", "unavailable", new IOException());

        assertFalse(error.retryable());
        assertEquals("unavailable", error.terminalMessage());
    }

    @Test void readyExternalFailureKeepsNormalOneShotBehavior() {
        assertNull(AndroidAudioRecorderImpl.externalStoragePreparationException(
                CaptureStorageCheck.ready(2L, 1L), true,
                "preparing", "unavailable", new IOException()));
    }

    @Test void readyExternalOperationNotPermittedRetries() {
        FileSystemException cause = new FileSystemException("/storage/sd/audio.m4a", null,
                "Operation not permitted");

        AudioRecorder.PreparationException error =
                AndroidAudioRecorderImpl.externalStoragePreparationException(
                        CaptureStorageCheck.ready(2L, 1L), true,
                        "preparing", "unavailable", cause);

        assertTrue(error.retryable());
        assertEquals(cause, error.getCause());
    }

    @Test void autoStoragePreparingKeepsFallbackBehavior() {
        assertNull(AndroidAudioRecorderImpl.externalStoragePreparationException(
                CaptureStorageCheck.preparing(0L, 1L, "checking"), false,
                "preparing", "unavailable", new IOException()));
    }
}