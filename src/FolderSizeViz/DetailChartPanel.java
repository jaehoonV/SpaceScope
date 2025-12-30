package FolderSizeViz;

import Utils.LanguageUtil;
import Utils.LocaleManager;
import Utils.UTF8Control;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.nio.file.Path;
import java.util.*;
import java.util.List;

public class DetailChartPanel extends JPanel implements Scrollable {

    private static final int PAD = 14;
    private static final int TITLE_H = 30;

    private static final int LABEL_H = 14;
    private static final int GAP1 = 4;
    private static final int BAR_H = 10;
    private static final int GAP2 = 10;
    private static final int ITEM_H = LABEL_H + GAP1 + BAR_H + GAP2;

    private static final int AFTER_TITLE_GAP = 8;
    private static final int AFTER_TITLE_LINE_OFFSET = 6;

    private static final int CHART_LEFT_PADDING = 24;

    private static final Comparator<FolderSizeVizApp.SizeItem> BY_SIZE_DESC =
            (a, b) -> Long.compare(b.bytes, a.bytes);
    
    private String title;
    private final List<FolderSizeVizApp.SizeItem> items = new ArrayList<>();

    private Path titleClickTarget;
    private Runnable onTitleClick;

    private final Rectangle titleTextBounds = new Rectangle();
    private boolean titleHover = false;

    public DetailChartPanel() {
        setOpaque(true);
        setBackground(UIManager.getColor("Panel.background"));
        setForeground(UIManager.getColor("Label.foreground"));
        setBorder(BorderFactory.createEmptyBorder(PAD, PAD, PAD, PAD));

        LanguageUtil.init();
        
        this.title = LanguageUtil.ln("label.none_selected");

        installTitleMouseHandlers();
        updatePreferredSize();
    }

    public void setTitle(String title) {
        this.title = (title == null || title.isBlank()) ? LanguageUtil.ln("label.none_selected") : title;
        updatePreferredSize();
        repaint();
    }

    public void setTitleClickTarget(Path path) {
        this.titleClickTarget = path;
        repaint();
    }

    public void setOnTitleClick(Runnable r) {
        this.onTitleClick = r;
        repaint();
    }

    public void setItems(List<FolderSizeVizApp.SizeItem> newItems) {
        items.clear();
        if (newItems != null) items.addAll(newItems);
        items.sort(BY_SIZE_DESC);
        updatePreferredSize();
        repaint();
    }

    public void upsertItem(FolderSizeVizApp.SizeItem item) {
        if (item == null) return;

        for (int i = 0; i < items.size(); i++) {
            FolderSizeVizApp.SizeItem cur = items.get(i);
            if (cur.isDirectory == item.isDirectory && cur.name.equals(item.name)) {
                items.set(i, item);
                items.sort(BY_SIZE_DESC);
                updatePreferredSize();
                repaint();
                return;
            }
        }

        items.add(item);
        items.sort(BY_SIZE_DESC);
        updatePreferredSize();
        repaint();
    }

    private void installTitleMouseHandlers() {
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (!isTitleClickable()) return;
                if (!SwingUtilities.isLeftMouseButton(e)) return;
                if (titleTextBounds.contains(e.getPoint())) onTitleClick.run();
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
    }

    private boolean isTitleClickable() {
        return titleClickTarget != null && onTitleClick != null;
    }

    private String buildTitleTooltip() {
        return (titleClickTarget == null) ? null : LanguageUtil.fmt("tooltip.go_to_folder", titleClickTarget.toString());
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

            FolderSizeVizApp.ThemeColors c = FolderSizeVizApp.ThemeColors.fromUI();

            int w = getWidth();
            int y = 0;

            Font titleFont = getFont().deriveFont(Font.BOLD, 15f);
            g2.setFont(titleFont);

            FontMetrics fm = g2.getFontMetrics();
            int baselineY = y + 18;

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
            y += AFTER_TITLE_GAP;

            if (items.isEmpty()) {
                g2.setColor(c.muted);
                g2.drawString(LanguageUtil.ln("hint.detail_empty"), 0, y + 30);
                return;
            }

            long max = 1L;
            for (FolderSizeVizApp.SizeItem it : items) max = Math.max(max, it.bytes);

            int barX = CHART_LEFT_PADDING;
            int barAreaWidth = w - CHART_LEFT_PADDING - 10;
            int tagX = CHART_LEFT_PADDING - 10;

            int yy = y;
            for (FolderSizeVizApp.SizeItem it : items) {
                double ratio = (max == 0) ? 0.0 : (double) it.bytes / (double) max;
                int barW = (int) (barAreaWidth * ratio);

                String tag = it.isDirectory ? "[DIR] " : "[FILE] ";
                String label = tag + trimMiddle(it.name, 42) + "  (" + FolderSizeVizApp.human(it.bytes) + ")";

                g2.setColor(c.fg);
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
