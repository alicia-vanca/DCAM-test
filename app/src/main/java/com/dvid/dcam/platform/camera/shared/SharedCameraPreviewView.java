package com.dvid.dcam.platform.camera.shared;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.DisplayCutout;
import android.view.Gravity;
import android.view.Surface;
import android.view.TextureView;
import android.view.WindowInsets;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.annotation.RequiresApi;
import com.dvid.dcam.R;
import com.dvid.dcam.core.logging.domain.LogCategory;
import com.dvid.dcam.core.logging.application.port.Logger;
import com.dvid.dcam.feature.device.domain.camera.CameraResolution;
import java.util.Objects;
import java.util.function.Consumer;

@SuppressLint("ViewConstructor")
public final class SharedCameraPreviewView extends FrameLayout {
    private final Logger logger;
    private final SurfaceHandle surfaceHandle;
    private final Runnable layoutChangedCallback;
    private final FrameLayout textureViewport;
    private final TextureView textureView;
    private final FrameLayout startupOverlay;
    private final ProgressBar startupProgress;
    private final TextView startupMessage;
    private final TextView startupDetail;

    private final Consumer<CharSequence> transientNotice;
    private final Consumer<CharSequence> persistentNotice;
    private final Runnable clearPersistentNotice;
    private String storageWarning;
    private Consumer<Boolean> previewExpectedChanged = ignored -> {};
    private boolean previewSurfaceAvailable;
    private boolean hideStartupOnNextFrame;
    private int appliedPreviewWidth;
    private int appliedPreviewHeight;
    private int appliedViewportWidth;
    private int appliedViewportHeight;
    private int appliedOutputRotation = Integer.MIN_VALUE;
    private int appliedPreviewRotation = Integer.MIN_VALUE;
    private boolean appliedPreviewMirrored;
    private boolean previewDiagnosticsPending;


    public SharedCameraPreviewView(
            Context context,
            Logger logger,
            SurfaceHandle surfaceHandle,
            Consumer<CharSequence> transientNotice,
            Consumer<CharSequence> persistentNotice,
            Runnable clearPersistentNotice) {
        super(context);
        this.logger = Objects.requireNonNull(logger, "logger");
        this.surfaceHandle = Objects.requireNonNull(surfaceHandle, "surfaceHandle");
        this.transientNotice = Objects.requireNonNull(transientNotice, "transientNotice");
        this.persistentNotice = Objects.requireNonNull(persistentNotice, "persistentNotice");
        this.clearPersistentNotice = Objects.requireNonNull(
                clearPersistentNotice, "clearPersistentNotice");
        setBackgroundColor(Color.rgb(17, 17, 17));

        textureView = new TextureView(context);
        textureView.setSurfaceTexture(surfaceHandle.surfaceTexture());
        textureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override public void onSurfaceTextureAvailable(
                    SurfaceTexture surfaceTexture, int width, int height) {
                CameraResolution buffer = surfaceHandle.restoreBufferSize();
                previewSurfaceAvailable = true;
                publishPreviewExpected();
                logger.info(LogCategory.CAMERA, "unspecified", "shared_camera_preview stage=attach outcome=surface_available"
                        + " view=" + width + "x" + height + " buffer=" + buffer);
            }

            @Override public void onSurfaceTextureSizeChanged(
                    SurfaceTexture surfaceTexture, int width, int height) {
                surfaceHandle.restoreBufferSize();
            }

            @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
                previewSurfaceAvailable = false;
                publishPreviewExpected();
                logger.info(LogCategory.CAMERA, "unspecified", "shared_camera_preview stage=detach cameraLifetime=retained");
                return false;
            }

            @Override public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {
                onPreviewFrame();
            }
        });
        textureViewport = new FrameLayout(context);
        textureViewport.setClipChildren(true);
        textureViewport.setClipToPadding(true);
        LayoutParams viewportLayout = new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        viewportLayout.gravity = Gravity.CENTER;
        addView(textureViewport, viewportLayout);
        LayoutParams textureLayout = new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        textureLayout.gravity = Gravity.CENTER;
        textureViewport.addView(textureView, textureLayout);
        layoutChangedCallback =
                () -> post(() -> updateTextureLayout(getWidth(), getHeight()));
        surfaceHandle.setLayoutChangedCallback(layoutChangedCallback);

        startupOverlay = new FrameLayout(context);
        startupOverlay.setBackgroundColor(Color.BLACK);
        LinearLayout startupPanel = new LinearLayout(context);
        startupPanel.setOrientation(LinearLayout.VERTICAL);
        startupPanel.setGravity(Gravity.CENTER);
        startupPanel.setPadding(dp(28), dp(20), dp(28), dp(20));
        startupPanel.setBackgroundResource(R.drawable.bg_setting_card);

        startupProgress = new ProgressBar(
                context, null, android.R.attr.progressBarStyleHorizontal);
        ColorStateList progressTint = ColorStateList.valueOf(Color.rgb(142, 200, 255));
        startupProgress.setIndeterminateTintList(progressTint);
        startupProgress.setProgressTintList(progressTint);
        startupProgress.setIndeterminate(true);
        startupPanel.addView(startupProgress,
                new LinearLayout.LayoutParams(dp(240), dp(8)));

        startupMessage = new TextView(context);
        startupMessage.setTextAppearance(R.style.DcamStatusText_Bold);
        startupMessage.setText(R.string.camera_starting);
        startupMessage.setGravity(Gravity.CENTER);
        startupMessage.setTextSize(16f);
        LinearLayout.LayoutParams startupMessageLayout = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        startupMessageLayout.topMargin = dp(12);
        startupPanel.addView(startupMessage, startupMessageLayout);

        startupDetail = new TextView(context);
        startupDetail.setTextAppearance(R.style.DcamStatusText);
        startupDetail.setGravity(Gravity.CENTER);
        startupDetail.setTextSize(14f);
        startupDetail.setVisibility(GONE);
        LinearLayout.LayoutParams startupDetailLayout = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        startupDetailLayout.topMargin = dp(8);
        startupPanel.addView(startupDetail, startupDetailLayout);

        startupOverlay.addView(startupPanel, new LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER));
        addView(startupOverlay, new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        showStarting();
    }

    @Override protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if (textureView == null) return;
        publishPreviewExpected();
    }

    boolean isPreviewExpected() {
        return previewSurfaceAvailable && getWindowVisibility() == VISIBLE;
    }

    private void publishPreviewExpected() {
        previewExpectedChanged.accept(isPreviewExpected());
    }

    public SurfaceHandle surfaceHandle() {
        return surfaceHandle;
    }

    void setPreviewExpectedChanged(Consumer<Boolean> listener) {
        previewExpectedChanged = Objects.requireNonNull(listener, "listener");
    }

    void clearPreviewExpectedChanged() {
        previewExpectedChanged = ignored -> {};
    }

    void detachSurfaceHandle() {
        surfaceHandle.clearLayoutChangedCallback(layoutChangedCallback);
    }

    public void showStarting() {
        if (deferToUiThread(this::showStarting)) return;
        startupProgress.setIndeterminate(true);
        startupProgress.setVisibility(VISIBLE);
        startupDetail.setVisibility(GONE);
        startupMessage.setText(R.string.camera_starting);
        hideStartupOnNextFrame = false;
        startupOverlay.setVisibility(VISIBLE);
    }

    public void showCheckingCapabilities() {
        if (deferToUiThread(this::showCheckingCapabilities)) return;
        startupProgress.setIndeterminate(false);
        startupProgress.setMax(1);
        startupProgress.setProgress(0);
        startupProgress.setVisibility(VISIBLE);
        startupDetail.setText(R.string.camera_capabilities_recheck_preparing);
        startupDetail.setVisibility(VISIBLE);
        startupMessage.setText(R.string.capture_quality_checking);
        hideStartupOnNextFrame = false;
        startupOverlay.setVisibility(VISIBLE);
    }

    public void showCheckingCapabilities(String profile, String cameraId, String stage,
            int completed, int total, String detail) {
        if (deferToUiThread(() -> showCheckingCapabilities(
                profile, cameraId, stage, completed, total, detail))) return;
        showCheckingCapabilities();
        startupProgress.setIndeterminate(false);
        startupProgress.setMax(total);
        startupProgress.setProgress(completed);
        String stageText = switch (stage) {
            case "preparing" -> getContext().getString(
                    R.string.camera_capabilities_stage_preparing);
            case "fast_scan" -> getContext().getString(
                    R.string.camera_capabilities_stage_fast_scan);
            case "standalone_image_verify" -> getContext().getString(
                    R.string.camera_capabilities_stage_standalone_image);
            case "exhaustive_real_verify" -> getContext().getString(
                    R.string.camera_capabilities_stage_tuple);
            default -> stage;
        };
        String cameraText = "all".equals(cameraId)
                ? getContext().getString(R.string.camera_capabilities_all_cameras)
                : cameraId;
        String summary = getContext().getString(
                R.string.camera_capabilities_recheck_progress_detail,
                profile, cameraText, stageText, completed, total);
        String target = progressTarget(detail);
        startupDetail.setText(target.isBlank() ? summary : summary + "\n" + target);
        startupDetail.setVisibility(VISIBLE);
    }

    public void hideStartupOverlay() {
        if (deferToUiThread(this::hideStartupOverlay)) return;
        hideStartupOnNextFrame = false;
        startupOverlay.setVisibility(GONE);
    }


    public void showCapabilityCheckFailed() {
        if (deferToUiThread(this::showCapabilityCheckFailed)) return;
        startupProgress.setVisibility(GONE);
        startupDetail.setVisibility(GONE);
        startupMessage.setText(R.string.camera_capabilities_recheck_failed);
        hideStartupOnNextFrame = false;
        startupOverlay.setVisibility(VISIBLE);
    }

    public void showPreviewWhenFrameArrives() {
        if (deferToUiThread(this::showPreviewWhenFrameArrives)) return;
        hideStartupOnNextFrame = true;
    }

    public void showStorageWarning(String text) {
        if (deferToUiThread(() -> showStorageWarning(text))) return;
        storageWarning = text;
        persistentNotice.accept(text);
    }

    public void clearStorageWarning() {
        if (deferToUiThread(this::clearStorageWarning)) return;
        if (storageWarning == null) return;
        storageWarning = null;
        clearPersistentNotice.run();
    }

    public void showTransientError(String text) {
        if (deferToUiThread(() -> showTransientError(text))) return;
        CharSequence message = text == null ? "Camera failed" : text;
        transientNotice.accept(message);
    }


    private boolean deferToUiThread(Runnable action) {
        if (Looper.myLooper() == Looper.getMainLooper()) return false;
        post(action);
        return true;
    }

    private static String progressTarget(String detail) {
        int image = detail.indexOf(" image=");
        if (image >= 0) return detail.substring(image + " image=".length());
        int tuple = detail.indexOf(" tuple=");
        if (tuple >= 0) return detail.substring(tuple + " tuple=".length());
        return "";
    }


    @Override protected void onSizeChanged(int width, int height,
            int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        post(() -> updateTextureLayout(getWidth(), getHeight()));
    }

    private void updateTextureLayout(int containerWidth, int containerHeight) {
        CameraResolution bufferResolution = surfaceHandle.currentResolution();
        CameraResolution displayResolution = surfaceHandle.currentDisplayResolution();
        int sensorOrientation = surfaceHandle.currentSensorOrientation();
        int displayRotation = surfaceHandle.currentDisplayRotation();
        int outputRotation = surfaceHandle.currentRotation();
        if (bufferResolution == null || displayResolution == null
                || sensorOrientation == Integer.MIN_VALUE
                || displayRotation == Integer.MIN_VALUE
                || outputRotation == Integer.MIN_VALUE
                || containerWidth <= 0 || containerHeight <= 0) return;
        boolean mirrored = surfaceHandle.currentMirrored();
        PreviewSizeCalculator.Layout previewLayout = PreviewSizeCalculator.containLayout(
                containerWidth, containerHeight, displayResolution, bufferResolution,
                displayRotation, outputRotation);
        int viewportWidth = previewLayout.viewportWidth();
        int viewportHeight = previewLayout.viewportHeight();
        int textureLayoutWidth = previewLayout.textureLayoutWidth();
        int textureLayoutHeight = previewLayout.textureLayoutHeight();
        int previewRotation = previewLayout.previewRotationDegrees();
        if (textureLayoutWidth == appliedPreviewWidth
                && textureLayoutHeight == appliedPreviewHeight
                && viewportWidth == appliedViewportWidth
                && viewportHeight == appliedViewportHeight
                && outputRotation == appliedOutputRotation
                && previewRotation == appliedPreviewRotation
                && mirrored == appliedPreviewMirrored) return;
        LayoutParams viewportLayout = (LayoutParams) textureViewport.getLayoutParams();
        viewportLayout.width = viewportWidth;
        viewportLayout.height = viewportHeight;
        viewportLayout.gravity = Gravity.CENTER;
        textureViewport.setLayoutParams(viewportLayout);
        LayoutParams layout = (LayoutParams) textureView.getLayoutParams();
        layout.width = textureLayoutWidth;
        layout.height = textureLayoutHeight;
        layout.gravity = Gravity.CENTER;
        textureView.setLayoutParams(layout);
        textureView.setPivotX(textureLayoutWidth / 2f);
        textureView.setPivotY(textureLayoutHeight / 2f);
        textureView.setRotation(previewRotation);
        Matrix appliedTransform = new Matrix();
        if (mirrored && previewRotation % 180 == 0) {
            appliedTransform.setScale(
                    -1f, 1f, textureLayoutWidth / 2f, textureLayoutHeight / 2f);
        } else if (mirrored) {
            appliedTransform.setScale(
                    1f, -1f, textureLayoutWidth / 2f, textureLayoutHeight / 2f);
        }
        textureView.setTransform(appliedTransform);
        previewDiagnosticsPending = true;
        textureView.postDelayed(this::logPreviewDiagnostics, 100L);
        appliedPreviewWidth = textureLayoutWidth;
        appliedPreviewHeight = textureLayoutHeight;
        appliedViewportWidth = viewportWidth;
        appliedViewportHeight = viewportHeight;
        appliedOutputRotation = outputRotation;
        appliedPreviewRotation = previewRotation;
        appliedPreviewMirrored = mirrored;
    }

    private void logPreviewDiagnostics() {
        if (!previewDiagnosticsPending) return;
        LayoutParams layout = (LayoutParams) textureView.getLayoutParams();
        int[] viewportLocation = new int[2];
        int[] textureLocation = new int[2];
        int[] rootLocation = new int[2];
        textureViewport.getLocationOnScreen(viewportLocation);
        textureView.getLocationOnScreen(textureLocation);
        getRootView().getLocationOnScreen(rootLocation);
        Rect visibleFrame = new Rect();
        getWindowVisibleDisplayFrame(visibleFrame);
        WindowInsets windowInsets = getRootWindowInsets();
        int systemLeft = windowInsets == null ? 0 : windowInsets.getSystemWindowInsetLeft();
        int systemTop = windowInsets == null ? 0 : windowInsets.getSystemWindowInsetTop();
        int systemRight = windowInsets == null ? 0 : windowInsets.getSystemWindowInsetRight();
        int systemBottom = windowInsets == null ? 0 : windowInsets.getSystemWindowInsetBottom();
        Rect cutoutInsets = displayCutoutInsets(windowInsets);
        Rect insetSafeBounds = new Rect(
                rootLocation[0] + Math.max(systemLeft, cutoutInsets.left),
                rootLocation[1] + Math.max(systemTop, cutoutInsets.top),
                rootLocation[0] + getRootView().getWidth()
                        - Math.max(systemRight, cutoutInsets.right),
                rootLocation[1] + getRootView().getHeight()
                        - Math.max(systemBottom, cutoutInsets.bottom));
        Rect safeAreaBounds = new Rect(insetSafeBounds);
        if (!safeAreaBounds.intersect(visibleFrame)) safeAreaBounds.setEmpty();
        Rect renderedPreviewBounds = new Rect(
                viewportLocation[0], viewportLocation[1],
                viewportLocation[0] + textureViewport.getWidth(),
                viewportLocation[1] + textureViewport.getHeight());
        boolean previewInsideSafeArea = safeAreaBounds.contains(renderedPreviewBounds);
        boolean layoutApplied = layout.width == textureView.getWidth()
                && layout.height == textureView.getHeight();
        previewDiagnosticsPending = false;
        if (previewInsideSafeArea && layoutApplied) return;
        Matrix viewTransform = textureView.getTransform(null);
        float[] surfaceTextureTransform = new float[16];
        surfaceHandle.copySurfaceTextureTransform(surfaceTextureTransform);
        logger.warn(LogCategory.CAMERA, "unspecified", null, "shared_camera_preview stage=transform_validation outcome=failed"
                + " buffer=" + surfaceHandle.currentResolution()
                + " display=" + surfaceHandle.currentDisplayResolution()
                + " sensorDegrees=" + surfaceHandle.currentSensorOrientation()
                + " displayDegrees=" + surfaceHandle.currentDisplayRotation()
                + " outputDegrees=" + surfaceHandle.currentRotation()
                + " previewDegrees=" + appliedPreviewRotation
                + " container=" + getWidth() + "x" + getHeight()
                + " viewportLayout=" + textureViewport.getLayoutParams().width + "x"
                + textureViewport.getLayoutParams().height
                + " viewportMeasured=" + textureViewport.getWidth() + "x"
                + textureViewport.getHeight()
                + " viewportBounds=" + textureViewport.getLeft() + ","
                + textureViewport.getTop() + "," + textureViewport.getRight() + ","
                + textureViewport.getBottom()
                + " viewportScreen=" + viewportLocation[0] + "," + viewportLocation[1]
                + " clipChildren=" + textureViewport.getClipChildren()
                + " clipToPadding=" + textureViewport.getClipToPadding()
                + " textureLayout=" + layout.width + "x" + layout.height
                + " textureMeasured=" + textureView.getWidth() + "x" + textureView.getHeight()
                + " textureBounds=" + textureView.getLeft() + "," + textureView.getTop()
                + "," + textureView.getRight() + "," + textureView.getBottom()
                + " textureScreen=" + textureLocation[0] + "," + textureLocation[1]
                + " systemBarInsets=" + systemLeft + "," + systemTop + ","
                + systemRight + "," + systemBottom
                + " displayCutoutInsets=" + cutoutInsets.left + "," + cutoutInsets.top + ","
                + cutoutInsets.right + "," + cutoutInsets.bottom
                + " windowVisibleFrame=" + visibleFrame.toShortString()
                + " safeAreaBounds=" + safeAreaBounds.toShortString()
                + " renderedPreviewBounds=" + renderedPreviewBounds.toShortString()
                + " previewInsideSafeArea=" + previewInsideSafeArea
                + " layoutApplied=" + layoutApplied
                + " textureRotation=" + textureView.getRotation()
                + " textureScale=" + textureView.getScaleX() + "x" + textureView.getScaleY()
                + " texturePivot=" + textureView.getPivotX() + "x" + textureView.getPivotY()
                + " viewMatrixIdentity=" + viewTransform.isIdentity()
                + " viewMatrix=" + matrixValues(viewTransform)
                + " surfaceTextureMatrix=" + matrixValues(surfaceTextureTransform), null);
    }

    private static Rect displayCutoutInsets(WindowInsets windowInsets) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P || windowInsets == null) {
            return new Rect();
        }
        return displayCutoutInsetsApi28(windowInsets);
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private static Rect displayCutoutInsetsApi28(WindowInsets windowInsets) {
        DisplayCutout cutout = windowInsets.getDisplayCutout();
        if (cutout == null) return new Rect();
        return new Rect(
                cutout.getSafeInsetLeft(),
                cutout.getSafeInsetTop(),
                cutout.getSafeInsetRight(),
                cutout.getSafeInsetBottom());
    }

    private static String matrixValues(Matrix matrix) {
        float[] values = new float[9];
        matrix.getValues(values);
        return java.util.Arrays.toString(values);
    }

    private static String matrixValues(float[] values) {
        return java.util.Arrays.toString(values);
    }

    private void onPreviewFrame() {
        if (!surfaceHandle.onFrame()) return;
        updateTextureLayout(getWidth(), getHeight());
        if (!hideStartupOnNextFrame) return;
        hideStartupOnNextFrame = false;
        startupOverlay.setVisibility(GONE);
        logger.info(LogCategory.CAMERA, "unspecified", "Camera preview received its first frame and hid the startup cover.");
    }


    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    public static final class SurfaceHandle implements SharedCameraPreviewOutput, AutoCloseable {
        private final SurfaceTexture surfaceTexture = new SurfaceTexture(false);
        private final Surface surface = new Surface(surfaceTexture);
        private Handler frameHandler;
        private Runnable frameCallback;
        private boolean closed;
        private CameraResolution currentResolution;
        private CameraResolution displayResolution;
        private int sensorOrientationDegrees = Integer.MIN_VALUE;
        private int displayRotationDegrees = Integer.MIN_VALUE;
        private int rotationDegrees = Integer.MIN_VALUE;
        private boolean mirrored;
        private Runnable layoutChangedCallback;

        @Override public synchronized Surface surface() {
            if (closed) throw new IllegalStateException("preview surface closed");
            return surface;
        }

        synchronized SurfaceTexture surfaceTexture() {
            if (closed) throw new IllegalStateException("preview surface closed");
            return surfaceTexture;
        }

        @Override public synchronized void resize(CameraResolution resolution) {
            Objects.requireNonNull(resolution, "resolution");
            if (closed) throw new IllegalStateException("preview surface closed");
            currentResolution = resolution;
            restoreBufferSize();
        }

        synchronized CameraResolution restoreBufferSize() {
            if (closed) throw new IllegalStateException("preview surface closed");
            CameraResolution resolution = currentResolution;
            if (resolution != null) {
                // TextureView resets producer buffer size when its UI layout changes.
                surfaceTexture.setDefaultBufferSize(resolution.width(), resolution.height());
            }
            return resolution;
        }

        synchronized CameraResolution currentResolution() {
            return currentResolution;
        }

        synchronized CameraResolution currentDisplayResolution() {
            return displayResolution;
        }

        @Override public synchronized void setDisplayResolution(CameraResolution resolution) {
            displayResolution = Objects.requireNonNull(resolution, "resolution");
        }

        synchronized void copySurfaceTextureTransform(float[] destination) {
            if (closed) throw new IllegalStateException("preview surface closed");
            surfaceTexture.getTransformMatrix(destination);
        }

        synchronized int currentSensorOrientation() {
            return sensorOrientationDegrees;
        }

        synchronized int currentDisplayRotation() {
            return displayRotationDegrees;
        }

        synchronized int currentRotation() {
            return rotationDegrees;
        }

        synchronized boolean currentMirrored() {
            return mirrored;
        }

        @Override public synchronized void setSensorOrientation(int sensorOrientationDegrees) {
            if (closed) throw new IllegalStateException("preview surface closed");
            this.sensorOrientationDegrees = CameraOrientation.normalize(sensorOrientationDegrees);
        }

        @Override public synchronized void setDisplayRotation(int displayRotationDegrees) {
            if (closed) throw new IllegalStateException("preview surface closed");
            this.displayRotationDegrees = CameraOrientation.normalize(displayRotationDegrees);
        }

        @Override public void setRotation(int rotationDegrees) {
            Runnable callback;
            synchronized (this) {
                if (closed) throw new IllegalStateException("preview surface closed");
                this.rotationDegrees = CameraOrientation.normalize(rotationDegrees);
                callback = frameCallback == null ? null : layoutChangedCallback;
            }
            if (callback != null) callback.run();
        }

        synchronized void setLayoutChangedCallback(Runnable callback) {
            if (closed) throw new IllegalStateException("preview surface closed");
            layoutChangedCallback = Objects.requireNonNull(callback, "callback");
        }

        synchronized void clearLayoutChangedCallback(Runnable callback) {
            if (layoutChangedCallback == callback) layoutChangedCallback = null;
        }

        @Override public synchronized void setMirrored(boolean mirrored) {
            if (closed) throw new IllegalStateException("preview surface closed");
            this.mirrored = mirrored;
        }

        @Override public synchronized void start(Handler handler, Runnable onFrame) {
            if (closed) throw new IllegalStateException("preview surface closed");
            frameHandler = Objects.requireNonNull(handler, "handler");
            frameCallback = Objects.requireNonNull(onFrame, "onFrame");
        }

        boolean onFrame() {
            Handler handler;
            Runnable callback;
            synchronized (this) {
                handler = frameHandler;
                callback = frameCallback;
            }
            if (handler == null || callback == null) return false;
            handler.post(callback);
            return true;
        }

        @Override public synchronized void stop() {
            frameCallback = null;
            frameHandler = null;
        }

        @Override public synchronized void close() {
            if (closed) return;
            stop();
            layoutChangedCallback = null;
            closed = true;
            surface.release();
            surfaceTexture.release();
        }
    }
}
