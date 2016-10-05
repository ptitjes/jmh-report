package com.github.ptitjes.jmh.report.format;

import com.github.ptitjes.jmh.report.annotations.Orientation;
import com.github.ptitjes.jmh.report.annotations.PlotType;
import com.github.ptitjes.jmh.report.chart.ChartGenerator;
import com.github.ptitjes.jmh.report.chart.PlotConfiguration;
import com.github.ptitjes.jmh.report.data.BenchmarkResultData;
import com.github.ptitjes.jmh.report.data.RunResultData;
import com.itextpdf.awt.FontMapper;
import com.itextpdf.awt.PdfGraphics2D;
import com.itextpdf.text.*;
import com.itextpdf.text.pdf.*;
import com.itextpdf.text.pdf.draw.LineSeparator;
import org.jfree.chart.JFreeChart;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.infra.BenchmarkParams;
import org.openjdk.jmh.infra.IterationParams;
import org.openjdk.jmh.runner.options.TimeValue;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Rectangle2D;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * @author Didier Villevalois
 */
public class PdfFormat implements ReportFormat {

	private final RenderingConfiguration renderingConfiguration;

	public PdfFormat(RenderingConfiguration renderingConfiguration) {
		this.renderingConfiguration = renderingConfiguration;
	}

	@Override
	public void makeReport(String filename, List<BenchmarkResultData> benchmarkResults) throws IOException {
		File file = new File(filename);
		file.getParentFile().mkdirs();

		Document document = new Document(PageSize.A4, 32, 32, 32, 32);
		try {
			PdfWriter writer = PdfWriter.getInstance(document, new FileOutputStream(file));
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

				PlotConfiguration plotConfiguration = new PlotConfiguration();
				plotConfiguration.orientation = Orientation.VERTICAL;
				plotConfiguration.type = PlotType.BARS;
				plotConfiguration.logScale = true;
//				plotConfiguration.axisParam = "implementation";

				chapter.add(makeChart(writer, document, benchmarkResult, plotConfiguration));

				chapter.add(Chunk.NEXTPAGE);
				document.add(chapter);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		document.close();
	}

	private Chapter makeChapter(String longName, int number) {
		Paragraph titleParagraph = new Paragraph(new Chunk(longName, chapterFont()));
		Chapter chapter = new Chapter(titleParagraph, number);
		chapter.setNumberDepth(0);

		LineSeparator separator = new LineSeparator();
		separator.setOffset(14);
		Paragraph separatorParagraph = new Paragraph(new Chunk(separator));
		separatorParagraph.setSpacingAfter(-15);
		chapter.add(separatorParagraph);
		return chapter;
	}

	private Paragraph makeParametersParagraph(Map<BenchmarkParams, RunResultData> perParamsResults) {
		Paragraph parametersParagraph = new Paragraph();
		parametersParagraph.setLeading(0, .9f);
		parametersParagraph.setIndentationLeft(12f);

		BenchmarkParams params = perParamsResults.entrySet().iterator().next().getKey();
		if (params != null) {
			addTextWithHeader(parametersParagraph, "", org.openjdk.jmh.util.Version.getVersion());

			addTextWithHeader(parametersParagraph, "Forks", "" + params.getForks() + " " + getForksString(params.getForks()));

			IterationParams warmup = params.getWarmup();
			if (warmup.getCount() > 0) {
				addTextWithHeader(parametersParagraph, "Warmup", "" + warmup.getCount() + " iterations, " + warmup.getTime() + " each" + (warmup.getBatchSize() <= 1 ? "" : ", " + warmup.getBatchSize() + " calls per op"));
			} else {
				addTextWithHeader(parametersParagraph, "Warmup", "<none>");
			}

			IterationParams measurement = params.getMeasurement();
			if (measurement.getCount() > 0) {
				addTextWithHeader(parametersParagraph, "Measurement", "" + measurement.getCount() + " iterations, " + measurement.getTime() + " each" + (measurement.getBatchSize() <= 1 ? "" : ", " + measurement.getBatchSize() + " calls per op"));
			} else {
				addTextWithHeader(parametersParagraph, "Measurement", "<none>");
			}

			TimeValue timeout = params.getTimeout();
			if (timeout != null) {
				boolean timeoutWarning = timeout.convertTo(TimeUnit.NANOSECONDS) <= measurement.getTime().convertTo(TimeUnit.NANOSECONDS) || timeout.convertTo(TimeUnit.NANOSECONDS) <= warmup.getTime().convertTo(TimeUnit.NANOSECONDS);
				addTextWithHeader(parametersParagraph, "Timeout", "" + timeout + " per iteration" + (timeoutWarning ? ", ***WARNING: The timeout might be too low!***" : ""));
			}

			addTextWithHeader(parametersParagraph, "Threads", "" + params.getThreads() + " " + getThreadsString(params.getThreads()) + (params.shouldSynchIterations() ? ", will synchronize iterations" : (params.getMode() == Mode.SingleShotTime ? "" : ", ***WARNING: Synchronize iterations are disabled!***")));
			addTextWithHeader(parametersParagraph, "Benchmark mode", params.getMode().longLabel());
		}
		return parametersParagraph;
	}

	private void addTextWithHeader(Paragraph parametersParagraph, String name, String content) {
		parametersParagraph.add(new Chunk(name + (name.equals("") ? "" : ": "), boldFont()));
		parametersParagraph.add(new Chunk(content, normalFont()));
		parametersParagraph.add(Chunk.NEWLINE);
	}

	private static String getForksString(int f) {
		return f > 1 ? "forks" : "fork";
	}

	private static String getThreadsString(int t) {
		return t > 1 ? "threads" : "thread";
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
		Phrase phrase = header ? new Phrase(string, tableHeaderFont()) : new Phrase(string, normalFont());

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

	private Image makeChart(PdfWriter writer, Document document, BenchmarkResultData benchmarkResult,
	                        PlotConfiguration plotConfiguration) throws DocumentException {
		JFreeChart chart = new ChartGenerator(renderingConfiguration, plotConfiguration)
				.generateChart(null, benchmarkResult);

		RenderingHints renderingHints = chart.getRenderingHints();
		Integer minHeight = (Integer) renderingHints.get(ChartGenerator.MIN_HEIGHT);

		PdfContentByte contentByte = writer.getDirectContent();

		float width = document.right() - document.left();
		float height = minHeight != null ? minHeight : document.top() - document.bottom() - 20;

		FontMapper fontMapper = new FontMapper() {
			public BaseFont awtToPdf(java.awt.Font font) {
				try {
					return BaseFont.createFont(renderingConfiguration.fontPath, BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
				} catch (DocumentException e) {
					e.printStackTrace();
				} catch (IOException e) {
					e.printStackTrace();
				}
				return null;
			}

			public java.awt.Font pdfToAwt(BaseFont font, int size) {
				return null;
			}
		};

		PdfTemplate template = contentByte.createTemplate(width, height);
		Graphics2D graphics2d = new PdfGraphics2D(template, width, height, fontMapper);
		Rectangle2D rectangle2d = new Rectangle2D.Double(0, 0, width, height);
		chart.draw(graphics2d, rectangle2d);
		graphics2d.dispose();

		Image image = Image.getInstance(template);
		image.scaleToFit(width, document.top() - document.bottom());
		return image;
	}

	private Font chapterFont() {
		return FontFactory.getFont(renderingConfiguration.fontPath, renderingConfiguration.headerFontSize, Font.BOLD);
	}

	private Font normalFont() {
		return FontFactory.getFont(renderingConfiguration.fontPath, renderingConfiguration.baseFontSize, Font.NORMAL);
	}

	private Font boldFont() {
		return FontFactory.getFont(renderingConfiguration.fontPath, renderingConfiguration.baseFontSize, Font.BOLD);
	}

	private Font tableHeaderFont() {
		return FontFactory.getFont(renderingConfiguration.fontPath, renderingConfiguration.baseFontSize, Font.NORMAL, BaseColor.WHITE);
	}
}
