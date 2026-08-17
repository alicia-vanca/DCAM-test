package com.dvid.dcam.app.ui;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.StringRes;
import com.dvid.dcam.R;

/** Single app-wide style and entry point for floating notices. */
public final class FloatingNotice {
    public static final long TRANSIENT_DURATION_MS = 2_000L;
    public static final int WARNING_TEXT_COLOR = Color.YELLOW;
    public static final int ERROR_TEXT_COLOR = Color.rgb(255, 179, 179);
    private static final Handler HANDLER = new Handler(Looper.getMainLooper());
    private static TextView persistentView;
    private static Activity lowPriorityPersistentActivity;
    private static CharSequence lowPriorityPersistentMessage;
    private static int lowPriorityPersistentTextColor = Color.WHITE;
    private static TextView lowPriorityPersistentView;
    private static TextView transientView;
    private static Runnable transientHide;

    private FloatingNotice() {}

    public static void show(Context context, @StringRes int message) {
        show(context, context.getString(message));
    }

    public static void show(Context context, @StringRes int message, int textColor) {
        show(context, context.getString(message), textColor);
    }

    public static void show(Context context, CharSequence message) {
        show(context, message, Color.WHITE);
    }

    public static void show(Context context, CharSequence message, int textColor) {
        if (!(context instanceof Activity)) {
            Toast.makeText(context.getApplicationContext(), message, Toast.LENGTH_SHORT).show();
            return;
        }
        hideTransient();
        Activity activity = (Activity) context;
        transientView = add(activity, message,
                persistentView == null && lowPriorityPersistentView == null ? 64 : 116,
                textColor);
        transientHide = FloatingNotice::hideTransient;
        HANDLER.postDelayed(transientHide, TRANSIENT_DURATION_MS);
    }

    public static void showPersistent(Context context, @StringRes int message) {
        showPersistent(context, context.getString(message));
    }

    public static void showPersistent(Context context, CharSequence message) {
        remove(persistentView);
        persistentView = null;
        remove(lowPriorityPersistentView);
        lowPriorityPersistentView = null;
        if (!(context instanceof Activity)) {
            Toast.makeText(context.getApplicationContext(), message, Toast.LENGTH_LONG).show();
            return;
        }
        Activity activity = (Activity) context;
        persistentView = add(activity, message, 64);
    }

    public static void showLowPriorityPersistent(Context context, CharSequence message) {
        showLowPriorityPersistent(context, message, Color.WHITE);
    }

    public static void showLowPriorityPersistent(
            Context context, CharSequence message, int textColor) {
        if (!(context instanceof Activity)) return;
        Activity activity = (Activity) context;
        lowPriorityPersistentActivity = activity;
        lowPriorityPersistentMessage = message;
        lowPriorityPersistentTextColor = textColor;
        if (persistentView != null) {
            remove(lowPriorityPersistentView);
            lowPriorityPersistentView = null;
            return;
        }
        if (lowPriorityPersistentView != null
                && lowPriorityPersistentView.getContext() == activity) {
            lowPriorityPersistentView.setText(message);
            lowPriorityPersistentView.setTextColor(textColor);
            return;
        }
        remove(lowPriorityPersistentView);
        lowPriorityPersistentView = add(activity, message, 64, textColor);
    }

    public static void hideLowPriorityPersistent() {
        remove(lowPriorityPersistentView);
        lowPriorityPersistentView = null;
        lowPriorityPersistentActivity = null;
        lowPriorityPersistentMessage = null;
        lowPriorityPersistentTextColor = Color.WHITE;
    }

    public static void clear(Activity activity) {
        if (persistentView != null && persistentView.getContext() == activity) {
            remove(persistentView);
            persistentView = null;
        }
        if (lowPriorityPersistentActivity == activity) hideLowPriorityPersistent();
        if (transientView != null && transientView.getContext() == activity) hideTransient();
    }

    public static void hidePersistent() {
        remove(persistentView);
        persistentView = null;
        restoreLowPriorityPersistent();
    }

    private static void restoreLowPriorityPersistent() {
        Activity activity = lowPriorityPersistentActivity;
        if (activity == null || lowPriorityPersistentMessage == null
                || activity.isFinishing() || activity.isDestroyed()) return;
        remove(lowPriorityPersistentView);
        lowPriorityPersistentView = add(
                activity, lowPriorityPersistentMessage, 64, lowPriorityPersistentTextColor);
    }

    private static void hideTransient() {
        if (transientHide != null) HANDLER.removeCallbacks(transientHide);
        remove(transientView);
        transientView = null;
        transientHide = null;
    }

    private static TextView add(Activity activity, CharSequence message, int bottomDp) {
        return add(activity, message, bottomDp, Color.WHITE);
    }

    private static TextView add(
            Activity activity, CharSequence message, int bottomDp, int textColor) {
        ViewGroup content = activity.findViewById(android.R.id.content);
        TextView text = noticeView(activity, message);
        text.setTextColor(textColor);
        FrameLayout.LayoutParams layout = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        layout.bottomMargin = dp(activity, bottomDp);
        content.addView(text, layout);
        return text;
    }

    private static void remove(TextView view) {
        if (view != null && view.getParent() instanceof ViewGroup) {
            ((ViewGroup) view.getParent()).removeView(view);
        }
    }

    private static TextView noticeView(Context context, CharSequence message) {
        TextView text = new TextView(context);
        text.setBackgroundResource(R.drawable.bg_preview_message);
        text.setGravity(Gravity.CENTER);
        text.setPadding(dp(context, 16), dp(context, 10),
                dp(context, 16), dp(context, 10));
        text.setText(message);
        text.setTextColor(Color.WHITE);
        text.setTextSize(14);
        return text;
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
