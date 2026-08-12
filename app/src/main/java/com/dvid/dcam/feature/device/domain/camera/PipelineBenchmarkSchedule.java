package com.dvid.dcam.feature.device.domain.camera;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class PipelineBenchmarkSchedule {
    private PipelineBenchmarkSchedule() {}

    public static List<Invocation> abba(CameraPipelineBenchmarkPlan.BenchmarkProtocol protocol) {
        Objects.requireNonNull(protocol, "protocol");
        List<Invocation> result = new ArrayList<>();
        CameraPipelineBenchmarkPlan.InitialOrder first = protocol.initialOrder();
        CameraPipelineBenchmarkPlan.InitialOrder second = opposite(first);
        result.add(new Invocation(first, true, 0, 0));
        result.add(new Invocation(second, true, 0, 1));
        for (int block = 1; block <= protocol.measuredBlocks(); block++) {
            result.add(new Invocation(first, false, block, 0));
            result.add(new Invocation(second, false, block, 1));
            result.add(new Invocation(second, false, block, 2));
            result.add(new Invocation(first, false, block, 3));
        }
        return List.copyOf(result);
    }

    private static CameraPipelineBenchmarkPlan.InitialOrder opposite(
            CameraPipelineBenchmarkPlan.InitialOrder value) {
        return value == CameraPipelineBenchmarkPlan.InitialOrder.PIPELINE_A
                ? CameraPipelineBenchmarkPlan.InitialOrder.PIPELINE_B
                : CameraPipelineBenchmarkPlan.InitialOrder.PIPELINE_A;
    }

    public record Invocation(
            CameraPipelineBenchmarkPlan.InitialOrder pipeline,
            boolean warmup,
            int block,
            int position) {
        public Invocation {
            pipeline = Objects.requireNonNull(pipeline, "pipeline");
            if (warmup && block != 0) {
                throw new IllegalArgumentException("warmup must use block zero");
            }
            if (!warmup && block <= 0) {
                throw new IllegalArgumentException("measured invocation needs block");
            }
            if (position < 0) {
                throw new IllegalArgumentException("position must not be negative");
            }
        }
    }
}