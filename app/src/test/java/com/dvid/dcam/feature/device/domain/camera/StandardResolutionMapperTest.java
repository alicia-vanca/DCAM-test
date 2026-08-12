package com.dvid.dcam.feature.device.domain.camera;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class StandardResolutionMapperTest {
    @Test void closestMeanDeltaWinsRegardlessOfInputOrder() {
        List<CameraResolution> candidates = List.of(
                resolution(1920, 1096),
                resolution(1920, 1064),
                resolution(1920, 1088));

        List<StandardResolution> first = StandardResolutionMapper.map(candidates);
        List<CameraResolution> reversed = new ArrayList<>(candidates);
        java.util.Collections.reverse(reversed);

        assertEquals(resolution(1920, 1088), byLabel(first).get(StandardResolutionLabel.FHD));
        assertEquals(first, StandardResolutionMapper.map(reversed));
    }

    @Test void rankedMatchesKeepExactBeforeAlignedFallback() {
        assertEquals(List.of(
                new StandardResolution(StandardResolutionLabel.FHD,
                        resolution(1920, 1080)),
                new StandardResolution(StandardResolutionLabel.FHD,
                        resolution(1920, 1088))),
                StandardResolutionMapper.rankedMatches(StandardResolutionLabel.FHD,
                        List.of(resolution(1920, 1088), resolution(1920, 1080))));
    }

    @Test void sdScoresBothCanonicalTargets() {
        assertEquals(resolution(640, 480), byLabel(StandardResolutionMapper.map(
                List.of(resolution(640, 480)))).get(StandardResolutionLabel.SD));

        Map<StandardResolutionLabel, CameraResolution> both = byLabel(
                StandardResolutionMapper.map(List.of(
                        resolution(640, 480), resolution(720, 480))));
        assertEquals(resolution(720, 480), both.get(StandardResolutionLabel.SD));
        assertFalse(both.containsKey(StandardResolutionLabel.MAX));
    }

    @Test void rawMaximumIsNotDuplicatedWhenStandardModeUsesSameActualSize() {
        Map<StandardResolutionLabel, CameraResolution> canonical = byLabel(
                StandardResolutionMapper.map(List.of(resolution(3840, 2160))));

        assertEquals(resolution(3840, 2160), canonical.get(StandardResolutionLabel.UHD));
        assertFalse(canonical.containsKey(StandardResolutionLabel.MAX));

        Map<StandardResolutionLabel, CameraResolution> customMaximum = byLabel(
                StandardResolutionMapper.map(List.of(
                        resolution(3840, 2160), resolution(4000, 3000))));
        assertEquals(resolution(4000, 3000),
                customMaximum.get(StandardResolutionLabel.MAX));
    }

    @Test void maximumMustBeStrictlyBetterThanHighestStandardActual() {
        Map<StandardResolutionLabel, CameraResolution> lowerLeftover = byLabel(
                StandardResolutionMapper.map(List.of(
                        resolution(2560, 1440), resolution(1920, 1440))));
        assertEquals(resolution(2560, 1440),
                lowerLeftover.get(StandardResolutionLabel.QHD));
        assertFalse(lowerLeftover.containsKey(StandardResolutionLabel.MAX));

        Map<StandardResolutionLabel, CameraResolution> higherLeftover = byLabel(
                StandardResolutionMapper.map(List.of(
                        resolution(2560, 1440), resolution(3000, 2000))));
        assertEquals(resolution(3000, 2000),
                higherLeftover.get(StandardResolutionLabel.MAX));
    }

    @Test void deterministicTieBreakersPreferLowerMaximumDeltaThenWidth() {
        Map<StandardResolutionLabel, CameraResolution> maximumDelta = byLabel(
                StandardResolutionMapper.map(List.of(
                        resolution(1276, 732),
                        resolution(1288, 728))));
        assertEquals(resolution(1288, 728), maximumDelta.get(StandardResolutionLabel.HD));

        Map<StandardResolutionLabel, CameraResolution> finalTie = byLabel(
                StandardResolutionMapper.map(List.of(
                        resolution(1264, 729),
                        resolution(1296, 711))));
        assertEquals(resolution(1296, 711), finalTie.get(StandardResolutionLabel.HD));
    }

    private static Map<StandardResolutionLabel, CameraResolution> byLabel(
            List<StandardResolution> resolutions) {
        Map<StandardResolutionLabel, CameraResolution> result =
                new EnumMap<>(StandardResolutionLabel.class);
        for (StandardResolution resolution : resolutions) {
            result.put(resolution.label(), resolution.actual());
        }
        return result;
    }

    private static CameraResolution resolution(int width, int height) {
        return new CameraResolution(width, height);
    }
}