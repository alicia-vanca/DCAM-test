package com.dvid.dcam.platform.storage;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.DataOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class AndroidDcamMediaValidatorTest {
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
    }
}