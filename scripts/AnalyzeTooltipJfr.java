import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingFile;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class AnalyzeTooltipJfr {
    private record Point(Instant time, String kind, long nanos, int index, int count) {}
    private record Memory(Instant time, long bytes, boolean afterGc) {}
    private record Pause(Instant time, long nanos) {}

    public static void main(String[] arguments) throws Exception {
        if (arguments.length == 1 && arguments[0].equals("--self-test")) {
            List<Long> values = new ArrayList<>(List.of(100L, 20L, 40L, 30L));
            if (percentile(values, 0.95) != 100 || percentile(values, 0.5) != 30 || percentile(List.of(), 0.95) != 0) {
                throw new AssertionError("Nearest-rank percentiles");
            }
            Instant boundary = Instant.ofEpochSecond(200);
            Point crossing = new Point(boundary.plusSeconds(1), "frame", 165000000000L, 0, 0);
            if (!overlaps(crossing, Instant.EPOCH, boundary)
                || !overlaps(crossing, boundary, boundary.plusSeconds(10))
                || overlaps(crossing, boundary.plusSeconds(2), boundary.plusSeconds(10))) {
                throw new AssertionError("Long frame crossing publication must not disappear");
            }
            System.out.println("AnalyzeTooltipJfr self-test passed");
            return;
        }
        if (arguments.length != 1) {
            throw new IllegalArgumentException("Expected one recording.jfr path or --self-test");
        }
        List<Point> samples = new ArrayList<>();
        List<Memory> heaps = new ArrayList<>();
        List<Memory> allocations = new ArrayList<>();
        List<Pause> pauses = new ArrayList<>();
        Instant world = null;
        Instant sidebar = null;
        Instant queryStart = null;
        Instant complete = null;
        try (RecordingFile recording = new RecordingFile(Path.of(arguments[0]))) {
            while (recording.hasMoreEvents()) {
                RecordedEvent event = recording.readEvent();
                switch (event.getEventType().getName()) {
                    case "jet.Benchmark" -> {
                        String kind = event.getString("kind");
                        Point point = new Point(event.getStartTime(), kind, event.getLong("nanos"), event.getInt("index"), event.getInt("count"));
                        samples.add(point);
                        switch (kind) {
                            case "world-ready" -> { if (world != null) { throw new IllegalStateException("Multiple world windows in one recording"); } world = point.time(); }
                            case "sidebar-drawn" -> sidebar = point.time();
                            case "query-start" -> queryStart = point.time();
                            case "complete" -> complete = point.time();
                            default -> {}
                        }
                    }
                    case "jdk.GCHeapSummary" -> heaps.add(new Memory(event.getStartTime(), event.getLong("heapUsed"), event.getString("when").contains("After")));
                    case "jdk.ObjectAllocationSample" -> allocations.add(new Memory(event.getStartTime(), event.getLong("weight"), false));
                    case "jdk.GarbageCollection" -> pauses.add(new Pause(event.getStartTime(), event.getDuration("sumOfPauses").toNanos()));
                    default -> {}
                }
            }
        }
        if (world == null || sidebar == null || queryStart == null || complete == null) {
            throw new IllegalStateException("Incomplete benchmark markers: world=" + world + ", sidebar=" + sidebar + ", queries=" + queryStart + ", complete=" + complete);
        }
        long queries = samples.stream().filter(point -> point.kind().equals("query")).count();
        if (queries != 120) { throw new IllegalStateException("Expected 120 queries, got " + queries); }
        System.out.println("metric,value,unit");
        output("world_to_sidebar", java.time.Duration.between(world, sidebar).toNanos() / 1e6, "ms");
        output("world_to_complete", java.time.Duration.between(world, complete).toNanos() / 1e6, "ms");
        summarize(samples, "frame", "startup_frame", world, sidebar);
        summarize(samples, "tick", "startup_tick", world, sidebar);
        summarize(samples, "frame", "ready_frame", sidebar, queryStart);
        summarize(samples, "frame", "query_frame", queryStart, complete);
        summarize(samples, "frame", "measured_frame", world, complete);
        summarize(samples, "query", "query", queryStart, complete);
        for (int index = 0; index < 12; index++) {
            List<Long> values = new ArrayList<>();
            Integer expectedCount = null;
            for (Point point : samples) {
                if (point.kind().equals("query") && point.index() == index) {
                    values.add(point.nanos());
                    if (expectedCount != null && expectedCount != point.count()) {
                        throw new IllegalStateException("Query result count changed for query " + index);
                    }
                    expectedCount = point.count();
                }
            }
            if (values.size() != 10) { throw new IllegalStateException("Missing query repetitions: " + index); }
            output("query_" + index + "_count", expectedCount, "items");
            output("query_" + index + "_p95", percentile(values, 0.95) / 1e6, "ms");
        }
        memory(heaps, allocations, pauses, "startup", world, sidebar);
        memory(heaps, allocations, pauses, "measured", world, complete);
    }

    private static boolean within(Instant time, Instant start, Instant end) {
        return !time.isBefore(start) && !time.isAfter(end);
    }

    private static void summarize(List<Point> samples, String kind, String prefix, Instant start, Instant end) {
        List<Long> values = samples.stream().filter(point -> point.kind().equals(kind)
                && (kind.equals("frame") ? overlaps(point, start, end) : within(point.time(), start, end)))
            .map(Point::nanos).sorted().toList();
        output(prefix + "_samples", values.size(), "count");
        output(prefix + "_p50", percentile(values, 0.5) / 1e6, "ms");
        output(prefix + "_p95", percentile(values, 0.95) / 1e6, "ms");
        output(prefix + "_max", percentile(values, 1) / 1e6, "ms");
        output(prefix + "_over_50ms", values.stream().filter(value -> value > 50000000).count(), "count");
    }

    private static boolean overlaps(Point point, Instant start, Instant end) {
        return point.time().isAfter(start) && point.time().minusNanos(point.nanos()).isBefore(end);
    }

    private static void memory(List<Memory> heaps, List<Memory> allocations, List<Pause> pauses, String prefix, Instant start, Instant end) {
        List<Memory> window = heaps.stream().filter(value -> within(value.time(), start, end)).toList();
        output(prefix + "_heap_samples", window.size(), "count");
        output(prefix + "_heap_gc_sample_max", window.stream().mapToLong(Memory::bytes).max().orElse(0), "bytes");
        output(prefix + "_heap_after_gc_max", window.stream().filter(Memory::afterGc).mapToLong(Memory::bytes).max().orElse(0), "bytes");
        output(prefix + "_allocation_sample_weight", allocations.stream().filter(value -> within(value.time(), start, end)).mapToLong(Memory::bytes).sum(), "bytes_estimate");
        List<Pause> gc = pauses.stream().filter(value -> within(value.time(), start, end)).toList();
        output(prefix + "_gc_count", gc.size(), "count");
        output(prefix + "_gc_pause_sum", gc.stream().mapToLong(Pause::nanos).sum() / 1e6, "ms");
        output(prefix + "_gc_pause_sum_max", gc.stream().mapToLong(Pause::nanos).max().orElse(0) / 1e6, "ms");
    }

    private static long percentile(List<Long> values, double quantile) {
        if (values.isEmpty()) { return 0; }
        List<Long> sorted = values.stream().sorted(Comparator.naturalOrder()).toList();
        return sorted.get(Math.max(0, (int) Math.ceil(quantile * sorted.size()) - 1));
    }

    private static void output(String name, double value, String unit) {
        System.out.printf(Locale.ROOT, "%s,%.6f,%s%n", name, value, unit);
    }
}