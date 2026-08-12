package com.dvid.dcam.platform.logging.loggly;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.util.Log;
import java.util.IdentityHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Android entry point that runs Room and crash-spool upload inside the :loggly process. */
public final class LogglyUploadJobService extends JobService {
    private static final String TAG = "LogglyUpload";
    private final ExecutorService uploadExecutor = Executors.newSingleThreadExecutor();
    private final RunRegistry activeRuns = new RunRegistry();

    @Override public boolean onStartJob(JobParameters parameters) {
        Log.i(TAG, "launch job onStartJob pid=" + android.os.Process.myPid());
        RunSignal signal = activeRuns.start(parameters);
        uploadExecutor.execute(() -> execute(parameters, signal));
        return true;
    }

    private void execute(JobParameters parameters, RunSignal signal) {
        Long nextRetryAtMillis;
        try {
            Long crashRetryAtMillis = LogglyCrashSpool.uploadPending(
                    getApplicationContext(), signal::isStopped);
            Long roomRetryAtMillis = RoomLogUploader.uploadPending(
                    getApplicationContext(), signal::isStopped);
            nextRetryAtMillis = earliest(crashRetryAtMillis, roomRetryAtMillis);
        } catch (RuntimeException error) {
            Log.e(TAG, "Loggly upload job failed", error);
            nextRetryAtMillis = System.currentTimeMillis() + 10_000L;
        }
        if (!activeRuns.finish(parameters, signal, () -> jobFinished(parameters, false))) return;
        LogglyUploadScheduler.scheduleRetry(getApplicationContext(), nextRetryAtMillis);
    }

    private static Long earliest(Long first, Long second) {
        if (first == null) return second;
        if (second == null) return first;
        return Math.min(first, second);
    }

    @Override public boolean onStopJob(JobParameters parameters) {
        Log.i(TAG, "launch job onStopJob pid=" + android.os.Process.myPid());
        activeRuns.stop(parameters);
        return true;
    }

    @Override public void onDestroy() {
        activeRuns.stopAll();
        uploadExecutor.shutdownNow();
        super.onDestroy();
    }

    static final class RunRegistry {
        private final IdentityHashMap<Object, RunSignal> runs = new IdentityHashMap<>();

        synchronized RunSignal start(Object key) {
            RunSignal signal = new RunSignal();
            RunSignal previous = runs.put(key, signal);
            if (previous != null) previous.stop();
            return signal;
        }

        synchronized void stop(Object key) {
            RunSignal signal = runs.remove(key);
            if (signal != null) signal.stop();
        }

        synchronized boolean finish(Object key, RunSignal signal, Runnable completion) {
            if (runs.get(key) != signal || signal.isStopped()) return false;
            completion.run();
            runs.remove(key);
            return true;
        }

        synchronized void stopAll() {
            for (RunSignal signal : runs.values()) signal.stop();
            runs.clear();
        }
    }

    static final class RunSignal {
        private volatile boolean stopped;

        void stop() { stopped = true; }

        boolean isStopped() { return stopped; }
    }
}
