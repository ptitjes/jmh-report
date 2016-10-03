package com.github.ptitjes.jmh.report.annotations;

/**
 * @author Didier Villevalois
 */
public @interface Plot {

	Axis horizontalAxis() default @Axis(type = AxisType.NORMAL);

	Axis verticalAxis() default @Axis(type = AxisType.NORMAL);
}
