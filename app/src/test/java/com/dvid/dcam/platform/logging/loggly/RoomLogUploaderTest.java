package com.dvid.dcam.platform.logging.loggly;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import android.content.Context;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;

class RoomLogUploaderTest {
    @Test
    void uploadLoopIsSerializedAcrossForegroundAndJobWorkers() throws Exception {
        assertTrue(Modifier.isSynchronized(RoomLogUploader.class
                .getDeclaredMethod("uploadPending", Context.class, BooleanSupplier.class)
                .getModifiers()));
    }

    @Test
    void bulkPayloadSeparatesEventsWithoutChangingJson() {
        assertEquals("{\"id\":1}\n{\"id\":2}",
                LogglyHttpClient.bulkPayload(List.of("{\"id\":1}", "{\"id\":2}")));
    }

    @Test
    void retryDelayIsIndependentAndExponentiallyCapped() {
        assertEquals(10_000L, RoomLogUploader.retryDelayMillis(1));
        assertEquals(20_000L, RoomLogUploader.retryDelayMillis(2));
        assertEquals(5_120_000L, RoomLogUploader.retryDelayMillis(10));
        assertEquals(18_000_000L, RoomLogUploader.retryDelayMillis(12));
        assertEquals(18_000_000L, RoomLogUploader.retryDelayMillis(20));
    }
}