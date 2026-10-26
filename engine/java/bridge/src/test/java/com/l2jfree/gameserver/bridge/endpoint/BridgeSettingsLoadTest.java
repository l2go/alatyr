/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.l2jfree.gameserver.bridge.endpoint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BridgeSettingsLoadTest
{
	@TempDir Path directory;

	@Test
	void operatorSettingsEnableTheShippedDisabledEndpoint() throws Exception
	{
		Path defaults = Files.createDirectory(directory.resolve("defaults"));
		Path operator = Files.createDirectory(directory.resolve("operator"));
		Files.writeString(defaults.resolve("bridge.properties"), "bridge.enabled=false\nbridge.secret=\nbridge.port=19016\nbridge.grace=12000\n");
		Files.writeString(operator.resolve("bridge.properties"), "bridge.enabled=true\nbridge.secret=" + "44".repeat(32) + "\n");
		String oldDefaults = System.getProperty("l2jfree.config.defaults");
		String oldOperator = System.getProperty("l2jfree.config.operator");
		try
		{
			System.setProperty("l2jfree.config.defaults", defaults.toString());
			System.setProperty("l2jfree.config.operator", operator.toString());
			BridgeSettings settings = BridgeSettings.load(Path.of("config"));
			assertThat(settings.enabled()).isTrue();
			assertThat(settings.port()).isEqualTo(19016);
			assertThat(settings.graceMs()).isEqualTo(12000);
			assertThat(settings.secret()).hasSize(32).containsOnly((byte)0x44);
			Files.writeString(operator.resolve("bridge.properties"), "bridge.enabled=true\nbridge.secret=\n");
			assertThatThrownBy(() -> BridgeSettings.load(Path.of("config"))).isInstanceOf(IllegalStateException.class);
		}
		finally
		{
			restore("l2jfree.config.defaults", oldDefaults);
			restore("l2jfree.config.operator", oldOperator);
		}
	}

	private static void restore(String name, String value)
	{
		if (value == null) System.clearProperty(name);
		else System.setProperty(name, value);
	}
}
