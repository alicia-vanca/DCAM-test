package com.dvid.dcam.app.devmode;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;
import android.os.SystemClock;
import com.dvid.dcam.app.AppComposition;
import com.dvid.dcam.app.ui.camera.CameraFlowCoordinator;
import com.dvid.dcam.core.logging.domain.LogCategory;
import com.dvid.dcam.core.logging.application.port.Logger;
import com.dvid.dcam.feature.capture.application.usecase.SerializedRecordingCoordinator;
import com.dvid.dcam.feature.device.domain.camera.CameraRuntimeState;
import com.dvid.dcam.platform.logging.app.AppLogger;
import com.dvid.dcam.platform.camera.shared.SharedCameraGateway;
import com.dvid.dcam.platform.camera.shared.runtime.ProcessCameraRuntimeBackend;
import com.dvid.dcam.platform.camera.shared.runtime.ProcessCameraRuntimeOwner;
import com.dvid.dcam.platform.recording.RecordingForegroundService;
import java.lang.reflect.Field;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;

public final class CameraIdleReleaseLatencyReceiver extends BroadcastReceiver {
    public static final String ACTION_PREPARE =
            "com.dvid.dcam.debug.camera_latency.PREPARE";
    public static final String ACTION_RESTORE =
            "com.dvid.dcam.debug.camera_latency.RESTORE";
    public static final String ACTION_RELEASE =
            "com.dvid.dcam.debug.camera_latency.RELEASE";
    public static final String ACTION_VIDEO =
            "com.dvid.dcam.debug.camera_latency.VIDEO";
    public static final String ACTION_PHOTO =
            "com.dvid.dcam.debug.camera_latency.PHOTO";
    private static final long CAMERA_TIMEOUT_MS = 60_000L;
    private static final long CAPTURE_TIMEOUT_MS = 30_000L;
    private static final long VIDEO_HOLD_MS = 750L;
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "camera-idle-latency-probe");
        thread.setDaemon(true);
        return thread;
    });

    @Override public void onReceive(Context context, Intent intent) {
        PendingResult pending = goAsync();
        Context applicationContext = context.getApplicationContext();
        String action = intent == null ? null : intent.getAction();
        String runId = intent == null ? null : intent.getStringExtra("run_id");
        int trial = intent == null ? 0 : intent.getIntExtra("trial", 0);
        EXECUTOR.execute(() -> {
            AppComposition composition = null;
            SharedCameraGateway camera = null;
            Logger logger = AppLogger.get();
            try {
                composition = AppComposition.create(applicationContext);
                camera = compositionField(composition, "recordingCamera", SharedCameraGateway.class);
                if (ACTION_PREPARE.equals(action) || ACTION_RESTORE.equals(action)) {
                    prepare(applicationContext, composition, camera, logger);
                } else if (ACTION_RELEASE.equals(action)) {
                    requireScreenOff(applicationContext);
                    release(applicationContext, camera, logger);
                } else if (ACTION_VIDEO.equals(action)) {
                    requireScreenOff(applicationContext);
                    measureVideo(applicationContext, camera, logger, value(runId), trial);
                } else if (ACTION_PHOTO.equals(action)) {
                    requireScreenOff(applicationContext);
                    measurePhoto(applicationContext, camera, logger, value(runId), trial);
                } else {
                    throw new IllegalArgumentException("Unsupported camera latency action: " + action);
                }
            } catch (Exception error) {
                logger.error(LogCategory.PERF, "unspecified", null, "Camera idle release latency probe failed. Action: " + action
                        + ". Run: " + value(runId) + ". Trial: " + trial + ".", error);
                if (composition != null && camera != null) {
                    stopAndRestore(applicationContext, composition, camera, logger);
                }
            } finally {
                pending.finish();
            }
        });
    }

    private static void prepare(Context context, AppComposition composition,
            SharedCameraGateway camera, Logger logger) throws Exception {
        SerializedRecordingCoordinator recordings = compositionField(composition,
                "recordingCoordinator", SerializedRecordingCoordinator.class);
        clearPendingStart(recordings);
        if (!RecordingForegroundService.keepCameraReady(context)) {
            throw new IllegalStateException("Could not start camera-ready foreground service");
        }
        composition.prepareCameraProfilesIfPermitted();
        CameraFlowCoordinator flow = compositionField(composition,
                "cameraFlow", CameraFlowCoordinator.class);
        if (flow.state() == CameraFlowCoordinator.State.READY) {
            ProcessCameraRuntimeOwner.RuntimeSnapshot settled = await(camera,
                    snapshot -> snapshot.inFlight().isEmpty(), CAMERA_TIMEOUT_MS,
                    "settle camera before prepare");
            if (settled.state() == CameraRuntimeState.CLOSED) flow.retry();
        }
        ProcessCameraRuntimeOwner.RuntimeSnapshot snapshot = await(camera,
                CameraIdleReleaseLatencyReceiver::ready, CAMERA_TIMEOUT_MS, "prepare camera");
        logger.info(LogCategory.PERF, "unspecified", "Prepare camera latency probe success. Camera: "
                + snapshot.committedSelection().orElseThrow().cameraId().value()
                + ". Runtime is ready.");
    }

    private static void release(Context context, SharedCameraGateway camera, Logger logger)
            throws Exception {
        ProcessCameraRuntimeOwner.RuntimeSnapshot before = camera.snapshot();
        if (before.state() == CameraRuntimeState.RECORDING || before.inFlight().isPresent()) {
            throw new IllegalStateException("Camera must be idle before release. State: "
                    + before.state() + ". In flight: " + before.inFlight().orElse(null));
        }
        long startedAt = SystemClock.elapsedRealtime();
        ProcessCameraRuntimeOwner.Submission submission = camera.releaseCamera();
        if (!accepted(submission)) {
            throw new IllegalStateException("Camera release rejected: " + submission);
        }
        await(camera, snapshot -> snapshot.state() == CameraRuntimeState.CLOSED
                        && snapshot.inFlight().isEmpty(),
                CAMERA_TIMEOUT_MS, "release camera");
        RecordingForegroundService.releaseCameraReady(context);
        logger.info(LogCategory.PERF, "unspecified", "Release idle camera while screen is off success. Elapsed: "
                + (SystemClock.elapsedRealtime() - startedAt) + " ms. Runtime is closed.");
    }

    private static void measureVideo(Context context, SharedCameraGateway camera,
            Logger logger, String runId, int trial) throws Exception {
        ProcessCameraRuntimeOwner.RuntimeSnapshot initial = camera.snapshot();
        boolean cold = initial.state() == CameraRuntimeState.CLOSED;
        requireReadyOrClosed(initial);
        CountDownLatch recording = new CountDownLatch(1);
        ProcessCameraRuntimeOwner.Attachment attachment = camera.observeRuntimeState(snapshot -> {
            if (snapshot.state() == CameraRuntimeState.RECORDING
                    && snapshot.inFlight().isEmpty()) recording.countDown();
        });
        long buttonAt = SystemClock.elapsedRealtime();
        logger.info(LogCategory.PERF, "unspecified", "Start " + temperature(cold)
                + " camera latency trial for video while screen is off. Run: "
                + runId + ". Trial: " + trial + ".");
        try {
            if (cold) startRebind(context, camera);
            camera.startVideo();
            if (!recording.await(CAPTURE_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                throw new IllegalStateException("Video recording did not start");
            }
        } finally {
            attachment.close();
        }
        long elapsed = SystemClock.elapsedRealtime() - buttonAt;
        logger.info(LogCategory.PERF, "unspecified", "Complete " + temperature(cold)
                + " camera latency trial for video while screen is off. Button to recording start: "
                + elapsed + " ms. Run: " + runId + ". Trial: " + trial + ".");
        SystemClock.sleep(VIDEO_HOLD_MS);
        camera.stopRecording();
        await(camera, CameraIdleReleaseLatencyReceiver::ready,
                CAPTURE_TIMEOUT_MS, "stop video");
    }

    private static void measurePhoto(Context context, SharedCameraGateway camera,
            Logger logger, String runId, int trial) throws Exception {
        ProcessCameraRuntimeOwner.RuntimeSnapshot initial = camera.snapshot();
        boolean cold = initial.state() == CameraRuntimeState.CLOSED;
        requireReadyOrClosed(initial);
        AtomicBoolean captureStarted = new AtomicBoolean();
        CountDownLatch completed = new CountDownLatch(1);
        ProcessCameraRuntimeOwner.Attachment attachment = camera.observeRuntimeState(snapshot -> {
            if (snapshot.inFlight().orElse(null)
                    == ProcessCameraRuntimeBackend.Operation.CAPTURE_PHOTO) {
                captureStarted.set(true);
            }
            if (captureStarted.get() && ready(snapshot) && !snapshot.pendingPhoto()) {
                completed.countDown();
            }
        });
        long buttonAt = SystemClock.elapsedRealtime();
        logger.info(LogCategory.PERF, "unspecified", "Start " + temperature(cold)
                + " camera latency trial for photo while screen is off. Run: "
                + runId + ". Trial: " + trial + ".");
        try {
            if (cold) startRebind(context, camera);
            camera.takePhoto();
            if (!completed.await(CAPTURE_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                throw new IllegalStateException("Photo capture did not complete");
            }
        } finally {
            attachment.close();
        }
        long elapsed = SystemClock.elapsedRealtime() - buttonAt;
        logger.info(LogCategory.PERF, "unspecified", "Complete " + temperature(cold)
                + " camera latency trial for photo while screen is off. Button to photo operation completion: "
                + elapsed + " ms. Run: " + runId + ". Trial: " + trial + ".");
    }

    private static void startRebind(Context context, SharedCameraGateway camera) {
        if (!RecordingForegroundService.keepCameraReady(context)) {
            throw new IllegalStateException("Could not restart camera-ready foreground service");
        }
        ProcessCameraRuntimeOwner.Submission submission = camera.bindCommitted();
        if (!accepted(submission)) {
            throw new IllegalStateException("Camera rebind rejected: " + submission);
        }
    }

    private static ProcessCameraRuntimeOwner.RuntimeSnapshot await(
            SharedCameraGateway camera,
            Predicate<ProcessCameraRuntimeOwner.RuntimeSnapshot> condition,
            long timeoutMillis,
            String operation) throws Exception {
        long deadline = SystemClock.elapsedRealtime() + timeoutMillis;
        ProcessCameraRuntimeOwner.RuntimeSnapshot snapshot = camera.snapshot();
        while (!condition.test(snapshot) && SystemClock.elapsedRealtime() < deadline) {
            SystemClock.sleep(20L);
            snapshot = camera.snapshot();
        }
        if (!condition.test(snapshot)) {
            throw new IllegalStateException(operation + " timed out. State: " + snapshot.state()
                    + ". In flight: " + snapshot.inFlight().orElse(null)
                    + ". Pending record: " + snapshot.pendingRecordStart()
                    + ". Pending photo: " + snapshot.pendingPhoto() + ".");
        }
        return snapshot;
    }

    private static boolean ready(ProcessCameraRuntimeOwner.RuntimeSnapshot snapshot) {
        return snapshot.state() == CameraRuntimeState.READY
                && snapshot.inFlight().isEmpty();
    }

    private static void requireReadyOrClosed(ProcessCameraRuntimeOwner.RuntimeSnapshot snapshot) {
        if (ready(snapshot) || snapshot.state() == CameraRuntimeState.CLOSED
                && snapshot.inFlight().isEmpty()) return;
        throw new IllegalStateException("Camera must be ready or closed. State: "
                + snapshot.state() + ". In flight: " + snapshot.inFlight().orElse(null));
    }

    private static void requireScreenOff(Context context) {
        PowerManager power = context.getSystemService(PowerManager.class);
        if (power == null || power.isInteractive()) {
            throw new IllegalStateException("Device screen must be off for camera latency trial");
        }
    }

    private static boolean accepted(ProcessCameraRuntimeOwner.Submission submission) {
        return submission == ProcessCameraRuntimeOwner.Submission.ACCEPTED
                || submission == ProcessCameraRuntimeOwner.Submission.HELD
                || submission == ProcessCameraRuntimeOwner.Submission.COALESCED
                || submission == ProcessCameraRuntimeOwner.Submission.NO_OP;
    }

    private static String temperature(boolean cold) {
        return cold ? "cold" : "hot";
    }

    private static String value(String value) {
        return value == null || value.isBlank() ? "unspecified" : value;
    }

    private static void clearPendingStart(SerializedRecordingCoordinator recordings)
            throws Exception {
        recordings.discardPendingStart();
        long deadline = SystemClock.elapsedRealtime() + CAPTURE_TIMEOUT_MS;
        while (recordings.isRecordingStartPending()
                && SystemClock.elapsedRealtime() < deadline) {
            SystemClock.sleep(20L);
        }
        if (recordings.isRecordingStartPending()) {
            throw new IllegalStateException("Pending video start did not clear");
        }
    }

    private static <T> T compositionField(
            AppComposition composition, String name, Class<T> type)
            throws ReflectiveOperationException {
        // ponytail: debug probe uses reflection to avoid a production test API; replace only if reused.
        Field field = AppComposition.class.getDeclaredField(name);
        field.setAccessible(true);
        return type.cast(field.get(composition));
    }

    private static void stopAndRestore(Context context, AppComposition composition,
            SharedCameraGateway camera, Logger logger) {
        try {
            if (camera.snapshot().state() == CameraRuntimeState.RECORDING) {
                camera.stopRecording();
                await(camera, CameraIdleReleaseLatencyReceiver::ready,
                        CAPTURE_TIMEOUT_MS, "cleanup recording stop");
            }
            prepare(context, composition, camera, logger);
        } catch (Exception cleanupError) {
            logger.warn(LogCategory.PERF, "unspecified", null, "Restore normal camera state after latency probe failure failed.",
                    cleanupError);
        }
    }
}