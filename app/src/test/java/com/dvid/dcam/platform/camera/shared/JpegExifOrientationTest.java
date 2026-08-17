package com.dvid.dcam.platform.camera.shared;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

final class JpegExifOrientationTest {
    @Test void insertsMinimalExifWhenJpegHasNoExif() throws Exception {
        byte[] source = jpegWithoutExif();

        byte[] updated = JpegExifOrientation.apply(source, 6);

        assertEquals(6, orientation(updated));
        assertArrayEquals(source, withoutInsertedExif(updated));
    }

    @Test void patchesBigEndianOrientationWithoutChangingOtherBytes() throws Exception {
        byte[] source = jpegWithExif(false, 1, 0x0112);
        byte[] expected = source.clone();
        expected[30] = 0;
        expected[31] = 8;

        byte[] updated = JpegExifOrientation.apply(source, 8);

        assertEquals(8, orientation(updated));
        assertArrayEquals(expected, updated);
    }

    @Test void patchesLittleEndianOrientationWithoutChangingOtherBytes() throws Exception {
        byte[] source = jpegWithExif(true, 1, 0x0112);
        byte[] expected = source.clone();
        expected[30] = 3;
        expected[31] = 0;

        byte[] updated = JpegExifOrientation.apply(source, 3);

        assertEquals(3, orientation(updated));
        assertArrayEquals(expected, updated);
    }

    @Test void addsOrientationToExistingExifWithoutChangingExistingTag() throws Exception {
        for (boolean littleEndian : new boolean[] {false, true}) {
            byte[] source = jpegWithExif(littleEndian, 7, 0x0100);
            byte[] original = source.clone();

            byte[] updated = JpegExifOrientation.apply(source, 6);

            assertEquals(6, tagValue(updated, 0x0112));
            assertEquals(7, tagValue(updated, 0x0100));
            assertArrayEquals(original, source);
        }
    }

    @Test void rejectsMalformedExifInsteadOfDroppingMetadata() {
        byte[] source = jpegWithExif(false, 1, 0x0112);
        source[12] = 'X';

        assertThrows(IOException.class, () -> JpegExifOrientation.apply(source, 6));
    }

    private static byte[] jpegWithoutExif() {
        return new byte[] {
                (byte) 0xff, (byte) 0xd8,
                (byte) 0xff, (byte) 0xe0, 0x00, 0x04, 0x01, 0x02,
                (byte) 0xff, (byte) 0xda
        };
    }

    private static byte[] jpegWithExif(boolean littleEndian, int value, int tag) {
        byte[] jpeg = new byte[] {
                (byte) 0xff, (byte) 0xd8,
                (byte) 0xff, (byte) 0xe1, 0x00, 0x22,
                'E', 'x', 'i', 'f', 0x00, 0x00,
                'M', 'M', 0x00, 0x2a, 0x00, 0x00, 0x00, 0x08,
                0x00, 0x01,
                (byte) (tag >>> 8), (byte) tag,
                0x00, 0x03,
                0x00, 0x00, 0x00, 0x01,
                0x00, (byte) value, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x00,
                (byte) 0xff, (byte) 0xda
        };
        if (!littleEndian) return jpeg;
        jpeg[12] = 'I';
        jpeg[13] = 'I';
        jpeg[14] = 0x2a;
        jpeg[15] = 0x00;
        jpeg[16] = 0x08;
        jpeg[17] = 0x00;
        jpeg[18] = 0x00;
        jpeg[19] = 0x00;
        jpeg[20] = 0x01;
        jpeg[21] = 0x00;
        jpeg[22] = (byte) tag;
        jpeg[23] = (byte) (tag >>> 8);
        jpeg[24] = 0x03;
        jpeg[25] = 0x00;
        jpeg[26] = 0x01;
        jpeg[27] = 0x00;
        jpeg[28] = 0x00;
        jpeg[29] = 0x00;
        jpeg[30] = (byte) value;
        jpeg[31] = 0x00;
        return jpeg;
    }

    private static int orientation(byte[] jpeg) {
        return tagValue(jpeg, 0x0112);
    }

    private static int tagValue(byte[] jpeg, int tag) {
        int tiffOffset = 12;
        boolean littleEndian = jpeg[tiffOffset] == 'I';
        int ifdOffset = tiffOffset + readUnsignedInt(jpeg, tiffOffset + 4, littleEndian);
        int entryCount = readUnsignedShort(jpeg, ifdOffset, littleEndian);
        for (int index = 0; index < entryCount; index++) {
            int entryOffset = ifdOffset + 2 + index * 12;
            if (readUnsignedShort(jpeg, entryOffset, littleEndian) == tag) {
                return readUnsignedShort(jpeg, entryOffset + 8, littleEndian);
            }
        }
        throw new AssertionError("missing EXIF tag " + tag);
    }

    private static int readUnsignedShort(
            byte[] data, int offset, boolean littleEndian) {
        return littleEndian
                ? Byte.toUnsignedInt(data[offset])
                        | Byte.toUnsignedInt(data[offset + 1]) << 8
                : Byte.toUnsignedInt(data[offset]) << 8
                        | Byte.toUnsignedInt(data[offset + 1]);
    }

    private static int readUnsignedInt(
            byte[] data, int offset, boolean littleEndian) {
        return littleEndian
                ? Byte.toUnsignedInt(data[offset])
                        | Byte.toUnsignedInt(data[offset + 1]) << 8
                        | Byte.toUnsignedInt(data[offset + 2]) << 16
                        | Byte.toUnsignedInt(data[offset + 3]) << 24
                : Byte.toUnsignedInt(data[offset]) << 24
                        | Byte.toUnsignedInt(data[offset + 1]) << 16
                        | Byte.toUnsignedInt(data[offset + 2]) << 8
                        | Byte.toUnsignedInt(data[offset + 3]);
    }

    private static byte[] withoutInsertedExif(byte[] jpeg) {
        int app1Bytes = 36;
        byte[] result = Arrays.copyOf(jpeg, jpeg.length - app1Bytes);
        System.arraycopy(jpeg, 2 + app1Bytes, result, 2, jpeg.length - 2 - app1Bytes);
        return result;
    }
}