package com.dvid.dcam.platform.camera.shared.egl;

import android.graphics.SurfaceTexture;
import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLExt;
import android.opengl.EGLSurface;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.view.Surface;
import com.dvid.dcam.core.logging.application.port.Logger;
import com.dvid.dcam.platform.camera.shared.CameraPipelineFailureClassifier;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

final class EglFrameFanOut implements AutoCloseable {
    interface Listener {
        void onSourceFrame(long timestampNanos);
        void onPreviewFrame(long timestampNanos);
        void onPreviewDrop(long totalDrops, String reason);
        void onEglError(CameraPipelineFailureClassifier.Signal signal, String detail, Throwable error);
    }

    static final class EglException extends Exception {
        private final CameraPipelineFailureClassifier.Signal signal;

        EglException(CameraPipelineFailureClassifier.Signal signal, String message) {
            super(message);
            this.signal = signal;
        }

        CameraPipelineFailureClassifier.Signal signal() { return signal; }
    }

    private static final int EGL_RECORDABLE_ANDROID = 0x3142;
    private static final long INIT_TIMEOUT_MILLIS = 5_000;
    private static final long RELEASE_TIMEOUT_MILLIS = 2_000;
    private static final long PREVIEW_STALL_MILLIS = 100;
    private static final float[] VERTICES = {
            -1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f
    };
    private static final float[] TEX_COORDS = {
            0f, 0f, 1f, 0f, 0f, 1f, 1f, 1f
    };

    private static final float[] IDENTITY_MATRIX = {
            1f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f,
            0f, 0f, 1f, 0f,
            0f, 0f, 0f, 1f
    };
    private static final String VERTEX_SHADER =
            "attribute vec4 aPosition;\n"
            + "attribute vec4 aTextureCoord;\n"
            + "uniform mat4 uTextureMatrix;\n"
            + "varying vec2 vTextureCoord;\n"
            + "void main(){gl_Position=aPosition;"
            + "vTextureCoord=(uTextureMatrix*aTextureCoord).xy;}";
    private static final String FRAGMENT_SHADER =
            "#extension GL_OES_EGL_image_external : require\n"
            + "precision mediump float;\n"
            + "uniform samplerExternalOES uTexture;\n"
            + "varying vec2 vTextureCoord;\n"
            + "void main(){gl_FragColor=texture2D(uTexture,vTextureCoord);}";
    private static final String PREVIEW_FRAGMENT_SHADER =
            "precision mediump float;\n"
            + "uniform sampler2D uTexture;\n"
            + "varying vec2 vTextureCoord;\n"
            + "void main(){gl_FragColor=texture2D(uTexture,vTextureCoord);}";

    private final Logger logger;
    private final Listener listener;
    private final HandlerThread thread;
    private final Handler handler;
    private final HandlerThread externalPreviewThread;
    private final Handler externalPreviewHandler;
    private final Surface externalPreviewSurface;
    private final Surface encoderSurface;
    private final int width;
    private final int height;
    private final AtomicInteger pendingSourceFrames = new AtomicInteger();
    private final AtomicBoolean renderScheduled = new AtomicBoolean();
    private final AtomicInteger pendingExternalPreviewFrames = new AtomicInteger();
    private final AtomicBoolean externalPreviewRenderScheduled = new AtomicBoolean();
    private final AtomicBoolean externalCleanupStarted = new AtomicBoolean();
    private final AtomicBoolean mainCleanupStarted = new AtomicBoolean();
    private final CountDownLatch externalCleanupFinished = new CountDownLatch(1);
    private final CountDownLatch mainCleanupFinished = new CountDownLatch(1);
    private final Object externalPreviewFrameLock = new Object();
    private final AtomicInteger externalPreviewTextureInUse = new AtomicInteger(-1);
    private volatile boolean encoderEnabled;
    private volatile boolean previewSuppressed;
    private volatile boolean closed;
    private volatile boolean externalCleanupSucceeded = true;
    private volatile boolean mainCleanupSucceeded = true;
    private volatile long previewDrops;
    private volatile long longestPreviewSwapMillis;
    private volatile String renderer = "unknown";
    private EGLDisplay display = EGL14.EGL_NO_DISPLAY;
    private EGLConfig config;
    private EGLContext eglContext = EGL14.EGL_NO_CONTEXT;
    private volatile EGLContext externalPreviewEglContext = EGL14.EGL_NO_CONTEXT;
    private EGLSurface pbuffer = EGL14.EGL_NO_SURFACE;
    private EGLSurface previewEglSurface = EGL14.EGL_NO_SURFACE;
    private volatile EGLSurface externalPreviewEglSurface = EGL14.EGL_NO_SURFACE;
    private volatile EGLSurface encoderEglSurface = EGL14.EGL_NO_SURFACE;
    private SurfaceTexture sourceTexture;
    private Surface sourceSurface;
    private SurfaceTexture headlessPreviewTexture;
    private Surface headlessPreviewSurface;
    private int sourceTextureId;
    private int headlessPreviewTextureId;
    private int program;
    private int positionHandle;
    private int textureCoordinateHandle;
    private int textureMatrixHandle;
    private int textureHandle;
    private int externalPreviewProgram;
    private int externalPreviewPositionHandle;
    private int externalPreviewTextureCoordinateHandle;
    private int externalPreviewTextureMatrixHandle;
    private int externalPreviewTextureHandle;
    private final int[] externalPreviewTextureIds = new int[2];
    private final int[] externalPreviewFramebuffers = new int[2];
    private final FloatBuffer vertices = floatBuffer(VERTICES);
    private final FloatBuffer textureCoordinates = floatBuffer(TEX_COORDS);
    private final FloatBuffer externalPreviewVertices = floatBuffer(VERTICES);
    private final FloatBuffer externalPreviewTextureCoordinates =
            floatBuffer(TEX_COORDS);
    private final float[] textureMatrix = new float[16];
    private final float[] encoderTextureMatrix = new float[16];
    private boolean sourceTransformLogged;
    private int externalPreviewViewportWidth = -1;
    private int externalPreviewViewportHeight = -1;
    private long externalPreviewTimestampNanos;
    private int queuedExternalPreviewTexture = -1;

    static EglFrameFanOut start(int width, int height,
            Surface previewSurface, Surface encoderSurface,
            Logger logger, Listener listener) throws EglException, InterruptedException {
        EglFrameFanOut fanOut = new EglFrameFanOut(width, height,
                previewSurface, encoderSurface, logger, listener);
        CountDownLatch initialized = new CountDownLatch(1);
        java.util.concurrent.atomic.AtomicReference<EglException> failure =
                new java.util.concurrent.atomic.AtomicReference<>();
        fanOut.handler.post(() -> {
            try { fanOut.initialize(); }
            catch (EglException error) { failure.set(error); }
            catch (RuntimeException error) {
                failure.set(new EglException(
                        CameraPipelineFailureClassifier.Signal.UNKNOWN_GLOBAL,
                        "egl_init:" + error.getClass().getSimpleName()));
            } finally { initialized.countDown(); }
        });
        if (!initialized.await(INIT_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) {
            fanOut.close();
            throw new EglException(CameraPipelineFailureClassifier.Signal.TIMEOUT,
                    "egl_init_timeout");
        }
        if (failure.get() != null) {
            fanOut.close();
            throw failure.get();
        }
        if (fanOut.externalPreviewSurface != null) {
            CountDownLatch previewInitialized = new CountDownLatch(1);
            java.util.concurrent.atomic.AtomicReference<EglException> previewFailure =
                    new java.util.concurrent.atomic.AtomicReference<>();
            boolean posted = fanOut.externalPreviewHandler.post(() -> {
                try { fanOut.initializeExternalPreview(); }
                catch (EglException error) { previewFailure.set(error); }
                catch (RuntimeException error) {
                    previewFailure.set(new EglException(
                            CameraPipelineFailureClassifier.Signal.UNKNOWN_GLOBAL,
                            "external_preview_init:" + error.getClass().getSimpleName()));
                } finally { previewInitialized.countDown(); }
            });
            if (!posted || !previewInitialized.await(
                    INIT_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) {
                fanOut.close();
                throw new EglException(CameraPipelineFailureClassifier.Signal.TIMEOUT,
                        "external_preview_init_timeout");
            }
            if (previewFailure.get() != null) {
                fanOut.close();
                throw previewFailure.get();
            }
        }
        return fanOut;
    }

    private EglFrameFanOut(int width, int height,
            Surface previewSurface, Surface encoderSurface,
            Logger logger, Listener listener) {
        if (width <= 0 || height <= 0) throw new IllegalArgumentException(
                "fan-out dimensions must be positive");
        this.width = width;
        this.height = height;
        externalPreviewSurface = previewSurface;
        this.encoderSurface = Objects.requireNonNull(encoderSurface, "encoderSurface");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.listener = Objects.requireNonNull(listener, "listener");
        thread = new HandlerThread("dcam-egl-fanout");
        thread.start();
        handler = new Handler(thread.getLooper());
        if (externalPreviewSurface == null) {
            externalPreviewThread = null;
            externalPreviewHandler = null;
        } else {
            externalPreviewThread = new HandlerThread("dcam-egl-preview");
            externalPreviewThread.start();
            externalPreviewHandler = new Handler(externalPreviewThread.getLooper());
        }
    }

    Surface sourceSurface() {
        Surface value = sourceSurface;
        if (value == null) throw new IllegalStateException("EGL source not initialized");
        return value;
    }

    boolean encoderTargetEnabled() {
        return encoderEnabled;
    }

    void attachEncoderSurface() throws EglException, InterruptedException {
        CountDownLatch attached = new CountDownLatch(1);
        java.util.concurrent.atomic.AtomicReference<EglException> failure =
                new java.util.concurrent.atomic.AtomicReference<>();
        handler.post(() -> {
            try {
                if (closed) {
                    failure.set(new EglException(
                            CameraPipelineFailureClassifier.Signal.CANCELLED,
                            "egl_fanout_closed"));
                    return;
                }
                if (encoderEglSurface == EGL14.EGL_NO_SURFACE) {
                    encoderEglSurface = createWindowSurface(encoderSurface, "encoder");
                }
                encoderEnabled = true;
            } catch (EglException error) {
                failure.set(error);
            } finally {
                attached.countDown();
            }
        });
        if (!attached.await(INIT_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) {
            throw new EglException(CameraPipelineFailureClassifier.Signal.TIMEOUT,
                    "encoder_surface_attach_timeout");
        }
        if (failure.get() != null) throw failure.get();
    }

    int downstreamSurfaceCount() {
        boolean previewReady = externalPreviewSurface == null
                ? previewEglSurface != EGL14.EGL_NO_SURFACE
                : externalPreviewEglSurface != EGL14.EGL_NO_SURFACE;
        return (previewReady ? 1 : 0) + (encoderSurface == null ? 0 : 1);
    }

    String metrics() {
        return "eglRenderer=" + renderer
                + ",previewDrops=" + previewDrops
                + ",previewSuppressed=" + previewSuppressed
                + ",encoderAttached=" + encoderEnabled
                + ",externalPreviewThreadAlive="
                + (externalPreviewThread != null && externalPreviewThread.isAlive())
                + ",longestPreviewSwapMs=" + longestPreviewSwapMillis;
    }

    private void initialize() throws EglException {
        display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
        if (display == EGL14.EGL_NO_DISPLAY) throw eglFailure("egl_get_display");
        int[] version = new int[2];
        if (!EGL14.eglInitialize(display, version, 0, version, 1)) {
            throw eglFailure("egl_initialize");
        }
        int[] attributes = {
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL_RECORDABLE_ANDROID, 1,
                EGL14.EGL_NONE
        };
        EGLConfig[] configs = new EGLConfig[1];
        int[] count = new int[1];
        if (!EGL14.eglChooseConfig(display, attributes, 0,
                configs, 0, 1, count, 0) || count[0] == 0) {
            throw new EglException(CameraPipelineFailureClassifier.Signal.EGL_UNAVAILABLE,
                    "egl_recordable_config_missing");
        }
        config = configs[0];
        eglContext = EGL14.eglCreateContext(display, config, EGL14.EGL_NO_CONTEXT,
                new int[] {EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE}, 0);
        if (eglContext == EGL14.EGL_NO_CONTEXT) throw eglFailure("egl_create_context");
        pbuffer = EGL14.eglCreatePbufferSurface(display, config,
                new int[] {EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE}, 0);
        if (pbuffer == EGL14.EGL_NO_SURFACE) throw eglFailure("egl_create_pbuffer");
        makeCurrent(pbuffer);
        program = createProgram(VERTEX_SHADER, FRAGMENT_SHADER);
        positionHandle = GLES20.glGetAttribLocation(program, "aPosition");
        textureCoordinateHandle = GLES20.glGetAttribLocation(program, "aTextureCoord");
        textureMatrixHandle = GLES20.glGetUniformLocation(program, "uTextureMatrix");
        textureHandle = GLES20.glGetUniformLocation(program, "uTexture");
        renderer = String.valueOf(GLES20.glGetString(GLES20.GL_RENDERER));
        sourceTextureId = createExternalTexture();
        sourceTexture = new SurfaceTexture(sourceTextureId);
        sourceTexture.setDefaultBufferSize(width, height);
        sourceTexture.setOnFrameAvailableListener(this::onSourceFrameAvailable, handler);
        sourceSurface = new Surface(sourceTexture);
        if (externalPreviewSurface == null) {
            headlessPreviewTextureId = createExternalTexture();
            headlessPreviewTexture = new SurfaceTexture(headlessPreviewTextureId);
            headlessPreviewTexture.setDefaultBufferSize(width, height);
            headlessPreviewTexture.setOnFrameAvailableListener(
                    ignored -> consumeHeadlessPreview(), handler);
            headlessPreviewSurface = new Surface(headlessPreviewTexture);
            previewEglSurface = createWindowSurface(headlessPreviewSurface, "preview");
        } else {
            initializeExternalPreviewBridge();
        }
        logger.info("pipeline=b-camera2-egl-fanout-v1 stage=egl_init outcome=pass"
                + " renderer=" + renderer + " width=" + width + " height=" + height);
    }
    private void initializeExternalPreview() throws EglException {
        externalPreviewEglContext = EGL14.eglCreateContext(display, config, eglContext,
                new int[] {EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE}, 0);
        if (externalPreviewEglContext == EGL14.EGL_NO_CONTEXT) {
            throw eglFailure("egl_create_external_preview_context");
        }
        externalPreviewEglSurface = createWindowSurface(
                externalPreviewSurface, "external_preview");
        makeCurrent(externalPreviewEglSurface, externalPreviewEglContext);
    }

    private void initializeExternalPreviewBridge() {
        externalPreviewProgram = createProgram(VERTEX_SHADER, PREVIEW_FRAGMENT_SHADER);
        externalPreviewPositionHandle = GLES20.glGetAttribLocation(
                externalPreviewProgram, "aPosition");
        externalPreviewTextureCoordinateHandle = GLES20.glGetAttribLocation(
                externalPreviewProgram, "aTextureCoord");
        externalPreviewTextureMatrixHandle = GLES20.glGetUniformLocation(
                externalPreviewProgram, "uTextureMatrix");
        externalPreviewTextureHandle = GLES20.glGetUniformLocation(
                externalPreviewProgram, "uTexture");
        GLES20.glGenTextures(externalPreviewTextureIds.length,
                externalPreviewTextureIds, 0);
        GLES20.glGenFramebuffers(externalPreviewFramebuffers.length,
                externalPreviewFramebuffers, 0);
        for (int index = 0; index < externalPreviewTextureIds.length; index++) {
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, externalPreviewTextureIds[index]);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,
                    GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,
                    GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,
                    GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,
                    GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA,
                    width, height, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null);
            GLES20.glBindFramebuffer(
                    GLES20.GL_FRAMEBUFFER, externalPreviewFramebuffers[index]);
            GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER,
                    GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D,
                    externalPreviewTextureIds[index], 0);
            if (GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER)
                    != GLES20.GL_FRAMEBUFFER_COMPLETE) {
                throw new IllegalStateException("external_preview_framebuffer_incomplete");
            }
        }
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
    }
    private void onSourceFrameAvailable(SurfaceTexture ignored) {
        if (closed) return;
        int pending = pendingSourceFrames.incrementAndGet();
        if (renderScheduled.compareAndSet(false, true)) handler.post(this::renderLatest);
        if (pending > 2) listener.onPreviewDrop(++previewDrops,
                "source_backlog=" + pending);
    }

    private void renderLatest() {
        int externalTextureSlot = -1;
        long externalTimestamp = 0;
        try {
            if (closed) return;
            int pending = pendingSourceFrames.getAndSet(0);
            EglFrameDispatchPolicy.Decision decision = EglFrameDispatchPolicy.decide(
                    encoderEnabled, !previewSuppressed, pending);
            sourceTexture.updateTexImage();
            sourceTexture.getTransformMatrix(textureMatrix);
            EglTextureTransform.cropOnlyEncoderMatrix(
                    textureMatrix, encoderTextureMatrix);
            if (!sourceTransformLogged) {
                sourceTransformLogged = true;
                logger.info("Prepare pipeline B frame transforms. Preview uses the measured "
                        + "SurfaceTexture transform; encoder keeps measured crop without "
                        + "preview-only rotation or mirror. Preview matrix: "
                        + java.util.Arrays.toString(textureMatrix)
                        + ". Encoder matrix: "
                        + java.util.Arrays.toString(encoderTextureMatrix) + ".");
            }
            long timestamp = sourceTexture.getTimestamp();
            listener.onSourceFrame(timestamp);
            if (decision.renderEncoder()) {
                drawTo(encoderEglSurface, true, timestamp, encoderTextureMatrix);
            }
            if (decision.renderPreview()) {
                if (externalPreviewSurface == null) {
                    drawTo(previewEglSurface, false, timestamp, textureMatrix);
                } else {
                    externalTextureSlot = selectExternalPreviewTexture();
                    if (externalTextureSlot >= 0) {
                        copySourceToExternalPreviewTexture(externalTextureSlot);
                        externalTimestamp = timestamp;
                    } else {
                        previewDrops++;
                        listener.onPreviewDrop(previewDrops,
                                "external_preview_buffers_busy");
                    }
                }
            }
            if (externalTextureSlot >= 0) {
                queueExternalPreview(externalTimestamp, externalTextureSlot);
            } else if (decision.countPreviewDrop()) {
                previewDrops++;
                listener.onPreviewDrop(previewDrops, "preview_backlog=" + pending);
            }
        } catch (RuntimeException error) {
            listener.onEglError(CameraPipelineFailureClassifier.Signal.EGL_CONTEXT_LOST,
                    "egl_render:" + error.getClass().getSimpleName() + ":" + error.getMessage(), error);
        } finally {
            renderScheduled.set(false);
            if (pendingSourceFrames.get() > 0 && !closed
                    && renderScheduled.compareAndSet(false, true)) {
                handler.post(this::renderLatest);
            }
        }
    }
    private int selectExternalPreviewTexture() {
        synchronized (externalPreviewFrameLock) {
            int inUse = externalPreviewTextureInUse.get();
            for (int index = 0; index < externalPreviewTextureIds.length; index++) {
                if (index != inUse && index != queuedExternalPreviewTexture) return index;
            }
            return -1;
        }
    }

    private void copySourceToExternalPreviewTexture(int textureSlot) {
        GLES20.glBindFramebuffer(
                GLES20.GL_FRAMEBUFFER, externalPreviewFramebuffers[textureSlot]);
        GLES20.glViewport(0, 0, width, height);
        GLES20.glUseProgram(program);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, sourceTextureId);
        GLES20.glUniform1i(textureHandle, 0);
        vertices.position(0);
        GLES20.glEnableVertexAttribArray(positionHandle);
        GLES20.glVertexAttribPointer(positionHandle, 2, GLES20.GL_FLOAT,
                false, 0, vertices);
        textureCoordinates.position(0);
        GLES20.glEnableVertexAttribArray(textureCoordinateHandle);
        GLES20.glVertexAttribPointer(textureCoordinateHandle, 2, GLES20.GL_FLOAT,
                false, 0, textureCoordinates);
        GLES20.glUniformMatrix4fv(textureMatrixHandle, 1, false, textureMatrix, 0);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        GLES20.glDisableVertexAttribArray(positionHandle);
        GLES20.glDisableVertexAttribArray(textureCoordinateHandle);
        GLES20.glUseProgram(0);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
        GLES20.glFinish();
    }

    private void queueExternalPreview(long timestampNanos, int textureSlot) {
        synchronized (externalPreviewFrameLock) {
            queuedExternalPreviewTexture = textureSlot;
            externalPreviewTimestampNanos = timestampNanos;
        }
        int pending = pendingExternalPreviewFrames.incrementAndGet();
        if (pending > 1) {
            previewDrops++;
            listener.onPreviewDrop(previewDrops,
                    "external_preview_backlog=" + pending);
        }
        scheduleExternalPreview();
    }
    private void scheduleExternalPreview() {
        if (closed || externalPreviewHandler == null
                || !externalPreviewRenderScheduled.compareAndSet(false, true)) return;
        if (!externalPreviewHandler.post(this::renderExternalPreviewLatest)) {
            externalPreviewRenderScheduled.set(false);
            previewSuppressed = true;
            listener.onPreviewDrop(++previewDrops,
                    "external_preview_handler_stopped");
        }
    }

    private void renderExternalPreviewLatest() {
        int textureSlot = -1;
        try {
            if (closed || previewSuppressed) return;
            pendingExternalPreviewFrames.getAndSet(0);
            long timestamp;
            synchronized (externalPreviewFrameLock) {
                textureSlot = queuedExternalPreviewTexture;
                queuedExternalPreviewTexture = -1;
                if (textureSlot >= 0) externalPreviewTextureInUse.set(textureSlot);
                timestamp = externalPreviewTimestampNanos;
            }
            if (textureSlot < 0) return;
            long started = SystemClock.elapsedRealtime();
            drawExternalPreviewFrame(timestamp, textureSlot);
            long swapMillis = Math.max(0, SystemClock.elapsedRealtime() - started);
            longestPreviewSwapMillis = Math.max(longestPreviewSwapMillis, swapMillis);
            if (swapMillis > PREVIEW_STALL_MILLIS) {
                previewDrops++;
                listener.onPreviewDrop(previewDrops,
                        "preview_swap_stall_ms=" + swapMillis);
            }
            listener.onPreviewFrame(timestamp);
        } catch (RuntimeException error) {
            previewSuppressed = true;
            previewDrops++;
            listener.onPreviewDrop(previewDrops,
                    "preview_render:" + error.getClass().getSimpleName());
            logger.warn("pipeline=b-camera2-egl-fanout-v1 stage=preview_render"
                    + " outcome=drop", error);
        } finally {
            if (textureSlot >= 0) externalPreviewTextureInUse.compareAndSet(textureSlot, -1);
            externalPreviewRenderScheduled.set(false);
            if (pendingExternalPreviewFrames.get() > 0 && !closed) {
                scheduleExternalPreview();
            }
        }
    }
    private void drawExternalPreviewFrame(long timestampNanos, int textureSlot) {
        makeCurrent(externalPreviewEglSurface, externalPreviewEglContext);
        int previewWidth = querySurfaceDimension(
                externalPreviewEglSurface, EGL14.EGL_WIDTH, "preview_width");
        int previewHeight = querySurfaceDimension(
                externalPreviewEglSurface, EGL14.EGL_HEIGHT, "preview_height");
        if (previewWidth != externalPreviewViewportWidth
                || previewHeight != externalPreviewViewportHeight) {
            externalPreviewViewportWidth = previewWidth;
            externalPreviewViewportHeight = previewHeight;
            logger.info("pipeline=b-camera2-egl-fanout-v1"
                    + " stage=external_preview_viewport outcome=updated source="
                    + width + "x" + height + " target=" + previewWidth + "x" + previewHeight);
        }
        GLES20.glViewport(0, 0, previewWidth, previewHeight);
        GLES20.glUseProgram(externalPreviewProgram);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(
                GLES20.GL_TEXTURE_2D, externalPreviewTextureIds[textureSlot]);
        GLES20.glUniform1i(externalPreviewTextureHandle, 0);
        externalPreviewVertices.position(0);
        GLES20.glEnableVertexAttribArray(externalPreviewPositionHandle);
        GLES20.glVertexAttribPointer(externalPreviewPositionHandle, 2, GLES20.GL_FLOAT,
                false, 0, externalPreviewVertices);
        externalPreviewTextureCoordinates.position(0);
        GLES20.glEnableVertexAttribArray(externalPreviewTextureCoordinateHandle);
        GLES20.glVertexAttribPointer(externalPreviewTextureCoordinateHandle, 2,
                GLES20.GL_FLOAT, false, 0, externalPreviewTextureCoordinates);
        GLES20.glUniformMatrix4fv(externalPreviewTextureMatrixHandle, 1,
                false, IDENTITY_MATRIX, 0);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        GLES20.glDisableVertexAttribArray(externalPreviewPositionHandle);
        GLES20.glDisableVertexAttribArray(externalPreviewTextureCoordinateHandle);
        GLES20.glUseProgram(0);
        if (timestampNanos > 0) {
            EGLExt.eglPresentationTimeANDROID(
                    display, externalPreviewEglSurface, timestampNanos);
        }
        if (!EGL14.eglSwapBuffers(display, externalPreviewEglSurface)) {
            throw new IllegalStateException("preview_swap_failed");
        }
    }
    private int querySurfaceDimension(EGLSurface surface, int attribute, String name) {
        int[] value = new int[1];
        if (!EGL14.eglQuerySurface(display, surface, attribute, value, 0)
                || value[0] <= 0) {
            throw new IllegalStateException(name + "_query_failed:" + EGL14.eglGetError());
        }
        return value[0];
    }

    private void drawTo(EGLSurface target, boolean encoderTarget, long timestampNanos,
            float[] targetTextureMatrix) {
        makeCurrent(target);
        GLES20.glViewport(0, 0, width, height);
        GLES20.glUseProgram(program);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, sourceTextureId);
        GLES20.glUniform1i(textureHandle, 0);
        vertices.position(0);
        GLES20.glEnableVertexAttribArray(positionHandle);
        GLES20.glVertexAttribPointer(positionHandle, 2, GLES20.GL_FLOAT,
                false, 0, vertices);
        textureCoordinates.position(0);
        GLES20.glEnableVertexAttribArray(textureCoordinateHandle);
        GLES20.glVertexAttribPointer(textureCoordinateHandle, 2, GLES20.GL_FLOAT,
                false, 0, textureCoordinates);
        GLES20.glUniformMatrix4fv(
                textureMatrixHandle, 1, false, targetTextureMatrix, 0);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        GLES20.glDisableVertexAttribArray(positionHandle);
        GLES20.glDisableVertexAttribArray(textureCoordinateHandle);
        GLES20.glUseProgram(0);
        if (encoderTarget) GLES20.glFinish();
        if (timestampNanos > 0) {
            EGLExt.eglPresentationTimeANDROID(display, target, timestampNanos);
        }
        if (!EGL14.eglSwapBuffers(display, target)) {
            if (encoderTarget) throw new IllegalStateException("encoder_swap_failed");
            throw new IllegalStateException("preview_swap_failed");
        }
    }

    private void consumeHeadlessPreview() {
        if (headlessPreviewTexture == null || closed) return;
        try {
            makeCurrent(pbuffer);
            headlessPreviewTexture.updateTexImage();
            listener.onPreviewFrame(headlessPreviewTexture.getTimestamp());
        } catch (RuntimeException error) {
            listener.onPreviewDrop(++previewDrops,
                    "headless_preview_consume:" + error.getClass().getSimpleName());
        }
    }

    private EGLSurface createWindowSurface(Surface surface, String name) throws EglException {
        EGLSurface value = EGL14.eglCreateWindowSurface(display, config, surface,
                new int[] {EGL14.EGL_NONE}, 0);
        if (value == EGL14.EGL_NO_SURFACE) throw eglFailure("egl_create_" + name + "_surface");
        return value;
    }

    private void makeCurrent(EGLSurface surface) {
        makeCurrent(surface, eglContext);
    }

    private void makeCurrent(EGLSurface surface, EGLContext context) {
        if (!EGL14.eglMakeCurrent(display, surface, surface, context)) {
            throw new IllegalStateException("egl_make_current_failed:" + EGL14.eglGetError());
        }
    }

    private static int createExternalTexture() {
        int[] textures = new int[1];
        GLES20.glGenTextures(1, textures, 0);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textures[0]);
        GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
        return textures[0];
    }

    private static int createProgram(String vertexSource, String fragmentSource) {
        int vertex = compileShader(GLES20.GL_VERTEX_SHADER, vertexSource);
        int fragment = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource);
        int value = GLES20.glCreateProgram();
        GLES20.glAttachShader(value, vertex);
        GLES20.glAttachShader(value, fragment);
        GLES20.glLinkProgram(value);
        int[] status = new int[1];
        GLES20.glGetProgramiv(value, GLES20.GL_LINK_STATUS, status, 0);
        GLES20.glDeleteShader(vertex);
        GLES20.glDeleteShader(fragment);
        if (status[0] == 0) {
            String info = GLES20.glGetProgramInfoLog(value);
            GLES20.glDeleteProgram(value);
            throw new IllegalStateException("egl_program_link:" + info);
        }
        return value;
    }

    private static int compileShader(int type, String source) {
        int shader = GLES20.glCreateShader(type);
        GLES20.glShaderSource(shader, source);
        GLES20.glCompileShader(shader);
        int[] status = new int[1];
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0);
        if (status[0] == 0) {
            String info = GLES20.glGetShaderInfoLog(shader);
            GLES20.glDeleteShader(shader);
            throw new IllegalStateException("egl_shader_compile:" + info);
        }
        return shader;
    }

    private EglException eglFailure(String stage) {
        int error = EGL14.eglGetError();
        CameraPipelineFailureClassifier.Signal signal =
                error == EGL14.EGL_BAD_ALLOC
                        ? CameraPipelineFailureClassifier.Signal.EGL_BAD_ALLOC
                        : CameraPipelineFailureClassifier.Signal.EGL_UNAVAILABLE;
        return new EglException(signal, stage + ":eglError=0x"
                + Integer.toHexString(error));
    }

    boolean closeAndAwait(long timeoutMillis) {
        long deadline = SystemClock.elapsedRealtime() + Math.max(0, timeoutMillis);
        closed = true;
        startExternalCleanup();
        boolean externalFinished = await(externalCleanupFinished, remaining(deadline));
        boolean externalJoined = externalPreviewThread == null
                || join(externalPreviewThread, remaining(deadline));
        if (!externalFinished || !externalJoined) return false;
        startMainCleanup();
        boolean mainFinished = await(mainCleanupFinished, remaining(deadline));
        boolean mainJoined = join(thread, remaining(deadline));
        return externalCleanupSucceeded && mainCleanupSucceeded
                && mainFinished && mainJoined;
    }

    boolean closeAndAwait() {
        return closeAndAwait(RELEASE_TIMEOUT_MILLIS);
    }

    @Override public void close() {
        closeAndAwait();
    }

    private void startExternalCleanup() {
        if (!externalCleanupStarted.compareAndSet(false, true)) return;
        if (externalPreviewThread == null || externalPreviewHandler == null) {
            externalCleanupFinished.countDown();
            return;
        }
        boolean posted = externalPreviewHandler.post(() -> {
            try { externalCleanupSucceeded = releaseExternalPreviewOnThread(); }
            finally { externalCleanupFinished.countDown(); }
        });
        externalPreviewThread.quitSafely();
        if (!posted) {
            externalCleanupSucceeded = false;
            externalCleanupFinished.countDown();
        }
    }

    private boolean releaseExternalPreviewOnThread() {
        boolean clean = true;
        if (display != EGL14.EGL_NO_DISPLAY
                && externalPreviewEglContext != EGL14.EGL_NO_CONTEXT
                && externalPreviewEglSurface != EGL14.EGL_NO_SURFACE) {
            clean &= cleanupStep("preview_make_current",
                    () -> makeCurrent(externalPreviewEglSurface,
                            externalPreviewEglContext));
        }
        if (display != EGL14.EGL_NO_DISPLAY) {
            clean &= cleanupStep("preview_clear_current", () -> {
                if (!EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE,
                        EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)) {
                    throw new IllegalStateException("preview_clear_current_failed");
                }
            });
        }
        clean &= destroySurface(externalPreviewEglSurface);
        externalPreviewEglSurface = EGL14.EGL_NO_SURFACE;
        if (display != EGL14.EGL_NO_DISPLAY
                && externalPreviewEglContext != EGL14.EGL_NO_CONTEXT) {
            clean &= cleanupStep("preview_destroy_context", () -> {
                if (!EGL14.eglDestroyContext(display, externalPreviewEglContext)) {
                    throw new IllegalStateException("preview_destroy_context_failed");
                }
            });
            externalPreviewEglContext = EGL14.EGL_NO_CONTEXT;
        }
        clean &= cleanupStep("preview_release_thread", EGL14::eglReleaseThread);
        return clean;
    }

    private void startMainCleanup() {
        if (!mainCleanupStarted.compareAndSet(false, true)) return;
        boolean posted = handler.post(() -> {
            try { mainCleanupSucceeded = releaseOnThread(); }
            finally { mainCleanupFinished.countDown(); }
        });
        thread.quitSafely();
        if (!posted) {
            mainCleanupSucceeded = false;
            mainCleanupFinished.countDown();
        }
    }

    private boolean releaseOnThread() {
        boolean clean = true;
        if (display != EGL14.EGL_NO_DISPLAY && eglContext != EGL14.EGL_NO_CONTEXT
                && pbuffer != EGL14.EGL_NO_SURFACE) {
            clean &= cleanupStep("main_make_current", () -> makeCurrent(pbuffer));
        }
        SurfaceTexture currentSourceTexture = sourceTexture;
        sourceTexture = null;
        if (currentSourceTexture != null) clean &= cleanupStep("source_texture", () -> {
            currentSourceTexture.setOnFrameAvailableListener(null);
            currentSourceTexture.release();
        });
        Surface currentSourceSurface = sourceSurface;
        sourceSurface = null;
        if (currentSourceSurface != null) clean &= cleanupStep(
                "source_surface", currentSourceSurface::release);
        SurfaceTexture currentHeadlessTexture = headlessPreviewTexture;
        headlessPreviewTexture = null;
        if (currentHeadlessTexture != null) clean &= cleanupStep("headless_texture", () -> {
            currentHeadlessTexture.setOnFrameAvailableListener(null);
            currentHeadlessTexture.release();
        });
        Surface currentHeadlessSurface = headlessPreviewSurface;
        headlessPreviewSurface = null;
        if (currentHeadlessSurface != null) clean &= cleanupStep(
                "headless_surface", currentHeadlessSurface::release);
        if (externalPreviewProgram != 0) {
            int currentProgram = externalPreviewProgram;
            externalPreviewProgram = 0;
            clean &= cleanupStep("external_preview_program",
                    () -> GLES20.glDeleteProgram(currentProgram));
        }
        if (externalPreviewFramebuffers[0] != 0 || externalPreviewFramebuffers[1] != 0) {
            int[] currentFramebuffers = externalPreviewFramebuffers.clone();
            for (int index = 0; index < externalPreviewFramebuffers.length; index++) {
                externalPreviewFramebuffers[index] = 0;
            }
            clean &= cleanupStep("external_preview_framebuffers",
                    () -> GLES20.glDeleteFramebuffers(
                            currentFramebuffers.length, currentFramebuffers, 0));
        }
        if (externalPreviewTextureIds[0] != 0 || externalPreviewTextureIds[1] != 0) {
            int[] currentTextures = externalPreviewTextureIds.clone();
            for (int index = 0; index < externalPreviewTextureIds.length; index++) {
                externalPreviewTextureIds[index] = 0;
            }
            clean &= cleanupStep("external_preview_textures",
                    () -> GLES20.glDeleteTextures(currentTextures.length, currentTextures, 0));
        }
        if (program != 0) {
            int currentProgram = program;
            program = 0;
            clean &= cleanupStep("program", () -> GLES20.glDeleteProgram(currentProgram));
        }
        if (sourceTextureId != 0) {
            int currentTexture = sourceTextureId;
            sourceTextureId = 0;
            clean &= cleanupStep("source_texture_id",
                    () -> GLES20.glDeleteTextures(1, new int[] {currentTexture}, 0));
        }
        if (headlessPreviewTextureId != 0) {
            int currentTexture = headlessPreviewTextureId;
            headlessPreviewTextureId = 0;
            clean &= cleanupStep("headless_texture_id",
                    () -> GLES20.glDeleteTextures(1, new int[] {currentTexture}, 0));
        }
        if (display != EGL14.EGL_NO_DISPLAY) {
            clean &= cleanupStep("main_clear_current", () -> {
                if (!EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE,
                        EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)) {
                    throw new IllegalStateException("main_clear_current_failed");
                }
            });
        }
        clean &= destroySurface(previewEglSurface);
        previewEglSurface = EGL14.EGL_NO_SURFACE;
        clean &= destroySurface(encoderEglSurface);
        encoderEglSurface = EGL14.EGL_NO_SURFACE;
        clean &= destroySurface(pbuffer);
        pbuffer = EGL14.EGL_NO_SURFACE;
        if (display != EGL14.EGL_NO_DISPLAY
                && eglContext != EGL14.EGL_NO_CONTEXT) {
            clean &= cleanupStep("main_destroy_context", () -> {
                if (!EGL14.eglDestroyContext(display, eglContext)) {
                    throw new IllegalStateException("main_destroy_context_failed");
                }
            });
            eglContext = EGL14.EGL_NO_CONTEXT;
        }
        if (display != EGL14.EGL_NO_DISPLAY) {
            clean &= cleanupStep("display_terminate", () -> {
                if (!EGL14.eglTerminate(display)) {
                    throw new IllegalStateException("egl_terminate_failed");
                }
            });
            display = EGL14.EGL_NO_DISPLAY;
        }
        clean &= cleanupStep("main_release_thread", EGL14::eglReleaseThread);
        return clean;
    }

    private boolean destroySurface(EGLSurface surface) {
        if (display == EGL14.EGL_NO_DISPLAY || surface == EGL14.EGL_NO_SURFACE) return true;
        try {
            return EGL14.eglDestroySurface(display, surface);
        } catch (RuntimeException error) {
            logger.warn("pipeline=b-camera2-egl-fanout-v1 stage=egl_cleanup"
                    + " outcome=global_failure detail=destroy_surface", error);
            return false;
        }
    }

    private boolean cleanupStep(String stage, Runnable action) {
        try {
            action.run();
            return true;
        } catch (RuntimeException error) {
            logger.warn("pipeline=b-camera2-egl-fanout-v1 stage=egl_cleanup"
                    + " outcome=global_failure detail=" + stage, error);
            return false;
        }
    }

    private static boolean await(CountDownLatch latch, long timeoutMillis) {
        try {
            return latch.await(timeoutMillis, TimeUnit.MILLISECONDS);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static boolean join(HandlerThread value, long timeoutMillis) {
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

    private static long remaining(long deadline) {
        return Math.max(0, deadline - SystemClock.elapsedRealtime());
    }

    private static FloatBuffer floatBuffer(float[] values) {
        FloatBuffer buffer = ByteBuffer.allocateDirect(values.length * Float.BYTES)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();
        buffer.put(values).position(0);
        return buffer;
    }
}