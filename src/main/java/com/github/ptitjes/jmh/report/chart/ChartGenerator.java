package com.github.ptitjes.jmh.report.chart;

import com.github.ptitjes.jmh.report.annotations.Orientation;
import com.github.ptitjes.jmh.report.annotations.PlotType;
import com.github.ptitjes.jmh.report.data.BenchmarkResultData;
import com.github.ptitjes.jmh.report.data.RunResultData;
import com.github.ptitjes.jmh.report.format.RenderingConfiguration;
import com.itextpdf.text.Font;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.*;
import org.jfree.chart.labels.ItemLabelAnchor;
import org.jfree.chart.labels.ItemLabelPosition;
import org.jfree.chart.labels.StandardCategoryItemLabelGenerator;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.plot.DefaultDrawingSupplier;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.renderer.category.StatisticalBarRenderer;
import org.jfree.chart.renderer.category.StatisticalLineAndShapeRenderer;
import org.jfree.data.statistics.DefaultStatisticalCategoryDataset;
import org.jfree.data.statistics.StatisticalCategoryDataset;
import org.jfree.ui.RectangleInsets;
import org.jfree.ui.TextAnchor;
import org.openjdk.jmh.infra.BenchmarkParams;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Paint;
import java.awt.RenderingHints;
import java.text.NumberFormat;
import java.util.*;

/**
 * @author Didier Villevalois
 */
public class ChartGenerator {

	private final RenderingConfiguration renderingConfiguration;
	private final PlotConfiguration plotConfiguration;

	public ChartGenerator(RenderingConfiguration renderingConfiguration, PlotConfiguration plotConfiguration) {
		this.renderingConfiguration = renderingConfiguration;
		this.plotConfiguration = plotConfiguration;
	}

	private static final NumberFormat MEAN_FORMAT = NumberFormat.getInstance();

	public JFreeChart generateChart(String title, BenchmarkResultData benchmarkResult) {

		String axisParamKey = plotConfiguration.axisParam;

		List<String> seriesParamKeys = new ArrayList<>(benchmarkResult.paramKeys);
		if (axisParamKey == null) axisParamKey = seriesParamKeys.size() > 1 ? seriesParamKeys.get(1) : "";
		if (axisParamKey != null) seriesParamKeys.remove(axisParamKey);

		String timeUnit = benchmarkResult.timeUnit;

		StatisticalCategoryDataset dataset = makeDataset(benchmarkResult);

		final JFreeChart chart = ChartFactory.createBarChart(
				title != null ? title : "", // title
				axisParamKey, // category axis label
				timeUnit, // value axis label
				dataset, // data
				plotConfiguration.orientation == Orientation.HORIZONTAL ?
						PlotOrientation.HORIZONTAL : PlotOrientation.VERTICAL, // orientation
				true, // include legend
				true, // tooltips
				false // urls
		);

		chart.setBackgroundPaint(Color.white);
		chart.setPadding(RectangleInsets.ZERO_INSETS);

		final CategoryPlot plot = chart.getCategoryPlot();
		plot.setBackgroundPaint(new Color(220, 220, 220));
		plot.setRangeAxisLocation(AxisLocation.BOTTOM_OR_LEFT);

		if (plotConfiguration.type == PlotType.BARS) {
			StatisticalBarRenderer renderer = new StatisticalBarRenderer();
			renderer.setBaseItemLabelGenerator(new StandardCategoryItemLabelGenerator("{2}", MEAN_FORMAT));

			if (plotConfiguration.orientation == Orientation.HORIZONTAL) {
				renderer.setBasePositiveItemLabelPosition(new ItemLabelPosition(ItemLabelAnchor.OUTSIDE9, TextAnchor.CENTER_LEFT));
				renderer.setItemLabelAnchorOffset(-5);
			} else {
				renderer.setBasePositiveItemLabelPosition(new ItemLabelPosition(ItemLabelAnchor.OUTSIDE6, TextAnchor.CENTER_LEFT, TextAnchor.CENTER_LEFT, -Math.PI / 2));
				renderer.setItemLabelAnchorOffset(-5);
			}

			renderer.setBaseItemLabelsVisible(true);
			renderer.setBaseItemLabelFont(renderer.getBaseItemLabelFont().deriveFont(Font.NORMAL, renderingConfiguration.baseFontSize));
			renderer.setBaseItemLabelPaint(Color.white);
			renderer.setErrorIndicatorStroke(new BasicStroke(0));
			renderer.setItemMargin(0);
			plot.setRenderer(renderer);

			renderer.setBaseLegendTextFont(renderer.getBaseItemLabelFont().deriveFont(Font.NORMAL, renderingConfiguration.baseFontSize));
		} else {
			StatisticalLineAndShapeRenderer renderer = new StatisticalLineAndShapeRenderer();
			renderer.setBaseShapesVisible(true);
			renderer.setErrorIndicatorStroke(new BasicStroke(0));
			plot.setRenderer(renderer);

			renderer.setBaseLegendTextFont(renderer.getBaseItemLabelFont().deriveFont(Font.NORMAL, renderingConfiguration.baseFontSize));
		}

		populateColors(plot);

		CategoryAxis domainAxis = plot.getDomainAxis();
		configureAxis(domainAxis);

		domainAxis.setLabel(axisParamKey);
		domainAxis.setCategoryLabelPositions(CategoryLabelPositions.UP_45);

		domainAxis.setCategoryMargin(0.05);
		domainAxis.setUpperMargin(0.01);
		domainAxis.setLowerMargin(0.01);

		if (plotConfiguration.logScale) {
			LogarithmicAxis rangeAxis = new LogarithmicAxis(timeUnit);
			configureAxis(rangeAxis);

			rangeAxis.setLabel(timeUnit);
			rangeAxis.setLabelInsets(RectangleInsets.ZERO_INSETS);
			rangeAxis.setMinorTickMarksVisible(true);
			rangeAxis.setAutoRange(true);
			rangeAxis.setAutoRangeIncludesZero(false);
			rangeAxis.setAllowNegativesFlag(true);
			plot.setRangeAxis(rangeAxis);
		} else {
			ValueAxis rangeAxis = plot.getRangeAxis();
			configureAxis(rangeAxis);

			rangeAxis.setLabel(timeUnit);
			rangeAxis.setLabelInsets(RectangleInsets.ZERO_INSETS);
			rangeAxis.setAutoRange(true);
			configureAxis(rangeAxis);
		}

		RenderingHints renderingHints = chart.getRenderingHints();
		if (plotConfiguration.type == PlotType.BARS && plotConfiguration.orientation == Orientation.HORIZONTAL) {
			renderingHints.put(MIN_HEIGHT, benchmarkResult.perParamsResults.size() * 12 + 80);
		}

		return chart;
	}

	public static final Object MIN_HEIGHT = new RenderingHints.Key(1000000) {
		@Override
		public boolean isCompatibleValue(Object val) {
			return val instanceof Integer && ((Integer) val) >= 0;
		}
	};

	private DefaultStatisticalCategoryDataset makeDataset(BenchmarkResultData result) {
		String axisParamKey = plotConfiguration.axisParam;

		List<String> seriesParamKeys = new ArrayList<>(result.paramKeys);
		if (axisParamKey == null) axisParamKey = seriesParamKeys.size() > 1 ? seriesParamKeys.get(1) : null;
		if (axisParamKey != null) seriesParamKeys.remove(axisParamKey);

		DefaultStatisticalCategoryDataset dataset = new DefaultStatisticalCategoryDataset();
		for (Map.Entry<BenchmarkParams, RunResultData> perParamsResult : result.perParamsResults.entrySet()) {
			BenchmarkParams params = perParamsResult.getKey();
			RunResultData.Results primaryResults = perParamsResult.getValue().primaryResults;

			String axisParam = params.getParam(axisParamKey);

			StringBuilder seriesParams = new StringBuilder();
			boolean first = true;
			for (String seriesParamKey : seriesParamKeys) {
				if (first) first = false;
				else seriesParams.append(" - ");
				seriesParams.append(params.getParam(seriesParamKey));
			}

			dataset.add(primaryResults.score, primaryResults.scoreError, seriesParams.toString(), axisParam);
		}
		return dataset;
	}

	private void configureAxis(Axis axis) {
		axis.setLabelFont(axis.getLabelFont().deriveFont(Font.BOLD, renderingConfiguration.bigFontSize));
		axis.setLabelInsets(RectangleInsets.ZERO_INSETS);
		axis.setTickLabelFont(axis.getTickLabelFont().deriveFont(Font.NORMAL, renderingConfiguration.baseFontSize));
	}

	private static final Paint[] PAINTS = new Paint[]{
			new Color(196, 160, 0),
			new Color(206, 92, 0),
			new Color(143, 89, 2),
			new Color(78, 154, 6),
			new Color(32, 74, 135),
			new Color(92, 53, 102),
			new Color(164, 0, 0),
			new Color(85, 87, 83),
	};

	private static final Paint[] PAINTS_MOD2 = new Paint[]{
			new Color(196, 160, 0),
			new Color(252, 233, 79),
			new Color(206, 92, 0),
			new Color(245, 121, 0),
			new Color(143, 89, 2),
			new Color(233, 185, 110),
			new Color(78, 154, 6),
			new Color(138, 226, 52),
			new Color(32, 74, 135),
			new Color(114, 159, 207),
			new Color(92, 53, 102),
			new Color(173, 127, 168),
			new Color(164, 0, 0),
			new Color(239, 41, 41),
			new Color(85, 87, 83),
			new Color(186, 189, 182),
	};

	private static void populateColors(CategoryPlot plot) {
		plot.setDrawingSupplier(
				new DefaultDrawingSupplier(PAINTS,
						DefaultDrawingSupplier.DEFAULT_FILL_PAINT_SEQUENCE,
						DefaultDrawingSupplier.DEFAULT_OUTLINE_PAINT_SEQUENCE,
						DefaultDrawingSupplier.DEFAULT_STROKE_SEQUENCE,
						DefaultDrawingSupplier.DEFAULT_OUTLINE_STROKE_SEQUENCE,
						DefaultDrawingSupplier.DEFAULT_SHAPE_SEQUENCE));
	}
}
