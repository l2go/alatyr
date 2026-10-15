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
package com.l2jfree.status;

import java.lang.management.ManagementFactory;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Whether a server accepts players yet, and how long it took to get there from the JVM start.
 */
public final class Readiness
{
	private static final Logger _log = LoggerFactory.getLogger(Readiness.class);
	
	private final String _server;
	private final long _jvmStartMillis;
	private volatile long _startupMillis = -1;
	
	public Readiness(String server, long jvmStartMillis)
	{
		_server = server;
		_jvmStartMillis = jvmStartMillis;
	}
	
	/** Readiness of this JVM, measured from its start time. */
	public static Readiness ofThisProcess(String server)
	{
		return new Readiness(server, ManagementFactory.getRuntimeMXBean().getStartTime());
	}
	
	/** Marks the server ready now and logs the startup time. */
	public long markReady()
	{
		final long startupMillis = markReady(System.currentTimeMillis());
		_log.info(String.format(Locale.ROOT, "Ready to accept players %.3f s after JVM start.", startupMillis / 1000.0));
		return startupMillis;
	}
	
	/** Marks the server ready at the given time; the first call wins. Returns the startup time in milliseconds. */
	public synchronized long markReady(long nowMillis)
	{
		if (_startupMillis < 0)
			_startupMillis = Math.max(0, nowMillis - _jvmStartMillis);
		
		return _startupMillis;
	}
	
	public boolean isReady()
	{
		return _startupMillis >= 0;
	}
	
	/** Milliseconds from the JVM start until the server was ready, or -1 while it is not ready. */
	public long startupMillis()
	{
		return _startupMillis;
	}
	
	public String server()
	{
		return _server;
	}
}
