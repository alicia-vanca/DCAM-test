package com.dvid.dcam.platform.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dvid.dcam.core.logging.application.port.Logger;
import com.dvid.dcam.core.logging.domain.LogCategory;
import com.dvid.dcam.feature.storage.domain.DcamMediaFileState;
import com.dvid.dcam.platform.database.dao.MediaFileStateDao;
import com.dvid.dcam.platform.database.entities.MediaFileStateEntity;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

final class RoomDcamMediaStateStoreTest {
    private static final String FILE_NAME = "DCAM_CAM001_USER001_20260908_120000.jpg";
    private static final File STORAGE_ROOT = new File("storage").getAbsoluteFile();

    @Test
    void persistsActualBdmaReadyStateOnCallerThread() {
        RecordingDao dao = new RecordingDao();
        CapturingLogger logger = new CapturingLogger();
        Thread caller = Thread.currentThread();

        new RoomDcamMediaStateStore(dao, logger)
                .update(FILE_NAME, STORAGE_ROOT, DcamMediaFileState.BDMA_READY);

        assertEquals(DcamMediaFileState.BDMA_READY.name(), dao.state.fileState);
        assertEquals(STORAGE_ROOT.getAbsolutePath(), dao.state.storageRoot);
        assertEquals(caller, dao.upsertThread);
        assertTrue(logger.infoCategories.contains(LogCategory.BDMA));
        assertTrue(logger.infoMessages.stream().anyMatch(message -> message.contains(FILE_NAME)
                && message.contains("ready for BDMA")));
    }

    @Test
    void databaseWriteFailureIsBestEffort() {
        RecordingDao dao = new RecordingDao(true);
        CapturingLogger logger = new CapturingLogger();

        new RoomDcamMediaStateStore(dao, logger)
                .update(FILE_NAME, STORAGE_ROOT, DcamMediaFileState.BDMA_READY);

        assertTrue(logger.warnCategories.contains(LogCategory.DB));
        assertTrue(logger.warnEventNames.contains(
                "QA-DB-003: media_file_state_persist_failed"));
        assertTrue(logger.warnMessages.stream().anyMatch(message -> message.contains(FILE_NAME)
                && message.contains("Could not persist media file state")));
        assertFalse(logger.infoCategories.contains(LogCategory.BDMA));
    }

    @Test
    void readsAndDeletesTrackedStates() {
        RecordingDao dao = new RecordingDao();
        CapturingLogger logger = new CapturingLogger();
        dao.state = new MediaFileStateEntity(
                FILE_NAME, DcamMediaFileState.RECOVERY_FAILED.name(),
                STORAGE_ROOT.getAbsolutePath(), 42L);
        RoomDcamMediaStateStore store = new RoomDcamMediaStateStore(dao, logger);

        DcamMediaStateStore.TrackedState tracked = store.findAll().get(0);
        assertEquals(FILE_NAME, tracked.mediaFileName());
        assertEquals(STORAGE_ROOT, tracked.storageRoot());
        assertEquals(DcamMediaFileState.RECOVERY_FAILED, tracked.state());

        store.delete(FILE_NAME);

        assertEquals(FILE_NAME, dao.deletedFileName);
    }

    private static final class RecordingDao implements MediaFileStateDao {
        private final boolean failOnUpsert;
        private MediaFileStateEntity state;
        private Thread upsertThread;
        private String deletedFileName;

        private RecordingDao() {
            this(false);
        }

        private RecordingDao(boolean failOnUpsert) {
            this.failOnUpsert = failOnUpsert;
        }

        @Override
        public void upsert(MediaFileStateEntity state) {
            if (failOnUpsert) throw new IllegalStateException("simulated SQLite write failure");
            this.state = state;
            upsertThread = Thread.currentThread();
        }

        @Override
        public List<MediaFileStateEntity> findAll() {
            return state == null ? List.of() : List.of(state);
        }

        @Override
        public void delete(String fileName) {
            deletedFileName = fileName;
        }
    }

    private static final class CapturingLogger implements Logger {
        private final List<String> infoMessages = new ArrayList<>();
        private final List<LogCategory> infoCategories = new ArrayList<>();
        private final List<String> warnMessages = new ArrayList<>();
        private final List<LogCategory> warnCategories = new ArrayList<>();
        private final List<String> warnEventNames = new ArrayList<>();

        @Override
        public void debug(LogCategory category, String eventName, String message) {}

        @Override
        public void info(LogCategory category, String eventName, String message) {
            infoCategories.add(category);
            infoMessages.add(message);
        }

        @Override
        public void info(LogCategory category, String eventName, String reasonCode,
                String message, Throwable error) {}

        @Override
        public void warn(LogCategory category, String eventName, String reasonCode,
                String message, Throwable error) {
            warnCategories.add(category);
            warnEventNames.add(eventName);
            warnMessages.add(message);
        }

        @Override
        public void error(LogCategory category, String eventName, String reasonCode,
                String message, Throwable error) {}
    }
}
