package com.dvid.dcam.app.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import androidx.arch.core.executor.ArchTaskExecutor;
import androidx.arch.core.executor.TaskExecutor;
import com.dvid.dcam.core.device.domain.DeviceInfo;
import com.dvid.dcam.feature.device.application.port.DeviceRepository;
import com.dvid.dcam.feature.device.application.usecase.RefreshDeviceStatusUseCase;
import com.dvid.dcam.feature.device.domain.DeviceStatus;
import com.dvid.dcam.feature.media.application.port.MediaRepository;
import com.dvid.dcam.feature.media.application.usecase.BrowseMediaUseCase;
import com.dvid.dcam.feature.media.domain.MediaEntry;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

final class MainViewModelMediaListingTest {
    @BeforeEach void runLiveDataSynchronously() {
        ArchTaskExecutor.getInstance().setDelegate(new TaskExecutor() {
            @Override public void executeOnDiskIO(Runnable runnable) { runnable.run(); }
            @Override public void postToMainThread(Runnable runnable) { runnable.run(); }
            @Override public boolean isMainThread() { return true; }
        });
    }

    @AfterEach void restoreLiveDataExecutor() {
        ArchTaskExecutor.getInstance().setDelegate(null);
    }

    @Test void rootListingPrewarmsStorageCounts() throws Exception {
        CountDownLatch prewarmStarted = new CountDownLatch(1);
        MediaRepository repository = new MediaRepository() {
            @Override public List<MediaEntry> list(String relativePath) {
                if ("Internal".equals(relativePath)) prewarmStarted.countDown();
                return Collections.emptyList();
            }

            @Override public List<MediaEntry> listWithoutCounts(String relativePath) {
                return List.of(new MediaEntry(
                        "Internal", "Internal", true, 0L, 0L, null, 0));
            }
        };
        MainViewModel viewModel = viewModel(repository);

        try {
            viewModel.openMediaFolder("");

            assertTrue(prewarmStarted.await(2, TimeUnit.SECONDS));
            assertFalse(viewModel.state().getValue().getMediaBrowser().isLoading());
            assertEquals("", viewModel.state().getValue().getMediaBrowser().getRelativePath());
        } finally {
            viewModel.onCleared();
        }
    }
    @Test void warmFolderCountsSkipExactSecondQuery() throws Exception {
        AtomicInteger exactCalls = new AtomicInteger();
        MediaRepository repository = new MediaRepository() {
            @Override public List<MediaEntry> list(String relativePath) {
                exactCalls.incrementAndGet();
                return Collections.emptyList();
            }

            @Override public List<MediaEntry> listWithoutCounts(String relativePath) {
                return List.of(folder(relativePath, 7));
            }
        };
        MainViewModel viewModel = viewModel(repository);

        try {
            viewModel.openMediaFolder("Internal");

            assertEquals(7, awaitCount(viewModel));
            assertEquals(0, exactCalls.get());
        } finally {
            viewModel.onCleared();
        }
    }
    @Test void listsFoldersBeforeExactCountsComplete() throws Exception {
        CountDownLatch countStarted = new CountDownLatch(1);
        CountDownLatch releaseCount = new CountDownLatch(1);
        MediaRepository repository = new MediaRepository() {
            @Override public List<MediaEntry> list(String relativePath) throws Exception {
                countStarted.countDown();
                releaseCount.await();
                return List.of(folder(relativePath, 7));
            }

            @Override public List<MediaEntry> listWithoutCounts(String relativePath) {
                return List.of(folder(relativePath, -1));
            }
        };
        MainViewModel viewModel = viewModel(repository);

        try {
            viewModel.openMediaFolder("Internal");
            assertTrue(countStarted.await(2, TimeUnit.SECONDS));

            MediaEntry fastEntry = viewModel.state().getValue()
                    .getMediaBrowser().getEntries().get(0);
            assertFalse(viewModel.state().getValue().getMediaBrowser().isLoading());
            assertFalse(fastEntry.hasChildFileCount());

            releaseCount.countDown();
            assertEquals(7, awaitCount(viewModel));
        } finally {
            releaseCount.countDown();
            viewModel.onCleared();
        }
    }

    @Test void newFolderCancelsObsoleteCountBeforeListing() throws Exception {
        CountDownLatch firstCountStarted = new CountDownLatch(1);
        CountDownLatch firstCountInterrupted = new CountDownLatch(1);
        CountDownLatch secondListStarted = new CountDownLatch(1);
        CountDownLatch neverRelease = new CountDownLatch(1);
        MediaRepository repository = new MediaRepository() {
            @Override public List<MediaEntry> list(String relativePath) throws Exception {
                firstCountStarted.countDown();
                try {
                    neverRelease.await();
                } catch (InterruptedException interrupted) {
                    firstCountInterrupted.countDown();
                    throw interrupted;
                }
                return Collections.emptyList();
            }

            @Override public List<MediaEntry> listWithoutCounts(String relativePath) {
                if ("Internal/Video".equals(relativePath)) {
                    secondListStarted.countDown();
                    return Collections.emptyList();
                }
                return List.of(folder(relativePath, -1));
            }
        };
        MainViewModel viewModel = viewModel(repository);

        try {
            viewModel.openMediaFolder("Internal");
            assertTrue(firstCountStarted.await(2, TimeUnit.SECONDS));
            viewModel.openMediaFolder("Internal/Video");

            assertTrue(firstCountInterrupted.await(2, TimeUnit.SECONDS));
            assertTrue(secondListStarted.await(2, TimeUnit.SECONDS));
            assertEquals("Internal/Video",
                    viewModel.state().getValue().getMediaBrowser().getRelativePath());
        } finally {
            viewModel.onCleared();
        }
    }

    private static int awaitCount(MainViewModel viewModel) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (System.nanoTime() < deadline) {
            List<MediaEntry> entries = viewModel.state().getValue().getMediaBrowser().getEntries();
            if (!entries.isEmpty() && entries.get(0).hasChildFileCount()) {
                return entries.get(0).getChildFileCount();
            }
            Thread.sleep(10L);
        }
        return -1;
    }

    private static MediaEntry folder(String path, int count) {
        return new MediaEntry("Video", path + "/Video", true, 0L, 0L, null, count);
    }

    private static MainViewModel viewModel(MediaRepository repository) {
        return new MainViewModel(
                DeviceStatus.unknown(),
                new RefreshDeviceStatusUseCase(new DeviceRepository() {
                    @Override public DeviceInfo readInfo() { return null; }
                    @Override public DeviceStatus readStatus() { return DeviceStatus.unknown(); }
                }),
                new BrowseMediaUseCase(repository),
                () -> true);
    }
}