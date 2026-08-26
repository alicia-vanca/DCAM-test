package com.dvid.dcam.app.resourcemonitor;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityManager.RunningAppProcessInfo;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.BatteryManager;
import android.os.CpuUsageInfo;
import android.os.Debug;
import android.os.HardwarePropertiesManager;
import android.os.Process;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
import androidx.core.content.ContextCompat;
import com.dvid.dcam.core.logging.application.port.Logger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;

/** Foreground-only controller for Resource Monitor sampling and overlay rendering. */
public final class ResourceMonitorController implements AutoCloseable {
    private static final long SAMPLE_INTERVAL_MS = 1_000L;
    private static final long PROCESS_RESPONSE_TIMEOUT_MS = 750L;
    private static final long PSS_INTERVAL_MS = 4_000L;
    private static final long BATTERY_INTERVAL_MS = 5_000L;
    private static final int DIAGNOSTIC_REQUEST_LIMIT = 5;
    private static final String ACTION_LABEL = ". Action: ";

    private final Activity activity;
    private final Context applicationContext;
    private final FrameLayout root;
    private final BooleanSupplier deviceOwner;
    private final Logger logger;
    private final ActivityManager activityManager;
    private final BatteryManager batteryManager;
    private final HardwarePropertiesManager hardwareProperties;
    private final TextView overlay;
    private final Object responseLock = new Object();
    private final Map<Integer, ProcessSample> processResponses = new HashMap<>();
    private final int logicalCoreCount;
    private final ResourceMonitorBatteryTrend batteryTrend = new ResourceMonitorBatteryTrend();
    private final BroadcastReceiver processResponseReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            receiveProcessSample(intent);
        }

        private void receiveProcessSample(Intent intent) {
            if (diagnosticRequestCount <= DIAGNOSTIC_REQUEST_LIMIT) {
                logger.info("Resource Monitor response receiver callback in PID " + Process.myPid()
                        + ACTION_LABEL + intent.getAction() + ". Request: "
                        + intent.getStringExtra(ResourceMonitorProcessReporter.EXTRA_REQUEST_ID)
                        + ". Response PID: "
                        + intent.getIntExtra(ResourceMonitorProcessReporter.EXTRA_PROCESS_PID, -1)
                        + ". Response UID: "
                        + intent.getIntExtra(ResourceMonitorProcessReporter.EXTRA_PROCESS_UID, -1)
                        + ".");
            }
            if (!ResourceMonitorProcessReporter.responseAction(applicationContext)
                    .equals(intent.getAction())) return;
            String requestId = intent.getStringExtra(
                    ResourceMonitorProcessReporter.EXTRA_REQUEST_ID);
            int uid = intent.getIntExtra(ResourceMonitorProcessReporter.EXTRA_PROCESS_UID, -1);
            int pid = intent.getIntExtra(ResourceMonitorProcessReporter.EXTRA_PROCESS_PID, -1);
            if (uid != Process.myUid() || pid <= 0) return;
            String processName = intent.getStringExtra(
                    ResourceMonitorProcessReporter.EXTRA_PROCESS_NAME);
            if (processName == null || processName.trim().isEmpty()) {
                processName = applicationContext.getPackageName();
            }
            ProcessSample sample = new ProcessSample(pid, processName,
                    intent.getLongExtra(ResourceMonitorProcessReporter.EXTRA_CPU_TIME_MS, -1L),
                    intent.getLongExtra(ResourceMonitorProcessReporter.EXTRA_HEAP_USED_BYTES, -1L),
                    intent.getLongExtra(ResourceMonitorProcessReporter.EXTRA_HEAP_LIMIT_BYTES, -1L),
                    intent.getLongExtra(ResourceMonitorProcessReporter.EXTRA_PSS_BYTES, -1L));
            boolean accepted;
            synchronized (responseLock) {
                accepted = Objects.equals(activeRequestId, requestId);
                if (accepted) {
                    processResponses.put(pid, sample);
                    responseLock.notifyAll();
                }
            }
            if (diagnosticRequestCount <= DIAGNOSTIC_REQUEST_LIMIT) {
                logger.info("Resource Monitor " + (accepted ? "accepted" : "ignored")
                        + " process sample response " + requestId + " from " + processName
                        + " PID " + pid + ".");
            }
        }
    };

    private ScheduledExecutorService executor;
    private boolean enabled;
    private boolean visible;
    private boolean receiverRegistered;
    private volatile boolean active;
    private final AtomicLong generation = new AtomicLong();
    private long requestSequence;
    private int diagnosticRequestCount;
    private String activeRequestId;
    private long previousProcessSampleAtMs;
    private Map<Integer, ProcessSample> previousProcessSamples = Collections.emptyMap();
    private CpuUsageInfo[] previousDeviceCpu;
    private Map<Integer, Long> latestPssBytes = Collections.emptyMap();
    private long latestPssAtMs;
    private BatterySnapshot latestBattery = BatterySnapshot.unavailable();
    private long latestBatteryAtMs;
    private String mode = "LITE";
    private boolean deviceCpuCapabilityLogged;

    public ResourceMonitorController(Activity activity, FrameLayout root,
            BooleanSupplier deviceOwner, Logger logger, boolean enabled) {
        this.activity = Objects.requireNonNull(activity, "activity");
        applicationContext = activity.getApplicationContext();
        this.root = Objects.requireNonNull(root, "root");
        this.deviceOwner = Objects.requireNonNull(deviceOwner, "deviceOwner");
        this.logger = Objects.requireNonNull(logger, "logger");
        activityManager = activity.getSystemService(ActivityManager.class);
        batteryManager = activity.getSystemService(BatteryManager.class);
        hardwareProperties = activity.getSystemService(HardwarePropertiesManager.class);
        logicalCoreCount = Math.max(1, Runtime.getRuntime().availableProcessors());
        overlay = createOverlay(activity);
        root.addView(overlay, overlayLayoutParams());
        this.enabled = enabled;
    }

    public void onStart() {
        visible = true;
        refreshActiveState();
    }

    public void onStop() {
        visible = false;
        refreshActiveState();
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        refreshActiveState();
    }

    private void refreshActiveState() {
        boolean shouldRun = enabled && visible;
        if (shouldRun == active) return;
        if (shouldRun) start();
        else stop();
    }

    private void start() {
        active = true;
        long session = generation.incrementAndGet();
        resetSessionState();
        diagnosticRequestCount = 0;
        registerProcessReceiver();
        overlay.setText("");
        overlay.setVisibility(View.GONE);
        executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "dcam-resource-monitor");
            thread.setDaemon(true);
            return thread;
        });
        executor.scheduleWithFixedDelay(() -> sample(session), 0L,
                SAMPLE_INTERVAL_MS, TimeUnit.MILLISECONDS);
        logger.info("Resource Monitor started while the app is visible.");
    }

    private void stop() {
        if (!active) {
            overlay.setVisibility(View.GONE);
            return;
        }
        active = false;
        generation.incrementAndGet();
        synchronized (responseLock) {
            activeRequestId = null;
            processResponses.clear();
            responseLock.notifyAll();
        }
        ScheduledExecutorService runningExecutor = executor;
        executor = null;
        if (runningExecutor != null) runningExecutor.shutdownNow();
        unregisterProcessReceiver();
        resetSessionState();
        overlay.setText("");
        overlay.setVisibility(View.GONE);
        logger.info("Resource Monitor stopped; periodic resource sampling is inactive.");
    }

    private void sample(long session) {
        if (!isActive(session)) return;
        long nowMs = SystemClock.elapsedRealtime();
        boolean samplePss = nowMs - latestPssAtMs >= PSS_INTERVAL_MS;
        Map<Integer, RunningProcess> runningProcesses = runningProcesses();
        Map<Integer, ProcessSample> processSamples = collectProcessSamples(
                session, runningProcesses, samplePss);
        runningProcesses = mergeRunningProcesses(runningProcesses, processSamples);
        if (!isActive(session)) return;

        ProcessCpuResult appCpu = appCpu(nowMs, processSamples);
        DeviceCpuResult deviceCpu = deviceCpu();
        DeviceMemory deviceMemory = deviceMemory();
        if (samplePss) {
            latestPssBytes = pssFromSamples(processSamples);
            latestPssAtMs = nowMs;
        }
        if (nowMs - latestBatteryAtMs >= BATTERY_INTERVAL_MS) {
            latestBattery = battery();
            latestBatteryAtMs = nowMs;
        }
        String nextMode = deviceCpu.supported ? "FULL" : "LITE";
        logModeIfChanged(nextMode, deviceCpu);
        mode = nextMode;

        MonitorSnapshot snapshot = new MonitorSnapshot(deviceCpu.percent,
                appCpu, logicalCoreCount, deviceMemory, processSamples,
                runningProcesses, latestPssBytes, latestBattery);
        activity.runOnUiThread(() -> render(session, snapshot));
    }

    private Map<Integer, ProcessSample> collectProcessSamples(long session,
            Map<Integer, RunningProcess> runningProcesses, boolean samplePss) {
        String requestId = session + ":" + (++requestSequence);
        synchronized (responseLock) {
            activeRequestId = requestId;
            processResponses.clear();
            ProcessSample self = selfProcessSample(samplePss);
            processResponses.put(self.pid, self);
        }
        boolean diagnostic = ++diagnosticRequestCount <= DIAGNOSTIC_REQUEST_LIMIT;
        String requestAction = ResourceMonitorProcessReporter.requestAction(applicationContext);
        if (diagnostic) {
            logger.info("Resource Monitor preparing process sample request " + requestId
                    + " from PID " + Process.myPid() + ACTION_LABEL + requestAction
                    + ". Package: " + applicationContext.getPackageName()
                    + ". Response receiver registered: " + receiverRegistered
                    + ". Discovered app PIDs: " + runningProcesses.keySet() + ".");
        }
        try {
            Intent request = new Intent(requestAction)
                    .setPackage(applicationContext.getPackageName())
                    .putExtra(ResourceMonitorProcessReporter.EXTRA_REQUEST_ID, requestId)
                    .putExtra(ResourceMonitorProcessReporter.EXTRA_INCLUDE_PSS, samplePss);
            PendingIntent.getBroadcast(applicationContext, requestId.hashCode(), request,
                    PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE).send();
            if (diagnostic) {
                logger.info("Resource Monitor submitted process sample request " + requestId
                        + " from PID " + Process.myPid() + ".");
            }
        } catch (PendingIntent.CanceledException | RuntimeException error) {
            logger.warn("Resource Monitor could not send process sample request "
                    + requestId + ".", error);
        }
        long requestSentAtMs = SystemClock.elapsedRealtime();
        long responseDeadlineMs = requestSentAtMs + PROCESS_RESPONSE_TIMEOUT_MS;
        synchronized (responseLock) {
            while (requestId.equals(activeRequestId) && isActive(session)) {
                long nowMs = SystemClock.elapsedRealtime();
                boolean allDiscoveredProcessesResponded = processResponses.keySet()
                        .containsAll(runningProcesses.keySet());
                boolean foundUndiscoveredProcess = processResponses.size()
                        > runningProcesses.size();
                long remainingMs = responseDeadlineMs - nowMs;
                boolean processCollectionComplete = allDiscoveredProcessesResponded
                        && (runningProcesses.size() > 1 || foundUndiscoveredProcess);
                if (processCollectionComplete || remainingMs <= 0L) break;
                try {
                    responseLock.wait(remainingMs);
                } catch (InterruptedException error) {
                    activeRequestId = null;
                    Thread.currentThread().interrupt();
                    return Collections.emptyMap();
                }
            }
            if (!requestId.equals(activeRequestId)) return Collections.emptyMap();
            activeRequestId = null;
            Map<Integer, ProcessSample> result = Collections.unmodifiableMap(
                    new HashMap<>(processResponses));
            if (diagnostic) {
                List<Integer> missingPids = new ArrayList<>(runningProcesses.keySet());
                missingPids.removeAll(result.keySet());
                logger.info("Resource Monitor completed process sample request " + requestId
                        + " after " + (SystemClock.elapsedRealtime() - requestSentAtMs)
                        + " ms. Received PIDs: " + result.keySet()
                        + ". Missing PIDs: " + missingPids + ".");
            }
            return result;
        }
    }

    private ProcessSample selfProcessSample(boolean samplePss) {
        Runtime runtime = Runtime.getRuntime();
        return new ProcessSample(Process.myPid(),
                ResourceMonitorProcessReporter.processName(applicationContext),
                Process.getElapsedCpuTime(),
                Math.max(0L, runtime.totalMemory() - runtime.freeMemory()),
                runtime.maxMemory(), samplePss ? currentPssBytes() : -1L);
    }

    private static long currentPssBytes() {
        try {
            long pssKilobytes = Debug.getPss();
            return pssKilobytes < 0L ? -1L : pssKilobytes * 1024L;
        } catch (RuntimeException error) {
            return -1L;
        }
    }

    private ProcessCpuResult appCpu(long nowMs, Map<Integer, ProcessSample> current) {
        long elapsedMs = previousProcessSampleAtMs <= 0L
                ? -1L : nowMs - previousProcessSampleAtMs;
        ProcessCpuResult result = calculateAppCpu(elapsedMs, previousProcessSamples,
                current, logicalCoreCount);
        previousProcessSampleAtMs = nowMs;
        previousProcessSamples = current;
        return result;
    }

    static ProcessCpuResult calculateAppCpu(long elapsedMs,
            Map<Integer, ProcessSample> previousSamples,
            Map<Integer, ProcessSample> currentSamples, int logicalCoreCount) {
        long cpuDeltaMs = 0L;
        int matchedProcessCount = 0;
        if (elapsedMs > 0L) {
            for (ProcessSample next : currentSamples.values()) {
                ProcessSample previous = previousSamples.get(next.pid);
                if (previous == null || next.cpuTimeMs < 0L || previous.cpuTimeMs < 0L
                        || next.cpuTimeMs < previous.cpuTimeMs) {
                    continue;
                }
                cpuDeltaMs += next.cpuTimeMs - previous.cpuTimeMs;
                matchedProcessCount++;
            }
        }
        double percent = matchedProcessCount == 0
                ? -1.0 : ResourceMonitorMath.cpuPercent(
                        cpuDeltaMs, elapsedMs, logicalCoreCount);
        return new ProcessCpuResult(percent);
    }

    @SuppressWarnings("java:S6885") // Math.clamp is unavailable on the API-26 runtime target.
    private DeviceCpuResult deviceCpu() {
        if (!deviceOwner.getAsBoolean()) {
            previousDeviceCpu = null;
            return DeviceCpuResult.unsupported("Device CPU telemetry is unavailable");
        }
        if (hardwareProperties == null) {
            previousDeviceCpu = null;
            return DeviceCpuResult.unsupported("Device CPU telemetry is unavailable");
        }
        CpuUsageInfo[] current;
        try {
            current = hardwareProperties.getCpuUsages();
        } catch (RuntimeException error) {
            previousDeviceCpu = null;
            return DeviceCpuResult.unsupported("Device CPU telemetry request failed");
        }
        if (current == null || current.length == 0) {
            previousDeviceCpu = null;
            return DeviceCpuResult.unsupported("Device CPU telemetry is unsupported");
        }
        CpuUsageInfo[] previous = previousDeviceCpu;
        previousDeviceCpu = Arrays.copyOf(current, current.length);
        if (previous == null) return new DeviceCpuResult(true, -1.0, null);
        long activeDelta = 0L;
        long totalDelta = 0L;
        int count = Math.min(previous.length, current.length);
        for (int index = 0; index < count; index++) {
            CpuUsageInfo before = previous[index];
            CpuUsageInfo after = current[index];
            if (before != null && after != null) {
                long nextActive = after.getActive() - before.getActive();
                long nextTotal = after.getTotal() - before.getTotal();
                if (nextActive >= 0L && nextTotal > 0L) {
                    activeDelta += nextActive;
                    totalDelta += nextTotal;
                }
            }
        }
        double percent = totalDelta <= 0L
                ? -1.0 : Math.max(0.0, Math.min(100.0,
                        100.0 * activeDelta / totalDelta));
        return new DeviceCpuResult(true, percent, null);
    }
    private DeviceMemory deviceMemory() {
        if (activityManager == null) return DeviceMemory.unavailable();
        ActivityManager.MemoryInfo info = new ActivityManager.MemoryInfo();
        try {
            activityManager.getMemoryInfo(info);
            return new DeviceMemory(info.totalMem,
                    Math.max(0L, info.totalMem - info.availMem));
        } catch (RuntimeException error) {
            return DeviceMemory.unavailable();
        }
    }

    static Map<Integer, Long> pssFromSamples(Map<Integer, ProcessSample> samples) {
        Map<Integer, Long> values = new HashMap<>();
        for (ProcessSample sample : samples.values()) {
            if (sample.pssBytes >= 0L) values.put(sample.pid, sample.pssBytes);
        }
        return Collections.unmodifiableMap(values);
    }


    private BatterySnapshot battery() {
        if (batteryManager == null) return BatterySnapshot.unavailable();
        try {
            int capacity = supportedPercent(batteryManager.getIntProperty(
                    BatteryManager.BATTERY_PROPERTY_CAPACITY));
            int currentMicroAmps = supportedInt(batteryManager.getIntProperty(
                    BatteryManager.BATTERY_PROPERTY_CURRENT_NOW));
            if (currentMicroAmps == Integer.MIN_VALUE) {
                currentMicroAmps = supportedInt(batteryManager.getIntProperty(
                        BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE));
            }
            long chargeCounterMicroAmpHours = batteryManager.getLongProperty(
                    BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER);
            return new BatterySnapshot(capacity, currentMicroAmps,
                    batteryTrend.update(currentMicroAmps,
                            chargeCounterMicroAmpHours, capacity));
        } catch (RuntimeException error) {
            return BatterySnapshot.unavailable();
        }
    }

    static Map<Integer, RunningProcess> mergeRunningProcesses(
            Map<Integer, RunningProcess> discovered, Map<Integer, ProcessSample> samples) {
        Map<Integer, RunningProcess> result = new LinkedHashMap<>(discovered);
        for (ProcessSample sample : samples.values()) {
            if (sample.pid <= 0 || sample.name == null || sample.name.trim().isEmpty()) continue;
            result.put(sample.pid, new RunningProcess(sample.name, sample.pid));
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(result));
    }
    private Map<Integer, RunningProcess> runningProcesses() {
        if (activityManager == null) return Collections.emptyMap();
        List<RunningAppProcessInfo> processes;
        try {
            processes = activityManager.getRunningAppProcesses();
        } catch (RuntimeException error) {
            return Collections.emptyMap();
        }
        if (processes == null) return Collections.emptyMap();
        Map<Integer, RunningProcess> result = new LinkedHashMap<>();
        for (RunningAppProcessInfo process : processes) {
            if (process.uid != Process.myUid() || process.pid <= 0) continue;
            String name = process.processName == null
                    ? applicationContext.getPackageName() : process.processName;
            result.put(process.pid, new RunningProcess(name, process.pid));
        }
        result.putIfAbsent(Process.myPid(),
                new RunningProcess(applicationContext.getPackageName(), Process.myPid()));
        return Collections.unmodifiableMap(new LinkedHashMap<>(result));
    }

    private void render(long session, MonitorSnapshot snapshot) {
        if (!isActive(session)) return;
        updateOverlayTopMargin();
        String display = snapshot.display(applicationContext.getPackageName());
        overlay.setText(display);
        overlay.setVisibility(display.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void logModeIfChanged(String nextMode, DeviceCpuResult deviceCpu) {
        if (nextMode.equals(mode) && deviceCpuCapabilityLogged) return;
        if (nextMode.equals("FULL")) {
            logger.info("Resource Monitor selected Full mode; Device CPU telemetry is available.");
        } else if (!deviceCpuCapabilityLogged) {
            logger.info("Resource Monitor selected Lite mode; " + deviceCpu.unavailableReason + ".");
        }
        deviceCpuCapabilityLogged = true;
    }

    private void registerProcessReceiver() {
        if (receiverRegistered) return;
        String responseAction = ResourceMonitorProcessReporter.responseAction(applicationContext);
        logger.info("Resource Monitor registering process response receiver in PID "
                + Process.myPid() + ACTION_LABEL + responseAction + ".");
        ContextCompat.registerReceiver(applicationContext, processResponseReceiver,
                new IntentFilter(responseAction), ContextCompat.RECEIVER_NOT_EXPORTED);
        receiverRegistered = true;
        logger.info("Resource Monitor registered process response receiver in PID "
                + Process.myPid() + ".");
    }

    private void unregisterProcessReceiver() {
        if (!receiverRegistered) return;
        applicationContext.unregisterReceiver(processResponseReceiver);
        receiverRegistered = false;
    }

    private boolean isActive(long session) {
        return active && generation.get() == session && !Thread.currentThread().isInterrupted();
    }

    private void resetSessionState() {
        previousProcessSampleAtMs = 0L;
        previousProcessSamples = Collections.emptyMap();
        previousDeviceCpu = null;
        latestPssBytes = Collections.emptyMap();
        latestPssAtMs = 0L;
        latestBattery = BatterySnapshot.unavailable();
        latestBatteryAtMs = 0L;
        mode = "LITE";
        deviceCpuCapabilityLogged = false;
    }

    private TextView createOverlay(Context context) {
        TextView view = new TextView(context);
        view.setBackgroundColor(Color.argb(190, 0, 0, 0));
        view.setTextColor(Color.WHITE);
        view.setTypeface(Typeface.MONOSPACE);
        view.setTextSize(10f);
        view.setPadding(dp(6), dp(4), dp(6), dp(4));
        view.setClickable(false);
        view.setLongClickable(false);
        view.setFocusable(false);
        view.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        view.setElevation(dp(12));
        view.setVisibility(View.GONE);
        return view;
    }

    private FrameLayout.LayoutParams overlayLayoutParams() {
        FrameLayout.LayoutParams layout = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.START);
        layout.leftMargin = dp(4);
        layout.topMargin = deviceOwner.getAsBoolean() ? dp(28) : dp(4);
        return layout;
    }

    private void updateOverlayTopMargin() {
        FrameLayout.LayoutParams layout = (FrameLayout.LayoutParams) overlay.getLayoutParams();
        int topMargin = deviceOwner.getAsBoolean() ? dp(28) : dp(4);
        if (layout.topMargin == topMargin) return;
        layout.topMargin = topMargin;
        overlay.setLayoutParams(layout);
    }

    private int dp(int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    @Override public void close() {
        visible = false;
        stop();
        if (overlay.getParent() == root) root.removeView(overlay);
    }

    private static int supportedPercent(int value) {
        return value >= 0 && value <= 100 ? value : -1;
    }

    private static int supportedInt(int value) {
        return value == Integer.MIN_VALUE ? Integer.MIN_VALUE : value;
    }

    record ProcessSample(int pid, String name, long cpuTimeMs, long heapUsedBytes,
            long heapLimitBytes, long pssBytes) {
        ProcessSample(int pid, String name, long cpuTimeMs, long heapUsedBytes,
                long heapLimitBytes) {
            this(pid, name, cpuTimeMs, heapUsedBytes, heapLimitBytes, -1L);
        }
    }

    record RunningProcess(String name, int pid) {
    }

    record ProcessCpuResult(double percent) {
    }

    record DeviceCpuResult(boolean supported, double percent, String unavailableReason) {
        private static DeviceCpuResult unsupported(String reason) {
            return new DeviceCpuResult(false, -1.0, reason);
        }
    }

    record DeviceMemory(long totalBytes, long usedBytes) {
        private static DeviceMemory unavailable() {
            return new DeviceMemory(-1L, -1L);
        }
    }

    record BatterySnapshot(int capacityPercent, int currentMicroAmps, String trend) {
        private static BatterySnapshot unavailable() {
            return new BatterySnapshot(-1, Integer.MIN_VALUE, null);
        }

        String display() {
            List<String> values = new ArrayList<>();
            if (capacityPercent >= 0) values.add(capacityPercent + "%");
            if (currentMicroAmps != Integer.MIN_VALUE) {
                values.add(String.format(Locale.ROOT, "%+.0f mA", currentMicroAmps / 1000.0));
            }
            if (trend != null && !trend.trim().isEmpty()) values.add(trend);
            return values.isEmpty() ? "" : String.join(" | ", values);
        }
    }

    record MonitorSnapshot(double deviceCpuPercent, ProcessCpuResult appCpu,
            int logicalCoreCount, DeviceMemory deviceMemory,
            Map<Integer, ProcessSample> processSamples,
            Map<Integer, RunningProcess> runningProcesses,
            Map<Integer, Long> pssBytes, BatterySnapshot battery) {
        String display(String packageName) {
            StringBuilder text = new StringBuilder();
            boolean hasDisplaySection = false;

            List<String> cpuLines = new ArrayList<>();
            addCpuLine(cpuLines, "Device", deviceCpuPercent, logicalCoreCount);
            addCpuLine(cpuLines, "DCAM", appCpu.percent, logicalCoreCount);
            hasDisplaySection = appendSection(text, "---- CPU ----", cpuLines,
                    hasDisplaySection);

            List<RunningProcess> running = new ArrayList<>(runningProcesses.values());
            running.sort(Comparator.comparing(RunningProcess::name)
                    .thenComparingInt(RunningProcess::pid));
            hasDisplaySection = appendSection(text, "---- RAM ----",
                    ramLines(packageName, deviceMemory, running, pssBytes), hasDisplaySection);

            List<String> heapLines = new ArrayList<>();
            for (RunningProcess process : running) {
                ProcessSample sample = processSamples.get(process.pid);
                if (sample == null || sample.heapLimitBytes <= 0L
                        || sample.heapUsedBytes < 0L) {
                    continue;
                }
                heapLines.add("• " + shortProcessName(packageName, process.name)
                        + ": Allowed " + ResourceMonitorMath.formatBytes(sample.heapLimitBytes)
                        + " | Used " + ResourceMonitorMath.formatBytes(sample.heapUsedBytes)
                        + " (" + ResourceMonitorMath.formatRatioPercent(
                                sample.heapUsedBytes, sample.heapLimitBytes) + ")");
            }
            hasDisplaySection = appendSection(text, "---- JAVA HEAP ----", heapLines,
                    hasDisplaySection);

            String batteryLine = battery.display();
            if (!batteryLine.isEmpty()) {
                appendSection(text, "---- BATTERY ----", Collections.singletonList(batteryLine),
                        hasDisplaySection);
            }
            return text.toString();
        }

        private static List<String> ramLines(String packageName, DeviceMemory deviceMemory,
                List<RunningProcess> running, Map<Integer, Long> pssBytes) {
            List<String> ramLines = new ArrayList<>();
            if (deviceMemory.totalBytes > 0L && deviceMemory.usedBytes >= 0L) {
                ramLines.add("Device: Total "
                        + ResourceMonitorMath.formatBytes(deviceMemory.totalBytes)
                        + " | Used " + ResourceMonitorMath.formatBytes(deviceMemory.usedBytes)
                        + " (" + ResourceMonitorMath.formatRatioPercent(
                                deviceMemory.usedBytes, deviceMemory.totalBytes) + ")");
            }
            List<String> processRamLines = new ArrayList<>();
            long appRamUsedBytes = 0L;
            boolean completeAppRam = !running.isEmpty();
            for (RunningProcess process : running) {
                Long processPssBytes = pssBytes.get(process.pid);
                if (processPssBytes == null || processPssBytes < 0L) {
                    completeAppRam = false;
                    break;
                }
                long displayedPssBytes = roundedMegabytes(processPssBytes);
                appRamUsedBytes += displayedPssBytes;
                String processLine = "• " + shortProcessName(packageName, process.name)
                        + ": Used " + ResourceMonitorMath.formatBytes(displayedPssBytes);
                String processPercent = ResourceMonitorMath.formatRatioPercent(
                        displayedPssBytes, deviceMemory.totalBytes);
                if (!processPercent.isEmpty()) processLine += " (" + processPercent + ")";
                processRamLines.add(processLine);
            }
            if (completeAppRam) {
                String appLine = "DCAM: Used "
                        + ResourceMonitorMath.formatBytes(appRamUsedBytes);
                String appPercent = ResourceMonitorMath.formatRatioPercent(
                        appRamUsedBytes, deviceMemory.totalBytes);
                if (!appPercent.isEmpty()) appLine += " (" + appPercent + ")";
                ramLines.add(appLine);
                ramLines.addAll(processRamLines);
            }
            return ramLines;
        }

        private static void addCpuLine(List<String> lines, String label,
                double percent, int logicalCoreCount) {
            if (percent < 0.0) return;
            lines.add(label + ": Used "
                    + ResourceMonitorMath.formatCpuUsed(percent, logicalCoreCount)
                    + " (" + ResourceMonitorMath.formatPercent(percent) + ")");
        }

        private static long roundedMegabytes(long bytes) {
            return Math.round(bytes / (1024.0 * 1024.0)) * 1024L * 1024L;
        }

        private static boolean appendSection(StringBuilder text, String title, List<String> lines,
                boolean hasPreviousSection) {
            if (lines.isEmpty()) return hasPreviousSection;
            if (hasPreviousSection) text.append('\n');
            text.append(title);
            for (String line : lines) text.append('\n').append(line);
            return true;
        }

        private static String shortProcessName(String packageName, String processName) {
            if (processName.equals(packageName)) return "main";
            if (processName.startsWith(packageName + ":")) {
                return processName.substring(packageName.length() + 1);
            }
            return processName;
        }
    }
}
