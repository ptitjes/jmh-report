package com.github.ptitjes.jmh.report.options;

import com.github.ptitjes.jmh.report.annotations.Filter;
import com.github.ptitjes.jmh.report.annotations.Plot;
import com.github.ptitjes.jmh.report.annotations.Report;
import com.github.ptitjes.jmh.report.chart.PlotConfiguration;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * @author Didier Villevalois
 */
public class AnnotationReader {

	private static Map<String, Report> perClassAnnotation = new HashMap<>();
	private static Map<String, Report> perMethodAnnotation = new HashMap<>();

	public List<PlotConfiguration> makePlotConfigurationsFor(String longMethodName) {
		List<PlotConfiguration> configurations = new ArrayList<>();

		Report annotationForMethod = retrieveAnnotationForMethod(longMethodName);
		Report annotationForClass = retrieveAnnotationForClass(classNameFor(longMethodName));

		if (annotationForClass != null) {
			for (Plot plot : annotationForClass.plots()) {
				configurations.add(buildPlotConfigurations(plot));
			}
		}
		if (annotationForMethod != null) {
			for (Plot plot : annotationForMethod.plots()) {
				configurations.add(buildPlotConfigurations(plot));
			}
		}

		if (configurations.isEmpty()) configurations.add(new PlotConfiguration());
		return configurations;
	}

	private PlotConfiguration buildPlotConfigurations(Plot plot) {
		PlotConfiguration configuration = new PlotConfiguration();
		configuration.perParam = plot.perParam().isEmpty() ? null : plot.perParam();
		configuration.paramFilters = buildPlotFilters(plot.filters());
		configuration.axisParam = plot.axisParam().isEmpty() ? null : plot.axisParam();
		configuration.type = plot.type();
		configuration.orientation = plot.orientation();
		configuration.logScale = plot.logScale();
		return configuration;
	}

	private Map<String, Pattern> buildPlotFilters(Filter[] filters) {
		HashMap<String, Pattern> paramFilters = new HashMap<>();
		for (Filter filter : filters) {
			paramFilters.put(filter.param(), Pattern.compile(filter.pattern()));
		}
		return paramFilters;
	}

	public static String classNameFor(String longMethodName) {
		int index = longMethodName.lastIndexOf('.');
		return longMethodName.substring(0, index);
	}

	public static Report getAnnotationForClass(String className) {
		if (!perClassAnnotation.containsKey(className)) {
			Report annotation = retrieveAnnotationForClass(className);
			perClassAnnotation.put(className, annotation);
		}
		return perClassAnnotation.get(className);
	}

	public static Report getAnnotationForMethod(String longMethodName) {
		if (!perMethodAnnotation.containsKey(longMethodName)) {
			Report annotation = retrieveAnnotationForMethod(longMethodName);
			perMethodAnnotation.put(longMethodName, annotation);
		}
		return perMethodAnnotation.get(longMethodName);
	}

	private static Report retrieveAnnotationForClass(String className) {
		try {
			Class<?> aClass = Class.forName(className);
			return aClass.getAnnotation(Report.class);
		} catch (ClassNotFoundException e) {
			throw new RuntimeException("Can't retrieve @Report annotation", e);
		}
	}

	private static Report retrieveAnnotationForMethod(String longMethodName) {
		int index = longMethodName.lastIndexOf('.');
		String className = longMethodName.substring(0, index);
		String methodName = longMethodName.substring(index + 1);
		try {
			Class<?> aClass = Class.forName(className);
			Method[] declaredMethods = aClass.getDeclaredMethods();
			for (Method declaredMethod : declaredMethods) {
				if (declaredMethod.getName().equals(methodName))
					return declaredMethod.getAnnotation(Report.class);
			}

			throw new RuntimeException("Can't retrieve method named '" + longMethodName + "'");
		} catch (ClassNotFoundException e) {
			throw new RuntimeException("Can't retrieve @Report annotation", e);
		}
	}
}
