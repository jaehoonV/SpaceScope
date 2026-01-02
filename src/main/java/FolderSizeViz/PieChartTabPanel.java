package FolderSizeViz;

import Utils.LanguageUtil;
import Utils.SizeFormatUtil;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartMouseEvent;
import org.jfree.chart.ChartMouseListener;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.entity.PieSectionEntity;
import org.jfree.chart.labels.PieSectionLabelGenerator;
import org.jfree.chart.plot.PiePlot;
import org.jfree.chart.title.LegendTitle;
import org.jfree.data.general.DefaultPieDataset;
import org.jfree.data.general.PieDataset;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.nio.file.Path;
import java.text.AttributedString;
import java.text.NumberFormat;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

public class PieChartTabPanel extends JPanel implements Scrollable {

    private static final Comparator<FolderSizeVizApp.SizeItem> BY_SIZE_DESC =
            (a, b) -> Long.compare(b.bytes, a.bytes);

    private static final String OTHERS_KEY = "Others";

    private static final int TITLE_FONT_SIZE = 15;
    private static final int TITLE_BASELINE_Y = 18;
    private static final int TITLE_H = 30;

    private static final int TITLE_HIT_PAD_X = 12;
    private static final int TITLE_HIT_PAD_Y = 6;

    private String title;

    private Path titleClickTarget;
    private Runnable onTitleClick;

    private final DefaultPieDataset<String> dataset = new DefaultPieDataset<>();
    private final JFreeChart chart;
    private final ChartPanel chartPanel;
    private final PiePlot plot;

    private Comparable<?> hoveredKey;

    private final HeaderPanel headerPanel = new HeaderPanel();

    private final Map<Comparable<?>, Path> keyToPath = new HashMap<>();
    private Consumer<Path> onSliceClick;

    public PieChartTabPanel() {
        super(new BorderLayout());

        LanguageUtil.init();
        this.title = LanguageUtil.ln("label.none_selected");

        setOpaque(true);
        setBackground(UIManager.getColor("Panel.background"));

        ToolTipManager.sharedInstance().setInitialDelay(80);
        ToolTipManager.sharedInstance().setReshowDelay(30);
        ToolTipManager.sharedInstance().setDismissDelay(7000);

        add(headerPanel, BorderLayout.NORTH);

        chart = ChartFactory.createPieChart(null, dataset, true, true, false);
        plot = (PiePlot) chart.getPlot();

        Font base = UIManager.getFont("Label.font");
        if (base == null) base = new Font(Font.SANS_SERIF, Font.PLAIN, 12);
        applyTheme(base);

        chartPanel = new ChartPanel(chart);
        chartPanel.setPopupMenu(null);
        chartPanel.setMouseWheelEnabled(true);

        installChartMouseHandlers();

        add(chartPanel, BorderLayout.CENTER);
    }

    public void setOnSliceClick(Consumer<Path> c) {
        this.onSliceClick = c;
    }

    public void setTitle(String title) {
        String fallback = LanguageUtil.ln("label.none_selected");
        this.title = (title == null || title.isBlank()) ? fallback : title;
        headerPanel.repaint();
    }

    public void setTitleClickTarget(Path path) {
        this.titleClickTarget = path;
        headerPanel.repaint();
    }

    public void setOnTitleClick(Runnable r) {
        this.onTitleClick = r;
        headerPanel.repaint();
    }

    public void clear() {
        dataset.clear();
        keyToPath.clear();
        hoveredKey = null;
        chartPanel.repaint();
    }

    public void setItemsTop10(List<FolderSizeVizApp.SizeItem> items) {
        dataset.clear();
        keyToPath.clear();
        hoveredKey = null;

        if (items == null || items.isEmpty()) {
            chartPanel.repaint();
            return;
        }

        List<FolderSizeVizApp.SizeItem> top10 = items.stream()
                .sorted(BY_SIZE_DESC)
                .limit(10)
                .toList();

        final int KEEP = 7;
        long othersBytes = 0L;

        for (int i = 0; i < top10.size(); i++) {
            FolderSizeVizApp.SizeItem it = top10.get(i);

            if (i < KEEP) {
                String baseKey = trimMiddle(it.name, 28);
                String key = baseKey;

                int n = 2;
                while (dataset.getKeys().contains(key)) {
                    key = baseKey + " (" + n++ + ")";
                }

                dataset.setValue(key, it.bytes);
                if (it.path != null) keyToPath.put(key, it.path);

            } else {
                othersBytes += it.bytes;
            }
        }

        if (othersBytes > 0) {
            dataset.setValue(OTHERS_KEY, othersBytes);
        }

        resetExplode();
        chartPanel.repaint();
    }

    private boolean isTitleClickable() {
        return titleClickTarget != null && onTitleClick != null;
    }

    private String buildTitleTooltip() {
        return (titleClickTarget == null)
                ? null
                : LanguageUtil.fmt("tooltip.go_to_folder", titleClickTarget.toString());
    }

    private void installChartMouseHandlers() {
        chartPanel.addChartMouseListener(new ChartMouseListener() {
            @Override
            public void chartMouseMoved(ChartMouseEvent e) {
                if (e.getEntity() instanceof PieSectionEntity pe) {
                    Comparable<?> key = pe.getSectionKey();
                    if (!Objects.equals(key, hoveredKey)) {
                        if (hoveredKey != null) plot.setExplodePercent(hoveredKey, 0.0);
                        hoveredKey = key;
                        plot.setExplodePercent(key, 0.08);
                        chartPanel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                        chartPanel.repaint();
                    }
                    return;
                }

                if (hoveredKey != null) {
                    plot.setExplodePercent(hoveredKey, 0.0);
                    hoveredKey = null;
                    chartPanel.repaint();
                }
                chartPanel.setCursor(Cursor.getDefaultCursor());
            }

            @Override
            public void chartMouseClicked(ChartMouseEvent e) {
                if (onSliceClick == null) return;
                if (!(e.getEntity() instanceof PieSectionEntity pe)) return;

                Comparable<?> key = pe.getSectionKey();
                if (key == null) return;

                if (OTHERS_KEY.equals(String.valueOf(key))) return;

                Path p = keyToPath.get(key);
                if (p == null) return;

                onSliceClick.accept(p);
            }
        });
    }

    private void resetExplode() {
        for (Comparable<?> k : dataset.getKeys()) {
            plot.setExplodePercent(k, 0.0);
        }
    }

    private void applyTheme(Font base) {
        Color bg = UIManager.getColor("Panel.background");
        Color fg = UIManager.getColor("Label.foreground");

        chart.setBackgroundPaint(bg);
        plot.setBackgroundPaint(bg);
        plot.setShadowPaint(null);
        plot.setSectionOutlinesVisible(false);
        plot.setOutlineVisible(false);

        final NumberFormat pctFmt = NumberFormat.getPercentInstance(Locale.getDefault());
        pctFmt.setMaximumFractionDigits(1);

        plot.setLabelGenerator(new PieSectionLabelGenerator() {
            @Override
            public String generateSectionLabel(PieDataset dataset, Comparable key) {
                Number v = dataset.getValue(key);
                if (v == null) return null;

                double value = v.doubleValue();
                double total = 0.0;
                for (int i = 0; i < dataset.getItemCount(); i++) {
                    Number n = dataset.getValue(i);
                    if (n != null) total += n.doubleValue();
                }
                double pct = (total <= 0.0) ? 0.0 : (value / total);

                return key + " " + pctFmt.format(pct);
            }

            @Override
            public AttributedString generateAttributedSectionLabel(PieDataset dataset, Comparable key) {
                return null;
            }
        });

        plot.setLegendLabelGenerator(new PieSectionLabelGenerator() {
            @Override
            public String generateSectionLabel(PieDataset dataset, Comparable key) {
                Number v = dataset.getValue(key);
                long bytes = (v == null) ? 0L : v.longValue();
                return key + "（" + SizeFormatUtil.human(bytes) + ")";
            }

            @Override
            public AttributedString generateAttributedSectionLabel(PieDataset dataset, Comparable key) {
                return null;
            }
        });

        plot.setLabelFont(base.deriveFont(Font.BOLD, base.getSize2D()));
        plot.setLabelPaint(fg);
        plot.setLabelBackgroundPaint(bg);
        plot.setLabelOutlinePaint(null);
        plot.setLabelShadowPaint(null);
        plot.setLabelLinkPaint(fg);
        plot.setLabelLinkStroke(new BasicStroke(1f));
        plot.setLabelLinkMargin(0.02);
        plot.setLabelGap(0.02);

        if (chart.getLegend() != null) {
            LegendTitle legend = chart.getLegend();
            legend.setPosition(org.jfree.chart.ui.RectangleEdge.RIGHT);
            legend.setItemFont(base.deriveFont(Font.BOLD, base.getSize2D() + 1f));
            legend.setBackgroundPaint(bg);
            legend.setItemPaint(fg);
            legend.setItemLabelPadding(new org.jfree.chart.ui.RectangleInsets(2, 6, 2, 6));
            legend.setMargin(8, 8, 8, 32);
        }

        plot.setToolTipGenerator((dset, key) -> {
            Number v = dset.getValue(key);
            long bytes = (v == null) ? 0L : v.longValue();
            return key + " / " + SizeFormatUtil.human(bytes);
        });
    }

    private static String trimMiddle(String s, int maxLen) {
        if (s == null || s.length() <= maxLen) return s;
        int keep = Math.max(4, (maxLen - 3) / 2);
        return s.substring(0, keep) + "..." + s.substring(s.length() - keep);
    }

    private final class HeaderPanel extends JPanel {
        private static final int PAD = 14;
        private static final int AFTER_TITLE_GAP = 8;
        private static final int AFTER_TITLE_LINE_OFFSET = 6;

        private final Rectangle titleTextBounds = new Rectangle();
        private boolean titleHover = false;

        HeaderPanel() {
            setOpaque(false);
            setBorder(null);
            setPreferredSize(new Dimension(10, PAD + TITLE_H + AFTER_TITLE_GAP));

            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (!SwingUtilities.isLeftMouseButton(e)) return;
                    if (!isTitleClickable()) return;

                    if (getTitleHitBounds().contains(e.getPoint())) {
                        onTitleClick.run();
                    }
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    if (!titleHover) return;
                    titleHover = false;
                    setCursor(Cursor.getDefaultCursor());
                    setToolTipText(null);
                    repaint();
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

                    boolean hit = getTitleHitBounds().contains(e.getPoint());
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
        }

        private void updateTitleTextBounds() {
            Font titleFont = getFont().deriveFont(Font.BOLD, (float) TITLE_FONT_SIZE);
            FontMetrics fm = getFontMetrics(titleFont);

            int textW = fm.stringWidth(title);
            int textH = fm.getHeight();
            int textTop = TITLE_BASELINE_Y - fm.getAscent();

            titleTextBounds.setBounds(0, textTop, textW, textH);
        }

        private Rectangle getTitleHitBounds() {
            updateTitleTextBounds();

            Rectangle r = new Rectangle(titleTextBounds);
            r.x -= TITLE_HIT_PAD_X;
            r.y -= TITLE_HIT_PAD_Y;
            r.width += TITLE_HIT_PAD_X * 2;
            r.height += TITLE_HIT_PAD_Y * 2;

            // HeaderPanel 크기 기준 clamp (중요!)
            if (r.x < 0) {
                r.width += r.x;
                r.x = 0;
            }
            if (r.y < 0) {
                r.height += r.y;
                r.y = 0;
            }
            r.width = Math.min(r.width, Math.max(0, getWidth() - r.x));
            r.height = Math.min(r.height, Math.max(0, getHeight() - r.y));
            return r;
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);

            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                FolderSizeVizApp.ThemeColors c = FolderSizeVizApp.ThemeColors.fromUI();

                int w = getWidth();
                int y = 0;

                Font titleFont = getFont().deriveFont(Font.BOLD, (float) TITLE_FONT_SIZE);
                g2.setFont(titleFont);

                FontMetrics fm = g2.getFontMetrics();
                int baselineY = y + TITLE_BASELINE_Y;

                int textW = fm.stringWidth(title);
                int textH = fm.getHeight();
                int textTop = baselineY - fm.getAscent();

                titleTextBounds.setBounds(0, textTop, textW, textH);

                g2.setColor(c.fg);
                g2.drawString(title, 0, baselineY);

                if (isTitleClickable() && titleHover) {
                    g2.setColor(c.muted);
                    int underlineY = baselineY + 2;
                    g2.drawLine(0, underlineY, Math.max(0, textW), underlineY);
                }

                y += TITLE_H;
                g2.setColor(c.line);
                g2.drawLine(0, y - AFTER_TITLE_LINE_OFFSET, w, y - AFTER_TITLE_LINE_OFFSET);

            } finally {
                g2.dispose();
            }
        }
    }

    @Override
    public Dimension getPreferredScrollableViewportSize() {
        return getPreferredSize();
    }

    @Override
    public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
        return 16;
    }

    @Override
    public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
        return 120;
    }

    @Override
    public boolean getScrollableTracksViewportWidth() {
        return true;
    }

    @Override
    public boolean getScrollableTracksViewportHeight() {
        return true;
    }
}
