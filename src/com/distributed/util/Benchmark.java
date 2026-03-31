package com.distributed.util;

 
/**
 * Utility class for measuring performance and generating download reports.
 * Tracks elapsed time and calculates transfer speeds.
 */
public class Benchmark {
    /** Start time in milliseconds */
    private long startTime;
    /** End time in milliseconds */
    private long endTime;
    /** Name of the task or file being measured */
    private final String taskName;

    public Benchmark(String taskName) {
        this.taskName = taskName;
    }

    /**
     * Marks the start of the benchmark.
     */
    public void start() {
        this.startTime = System.currentTimeMillis();
    }

    /**
     * Marks the end of the benchmark.
     */
    public void stop() {
        this.endTime = System.currentTimeMillis();
    }

     
    public long getDurationMillis() {
        return endTime - startTime;
    }

     
    public double getDurationSeconds() {
        return getDurationMillis() / 1000.0;
    }

    /**
     * Generates a formatted summary of the benchmark results.
     * @param totalBytes Total amount of data transferred
     * @return A multi-line string containing the benchmark report
     */
    public String getFormattedSummary(long totalBytes) {
        double durationSeconds = getDurationSeconds();
        if (durationSeconds <= 0) durationSeconds = 0.001; 
        
        double speedVal = totalBytes / durationSeconds; 
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
