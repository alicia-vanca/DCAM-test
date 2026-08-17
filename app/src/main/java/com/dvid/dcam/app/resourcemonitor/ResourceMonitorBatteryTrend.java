package com.dvid.dcam.app.resourcemonitor;

import java.util.Locale;

/** Estimates battery rate from current draw without waiting for capacity percentage changes. */
public final class ResourceMonitorBatteryTrend {
    public String update(int currentMicroAmps, long chargeCounterMicroAmpHours,
            int capacityPercent) {
        if (currentMicroAmps == Integer.MIN_VALUE
                || chargeCounterMicroAmpHours <= 0L
                || capacityPercent <= 0 || capacityPercent > 100
                || currentMicroAmps == 0) {
            return null;
        }
        double estimatedFullChargeMicroAmpHours = chargeCounterMicroAmpHours
                * 100.0 / capacityPercent;
        double ratePercentPerMinute = currentMicroAmps * 100.0
                / estimatedFullChargeMicroAmpHours / 60.0;
        if (Double.isNaN(ratePercentPerMinute)
                || Double.isInfinite(ratePercentPerMinute)
                || Math.abs(ratePercentPerMinute) < 0.005) {
            return null;
        }
        return formatRate(ratePercentPerMinute);
    }

    private static String formatRate(double ratePercentPerMinute) {
        double absoluteRate = Math.abs(ratePercentPerMinute);
        double roundedTenth = Math.round(absoluteRate * 10.0) / 10.0;
        int decimals = roundedTenth >= 1.0 ? 0 : roundedTenth >= 0.1 ? 1 : 2;
        return String.format(Locale.ROOT, "%+." + decimals + "f%%/min", ratePercentPerMinute);
    }
}