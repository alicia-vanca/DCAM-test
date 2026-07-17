package com.dvid.dcam.app.shell;

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
    private static final Handler HANDLER = new Handler(Looper.getMainLooper());
    private static TextView persistentView;
    private static TextView transientView;
    private static Runnable transientHide;

    private FloatingNotice() {}

    public static void show(Context context, @StringRes int message) {
        show(context, context.getString(message));
    }

    public static void show(Context context, CharSequence message) {
        if (!(context instanceof Activity)) {
            Toast.makeText(context.getApplicationContext(), message, Toast.LENGTH_SHORT).show();
            return;
        }
        hideTransient();
        Activity activity = (Activity) context;
        transientView = add(activity, message, persistentView == null ? 64 : 116);
        transientHide = FloatingNotice::hideTransient;
        HANDLER.postDelayed(transientHide, 2_000L);
    }

    public static void showPersistent(Context context, @StringRes int message) {
        hidePersistent();
        if (!(context instanceof Activity)) {
            Toast.makeText(context.getApplicationContext(), message, Toast.LENGTH_LONG).show();
            return;
        }
        Activity activity = (Activity) context;
        persistentView = add(activity, activity.getString(message), 64);
    }

    public static void clear(Activity activity) {
        if (persistentView != null && persistentView.getContext() == activity) hidePersistent();
        if (transientView != null && transientView.getContext() == activity) hideTransient();
    }

    public static void hidePersistent() {
        remove(persistentView);
        persistentView = null;
    }

    private static void hideTransient() {
        if (transientHide != null) HANDLER.removeCallbacks(transientHide);
        remove(transientView);
        transientView = null;
        transientHide = null;
    }

    private static TextView add(Activity activity, CharSequence message, int bottomDp) {
        ViewGroup content = activity.findViewById(android.R.id.content);
        TextView text = noticeView(activity, message);
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
