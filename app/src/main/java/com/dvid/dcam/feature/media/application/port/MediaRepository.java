package com.dvid.dcam.feature.media.application.port;

import com.dvid.dcam.feature.media.domain.MediaEntry;
import java.util.List;

/** Repository boundary for DCAM-managed media entries. */
public interface MediaRepository {
    List<MediaEntry> list(String relativePath) throws Exception;

    default List<MediaEntry> listWithoutCounts(String relativePath) throws Exception {
        return list(relativePath);
    }
}
