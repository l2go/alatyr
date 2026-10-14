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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class SmokeClientTest
{
	@Test
	void withoutOptionsTheClientTargetsTheLocalPlatform()
	{
		SmokeClient.Config config = SmokeClient.parse(new String[0]);
		
		assertThat(config.host()).isEqualTo("127.0.0.1");
		assertThat(config.loginPort()).isEqualTo(2106);
		assertThat(config.worldPort()).isEqualTo(7777);
		assertThat(config.account()).isNull();
	}
	
	@Test
	void everyOptionIsRead()
	{
		SmokeClient.Config config = SmokeClient.parse(new String[] { "--host", "example.org", "--login-port", "2107",
				"--world-port", "7778", "--protocol-revision", "83", "--timeout-seconds", "30", "--account", "anna",
				"--password", "secret", "--existing-characters", "1" });
		
		assertThat(config).isEqualTo(new SmokeClient.Config("example.org", 2107, 7778, 83, 30, "anna", "secret", 1, 1, 1,
				"default"));
	}
	
	@Test
	void manySessionsAreReadAndTheParallelismNeverExceedsThem()
	{
		SmokeClient.Config config = SmokeClient.parse(new String[] { "--sessions", "30", "--parallel", "8" });
		
		assertThat(config.sessions()).isEqualTo(30);
		assertThat(config.parallel()).isEqualTo(8);
		assertThat(SmokeClient.parse(new String[] { "--sessions", "3", "--parallel", "8" }).parallel()).isEqualTo(3);
	}
	
	@Test
	void manySessionsAgainstNothingAllFailAndAreCounted()
	{
		SmokeClient.Config config = SmokeClient.parse(new String[] { "--login-port", "1", "--world-port", "1",
				"--timeout-seconds", "5", "--sessions", "4", "--parallel", "2" });
		java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
		
		boolean passed = SmokeClient.runMany(config, new java.io.PrintStream(bytes));
		
		assertThat(passed).isFalse();
		assertThat(bytes.toString()).contains("0 of 4 sessions passed").contains("FAILED 4 x");
	}
	
	@Test
	void badInputIsRefusedWithAMessage()
	{
		assertThatThrownBy(() -> SmokeClient.parse(new String[] { "--nope", "1" })).hasMessageContaining("unknown option");
		assertThatThrownBy(() -> SmokeClient.parse(new String[] { "--login-port" })).hasMessageContaining("missing value");
		assertThatThrownBy(() -> SmokeClient.parse(new String[] { "--login-port", "x" })).hasMessageContaining("number");
		assertThatThrownBy(() -> SmokeClient.parse(new String[] { "--account", "anna" })).hasMessageContaining("together");
		assertThatThrownBy(() -> SmokeClient.parse(new String[] { "--sessions", "0" })).hasMessageContaining("at least 1");
		assertThatThrownBy(() -> SmokeClient.parse(new String[] { "--sessions", "2", "--account", "anna", "--password", "x" }))
				.hasMessageContaining("--account does not fit");
		assertThatThrownBy(() -> SmokeClient.parse(new String[] { "--existing-characters", "1" }))
				.hasMessageContaining("needs --account");
		assertThatThrownBy(() -> SmokeClient.parse(new String[] { "--scenario", "other" }))
				.hasMessageContaining("--scenario");
	}

	@Test
	void theScenarioDefaultsToTheShortSessionAndTheSeedLoopIsNamed()
	{
		assertThat(SmokeClient.parse(new String[0]).scenario()).isEqualTo("default");
		assertThat(SmokeClient.parse(new String[] { "--scenario", "seed-loop" }).scenario()).isEqualTo("seed-loop");
	}
	
	@Test
	void aRunAgainstNothingFailsWithAReasonAndDoesNotThrow()
	{
		SmokeClient.Config config = new SmokeClient.Config("127.0.0.1", 1, 1, 87, 5, null, null,
				SmokeClient.Config.UNCHECKED, 1, 1, "default");
		Reporter reporter = new Reporter(new java.io.PrintStream(java.io.OutputStream.nullOutputStream()));
		
		SmokeClient.Result result = SmokeClient.run(config, reporter);
		
		assertThat(result.passed()).isFalse();
		assertThat(result.failure()).isNotBlank();
		assertThat(result.lines()).anyMatch(line -> line.startsWith("FAIL"));
	}
}
