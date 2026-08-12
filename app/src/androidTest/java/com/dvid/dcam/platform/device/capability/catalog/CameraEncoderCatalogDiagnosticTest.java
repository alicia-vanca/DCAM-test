package com.dvid.dcam.platform.device.capability.catalog;

import static org.junit.Assert.assertNotNull;

import android.content.Context;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.dvid.dcam.core.logging.application.port.Logger;
import com.dvid.dcam.feature.device.application.port.CameraCatalogSource.CameraFacts;
import com.dvid.dcam.feature.device.application.port.CameraCatalogSource.EncoderFacts;
import com.dvid.dcam.feature.device.application.port.CameraCatalogSource.RawCatalog;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class CameraEncoderCatalogDiagnosticTest {
    @Test public void printsRawFactsWithoutChangingSettingsOrPersistence() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        RawCatalog catalog = new AndroidCameraCatalogSource(
                context, new PrintingLogger()).load();

        System.out.println("CAMERA_CATALOG_DIAGNOSTIC device=" + catalog.deviceIdentity());
        System.out.println("CAMERA_CATALOG_DIAGNOSTIC concurrency=" + catalog.concurrency());
        for (CameraFacts camera : catalog.cameras()) printCamera(camera);
        for (EncoderFacts encoder : catalog.h264Encoders()) printEncoder(encoder);
        printChunks("hardwareSignatureInput", catalog.hardwareSignatureInput());

        assertNotNull(catalog.cameras());
        assertNotNull(catalog.h264Encoders());
        assertNotNull(catalog.hardwareSignatureInput());
    }

    private static void printCamera(CameraFacts camera) {
        String prefix = "CAMERA_CATALOG_DIAGNOSTIC cameraId=" + camera.cameraId();
        System.out.println(prefix + " hardwareLevel=" + camera.hardwareLevel()
                + " lensFacing=" + camera.lensFacing()
                + " sensorOrientationDegrees=" + camera.sensorOrientationDegrees()
                + " streamConfigurationMapAvailable="
                + camera.streamConfigurationMapAvailable());
        System.out.println(prefix + " availableCapabilities="
                + camera.availableCapabilities());
        System.out.println(prefix + " physicalCameraIds=" + camera.physicalCameraIds());
        System.out.println(prefix + " privateOutputs=" + camera.privateOutputs());
        System.out.println(prefix + " mediaCodecOutputs=" + camera.mediaCodecOutputs());
        System.out.println(prefix + " mediaRecorderOutputs=" + camera.mediaRecorderOutputs());
        System.out.println(prefix + " jpegOutputs=" + camera.jpegOutputs());
        System.out.println(prefix + " aeTargetFpsRanges=" + camera.aeTargetFpsRanges());
    }

    private static void printEncoder(EncoderFacts encoder) {
        String prefix = "CAMERA_CATALOG_DIAGNOSTIC encoder=" + encoder.name();
        System.out.println(prefix + " canonicalName=" + encoder.canonicalName()
                + " alias=" + encoder.alias()
                + " hardwareAccelerated=" + encoder.hardwareAccelerated()
                + " softwareOnly=" + encoder.softwareOnly()
                + " vendor=" + encoder.vendor()
                + " maxSupportedInstances=" + encoder.maxSupportedInstances());
        System.out.println(prefix + " profileLevels=" + encoder.profileLevels());
        System.out.println(prefix + " colorFormats=" + encoder.colorFormats());
        System.out.println(prefix + " videoCapabilities=" + encoder.videoCapabilities());
    }

    private static void printChunks(String label, String value) {
        int chunkSize = 3000;
        for (int offset = 0; offset < value.length(); offset += chunkSize) {
            int end = Math.min(value.length(), offset + chunkSize);
            System.out.println("CAMERA_CATALOG_DIAGNOSTIC " + label
                    + " offset=" + offset + " value=" + value.substring(offset, end));
        }
    }

    private static final class PrintingLogger implements Logger {
        @Override public void debug(String message) { System.out.println(message); }

        @Override public void info(String message) { System.out.println(message); }

        @Override public void info(String message, Throwable error) {
            System.out.println(message + " error=" + error);
        }

        @Override public void warn(String message, Throwable error) {
            System.out.println(message + " error=" + error);
        }

        @Override public void error(String message, Throwable error) {
            System.out.println(message + " error=" + error);
        }
    }
}
