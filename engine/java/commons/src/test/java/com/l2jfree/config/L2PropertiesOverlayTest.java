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
package com.l2jfree.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The configuration overlay: defaults directory, operator directory, and a checkout that starts unchanged.
 */
class L2PropertiesOverlayTest
{
	@TempDir
	Path root;
	
	private Path checkout;
	private Path defaults;
	private Path operator;
	
	@BeforeEach
	void setUp() throws IOException
	{
		System.clearProperty(ConfigOverlay.DEFAULTS_PROPERTY);
		System.clearProperty(ConfigOverlay.OPERATOR_PROPERTY);
		checkout = Files.createDirectory(root.resolve("checkout"));
		defaults = Files.createDirectory(root.resolve("defaults"));
		operator = Files.createDirectory(root.resolve("operator"));
	}
	
	@AfterEach
	void tearDown()
	{
		System.clearProperty(ConfigOverlay.DEFAULTS_PROPERTY);
		System.clearProperty(ConfigOverlay.OPERATOR_PROPERTY);
	}
	
	private static Path write(Path directory, String name, String content) throws IOException
	{
		return Files.writeString(directory.resolve(name), content);
	}
	
	@Test
	@DisplayName("without system properties the file is read as given")
	void checkoutStartsUnchanged() throws IOException
	{
		final Path file = write(checkout, "server.properties", "Port=7777\nName=checkout\n");
		
		final L2Properties properties = new L2Properties(file.toString());
		
		assertThat(properties.getProperty("Port")).isEqualTo("7777");
		assertThat(properties.getProperty("Name")).isEqualTo("checkout");
	}
	
	@Test
	@DisplayName("a missing file still fails with FileNotFoundException")
	void missingFileFails()
	{
		final String missing = checkout.resolve("absent.properties").toString();
		
		assertThatThrownBy(() -> new L2Properties(missing)).isInstanceOf(FileNotFoundException.class);
		assertThatThrownBy(() -> new L2Properties(new File(missing))).isInstanceOf(FileNotFoundException.class);
	}
	
	@Test
	@DisplayName("the defaults directory replaces the directory of the path as given")
	void defaultsDirectoryIsUsed() throws IOException
	{
		write(checkout, "server.properties", "Name=checkout\n");
		write(defaults, "server.properties", "Name=defaults\n");
		System.setProperty(ConfigOverlay.DEFAULTS_PROPERTY, defaults.toString());
		
		final L2Properties properties = new L2Properties(new File(checkout.toFile(), "server.properties"));
		
		assertThat(properties.getProperty("Name")).isEqualTo("defaults");
	}
	
	@Test
	@DisplayName("a file missing in the defaults directory falls back to the path as given")
	void missingDefaultFallsBackToThePathAsGiven() throws IOException
	{
		write(checkout, "extra.properties", "Name=checkout\n");
		System.setProperty(ConfigOverlay.DEFAULTS_PROPERTY, defaults.toString());
		
		final L2Properties properties = new L2Properties(checkout.resolve("extra.properties").toString());
		
		assertThat(properties.getProperty("Name")).isEqualTo("checkout");
	}
	
	@Test
	@DisplayName("a file missing in both places fails with FileNotFoundException")
	void missingEverywhereFails()
	{
		System.setProperty(ConfigOverlay.DEFAULTS_PROPERTY, defaults.toString());
		final String missing = checkout.resolve("absent.properties").toString();
		
		assertThatThrownBy(() -> new L2Properties(missing)).isInstanceOf(FileNotFoundException.class);
	}
	
	@Test
	@DisplayName("an operator key replaces the default and every other key keeps its default")
	void operatorOverridesOnlyItsKeys() throws IOException
	{
		write(defaults, "rates.properties", "XpRate=1\nSpRate=1\n");
		write(operator, "rates.properties", "XpRate=5\n");
		System.setProperty(ConfigOverlay.DEFAULTS_PROPERTY, defaults.toString());
		System.setProperty(ConfigOverlay.OPERATOR_PROPERTY, operator.toString());
		
		final L2Properties properties = new L2Properties("./config/rates.properties");
		
		assertThat(properties.getInteger("XpRate")).isEqualTo(5);
		assertThat(properties.getInteger("SpRate")).isEqualTo(1);
	}
	
	@Test
	@DisplayName("the operator directory overlays a checkout file when no defaults directory is set")
	void operatorOverlaysTheCheckout() throws IOException
	{
		final Path file = write(checkout, "rates.properties", "XpRate=1\nSpRate=1\n");
		write(operator, "rates.properties", "SpRate=3\n");
		System.setProperty(ConfigOverlay.OPERATOR_PROPERTY, operator.toString());
		
		final L2Properties properties = new L2Properties(file.toFile());
		
		assertThat(properties.getInteger("XpRate")).isEqualTo(1);
		assertThat(properties.getInteger("SpRate")).isEqualTo(3);
	}
	
	@Test
	@DisplayName("an operator directory without a file of that name changes nothing")
	void operatorWithoutTheFileChangesNothing() throws IOException
	{
		write(defaults, "rates.properties", "XpRate=1\n");
		write(operator, "other.properties", "XpRate=9\n");
		System.setProperty(ConfigOverlay.DEFAULTS_PROPERTY, defaults.toString());
		System.setProperty(ConfigOverlay.OPERATOR_PROPERTY, operator.toString());
		
		assertThat(new L2Properties("./config/rates.properties").getInteger("XpRate")).isEqualTo(1);
	}
	
	@Test
	@DisplayName("an operator file without defaults file still fails")
	void operatorAloneIsNotEnough() throws IOException
	{
		write(operator, "rates.properties", "XpRate=9\n");
		System.setProperty(ConfigOverlay.DEFAULTS_PROPERTY, defaults.toString());
		System.setProperty(ConfigOverlay.OPERATOR_PROPERTY, operator.toString());
		
		assertThatThrownBy(() -> new L2Properties(checkout.resolve("rates.properties").toString()))
				.isInstanceOf(FileNotFoundException.class);
	}
	
	@Test
	@DisplayName("an operator key that the defaults do not contain is reported")
	void unknownOperatorKeysAreReported() throws IOException
	{
		write(defaults, "rates.properties", "XpRate=1\n");
		write(operator, "rates.properties", "XpRate=2\nXpRaet=3\n");
		System.setProperty(ConfigOverlay.DEFAULTS_PROPERTY, defaults.toString());
		System.setProperty(ConfigOverlay.OPERATOR_PROPERTY, operator.toString());
		
		final Properties target = new Properties();
		
		assertThat(ConfigOverlay.load(target, new File("./config/rates.properties"))).containsExactly("XpRaet");
		assertThat(target.getProperty("XpRate")).isEqualTo("2");
		assertThat(target.getProperty("XpRaet")).isEqualTo("3");
	}
	
	@Test
	@DisplayName("known operator keys are not reported")
	void knownOperatorKeysAreNotReported() throws IOException
	{
		write(defaults, "rates.properties", "XpRate=1\n");
		write(operator, "rates.properties", "XpRate=2\n");
		System.setProperty(ConfigOverlay.DEFAULTS_PROPERTY, defaults.toString());
		System.setProperty(ConfigOverlay.OPERATOR_PROPERTY, operator.toString());
		
		assertThat(ConfigOverlay.load(new Properties(), new File("rates.properties"))).isEmpty();
	}
	
	@Test
	@DisplayName("blank system properties are ignored")
	void blankSystemPropertiesAreIgnored() throws IOException
	{
		final Path file = write(checkout, "server.properties", "Name=checkout\n");
		System.setProperty(ConfigOverlay.DEFAULTS_PROPERTY, " ");
		System.setProperty(ConfigOverlay.OPERATOR_PROPERTY, "");
		
		assertThat(new L2Properties(file.toFile()).getProperty("Name")).isEqualTo("checkout");
	}
}
