package com.dvid.dcam.platform.storage;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.dvid.dcam.core.logging.application.port.Logger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class DcamMediaReservationTest {
    @Test void mediaOutputReservesOneFilenamePerTypePerSecond(@TempDir Path root) throws Exception {
        MutableClock clock = new MutableClock(
                LocalDateTime.of(2026, 8, 4, 10, 0, 0, 250_000_000),
                ZoneId.of("Asia/Ho_Chi_Minh"));
        DcamMediaReservation reservation = new DcamMediaReservation(clock, clock::sleep);
        DcamMediaOutputImpl output = new DcamMediaOutputImpl(
                null, new DcamStorage(root.toFile()), () -> false,
                new NoOpLogger(), reservation);

        DcamMediaFile firstVideo = output.mediaFile(
                DcamFileType.VIDEO, "CAM001", "000001", false);
        DcamMediaFile image = output.mediaFile(
                DcamFileType.IMAGE, "CAM001", "000001", false);
        DcamMediaFile imp = output.mediaFile(
                DcamFileType.IMP, "CAM001", "000001", false);
        DcamMediaFile secondImp = output.mediaFile(
                DcamFileType.IMP, "CAM001", "000001", false);
        DcamMediaFile secondVideo = output.mediaFile(
                DcamFileType.VIDEO, "CAM001", "000001", false);
        DcamMediaFile firstAudio = output.durableAudioMediaFile(
                "CAM001", "000001", false);
        DcamMediaFile secondAudio = output.durableAudioMediaFile(
                "CAM001", "000001", false);

        assertEquals("DCAM_CAM001_000001_20260804_100000.mp4",
                firstVideo.getFileName());
        assertEquals("DCAM_CAM001_000001_20260804_100000.jpg", image.getFileName());
        assertEquals("DCAM_CAM001_000001_20260804_100000_IMP.mp4", imp.getFileName());
        assertEquals("DCAM_CAM001_000001_20260804_100001_IMP.mp4",
                secondImp.getFileName());
        assertEquals("DCAM_CAM001_000001_20260804_100001.mp4",
                secondVideo.getFileName());
        assertEquals("DCAM_CAM001_000001_20260804_100001.m4a",
                firstAudio.getFileName());
        assertEquals("DCAM_CAM001_000001_20260804_100002.m4a",
                secondAudio.getFileName());
        assertEquals(List.of(750L, 1000L), clock.sleeps);
    }

    @Test void recreatedOutputSkipsExactStagingFilename(@TempDir Path root)
            throws Exception {
        ZoneId zone = ZoneId.of("Asia/Ho_Chi_Minh");
        LocalDateTime now = LocalDateTime.of(2026, 8, 4, 11, 30, 0, 250_000_000);
        MutableClock firstClock = new MutableClock(now, zone);
        DcamMediaOutputImpl firstOutput = new DcamMediaOutputImpl(
                null, new DcamStorage(root.toFile()), () -> false, new NoOpLogger(),
                new DcamMediaReservation(firstClock, firstClock::sleep));
        DcamMediaFile existing = firstOutput.mediaFile(
                DcamFileType.VIDEO, "CAM001", "000001", false);
        Files.createDirectories(existing.getFile().toPath().getParent());
        Files.write(existing.getFile().toPath(), new byte[] {1, 2, 3});

        MutableClock recreatedClock = new MutableClock(now, zone);
        DcamMediaOutputImpl recreatedOutput = new DcamMediaOutputImpl(
                null, new DcamStorage(root.toFile()), () -> false, new NoOpLogger(),
                new DcamMediaReservation(recreatedClock, recreatedClock::sleep));
        DcamMediaFile next = recreatedOutput.mediaFile(
                DcamFileType.VIDEO, "CAM001", "000001", false);

        assertEquals("DCAM_CAM001_000001_20260804_113001.mp4",
                next.getFileName());
        assertEquals(List.of(750L), recreatedClock.sleeps);
        assertArrayEquals(new byte[] {1, 2, 3},
                Files.readAllBytes(existing.getFile().toPath()));
    }

    @Test void recreatedOutputSkipsExactFinalFilename(@TempDir Path root)
            throws Exception {
        ZoneId zone = ZoneId.of("Asia/Ho_Chi_Minh");
        LocalDateTime now = LocalDateTime.of(2026, 8, 4, 12, 15, 0, 125_000_000);
        MutableClock firstClock = new MutableClock(now, zone);
        DcamStorage storage = new DcamStorage(root.toFile());
        DcamMediaOutputImpl firstOutput = new DcamMediaOutputImpl(
                null, storage, () -> false, new NoOpLogger(),
                new DcamMediaReservation(firstClock, firstClock::sleep));
        DcamMediaFile existing = firstOutput.mediaFile(
                DcamFileType.IMAGE, "CAM001", "000001", false);
        Files.createDirectories(existing.getFile().toPath().getParent());
        Files.write(existing.getFile().toPath(), new byte[] {4, 5, 6});
        Path finalPath = storage.finalFile(existing).toPath();
        Files.createDirectories(finalPath.getParent());
        Files.move(existing.getFile().toPath(), finalPath);

        MutableClock recreatedClock = new MutableClock(now, zone);
        DcamMediaOutputImpl recreatedOutput = new DcamMediaOutputImpl(
                null, new DcamStorage(root.toFile()), () -> false, new NoOpLogger(),
                new DcamMediaReservation(recreatedClock, recreatedClock::sleep));
        DcamMediaFile next = recreatedOutput.mediaFile(
                DcamFileType.IMAGE, "CAM001", "000001", false);

        assertEquals("DCAM_CAM001_000001_20260804_121501.jpg",
                next.getFileName());
        assertEquals(List.of(875L), recreatedClock.sleeps);
        assertArrayEquals(new byte[] {4, 5, 6}, Files.readAllBytes(finalPath));
    }

    private static final class MutableClock extends Clock {
        private Instant now;
        private final ZoneId zone;
        private final List<Long> sleeps = new ArrayList<>();

        private MutableClock(LocalDateTime now, ZoneId zone) {
            this.now = now.atZone(zone).toInstant();
            this.zone = zone;
        }

        private void sleep(long millis) {
            sleeps.add(millis);
            now = now.plusMillis(millis);
        }

        @Override public ZoneId getZone() { return zone; }

        @Override public Clock withZone(ZoneId value) {
            return Clock.fixed(now, value);
        }

        @Override public Instant instant() { return now; }
    }

    private static final class NoOpLogger implements Logger {
        @Override public void debug(String message) {}
        @Override public void info(String message) {}
        @Override public void info(String message, Throwable error) {}
        @Override public void warn(String message, Throwable error) {}
        @Override public void error(String message, Throwable error) {}
    }
}