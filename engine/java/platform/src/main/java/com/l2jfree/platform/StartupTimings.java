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
package com.l2jfree.platform;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.LongSupplier;

/**
 * The time the start takes, by step, for the log line that the start-up measurement reads. The first step covers the
 * time between the start of the JVM and the first line of {@code main}, which is where class loading and the cache of
 * the classes show.
 */
final class StartupTimings
{
	private final LongSupplier _nanos;
	private final Map<String, Long> _stepsMillis = new LinkedHashMap<>();
	private final long _jvmMillisBeforeMain;
	private long _lastNanos;
	
	/**
	 * @param jvmMillisBeforeMain time from the start of the JVM until now
	 * @param nanos the clock in nanoseconds
	 */
	StartupTimings(long jvmMillisBeforeMain, LongSupplier nanos)
	{
		_jvmMillisBeforeMain = jvmMillisBeforeMain;
		_nanos = nanos;
		_lastNanos = nanos.getAsLong();
	}
	
	/** Ends the step that began with the previous call, or with the creation. */
	void done(String step)
	{
		final long now = _nanos.getAsLong();
		_stepsMillis.put(step, (now - _lastNanos) / 1_000_000);
		_lastNanos = now;
	}
	
	/** @return the time from the start of the JVM to the last step */
	long totalMillis()
	{
		return _jvmMillisBeforeMain + _stepsMillis.values().stream().mapToLong(Long::longValue).sum();
	}
	
	/** @return for example {@code 14231 ms (before main 412, login prepared 1203, world started 11800)} */
	String summary()
	{
		final StringBuilder text = new StringBuilder().append(totalMillis()).append(" ms (before main ")
				.append(_jvmMillisBeforeMain);
		_stepsMillis.forEach((step, millis) -> text.append(", ").append(step).append(' ').append(millis));
		return text.append(')').toString();
	}
}
