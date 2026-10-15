/*
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 */
package com.l2jfree.util.logging;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.filter.Filter;
import ch.qos.logback.core.spi.FilterReply;

/** Preserves the legacy exclusions for routine and low-value item events. */
public final class ItemEventFilter extends Filter<ILoggingEvent>
{
	private static final Set<String> EXCLUDED_TYPES = Set.of("SHOT", "ARROW", "BOLT", "HERB");

	@Override
	public FilterReply decide(ILoggingEvent event)
	{
		Object[] arguments = event.getArgumentArray();
		if (arguments == null || arguments.length == 0 || !(arguments[0] instanceof List<?> parameters)
				|| parameters.size() < 2 || !(parameters.get(0) instanceof String process))
			return FilterReply.NEUTRAL;

		int separator = process.indexOf(':');
		if (separator >= 0 && "Consume".equals(process.substring(separator + 1)))
			return FilterReply.DENY;

		Object item = parameters.get(1);
		if (item == null)
			return FilterReply.NEUTRAL;
		try
		{
			Method getItemType = item.getClass().getMethod("getItemType");
			Object itemType = getItemType.invoke(item);
			if (itemType instanceof Enum<?> type && EXCLUDED_TYPES.contains(type.name()))
				return FilterReply.DENY;
		}
		catch (ReflectiveOperationException | SecurityException ignored)
		{
			// Preserve the event if a custom item implementation has no public type accessor.
		}
		return FilterReply.NEUTRAL;
	}
}
