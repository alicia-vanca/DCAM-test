package com.dvid.dcam.platform.storage;

import androidx.annotation.OptIn;
import androidx.media3.common.Format;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.container.Mp4LocationData;
import androidx.media3.muxer.BufferInfo;
import androidx.media3.muxer.FragmentedMp4Muxer;
import androidx.media3.muxer.MuxerException;
import com.dvid.dcam.core.logging.application.port.Logger;
import com.dvid.dcam.feature.location.domain.GpsCoordinate;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.function.Supplier;

@OptIn(markerClass = UnstableApi.class)
public final class DcamAudioM4aWriter implements AutoCloseable {
    private static final long FRAGMENT_DURATION_MILLIS = 500L;

    private final DcamFragmentedMp4Layout.OutputChannel outputChannel;
    private final Supplier<GpsCoordinate> captureLocation;
    private final Logger logger;
    private FragmentedMp4Muxer muxer;
    private int trackId = -1;
    private boolean seekIndexReserved;
    private boolean gpsRouteEnabled = true;
    private boolean gpsLookupFailureLogged;
    private GpsCoordinate lastGpsCoordinate;
    private long sampleCount;
    private boolean closed;

    public DcamAudioM4aWriter(
            DcamRecordingOutput output,
            Supplier<GpsCoordinate> captureLocation,
            Logger logger) {
        outputChannel = DcamFragmentedMp4Layout.offsetAwareAudioChannel(
                Objects.requireNonNull(output, "output"));
        this.captureLocation = Objects.requireNonNull(captureLocation, "captureLocation");
        this.logger = Objects.requireNonNull(logger, "logger");
        muxer = new FragmentedMp4Muxer.Builder(outputChannel)
                .setFragmentDurationMs(FRAGMENT_DURATION_MILLIS)
                .build();
    }

    public synchronized void start(Format audioFormat) throws IOException {
        ensureOpen();
        if (trackId >= 0) throw new IOException("audio_m4a_track_already_started");
        GpsCoordinate location = currentCaptureLocation();
        if (location != null) {
            try {
                muxer.addMetadataEntry(new Mp4LocationData(
                        (float) location.getLatitude(), (float) location.getLongitude()));
            } catch (RuntimeException error) {
                logger.warn("Start audio recording without MP4 location snapshot because the "
                        + "M4A muxer rejected current location.", error);
            }
        }
        try {
            trackId = muxer.addTrack(Objects.requireNonNull(audioFormat, "audioFormat"));
        } catch (RuntimeException error) {
            throw new IOException("audio_m4a_track_start_failed", error);
        }
    }

    public synchronized void writeSample(ByteBuffer sample, BufferInfo sampleInfo)
            throws IOException {
        ensureOpen();
        if (trackId < 0) throw new IOException("audio_m4a_track_not_started");
        Objects.requireNonNull(sample, "sample");
        Objects.requireNonNull(sampleInfo, "sampleInfo");
        try {
            muxer.writeSampleData(trackId, sample, sampleInfo);
        } catch (MuxerException | RuntimeException error) {
            throw new IOException("audio_m4a_sample_write_failed", error);
        }
        writeGpsRoutePoint(sampleInfo.presentationTimeUs);
        if (!seekIndexReserved) {
            outputChannel.reserveSeekIndex();
            seekIndexReserved = true;
        }
        sampleCount++;
    }

    public synchronized long sampleCount() {
        return sampleCount;
    }

    private void writeGpsRoutePoint(long presentationTimeUs) {
        if (!gpsRouteEnabled) return;
        GpsCoordinate coordinate = currentCaptureLocation();
        if (!gpsCoordinateChanged(lastGpsCoordinate, coordinate)) return;
        try {
            outputChannel.queueGpsRoutePoint(presentationTimeUs,
                    coordinate.getLatitude(), coordinate.getLongitude());
            lastGpsCoordinate = coordinate;
        } catch (RuntimeException error) {
            gpsRouteEnabled = false;
            logger.warn("Stop embedding GPS route metadata because the M4A output rejected a "
                    + "route point. Audio recording continues.", error);
        }
    }

    private GpsCoordinate currentCaptureLocation() {
        try {
            return captureLocation.get();
        } catch (RuntimeException error) {
            if (!gpsLookupFailureLogged) {
                gpsLookupFailureLogged = true;
                logger.warn("Audio GPS route lookup failed. Recording continues without new GPS "
                        + "metadata until location becomes available.", error);
            }
            return null;
        }
    }

    private static boolean gpsCoordinateChanged(
            GpsCoordinate previous, GpsCoordinate current) {
        if (current == null) return false;
        return previous == null
                || Double.compare(previous.getLatitude(), current.getLatitude()) != 0
                || Double.compare(previous.getLongitude(), current.getLongitude()) != 0;
    }

    private void ensureOpen() throws IOException {
        if (closed || muxer == null) throw new IOException("audio_m4a_writer_closed");
    }

    @Override public synchronized void close() throws IOException {
        if (closed) return;
        closed = true;
        FragmentedMp4Muxer current = muxer;
        muxer = null;
        if (current == null || trackId < 0) return;
        try {
            current.close();
        } catch (MuxerException | RuntimeException error) {
            throw new IOException("audio_m4a_close_failed", error);
        }
    }
}