package FolderSizeViz;

import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.intellijthemes.materialthemeuilite.FlatMTArcDarkIJTheme;

import javax.swing.*;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeWillExpandListener;
import javax.swing.filechooser.FileSystemView;
import javax.swing.tree.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.DecimalFormat;
import java.util.*;
import java.util.List;
import java.util.concurrent.CancellationException;

public class FolderSizeVizApp {

    private static final String APP_TITLE = "Folder Size Visualizer";
    private static final String LOADING_NODE_TEXT = "loading...";

    private static final int FRAME_W = 1200;
    private static final int FRAME_H = 760;

    private static final int TREE_MIN_W = 360;
    private static final double SPLIT_RESIZE_WEIGHT = 0.32;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            setupDarkTheme();
            new MainFrame().setVisible(true);
        });
    }

    private static void setupDarkTheme() {
        FlatMTArcDarkIJTheme.setup();

        UIManager.put("Tree.rowHeight", 24);
        UIManager.put("ScrollBar.width", 12);
        UIManager.put("Component.arc", 12);
        UIManager.put("Button.arc", 12);
        UIManager.put("ProgressBar.arc", 12);

        FlatLaf.updateUI();
    }

    static class MainFrame extends JFrame {

        private final JTree tree;
        private final DefaultTreeModel treeModel;

        // 탭1: 상세(바차트 스타일) - 별도 파일
        private final DetailChartPanel detailPanel = new DetailChartPanel();

        // 탭2: 파이차트(JFreeChart) - 별도 파일
        private final PieChartTabPanel pieChartPanel = new PieChartTabPanel();

        private final JTabbedPane rightTabs = new JTabbedPane();

        private final JLabel statusLabel = new JLabel("준비됨");
        private final JProgressBar progressBar = new JProgressBar();

        private SizeScanWorker currentWorker;

        // 탭2 갱신용 캐시(스캔 결과 최신 상태 유지)
        private final List<SizeItem> latestItems = new ArrayList<>();
        private Path latestFolder = null;

        MainFrame() {
            super(APP_TITLE);
            setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            setSize(FRAME_W, FRAME_H);
            setLocationRelativeTo(null);

            FolderNode rootNode = createDrivesRootNode();
            treeModel = new DefaultTreeModel(rootNode);

            tree = createTree(treeModel);
            installTreeListeners();

            JSplitPane splitPane = buildSplitPane();

            setLayout(new BorderLayout());
            add(splitPane, BorderLayout.CENTER);
        }

        // ---------------- UI 구성 ----------------

        private JTree createTree(DefaultTreeModel model) {
            JTree t = new JTree(model);
            t.setRootVisible(false);
            t.setShowsRootHandles(true);
            t.setCellRenderer(new OsIconTreeCellRenderer());
            return t;
        }

        private JSplitPane buildSplitPane() {
            JScrollPane leftScroll = new JScrollPane(tree);
            leftScroll.setMinimumSize(new Dimension(TREE_MIN_W, 0));
            leftScroll.setBorder(BorderFactory.createEmptyBorder());
            tree.setBorder(BorderFactory.createEmptyBorder());

            JPanel right = new JPanel(new BorderLayout());
            right.add(buildRightTabs(), BorderLayout.CENTER);
            right.add(buildBottomBar(), BorderLayout.SOUTH);

            JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftScroll, right);
            splitPane.setResizeWeight(SPLIT_RESIZE_WEIGHT);
            return splitPane;
        }

        private JComponent buildRightTabs() {
            JScrollPane detailScroll = new JScrollPane(detailPanel);
            detailScroll.setBorder(BorderFactory.createEmptyBorder());

            JPanel pieTab = new JPanel(new BorderLayout());
            pieTab.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
            pieTab.add(pieChartPanel, BorderLayout.CENTER);

            rightTabs.addTab("상세", detailScroll);
            rightTabs.addTab("차트", pieTab);

            rightTabs.addChangeListener(e -> {
                // 탭2로 넘어갈 때 최신 결과로 갱신
                if (rightTabs.getSelectedIndex() == 1) {
                    refreshPieChartFromLatest();
                }
            });

            return rightTabs;
        }

        private JPanel buildBottomBar() {
            ThemeColors c = ThemeColors.fromUI();

            JPanel bottom = new JPanel(new BorderLayout());
            bottom.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, c.line));

            JPanel content = new JPanel(new BorderLayout(10, 0));
            content.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));

            progressBar.setStringPainted(true);
            progressBar.setVisible(false);

            content.add(statusLabel, BorderLayout.CENTER);
            content.add(progressBar, BorderLayout.EAST);

            bottom.add(content, BorderLayout.CENTER);
            return bottom;
        }

        // ---------------- 트리 로딩/리스너 ----------------

        private void installTreeListeners() {
            tree.addTreeWillExpandListener(new TreeWillExpandListener() {
                @Override
                public void treeWillExpand(TreeExpansionEvent event) {
                    Object last = event.getPath().getLastPathComponent();
                    if (last instanceof FolderNode node) {
                        ensureChildrenLoaded(node);
                    }
                }
                @Override public void treeWillCollapse(TreeExpansionEvent event) { /* no-op */ }
            });

            tree.addTreeSelectionListener(e -> {
                Object sel = tree.getLastSelectedPathComponent();
                if (!(sel instanceof FolderNode node)) return;
                if (node.isVirtual()) return;

                if (node.isDirectory) startScan(node.path);
                else showFileInfo(node.path);
            });
        }

        private FolderNode createDrivesRootNode() {
            FolderNode root = FolderNode.createVirtualRoot("내 PC");

            File[] roots = File.listRoots();
            if (roots == null || roots.length == 0) return root;

            Arrays.sort(roots, Comparator.comparing(File::getPath, String.CASE_INSENSITIVE_ORDER));

            for (File r : roots) {
                try {
                    Path p = r.toPath();
                    FolderNode drive = new FolderNode(p);
                    maybeAddLoadingPlaceholder(drive, p);
                    root.add(drive);
                } catch (Exception ignored) {
                }
            }
            return root;
        }

        private void ensureChildrenLoaded(FolderNode node) {
            if (!node.isDirectory) return;
            if (node.isVirtual()) return;
            if (node.childrenLoaded) return;

            loadChildrenOneLevel(node);
            treeModel.reload(node);
            node.childrenLoaded = true;
        }

        private void loadChildrenOneLevel(FolderNode node) {
            node.removeAllChildren();

            List<Path> dirs = new ArrayList<>();
            List<Path> files = new ArrayList<>();

            try (DirectoryStream<Path> ds = Files.newDirectoryStream(node.path)) {
                for (Path p : ds) {
                    try {
                        if (Files.isDirectory(p)) dirs.add(p);
                        else files.add(p);
                    } catch (Exception ignored) {
                    }
                }
            } catch (IOException | SecurityException ignored) {
                return;
            }

            dirs.sort(PATH_BY_NAME);
            files.sort(PATH_BY_NAME);

            for (Path dir : dirs) {
                FolderNode child = new FolderNode(dir);
                maybeAddLoadingPlaceholder(child, dir);
                node.add(child);
            }
            for (Path f : files) {
                node.add(new FolderNode(f));
            }
        }

        private void maybeAddLoadingPlaceholder(FolderNode folderNode, Path dir) {
            if (hasAnyChildEntry(dir)) {
                folderNode.add(new DefaultMutableTreeNode(LOADING_NODE_TEXT));
            }
        }

        // ---------------- 동작: 파일 선택 / 폴더 스캔 ----------------

        private void showFileInfo(Path file) {
            cancelCurrentWorker();
            progressBar.setVisible(false);

            String name = fileNameOrPath(file);
            statusLabel.setText("파일 선택: " + file);

            // 탭1
            detailPanel.setTitle("파일: " + name);
            detailPanel.setItems(Collections.emptyList());

            Path openPath = file.getParent();
            detailPanel.setTitleClickTarget(openPath);
            detailPanel.setOnTitleClick(() -> openInExplorer(openPath));

            // 탭2(차트는 폴더 기준이므로 초기화)
            clearLatest();
            pieChartPanel.setTitle("선택 없음");
            pieChartPanel.clear();
        }

        private void startScan(Path folder) {
            cancelCurrentWorker();

            setLatestFolder(folder);
            statusLabel.setText("스캔 시작: " + folder);

            progressBar.setIndeterminate(true);
            progressBar.setString("스캔 중...");
            progressBar.setVisible(true);

            // 탭1 초기화
            detailPanel.setTitle("폴더: " + folder);
            detailPanel.setItems(Collections.emptyList());
            detailPanel.setTitleClickTarget(folder);
            detailPanel.setOnTitleClick(() -> openInExplorer(folder));

            // 탭2 초기화
            pieChartPanel.setTitle("폴더: " + folder);
            pieChartPanel.clear();

            currentWorker = new SizeScanWorker(folder, new SizeScanWorker.Callback() {
                @Override
                public void onPartial(SizeItem item) {
                    detailPanel.upsertItem(item);
                    upsertLatest(item);

                    // 차트 탭이 열려있을 때만 실시간 갱신
                    if (rightTabs.getSelectedIndex() == 1) {
                        pieChartPanel.setItemsTop10(latestItems);
                    }
                }

                @Override
                public void onDone(List<SizeItem> finalItems, long totalBytes, long scannedFiles) {
                    progressBar.setVisible(false);
                    statusLabel.setText("완료: " + folder + " / 총 " + human(totalBytes) + " / 파일 " + scannedFiles + "개");

                    detailPanel.setItems(finalItems);

                    latestItems.clear();
                    latestItems.addAll(finalItems);

                    // 완료 시에는 탭 상태와 상관없이 최신 데이터 반영
                    refreshPieChartFromLatest();
                }

                @Override
                public void onCancelled() {
                    progressBar.setVisible(false);
                    statusLabel.setText("스캔 취소됨: " + folder);
                }

                @Override
                public void onError(Exception ex) {
                    progressBar.setVisible(false);
                    statusLabel.setText("오류: " + ex.getClass().getSimpleName() + " - " + ex.getMessage());
                }
            });

            currentWorker.execute();
        }

        // ---------------- 최신 결과/차트 갱신 ----------------

        private void refreshPieChartFromLatest() {
            pieChartPanel.setTitle(latestFolder == null ? "선택 없음" : "폴더: " + latestFolder);
            pieChartPanel.setItemsTop10(latestItems);
        }

        private void clearLatest() {
            latestFolder = null;
            latestItems.clear();
        }

        private void setLatestFolder(Path folder) {
            latestFolder = folder;
            latestItems.clear();
        }

        private void upsertLatest(SizeItem item) {
            for (int i = 0; i < latestItems.size(); i++) {
                SizeItem cur = latestItems.get(i);
                if (cur.isDirectory == item.isDirectory && cur.name.equals(item.name)) {
                    latestItems.set(i, item);
                    return;
                }
            }
            latestItems.add(item);
        }

        // ---------------- 기타 ----------------

        private void openInExplorer(Path path) {
            if (path == null) return;
            try {
                Desktop.getDesktop().open(path.toFile());
            } catch (Exception ex) {
                statusLabel.setText("탐색기 열기 실패: " + path);
            }
        }

        private void cancelCurrentWorker() {
            if (currentWorker != null && !currentWorker.isDone()) {
                currentWorker.cancel(true);
            }
        }

        private static final Comparator<Path> PATH_BY_NAME = Comparator.comparing(p -> {
            Path fn = p.getFileName();
            String s = (fn == null ? p.toString() : fn.toString());
            return s.toLowerCase(Locale.ROOT);
        });

        private static String fileNameOrPath(Path p) {
            Path fn = p.getFileName();
            return fn == null ? p.toString() : fn.toString();
        }

        private static boolean hasAnyChildEntry(Path dir) {
            try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir)) {
                return ds.iterator().hasNext();
            } catch (Exception ignored) {
                return false;
            }
        }
    }

    // ---------------- 트리 노드 / 렌더러 ----------------

    static class FolderNode extends DefaultMutableTreeNode {
        final Path path;
        final boolean isDirectory;
        boolean childrenLoaded = false;

        private FolderNode(String label) {
            this.path = null;
            this.isDirectory = true;
            setUserObject(label);
        }

        FolderNode(Path path) {
            this.path = path;
            this.isDirectory = Files.isDirectory(path);
            setUserObject(path.toString());
        }

        static FolderNode createVirtualRoot(String label) {
            return new FolderNode(label);
        }

        boolean isVirtual() {
            return path == null;
        }

        @Override
        public String toString() {
            if (path == null) return String.valueOf(getUserObject());
            if (path.getParent() == null) return path.toString();
            Path name = path.getFileName();
            return (name == null) ? path.toString() : name.toString();
        }
    }

    static class OsIconTreeCellRenderer extends DefaultTreeCellRenderer {
        private final FileSystemView fsv = FileSystemView.getFileSystemView();
        private final Map<String, Icon> iconCache = new HashMap<>();

        @Override
        public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel,
                                                      boolean expanded, boolean leaf, int row, boolean hasFocus) {
            super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);

            if (value instanceof FolderNode node) {
                setText(node.toString());

                if (!node.isVirtual()) {
                    setToolTipText(node.path.toString());
                    Icon icon = getSystemIconCached(node.path, node.isDirectory);
                    if (icon != null) setIcon(icon);
                } else {
                    setToolTipText(null);
                    Icon icon = UIManager.getIcon("FileView.computerIcon");
                    if (icon != null) setIcon(icon);
                }
                return this;
            }

            if (value instanceof DefaultMutableTreeNode dmtn) {
                if (LOADING_NODE_TEXT.equals(String.valueOf(dmtn.getUserObject()))) {
                    setText(LOADING_NODE_TEXT);
                    setIcon(UIManager.getIcon("Tree.closedIcon"));
                }
            }

            return this;
        }

        private Icon getSystemIconCached(Path path, boolean isDir) {
            try {
                String key;
                if (isDir) {
                    key = (path.getParent() == null) ? "DRIVE:" + path.toString() : "DIR";
                } else {
                    String name = path.getFileName() == null ? path.toString() : path.getFileName().toString();
                    int dot = name.lastIndexOf('.');
                    String ext = (dot >= 0 && dot < name.length() - 1)
                            ? name.substring(dot + 1).toLowerCase(Locale.ROOT)
                            : "";
                    key = "FILE:" + ext;
                }

                Icon cached = iconCache.get(key);
                if (cached != null) return cached;

                Icon sysIcon = fsv.getSystemIcon(path.toFile());
                if (sysIcon != null) iconCache.put(key, sysIcon);
                return sysIcon;

            } catch (Exception ignored) {
                return null;
            }
        }
    }

    // ---------------- 데이터/테마 ----------------

    public static class SizeItem {
        public final String name;
        public final long bytes;
        public final boolean isDirectory;

        public SizeItem(String name, long bytes, boolean isDirectory) {
            this.name = name;
            this.bytes = bytes;
            this.isDirectory = isDirectory;
        }
    }

    public static class ThemeColors {
        public final Color fg;
        public final Color muted;
        public final Color line;
        public final Color dirBar;
        public final Color fileBar;

        private ThemeColors(Color fg, Color muted, Color line, Color dirBar, Color fileBar) {
            this.fg = fg;
            this.muted = muted;
            this.line = line;
            this.dirBar = dirBar;
            this.fileBar = fileBar;
        }

        public static ThemeColors fromUI() {
            Color fg = UIManager.getColor("Label.foreground");
            Color muted = UIManager.getColor("Label.disabledForeground");
            if (muted == null && fg != null) muted = fg.darker();

            Color line = UIManager.getColor("Separator.foreground");
            if (line == null) line = UIManager.getColor("Component.borderColor");
            if (line == null) line = muted;

            Color dirBar = UIManager.getColor("Actions.Blue");
            if (dirBar == null) dirBar = new Color(80, 140, 220);

            Color fileBar = UIManager.getColor("Actions.Green");
            if (fileBar == null) fileBar = new Color(120, 200, 160);

            return new ThemeColors(fg, muted, line, dirBar, fileBar);
        }
    }

    // ---------------- 스캔 워커 ----------------

    static class SizeScanWorker extends SwingWorker<List<SizeItem>, SizeItem> {

        interface Callback {
            void onPartial(SizeItem item);
            void onDone(List<SizeItem> finalItems, long totalBytes, long scannedFiles);
            void onCancelled();
            void onError(Exception ex);
        }

        private final Path folder;
        private final Callback cb;

        private long totalBytes = 0;
        private long scannedFiles = 0;

        SizeScanWorker(Path folder, Callback cb) {
            this.folder = folder;
            this.cb = cb;
        }

        @Override
        protected List<SizeItem> doInBackground() {
            List<Path> childrenDirs = new ArrayList<>();
            List<Path> childrenFiles = new ArrayList<>();

            try (DirectoryStream<Path> ds = Files.newDirectoryStream(folder)) {
                for (Path p : ds) {
                    if (isCancelled()) return Collections.emptyList();
                    try {
                        if (Files.isDirectory(p)) childrenDirs.add(p);
                        else childrenFiles.add(p);
                    } catch (Exception ignored) {
                    }
                }
            } catch (IOException | SecurityException ignored) {
            }

            childrenFiles.sort(MainFrame.PATH_BY_NAME);
            childrenDirs.sort(MainFrame.PATH_BY_NAME);

            List<SizeItem> result = new ArrayList<>();

            for (Path f : childrenFiles) {
                if (isCancelled()) return Collections.emptyList();

                long sz = 0;
                try {
                    if (Files.isRegularFile(f)) {
                        sz = Files.size(f);
                        scannedFiles++;
                    }
                } catch (Exception ignored) {
                }

                String name = MainFrame.fileNameOrPath(f);
                SizeItem item = new SizeItem(name, sz, false);

                result.add(item);
                totalBytes += sz;
                publish(item);
            }

            for (Path d : childrenDirs) {
                if (isCancelled()) return Collections.emptyList();

                long dirBytes = folderSizeRecursive(d);
                String name = MainFrame.fileNameOrPath(d);
                SizeItem item = new SizeItem(name, dirBytes, true);

                result.add(item);
                totalBytes += dirBytes;
                publish(item);
            }

            return result;
        }

        @Override
        protected void process(List<SizeItem> chunks) {
            for (SizeItem it : chunks) cb.onPartial(it);
        }

        @Override
        protected void done() {
            try {
                if (isCancelled()) {
                    cb.onCancelled();
                    return;
                }
                cb.onDone(get(), totalBytes, scannedFiles);
            } catch (CancellationException ce) {
                cb.onCancelled();
            } catch (Exception ex) {
                cb.onError(ex);
            }
        }

        private long folderSizeRecursive(Path start) {
            final long[] sum = {0L};

            try {
                Files.walkFileTree(start, new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                        return isCancelled() ? FileVisitResult.TERMINATE : FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                        if (isCancelled()) return FileVisitResult.TERMINATE;

                        try {
                            if (attrs.isRegularFile()) {
                                sum[0] += attrs.size();
                                scannedFiles++;
                            }
                        } catch (Exception ignored) {
                        }
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFileFailed(Path file, IOException exc) {
                        return isCancelled() ? FileVisitResult.TERMINATE : FileVisitResult.CONTINUE;
                    }
                });
            } catch (IOException | SecurityException ignored) {
            }

            return sum[0];
        }
    }

    // ---------------- 유틸 ----------------

    public static String human(long bytes) {
        if (bytes < 1024) return bytes + " B";
        double b = bytes;
        String[] u = {"KB", "MB", "GB", "TB"};
        int i = -1;
        while (b >= 1024 && i < u.length - 1) {
            b /= 1024.0;
            i++;
        }
        if (i < 0) return bytes + " B";
        DecimalFormat df = new DecimalFormat("#,##0.0");
        return df.format(b) + " " + u[i];
    }
}
