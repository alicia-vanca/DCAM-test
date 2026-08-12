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

    @Override public DcamMediaValidationResult validate(DcamFileType type, File file) {
        if (!file.isFile()) {
            return DcamMediaValidationResult.rejected(
                    "File does not exist or is not a regular file.");
        }
        if (file.length() <= 0L) {
            return DcamMediaValidationResult.rejected("File is empty.");
        }
        if (type == DcamFileType.IMAGE) return validateImage(file);
        if (type == DcamFileType.VIDEO || type == DcamFileType.IMP) {
            String structureIssue = mp4StructureIssue(file);
            return structureIssue == null
                    ? validateTrack(file, MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO, "video")
                    : DcamMediaValidationResult.rejected(structureIssue);
        }
        if (type == DcamFileType.AUDIO) {
            return validateTrack(file, MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO, "audio");
        }
        return DcamMediaValidationResult.rejected("Unsupported media type: " + type + ".");
    }

    @Override public DcamMediaValidationResult validate(
            DcamFileType type, DcamRandomAccessMedia media) {
        try {
            if (media.size() <= 0L) {
                return DcamMediaValidationResult.rejected("File is empty.");
            }
            if (type == DcamFileType.VIDEO || type == DcamFileType.IMP) {
                String structureIssue = mp4StructureIssue(media);
                return structureIssue == null
                        ? validateTrack(media,
                                MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO, "video")
                        : DcamMediaValidationResult.rejected(structureIssue);
            }
            if (type == DcamFileType.AUDIO) {
                return validateTrack(
                        media, MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO, "audio");
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

    private static DcamMediaValidationResult validateTrack(
            File file, int trackMetadataKey, String mediaKind) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(file.getAbsolutePath());
            return validateTrack(retriever, trackMetadataKey, mediaKind);
        } catch (RuntimeException invalid) {
            return metadataFailure(mediaKind, invalid);
        } finally {
            try { retriever.release(); } catch (Exception ignored) { }
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
            try { retriever.release(); } catch (Exception ignored) { }
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
                int requested = (int) Math.min((long) size, length - position);
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
        }
    }
}