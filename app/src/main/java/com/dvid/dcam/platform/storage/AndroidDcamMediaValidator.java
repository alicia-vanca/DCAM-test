package com.dvid.dcam.platform.storage;

import android.graphics.BitmapFactory;
import android.media.MediaDataSource;
import android.media.MediaMetadataRetriever;
import java.io.File;
import java.io.IOException;

import java.io.RandomAccessFile;
import java.nio.ByteBuffer;

/** Uses container checks and Android decoders to reject unsafe recovery candidates. */
final class AndroidDcamMediaValidator implements DcamMediaValidator {
    private static final int BOX_MOOV = 0x6d6f6f76;
    private static final String AUDIO_TRACK = "audio";

    @Override public DcamMediaValidationResult validate(DcamFileType type, File file) {
        if (!file.isFile()) {
            return DcamMediaValidationResult.rejected(
                    "File does not exist or is not a regular file.");
        }
        if (file.length() <= 0L) {
            return DcamMediaValidationResult.rejected("File is empty.");
        }
        if (type == DcamFileType.IMAGE) return validateImage(file);
        if (type.usesFragmentedMp4Container()) {
            String trackKind = type.isVideo() ? "video" : AUDIO_TRACK;
            int metadataKey = type.isVideo()
                    ? MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO
                    : MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO;
            String structureIssue = mp4StructureIssue(file);
            return structureIssue == null
                    ? validateTrack(file, metadataKey, trackKind)
                    : DcamMediaValidationResult.rejected(structureIssue);
        }
        if (type == DcamFileType.AUDIO) {
            return validateTrack(file, MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO, AUDIO_TRACK);
        }
        return DcamMediaValidationResult.rejected("Unsupported media type: " + type + ".");
    }

    @Override public DcamMediaValidationResult validate(
            DcamFileType type, DcamRandomAccessMedia media) {
        try {
            if (media.size() <= 0L) {
                return DcamMediaValidationResult.rejected("File is empty.");
            }
            if (type == DcamFileType.IMAGE) return validateImage(media);
            if (type.usesFragmentedMp4Container()) {
                String trackKind = type.isVideo() ? "video" : AUDIO_TRACK;
                int metadataKey = type.isVideo()
                        ? MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO
                        : MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO;
                String structureIssue = mp4StructureIssue(media);
                return structureIssue == null
                        ? validateTrack(media, metadataKey, trackKind)
                        : DcamMediaValidationResult.rejected(structureIssue);
            }
            if (type == DcamFileType.AUDIO) {
                return validateTrack(
                        media, MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO, AUDIO_TRACK);
            }
            return DcamMediaValidationResult.rejected(
                    "Unsupported logical media type: " + type + ".");
        } catch (IOException failure) {
            return DcamMediaValidationResult.rejected(
                    "Could not inspect logical media: " + message(failure) + ".");
        }
    }

    private static DcamMediaValidationResult validateImage(File file) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getAbsolutePath(), options);
        if (options.outWidth <= 0 || options.outHeight <= 0) {
            return DcamMediaValidationResult.rejected(
                    "Android image decoder found no valid image dimensions.");
        }
        return DcamMediaValidationResult.accepted(
                "Android image decoder found dimensions "
                        + options.outWidth + "x" + options.outHeight + ".");
    }

    private static DcamMediaValidationResult validateImage(DcamRandomAccessMedia media)
            throws IOException {
        media.position(0L);
        if (media.readUnsignedByte() != 0xff || media.readUnsignedByte() != 0xd8) {
            return DcamMediaValidationResult.rejected("Logical image has no JPEG SOI marker.");
        }
        LogicalJpegState state = new LogicalJpegState();
        while (hasNextJpegMarker(media, state)) {
            DcamMediaValidationResult result = validateJpegMarker(
                    media, state, nextJpegMarker(media, state));
            if (result != null) return result;
        }
        return DcamMediaValidationResult.rejected(
                "Logical JPEG ended before the EOI marker.");
    }

    private static boolean hasNextJpegMarker(DcamRandomAccessMedia media, LogicalJpegState state)
            throws IOException {
        return state.pendingMarker >= 0 || media.position() < media.size();
    }

    private static int nextJpegMarker(DcamRandomAccessMedia media, LogicalJpegState state)
            throws IOException {
        if (state.pendingMarker < 0) return readJpegMarker(media);
        int marker = state.pendingMarker;
        state.pendingMarker = -1;
        return marker;
    }

    private static DcamMediaValidationResult validateJpegMarker(
            DcamRandomAccessMedia media, LogicalJpegState state, int marker) throws IOException {
        if (marker == 0xd9) return jpegEndResult(state);
        if (marker == 0xda) return validateJpegScan(media, state);
        if (isJpegStandaloneMarker(marker)) return null;
        return validateJpegSegment(media, state, marker);
    }

    private static DcamMediaValidationResult jpegEndResult(LogicalJpegState state) {
        if (!state.hasCompleteImage()) {
            return DcamMediaValidationResult.rejected(
                    "Logical JPEG ended before complete image metadata and scan data.");
        }
        return DcamMediaValidationResult.accepted(
                "Logical JPEG reports complete dimensions "
                        + state.width + "x" + state.height + ".");
    }

    private static DcamMediaValidationResult validateJpegScan(
            DcamRandomAccessMedia media, LogicalJpegState state) throws IOException {
        int segmentBytes = readJpegSegmentLength(media);
        if (segmentBytes < 8) {
            return DcamMediaValidationResult.rejected("Logical JPEG scan header is truncated.");
        }
        int componentCount = media.readUnsignedByte();
        if (componentCount <= 0 || segmentBytes != 6 + componentCount * 2) {
            return DcamMediaValidationResult.rejected("Logical JPEG scan header length is invalid.");
        }
        media.position(media.position() + segmentBytes - 3L);
        state.scanStarted = true;
        state.pendingMarker = scanToNextJpegMarker(media);
        return null;
    }

    private static DcamMediaValidationResult validateJpegSegment(
            DcamRandomAccessMedia media, LogicalJpegState state, int marker) throws IOException {
        int segmentBytes = readJpegSegmentLength(media);
        if (isJpegStartOfFrame(marker)) return validateJpegStartOfFrame(media, state, segmentBytes);
        media.position(media.position() + segmentBytes - 2L);
        return null;
    }

    private static DcamMediaValidationResult validateJpegStartOfFrame(
            DcamRandomAccessMedia media, LogicalJpegState state, int segmentBytes) throws IOException {
        if (segmentBytes < 8) {
            return DcamMediaValidationResult.rejected(
                    "Logical JPEG start-of-frame segment is truncated.");
        }
        media.readUnsignedByte();
        state.height = media.readUnsignedByte() << 8 | media.readUnsignedByte();
        state.width = media.readUnsignedByte() << 8 | media.readUnsignedByte();
        int componentCount = media.readUnsignedByte();
        if (state.width <= 0 || state.height <= 0 || componentCount <= 0) {
            return DcamMediaValidationResult.rejected(
                    "Logical JPEG reports invalid image dimensions or components.");
        }
        if (segmentBytes != 8 + componentCount * 3) {
            return DcamMediaValidationResult.rejected(
                    "Logical JPEG start-of-frame length is invalid.");
        }
        media.position(media.position() + segmentBytes - 8L);
        return null;
    }

    private static boolean isJpegStandaloneMarker(int marker) {
        return marker == 0x01 || marker == 0xd8 || marker >= 0xd0 && marker <= 0xd7;
    }

    private static int readJpegMarker(DcamRandomAccessMedia media) throws IOException {
        int prefix;
        do { prefix = media.readUnsignedByte(); } while (prefix != 0xff);
        int marker;
        do { marker = media.readUnsignedByte(); } while (marker == 0xff);
        if (marker == 0x00) throw new IOException("JPEG stuffed byte appears outside scan data.");
        return marker;
    }

    private static int readJpegSegmentLength(DcamRandomAccessMedia media) throws IOException {
        int segmentBytes = media.readUnsignedByte() << 8 | media.readUnsignedByte();
        if (segmentBytes < 2 || segmentBytes - 2L > media.size() - media.position()) {
            throw new IOException("Logical JPEG segment length is invalid.");
        }
        return segmentBytes;
    }

    private static int scanToNextJpegMarker(DcamRandomAccessMedia media) throws IOException {
        while (media.position() < media.size()) {
            if (media.readUnsignedByte() == 0xff) {
                int marker;
                do { marker = media.readUnsignedByte(); } while (marker == 0xff);
                if (marker != 0x00 && (marker < 0xd0 || marker > 0xd7)) return marker;
            }
        }
        throw new IOException("Logical JPEG scan ended before the EOI marker.");
    }

    private static boolean isJpegStartOfFrame(int marker) {
        return marker >= 0xc0 && marker <= 0xcf
                && marker != 0xc4 && marker != 0xc8 && marker != 0xcc;
    }
    private static DcamMediaValidationResult validateTrack(
            File file, int trackMetadataKey, String mediaKind) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(file.getAbsolutePath());
            return validateTrack(retriever, trackMetadataKey, mediaKind);
        } catch (RuntimeException invalid) {
            return metadataFailure(mediaKind, invalid);
        } finally {
            try {
                retriever.release();
            } catch (Exception ignored) {
                // Release is best effort and must not hide the validation result.
            }
        }
    }

    private static DcamMediaValidationResult validateTrack(
            DcamRandomAccessMedia media, int trackMetadataKey, String mediaKind) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(new RandomAccessMediaDataSource(media));
            return validateTrack(retriever, trackMetadataKey, mediaKind);
        } catch (RuntimeException invalid) {
            return metadataFailure(mediaKind, invalid);
        } finally {
            try {
                retriever.release();
            } catch (Exception ignored) {
                // Release is best effort and must not hide the validation result.
            }
        }
    }

    private static DcamMediaValidationResult validateTrack(
            MediaMetadataRetriever retriever, int trackMetadataKey, String mediaKind) {
        String hasTrack = retriever.extractMetadata(trackMetadataKey);
        if (!"yes".equalsIgnoreCase(hasTrack)) {
            return DcamMediaValidationResult.rejected(
                    "Android metadata reports no " + mediaKind + " track.");
        }
        String duration = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_DURATION);
        if (duration == null) {
            return DcamMediaValidationResult.rejected(
                    "Android metadata has no duration for the " + mediaKind + " track.");
        }
        long durationMillis;
        try {
            durationMillis = Long.parseLong(duration);
        } catch (NumberFormatException invalidDuration) {
            return DcamMediaValidationResult.rejected(
                    "Android metadata returned invalid duration '" + duration + "'.");
        }
        if (durationMillis <= 0L) {
            return DcamMediaValidationResult.rejectedNonPositiveDuration(
                    mediaKind, durationMillis);
        }
        return DcamMediaValidationResult.accepted(
                "Android metadata found a " + mediaKind + " track with duration "
                        + durationMillis + " ms.");
    }

    private static DcamMediaValidationResult metadataFailure(
            String mediaKind, RuntimeException invalid) {
        return DcamMediaValidationResult.rejected(
                "Android could not read " + mediaKind + " metadata: "
                        + message(invalid) + ".");
    }

    private static String mp4StructureIssue(File file) {
        try (RandomAccessFile input = new RandomAccessFile(file, "r")) {
            long fileLength = input.length();
            long offset = 0L;
            while (offset + 8L <= fileLength) {
                input.seek(offset);
                long boxSize = Integer.toUnsignedLong(input.readInt());
                int boxType = input.readInt();
                long headerSize = 8L;
                if (boxSize == 1L) {
                    if (offset + 16L > fileLength) {
                        return truncatedExtendedSize(boxType);
                    }
                    boxSize = input.readLong();
                    headerSize = 16L;
                } else if (boxSize == 0L) {
                    boxSize = fileLength - offset;
                }
                if (boxType == BOX_MOOV) return null;
                if (boxSize < headerSize || boxSize > fileLength - offset) {
                    return invalidBoxSize(boxType, boxSize, fileLength);
                }
                offset += boxSize;
            }
            return missingMoov();
        } catch (IOException failure) {
            return "Could not inspect MP4 container: " + message(failure) + ".";
        }
    }

    private static String mp4StructureIssue(DcamRandomAccessMedia input) throws IOException {
        long fileLength = input.size();
        long offset = 0L;
        while (offset + 8L <= fileLength) {
            input.seek(offset);
            long boxSize = Integer.toUnsignedLong(input.readInt());
            int boxType = input.readInt();
            long headerSize = 8L;
            if (boxSize == 1L) {
                if (offset + 16L > fileLength) return truncatedExtendedSize(boxType);
                boxSize = input.readLong();
                headerSize = 16L;
            } else if (boxSize == 0L) {
                boxSize = fileLength - offset;
            }
            if (boxType == BOX_MOOV) return null;
            if (boxSize < headerSize || boxSize > fileLength - offset) {
                return invalidBoxSize(boxType, boxSize, fileLength);
            }
            offset += boxSize;
        }
        return missingMoov();
    }

    private static String truncatedExtendedSize(int boxType) {
        return "MP4 box '" + boxType(boxType)
                + "' has truncated extended-size metadata. Recording ended before "
                + "MP4 finalization completed.";
    }

    private static String invalidBoxSize(int boxType, long boxSize, long fileLength) {
        return "MP4 box '" + boxType(boxType) + "' declares invalid size "
                + boxSize + " for a " + fileLength + "-byte file. Recording ended "
                + "before MP4 finalization completed.";
    }

    private static String missingMoov() {
        return "MP4 has no top-level 'moov' metadata box. Recording ended before "
                + "MP4 finalization completed.";
    }

    private static String boxType(int value) {
        return new String(new char[] {
                (char) ((value >>> 24) & 0xff),
                (char) ((value >>> 16) & 0xff),
                (char) ((value >>> 8) & 0xff),
                (char) (value & 0xff)});
    }

    private static String message(Throwable failure) {
        String detail = failure.getMessage();
        return detail == null || detail.isBlank()
                ? failure.getClass().getSimpleName() : detail;
    }

    private static final class RandomAccessMediaDataSource extends MediaDataSource {
        private final DcamRandomAccessMedia media;

        private RandomAccessMediaDataSource(DcamRandomAccessMedia media) {
            this.media = media;
        }

        @Override public int readAt(long position, byte[] buffer, int offset, int size)
                throws IOException {
            if (position < 0L || offset < 0 || size < 0 || offset > buffer.length - size) {
                throw new IOException("Invalid logical media read range.");
            }
            synchronized (media) {
                long length = media.size();
                if (position >= length) return -1;
                int requested = (int) Math.min(size, length - position);
                ByteBuffer target = ByteBuffer.wrap(buffer, offset, requested);
                media.position(position);
                int total = 0;
                while (target.hasRemaining()) {
                    int read = media.read(target);
                    if (read < 0) break;
                    if (read == 0) throw new IOException("Logical media read stopped.");
                    total += read;
                }
                return total == 0 ? -1 : total;
            }
        }

        @Override public long getSize() throws IOException {
            synchronized (media) {
                return media.size();
            }
        }

        @Override public void close() {
            // The caller owns the supplied logical media.
        }
    }

    private static final class LogicalJpegState {
        private int width;
        private int height;
        private boolean scanStarted;
        private int pendingMarker = -1;

        private boolean hasCompleteImage() {
            return width > 0 && height > 0 && scanStarted;
        }
    }
}
