/*
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 */
package com.l2jfree.util.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.pattern.ClassicConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;

/** Adds the legacy source method only for warnings and events with exceptions. */
public final class LegacyCallerConverter extends ClassicConverter
{
	@Override
	public String convert(ILoggingEvent event)
	{
		if (event.getLoggerName().startsWith("system."))
			return "";
		if (event.getLevel().toInt() <= Level.INFO_INT && event.getThrowableProxy() == null)
			return "";
		StackTraceElement[] callerData = event.getCallerData();
		if (callerData == null || callerData.length == 0)
			return "";
		StackTraceElement caller = callerData[0];
		return caller.getClassName() + "." + caller.getMethodName() + "(): ";
	}
}
