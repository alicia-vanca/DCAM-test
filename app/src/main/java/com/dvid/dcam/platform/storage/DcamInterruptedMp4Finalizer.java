package com.dvid.dcam.platform.storage;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

final class DcamInterruptedMp4Finalizer {
    private static final int BOX_HDLR = 0x68646c72;
    private static final int BOX_MDAT = 0x6d646174;
    private static final int BOX_MDHD = 0x6d646864;
    private static final int BOX_MDIA = 0x6d646961;
    private static final int BOX_MOOF = 0x6d6f6f66;
    private static final int BOX_MOOV = 0x6d6f6f76;
    private static final int BOX_MVEX = 0x6d766578;
    private static final int BOX_MVHD = 0x6d766864;
    private static final int BOX_SIDX = 0x73696478;
    private static final int BOX_TFHD = 0x74666864;
    private static final int BOX_TKHD = 0x746b6864;
    private static final int BOX_TRAF = 0x74726166;
    private static final int BOX_TRAK = 0x7472616b;
    private static final int BOX_TRUN = 0x7472756e;
    private static final int HANDLER_AUDIO = 0x736f756e;
    private static final int HANDLER_VIDEO = 0x76696465;
    private static final int SIDX_HEADER_BYTES = 40;
    private static final int SIDX_REFERENCE_BYTES = 12;
    private static final int SIDX_STARTS_WITH_SAP_TYPE_1 = 0x90000000;
    private static final long UNSIGNED_INT_MAX = 0xffff_ffffL;

    Result finalizeInterrupted(File file) throws IOException {
        Path source = file.toPath().toAbsolutePath();
        Path parent = source.getParent();
        if (parent == null) throw new IOException("MP4 staging directory is unavailable.");
        Path working = Files.createTempFile(parent, "." + file.getName() + ".repair-", ".tmp");
        try {
            Files.copy(source, working, StandardCopyOption.REPLACE_EXISTING);
            Result result = finalizeWorkingCopy(working.toFile());
            if (!result.finalized() || result.durationMillis() <= 0L) return result;
            DcamMediaPublisher.move(working, source, StandardCopyOption.REPLACE_EXISTING);
            return result;
        } finally {
            Files.deleteIfExists(working);
        }
    }

    Result finalizeInterrupted(DcamRandomAccessMedia media) throws IOException {
        media.beginTransaction();
        try {
            Result result = finalizeMedia(media);
            if (!result.finalized() || result.durationMillis() <= 0L) {
                media.rollbackTransaction();
                return result;
            }
            media.commitTransaction();
            media.force(false);
            return result;
        } catch (IOException | RuntimeException failure) {
            try {
                media.rollbackTransaction();
            } catch (IOException rollbackFailure) {
                failure.addSuppressed(rollbackFailure);
            }
            throw failure;
        }
    }


    CleanResult finalizeCleanTimed(File file, long durationUs) throws IOException {
        if (durationUs <= 0L) throw new IOException("Clean MP4 duration is unavailable.");
        try (DcamRandomAccessMedia media = PlainDcamRandomAccessMedia.open(file)) {
            long patchStarted = System.nanoTime();
            CleanPatch patch = patchCleanTimed(media, durationUs);
            long patchCompleted = System.nanoTime();
            media.force(false);
            long syncCompleted = System.nanoTime();
            return patch.result(
                    elapsedMillis(patchStarted, patchCompleted),
                    elapsedMillis(patchCompleted, syncCompleted));
        }
    }

    CleanResult finalizeCleanTimed(DcamRandomAccessMedia media, long durationUs)
            throws IOException {
        if (durationUs <= 0L) throw new IOException("Clean MP4 duration is unavailable.");
        media.beginTransaction();
        long patchStarted = System.nanoTime();
        try {
            CleanPatch patch = patchCleanTimed(media, durationUs);
            long patchCompleted = System.nanoTime();
            media.commitTransaction();
            media.force(false);
            long syncCompleted = System.nanoTime();
            return patch.result(
                    elapsedMillis(patchStarted, patchCompleted),
                    elapsedMillis(patchCompleted, syncCompleted));
        } catch (IOException | RuntimeException failure) {
            try {
                media.rollbackTransaction();
            } catch (IOException rollbackFailure) {
                failure.addSuppressed(rollbackFailure);
            }
            throw failure;
        }
    }

    private static CleanPatch patchCleanTimed(DcamRandomAccessMedia media, long durationUs)
            throws IOException {
        DurationPatch duration = patchKnownDuration(media, media.length(), durationUs);
        if (!duration.patched()) {
            throw new IOException("Clean MP4 duration metadata could not be patched.");
        }
        boolean writerSeekIndexUsed = patchSeekIndex(media, media.length(), false);
        return new CleanPatch(duration.durationMillis(), writerSeekIndexUsed);
    }

    private static long elapsedMillis(long started, long completed) {
        return Math.max(0L, (completed - started) / 1_000_000L);
    }

    private static Result finalizeWorkingCopy(File file) throws IOException {
        try (DcamRandomAccessMedia media = PlainDcamRandomAccessMedia.open(file)) {
            Result result = finalizeMedia(media);
            if (result.finalized() && result.durationMillis() > 0L) media.force(false);
            return result;
        }
    }

    private static Result finalizeMedia(DcamRandomAccessMedia media) throws IOException {
        long originalBytes = media.length();
        FragmentScan scan = scanFragmentedMedia(media, originalBytes,
                seekIndexReservation(media, originalBytes));
        Result unchanged = unchangedFinalizationResult(originalBytes, scan);
        if (unchanged != null) return unchanged;
        return finalizeRecoveredMedia(media, originalBytes, scan);
    }

    private static FragmentScan scanFragmentedMedia(
            DcamRandomAccessMedia media, long originalBytes, SeekIndexReservation seekIndex)
            throws IOException {
        FragmentScanState state = new FragmentScanState(0L, -1L, 0, false, -1L);
        while (state.offset() < originalBytes && state.incompleteOffset() < 0L) {
            state = scanNextTopLevelBox(media, state, originalBytes, seekIndex);
        }
        long incompleteOffset = state.incompleteOffset() >= 0L
                ? state.incompleteOffset() : state.pendingFragmentOffset();
        return new FragmentScan(state.fragmented(), incompleteOffset, state.completeFragments());
    }

    private static FragmentScanState scanNextTopLevelBox(
            DcamRandomAccessMedia media, FragmentScanState state, long originalBytes,
            SeekIndexReservation seekIndex) throws IOException {
        if (seekIndex != null && state.offset() == seekIndex.offset()) {
            return new FragmentScanState(seekIndex.end(), state.pendingFragmentOffset(),
                    state.completeFragments(), state.fragmented(), -1L);
        }
        if (originalBytes - state.offset() < 8L) return incompleteTail(state, state.offset());
        Box box = readBox(media, state.offset(), originalBytes);
        if (box == null) return incompleteTail(state, state.offset());
        return scanTopLevelBox(media, state, box);
    }

    private static FragmentScanState scanTopLevelBox(
            DcamRandomAccessMedia media, FragmentScanState state, Box box) throws IOException {
        boolean fragmented = state.fragmented();
        long pendingFragmentOffset = state.pendingFragmentOffset();
        int completeFragments = state.completeFragments();
        if (box.type() == BOX_MOOV) {
            fragmented = findChild(media, box, BOX_MVEX) != null;
        } else if (box.type() == BOX_MOOF && fragmented) {
            if (pendingFragmentOffset >= 0L) return incompleteTail(state, pendingFragmentOffset);
            pendingFragmentOffset = state.offset();
        } else if (box.type() == BOX_MDAT && pendingFragmentOffset >= 0L) {
            completeFragments++;
            pendingFragmentOffset = -1L;
        } else if (pendingFragmentOffset >= 0L) {
            return incompleteTail(state, pendingFragmentOffset);
        }
        return new FragmentScanState(box.end(), pendingFragmentOffset, completeFragments,
                fragmented, -1L);
    }

    private static FragmentScanState incompleteTail(FragmentScanState state, long fallbackOffset) {
        long incompleteOffset = state.pendingFragmentOffset() >= 0L
                ? state.pendingFragmentOffset() : fallbackOffset;
        return new FragmentScanState(state.offset(), state.pendingFragmentOffset(),
                state.completeFragments(), state.fragmented(), incompleteOffset);
    }

    private static Result unchangedFinalizationResult(long originalBytes, FragmentScan scan) {
        if (!scan.fragmented()) {
            return Result.unchanged(originalBytes, scan.completeFragments(),
                    "File is not a fragmented MP4 recording.");
        }
        if (scan.hasIncompleteTail() && scan.completeFragments() == 0) {
            return Result.unchanged(originalBytes, 0,
                    "No complete media fragment exists before the incomplete tail.");
        }
        return null;
    }

    private static Result finalizeRecoveredMedia(
            DcamRandomAccessMedia media, long originalBytes, FragmentScan scan) throws IOException {
        long retainedBytes = scan.hasIncompleteTail() ? scan.incompleteOffset() : originalBytes;
        if (scan.hasIncompleteTail()) media.setLength(retainedBytes);
        DurationPatch duration = patchDuration(media, retainedBytes);
        if (!duration.patched()) {
            return Result.unchanged(originalBytes, scan.completeFragments(),
                    "Duration metadata could not be derived; original file was preserved.");
        }
        patchSeekIndex(media, retainedBytes, true);
        return Result.finalized(originalBytes, retainedBytes, scan.completeFragments(),
                scan.hasIncompleteTail(), duration.durationMillis());
    }
    private static DurationPatch patchDuration(DcamRandomAccessMedia media, long fileLength)
            throws IOException {
        DurationContext context = durationContext(media, fileLength);
        if (context == null) return DurationPatch.none();

        SeekIndexReservation reservation = seekIndexReservation(media, fileLength);
        long trackDuration = 0L;
        long lastPositiveSampleDuration = 0L;
        long offset = 0L;
        while (offset + 8L <= fileLength) {
            if (reservation != null && offset == reservation.offset()) {
                offset = reservation.end();
                continue;
            }
            Box box = readBox(media, offset, fileLength);
            if (box == null) return DurationPatch.none();
            if (box.type() == BOX_MOOF) {
                FragmentTiming timing = fragmentTiming(media, box, context.track().trackId(),
                        lastPositiveSampleDuration, reservation != null);
                if (timing == null || timing.duration() > Long.MAX_VALUE - trackDuration) {
                    return DurationPatch.none();
                }
                trackDuration += timing.duration();
                lastPositiveSampleDuration = timing.lastPositiveSampleDuration();
            }
            offset = box.end();
        }
        return writeDuration(media, context, trackDuration);
    }

    private static DurationPatch patchKnownDuration(
            DcamRandomAccessMedia media, long fileLength, long durationUs) throws IOException {
        DurationContext context = durationContext(media, fileLength);
        if (context == null) return DurationPatch.none();
        long trackDuration = Math.round(
                durationUs * (double) context.track().timescale() / 1_000_000.0);
        return writeDuration(media, context, trackDuration);
    }

    private static DurationContext durationContext(DcamRandomAccessMedia media, long fileLength)
            throws IOException {
        Box moov = findTopLevel(media, fileLength, BOX_MOOV);
        if (moov == null) return null;
        Box mvhd = findChild(media, moov, BOX_MVHD);
        if (mvhd == null || !contains(mvhd, 12L, 8L) || version(media, mvhd) != 0) {
            return null;
        }
        long movieTimescale = readUnsignedInt(media, mvhd.contentOffset() + 12L);
        TrackHeader track = findTrack(media, moov);
        if (movieTimescale <= 0L || track == null || track.timescale() <= 0L) return null;
        return new DurationContext(movieTimescale, mvhd.contentOffset() + 16L, track);
    }

    private static DurationPatch writeDuration(
            DcamRandomAccessMedia media, DurationContext context, long trackDuration)
            throws IOException {
        if (trackDuration <= 0L || trackDuration > UNSIGNED_INT_MAX) {
            return DurationPatch.none();
        }
        long movieDuration = Math.round(trackDuration
                * (double) context.movieTimescale() / context.track().timescale());
        if (movieDuration <= 0L || movieDuration > UNSIGNED_INT_MAX) {
            return DurationPatch.none();
        }

        writeUnsignedInt(media, context.mvhdDurationOffset(), movieDuration);
        writeUnsignedInt(media, context.track().tkhdDurationOffset(), movieDuration);
        writeUnsignedInt(media, context.track().mdhdDurationOffset(), trackDuration);
        long durationMillis = Math.max(1L,
                Math.round(trackDuration * 1_000.0 / context.track().timescale()));
        return new DurationPatch(true, durationMillis);
    }

    private static TrackHeader findTrack(DcamRandomAccessMedia media, Box moov) throws IOException {
        TrackHeader audioTrack = null;
        for (TrackHeader track : findTracks(media, moov)) {
            if (track.handlerType() == HANDLER_VIDEO) return track;
            if (track.handlerType() == HANDLER_AUDIO && audioTrack == null) audioTrack = track;
        }
        return audioTrack;
    }

    private static List<TrackHeader> findTracks(DcamRandomAccessMedia media, Box moov)
            throws IOException {
        List<TrackHeader> tracks = new ArrayList<>();
        long offset = moov.contentOffset();
        while (offset + 8L <= moov.end()) {
            Box child = readBox(media, offset, moov.end());
            if (child == null) return List.of();
            TrackHeader track = child.type() == BOX_TRAK ? readTrackHeader(media, child) : null;
            if (track != null) tracks.add(track);
            offset = child.end();
        }
        return tracks;
    }

    private static TrackHeader readTrackHeader(DcamRandomAccessMedia media, Box trak)
            throws IOException {
        Box tkhd = findChild(media, trak, BOX_TKHD);
        Box mdia = findChild(media, trak, BOX_MDIA);
        if (tkhd == null || mdia == null) return null;
        Box mdhd = findChild(media, mdia, BOX_MDHD);
        Box hdlr = findChild(media, mdia, BOX_HDLR);
        if (mdhd == null || hdlr == null || !contains(tkhd, 12L, 12L)
                || !contains(mdhd, 12L, 8L) || !contains(hdlr, 8L, 4L)
                || version(media, tkhd) != 0 || version(media, mdhd) != 0) {
            return null;
        }
        long handlerType = readUnsignedInt(media, hdlr.contentOffset() + 8L);
        long trackId = readUnsignedInt(media, tkhd.contentOffset() + 12L);
        long timescale = readUnsignedInt(media, mdhd.contentOffset() + 12L);
        if ((handlerType != HANDLER_VIDEO && handlerType != HANDLER_AUDIO)
                || trackId <= 0L || timescale <= 0L) {
            return null;
        }
        return new TrackHeader(trackId, timescale, tkhd.contentOffset() + 20L,
                mdhd.contentOffset() + 16L, handlerType);
    }

    private static FragmentTiming fragmentTiming(DcamRandomAccessMedia media, Box moof,
            long trackId, long fallbackSampleDuration, boolean synthesizeZeroDurations)
            throws IOException {
        TimingAccumulator timing = new TimingAccumulator();
        long offset = moof.contentOffset();
        while (offset + 8L <= moof.end()) {
            Box child = readBox(media, offset, moof.end());
            if (child == null) return null;
            if (child.type() == BOX_TRAF) {
                TrackFragmentTiming trackTiming = trackFragmentTiming(media, child, trackId);
                if (trackTiming == null || !timing.add(trackTiming)) return null;
            }
            offset = child.end();
        }
        return resolveFragmentTiming(timing, fallbackSampleDuration, synthesizeZeroDurations);
    }

    private static FragmentTiming resolveFragmentTiming(
            TimingAccumulator timing, long fallbackSampleDuration, boolean synthesizeZeroDurations) {
        long terminalSampleDuration = timing.lastPositiveSampleDuration() > 0L
                ? timing.lastPositiveSampleDuration() : fallbackSampleDuration;
        if (!synthesizeZeroDurations) {
            return new FragmentTiming(timing.duration(), terminalSampleDuration);
        }
        if (timing.isEmpty()) {
            return new FragmentTiming(0L, fallbackSampleDuration);
        }
        DcamFragmentedMp4Layout.ResolvedDuration resolved =
                DcamFragmentedMp4Layout.resolveDuration(timing.duration(),
                        timing.terminalZeroDurationSamples(), timing.lastPositiveSampleDuration(),
                        fallbackSampleDuration);
        return resolved == null ? null
                : new FragmentTiming(
                        resolved.duration(), resolved.lastPositiveSampleDuration());
    }

    private static TrackFragmentTiming trackFragmentTiming(
            DcamRandomAccessMedia media, Box traf, long trackId) throws IOException {
        Box tfhd = findChild(media, traf, BOX_TFHD);
        if (tfhd == null) return null;
        TrackFragment header = readTrackFragment(media, tfhd);
        if (header == null) return null;
        if (header.trackId() != trackId) {
            return new TrackFragmentTiming(0L, 0L, 0L, false);
        }
        return readTrackFragmentTimings(media, traf, header.defaultSampleDuration());
    }

    private static TrackFragmentTiming readTrackFragmentTimings(
            DcamRandomAccessMedia media, Box traf, long defaultSampleDuration) throws IOException {
        TimingAccumulator timing = new TimingAccumulator();
        long offset = traf.contentOffset();
        while (offset + 8L <= traf.end()) {
            Box child = readBox(media, offset, traf.end());
            if (child == null) return null;
            if (child.type() == BOX_TRUN) {
                TrackRunTiming runTiming = readTrackRunTiming(media, child, defaultSampleDuration);
                if (runTiming == null || !timing.add(runTiming)) return null;
            }
            offset = child.end();
        }
        return timing.toTrackFragmentTiming();
    }

    private static TrackFragment readTrackFragment(DcamRandomAccessMedia media, Box tfhd)
            throws IOException {
        media.seek(tfhd.contentOffset());
        requireBytes(media, tfhd, 8L);
        int versionAndFlags = media.readInt();
        int flags = versionAndFlags & 0x00ff_ffff;
        long trackId = Integer.toUnsignedLong(media.readInt());
        if ((flags & 0x000001) != 0) skip(media, tfhd, 8L);
        if ((flags & 0x000002) != 0) skip(media, tfhd, 4L);
        long defaultSampleDuration = 0L;
        if ((flags & 0x000008) != 0) {
            requireBytes(media, tfhd, 4L);
            defaultSampleDuration = Integer.toUnsignedLong(media.readInt());
        }
        if ((flags & 0x000010) != 0) skip(media, tfhd, 4L);
        if ((flags & 0x000020) != 0) skip(media, tfhd, 4L);
        return new TrackFragment(trackId, defaultSampleDuration);
    }

    private static TrackRunTiming readTrackRunTiming(
            DcamRandomAccessMedia media, Box trun, long defaultSampleDuration) throws IOException {
        TrackRunHeader header = readTrackRunHeader(media, trun);
        if (header.sampleCount() == 0L) return new TrackRunTiming(0L, 0L, 0L, false);
        if (!hasCompleteTrackRunSamples(media, trun, header)) return null;
        if (!header.sampleDurationsPresent()) {
            return implicitTrackRunTiming(header.sampleCount(), defaultSampleDuration);
        }
        return explicitTrackRunTiming(media, trun, header);
    }

    private static TrackRunHeader readTrackRunHeader(DcamRandomAccessMedia media, Box trun)
            throws IOException {
        media.seek(trun.contentOffset());
        requireBytes(media, trun, 8L);
        int flags = media.readInt() & 0x00ff_ffff;
        long sampleCount = Integer.toUnsignedLong(media.readInt());
        if ((flags & 0x000001) != 0) skip(media, trun, 4L);
        if ((flags & 0x000004) != 0) skip(media, trun, 4L);
        return new TrackRunHeader(flags, sampleCount);
    }

    private static boolean hasCompleteTrackRunSamples(
            DcamRandomAccessMedia media, Box trun, TrackRunHeader header) throws IOException {
        int bytesPerSample = bytesPerTrackRunSample(header.flags());
        long remaining = trun.end() - media.getFilePointer();
        return bytesPerSample == 0 || header.sampleCount() <= remaining / bytesPerSample;
    }

    private static int bytesPerTrackRunSample(int flags) {
        int bytes = (flags & 0x000100) != 0 ? 4 : 0;
        if ((flags & 0x000200) != 0) bytes += 4;
        if ((flags & 0x000400) != 0) bytes += 4;
        if ((flags & 0x000800) != 0) bytes += 4;
        return bytes;
    }

    private static TrackRunTiming implicitTrackRunTiming(long sampleCount, long defaultSampleDuration) {
        if (defaultSampleDuration <= 0L) return null;
        try {
            return new TrackRunTiming(Math.multiplyExact(sampleCount, defaultSampleDuration),
                    0L, defaultSampleDuration, true);
        } catch (ArithmeticException overflow) {
            return null;
        }
    }

    private static TrackRunTiming explicitTrackRunTiming(
            DcamRandomAccessMedia media, Box trun, TrackRunHeader header) throws IOException {
        TimingAccumulator timing = new TimingAccumulator();
        for (long sample = 0L; sample < header.sampleCount(); sample++) {
            requireBytes(media, trun, 4L);
            long sampleDuration = Integer.toUnsignedLong(media.readInt());
            if (!timing.addSampleDuration(sampleDuration)) return null;
            skipOptionalTrackRunSampleFields(media, trun, header.flags());
        }
        return timing.toTrackRunTiming();
    }

    private static void skipOptionalTrackRunSampleFields(
            DcamRandomAccessMedia media, Box trun, int flags) throws IOException {
        if ((flags & 0x000200) != 0) skip(media, trun, 4L);
        if ((flags & 0x000400) != 0) skip(media, trun, 4L);
        if ((flags & 0x000800) != 0) skip(media, trun, 4L);
    }

    private static boolean patchSeekIndex(
            DcamRandomAccessMedia media, long fileLength, boolean repairFragmentOffsets)
            throws IOException {
        SeekIndexReservation reservation = seekIndexReservation(media, fileLength);
        if (reservation == null) return false;
        List<TrackHeader> tracks = seekIndexTracks(media, fileLength);
        if (!repairFragmentOffsets) {
            return patchCleanSeekIndex(media, fileLength, reservation, tracks);
        }
        rebuildSeekIndex(media, fileLength, reservation, tracks);
        return false;
    }

    private static List<TrackHeader> seekIndexTracks(DcamRandomAccessMedia media, long fileLength)
            throws IOException {
        Box moov = findTopLevel(media, fileLength, BOX_MOOV);
        List<TrackHeader> tracks = moov == null ? List.of() : findTracks(media, moov);
        if (tracks.isEmpty()) throw new IOException("Seek index track metadata is unavailable.");
        return tracks;
    }

    private static boolean patchCleanSeekIndex(
            DcamRandomAccessMedia media, long fileLength, SeekIndexReservation reservation,
            List<TrackHeader> tracks) throws IOException {
        DcamFragmentedMp4Layout.StreamedSeekIndex streamed =
                DcamFragmentedMp4Layout.readStreamedSeekIndex(
                        media, reservation.offset(), reservation.end(), fileLength);
        if (streamed != null) {
            TrackHeader streamedTrack = findTrackById(tracks, streamed.trackId());
            if (streamedTrack == null) {
                throw new IOException("Clean streamed seek index track is unavailable.");
            }
            patchStreamedSegmentIndex(media, reservation, streamedTrack, streamed);
            return true;
        }
        DcamFragmentedMp4Layout.RecordedSeekIndex recorded =
                DcamFragmentedMp4Layout.readRecordedSeekIndex(
                        media, reservation.offset(), reservation.end(), fileLength);
        if (recorded == null) {
            throw new IOException("Clean seek index writer metadata is unavailable.");
        }
        List<SegmentIndex> recordedIndexes = recordedSegmentIndexes(tracks, recorded);
        if (recordedIndexes.isEmpty()) {
            throw new IOException("Clean seek index writer metadata is incompatible.");
        }
        requireIndexFits(reservation, recordedIndexes);
        writeSegmentIndexes(media, reservation, recordedIndexes, recorded.firstMoofOffset());
        return true;
    }

    private static TrackHeader findTrackById(List<TrackHeader> tracks, long trackId) {
        for (TrackHeader track : tracks) {
            if (track.trackId() == trackId) return track;
        }
        return null;
    }

    private static void rebuildSeekIndex(
            DcamRandomAccessMedia media, long fileLength, SeekIndexReservation reservation,
            List<TrackHeader> tracks) throws IOException {
        SeekIndexBuilder builder = new SeekIndexBuilder(tracks);
        scanSeekIndexFragments(media, fileLength, reservation, builder);
        List<SegmentIndex> indexes = builder.buildIndexes();
        if (indexes.isEmpty()) throw new IOException("Seek index has no timed media tracks.");
        requireIndexFits(reservation, indexes);
        writeSegmentIndexes(media, reservation, indexes, builder.firstMoofOffset());
    }

    private static void scanSeekIndexFragments(
            DcamRandomAccessMedia media, long fileLength, SeekIndexReservation reservation,
            SeekIndexBuilder builder) throws IOException {
        long offset = 0L;
        while (offset + 8L <= fileLength) {
            if (offset == reservation.offset()) {
                offset = reservation.end();
            } else {
                offset = nextSeekIndexFragmentOffset(media, fileLength, offset, builder);
            }
        }
    }

    private static long nextSeekIndexFragmentOffset(
            DcamRandomAccessMedia media, long fileLength, long offset, SeekIndexBuilder builder)
            throws IOException {
        Box box = readBox(media, offset, fileLength);
        if (box == null) throw new IOException("Seek index found invalid top-level MP4 metadata.");
        if (box.type() != BOX_MOOF) return box.end();
        Box mdat = readBox(media, box.end(), fileLength);
        if (mdat == null || mdat.type() != BOX_MDAT) {
            throw new IOException("Seek index found a media fragment without sample data.");
        }
        long fragmentEnd = gpsRouteBoxEnd(media, mdat.end(), fileLength);
        long referenceSize = fragmentEnd - offset;
        if (referenceSize <= 0L || referenceSize > 0x7fff_ffffL) {
            throw new IOException("Seek index fragment metadata is outside MP4 limits.");
        }
        patchFragmentBaseOffsets(media, box, offset);
        builder.appendFragment(media, box, offset, referenceSize);
        return fragmentEnd;
    }

    private static long gpsRouteBoxEnd(
            DcamRandomAccessMedia media, long offset, long fileLength) throws IOException {
        long end = offset;
        while (end + 8L <= fileLength) {
            Box box = readBox(media, end, fileLength);
            if (box == null || !isGpsRouteBox(media, box)) break;
            end = box.end();
        }
        return end;
    }

    private static boolean isGpsRouteBox(DcamRandomAccessMedia media, Box box)
            throws IOException {
        if (box.type() != DcamFragmentedMp4Layout.BOX_UUID || !contains(box, 0L, 16L)) {
            return false;
        }
        media.seek(box.contentOffset());
        return media.readLong()
                        == DcamFragmentedMp4Layout.GPS_ROUTE_UUID_MOST_SIGNIFICANT_BITS
                && media.readLong()
                        == DcamFragmentedMp4Layout.GPS_ROUTE_UUID_LEAST_SIGNIFICANT_BITS;
    }
    private static void patchStreamedSegmentIndex(
            DcamRandomAccessMedia media,
            SeekIndexReservation reservation,
            TrackHeader track,
            DcamFragmentedMp4Layout.StreamedSeekIndex streamed) throws IOException {
        int sidxBytes = SIDX_HEADER_BYTES
                + streamed.referenceCount() * SIDX_REFERENCE_BYTES;
        long firstOffset = streamed.firstMoofOffset() - (reservation.offset() + sidxBytes);
        long freeOffset = reservation.offset() + sidxBytes;
        long freeBytes = reservation.end() - freeOffset;
        if (firstOffset < 0L || freeBytes < 16L) {
            throw new IOException("Clean streamed seek index reservation is too small.");
        }
        media.seek(reservation.offset() + 8L);
        media.writeInt(0x01000000);
        media.writeInt((int) track.trackId());
        media.writeInt((int) track.timescale());
        media.writeLong(0L);
        media.writeLong(firstOffset);
        media.writeShort(0);
        media.writeShort(streamed.referenceCount());
        media.seek(freeOffset);
        media.writeInt((int) freeBytes);
        media.writeInt(DcamFragmentedMp4Layout.BOX_FREE);
        media.seek(reservation.end() - Long.BYTES);
        media.writeLong(DcamFragmentedMp4Layout.SEEK_INDEX_MAGIC);
        media.seek(reservation.offset());
        media.writeInt(sidxBytes);
        media.writeInt(BOX_SIDX);
    }

    private static List<SegmentIndex> recordedSegmentIndexes(
            List<TrackHeader> tracks, DcamFragmentedMp4Layout.RecordedSeekIndex recorded) throws IOException {
        List<SegmentIndex> indexes = new ArrayList<>();
        for (int recordedTrack = 0; recordedTrack < recorded.trackIds().size(); recordedTrack++) {
            long trackId = recorded.trackIds().get(recordedTrack);
            TrackHeader track = null;
            for (TrackHeader candidate : tracks) {
                if (candidate.trackId() == trackId) {
                    track = candidate;
                    break;
                }
            }
            if (track == null) return List.of();
            List<FragmentReference> references = new ArrayList<>(recorded.fragments().size());
            long pendingBytes = 0L;
            for (DcamFragmentedMp4Layout.RecordedFragment fragment : recorded.fragments()) {
                pendingBytes = addReferenceBytes(pendingBytes, fragment.size());
                long duration = fragment.durations().get(recordedTrack);
                if (duration > 0L) {
                    references.add(new FragmentReference(pendingBytes, duration));
                    pendingBytes = 0L;
                }
            }
            appendTrailingReferenceBytes(references, pendingBytes);
            if (references.isEmpty()) return List.of();
            indexes.add(new SegmentIndex(track, references));
        }
        return indexes;
    }

    private static long addReferenceBytes(long accumulated, long fragmentBytes)
            throws IOException {
        if (fragmentBytes <= 0L || fragmentBytes > 0x7fff_ffffL
                || accumulated > 0x7fff_ffffL - fragmentBytes) {
            throw new IOException("Seek index reference size is outside MP4 limits.");
        }
        return accumulated + fragmentBytes;
    }

    private static void appendTrailingReferenceBytes(
            List<FragmentReference> references, long trailingBytes) throws IOException {
        if (trailingBytes == 0L) return;
        if (references.isEmpty()) {
            throw new IOException("Seek index track has no timed media fragment.");
        }
        int lastIndex = references.size() - 1;
        FragmentReference last = references.get(lastIndex);
        references.set(lastIndex, new FragmentReference(
                addReferenceBytes(last.size(), trailingBytes), last.duration()));
    }

    private static void requireIndexFits(
            SeekIndexReservation reservation, List<SegmentIndex> indexes) throws IOException {
        long indexBytes = 0L;
        for (SegmentIndex index : indexes) {
            if (index.references().isEmpty() || index.references().size() > 0xffff) {
                throw new IOException("Seek index fragment count is outside MP4 limits.");
            }
            indexBytes += SIDX_HEADER_BYTES
                    + (long) index.references().size() * SIDX_REFERENCE_BYTES;
        }
        if (indexBytes + 16L > reservation.size()) {
            throw new IOException("Seek index reservation is too small.");
        }
    }

    private static SeekIndexReservation seekIndexReservation(
            DcamRandomAccessMedia media, long fileLength) throws IOException {
        Box moov = findTopLevel(media, fileLength, BOX_MOOV);
        if (moov == null || moov.end() > fileLength - DcamFragmentedMp4Layout.SEEK_INDEX_RESERVE_BYTES) return null;
        long offset = moov.end();
        long end = offset + DcamFragmentedMp4Layout.SEEK_INDEX_RESERVE_BYTES;
        media.seek(end - Long.BYTES);
        return media.readLong() == DcamFragmentedMp4Layout.SEEK_INDEX_MAGIC
                ? new SeekIndexReservation(offset, end) : null;
    }

    private static void patchFragmentBaseOffsets(
            DcamRandomAccessMedia media, Box moof, long moofOffset) throws IOException {
        long offset = moof.contentOffset();
        while (offset + 8L <= moof.end()) {
            Box child = readBox(media, offset, moof.end());
            if (child == null) throw new IOException("Seek index found invalid fragment metadata.");
            if (child.type() == BOX_TRAF) patchTrackFragmentBaseOffset(media, child, moofOffset);
            offset = child.end();
        }
    }

    private static void patchTrackFragmentBaseOffset(
            DcamRandomAccessMedia media, Box traf, long moofOffset) throws IOException {
        Box tfhd = findChild(media, traf, BOX_TFHD);
        if (tfhd == null) throw new IOException("Seek index found a fragment without track metadata.");
        media.seek(tfhd.contentOffset());
        requireBytes(media, tfhd, 8L);
        int flags = media.readInt() & 0x00ff_ffff;
        media.readInt();
        if ((flags & 0x000001) == 0) return;
        requireBytes(media, tfhd, Long.BYTES);
        media.writeLong(moofOffset);
    }

    private static void writeSegmentIndexes(
            DcamRandomAccessMedia media,
            SeekIndexReservation reservation,
            List<SegmentIndex> indexes,
            long firstMoofOffset) throws IOException {
        List<Long> indexOffsets = new ArrayList<>();
        long offset = reservation.offset();
        for (SegmentIndex index : indexes) {
            int sidxBytes = SIDX_HEADER_BYTES
                    + index.references().size() * SIDX_REFERENCE_BYTES;
            long firstOffset = firstMoofOffset - (offset + sidxBytes);
            if (firstOffset < 0L) throw new IOException("Seek index reservation is too small.");
            indexOffsets.add(offset);
            media.seek(offset + 8L);
            media.writeInt(0x01000000);
            media.writeInt((int) index.track().trackId());
            media.writeInt((int) index.track().timescale());
            media.writeLong(0L);
            media.writeLong(firstOffset);
            media.writeShort(0);
            media.writeShort(index.references().size());
            for (FragmentReference reference : index.references()) {
                media.writeInt((int) reference.size());
                media.writeInt((int) reference.duration());
                media.writeInt(SIDX_STARTS_WITH_SAP_TYPE_1);
            }
            offset += sidxBytes;
        }
        long freeBytes = reservation.end() - offset;
        media.seek(offset);
        media.writeInt((int) freeBytes);
        media.writeInt(DcamFragmentedMp4Layout.BOX_FREE);
        media.seek(reservation.end() - Long.BYTES);
        media.writeLong(DcamFragmentedMp4Layout.SEEK_INDEX_MAGIC);
        for (int index = indexes.size() - 1; index >= 0; index--) {
            int sidxBytes = SIDX_HEADER_BYTES
                    + indexes.get(index).references().size() * SIDX_REFERENCE_BYTES;
            media.seek(indexOffsets.get(index));
            media.writeInt(sidxBytes);
            media.writeInt(BOX_SIDX);
        }
    }

    private static Box findTopLevel(DcamRandomAccessMedia media, long fileLength, int expectedType)
            throws IOException {
        long offset = 0L;
        while (offset + 8L <= fileLength) {
            Box box = readBox(media, offset, fileLength);
            if (box == null) return null;
            if (box.type() == expectedType) return box;
            offset = box.end();
        }
        return null;
    }

    private static Box findChild(DcamRandomAccessMedia media, Box parent, int expectedType)
            throws IOException {
        long offset = parent.contentOffset();
        while (offset + 8L <= parent.end()) {
            Box child = readBox(media, offset, parent.end());
            if (child == null) return null;
            if (child.type() == expectedType) return child;
            offset = child.end();
        }
        return null;
    }

    private static Box readBox(DcamRandomAccessMedia media, long offset, long boxEnd)
            throws IOException {
        if (offset + 8L > boxEnd) return null;
        media.seek(offset);
        long boxSize = Integer.toUnsignedLong(media.readInt());
        int boxType = media.readInt();
        long headerSize = 8L;
        if (boxSize == 1L) {
            if (offset + 16L > boxEnd) return null;
            boxSize = media.readLong();
            headerSize = 16L;
        } else if (boxSize == 0L) {
            boxSize = boxEnd - offset;
        }
        if (boxSize < headerSize || boxSize > boxEnd - offset) return null;
        return new Box(boxType, offset + headerSize, offset + boxSize);
    }

    private static int version(DcamRandomAccessMedia media, Box box) throws IOException {
        media.seek(box.contentOffset());
        requireBytes(media, box, 1L);
        return media.readUnsignedByte();
    }

    private static boolean contains(Box box, long relativeOffset, long bytes) {
        long contentBytes = box.end() - box.contentOffset();
        return relativeOffset >= 0L && bytes >= 0L
                && relativeOffset <= contentBytes && bytes <= contentBytes - relativeOffset;
    }

    private static long readUnsignedInt(DcamRandomAccessMedia media, long offset) throws IOException {
        media.seek(offset);
        return Integer.toUnsignedLong(media.readInt());
    }

    private static void writeUnsignedInt(DcamRandomAccessMedia media, long offset, long value)
            throws IOException {
        media.seek(offset);
        media.writeInt((int) value);
    }

    private static void skip(DcamRandomAccessMedia media, Box box, long bytes) throws IOException {
        requireBytes(media, box, bytes);
        media.seek(media.getFilePointer() + bytes);
    }

    private static void requireBytes(DcamRandomAccessMedia media, Box box, long bytes)
            throws IOException {
        if (media.getFilePointer() + bytes > box.end()) {
            throw new IOException("Truncated MP4 box metadata.");
        }
    }

    private static final class TimingAccumulator {
        private long duration;
        private long terminalZeroDurationSamples;
        private long lastPositiveSampleDuration;
        private boolean hasPositiveSampleDuration;

        private boolean add(TrackFragmentTiming timing) {
            return add(timing.duration(), timing.terminalZeroDurationSamples(),
                    timing.lastPositiveSampleDuration(), timing.hasPositiveSampleDuration());
        }

        private boolean add(TrackRunTiming timing) {
            return add(timing.duration(), timing.terminalZeroDurationSamples(),
                    timing.lastPositiveSampleDuration(), timing.hasPositiveSampleDuration());
        }

        private boolean add(
                long addedDuration, long trailingZeroSamples, long lastPositiveDuration,
                boolean hasPositiveDuration) {
            if (addedDuration > Long.MAX_VALUE - duration) return false;
            duration += addedDuration;
            if (hasPositiveDuration) {
                terminalZeroDurationSamples = trailingZeroSamples;
                hasPositiveSampleDuration = true;
            } else if (trailingZeroSamples > Long.MAX_VALUE - terminalZeroDurationSamples) {
                return false;
            } else {
                terminalZeroDurationSamples += trailingZeroSamples;
            }
            if (lastPositiveDuration > 0L) lastPositiveSampleDuration = lastPositiveDuration;
            return true;
        }

        private boolean addSampleDuration(long sampleDuration) {
            if (sampleDuration == 0L) {
                terminalZeroDurationSamples++;
                return true;
            }
            if (sampleDuration > Long.MAX_VALUE - duration) return false;
            duration += sampleDuration;
            terminalZeroDurationSamples = 0L;
            lastPositiveSampleDuration = sampleDuration;
            hasPositiveSampleDuration = true;
            return true;
        }

        private long duration() { return duration; }

        private long terminalZeroDurationSamples() { return terminalZeroDurationSamples; }

        private long lastPositiveSampleDuration() { return lastPositiveSampleDuration; }

        private boolean isEmpty() {
            return duration == 0L && terminalZeroDurationSamples == 0L
                    && lastPositiveSampleDuration == 0L;
        }

        private TrackRunTiming toTrackRunTiming() {
            return new TrackRunTiming(duration, terminalZeroDurationSamples,
                    lastPositiveSampleDuration, hasPositiveSampleDuration);
        }

        private TrackFragmentTiming toTrackFragmentTiming() {
            return new TrackFragmentTiming(duration, terminalZeroDurationSamples,
                    lastPositiveSampleDuration, hasPositiveSampleDuration);
        }
    }

    private static final class SeekIndexBuilder {
        private final List<TrackHeader> tracks;
        private final List<List<FragmentReference>> referencesByTrack = new ArrayList<>();
        private final boolean aggregateAudio;
        private final long[] lastPositiveSampleDurations;
        private final long[] pendingReferenceBytes;
        private final long[] pendingReferenceDurations;
        private final int[] pendingTimedFragments;
        private long firstMoofOffset = -1L;

        private SeekIndexBuilder(List<TrackHeader> tracks) {
            this.tracks = tracks;
            for (int track = 0; track < tracks.size(); track++) {
                referencesByTrack.add(new ArrayList<>());
            }
            aggregateAudio = tracks.size() == 1
                    && tracks.get(0).handlerType() == HANDLER_AUDIO;
            lastPositiveSampleDurations = new long[tracks.size()];
            pendingReferenceBytes = new long[tracks.size()];
            pendingReferenceDurations = new long[tracks.size()];
            pendingTimedFragments = new int[tracks.size()];
        }

        private void appendFragment(
                DcamRandomAccessMedia media, Box moof, long moofOffset, long referenceSize)
                throws IOException {
            for (int track = 0; track < tracks.size(); track++) {
                appendTrackFragment(media, moof, track, referenceSize);
            }
            if (firstMoofOffset < 0L) firstMoofOffset = moofOffset;
        }

        private void appendTrackFragment(
                DcamRandomAccessMedia media, Box moof, int track, long referenceSize) throws IOException {
            FragmentTiming timing = fragmentTiming(media, moof, tracks.get(track).trackId(),
                    lastPositiveSampleDurations[track], true);
            if (timing == null || timing.duration() > UNSIGNED_INT_MAX) {
                throw new IOException("Seek index fragment metadata is outside MP4 limits.");
            }
            pendingReferenceBytes[track] = addReferenceBytes(
                    pendingReferenceBytes[track], referenceSize);
            if (timing.duration() > 0L) appendTimedReference(track, timing.duration());
            lastPositiveSampleDurations[track] = timing.lastPositiveSampleDuration();
        }

        private void appendTimedReference(int track, long duration) throws IOException {
            if (aggregateAudio) {
                appendAggregatedAudioReference(track, duration);
            } else {
                referencesByTrack.get(track).add(new FragmentReference(
                        pendingReferenceBytes[track], duration));
                pendingReferenceBytes[track] = 0L;
            }
        }

        private void appendAggregatedAudioReference(int track, long duration) throws IOException {
            if (duration > UNSIGNED_INT_MAX - pendingReferenceDurations[track]) {
                throw new IOException("Seek index aggregate duration is outside MP4 limits.");
            }
            pendingReferenceDurations[track] += duration;
            pendingTimedFragments[track]++;
            if (pendingTimedFragments[track]
                    >= DcamFragmentedMp4Layout.AUDIO_FRAGMENTS_PER_INDEX_REFERENCE) {
                referencesByTrack.get(track).add(new FragmentReference(
                        pendingReferenceBytes[track], pendingReferenceDurations[track]));
                pendingReferenceBytes[track] = 0L;
                pendingReferenceDurations[track] = 0L;
                pendingTimedFragments[track] = 0;
            }
        }

        private List<SegmentIndex> buildIndexes() throws IOException {
            List<SegmentIndex> indexes = new ArrayList<>();
            for (int track = 0; track < tracks.size(); track++) {
                SegmentIndex index = buildTrackIndex(track);
                if (index != null) indexes.add(index);
            }
            return indexes;
        }

        private SegmentIndex buildTrackIndex(int track) throws IOException {
            List<FragmentReference> references = referencesByTrack.get(track);
            if (aggregateAudio && pendingReferenceDurations[track] > 0L) {
                references.add(new FragmentReference(
                        pendingReferenceBytes[track], pendingReferenceDurations[track]));
                pendingReferenceBytes[track] = 0L;
            }
            if (references.isEmpty()) return null;
            appendTrailingReferenceBytes(references, pendingReferenceBytes[track]);
            if (references.size() > 0xffff) {
                throw new IOException("Seek index fragment count is outside MP4 limits.");
            }
            return new SegmentIndex(tracks.get(track), references);
        }

        private long firstMoofOffset() { return firstMoofOffset; }
    }

    private record FragmentTiming(long duration, long lastPositiveSampleDuration) {}

    private record TrackFragmentTiming(long duration, long terminalZeroDurationSamples,
            long lastPositiveSampleDuration, boolean hasPositiveSampleDuration) {}

    private record TrackRunTiming(long duration, long terminalZeroDurationSamples,
            long lastPositiveSampleDuration, boolean hasPositiveSampleDuration) {}

    private record TrackRunHeader(int flags, long sampleCount) {
        boolean sampleDurationsPresent() { return (flags & 0x000100) != 0; }
    }

    private record FragmentScan(boolean fragmented, long incompleteOffset, int completeFragments) {
        boolean hasIncompleteTail() { return incompleteOffset >= 0L; }
    }

    private record FragmentScanState(
            long offset, long pendingFragmentOffset, int completeFragments, boolean fragmented,
            long incompleteOffset) {}

    private record SeekIndexReservation(long offset, long end) {
        long size() { return end - offset; }
    }

    private record FragmentReference(long size, long duration) {}

    private record Box(int type, long contentOffset, long end) {}

    private record TrackHeader(long trackId, long timescale, long tkhdDurationOffset,
            long mdhdDurationOffset, long handlerType) {}

    private record SegmentIndex(TrackHeader track, List<FragmentReference> references) {}

    private record DurationContext(
            long movieTimescale, long mvhdDurationOffset, TrackHeader track) {}

    private record TrackFragment(long trackId, long defaultSampleDuration) {}

    private record DurationPatch(boolean patched, long durationMillis) {
        static DurationPatch none() { return new DurationPatch(false, 0L); }
    }

    private record CleanPatch(long durationMillis, boolean writerSeekIndexUsed) {
        CleanResult result(long metadataPatchMillis, long durabilitySyncMillis) {
            return new CleanResult(durationMillis, metadataPatchMillis,
                    durabilitySyncMillis, writerSeekIndexUsed);
        }
    }

    record CleanResult(long durationMillis, long metadataPatchMillis, long durabilitySyncMillis,
            boolean writerSeekIndexUsed) {}

    record Result(
            boolean finalized,
            long originalBytes,
            long retainedBytes,
            int completeFragments,
            long durationMillis,
            String detail) {
        static Result unchanged(long bytes, int completeFragments, String detail) {
            return new Result(false, bytes, bytes, completeFragments, 0L, detail);
        }

        static Result finalized(long originalBytes, long retainedBytes, int completeFragments,
                boolean trimmed, long durationMillis) {
            long discardedBytes = originalBytes - retainedBytes;
            String trimDetail = trimmed
                    ? " Retained " + retainedBytes + " bytes and discarded " + discardedBytes
                            + " incomplete trailing bytes."
                    : "";
            String durationDetail = durationMillis > 0L
                    ? " Patched duration metadata to " + durationMillis + " ms."
                    : "";
            return new Result(true, originalBytes, retainedBytes, completeFragments,
                    durationMillis, "Finalized " + completeFragments + " complete fragment(s)."
                            + trimDetail + durationDetail);
        }
    }
}