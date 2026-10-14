/*
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */
package com.l2jfree.smoke;

import java.io.OutputStream;
import java.io.PrintStream;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * A Lineage II client for the end-to-end check of the platform: it logs in, takes the one world of the server list,
 * creates a character, enters the world, logs out, and logs in again. It needs nothing but the JDK and uses no class
 * of the server, so a defect in the framing, a cipher, or the order of packets shows up here and not only in the
 * tests of the server.
 * <p>
 * It exits with 0 when every step passed and with 1 when one failed. Each step prints a line.
 */
public final class SmokeClient
{
	private static final String USAGE = "usage: SmokeClient [--host HOST] [--login-port N] [--world-port N] "
			+ "[--protocol-revision N] [--timeout-seconds N] [--account NAME] [--password TEXT] [--existing-characters N] "
			+ "[--sessions N] [--parallel N] [--scenario default|seed-loop]";

	/** The settings of a run. */
	record Config(String host, int loginPort, int worldPort, int protocolRevision, int timeoutSeconds, String account,
			String password, int existingCharacters, int sessions, int parallel, String scenario)
	{
		/** {@code existingCharacters} when the number of characters the account has on entry is not checked */
		static final int UNCHECKED = -1;
		/** Create a character, enter the world, and leave. The image job uses this. */
		static final String SCENARIO_DEFAULT = "default";
		/** Play the authored world's loop, then enter again and compare the character. */
		static final String SCENARIO_SEED_LOOP = "seed-loop";
		
		static Config defaults()
		{
			return new Config("127.0.0.1", 2106, 7777, 87, 120, null, null, UNCHECKED, 1, 1, SCENARIO_DEFAULT);
		}
		
		boolean seedLoop()
		{
			return SCENARIO_SEED_LOOP.equals(scenario);
		}
	}

	/** The outcome of a run: whether it passed, the lines it printed, and the reason when it did not. */
	public record Result(boolean passed, List<String> lines, String failure)
	{
	}

	private SmokeClient()
	{
	}

	public static void main(String[] args)
	{
		final Config config;
		try
		{
			config = parse(args);
		}
		catch (IllegalArgumentException e)
		{
			System.err.println(e.getMessage());
			System.err.println(USAGE);
			System.exit(2);
			return;
		}

		if (config.sessions() > 1)
		{
			System.exit(runMany(config, System.out) ? 0 : 1);
			return;
		}

		final Result result = run(config, new Reporter(System.out), 0);
		System.out.println(result.passed() ? "SMOKE PASSED" : "SMOKE FAILED: " + result.failure());
		System.exit(result.passed() ? 0 : 1);
	}

	/**
	 * Takes many clients through the whole flow at once, each with an account of its own, and reports how many
	 * passed and why the others did not.
	 *
	 * @return true when every session passed
	 */
	static boolean runMany(Config config, PrintStream out)
	{
		final ExecutorService executor = Executors.newFixedThreadPool(config.parallel());
		final long begin = System.nanoTime();
		try
		{
			final List<Future<Result>> futures = new ArrayList<Future<Result>>();
			for (int i = 0; i < config.sessions(); i++)
			{
				final int sessionIndex = i;
				// The default scenario keeps only the summary. The seed loop's object ids and counts have to
				// stay visible when many sessions run at once, so those lines are kept and printed afterwards.
				final java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
				final PrintStream sessionOut = config.seedLoop() ? new PrintStream(buffer)
						: new PrintStream(OutputStream.nullOutputStream());
				futures.add(executor.submit(() -> {
					Result result = run(config, new Reporter(sessionOut), sessionIndex);
					if (config.seedLoop())
					{
						sessionOut.flush();
						synchronized (out)
						{
							out.print(buffer.toString());
						}
					}
					return result;
				}));
			}
			
			final Map<String, Integer> failures = new LinkedHashMap<String, Integer>();
			int passed = 0;
			for (Future<Result> future : futures)
			{
				Result result;
				try
				{
					result = future.get();
				}
				catch (InterruptedException | ExecutionException e)
				{
					result = new Result(false, List.of(), e.toString());
				}
				if (result.passed())
					passed++;
				else
					failures.merge(result.failure(), 1, Integer::sum);
			}
			
			out.println(passed + " of " + config.sessions() + " sessions passed, " + config.parallel() + " at a time, in "
					+ (System.nanoTime() - begin) / 1_000_000_000L + " s");
			failures.forEach((reason, count) -> out.println("FAILED " + count + " x " + reason));
			return passed == config.sessions();
		}
		finally
		{
			executor.shutdownNow();
		}
	}

	/** Runs the whole flow against a server and reports it. Never throws for a failed step. */
	static Result run(Config config, Reporter reporter)
	{
		return run(config, reporter, 0);
	}

	/**
	 * Runs one session. {@code sessionIndex} chooses the first beast of the seed loop: session i takes the
	 * i-th visible beast of that template, ordered by coordinates. The default scenario does not use it.
	 */
	static Result run(Config config, Reporter reporter, int sessionIndex)
	{
		final Deadline deadline = Deadline.after(config.timeoutSeconds());
		// the password of the test account is random, so it comes from a secure source
		final Random random = new SecureRandom();
		final String account = config.account() != null ? config.account() : "smoke" + letters(random, 6);
		final String password = config.password() != null ? config.password() : "pw" + letters(random, 10);
		final String character = "Smoke" + letters(random, 6);
		// a new account has no characters; a given one has as many as the caller says, when it says
		final int expectedCharacters = config.account() == null ? 0 : config.existingCharacters();

		try
		{
			if (config.seedLoop())
				SeedLoop.play(config, deadline, reporter, account, password, character, sessionIndex);
			else
			{
				LoginFlow.Session session = LoginFlow.selectWorld(config, deadline, reporter, account, password);
				WorldFlow.play(config, deadline, reporter, session, account, expectedCharacters, character);
				LoginFlow.loginAgain(config, deadline, reporter, account, password);
			}
			return new Result(true, reporter.lines(), null);
		}
		catch (SmokeException e)
		{
			reporter.fail(e.getMessage());
			return new Result(false, reporter.lines(), e.getMessage());
		}
		catch (RuntimeException e)
		{
			String reason = e.getClass().getSimpleName() + ": " + e.getMessage();
			reporter.fail(reason);
			return new Result(false, reporter.lines(), reason);
		}
	}

	static Config parse(String[] args)
	{
		final Config defaults = Config.defaults();
		String host = defaults.host();
		int loginPort = defaults.loginPort();
		int worldPort = defaults.worldPort();
		int protocolRevision = defaults.protocolRevision();
		int timeoutSeconds = defaults.timeoutSeconds();
		String account = null;
		String password = null;
		int existingCharacters = Config.UNCHECKED;
		int sessions = 1;
		int parallel = 1;
		String scenario = Config.SCENARIO_DEFAULT;
		
		for (int i = 0; i < args.length; i++)
		{
			String name = args[i];
			if (i + 1 >= args.length)
				throw new IllegalArgumentException("missing value for " + name);
			
			String value = args[++i];
			switch (name)
			{
				case "--host":
					host = value;
					break;
				case "--login-port":
					loginPort = number(name, value);
					break;
				case "--world-port":
					worldPort = number(name, value);
					break;
				case "--protocol-revision":
					protocolRevision = number(name, value);
					break;
				case "--timeout-seconds":
					timeoutSeconds = number(name, value);
					break;
				case "--account":
					account = value;
					break;
				case "--password":
					password = value;
					break;
				case "--existing-characters":
					existingCharacters = number(name, value);
					break;
				case "--sessions":
					sessions = number(name, value);
					break;
				case "--parallel":
					parallel = number(name, value);
					break;
				case "--scenario":
					scenario = value;
					break;
				default:
					throw new IllegalArgumentException("unknown option " + name);
			}
		}
		
		if ((account == null) != (password == null))
			throw new IllegalArgumentException("--account and --password go together");
		if (existingCharacters != Config.UNCHECKED && account == null)
			throw new IllegalArgumentException("--existing-characters needs --account, a new account has none");
		
		if (sessions < 1 || parallel < 1)
			throw new IllegalArgumentException("--sessions and --parallel are at least 1");
		if (sessions > 1 && account != null)
			throw new IllegalArgumentException("--sessions makes an account for every session, so --account does not fit");
		if (!Config.SCENARIO_DEFAULT.equals(scenario) && !Config.SCENARIO_SEED_LOOP.equals(scenario))
			throw new IllegalArgumentException("--scenario is default or seed-loop, not '" + scenario + "'");
		
		return new Config(host, loginPort, worldPort, protocolRevision, timeoutSeconds, account, password,
				existingCharacters, sessions, Math.min(parallel, sessions), scenario);
	}
	
	private static int number(String name, String value)
	{
		try
		{
			return Integer.parseInt(value);
		}
		catch (NumberFormatException e)
		{
			throw new IllegalArgumentException(name + " needs a number, not '" + value + "'");
		}
	}

	private static String letters(Random random, int count)
	{
		StringBuilder text = new StringBuilder(count);
		for (int i = 0; i < count; i++)
			text.append((char)('a' + random.nextInt(26)));
		return text.toString();
	}
}
