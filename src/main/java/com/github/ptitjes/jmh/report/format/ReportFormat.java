package com.github.ptitjes.jmh.report.format;

import com.github.ptitjes.jmh.report.data.BenchmarkResultData;

import java.io.IOException;
import java.util.List;

/**
 * @author Didier Villevalois
 */
public interface ReportFormat {

	void makeReport(String filename, List<BenchmarkResultData> perNameParamsResults) throws IOException;
}
