package com.github.ptitjes.jmh.report.chart;

import com.github.ptitjes.jmh.report.annotations.Orientation;
import com.github.ptitjes.jmh.report.annotations.PlotType;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * @author Didier Villevalois
 */
public class PlotConfiguration {

	public String perParam = null;
	public Map<String, Pattern> paramFilters = new HashMap<>();
	public String axisParam = null;
	public PlotType type = PlotType.BARS;
	public Orientation orientation = Orientation.VERTICAL;
	public boolean logScale = false;
}
