package com.github.ptitjes.jmh.report;

import com.github.ptitjes.jmh.report.data.BenchmarkResultData;
import com.github.ptitjes.jmh.report.data.JSONResultParser;
import com.github.ptitjes.jmh.report.data.RunResultData;
import com.github.ptitjes.jmh.report.format.PdfFormat;
import com.github.ptitjes.jmh.report.format.RenderingConfiguration;
import org.openjdk.jmh.infra.BenchmarkParams;
import org.openjdk.jmh.results.BenchmarkResult;
import org.openjdk.jmh.results.IterationResult;
import org.openjdk.jmh.results.Result;
import org.openjdk.jmh.results.RunResult;

import java.io.File;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * @author Didier Villevalois
 */
public class ReportGenerator {

	public void makeReportFromResultFile(String resultFilename) throws IOException {
		String outputFilename = resultFilename.substring(0, resultFilename.length() - ".json".length()) + ".pdf";
		List<RunResultData> allRunResultData = new JSONResultParser().parseFrom(new File(resultFilename));
		new ReportGenerator().makeReport(outputFilename, allRunResultData);
	}

	public void makeReport(String filename, Collection<RunResult> runResults) throws IOException {
		List<RunResultData> allResults = new ArrayList<>();

		for (RunResult runResult : runResults) {
			BenchmarkParams params = runResult.getParams();
			allResults.add(new RunResultData(params, makeResults(params, runResult)));
		}

		makeReport(filename, allResults);
	}

	private RunResultData.Results makeResults(BenchmarkParams params, RunResult runResult) {
		int forks = params.getForks();
		int iterations = params.getMeasurement().getCount();

		List<BenchmarkResult> benchmarkResults = new ArrayList<>(runResult.getBenchmarkResults());
		double[][] rawData = new double[forks][iterations];
		for (int i = 0; i < forks; i++) {
			BenchmarkResult benchmarkResult = benchmarkResults.get(i);
			List<IterationResult> iterationResults = new ArrayList<>(benchmarkResult.getIterationResults());
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

		for (RunResultData resultData : allRunResultData) {
			BenchmarkParams benchmarkParams = resultData.params;
			String benchmarkName = benchmarkParams.getBenchmark();

			Map<BenchmarkParams, RunResultData> results = perNameParamsResults.get(benchmarkName);
			if (results == null) {
				results = new LinkedHashMap<>();
				perNameParamsResults.put(benchmarkName, results);
			}

			results.put(benchmarkParams, resultData);
		}

		List<BenchmarkResultData> benchmarkResults = new ArrayList<>();
		for (Map.Entry<String, Map<BenchmarkParams, RunResultData>> results : perNameParamsResults.entrySet()) {
			String benchmarkName = results.getKey();
			Map<BenchmarkParams, RunResultData> perParamsResults = results.getValue();

			BenchmarkResultData benchmarkResult = new BenchmarkResultData();
			benchmarkResult.longName = benchmarkName;
			benchmarkResult.perParamsResults = perParamsResults;

			String timeUnit = null;
			Set<String> paramKeys = new LinkedHashSet<>();
			for (Map.Entry<BenchmarkParams, RunResultData> entry : perParamsResults.entrySet()) {
				BenchmarkParams benchmarkParams = entry.getKey();
				RunResultData resultData = entry.getValue();

				paramKeys.addAll(benchmarkParams.getParamsKeys());

				if (timeUnit != null) assert timeUnit == resultData.primaryResults.scoreUnit;
				else timeUnit = resultData.primaryResults.scoreUnit;
			}

			benchmarkResult.paramKeys = paramKeys;
			benchmarkResult.timeUnit = timeUnit;

			benchmarkResults.add(benchmarkResult);
		}

		new PdfFormat(new RenderingConfiguration())
				.makeReport(filename, benchmarkResults);
	}

	public static String reportDate() {
		DateFormat df = new SimpleDateFormat("yyyy-MM-dd-HH:mm:ss");
		df.setTimeZone(TimeZone.getTimeZone("GMT"));
		return df.format(new Date());
	}
}
