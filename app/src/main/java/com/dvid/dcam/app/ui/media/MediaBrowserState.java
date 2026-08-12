package com.dvid.dcam.app.ui.media;

import com.dvid.dcam.feature.media.domain.MediaEntry;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable state for the read-only DCAM media explorer. */
public final class MediaBrowserState {
    private final String relativePath;
    private final List<MediaEntry> entries;
    private final boolean loading;
    private final String error;

    public MediaBrowserState(String relativePath, List<MediaEntry> entries, boolean loading, String error) {
        this.relativePath = relativePath == null ? "" : relativePath;
        this.entries = Collections.unmodifiableList(new ArrayList<>(
                entries == null ? Collections.emptyList() : entries));
        this.loading = loading;
        this.error = error;
    }

    public static MediaBrowserState root() {
        return new MediaBrowserState("", Collections.emptyList(), false, null);
    }

    public String getRelativePath() { return relativePath; }
    public List<MediaEntry> getEntries() { return entries; }
    public boolean isLoading() { return loading; }
    public String getError() { return error; }
}
