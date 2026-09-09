package com.dvid.dcam.platform.storage;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.StandardOpenOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import com.dvid.dcam.core.logging.domain.LogCategory;
import com.dvid.dcam.core.logging.application.port.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class DcamInterruptedMp4FinalizerTest {
    private static final int HANDLER_AUDIO = 0x736f756e;
    private static final int HANDLER_VIDEO = 0x76696465;

    @TempDir Path root;

    @Test
    void trimsIncompleteTrailingFragmentAfterCompleteFragment() throws Exception {
        byte[] complete = fragmentedVideoWithZeroDuration();
        byte[] incompleteFragment = bytes(box("moof", new byte[] {5, 6}),
                declaredBox("mdat", 40, new byte[] {7, 8, 9}));
        byte[] recoverable = bytes(complete, incompleteFragment);
        Path file = root.resolve("recording.mp4");
        Files.write(file, recoverable);
        long expectedBytes = complete.length;

        DcamInterruptedMp4Finalizer.Result result =
                new DcamInterruptedMp4Finalizer().finalizeInterrupted(file.toFile());

        assertTrue(result.finalized());
        assertEquals(expectedBytes, Files.size(file));
        assertEquals(1, result.completeFragments());
        assertEquals(recoverable.length - expectedBytes,
                result.originalBytes() - result.retainedBytes());
    }

    @Test
    void cleanFinalizationPatchesKnownDurationInPlace() throws Exception {
        Path file = root.resolve("clean.mp4");
        byte[] original = fragmentedVideoWithZeroDuration();
        Files.write(file, original);

        long durationMillis = new DcamInterruptedMp4Finalizer().finalizeCleanTimed(
                file.toFile(), 2_000_000L).durationMillis();

        assertEquals(2_000L, durationMillis);
        assertEquals(20_000L, movieDuration(Files.readAllBytes(file)));
        assertEquals(original.length, Files.size(file));
        try (var files = Files.list(root)) {
            assertTrue(files.noneMatch(path -> path.getFileName().toString().contains(".repair-")));
        }
    }

    @Test
    void muxerOutputRecordsSeekIndexAndPatchesFragmentOffsets() throws Exception {
        Path file = root.resolve("writer-time-index.mp4");
        byte[] header = bytes(box("ftyp", new byte[] {0}), fragmentedAvMoov());
        byte[] moof = fragmentMoof(
                0L, new int[] {45_000, 45_000}, new int[] {24_000, 24_000});
        byte[] mdat = box("mdat", new byte[] {1, 2, 3, 4});
        long moofOffset;

        try (FileChannel fileChannel = FileChannel.open(file,
                     StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE);
                DcamFragmentedMp4Layout.OutputChannel muxerOutput =
                     DcamFragmentedMp4Layout.offsetAwareChannel(fileChannel)) {
            muxerOutput.write(ByteBuffer.wrap(header));
            muxerOutput.reserveSeekIndex();
            moofOffset = fileChannel.position();
            muxerOutput.write(ByteBuffer.wrap(moof));
            muxerOutput.write(ByteBuffer.wrap(mdat));
        }

        DcamInterruptedMp4Finalizer.CleanResult result =
                new DcamInterruptedMp4Finalizer().finalizeCleanTimed(
                        file.toFile(), 1_000_000L);

        byte[] written = Files.readAllBytes(file);
        assertTrue(result.writerSeekIndexUsed());
        int moov = childBox(written, 0, written.length, "moov");
        int sidx = childBox(written, boxEnd(written, moov), written.length, "sidx");
        assertEquals(1, unsignedShort(written, contentOffset(sidx) + 30));
        assertEquals(moofOffset, fragmentBaseOffset(written, (int) moofOffset));
        assertEquals(moofOffset, fragmentBaseOffset(written, (int) moofOffset, 1));
    }

    @Test
    void cleanAudioFinalizationUsesWriterSeekIndex() throws Exception {
        Path file = root.resolve("writer-time-audio-index.m4a");
        byte[] header = bytes(box("ftyp", new byte[] {0}), fragmentedAudioMoov());
        byte[] moof = fragmentMoof(0L, 24_000, 24_000);
        byte[] mdat = box("mdat", new byte[] {1, 2, 3, 4});
        long moofOffset;

        try (FileChannel fileChannel = FileChannel.open(file,
                     StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE);
                DcamFragmentedMp4Layout.OutputChannel muxerOutput =
                     DcamFragmentedMp4Layout.offsetAwareAudioChannel(fileChannel)) {
            muxerOutput.write(ByteBuffer.wrap(header));
            muxerOutput.reserveSeekIndex();
            moofOffset = fileChannel.position();
            muxerOutput.write(ByteBuffer.wrap(moof));
            muxerOutput.write(ByteBuffer.wrap(mdat));
        }

        DcamInterruptedMp4Finalizer.CleanResult result =
                new DcamInterruptedMp4Finalizer().finalizeCleanTimed(
                        file.toFile(), 1_000_000L);

        byte[] written = Files.readAllBytes(file);
        assertTrue(result.writerSeekIndexUsed());
        assertEquals(10_000L, movieDuration(written));
        int moov = childBox(written, 0, written.length, "moov");
        int sidx = childBox(written, boxEnd(written, moov), written.length, "sidx");
        assertEquals(48_000L, unsignedInt(written, contentOffset(sidx) + 8));
        assertEquals(moofOffset, fragmentBaseOffset(written, (int) moofOffset));
    }

    @Test
    void cleanTenHourAudioFinalizationProducesBoundedSeekIndex() throws Exception {
        Path file = root.resolve("writer-time-ten-hour-audio-index.m4a");
        byte[] header = bytes(box("ftyp", new byte[] {0}), fragmentedAudioMoov());
        byte[] moof = fragmentMoof(0L, 24_000);
        byte[] mdat = box("mdat", new byte[] {1});

        try (FileChannel fileChannel = FileChannel.open(file,
                     StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE);
                DcamFragmentedMp4Layout.OutputChannel muxerOutput =
                     DcamFragmentedMp4Layout.offsetAwareAudioChannel(fileChannel)) {
            muxerOutput.write(ByteBuffer.wrap(header));
            muxerOutput.reserveSeekIndex();
            for (int fragment = 0; fragment < 72_000; fragment++) {
                muxerOutput.write(ByteBuffer.wrap(moof));
                muxerOutput.write(ByteBuffer.wrap(mdat));
            }
        }

        DcamInterruptedMp4Finalizer.CleanResult result;
        long finalizationReadBytes;
        long finalizationWrittenBytes;
        try (CountingMedia media = new CountingMedia(
                     PlainDcamRandomAccessMedia.open(file.toFile()))) {
            result = new DcamInterruptedMp4Finalizer().finalizeCleanTimed(
                    media, 36_000_000_000L);
            finalizationReadBytes = media.readBytes;
            finalizationWrittenBytes = media.writtenBytes;
        }

        byte[] written = Files.readAllBytes(file);
        assertTrue(result.writerSeekIndexUsed());
        assertTrue(finalizationReadBytes < 32L * 1024L);
        assertTrue(finalizationWrittenBytes < 32L * 1024L);
        assertEquals(360_000_000L, movieDuration(written));
        int moov = childBox(written, 0, written.length, "moov");
        int sidx = childBox(written, boxEnd(written, moov), written.length, "sidx");
        assertEquals(36_000, unsignedShort(written, contentOffset(sidx) + 30));
    }

    @Test
    void interruptedTenHourAudioRecoversDurationAndSeekIndex() throws Exception {
        Path file = root.resolve("interrupted-ten-hour-audio-index.m4a");
        byte[] header = bytes(box("ftyp", new byte[] {0}), fragmentedAudioMoov());
        byte[] moof = fragmentMoof(0L, 24_000);
        byte[] mdat = box("mdat", new byte[] {1});
        FileChannel fileChannel = FileChannel.open(file,
                StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE);
        try {
            DcamFragmentedMp4Layout.OutputChannel muxerOutput =
                    DcamFragmentedMp4Layout.offsetAwareChannel(fileChannel);
            muxerOutput.write(ByteBuffer.wrap(header));
            muxerOutput.reserveSeekIndex();
            for (int fragment = 0; fragment < 72_000; fragment++) {
                muxerOutput.write(ByteBuffer.wrap(moof));
                muxerOutput.write(ByteBuffer.wrap(mdat));
            }
            fileChannel.force(true);
        } finally {
            fileChannel.close();
        }

        DcamInterruptedMp4Finalizer.Result result =
                new DcamInterruptedMp4Finalizer().finalizeInterrupted(file.toFile());

        byte[] recovered = Files.readAllBytes(file);
        assertTrue(result.finalized());
        assertEquals(72_000, result.completeFragments());
        assertEquals(36_000_000L, result.durationMillis());
        assertEquals(360_000_000L, movieDuration(recovered));
        int moov = childBox(recovered, 0, recovered.length, "moov");
        int sidx = childBox(recovered, boxEnd(recovered, moov), recovered.length, "sidx");
        assertEquals(36_000, unsignedShort(recovered, contentOffset(sidx) + 30));
    }

    @Test
    void writerSeekIndexHandlesSparseTrackFragments() throws Exception {
        Path file = root.resolve("writer-time-sparse-index.mp4");
        byte[] header = bytes(box("ftyp", new byte[] {0}), fragmentedAvMoov());
        byte[][] moofs = {
                box("moof", fragmentTraf(0L, 1, 45_000, 45_000)),
                fragmentMoof(
                        0L, new int[] {45_000, 45_000}, new int[] {24_000, 24_000}),
                box("moof", fragmentTraf(0L, 2, 24_000, 24_000))
        };

        try (FileChannel fileChannel = FileChannel.open(file,
                     StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE);
                DcamFragmentedMp4Layout.OutputChannel muxerOutput =
                     DcamFragmentedMp4Layout.offsetAwareChannel(fileChannel)) {
            muxerOutput.write(ByteBuffer.wrap(header));
            muxerOutput.reserveSeekIndex();
            for (int fragment = 0; fragment < moofs.length; fragment++) {
                muxerOutput.write(ByteBuffer.wrap(moofs[fragment]));
                muxerOutput.write(ByteBuffer.wrap(box("mdat", new byte[] {(byte) fragment})));
            }
        }

        DcamInterruptedMp4Finalizer.CleanResult result =
                new DcamInterruptedMp4Finalizer().finalizeCleanTimed(
                        file.toFile(), 2_000_000L);

        byte[] written = Files.readAllBytes(file);
        assertTrue(result.writerSeekIndexUsed());
        int moov = childBox(written, 0, written.length, "moov");
        int videoSidx = childBox(written, boxEnd(written, moov), written.length, "sidx");
        int audioSidx = childBox(written, boxEnd(written, videoSidx), written.length, "sidx");
        assertEquals(2, unsignedShort(written, contentOffset(videoSidx) + 30));
        assertEquals(2, unsignedShort(written, contentOffset(audioSidx) + 30));
    }

    @Test
    void writerSeekIndexIncludesGpsRouteBoxes() throws Exception {
        Path file = root.resolve("writer-time-gps-route-index.mp4");
        byte[] header = bytes(box("ftyp", new byte[] {0}), fragmentedMoov());
        byte[] firstMoof = fragmentMoof(0L, 45_000);
        byte[] firstMdat = box("mdat", new byte[] {1});
        byte[] secondMoof = fragmentMoof(0L, 45_000);
        byte[] secondMdat = box("mdat", new byte[] {2});

        try (FileChannel fileChannel = FileChannel.open(file,
                     StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE);
                DcamFragmentedMp4Layout.OutputChannel muxerOutput =
                     DcamFragmentedMp4Layout.offsetAwareChannel(fileChannel)) {
            muxerOutput.write(ByteBuffer.wrap(header));
            muxerOutput.reserveSeekIndex();
            muxerOutput.queueGpsRoutePoint(0L, 21.034918, 105.767322);
            muxerOutput.write(ByteBuffer.wrap(firstMoof));
            muxerOutput.write(ByteBuffer.wrap(firstMdat));
            muxerOutput.queueGpsRoutePoint(1_000_000L, 21.034919, 105.767323);
            muxerOutput.write(ByteBuffer.wrap(secondMoof));
            muxerOutput.write(ByteBuffer.wrap(secondMdat));
        }

        DcamInterruptedMp4Finalizer.CleanResult result =
                new DcamInterruptedMp4Finalizer().finalizeCleanTimed(
                        file.toFile(), 2_000_000L);

        byte[] written = Files.readAllBytes(file);
        assertTrue(result.writerSeekIndexUsed());
        int moov = childBox(written, 0, written.length, "moov");
        int sidx = childBox(written, boxEnd(written, moov), written.length, "sidx");
        assertEquals(2, unsignedShort(written, contentOffset(sidx) + 30));
        assertEquals(firstMoof.length + firstMdat.length
                        + DcamFragmentedMp4Layout.GPS_ROUTE_BOX_BYTES,
                sidxReferenceSize(written, sidx, 0));
        assertEquals(secondMoof.length + secondMdat.length
                        + DcamFragmentedMp4Layout.GPS_ROUTE_BOX_BYTES,
                sidxReferenceSize(written, sidx, 1));
        int firstWrittenMoof = childBox(
                written, boxEnd(written, sidx), written.length, "moof");
        int firstWrittenMdat = childBox(
                written, boxEnd(written, firstWrittenMoof), written.length, "mdat");
        int firstUuid = childBox(
                written, boxEnd(written, firstWrittenMdat), written.length, "uuid");
        assertGpsRoutePoint(written, firstUuid, 0L, 21.034918, 105.767322);
        int secondWrittenMoof = childBox(
                written, boxEnd(written, firstUuid), written.length, "moof");
        int secondWrittenMdat = childBox(
                written, boxEnd(written, secondWrittenMoof), written.length, "mdat");
        int secondUuid = childBox(
                written, boxEnd(written, secondWrittenMdat), written.length, "uuid");
        assertGpsRoutePoint(written, secondUuid,
                1_000_000L, 21.034919, 105.767323);
    }

    @Test
    void interruptedFinalizationPreservesGpsRouteAndTrimsPartialFinalBox()
            throws Exception {
        byte[] firstMoof = fragmentMoof(0L, 45_000);
        byte[] firstMdat = box("mdat", new byte[] {1});
        byte[] firstRoute = gpsRouteBox(0L, 21.034918, 105.767322);
        byte[] secondMoof = fragmentMoof(0L, 45_000);
        byte[] secondMdat = box("mdat", new byte[] {2});
        byte[] partialRoute = Arrays.copyOf(
                gpsRouteBox(1_000_000L, 21.034919, 105.767323), 30);
        byte[] recoverable = bytes(
                box("ftyp", new byte[] {0}), fragmentedMoov(), seekIndexReserve(),
                firstMoof, firstMdat, firstRoute, secondMoof, secondMdat, partialRoute);
        Path file = root.resolve("interrupted-gps-route.mp4");
        Files.write(file, recoverable);
        long expectedBytes = recoverable.length - partialRoute.length;

        DcamInterruptedMp4Finalizer.Result result =
                new DcamInterruptedMp4Finalizer().finalizeInterrupted(file.toFile());

        byte[] written = Files.readAllBytes(file);
        assertTrue(result.finalized());
        assertEquals(2, result.completeFragments());
        assertEquals(expectedBytes, written.length);
        int moov = childBox(written, 0, written.length, "moov");
        int sidx = childBox(written, boxEnd(written, moov), written.length, "sidx");
        assertEquals(firstMoof.length + firstMdat.length + firstRoute.length,
                sidxReferenceSize(written, sidx, 0));
        assertEquals(secondMoof.length + secondMdat.length,
                sidxReferenceSize(written, sidx, 1));
        int firstWrittenMoof = childBox(
                written, boxEnd(written, sidx), written.length, "moof");
        int firstWrittenMdat = childBox(
                written, boxEnd(written, firstWrittenMoof), written.length, "mdat");
        int firstUuid = childBox(
                written, boxEnd(written, firstWrittenMdat), written.length, "uuid");
        assertGpsRoutePoint(written, firstUuid, 0L, 21.034918, 105.767322);
        int secondWrittenMoof = childBox(
                written, boxEnd(written, firstUuid), written.length, "moof");
        int secondWrittenMdat = childBox(
                written, boxEnd(written, secondWrittenMoof), written.length, "mdat");
        assertEquals(written.length, boxEnd(written, secondWrittenMdat));
    }

    @Test
    void interruptedAudioOnlyM4aPreservesGpsRouteAndTrimsCrashTail() throws Exception {
        byte[] header = bytes(
                box("ftyp", new byte[] {0}), fragmentedAudioMoov(), seekIndexReserve());
        byte[] firstMoof = fragmentMoof(0L, 1_024);
        byte[] firstMdat = box("mdat", new byte[] {1});
        byte[] firstRoute = gpsRouteBox(0L, 21.034918, 105.767322);
        byte[] secondMoof = fragmentMoof(0L, 1_024);
        byte[] secondMdat = box("mdat", new byte[] {2});
        byte[] secondRoute = gpsRouteBox(21_333L, 21.034919, 105.767323);
        byte[] crashTail = bytes(
                fragmentMoof(0L, 1_024), declaredBox("mdat", 40, new byte[] {3}));
        byte[] recoverable = bytes(
                header, firstMoof, firstMdat, firstRoute,
                secondMoof, secondMdat, secondRoute, crashTail);
        Path file = root.resolve("interrupted-audio-route.m4a");
        Files.write(file, recoverable);
        long expectedBytes = recoverable.length - crashTail.length;

        DcamInterruptedMp4Finalizer.Result result =
                new DcamInterruptedMp4Finalizer().finalizeInterrupted(file.toFile());

        byte[] written = Files.readAllBytes(file);
        assertTrue(result.finalized());
        assertEquals(2, result.completeFragments());
        assertTrue(result.durationMillis() > 0L);
        assertEquals(expectedBytes, written.length);
        int moov = childBox(written, 0, written.length, "moov");
        int sidx = childBox(written, boxEnd(written, moov), written.length, "sidx");
        assertEquals(1, unsignedShort(written, contentOffset(sidx) + 30));
        assertEquals(firstMoof.length + firstMdat.length + firstRoute.length
                        + secondMoof.length + secondMdat.length + secondRoute.length,
                sidxReferenceSize(written, sidx, 0));
        assertEquals(2_048L, sidxReferenceDuration(written, sidx, 0));
        int firstWrittenMoof = childBox(
                written, boxEnd(written, sidx), written.length, "moof");
        int firstWrittenMdat = childBox(
                written, boxEnd(written, firstWrittenMoof), written.length, "mdat");
        int firstUuid = childBox(
                written, boxEnd(written, firstWrittenMdat), written.length, "uuid");
        assertGpsRoutePoint(written, firstUuid, 0L, 21.034918, 105.767322);
        int secondWrittenMoof = childBox(
                written, boxEnd(written, firstUuid), written.length, "moof");
        int secondWrittenMdat = childBox(
                written, boxEnd(written, secondWrittenMoof), written.length, "mdat");
        int secondUuid = childBox(
                written, boxEnd(written, secondWrittenMdat), written.length, "uuid");
        assertGpsRoutePoint(written, secondUuid,
                21_333L, 21.034919, 105.767323);
        assertEquals(written.length, boxEnd(written, secondUuid));
    }

    @Test
    void writerSeekIndexUsesTfhdDefaultSampleDuration() throws Exception {
        Path file = root.resolve("writer-time-default-duration-index.mp4");
        byte[] header = bytes(box("ftyp", new byte[] {0}), fragmentedMoov());
        byte[] moof = box("moof", fragmentTrafWithDefaultDuration(0L, 1, 3_000, 2));

        try (FileChannel fileChannel = FileChannel.open(file,
                     StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE);
                DcamFragmentedMp4Layout.OutputChannel muxerOutput =
                     DcamFragmentedMp4Layout.offsetAwareChannel(fileChannel)) {
            muxerOutput.write(ByteBuffer.wrap(header));
            muxerOutput.reserveSeekIndex();
            muxerOutput.write(ByteBuffer.wrap(moof));
            muxerOutput.write(ByteBuffer.wrap(box("mdat", new byte[] {1})));
        }

        DcamInterruptedMp4Finalizer.CleanResult result =
                new DcamInterruptedMp4Finalizer().finalizeCleanTimed(
                        file.toFile(), 66_667L);

        byte[] written = Files.readAllBytes(file);
        assertTrue(result.writerSeekIndexUsed());
        int moov = childBox(written, 0, written.length, "moov");
        int sidx = childBox(written, boxEnd(written, moov), written.length, "sidx");
        assertEquals(6_000L, sidxReferenceDuration(written, sidx, 0));
    }

    @Test
    void segmentedOutputCheckpointsOnlyAfterCompleteSplitMdatAndStaysOpen()
            throws Exception {
        String password = "recording-pass";
        File encrypted = root.resolve("split-mdat_enc.mp4").toFile();
        byte[] header = bytes(box("ftyp", new byte[] {0}), fragmentedAvMoov());
        byte[] moof = fragmentMoof(
                0L, new int[] {45_000, 45_000}, new int[] {24_000, 24_000});
        byte[] mdat = box("mdat", new byte[] {1, 2, 3, 4});

        try (DcamRecordingOutput output =
                     DcamRecordingOutput.openSegmentedAesGcm(encrypted, password)) {
            DcamFragmentedMp4Layout.OutputChannel muxerOutput =
                    DcamFragmentedMp4Layout.offsetAwareChannel(output);
            muxerOutput.write(ByteBuffer.wrap(header));
            muxerOutput.reserveSeekIndex();
            muxerOutput.queueGpsRoutePoint(0L, 21.034918, 105.767322);
            muxerOutput.write(ByteBuffer.wrap(moof));
            muxerOutput.write(ByteBuffer.wrap(Arrays.copyOfRange(mdat, 0, 10)));
            assertEquals(0, authenticatedRecordCount(
                    encrypted, password, DcamSegmentedGcmFormat.TYPE_CHECKPOINT));

            muxerOutput.write(ByteBuffer.wrap(Arrays.copyOfRange(mdat, 10, mdat.length)));
            assertEquals(1, authenticatedRecordCount(
                    encrypted, password, DcamSegmentedGcmFormat.TYPE_CHECKPOINT));
            muxerOutput.close();
            assertTrue(output.isOpen());

            DcamInterruptedMp4Finalizer.CleanResult result =
                    new DcamInterruptedMp4Finalizer().finalizeCleanTimed(
                            output.media(), 1_000_000L);
            assertTrue(result.writerSeekIndexUsed());
            output.finish();
        }

        assertEquals(1, authenticatedRecordCount(
                encrypted, password, DcamSegmentedGcmFormat.TYPE_FINAL));
        try (SegmentedAesGcmMediaStore decrypted =
                     SegmentedAesGcmMediaStore.openPublished(encrypted, password)) {
            byte[] published = readAll(decrypted);
            assertEquals(10_000L, movieDuration(published));
            int moov = childBox(published, 0, published.length, "moov");
            int sidx = childBox(published, boxEnd(published, moov), published.length, "sidx");
            int writtenMoof = childBox(
                    published, boxEnd(published, sidx), published.length, "moof");
            int writtenMdat = childBox(
                    published, boxEnd(published, writtenMoof), published.length, "mdat");
            int uuid = childBox(
                    published, boxEnd(published, writtenMdat), published.length, "uuid");
            assertGpsRoutePoint(published, uuid, 0L, 21.034918, 105.767322);
        }
    }

    @Test
    void streamedAudioIndexFinalizesEncryptedAndDecryptsNormally() throws Exception {
        String password = "audio-recording-pass";
        File encrypted = root.resolve("streamed-audio-index_enc.m4a").toFile();
        byte[] header = bytes(box("ftyp", new byte[] {0}), fragmentedAudioMoov());
        byte[] moof = fragmentMoof(0L, 24_000);
        byte[] mdat = box("mdat", new byte[] {1, 2, 3, 4});

        try (DcamRecordingOutput output = DcamRecordingOutput.openSegmentedAesGcm(
                     encrypted, password, SegmentedAesGcmMediaStore.AUDIO_RECORDING_BLOCK_BYTES)) {
            DcamFragmentedMp4Layout.OutputChannel muxerOutput =
                    DcamFragmentedMp4Layout.offsetAwareAudioChannel(output);
            muxerOutput.write(ByteBuffer.wrap(header));
            muxerOutput.reserveSeekIndex();
            for (int fragment = 0; fragment < 8; fragment++) {
                muxerOutput.write(ByteBuffer.wrap(moof));
                muxerOutput.write(ByteBuffer.wrap(mdat));
            }
            muxerOutput.close();

            DcamInterruptedMp4Finalizer.CleanResult result =
                    new DcamInterruptedMp4Finalizer().finalizeCleanTimed(
                            output.media(), 4_000_000L);
            assertTrue(result.writerSeekIndexUsed());
            output.finish();
        }

        assertEquals(1, authenticatedRecordCount(
                encrypted, password, DcamSegmentedGcmFormat.TYPE_FINAL));
        try (SegmentedAesGcmMediaStore decrypted =
                     SegmentedAesGcmMediaStore.openPublished(encrypted, password)) {
            byte[] published = readAll(decrypted);
            assertEquals(40_000L, movieDuration(published));
            int moov = childBox(published, 0, published.length, "moov");
            int sidx = childBox(published, boxEnd(published, moov), published.length, "sidx");
            assertEquals(4, unsignedShort(published, contentOffset(sidx) + 30));
            for (int reference = 0; reference < 4; reference++) {
                assertEquals(48_000L, sidxReferenceDuration(published, sidx, reference));
            }
        }
    }

    @Test
    void segmentedCleanFinalizationRollsBackAllLogicalPatchesOnFailure() throws Exception {
        String password = "recording-pass";
        File encrypted = root.resolve("rollback_enc.mp4").toFile();
        byte[] original = fragmentedVideoWithSeekReserve();

        try (DcamRecordingOutput output =
                     DcamRecordingOutput.openSegmentedAesGcm(encrypted, password)) {
            writeFully(output, ByteBuffer.wrap(original));
            output.checkpoint();

            IOException failure = assertThrows(IOException.class,
                    () -> new DcamInterruptedMp4Finalizer().finalizeCleanTimed(
                            output.media(), 2_000_000L));

            assertTrue(failure.getMessage().contains("writer metadata"));
            assertArrayEquals(original, readAll(output.media()));
        }
        try (SegmentedAesGcmMediaStore recovered =
                     SegmentedAesGcmMediaStore.openForRecovery(encrypted, password)) {
            assertArrayEquals(original, readAll(recovered));
        }
    }
    @Test
    void cleanFinalizationRejectsMissingWriterIndexWithoutScanning() throws Exception {
        Path file = root.resolve("missing-writer-index.mp4");
        Files.write(file, fragmentedVideoWithSeekReserve());

        IOException failure = assertThrows(IOException.class,
                () -> new DcamInterruptedMp4Finalizer().finalizeCleanTimed(
                        file.toFile(), 2_000_000L));

        assertTrue(failure.getMessage().contains("writer metadata"));
        assertTrue(Files.isRegularFile(file));
    }

    @Test
    void interruptedFinalizationWritesSeekIndexAndRepairsFragmentOffsets() throws Exception {
        Path file = root.resolve("seekable.mp4");
        Files.write(file, fragmentedVideoWithSeekReserve());

        DcamInterruptedMp4Finalizer.Result result =
                new DcamInterruptedMp4Finalizer().finalizeInterrupted(file.toFile());

        byte[] finalized = Files.readAllBytes(file);
        assertTrue(result.finalized());
        int moov = childBox(finalized, 0, finalized.length, "moov");
        int sidx = childBox(finalized, boxEnd(finalized, moov), finalized.length, "sidx");
        assertEquals(90_000L, unsignedInt(finalized, contentOffset(sidx) + 8));
        assertEquals(2, unsignedShort(finalized, contentOffset(sidx) + 30));
        int firstMoof = childBox(finalized, boxEnd(finalized, sidx), finalized.length, "moof");
        assertEquals(firstMoof - boxEnd(finalized, sidx),
                longValue(finalized, contentOffset(sidx) + 20));
        assertEquals(firstMoof, fragmentBaseOffset(finalized, firstMoof));
        int firstMdat = childBox(
                finalized, boxEnd(finalized, firstMoof), finalized.length, "mdat");
        int secondMoof = childBox(
                finalized, boxEnd(finalized, firstMdat), finalized.length, "moof");
        assertEquals(secondMoof, fragmentBaseOffset(finalized, secondMoof));
        assertEquals(DcamFragmentedMp4Layout.SEEK_INDEX_MAGIC,
                longValue(finalized, boxEnd(finalized, moov)
                        + DcamFragmentedMp4Layout.SEEK_INDEX_RESERVE_BYTES - 8));
    }

    @Test
    void interruptedFinalizationWritesSeekIndexForEveryTimedTrack() throws Exception {
        Path file = root.resolve("seekable-av.mp4");
        Files.write(file, fragmentedAvWithSeekReserve());

        assertTrue(new DcamInterruptedMp4Finalizer()
                .finalizeInterrupted(file.toFile()).finalized());

        byte[] finalized = Files.readAllBytes(file);
        int moov = childBox(finalized, 0, finalized.length, "moov");
        int videoSidx = childBox(finalized, boxEnd(finalized, moov), finalized.length, "sidx");
        int audioSidx = childBox(
                finalized, boxEnd(finalized, videoSidx), finalized.length, "sidx");
        int firstMoof = childBox(
                finalized, boxEnd(finalized, audioSidx), finalized.length, "moof");
        assertEquals(1L, unsignedInt(finalized, contentOffset(videoSidx) + 4));
        assertEquals(90_000L, unsignedInt(finalized, contentOffset(videoSidx) + 8));
        assertEquals(90_000L, sidxReferenceDuration(finalized, videoSidx, 0));
        assertEquals(firstMoof - boxEnd(finalized, videoSidx),
                longValue(finalized, contentOffset(videoSidx) + 20));
        assertEquals(2L, unsignedInt(finalized, contentOffset(audioSidx) + 4));
        assertEquals(48_000L, unsignedInt(finalized, contentOffset(audioSidx) + 8));
        assertEquals(48_000L, sidxReferenceDuration(finalized, audioSidx, 0));
        assertEquals(firstMoof - boxEnd(finalized, audioSidx),
                longValue(finalized, contentOffset(audioSidx) + 20));
    }

    @Test
    void interruptedFinalizationIndexesTwoSampleFinalFragment() throws Exception {
        Path file = root.resolve("seekable-two-sample-tail.mp4");
        Files.write(file, fragmentedVideoWithSeekReserve(
                new int[] {3_000, 3_000, 3_000}, new int[] {3_000, 0}));

        assertTrue(new DcamInterruptedMp4Finalizer()
                .finalizeInterrupted(file.toFile()).finalized());

        byte[] finalized = Files.readAllBytes(file);
        int moov = childBox(finalized, 0, finalized.length, "moov");
        int sidx = childBox(finalized, boxEnd(finalized, moov), finalized.length, "sidx");
        assertEquals(9_000L, sidxReferenceDuration(finalized, sidx, 0));
        assertEquals(6_000L, sidxReferenceDuration(finalized, sidx, 1));
        assertEquals(15_000L, sidxReferenceDuration(finalized, sidx, 0)
                + sidxReferenceDuration(finalized, sidx, 1));
    }

    @Test
    void interruptedFinalizationDoesNotExpandInternalZeroDuration() throws Exception {
        Path file = root.resolve("seekable-internal-zero.mp4");
        Files.write(file, fragmentedVideoWithSeekReserve(
                new int[] {3_000, 3_000, 3_000}, new int[] {3_000, 0, 3_000}));

        assertTrue(new DcamInterruptedMp4Finalizer()
                .finalizeInterrupted(file.toFile()).finalized());

        byte[] finalized = Files.readAllBytes(file);
        int moov = childBox(finalized, 0, finalized.length, "moov");
        int sidx = childBox(finalized, boxEnd(finalized, moov), finalized.length, "sidx");
        assertEquals(6_000L, sidxReferenceDuration(finalized, sidx, 1));
    }
    @Test
    void interruptedFinalizationIndexesOneSampleFinalFragment() throws Exception {
        Path file = root.resolve("seekable-one-sample-tail.mp4");
        Files.write(file, fragmentedVideoWithSeekReserve(
                new int[] {3_000, 3_000, 3_000}, new int[] {0}));

        DcamInterruptedMp4Finalizer.Result result =
                new DcamInterruptedMp4Finalizer().finalizeInterrupted(file.toFile());

        assertTrue(result.finalized());
        assertEquals(133L, result.durationMillis());
        byte[] recovered = Files.readAllBytes(file);
        int moov = childBox(recovered, 0, recovered.length, "moov");
        int sidx = childBox(recovered, boxEnd(recovered, moov), recovered.length, "sidx");
        assertEquals(9_000L, sidxReferenceDuration(recovered, sidx, 0));
        assertEquals(3_000L, sidxReferenceDuration(recovered, sidx, 1));
    }
    @Test
    void interruptedFinalizationWritesSeekIndexAfterTrimmingTail() throws Exception {
        byte[] complete = fragmentedVideoWithSeekReserve();
        byte[] original = bytes(complete, box("moof", new byte[] {1}),
                declaredBox("mdat", 100, new byte[] {2, 3}));
        Path file = root.resolve("seekable-recovered.mp4");
        Files.write(file, original);

        DcamInterruptedMp4Finalizer.Result result =
                new DcamInterruptedMp4Finalizer().finalizeInterrupted(file.toFile());

        assertTrue(result.finalized());
        assertEquals(2, result.completeFragments());
        byte[] recovered = Files.readAllBytes(file);
        int moov = childBox(recovered, 0, recovered.length, "moov");
        int sidx = childBox(recovered, boxEnd(recovered, moov), recovered.length, "sidx");
        assertEquals(2, unsignedShort(recovered, contentOffset(sidx) + 30));
    }

    @Test
    void interruptedFinalizationRepairsBeforeSeekIndexHeaderCommit() throws Exception {
        Path file = root.resolve("seek-index-header-interrupted.mp4");
        Files.write(file, fragmentedVideoWithSeekReserve());
        assertTrue(new DcamInterruptedMp4Finalizer()
                .finalizeInterrupted(file.toFile()).finalized());

        byte[] interrupted = Files.readAllBytes(file);
        int moov = childBox(interrupted, 0, interrupted.length, "moov");
        int sidxBeforeInterruption = childBox(
                interrupted, boxEnd(interrupted, moov), interrupted.length, "sidx");
        ByteBuffer header = ByteBuffer.wrap(interrupted, boxEnd(interrupted, moov), 8);
        header.putInt(boxEnd(interrupted, sidxBeforeInterruption) - sidxBeforeInterruption);
        header.put("free".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        Files.write(file, interrupted);

        DcamInterruptedMp4Finalizer.Result result =
                new DcamInterruptedMp4Finalizer().finalizeInterrupted(file.toFile());

        assertTrue(result.finalized());
        byte[] recovered = Files.readAllBytes(file);
        int recoveredMoov = childBox(recovered, 0, recovered.length, "moov");
        int sidx = childBox(recovered, boxEnd(recovered, recoveredMoov),
                recovered.length, "sidx");
        assertEquals(2, unsignedShort(recovered, contentOffset(sidx) + 30));
        int firstMoof = childBox(recovered, boxEnd(recovered, sidx),
                recovered.length, "moof");
        assertEquals(firstMoof, fragmentBaseOffset(recovered, firstMoof));
    }

    @Test
    void recoversAfterPowerLossDuringCleanDurationPatch() throws Exception {
        byte[] interrupted = fragmentedVideoWithZeroDuration();
        int moov = childBox(interrupted, 0, interrupted.length, "moov");
        int mvhd = childBox(interrupted, contentOffset(moov), boxEnd(interrupted, moov), "mvhd");
        int trak = childBox(interrupted, contentOffset(moov), boxEnd(interrupted, moov), "trak");
        int tkhd = childBox(interrupted, contentOffset(trak), boxEnd(interrupted, trak), "tkhd");
        int mdia = childBox(interrupted, contentOffset(trak), boxEnd(interrupted, trak), "mdia");
        int mdhd = childBox(interrupted, contentOffset(mdia), boxEnd(interrupted, mdia), "mdhd");
        ByteBuffer.wrap(interrupted, contentOffset(mvhd) + 16, 4).putInt(20_000);
        ByteBuffer.wrap(interrupted, contentOffset(tkhd) + 20, 4).putInt(1);
        ByteBuffer.wrap(interrupted, contentOffset(mdhd) + 16, 4).putInt(180_000);
        Path file = root.resolve("power-loss-during-duration-patch.mp4");
        Files.write(file, interrupted);

        DcamInterruptedMp4Finalizer.Result result =
                new DcamInterruptedMp4Finalizer().finalizeInterrupted(file.toFile());

        assertTrue(result.finalized());
        assertEquals(100L, result.durationMillis());
        byte[] recovered = Files.readAllBytes(file);
        assertEquals(1_000L, unsignedInt(recovered, contentOffset(mvhd) + 16));
        assertEquals(1_000L, unsignedInt(recovered, contentOffset(tkhd) + 20));
        assertEquals(9_000L, unsignedInt(recovered, contentOffset(mdhd) + 16));
    }

    @Test
    void replacementFallsBackWhenAtomicMoveIsUnsupported() throws Exception {
        Path source = root.resolve("repaired.tmp");
        Path target = root.resolve("recording.mp4");
        Files.writeString(source, "repaired");
        Files.writeString(target, "interrupted");
        AtomicInteger attempts = new AtomicInteger();

        DcamMediaPublisher.move((from, to, options) -> {
            if (attempts.incrementAndGet() == 1) {
                throw new AtomicMoveNotSupportedException(
                        from.toString(), to.toString(), "unsupported");
            }
            return Files.move(from, to, options);
        }, source, target, StandardCopyOption.REPLACE_EXISTING);

        assertEquals(2, attempts.get());
        assertEquals("repaired", Files.readString(target));
        assertFalse(Files.exists(source));
    }

    @Test
    void recoveryFinalizesValidatesAndPublishesInterruptedRecording() throws Exception {
        DcamStorage storage = new DcamStorage(root.toFile());
        DcamMediaFile media = storage.mediaFile(
                DcamFileType.VIDEO, "CAM001", "000001",
                LocalDateTime.of(2026, 7, 30, 10, 21, 52), false);
        Files.createDirectories(media.getFile().toPath().getParent());
        byte[] complete = fragmentedVideoWithZeroDuration();
        byte[] interrupted = bytes(complete, box("moof", new byte[] {5}),
                declaredBox("mdat", 40, new byte[] {6, 7}));
        Files.write(media.getFile().toPath(), interrupted);
        long expectedBytes = complete.length;
        long[] validatedBytes = {-1L};
        DcamMediaValidator validator = (type, file) -> {
            validatedBytes[0] = file.length();
            return file.length() == expectedBytes
                    ? DcamMediaValidationResult.accepted("Recovered fragment is playable.")
                    : DcamMediaValidationResult.rejected("Incomplete fragment remains.");
        };

        StagedMediaRecoveryReport report = new DcamStagedMediaRecovery(
                storage, new DcamMediaFinalizer(storage), validator, new NoOpLogger()).recover();

        assertEquals(expectedBytes, validatedBytes[0]);
        assertEquals(1, report.getRecovered());
        assertEquals(expectedBytes, storage.finalFile(media).length());
        assertFalse(media.getFile().exists());
    }

    @Test
    void leavesCompleteFragmentedMp4Unchanged() throws Exception {
        byte[] complete = bytes(fragmentedHeader(), box("moof", new byte[] {1}),
                box("mdat", new byte[] {2}));
        Path file = root.resolve("complete.mp4");
        Files.write(file, complete);

        DcamInterruptedMp4Finalizer.Result result =
                new DcamInterruptedMp4Finalizer().finalizeInterrupted(file.toFile());

        assertFalse(result.finalized());
        assertEquals(complete.length, Files.size(file));
        assertEquals(1, result.completeFragments());
    }

    @Test
    void patchesZeroDurationHeadersFromFragmentSamples() throws Exception {
        Path file = root.resolve("duration.mp4");
        Files.write(file, fragmentedVideoWithZeroDuration());

        DcamInterruptedMp4Finalizer.Result result =
                new DcamInterruptedMp4Finalizer().finalizeInterrupted(file.toFile());

        assertTrue(result.finalized());
        assertEquals(100L, result.durationMillis());
        byte[] patched = Files.readAllBytes(file);
        int moov = childBox(patched, 0, patched.length, "moov");
        int mvhd = childBox(patched, contentOffset(moov), boxEnd(patched, moov), "mvhd");
        int trak = childBox(patched, contentOffset(moov), boxEnd(patched, moov), "trak");
        int tkhd = childBox(patched, contentOffset(trak), boxEnd(patched, trak), "tkhd");
        int mdia = childBox(patched, contentOffset(trak), boxEnd(patched, trak), "mdia");
        int mdhd = childBox(patched, contentOffset(mdia), boxEnd(patched, mdia), "mdhd");
        assertEquals(1_000L, unsignedInt(patched, contentOffset(mvhd) + 16));
        assertEquals(1_000L, unsignedInt(patched, contentOffset(tkhd) + 20));
        assertEquals(9_000L, unsignedInt(patched, contentOffset(mdhd) + 16));
    }
    @Test
    void acceptsZeroFinalSampleWhenTrackDurationRemainsPositive() throws Exception {
        Path file = root.resolve("short-duration.mp4");
        Files.write(file, fragmentedVideoWithSampleDurations(3_000, 0));

        DcamInterruptedMp4Finalizer.Result result =
                new DcamInterruptedMp4Finalizer().finalizeInterrupted(file.toFile());

        assertTrue(result.finalized());
        assertEquals(33L, result.durationMillis());
        assertEquals(333L, movieDuration(Files.readAllBytes(file)));
    }

    @Test
    void rejectsOversizedSampleCountWithoutLoopingOrChangingFile() throws Exception {
        byte[] original = fragmentedVideo(
                box("tfhd", ints(0x000008, 1, 3_000)),
                box("trun", ints(0, -1)));
        Path file = root.resolve("oversized-sample-count.mp4");
        Files.write(file, original);

        DcamInterruptedMp4Finalizer.Result result =
                new DcamInterruptedMp4Finalizer().finalizeInterrupted(file.toFile());

        assertFalse(result.finalized());
        assertArrayEquals(original, Files.readAllBytes(file));
    }

    @Test
    void preservesOriginalWhenIncompleteTailCannotBeDurationPatched() throws Exception {
        byte[] complete = fragmentedVideoWithZeroDuration();
        int moov = childBox(complete, 0, complete.length, "moov");
        int mvhd = childBox(complete, contentOffset(moov), boxEnd(complete, moov), "mvhd");
        complete[contentOffset(mvhd)] = 1;
        byte[] original = bytes(complete, box("moof", new byte[] {5}),
                declaredBox("mdat", 40, new byte[] {6, 7}));
        Path file = root.resolve("unsupported-version.mp4");
        Files.write(file, original);

        DcamInterruptedMp4Finalizer.Result result =
                new DcamInterruptedMp4Finalizer().finalizeInterrupted(file.toFile());

        assertFalse(result.finalized());
        assertArrayEquals(original, Files.readAllBytes(file));
    }
    @Test
    void preservesIncompleteOnlyFragmentBecauseNoPlayableFragmentExists() throws Exception {
        byte[] incomplete = bytes(fragmentedHeader(), box("moof", new byte[] {1}),
                declaredBox("mdat", 100, new byte[] {2, 3}));
        Path file = root.resolve("short.mp4");
        Files.write(file, incomplete);

        DcamInterruptedMp4Finalizer.Result result =
                new DcamInterruptedMp4Finalizer().finalizeInterrupted(file.toFile());

        assertFalse(result.finalized());
        assertEquals(incomplete.length, Files.size(file));
        assertEquals(0, result.completeFragments());
    }

    @Test
    void leavesLegacyInterruptedMp4Unchanged() throws Exception {
        byte[] legacy = bytes(box("ftyp", new byte[] {1}),
                declaredBox("mdat", 100, new byte[] {2, 3}));
        Path file = root.resolve("legacy.mp4");
        Files.write(file, legacy);

        DcamInterruptedMp4Finalizer.Result result =
                new DcamInterruptedMp4Finalizer().finalizeInterrupted(file.toFile());

        assertFalse(result.finalized());
        assertEquals(legacy.length, Files.size(file));
        assertEquals(0, result.completeFragments());
    }

    static byte[] interruptedAudioM4aWithGpsRoute() {
        byte[] firstMoof = fragmentMoof(0L, 1_024);
        byte[] secondMoof = fragmentMoof(0L, 1_024);
        return bytes(
                box("ftyp", new byte[] {0}), fragmentedAudioMoov(), seekIndexReserve(),
                firstMoof, box("mdat", new byte[] {1}),
                gpsRouteBox(0L, 21.034918, 105.767322),
                secondMoof, box("mdat", new byte[] {2}),
                gpsRouteBox(21_333L, 21.034919, 105.767323),
                fragmentMoof(0L, 1_024), declaredBox("mdat", 40, new byte[] {3}));
    }

    static byte[] fragmentedVideoWithZeroDuration() {
        return fragmentedVideoWithSampleDurations(3_000, 3_000, 3_000);
    }

    private static byte[] fragmentedVideoWithSampleDurations(int... durations) {
        int[] trunValues = new int[durations.length + 2];
        trunValues[0] = 0x00000100;
        trunValues[1] = durations.length;
        System.arraycopy(durations, 0, trunValues, 2, durations.length);
        return fragmentedVideo(box("tfhd", ints(0, 1)), box("trun", ints(trunValues)));
    }

    private static byte[] fragmentedVideo(byte[] tfhd, byte[] trun) {
        return bytes(box("ftyp", new byte[] {0}), fragmentedMoov(),
                box("moof", box("traf", bytes(tfhd, trun))),
                box("mdat", new byte[] {1}));
    }

    private static byte[] fragmentedVideoWithSeekReserve() {
        return fragmentedVideoWithSeekReserve(
                new int[] {45_000, 45_000}, new int[] {30_000, 30_000, 30_000});
    }

    private static byte[] fragmentedVideoWithSeekReserve(
            int[] firstDurations, int[] secondDurations) {
        byte[] header = bytes(box("ftyp", new byte[] {0}), fragmentedMoov());
        byte[] firstMdat = box("mdat", new byte[] {1, 2, 3, 4});
        long firstMoofOffset = header.length + DcamFragmentedMp4Layout.SEEK_INDEX_RESERVE_BYTES;
        byte[] firstMoof = fragmentMoof(firstMoofOffset, firstDurations);
        long secondMoofOffset = firstMoofOffset + firstMoof.length + firstMdat.length;
        byte[] secondMoof = fragmentMoof(secondMoofOffset, secondDurations);
        return bytes(header, seekIndexReserve(), firstMoof, firstMdat,
                secondMoof, box("mdat", new byte[] {5, 6, 7, 8}));
    }

    static byte[] fragmentedAvWithSeekReserve() {
        byte[] header = bytes(box("ftyp", new byte[] {0}), fragmentedAvMoov());
        byte[] firstMdat = box("mdat", new byte[] {1, 2, 3, 4});
        long firstMoofOffset = header.length + DcamFragmentedMp4Layout.SEEK_INDEX_RESERVE_BYTES;
        byte[] firstMoof = fragmentMoof(firstMoofOffset,
                new int[] {45_000, 45_000}, new int[] {24_000, 24_000});
        long secondMoofOffset = firstMoofOffset + firstMoof.length + firstMdat.length;
        byte[] secondMoof = fragmentMoof(secondMoofOffset,
                new int[] {45_000, 45_000}, new int[] {24_000, 24_000});
        return bytes(header, seekIndexReserve(), firstMoof, firstMdat,
                secondMoof, box("mdat", new byte[] {5, 6, 7, 8}));
    }

    private static byte[] fragmentedMoov() {
        byte[] mvhd = box("mvhd", ints(0, 0, 0, 10_000, 0));
        byte[] tkhd = box("tkhd", ints(0, 0, 0, 1, 0, 0));
        byte[] mdhd = box("mdhd", ints(0, 0, 0, 90_000, 0));
        byte[] hdlr = box("hdlr", ints(0, 0, HANDLER_VIDEO));
        byte[] trak = box("trak", bytes(tkhd, box("mdia", bytes(mdhd, hdlr))));
        return box("moov", bytes(mvhd, trak, box("mvex", new byte[0])));
    }

    private static byte[] fragmentedAudioMoov() {
        byte[] mvhd = box("mvhd", ints(0, 0, 0, 10_000, 0));
        byte[] tkhd = box("tkhd", ints(0, 0, 0, 1, 0, 0));
        byte[] mdhd = box("mdhd", ints(0, 0, 0, 48_000, 0));
        byte[] hdlr = box("hdlr", ints(0, 0, HANDLER_AUDIO));
        byte[] trak = box("trak", bytes(tkhd, box("mdia", bytes(mdhd, hdlr))));
        return box("moov", bytes(mvhd, trak, box("mvex", new byte[0])));
    }

    private static byte[] fragmentedAvMoov() {
        byte[] mvhd = box("mvhd", ints(0, 0, 0, 10_000, 0));
        byte[] videoTkhd = box("tkhd", ints(0, 0, 0, 1, 0, 0));
        byte[] videoMdhd = box("mdhd", ints(0, 0, 0, 90_000, 0));
        byte[] videoHdlr = box("hdlr", ints(0, 0, HANDLER_VIDEO));
        byte[] videoTrak = box("trak", bytes(
                videoTkhd, box("mdia", bytes(videoMdhd, videoHdlr))));
        byte[] audioTkhd = box("tkhd", ints(0, 0, 0, 2, 0, 0));
        byte[] audioMdhd = box("mdhd", ints(0, 0, 0, 48_000, 0));
        byte[] audioHdlr = box("hdlr", ints(0, 0, HANDLER_AUDIO));
        byte[] audioTrak = box("trak", bytes(
                audioTkhd, box("mdia", bytes(audioMdhd, audioHdlr))));
        return box("moov", bytes(mvhd, videoTrak, audioTrak, box("mvex", new byte[0])));
    }


    private static byte[] fragmentMoof(long baseOffset, int... durations) {
        return box("moof", fragmentTraf(baseOffset, 1, durations));
    }

    private static byte[] fragmentMoof(
            long baseOffset, int[] videoDurations, int[] audioDurations) {
        return box("moof", bytes(
                fragmentTraf(baseOffset, 1, videoDurations),
                fragmentTraf(baseOffset, 2, audioDurations)));
    }

    private static byte[] fragmentTraf(long baseOffset, int trackId, int... durations) {
        int[] trunValues = new int[durations.length + 2];
        trunValues[0] = 0x00000100;
        trunValues[1] = durations.length;
        System.arraycopy(durations, 0, trunValues, 2, durations.length);
        byte[] tfhd = box("tfhd", bytes(ints(0x000001, trackId), longs(baseOffset)));
        return box("traf", bytes(tfhd, box("trun", ints(trunValues))));
    }

    private static byte[] fragmentTrafWithDefaultDuration(
            long baseOffset, int trackId, int defaultDuration, int sampleCount) {
        byte[] tfhd = box("tfhd", bytes(
                ints(0x000009, trackId), longs(baseOffset), ints(defaultDuration)));
        return box("traf", bytes(tfhd, box("trun", ints(0, sampleCount))));
    }


    private static byte[] seekIndexReserve() {
        ByteBuffer reserve = ByteBuffer.allocate(DcamFragmentedMp4Layout.SEEK_INDEX_RESERVE_BYTES);
        reserve.putInt(DcamFragmentedMp4Layout.SEEK_INDEX_RESERVE_BYTES);
        reserve.put("free".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        reserve.putLong(DcamFragmentedMp4Layout.SEEK_INDEX_MAGIC);
        reserve.position(DcamFragmentedMp4Layout.SEEK_INDEX_RESERVE_BYTES - Long.BYTES);
        reserve.putLong(DcamFragmentedMp4Layout.SEEK_INDEX_MAGIC);
        return reserve.array();
    }

    private static byte[] ints(int... values) {
        ByteBuffer data = ByteBuffer.allocate(values.length * Integer.BYTES);
        for (int value : values) data.putInt(value);
        return data.array();
    }

    private static byte[] longs(long... values) {
        ByteBuffer data = ByteBuffer.allocate(values.length * Long.BYTES);
        for (long value : values) data.putLong(value);
        return data.array();
    }

    private static int childBox(byte[] data, int offset, int end, String expectedType) {
        while (offset + 8 <= end) {
            int size = ByteBuffer.wrap(data, offset, 4).getInt();
            if (size < 8 || offset + size > end) break;
            String type = new String(data, offset + 4, 4,
                    java.nio.charset.StandardCharsets.US_ASCII);
            if (expectedType.equals(type)) return offset;
            offset += size;
        }
        throw new AssertionError("Missing MP4 box " + expectedType);
    }

    private static int contentOffset(int boxOffset) {
        return boxOffset + 8;
    }

    private static int boxEnd(byte[] data, int boxOffset) {
        return boxOffset + ByteBuffer.wrap(data, boxOffset, 4).getInt();
    }

    private static long unsignedInt(byte[] data, int offset) {
        return Integer.toUnsignedLong(ByteBuffer.wrap(data, offset, 4).getInt());
    }

    private static int unsignedShort(byte[] data, int offset) {
        return Short.toUnsignedInt(ByteBuffer.wrap(data, offset, 2).getShort());
    }

    private static long longValue(byte[] data, int offset) {
        return ByteBuffer.wrap(data, offset, Long.BYTES).getLong();
    }

    private static long sidxReferenceSize(byte[] data, int sidx, int referenceIndex) {
        return unsignedInt(data, contentOffset(sidx) + 32 + referenceIndex * 12)
                & 0x7fff_ffffL;
    }

    private static long sidxReferenceDuration(byte[] data, int sidx, int referenceIndex) {
        return unsignedInt(data, contentOffset(sidx) + 36 + referenceIndex * 12);
    }

    private static byte[] gpsRouteBox(
            long presentationTimeUs, double latitude, double longitude) {
        ByteBuffer box = ByteBuffer.allocate(DcamFragmentedMp4Layout.GPS_ROUTE_BOX_BYTES);
        box.putInt(DcamFragmentedMp4Layout.GPS_ROUTE_BOX_BYTES);
        box.putInt(DcamFragmentedMp4Layout.BOX_UUID);
        box.putLong(DcamFragmentedMp4Layout.GPS_ROUTE_UUID_MOST_SIGNIFICANT_BITS);
        box.putLong(DcamFragmentedMp4Layout.GPS_ROUTE_UUID_LEAST_SIGNIFICANT_BITS);
        box.putInt(1);
        box.putLong(presentationTimeUs);
        box.putDouble(latitude);
        box.putDouble(longitude);
        return box.array();
    }

    private static void assertGpsRoutePoint(byte[] data, int boxOffset,
            long presentationTimeUs, double latitude, double longitude) {
        assertEquals(DcamFragmentedMp4Layout.GPS_ROUTE_BOX_BYTES,
                boxEnd(data, boxOffset) - boxOffset);
        int content = contentOffset(boxOffset);
        assertEquals(DcamFragmentedMp4Layout.GPS_ROUTE_UUID_MOST_SIGNIFICANT_BITS,
                longValue(data, content));
        assertEquals(DcamFragmentedMp4Layout.GPS_ROUTE_UUID_LEAST_SIGNIFICANT_BITS,
                longValue(data, content + Long.BYTES));
        assertEquals(1L, unsignedInt(data, content + 2 * Long.BYTES));
        assertEquals(presentationTimeUs, longValue(data, content + 20));
        assertEquals(latitude, doubleValue(data, content + 28));
        assertEquals(longitude, doubleValue(data, content + 36));
    }

    private static double doubleValue(byte[] data, int offset) {
        return ByteBuffer.wrap(data, offset, Double.BYTES).getDouble();
    }
    private static long fragmentBaseOffset(byte[] data, int moof) {
        return fragmentBaseOffset(data, moof, 0);
    }

    private static long fragmentBaseOffset(byte[] data, int moof, int trackIndex) {
        int offset = contentOffset(moof);
        int end = boxEnd(data, moof);
        for (int currentTrack = 0; currentTrack <= trackIndex; currentTrack++) {
            int traf = childBox(data, offset, end, "traf");
            if (currentTrack == trackIndex) {
                int tfhd = childBox(data, contentOffset(traf), boxEnd(data, traf), "tfhd");
                return longValue(data, contentOffset(tfhd) + 8);
            }
            offset = boxEnd(data, traf);
        }
        throw new AssertionError("Missing fragment track " + trackIndex);
    }

    static long movieDuration(byte[] data) {
        int moov = childBox(data, 0, data.length, "moov");
        int mvhd = childBox(data, contentOffset(moov), boxEnd(data, moov), "mvhd");
        return unsignedInt(data, contentOffset(mvhd) + 16);
    }
    private static void writeFully(DcamRecordingOutput output, ByteBuffer source)
            throws Exception {
        while (source.hasRemaining()) assertTrue(output.write(source) > 0);
    }

    private static byte[] readAll(DcamRandomAccessMedia media) throws Exception {
        media.position(0L);
        ByteBuffer target = ByteBuffer.allocate(Math.toIntExact(media.size()));
        while (target.hasRemaining()) {
            int read = media.read(target);
            if (read < 0) break;
        }
        return target.array();
    }

    private static int authenticatedRecordCount(File file, String password, int type)
            throws Exception {
        try (RandomAccessFile input = new RandomAccessFile(file, "r")) {
            byte[] encodedHeader = new byte[DcamSegmentedGcmFormat.FILE_HEADER_BYTES];
            input.readFully(encodedHeader);
            DcamSegmentedGcmFormat.Header header =
                    DcamSegmentedGcmFormat.readHeader(encodedHeader);
            byte[] key = DcamSegmentedGcmFormat.deriveKey(header, password);
            try {
                int count = 0;
                long offset = DcamSegmentedGcmFormat.FILE_HEADER_BYTES;
                while (offset < input.length()) {
                    input.seek(offset);
                    byte[] recordHeader =
                            new byte[DcamSegmentedGcmFormat.RECORD_HEADER_BYTES];
                    input.readFully(recordHeader);
                    DcamSegmentedGcmFormat.RecordMetadata metadata =
                            DcamSegmentedGcmFormat.readRecordHeader(header, recordHeader);
                    byte[] encoded = new byte[metadata.totalBytes];
                    input.seek(offset);
                    input.readFully(encoded);
                    DcamSegmentedGcmFormat.decodeRecord(header, key, encoded);
                    if (metadata.type == type) count++;
                    offset += metadata.totalBytes;
                }
                return count;
            } finally {
                Arrays.fill(key, (byte) 0);
            }
        }
    }
    private static byte[] fragmentedHeader() {
        return bytes(box("ftyp", new byte[] {0}), box("moov", box("mvex", new byte[0])));
    }

    private static byte[] box(String type, byte[] payload) {
        return declaredBox(type, payload.length + 8, payload);
    }

    private static byte[] declaredBox(String type, int declaredSize, byte[] payload) {
        ByteBuffer box = ByteBuffer.allocate(payload.length + 8);
        box.putInt(declaredSize);
        box.put(type.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        box.put(payload);
        return box.array();
    }

    private static byte[] bytes(byte[]... parts) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        for (byte[] part : parts) output.writeBytes(part);
        return output.toByteArray();
    }

    private static final class CountingMedia implements DcamRandomAccessMedia {
        private final DcamRandomAccessMedia delegate;
        private long readBytes;
        private long writtenBytes;

        private CountingMedia(DcamRandomAccessMedia delegate) {
            this.delegate = delegate;
        }

        @Override public int read(ByteBuffer target) throws IOException {
            int read = delegate.read(target);
            if (read > 0) readBytes += read;
            return read;
        }

        @Override public int write(ByteBuffer source) throws IOException {
            int written = delegate.write(source);
            if (written > 0) writtenBytes += written;
            return written;
        }

        @Override public long position() throws IOException { return delegate.position(); }

        @Override public CountingMedia position(long position) throws IOException {
            delegate.position(position);
            return this;
        }

        @Override public long size() throws IOException { return delegate.size(); }

        @Override public CountingMedia truncate(long size) throws IOException {
            delegate.truncate(size);
            return this;
        }

        @Override public long physicalSize() throws IOException { return delegate.physicalSize(); }

        @Override public long estimatedPhysicalSize() throws IOException {
            return delegate.estimatedPhysicalSize();
        }

        @Override public long finalizationReserveBytes() {
            return delegate.finalizationReserveBytes();
        }

        @Override public void force(boolean metadata) throws IOException {
            delegate.force(metadata);
        }

        @Override public void checkpoint() throws IOException { delegate.checkpoint(); }

        @Override public void finish() throws IOException { delegate.finish(); }

        @Override public boolean isFinalized() { return delegate.isFinalized(); }

        @Override public void beginTransaction() throws IOException { delegate.beginTransaction(); }

        @Override public void commitTransaction() throws IOException { delegate.commitTransaction(); }

        @Override public void rollbackTransaction() throws IOException {
            delegate.rollbackTransaction();
        }

        @Override public boolean isOpen() { return delegate.isOpen(); }

        @Override public void close() throws IOException { delegate.close(); }
    }

    private static final class NoOpLogger implements Logger {
        @Override public void debug(LogCategory category, String eventName, String message) {}
        @Override public void info(LogCategory category, String eventName, String message) {}
        @Override public void info(LogCategory category, String eventName, String reasonCode, String message, Throwable error) {}
        @Override public void warn(LogCategory category, String eventName, String reasonCode, String message, Throwable error) {}
        @Override public void error(LogCategory category, String eventName, String reasonCode, String message, Throwable error) {}
    }
}