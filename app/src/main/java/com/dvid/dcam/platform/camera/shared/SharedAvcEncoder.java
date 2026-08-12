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
import androidx.media3.common.util.MediaFormatUtil;
import androidx.media3.container.Mp4OrientationData;
import androidx.media3.muxer.FragmentedMp4Muxer;
import androidx.media3.muxer.MuxerException;
import androidx.media3.muxer.MuxerUtil;
import com.dvid.dcam.core.logging.application.port.Logger;
import com.dvid.dcam.feature.device.domain.camera.CameraResolution;
import com.dvid.dcam.platform.audio.SharedMicrophoneCapture;
import com.dvid.dcam.platform.storage.DcamFragmentedMp4Layout;
import com.dvid.dcam.platform.storage.DcamRecordingOutput;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public final class SharedAvcEncoder implements AutoCloseable {
    public record Segment(File file, long sampleCount, long durationUs, String detail) {}

    private static final String MIME_TYPE = MediaFormat.MIMETYPE_VIDEO_AVC;
    private static final String AUDIO_MIME_TYPE = MediaFormat.MIMETYPE_AUDIO_AAC;
    private static final int AUDIO_SAMPLE_RATE = SharedMicrophoneCapture.SAMPLE_RATE;
    private static final int AUDIO_CHANNEL_COUNT = SharedMicrophoneCapture.CHANNEL_COUNT;
    private static final int AUDIO_BIT_RATE = 64_000;
    private static final int AUDIO_BYTES_PER_FRAME = SharedMicrophoneCapture.BYTES_PER_FRAME;
    private static final int AUDIO_PRIME_BYTES = 8 * 1024;
    private static final long AUDIO_PRIME_TIMEOUT_MILLIS = 1_000L;
    private static final long AUDIO_DRAIN_MARGIN_MILLIS = 1_000L;
    private static final long AUDIO_STOP_TIMEOUT_MILLIS =
            SharedMicrophoneCapture.maximumSubscriptionBacklogMillis()
                    + AUDIO_DRAIN_MARGIN_MILLIS;
    private static final int I_FRAME_INTERVAL_SECONDS = 1;
    private static final long FRAGMENT_DURATION_MILLIS = 900L;
    // ponytail: Startup RAM holds one GOP; use disk-backed retention before enabling pre-record.
    private static final int MIN_PENDING_VIDEO_GOP_BYTES = 8 * 1024 * 1024;
    private static final int MAX_PENDING_VIDEO_GOP_BYTES = 64 * 1024 * 1024;
    private static final Object REUSABLE_CODEC_LOCK = new Object();
    private static final Map<String, MediaCodec> REUSABLE_CODECS = new HashMap<>();

    private final Object lock = new Object();
    private final Logger logger;
    private final SharedMicrophoneCapture microphoneCapture;
    private final String pipelineId;
    private final String codecName;
    private final boolean recycleCodec;
    private final long preRecordGopDurationUs;
    private final long nominalVideoFrameDurationUs;
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
    private FragmentedMp4Muxer muxer;
    private DcamRecordingOutput muxerOutput;
    private boolean muxerOutputOwned;
    private DcamFragmentedMp4Layout.OutputChannel muxerChannel;
    private boolean seekIndexReserved;
    private CountDownLatch segmentOutputReady = new CountDownLatch(0);
    private int trackIndex = -1;
    private int audioTrackIndex = -1;
    private MediaFormat audioOutputFormat;
    private MediaFormat primedAudioOutputFormat;
    private boolean audioRequired;

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



    public static SharedAvcEncoder open(
            int width, int height, int framesPerSecond, Logger logger,
            String pipelineId, String callbackThreadName, boolean recycleCodec,
            int rotationDegrees, long preRecordGopDurationMillis) throws IOException {
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
                    preRecordGopDurationMillis);
            inputSurface = encoder.inputSurface;
            encoder.primeAudioCodec();
            return encoder;
        } catch (IOException | RuntimeException error) {
            if (encoder != null) encoder.releaseAudioCodec();
            if (inputSurface != null) inputSurface.release();
            if (codec != null) {
                try { codec.release(); } catch (RuntimeException ignored) {}
            }
            callbackThread.quitSafely();
            throw error;
        }
    }

    private SharedAvcEncoder(Logger logger, String pipelineId, String codecName,
            boolean recycleCodec, HandlerThread callbackThread, MediaCodec codec,
            int width, int height, int framesPerSecond, int bitrate, int rotationDegrees,
            long preRecordGopDurationMillis) {
        this.logger = Objects.requireNonNull(logger, "logger");
        microphoneCapture = SharedMicrophoneCapture.process(logger);
        this.pipelineId = Objects.requireNonNull(pipelineId, "pipelineId");
        this.codecName = Objects.requireNonNull(codecName, "codecName");
        this.recycleCodec = recycleCodec;
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
        this.callbackThread = callbackThread;
        callbackHandler = new Handler(callbackThread.getLooper());
        this.codec = codec;
        codec.setCallback(new MediaCodec.Callback() {
            @Override public void onInputBufferAvailable(MediaCodec codec, int index) {}

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
                SharedAvcEncoder.this.logger.warn(
                        "pipeline=" + pipelineId + " stage=encoder_callback"
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
                    if (acceptingSegment) limitListener = startMuxerLocked();
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
                logger.warn("Suspend idle video encoder failed. Next recording may wait for a key frame. Pipeline: "
                        + pipelineId + ".", error);
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
            logger.info("Prime AAC encoder success. Pipeline: " + pipelineId
                    + ". Elapsed: " + (System.nanoTime() / 1_000_000L - startedAt) + " ms."
                    + " Microphone remains closed until recording.");
        } catch (IOException | RuntimeException error) {
            logger.warn("Prime AAC encoder failed. Recording will retry AAC setup on demand."
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
            }
            if (limitListenerToRun != null) runLimitListener(limitListenerToRun);
            long completedAt = SystemClock.elapsedRealtime();
            logger.info("Begin recording encoder segment success. Segment setup: "
                    + (stateReadyAt - startedAt) + " ms. Audio thread launch: "
                    + (audioLaunchedAt - stateReadyAt) + " ms. Key-frame request: "
                    + (keyFrameRequestedAt - audioLaunchedAt) + " ms. Input resume: "
                    + (inputResumedAt - keyFrameRequestedAt) + " ms. Final output open: "
                    + (outputOpenedAt - inputResumedAt) + " ms. MP4 container create: "
                    + (containerCreatedAt - outputOpenedAt) + " ms. Container attach: "
                    + (completedAt - containerCreatedAt) + " ms. Total: "
                    + (completedAt - startedAt) + " ms. Pipeline: " + pipelineId
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
        }
        suspendInput();
        long inputSuspendedAt = SystemClock.elapsedRealtime();
        requestAudioStop();
        long audioStopRequestedAt = SystemClock.elapsedRealtime();
        synchronized (lock) {
            clearPendingVideoGopLocked();
        }
        logger.info("Pause recording encoder input success. Input suspend: "
                + (inputSuspendedAt - startedAt) + " ms. Audio stop requested: "
                + (audioStopRequestedAt - inputSuspendedAt) + " ms. Total: "
                + (audioStopRequestedAt - startedAt) + " ms. Pipeline: " + pipelineId + ".");
    }

    public void discard() {
        synchronized (lock) {
            acceptingSegment = false;
        }
        suspendInput();
        stopAudioCapture();
        synchronized (lock) {
            closeMuxerLocked(true);
            segmentFile = null;
            segmentSamples = 0;
            resetSegmentDurationLocked();
            recordingFileSizeLimiter.reset(0L);
            recordingLimitListener = () -> {};
            segmentVideoCutoffUs = -1;
            videoTimelineOriginUs = -1;
            recordingSystemTimeOriginUs = -1;
            audioCaptureStarted = false;
            waitingForKeyFrame = false;
            clearPendingVideoGopLocked();
            firstSample.countDown();
        }
    }

    public Segment finish() {
        long startedAt = SystemClock.elapsedRealtime();
        synchronized (lock) {
            acceptingSegment = false;
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
                String detail = callbackError != null
                        ? callbackError : finalized ? "segment_finalized" : "muxer_close_failed";
                segmentFile = null;
                segmentSamples = 0;
                resetSegmentDurationLocked();
                recordingFileSizeLimiter.reset(0L);
                recordingLimitListener = () -> {};
                segmentVideoCutoffUs = -1;
                videoTimelineOriginUs = -1;
                recordingSystemTimeOriginUs = -1;
                audioCaptureStarted = false;
                waitingForKeyFrame = false;
                clearPendingVideoGopLocked();
                firstSample.countDown();
                result = new Segment(file, samples, durationUs, detail);
            }
        }
        long completedAt = SystemClock.elapsedRealtime();
        logger.info("Finalize recording encoder segment "
                + (audioStopped && "segment_finalized".equals(result.detail())
                        ? "success" : "failed")
                + ". Audio stop and drain: " + (audioStoppedAt - startedAt)
                + " ms. Container close: " + (completedAt - audioStoppedAt)
                + " ms. Total: " + (completedAt - startedAt)
                + " ms. Pipeline: " + pipelineId + ".");
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
                logger.info("Prepare AAC encoder on recording demand success. Pipeline: "
                        + pipelineId + ". Elapsed: "
                        + (System.nanoTime() / 1_000_000L - startedAt) + " ms.");
            }
            runningCodec.flush();
            synchronized (lock) {
                if (audioStopRequested) return;
                microphoneSubscription = runningMicrophone;
                audioCaptureStarted = true;
                audioCaptureReady.countDown();
            }
            logger.info("Attach video AAC encoder to shared microphone success. Pipeline: "
                    + pipelineId + ". Elapsed: "
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
        logger.info("Align recording audio to first video sample. Audio start offset: "
                + audioPresentationTimeUs(audioStartOffsetFrames) / 1_000L
                + " ms. Dropped microphone lead: "
                + audioPresentationTimeUs(discardFrames) / 1_000L
                + " ms. Pipeline: " + pipelineId + ".");
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
                            int bytes;
                            try {
                                bytes = runningMicrophone.read(input);
                            } catch (InterruptedException error) {
                                Thread.currentThread().interrupt();
                                if (audioStopRequested) bytes = -1;
                                else throw new IllegalStateException(
                                        "shared_microphone_read_interrupted", error);
                            } catch (IOException error) {
                                throw new IllegalStateException(
                                        "shared_microphone_read_failed", error);
                            }
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
                while (true) {
                    int outputIndex = runningCodec.dequeueOutputBuffer(outputInfo, 0);
                    if (outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER) break;
                    if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        Runnable limitListener = null;
                        synchronized (lock) {
                            audioOutputFormat = runningCodec.getOutputFormat();
                            limitListener = startMuxerLocked();
                        }
                        if (limitListener != null) runLimitListener(limitListener);
                        continue;
                    }
                    if (outputIndex < 0) continue;
                    handleAudioOutput(runningCodec, outputIndex, outputInfo);
                    if ((outputInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        outputEnded = true;
                        break;
                    }
                }
            }
        } catch (RuntimeException error) {
            audioFailure("audio_encoder", error);
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
                        recordingFileSizeLimiter.evaluateSample(
                                sourceInfo.size, fileBytesForNextSampleLocked());
                if (sizeDecision.shouldWrite()) {
                    muxer.writeSampleData(audioTrackIndex, buffer,
                            MuxerUtil.getMuxerBufferInfoFromMediaCodecBufferInfo(writeInfo));
                    reserveSeekIndexLocked();
                }
                if (sizeDecision.shouldStop()) {
                    acceptingSegment = false;
                    audioStopRequested = true;
                    limitListener = recordingLimitListener;
                }
            }
        } catch (MuxerException | RuntimeException error) {
            audioFailure("audio_muxer_write", error);
        } finally {
            try { runningCodec.releaseOutputBuffer(index, false); }
            catch (RuntimeException ignored) {}
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

    private void requestAudioStop() {
        SharedMicrophoneCapture.Subscription microphone;
        synchronized (lock) {
            if (audioCaptureStopped && audioThread == null && microphoneSubscription == null) return;
            if (!audioStopRequested) {
                audioStopTargetFrames = audioFrameCountForDurationUs(segmentDurationUs(
                        segmentSamples, segmentMaxVideoPresentationTimeUs,
                        segmentSecondMaxVideoPresentationTimeUs, nominalVideoFrameDurationUs));
            }
            audioStopRequested = true;
            audioCaptureStarted = false;
            audioCaptureReady.countDown();
            segmentOutputReady.countDown();
            microphone = microphoneSubscription;
        }
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
        thread.interrupt();
        synchronized (lock) {
            if (callbackError == null) callbackError = "audio_encoder_stop_timeout";
            firstSample.countDown();
        }
        logger.info("Stop recording audio encoder failed because AAC muxer drain exceeded "
                + timeoutMillis + " ms. Pipeline: " + pipelineId + ".");
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
        logger.warn("pipeline=" + pipelineId + " stage=" + stage
                + " outcome=global_failure", error);
    }

    private static void releaseAudio(MediaCodec codec,
            SharedMicrophoneCapture.Subscription microphone) {
        if (microphone != null) microphone.close();
        if (codec != null) {
            try { codec.release(); } catch (RuntimeException ignored) {}
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
        try {
            latch.await(AUDIO_PRIME_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
        }
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
            logger.warn("pipeline=" + pipelineId
                    + " stage=recording_limit_callback outcome=failed", error);
        }
    }
    private void handleOutput(int index, MediaCodec.BufferInfo sourceInfo) {
        Runnable limitListener = null;
        try {
            synchronized (lock) {
                ByteBuffer buffer = codec.getOutputBuffer(index);
                if (buffer == null || sourceInfo.size <= 0
                        || (sourceInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0
                        || !acceptingSegment) {
                    return;
                }
                if (!sampleAfterCutoff(
                        segmentVideoCutoffUs, sourceInfo.presentationTimeUs)) return;
                boolean keyFrame = (sourceInfo.flags & MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0;
                if (waitingForKeyFrame && !keyFrame) return;
                waitingForKeyFrame = false;
                buffer.position(sourceInfo.offset);
                buffer.limit(sourceInfo.offset + sourceInfo.size);
                if (!muxerStarted) {
                    bufferPendingVideoSampleLocked(buffer, sourceInfo, keyFrame);
                    return;
                }
                limitListener = writeVideoSampleLocked(buffer, sourceInfo.presentationTimeUs,
                        sourceInfo.flags);
            }
        } catch (MuxerException | RuntimeException error) {
            synchronized (lock) {
                callbackError = "muxer_write:" + error.getClass().getSimpleName();
                acceptingSegment = false;
                audioStopRequested = true;
                firstSample.countDown();
            }
            logger.warn(
                    "pipeline=" + pipelineId + " stage=muxer_write"
                            + " outcome=global_failure",
                    error);
        } finally {
            try { codec.releaseOutputBuffer(index, false); } catch (RuntimeException ignored) {}
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
                recordingFileSizeLimiter.evaluateSample(sampleSize, fileBytesForNextSampleLocked());
        if (sizeDecision.shouldWrite()) {
            muxer.writeSampleData(trackIndex, buffer,
                    MuxerUtil.getMuxerBufferInfoFromMediaCodecBufferInfo(writeInfo));
            reserveSeekIndexLocked();
            recordVideoPresentationTimeLocked(writeInfo.presentationTimeUs);
            segmentSamples++;
            totalSamples++;
            firstSample.countDown();
        }
        if (!sizeDecision.shouldStop()) return null;
        acceptingSegment = false;
        audioStopTargetFrames = audioFrameCountForDurationUs(segmentDurationUs(
                segmentSamples, segmentMaxVideoPresentationTimeUs,
                segmentSecondMaxVideoPresentationTimeUs, nominalVideoFrameDurationUs));
        audioStopRequested = true;
        firstSample.countDown();
        return recordingLimitListener;
    }

    private void bufferPendingVideoSampleLocked(
            ByteBuffer buffer, MediaCodec.BufferInfo sourceInfo, boolean keyFrame) {
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
        pendingVideoGop.add(new PendingVideoSample(
                data, sourceInfo.presentationTimeUs, sourceInfo.flags));
        pendingVideoGopBytes += size;
    }

    private Runnable flushPendingVideoGopLocked() throws MuxerException {
        if (pendingVideoGop.isEmpty()) return null;
        Runnable limitListener = null;
        for (PendingVideoSample sample : pendingVideoGop) {
            if (!acceptingSegment) break;
            limitListener = writeVideoSampleLocked(
                    ByteBuffer.wrap(sample.data()), sample.presentationTimeUs(), sample.flags());
            if (limitListener != null) break;
        }
        clearPendingVideoGopLocked();
        return limitListener;
    }

    private void clearPendingVideoGopLocked() {
        pendingVideoGop.clear();
        pendingVideoGopBytes = 0;
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

    private Runnable startMuxerLocked() {
        if (muxer == null || muxerStarted || outputFormat == null
                || audioRequired && audioOutputFormat == null) return null;
        try {
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
            audioCaptureStarted = false;
            audioCaptureReady.countDown();
            firstSample.countDown();
            closeMuxerLocked(true);
            return null;
        }
    }

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
        audioOutputFormat = null;
        audioRequired = false;
        clearPendingVideoGopLocked();
        boolean closed = true;
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
                closed = false;
                logger.warn("Could not finalize recording container '"
                        + segmentPath() + "'. Temp file remains available for startup recovery. "
                        + "Reason: " + message(failure) + ".", failure);
            }
        }
        if (output != null && (closeOutput || outputOwned)) {
            try {
                output.close();
            } catch (IOException failure) {
                closed = false;
                logger.warn("Could not close recording file '" + segmentPath()
                        + "'. Temp file remains available for startup recovery. Reason: "
                        + message(failure) + ".", failure);
            }
        }
        return closed;
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

    public boolean closeAndAwait(long timeoutMillis) {
        synchronized (lock) {
            closed = true;
            acceptingSegment = false;
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
            try { currentCodec.stop(); } catch (RuntimeException ignored) {}
            try { currentCodec.setCallback(null); } catch (RuntimeException ignored) {}
        }
        Surface currentInputSurface = inputSurface;
        if (!inputSurfaceReleased && currentInputSurface != null) {
            try {
                currentInputSurface.release();
                inputSurfaceReleased = true;
                inputSurface = null;
            } catch (RuntimeException error) {
                logger.warn("pipeline=" + pipelineId
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
                    logger.warn("pipeline=" + pipelineId
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
                logger.warn("camera_benchmark codec_pool_release_failed", error);
            }
        }
        logger.info("camera_benchmark codec_pool_released count=" + released);
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
            if (!info.isEncoder()) continue;
            String[] types = info.getSupportedTypes();
            boolean supportsAvc = false;
            for (String type : types) {
                if (MIME_TYPE.equalsIgnoreCase(type)) {
                    supportsAvc = true;
                    break;
                }
            }
            if (!supportsAvc) continue;
            try {
                MediaCodecInfo.CodecCapabilities capabilities =
                        info.getCapabilitiesForType(MIME_TYPE);
                MediaCodecInfo.VideoCapabilities video = capabilities.getVideoCapabilities();
                if (!video.areSizeAndRateSupported(width, height, framesPerSecond)) continue;
                int bitrate = chooseBitrate(video.getBitrateRange(), width, height, framesPerSecond);
                return new EncoderSelection(info.getName(), bitrate);
            } catch (RuntimeException ignored) {}
        }
        throw new IOException(
                "no H.264 surface encoder for " + width + "x" + height + "@"
                        + framesPerSecond);
    }

    private static int chooseBitrate(
            Range<Integer> range, int width, int height, int framesPerSecond) {
        long desired = Math.max(1_000_000L,
                (long) width * height * framesPerSecond / 6L);
        long clamped = Math.max(range.getLower(), Math.min(range.getUpper(), desired));
        return (int) Math.min(Integer.MAX_VALUE, clamped);
    }

    private record PendingVideoSample(byte[] data, long presentationTimeUs, int flags) {}

    private record PrimedAudioCodec(MediaCodec codec, MediaFormat outputFormat) {}

    private record EncoderSelection(String codecName, int bitrate) {}
}