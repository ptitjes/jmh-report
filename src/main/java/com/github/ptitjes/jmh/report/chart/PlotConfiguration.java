package com.github.ptitjes.jmh.report.chart;

import com.github.ptitjes.jmh.report.annotations.Orientation;
import com.github.ptitjes.jmh.report.annotations.PlotType;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Didier Villevalois
 */
public class PlotConfiguration {

	public String perParam;
	public Map<String, String> paramFilters = new HashMap<>();
	public String axisParam;
	public PlotType type;
	public Orientation orientation;
	public boolean logScale;
}
