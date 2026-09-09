package com.dvid.dcam.platform.storage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.Context;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Bundle;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.muxer.BufferInfo;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.dvid.dcam.core.logging.domain.LogCategory;
import com.dvid.dcam.core.logging.application.port.Logger;
import com.dvid.dcam.feature.location.domain.GpsCoordinate;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class AudioM4aGpsCrashRecoveryDeviceTest {
    private static final String PHASE_ARGUMENT = "dcamAudioM4aCrashPhase";
    private static final String MODE_ARGUMENT = "dcamAudioM4aCrashMode";
    private static final String POWER_LOSS_ARGUMENT = "dcamAudioM4aPowerLoss";
    private static final String STAGE_PHASE = "stage";
    private static final String RECOVER_PHASE = "recover";
    private static final String ASSET_NAME = "audio_m4a_gps_crash_proof.aac";
    private static final String PASSWORD = "dcam-m4a-crash-proof";
    private static final int AUDIO_SAMPLE_RATE = 48_000;
    private static final int AUDIO_CHANNEL_COUNT = 1;
    private static final int AUDIO_BIT_RATE = 64_000;
    private static final int AAC_SAMPLES_PER_FRAME = 1_024;
    private static final int BOX_MOOF = 0x6d6f6f66;
    private static final int STATUS_CODE = 2;
    private static final int HALT_CODE = 73;
    private static final LocalDateTime CREATED_AT =
            LocalDateTime.of(2026, 8, 14, 10, 30);
    private static final List<RoutePoint> EXPECTED_ROUTE = List.of(
            new RoutePoint(0L, 21.034918, 105.767322),
            new RoutePoint(1_002_666L, 21.034919, 105.767323),
            new RoutePoint(2_005_333L, 21.034920, 105.767324));

    @Test
    public void stageAndHalt() throws Exception {
        assumePhase(STAGE_PHASE);
        ProofMode mode = proofMode();
        boolean waitForPowerLoss = "true".equals(
                InstrumentationRegistry.getArguments().getString(POWER_LOSS_ARGUMENT));
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        File root = proofRoot(context, mode);
        deleteTree(root);
        Files.createDirectories(root.toPath());

        DcamStorage storage = new DcamStorage(root);
        DcamMediaFile media = storage.mediaFile(
                DcamFileType.AUDIO_M4A, "CAM001", "000001", CREATED_AT, mode.encrypted);
        DcamMediaOutputImpl mediaOutput = new DcamMediaOutputImpl(
                null, storage, () -> true, () -> PASSWORD, new NoOpLogger());
        DcamRecordingOutput output = mediaOutput.openAudioOutput(media);
        RoutePoint firstPoint = EXPECTED_ROUTE.get(0);
        AtomicReference<GpsCoordinate> captureLocation = new AtomicReference<>(
                new GpsCoordinate(firstPoint.latitude, firstPoint.longitude));
        DcamAudioM4aWriter writer = new DcamAudioM4aWriter(
                output, captureLocation::get, new NoOpLogger());
        writer.start(aacFormat());
        List<AacFrame> frames = readAacFrames(
                InstrumentationRegistry.getInstrumentation().getContext());
        int routeIndex = 1;

        for (int frameIndex = 0; frameIndex < frames.size(); frameIndex++) {
            AacFrame frame = frames.get(frameIndex);
            long presentationTimeUs = presentationTimeUs(frameIndex);
            while (routeIndex < EXPECTED_ROUTE.size()
                    && EXPECTED_ROUTE.get(routeIndex).presentationTimeUs <= presentationTimeUs) {
                RoutePoint point = EXPECTED_ROUTE.get(routeIndex++);
                captureLocation.set(new GpsCoordinate(point.latitude, point.longitude));
            }
            writer.writeSample(ByteBuffer.wrap(frame.payload),
                    new BufferInfo(presentationTimeUs, frame.payload.length, 0));
        }

        assertEquals(EXPECTED_ROUTE.size(), routeIndex);
        output.checkpoint();
        byte[] logicalBeforeCrash = readAll(output.media());
        assertEquals(EXPECTED_ROUTE, gpsRoutePoints(logicalBeforeCrash));
        assertTrue(countTopLevelBoxes(logicalBeforeCrash, BOX_MOOF) >= 2);
        assertTrue(media.getFile().isFile());

        Bundle status = new Bundle();
        status.putString("proof_phase", STAGE_PHASE);
        status.putString("proof_mode", mode.argumentValue);
        status.putString("staged_file", media.getFile().getAbsolutePath());
        status.putLong("logical_bytes", logicalBeforeCrash.length);
        status.putInt("route_points", EXPECTED_ROUTE.size());
        status.putBoolean("power_loss_ready", waitForPowerLoss);
        InstrumentationRegistry.getInstrumentation().sendStatus(STATUS_CODE, status);
        if (waitForPowerLoss) {
            writeUntilPowerLoss(writer, frames);
            fail("Power-loss write loop returned");
        }
        Thread.sleep(250L);
        Runtime.getRuntime().halt(HALT_CODE);
        fail("Runtime.halt returned");
    }

    @Test
    public void recoverAfterHalt() throws Exception {
        assumePhase(RECOVER_PHASE);
        ProofMode mode = proofMode();
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        File root = proofRoot(context, mode);
        DcamStorage storage = new DcamStorage(root);
        DcamMediaFile media = storage.mediaFile(
                DcamFileType.AUDIO_M4A, "CAM001", "000001", CREATED_AT, mode.encrypted);
        assertTrue("Staged M4A is missing before recovery", media.getFile().isFile());

        AndroidDcamMediaValidator validator = new AndroidDcamMediaValidator();
        DcamStagedMediaRecovery recovery = new DcamStagedMediaRecovery(
                storage,
                new DcamMediaFinalizer(storage),
                validator,
                () -> true,
                () -> PASSWORD,
                new NoOpLogger());
        StagedMediaRecoveryReport report = recovery.recover();

        File published = storage.finalFile(media);
        assertEquals(1, report.getRecovered());
        assertEquals(0, report.getPreserved());
        assertTrue(report.isResolved(media.getFileName()));
        assertFalse(media.getFile().exists());
        assertTrue(published.isFile());

        byte[] recovered;
        DcamMediaValidationResult validation;
        if (mode.encrypted) {
            try (SegmentedAesGcmMediaStore logical =
                         SegmentedAesGcmMediaStore.openPublished(published, PASSWORD)) {
                validation = validator.validate(DcamFileType.AUDIO_M4A, logical);
                recovered = readAll(logical);
            }
        } else {
            validation = validator.validate(DcamFileType.AUDIO_M4A, published);
            recovered = Files.readAllBytes(published.toPath());
        }
        assertTrue(validation.detail(), validation.playable());
        assertEquals(EXPECTED_ROUTE, gpsRoutePoints(recovered));
        assertNoMd5(storage.finalFile(media).getParentFile());

        File proofDirectory = new File(root, "Proof");
        Files.createDirectories(proofDirectory.toPath());
        File export = new File(proofDirectory, "recovered.m4a");
        Files.write(export.toPath(), recovered);
        try (FileChannel channel = FileChannel.open(export.toPath(), StandardOpenOption.WRITE)) {
            channel.force(true);
        }

        PlaybackProof playback = verifyPlayback(export);
        assertTrue(playback.sampleCount >= 100);
        assertTrue(playback.lastPresentationTimeUs >= 5_000_000L);
        assertTrue(playback.seekPresentationTimeUs >= 0L);

        Bundle status = new Bundle();
        status.putString("proof_phase", RECOVER_PHASE);
        status.putString("proof_mode", mode.argumentValue);
        status.putString("published_file", published.getAbsolutePath());
        status.putString("export_file", export.getAbsolutePath());
        status.putString("validation", validation.detail());
        status.putInt("route_points", EXPECTED_ROUTE.size());
        status.putInt("sample_count", playback.sampleCount);
        status.putLong("last_presentation_time_us", playback.lastPresentationTimeUs);
        status.putLong("seek_presentation_time_us", playback.seekPresentationTimeUs);
        InstrumentationRegistry.getInstrumentation().sendStatus(STATUS_CODE, status);
    }

    private static void writeUntilPowerLoss(
            DcamAudioM4aWriter writer, List<AacFrame> frames) throws Exception {
        int frameIndex = frames.size();
        long frameDurationNanos =
                (long) AAC_SAMPLES_PER_FRAME * 1_000_000_000L / AUDIO_SAMPLE_RATE;
        long nextWriteAtNanos = System.nanoTime();
        while (true) {
            AacFrame frame = frames.get(frameIndex % frames.size());
            long presentationTimeUs = presentationTimeUs(frameIndex);
            writer.writeSample(ByteBuffer.wrap(frame.payload),
                    new BufferInfo(presentationTimeUs, frame.payload.length, 0));
            frameIndex++;
            nextWriteAtNanos += frameDurationNanos;
            long remainingNanos = nextWriteAtNanos - System.nanoTime();
            if (remainingNanos > 0L) {
                Thread.sleep(remainingNanos / 1_000_000L,
                        (int) (remainingNanos % 1_000_000L));
            }
        }
    }

    private static Format aacFormat() {
        return new Format.Builder()
                .setSampleMimeType(MimeTypes.AUDIO_AAC)
                .setCodecs("mp4a.40.2")
                .setSampleRate(AUDIO_SAMPLE_RATE)
                .setChannelCount(AUDIO_CHANNEL_COUNT)
                .setAverageBitrate(AUDIO_BIT_RATE)
                .setInitializationData(List.of(new byte[] {0x11, (byte) 0x88}))
                .build();
    }

    private static List<AacFrame> readAacFrames(Context context) throws IOException {
        byte[] data;
        try (InputStream input = context.getAssets().open(ASSET_NAME);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8 * 1024];
            int bytesRead;
            while ((bytesRead = input.read(buffer)) >= 0) {
                if (bytesRead > 0) output.write(buffer, 0, bytesRead);
            }
            data = output.toByteArray();
        }

        List<AacFrame> frames = new ArrayList<>();
        int offset = 0;
        while (offset < data.length) {
            if (data.length - offset < 7) throw new IOException("Truncated ADTS header.");
            int first = data[offset] & 0xff;
            int second = data[offset + 1] & 0xff;
            if (first != 0xff || (second & 0xf6) != 0xf0) {
                throw new IOException("Invalid ADTS sync word.");
            }
            int headerBytes = (second & 1) == 0 ? 9 : 7;
            int frequencyIndex = (data[offset + 2] >>> 2) & 0x0f;
            int channelConfiguration = ((data[offset + 2] & 1) << 2)
                    | ((data[offset + 3] >>> 6) & 3);
            int frameBytes = ((data[offset + 3] & 3) << 11)
                    | ((data[offset + 4] & 0xff) << 3)
                    | ((data[offset + 5] & 0xe0) >>> 5);
            if (frequencyIndex != 3 || channelConfiguration != AUDIO_CHANNEL_COUNT) {
                throw new IOException("Unexpected ADTS audio format.");
            }
            if ((data[offset + 6] & 3) != 0 || frameBytes < headerBytes
                    || frameBytes > data.length - offset) {
                throw new IOException("Invalid ADTS frame length.");
            }
            frames.add(new AacFrame(Arrays.copyOfRange(
                    data, offset + headerBytes, offset + frameBytes)));
            offset += frameBytes;
        }
        if (frames.size() < 100) throw new IOException("AAC proof asset is too short.");
        return List.copyOf(frames);
    }

    private static PlaybackProof verifyPlayback(File file) throws IOException {
        MediaExtractor extractor = new MediaExtractor();
        try {
            extractor.setDataSource(file.getAbsolutePath());
            int audioTrack = -1;
            for (int track = 0; track < extractor.getTrackCount(); track++) {
                MediaFormat format = extractor.getTrackFormat(track);
                String mimeType = format.getString(MediaFormat.KEY_MIME);
                if (mimeType != null && mimeType.startsWith("audio/")) {
                    audioTrack = track;
                    break;
                }
            }
            assertTrue("Recovered M4A has no audio track", audioTrack >= 0);
            extractor.selectTrack(audioTrack);
            ByteBuffer sample = ByteBuffer.allocate(256 * 1024);
            int sampleCount = 0;
            long previousPresentationTimeUs = -1L;
            long lastPresentationTimeUs = -1L;
            while (true) {
                sample.clear();
                int sampleBytes = extractor.readSampleData(sample, 0);
                if (sampleBytes < 0) break;
                long presentationTimeUs = extractor.getSampleTime();
                assertTrue("Recovered M4A sample timestamp is unavailable",
                        presentationTimeUs >= 0L);
                assertTrue("Recovered M4A sample timestamps are not monotonic",
                        presentationTimeUs > previousPresentationTimeUs);
                previousPresentationTimeUs = presentationTimeUs;
                lastPresentationTimeUs = presentationTimeUs;
                sampleCount++;
                if (!extractor.advance()) break;
            }
            assertTrue("Recovered M4A has no readable audio samples", sampleCount > 0);
            long seekTargetUs = lastPresentationTimeUs / 2L;
            extractor.seekTo(seekTargetUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
            sample.clear();
            assertTrue("Recovered M4A seek returned no sample",
                    extractor.readSampleData(sample, 0) >= 0);
            long seekPresentationTimeUs = extractor.getSampleTime();
            assertTrue("Recovered M4A seek moved outside the normal one-second index window",
                    Math.abs(seekPresentationTimeUs - seekTargetUs) <= 1_000_000L);
            return new PlaybackProof(
                    sampleCount, lastPresentationTimeUs, seekPresentationTimeUs);
        } finally {
            extractor.release();
        }
    }

    private static byte[] readAll(DcamRandomAccessMedia media) throws IOException {
        media.position(0L);
        ByteBuffer target = ByteBuffer.allocate(Math.toIntExact(media.size()));
        while (target.hasRemaining()) {
            int bytesRead = media.read(target);
            if (bytesRead < 0) break;
            if (bytesRead == 0) throw new IOException("Logical media read stopped.");
        }
        if (target.hasRemaining()) throw new IOException("Logical media ended early.");
        return target.array();
    }

    private static List<RoutePoint> gpsRoutePoints(byte[] data) {
        List<RoutePoint> points = new ArrayList<>();
        ByteBuffer bytes = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN);
        int offset = 0;
        while (offset <= data.length - 8) {
            long boxBytes = Integer.toUnsignedLong(bytes.getInt(offset));
            if (boxBytes < 8L || boxBytes > data.length - offset) break;
            if (bytes.getInt(offset + 4) == DcamFragmentedMp4Layout.BOX_UUID
                    && boxBytes == DcamFragmentedMp4Layout.GPS_ROUTE_BOX_BYTES) {
                int content = offset + 8;
                if (bytes.getLong(content)
                                == DcamFragmentedMp4Layout.GPS_ROUTE_UUID_MOST_SIGNIFICANT_BITS
                        && bytes.getLong(content + 8)
                                == DcamFragmentedMp4Layout.GPS_ROUTE_UUID_LEAST_SIGNIFICANT_BITS
                        && bytes.getInt(content + 16) == 1) {
                    points.add(new RoutePoint(
                            bytes.getLong(content + 20),
                            bytes.getDouble(content + 28),
                            bytes.getDouble(content + 36)));
                }
            }
            offset += (int) boxBytes;
        }
        return List.copyOf(points);
    }

    private static int countTopLevelBoxes(byte[] data, int expectedType) {
        ByteBuffer bytes = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN);
        int count = 0;
        int offset = 0;
        while (offset <= data.length - 8) {
            long boxBytes = Integer.toUnsignedLong(bytes.getInt(offset));
            if (boxBytes < 8L || boxBytes > data.length - offset) break;
            if (bytes.getInt(offset + 4) == expectedType) count++;
            offset += (int) boxBytes;
        }
        return count;
    }

    private static long presentationTimeUs(int frameIndex) {
        return (long) frameIndex * AAC_SAMPLES_PER_FRAME * 1_000_000L / AUDIO_SAMPLE_RATE;
    }

    private static File proofRoot(Context context, ProofMode mode) {
        File externalFiles = context.getExternalFilesDir(null);
        assertNotNull("External app files directory is unavailable", externalFiles);
        return new File(externalFiles, "m4a-gps-crash-proof/" + mode.argumentValue);
    }

    private static ProofMode proofMode() {
        String mode = InstrumentationRegistry.getArguments().getString(MODE_ARGUMENT);
        if (ProofMode.PLAIN.argumentValue.equals(mode)) return ProofMode.PLAIN;
        if (ProofMode.ENCRYPTED.argumentValue.equals(mode)) return ProofMode.ENCRYPTED;
        throw new IllegalArgumentException("Missing or invalid " + MODE_ARGUMENT + ": " + mode);
    }

    private static void assumePhase(String expected) {
        String phase = InstrumentationRegistry.getArguments().getString(PHASE_ARGUMENT);
        Assume.assumeTrue("Manual M4A crash proof phase only", expected.equals(phase));
    }

    private static void assertNoMd5(File directory) {
        File[] files = directory.listFiles();
        if (files == null) return;
        for (File file : files) {
            assertFalse("M4A must not create video MD5 sidecar: " + file.getName(),
                    file.getName().toLowerCase(java.util.Locale.ROOT).endsWith(".md5"));
        }
    }

    private static void deleteTree(File file) throws IOException {
        if (!file.exists()) return;
        if (Files.isSymbolicLink(file.toPath())) {
            Files.delete(file.toPath());
            return;
        }
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) deleteTree(child);
        }
        Files.deleteIfExists(file.toPath());
    }

    private enum ProofMode {
        PLAIN("plain", false),
        ENCRYPTED("encrypted", true);

        private final String argumentValue;
        private final boolean encrypted;

        ProofMode(String argumentValue, boolean encrypted) {
            this.argumentValue = argumentValue;
            this.encrypted = encrypted;
        }
    }

    private record AacFrame(byte[] payload) {}

    private record RoutePoint(long presentationTimeUs, double latitude, double longitude) {}

    private record PlaybackProof(
            int sampleCount, long lastPresentationTimeUs, long seekPresentationTimeUs) {}

    private static final class NoOpLogger implements Logger {
        @Override public void debug(LogCategory category, String eventName, String message) {}
        @Override public void info(LogCategory category, String eventName, String message) {}
        @Override public void info(LogCategory category, String eventName, String reasonCode, String message, Throwable error) {}
        @Override public void warn(LogCategory category, String eventName, String reasonCode, String message, Throwable error) {}
        @Override public void error(LogCategory category, String eventName, String reasonCode, String message, Throwable error) {}
    }
}