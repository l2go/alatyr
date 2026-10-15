/*
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 */
package com.l2jfree.util.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.filter.Filter;
import ch.qos.logback.core.spi.FilterReply;

/** Routes warning and error events to the dedicated error stream. */
public final class ErrorEventFilter extends Filter<ILoggingEvent>
{
	@Override
	public FilterReply decide(ILoggingEvent event)
	{
		return event.getLevel().toInt() > ch.qos.logback.classic.Level.INFO_INT
				|| event.getThrowableProxy() != null ? FilterReply.ACCEPT : FilterReply.DENY;
	}
}
