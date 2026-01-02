package export;

import java.util.Date;

public record CsvExportRow(
        String type,      // Folder/File
        int depth,
        String path,
        long sizeBytes,
        String formattedSize,
        Date lastModified
) {}
