/*
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 */
package com.l2jfree.util.logging;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.AppenderBase;

/** Sends formatted root log events to in-game status and admin listeners. */
public final class ListeningLogAppender extends AppenderBase<ILoggingEvent>
{
	private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd MMM HH:mm:ss,SSS", Locale.ENGLISH)
			.withZone(ZoneId.systemDefault());

	@Override
	protected void append(ILoggingEvent event)
	{
		StringBuilder message = new StringBuilder().append(event.getLevel()).append(" [")
				.append(DATE_FORMAT.format(Instant.ofEpochMilli(event.getTimeStamp()))).append("] ")
				.append(event.getLoggerName()).append(": ").append(event.getFormattedMessage());
		if (event.getThrowableProxy() != null)
			message.append(System.lineSeparator()).append(ThrowableProxyUtil.asString(event.getThrowableProxy()));
		for (String line : message.toString().split("\\R", -1))
			ListeningLog.writeToListeners(line);
	}
}
