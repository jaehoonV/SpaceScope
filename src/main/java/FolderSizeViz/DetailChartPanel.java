package FolderSizeViz;

import Utils.LanguageUtil;
import Utils.SizeFormatUtil;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

public class DetailChartPanel extends JPanel implements Scrollable {

    private static final int PAD = 14;

    private static final int TITLE_FONT_SIZE = 15;
    private static final int TITLE_BASELINE_Y = 18;
    private static final int TITLE_H = 30;

    private static final int LABEL_BASELINE_OFFSET = 12;
    private static final int LABEL_H = 14;

    private static final int GAP1 = 4;
    private static final int BAR_H = 10;
    private static final int GAP2 = 10;
    private static final int ITEM_H = LABEL_H + GAP1 + BAR_H + GAP2;

    private static final int AFTER_TITLE_GAP = 8;
    private static final int AFTER_TITLE_LINE_OFFSET = 6;

    private static final int CHART_LEFT_PADDING = 24;
    private static final int ITEM_RIGHT_PADDING = 10;

    private static final int TITLE_HIT_PAD_X = 12;
    private static final int TITLE_HIT_PAD_Y = 6;

    private static final int LABEL_TRIM_LEN = 42;

    private static final Comparator<FolderSizeVizApp.SizeItem> BY_SIZE_DESC =
            (a, b) -> Long.compare(b.bytes, a.bytes);

    private String title;
    private final List<FolderSizeVizApp.SizeItem> items = new ArrayList<>();

    private long maxBytes = 1L;

    private Path titleClickTarget;
    private Runnable onTitleClick;

    private final Rectangle titleTextBounds = new Rectangle();
    private boolean titleHover = false;

    private Consumer<Path> onItemClick;

    public DetailChartPanel() {
        setOpaque(true);
        setBackground(UIManager.getColor("Panel.background"));
        setForeground(UIManager.getColor("Label.foreground"));
        setBorder(BorderFactory.createEmptyBorder(PAD, PAD, PAD, PAD));

        LanguageUtil.init();
        this.title = LanguageUtil.ln("label.none_selected");

        installMouseHandlers();
        updatePreferredSize();
    }

    public void setOnItemClick(Consumer<Path> c) {
        this.onItemClick = c;
    }

    public void setTitle(String title) {
        this.title = (title == null || title.isBlank())
                ? LanguageUtil.ln("label.none_selected")
                : title;

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

        recomputeMaxBytes();
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

                recomputeMaxBytes();
                updatePreferredSize();
                repaint();
                return;
            }
        }

        items.add(item);
        items.sort(BY_SIZE_DESC);

        // maxBytes는 증가만 빠르게 처리 가능하지만, 정합성을 위해 단순 재계산
        recomputeMaxBytes();
        updatePreferredSize();
        repaint();
    }

    private void recomputeMaxBytes() {
        long max = 1L;
        for (FolderSizeVizApp.SizeItem it : items) {
            max = Math.max(max, it.bytes);
        }
        maxBytes = max;
    }

    private void installMouseHandlers() {
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (!SwingUtilities.isLeftMouseButton(e)) return;

                // 1) Title click
                if (isTitleClickable() && getTitleHitBounds().contains(e.getPoint())) {
                    onTitleClick.run();
                    return;
                }

                // 2) Item click (label + bar)
                int idx = hitTestItemIndex(e.getPoint());
                if (idx < 0) return;

                FolderSizeVizApp.SizeItem it = items.get(idx);
                if (it != null && it.path != null) {
                    onItemClick.accept(it.path);
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
                Point pt = e.getPoint();

                // 1) Title hover
                if (isTitleClickable()) {
                    boolean hitTitle = getTitleHitBounds().contains(pt);

                    if (hitTitle != titleHover) {
                        titleHover = hitTitle;
                        repaint();
                    }

                    if (hitTitle) {
                        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                        setToolTipText(buildTitleTooltip());
                        return;
                    }
                }

                if (titleHover) {
                    titleHover = false;
                    repaint();
                }

                // 2) Item hover
                if (onItemClick != null && !items.isEmpty()) {
                    int idx = hitTestItemIndex(pt);
                    if (idx >= 0) {
                        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                        setToolTipText(null);
                        return;
                    }
                }

                // 3) default
                setCursor(Cursor.getDefaultCursor());
                setToolTipText(null);
            }
        });
    }

    private boolean isTitleClickable() {
        return titleClickTarget != null && onTitleClick != null;
    }

    private String buildTitleTooltip() {
        return (titleClickTarget == null)
                ? null
                : LanguageUtil.fmt("tooltip.go_to_folder", titleClickTarget.toString());
    }

    /**
     * paint가 안 돈 상태에서도 클릭 판정이 정확해야 하므로,
     * 현재 폰트/타이틀 문자열 기준으로 titleTextBounds를 갱신한다.
     */
    private void updateTitleTextBoundsIfNeeded() {
        Font titleFont = getFont().deriveFont(Font.BOLD, (float) TITLE_FONT_SIZE);
        FontMetrics fm = getFontMetrics(titleFont);

        int textW = fm.stringWidth(title);
        int textH = fm.getHeight();
        int textTop = TITLE_BASELINE_Y - fm.getAscent();

        titleTextBounds.setBounds(0, textTop, textW, textH);
    }

    private Rectangle getTitleHitBounds() {
        updateTitleTextBoundsIfNeeded();

        Rectangle r = new Rectangle(titleTextBounds);
        r.x -= TITLE_HIT_PAD_X;
        r.y -= TITLE_HIT_PAD_Y;
        r.width += TITLE_HIT_PAD_X * 2;
        r.height += TITLE_HIT_PAD_Y * 2;

        // 컴포넌트 영역 밖으로 나가지 않게 clamp
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

    private int getItemsStartY() {
        return TITLE_H + AFTER_TITLE_GAP;
    }

    private int hitTestItemIndex(Point pt) {
        if (!SwingUtilities.isLeftMouseButton(new MouseEvent(this, 0, 0, 0, 0, 0, 1, false)) && onItemClick == null) {
            // 방어용(실제론 mouseMoved에서만 호출 가능하니 큰 의미는 없음)
        }
        if (onItemClick == null) return -1;
        if (items.isEmpty()) return -1;

        int itemsStartY = getItemsStartY();
        if (pt.y < itemsStartY) return -1;

        int idx = (pt.y - itemsStartY) / ITEM_H;
        if (idx < 0 || idx >= items.size()) return -1;

        Rectangle bar = getBarRectForIndex(idx);
        Rectangle label = getLabelRectForIndex(idx);

        boolean hit = (bar != null && bar.contains(pt)) || (label != null && label.contains(pt));
        return hit ? idx : -1;
    }

    private Rectangle getBarRectForIndex(int idx) {
        if (idx < 0 || idx >= items.size()) return null;

        int w = getWidth();
        int itemsStartY = getItemsStartY();

        FolderSizeVizApp.SizeItem it = items.get(idx);
        double ratio = (maxBytes <= 0) ? 0.0 : (double) it.bytes / (double) maxBytes;

        int barX = CHART_LEFT_PADDING;
        int barAreaWidth = Math.max(0, w - CHART_LEFT_PADDING - ITEM_RIGHT_PADDING);

        int yy = itemsStartY + (idx * ITEM_H);
        int barY = yy + LABEL_H + GAP1;

        int barW = (int) (barAreaWidth * ratio);
        barW = Math.max(2, barW);

        return new Rectangle(barX, barY, barW, BAR_H);
    }

    private String buildItemLabel(FolderSizeVizApp.SizeItem it) {
        String tag = it.isDirectory ? "[DIR] " : "[FILE] ";
        return tag + trimMiddle(it.name, LABEL_TRIM_LEN) + "  (" + SizeFormatUtil.human(it.bytes) + ")";
    }

    private Rectangle getLabelRectForIndex(int idx) {
        if (idx < 0 || idx >= items.size()) return null;

        int itemsStartY = getItemsStartY();
        int yy = itemsStartY + (idx * ITEM_H);

        int tagX = CHART_LEFT_PADDING - ITEM_RIGHT_PADDING;
        int baseline = yy + LABEL_BASELINE_OFFSET;

        FontMetrics fm = getFontMetrics(getFont());

        FolderSizeVizApp.SizeItem it = items.get(idx);
        String label = buildItemLabel(it);

        int textW = fm.stringWidth(label);
        int textH = fm.getHeight();
        int textTop = baseline - fm.getAscent();

        return new Rectangle(tagX, textTop, textW, textH);
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

            // Title
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

            // Divider
            y += TITLE_H;

            g2.setColor(c.line);
            g2.drawLine(0, y - AFTER_TITLE_LINE_OFFSET, w, y - AFTER_TITLE_LINE_OFFSET);
            y += AFTER_TITLE_GAP;

            // Empty
            if (items.isEmpty()) {
                g2.setColor(c.muted);
                g2.drawString(LanguageUtil.ln("hint.detail_empty"), 0, y + 30);
                return;
            }

            // Items
            int barX = CHART_LEFT_PADDING;
            int barAreaWidth = Math.max(0, w - CHART_LEFT_PADDING - ITEM_RIGHT_PADDING);
            int labelX = CHART_LEFT_PADDING - ITEM_RIGHT_PADDING;

            int yy = y;
            for (FolderSizeVizApp.SizeItem it : items) {
                double ratio = (maxBytes <= 0) ? 0.0 : (double) it.bytes / (double) maxBytes;
                int barW = (int) (barAreaWidth * ratio);

                String label = buildItemLabel(it);

                g2.setColor(c.fg);
                g2.drawString(label, labelX, yy + LABEL_BASELINE_OFFSET);

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
