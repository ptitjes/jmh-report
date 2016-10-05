package com.github.ptitjes.jmh.report.annotations;

/**
 * @author Didier Villevalois
 */
public @interface Plot {

	String perParam() default "";

	Filter[] filters() default {};

	String axisParam() default "";

	PlotType type() default PlotType.BARS;

	Orientation orientation() default Orientation.VERTICAL;

	boolean logScale() default false;
}
