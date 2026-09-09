package com.dvid.dcam.platform.camera.shared;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Process;
import android.os.SystemClock;
import android.util.Range;
import android.view.Surface;
import androidx.annotation.OptIn;
import androidx.media3.common.util.MediaFormatUtil;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.container.Mp4LocationData;
import androidx.media3.container.Mp4OrientationData;
import androidx.media3.muxer.FragmentedMp4Muxer;
import androidx.media3.muxer.MuxerException;
import androidx.media3.muxer.MuxerUtil;
import com.dvid.dcam.core.logging.domain.LogCategory;
import com.dvid.dcam.core.logging.application.port.Logger;
import com.dvid.dcam.feature.capture.domain.AudioCaptureSettings;
import com.dvid.dcam.feature.storage.domain.CaptureStorageCapacityPolicy;
import com.dvid.dcam.feature.location.domain.GpsCoordinate;
import com.dvid.dcam.feature.device.domain.camera.CameraResolution;
import com.dvid.dcam.platform.audio.SharedMicrophoneCapture;
import com.dvid.dcam.platform.storage.DcamFragmentedMp4Layout;
import com.dvid.dcam.platform.storage.DcamRecordingOutput;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@OptIn(markerClass = UnstableApi.class)
public final class SharedAvcEncoder implements AutoCloseable {
    public record Segment(File file, long sampleCount, long durationUs, String detail) {}

    private static final String MIME_TYPE = MediaFormat.MIMETYPE_VIDEO_AVC;
    private static final String AUDIO_MIME_TYPE = MediaFormat.MIMETYPE_AUDIO_AAC;

    private static final int AUDIO_SAMPLE_RATE = SharedMicrophoneCapture.SAMPLE_RATE;
    private static final int AUDIO_CHANNEL_COUNT = SharedMicrophoneCapture.CHANNEL_COUNT;
    private static final int AUDIO_BIT_RATE = AudioCaptureSettings.BIT_RATE_BPS;
    private static final int AUDIO_BYTES_PER_FRAME = SharedMicrophoneCapture.BYTES_PER_FRAME;
    private static final int AUDIO_PRIME_BYTES = 8 * 1024;
    private static final long AUDIO_PRIME_TIMEOUT_MILLIS = 1_000L;
    private static final long AUDIO_DRAIN_MARGIN_MILLIS = 1_000L;
    private static final long AUDIO_STOP_TIMEOUT_MILLIS =
            SharedMicrophoneCapture.maximumSubscriptionBacklogMillis()
                    + AUDIO_DRAIN_MARGIN_MILLIS;
    private static final int I_FRAME_INTERVAL_SECONDS = 1;
    private static final long FRAGMENT_DURATION_MILLIS = 900L;
    private static final long STORAGE_FLOOR_CHECK_INTERVAL_MILLIS = 1_000L;
    // ponytail: Startup RAM holds one GOP; use disk-backed retention before enabling pre-record.
    private static final int MIN_PENDING_VIDEO_GOP_BYTES = 8 * 1024 * 1024;
    private static final int MAX_PENDING_VIDEO_GOP_BYTES = 64 * 1024 * 1024;
    private static final int RECENT_VIDEO_SAMPLE_CAPACITY = 64;
    private static final int VIDEO_SAMPLE_PREFIX_BYTES = 8;
    private static final Object REUSABLE_CODEC_LOCK = new Object();
    private static final Map<String, MediaCodec> REUSABLE_CODECS = new HashMap<>();
    private static final String PIPELINE_LOG_FIELD = "pipeline=";
    private static final String ELAPSED_LOG_FRAGMENT = ". Elapsed: ";
    private static final String TOTAL_DURATION_LOG_FRAGMENT = " ms. Total: ";
    private static final String PIPELINE_DURATION_LOG_FRAGMENT = " ms. Pipeline: ";
    private static final String PIPELINE_CONTEXT = ". Pipeline: ";
    private static final String CODEC_CONTEXT = ". Codec: ";

    private final Object lock = new Object();
    private final Logger logger;
    private final SharedMicrophoneCapture microphoneCapture;
    private final String pipelineId;
    private final String codecName;
    private final boolean recycleCodec;
    private final long preRecordGopDurationUs;
    private final long nominalVideoFrameDurationUs;
    private final int videoBitrateBitsPerSecond;
    private final Supplier<GpsCoordinate> captureLocation;
    private int rotationDegrees;
    private HandlerThread callbackThread;
    private Handler callbackHandler;
    private MediaCodec codec;
    private Surface inputSurface;
    private MediaFormat outputFormat;
    private final CountDownLatch outputFormatReady = new CountDownLatch(1);
    private final List<PendingVideoSample> pendingVideoGop = new ArrayList<>();
    private final int maxPendingVideoGopBytes;
    private int pendingVideoGopBytes;
    private final VideoSampleAssembler videoSampleAssembler;
    private final long[] recentVideoSampleSequences =
            new long[RECENT_VIDEO_SAMPLE_CAPACITY];
    private final long[] recentVideoSamplePresentationTimesUs =
            new long[RECENT_VIDEO_SAMPLE_CAPACITY];
    private final long[] recentVideoSamplePrefixes =
            new long[RECENT_VIDEO_SAMPLE_CAPACITY];
    private final int[] recentVideoSampleOffsets =
            new int[RECENT_VIDEO_SAMPLE_CAPACITY];
    private final int[] recentVideoSampleSizes =
            new int[RECENT_VIDEO_SAMPLE_CAPACITY];
    private final int[] recentVideoSampleFlags =
            new int[RECENT_VIDEO_SAMPLE_CAPACITY];
    private final int[] recentVideoSampleBufferCounts =
            new int[RECENT_VIDEO_SAMPLE_CAPACITY];
    private final int[] recentVideoSamplePrefixLengths =
            new int[RECENT_VIDEO_SAMPLE_CAPACITY];
    private long nextVideoSampleSequence;
    private int recentVideoSampleCount;
    private int nextRecentVideoSampleIndex;
    private boolean partialVideoSampleLogged;
    private boolean partialVideoTimestampMismatchLogged;
    private boolean malformedVideoRecoveryActive;
    private int malformedVideoSamplesDropped;
    private int dependentVideoSamplesDropped;
    private long malformedVideoRecoveryStartedAtUs = -1L;
    private FragmentedMp4Muxer muxer;
    private DcamRecordingOutput muxerOutput;
    private boolean muxerOutputOwned;
    private DcamFragmentedMp4Layout.OutputChannel muxerChannel;
    private boolean seekIndexReserved;
    private CountDownLatch segmentOutputReady = new CountDownLatch(0);
    private int trackIndex = -1;
    private int audioTrackIndex = -1;
    private boolean gpsRouteEnabled;
    private MediaFormat audioOutputFormat;
    private MediaFormat primedAudioOutputFormat;
    private boolean audioRequired;
    private GpsCoordinate segmentLocation;
    private GpsCoordinate lastGpsCoordinate;
    private boolean gpsLookupFailureLogged;

    private Thread audioThread;
    private MediaCodec audioCodec;
    private SharedMicrophoneCapture.Subscription microphoneSubscription;
    private volatile boolean audioStopRequested;
    private long audioStopTargetFrames;
    private boolean audioCaptureStopped;
    private CountDownLatch audioMuxerWritesFinished = new CountDownLatch(0);
    private boolean muxerStarted;
    private boolean acceptingSegment;
    private boolean waitingForKeyFrame;
    private File segmentFile;
    private long segmentSamples;
    private long segmentMaxVideoPresentationTimeUs = -1L;
    private long segmentSecondMaxVideoPresentationTimeUs = -1L;
    private long totalSamples;
    private final RecordingFileSizeLimiter recordingFileSizeLimiter =
            new RecordingFileSizeLimiter();
    private Runnable recordingLimitListener = () -> {};
    private final Runnable storageFloorMonitor = this::monitorStorageFloor;
    private boolean storageFloorMonitorScheduled;
    private boolean storageFloorBreached;
    private long latestVideoTimestampUs = -1;
    private long segmentVideoCutoffUs = -1;
    private long videoTimelineOriginUs = -1;
    private long recordingSystemTimeOriginUs = -1;
    private long videoTimestampToSystemOffsetUs;
    private boolean videoTimestampClockReady;
    private boolean videoTimestampClockRealtime;
    private CountDownLatch firstSample = new CountDownLatch(0);
    private CountDownLatch audioCaptureReady = new CountDownLatch(0);
    private boolean audioCaptureStarted;
    private String callbackError;
    private boolean closed;
    private boolean codecReleased;
    private boolean inputSurfaceReleased;
    private boolean inputSuspended;



    @SuppressWarnings("java:S107") // Keep the public factory signature stable for pipeline callers.
    public static SharedAvcEncoder open(
            int width, int height, int framesPerSecond, Logger logger,
            String pipelineId, String callbackThreadName, boolean recycleCodec,
            int rotationDegrees, long preRecordGopDurationMillis,
            Supplier<GpsCoordinate> captureLocation) throws IOException {
        EncoderSelection selection = selectEncoder(width, height, framesPerSecond);
        HandlerThread callbackThread = new HandlerThread(
                Objects.requireNonNull(callbackThreadName, "callbackThreadName"));
        callbackThread.start();
        MediaCodec codec = null;
        Surface inputSurface = null;
        SharedAvcEncoder encoder = null;
        try {
            codec = acquireCodec(selection.codecName(), recycleCodec);
            encoder = new SharedAvcEncoder(
                    logger, pipelineId, selection.codecName(), recycleCodec,
                    callbackThread, codec, width, height, framesPerSecond,
                    selection.bitrate(), CameraOrientation.normalize(rotationDegrees),
                    preRecordGopDurationMillis, captureLocation);
            inputSurface = encoder.inputSurface;
            encoder.primeAudioCodec();
            return encoder;
        } catch (IOException | RuntimeException error) {
            if (encoder != null) encoder.releaseAudioCodec();
            if (inputSurface != null) inputSurface.release();
            if (codec != null) {
                try {
                    codec.release();
                } catch (RuntimeException ignored) {
                    // Preserve the original initialization failure if codec cleanup also fails.
                }
            }
            callbackThread.quitSafely();
            throw error;
        }
    }

    @SuppressWarnings("java:S6885") // Math.clamp is unavailable on the API-26 runtime target.
    private SharedAvcEncoder(Logger logger, String pipelineId, String codecName,
            boolean recycleCodec, HandlerThread callbackThread, MediaCodec codec,
            int width, int height, int framesPerSecond, int bitrate, int rotationDegrees,
            long preRecordGopDurationMillis, Supplier<GpsCoordinate> captureLocation) {
        this.logger = Objects.requireNonNull(logger, "logger");
        microphoneCapture = SharedMicrophoneCapture.process(logger);
        this.pipelineId = Objects.requireNonNull(pipelineId, "pipelineId");
        this.codecName = Objects.requireNonNull(codecName, "codecName");
        this.recycleCodec = recycleCodec;
        videoBitrateBitsPerSecond = bitrate;
        this.captureLocation = Objects.requireNonNull(captureLocation, "captureLocation");
        if (preRecordGopDurationMillis < 0L
                || preRecordGopDurationMillis > Long.MAX_VALUE / 1_000L) {
            throw new IllegalArgumentException("invalid pre-record GOP duration");
        }
        preRecordGopDurationUs = preRecordGopDurationMillis * 1_000L;
        if (framesPerSecond <= 0) throw new IllegalArgumentException("invalid frame rate");
        nominalVideoFrameDurationUs = Math.max(
                1L, Math.round(1_000_000.0 / framesPerSecond));
        this.rotationDegrees = rotationDegrees;
        maxPendingVideoGopBytes = Math.min(MAX_PENDING_VIDEO_GOP_BYTES,
                Math.max(MIN_PENDING_VIDEO_GOP_BYTES, bitrate / 4));
        videoSampleAssembler = new VideoSampleAssembler(maxPendingVideoGopBytes);
        this.callbackThread = callbackThread;
        callbackHandler = new Handler(callbackThread.getLooper());
        this.codec = codec;
        codec.setCallback(new MediaCodec.Callback() {
            @Override public void onInputBufferAvailable(MediaCodec codec, int index) {
                // Surface-input encoders never provide input buffers to the application.
            }

            @Override public void onOutputBufferAvailable(
                    MediaCodec codec, int index, MediaCodec.BufferInfo info) {
                handleOutput(index, info);
            }

            @Override public void onError(MediaCodec codec, MediaCodec.CodecException error) {
                synchronized (lock) {
                    callbackError = "codec_callback:" + error.getDiagnosticInfo();
                    outputFormatReady.countDown();
                    firstSample.countDown();
                }
                SharedAvcEncoder.this.logger.warn(LogCategory.RECORDING,
                        "unspecified", null, PIPELINE_LOG_FIELD + pipelineId + " stage=encoder_callback"
                                + " outcome=codec_error",
                        error);
            }

            @Override public void onOutputFormatChanged(
                    MediaCodec codec, MediaFormat format) {
                Runnable limitListener = null;
                synchronized (lock) {
                    format.setInteger(MediaFormat.KEY_ROTATION, rotationDegrees);
                    outputFormat = format;
                    outputFormatReady.countDown();
                    if (acceptingSegment) {
                        limitListener = startMuxerLocked();
                        scheduleStorageFloorMonitorLocked();
                    }
                }
                if (limitListener != null) runLimitListener(limitListener);
            }
        }, callbackHandler);
        MediaFormat format = MediaFormat.createVideoFormat(MIME_TYPE, width, height);
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        format.setInteger(MediaFormat.KEY_BIT_RATE, bitrate);
        format.setInteger(MediaFormat.KEY_FRAME_RATE, framesPerSecond);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, I_FRAME_INTERVAL_SECONDS);
        Surface createdInputSurface = null;
        try {
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            createdInputSurface = codec.createInputSurface();
            inputSurface = createdInputSurface;
            codec.start();
        } catch (RuntimeException error) {
            if (createdInputSurface != null) createdInputSurface.release();
            throw error;
        }
    }

    public Surface inputSurface() {
        return inputSurface;
    }

    public long recordingBitrateBitsPerSecond(boolean includeAudio) {
        return totalRecordingBitrateBitsPerSecond(videoBitrateBitsPerSecond, includeAudio);
    }

    static long recordingBitrateBitsPerSecond(
            int width, int height, int framesPerSecond, boolean includeAudio)
            throws IOException {
        return totalRecordingBitrateBitsPerSecond(
                selectEncoder(width, height, framesPerSecond).bitrate(), includeAudio);
    }

    private static long totalRecordingBitrateBitsPerSecond(
            int videoBitrateBitsPerSecond, boolean includeAudio) {
        return Math.addExact(videoBitrateBitsPerSecond,
                includeAudio ? AUDIO_BIT_RATE : 0L);
    }

    public void observeVideoFrameTimestamp(
            long timestampNanos, boolean elapsedRealtimeClock) {
        if (timestampNanos <= 0L) return;
        long timestampUs = timestampNanos / 1_000L;
        long systemTimeUs = SystemClock.elapsedRealtimeNanos() / 1_000L;
        synchronized (lock) {
            if (closed) return;
            latestVideoTimestampUs = timestampUs;
            videoTimestampClockRealtime = elapsedRealtimeClock;
            videoTimestampToSystemOffsetUs = elapsedRealtimeClock
                    ? 0L : systemTimeUs - timestampUs;
            videoTimestampClockReady = true;
        }
    }

    public boolean awaitOutputFormat(long timeoutMillis) throws InterruptedException {
        if (!outputFormatReady.await(Math.max(1, timeoutMillis), TimeUnit.MILLISECONDS)) {
            return false;
        }
        synchronized (lock) {
            return outputFormat != null && callbackError == null;
        }
    }

    public void requestKeyFrame() {
        synchronized (lock) {
            ensureOpen();
            Bundle parameters = new Bundle();
            parameters.putInt(MediaCodec.PARAMETER_KEY_REQUEST_SYNC_FRAME, 0);
            codec.setParameters(parameters);
        }
    }

    public void suspendInput() {
        synchronized (lock) {
            ensureOpen();
            if (inputSuspended) return;
            Bundle parameters = new Bundle();
            parameters.putInt(MediaCodec.PARAMETER_KEY_SUSPEND, 1);
            try {
                codec.setParameters(parameters);
                inputSuspended = true;
            } catch (RuntimeException error) {
                logger.warn(LogCategory.RECORDING, "unspecified", null, "Suspend idle video encoder failed. Next recording may wait for a key frame"
                        + PIPELINE_CONTEXT + pipelineId + ".", error);
            }
        }
    }

    private void resumeInput() {
        synchronized (lock) {
            ensureOpen();
            if (!inputSuspended) return;
            Bundle parameters = new Bundle();
            parameters.putInt(MediaCodec.PARAMETER_KEY_SUSPEND, 0);
            codec.setParameters(parameters);
            inputSuspended = false;
        }
    }

    public boolean setRotation(int rotationDegrees) {
        synchronized (lock) {
            ensureOpen();
            if (acceptingSegment || muxerStarted) return false;
            this.rotationDegrees = CameraOrientation.normalize(rotationDegrees);
            if (outputFormat != null) {
                outputFormat.setInteger(MediaFormat.KEY_ROTATION, this.rotationDegrees);
            }
            return true;
        }
    }

    public long totalSamples() {
        synchronized (lock) {
            return totalSamples;
        }
    }

    public String callbackError() {
        synchronized (lock) {
            return callbackError;
        }
    }


    private void primeAudioCodec() {
        synchronized (lock) {
            if (closed || audioCodec != null) return;
        }
        long startedAt = System.nanoTime() / 1_000_000L;
        try {
            PrimedAudioCodec prepared = openPrimedAudioCodec();
            boolean accepted;
            synchronized (lock) {
                accepted = !closed && audioCodec == null;
                if (accepted) {
                    audioCodec = prepared.codec();
                    primedAudioOutputFormat = prepared.outputFormat();
                }
            }
            if (!accepted) {
                releaseAudio(prepared.codec(), null);
                return;
            }
            logger.info(LogCategory.RECORDING, "unspecified", "Prime AAC encoder success" + PIPELINE_CONTEXT + pipelineId
                    + ELAPSED_LOG_FRAGMENT + (System.nanoTime() / 1_000_000L - startedAt) + " ms."
                    + " Microphone remains closed until recording.");
        } catch (IOException | RuntimeException error) {
            logger.warn(LogCategory.RECORDING, "unspecified", null, "Prime AAC encoder failed. Recording will retry AAC setup on demand."
                    + " Pipeline: " + pipelineId + ". Reason: " + message(error) + ".", error);
        }
    }

    private static PrimedAudioCodec openPrimedAudioCodec() throws IOException {
        MediaCodec prepared = null;
        try {
            prepared = MediaCodec.createEncoderByType(AUDIO_MIME_TYPE);
            MediaFormat format = MediaFormat.createAudioFormat(
                    AUDIO_MIME_TYPE, AUDIO_SAMPLE_RATE, AUDIO_CHANNEL_COUNT);
            format.setInteger(MediaFormat.KEY_AAC_PROFILE,
                    MediaCodecInfo.CodecProfileLevel.AACObjectLC);
            format.setInteger(MediaFormat.KEY_BIT_RATE, AUDIO_BIT_RATE);
            format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, AUDIO_PRIME_BYTES);
            prepared.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            prepared.start();
            MediaFormat output = primeAudioOutputFormat(prepared);
            prepared.flush();
            return new PrimedAudioCodec(prepared, output);
        } catch (IOException | RuntimeException error) {
            releaseAudio(prepared, null);
            throw error;
        }
    }

    private static MediaFormat primeAudioOutputFormat(MediaCodec codec) {
        int inputIndex = codec.dequeueInputBuffer(AUDIO_PRIME_TIMEOUT_MILLIS * 1_000L);
        if (inputIndex < 0) {
            throw new IllegalStateException("audio_prime_input_unavailable:" + inputIndex);
        }
        ByteBuffer input = codec.getInputBuffer(inputIndex);
        if (input == null) throw new IllegalStateException("audio_prime_input_missing");
        input.clear();
        int bytes = Math.min(input.remaining(), AUDIO_PRIME_BYTES);
        for (int index = 0; index < bytes; index++) input.put((byte) 0);
        codec.queueInputBuffer(inputIndex, 0, bytes, 0L, 0);
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        long deadline = System.nanoTime() + AUDIO_PRIME_TIMEOUT_MILLIS * 1_000_000L;
        while (System.nanoTime() < deadline) {
            long remainingMicros = Math.max(1L,
                    (deadline - System.nanoTime()) / 1_000L);
            int outputIndex = codec.dequeueOutputBuffer(info,
                    Math.min(remainingMicros, 10_000L));
            if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                return codec.getOutputFormat();
            }
            if (outputIndex >= 0) {
                codec.releaseOutputBuffer(outputIndex, false);
            }
        }
        throw new IllegalStateException("audio_prime_format_timeout");
    }

    private void releaseAudioCodec() {
        MediaCodec current;
        synchronized (lock) {
            current = audioCodec;
            audioCodec = null;
            primedAudioOutputFormat = null;
        }
        releaseAudio(current, null);
    }

    public void begin(File outputFile) throws IOException {
        begin(outputFile, 0L, () -> {}, false);
    }

    public void begin(File outputFile, long fileSizeLimitBytes, Runnable limitListener)
            throws IOException {
        begin(outputFile, fileSizeLimitBytes, limitListener, false);
    }

    public void begin(File outputFile, long fileSizeLimitBytes, Runnable limitListener,
            boolean includeAudio) throws IOException {
        begin(Objects.requireNonNull(outputFile, "outputFile"), null,
                fileSizeLimitBytes, limitListener, includeAudio, true);
    }

    public void begin(DcamRecordingOutput recordingOutput, long fileSizeLimitBytes,
            Runnable limitListener, boolean includeAudio) throws IOException {
        DcamRecordingOutput output = Objects.requireNonNull(
                recordingOutput, "recordingOutput");
        if (!output.isOpen()) throw new IOException("Recording output is closed.");
        begin(output.file(), output, fileSizeLimitBytes, limitListener, includeAudio, false);
    }

    @SuppressWarnings({"java:S6541", "java:S3776"})
    // Keep initialization, lock ownership, and rollback in one lifecycle transaction.
    private void begin(
            File outputFile,
            DcamRecordingOutput preparedOutput,
            long fileSizeLimitBytes,
            Runnable limitListener,
            boolean includeAudio,
            boolean outputOwned) throws IOException {
        Objects.requireNonNull(limitListener, "limitListener");
        if (fileSizeLimitBytes < 0L) {
            throw new IllegalArgumentException("fileSizeLimitBytes must be non-negative");
        }
        long finalizationReserveBytes = preparedOutput == null
                ? RecordingFileSizeLimiter.MP4_FINALIZATION_RESERVE_BYTES
                : Math.max(RecordingFileSizeLimiter.MP4_FINALIZATION_RESERVE_BYTES,
                        preparedOutput.finalizationReserveBytes());
        long startedAt = SystemClock.elapsedRealtime();
        long stateReadyAt;
        CountDownLatch outputReady;
        synchronized (lock) {
            ensureOpen();
            if (acceptingSegment || muxer != null) {
                throw new IllegalStateException("encoder segment already active");
            }
            if (callbackError != null) {
                throw new IllegalStateException(callbackError);
            }
            if (audioThread != null) {
                throw new IllegalStateException("audio_encoder_already_active");
            }
            segmentFile = outputFile;
            gpsRouteEnabled = true;
            lastGpsCoordinate = null;
            gpsLookupFailureLogged = false;
            segmentLocation = currentCaptureLocation();
            segmentSamples = 0;
            seekIndexReserved = false;
            resetSegmentDurationLocked();
            audioRequired = includeAudio;
            audioOutputFormat = includeAudio ? primedAudioOutputFormat : null;
            audioTrackIndex = -1;
            audioStopRequested = false;
            audioStopTargetFrames = 0L;
            audioCaptureStopped = false;
            audioMuxerWritesFinished = new CountDownLatch(includeAudio ? 1 : 0);
            recordingFileSizeLimiter.resetForMp4(
                    fileSizeLimitBytes, finalizationReserveBytes);
            cancelStorageFloorMonitorLocked();
            storageFloorBreached = false;
            recordingLimitListener = limitListener;
            segmentVideoCutoffUs = segmentVideoCutoffUs(
                    latestVideoTimestampUs, preRecordGopDurationUs);
            videoTimelineOriginUs = -1;
            recordingSystemTimeOriginUs = -1;
            firstSample = new CountDownLatch(1);
            audioCaptureStarted = !includeAudio;
            audioCaptureReady = new CountDownLatch(includeAudio ? 1 : 0);
            segmentOutputReady = new CountDownLatch(1);
            outputReady = segmentOutputReady;
            waitingForKeyFrame = true;
            clearPendingVideoGopLocked();
            videoSampleAssembler.clear();
            clearMalformedVideoRecoveryLocked();
            clearRecentVideoSamplesLocked();
            acceptingSegment = true;
            trackIndex = -1;
            muxerStarted = false;
            stateReadyAt = SystemClock.elapsedRealtime();
        }
        DcamRecordingOutput output = preparedOutput;
        try {
            if (includeAudio) startAudioAsync();
            long audioLaunchedAt = SystemClock.elapsedRealtime();
            requestKeyFrame();
            long keyFrameRequestedAt = SystemClock.elapsedRealtime();
            resumeInput();
            long inputResumedAt = SystemClock.elapsedRealtime();
            if (output == null) output = DcamRecordingOutput.openPlain(outputFile);
            long outputOpenedAt = SystemClock.elapsedRealtime();
            DcamFragmentedMp4Layout.OutputChannel nextMuxerChannel =
                    DcamFragmentedMp4Layout.offsetAwareChannel(output);
            FragmentedMp4Muxer nextMuxer = new FragmentedMp4Muxer.Builder(nextMuxerChannel)
                    .setFragmentDurationMs(FRAGMENT_DURATION_MILLIS)
                    .build();
            long containerCreatedAt = SystemClock.elapsedRealtime();
            Runnable limitListenerToRun;
            synchronized (lock) {
                muxer = nextMuxer;
                muxerOutput = output;
                muxerOutputOwned = outputOwned;
                muxerChannel = nextMuxerChannel;
                output = null;
                limitListenerToRun = startMuxerLocked();
                outputReady.countDown();
                scheduleStorageFloorMonitorLocked();
            }
            if (limitListenerToRun != null) runLimitListener(limitListenerToRun);
            long completedAt = SystemClock.elapsedRealtime();
            logger.info(LogCategory.RECORDING, "unspecified", "Begin recording encoder segment success. Segment setup: "
                    + (stateReadyAt - startedAt) + " ms. Audio thread launch: "
                    + (audioLaunchedAt - stateReadyAt) + " ms. Key-frame request: "
                    + (keyFrameRequestedAt - audioLaunchedAt) + " ms. Input resume: "
                    + (inputResumedAt - keyFrameRequestedAt) + " ms. Final output open: "
                    + (outputOpenedAt - inputResumedAt) + " ms. MP4 container create: "
                    + (containerCreatedAt - outputOpenedAt) + " ms. Container attach: "
                    + (completedAt - containerCreatedAt) + TOTAL_DURATION_LOG_FRAGMENT
                    + (completedAt - startedAt) + PIPELINE_DURATION_LOG_FRAGMENT + pipelineId
                    + ". Audio: " + includeAudio + ".");
        } catch (IOException | RuntimeException error) {
            outputReady.countDown();
            if (output != null && output.isOpen()) {
                try {
                    output.close();
                } catch (IOException closeFailure) {
                    error.addSuppressed(closeFailure);
                }
            }
            discard();
            throw error;
        }
    }
    public boolean awaitFirstSample(long timeoutMillis) throws InterruptedException {
        CountDownLatch latch;
        synchronized (lock) {
            latch = firstSample;
        }
        return latch.await(Math.max(1, timeoutMillis), TimeUnit.MILLISECONDS)
                && segmentSampleCount() > 0;
    }

    public boolean awaitAudioCaptureReady(long timeoutMillis) throws InterruptedException {
        CountDownLatch latch;
        synchronized (lock) {
            if (!audioRequired) return true;
            latch = audioCaptureReady;
        }
        if (!latch.await(Math.max(1, timeoutMillis), TimeUnit.MILLISECONDS)) return false;
        synchronized (lock) {
            return audioCaptureStarted && callbackError == null;
        }
    }

    public CameraResolution encodedVideoResolution() {
        synchronized (lock) {
            if (outputFormat == null
                    || !outputFormat.containsKey(MediaFormat.KEY_WIDTH)
                    || !outputFormat.containsKey(MediaFormat.KEY_HEIGHT)) return null;
            return new CameraResolution(
                    outputFormat.getInteger(MediaFormat.KEY_WIDTH),
                    outputFormat.getInteger(MediaFormat.KEY_HEIGHT));
        }
    }

    public void pause() {
        long startedAt = SystemClock.elapsedRealtime();
        synchronized (lock) {
            acceptingSegment = false;
            cancelStorageFloorMonitorLocked();
        }
        suspendInput();
        long inputSuspendedAt = SystemClock.elapsedRealtime();
        signalAudioStop();
        long audioStopRequestedAt = SystemClock.elapsedRealtime();
        synchronized (lock) {
            clearPendingVideoGopLocked();
            videoSampleAssembler.clear();
            clearMalformedVideoRecoveryLocked();
        }
        logger.info(LogCategory.RECORDING, "unspecified", "Pause recording encoder input success. Input suspend: "
                + (inputSuspendedAt - startedAt) + " ms. Audio stop requested: "
                + (audioStopRequestedAt - inputSuspendedAt) + TOTAL_DURATION_LOG_FRAGMENT
                + (audioStopRequestedAt - startedAt) + PIPELINE_DURATION_LOG_FRAGMENT + pipelineId
                + ".");
    }

    public void discard() {
        synchronized (lock) {
            acceptingSegment = false;
            cancelStorageFloorMonitorLocked();
        }
        suspendInput();
        stopAudioCapture();
        synchronized (lock) {
            closeMuxerLocked(true);
            segmentFile = null;
            segmentSamples = 0;
            resetSegmentDurationLocked();
            recordingFileSizeLimiter.reset(0L);

            storageFloorBreached = false;
            recordingLimitListener = () -> {};
            segmentVideoCutoffUs = -1;
            videoTimelineOriginUs = -1;
            recordingSystemTimeOriginUs = -1;
            audioCaptureStarted = false;
            waitingForKeyFrame = false;
            clearPendingVideoGopLocked();
            videoSampleAssembler.clear();
            clearMalformedVideoRecoveryLocked();
            firstSample.countDown();
        }
    }

    public Segment finish() {
        long startedAt = SystemClock.elapsedRealtime();
        synchronized (lock) {
            acceptingSegment = false;
            cancelStorageFloorMonitorLocked();
        }
        boolean audioStopped = stopAudioCapture();
        long audioStoppedAt = SystemClock.elapsedRealtime();
        Segment result;
        synchronized (lock) {
            File file = segmentFile;
            long samples = segmentSamples;
            long durationUs = segmentDurationUs(
                    samples, segmentMaxVideoPresentationTimeUs,
                    segmentSecondMaxVideoPresentationTimeUs, nominalVideoFrameDurationUs);
            if (!audioStopped) {
                String detail = callbackError == null ? "audio_encoder_stop_timeout" : callbackError;
                result = new Segment(file, samples, durationUs, detail);
            } else {
                boolean finalized = closeMuxerLocked(false);
                String detail = callbackError;
                if (detail == null) {
                    detail = finalized ? "segment_finalized" : "muxer_close_failed";
                }
                segmentFile = null;
                segmentSamples = 0;
                resetSegmentDurationLocked();
                recordingFileSizeLimiter.reset(0L);
                storageFloorBreached = false;
                recordingLimitListener = () -> {};
                segmentVideoCutoffUs = -1;
                videoTimelineOriginUs = -1;
                recordingSystemTimeOriginUs = -1;
                audioCaptureStarted = false;
                waitingForKeyFrame = false;
                clearPendingVideoGopLocked();
                videoSampleAssembler.clear();
                clearMalformedVideoRecoveryLocked();
                firstSample.countDown();
                result = new Segment(file, samples, durationUs, detail);
            }
        }
        long completedAt = SystemClock.elapsedRealtime();
        logger.info(LogCategory.RECORDING, "unspecified", "Finalize recording encoder segment "
                + (audioStopped && "segment_finalized".equals(result.detail())
                        ? "success" : "failed")
                + ". Audio stop and drain: " + (audioStoppedAt - startedAt)
                + " ms. Container close: " + (completedAt - audioStoppedAt)
                + TOTAL_DURATION_LOG_FRAGMENT + (completedAt - startedAt)
                + PIPELINE_DURATION_LOG_FRAGMENT + pipelineId + ".");
        return result;
    }

    private long segmentSampleCount() {
        synchronized (lock) {
            return segmentSamples;
        }
    }

    private void awaitSegmentOutputReady() throws InterruptedException {
        CountDownLatch outputReady;
        synchronized (lock) {
            outputReady = segmentOutputReady;
        }
        outputReady.await();
    }

    private void startAudioAsync() {
        Thread thread = new Thread(this::prepareAndRunAudio, "dcam-video-audio");
        thread.setDaemon(true);
        synchronized (lock) {
            ensureOpen();
            if (audioThread != null) {
                throw new IllegalStateException("audio_encoder_already_active");
            }
            audioThread = thread;
        }
        try {
            thread.start();
        } catch (RuntimeException error) {
            synchronized (lock) {
                if (audioThread == thread) audioThread = null;
            }
            throw new IllegalStateException("audio_encoder_start", error);
        }
    }

    @SuppressWarnings({"java:S6541", "java:S3776"})
    // Keep audio startup, handoff, and teardown in one thread-owned transaction.
    private void prepareAndRunAudio() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO);
        long startedAt = System.nanoTime() / 1_000_000L;
        MediaCodec runningCodec;
        synchronized (lock) {
            runningCodec = audioCodec;
        }
        SharedMicrophoneCapture.Subscription runningMicrophone = null;
        try {
            runningMicrophone = microphoneCapture.subscribe();
            long captureStartedSystemTimeUs = runningMicrophone.startedAtSystemTimeUs();
            if (audioStopRequested) return;
            if (runningCodec == null) {
                PrimedAudioCodec prepared = openPrimedAudioCodec();
                boolean accepted;
                Runnable limitListener;
                synchronized (lock) {
                    accepted = !audioStopRequested && !closed;
                    limitListener = null;
                    if (accepted) {
                        runningCodec = prepared.codec();
                        audioCodec = runningCodec;
                        audioOutputFormat = prepared.outputFormat();
                        limitListener = startMuxerLocked();
                    }
                }
                if (!accepted) {
                    releaseAudio(prepared.codec(), null);
                    return;
                }
                if (limitListener != null) runLimitListener(limitListener);
                logger.info(LogCategory.RECORDING, "unspecified", "Prepare AAC encoder on recording demand success"
                        + PIPELINE_CONTEXT + pipelineId + ELAPSED_LOG_FRAGMENT
                        + (System.nanoTime() / 1_000_000L - startedAt) + " ms.");
            }
            runningCodec.flush();
            synchronized (lock) {
                if (audioStopRequested) return;
                microphoneSubscription = runningMicrophone;
                audioCaptureStarted = true;
                audioCaptureReady.countDown();
            }
            logger.info(LogCategory.RECORDING, "unspecified", "Attach video AAC encoder to shared microphone success"
                    + PIPELINE_CONTEXT + pipelineId + ELAPSED_LOG_FRAGMENT
                    + (System.nanoTime() / 1_000_000L - startedAt) + " ms.");
            awaitSegmentOutputReady();
            if (audioStopRequested) return;
            runAudio(runningCodec, runningMicrophone, captureStartedSystemTimeUs);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            if (!audioStopRequested) audioFailure("audio_encoder_prepare", error);
        } catch (IOException | RuntimeException error) {
            if (!audioStopRequested) audioFailure("audio_encoder_prepare", error);
        } finally {
            if (runningMicrophone != null) runningMicrophone.close();
            MediaCodec finishedCodec = null;
            boolean primeNextRecording;
            Thread currentThread = Thread.currentThread();
            synchronized (lock) {
                if (microphoneSubscription == runningMicrophone) microphoneSubscription = null;
                if (audioCodec == runningCodec) {
                    finishedCodec = audioCodec;
                    audioCodec = null;
                    primedAudioOutputFormat = null;
                }
                if (audioThread == currentThread) {
                    audioCaptureStopped = true;
                    audioMuxerWritesFinished.countDown();
                }
                primeNextRecording = !closed && callbackError == null;
            }
            releaseAudio(finishedCodec, null);
            if (primeNextRecording) primeAudioCodec();
            synchronized (lock) {
                if (audioThread == currentThread) audioThread = null;
            }
        }
    }

    @SuppressWarnings({"java:S6541", "java:S3776", "java:S6885"})
    // Keep audio input/output draining in one ordered codec transaction.
    private void runAudio(MediaCodec runningCodec,
            SharedMicrophoneCapture.Subscription runningMicrophone,
            long captureStartedSystemTimeUs) {
        MediaCodec.BufferInfo outputInfo = new MediaCodec.BufferInfo();
        long recordingOriginSystemTimeUs =
                awaitRecordingSystemTimeOriginUs(captureStartedSystemTimeUs);
        long alignmentFrames = audioAlignmentFrames(
                captureStartedSystemTimeUs - recordingOriginSystemTimeUs);
        long audioStartOffsetFrames = Math.max(0L, alignmentFrames);
        long discardFrames = Math.max(0L, -alignmentFrames);
        logger.info(LogCategory.RECORDING, "unspecified", "Align recording audio to first video sample. Audio start offset: "
                + audioPresentationTimeUs(audioStartOffsetFrames) / 1_000L
                + " ms. Dropped microphone lead: "
                + audioPresentationTimeUs(discardFrames) / 1_000L
                + PIPELINE_DURATION_LOG_FRAGMENT + pipelineId + ".");
        long submittedFrames = audioStartOffsetFrames;
        boolean inputEnded = false;
        boolean outputEnded = false;
        try {
            while (!outputEnded) {
                if (!inputEnded) {
                    int inputIndex = runningCodec.dequeueInputBuffer(10_000);
                    if (inputIndex >= 0) {
                        ByteBuffer input = runningCodec.getInputBuffer(inputIndex);
                        if (input == null) {
                            throw new IllegalStateException("audio_input_buffer_missing");
                        }
                        input.clear();
                        long remainingStopFrames = audioStopRequested
                                ? Math.max(0L, audioStopTargetFrames - submittedFrames)
                                : Long.MAX_VALUE;
                        if (remainingStopFrames == 0L) {
                            runningCodec.queueInputBuffer(inputIndex, 0, 0,
                                    audioPresentationTimeUs(submittedFrames),
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            inputEnded = true;
                        } else {
                            int bytes = readMicrophoneInput(runningMicrophone, input);
                            if (bytes <= 0) {
                                if (!audioStopRequested) {
                                    throw new IllegalStateException(
                                            "audio_record_read_failed:" + bytes);
                                }
                                long silenceFrames = audioSilenceFrameCount(
                                        audioStopTargetFrames, submittedFrames);
                                if (silenceFrames == 0L) {
                                    runningCodec.queueInputBuffer(inputIndex, 0, 0,
                                            audioPresentationTimeUs(submittedFrames),
                                            MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                                    inputEnded = true;
                                } else {
                                    int writeFrames = (int) Math.min(silenceFrames,
                                            input.remaining() / AUDIO_BYTES_PER_FRAME);
                                    if (writeFrames <= 0) {
                                        throw new IllegalStateException("audio_silence_buffer_unavailable");
                                    }
                                    int writeBytes = writeFrames * AUDIO_BYTES_PER_FRAME;
                                    for (int offset = 0; offset < writeBytes; offset++) {
                                        input.put((byte) 0);
                                    }
                                    runningCodec.queueInputBuffer(inputIndex, 0, writeBytes,
                                            audioPresentationTimeUs(submittedFrames), 0);
                                    submittedFrames += writeFrames;
                                }
                            } else {
                                long frames = bytes / AUDIO_BYTES_PER_FRAME;
                                int skippedFrames = (int) Math.min(discardFrames, frames);
                                int offset = skippedFrames * AUDIO_BYTES_PER_FRAME;
                                long writeFrames = frames - skippedFrames;
                                if (audioStopRequested) {
                                    writeFrames = Math.min(writeFrames, Math.max(
                                            0L, audioStopTargetFrames - submittedFrames));
                                }
                                int writeBytes = (int) writeFrames * AUDIO_BYTES_PER_FRAME;
                                discardFrames -= skippedFrames;
                                if (writeBytes == 0 && audioStopRequested) {
                                    runningCodec.queueInputBuffer(inputIndex, 0, 0,
                                            audioPresentationTimeUs(submittedFrames),
                                            MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                                    inputEnded = true;
                                } else {
                                    runningCodec.queueInputBuffer(inputIndex, offset, writeBytes,
                                            audioPresentationTimeUs(submittedFrames), 0);
                                    submittedFrames += writeFrames;
                                }
                            }
                        }
                    }
                }
                if (drainAudioOutput(runningCodec, outputInfo)) outputEnded = true;
            }
        } catch (RuntimeException error) {
            audioFailure("audio_encoder", error);
        }
    }

    private int readMicrophoneInput(
            SharedMicrophoneCapture.Subscription runningMicrophone, ByteBuffer input) {
        try {
            return runningMicrophone.read(input);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            if (audioStopRequested) return -1;
            throw new IllegalStateException("shared_microphone_read_interrupted", error);
        } catch (IOException error) {
            throw new IllegalStateException("shared_microphone_read_failed", error);
        }
    }

    private boolean drainAudioOutput(MediaCodec runningCodec, MediaCodec.BufferInfo outputInfo) {
        while (true) {
            int outputIndex = runningCodec.dequeueOutputBuffer(outputInfo, 0);
            if (outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER) return false;
            if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                Runnable limitListener = null;
                synchronized (lock) {
                    audioOutputFormat = runningCodec.getOutputFormat();
                    limitListener = startMuxerLocked();
                }
                if (limitListener != null) runLimitListener(limitListener);
            } else if (outputIndex >= 0) {
                handleAudioOutput(runningCodec, outputIndex, outputInfo);
                if ((outputInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) return true;
            }
        }
    }

    private void handleAudioOutput(MediaCodec runningCodec, int index,
            MediaCodec.BufferInfo sourceInfo) {
        Runnable limitListener = null;
        try {
            synchronized (lock) {
                ByteBuffer buffer = runningCodec.getOutputBuffer(index);
                if (buffer == null || sourceInfo.size <= 0
                        || (sourceInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0
                        || !muxerStarted || segmentFile == null) return;
                buffer.position(sourceInfo.offset);
                buffer.limit(sourceInfo.offset + sourceInfo.size);
                MediaCodec.BufferInfo writeInfo = new MediaCodec.BufferInfo();
                writeInfo.set(buffer.position(), sourceInfo.size, sourceInfo.presentationTimeUs,
                        sourceInfo.flags);
                RecordingFileSizeLimiter.Decision sizeDecision =
                        evaluateRecordingSampleLimitLocked(sourceInfo.size);
                if (sizeDecision.shouldWrite()) {
                    muxer.writeSampleData(audioTrackIndex, buffer,
                            MuxerUtil.getMuxerBufferInfoFromMediaCodecBufferInfo(writeInfo));
                    reserveSeekIndexLocked();
                }
                if (sizeDecision.shouldStop()) {
                    limitListener = stopForRecordingLimitLocked();
                }
            }
        } catch (MuxerException | RuntimeException error) {
            audioFailure("audio_muxer_write", error);
        } finally {
            try {
                runningCodec.releaseOutputBuffer(index, false);
            } catch (RuntimeException ignored) {
                // Audio encoder teardown can invalidate an already-consumed output buffer.
            }
        }
        if (limitListener != null) runLimitListener(limitListener);
    }


    private long fileBytesForNextSampleLocked() {
        long currentBytes;
        try {
            currentBytes = muxerOutput == null
                    ? segmentFile.length() : muxerOutput.estimatedPhysicalSize();
        } catch (IOException failure) {
            throw new IllegalStateException("recording_output_size_failed", failure);
        }
        if (seekIndexReserved) return currentBytes;
        int reserveBytes = DcamFragmentedMp4Layout.SEEK_INDEX_RESERVE_BYTES;
        return currentBytes > Long.MAX_VALUE - reserveBytes
                ? Long.MAX_VALUE : currentBytes + reserveBytes;
    }

    private RecordingFileSizeLimiter.Decision evaluateRecordingSampleLimitLocked(int sampleSize) {
        if (storageFloorBreached) return recordingFileSizeLimiter.forceStop();
        return recordingFileSizeLimiter.evaluateSample(sampleSize, fileBytesForNextSampleLocked());
    }

    private void scheduleStorageFloorMonitorLocked() {
        Handler handler = callbackHandler;
        if (handler == null || closed || !acceptingSegment || muxerOutput == null
                || storageFloorBreached || storageFloorMonitorScheduled) return;
        storageFloorMonitorScheduled = true;
        if (!handler.postDelayed(storageFloorMonitor, STORAGE_FLOOR_CHECK_INTERVAL_MILLIS)) {
            storageFloorMonitorScheduled = false;
        }
    }

    private void cancelStorageFloorMonitorLocked() {
        storageFloorMonitorScheduled = false;
        Handler handler = callbackHandler;
        if (handler != null) handler.removeCallbacks(storageFloorMonitor);
    }

    private void monitorStorageFloor() {
        Runnable limitListener = null;
        synchronized (lock) {
            storageFloorMonitorScheduled = false;
            if (!acceptingSegment || closed || muxerOutput == null || storageFloorBreached) return;
            if (!muxerStarted) {
                scheduleStorageFloorMonitorLocked();
            } else if (isStorageFloorBreachedLocked()) {
                limitListener = stopForRecordingLimitLocked();
            } else {
                scheduleStorageFloorMonitorLocked();
            }
        }
        if (limitListener != null) runLimitListener(limitListener);
    }

    private boolean isStorageFloorBreachedLocked() {
        if (storageFloorBreached) return true;
        DcamRecordingOutput output = muxerOutput;
        if (output == null) return false;
        long freeBytes;
        RuntimeException readFailure = null;
        try {
            freeBytes = output.availableBytes();
        } catch (RuntimeException error) {
            freeBytes = 0L;
            readFailure = error;
        }
        if (!CaptureStorageCapacityPolicy.isBelowCaptureSafetyFloor(freeBytes)) return false;
        storageFloorBreached = true;
        String reason = readFailure == null
                ? "recording-volume free space fell below capture safety floor"
                : "recording-volume free space could not be read";
        logger.warn(LogCategory.RECORDING, "unspecified", null, "Stop recording because " + reason + ". File: " + output.file().getName()
                + ". Free: " + freeBytes + " bytes. Floor: "
                + CaptureStorageCapacityPolicy.MIN_CAPTURE_FREE_BYTES + " bytes.", readFailure);
        return true;
    }

    private Runnable stopForRecordingLimitLocked() {
        recordingFileSizeLimiter.forceStop();
        acceptingSegment = false;
        audioStopTargetFrames = audioFrameCountForDurationUs(segmentDurationUs(
                segmentSamples, segmentMaxVideoPresentationTimeUs,
                segmentSecondMaxVideoPresentationTimeUs, nominalVideoFrameDurationUs));
        audioStopRequested = true;
        firstSample.countDown();
        return takeRecordingLimitListenerLocked();
    }

    private Runnable takeRecordingLimitListenerLocked() {
        Runnable listener = recordingLimitListener;
        recordingLimitListener = () -> {};
        return listener;
    }

    private void reserveSeekIndexLocked() throws MuxerException {
        if (seekIndexReserved) return;
        DcamFragmentedMp4Layout.OutputChannel output = muxerChannel;
        if (output == null) {
            throw new MuxerException("Recording output is unavailable.",
                    new IOException("Recording output is unavailable."));
        }
        try {
            output.reserveSeekIndex();
            seekIndexReserved = true;
        } catch (IOException | ArithmeticException error) {
            throw new MuxerException("Failed to reserve fragmented MP4 seek index.", error);
        }
    }

    private SharedMicrophoneCapture.Subscription signalAudioStop() {
        synchronized (lock) {
            if (audioCaptureStopped && audioThread == null && microphoneSubscription == null) {
                return null;
            }
            if (!audioStopRequested) {
                audioStopTargetFrames = audioFrameCountForDurationUs(segmentDurationUs(
                        segmentSamples, segmentMaxVideoPresentationTimeUs,
                        segmentSecondMaxVideoPresentationTimeUs, nominalVideoFrameDurationUs));
            }
            audioStopRequested = true;
            audioCaptureStarted = false;
            audioCaptureReady.countDown();
            segmentOutputReady.countDown();
            return microphoneSubscription;
        }
    }

    private void requestAudioStop() {
        SharedMicrophoneCapture.Subscription microphone = signalAudioStop();
        if (microphone != null) microphone.close();
    }

    private boolean stopAudioCapture() {
        return stopAudioCapture(AUDIO_STOP_TIMEOUT_MILLIS);
    }

    private boolean stopAudioCapture(long timeoutMillis) {
        requestAudioStop();
        CountDownLatch muxerWritesFinished;
        Thread thread;
        synchronized (lock) {
            if (audioCaptureStopped && audioMuxerWritesFinished.getCount() == 0L) return true;
            muxerWritesFinished = audioMuxerWritesFinished;
            thread = audioThread;
        }
        if (thread == null || thread == Thread.currentThread()) return true;
        if (await(muxerWritesFinished, timeoutMillis)) return true;
        boolean workerAliveBeforeInterrupt = thread.isAlive();
        thread.interrupt();
        synchronized (lock) {
            if (callbackError == null) callbackError = "audio_encoder_stop_timeout";
            firstSample.countDown();
        }
        logger.info(LogCategory.RECORDING, "unspecified", "Stop recording audio encoder failed because AAC muxer drain exceeded "
                + timeoutMillis + " ms. The audio worker was "
                + (workerAliveBeforeInterrupt ? "still running" : "no longer running")
                + " before interruption" + PIPELINE_CONTEXT + pipelineId + ".");
        return false;
    }

    private boolean stopAudioCaptureAndAwaitCleanup(long timeoutMillis) {
        long startedAt = SystemClock.elapsedRealtime();
        if (!stopAudioCapture(timeoutMillis)) return false;
        Thread thread;
        synchronized (lock) {
            thread = audioThread;
        }
        if (thread == null || thread == Thread.currentThread()) return true;
        return join(thread, Math.max(0L,
                timeoutMillis - (SystemClock.elapsedRealtime() - startedAt)));
    }
    private void audioFailure(String stage, Throwable error) {
        synchronized (lock) {
            if (callbackError == null) {
                callbackError = stage + ":" + error.getClass().getSimpleName();
            }
            acceptingSegment = false;
            audioStopRequested = true;
            audioCaptureStarted = false;
            audioCaptureReady.countDown();
            firstSample.countDown();
        }
        logger.warn(LogCategory.RECORDING, "unspecified", null, PIPELINE_LOG_FIELD + pipelineId + " stage=" + stage
                + " outcome=global_failure", error);
    }

    private static void releaseAudio(MediaCodec codec,
            SharedMicrophoneCapture.Subscription microphone) {
        if (microphone != null) microphone.close();
        if (codec != null) {
            try {
                codec.release();
            } catch (RuntimeException ignored) {
                // Subscription cleanup must continue when a codec rejects best-effort release.
            }
        }
    }

    private GpsCoordinate currentCaptureLocation() {
        try {
            return captureLocation.get();
        } catch (RuntimeException error) {
            if (!gpsLookupFailureLogged) {
                gpsLookupFailureLogged = true;
                logger.warn(LogCategory.RECORDING, "unspecified", null, "Recording GPS route lookup failed. Video continues without new "
                        + "GPS metadata until location becomes available.", error);
            }
            return null;
        }
    }

    static long segmentVideoCutoffUs(long latestVideoTimestampUs, long preRecordDurationUs) {
        return latestVideoTimestampUs < 0L
                ? -1L : Math.max(0L, latestVideoTimestampUs - preRecordDurationUs);
    }

    static boolean sampleAfterCutoff(long cutoffUs, long presentationTimeUs) {
        return cutoffUs < 0L || presentationTimeUs > cutoffUs;
    }

    static long videoFrameSystemTimeUs(boolean clockReady, boolean elapsedRealtimeClock,
            long timestampToSystemOffsetUs, long presentationTimeUs, long fallbackUs) {
        if (!clockReady) return fallbackUs;
        return elapsedRealtimeClock
                ? presentationTimeUs : presentationTimeUs + timestampToSystemOffsetUs;
    }

    static long timelinePresentationTimeUs(long timelineOriginUs, long segmentTimeUs) {
        return timelineOriginUs < 0L
                ? 0L : Math.max(0L, segmentTimeUs - timelineOriginUs);
    }

    static long segmentDurationUs(long sampleCount, long maximumPresentationTimeUs,
            long secondMaximumPresentationTimeUs, long nominalFrameDurationUs) {
        if (sampleCount <= 0L || maximumPresentationTimeUs < 0L) return 0L;
        long finalSampleDurationUs = Math.max(1L, nominalFrameDurationUs);
        if (sampleCount > 1L && secondMaximumPresentationTimeUs >= 0L) {
            long observedFrameDurationUs =
                    maximumPresentationTimeUs - secondMaximumPresentationTimeUs;
            if (observedFrameDurationUs > 0L) {
                finalSampleDurationUs = observedFrameDurationUs;
            }
        }
        return maximumPresentationTimeUs > Long.MAX_VALUE - finalSampleDurationUs
                ? Long.MAX_VALUE : maximumPresentationTimeUs + finalSampleDurationUs;
    }


    static Mp4LocationData mp4LocationData(GpsCoordinate coordinate) {
        Objects.requireNonNull(coordinate, "coordinate");
        return new Mp4LocationData(
                (float) coordinate.getLatitude(), (float) coordinate.getLongitude());
    }

    static boolean gpsCoordinateChanged(
            GpsCoordinate previous, GpsCoordinate current) {
        if (current == null) return false;
        return previous == null
                || Double.compare(previous.getLatitude(), current.getLatitude()) != 0
                || Double.compare(previous.getLongitude(), current.getLongitude()) != 0;
    }


    static long audioFrameCountForDurationUs(long durationUs) {
        if (durationUs <= 0L) return 0L;
        return Math.max(1L, Math.round(durationUs * AUDIO_SAMPLE_RATE / 1_000_000.0));
    }

    static long audioSilenceFrameCount(long targetFrames, long submittedFrames) {
        return Math.max(0L, targetFrames - submittedFrames);
    }

    static long audioAlignmentFrames(long alignmentUs) {
        return alignmentUs * AUDIO_SAMPLE_RATE / 1_000_000L;
    }

    private long awaitRecordingSystemTimeOriginUs(long fallbackUs) {
        CountDownLatch latch;
        synchronized (lock) {
            if (recordingSystemTimeOriginUs >= 0L) return recordingSystemTimeOriginUs;
            latch = firstSample;
        }
        await(latch, AUDIO_PRIME_TIMEOUT_MILLIS);
        synchronized (lock) {
            return recordingSystemTimeOriginUs >= 0L ? recordingSystemTimeOriginUs : fallbackUs;
        }
    }

    private static long audioPresentationTimeUs(long frames) {
        return frames * 1_000_000L / AUDIO_SAMPLE_RATE;
    }

    private void runLimitListener(Runnable listener) {
        try {
            listener.run();
        } catch (RuntimeException error) {
            logger.warn(LogCategory.RECORDING, "unspecified", null, PIPELINE_LOG_FIELD + pipelineId
                    + " stage=recording_limit_callback outcome=failed", error);
        }
    }
    @SuppressWarnings({"java:S6541", "java:S3776"})
    // Keep callback buffer ownership and muxer rollback in one synchronized transaction.
    private void handleOutput(int index, MediaCodec.BufferInfo sourceInfo) {
        Runnable limitListener = null;
        String recentSamples = "none";
        try {
            synchronized (lock) {
                ByteBuffer buffer = codec.getOutputBuffer(index);
                if (buffer == null || sourceInfo.size <= 0) return;
                if (!acceptingSegment) {
                    videoSampleAssembler.clear();
                    return;
                }
                if ((sourceInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) return;
                buffer.position(sourceInfo.offset);
                buffer.limit(sourceInfo.offset + sourceInfo.size);
                long presentationTimeUs = sourceInfo.presentationTimeUs;
                int flags = sourceInfo.flags;
                int sourceBufferCount = 1;
                if (videoSampleAssembler.hasPending()
                        || (flags & MediaCodec.BUFFER_FLAG_PARTIAL_FRAME) != 0) {
                    AssembledVideoSample assembled = videoSampleAssembler.append(
                            buffer, presentationTimeUs, flags);
                    if (assembled == null) return;
                    buffer = ByteBuffer.wrap(assembled.data());
                    presentationTimeUs = assembled.presentationTimeUs();
                    flags = assembled.flags();
                    sourceBufferCount = assembled.bufferCount();
                    if (!partialVideoSampleLogged) {
                        partialVideoSampleLogged = true;
                        logger.info(LogCategory.RECORDING, "unspecified", "Join split H.264 encoder output into one video sample success. "
                                + "Encoder buffers: " + sourceBufferCount + ". Bytes: "
                                + buffer.remaining() + PIPELINE_CONTEXT + pipelineId + CODEC_CONTEXT
                                + codecName + ".");
                    }
                    if (assembled.timestampChanged()
                            && !partialVideoTimestampMismatchLogged) {
                        partialVideoTimestampMismatchLogged = true;
                        logger.info(LogCategory.RECORDING, "unspecified", "Join split H.264 encoder output whose buffer timestamps "
                                + "differed. First timestamp retained" + PIPELINE_CONTEXT + pipelineId
                                + CODEC_CONTEXT + codecName + ".");
                    }
                }
                if (!sampleAfterCutoff(segmentVideoCutoffUs, presentationTimeUs)) return;
                long sampleSequence = ++nextVideoSampleSequence;
                int malformedByteOffset = invalidAnnexBOffset(buffer);
                if (malformedByteOffset >= 0) {
                    malformedVideoSamplesDropped++;
                    if (!malformedVideoRecoveryActive) {
                        malformedVideoRecoveryActive = true;
                        malformedVideoRecoveryStartedAtUs = presentationTimeUs;
                        logger.warn(LogCategory.RECORDING, "unspecified", null, "Drop malformed H.264 encoder sample before MP4 muxing. "
                                + "Video recording continues at the next valid key frame. Sample: #"
                                + sampleSequence + ". Timestamp: " + presentationTimeUs
                                + " us. Bytes: " + buffer.remaining() + ". Flags: 0x"
                                + Integer.toHexString(flags) + ". Encoder buffers: "
                                + sourceBufferCount + ". Invalid Annex-B offset: "
                                + malformedByteOffset + ". Nearby bytes: "
                                + annexBBytesAround(buffer, malformedByteOffset) + PIPELINE_CONTEXT
                                + pipelineId + CODEC_CONTEXT + codecName + ".", null);
                    }
                    waitingForKeyFrame = true;
                    return;
                }
                boolean keyFrame = (flags & MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0;
                if (waitingForKeyFrame && !keyFrame) {
                    if (malformedVideoRecoveryActive) dependentVideoSamplesDropped++;
                    return;
                }
                waitingForKeyFrame = false;
                if (malformedVideoRecoveryActive) {
                    long videoGapMillis = Math.max(
                            0L, presentationTimeUs - malformedVideoRecoveryStartedAtUs) / 1_000L;
                    logger.info(LogCategory.RECORDING, "unspecified", "Resume H.264 video muxing at a valid key frame after malformed "
                            + "encoder output. Dropped malformed samples: "
                            + malformedVideoSamplesDropped + ". Dropped dependent samples: "
                            + dependentVideoSamplesDropped + ". Video gap: " + videoGapMillis
                            + " ms. Recording continued" + PIPELINE_CONTEXT + pipelineId
                            + CODEC_CONTEXT + codecName + ".");
                    clearMalformedVideoRecoveryLocked();
                }
                rememberVideoSampleLocked(
                        sampleSequence, buffer, presentationTimeUs, flags, sourceBufferCount);
                if (!muxerStarted) {
                    bufferPendingVideoSampleLocked(
                            buffer, presentationTimeUs, flags, keyFrame);
                    return;
                }
                limitListener = writeVideoSampleLocked(buffer, presentationTimeUs, flags);
            }
        } catch (MuxerException | RuntimeException error) {
            synchronized (lock) {
                callbackError = "muxer_write:" + error.getClass().getSimpleName();
                acceptingSegment = false;
                audioStopRequested = true;
                videoSampleAssembler.clear();
                clearMalformedVideoRecoveryLocked();
                firstSample.countDown();
                recentSamples = recentVideoSamplesLocked();
            }
            logger.error(LogCategory.RECORDING, "unspecified", null, "Write encoded H.264 output to MP4 failed. Recording segment stopped "
                    + "to protect the container. Recent complete video samples, oldest first: "
                    + recentSamples + PIPELINE_CONTEXT + pipelineId + CODEC_CONTEXT + codecName
                    + ".", error);
        } finally {
            try {
                codec.releaseOutputBuffer(index, false);
            } catch (RuntimeException ignored) {
                // Codec shutdown can invalidate the callback buffer after muxer failure handling.
            }
        }
        if (limitListener != null) runLimitListener(limitListener);
    }

    private Runnable writeVideoSampleLocked(
            ByteBuffer buffer, long presentationTimeUs, int flags) throws MuxerException {
        if (videoTimelineOriginUs < 0L) {
            videoTimelineOriginUs = presentationTimeUs;
            recordingSystemTimeOriginUs = videoFrameSystemTimeUs(
                    videoTimestampClockReady, videoTimestampClockRealtime,
                    videoTimestampToSystemOffsetUs, presentationTimeUs,
                    SystemClock.elapsedRealtimeNanos() / 1_000L);
        }
        int sampleSize = buffer.remaining();
        MediaCodec.BufferInfo writeInfo = new MediaCodec.BufferInfo();
        writeInfo.set(
                buffer.position(),
                sampleSize,
                timelinePresentationTimeUs(
                        videoTimelineOriginUs, presentationTimeUs),
                flags);
        RecordingFileSizeLimiter.Decision sizeDecision =
                evaluateRecordingSampleLimitLocked(sampleSize);
        if (sizeDecision.shouldWrite()) {
            muxer.writeSampleData(trackIndex, buffer,
                    MuxerUtil.getMuxerBufferInfoFromMediaCodecBufferInfo(writeInfo));
            writeGpsSampleLocked(writeInfo.presentationTimeUs);
            reserveSeekIndexLocked();
            recordVideoPresentationTimeLocked(writeInfo.presentationTimeUs);
            segmentSamples++;
            totalSamples++;
            firstSample.countDown();
        }
        if (!sizeDecision.shouldStop()) return null;
        return stopForRecordingLimitLocked();
    }



    private void writeGpsSampleLocked(long presentationTimeUs) {
        if (!gpsRouteEnabled || muxerChannel == null) return;
        GpsCoordinate coordinate = currentCaptureLocation();
        if (!gpsCoordinateChanged(lastGpsCoordinate, coordinate)) return;
        try {
            muxerChannel.queueGpsRoutePoint(presentationTimeUs,
                    coordinate.getLatitude(), coordinate.getLongitude());
            lastGpsCoordinate = coordinate;
        } catch (RuntimeException error) {
            gpsRouteEnabled = false;
            logger.warn(LogCategory.RECORDING, "unspecified", null, "Stop embedding GPS route metadata because the MP4 output rejected a "
                    + "route point. Video recording continues.", error);
        }
    }
    private void bufferPendingVideoSampleLocked(
            ByteBuffer buffer, long presentationTimeUs, int flags, boolean keyFrame) {
        if (keyFrame) clearPendingVideoGopLocked();
        int size = buffer.remaining();
        if (size > maxPendingVideoGopBytes
                || pendingVideoGopBytes > maxPendingVideoGopBytes - size) {
            callbackError = "video_startup_gop_too_large";
            acceptingSegment = false;
            audioStopRequested = true;
            audioCaptureStarted = false;
            audioCaptureReady.countDown();
            firstSample.countDown();
            return;
        }
        byte[] data = new byte[size];
        buffer.duplicate().get(data);
        pendingVideoGop.add(new PendingVideoSample(data, presentationTimeUs, flags));
        pendingVideoGopBytes += size;
    }

    private Runnable flushPendingVideoGopLocked() throws MuxerException {
        if (pendingVideoGop.isEmpty()) return null;
        Runnable limitListener = null;
        int sampleIndex = 0;
        while (sampleIndex < pendingVideoGop.size()
                && acceptingSegment && limitListener == null) {
            PendingVideoSample sample = pendingVideoGop.get(sampleIndex++);
            limitListener = writeVideoSampleLocked(
                    ByteBuffer.wrap(sample.data()), sample.presentationTimeUs(), sample.flags());
        }
        clearPendingVideoGopLocked();
        return limitListener;
    }

    private void clearPendingVideoGopLocked() {
        pendingVideoGop.clear();
        pendingVideoGopBytes = 0;
    }

    static int invalidAnnexBOffset(ByteBuffer input) {
        ByteBuffer data = Objects.requireNonNull(input, "input").asReadOnlyBuffer().slice();
        if (!data.hasRemaining()) return -1;
        int nalStartCodeIndex = findAnnexBStartCodeOrInvalid(data, 0);
        if (nalStartCodeIndex < 0) return ~nalStartCodeIndex;
        int currentIndex = nalStartCodeIndex + 3;
        while (currentIndex < data.limit()) {
            currentIndex = findAnnexBNalEndIndex(data, currentIndex);
            if (currentIndex >= data.limit()) return -1;
            nalStartCodeIndex = findAnnexBStartCodeOrInvalid(data, currentIndex);
            if (nalStartCodeIndex < 0) return ~nalStartCodeIndex;
            if (nalStartCodeIndex >= data.limit()) return -1;
            currentIndex = nalStartCodeIndex + 3;
        }
        return -1;
    }

    @SuppressWarnings("java:S3776")
    private static int findAnnexBNalEndIndex(ByteBuffer input, int currentIndex) {
        while (currentIndex <= input.limit() - 4) {
            int fourBytes = input.getInt(currentIndex);
            if ((fourBytes & 0xffffff00) == 0
                    || (fourBytes & 0xffffff00) == 0x00000100) {
                return currentIndex;
            }
            if ((fourBytes & 0x00ffffff) == 0
                    || (fourBytes & 0x00ffffff) == 0x00000001) {
                return currentIndex + 1;
            }
            if ((fourBytes & 0x0000ffff) == 0) {
                currentIndex += 2;
            } else if ((fourBytes & 0x000000ff) == 0) {
                currentIndex += 3;
            } else {
                currentIndex += 4;
            }
        }
        if (currentIndex == input.limit() - 3) {
            short firstTwoBytes = input.getShort(currentIndex);
            byte lastByte = input.get(currentIndex + 2);
            if (firstTwoBytes == 0 && (lastByte == 0 || lastByte == 1)) {
                return currentIndex;
            }
        }
        return input.limit();
    }

    @SuppressWarnings("java:S3776")
    private static int findAnnexBStartCodeOrInvalid(ByteBuffer input, int currentIndex) {
        while (currentIndex <= input.limit() - 4) {
            int fourBytes = input.getInt(currentIndex);
            if ((fourBytes & 0xffffff00) == 0x00000100) return currentIndex;
            if ((fourBytes & 0xffffff00) != 0) {
                for (int byteIndex = 0; byteIndex < 3; byteIndex++) {
                    if (input.get(currentIndex + byteIndex) != 0) {
                        return ~(currentIndex + byteIndex);
                    }
                }
            }
            int lastByte = fourBytes & 0x000000ff;
            if (lastByte == 1) return currentIndex + 1;
            if (lastByte != 0) return ~(currentIndex + 3);
            currentIndex++;
        }
        if (currentIndex <= input.limit() - 3) {
            short firstTwoBytes = input.getShort(currentIndex);
            if (firstTwoBytes != 0) {
                return input.get(currentIndex) != 0 ? ~currentIndex : ~(currentIndex + 1);
            }
            byte lastByte = input.get(currentIndex + 2);
            if (lastByte == 1) return currentIndex;
            if (lastByte != 0) return ~(currentIndex + 2);
        } else {
            while (currentIndex < input.limit()) {
                if (input.get(currentIndex) != 0) return ~currentIndex;
                currentIndex++;
            }
        }
        return input.limit();
    }

    @SuppressWarnings("java:S6885") // Math.clamp is unavailable on the API-26 runtime target.
    private static String annexBBytesAround(ByteBuffer input, int invalidOffset) {
        ByteBuffer data = input.asReadOnlyBuffer().slice();
        int offset = Math.max(0, Math.min(invalidOffset, data.limit() - 1));
        int start = Math.max(0, offset - 8);
        int end = Math.min(data.limit(), offset + 9);
        StringBuilder bytes = new StringBuilder();
        if (start > 0) bytes.append("... ");
        for (int index = start; index < end; index++) {
            if (index > start) bytes.append(' ');
            if (index == offset) bytes.append('[');
            int value = data.get(index) & 0xff;
            bytes.append(Character.forDigit(value >>> 4, 16));
            bytes.append(Character.forDigit(value & 0x0f, 16));
            if (index == offset) bytes.append(']');
        }
        if (end < data.limit()) bytes.append(" ...");
        return bytes.toString();
    }

    private void clearMalformedVideoRecoveryLocked() {
        malformedVideoRecoveryActive = false;
        malformedVideoSamplesDropped = 0;
        dependentVideoSamplesDropped = 0;
        malformedVideoRecoveryStartedAtUs = -1L;
    }
    private void rememberVideoSampleLocked(
            long sampleSequence, ByteBuffer buffer, long presentationTimeUs,
            int flags, int sourceBufferCount) {
        int index = nextRecentVideoSampleIndex;
        int prefixLength = Math.min(VIDEO_SAMPLE_PREFIX_BYTES, buffer.remaining());
        long prefix = 0L;
        for (int byteIndex = 0; byteIndex < prefixLength; byteIndex++) {
            prefix = prefix << 8 | buffer.get(buffer.position() + byteIndex) & 0xffL;
        }
        recentVideoSampleSequences[index] = sampleSequence;
        recentVideoSamplePresentationTimesUs[index] = presentationTimeUs;
        recentVideoSamplePrefixes[index] = prefix;
        recentVideoSampleOffsets[index] = buffer.position();
        recentVideoSampleSizes[index] = buffer.remaining();
        recentVideoSampleFlags[index] = flags;
        recentVideoSampleBufferCounts[index] = sourceBufferCount;
        recentVideoSamplePrefixLengths[index] = prefixLength;
        nextRecentVideoSampleIndex = (index + 1) % RECENT_VIDEO_SAMPLE_CAPACITY;
        recentVideoSampleCount = Math.min(
                RECENT_VIDEO_SAMPLE_CAPACITY, recentVideoSampleCount + 1);
    }

    private void clearRecentVideoSamplesLocked() {
        nextVideoSampleSequence = 0L;
        recentVideoSampleCount = 0;
        nextRecentVideoSampleIndex = 0;
    }

    private String recentVideoSamplesLocked() {
        if (recentVideoSampleCount == 0) return "none";
        StringBuilder samples = new StringBuilder();
        int index = (nextRecentVideoSampleIndex - recentVideoSampleCount
                + RECENT_VIDEO_SAMPLE_CAPACITY) % RECENT_VIDEO_SAMPLE_CAPACITY;
        for (int sampleIndex = 0; sampleIndex < recentVideoSampleCount; sampleIndex++) {
            if (sampleIndex > 0) samples.append("; ");
            int current = (index + sampleIndex) % RECENT_VIDEO_SAMPLE_CAPACITY;
            samples.append('#').append(recentVideoSampleSequences[current])
                    .append(" at ").append(recentVideoSamplePresentationTimesUs[current])
                    .append(" us, ").append(recentVideoSampleSizes[current]).append(" bytes, offset ")
                    .append(recentVideoSampleOffsets[current]).append(", flags 0x")
                    .append(Integer.toHexString(recentVideoSampleFlags[current])).append(", ")
                    .append(recentVideoSampleBufferCounts[current]).append(" encoder buffers, prefix ");
            appendVideoSamplePrefix(samples, recentVideoSamplePrefixes[current],
                    recentVideoSamplePrefixLengths[current]);
            if (recentVideoSampleSizes[current] > recentVideoSamplePrefixLengths[current]) {
                samples.append("...");
            }
        }
        return samples.toString();
    }

    private static void appendVideoSamplePrefix(
            StringBuilder output, long prefix, int length) {
        if (length == 0) {
            output.append("empty");
            return;
        }
        for (int byteIndex = length - 1; byteIndex >= 0; byteIndex--) {
            int value = (int) (prefix >>> byteIndex * 8) & 0xff;
            output.append(Character.forDigit(value >>> 4, 16));
            output.append(Character.forDigit(value & 0x0f, 16));
        }
    }

    private void recordVideoPresentationTimeLocked(long presentationTimeUs) {
        if (presentationTimeUs >= segmentMaxVideoPresentationTimeUs) {
            segmentSecondMaxVideoPresentationTimeUs = segmentMaxVideoPresentationTimeUs;
            segmentMaxVideoPresentationTimeUs = presentationTimeUs;
        } else if (presentationTimeUs > segmentSecondMaxVideoPresentationTimeUs) {
            segmentSecondMaxVideoPresentationTimeUs = presentationTimeUs;
        }
    }


    private void resetSegmentDurationLocked() {
        segmentMaxVideoPresentationTimeUs = -1L;
        segmentSecondMaxVideoPresentationTimeUs = -1L;
    }

    private void addLocationMetadataLocked() {
        if (segmentLocation == null) return;
        try {
            muxer.addMetadataEntry(mp4LocationData(segmentLocation));
        } catch (RuntimeException error) {
            logger.warn(LogCategory.RECORDING, "unspecified", null, "Start recording without GPS metadata because MP4 muxer "
                    + "rejected current location.", error);
        }
    }

    private Runnable startMuxerLocked() {
        if (muxer == null || muxerStarted || outputFormat == null
                || audioRequired && audioOutputFormat == null) return null;
        try {
            addLocationMetadataLocked();
            muxer.addMetadataEntry(new Mp4OrientationData(rotationDegrees));
            trackIndex = muxer.addTrack(MediaFormatUtil.createFormatFromMediaFormat(outputFormat));
            if (audioRequired) {
                audioTrackIndex = muxer.addTrack(
                        MediaFormatUtil.createFormatFromMediaFormat(audioOutputFormat));
            }
            muxerStarted = true;
            return flushPendingVideoGopLocked();
        } catch (MuxerException | RuntimeException error) {
            callbackError = "muxer_start:" + error.getClass().getSimpleName();
            acceptingSegment = false;
            audioStopRequested = true;
            videoSampleAssembler.clear();
            clearMalformedVideoRecoveryLocked();
            logger.error(LogCategory.RECORDING, "unspecified", null, "Start MP4 muxer failed while flushing buffered H.264 samples. "
                    + "Recording segment stopped to protect the container. Recent complete "
                    + "video samples, oldest first: " + recentVideoSamplesLocked()
                    + PIPELINE_CONTEXT + pipelineId + CODEC_CONTEXT + codecName + ".", error);
            audioCaptureStarted = false;
            audioCaptureReady.countDown();
            firstSample.countDown();
            closeMuxerLocked(true);
            return null;
        }
    }

    @SuppressWarnings("java:S3776")
    private boolean closeMuxerLocked(boolean closeOutput) {
        FragmentedMp4Muxer current = muxer;
        DcamRecordingOutput output = muxerOutput;
        boolean outputOwned = muxerOutputOwned;
        int videoTrackIndex = trackIndex;
        long videoEndTimeUs = segmentDurationUs(
                segmentSamples, segmentMaxVideoPresentationTimeUs,
                segmentSecondMaxVideoPresentationTimeUs, nominalVideoFrameDurationUs);
        muxer = null;
        muxerOutput = null;
        muxerOutputOwned = false;
        muxerChannel = null;
        muxerStarted = false;
        trackIndex = -1;
        audioTrackIndex = -1;
        gpsRouteEnabled = false;
        audioOutputFormat = null;
        audioRequired = false;
        segmentLocation = null;
        lastGpsCoordinate = null;
        gpsLookupFailureLogged = false;
        clearPendingVideoGopLocked();
        videoSampleAssembler.clear();
        clearMalformedVideoRecoveryLocked();
        boolean muxerClosed = true;
        if (current != null) {
            Throwable failure = null;
            try {
                writeVideoEndOfStream(current, videoTrackIndex, videoEndTimeUs);
            } catch (MuxerException | RuntimeException endOfStreamFailure) {
                failure = endOfStreamFailure;
            }
            try {
                current.close();
            } catch (MuxerException | RuntimeException closeFailure) {
                if (failure == null) failure = closeFailure;
                else failure.addSuppressed(closeFailure);
            }
            if (failure != null) {
                muxerClosed = false;
                logger.error(LogCategory.RECORDING, "media_finalization_failed", null, "Could not finalize recording container '"
                        + segmentPath() + "'. Temp file remains available for startup recovery. "
                        + "Reason: " + message(failure) + ".", failure);
            }
        }
        if (output != null && (closeOutput || outputOwned)) {
            try {
                output.close();
            } catch (IOException failure) {
                muxerClosed = false;
                logger.error(LogCategory.RECORDING, "media_finalization_failed", null, "Could not close recording file '" + segmentPath()
                        + "'. Temp file remains available for startup recovery. Reason: "
                        + message(failure) + ".", failure);
            }
        }
        return muxerClosed;
    }
    private static void writeVideoEndOfStream(
            FragmentedMp4Muxer muxer, int trackIndex, long endTimeUs) throws MuxerException {
        if (trackIndex < 0 || endTimeUs <= 0L) return;
        MediaCodec.BufferInfo endOfStream = new MediaCodec.BufferInfo();
        endOfStream.set(0, 0, endTimeUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
        muxer.writeSampleData(trackIndex, ByteBuffer.allocate(0),
                MuxerUtil.getMuxerBufferInfoFromMediaCodecBufferInfo(endOfStream));
    }

    private String segmentPath() {
        return segmentFile == null ? "unknown" : segmentFile.getAbsolutePath();
    }

    private static String message(Throwable failure) {
        String detail = failure.getMessage();
        return detail == null || detail.isBlank()
                ? failure.getClass().getSimpleName() : detail;
    }

    @SuppressWarnings({"java:S6541", "java:S3776"})
    // Preserve the ordered audio, muxer, codec, surface, and callback-thread teardown.
    public boolean closeAndAwait(long timeoutMillis) {
        synchronized (lock) {
            closed = true;
            acceptingSegment = false;
            cancelStorageFloorMonitorLocked();
            outputFormatReady.countDown();
            firstSample.countDown();
        }
        boolean audioReleased = stopAudioCaptureAndAwaitCleanup(timeoutMillis);
        if (audioReleased) releaseAudioCodec();
        boolean muxerReleased;
        synchronized (lock) {
            muxerReleased = audioReleased && closeMuxerLocked(true);
        }
        MediaCodec currentCodec = codec;
        if (!codecReleased && currentCodec != null) {
            try {
                currentCodec.stop();
            } catch (RuntimeException ignored) {
                // Continue closing remaining resources when a terminal codec rejects stop.
            }
            try {
                currentCodec.setCallback(null);
            } catch (RuntimeException ignored) {
                // The callback thread is shut down below even if callback detachment fails.
            }
        }
        Surface currentInputSurface = inputSurface;
        if (!inputSurfaceReleased && currentInputSurface != null) {
            try {
                currentInputSurface.release();
                inputSurfaceReleased = true;
                inputSurface = null;
            } catch (RuntimeException error) {
                logger.warn(LogCategory.RECORDING, "unspecified", null, PIPELINE_LOG_FIELD + pipelineId
                        + " stage=encoder_release outcome=input_surface_release_failed",
                        error);
            }
        }
        Handler currentHandler = callbackHandler;
        if (currentHandler != null) currentHandler.removeCallbacksAndMessages(null);
        HandlerThread currentThread = callbackThread;
        if (currentThread != null) currentThread.quitSafely();
        boolean threadReleased = currentThread == null || join(currentThread, timeoutMillis);
        if (threadReleased) {
            callbackHandler = null;
            callbackThread = null;
        }
        if (!codecReleased && currentCodec != null) {
            boolean recycled = recycleCodec && inputSurfaceReleased && threadReleased
                    && recycleCodec(codecName, currentCodec);
            if (recycled) {
                codecReleased = true;
                codec = null;
            } else {
                try {
                    currentCodec.release();
                    codecReleased = true;
                    codec = null;
                } catch (RuntimeException error) {
                    logger.warn(LogCategory.RECORDING, "unspecified", null, PIPELINE_LOG_FIELD + pipelineId
                            + " stage=encoder_release outcome=codec_release_failed", error);
                }
            }
        }
        return audioReleased && muxerReleased && codecReleased
                && inputSurfaceReleased && threadReleased;
    }
    boolean closeAndAwait() {
        return closeAndAwait(2_000);
    }

    @Override public void close() {
        closeAndAwait();
    }

    private static MediaCodec acquireCodec(String codecName, boolean reusable)
            throws IOException {
        if (reusable) {
            synchronized (REUSABLE_CODEC_LOCK) {
                MediaCodec pooled = REUSABLE_CODECS.remove(codecName);
                if (pooled != null) return pooled;
            }
        }
        return MediaCodec.createByCodecName(codecName);
    }

    private static boolean recycleCodec(String codecName, MediaCodec codec) {
        try {
            codec.reset();
        } catch (RuntimeException error) {
            return false;
        }
        synchronized (REUSABLE_CODEC_LOCK) {
            if (REUSABLE_CODECS.containsKey(codecName)) return false;
            REUSABLE_CODECS.put(codecName, codec);
            return true;
        }
    }

    public static int releaseReusableCodecs(Logger logger) {
        Objects.requireNonNull(logger, "logger");
        List<MediaCodec> codecs;
        synchronized (REUSABLE_CODEC_LOCK) {
            codecs = new ArrayList<>(REUSABLE_CODECS.values());
            REUSABLE_CODECS.clear();
        }
        int released = 0;
        for (MediaCodec codec : codecs) {
            try {
                codec.release();
                released++;
            } catch (RuntimeException error) {
                logger.warn(LogCategory.RECORDING, "unspecified", null, "camera_benchmark codec_pool_release_failed", error);
            }
        }
        logger.info(LogCategory.RECORDING, "unspecified", "camera_benchmark codec_pool_released count=" + released);
        return released;
    }

    private static boolean await(CountDownLatch value, long timeoutMillis) {
        if (value.getCount() == 0L) return true;
        if (timeoutMillis <= 0L) return false;
        try {
            return value.await(timeoutMillis, TimeUnit.MILLISECONDS);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static boolean join(Thread value, long timeoutMillis) {
        if (!value.isAlive()) return true;
        if (timeoutMillis <= 0) return false;
        try {
            value.join(timeoutMillis);
            return !value.isAlive();
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private void ensureOpen() {
        if (closed) throw new IllegalStateException("encoder closed");
    }

    private static EncoderSelection selectEncoder(
            int width, int height, int framesPerSecond) throws IOException {
        for (MediaCodecInfo info : new MediaCodecList(MediaCodecList.ALL_CODECS)
                .getCodecInfos()) {
            if (info.isEncoder()) {
                String[] types = info.getSupportedTypes();
                boolean supportsAvc = false;
                for (String type : types) {
                    if (MIME_TYPE.equalsIgnoreCase(type)) supportsAvc = true;
                }
                if (supportsAvc) {
                    try {
                        MediaCodecInfo.CodecCapabilities capabilities =
                                info.getCapabilitiesForType(MIME_TYPE);
                        MediaCodecInfo.VideoCapabilities video = capabilities.getVideoCapabilities();
                        if (video.areSizeAndRateSupported(width, height, framesPerSecond)) {
                            int bitrate = chooseBitrate(
                                    video.getBitrateRange(), width, height, framesPerSecond);
                            return new EncoderSelection(info.getName(), bitrate);
                        }
                    } catch (RuntimeException ignored) {
                        // Skip a codec whose vendor capability query fails and continue selection.
                    }
                }
            }
        }
        throw new IOException(
                "no H.264 surface encoder for " + width + "x" + height + "@"
                        + framesPerSecond);
    }

    @SuppressWarnings("java:S6885") // Math.clamp is unavailable on the API-26 runtime target.
    private static int chooseBitrate(
            Range<Integer> range, int width, int height, int framesPerSecond) {
        long desired = Math.max(1_000_000L,
                (long) width * height * framesPerSecond / 6L);
        long clamped = Math.max(range.getLower(), Math.min(range.getUpper(), desired));
        return (int) Math.min(Integer.MAX_VALUE, clamped);
    }

    static final class VideoSampleAssembler {
        private final int maxBytes;
        private ByteArrayOutputStream pendingData;
        private long presentationTimeUs;
        private int flags;
        private int bufferCount;
        private boolean timestampChanged;

        VideoSampleAssembler(int maxBytes) {
            if (maxBytes <= 0) throw new IllegalArgumentException("maxBytes must be positive");
            this.maxBytes = maxBytes;
        }

        boolean hasPending() {
            return pendingData != null;
        }

        @SuppressWarnings("java:S6885") // Math.clamp is unavailable on the API-26 runtime target.
        AssembledVideoSample append(ByteBuffer buffer, long presentationTimeUs, int flags) {
            Objects.requireNonNull(buffer, "buffer");
            int partSize = buffer.remaining();
            if (pendingData == null) {
                pendingData = new ByteArrayOutputStream(
                        Math.min(maxBytes, Math.max(32, partSize)));
                this.presentationTimeUs = presentationTimeUs;
                this.flags = 0;
                bufferCount = 0;
                timestampChanged = false;
            } else if (presentationTimeUs != this.presentationTimeUs) {
                timestampChanged = true;
            }
            int pendingSize = pendingData.size();
            if (partSize > maxBytes - pendingSize) {
                long combinedSize = (long) pendingSize + partSize;
                clear();
                throw new IllegalStateException(
                        "partial H.264 sample exceeds " + maxBytes + " bytes: " + combinedSize);
            }
            byte[] part = new byte[partSize];
            buffer.duplicate().get(part);
            pendingData.write(part, 0, part.length);
            this.flags |= flags;
            bufferCount++;
            if ((flags & MediaCodec.BUFFER_FLAG_PARTIAL_FRAME) != 0) return null;
            AssembledVideoSample sample = new AssembledVideoSample(
                    pendingData.toByteArray(), this.presentationTimeUs,
                    this.flags & ~MediaCodec.BUFFER_FLAG_PARTIAL_FRAME,
                    bufferCount, timestampChanged);
            clear();
            return sample;
        }

        void clear() {
            pendingData = null;
            presentationTimeUs = 0L;
            flags = 0;
            bufferCount = 0;
            timestampChanged = false;
        }
    }

    record AssembledVideoSample(
            byte[] data, long presentationTimeUs, int flags,
            int bufferCount, boolean timestampChanged) {
        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof AssembledVideoSample value)) return false;
            return presentationTimeUs == value.presentationTimeUs
                    && flags == value.flags
                    && bufferCount == value.bufferCount
                    && timestampChanged == value.timestampChanged
                    && Arrays.equals(data, value.data);
        }

        @Override public int hashCode() {
            int result = Arrays.hashCode(data);
            result = 31 * result + Long.hashCode(presentationTimeUs);
            result = 31 * result + Integer.hashCode(flags);
            result = 31 * result + Integer.hashCode(bufferCount);
            return 31 * result + Boolean.hashCode(timestampChanged);
        }

        @Override public String toString() {
            return "AssembledVideoSample[data=" + Arrays.toString(data)
                    + ", presentationTimeUs=" + presentationTimeUs
                    + ", flags=" + flags + ", bufferCount=" + bufferCount
                    + ", timestampChanged=" + timestampChanged + "]";
        }
    }

    private record PendingVideoSample(byte[] data, long presentationTimeUs, int flags) {
        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof PendingVideoSample value)) return false;
            return presentationTimeUs == value.presentationTimeUs
                    && flags == value.flags
                    && Arrays.equals(data, value.data);
        }

        @Override public int hashCode() {
            int result = Arrays.hashCode(data);
            result = 31 * result + Long.hashCode(presentationTimeUs);
            return 31 * result + Integer.hashCode(flags);
        }

        @Override public String toString() {
            return "PendingVideoSample[data=" + Arrays.toString(data)
                    + ", presentationTimeUs=" + presentationTimeUs
                    + ", flags=" + flags + "]";
        }
    }

    private record PrimedAudioCodec(MediaCodec codec, MediaFormat outputFormat) {}

    private record EncoderSelection(String codecName, int bitrate) {}
}
