package com.github.ptitjes.jmh.report.annotations;

/**
 * @author Didier Villevalois
 */
public @interface Plot {

	Filter[] filters() default {};

	String perParam() default "";

	Axis horizontalAxis() default @Axis;

	Axis verticalAxis() default @Axis;
}
