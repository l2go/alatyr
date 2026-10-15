/*
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 */
package com.l2jfree.util.logging;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.logging.Level;

import ch.qos.logback.classic.pattern.ThrowableProxyConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;

import com.l2jfree.L2AutoInitialization;

/** Preserves the legacy configurable synthetic stack trace diagnostics. */
public final class LegacyThrowableConverter extends ThrowableProxyConverter
{
	@Override
	public String convert(ILoggingEvent event)
	{
		if (event.getThrowableProxy() != null)
			return super.convert(event);

		String message = event.getFormattedMessage();
		Level threshold = L2AutoInitialization.EXTENDED_LOG_LEVEL;
		if (threshold != Level.OFF && toJulLevel(event.getLevel().toInt()) >= threshold.intValue()
				|| message != null && message.contains("Unevenly distributed hash code - Degraded Preformance"))
		{
			StringWriter output = new StringWriter();
			new Exception("This is just an extended feature of logging to show the stacktrace! It's not a real exception to report!")
					.printStackTrace(new PrintWriter(output));
			return System.lineSeparator() + output;
		}
		return "";
	}

	private static int toJulLevel(int logbackLevel)
	{
		if (logbackLevel >= ch.qos.logback.classic.Level.ERROR_INT)
			return Level.SEVERE.intValue();
		if (logbackLevel >= ch.qos.logback.classic.Level.WARN_INT)
			return Level.WARNING.intValue();
		if (logbackLevel >= ch.qos.logback.classic.Level.INFO_INT)
			return Level.INFO.intValue();
		if (logbackLevel >= ch.qos.logback.classic.Level.DEBUG_INT)
			return Level.FINE.intValue();
		return Level.FINEST.intValue();
	}
}
