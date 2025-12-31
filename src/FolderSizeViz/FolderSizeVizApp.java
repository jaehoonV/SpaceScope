package FolderSizeViz;

import Utils.LanguageUtil;
import Utils.LocaleManager;
import Utils.SizeFormatUtil;
import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.intellijthemes.materialthemeuilite.FlatMTArcDarkIJTheme;

import javax.swing.*;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeWillExpandListener;
import javax.swing.filechooser.FileSystemView;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.List;
import java.util.concurrent.CancellationException;

public class FolderSizeVizApp {

    private static final String APP_VERSION = "2.0.0";
    private static final String APP_AUTHOR = "LEE JAEHOON";

    private static final String LOADING_NODE_TEXT = "loading...";

    private static final int FRAME_W = 1300;
    private static final int FRAME_H = 700;

    private static final int TREE_MIN_W = 360;
    private static final double SPLIT_RESIZE_WEIGHT = 0.32;

    private static long scanToken = 0;

    private static final Comparator<Path> PATH_BY_NAME = Comparator.comparing(p -> {
        Path fn = p.getFileName();
        String s = (fn == null ? p.toString() : fn.toString());
        return s.toLowerCase(Locale.ROOT);
    });

    public static void main(String[] args) {
        LanguageUtil.init();

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

        private final DetailChartPanel detailPanel = new DetailChartPanel();
        private final PieChartTabPanel pieChartPanel = new PieChartTabPanel();

        private final JTabbedPane rightTabs = new JTabbedPane();

        private final JLabel statusLabel = new JLabel(LanguageUtil.ln("status.ready"));
        private final JProgressBar progressBar = new JProgressBar();

        private SizeScanWorker currentWorker;

        private ExportCsvWorker exportWorker;

        private final List<SizeItem> latestItems = new ArrayList<>();
        private Path latestFolder;

        MainFrame() {
            super("📁 " + LanguageUtil.ln("app.title"));
            setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            setSize(FRAME_W, FRAME_H);
            setLocationRelativeTo(null);

            setJMenuBar(buildMenuBar());

            FolderNode rootNode = createDrivesRootNode();
            treeModel = new DefaultTreeModel(rootNode);

            tree = createTree(treeModel);
            installTreeListeners();

            setLayout(new BorderLayout());
            add(buildSplitPane(), BorderLayout.CENTER);

            // 클릭 → 트리 선택 + 재스캔 공통 처리
            detailPanel.setOnItemClick(this::selectAndScan);
            pieChartPanel.setOnSliceClick(this::selectAndScan);
        }

        private void selectAndScan(Path path) {
            if (path == null) return;
            selectPathInTree(path);
            scanAndUpdateUI(path);
        }

        private JMenuBar buildMenuBar() {
            JMenuBar menuBar = new JMenuBar();

            JMenu settingsMenu = new JMenu(LanguageUtil.ln("menu.settings"));
            JMenu languageMenu = new JMenu(LanguageUtil.ln("menu.settings.language"));
            JMenuItem koreanItem = new JMenuItem(LanguageUtil.ln("menu.settings.language.korean"));
            JMenuItem englishItem = new JMenuItem(LanguageUtil.ln("menu.settings.language.english"));

            koreanItem.addActionListener(e -> switchLanguage(Locale.KOREAN));
            englishItem.addActionListener(e -> switchLanguage(Locale.ENGLISH));

            languageMenu.add(koreanItem);
            languageMenu.add(englishItem);

            JMenuItem aboutItem = new JMenuItem(LanguageUtil.ln("menu.settings.about"));
            aboutItem.addActionListener(e -> showAbout());

            settingsMenu.add(languageMenu);
            settingsMenu.addSeparator();
            settingsMenu.add(aboutItem);

            menuBar.add(settingsMenu);

            JMenu fileMenu = new JMenu(LanguageUtil.ln("menu.file"));
            JMenuItem exportItem = new JMenuItem(LanguageUtil.ln("menu.file.export"));
            exportItem.addActionListener(e -> onExportClicked());
            fileMenu.add(exportItem);
            menuBar.add(fileMenu);

            return menuBar;
        }

        private void onExportClicked() {
            if (latestFolder == null) {
                JOptionPane.showMessageDialog(
                        this,
                        LanguageUtil.ln("status.no_selection"),
                        LanguageUtil.ln("menu.file.export"),
                        JOptionPane.WARNING_MESSAGE
                );
                return;
            }

            if (exportWorker != null && !exportWorker.isDone()) {
                JOptionPane.showMessageDialog(
                        this,
                        LanguageUtil.ln("status.export_running"),
                        LanguageUtil.ln("menu.file.export"),
                        JOptionPane.WARNING_MESSAGE
                );
                return;
            }

            JFileChooser fc = new JFileChooser();
            fc.setDialogTitle(LanguageUtil.ln("export.dialog_title"));

            String base = (latestFolder.getFileName() == null) ? "folder_size" : latestFolder.getFileName().toString();
            fc.setSelectedFile(new File(base + "_folder_size_report.csv"));

            int result = fc.showSaveDialog(this);
            if (result != JFileChooser.APPROVE_OPTION) return;

            File outFile = fc.getSelectedFile();

            progressBar.setVisible(true);
            progressBar.setIndeterminate(false);
            progressBar.setMinimum(0);
            progressBar.setMaximum(100);
            progressBar.setValue(0);
            progressBar.setStringPainted(true);
            progressBar.setString(LanguageUtil.ln("status.export_preparing"));
            statusLabel.setText(LanguageUtil.ln("status.export_start") + " : " + latestFolder);

            boolean includeFiles = true;

            exportWorker = new ExportCsvWorker(latestFolder, outFile, includeFiles);
            exportWorker.addPropertyChangeListener(evt -> {
                if ("progress".equals(evt.getPropertyName())) {
                    int p = (int) evt.getNewValue();
                    progressBar.setValue(p);
                    progressBar.setString(LanguageUtil.ln("status.exporting") + " " + p + "%");
                }
            });
            exportWorker.execute();

        }

        private void exportLatestToCsv(File outFile) throws IOException {
            List<export.CsvExportRow> rows = buildRowsFromLatest();
            export.CsvExportService.write(outFile, rows);
        }

        private List<export.CsvExportRow> buildRowsFromLatest() {
            List<export.CsvExportRow> rows = new ArrayList<>();

            if (latestFolder == null) return rows;

            boolean rootIsDir = false;
            try { rootIsDir = Files.isDirectory(latestFolder); } catch (Exception ignored) {}

            long totalBytes = 0L;
            for (SizeItem it : latestItems) totalBytes += Math.max(0L, it.bytes);

            Date rootDate = safeDate(latestFolder);

            // Root row
            long rootSize = rootIsDir ? totalBytes : (!latestItems.isEmpty() ? latestItems.get(0).bytes : 0L);
            rows.add(new export.CsvExportRow(
                    rootIsDir ? "Folder" : "File",
                    0,
                    latestFolder.toAbsolutePath().normalize().toString(),
                    rootSize,
                    SizeFormatUtil.human(rootSize),
                    rootDate
            ));

            // Children rows (Depth=1)
            for (SizeItem it : latestItems) {
                rows.add(new export.CsvExportRow(
                        it.isDirectory ? "Folder" : "File",
                        1,
                        it.path.toAbsolutePath().normalize().toString(),
                        it.bytes,
                        SizeFormatUtil.human(it.bytes),
                        safeDate(it.path)
                ));
            }

            return rows;
        }

        private static Date safeDate(Path p) {
            try {
                return new Date(Files.getLastModifiedTime(p).toMillis());
            } catch (Exception ignored) {
                return new Date(0L);
            }
        }

        private void switchLanguage(Locale newLocale) {
            LocaleManager.saveLocale(newLocale);
            dispose();
            LanguageUtil.setLocale(newLocale);

            SwingUtilities.invokeLater(() -> {
                setupDarkTheme();
                new MainFrame().setVisible(true);
            });
        }

        private void showAbout() {
            String message = String.format("""
                    📁 %s
                    Version %s
                    © 2025 %s
                    """, LanguageUtil.ln("app.title"), APP_VERSION, APP_AUTHOR);

            JOptionPane.showMessageDialog(
                    this,
                    message,
                    LanguageUtil.ln("menu.settings.about"),
                    JOptionPane.INFORMATION_MESSAGE
            );
        }

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

            JScrollPane pieScroll = new JScrollPane(pieChartPanel);
            pieScroll.setBorder(BorderFactory.createEmptyBorder());
            pieScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
            pieScroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER);

            rightTabs.addTab(LanguageUtil.ln("tab.detail"), detailScroll);
            rightTabs.addTab(LanguageUtil.ln("tab.chart"), pieScroll);

            rightTabs.addChangeListener(e -> {
                if (rightTabs.getSelectedIndex() == 1) refreshPieChartFromLatest();
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

        private void installTreeListeners() {
            tree.addTreeWillExpandListener(new TreeWillExpandListener() {
                @Override
                public void treeWillExpand(TreeExpansionEvent event) {
                    Object last = event.getPath().getLastPathComponent();
                    if (last instanceof FolderNode node) ensureChildrenLoaded(node);
                }

                @Override
                public void treeWillCollapse(TreeExpansionEvent event) {
                }
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
            FolderNode root = FolderNode.createVirtualRoot(LanguageUtil.ln("tree.root"));

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
            if (!node.isDirectory || node.isVirtual() || node.childrenLoaded) return;

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

        private void showFileInfo(Path file) {
            cancelCurrentWorker();
            ++scanToken;

            progressBar.setVisible(false);

            String name = fileNameOrPath(file);
            statusLabel.setText(LanguageUtil.ln("status.file_selected") + " : " + file);

            long size = 0L;
            try {
                if (Files.isRegularFile(file)) size = Files.size(file);
            } catch (Exception ignored) {
            }

            SizeItem single = new SizeItem(name, size, false, file);
            List<SizeItem> singleList = List.of(single);

            latestFolder = file;
            latestItems.clear();
            latestItems.addAll(singleList);

            detailPanel.setTitle(LanguageUtil.ln("label.file") + " : " + name);
            detailPanel.setItems(singleList);

            Path openPath = file.getParent();
            if (openPath == null) openPath = file.getRoot(); // 루트 방어

            detailPanel.setTitleClickTarget(openPath);
            Path finalOpenPath = openPath;
            detailPanel.setOnTitleClick(() -> openInExplorer(finalOpenPath));

            pieChartPanel.setTitle(LanguageUtil.ln("label.file") + " : " + name);
            pieChartPanel.setTitleClickTarget(openPath);
            pieChartPanel.setOnTitleClick(() -> openInExplorer(finalOpenPath));
            pieChartPanel.setItemsTop10(singleList);
        }

        private void selectPathInTree(Path target) {
            if (target == null) return;

            Path toSelect = target;
            try {
                if (!Files.isDirectory(target)) {
                    Path parent = target.getParent();
                    if (parent != null) toSelect = parent;
                }
            } catch (Exception ignored) {
            }

            FolderNode root = (FolderNode) treeModel.getRoot();

            Path drive = toSelect.getRoot();
            if (drive == null) return;

            FolderNode driveNode = null;
            for (int i = 0; i < root.getChildCount(); i++) {
                Object ch = root.getChildAt(i);
                if (ch instanceof FolderNode fn && !fn.isVirtual() && drive.equals(fn.path)) {
                    driveNode = fn;
                    break;
                }
            }
            if (driveNode == null) return;

            javax.swing.tree.TreePath tp = new javax.swing.tree.TreePath(driveNode.getPath());
            tree.expandPath(tp);
            ensureChildrenLoaded(driveNode);

            Path curPath = drive;
            FolderNode curNode = driveNode;

            for (Path namePart : drive.relativize(toSelect)) {
                curPath = curPath.resolve(namePart);

                ensureChildrenLoaded(curNode);

                FolderNode next = null;
                for (int i = 0; i < curNode.getChildCount(); i++) {
                    Object ch = curNode.getChildAt(i);
                    if (ch instanceof FolderNode fn && !fn.isVirtual() && curPath.equals(fn.path)) {
                        next = fn;
                        break;
                    }
                }
                if (next == null) break;

                curNode = next;
                tp = tp.pathByAddingChild(curNode);
                tree.expandPath(tp);
            }

            tree.setSelectionPath(tp);
            tree.scrollPathToVisible(tp);
        }

        private void scanAndUpdateUI(Path path) {
            if (path == null) return;

            try {
                if (Files.isDirectory(path)) startScan(path);
                else showFileInfo(path);
            } catch (Exception ex) {
                statusLabel.setText(LanguageUtil.ln("status.error") + " : " + path);
            }
        }

        private void startScan(Path folder) {
            cancelCurrentWorker();

            final long myToken = ++scanToken;

            latestFolder = folder;
            latestItems.clear();

            statusLabel.setText(LanguageUtil.ln("status.scan_start") + " : " + folder);

            progressBar.setIndeterminate(true);
            progressBar.setString(LanguageUtil.ln("status.scanning"));
            progressBar.setVisible(true);

            applySelectionToDetail(LanguageUtil.ln("info.folder") + " : " + folder, folder);
            applySelectionToPie(LanguageUtil.ln("info.folder") + " : " + folder, folder);
            pieChartPanel.clear();

            currentWorker = new SizeScanWorker(folder, new SizeScanWorker.Callback() {
                @Override
                public void onPartial(SizeItem item) {
                    if (myToken != scanToken) return;

                    detailPanel.upsertItem(item);
                    upsertLatest(item);

                    if (rightTabs.getSelectedIndex() == 1) {
                        pieChartPanel.setItemsTop10(latestItems);
                    }
                }

                @Override
                public void onDone(List<SizeItem> finalItems, long totalBytes, long scannedFiles) {
                    if (myToken != scanToken) return;

                    progressBar.setVisible(false);
                    statusLabel.setText(
                            LanguageUtil.ln("status.done") + " : " + folder
                                    + " / " + LanguageUtil.ln("info.total_size") + " : " + SizeFormatUtil.human(totalBytes)
                                    + " / " + LanguageUtil.ln("label.file") + " : " + scannedFiles
                    );

                    detailPanel.setItems(finalItems);

                    latestItems.clear();
                    latestItems.addAll(finalItems);

                    refreshPieChartFromLatest();
                }

                @Override
                public void onCancelled() {
                    if (myToken != scanToken) return;

                    progressBar.setVisible(false);
                    statusLabel.setText(LanguageUtil.ln("status.scan_cancelled") + " : " + folder);
                }

                @Override
                public void onError(Exception ex) {
                    if (myToken != scanToken) return;

                    progressBar.setVisible(false);
                    statusLabel.setText(
                            LanguageUtil.ln("status.error") + " : " + ex.getClass().getSimpleName() + " - " + ex.getMessage()
                    );
                }
            });

            currentWorker.execute();
        }

        private void applySelectionToDetail(String title, Path openPath) {
            detailPanel.setTitle(title);
            detailPanel.setItems(Collections.emptyList());
            detailPanel.setTitleClickTarget(openPath);
            detailPanel.setOnTitleClick(() -> openInExplorer(openPath));
        }

        private void applySelectionToPie(String title, Path openPath) {
            pieChartPanel.setTitle(title);
            pieChartPanel.setTitleClickTarget(openPath);
            pieChartPanel.setOnTitleClick(() -> openInExplorer(openPath));
        }

        private void refreshPieChartFromLatest() {
            if (latestFolder == null) {
                pieChartPanel.setTitle(LanguageUtil.ln("label.none_selected"));
                pieChartPanel.setTitleClickTarget(null);
                pieChartPanel.setOnTitleClick(null);
                pieChartPanel.clear();
                return;
            }

            boolean isDir;
            try {
                isDir = Files.isDirectory(latestFolder);
            } catch (Exception ignored) {
                isDir = false;
            }

            if (isDir) {
                pieChartPanel.setTitle(LanguageUtil.ln("info.folder") + " : " + latestFolder);
                pieChartPanel.setTitleClickTarget(latestFolder);
                pieChartPanel.setOnTitleClick(() -> openInExplorer(latestFolder));
                pieChartPanel.setItemsTop10(latestItems);
                return;
            }

            String name = fileNameOrPath(latestFolder);
            Path openPath = latestFolder.getParent();
            if (openPath == null) openPath = latestFolder.getRoot();

            pieChartPanel.setTitle(LanguageUtil.ln("label.file") + " : " + name);
            pieChartPanel.setTitleClickTarget(openPath);
            Path finalOpenPath = openPath;
            pieChartPanel.setOnTitleClick(() -> openInExplorer(finalOpenPath));
            pieChartPanel.setItemsTop10(latestItems);
        }

        private void upsertLatest(SizeItem item) {
            for (int i = 0; i < latestItems.size(); i++) {
                SizeItem cur = latestItems.get(i);
                if (cur.isDirectory == item.isDirectory && Objects.equals(cur.name, item.name)) {
                    latestItems.set(i, item);
                    return;
                }
            }
            latestItems.add(item);
        }

        private void openInExplorer(Path path) {
            if (path == null) return;

            Path p = path.toAbsolutePath().normalize();

            // Desktop API 우선
            try {
                if (Desktop.isDesktopSupported()) {
                    Desktop.getDesktop().open(p.toFile());
                    return;
                }
            } catch (Exception ignored) {
            }

            // Explorer fallback: 파일이면 /select 사용
            try {
                boolean isDir = false;
                try {
                    isDir = Files.isDirectory(p);
                } catch (Exception ignored) {
                }

                ProcessBuilder pb = isDir
                        ? new ProcessBuilder("explorer.exe", p.toString())
                        : new ProcessBuilder("explorer.exe", "/select," + p.toString());

                pb.start();

            } catch (Exception ex) {
                statusLabel.setText(LanguageUtil.ln("error.explorer_open_failed") + " : " + p);
            }
        }

        private void cancelCurrentWorker() {
            if (currentWorker != null && !currentWorker.isDone()) currentWorker.cancel(true);
        }

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

        private final class ExportCsvWorker extends SwingWorker<Void, Void> {

            private final Path root;
            private final File outFile;
            private final boolean includeFiles;

            ExportCsvWorker(Path root, File outFile, boolean includeFiles) {
                this.root = root;
                this.outFile = outFile;
                this.includeFiles = includeFiles;
            }

            @Override
            protected Void doInBackground() throws Exception {
                // 결정형 progress를 위해 2-pass
                // 1) 총 엔트리 수 계산 (폴더/파일 row 개수)
                int total = export.CsvRecursiveExportService.countEntries(root, includeFiles);
                if (total <= 0) total = 1;

                // 2) 실제 export (done 증가시마다 progress 갱신)
                int finalTotal = total;
                export.CsvRecursiveExportService.exportWithProgress(
                        root,
                        outFile,
                        includeFiles,
                        total,
                        done -> {
                            int pct = (int) Math.min(100, Math.round(done * 100.0 / finalTotal));
                            setProgress(pct);
                        }
                );

                return null;
            }

            @Override
            protected void done() {
                try {
                    get(); // 예외 있으면 던짐

                    progressBar.setValue(100);
                    progressBar.setString(LanguageUtil.ln("status.export_done") + " 100%");
                    progressBar.setVisible(false);

                    statusLabel.setText(LanguageUtil.ln("status.export_done") + " : " + outFile.getAbsolutePath());

                    JOptionPane.showMessageDialog(
                            MainFrame.this,
                            LanguageUtil.ln("status.csv_done") + "\n" + outFile.getAbsolutePath(),
                            LanguageUtil.ln("menu.file.export"),
                            JOptionPane.INFORMATION_MESSAGE
                    );

                } catch (Exception ex) {
                    progressBar.setVisible(false);
                    statusLabel.setText(LanguageUtil.ln("status.error") + " : " + ex.getMessage());

                    JOptionPane.showMessageDialog(
                            MainFrame.this,
                            LanguageUtil.ln("status.error") + " : " + ex.getMessage(),
                            LanguageUtil.ln("menu.file.export"),
                            JOptionPane.ERROR_MESSAGE
                    );
                }
            }
        }

    }

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
                    key = (path.getParent() == null) ? "DRIVE:" + path : "DIR";
                } else {
                    String name = (path.getFileName() == null) ? path.toString() : path.getFileName().toString();
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

    public static class SizeItem {
        public final String name;
        public final long bytes;
        public final boolean isDirectory;
        public final Path path;

        public SizeItem(String name, long bytes, boolean isDirectory, Path path) {
            this.name = name;
            this.bytes = bytes;
            this.isDirectory = isDirectory;
            this.path = path;
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

            childrenFiles.sort(PATH_BY_NAME);
            childrenDirs.sort(PATH_BY_NAME);

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

                SizeItem item = new SizeItem(MainFrame.fileNameOrPath(f), sz, false, f);
                result.add(item);
                totalBytes += sz;
                publish(item);
            }

            for (Path d : childrenDirs) {
                if (isCancelled()) return Collections.emptyList();

                long dirBytes = folderSizeRecursive(d);
                SizeItem item = new SizeItem(MainFrame.fileNameOrPath(d), dirBytes, true, d);

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
}
