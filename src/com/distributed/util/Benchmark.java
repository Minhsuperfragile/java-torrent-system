package com.distributed.util;

/**
 * Utility class for benchmarking tasks such as file downloads.
 * Measures time elapsed and calculates transfer speeds.
 */
public class Benchmark {
    private long startTime;
    private long endTime;
    private final String taskName;

    public Benchmark(String taskName) {
        this.taskName = taskName;
    }

    /**
     * Starts the timer.
     */
    public void start() {
        this.startTime = System.currentTimeMillis();
    }

    /**
     * Stops the timer.
     */
    public void stop() {
        this.endTime = System.currentTimeMillis();
    }

    /**
     * Returns the duration in milliseconds.
     */
    public long getDurationMillis() {
        return endTime - startTime;
    }

    /**
     * Returns the duration in seconds.
     */
    public double getDurationSeconds() {
        return getDurationMillis() / 1000.0;
    }

    /**
     * Generates a human-readable summary of the benchmark.
     * 
     * @param totalBytes The total amount of data processed during the task.
     * @return A formatted string with size, time, and speed.
     */
    public String getFormattedSummary(long totalBytes) {
        double durationSeconds = getDurationSeconds();
        if (durationSeconds <= 0) durationSeconds = 0.001; // Avoid division by zero
        
        double speedVal = totalBytes / durationSeconds; // bytes per second
        String speedStr = formatSpeed(speedVal);
        String sizeStr = formatSize(totalBytes);

        return String.format(
            "\n========================================\n" +
            "   BENCHMARK REPORT: %s\n" +
            "========================================\n" +
            "File Name:      %s\n" +
            "Total Size:     %s\n" +
            "Time Elapsed:   %.3f seconds\n" +
            "Average Speed:  %s\n" +
            "========================================\n",
            taskName.toUpperCase(), taskName, sizeStr, durationSeconds, speedStr
        );
    }

    private String formatSize(long bytes) {
        if (bytes <= 0) return "0 B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        if (exp == 0) return bytes + " B";
        String pre = "KMGTPE".substring(exp - 1, exp);
        return String.format("%.2f %sB", bytes / Math.pow(1024, exp), pre);
    }

    private String formatSpeed(double bytesPerSecond) {
        if (bytesPerSecond <= 0) return "0.00 B/s";
        int exp = (int) (Math.log(bytesPerSecond) / Math.log(1024));
        if (exp == 0) return String.format("%.2f B/s", bytesPerSecond);
        String pre = "KMGTPE".substring(exp - 1, exp);
        return String.format("%.2f %sB/s", bytesPerSecond / Math.pow(1024, exp), pre);
    }
}
