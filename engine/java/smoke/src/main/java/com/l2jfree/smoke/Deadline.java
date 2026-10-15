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
package com.l2jfree.smoke;

import java.util.concurrent.TimeUnit;

/**
 * The time limit of one whole run. Every blocking call of the client stays within it.
 */
final class Deadline
{
	private final int _seconds;
	private final long _endNanos;

	private Deadline(int seconds)
	{
		_seconds = seconds;
		_endNanos = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds);
	}

	static Deadline after(int seconds)
	{
		return new Deadline(seconds);
	}

	int seconds()
	{
		return _seconds;
	}

	long remainingMillis()
	{
		return Math.max(0L, TimeUnit.NANOSECONDS.toMillis(_endNanos - System.nanoTime()));
	}

	boolean expired()
	{
		return remainingMillis() <= 0L;
	}

	/**
	 * Sleeps for the given time, or for the rest of the time limit when that is shorter.
	 */
	void sleep(long millis) throws SmokeException
	{
		long time = Math.min(millis, remainingMillis());
		if (time <= 0L)
			return;

		try
		{
			Thread.sleep(time);
		}
		catch (InterruptedException e)
		{
			Thread.currentThread().interrupt();
			throw new SmokeException("interrupted", e);
		}
	}
}
