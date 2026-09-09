package com.dvid.dcam.platform.storage;

import com.dvid.dcam.core.logging.application.port.Logger;
import com.dvid.dcam.core.logging.domain.LogCategory;
import com.dvid.dcam.feature.storage.domain.DcamMediaFileState;
import com.dvid.dcam.platform.database.dao.MediaFileStateDao;
import com.dvid.dcam.platform.database.entities.MediaFileStateEntity;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

final class RoomDcamMediaStateStore implements DcamMediaStateStore {
    private final MediaFileStateDao dao;
    private final Logger logger;

    RoomDcamMediaStateStore(MediaFileStateDao dao, Logger logger) {
        this.dao = Objects.requireNonNull(dao, "dao");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    @Override
    public void update(
            String mediaFileName, File storageRoot, DcamMediaFileState state) {
        try {
            dao.upsert(new MediaFileStateEntity(
                    mediaFileName, state.name(), storageRoot.getAbsolutePath(),
                    System.currentTimeMillis()));
            if (state == DcamMediaFileState.BDMA_READY) {
                logger.info(LogCategory.BDMA, "media_bdma_ready", "Media file '"
                        + mediaFileName + "' is ready for BDMA.");
            }
        } catch (RuntimeException failure) {
            logger.warn(LogCategory.DB, "QA-DB-003: media_file_state_persist_failed", null,
                    "Could not persist media file state for '" + mediaFileName
                            + "'. Requested state: " + state + ".",
                    failure);
        }
    }

    @Override
    public List<TrackedState> findAll() {
        try {
            List<TrackedState> states = new ArrayList<>();
            for (MediaFileStateEntity entity : dao.findAll()) {
                try {
                    states.add(new TrackedState(
                            entity.fileName, new File(entity.storageRoot),
                            DcamMediaFileState.valueOf(entity.fileState)));
                } catch (IllegalArgumentException failure) {
                    logger.warn(LogCategory.DB, "QA-DB-003: media_file_state_invalid", null,
                            "Ignored invalid media file state for '" + entity.fileName
                                    + "'. Persisted state: " + entity.fileState + ".",
                            failure);
                }
            }
            return List.copyOf(states);
        } catch (RuntimeException failure) {
            logger.warn(LogCategory.DB, "QA-DB-003: media_file_state_read_failed", null,
                    "Could not read media file states for startup reconciliation.", failure);
            return List.of();
        }
    }

    @Override
    public void delete(String mediaFileName) {
        try {
            dao.delete(mediaFileName);
        } catch (RuntimeException failure) {
            logger.warn(LogCategory.DB, "QA-DB-003: media_file_state_delete_failed", null,
                    "Could not delete media file state for '" + mediaFileName + "'.", failure);
        }
    }
}
