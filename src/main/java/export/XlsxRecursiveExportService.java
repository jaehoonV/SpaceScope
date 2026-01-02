package export;

import Utils.SizeFormatUtil;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.function.IntConsumer;

public final class XlsxRecursiveExportService {

    private XlsxRecursiveExportService() {}

    // 컬럼 정의 (추가한 Name 포함)
    private static final String[] HEADERS = {
            "Type", "Name", "Depth", "Path", "Size (bytes)", "Formatted Size", "Last Modified"
    };

    // 문자 기준 너비(Excel column width: chars * 256)
    private static final int[] COL_WIDTH_CHARS = {
            12, 30, 8, 80, 18, 18, 22
    };

    /**
     * 기본: root 하위 전체(재귀) 스캔 후 XLSX 저장.
     * @param rootDir 디렉토리 권장 (파일이면 파일 1줄만 저장)
     * @param outFile 저장할 XLSX 파일
     * @param includeFiles 파일 항목도 XLSX에 포함할지
     */
    public static void export(Path rootDir, File outFile, boolean includeFiles) throws IOException {
        Objects.requireNonNull(rootDir, "rootDir");
        Objects.requireNonNull(outFile, "outFile");

        if (!Files.isDirectory(rootDir)) {
            writeSingleFile(rootDir, outFile);
            return;
        }

        Map<Path, Long> dirSizeMap = buildDirSizeMap(rootDir);

        try (SXSSFWorkbook wb = createWorkbook();
             FileOutputStream fos = new FileOutputStream(outFile)) {

            Sheet sheet = wb.createSheet("Folder Size");
            initSheetLayout(sheet);

            CellStyle headerStyle = createHeaderStyle(wb);
            int rowIdx = 0;

            // header
            Row header = sheet.createRow(rowIdx++);
            for (int c = 0; c < HEADERS.length; c++) {
                Cell cell = header.createCell(c);
                cell.setCellValue(HEADERS[c]);
                cell.setCellStyle(headerStyle);
            }

            // data
            rowIdx = writeTreePreOrder(sheet, rowIdx, rootDir, 0, includeFiles, dirSizeMap, null, 0, null);

            // 보기 편의
            sheet.createFreezePane(0, 1);
            sheet.setAutoFilter(new CellRangeAddress(0, Math.max(0, rowIdx - 1), 0, HEADERS.length - 1));

            wb.write(fos);
            wb.dispose(); // SXSSF 임시파일 정리
        }
    }

    /**
     * 진행률 포함 Export
     * @param totalEntries 미리 countEntries로 계산한 총 행 수(헤더 제외)
     * @param onDoneEntries 현재까지 완료된 행 수 콜백(헤더 제외)
     */
    public static void exportWithProgress(
            Path rootDir,
            File outFile,
            boolean includeFiles,
            int totalEntries,
            IntConsumer onDoneEntries
    ) throws IOException {
        Objects.requireNonNull(rootDir, "rootDir");
        Objects.requireNonNull(outFile, "outFile");

        if (!Files.isDirectory(rootDir)) {
            writeSingleFile(rootDir, outFile);
            if (onDoneEntries != null) onDoneEntries.accept(1);
            return;
        }

        Map<Path, Long> dirSizeMap = buildDirSizeMap(rootDir);

        final int[] done = {0};

        try (SXSSFWorkbook wb = createWorkbook();
             FileOutputStream fos = new FileOutputStream(outFile)) {

            Sheet sheet = wb.createSheet("Folder Size");
            initSheetLayout(sheet);

            CellStyle headerStyle = createHeaderStyle(wb);
            int rowIdx = 0;

            // header
            Row header = sheet.createRow(rowIdx++);
            for (int c = 0; c < HEADERS.length; c++) {
                Cell cell = header.createCell(c);
                cell.setCellValue(HEADERS[c]);
                cell.setCellStyle(headerStyle);
            }

            // data with progress
            rowIdx = writeTreePreOrder(sheet, rowIdx, rootDir, 0, includeFiles, dirSizeMap, done, totalEntries, onDoneEntries);

            sheet.createFreezePane(0, 1);
            sheet.setAutoFilter(new CellRangeAddress(0, Math.max(0, rowIdx - 1), 0, HEADERS.length - 1));

            wb.write(fos);
            wb.dispose();
        }
    }

    public static int countEntries(Path root, boolean includeFiles) throws IOException {
        if (!Files.isDirectory(root)) return 1;

        final int[] count = {1}; // root folder row

        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                if (!dir.equals(root)) count[0]++;
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (includeFiles && attrs.isRegularFile()) count[0]++;
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) {
                return FileVisitResult.CONTINUE;
            }
        });

        return count[0];
    }

    // ---------------------------
    // 내부 구현
    // ---------------------------

    private static SXSSFWorkbook createWorkbook() {
        // 200 rows window: 메모리/성능 밸런스
        SXSSFWorkbook wb = new SXSSFWorkbook(200);
        wb.setCompressTempFiles(true);
        return wb;
    }

    private static void initSheetLayout(Sheet sheet) {
        for (int i = 0; i < COL_WIDTH_CHARS.length; i++) {
            sheet.setColumnWidth(i, COL_WIDTH_CHARS[i] * 256);
        }
        sheet.setDefaultRowHeightInPoints(18);
    }

    private static CellStyle createHeaderStyle(Workbook wb) {
        Font font = wb.createFont();
        font.setBold(true);

        CellStyle style = wb.createCellStyle();
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setBorderBottom(BorderStyle.THIN);
        return style;
    }

    private static void writeSingleFile(Path file, File outFile) throws IOException {
        long size = 0L;
        try { size = Files.size(file); } catch (Exception ignored) {}

        try (SXSSFWorkbook wb = createWorkbook();
             FileOutputStream fos = new FileOutputStream(outFile)) {

            Sheet sheet = wb.createSheet("Folder Size");
            initSheetLayout(sheet);

            CellStyle headerStyle = createHeaderStyle(wb);

            int rowIdx = 0;
            Row header = sheet.createRow(rowIdx++);
            for (int c = 0; c < HEADERS.length; c++) {
                Cell cell = header.createCell(c);
                cell.setCellValue(HEADERS[c]);
                cell.setCellStyle(headerStyle);
            }

            Row row = sheet.createRow(rowIdx);
            fillRow(row,
                    "File",
                    entryName(file),
                    0,
                    file.toAbsolutePath().normalize().toString(),
                    size,
                    SizeFormatUtil.human(size),
                    lastModified(file)
            );

            sheet.createFreezePane(0, 1);
            sheet.setAutoFilter(new CellRangeAddress(0, rowIdx, 0, HEADERS.length - 1));

            wb.write(fos);
            wb.dispose();
        }
    }

    /**
     * pre-order 출력. 반환값은 다음 rowIdx
     */
    private static int writeTreePreOrder(
            Sheet sheet,
            int rowIdx,
            Path dir,
            int depth,
            boolean includeFiles,
            Map<Path, Long> dirSizeMap,
            int[] done,
            int totalEntries,
            IntConsumer onDoneEntries
    ) {
        long dirBytes = dirSizeMap.getOrDefault(dir, 0L);

        // Folder row
        Row row = sheet.createRow(rowIdx++);
        fillRow(row,
                "Folder",
                entryName(dir),
                depth,
                dir.toAbsolutePath().normalize().toString(),
                dirBytes,
                SizeFormatUtil.human(dirBytes),
                lastModified(dir)
        );

        if (done != null) {
            done[0]++;
            if (onDoneEntries != null) onDoneEntries.accept(done[0]);
        }

        // children: 이름 기준 정렬
        List<Path> childDirs = new ArrayList<>();
        List<Path> childFiles = new ArrayList<>();

        try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir)) {
            for (Path p : ds) {
                try {
                    if (Files.isDirectory(p)) childDirs.add(p);
                    else if (includeFiles && Files.isRegularFile(p)) childFiles.add(p);
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {
            return rowIdx;
        }

        Comparator<Path> byName = Comparator.comparing(p -> {
            Path fn = p.getFileName();
            String s = (fn == null ? p.toString() : fn.toString());
            return s.toLowerCase(Locale.ROOT);
        });

        childFiles.sort(byName);
        childDirs.sort(byName);

        // files
        if (includeFiles) {
            for (Path f : childFiles) {
                long sz = 0L;
                try { sz = Files.size(f); } catch (Exception ignored) {}

                Row fr = sheet.createRow(rowIdx++);
                fillRow(fr,
                        "File",
                        entryName(f),
                        depth + 1,
                        f.toAbsolutePath().normalize().toString(),
                        sz,
                        SizeFormatUtil.human(sz),
                        lastModified(f)
                );

                if (done != null) {
                    done[0]++;
                    if (onDoneEntries != null) onDoneEntries.accept(done[0]);
                }
            }
        }

        // dirs recurse
        for (Path d : childDirs) {
            rowIdx = writeTreePreOrder(sheet, rowIdx, d, depth + 1, includeFiles, dirSizeMap, done, totalEntries, onDoneEntries);
        }

        return rowIdx;
    }

    private static void fillRow(
            Row row,
            String type,
            String name,
            int depth,
            String path,
            long sizeBytes,
            String formatted,
            String lastModified
    ) {
        int c = 0;

        row.createCell(c++).setCellValue(nvl(type));
        row.createCell(c++).setCellValue(nvl(name));

        // depth: 숫자
        Cell depthCell = row.createCell(c++);
        depthCell.setCellValue(depth);

        row.createCell(c++).setCellValue(nvl(path));

        // size bytes: 숫자(필터/정렬 편의)
        Cell sizeCell = row.createCell(c++);
        sizeCell.setCellValue(sizeBytes);

        row.createCell(c++).setCellValue(nvl(formatted));
        row.createCell(c++).setCellValue(nvl(lastModified));
    }

    private static String nvl(String s) {
        return s == null ? "" : s;
    }

    private static String entryName(Path p) {
        if (p == null) return "";
        Path fn = p.getFileName();
        String name = (fn == null ? "" : fn.toString());
        if (name.isEmpty()) {
            // 루트(C:\, /) 같은 경우
            Path abs = p.toAbsolutePath().normalize();
            return abs.toString();
        }
        return name;
    }

    private static String lastModified(Path p) {
        try {
            long ms = Files.getLastModifiedTime(p).toMillis();
            Date d = new Date(ms);
            return String.format("%tF %<tT", d);
        } catch (Exception ignored) {
            return "";
        }
    }

    /**
     * Files.walkFileTree로 후위(post)에서 디렉토리별 합계를 계산해서 Map으로 만든다.
     * - 파일 크기는 부모 디렉토리에 더해짐
     * - 각 디렉토리는 (하위 전체 포함) total bytes가 저장됨
     */
    private static Map<Path, Long> buildDirSizeMap(Path rootDir) throws IOException {
        Map<Path, Long> dirSum = new HashMap<>();
        Deque<Path> stack = new ArrayDeque<>();

        Files.walkFileTree(rootDir, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                stack.push(dir);
                dirSum.putIfAbsent(dir, 0L);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                long sz = 0L;
                try {
                    if (attrs.isRegularFile()) sz = attrs.size();
                } catch (Exception ignored) {}

                Path curDir = stack.peek();
                if (curDir != null) dirSum.put(curDir, dirSum.getOrDefault(curDir, 0L) + sz);

                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) {
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) {
                long mySum = dirSum.getOrDefault(dir, 0L);

                if (!stack.isEmpty() && stack.peek().equals(dir)) stack.pop();

                Path parent = stack.peek();
                if (parent != null) dirSum.put(parent, dirSum.getOrDefault(parent, 0L) + mySum);

                return FileVisitResult.CONTINUE;
            }
        });

        return dirSum;
    }
}
