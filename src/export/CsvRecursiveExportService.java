package export;

import Utils.SizeFormatUtil;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.function.IntConsumer;

public final class CsvRecursiveExportService {

    private CsvRecursiveExportService() {}

    /**
     * 기본: root 하위 전체(재귀) 스캔 후 CSV 저장.
     * @param rootDir 디렉토리만 권장 (파일이면 파일 1줄만 저장)
     * @param outFile 저장할 CSV 파일
     * @param includeFiles 파일 항목도 CSV에 포함할지
     */
    public static void export(Path rootDir, File outFile, boolean includeFiles) throws IOException {
        Objects.requireNonNull(rootDir, "rootDir");
        Objects.requireNonNull(outFile, "outFile");

        // 파일 선택된 경우: 파일 1줄만
        if (!Files.isDirectory(rootDir)) {
            writeSingleFile(rootDir, outFile);
            return;
        }

        // 1) 전체 재귀 스캔해서 dirSizeMap 구성 (Path -> bytes)
        Map<Path, Long> dirSizeMap = buildDirSizeMap(rootDir);

        // 2) 출력(원하는 순서: pre-order) - size는 map에서 가져옴
        try (PrintWriter w = new PrintWriter(
                new BufferedWriter(new OutputStreamWriter(new FileOutputStream(outFile), StandardCharsets.UTF_8))
        )) {
            // Excel 한글 깨짐 방지 BOM
            w.write('\uFEFF');

            w.println("Type,Depth,Path,Size (bytes),Formatted Size,Last Modified");

            writeTreePreOrder(rootDir, 0, includeFiles, dirSizeMap, w);
        }
    }

    private static void writeSingleFile(Path file, File outFile) throws IOException {
        long size = 0L;
        try { size = Files.size(file); } catch (Exception ignored) {}

        String lm = lastModified(file);

        try (PrintWriter w = new PrintWriter(
                new BufferedWriter(new OutputStreamWriter(new FileOutputStream(outFile), StandardCharsets.UTF_8))
        )) {
            w.write('\uFEFF');
            w.println("Type,Depth,Path,Size (bytes),Formatted Size,Last Modified");
            w.printf("File,%d,\"%s\",%d,%s,%s%n",
                    0,
                    escapeCsv(file.toAbsolutePath().normalize().toString()),
                    size,
                    SizeFormatUtil.human(size),
                    lm
            );
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

                // 현재 스택 top(부모 dir)에 파일 크기 누적
                Path curDir = stack.peek();
                if (curDir != null) dirSum.put(curDir, dirSum.getOrDefault(curDir, 0L) + sz);

                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) {
                // 권한/에러는 무시하고 진행
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) {
                // dir의 누적합을 부모 dir에 더해줌 (후위에서 상향 누적)
                long mySum = dirSum.getOrDefault(dir, 0L);

                // pop
                if (!stack.isEmpty() && stack.peek().equals(dir)) stack.pop();

                Path parent = stack.peek();
                if (parent != null) dirSum.put(parent, dirSum.getOrDefault(parent, 0L) + mySum);

                return FileVisitResult.CONTINUE;
            }
        });

        return dirSum;
    }

    /**
     * 디렉토리/파일 트리를 pre-order로 출력한다.
     * 디렉토리 size는 dirSizeMap에서 가져온다.
     */
    private static void writeTreePreOrder(
            Path dir,
            int depth,
            boolean includeFiles,
            Map<Path, Long> dirSizeMap,
            PrintWriter w
    ) {
        long dirBytes = dirSizeMap.getOrDefault(dir, 0L);

        w.printf("Folder,%d,\"%s\",%d,%s,%s%n",
                depth,
                escapeCsv(dir.toAbsolutePath().normalize().toString()),
                dirBytes,
                SizeFormatUtil.human(dirBytes),
                lastModified(dir)
        );

        // children: 이름 기준 정렬 (UI와 맞추기)
        List<Path> childDirs = new ArrayList<>();
        List<Path> childFiles = new ArrayList<>();

        try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir)) {
            for (Path p : ds) {
                try {
                    if (Files.isDirectory(p)) childDirs.add(p);
                    else if (Files.isRegularFile(p)) childFiles.add(p);
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {
            return;
        }

        childDirs.sort(Comparator.comparing(p -> {
            Path fn = p.getFileName();
            String s = (fn == null ? p.toString() : fn.toString());
            return s.toLowerCase(Locale.ROOT);
        }));

        childFiles.sort(Comparator.comparing(p -> {
            Path fn = p.getFileName();
            String s = (fn == null ? p.toString() : fn.toString());
            return s.toLowerCase(Locale.ROOT);
        }));

        // 파일 출력(옵션)
        if (includeFiles) {
            for (Path f : childFiles) {
                long sz = 0L;
                try { sz = Files.size(f); } catch (Exception ignored) {}

                w.printf("File,%d,\"%s\",%d,%s,%s%n",
                        depth + 1,
                        escapeCsv(f.toAbsolutePath().normalize().toString()),
                        sz,
                        SizeFormatUtil.human(sz),
                        lastModified(f)
                );
            }
        }

        // 하위 디렉토리 재귀
        for (Path d : childDirs) {
            writeTreePreOrder(d, depth + 1, includeFiles, dirSizeMap, w);
        }
    }

    private static String escapeCsv(String s) {
        return s == null ? "" : s.replace("\"", "\"\"");
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

        try (PrintWriter w = new PrintWriter(
                new BufferedWriter(new OutputStreamWriter(new FileOutputStream(outFile), StandardCharsets.UTF_8))
        )) {
            w.write('\uFEFF');
            w.println("Type,Depth,Path,Size (bytes),Formatted Size,Last Modified");

            writeTreePreOrderWithProgress(rootDir, 0, includeFiles, dirSizeMap, w, done, totalEntries, onDoneEntries);
        }
    }

    private static void writeTreePreOrderWithProgress(
            Path dir,
            int depth,
            boolean includeFiles,
            Map<Path, Long> dirSizeMap,
            PrintWriter w,
            int[] done,
            int totalEntries,
            IntConsumer onDoneEntries
    ) {
        long dirBytes = dirSizeMap.getOrDefault(dir, 0L);

        w.printf("Folder,%d,\"%s\",%d,%s,%s%n",
                depth,
                escapeCsv(dir.toAbsolutePath().normalize().toString()),
                dirBytes,
                Utils.SizeFormatUtil.human(dirBytes),
                lastModified(dir)
        );

        done[0]++;
        if (onDoneEntries != null) onDoneEntries.accept(done[0]);

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
            return;
        }

        Comparator<Path> byName = Comparator.comparing(p -> {
            Path fn = p.getFileName();
            String s = (fn == null ? p.toString() : fn.toString());
            return s.toLowerCase(Locale.ROOT);
        });

        childFiles.sort(byName);
        childDirs.sort(byName);

        if (includeFiles) {
            for (Path f : childFiles) {
                long sz = 0L;
                try { sz = Files.size(f); } catch (Exception ignored) {}

                w.printf("File,%d,\"%s\",%d,%s,%s%n",
                        depth + 1,
                        escapeCsv(f.toAbsolutePath().normalize().toString()),
                        sz,
                        Utils.SizeFormatUtil.human(sz),
                        lastModified(f)
                );

                done[0]++;
                if (onDoneEntries != null) onDoneEntries.accept(done[0]);
            }
        }

        for (Path d : childDirs) {
            writeTreePreOrderWithProgress(d, depth + 1, includeFiles, dirSizeMap, w, done, totalEntries, onDoneEntries);
        }
    }

}
