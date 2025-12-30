package FolderSizeExporter;

import Utils.LocaleManager;
import Utils.UTF8Control;
import com.formdev.flatlaf.intellijthemes.materialthemeuilite.FlatMTArcDarkIJTheme;

import javax.swing.*;
import javax.swing.filechooser.FileSystemView;
import java.awt.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveTask;

public class FolderSizeExporterGUI {

    private static JTextArea textArea;
    private static JProgressBar progressBar;
    private static JLabel statusLabel;
    private static JButton startButton, stopButton;
    private static JCheckBox includeFilesCheckBox;
    private static JComboBox<String> sortComboBox;
    private static File selectedFolder;
    private static ForkJoinPool pool;
    private static ResourceBundle bundle;
    private static Locale locale;

    private static volatile boolean stopRequested = false;
    private static int totalFolders = 0;
    private static int processedFolders = 0;

    private static final String APP_VERSION = "1.2.1";
    private static final String APP_AUTHOR = "LEE JAEHOON";

    public static void main(String[] args) {
        locale = LocaleManager.loadLocale();
        bundle = ResourceBundle.getBundle("resources.messages", locale, new UTF8Control());

        try {
            FlatMTArcDarkIJTheme.setup();
            UIManager.put("Component.arc", 10);
            UIManager.put("Button.arc", 10);
            UIManager.put("ProgressBar.arc", 10);
            UIManager.put("TextComponent.arc", 10);
        } catch (Exception e) {
            e.printStackTrace();
        }

        SwingUtilities.invokeLater(FolderSizeExporterGUI::createAndShowGUI);
    }

    private static void createAndShowGUI() {
        JFrame frame = new JFrame("📁 " + bundle.getString("app.title"));
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(950, 720);
        frame.setLocationRelativeTo(null);

        // Settings
        JMenuBar menuBar = new JMenuBar();
        JMenu settingsMenu = new JMenu(bundle.getString("menu.settings"));

        // language
        JMenu languageMenu = new JMenu(bundle.getString("menu.settings.language"));
        JMenuItem koreanItem = new JMenuItem(bundle.getString("menu.settings.language.korean"));
        JMenuItem englishItem = new JMenuItem(bundle.getString("menu.settings.language.english"));

        koreanItem.addActionListener(e -> switchLanguage(Locale.KOREAN, frame));
        englishItem.addActionListener(e -> switchLanguage(Locale.ENGLISH, frame));

        languageMenu.add(koreanItem);
        languageMenu.add(englishItem);

        // about
        JMenuItem aboutItem = new JMenuItem(bundle.getString("menu.settings.about"));
        aboutItem.addActionListener(e -> {
            String message = String.format("""
            📁 %s
            Version %s
            © 2025 %s
            """, bundle.getString("app.title"), APP_VERSION, APP_AUTHOR);

            JOptionPane.showMessageDialog(
                    frame,
                    message,
                    bundle.getString("menu.settings.about"),
                    JOptionPane.INFORMATION_MESSAGE
            );
        });

        settingsMenu.add(languageMenu);
        settingsMenu.addSeparator();
        settingsMenu.add(aboutItem);
        menuBar.add(settingsMenu);
        frame.setJMenuBar(menuBar);

        // 텍스트 영역
        textArea = new JTextArea();
        textArea.setEditable(false);
        textArea.setFont(new Font("Noto Sans KR", Font.PLAIN, 13));
        JScrollPane scrollPane = new JScrollPane(textArea);
        scrollPane.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // ProgressBar
        progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);

        statusLabel = new JLabel(bundle.getString("status.ready"));
        statusLabel.setHorizontalAlignment(SwingConstants.LEFT);
        statusLabel.setBorder(BorderFactory.createEmptyBorder(0, 10, 5, 0));

        JButton selectButton = new JButton("📂 " + bundle.getString("button.select"));
        startButton = new JButton("🔍 " + bundle.getString("button.start"));
        stopButton = new JButton("⛔ " + bundle.getString("button.stop"));
        startButton.setEnabled(false);
        stopButton.setEnabled(false);

        includeFilesCheckBox = new JCheckBox(bundle.getString("checkbox.include_files"));
        includeFilesCheckBox.setSelected(false);

        sortComboBox = new JComboBox<>(new String[]{bundle.getString("sort.size"), bundle.getString("sort.name"), bundle.getString("sort.date")});
        sortComboBox.setFocusable(false);

        JLabel sortLabel = new JLabel(bundle.getString("label.sort") + ":");
        JPanel controlPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 10));
        controlPanel.add(selectButton);
        controlPanel.add(startButton);
        controlPanel.add(stopButton);
        controlPanel.add(sortLabel);
        controlPanel.add(sortComboBox);
        controlPanel.add(includeFilesCheckBox);

        frame.add(controlPanel, BorderLayout.NORTH);
        frame.add(scrollPane, BorderLayout.CENTER);

        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.add(statusLabel, BorderLayout.NORTH);
        bottomPanel.add(progressBar, BorderLayout.SOUTH);
        frame.add(bottomPanel, BorderLayout.SOUTH);

        JFileChooser chooser = new JFileChooser(FileSystemView.getFileSystemView().getHomeDirectory());
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);

        // 이벤트
        selectButton.addActionListener(e -> {
            int result = chooser.showOpenDialog(frame);
            if (result == JFileChooser.APPROVE_OPTION) {
                selectedFolder = chooser.getSelectedFile();
                textArea.setText(bundle.getString("status.folder_selected") + ": " + selectedFolder.getAbsolutePath() + "\n");
                progressBar.setValue(0);
                processedFolders = 0;
                totalFolders = 0;
                stopRequested = false;
                startButton.setEnabled(true);
                stopButton.setEnabled(false);
                statusLabel.setText(bundle.getString("status.folder_select_done"));
            }
        });

        startButton.addActionListener(e -> {
            stopRequested = false;
            startButton.setEnabled(false);
            stopButton.setEnabled(true);
            analyzeFolder();
        });

        stopButton.addActionListener(e -> {
            stopRequested = true;
            if (pool != null) pool.shutdownNow();
            SwingUtilities.invokeLater(() -> {
                textArea.append("\n" + bundle.getString("status.analysis_stop") + "\n");
                progressBar.setValue(0);
                startButton.setEnabled(true);
                stopButton.setEnabled(false);
                statusLabel.setText(bundle.getString("status.analysis_stopped"));
            });
        });

        frame.setVisible(true);
    }

    private static void switchLanguage(Locale newLocale, JFrame frame) {
        LocaleManager.saveLocale(newLocale);
        frame.dispose();
        locale = newLocale;
        bundle = ResourceBundle.getBundle("resources.messages", locale, new UTF8Control());
        SwingUtilities.invokeLater(FolderSizeExporterGUI::createAndShowGUI);
    }

    private static void analyzeFolder() {
        new Thread(() -> {
            try {
                SwingUtilities.invokeLater(() -> {
                    textArea.append(bundle.getString("status.analysis_start") + "\n");
                    progressBar.setValue(0);
                });

                processedFolders = 0;
                pool = new ForkJoinPool();

                long startTime = System.currentTimeMillis();

                // 폴더 개수 미리 세기 (진행률 계산용)
                totalFolders = countFolders(selectedFolder);

                Map<File, Long> folderSizes = new ConcurrentHashMap<>();
                pool.invoke(new FolderSizeTask(selectedFolder, folderSizes));

                if (stopRequested) return;

                long totalSize = folderSizes.getOrDefault(selectedFolder, 0L);

                // CSV 생성
                File downloadFolder = new File(System.getProperty("user.home"), "Downloads");
                String baseName = selectedFolder.getName() + "_folder_size_report.csv";
                File csvFile = new File(downloadFolder, baseName);

                // CSV 파일 중복 방지
                int counter = 1;
                while (csvFile.exists()) {
                    csvFile = new File(downloadFolder, String.format("%s_folder_size_report(%d).csv", selectedFolder.getName(), counter++));
                }

                try (PrintWriter writer = new PrintWriter(
                        new BufferedWriter(new OutputStreamWriter(new FileOutputStream(csvFile), StandardCharsets.UTF_8))
                )) {
                    writer.write('\uFEFF');
                    writer.println("Type,Depth,Path,Size (bytes),Formatted Size,Last Modified");
                    traverseAndWrite(selectedFolder, writer, 0, folderSizes);
                    SwingUtilities.invokeLater(() -> progressBar.setValue(100));
                }

                final File outputCsvFile = csvFile;
                // 완료 메시지
                SwingUtilities.invokeLater(() -> {
                    textArea.append("\n" + bundle.getString("info.total_folders") + ": " + totalFolders + "\n");
                    textArea.append(bundle.getString("info.total_size") + ": " + formatSize(totalSize) + "\n");
                    textArea.append(bundle.getString("status.csv_done") + ": " + outputCsvFile.getAbsolutePath() + "\n");
                    textArea.append(String.format(bundle.getString("status.analysis_done") + " (%.2f" + bundle.getString("info.seconds") + ")\n", (System.currentTimeMillis() - startTime) / 1000.0));
                    progressBar.setValue(100);
                    startButton.setEnabled(true);
                    stopButton.setEnabled(false);
                    statusLabel.setText(bundle.getString("status.analysis_done") + " ✅");

                    JOptionPane.showMessageDialog(null,
                            bundle.getString("status.folder_analysis_complete") + "\n\n" + bundle.getString("info.csv_file") + ":\n" + outputCsvFile.getAbsolutePath(),
                            bundle.getString("status.analysis_done"), JOptionPane.INFORMATION_MESSAGE);
                });

            } catch (Exception ex) {
                ex.printStackTrace();
                SwingUtilities.invokeLater(() -> textArea.append(bundle.getString("status.error") + ": " + ex.getMessage() + "\n"));
            } finally {
                SwingUtilities.invokeLater(() -> {
                    startButton.setEnabled(true);
                    stopButton.setEnabled(false);
                });
            }
        }).start();
    }

    /** 병렬 폴더 크기 계산 */
    private static class FolderSizeTask extends RecursiveTask<Long> {
        private final File folder;
        private final Map<File, Long> folderSizes;

        FolderSizeTask(File folder, Map<File, Long> folderSizes) {
            this.folder = folder;
            this.folderSizes = folderSizes;
        }

        @Override
        protected Long compute() {
            File[] files = folder.listFiles();
            if (files == null) return 0L;

            long size = 0;
            List<FolderSizeTask> subTasks = new ArrayList<>();

            for (File f : files) {
                if (f.isFile()) {
                    size += f.length();
                } else if (f.isDirectory()) {
                    FolderSizeTask task = new FolderSizeTask(f, folderSizes);
                    task.fork();
                    subTasks.add(task);
                }
            }

            for (FolderSizeTask t : subTasks) size += t.join();
            folderSizes.put(folder, size);
            return size;
        }
    }

    /** 트리 + CSV 출력 (파일 포함 가능) */
    private static void traverseAndWrite(File folder, PrintWriter writer, int depth, Map<File, Long> folderSizes) {
        if (stopRequested) return;

        long size = folderSizes.getOrDefault(folder, 0L);
        String prefix = "│   ".repeat(Math.max(0, depth - 1)) + (depth == 0 ? "" : "└── ");
        String line = String.format("%s%s [%s]%n", prefix, folder.getName(), formatSize(size));

        writer.printf("Folder,%d,\"%s\",%d,%s,%tF %<tT%n", depth, folder.getAbsolutePath(), size, formatSize(size), new Date(folder.lastModified()));
        SwingUtilities.invokeLater(() -> {
            if (stopRequested) return;
            textArea.append(line);
            textArea.setCaretPosition(textArea.getDocument().getLength());
            processedFolders++;
            int percent = (int) ((processedFolders / (double) totalFolders) * 100);
            progressBar.setValue(Math.min(percent, 99));
            statusLabel.setText(String.format(bundle.getString("status.progress") + ": %d / %d " + bundle.getString("info.folder"), processedFolders, totalFolders));
        });

        // 파일 단위 출력
        if (includeFilesCheckBox.isSelected()) {
            File[] files = folder.listFiles(File::isFile);
            if (files != null) {
                Arrays.sort(files, Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
                for (File file : files) {
                    String subPrefix = "│   ".repeat(depth) + "├── ";
                    writer.printf("File,%d,\"%s\",%d,%s,%tF %<tT%n",
                            depth + 1, file.getAbsolutePath(), file.length(), formatSize(file.length()), new Date(file.lastModified()));

                    final String lineForUI = subPrefix + file.getName() + " [" + formatSize(file.length()) + "]\n";
                    SwingUtilities.invokeLater(() -> {
                        if (!stopRequested) {
                            textArea.append(lineForUI);
                        }
                    });
                }
            }
        }

        File[] subFolders = folder.listFiles(File::isDirectory);
        if (subFolders == null) return;

        List<File> sorted = new ArrayList<>(Arrays.asList(subFolders));
        sorted.sort(getComparator(folderSizes));

        for (File sub : sorted) {
            if (stopRequested) return;
            traverseAndWrite(sub, writer, depth + 1, folderSizes);
        }
    }

    /** 폴더 개수 미리 계산 */
    private static int countFolders(File folder) {
        File[] sub = folder.listFiles(File::isDirectory);
        if (sub == null) return 1;
        int count = 1;
        for (File f : sub) count += countFolders(f);
        return count;
    }

    /** 정렬 기준 선택 */
    private static Comparator<File> getComparator(Map<File, Long> folderSizes) {
        int idx = sortComboBox.getSelectedIndex();
        return switch (idx) {
            case 0 -> Comparator.<File, Long>comparing(folderSizes::get, Comparator.nullsLast(Long::compare)).reversed(); // size
            case 2 -> Comparator.comparingLong(File::lastModified).reversed(); // date (0: size, 1: name, 2: date 순서 유지)
            default -> Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER); // name
        };
    }

    /** 보기 좋은 크기 단위 변환 */
    private static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp - 1) + "B";
        return new DecimalFormat("#,##0.00").format(bytes / Math.pow(1024, exp)) + " " + pre;
    }
}
