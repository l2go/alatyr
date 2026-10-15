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
package com.l2jfree.util.logging;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LogbackOperatorConfigurationTest
{
	@Test
	@DisplayName("operator Logback files match the packaged configuration")
	void operatorFilesMatchThePackagedConfiguration() throws Exception
	{
		byte[] packaged;
		try (InputStream input = getClass().getResourceAsStream("/logback.xml"))
		{
			assertThat(input).as("packaged Logback configuration").isNotNull();
			packaged = input.readAllBytes();
		}
		assertThat(new String(packaged)).contains("ch.qos.logback.classic.jul.LevelChangePropagator");
		
		assertThat(Files.readAllBytes(Path.of("..", "l2jfree-core", "config", "logback.xml"))).isEqualTo(packaged);
		assertThat(Files.readAllBytes(Path.of("..", "l2jfree-login", "config", "logback.xml"))).isEqualTo(packaged);
	}
}
