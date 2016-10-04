package com.github.ptitjes.jmh.report;

import com.github.ptitjes.jmh.report.options.ReportCommandLineOptions;
import org.openjdk.jmh.results.RunResult;
import org.openjdk.jmh.results.format.ResultFormatType;
import org.openjdk.jmh.runner.Defaults;
import org.openjdk.jmh.runner.NoBenchmarksException;
import org.openjdk.jmh.runner.ProfilersFailedException;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.*;
import org.openjdk.jmh.util.Optional;

import java.util.Collection;

/**
 * @author Didier Villevalois
 */
public class Main {

	public static void main(String[] args) throws Exception {
		try {
			ReportCommandLineOptions reportCmdOptions = new ReportCommandLineOptions(args);

			Optional<String> regenReport = reportCmdOptions.getRegenReport();
			if (regenReport.hasValue()) {
				new ReportGenerator().makeReportFromResultFile(regenReport.get());
				return;
			}

			CommandLineOptions cmdOptions = new CommandLineOptions(args);

			String date = ReportGenerator.reportDate();

			Options options = new OptionsBuilder()
					.parent(cmdOptions)
					.resultFormat(ResultFormatType.JSON)
					.result("results/" + date + ".json")
					.build();

			Runner runner = new Runner(options);

			if (cmdOptions.shouldHelp()) {
				cmdOptions.showHelp();
				return;
			}

			if (cmdOptions.shouldList()) {
				runner.list();
				return;
			}

			if (cmdOptions.shouldListWithParams()) {
				runner.listWithParams(cmdOptions);
				return;
			}

			if (cmdOptions.shouldListProfilers()) {
				cmdOptions.listProfilers();
				return;
			}

			if (cmdOptions.shouldListResultFormats()) {
				cmdOptions.listResultFormats();
				return;
			}

			try {
				Collection<RunResult> runResults = runner.run();
				new ReportGenerator().makeReport("results/" + date + ".pdf", runResults);
			} catch (NoBenchmarksException e) {
				System.err.println("No matching benchmarks. Miss-spelled regexp?");

				if (cmdOptions.verbosity().orElse(Defaults.VERBOSITY) != VerboseMode.EXTRA) {
					System.err.println("Use " + VerboseMode.EXTRA + " verbose mode to debug the pattern matching.");
				} else {
					runner.list();
				}
				System.exit(1);
			} catch (ProfilersFailedException e) {
				// This is not exactly an error, set non-zero exit code
				System.err.println(e.getMessage());
				System.exit(1);
			} catch (RunnerException e) {
				System.err.print("ERROR: ");
				e.printStackTrace(System.err);
				System.exit(1);
			}

		} catch (CommandLineOptionException e) {
			System.err.println("Error parsing command line:");
			System.err.println(" " + e.getMessage());
			System.exit(1);
		}
	}
}
