package com.tonywww.jeioptimize.instrumentation;

import com.tonywww.jeioptimize.JeiOptimize;
import com.tonywww.jeioptimize.config.JeiOptFeatureFlags;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Names the code responsible when a JEI startup phase runs far longer than expected.
 *
 * <p>JEI reports that a phase is slow but not what is making it slow, so the blame lands on
 * whichever mod's name appears in the message. Once a phase passes the configured threshold this
 * samples the stack of the thread running it and reports the frames it kept landing on.
 *
 * <p>Purely observational: it never changes what runs, in what order, or on which thread.
 */
public final class JeiOptStallWatchdog {
    private static final long SAMPLE_INTERVAL_MS = 250L;
    private static final long LIVE_REPORT_INTERVAL_NANOS = TimeUnit.SECONDS.toNanos(10L);
    private static final int REPORTED_FRAMES = 6;

    private static final Object LOCK = new Object();
    private static Watch active;
    private static Thread sampler;

    private JeiOptStallWatchdog() {
    }

    public static void run(String label, Runnable call) {
        Watch watch = new Watch(label, Thread.currentThread());
        begin(watch);
        try {
            call.run();
        } finally {
            end(watch);
        }
    }

    private static void begin(Watch watch) {
        synchronized (LOCK) {
            active = watch;
            if (sampler == null) {
                sampler = new Thread(JeiOptStallWatchdog::sampleLoop, JeiOptimize.MOD_ID + "-stall-watchdog");
                sampler.setDaemon(true);
                sampler.start();
            }
            LOCK.notifyAll();
        }
    }

    private static void end(Watch watch) {
        Report report;
        synchronized (LOCK) {
            if (active == watch) {
                active = null;
            }
            if (watch.samples == 0) {
                return;
            }
            report = watch.report(System.nanoTime(), false);
        }
        logReport(report);
    }

    private static void sampleLoop() {
        while (true) {
            try {
                Watch watch;
                synchronized (LOCK) {
                    while (active == null) {
                        LOCK.wait();
                    }
                    watch = active;
                }
                Thread.sleep(SAMPLE_INTERVAL_MS);
                long thresholdNanos = TimeUnit.SECONDS.toNanos(JeiOptFeatureFlags.stallThresholdSeconds());
                Report report = null;
                synchronized (LOCK) {
                    long now = System.nanoTime();
                    if (active == watch && now - watch.startNanos >= thresholdNanos) {
                        watch.sample();
                        if (watch.lastLiveReportNanos == 0L
                            || now - watch.lastLiveReportNanos >= LIVE_REPORT_INTERVAL_NANOS) {
                            watch.lastLiveReportNanos = now;
                            report = watch.report(now, true);
                        }
                    }
                }
                if (report != null) {
                    logReport(report);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (RuntimeException | LinkageError e) {
                JeiOptimize.LOGGER.debug("JEI Optimize stall watchdog sample failed", e);
            }
        }
    }

    private static final class Watch {
        private final String label;
        private final Thread thread;
        private final long startNanos = System.nanoTime();
        private final Map<String, Integer> frameCounts = new HashMap<>();
        private int samples;
        private long lastLiveReportNanos;

        private Watch(String label, Thread thread) {
            this.label = label;
            this.thread = thread;
        }

        private void sample() {
            String frame = topApplicationFrame(thread.getStackTrace());
            if (frame != null) {
                frameCounts.merge(frame, 1, Integer::sum);
                samples++;
            }
        }

        private List<String> topFrames() {
            List<String> lines = new ArrayList<>();
            frameCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(REPORTED_FRAMES)
                .forEach(entry -> lines.add(String.format(
                    Locale.ROOT, "%3d%%  %s", entry.getValue() * 100 / samples, entry.getKey())));
            return lines;
        }

        private Report report(long now, boolean live) {
            return new Report(
                label,
                now - startNanos,
                samples,
                topFrames(),
                live ? applicationFrames(thread.getStackTrace()) : List.of(),
                live
            );
        }
    }

    private static void logReport(Report report) {
        if (report.live()) {
            JeiOptimize.LOGGER.warn(
                "JEI Optimize stall watchdog: '{}' is still running after {}. Current thread stack:",
                report.label(),
                formatElapsed(report.elapsedNanos()));
            for (String frame : report.liveFrames()) {
                JeiOptimize.LOGGER.warn("    {}", frame);
            }
            return;
        }

        JeiOptimize.LOGGER.warn(
            "JEI Optimize stall watchdog: '{}' took {}. Sampled {} stack(s); that thread was most often in:",
            report.label(),
            formatElapsed(report.elapsedNanos()),
            report.samples());
        for (String frame : report.topFrames()) {
            JeiOptimize.LOGGER.warn("    {}", frame);
        }
    }

    private static String formatElapsed(long elapsedNanos) {
        return String.format(Locale.ROOT, "%.1f s", elapsedNanos / 1_000_000_000.0);
    }

    /**
     * JDK, class-loading and bytecode-transformation frames sit on top of almost every stack and
     * say nothing about who is responsible; the caller underneath them does.
     */
    private static final String[] IGNORED_FRAME_PREFIXES = {
        "java.", "jdk.", "sun.",
        "cpw.mods.", "org.objectweb.asm", "org.spongepowered.asm",
        "net.minecraftforge.fml.loading", "net.neoforged.fml.loading",
    };

    private static String topApplicationFrame(StackTraceElement[] stack) {
        for (StackTraceElement element : stack) {
            String className = element.getClassName();
            boolean ignored = false;
            for (String prefix : IGNORED_FRAME_PREFIXES) {
                if (className.startsWith(prefix)) {
                    ignored = true;
                    break;
                }
            }
            if (ignored) {
                continue;
            }
            return className + "#" + element.getMethodName();
        }
        return null;
    }

    private static List<String> applicationFrames(StackTraceElement[] stack) {
        List<String> frames = new ArrayList<>();
        for (StackTraceElement element : stack) {
            String className = element.getClassName();
            boolean ignored = false;
            for (String prefix : IGNORED_FRAME_PREFIXES) {
                if (className.startsWith(prefix)) {
                    ignored = true;
                    break;
                }
            }
            if (!ignored) {
                frames.add(element.toString());
                if (frames.size() == REPORTED_FRAMES) {
                    break;
                }
            }
        }
        return frames;
    }

    private record Report(
        String label,
        long elapsedNanos,
        int samples,
        List<String> topFrames,
        List<String> liveFrames,
        boolean live
    ) {
    }
}
