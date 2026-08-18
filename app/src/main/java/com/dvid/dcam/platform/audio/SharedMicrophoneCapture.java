package com.dvid.dcam.platform.audio;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import com.dvid.dcam.core.logging.application.port.Logger;
import com.dvid.dcam.feature.capture.domain.AudioCaptureSettings;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public final class SharedMicrophoneCapture {
    public static final int SAMPLE_RATE = AudioCaptureSettings.SAMPLE_RATE_HZ;
    public static final int CHANNEL_COUNT = AudioCaptureSettings.CHANNEL_COUNT;
    public static final int BYTES_PER_FRAME = 2;
    private static final int CAPTURE_CHUNK_BYTES = 4_096;
    private static final int QUEUE_CAPACITY = 64;
    private static final long STOP_TIMEOUT_MILLIS = 2_000L;
    private static final long SUBSCRIPTION_BACKPRESSURE_WAIT_MILLIS = 250L;
    private static SharedMicrophoneCapture processInstance;

    private final Object lock = new Object();
    private final Logger logger;
    private final Set<Subscription> subscriptions = new LinkedHashSet<>();
    private CountDownLatch stopping = new CountDownLatch(0);
    private AudioRecord recorder;
    private Thread captureThread;

    public static synchronized SharedMicrophoneCapture process(Logger logger) {
        if (processInstance == null) processInstance = new SharedMicrophoneCapture(logger);
        return processInstance;
    }

    SharedMicrophoneCapture(Logger logger) {
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    public static long maximumSubscriptionBacklogMillis() {
        long bufferedFrames = (long) QUEUE_CAPACITY * CAPTURE_CHUNK_BYTES / BYTES_PER_FRAME;
        return (bufferedFrames * 1_000L + SAMPLE_RATE - 1L) / SAMPLE_RATE;
    }

    public Subscription subscribe() throws IOException, InterruptedException {
        while (true) {
            CountDownLatch waitForStop;
            synchronized (lock) {
                waitForStop = stopping;
                if (waitForStop.getCount() == 0L) {
                    Subscription subscription = new Subscription(this,
                            android.os.SystemClock.elapsedRealtimeNanos() / 1_000L);
                    subscriptions.add(subscription);
                    if (recorder == null) {
                        try {
                            startCaptureLocked();
                        } catch (RuntimeException error) {
                            subscriptions.remove(subscription);
                            subscription.signalFailure(error);
                            throw error;
                        }
                    }
                    logger.info("Shared microphone consumer attached. Consumers: "
                            + subscriptions.size() + ".");
                    return subscription;
                }
            }
            waitForStop.await();
        }
    }

    private void startCaptureLocked() {
        long startedAt = android.os.SystemClock.elapsedRealtime();
        int minimumBuffer = AudioRecord.getMinBufferSize(
                SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        long bufferReadyAt = android.os.SystemClock.elapsedRealtime();
        if (minimumBuffer <= 0) {
            throw new IllegalStateException("audio_record_buffer_unavailable:" + minimumBuffer);
        }
        int bufferSize = Math.max(4_096, minimumBuffer * 2) & ~1;
        AudioRecord next = new AudioRecord(MediaRecorder.AudioSource.CAMCORDER,
                SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT, bufferSize);
        long recorderReadyAt = android.os.SystemClock.elapsedRealtime();
        if (next.getState() != AudioRecord.STATE_INITIALIZED) {
            try { next.release(); } catch (RuntimeException ignored) {}
            throw new IllegalStateException("audio_record_initialization_failed");
        }
        try {
            next.startRecording();
            long captureReadyAt = android.os.SystemClock.elapsedRealtime();
            if (next.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING) {
                throw new IllegalStateException("audio_record_start_failed");
            }
            recorder = next;
            Thread nextThread = new Thread(() -> capture(next), "dcam-shared-microphone");
            nextThread.setDaemon(true);
            captureThread = nextThread;
            nextThread.start();
            long threadStartedAt = android.os.SystemClock.elapsedRealtime();
            logger.info("Start shared microphone capture success. Minimum buffer query: "
                    + (bufferReadyAt - startedAt) + " ms. AudioRecord initialization: "
                    + (recorderReadyAt - bufferReadyAt) + " ms. AudioRecord start: "
                    + (captureReadyAt - recorderReadyAt) + " ms. Capture thread start: "
                    + (threadStartedAt - captureReadyAt) + " ms. Total: "
                    + (threadStartedAt - startedAt) + " ms. Buffer: " + bufferSize
                    + " bytes. Consumers: " + subscriptions.size() + ".");
        } catch (RuntimeException error) {
            recorder = null;
            captureThread = null;
            try { next.stop(); } catch (RuntimeException ignored) {}
            try { next.release(); } catch (RuntimeException ignored) {}
            throw error;
        }
    }

    private void capture(AudioRecord current) {
        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_AUDIO);
        byte[] buffer = new byte[CAPTURE_CHUNK_BYTES];
        try {
            while (true) {
                synchronized (lock) {
                    if (recorder != current) return;
                }
                int bytes = current.read(buffer, 0, buffer.length, AudioRecord.READ_BLOCKING);
                if (bytes <= 0) {
                    failCapture(current, new IOException("audio_record_read_failed:" + bytes));
                    return;
                }
                bytes -= bytes % BYTES_PER_FRAME;
                if (bytes == 0) continue;
                List<Subscription> active;
                synchronized (lock) {
                    if (recorder != current) return;
                    active = new ArrayList<>(subscriptions);
                }
                for (Subscription subscription : active) subscription.offer(buffer, bytes);
            }
        } catch (RuntimeException error) {
            failCapture(current, new IOException("audio_record_capture_failed", error));
        } finally {
            try { current.stop(); } catch (RuntimeException ignored) {}
            try { current.release(); } catch (RuntimeException ignored) {}
            CountDownLatch finished;
            synchronized (lock) {
                finished = stopping;
                if (recorder == current) {
                    recorder = null;
                    captureThread = null;
                }
            }
            if (finished.getCount() > 0L) finished.countDown();
        }
    }

    private void failCapture(AudioRecord current, IOException failure) {
        List<Subscription> failed;
        synchronized (lock) {
            if (recorder != current) return;
            recorder = null;
            captureThread = null;
            stopping = new CountDownLatch(1);
            failed = new ArrayList<>(subscriptions);
            subscriptions.clear();
        }
        for (Subscription subscription : failed) subscription.signalFailure(failure);
        logger.warn("Shared microphone capture stopped after read failure. Consumers: "
                + failed.size() + ".", failure);
    }

    private void unsubscribe(Subscription subscription) {
        AudioRecord current = null;
        Thread thread = null;
        synchronized (lock) {
            if (!subscriptions.remove(subscription)) return;
            subscription.signalEnd();
            if (subscriptions.isEmpty() && recorder != null) {
                current = recorder;
                thread = captureThread;
                stopping = new CountDownLatch(1);
                recorder = null;
                captureThread = null;
            }
        }
        if (current == null) return;
        try { current.stop(); } catch (RuntimeException ignored) {}
        if (thread != null && thread != Thread.currentThread()) join(thread);
        logger.info("Shared microphone capture stopped. No consumers remain.");
    }


    private static void join(Thread thread) {
        try {
            thread.join(STOP_TIMEOUT_MILLIS);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
        }
        if (thread.isAlive()) thread.interrupt();
    }

    public static final class Subscription implements AutoCloseable {
        private static final Chunk END = new Chunk(null, null, true);
        private final SharedMicrophoneCapture owner;
        private final long startedAtSystemTimeUs;
        private final ArrayBlockingQueue<Chunk> queue = new ArrayBlockingQueue<>(QUEUE_CAPACITY);
        private byte[] pending;
        private int pendingOffset;
        private boolean ended;
        private IOException pendingFailure;
        private volatile boolean closed;

        private Subscription(SharedMicrophoneCapture owner, long startedAtSystemTimeUs) {
            this.owner = owner;
            this.startedAtSystemTimeUs = startedAtSystemTimeUs;
        }

        public long startedAtSystemTimeUs() { return startedAtSystemTimeUs; }

        public int read(ByteBuffer target) throws IOException, InterruptedException {
            Objects.requireNonNull(target, "target");
            if (!target.hasRemaining()) return 0;
            int totalBytes = 0;
            while (target.hasRemaining()) {
                if (pending == null || pendingOffset == pending.length) {
                    if (pendingFailure != null) {
                        if (totalBytes > 0) break;
                        IOException failure = pendingFailure;
                        pendingFailure = null;
                        throw failure;
                    }
                    if (ended) return totalBytes > 0 ? totalBytes : -1;
                    Chunk chunk;
                    if (totalBytes == 0) {
                        if (closed && queue.isEmpty()) {
                            ended = true;
                            return -1;
                        }
                        chunk = queue.poll(100L, TimeUnit.MILLISECONDS);
                        if (chunk == null) {
                            if (closed) {
                                ended = true;
                                return -1;
                            }
                            continue;
                        }
                    } else {
                        chunk = queue.poll();
                        if (chunk == null) {
                            if (closed) ended = true;
                            break;
                        }
                    }
                    if (chunk.failure != null) {
                        if (totalBytes == 0) throw chunk.failure;
                        pendingFailure = chunk.failure;
                        break;
                    }
                    if (chunk.end) {
                        ended = true;
                        break;
                    }
                    pending = chunk.data;
                    pendingOffset = 0;
                }
                int bytes = Math.min(target.remaining(), pending.length - pendingOffset);
                target.put(pending, pendingOffset, bytes);
                pendingOffset += bytes;
                totalBytes += bytes;
            }
            return totalBytes > 0 ? totalBytes : ended ? -1 : 0;
        }

        private void offer(byte[] bytes, int length) {
            if (closed) return;
            byte[] copy = new byte[length];
            System.arraycopy(bytes, 0, copy, 0, length);
            Chunk chunk = new Chunk(copy, null, false);
            while (!closed) {
                try {
                    if (queue.offer(chunk, SUBSCRIPTION_BACKPRESSURE_WAIT_MILLIS,
                            TimeUnit.MILLISECONDS)) return;
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }

        private void signalFailure(Throwable failure) {
            closed = true;
            queue.clear();
            queue.offer(new Chunk(null,
                    failure instanceof IOException ? (IOException) failure
                            : new IOException("shared_microphone_failed", failure), false));
        }

        private void signalEnd() {
            closed = true;
            queue.offer(END);
        }

        @Override public void close() { owner.unsubscribe(this); }
    }

    private record Chunk(byte[] data, IOException failure, boolean end) {}
}