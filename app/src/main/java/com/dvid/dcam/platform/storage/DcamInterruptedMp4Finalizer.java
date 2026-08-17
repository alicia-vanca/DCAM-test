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
        long offset = 0L;
        long incompleteOffset = -1L;
        long pendingFragmentOffset = -1L;
        int completeFragments = 0;
        boolean fragmented = false;
        SeekIndexReservation seekIndex = seekIndexReservation(media, originalBytes);

        while (offset < originalBytes) {
            if (seekIndex != null && offset == seekIndex.offset()) {
                offset = seekIndex.end();
                continue;
            }
            long remaining = originalBytes - offset;
            if (remaining < 8L) {
                incompleteOffset = pendingFragmentOffset >= 0L
                        ? pendingFragmentOffset : offset;
                break;
            }
            Box box = readBox(media, offset, originalBytes);
            if (box == null) {
                incompleteOffset = pendingFragmentOffset >= 0L
                        ? pendingFragmentOffset : offset;
                break;
            }

            if (box.type() == BOX_MOOV) {
                fragmented = findChild(media, box, BOX_MVEX) != null;
            } else if (box.type() == BOX_MOOF && fragmented) {
                if (pendingFragmentOffset >= 0L) {
                    incompleteOffset = pendingFragmentOffset;
                    break;
                }
                pendingFragmentOffset = offset;
            } else if (box.type() == BOX_MDAT && pendingFragmentOffset >= 0L) {
                completeFragments++;
                pendingFragmentOffset = -1L;
            } else if (pendingFragmentOffset >= 0L) {
                incompleteOffset = pendingFragmentOffset;
                break;
            }
            offset = box.end();
        }

        if (incompleteOffset < 0L && pendingFragmentOffset >= 0L) {
            incompleteOffset = pendingFragmentOffset;
        }
        if (!fragmented) {
            return Result.unchanged(originalBytes, completeFragments,
                    "File is not a fragmented MP4 recording.");
        }
        if (incompleteOffset >= 0L && completeFragments == 0) {
            return Result.unchanged(originalBytes, 0,
                    "No complete media fragment exists before the incomplete tail.");
        }

        long retainedBytes = originalBytes;
        boolean trimmed = incompleteOffset >= 0L;
        if (trimmed) {
            retainedBytes = incompleteOffset;
            media.setLength(retainedBytes);
        }
        DurationPatch duration = patchDuration(media, retainedBytes);
        if (!duration.patched()) {
            return Result.unchanged(originalBytes, completeFragments,
                    "Duration metadata could not be derived; original file was preserved.");
        }
        patchSeekIndex(media, retainedBytes, true);
        return Result.finalized(originalBytes, retainedBytes, completeFragments,
                trimmed, duration.durationMillis());
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
            if (child.type() == BOX_TRAK) {
                Box tkhd = findChild(media, child, BOX_TKHD);
                Box mdia = findChild(media, child, BOX_MDIA);
                Box mdhd = mdia == null ? null : findChild(media, mdia, BOX_MDHD);
                Box hdlr = mdia == null ? null : findChild(media, mdia, BOX_HDLR);
                if (tkhd != null && mdhd != null && hdlr != null
                        && contains(tkhd, 12L, 12L) && contains(mdhd, 12L, 8L)
                        && contains(hdlr, 8L, 4L)
                        && version(media, tkhd) == 0 && version(media, mdhd) == 0) {
                    long handlerType = readUnsignedInt(media, hdlr.contentOffset() + 8L);
                    long trackId = readUnsignedInt(media, tkhd.contentOffset() + 12L);
                    long timescale = readUnsignedInt(media, mdhd.contentOffset() + 12L);
                    if ((handlerType == HANDLER_VIDEO || handlerType == HANDLER_AUDIO)
                            && trackId > 0L && timescale > 0L) {
                        tracks.add(new TrackHeader(trackId, timescale,
                                tkhd.contentOffset() + 20L, mdhd.contentOffset() + 16L,
                                handlerType));
                    }
                }
            }
            offset = child.end();
        }
        return tracks;
    }

    private static FragmentTiming fragmentTiming(DcamRandomAccessMedia media, Box moof,
            long trackId, long fallbackSampleDuration, boolean synthesizeZeroDurations)
            throws IOException {
        long duration = 0L;
        long terminalZeroDurationSamples = 0L;
        long lastPositiveSampleDuration = 0L;
        long offset = moof.contentOffset();
        while (offset + 8L <= moof.end()) {
            Box child = readBox(media, offset, moof.end());
            if (child == null) return null;
            if (child.type() == BOX_TRAF) {
                TrackFragmentTiming timing = trackFragmentTiming(media, child, trackId);
                if (timing == null || timing.duration() > Long.MAX_VALUE - duration) return null;
                duration += timing.duration();
                if (timing.hasPositiveSampleDuration()) {
                    terminalZeroDurationSamples = timing.terminalZeroDurationSamples();
                } else if (timing.terminalZeroDurationSamples()
                        > Long.MAX_VALUE - terminalZeroDurationSamples) {
                    return null;
                } else {
                    terminalZeroDurationSamples += timing.terminalZeroDurationSamples();
                }
                if (timing.lastPositiveSampleDuration() > 0L) {
                    lastPositiveSampleDuration = timing.lastPositiveSampleDuration();
                }
            }
            offset = child.end();
        }
        long terminalSampleDuration = lastPositiveSampleDuration > 0L
                ? lastPositiveSampleDuration : fallbackSampleDuration;
        if (!synthesizeZeroDurations) {
            return new FragmentTiming(duration, terminalSampleDuration);
        }
        if (duration == 0L && terminalZeroDurationSamples == 0L
                && lastPositiveSampleDuration == 0L) {
            return new FragmentTiming(0L, fallbackSampleDuration);
        }
        DcamFragmentedMp4Layout.ResolvedDuration resolved =
                DcamFragmentedMp4Layout.resolveDuration(duration,
                        terminalZeroDurationSamples, lastPositiveSampleDuration,
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

        long duration = 0L;
        long terminalZeroDurationSamples = 0L;
        long lastPositiveSampleDuration = 0L;
        boolean hasPositiveSampleDuration = false;
        long offset = traf.contentOffset();
        while (offset + 8L <= traf.end()) {
            Box child = readBox(media, offset, traf.end());
            if (child == null) return null;
            if (child.type() == BOX_TRUN) {
                TrackRunTiming timing = readTrackRunTiming(
                        media, child, header.defaultSampleDuration());
                if (timing == null || timing.duration() > Long.MAX_VALUE - duration) return null;
                duration += timing.duration();
                if (timing.hasPositiveSampleDuration()) {
                    terminalZeroDurationSamples = timing.terminalZeroDurationSamples();
                    hasPositiveSampleDuration = true;
                } else if (timing.terminalZeroDurationSamples()
                        > Long.MAX_VALUE - terminalZeroDurationSamples) {
                    return null;
                } else {
                    terminalZeroDurationSamples += timing.terminalZeroDurationSamples();
                }
                if (timing.lastPositiveSampleDuration() > 0L) {
                    lastPositiveSampleDuration = timing.lastPositiveSampleDuration();
                }
            }
            offset = child.end();
        }
        return new TrackFragmentTiming(duration, terminalZeroDurationSamples,
                lastPositiveSampleDuration, hasPositiveSampleDuration);
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
        media.seek(trun.contentOffset());
        requireBytes(media, trun, 8L);
        int versionAndFlags = media.readInt();
        int flags = versionAndFlags & 0x00ff_ffff;
        long sampleCount = Integer.toUnsignedLong(media.readInt());
        if ((flags & 0x000001) != 0) skip(media, trun, 4L);
        if ((flags & 0x000004) != 0) skip(media, trun, 4L);
        if (sampleCount == 0L) return new TrackRunTiming(0L, 0L, 0L, false);

        boolean sampleDurationsPresent = (flags & 0x000100) != 0;
        int bytesPerSample = 0;
        if (sampleDurationsPresent) bytesPerSample += 4;
        if ((flags & 0x000200) != 0) bytesPerSample += 4;
        if ((flags & 0x000400) != 0) bytesPerSample += 4;
        if ((flags & 0x000800) != 0) bytesPerSample += 4;
        long remaining = trun.end() - media.getFilePointer();
        if (bytesPerSample > 0 && sampleCount > remaining / bytesPerSample) return null;
        if (!sampleDurationsPresent) {
            if (defaultSampleDuration <= 0L) return null;
            try {
                return new TrackRunTiming(
                        Math.multiplyExact(sampleCount, defaultSampleDuration),
                        0L, defaultSampleDuration, true);
            } catch (ArithmeticException overflow) {
                return null;
            }
        }

        long duration = 0L;
        long terminalZeroDurationSamples = 0L;
        long lastPositiveSampleDuration = 0L;
        boolean hasPositiveSampleDuration = false;
        for (long sample = 0L; sample < sampleCount; sample++) {
            requireBytes(media, trun, 4L);
            long sampleDuration = Integer.toUnsignedLong(media.readInt());
            if (sampleDuration == 0L) {
                terminalZeroDurationSamples++;
            } else {
                if (sampleDuration > Long.MAX_VALUE - duration) return null;
                duration += sampleDuration;
                terminalZeroDurationSamples = 0L;
                lastPositiveSampleDuration = sampleDuration;
                hasPositiveSampleDuration = true;
            }
            if ((flags & 0x000200) != 0) skip(media, trun, 4L);
            if ((flags & 0x000400) != 0) skip(media, trun, 4L);
            if ((flags & 0x000800) != 0) skip(media, trun, 4L);
        }
        return new TrackRunTiming(duration, terminalZeroDurationSamples,
                lastPositiveSampleDuration, hasPositiveSampleDuration);
    }

    private static boolean patchSeekIndex(
            DcamRandomAccessMedia media, long fileLength, boolean repairFragmentOffsets)
            throws IOException {
        SeekIndexReservation reservation = seekIndexReservation(media, fileLength);
        if (reservation == null) return false;
        Box moov = findTopLevel(media, fileLength, BOX_MOOV);
        List<TrackHeader> tracks = moov == null ? List.of() : findTracks(media, moov);
        if (tracks.isEmpty()) throw new IOException("Seek index track metadata is unavailable.");
        if (!repairFragmentOffsets) {
            DcamFragmentedMp4Layout.StreamedSeekIndex streamed =
                    DcamFragmentedMp4Layout.readStreamedSeekIndex(
                            media, reservation.offset(), reservation.end(), fileLength);
            if (streamed != null) {
                TrackHeader streamedTrack = null;
                for (TrackHeader track : tracks) {
                    if (track.trackId() == streamed.trackId()) {
                        streamedTrack = track;
                        break;
                    }
                }
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
            writeSegmentIndexes(media, reservation, recordedIndexes,
                    recorded.firstMoofOffset());
            return true;
        }

        List<List<FragmentReference>> referencesByTrack = new ArrayList<>();
        for (int track = 0; track < tracks.size(); track++) {
            referencesByTrack.add(new ArrayList<>());
        }
        boolean aggregateAudio = tracks.size() == 1
                && tracks.get(0).handlerType() == HANDLER_AUDIO;
        long[] lastPositiveSampleDurations = new long[tracks.size()];
        long[] pendingReferenceBytes = new long[tracks.size()];
        long[] pendingReferenceDurations = new long[tracks.size()];
        int[] pendingTimedFragments = new int[tracks.size()];
        long firstMoofOffset = -1L;
        long offset = 0L;
        while (offset + 8L <= fileLength) {
            if (offset == reservation.offset()) {
                offset = reservation.end();
                continue;
            }
            Box box = readBox(media, offset, fileLength);
            if (box == null) throw new IOException("Seek index found invalid top-level MP4 metadata.");
            if (box.type() != BOX_MOOF) {
                offset = box.end();
                continue;
            }
            Box mdat = readBox(media, box.end(), fileLength);
            if (mdat == null || mdat.type() != BOX_MDAT) {
                throw new IOException("Seek index found a media fragment without sample data.");
            }
            long fragmentEnd = gpsRouteBoxEnd(media, mdat.end(), fileLength);
            long referenceSize = fragmentEnd - offset;
            if (referenceSize <= 0L || referenceSize > 0x7fff_ffffL) {
                throw new IOException("Seek index fragment metadata is outside MP4 limits.");
            }
            if (repairFragmentOffsets) patchFragmentBaseOffsets(media, box, offset);
            for (int track = 0; track < tracks.size(); track++) {
                FragmentTiming timing = fragmentTiming(media, box, tracks.get(track).trackId(),
                        lastPositiveSampleDurations[track], true);
                if (timing == null || timing.duration() > UNSIGNED_INT_MAX) {
                    throw new IOException("Seek index fragment metadata is outside MP4 limits.");
                }
                pendingReferenceBytes[track] = addReferenceBytes(
                        pendingReferenceBytes[track], referenceSize);
                if (timing.duration() > 0L) {
                    if (aggregateAudio) {
                        if (timing.duration()
                                > UNSIGNED_INT_MAX - pendingReferenceDurations[track]) {
                            throw new IOException(
                                    "Seek index aggregate duration is outside MP4 limits.");
                        }
                        pendingReferenceDurations[track] += timing.duration();
                        pendingTimedFragments[track]++;
                        if (pendingTimedFragments[track]
                                >= DcamFragmentedMp4Layout.AUDIO_FRAGMENTS_PER_INDEX_REFERENCE) {
                            referencesByTrack.get(track).add(new FragmentReference(
                                    pendingReferenceBytes[track],
                                    pendingReferenceDurations[track]));
                            pendingReferenceBytes[track] = 0L;
                            pendingReferenceDurations[track] = 0L;
                            pendingTimedFragments[track] = 0;
                        }
                    } else {
                        referencesByTrack.get(track).add(new FragmentReference(
                                pendingReferenceBytes[track], timing.duration()));
                        pendingReferenceBytes[track] = 0L;
                    }
                }
                lastPositiveSampleDurations[track] = timing.lastPositiveSampleDuration();
            }
            if (firstMoofOffset < 0L) firstMoofOffset = offset;
            offset = fragmentEnd;
        }

        List<SegmentIndex> indexes = new ArrayList<>();
        for (int track = 0; track < tracks.size(); track++) {
            List<FragmentReference> references = referencesByTrack.get(track);
            if (aggregateAudio && pendingReferenceDurations[track] > 0L) {
                references.add(new FragmentReference(
                        pendingReferenceBytes[track], pendingReferenceDurations[track]));
                pendingReferenceBytes[track] = 0L;
            }
            if (references.isEmpty()) continue;
            appendTrailingReferenceBytes(references, pendingReferenceBytes[track]);
            if (references.size() > 0xffff) {
                throw new IOException("Seek index fragment count is outside MP4 limits.");
            }
            indexes.add(new SegmentIndex(tracks.get(track), references));
        }
        if (indexes.isEmpty()) throw new IOException("Seek index has no timed media tracks.");
        requireIndexFits(reservation, indexes);
        writeSegmentIndexes(media, reservation, indexes, firstMoofOffset);
        return false;
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

    private record FragmentTiming(long duration, long lastPositiveSampleDuration) {}

    private record TrackFragmentTiming(long duration, long terminalZeroDurationSamples,
            long lastPositiveSampleDuration, boolean hasPositiveSampleDuration) {}

    private record TrackRunTiming(long duration, long terminalZeroDurationSamples,
            long lastPositiveSampleDuration, boolean hasPositiveSampleDuration) {}

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