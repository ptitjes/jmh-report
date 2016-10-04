package com.github.ptitjes.jmh.report.data;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.infra.BenchmarkParams;
import org.openjdk.jmh.infra.IterationParams;
import org.openjdk.jmh.runner.IterationType;
import org.openjdk.jmh.runner.WorkloadParams;
import org.openjdk.jmh.runner.options.TimeValue;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Didier Villevalois
 */
public class JSONResultParser {

	public List<RunResultData> parseFrom(File file) throws IOException {
		FileInputStream inputStream = new FileInputStream(file);
		try {
			JSONArray jsonArray = new JSONArray(new JSONTokener(inputStream));
			return readJSONData(jsonArray);
		} finally {
			inputStream.close();
		}
	}

	public List<RunResultData> parseFrom(String string) {
		JSONArray jsonArray = new JSONArray(new JSONTokener(string));
		return readJSONData(jsonArray);
	}

	private List<RunResultData> readJSONData(JSONArray jsonArray) {
		List<RunResultData> runResults = new ArrayList<RunResultData>();

		for (Object element : jsonArray) {
			JSONObject jsonObject = (JSONObject) element;

			BenchmarkParams benchmarkParams = readBenchmarkParams(jsonObject);
			runResults.add(new RunResultData(
					benchmarkParams,
					readBenchmarkResults(jsonObject.getJSONObject("primaryMetric"), benchmarkParams)
			));
		}
		return runResults;
	}

	private BenchmarkParams readBenchmarkParams(JSONObject jsonObject) {
		return new BenchmarkParams(
				jsonObject.getString("benchmark"),
				null,
				true,
				jsonObject.getInt("threads"),
				null,
				jsonObject.getInt("forks"),
				0,
				readIterationParams(jsonObject, IterationType.WARMUP),
				readIterationParams(jsonObject, IterationType.MEASUREMENT),
				Mode.deepValueOf(jsonObject.getString("mode")),
				readWorkloadParams(jsonObject.getJSONObject("params")),
				null,
				0,
				null,
				Collections.EMPTY_LIST,
				null
		);
	}

	private IterationParams readIterationParams(JSONObject jsonObject, IterationType type) {
		String prefix = type.name().toLowerCase();
		return new IterationParams(
				type,
				jsonObject.getInt(prefix + "Iterations"),
				readTimeValue(jsonObject, prefix + "Time"),
				jsonObject.getInt(prefix + "BatchSize")
		);
	}

	private TimeValue readTimeValue(JSONObject jsonObject, String label) {
		return TimeValue.fromString(jsonObject.getString(label));
	}

	private WorkloadParams readWorkloadParams(JSONObject jsonObject) {
		WorkloadParams params = new WorkloadParams();
		int order = 0;
		for (String key : jsonObject.keySet()) {
			params.put(key, jsonObject.getString(key), order++);
		}
		return params;
	}

	private RunResultData.Results readBenchmarkResults(JSONObject jsonObject, BenchmarkParams benchmarkParams) {
		JSONArray array = jsonObject.getJSONArray("rawData");

		int forks = benchmarkParams.getForks();
		int iterations = benchmarkParams.getMeasurement().getCount();
		double[][] rawData = new double[forks][iterations];
		for (int i = 0; i < forks; i++) {
			for (int j = 0; j < iterations; j++) {
				rawData[i][j] = array.getJSONArray(i).getDouble(j);
			}
		}

		return new RunResultData.Results(
				jsonObject.getDouble("score"),
				jsonObject.getDouble("scoreError"),
				jsonObject.getJSONArray("scoreConfidence").getDouble(0),
				jsonObject.getJSONArray("scoreConfidence").getDouble(1),
				jsonObject.getString("scoreUnit"),
				rawData
		);
	}
}
