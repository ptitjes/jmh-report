package com.github.ptitjes.jmh.report.data;

import org.openjdk.jmh.infra.BenchmarkParams;

import java.util.Map;
import java.util.Set;

/**
 * @author Didier Villevalois
 */
public class BenchmarkResultData {

	public String longName;
	public Set<String> paramKeys;
	public String timeUnit;

	public Map<BenchmarkParams, RunResultData> perParamsResults;
}
