package FolderSizeViz;

import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.intellijthemes.materialthemeuilite.FlatMTArcDarkIJTheme;

import javax.swing.*;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeWillExpandListener;
import javax.swing.filechooser.FileSystemView;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
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

    private static final int CHART_LEFT_PADDING = 24;

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

        private final ChartPanel chartPanel = new ChartPanel();
        private final JLabel statusLabel = new JLabel("준비됨");
        private final JProgressBar progressBar = new JProgressBar();

        private SizeScanWorker currentWorker;

        MainFrame() {
            super(APP_TITLE);
            setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            setSize(FRAME_W, FRAME_H);
            setLocationRelativeTo(null);

            FolderNode rootNode = createDrivesRootNode();
            treeModel = new DefaultTreeModel(rootNode);

            tree = new JTree(treeModel);
            tree.setRootVisible(false);
            tree.setShowsRootHandles(true);
            tree.setCellRenderer(new OsIconTreeCellRenderer());

            tree.addTreeWillExpandListener(new TreeWillExpandListener() {
                @Override
                public void treeWillExpand(TreeExpansionEvent event) {
                    Object last = event.getPath().getLastPathComponent();
                    if (last instanceof FolderNode node) {
                        ensureChildrenLoaded(node);
                    }
                }
                @Override public void treeWillCollapse(TreeExpansionEvent event) {}
            });

            tree.addTreeSelectionListener(e -> {
                Object sel = tree.getLastSelectedPathComponent();
                if (!(sel instanceof FolderNode node)) return;
                if (node.isVirtual()) return;

                if (node.isDirectory) startScan(node.path);
                else showFileInfo(node.path);
            });

            JScrollPane leftScroll = new JScrollPane(tree);
            leftScroll.setMinimumSize(new Dimension(TREE_MIN_W, 0));
            leftScroll.setBorder(BorderFactory.createEmptyBorder());
            tree.setBorder(BorderFactory.createEmptyBorder());

            JScrollPane chartScroll = new JScrollPane(chartPanel);
            chartScroll.setBorder(BorderFactory.createEmptyBorder());

            JPanel right = new JPanel(new BorderLayout());
            right.add(chartScroll, BorderLayout.CENTER);
            right.add(buildBottomBar(), BorderLayout.SOUTH);

            JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftScroll, right);
            splitPane.setResizeWeight(SPLIT_RESIZE_WEIGHT);

            setLayout(new BorderLayout());
            add(splitPane, BorderLayout.CENTER);
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

        private void showFileInfo(Path file) {
            cancelCurrentWorker();
            progressBar.setVisible(false);

            String name = fileNameOrPath(file);

            statusLabel.setText("파일 선택: " + file);
            chartPanel.setTitle("파일: " + name);
            chartPanel.setItems(Collections.emptyList());

            Path openPath = file.getParent();
            chartPanel.setTitleClickTarget(openPath);
            chartPanel.setOnTitleClick(() -> openInExplorer(openPath));
        }

        private void startScan(Path folder) {
            cancelCurrentWorker();

            statusLabel.setText("스캔 시작: " + folder);
            progressBar.setIndeterminate(true);
            progressBar.setString("스캔 중...");
            progressBar.setVisible(true);

            chartPanel.setTitle("폴더: " + folder);
            chartPanel.setItems(Collections.emptyList());

            chartPanel.setTitleClickTarget(folder);
            chartPanel.setOnTitleClick(() -> openInExplorer(folder));

            currentWorker = new SizeScanWorker(folder, new SizeScanWorker.Callback() {
                @Override public void onPartial(SizeItem item) {
                    chartPanel.upsertItem(item);
                }

                @Override public void onDone(List<SizeItem> finalItems, long totalBytes, long scannedFiles) {
                    progressBar.setVisible(false);
                    statusLabel.setText("완료: " + folder + " / 총 " + human(totalBytes) + " / 파일 " + scannedFiles + "개");
                    chartPanel.setItems(finalItems);
                }

                @Override public void onCancelled() {
                    progressBar.setVisible(false);
                    statusLabel.setText("스캔 취소됨: " + folder);
                }

                @Override public void onError(Exception ex) {
                    progressBar.setVisible(false);
                    statusLabel.setText("오류: " + ex.getClass().getSimpleName() + " - " + ex.getMessage());
                }
            });

            currentWorker.execute();
        }

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

    static class SizeItem {
        final String name;
        final long bytes;
        final boolean isDirectory;

        SizeItem(String name, long bytes, boolean isDirectory) {
            this.name = name;
            this.bytes = bytes;
            this.isDirectory = isDirectory;
        }
    }

    static class ChartPanel extends JPanel implements Scrollable {

        private static final int PAD = 14;
        private static final int TITLE_H = 30;

        private static final int LABEL_H = 14;
        private static final int GAP1 = 4;
        private static final int BAR_H = 10;
        private static final int GAP2 = 10;
        private static final int ITEM_H = LABEL_H + GAP1 + BAR_H + GAP2;

        private static final int AFTER_TITLE_GAP = 8;
        private static final int AFTER_TITLE_LINE_OFFSET = 6;

        private String title = "선택 없음";
        private List<SizeItem> items = new ArrayList<>();

        private Path titleClickTarget;
        private Runnable onTitleClick;

        private final Rectangle titleTextBounds = new Rectangle();
        private boolean titleHover = false;

        ChartPanel() {
            setOpaque(true);
            setBackground(UIManager.getColor("Panel.background"));
            setForeground(UIManager.getColor("Label.foreground"));
            setBorder(BorderFactory.createEmptyBorder(PAD, PAD, PAD, PAD));

            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (!isTitleClickable()) return;
                    if (titleTextBounds.contains(e.getPoint())) {
                        onTitleClick.run();
                    }
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    titleHover = false;
                    setCursor(Cursor.getDefaultCursor());
                    setToolTipText(null);
                }
            });

            addMouseMotionListener(new MouseAdapter() {
                @Override
                public void mouseMoved(MouseEvent e) {
                    if (!isTitleClickable()) {
                        if (titleHover) {
                            titleHover = false;
                            setCursor(Cursor.getDefaultCursor());
                            setToolTipText(null);
                            repaint();
                        }
                        return;
                    }

                    boolean hit = titleTextBounds.contains(e.getPoint());
                    if (hit != titleHover) {
                        titleHover = hit;
                        repaint();
                    }

                    if (hit) {
                        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                        setToolTipText(buildTitleTooltip());
                    } else {
                        setCursor(Cursor.getDefaultCursor());
                        setToolTipText(null);
                    }
                }
            });

            updatePreferredSize();
        }

        private boolean isTitleClickable() {
            return titleClickTarget != null && onTitleClick != null;
        }

        private String buildTitleTooltip() {
            if (titleClickTarget == null) return null;
            return titleClickTarget.toString() + " 폴더로 이동";
        }

        void setTitle(String title) {
            this.title = title;
            updatePreferredSize();
            repaint();
        }

        void setTitleClickTarget(Path path) {
            this.titleClickTarget = path;
            repaint();
        }

        void setOnTitleClick(Runnable r) {
            this.onTitleClick = r;
            repaint();
        }

        void setItems(List<SizeItem> newItems) {
            items = sortedCopy(newItems);
            updatePreferredSize();
            repaint();
        }

        void upsertItem(SizeItem item) {
            boolean replaced = false;

            for (int i = 0; i < items.size(); i++) {
                SizeItem cur = items.get(i);
                if (cur.isDirectory == item.isDirectory && cur.name.equals(item.name)) {
                    items.set(i, item);
                    replaced = true;
                    break;
                }
            }

            if (!replaced) items.add(item);
            items.sort((a, b) -> Long.compare(b.bytes, a.bytes));

            updatePreferredSize();
            repaint();
        }

        private static List<SizeItem> sortedCopy(List<SizeItem> src) {
            List<SizeItem> sorted = new ArrayList<>(src);
            sorted.sort((a, b) -> Long.compare(b.bytes, a.bytes));
            return sorted;
        }

        private void updatePreferredSize() {
            int header = TITLE_H + AFTER_TITLE_GAP;
            int totalH = Math.max(200, header + (items.size() * ITEM_H));
            setPreferredSize(new Dimension(10, totalH));
            revalidate();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                ThemeColors c = ThemeColors.fromUI();

                int w = getWidth();
                int y = 0;

                Font titleFont = getFont().deriveFont(Font.BOLD, 15f);
                g2.setFont(titleFont);

                FontMetrics fm = g2.getFontMetrics();
                int baselineY = y + 18;

                String titleText = title;

                int textW = fm.stringWidth(titleText);
                int textH = fm.getHeight();
                int textTop = baselineY - fm.getAscent();

                titleTextBounds.setBounds(0, textTop, textW, textH);

                g2.setColor(c.fg);
                g2.drawString(titleText, 0, baselineY);

                if (isTitleClickable() && titleHover) {
                    g2.setColor(c.muted);
                    int underlineY = baselineY + 2;
                    g2.drawLine(0, underlineY, Math.max(0, textW), underlineY);
                }

                y += TITLE_H;

                g2.setColor(c.line);
                g2.drawLine(0, y - AFTER_TITLE_LINE_OFFSET, w, y - AFTER_TITLE_LINE_OFFSET);
                y += AFTER_TITLE_GAP;

                int chartTop = y;

                if (items.isEmpty()) {
                    g2.setColor(c.muted);
                    g2.drawString("폴더를 선택하면 하위 폴더/파일 용량을 함께 표시합니다. 파일을 선택하면 파일 크기만 표시합니다.", 0, chartTop + 30);
                    return;
                }

                long max = items.stream().mapToLong(it -> it.bytes).max().orElse(1L);
                int barX = CHART_LEFT_PADDING;
                int barAreaWidth = w - CHART_LEFT_PADDING - 10;
                int tagX = CHART_LEFT_PADDING - 10;

                int yy = chartTop;

                for (SizeItem it : items) {
                    double ratio = (max == 0) ? 0.0 : (double) it.bytes / (double) max;
                    int barW = (int) (barAreaWidth * ratio);

                    g2.setColor(c.fg);
                    String tag = it.isDirectory ? "[DIR] " : "[FILE] ";
                    String label = tag + trimMiddle(it.name, 42) + "  (" + human(it.bytes) + ")";
                    g2.drawString(label, tagX, yy + 12);

                    int barY = yy + LABEL_H + GAP1;
                    g2.setColor(it.isDirectory ? c.dirBar : c.fileBar);
                    g2.fillRoundRect(barX, barY, Math.max(2, barW), BAR_H, 10, 10);

                    yy += ITEM_H;
                }
            } finally {
                g2.dispose();
            }
        }

        private static String trimMiddle(String s, int maxLen) {
            if (s == null) return "";
            if (s.length() <= maxLen) return s;
            int keep = Math.max(4, (maxLen - 3) / 2);
            return s.substring(0, keep) + "..." + s.substring(s.length() - keep);
        }

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
            return ITEM_H;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
            return ITEM_H * 6;
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return false;
        }
    }

    static class ThemeColors {
        final Color fg;
        final Color muted;
        final Color line;
        final Color dirBar;
        final Color fileBar;

        private ThemeColors(Color fg, Color muted, Color line, Color dirBar, Color fileBar) {
            this.fg = fg;
            this.muted = muted;
            this.line = line;
            this.dirBar = dirBar;
            this.fileBar = fileBar;
        }

        static ThemeColors fromUI() {
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

    private static String human(long bytes) {
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
