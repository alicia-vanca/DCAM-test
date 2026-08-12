package com.dvid.dcam.platform.logging.loggly;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class LogglyUploadServiceLifecycleTest {
    @Test
    void foregroundServiceOwnsNormalQueueUpload() throws IOException {
        String service = read("app/src/main/java/com/dvid/dcam/platform/logging/loggly/LogglyUploadService.java");
        assertTrue(service.contains("startForeground("));
        assertTrue(service.contains("RoomLogUploader.uploadPending"));
        assertTrue(service.contains("START_STICKY"));
        assertTrue(service.contains("FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED"));
        assertTrue(service.contains("onTimeout"));
        assertTrue(service.contains("IMPORTANCE_LOW"));
        assertTrue(service.contains("isSystemExemptedEligible"));
        assertTrue(service.contains("isAdminActive"));
        assertTrue(service.contains("networkCallback == null"));
        assertFalse(service.contains("LogglyUploadScheduler.cancel"));
        String timeout = service.substring(service.indexOf("onTimeout"),
                service.indexOf("private int foregroundServiceType"));
        assertTrue(timeout.contains("stopSelf();"));
        assertFalse(timeout.contains("stopSelf(startId)"));
    }

    @Test
    void wakeBeforeWaitIsRemembered() {
        LogglyUploadService.WakeSignal signal = new LogglyUploadService.WakeSignal();
        long generation = signal.generation();
        signal.wake();
        assertTrue(signal.changedSince(generation));
    }

    @Test
    void concurrentJobIdsFinishIndependently() {
        LogglyUploadJobService.RunRegistry runs = new LogglyUploadJobService.RunRegistry();
        Object firstParameters = new Object();
        Object secondParameters = new Object();
        LogglyUploadJobService.RunSignal first = runs.start(firstParameters);
        LogglyUploadJobService.RunSignal second = runs.start(secondParameters);
        runs.stop(firstParameters);
        boolean[] secondFinished = {false};
        assertFalse(runs.finish(firstParameters, first, () -> { }));
        assertTrue(runs.finish(secondParameters, second, () -> secondFinished[0] = true));
        assertTrue(first.isStopped());
        assertTrue(secondFinished[0]);
    }

    @Test
    void mainProcessContinuouslySupervisesLogglyProcess() throws IOException {
        String application = read("app/src/main/java/com/dvid/dcam/app/DcamApplication.java");
        String supervisor = read("app/src/main/java/com/dvid/dcam/platform/logging/loggly/LogglyProcessSupervisor.java");
        String bootstrap = read("app/src/main/java/com/dvid/dcam/platform/logging/loggly/LogglyProcessBootstrapActivity.java");
        String manifest = read("app/src/main/AndroidManifest.xml");
        assertTrue(application.contains("new LogglyProcessSupervisor(this)"));
        assertTrue(application.contains("logglySupervisor.start()"));
        assertTrue(supervisor.contains("Context.BIND_AUTO_CREATE"));
        assertFalse(supervisor.contains("Context.BIND_ABOVE_CLIENT"));
        assertFalse(supervisor.contains("Context.BIND_IMPORTANT"));
        assertTrue(supervisor.contains("onServiceDisconnected"));
        assertTrue(supervisor.contains("onBindingDied"));
        assertTrue(supervisor.contains("current.isBinderAlive()"));
        assertTrue(supervisor.contains("mainHandler.postDelayed(healthCheck"));
        assertTrue(supervisor.contains("LogglyProcessBootstrapActivity.start(activity)"));
        assertTrue(supervisor.contains("LogglyUploadScheduler.scheduleJobNow(application)"));
        assertFalse(supervisor.contains("LogglyHttpClient"));
        assertTrue(bootstrap.contains("activity.startActivity(intent)"));
        assertTrue(bootstrap.contains("LogglyUploadService.start(this)"));
        assertTrue(bootstrap.contains("finish();"));
        String activity = serviceDeclaration(manifest, "LogglyProcessBootstrapActivity");
        assertTrue(activity.contains("android:process=\":loggly\""));
        assertTrue(activity.contains("@android:style/Theme.NoDisplay"));
    }

    @Test
    void schedulerKeepsJobFallback() throws IOException {
        String scheduler = read("app/src/main/java/com/dvid/dcam/platform/logging/loggly/LogglyUploadScheduler.java");
        assertTrue(scheduler.contains("LogglyUploadService.start(context)"));
        assertTrue(scheduler.contains("scheduleJobNow(context)"));
        assertTrue(scheduler.contains("scheduleRetry"));
        assertTrue(scheduler.contains("UPLOAD_JOB_ID = 0xDC04"));
        assertTrue(scheduler.contains("RETRY_JOB_ID = 0xDC05"));
    }

    @Test
    void supervisorStartsAndBindsForegroundService() throws IOException {
        String supervisor = read("app/src/main/java/com/dvid/dcam/platform/logging/loggly/LogglyProcessSupervisor.java");
        assertTrue(supervisor.contains("LogglyUploadService.start(application)"));
        assertTrue(supervisor.contains("application.bindService("));
        assertTrue(supervisor.contains("Context.BIND_AUTO_CREATE"));
    }

    @Test
    void workManagerJobIdsDoNotOverlapLogglyFallback() throws IOException {
        String application = read("app/src/main/java/com/dvid/dcam/app/DcamApplication.java");
        String scheduler = read("app/src/main/java/com/dvid/dcam/platform/logging/loggly/LogglyUploadScheduler.java");
        assertTrue(application.contains("WORK_MANAGER_JOB_ID_MIN = 0xE000"));
        assertTrue(application.contains("WORK_MANAGER_JOB_ID_MAX = 0xEFFF"));
        assertTrue(application.contains("setJobSchedulerJobIdRange"));
        assertTrue(scheduler.contains("UPLOAD_JOB_ID = 0xDC04"));
        String manifest = read("app/src/main/AndroidManifest.xml");
        assertTrue(manifest.contains("androidx.work.WorkManagerInitializer"));
        assertTrue(manifest.contains("tools:node=\"remove\""));
    }

    @Test
    void crashSpoolIsAtomicAndDrainedByExistingUploaders() throws IOException {
        String spool = read("app/src/main/java/com/dvid/dcam/platform/logging/loggly/LogglyCrashSpool.java");
        String job = read("app/src/main/java/com/dvid/dcam/platform/logging/loggly/LogglyUploadJobService.java");
        String service = read("app/src/main/java/com/dvid/dcam/platform/logging/loggly/LogglyUploadService.java");
        assertTrue(spool.contains("getNoBackupFilesDir"));
        assertTrue(spool.contains("output.getFD().sync()"));
        assertTrue(spool.contains("StandardCopyOption.ATOMIC_MOVE"));
        assertTrue(spool.contains("LogglyUploadScheduler.scheduleJobNow(context)"));
        assertTrue(spool.contains("synchronized (UPLOAD_LOCK)"));
        assertFalse(spool.contains("AppDatabase"));
        assertTrue(job.contains("LogglyCrashSpool.uploadPending"));
        assertTrue(service.contains("LogglyCrashSpool.uploadPending"));
    }

    @Test
    void manifestLimitsSystemExemptedToPersistentUploader() throws IOException {
        String manifest = read("app/src/main/AndroidManifest.xml");
        String uploader = serviceDeclaration(manifest, "LogglyUploadService");
        assertTrue(uploader.contains("dataSync|systemExempted"));
        assertFalse(manifest.contains("LogglyCrashRelayService"));
    }

    @Test
    void notificationUsesSteadyStateCopy() throws IOException {
        String strings = read("app/src/main/res/values/strings.xml");
        assertTrue(strings.contains("DCAM is running in the background"));
        assertFalse(strings.contains("delivering queued logs"));
    }

    private static String serviceDeclaration(String manifest, String serviceName) {
        int start = manifest.indexOf("android:name=\".platform.logging.loggly." + serviceName + "\"");
        int end = manifest.indexOf("/>", start);
        return manifest.substring(start, end);
    }

    private static String read(String relativePath) throws IOException {
        Path path = Path.of(relativePath);
        if (!Files.exists(path)) path = Path.of("..").resolve(relativePath);
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}
