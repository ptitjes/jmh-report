package com.github.ptitjes.jmh.report.annotations;

/**
 * @author Didier Villevalois
 */
public @interface Filter {

	String param() default "";

	String pattern();
}
