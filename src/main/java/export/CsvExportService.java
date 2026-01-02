package export;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.List;

public final class CsvExportService {

    private CsvExportService() {}

    public static void write(File outFile, List<CsvExportRow> rows) throws IOException {
        try (PrintWriter writer = new PrintWriter(
                new BufferedWriter(new OutputStreamWriter(new FileOutputStream(outFile), StandardCharsets.UTF_8))
        )) {
            // Excel 한글 깨짐 방지용 BOM
            writer.write('\uFEFF');

            writer.println("Type,Depth,Path,Size (bytes),Formatted Size,Last Modified");

            for (CsvExportRow r : rows) {
                writer.printf("%s,%d,\"%s\",%d,%s,%tF %<tT%n",
                        safe(r.type()),
                        r.depth(),
                        escapeCsv(r.path()),
                        r.sizeBytes(),
                        safe(r.formattedSize()),
                        r.lastModified()
                );
            }
        }
    }

    private static String escapeCsv(String s) {
        if (s == null) return "";
        return s.replace("\"", "\"\"");
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }
}
