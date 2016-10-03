package com.github.ptitjes.jmh.report.options;

import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;
import joptsimple.ValueConverter;
import joptsimple.util.RegexMatcher;
import org.openjdk.jmh.util.Optional;

/**
 * @author Didier Villevalois
 */
public class ReportCommandLineOptions implements ReportOptions {

	private final OptionParser parser;
	private final Optional<String> regenReport;

	public ReportCommandLineOptions(String[] args) {
		parser = new OptionParser();

		OptionSpec<String> regenReport = parser.accepts("regenReport", "Regenerate report from json result file.")
				.withRequiredArg().withValuesConvertedBy(new RegexMatcher(".*\\.json", 0))
				.describedAs("filename.json");

		parser.allowsUnrecognizedOptions();

		OptionSet optionSet = parser.parse(args);

		this.regenReport = toOptional(regenReport, optionSet);
	}

	private static <T> Optional<T> toOptional(OptionSpec<T> option, OptionSet set) {
		if (set.has(option)) {
			return Optional.eitherOf(option.value(set));
		}
		return Optional.none();
	}

	@Override
	public Optional<String> getRegenReport() {
		return regenReport;
	}
}
