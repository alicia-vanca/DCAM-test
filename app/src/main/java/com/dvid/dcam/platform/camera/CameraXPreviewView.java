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

/** CameraX preview surface and camera-facing status text. */
@SuppressLint("ViewConstructor")
public final class CameraXPreviewView extends FrameLayout {
    private final PreviewView previewView;
    private final TextView message;

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

    Preview.SurfaceProvider surfaceProvider() {
        return previewView.getSurfaceProvider();
    }

    void showPermissionRequired() {
        showMessage(getContext().getString(com.dvid.dcam.R.string.camera_permission_required));
    }

    void showStarting() { showMessage("Starting camera..."); }

    void clearMessage() {
        showMessage("");
    }

    void showRecording(String fileName) {
        clearMessage();
    }

    public void showStorageWarning(String text) {
        message.setVisibility(VISIBLE);
        message.setTextColor(Color.rgb(255, 196, 0));
        message.setText(text);
    }

    public void clearStorageWarning() {
        clearMessage();
    }

    void showError(String text) {
        message.setVisibility(VISIBLE);
        message.setTextColor(Color.RED);
        message.setText(text == null ? "Camera failed" : text);
    }

    private void showMessage(String text) {
        message.setTextColor(Color.WHITE);
        message.setText(text);
        message.setVisibility(text == null || text.isEmpty() ? GONE : VISIBLE);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
