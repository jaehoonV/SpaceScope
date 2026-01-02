package Utils;

import java.text.DecimalFormat;

public final class SizeFormatUtil {

    private static final String[] UNITS = {"KB", "MB", "GB", "TB"};
    private static final long KIB = 1024L;

    private static final ThreadLocal<DecimalFormat> DF =
            ThreadLocal.withInitial(() -> new DecimalFormat("#,##0.0"));

    private SizeFormatUtil() {}

    public static String human(long bytes) {
        if (bytes < KIB) return bytes + " B";

        double v = bytes;
        int unit = -1;

        while (v >= KIB && unit < UNITS.length - 1) {
            v /= (double) KIB;
            unit++;
        }

        return DF.get().format(v) + " " + UNITS[unit];
    }
}
