package com.dvid.dcam.platform.audio;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class SharedMicrophoneCaptureTest {
    @Test void reportsBoundedSubscriptionBacklogDuration() {
        assertEquals(2_731L, SharedMicrophoneCapture.maximumSubscriptionBacklogMillis());
    }
}
