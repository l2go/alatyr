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
package com.l2jfree.network;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * The one daemon thread of the network module for periodic housekeeping: the flush of the coalesced log lines and
 * the cleanup of the flood tables. It replaces the {@code Timer} of the old transport.
 */
final class Maintenance
{
	private static final ScheduledExecutorService _scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
		Thread thread = new Thread(runnable, "network-maintenance");
		thread.setDaemon(true);
		return thread;
	});
	
	private Maintenance()
	{
	}
	
	/**
	 * Runs the task every {@code interval} milliseconds, for as long as the process lives. An exception of a run is
	 * printed and does not stop later runs.
	 */
	static void add(final Runnable runnable, long interval)
	{
		_scheduler.scheduleAtFixedRate(new Runnable() {
			@Override
			public void run()
			{
				try
				{
					runnable.run();
				}
				catch (RuntimeException e)
				{
					e.printStackTrace();
				}
			}
		}, interval, interval, TimeUnit.MILLISECONDS);
	}
}
