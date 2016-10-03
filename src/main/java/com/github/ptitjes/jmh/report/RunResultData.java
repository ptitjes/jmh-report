package com.github.ptitjes.jmh.report;

import org.openjdk.jmh.infra.BenchmarkParams;

/**
 * @author Didier Villevalois
 */
public class RunResultData {

	public final BenchmarkParams params;
	public final Results primaryResults;

	public RunResultData(BenchmarkParams params, Results primaryResults) {
		this.params = params;
		this.primaryResults = primaryResults;
	}

	public static class Results {
		public final double score;
		public final double scoreError;
		public final double scoreConfidenceMin;
		public final double scoreConfidenceMax;
		public final String scoreUnit;
		public final double[][] rawData;

		public Results(double score, double scoreError, double scoreConfidenceMin, double scoreConfidenceMax, String scoreUnit, double[][] rawData) {
			this.score = score;
			this.scoreError = scoreError;
			this.scoreConfidenceMin = scoreConfidenceMin;
			this.scoreConfidenceMax = scoreConfidenceMax;
			this.scoreUnit = scoreUnit;
			this.rawData = rawData;
		}
	}
}
