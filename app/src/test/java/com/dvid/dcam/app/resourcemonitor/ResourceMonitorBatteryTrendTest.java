package com.dvid.dcam.app.resourcemonitor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

final class ResourceMonitorBatteryTrendTest {
    @Test void dischargeRateAppearsFromCurrentSample() {
        ResourceMonitorBatteryTrend trend = new ResourceMonitorBatteryTrend();

        assertEquals("-0.2%/min", trend.update(-375_000, 2_500_000L, 80));
    }

    @Test void chargingRateKeepsPositiveSign() {
        ResourceMonitorBatteryTrend trend = new ResourceMonitorBatteryTrend();

        assertEquals("+0.1%/min", trend.update(187_500, 2_500_000L, 80));
    }

    @Test void unsupportedBatteryPropertiesStayHidden() {
        ResourceMonitorBatteryTrend trend = new ResourceMonitorBatteryTrend();

        assertNull(trend.update(Integer.MIN_VALUE, 2_500_000L, 80));
        assertNull(trend.update(-375_000, Long.MIN_VALUE, 80));
        assertNull(trend.update(-375_000, 2_500_000L, 0));
        assertNull(trend.update(0, 2_500_000L, 80));
    }
}