package com.dvid.dcam.app.ui;

import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.os.Build;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.FrameLayout;

import androidx.activity.ComponentActivity;

import com.dvid.dcam.R;
import com.dvid.dcam.databinding.ActivityMainBinding;
import java.util.function.BooleanSupplier;

public final class ActivityChromeController {
    private final ComponentActivity activity;
    private final ActivityMainBinding binding;
    private final BooleanSupplier deviceOwner;

    public ActivityChromeController(
            ComponentActivity activity,
            ActivityMainBinding binding,
            BooleanSupplier deviceOwner) {
        this.activity = activity;
        this.binding = binding;
        this.deviceOwner = deviceOwner;
    }

    public void hideSystemStatusBar() {
        if (!deviceOwner.getAsBoolean()) {
            activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            return;
        }
        activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            View decor = activity.getWindow().getDecorView();
            WindowInsetsController controller = decor.getWindowInsetsController();
            if (controller != null) {
                controller.setSystemBarsBehavior(
                        WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                controller.hide(WindowInsets.Type.statusBars());
            }
            return;
        }
        activity.getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }

    public void updateManagedTopBar() {
        boolean visible = deviceOwner.getAsBoolean();
        binding.managedTopBar.setVisibility(visible ? View.VISIBLE : View.GONE);
        FrameLayout.LayoutParams contentLayout =
                (FrameLayout.LayoutParams) binding.contentRoot.getLayoutParams();
        int topMargin = visible ? dp(24) : 0;
        if (contentLayout.topMargin != topMargin) {
            contentLayout.topMargin = topMargin;
            binding.contentRoot.setLayoutParams(contentLayout);
        }
        if (!visible) return;

        BatteryManager battery = activity.getSystemService(BatteryManager.class);
        int batteryPercent = battery == null ? -1
                : battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
        binding.managedBatteryStatus.setPercent(batteryPercent);
        binding.managedBatteryStatus.setContentDescription(batteryPercent < 0
                ? activity.getString(R.string.battery_unknown)
                : activity.getString(R.string.battery_percent, batteryPercent));

        ConnectivityManager connectivity = activity.getSystemService(ConnectivityManager.class);
        NetworkCapabilities capabilities = connectivity == null ? null
                : connectivity.getNetworkCapabilities(connectivity.getActiveNetwork());
        boolean wifiConnected = capabilities != null
                && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
        int wifiLevel = 0;
        if (wifiConnected) {
            int rssi = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                    ? capabilities.getSignalStrength()
                    : NetworkCapabilities.SIGNAL_STRENGTH_UNSPECIFIED;
            if (rssi == NetworkCapabilities.SIGNAL_STRENGTH_UNSPECIFIED) {
                WifiManager wifi = activity.getSystemService(WifiManager.class);
                if (wifi != null && wifi.getConnectionInfo() != null) {
                    rssi = wifi.getConnectionInfo().getRssi();
                }
            }
            wifiLevel = rssi == NetworkCapabilities.SIGNAL_STRENGTH_UNSPECIFIED
                    ? 1 : WifiManager.calculateSignalLevel(rssi, 4) + 1;
        }
        binding.managedWifiStatus.setSignal(wifiConnected, wifiLevel);
        binding.managedWifiStatus.setContentDescription(wifiConnected
                ? activity.getString(R.string.wifi_signal_level, wifiLevel)
                : activity.getString(R.string.wifi_disconnected));
    }

    public void bindSystemNavigationInset() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return;
        binding.getRoot().setOnApplyWindowInsetsListener((view, insets) -> {
            int bottom = insets.getInsets(WindowInsets.Type.navigationBars()).bottom;
            FrameLayout.LayoutParams layout =
                    (FrameLayout.LayoutParams) binding.customStatusBar.getLayoutParams();
            layout.bottomMargin = bottom + dp(8);
            binding.customStatusBar.setLayoutParams(layout);
            return insets;
        });
        binding.getRoot().requestApplyInsets();
    }

    private int dp(int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}