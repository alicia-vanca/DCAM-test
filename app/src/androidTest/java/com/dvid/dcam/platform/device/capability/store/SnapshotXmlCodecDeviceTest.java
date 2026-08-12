package com.dvid.dcam.platform.device.capability.store;

import static org.junit.Assert.assertEquals;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.dvid.dcam.feature.device.application.port.CameraCapabilityStore.InitializationState;
import java.nio.charset.StandardCharsets;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class SnapshotXmlCodecDeviceTest {
    @Test public void decoderSupportsPlatformDocumentBuilderFactory() throws Exception {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<capabilities format=\"1\" initializationState=\"incomplete\" "
                + "hardwareSignature=\"device\" />\n";

        var snapshot = new SnapshotXmlCodec().decode(xml.getBytes(StandardCharsets.UTF_8));

        assertEquals(InitializationState.INCOMPLETE, snapshot.initializationState());
        assertEquals("device", snapshot.hardwareSignature());
    }
}
