package com.github.ptitjes.jmh.report.annotations;

/**
 * @author Didier Villevalois
 */
public @interface Axis {

	AxisType type() default AxisType.NORMAL;

	String[] params() default {};
}
