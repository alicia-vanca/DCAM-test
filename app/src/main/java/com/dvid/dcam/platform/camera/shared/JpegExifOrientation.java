package com.dvid.dcam.platform.camera.shared;

import java.io.IOException;
import java.util.Arrays;

final class JpegExifOrientation {
    private static final int MARKER_APP1 = 0xe1;
    private static final int MARKER_EOI = 0xd9;
    private static final int MARKER_SOS = 0xda;
    private static final int TAG_ORIENTATION = 0x0112;
    private static final int TYPE_SHORT = 3;
    private static final byte[] EXIF_PREFIX = {'E', 'x', 'i', 'f', 0, 0};

    private JpegExifOrientation() {}

    static byte[] apply(byte[] jpeg, int orientation) throws IOException {
        validateJpeg(jpeg);
        int offset = 2;
        while (offset < jpeg.length) {
            JpegMarker marker = readMarker(jpeg, offset);
            if (marker.endsMetadata()) {
                return insertMinimalExif(jpeg, orientation);
            }
            if (marker.hasNoLength()) {
                offset = marker.nextOffset();
                continue;
            }
            JpegSegment segment = readSegment(jpeg, marker.nextOffset());
            if (marker.value() == MARKER_APP1 && segment.hasExifPrefix(jpeg)) {
                return applyToExif(jpeg, segment, orientation);
            }
            offset = segment.end();
        }
        throw new IOException("JPEG payload ended before image data.");
    }

    private static void validateJpeg(byte[] jpeg) throws IOException {
        if (jpeg == null || jpeg.length < 4
                || unsigned(jpeg[0]) != 0xff || unsigned(jpeg[1]) != 0xd8) {
            throw new IOException("JPEG payload has no SOI marker.");
        }
    }

    private static JpegMarker readMarker(byte[] jpeg, int offset) throws IOException {
        if (unsigned(jpeg[offset]) != 0xff) {
            throw new IOException("JPEG metadata marker prefix is invalid.");
        }
        while (offset < jpeg.length && unsigned(jpeg[offset]) == 0xff) {
            offset++;
        }
        if (offset >= jpeg.length) throw new IOException("JPEG marker is truncated.");
        return new JpegMarker(unsigned(jpeg[offset]), offset + 1);
    }

    private static JpegSegment readSegment(byte[] jpeg, int lengthOffset) throws IOException {
        if (lengthOffset + 2 > jpeg.length) {
            throw new IOException("JPEG segment length is truncated.");
        }
        int segmentBytes = readUnsignedShortBigEndian(jpeg, lengthOffset);
        if (segmentBytes < 2 || segmentBytes > jpeg.length - lengthOffset) {
            throw new IOException("JPEG segment length is invalid.");
        }
        return new JpegSegment(lengthOffset, segmentBytes, lengthOffset + 2,
                lengthOffset + segmentBytes);
    }

    private static byte[] applyToExif(byte[] jpeg, JpegSegment segment, int orientation)
            throws IOException {
        ExifIfd ifd = readExifIfd(jpeg, segment);
        int orientationEntry = findOrientationEntry(jpeg, ifd);
        if (orientationEntry >= 0) {
            writeUnsignedShort(jpeg, orientationEntry + 8, orientation, ifd.littleEndian());
            jpeg[orientationEntry + 10] = 0;
            jpeg[orientationEntry + 11] = 0;
            return jpeg;
        }
        return appendOrientationIfd(jpeg, ifd, orientation);
    }

    private static ExifIfd readExifIfd(byte[] jpeg, JpegSegment segment) throws IOException {
        int tiffOffset = segment.payloadOffset() + EXIF_PREFIX.length;
        int exifEnd = segment.end();
        if (tiffOffset + 8 > exifEnd) throw new IOException("EXIF TIFF header is truncated.");
        boolean littleEndian = isLittleEndian(jpeg, tiffOffset);
        if (readUnsignedShort(jpeg, tiffOffset + 2, littleEndian) != 42) {
            throw new IOException("EXIF TIFF marker is invalid.");
        }
        long ifdRelativeOffset = readUnsignedInt(jpeg, tiffOffset + 4, littleEndian);
        if (ifdRelativeOffset > Integer.MAX_VALUE) {
            throw new IOException("EXIF IFD offset is too large.");
        }
        long ifdLong = tiffOffset + ifdRelativeOffset;
        if (ifdLong < tiffOffset || ifdLong + 2L > exifEnd) {
            throw new IOException("EXIF IFD offset is outside APP1.");
        }
        int ifdOffset = (int) ifdLong;
        int entryCount = readUnsignedShort(jpeg, ifdOffset, littleEndian);
        long entriesEnd = ifdOffset + 2L + entryCount * 12L;
        if (entriesEnd + 4L > exifEnd) throw new IOException("EXIF IFD entries are truncated.");
        return new ExifIfd(segment.lengthOffset(), segment.bytes(), tiffOffset, exifEnd,
                ifdOffset, entryCount, littleEndian);
    }

    private static boolean isLittleEndian(byte[] jpeg, int tiffOffset) throws IOException {
        if (jpeg[tiffOffset] == 'I' && jpeg[tiffOffset + 1] == 'I') return true;
        if (jpeg[tiffOffset] == 'M' && jpeg[tiffOffset + 1] == 'M') return false;
        throw new IOException("EXIF TIFF byte order is invalid.");
    }

    private static int findOrientationEntry(byte[] jpeg, ExifIfd ifd) throws IOException {
        for (int index = 0; index < ifd.entryCount(); index++) {
            int entryOffset = ifd.ifdOffset() + 2 + index * 12;
            if (readUnsignedShort(jpeg, entryOffset, ifd.littleEndian()) != TAG_ORIENTATION) {
                continue;
            }
            int type = readUnsignedShort(jpeg, entryOffset + 2, ifd.littleEndian());
            long count = readUnsignedInt(jpeg, entryOffset + 4, ifd.littleEndian());
            if (type != TYPE_SHORT || count != 1L) {
                throw new IOException("EXIF Orientation entry has unsupported type or count.");
            }
            return entryOffset;
        }
        return -1;
    }

    private static byte[] appendOrientationIfd(byte[] jpeg, ExifIfd ifd, int orientation)
            throws IOException {
        if (ifd.entryCount() == 0xffff) {
            throw new IOException("EXIF IFD has too many entries.");
        }
        int oldIfdBytes = 2 + ifd.entryCount() * 12 + 4;
        int newIfdBytes = oldIfdBytes + 12;
        int newSegmentBytes = ifd.segmentBytes() + newIfdBytes;
        if (newSegmentBytes > 0xffff) {
            throw new IOException("EXIF APP1 is too large to add Orientation.");
        }
        byte[] result = Arrays.copyOf(jpeg, jpeg.length + newIfdBytes);
        System.arraycopy(jpeg, ifd.exifEnd(), result, ifd.exifEnd() + newIfdBytes,
                jpeg.length - ifd.exifEnd());
        writeUnsignedShortBigEndian(result, ifd.segmentLengthOffset(), newSegmentBytes);
        int newIfdOffset = ifd.exifEnd();
        writeUnsignedInt(result, ifd.tiffOffset() + 4,
                (long) newIfdOffset - ifd.tiffOffset(), ifd.littleEndian());
        writeUnsignedShort(result, newIfdOffset, ifd.entryCount() + 1, ifd.littleEndian());
        int insertionIndex = 0;
        while (insertionIndex < ifd.entryCount()
                && readUnsignedShort(jpeg, ifd.ifdOffset() + 2 + insertionIndex * 12,
                        ifd.littleEndian()) < TAG_ORIENTATION) {
            insertionIndex++;
        }
        int entriesBefore = insertionIndex * 12;
        System.arraycopy(jpeg, ifd.ifdOffset() + 2, result, newIfdOffset + 2, entriesBefore);
        int orientationEntry = newIfdOffset + 2 + entriesBefore;
        writeUnsignedShort(result, orientationEntry, TAG_ORIENTATION, ifd.littleEndian());
        writeUnsignedShort(result, orientationEntry + 2, TYPE_SHORT, ifd.littleEndian());
        writeUnsignedInt(result, orientationEntry + 4, 1L, ifd.littleEndian());
        writeUnsignedShort(result, orientationEntry + 8, orientation, ifd.littleEndian());
        int entriesAfter = (ifd.entryCount() - insertionIndex) * 12;
        System.arraycopy(jpeg, ifd.ifdOffset() + 2 + entriesBefore, result,
                orientationEntry + 12, entriesAfter);
        System.arraycopy(jpeg, ifd.ifdOffset() + oldIfdBytes - 4, result,
                newIfdOffset + newIfdBytes - 4, 4);
        return result;
    }

    private record JpegMarker(int value, int nextOffset) {
        private boolean endsMetadata() {
            return value == MARKER_SOS || value == MARKER_EOI;
        }

        private boolean hasNoLength() {
            return value == 0x01 || value >= 0xd0 && value <= 0xd7;
        }
    }

    private record JpegSegment(int lengthOffset, int bytes, int payloadOffset, int end) {
        private boolean hasExifPrefix(byte[] jpeg) {
            return payloadOffset + EXIF_PREFIX.length <= end
                    && startsWith(jpeg, payloadOffset, EXIF_PREFIX);
        }
    }

    private record ExifIfd(
            int segmentLengthOffset, int segmentBytes, int tiffOffset, int exifEnd,
            int ifdOffset, int entryCount, boolean littleEndian) {}

    private static byte[] insertMinimalExif(byte[] jpeg, int orientation) {
        byte[] app1 = new byte[] {
                (byte) 0xff, (byte) MARKER_APP1, 0x00, 0x22,
                'E', 'x', 'i', 'f', 0x00, 0x00,
                'M', 'M', 0x00, 0x2a, 0x00, 0x00, 0x00, 0x08,
                0x00, 0x01,
                0x01, 0x12, 0x00, 0x03, 0x00, 0x00, 0x00, 0x01,
                0x00, 0x00, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x00
        };
        writeUnsignedShort(app1, 28, orientation, false);
        byte[] result = Arrays.copyOf(jpeg, jpeg.length + app1.length);
        System.arraycopy(jpeg, 2, result, 2 + app1.length, jpeg.length - 2);
        System.arraycopy(app1, 0, result, 2, app1.length);
        return result;
    }

    private static boolean startsWith(byte[] data, int offset, byte[] prefix) {
        if (offset < 0 || prefix.length > data.length - offset) return false;
        for (int index = 0; index < prefix.length; index++) {
            if (data[offset + index] != prefix[index]) return false;
        }
        return true;
    }

    private static int readUnsignedShortBigEndian(byte[] data, int offset) {
        return unsigned(data[offset]) << 8 | unsigned(data[offset + 1]);
    }

    private static void writeUnsignedShortBigEndian(byte[] data, int offset, int value) {
        data[offset] = (byte) (value >>> 8);
        data[offset + 1] = (byte) value;
    }

    private static int readUnsignedShort(byte[] data, int offset, boolean littleEndian) {
        return littleEndian
                ? unsigned(data[offset]) | unsigned(data[offset + 1]) << 8
                : unsigned(data[offset]) << 8 | unsigned(data[offset + 1]);
    }

    private static long readUnsignedInt(byte[] data, int offset, boolean littleEndian) {
        if (littleEndian) {
            return Integer.toUnsignedLong(unsigned(data[offset])
                    | unsigned(data[offset + 1]) << 8
                    | unsigned(data[offset + 2]) << 16
                    | unsigned(data[offset + 3]) << 24);
        }
        return Integer.toUnsignedLong(unsigned(data[offset]) << 24
                | unsigned(data[offset + 1]) << 16
                | unsigned(data[offset + 2]) << 8
                | unsigned(data[offset + 3]));
    }

    private static void writeUnsignedInt(
            byte[] data, int offset, long value, boolean littleEndian) {
        if (littleEndian) {
            data[offset] = (byte) value;
            data[offset + 1] = (byte) (value >>> 8);
            data[offset + 2] = (byte) (value >>> 16);
            data[offset + 3] = (byte) (value >>> 24);
        } else {
            data[offset] = (byte) (value >>> 24);
            data[offset + 1] = (byte) (value >>> 16);
            data[offset + 2] = (byte) (value >>> 8);
            data[offset + 3] = (byte) value;
        }
    }

    private static void writeUnsignedShort(
            byte[] data, int offset, int value, boolean littleEndian) {
        if (littleEndian) {
            data[offset] = (byte) value;
            data[offset + 1] = (byte) (value >>> 8);
        } else {
            data[offset] = (byte) (value >>> 8);
            data[offset + 1] = (byte) value;
        }
    }

    private static int unsigned(byte value) {
        return Byte.toUnsignedInt(value);
    }
}
