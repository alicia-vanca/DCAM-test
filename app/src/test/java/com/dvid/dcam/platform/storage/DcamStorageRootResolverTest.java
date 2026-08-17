package com.dvid.dcam.platform.storage;

import com.dvid.dcam.feature.storage.domain.CaptureStorageCapacityPolicy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import com.dvid.dcam.feature.storage.domain.MediaPartitionLocation;
import java.io.File;
import java.util.List;
import org.junit.jupiter.api.Test;

final class DcamStorageRootResolverTest {
    private static final long QUALIFIED =
            CaptureStorageCapacityPolicy.MIN_NEW_CAPTURE_AVAILABLE_BYTES;
    private final DcamStorageRootResolver resolver =
            new DcamStorageRootResolver(new CaptureStorageCapacityPolicy());
    private final DcamStorageCandidate internal = candidate(MediaPartitionLocation.INTERNAL, "internal", QUALIFIED);

    @Test void autoPrioritizesQualifiedExternalStorage() {
        DcamStorageResolution result = resolver.resolve(MediaPartitionLocation.AUTO, internal,
                List.of(candidate(MediaPartitionLocation.EXTERNAL, "sd-card", QUALIFIED)),
                QUALIFIED);

        assertEquals(MediaPartitionLocation.EXTERNAL, result.getResolvedMode());
        assertEquals(new File("sd-card"), result.getRoot());
        assertFalse(result.isFallback());
    }

    @Test void fallsBackToInternalWhenExternalDoesNotQualify() {
        DcamStorageResolution result = resolver.resolve(MediaPartitionLocation.AUTO, internal,
                List.of(candidate(MediaPartitionLocation.EXTERNAL, "full-sd-card", QUALIFIED - 1L)),
                QUALIFIED);

        assertEquals(MediaPartitionLocation.INTERNAL, result.getResolvedMode());
        assertEquals(new File("internal"), result.getRoot());
        assertTrue(result.isFallback());
    }

    @Test void explicitInternalNeverSelectsExternal() {
        DcamStorageResolution result = resolver.resolve(MediaPartitionLocation.INTERNAL, internal,
                List.of(candidate(MediaPartitionLocation.EXTERNAL, "sd-card", QUALIFIED)),
                QUALIFIED);

        assertEquals(MediaPartitionLocation.INTERNAL, result.getResolvedMode());
        assertEquals(new File("internal"), result.getRoot());
        assertFalse(result.isFallback());
    }

    @Test void explicitExternalFallsBackOnlyWhenExternalDoesNotQualify() {
        DcamStorageResolution qualified = resolver.resolve(MediaPartitionLocation.EXTERNAL, internal,
                List.of(candidate(MediaPartitionLocation.EXTERNAL, "sd-card", QUALIFIED)),
                QUALIFIED);
        DcamStorageResolution unavailable = resolver.resolve(MediaPartitionLocation.EXTERNAL, internal,
                List.of(new DcamStorageCandidate(MediaPartitionLocation.EXTERNAL,
                        new File("sd-card"), false, false, 0L)), QUALIFIED);

        assertEquals(MediaPartitionLocation.EXTERNAL, qualified.getResolvedMode());
        assertEquals(MediaPartitionLocation.INTERNAL, unavailable.getResolvedMode());
        assertTrue(unavailable.isFallback());
    }

    @Test void nextCaptureCanFallBackAfterExternalLosesCapacity() {
        DcamStorageResolution firstCapture = resolver.resolve(MediaPartitionLocation.AUTO, internal,
                List.of(candidate(MediaPartitionLocation.EXTERNAL, "sd-card", QUALIFIED)),
                QUALIFIED);
        DcamStorageResolution nextCapture = resolver.resolve(MediaPartitionLocation.AUTO, internal,
                List.of(candidate(MediaPartitionLocation.EXTERNAL, "sd-card", QUALIFIED - 1L)),
                QUALIFIED);

        assertEquals(MediaPartitionLocation.EXTERNAL, firstCapture.getResolvedMode());
        assertEquals(MediaPartitionLocation.INTERNAL, nextCapture.getResolvedMode());
        assertTrue(nextCapture.isFallback());
    }

    @Test void autoFallsBackWhenExternalMeetsPhotoFloorButNotRecordingMinimum() {
        long recordingMinimum = QUALIFIED * 2L;
        DcamStorageCandidate recordingInternal = candidate(
                MediaPartitionLocation.INTERNAL, "internal", recordingMinimum);

        DcamStorageResolution result = resolver.resolve(MediaPartitionLocation.AUTO,
                recordingInternal,
                List.of(candidate(MediaPartitionLocation.EXTERNAL, "sd-card", QUALIFIED)),
                recordingMinimum);

        assertEquals(MediaPartitionLocation.INTERNAL, result.getResolvedMode());
        assertEquals(new File("internal"), result.getRoot());
        assertTrue(result.isFallback());
    }

    private static DcamStorageCandidate candidate(MediaPartitionLocation mode, String path, long available) {
        return new DcamStorageCandidate(mode, new File(path), true, true, available);
    }
}
