package com.dvid.dcam.platform.storage;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.DataOutputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class AndroidDcamMediaValidatorTest {
    @Test
    void acceptsValidLogicalJpegWithoutReadingCiphertextFile(@TempDir Path root)
            throws Exception {
        Path file = root.resolve("logical.jpg");
        try (DcamRecordingOutput output = DcamRecordingOutput.openPlain(file.toFile())) {
            byte[] jpeg = jpeg(640, 480);
            ByteBuffer source = ByteBuffer.wrap(jpeg);
            while (source.hasRemaining()) output.write(source);

            DcamMediaValidationResult result = new AndroidDcamMediaValidator()
                    .validate(DcamFileType.IMAGE, output.media());

            assertTrue(result.playable(), result.detail());
            assertTrue(result.detail().contains("640x480"));
        }
    }

    @Test
    void rejectsLogicalJpegWithTruncatedDeclaredSegment(@TempDir Path root)
            throws Exception {
        Path file = root.resolve("truncated.jpg");
        try (DcamRecordingOutput output = DcamRecordingOutput.openPlain(file.toFile())) {
            ByteBuffer source = ByteBuffer.wrap(new byte[] {
                    (byte) 0xff, (byte) 0xd8,
                    (byte) 0xff, (byte) 0xc0, 0x00, 0x11, 0x08, 0x01, 0x00
            });
            while (source.hasRemaining()) output.write(source);

            DcamMediaValidationResult result = new AndroidDcamMediaValidator()
                    .validate(DcamFileType.IMAGE, output.media());

            assertFalse(result.playable());
            assertTrue(result.detail().contains("segment length is invalid"));
        }
    }

    @Test
    void rejectsInterruptedMp4WithUnfinishedMdatSizeBeforeAndroidDecoder(
            @TempDir Path root) throws Exception {
        Path file = root.resolve("interrupted.mp4");
        try (DataOutputStream output = new DataOutputStream(Files.newOutputStream(file))) {
            output.writeInt(24);
            output.writeBytes("ftyp");
            output.writeBytes("mp42");
            output.writeInt(0);
            output.writeBytes("isom");
            output.writeBytes("mp42");
            output.writeInt(1);
            output.writeBytes("mdat");
            output.writeLong(0x3f3f3f3f3f3f3f3fL);
            output.write(new byte[128]);
        }

        DcamMediaValidationResult result =
                new AndroidDcamMediaValidator().validate(DcamFileType.VIDEO, file.toFile());

        assertFalse(result.playable());
        assertTrue(result.detail().contains("MP4 box 'mdat' declares invalid size"));
        assertTrue(result.detail().contains("before MP4 finalization completed"));

        DcamMediaValidationResult audioResult =
                new AndroidDcamMediaValidator().validate(
                        DcamFileType.AUDIO_M4A, file.toFile());

        assertFalse(audioResult.playable());
        assertTrue(audioResult.detail().contains("MP4 box 'mdat' declares invalid size"));
    }

    private static byte[] jpeg(int width, int height) {
        return new byte[] {
                (byte) 0xff, (byte) 0xd8,
                (byte) 0xff, (byte) 0xe0, 0x00, 0x04, 0x01, 0x02,
                (byte) 0xff, (byte) 0xc0, 0x00, 0x11, 0x08,
                (byte) (height >>> 8), (byte) height,
                (byte) (width >>> 8), (byte) width,
                0x03,
                0x01, 0x11, 0x00,
                0x02, 0x11, 0x00,
                0x03, 0x11, 0x00,
                (byte) 0xff, (byte) 0xda, 0x00, 0x08,
                0x01, 0x01, 0x00, 0x00, 0x3f, 0x00,
                0x00, (byte) 0xff, (byte) 0xd9
        };
    }
}