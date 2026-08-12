package com.dvid.dcam.platform.storage;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import com.dvid.dcam.platform.logging.app.AppLogger;

public final class DcamMd5RetryWorker extends Worker {
    public DcamMd5RetryWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull @Override public Result doWork() {
        DcamStorage storage = DcamStorage.from(getApplicationContext());
        DcamMd5RetryQueue queue = new DcamMd5RetryQueue(
                storage.configsFile().getParentFile().getParentFile());
        try {
            return DcamMd5RetryProcessor.process(queue, AppLogger.get()) ? Result.success() : Result.retry();
        } catch (Exception failure) {
            return Result.retry();
        }
    }
}
