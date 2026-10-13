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

import java.util.function.IntConsumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.l2jfree.gameserver.persistence.WorldConfigurationException;

/**
 * Ends the process with a non-zero status when the start fails, and says why in the log.
 * <p>
 * A failed start (a port in use, another server on the database, a bad setting) leaves the pool and network threads
 * of the half-started modules running, so without an explicit exit the process hangs and a supervisor never sees the
 * failure.
 */
final class StartupGuard
{
	private static final Logger _log = LoggerFactory.getLogger(StartupGuard.class);
	
	private StartupGuard()
	{
	}
	
	/** The start of the platform. */
	@FunctionalInterface
	interface Startup
	{
		void run() throws Exception;
	}
	
	/**
	 * Runs the start; when it throws, logs the cause and passes status 1 to {@code exit}.
	 */
	static void startOrExit(Startup startup, IntConsumer exit)
	{
		try
		{
			startup.run();
		}
		catch (WorldConfigurationException e)
		{
			_log.error(e.getMessage());
			exit.accept(1);
		}
		catch (Throwable t)
		{
			_log.error("Platform: the server could not start", t);
			exit.accept(1);
		}
	}
}
