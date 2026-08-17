package com.dvid.dcam.platform.storage;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.util.ArrayList;
import java.util.List;

public final class DcamFragmentedMp4Layout {
    static final int BOX_FREE = 0x66726565;
    private static final int BOX_MDAT = 0x6d646174;
    private static final int BOX_MOOF = 0x6d6f6f66;
    static final int BOX_UUID = 0x75756964;
    private static final int BOX_TFHD = 0x74666864;
    private static final int BOX_TRAF = 0x74726166;
    private static final int BOX_TRUN = 0x7472756e;
    private static final int BOX_HEADER_BYTES = 8;
    private static final int BASE_DATA_OFFSET_PRESENT = 0x000001;
    private static final int SAMPLE_DESCRIPTION_INDEX_PRESENT = 0x000002;
    private static final int DEFAULT_SAMPLE_DURATION_PRESENT = 0x000008;
    private static final int DATA_OFFSET_PRESENT = 0x000001;
    private static final int FIRST_SAMPLE_FLAGS_PRESENT = 0x000004;
    private static final int SAMPLE_DURATION_PRESENT = 0x000100;
    private static final int SAMPLE_SIZE_PRESENT = 0x000200;
    private static final int SAMPLE_FLAGS_PRESENT = 0x000400;
    private static final int SAMPLE_COMPOSITION_OFFSET_PRESENT = 0x000800;
    private static final int MAX_RECORDED_TRACKS = 16;
    private static final int RECORDED_INDEX_VERSION = 1;
    static final int GPS_ROUTE_BOX_BYTES = 52;
    static final long GPS_ROUTE_UUID_MOST_SIGNIFICANT_BITS = 0x929d6a784ea44f89L;
    static final long GPS_ROUTE_UUID_LEAST_SIGNIFICANT_BITS = 0xa10c7b6f2fe4265dL;
    private static final int GPS_ROUTE_VERSION = 1;
    private static final long UNSIGNED_INT_MAX = 0xffff_ffffL;
    static final int SIDX_HEADER_BYTES = 40;
    static final int SIDX_REFERENCE_BYTES = 12;
    static final int SIDX_STARTS_WITH_SAP_TYPE_1 = 0x90000000;
    static final int AUDIO_FRAGMENTS_PER_INDEX_REFERENCE = 2;
    private static final int STREAMED_INDEX_REFERENCE_BATCH = 256;
    private static final int STREAMED_INDEX_FOOTER_BYTES = 64;
    private static final int STREAMED_INDEX_VERSION = 1;
    // ponytail: Flat Android sidx supports about 18 hours at 500 ms fragments; increase
    // audio aggregation when longer sessions become a requirement.
    public static final int SEEK_INDEX_RESERVE_BYTES = 1024 * 1024;
    static final long SEEK_INDEX_MAGIC = 0x4443414d53494458L;
    private static final long STREAMED_INDEX_MAGIC = 0x4443414d41494458L;
    private static final long RECORDED_INDEX_MAGIC = 0x4443414d52494458L;

    private DcamFragmentedMp4Layout() {}

    public static OutputChannel offsetAwareChannel(FileChannel channel) {
        return new OutputChannel(channel, null, false);
    }

    public static OutputChannel offsetAwareChannel(DcamRecordingOutput output) {
        return new OutputChannel(output, output, false);
    }

    static OutputChannel offsetAwareAudioChannel(FileChannel channel) {
        return new OutputChannel(channel, null, true);
    }

    static OutputChannel offsetAwareAudioChannel(DcamRecordingOutput output) {
        return new OutputChannel(output, output, true);
    }

    public static void reserveSeekIndex(FileChannel channel) throws IOException {
        reserveSeekIndex((SeekableByteChannel) channel);
    }

    private static void reserveSeekIndex(SeekableByteChannel channel) throws IOException {
        long offset = channel.position();
        long end = Math.addExact(offset, SEEK_INDEX_RESERVE_BYTES);
        ByteBuffer header = ByteBuffer.allocate(16);
        header.putInt(SEEK_INDEX_RESERVE_BYTES);
        header.putInt(BOX_FREE);
        header.putLong(SEEK_INDEX_MAGIC);
        header.flip();
        writeFully(channel, header);
        channel.position(end - Long.BYTES);
        ByteBuffer marker = ByteBuffer.allocate(Long.BYTES);
        marker.putLong(SEEK_INDEX_MAGIC);
        marker.flip();
        writeFully(channel, marker);
        channel.position(end);
    }

    static StreamedSeekIndex readStreamedSeekIndex(
            DcamRandomAccessMedia media, long reservationOffset, long reservationEnd,
            long fileLength) throws IOException {
        long footerOffset = reservationEnd - STREAMED_INDEX_FOOTER_BYTES;
        if (footerOffset < reservationOffset + SIDX_HEADER_BYTES + SIDX_REFERENCE_BYTES) {
            return null;
        }
        media.seek(footerOffset);
        if (media.readLong() != STREAMED_INDEX_MAGIC) return null;
        int version = media.readInt();
        int referenceCount = media.readInt();
        long trackId = Integer.toUnsignedLong(media.readInt());
        int reserved = media.readInt();
        long recordedFileLength = media.readLong();
        long firstMoofOffset = media.readLong();
        long referencedBytes = media.readLong();
        long trailingMagic = media.readLong();
        long reservationMagic = media.readLong();
        long indexBytes = SIDX_HEADER_BYTES
                + (long) referenceCount * SIDX_REFERENCE_BYTES;
        if (version != STREAMED_INDEX_VERSION || reserved != 0
                || referenceCount <= 0 || referenceCount > 0xffff || trackId <= 0L
                || recordedFileLength != fileLength || firstMoofOffset != reservationEnd
                || referencedBytes != fileLength - firstMoofOffset
                || indexBytes > footerOffset - reservationOffset
                || trailingMagic != STREAMED_INDEX_MAGIC
                || reservationMagic != SEEK_INDEX_MAGIC) {
            return null;
        }
        return new StreamedSeekIndex(trackId, referenceCount, firstMoofOffset);
    }

    static RecordedSeekIndex readRecordedSeekIndex(
            DcamRandomAccessMedia media, long reservationOffset, long reservationEnd, long fileLength)
            throws IOException {
        long payloadOffset = reservationOffset + BOX_HEADER_BYTES;
        if (recordedIndexBytes(1, 1) > reservationEnd - Long.BYTES - payloadOffset) return null;
        media.seek(payloadOffset);
        if (media.readLong() != RECORDED_INDEX_MAGIC) return null;
        int version = media.readInt();
        int trackCount = media.readInt();
        int fragmentCount = media.readInt();
        int reserved = media.readInt();
        long recordedFileLength = media.readLong();
        long firstMoofOffset = media.readLong();
        if (version != RECORDED_INDEX_VERSION || reserved != 0
                || trackCount <= 0 || trackCount > MAX_RECORDED_TRACKS
                || fragmentCount <= 0 || fragmentCount > 0xffff
                || recordedFileLength != fileLength || firstMoofOffset != reservationEnd) {
            return null;
        }
        long recordedBytes = recordedIndexBytes(trackCount, fragmentCount);
        if (recordedBytes > reservationEnd - Long.BYTES - payloadOffset) return null;

        List<Long> trackIds = new ArrayList<>(trackCount);
        for (int track = 0; track < trackCount; track++) {
            long trackId = Integer.toUnsignedLong(media.readInt());
            if (trackId <= 0L || trackIds.contains(trackId)) return null;
            trackIds.add(trackId);
        }
        List<RecordedFragment> fragments = new ArrayList<>(fragmentCount);
        long totalReferenceBytes = 0L;
        for (int fragment = 0; fragment < fragmentCount; fragment++) {
            long size = Integer.toUnsignedLong(media.readInt());
            if (size <= 0L || size > 0x7fff_ffffL) return null;
            if (size > Long.MAX_VALUE - totalReferenceBytes) return null;
            totalReferenceBytes += size;
            List<Long> durations = new ArrayList<>(trackCount);
            for (int track = 0; track < trackCount; track++) {
                durations.add(Integer.toUnsignedLong(media.readInt()));
            }
            fragments.add(new RecordedFragment(size, List.copyOf(durations)));
        }
        if (media.readLong() != RECORDED_INDEX_MAGIC
                || firstMoofOffset > Long.MAX_VALUE - totalReferenceBytes
                || firstMoofOffset + totalReferenceBytes != fileLength) {
            return null;
        }
        return new RecordedSeekIndex(firstMoofOffset, List.copyOf(trackIds),
                List.copyOf(fragments));
    }

    private static long recordedIndexBytes(int trackCount, int fragmentCount) {
        return 48L + Integer.BYTES * (long) trackCount
                + Integer.BYTES * (long) fragmentCount * (1L + trackCount);
    }

    private static boolean isBox(ByteBuffer source, int expectedType, boolean requireComplete) {
        if (source.remaining() < BOX_HEADER_BYTES) return false;
        ByteBuffer box = source.duplicate().order(ByteOrder.BIG_ENDIAN);
        int offset = box.position();
        long size = Integer.toUnsignedLong(box.getInt(offset));
        return size >= BOX_HEADER_BYTES
                && (!requireComplete || size == box.remaining())
                && box.getInt(offset + Integer.BYTES) == expectedType;
    }

    private static List<RawTrackTiming> patchFragmentBaseOffsets(
            ByteBuffer moof, long moofOffset) throws IOException {
        int end = boxEnd(moof, 0, moof.limit());
        if (end != moof.limit() || moof.getInt(Integer.BYTES) != BOX_MOOF) {
            throw new IOException("Media3 fragment metadata is invalid.");
        }
        List<RawTrackTiming> tracks = new ArrayList<>();
        int offset = BOX_HEADER_BYTES;
        while (offset < end) {
            int childEnd = boxEnd(moof, offset, end);
            if (moof.getInt(offset + Integer.BYTES) == BOX_TRAF) {
                tracks.add(patchTrackFragmentBaseOffset(moof, offset, childEnd, moofOffset));
            }
            offset = childEnd;
        }
        if (tracks.isEmpty()) throw new IOException("Media3 fragment has no timed tracks.");
        return tracks;
    }

    private static RawTrackTiming patchTrackFragmentBaseOffset(
            ByteBuffer moof, int trafOffset, int trafEnd, long moofOffset) throws IOException {
        long trackId = -1L;
        long defaultSampleDuration = 0L;
        long duration = 0L;
        long terminalZeroDurationSamples = 0L;
        long lastPositiveSampleDuration = 0L;
        boolean hasPositiveSampleDuration = false;
        int offset = trafOffset + BOX_HEADER_BYTES;
        while (offset < trafEnd) {
            int childEnd = boxEnd(moof, offset, trafEnd);
            int type = moof.getInt(offset + Integer.BYTES);
            if (type == BOX_TFHD) {
                int contentOffset = offset + BOX_HEADER_BYTES;
                requireBytes(contentOffset, 2 * Integer.BYTES, childEnd,
                        "Media3 track fragment metadata is truncated.");
                int flags = moof.getInt(contentOffset) & 0x00ff_ffff;
                trackId = Integer.toUnsignedLong(moof.getInt(contentOffset + Integer.BYTES));
                int fieldOffset = contentOffset + 2 * Integer.BYTES;
                if ((flags & BASE_DATA_OFFSET_PRESENT) != 0) {
                    requireBytes(fieldOffset, Long.BYTES, childEnd,
                            "Media3 track fragment base offset is truncated.");
                    moof.putLong(fieldOffset, moofOffset);
                    fieldOffset += Long.BYTES;
                }
                if ((flags & SAMPLE_DESCRIPTION_INDEX_PRESENT) != 0) {
                    fieldOffset = skip(fieldOffset, Integer.BYTES, childEnd);
                }
                if ((flags & DEFAULT_SAMPLE_DURATION_PRESENT) != 0) {
                    requireBytes(fieldOffset, Integer.BYTES, childEnd,
                            "Media3 default sample duration is truncated.");
                    defaultSampleDuration = Integer.toUnsignedLong(moof.getInt(fieldOffset));
                }
            } else if (type == BOX_TRUN) {
                RawTrackTiming timing = readTrackRunTiming(
                        moof, offset, childEnd, defaultSampleDuration);
                if (timing.duration() > Long.MAX_VALUE - duration) {
                    throw new IOException("Media3 fragment duration exceeds MP4 limits.");
                }
                duration += timing.duration();
                if (timing.hasPositiveSampleDuration()) {
                    terminalZeroDurationSamples = timing.terminalZeroDurationSamples();
                    hasPositiveSampleDuration = true;
                } else if (timing.terminalZeroDurationSamples()
                        > Long.MAX_VALUE - terminalZeroDurationSamples) {
                    throw new IOException("Media3 fragment sample count exceeds MP4 limits.");
                } else {
                    terminalZeroDurationSamples += timing.terminalZeroDurationSamples();
                }
                if (timing.lastPositiveSampleDuration() > 0L) {
                    lastPositiveSampleDuration = timing.lastPositiveSampleDuration();
                }
            }
            offset = childEnd;
        }
        if (trackId <= 0L) throw new IOException("Media3 fragment track ID is unavailable.");
        return new RawTrackTiming(trackId, duration, terminalZeroDurationSamples,
                lastPositiveSampleDuration, hasPositiveSampleDuration);
    }

    private static RawTrackTiming readTrackRunTiming(
            ByteBuffer moof, int trunOffset, int trunEnd, long defaultSampleDuration)
            throws IOException {
        int offset = trunOffset + BOX_HEADER_BYTES;
        requireBytes(offset, 2 * Integer.BYTES, trunEnd,
                "Media3 track run metadata is truncated.");
        int flags = moof.getInt(offset) & 0x00ff_ffff;
        long sampleCount = Integer.toUnsignedLong(moof.getInt(offset + Integer.BYTES));
        offset += 2 * Integer.BYTES;
        if ((flags & DATA_OFFSET_PRESENT) != 0) offset = skip(offset, Integer.BYTES, trunEnd);
        if ((flags & FIRST_SAMPLE_FLAGS_PRESENT) != 0) {
            offset = skip(offset, Integer.BYTES, trunEnd);
        }
        if (sampleCount == 0L) return new RawTrackTiming(0L, 0L, 0L, 0L, false);
        if ((flags & SAMPLE_DURATION_PRESENT) == 0) {
            if (defaultSampleDuration <= 0L) {
                throw new IOException("Media3 track run omits sample durations.");
            }
            try {
                return new RawTrackTiming(0L,
                        Math.multiplyExact(sampleCount, defaultSampleDuration),
                        0L, defaultSampleDuration, true);
            } catch (ArithmeticException overflow) {
                throw new IOException("Media3 track run duration exceeds MP4 limits.", overflow);
            }
        }

        long duration = 0L;
        long terminalZeroDurationSamples = 0L;
        long lastPositiveSampleDuration = 0L;
        boolean hasPositiveSampleDuration = false;
        for (long sample = 0L; sample < sampleCount; sample++) {
            requireBytes(offset, Integer.BYTES, trunEnd,
                    "Media3 track run sample duration is truncated.");
            long sampleDuration = Integer.toUnsignedLong(moof.getInt(offset));
            offset += Integer.BYTES;
            if (sampleDuration == 0L) {
                terminalZeroDurationSamples++;
            } else {
                if (sampleDuration > Long.MAX_VALUE - duration) {
                    throw new IOException("Media3 track run duration exceeds MP4 limits.");
                }
                duration += sampleDuration;
                terminalZeroDurationSamples = 0L;
                lastPositiveSampleDuration = sampleDuration;
                hasPositiveSampleDuration = true;
            }
            if ((flags & SAMPLE_SIZE_PRESENT) != 0) {
                offset = skip(offset, Integer.BYTES, trunEnd);
            }
            if ((flags & SAMPLE_FLAGS_PRESENT) != 0) {
                offset = skip(offset, Integer.BYTES, trunEnd);
            }
            if ((flags & SAMPLE_COMPOSITION_OFFSET_PRESENT) != 0) {
                offset = skip(offset, Integer.BYTES, trunEnd);
            }
        }
        return new RawTrackTiming(0L, duration, terminalZeroDurationSamples,
                lastPositiveSampleDuration, hasPositiveSampleDuration);
    }

    private static int skip(int offset, int bytes, int end) throws IOException {
        requireBytes(offset, bytes, end, "Media3 fragment metadata is truncated.");
        return offset + bytes;
    }

    private static void requireBytes(int offset, int bytes, int end, String message)
            throws IOException {
        if (offset < 0 || bytes < 0 || offset > end - bytes) throw new IOException(message);
    }

    private static int boxEnd(ByteBuffer data, int offset, int limit) throws IOException {
        // ponytail: Media3 emits 32-bit moof boxes; add extended-size parsing if that changes.
        if (offset < 0 || offset > limit - BOX_HEADER_BYTES) {
            throw new IOException("Media3 fragment box header is truncated.");
        }
        long size = Integer.toUnsignedLong(data.getInt(offset));
        if (size < BOX_HEADER_BYTES || size > limit - offset) {
            throw new IOException("Media3 fragment box size is invalid.");
        }
        return offset + (int) size;
    }

    private static void writeFully(SeekableByteChannel channel, ByteBuffer data) throws IOException {
        while (data.hasRemaining()) {
            if (channel.write(data) <= 0) {
                throw new IOException("Recording output stopped accepting MP4 metadata.");
            }
        }
    }

    public static final class OutputChannel implements WritableByteChannel {
        private final SeekableByteChannel channel;
        private final DcamRecordingOutput recordingOutput;
        private final boolean streamedAudioSeekIndex;
        private final ByteBuffer streamedReferenceBuffer;
        private final List<Long> trackIds = new ArrayList<>();
        private final List<RecordedFragment> fragments = new ArrayList<>();
        private final List<byte[]> pendingGpsRouteBoxes = new ArrayList<>();
        private long[] lastPositiveSampleDurations = new long[0];
        private PendingFragment pendingFragment;
        private long reservationOffset = -1L;
        private long firstMoofOffset = -1L;
        private long pendingStreamedReferenceBytes;
        private long pendingStreamedReferenceDuration;
        private int pendingStreamedTimedFragments;
        private int streamedReferenceCount;
        private long streamedReferencedBytes;

        private OutputChannel(
                SeekableByteChannel channel, DcamRecordingOutput recordingOutput,
                boolean streamedAudioSeekIndex) {
            this.channel = channel;
            this.recordingOutput = recordingOutput;
            this.streamedAudioSeekIndex = streamedAudioSeekIndex;
            streamedReferenceBuffer = streamedAudioSeekIndex
                    ? ByteBuffer.allocate(STREAMED_INDEX_REFERENCE_BATCH * SIDX_REFERENCE_BYTES)
                            .order(ByteOrder.BIG_ENDIAN)
                    : null;
        }

        public void reserveSeekIndex() throws IOException {
            if (reservationOffset >= 0L) return;
            reservationOffset = channel.position();
            DcamFragmentedMp4Layout.reserveSeekIndex(channel);
        }

        public void queueGpsRoutePoint(
                long presentationTimeUs, double latitude, double longitude) {
            if (presentationTimeUs < 0L || !Double.isFinite(latitude)
                    || !Double.isFinite(longitude) || latitude < -90.0 || latitude > 90.0
                    || longitude < -180.0 || longitude > 180.0) {
                throw new IllegalArgumentException("Invalid GPS route point");
            }
            ByteBuffer box = ByteBuffer.allocate(GPS_ROUTE_BOX_BYTES).order(ByteOrder.BIG_ENDIAN);
            box.putInt(GPS_ROUTE_BOX_BYTES);
            box.putInt(BOX_UUID);
            box.putLong(GPS_ROUTE_UUID_MOST_SIGNIFICANT_BITS);
            box.putLong(GPS_ROUTE_UUID_LEAST_SIGNIFICANT_BITS);
            box.putInt(GPS_ROUTE_VERSION);
            box.putLong(presentationTimeUs);
            box.putDouble(latitude);
            box.putDouble(longitude);
            pendingGpsRouteBoxes.add(box.array());
        }

        @Override public int write(ByteBuffer source) throws IOException {
            int bytes = source.remaining();
            if (isBox(source, BOX_MOOF, true)) {
                writeMoof(source, bytes);
                return bytes;
            }
            if (pendingFragment != null) {
                writeFragmentData(source, bytes);
                return bytes;
            }
            writeFully(channel, source);
            return bytes;
        }

        private void writeMoof(ByteBuffer source, int bytes) throws IOException {
            if (pendingFragment != null) {
                throw new IOException("Media3 wrote a fragment before prior sample data.");
            }
            long moofOffset = channel.position();
            ByteBuffer patched = ByteBuffer.allocate(bytes).order(ByteOrder.BIG_ENDIAN);
            patched.put(source.duplicate());
            patched.flip();
            List<RawTrackTiming> rawTracks = patchFragmentBaseOffsets(patched, moofOffset);
            List<Long> durations = resolveDurations(rawTracks);
            if (firstMoofOffset < 0L) {
                if (reservationOffset < 0L
                        || moofOffset != reservationOffset + SEEK_INDEX_RESERVE_BYTES) {
                    throw new IOException("Fragmented MP4 seek index reservation is unavailable.");
                }
                firstMoofOffset = moofOffset;
            }
            pendingFragment = new PendingFragment(bytes, durations, 0L, -1L);
            writeFully(channel, patched);
            source.position(source.limit());
        }

        private List<Long> resolveDurations(List<RawTrackTiming> rawTracks) throws IOException {
            if (rawTracks.size() > MAX_RECORDED_TRACKS) {
                throw new IOException("Fragmented MP4 has too many timed tracks.");
            }
            List<Long> fragmentTrackIds = new ArrayList<>(rawTracks.size());
            for (RawTrackTiming timing : rawTracks) {
                if (fragmentTrackIds.contains(timing.trackId())) {
                    throw new IOException("Fragmented MP4 repeats a track within one fragment.");
                }
                fragmentTrackIds.add(timing.trackId());
                if (!trackIds.contains(timing.trackId())) addRecordedTrack(timing.trackId());
            }

            List<Long> durations = new ArrayList<>(trackIds.size());
            for (int track = 0; track < trackIds.size(); track++) {
                RawTrackTiming timing = null;
                long trackId = trackIds.get(track);
                for (RawTrackTiming candidate : rawTracks) {
                    if (candidate.trackId() == trackId) {
                        timing = candidate;
                        break;
                    }
                }
                if (timing == null) {
                    durations.add(0L);
                    continue;
                }
                ResolvedDuration resolved = resolveDuration(timing.duration(),
                        timing.terminalZeroDurationSamples(),
                        timing.lastPositiveSampleDuration(),
                        lastPositiveSampleDurations[track]);
                if (resolved == null) {
                    throw new IOException("Fragmented MP4 duration is outside MP4 limits.");
                }
                lastPositiveSampleDurations[track] = resolved.lastPositiveSampleDuration();
                durations.add(resolved.duration());
            }
            return List.copyOf(durations);
        }

        private void addRecordedTrack(long trackId) throws IOException {
            if (streamedAudioSeekIndex && !trackIds.isEmpty()) {
                throw new IOException("Audio fragmented MP4 has multiple timed tracks.");
            }
            if (trackIds.size() >= MAX_RECORDED_TRACKS) {
                throw new IOException("Fragmented MP4 has too many timed tracks.");
            }
            trackIds.add(trackId);
            long[] expandedDurations = new long[trackIds.size()];
            System.arraycopy(lastPositiveSampleDurations, 0, expandedDurations, 0,
                    lastPositiveSampleDurations.length);
            lastPositiveSampleDurations = expandedDurations;
            for (int fragment = 0; fragment < fragments.size(); fragment++) {
                RecordedFragment recorded = fragments.get(fragment);
                List<Long> durations = new ArrayList<>(recorded.durations());
                durations.add(0L);
                fragments.set(fragment,
                        new RecordedFragment(recorded.size(), List.copyOf(durations)));
            }
        }

        private void writeFragmentData(ByteBuffer source, int bytes) throws IOException {
            PendingFragment pending = pendingFragment;
            if (pending.remainingMdatBytes() < 0L) {
                if (!isBox(source, BOX_MDAT, false)) {
                    throw new IOException("Media3 fragment sample-data header is unavailable.");
                }
                ByteBuffer mdat = source.duplicate().order(ByteOrder.BIG_ENDIAN);
                long mdatBytes = Integer.toUnsignedLong(mdat.getInt(mdat.position()));
                long referenceSize = pending.moofBytes() + mdatBytes;
                if (referenceSize <= 0L || referenceSize > 0x7fff_ffffL) {
                    throw new IOException("Fragmented MP4 reference size is outside MP4 limits.");
                }
                pending = new PendingFragment(
                        pending.moofBytes(), pending.durations(), mdatBytes, mdatBytes);
            }
            if (bytes > pending.remainingMdatBytes()) {
                throw new IOException("Media3 fragment sample data exceeds its declared box size.");
            }
            writeFully(channel, source);
            long remaining = pending.remainingMdatBytes() - bytes;
            if (remaining > 0L) {
                pendingFragment = new PendingFragment(
                        pending.moofBytes(), pending.durations(), pending.mdatBytes(), remaining);
                return;
            }
            long routeBytes = (long) pendingGpsRouteBoxes.size() * GPS_ROUTE_BOX_BYTES;
            long referenceSize = pending.moofBytes() + pending.mdatBytes() + routeBytes;
            if (referenceSize <= 0L || referenceSize > 0x7fff_ffffL) {
                throw new IOException("Fragmented MP4 reference size is outside MP4 limits.");
            }
            for (byte[] routeBox : pendingGpsRouteBoxes) {
                writeFully(channel, ByteBuffer.wrap(routeBox));
            }
            pendingGpsRouteBoxes.clear();
            recordFragment(referenceSize, pending.durations());
            pendingFragment = null;
            if (recordingOutput != null) recordingOutput.checkpoint();
        }

        private void recordFragment(long referenceSize, List<Long> durations) throws IOException {
            if (!streamedAudioSeekIndex) {
                fragments.add(new RecordedFragment(referenceSize, durations));
                return;
            }
            if (durations.size() != 1) {
                throw new IOException("Audio fragmented MP4 seek index requires one timed track.");
            }
            long duration = durations.get(0);
            if (duration > 0L
                    && pendingStreamedTimedFragments >= AUDIO_FRAGMENTS_PER_INDEX_REFERENCE) {
                emitStreamedReference();
            }
            pendingStreamedReferenceBytes = addStreamedReferenceBytes(
                    pendingStreamedReferenceBytes, referenceSize);
            if (duration <= 0L) return;
            if (duration > UNSIGNED_INT_MAX - pendingStreamedReferenceDuration) {
                throw new IOException("Audio seek index duration is outside MP4 limits.");
            }
            pendingStreamedReferenceDuration += duration;
            pendingStreamedTimedFragments++;
        }

        private static long addStreamedReferenceBytes(long accumulated, long fragmentBytes)
                throws IOException {
            if (fragmentBytes <= 0L || fragmentBytes > 0x7fff_ffffL
                    || accumulated > 0x7fff_ffffL - fragmentBytes) {
                throw new IOException("Audio seek index reference size is outside MP4 limits.");
            }
            return accumulated + fragmentBytes;
        }

        private void emitStreamedReference() throws IOException {
            if (pendingStreamedReferenceBytes == 0L) return;
            if (pendingStreamedReferenceDuration <= 0L) {
                throw new IOException("Audio seek index has no timed media fragment.");
            }
            long footerOffset = reservationOffset + SEEK_INDEX_RESERVE_BYTES
                    - STREAMED_INDEX_FOOTER_BYTES;
            long entryEnd = reservationOffset + SIDX_HEADER_BYTES
                    + (long) (streamedReferenceCount + 1) * SIDX_REFERENCE_BYTES;
            if (reservationOffset < 0L || streamedReferenceCount >= 0xffff
                    || entryEnd > footerOffset) {
                throw new IOException("Audio seek index reference capacity is exhausted.");
            }
            if (streamedReferenceBuffer.remaining() < SIDX_REFERENCE_BYTES) {
                flushStreamedReferences();
            }
            streamedReferenceBuffer.putInt((int) pendingStreamedReferenceBytes);
            streamedReferenceBuffer.putInt((int) pendingStreamedReferenceDuration);
            streamedReferenceBuffer.putInt(SIDX_STARTS_WITH_SAP_TYPE_1);
            streamedReferenceCount++;
            streamedReferencedBytes += pendingStreamedReferenceBytes;
            pendingStreamedReferenceBytes = 0L;
            pendingStreamedReferenceDuration = 0L;
            pendingStreamedTimedFragments = 0;
            if (streamedReferenceBuffer.remaining() < SIDX_REFERENCE_BYTES) {
                flushStreamedReferences();
            }
        }

        private void flushStreamedReferences() throws IOException {
            int bytes = streamedReferenceBuffer.position();
            if (bytes == 0) return;
            int bufferedReferences = bytes / SIDX_REFERENCE_BYTES;
            long firstBufferedReference = streamedReferenceCount - bufferedReferences;
            long offset = reservationOffset + SIDX_HEADER_BYTES
                    + firstBufferedReference * SIDX_REFERENCE_BYTES;
            streamedReferenceBuffer.flip();
            writeReservationData(offset, streamedReferenceBuffer);
            streamedReferenceBuffer.clear();
        }

        private void writeStreamedSeekIndex() throws IOException {
            emitStreamedReference();
            flushStreamedReferences();
            if (streamedReferenceCount == 0) return;
            long fileLength = channel.position();
            long reservationEnd = reservationOffset + SEEK_INDEX_RESERVE_BYTES;
            if (trackIds.size() != 1 || firstMoofOffset != reservationEnd
                    || streamedReferencedBytes != fileLength - firstMoofOffset) {
                throw new IOException("Audio streamed seek index metadata is inconsistent.");
            }
            ByteBuffer footer = ByteBuffer.allocate(STREAMED_INDEX_FOOTER_BYTES)
                    .order(ByteOrder.BIG_ENDIAN);
            footer.putLong(STREAMED_INDEX_MAGIC);
            footer.putInt(STREAMED_INDEX_VERSION);
            footer.putInt(streamedReferenceCount);
            footer.putInt((int) (long) trackIds.get(0));
            footer.putInt(0);
            footer.putLong(fileLength);
            footer.putLong(firstMoofOffset);
            footer.putLong(streamedReferencedBytes);
            footer.putLong(STREAMED_INDEX_MAGIC);
            footer.putLong(SEEK_INDEX_MAGIC);
            footer.flip();
            writeReservationData(reservationEnd - STREAMED_INDEX_FOOTER_BYTES, footer);
        }

        private void writeReservationData(long offset, ByteBuffer data) throws IOException {
            long appendPosition = channel.position();
            boolean transactionStarted = false;
            try {
                if (recordingOutput != null) {
                    recordingOutput.beginTransaction();
                    transactionStarted = true;
                }
                channel.position(offset);
                writeFully(channel, data);
                channel.position(appendPosition);
                if (transactionStarted) {
                    recordingOutput.commitTransaction();
                    transactionStarted = false;
                }
            } catch (IOException failure) {
                if (transactionStarted) {
                    try {
                        recordingOutput.rollbackTransaction();
                    } catch (IOException rollbackFailure) {
                        failure.addSuppressed(rollbackFailure);
                    }
                }
                try {
                    channel.position(Math.min(appendPosition, channel.size()));
                } catch (IOException positionFailure) {
                    failure.addSuppressed(positionFailure);
                }
                throw failure;
            }
        }

        private void writeRecordedSeekIndex() throws IOException {
            if (pendingFragment != null) {
                throw new IOException("Fragmented MP4 ended before fragment sample data.");
            }
            if (!pendingGpsRouteBoxes.isEmpty()) {
                throw new IOException("Fragmented MP4 ended before queued GPS route metadata.");
            }
            if (streamedAudioSeekIndex) {
                writeStreamedSeekIndex();
                return;
            }
            if (fragments.isEmpty()) return;
            long fileLength = channel.position();
            int trackCount = trackIds.size();
            int fragmentCount = fragments.size();
            long recordedBytes = recordedIndexBytes(trackCount, fragmentCount);
            long payloadOffset = reservationOffset + BOX_HEADER_BYTES;
            long reservationEnd = reservationOffset + SEEK_INDEX_RESERVE_BYTES;
            if (reservationOffset < 0L || firstMoofOffset != reservationEnd
                    || recordedBytes > reservationEnd - Long.BYTES - payloadOffset
                    || recordedBytes > Integer.MAX_VALUE) {
                throw new IOException("Fragmented MP4 recorded seek index is too large.");
            }
            ByteBuffer index = ByteBuffer.allocate((int) recordedBytes).order(ByteOrder.BIG_ENDIAN);
            index.putLong(RECORDED_INDEX_MAGIC);
            index.putInt(RECORDED_INDEX_VERSION);
            index.putInt(trackCount);
            index.putInt(fragmentCount);
            index.putInt(0);
            index.putLong(fileLength);
            index.putLong(firstMoofOffset);
            for (long trackId : trackIds) index.putInt((int) trackId);
            for (RecordedFragment fragment : fragments) {
                index.putInt((int) fragment.size());
                for (long duration : fragment.durations()) index.putInt((int) duration);
            }
            index.putLong(RECORDED_INDEX_MAGIC);
            index.flip();
            boolean transactionStarted = false;
            try {
                if (recordingOutput != null) {
                    recordingOutput.beginTransaction();
                    transactionStarted = true;
                }
                channel.position(payloadOffset);
                writeFully(channel, index);
                channel.position(fileLength);
                if (transactionStarted) recordingOutput.commitTransaction();
            } catch (IOException failure) {
                if (transactionStarted) {
                    try {
                        recordingOutput.rollbackTransaction();
                    } catch (IOException rollbackFailure) {
                        failure.addSuppressed(rollbackFailure);
                    }
                }
                throw failure;
            }
        }

        @Override public boolean isOpen() {
            return channel.isOpen();
        }

        @Override public void close() throws IOException {
            IOException failure = null;
            try {
                writeRecordedSeekIndex();
            } catch (IOException error) {
                failure = error;
            }
            if (recordingOutput == null) {
                try {
                    channel.close();
                } catch (IOException error) {
                    if (failure == null) failure = error;
                    else failure.addSuppressed(error);
                }
            }
            if (failure != null) throw failure;
        }
    }

    static ResolvedDuration resolveDuration(long duration, long terminalZeroDurationSamples,
            long lastPositiveSampleDuration, long fallbackSampleDuration) {
        long terminalSampleDuration = lastPositiveSampleDuration > 0L
                ? lastPositiveSampleDuration : fallbackSampleDuration;
        if (terminalZeroDurationSamples > 0L) {
            if (terminalSampleDuration <= 0L) return null;
            try {
                duration = Math.addExact(duration,
                        Math.multiplyExact(terminalZeroDurationSamples, terminalSampleDuration));
            } catch (ArithmeticException overflow) {
                return null;
            }
        }
        return duration <= 0L || duration > UNSIGNED_INT_MAX
                ? null : new ResolvedDuration(duration, terminalSampleDuration);
    }

    static record StreamedSeekIndex(long trackId, int referenceCount, long firstMoofOffset) {}

    static record RecordedSeekIndex(
            long firstMoofOffset, List<Long> trackIds, List<RecordedFragment> fragments) {}

    static record RecordedFragment(long size, List<Long> durations) {}

    static record ResolvedDuration(long duration, long lastPositiveSampleDuration) {}

    private record PendingFragment(
            long moofBytes, List<Long> durations, long mdatBytes, long remainingMdatBytes) {}

    private record RawTrackTiming(long trackId, long duration, long terminalZeroDurationSamples,
            long lastPositiveSampleDuration, boolean hasPositiveSampleDuration) {}
}