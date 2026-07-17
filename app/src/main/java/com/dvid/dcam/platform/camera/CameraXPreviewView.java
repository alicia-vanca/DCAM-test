package com.dvid.dcam.platform.camera;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;
import androidx.camera.core.Preview;
import androidx.camera.view.PreviewView;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.Observer;

/** CameraX preview surface and camera-facing status text. */
@SuppressLint("ViewConstructor")
public final class CameraXPreviewView extends FrameLayout {
    private final PreviewView previewView;
    private final TextView message;
    private final MessageState messageState = new MessageState();
    private final Observer<PreviewView.StreamState> startingObserver =
            new Observer<PreviewView.StreamState>() {
                @Override public void onChanged(PreviewView.StreamState state) {
                    if (state != PreviewView.StreamState.STREAMING) return;
                    previewView.getPreviewStreamState().removeObserver(this);
                    clearMessage(MessageOwner.STARTING);
                }
            };

    public CameraXPreviewView(Context context) {
        super(context);
        setBackgroundColor(Color.rgb(17, 17, 17));

        previewView = new PreviewView(context);
        previewView.setScaleType(PreviewView.ScaleType.FILL_CENTER);
        addView(previewView, new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        message = new TextView(context);
        message.setTextColor(Color.WHITE);
        message.setTextSize(11);
        message.setGravity(Gravity.CENTER);
        message.setPadding(dp(10), dp(5), dp(10), dp(5));
        message.setBackgroundResource(com.dvid.dcam.R.drawable.bg_preview_message);
        LayoutParams messageLayout = new LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        messageLayout.setMargins(dp(8), dp(8), dp(8), dp(8));
        addView(message, messageLayout);
        clearMessage();
    }

    @Override protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        com.dvid.dcam.platform.logging.DcamLogger.i(
                "Camera preview window visibility=" + visibility + " attached=" + isAttachedToWindow());
    }

    @Override protected void onDetachedFromWindow() {
        com.dvid.dcam.platform.logging.DcamLogger.i("Camera preview detached from window");
        super.onDetachedFromWindow();
    }

    Preview.SurfaceProvider surfaceProvider() {
        return previewView.getSurfaceProvider();
    }

    void showPermissionRequired() {
        showMessage(MessageOwner.PERMISSION,
                getContext().getString(com.dvid.dcam.R.string.camera_permission_required));
    }

    void showStarting(LifecycleOwner lifecycleOwner) {
        showMessage(MessageOwner.STARTING,
                getContext().getString(com.dvid.dcam.R.string.camera_starting));
        previewView.getPreviewStreamState().observe(lifecycleOwner, startingObserver);
    }

    void clearMessage() {
        showMessage(MessageOwner.NONE, "");
    }

    void showCameraInterrupted(String text) {
        showMessage(MessageOwner.INTERRUPTED, text);
        message.setTextColor(Color.YELLOW);
    }

    void clearCameraInterrupted() {
        clearMessage(MessageOwner.INTERRUPTED);
    }
    void showRecording(String fileName) {
        clearMessage();
    }

    public void showStorageWarning(String text) {
        showMessage(MessageOwner.STORAGE, text);
        message.setTextColor(Color.rgb(255, 196, 0));
    }

    public void clearStorageWarning() {
        clearMessage(MessageOwner.STORAGE);
    }

    void showError(String text) {
        showMessage(MessageOwner.ERROR, text == null ? "Camera failed" : text);
        message.setTextColor(Color.RED);
    }

    private void showMessage(MessageOwner owner, String text) {
        messageState.show(owner);
        message.setTextColor(Color.WHITE);
        message.setText(text);
        message.setVisibility(text == null || text.isEmpty() ? GONE : VISIBLE);
    }

    private void clearMessage(MessageOwner owner) {
        if (messageState.clear(owner)) showMessage(MessageOwner.NONE, "");
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    enum MessageOwner { NONE, STARTING, PERMISSION, STORAGE, ERROR, INTERRUPTED }

    static final class MessageState {
        private MessageOwner owner = MessageOwner.NONE;

        void show(MessageOwner owner) { this.owner = owner; }

        boolean clear(MessageOwner owner) {
            if (this.owner != owner) return false;
            this.owner = MessageOwner.NONE;
            return true;
        }
    }
}
