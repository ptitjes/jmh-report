package com.github.ptitjes.jmh.report.format;

import com.github.ptitjes.jmh.report.data.BenchmarkResultData;
import com.github.ptitjes.jmh.report.data.RunResultData;
import com.itextpdf.awt.PdfGraphics2D;
import com.itextpdf.text.*;
import com.itextpdf.text.Font;
import com.itextpdf.text.Image;
import com.itextpdf.text.pdf.*;
import com.itextpdf.text.pdf.draw.LineSeparator;
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
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.infra.BenchmarkParams;
import org.openjdk.jmh.infra.IterationParams;
import org.openjdk.jmh.runner.options.TimeValue;

import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.NumberFormat;
import java.util.*;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * @author Didier Villevalois
 */
public class PdfFormat implements ReportFormat {

	public static final int BASE_FONT_SIZE = 9;
	public static final int BIG_FONT_SIZE = 11;
	public static final int HEADER_FONT_SIZE = 14;

	public static final Font CHAPTER_FONT = FontFactory.getFont(FontFactory.HELVETICA, HEADER_FONT_SIZE, Font.BOLD);
	public static final Font NORMAL_FONT = FontFactory.getFont(FontFactory.HELVETICA, BASE_FONT_SIZE, Font.NORMAL);
	public static final Font TABLE_HEADER_FONT = FontFactory.getFont(FontFactory.HELVETICA, BASE_FONT_SIZE, Font.NORMAL);
	public static final Font BOLD_FONT = FontFactory.getFont(FontFactory.HELVETICA, BASE_FONT_SIZE, Font.BOLD);

	static {
		TABLE_HEADER_FONT.setColor(BaseColor.WHITE);
	}

	@Override
	public void makeReport(String filename, List<BenchmarkResultData> benchmarkResults) throws IOException {
		File file = new File(filename);
		file.getParentFile().mkdirs();

		PdfWriter writer = null;
		Document document = new Document(PageSize.A4, 32, 32, 32, 32);
		try {
			writer = PdfWriter.getInstance(document, new FileOutputStream(file));
			document.open();

			int chapterNumber = 1;
			for (BenchmarkResultData benchmarkResult : benchmarkResults) {
				String longName = benchmarkResult.longName;
				Set<String> paramKeys = benchmarkResult.paramKeys;
				String timeUnit = benchmarkResult.timeUnit;
				Map<BenchmarkParams, RunResultData> perParamsResults = benchmarkResult.perParamsResults;

				Chapter chapter = makeChapter(longName, chapterNumber++);
				chapter.add(makeParametersParagraph(perParamsResults));
				chapter.add(makeTableParagraph(paramKeys, timeUnit, perParamsResults));

				chapter.add(makeChart(writer, document, benchmarkResult));

				chapter.add(Chunk.NEXTPAGE);
				document.add(chapter);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		document.close();
	}

	private Paragraph makeParametersParagraph(Map<BenchmarkParams, RunResultData> perParamsResults) {
		Paragraph parametersParagraph = new Paragraph();
		BenchmarkParams params = perParamsResults.entrySet().iterator().next().getKey();
		if (params != null) {
			parametersParagraph.add(makeHeaderParagraph("", org.openjdk.jmh.util.Version.getVersion()));

			parametersParagraph.add(makeHeaderParagraph("Forks", "" + params.getForks() + " " + getForksString(params.getForks())));

			IterationParams warmup = params.getWarmup();
			if (warmup.getCount() > 0) {
				parametersParagraph.add(makeHeaderParagraph("Warmup", "" + warmup.getCount() + " iterations, " + warmup.getTime() + " each" + (warmup.getBatchSize() <= 1 ? "" : ", " + warmup.getBatchSize() + " calls per op")));
			} else {
				parametersParagraph.add(makeHeaderParagraph("Warmup", "<none>"));
			}

			IterationParams measurement = params.getMeasurement();
			if (measurement.getCount() > 0) {
				parametersParagraph.add(makeHeaderParagraph("Measurement", "" + measurement.getCount() + " iterations, " + measurement.getTime() + " each" + (measurement.getBatchSize() <= 1 ? "" : ", " + measurement.getBatchSize() + " calls per op")));
			} else {
				parametersParagraph.add(makeHeaderParagraph("Measurement", "<none>"));
			}

			TimeValue timeout = params.getTimeout();
			if (timeout != null) {
				boolean timeoutWarning = timeout.convertTo(TimeUnit.NANOSECONDS) <= measurement.getTime().convertTo(TimeUnit.NANOSECONDS) || timeout.convertTo(TimeUnit.NANOSECONDS) <= warmup.getTime().convertTo(TimeUnit.NANOSECONDS);
				parametersParagraph.add(makeHeaderParagraph("Timeout", "" + timeout + " per iteration" + (timeoutWarning ? ", ***WARNING: The timeout might be too low!***" : "")));
			}

			parametersParagraph.add(makeHeaderParagraph("Threads", "" + params.getThreads() + " " + getThreadsString(params.getThreads()) + (params.shouldSynchIterations() ? ", will synchronize iterations" : (params.getMode() == Mode.SingleShotTime ? "" : ", ***WARNING: Synchronize iterations are disabled!***"))));
			parametersParagraph.add(makeHeaderParagraph("Benchmark mode", params.getMode().longLabel()));
		}
		return parametersParagraph;
	}

	private Paragraph makeHeaderParagraph(String name, String content) {
		Chunk chunk = new Chunk(name + (name.equals("") ? "" : ": "), BOLD_FONT);
		Paragraph paragraph = new Paragraph(chunk);
		paragraph.setSpacingAfter(-4);
		paragraph.setIndentationLeft(16);
		paragraph.add(new Chunk(content, NORMAL_FONT));
		return paragraph;
	}

	private static String getForksString(int f) {
		return f > 1 ? "forks" : "fork";
	}

	private static String getThreadsString(int t) {
		return t > 1 ? "threads" : "thread";
	}

	private Chapter makeChapter(String longName, int number) {
		Paragraph titleParagraph = new Paragraph(new Chunk(longName, CHAPTER_FONT));
		Chapter chapter = new Chapter(titleParagraph, number);
		chapter.setNumberDepth(0);

		LineSeparator separator = new LineSeparator();
		separator.setOffset(14);
		Paragraph separatorParagraph = new Paragraph(new Chunk(separator));
		separatorParagraph.setSpacingAfter(-18);
		chapter.add(separatorParagraph);
		return chapter;
	}

	private String[] HEADERS = new String[]{"Score", "Error (±)", "Unit"};

	private Paragraph makeTableParagraph(Set<String> paramKeys, String timeUnit, Map<BenchmarkParams, RunResultData> perParamsResults) throws DocumentException {
		Paragraph paragraph = new Paragraph();
		paragraph.setSpacingBefore(1);
		PdfPTable table = new PdfPTable(paramKeys.size() + HEADERS.length);
		table.setWidthPercentage(100);
		table.setWidths(new float[]{100, 100, 60, 60, 50});

		// Output table headers
		for (String paramKey : paramKeys) {
			table.addCell(makeCell(paramKey, Element.ALIGN_CENTER, true));
		}
		for (int i = 0; i < HEADERS.length; i++) {
			table.addCell(makeCell(HEADERS[i], Element.ALIGN_CENTER, true));
		}

		// Output table content
		for (Map.Entry<BenchmarkParams, RunResultData> perParamsResult : perParamsResults.entrySet()) {
			BenchmarkParams params = perParamsResult.getKey();
			RunResultData.Results primaryResults = perParamsResult.getValue().primaryResults;

//					Number javacMean = findMeanFor(column, "Javac", dataset);
//					Number jlatoMean = findMeanFor(column, "JLaTo", dataset);

			Number mean = primaryResults.score;
			Number stdDev = primaryResults.scoreError;

			for (String paramKey : paramKeys) {
				table.addCell(makeCell(params.getParam(paramKey), Element.ALIGN_LEFT, false));
			}

			table.addCell(makeCell(String.format("%.3f", mean), Element.ALIGN_RIGHT, false));
			table.addCell(makeCell(String.format("%.3f", stdDev), Element.ALIGN_RIGHT, false));
			table.addCell(makeCell(timeUnit, Element.ALIGN_CENTER, false));
		}
		paragraph.add(table);
		return paragraph;
	}

	private PdfPCell makeCell(String string, int alignment, boolean header) {
		Phrase phrase = header ? new Phrase(string, TABLE_HEADER_FONT) : new Phrase(string, NORMAL_FONT);

		PdfPCell cell = new PdfPCell(phrase);
		cell.setHorizontalAlignment(alignment);

		if (header) {
			cell.setPadding(4.0f);
			cell.setPaddingTop(1.0f);
			cell.setBackgroundColor(BaseColor.BLACK);
			cell.setVerticalAlignment(Element.ALIGN_BOTTOM);
		} else {
			cell.setPadding(3.0f);
			cell.setPaddingTop(.0f);
		}

		return cell;
	}

	private Image makeChart(PdfWriter writer, Document document, BenchmarkResultData benchmarkResult) throws DocumentException {
		boolean bars = false;
		boolean logScale = true;

		Set<String> paramKeys = benchmarkResult.paramKeys;
		String timeUnit = benchmarkResult.timeUnit;

		Iterator<String> paramKeysIterator = paramKeys.iterator();
		String firstParamKey = paramKeysIterator.next();
		List<String> otherParamKeys = new ArrayList<>();
		while (paramKeysIterator.hasNext()) {
			otherParamKeys.add(paramKeysIterator.next());
		}

		StatisticalCategoryDataset dataset = makeDataset(benchmarkResult);
		JFreeChart chart = generateChart(null, dataset, firstParamKey, otherParamKeys.toString(), timeUnit, bars, logScale);

		PdfContentByte contentByte = writer.getDirectContent();

		float width = document.right() - document.left();
		float height = bars ? dataset.getColumnCount() * dataset.getRowCount() * 12 + 80
				: document.top() - document.bottom() - 20 /*dataset.getColumnCount() * 32 + 80*/;

		PdfTemplate template = contentByte.createTemplate(width, height);
		Graphics2D graphics2d = new PdfGraphics2D(template, width, height);
		Rectangle2D rectangle2d = new Rectangle2D.Double(0, 0, width, height);
		chart.draw(graphics2d, rectangle2d);
		graphics2d.dispose();

		Image image = Image.getInstance(template);
		image.scaleToFit(width, document.top() - document.bottom());
		return image;
	}

	private DefaultStatisticalCategoryDataset makeDataset(BenchmarkResultData result) {
		Iterator<String> paramKeys = result.paramKeys.iterator();
		String firstParamKey = paramKeys.next();
		List<String> otherParamKeys = new ArrayList<>();
		while (paramKeys.hasNext()) {
			otherParamKeys.add(paramKeys.next());
		}

		DefaultStatisticalCategoryDataset dataset = new DefaultStatisticalCategoryDataset();
		for (Map.Entry<BenchmarkParams, RunResultData> perParamsResult : result.perParamsResults.entrySet()) {
			BenchmarkParams params = perParamsResult.getKey();
			RunResultData.Results primaryResults = perParamsResult.getValue().primaryResults;

			String firstParam = params.getParam(firstParamKey);
			StringBuilder otherParams = new StringBuilder();
			boolean first = true;
			for (String otherParamKey : otherParamKeys) {
				if (first) first = false;
				else otherParams.append(" - ");
				otherParams.append(params.getParam(otherParamKey));
			}

			dataset.add(primaryResults.score, primaryResults.scoreError, firstParam, otherParams.toString());
		}
		return dataset;
	}

	private static final NumberFormat MEAN_FORMAT = NumberFormat.getInstance();

	private JFreeChart generateChart(String title, StatisticalCategoryDataset dataset,
	                                 String firstParamKey, String otherParamKeys,
	                                 String timeUnit, boolean bars, boolean logScale) {
		final JFreeChart chart = ChartFactory.createBarChart("", // title
				otherParamKeys, // category axis label
				timeUnit, // value axis label
				dataset, // data
				bars ? PlotOrientation.HORIZONTAL : PlotOrientation.VERTICAL, // orientation
				true, // include legend
				true, // tooltips
				false // urls
		);

		chart.setBackgroundPaint(Color.white);
		chart.setPadding(RectangleInsets.ZERO_INSETS);

		final CategoryPlot plot = chart.getCategoryPlot();
		plot.setBackgroundPaint(new Color(220, 220, 220));
		plot.setRangeAxisLocation(AxisLocation.BOTTOM_OR_LEFT);

		if (bars) {
			StatisticalBarRenderer renderer = new StatisticalBarRenderer();
			renderer.setBaseItemLabelGenerator(new StandardCategoryItemLabelGenerator("{2}", MEAN_FORMAT));
			renderer.setBasePositiveItemLabelPosition(new ItemLabelPosition(ItemLabelAnchor.OUTSIDE9, TextAnchor.CENTER_LEFT));
			renderer.setItemLabelAnchorOffset(-5);
			renderer.setBaseItemLabelsVisible(true);
			renderer.setBaseItemLabelFont(renderer.getBaseItemLabelFont().deriveFont(Font.NORMAL, BASE_FONT_SIZE));
			renderer.setBaseItemLabelPaint(Color.white);
			renderer.setErrorIndicatorStroke(new BasicStroke(0));
			renderer.setItemMargin(0);
			plot.setRenderer(renderer);

			renderer.setBaseLegendTextFont(renderer.getBaseItemLabelFont().deriveFont(Font.NORMAL, BASE_FONT_SIZE));
		} else {
			StatisticalLineAndShapeRenderer renderer = new StatisticalLineAndShapeRenderer();
			renderer.setBaseShapesVisible(true);
			renderer.setErrorIndicatorStroke(new BasicStroke(0));
			plot.setRenderer(renderer);

			renderer.setBaseLegendTextFont(renderer.getBaseItemLabelFont().deriveFont(Font.NORMAL, BASE_FONT_SIZE));
		}

		populateColors(plot);

		CategoryAxis domainAxis = plot.getDomainAxis();
		configureAxis(domainAxis);

		domainAxis.setLabel(otherParamKeys);

		domainAxis.setCategoryMargin(0.05);
		domainAxis.setUpperMargin(0.01);
		domainAxis.setLowerMargin(0.01);

		if (logScale) {
			LogarithmicAxis rangeAxis = new LogarithmicAxis(timeUnit);
			configureAxis(rangeAxis);

			rangeAxis.setLabel(timeUnit);
			rangeAxis.setLabelInsets(RectangleInsets.ZERO_INSETS);
			rangeAxis.setMinorTickMarksVisible(true);
			rangeAxis.setAutoRange(true);
			rangeAxis.setAutoRangeIncludesZero(false);
			plot.setRangeAxis(rangeAxis);
		} else {
			ValueAxis rangeAxis = plot.getRangeAxis();
			configureAxis(rangeAxis);

			rangeAxis.setLabel(timeUnit);
			rangeAxis.setLabelInsets(RectangleInsets.ZERO_INSETS);
			rangeAxis.setAutoRange(true);
			configureAxis(rangeAxis);
		}

		return chart;
	}

	private void configureAxis(Axis axis) {
		axis.setLabelFont(axis.getLabelFont().deriveFont(Font.BOLD, BIG_FONT_SIZE));
		axis.setLabelInsets(RectangleInsets.ZERO_INSETS);
		axis.setTickLabelFont(axis.getTickLabelFont().deriveFont(Font.NORMAL, BASE_FONT_SIZE));
	}

	private static final Paint[] PAINTS = new Paint[]{
			new Color(196, 160, 0),
			new Color(206, 92, 0),
			new Color(143, 89, 2),
			new Color(78, 154, 6),
			new Color(32, 74, 135),
			new Color(92, 53, 102),
			new Color(164, 0, 0),
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
