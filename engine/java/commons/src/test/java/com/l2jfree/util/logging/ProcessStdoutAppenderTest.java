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

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.classic.util.LogbackMDCAdapter;

class ProcessStdoutAppenderTest
{
	/**
	 * The servers redirect System.out into the logger. A console appender that wrote to System.out would feed every
	 * line back into the logger (duplicated lines) and take the PrintStream lock while holding its own, which
	 * deadlocked against threads printing to System.out.
	 */
	@Test
	void writesToTheProcessStreamNotToTheCurrentSystemOut()
	{
		ByteArrayOutputStream target = new ByteArrayOutputStream();
		ByteArrayOutputStream redirected = new ByteArrayOutputStream();
		PrintStream original = System.out;
		LoggerContext context = new LoggerContext();
		context.setMDCAdapter(new LogbackMDCAdapter());
		ProcessStdoutAppender<ILoggingEvent> appender = new ProcessStdoutAppender<ILoggingEvent>(target);
		try
		{
			System.setOut(new PrintStream(redirected, true, StandardCharsets.UTF_8));
			
			PatternLayoutEncoder encoder = new PatternLayoutEncoder();
			encoder.setContext(context);
			encoder.setPattern("%level %msg%n");
			encoder.start();
			appender.setContext(context);
			appender.setEncoder(encoder);
			appender.start();
			
			Logger logger = context.getLogger("test");
			appender.doAppend(new LoggingEvent(Logger.FQCN, logger, Level.INFO, "hello", null, null));
		}
		finally
		{
			appender.stop();
			System.setOut(original);
		}
		
		assertThat(target.toString(StandardCharsets.UTF_8)).isEqualTo("INFO hello" + System.lineSeparator());
		assertThat(redirected.size()).isZero();
	}
	
	@Test
	void serverConfigurationsDoNotUseConsoleAppender() throws Exception
	{
		for (Path configuration : List.of(Path.of("src/main/resources/logback.xml"),
				Path.of("../l2jfree-core/config/logback.xml"), Path.of("../l2jfree-login/config/logback.xml")))
			assertThat(Files.readString(configuration)).as(configuration.toString()).doesNotContain("ConsoleAppender")
					.contains("com.l2jfree.util.logging.ProcessStdoutAppender");
	}
	
	@Test
	void stoppingDoesNotCloseTheProcessStream()
	{
		ProcessStdoutAppender<ILoggingEvent> appender = new ProcessStdoutAppender<ILoggingEvent>();
		appender.setContext(new LoggerContext());
		PatternLayoutEncoder encoder = new PatternLayoutEncoder();
		encoder.setContext(appender.getContext());
		encoder.setPattern("%msg%n");
		encoder.start();
		appender.setEncoder(encoder);
		appender.start();
		appender.stop();
		
		// Closing a FileOutputStream on FileDescriptor.out would invalidate the descriptor.
		assertThat(java.io.FileDescriptor.out.valid()).isTrue();
	}
}
