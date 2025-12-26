package FolderSizeViz;

import org.jfree.chart.*;
import org.jfree.chart.entity.PieSectionEntity;
import org.jfree.chart.plot.PiePlot;
import org.jfree.data.general.DefaultPieDataset;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.Objects;

public class PieChartTabPanel extends JPanel {

    private final JLabel titleLabel = new JLabel("선택 없음");

    private final DefaultPieDataset<String> dataset = new DefaultPieDataset<>();
    private final JFreeChart chart;
    private final ChartPanel chartPanel; // <-- 이건 JFreeChart의 ChartPanel (별도 파일이므로 충돌 없음)
    private final PiePlot plot;

    private Comparable<?> hoveredKey = null;

    public PieChartTabPanel() {
        super(new BorderLayout(8, 8));
        setOpaque(true);

        Font base = UIManager.getFont("Label.font");
        if (base == null) base = new Font(Font.SANS_SERIF, Font.PLAIN, 12);
        titleLabel.setFont(base.deriveFont(Font.BOLD, base.getSize2D() + 1f));
        add(titleLabel, BorderLayout.NORTH);

        chart = ChartFactory.createPieChart(
                "Storage Usage (Top Items)",
                dataset,
                true,
                true,
                false
        );

        if (chart.getLegend() != null) {
            chart.getLegend().setPosition(org.jfree.chart.ui.RectangleEdge.RIGHT);
        }
        chart.getLegend().setItemLabelPadding(new org.jfree.chart.ui.RectangleInsets(2, 6, 2, 6));

        plot = (PiePlot) chart.getPlot();
        applyDarkTheme(base);

        chartPanel = new ChartPanel(chart);
        chartPanel.setPopupMenu(null);
        chartPanel.setMouseWheelEnabled(true);

        installHoverEffect();

        add(chartPanel, BorderLayout.CENTER);
    }

    public void setTitle(String title) {
        titleLabel.setText(title);
    }

    public void clear() {
        dataset.clear();
        hoveredKey = null;
        chartPanel.repaint();
    }

    public void setItemsTop10(List<FolderSizeVizApp.SizeItem> items) {
        dataset.clear();
        hoveredKey = null;

        if (items == null || items.isEmpty()) {
            chartPanel.repaint();
            return;
        }

        // 상위 10개 뽑고, 그 중 상위 7개만 개별 표시 + 나머지 3개를 Others로 합치기
        List<FolderSizeVizApp.SizeItem> top10 = items.stream()
                .sorted((a, b) -> Long.compare(b.bytes, a.bytes))
                .limit(10)
                .toList();

        final int KEEP = 7;
        long othersBytes = 0;

        for (int i = 0; i < top10.size(); i++) {
            FolderSizeVizApp.SizeItem it = top10.get(i);
            if (i < KEEP) {
                dataset.setValue(trimMiddle(it.name, 28), it.bytes);
            } else {
                othersBytes += it.bytes;
            }
        }

        if (othersBytes > 0) {
            dataset.setValue("Others", othersBytes);
        }

        resetExplode();
        chartPanel.repaint();

    }

    private void installHoverEffect() {
        chartPanel.addChartMouseListener(new ChartMouseListener() {
            @Override
            public void chartMouseMoved(ChartMouseEvent e) {
                if (e.getEntity() instanceof PieSectionEntity pe) {
                    Comparable<?> key = pe.getSectionKey();
                    if (!Objects.equals(key, hoveredKey)) {
                        hoveredKey = key;
                        resetExplode();
                        plot.setExplodePercent(key, 0.08);
                        chartPanel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                        chartPanel.repaint();
                    }
                } else {
                    hoveredKey = null;
                    resetExplode();
                    chartPanel.setCursor(Cursor.getDefaultCursor());
                }
            }

            @Override
            public void chartMouseClicked(ChartMouseEvent e) {}
        });
    }

    private void resetExplode() {
        for (Comparable<?> k : dataset.getKeys()) {
            plot.setExplodePercent(k, 0.0);
        }
    }

    private void applyDarkTheme(Font base) {
        Color bg = UIManager.getColor("Panel.background");
        Color fg = UIManager.getColor("Label.foreground");

        chart.setBackgroundPaint(bg);
        chart.getTitle().setPaint(fg);
        chart.getTitle().setFont(base.deriveFont(Font.BOLD, base.getSize2D() + 1f));

        plot.setBackgroundPaint(bg);
        plot.setLabelPaint(fg);
        plot.setLabelFont(base.deriveFont(Font.BOLD, base.getSize2D() + 1f));
        plot.setShadowPaint(null);

        // 라벨(조각 옆 박스) 전부 숨김 -> 화면이 확 정리됨
        plot.setLabelGenerator(null);
        // 혹시 라벨이 살아있으면 선/배경 때문에 지저분하니 함께 정리
        plot.setLabelOutlinePaint(null);
        plot.setLabelShadowPaint(null);
        plot.setLabelBackgroundPaint(bg);
        plot.setSectionOutlinesVisible(false); // 조각 경계선 제거(깔끔)
        plot.setShadowPaint(null);             // 그림자 제거(다크에서 탁해짐 방지)
        plot.setOutlineVisible(false);         // 플롯 외곽선 제거

        if (chart.getLegend() != null) {
            chart.getLegend().setItemFont(base.deriveFont(Font.BOLD, base.getSize2D() + 1f));
            chart.getLegend().setBackgroundPaint(bg);
            chart.getLegend().setItemPaint(fg);
        }

        // Tooltip 커스터마이징(선택) - 기본도 잘 나옴
        plot.setToolTipGenerator((dset, key) -> {
            Number v = dset.getValue(key);
            long bytes = (v == null) ? 0L : v.longValue();
            return key + " / " + FolderSizeVizApp.human(bytes);
        });
    }

    private static String trimMiddle(String s, int maxLen) {
        if (s == null || s.length() <= maxLen) return s;
        int keep = Math.max(4, (maxLen - 3) / 2);
        return s.substring(0, keep) + "..." + s.substring(s.length() - keep);
    }
}
