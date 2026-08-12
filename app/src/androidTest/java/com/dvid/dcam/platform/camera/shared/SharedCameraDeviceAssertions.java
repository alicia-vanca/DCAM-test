package com.dvid.dcam.platform.camera.shared;

import android.media.MediaExtractor;
import android.media.MediaFormat;
import java.io.File;
import java.io.IOException;

public final class SharedCameraDeviceAssertions {
    private SharedCameraDeviceAssertions() {}

    public static long audioSampleCount(File file) throws IOException {
        MediaExtractor extractor = new MediaExtractor();
        try {
            extractor.setDataSource(file.getAbsolutePath());
            for (int index = 0; index < extractor.getTrackCount(); index++) {
                MediaFormat format = extractor.getTrackFormat(index);
                if (!MediaFormat.MIMETYPE_AUDIO_AAC.equals(format.getString(MediaFormat.KEY_MIME))) {
                    continue;
                }
                extractor.selectTrack(index);
                long samples = 0L;
                while (extractor.getSampleTrackIndex() >= 0) {
                    samples++;
                    if (!extractor.advance()) break;
                }
                return samples;
            }
            return 0L;
        } finally {
            extractor.release();
        }
    }
}
