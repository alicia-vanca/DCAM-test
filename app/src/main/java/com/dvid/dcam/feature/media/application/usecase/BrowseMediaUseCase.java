package com.dvid.dcam.feature.media.application.usecase;

import com.dvid.dcam.feature.media.application.port.MediaRepository;
import com.dvid.dcam.feature.media.domain.MediaEntry;
import java.util.List;

public final class BrowseMediaUseCase {
    private final MediaRepository repository;

    public BrowseMediaUseCase(MediaRepository repository) {
        this.repository = repository;
    }

    public List<MediaEntry> execute(String relativePath) throws Exception {
        return repository.list(relativePath);
    }

    public List<MediaEntry> executeWithoutCounts(String relativePath) throws Exception {
        return repository.listWithoutCounts(relativePath);
    }
}
