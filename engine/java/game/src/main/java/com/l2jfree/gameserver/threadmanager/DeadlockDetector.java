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
package com.l2jfree.gameserver.threadmanager;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.l2jfree.Config;
import com.l2jfree.gameserver.Shutdown;
import com.l2jfree.gameserver.Shutdown.ShutdownMode;
import com.l2jfree.gameserver.util.Util;
import com.l2jfree.lang.L2Thread;

public final class DeadlockDetector extends L2Thread
{
	private static final Logger _log = LoggerFactory.getLogger(DeadlockDetector.class);
	
	private static final class SingletonHolder
	{
		private static final DeadlockDetector INSTANCE = new DeadlockDetector();
	}
	
	public static DeadlockDetector getInstance()
	{
		return SingletonHolder.INSTANCE;
	}
	
	private final Set<Long> _logged = new LinkedHashSet<Long>();
	
	private DeadlockDetector()
	{
		super("DeadlockDetector");
		setPriority(Thread.MAX_PRIORITY);
		setDaemon(true);
		
		start();
		
		_log.info("DeadlockDetector: Initialized.");
	}
	
	@Override
	protected void runTurn()
	{
		long[] ids = findDeadlockedThreadIds();
		if (ids == null)
			return;
		
		List<Thread> deadlocked = new ArrayList<Thread>();
		
		for (long id : ids)
			if (_logged.add(id))
			{
				Thread thread = findThreadById(id);
				if (thread != null)
					deadlocked.add(thread);
				else
					_log.warn("Deadlocked thread terminated before diagnostic capture: " + id);
			}
		
		if (!deadlocked.isEmpty())
		{
			Util.printSection("Deadlocked Thread(s)");
			for (Thread thread : deadlocked)
			{
				_log.error("");
				
				for (String line : L2Thread.getStats(thread))
					_log.error(line);
			}
			
			new Halt().start();
			
			Shutdown.start("DeadlockDetector", 1, ShutdownMode.RESTART);
		}
	}
	
	@Override
	protected void sleepTurn() throws InterruptedException
	{
		Thread.sleep(Config.DEADLOCKCHECK_INTERVAL);
	}
	
	private long[] findDeadlockedThreadIds()
	{
		if (ManagementFactory.getThreadMXBean().isSynchronizerUsageSupported())
			return ManagementFactory.getThreadMXBean().findDeadlockedThreads();
		else
			return ManagementFactory.getThreadMXBean().findMonitorDeadlockedThreads();
	}
	
	private Thread findThreadById(long id)
	{
		for (Thread thread : Thread.getAllStackTraces().keySet())
			if (thread.threadId() == id)
				return thread;
		return null;
	}
	
	private static final class Halt extends Thread
	{
		@Override
		public void run()
		{
			try
			{
				sleep(40000);
			}
			catch (InterruptedException e)
			{
				e.printStackTrace();
			}
			finally
			{
				Shutdown.halt("DeadlockDetector");
			}
		}
	}
}
