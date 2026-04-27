package cn.lsc.cardinality.util;

public final class MemoryProbe {
    private MemoryProbe() {
    }

    public static long usedHeapBytes() {
        Runtime runtime = Runtime.getRuntime();
        return runtime.totalMemory() - runtime.freeMemory();
    }

    // 通过主动 GC 降低历史临时对象的干扰。GC 无法被 JVM 强制保证，因此该值只作为近似测量。
    public static long usedHeapAfterGc() {
        for (int i = 0; i < 2; i++) {
            System.gc();
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return usedHeapBytes();
    }

    public static String humanize(long bytes) {
        String sign = bytes < 0 ? "-" : "";
        double abs = Math.abs((double) bytes);
        if (abs < 1024.0) {
            return sign + (long) abs + " B";
        }
        if (abs < 1024.0 * 1024.0) {
            return String.format(java.util.Locale.ROOT, "%s%.1f KB", sign, abs / 1024.0);
        }
        return String.format(java.util.Locale.ROOT, "%s%.2f MB", sign, abs / (1024.0 * 1024.0));
    }
}
