package com.dvid.dcam.app;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class PipelineStringResourceTest {
    @Test void tupleCountUsesTypedAndroidFormatPlaceholder() throws IOException {
        assertTrue(resource("values/strings.xml").contains(
                "<string name=\"camera_pipeline_tuple_count\">%1$d capture tuples</string>"));
        assertTrue(resource("values-vi/strings.xml").contains(
                "<string name=\"camera_pipeline_tuple_count\">%1$d capture tuple</string>"));
    }

    @Test void fastBuildDetailUsesTypedTupleCount() throws IOException {
        assertTrue(resource("values/strings.xml").contains(
                "<string name=\"camera_pipeline_fast_build_detail\">"
                        + "Fast build: %1$d tuples</string>"));
        assertTrue(resource("values-vi/strings.xml").contains(
                "<string name=\"camera_pipeline_fast_build_detail\">"
                        + "Bản dựng nhanh: %1$d tuple</string>"));
    }

    @Test void verifiedSummaryIncludesTupleCountAndElapsedSeconds() throws IOException {
        assertTrue(resource("values/strings.xml").contains(
                "<string name=\"camera_pipeline_verified_summary\">"
                        + "%1$d verified tuples in %2$.1f s</string>"));
        assertTrue(resource("values-vi/strings.xml").contains(
                "<string name=\"camera_pipeline_verified_summary\">"
                        + "%1$d tuple đã xác minh trong %2$.1f giây</string>"));
    }

    private static String resource(String suffix) throws IOException {
        Path app = Path.of("app/src/main/res");
        Path root = Files.exists(app) ? app : Path.of("src/main/res");
        return Files.readString(root.resolve(suffix), StandardCharsets.UTF_8);
    }
}