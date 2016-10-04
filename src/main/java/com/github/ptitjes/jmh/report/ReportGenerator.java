package com.github.ptitjes.jmh.report;

import com.itextpdf.awt.PdfGraphics2D;
import com.itextpdf.text.*;
import com.itextpdf.text.pdf.*;
import com.itextpdf.text.pdf.draw.LineSeparator;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.Axis;
import org.jfree.chart.axis.AxisLocation;
import org.jfree.chart.axis.CategoryAxis;
import org.jfree.chart.axis.LogarithmicAxis;
import org.jfree.chart.axis.ValueAxis;
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
import org.openjdk.jmh.results.BenchmarkResult;
import org.openjdk.jmh.results.IterationResult;
import org.openjdk.jmh.results.Result;
import org.openjdk.jmh.results.RunResult;
import org.openjdk.jmh.runner.options.TimeValue;
import org.openjdk.jmh.util.Utils;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Paint;
import java.awt.geom.Rectangle2D;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.DateFormat;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * @author Didier Villevalois
 */
public class ReportGenerator {

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

	public void makeReportFromResultFile(String resultFilename) throws IOException {
		String outputFilename = resultFilename.substring(0, resultFilename.length() - ".json".length()) + ".pdf";
		List<RunResultData> allRunResultData = new JSONResultParser().parseFrom(new File(resultFilename));
		new ReportGenerator().makeReport(outputFilename, allRunResultData);
	}

	public void makeReport(String filename, Collection<RunResult> runResults) throws IOException {
		List<RunResultData> allResults = new ArrayList<RunResultData>();

		for (RunResult runResult : runResults) {
			BenchmarkParams params = runResult.getParams();
			allResults.add(new RunResultData(params, makeResults(params, runResult)));
		}

		makeReport(filename, allResults);
	}

	private RunResultData.Results makeResults(BenchmarkParams params, RunResult runResult) {
		int forks = params.getForks();
		int iterations = params.getMeasurement().getCount();

		List<BenchmarkResult> benchmarkResults = new ArrayList<BenchmarkResult>(runResult.getBenchmarkResults());
		double[][] rawData = new double[forks][iterations];
		for (int i = 0; i < forks; i++) {
			BenchmarkResult benchmarkResult = benchmarkResults.get(i);
			List<IterationResult> iterationResults = new ArrayList<IterationResult>(benchmarkResult.getIterationResults());
			for (int j = 0; j < iterations; j++) {
				rawData[i][j] = iterationResults.get(j).getPrimaryResult().getScore();
			}
		}

		Result primaryResult = runResult.getPrimaryResult();
		return new RunResultData.Results(
				primaryResult.getScore(),
				primaryResult.getScoreError(),
				primaryResult.getScoreConfidence()[0],
				primaryResult.getScoreConfidence()[1],
				primaryResult.getScoreUnit(),
				rawData
		);
	}

	public void makeReport(String filename, List<RunResultData> allRunResultData) throws IOException {
		Map<String, Map<BenchmarkParams, RunResultData>> perNameParamsResults = new LinkedHashMap<>();

		Map<String, BenchmarkParams> perNameParams = new LinkedHashMap<String, BenchmarkParams>();
		Map<String, String> perNameTimeUnit = new LinkedHashMap<String, String>();

		for (RunResultData runResultData : allRunResultData) {
			BenchmarkParams params = runResultData.params;
			String benchmark = params.getBenchmark();

			String[] benchmarkSplit = benchmark.split("\\.");

			String benchmarkName = benchmarkSplit[benchmarkSplit.length - 2] + "." + benchmarkSplit[benchmarkSplit.length - 1];

			perNameParams.put(benchmarkName, params);
			perNameTimeUnit.put(benchmarkName, runResultData.primaryResults.scoreUnit);

			Map<BenchmarkParams, RunResultData> results = perNameParamsResults.get(benchmarkName);
			if (results == null) {
				results = new LinkedHashMap<>();
				perNameParamsResults.put(benchmarkName, results);
			}

			results.put(params, runResultData);
		}

		Map<String, StatisticalCategoryDataset> perNameDatasets = new LinkedHashMap<String, StatisticalCategoryDataset>();
		for (Map.Entry<String, Map<BenchmarkParams, RunResultData>> results : perNameParamsResults.entrySet()) {
			String benchmarkName = results.getKey();

			BenchmarkParams benchmarkParams = perNameParams.get(benchmarkName);

			Iterator<String> paramKeys = benchmarkParams.getParamsKeys().iterator();
			String firstParamKey = paramKeys.next();
			List<String> otherParamKeys = new ArrayList<>();
			while (paramKeys.hasNext()) {
				otherParamKeys.add(paramKeys.next());
			}

			DefaultStatisticalCategoryDataset dataset = new DefaultStatisticalCategoryDataset();
			for (Map.Entry<BenchmarkParams, RunResultData> perParams : results.getValue().entrySet()) {
				BenchmarkParams params = perParams.getKey();
				RunResultData.Results result = perParams.getValue().primaryResults;

				String firstParam = params.getParam(firstParamKey);
				StringBuilder otherParams = new StringBuilder();
				boolean first = true;
				for (String otherParamKey : otherParamKeys) {
					if (first) first = false;
					else otherParams.append(" - ");
					otherParams.append(params.getParam(otherParamKey));
				}

				dataset.add(result.score, result.scoreError, firstParam, otherParams.toString());
			}
			perNameDatasets.put(benchmarkName, dataset);
		}

		makeReport(filename, perNameDatasets, perNameParams, perNameTimeUnit);
	}

	public void makeReport(String filename, Map<String, StatisticalCategoryDataset> perNameDatasets,
	                       Map<String, BenchmarkParams> perNameParams,
	                       Map<String, String> perNameTimeUnit) throws IOException {
		File file = new File(filename);
		file.getParentFile().mkdirs();

		PdfWriter writer = null;
		Document document = new Document(PageSize.A4, 32, 32, 32, 32);
		try {
			writer = PdfWriter.getInstance(document, new FileOutputStream(file));
			document.open();

			int chapterNumber = 1;
			for (Map.Entry<String, StatisticalCategoryDataset> entry : perNameDatasets.entrySet()) {
				String name = entry.getKey();
				StatisticalCategoryDataset dataset = entry.getValue();
				BenchmarkParams params = perNameParams == null ? null : perNameParams.get(name);

				Paragraph titleParagraph = new Paragraph(new Chunk(name, CHAPTER_FONT));
				Chapter chapter = new Chapter(titleParagraph, chapterNumber++);
				chapter.setNumberDepth(0);
				chapter.setTriggerNewPage(true);

				LineSeparator separator = new LineSeparator();
				separator.setOffset(14);
				Paragraph separatorParagraph = new Paragraph(new Chunk(separator));
				separatorParagraph.setSpacingAfter(-18);
				chapter.add(separatorParagraph);

				if (params != null) {
					chapter.add(makeHeaderParagraph("", org.openjdk.jmh.util.Version.getVersion()));

					String jvm = params.getJvm();
					if (jvm != null) chapter.add(makeHeaderParagraph("VM invoker", jvm));

					Collection<String> jvmArgs = params.getJvmArgs();
					if (jvmArgs != null && !jvmArgs.isEmpty()) {
						String opts = Utils.join(jvmArgs, " ");
						if (opts.trim().isEmpty()) {
							opts = "<none>";
						}
						chapter.add(makeHeaderParagraph("VM options", opts));
					}

					chapter.add(makeHeaderParagraph("Forks", "" + params.getForks() + " " + getForksString(params.getForks())));

					IterationParams warmup = params.getWarmup();
					if (warmup.getCount() > 0) {
						chapter.add(makeHeaderParagraph("Warmup", "" + warmup.getCount() + " iterations, " + warmup.getTime() + " each" + (warmup.getBatchSize() <= 1 ? "" : ", " + warmup.getBatchSize() + " calls per op")));
					} else {
						chapter.add(makeHeaderParagraph("Warmup", "<none>"));
					}

					IterationParams measurement = params.getMeasurement();
					if (measurement.getCount() > 0) {
						chapter.add(makeHeaderParagraph("Measurement", "" + measurement.getCount() + " iterations, " + measurement.getTime() + " each" + (measurement.getBatchSize() <= 1 ? "" : ", " + measurement.getBatchSize() + " calls per op")));
					} else {
						chapter.add(makeHeaderParagraph("Measurement", "<none>"));
					}

					TimeValue timeout = params.getTimeout();
					if (timeout != null) {
						boolean timeoutWarning = timeout.convertTo(TimeUnit.NANOSECONDS) <= measurement.getTime().convertTo(TimeUnit.NANOSECONDS) || timeout.convertTo(TimeUnit.NANOSECONDS) <= warmup.getTime().convertTo(TimeUnit.NANOSECONDS);
						chapter.add(makeHeaderParagraph("Timeout", "" + timeout + " per iteration" + (timeoutWarning ? ", ***WARNING: The timeout might be too low!***" : "")));
					}

					chapter.add(makeHeaderParagraph("Threads", "" + params.getThreads() + " " + getThreadsString(params.getThreads()) + (params.shouldSynchIterations() ? ", will synchronize iterations" : (params.getMode() == Mode.SingleShotTime ? "" : ", ***WARNING: Synchronize iterations are disabled!***"))));
					chapter.add(makeHeaderParagraph("Benchmark mode", params.getMode().longLabel()));
				}

				Paragraph paragraph = new Paragraph();
				paragraph.setSpacingBefore(1);
				PdfPTable table = new PdfPTable(2 + HEADERS.length);
				table.setWidthPercentage(100);
				table.setWidths(new float[]{100, 100, 60, 60, 50});

				Iterator<String> paramKeys = params.getParamsKeys().iterator();
				String firstParamKey = paramKeys.next();
				List<String> otherParamKeys = new ArrayList<>();
				while (paramKeys.hasNext()) {
					otherParamKeys.add(paramKeys.next());
				}
				String timeUnit = perNameTimeUnit.get(name);

				table.addCell(makeCell(firstParamKey, Element.ALIGN_CENTER, true));
				table.addCell(makeCell(otherParamKeys.toString(), Element.ALIGN_CENTER, true));

				for (int i = 0; i < HEADERS.length; i++) {
					table.addCell(makeCell(HEADERS[i], Element.ALIGN_CENTER, true));
				}

				for (Object column : dataset.getColumnKeys()) {
//					Number javacMean = findMeanFor(column, "Javac", dataset);
//					Number jlatoMean = findMeanFor(column, "JLaTo", dataset);

					for (Object row : dataset.getRowKeys()) {
						Number mean = dataset.getMeanValue((Comparable) row, (Comparable) column);
						Number stdDev = dataset.getStdDevValue((Comparable) row, (Comparable) column);

						if (mean != null) {
							table.addCell(makeCell((String) row, Element.ALIGN_LEFT, false));
							table.addCell(makeCell((String) column, Element.ALIGN_LEFT, false));

							table.addCell(makeCell(String.format("%.3f", mean), Element.ALIGN_RIGHT, false));
							table.addCell(makeCell(String.format("%.3f", stdDev), Element.ALIGN_RIGHT, false));
							table.addCell(makeCell(timeUnit, Element.ALIGN_CENTER, false));
//							table.addCell(makeCell(javacMean == null ? "" : String.format("%.2fx", mean.doubleValue() / javacMean.doubleValue()), Element.ALIGN_CENTER, false));
//							table.addCell(makeCell(jlatoMean == null ? "" : String.format("%.2fx", mean.doubleValue() / jlatoMean.doubleValue()), Element.ALIGN_CENTER, false));
						}
					}
				}

				paragraph.add(table);
				chapter.add(paragraph);

				addChart(writer, document, chapter, name, dataset, firstParamKey, otherParamKeys.toString(), timeUnit);

				document.add(chapter);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		document.close();
	}

	public static String reportDate() {
		DateFormat df = new SimpleDateFormat("yyyy-MM-dd-HH:mm:ss");
		df.setTimeZone(TimeZone.getTimeZone("GMT"));
		return df.format(new Date());
	}

	protected Paragraph makeHeaderParagraph(String name, String content) {
		Chunk chunk = new Chunk(name + (name.equals("") ? "" : ": "), BOLD_FONT);
		Paragraph paragraph = new Paragraph(chunk);
		paragraph.setSpacingAfter(-4);
		paragraph.setIndentationLeft(16);
		paragraph.add(new Chunk(content, NORMAL_FONT));
		return paragraph;
	}

	protected static String getForksString(int f) {
		return f > 1 ? "forks" : "fork";
	}

	protected static String getThreadsString(int t) {
		return t > 1 ? "threads" : "thread";
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

	public static final NumberFormat MEAN_FORMAT = NumberFormat.getInstance();

	private String[] HEADERS = new String[]{"Score", "Error (±)", "Unit"};

	private void addChart(PdfWriter writer, Document document, Chapter chapter, String title, StatisticalCategoryDataset dataset, String firstParamKey, String otherParamKeys, String timeUnit) throws DocumentException {
		boolean bars = false;
		boolean logScale = true;

		JFreeChart chart = generateChart(title, dataset, firstParamKey, otherParamKeys, timeUnit, bars, logScale);

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
		chapter.add(image);
		chapter.add(Chunk.NEXTPAGE);
	}

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

	public void configureAxis(Axis axis) {
		axis.setLabelFont(axis.getLabelFont().deriveFont(Font.BOLD, BIG_FONT_SIZE));
		axis.setLabelInsets(RectangleInsets.ZERO_INSETS);
		axis.setTickLabelFont(axis.getTickLabelFont().deriveFont(Font.NORMAL, BASE_FONT_SIZE));
	}

	public static final Paint[] PAINTS = new Paint[]{
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
