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
package com.l2jfree.gameserver;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.l2jfree.Config;
import com.l2jfree.util.concurrent.ExecuteWrapper;
import com.l2jfree.util.concurrent.L2RejectedExecutionHandler;
import com.l2jfree.util.concurrent.RunnableStatsManager;
import com.l2jfree.util.concurrent.RunnableStatsManager.SortBy;
import com.l2jfree.util.concurrent.ScheduledFutureWrapper;
import com.l2jfree.util.concurrent.VirtualTaskExecutor;

/**
 * @author -Wooden-, NB4L1
 */
public final class ThreadPoolManager
{
	private static final Logger _log = LoggerFactory.getLogger(ThreadPoolManager.class);
	
	public static final long MAXIMUM_RUNTIME_IN_MILLISEC_WITHOUT_WARNING = 5000;
	
	private static final long MAX_DELAY = TimeUnit.NANOSECONDS.toMillis(Long.MAX_VALUE - System.nanoTime()) / 2;
	
	public static ThreadPoolManager getInstance()
	{
		return SingletonHolder._instance;
	}
	
	private final ScheduledThreadPoolExecutor _scheduledPool;
	private final ThreadPoolExecutor _instantPool;
	/** Blocking work (admin commands, cleanups): one virtual thread per task. World tasks stay on the pools above. */
	private final VirtualTaskExecutor _longRunningPool;
	
	private ThreadPoolManager()
	{
		final int instantPoolSize = Math.max(1, Config.THREAD_POOL_SIZE / 3);
		
		_scheduledPool = new ScheduledThreadPoolExecutor(Config.THREAD_POOL_SIZE - instantPoolSize);
		_scheduledPool.setRejectedExecutionHandler(new L2RejectedExecutionHandler());
		_scheduledPool.prestartAllCoreThreads();
		
		_instantPool =
				new ThreadPoolExecutor(instantPoolSize, instantPoolSize, 0, TimeUnit.SECONDS,
						new ArrayBlockingQueue<Runnable>(100000));
		_instantPool.setRejectedExecutionHandler(new L2RejectedExecutionHandler());
		_instantPool.prestartAllCoreThreads();
		
		_longRunningPool = new VirtualTaskExecutor("long-running");
		
		scheduleAtFixedRate(new Runnable() {
			@Override
			public void run()
			{
				purge();
			}
		}, 60000, 60000);
		
		_log.info("ThreadPoolManager: Initialized with " + _scheduledPool.getPoolSize() + " scheduler, "
				+ _instantPool.getPoolSize() + " instant thread(s); long running tasks on virtual threads.");
	}
	
	private final long validate(long delay)
	{
		return Math.max(0, Math.min(MAX_DELAY, delay));
	}
	
	private static final class ThreadPoolExecuteWrapper extends ExecuteWrapper
	{
		private ThreadPoolExecuteWrapper(Runnable runnable)
		{
			super(runnable);
		}
		
		@Override
		protected long getMaximumRuntimeInMillisecWithoutWarning()
		{
			return MAXIMUM_RUNTIME_IN_MILLISEC_WITHOUT_WARNING;
		}
	}
	
	// ===========================================================================================
	
	public final ScheduledFuture<?> schedule(Runnable r, long delay)
	{
		r = new ThreadPoolExecuteWrapper(r);
		delay = validate(delay);
		
		return new ScheduledFutureWrapper(_scheduledPool.schedule(r, delay, TimeUnit.MILLISECONDS));
	}
	
	public final ScheduledFuture<?> scheduleEffect(Runnable r, long delay)
	{
		return schedule(r, delay);
	}
	
	public final ScheduledFuture<?> scheduleGeneral(Runnable r, long delay)
	{
		return schedule(r, delay);
	}
	
	public final ScheduledFuture<?> scheduleAi(Runnable r, long delay)
	{
		return schedule(r, delay);
	}
	
	// ===========================================================================================
	
	public final ScheduledFuture<?> scheduleAtFixedRate(Runnable r, long delay, long period)
	{
		r = new ThreadPoolExecuteWrapper(r);
		delay = validate(delay);
		period = validate(period);
		
		return new ScheduledFutureWrapper(_scheduledPool.scheduleAtFixedRate(r, delay, period, TimeUnit.MILLISECONDS));
	}
	
	public final ScheduledFuture<?> scheduleEffectAtFixedRate(Runnable r, long delay, long period)
	{
		return scheduleAtFixedRate(r, delay, period);
	}
	
	public final ScheduledFuture<?> scheduleGeneralAtFixedRate(Runnable r, long delay, long period)
	{
		return scheduleAtFixedRate(r, delay, period);
	}
	
	public final ScheduledFuture<?> scheduleAiAtFixedRate(Runnable r, long delay, long period)
	{
		return scheduleAtFixedRate(r, delay, period);
	}
	
	// ===========================================================================================
	
	public final void execute(Runnable r)
	{
		r = new ThreadPoolExecuteWrapper(r);
		
		_instantPool.execute(r);
	}
	
	public final void executeTask(Runnable r)
	{
		execute(r);
	}
	
	public final void executeLongRunning(Runnable r)
	{
		r = new ExecuteWrapper(r);
		
		_longRunningPool.execute(r);
	}
	
	// ===========================================================================================
	
	public final Future<?> submit(Runnable r)
	{
		r = new ThreadPoolExecuteWrapper(r);
		
		return _instantPool.submit(r);
	}
	
	public final Future<?> submitLongRunning(Runnable r)
	{
		r = new ExecuteWrapper(r);
		
		return _longRunningPool.submit(r);
	}
	
	// ===========================================================================================
	
	public List<String> getStats()
	{
		List<String> list = new ArrayList<String>();
		
		list.add("");
		list.add("Scheduled pool:");
		list.add("=================================================");
		list.add("\tgetActiveCount: ...... " + _scheduledPool.getActiveCount());
		list.add("\tgetCorePoolSize: ..... " + _scheduledPool.getCorePoolSize());
		list.add("\tgetPoolSize: ......... " + _scheduledPool.getPoolSize());
		list.add("\tgetLargestPoolSize: .. " + _scheduledPool.getLargestPoolSize());
		list.add("\tgetMaximumPoolSize: .. " + _scheduledPool.getMaximumPoolSize());
		list.add("\tgetCompletedTaskCount: " + _scheduledPool.getCompletedTaskCount());
		list.add("\tgetQueuedTaskCount: .. " + _scheduledPool.getQueue().size());
		list.add("\tgetTaskCount: ........ " + _scheduledPool.getTaskCount());
		list.add("");
		list.add("Instant pool:");
		list.add("=================================================");
		list.add("\tgetActiveCount: ...... " + _instantPool.getActiveCount());
		list.add("\tgetCorePoolSize: ..... " + _instantPool.getCorePoolSize());
		list.add("\tgetPoolSize: ......... " + _instantPool.getPoolSize());
		list.add("\tgetLargestPoolSize: .. " + _instantPool.getLargestPoolSize());
		list.add("\tgetMaximumPoolSize: .. " + _instantPool.getMaximumPoolSize());
		list.add("\tgetCompletedTaskCount: " + _instantPool.getCompletedTaskCount());
		list.add("\tgetQueuedTaskCount: .. " + _instantPool.getQueue().size());
		list.add("\tgetTaskCount: ........ " + _instantPool.getTaskCount());
		list.add("");
		list.add("Long running tasks (virtual threads):");
		list.add("=================================================");
		list.add("\tgetActiveCount: ...... " + _longRunningPool.getActiveCount());
		list.add("\tgetLargestActive: .... " + _longRunningPool.getLargestActiveCount());
		list.add("\tgetCompletedTaskCount: " + _longRunningPool.getCompletedTaskCount());
		list.add("");
		
		return list;
	}
	
	private boolean awaitTermination(long timeoutInMillisec) throws InterruptedException
	{
		final long begin = System.currentTimeMillis();
		
		while (System.currentTimeMillis() - begin < timeoutInMillisec)
		{
			if (!_scheduledPool.awaitTermination(10, TimeUnit.MILLISECONDS) && _scheduledPool.getActiveCount() > 0)
				continue;
			
			if (!_instantPool.awaitTermination(10, TimeUnit.MILLISECONDS) && _instantPool.getActiveCount() > 0)
				continue;
			
			if (!_longRunningPool.awaitTermination(10, TimeUnit.MILLISECONDS) && _longRunningPool.getActiveCount() > 0)
				continue;
			
			return true;
		}
		
		return false;
	}
	
	private int getTaskCount(ThreadPoolExecutor tp)
	{
		return tp.getQueue().size() + tp.getActiveCount();
	}
	
	public void shutdown()
	{
		final long begin = System.currentTimeMillis();
		
		System.out.println("ThreadPoolManager: Shutting down.");
		System.out.println("\t... executing " + getTaskCount(_scheduledPool) + " scheduled tasks.");
		System.out.println("\t... executing " + getTaskCount(_instantPool) + " instant tasks.");
		System.out.println("\t... executing " + _longRunningPool.getActiveCount() + " long running tasks.");
		
		_scheduledPool.shutdown();
		_instantPool.shutdown();
		_longRunningPool.shutdown();
		
		boolean success = false;
		try
		{
			success |= awaitTermination(5000);
			
			_scheduledPool.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
			_scheduledPool.setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
			
			success |= awaitTermination(10000);
		}
		catch (InterruptedException e)
		{
			e.printStackTrace();
		}
		
		System.out.println("\t... success: " + success + " in " + (System.currentTimeMillis() - begin) + " msec.");
		System.out.println("\t... " + getTaskCount(_scheduledPool) + " scheduled tasks left.");
		System.out.println("\t... " + getTaskCount(_instantPool) + " instant tasks left.");
		System.out.println("\t... " + _longRunningPool.getActiveCount() + " long running tasks left.");
		
		if (TimeUnit.HOURS.toMillis(12) < (System.currentTimeMillis() - GameServer.getStartedTime().getTimeInMillis()))
			RunnableStatsManager.dumpClassStats(SortBy.TOTAL);
	}
	
	public void purge()
	{
		_scheduledPool.purge();
		_instantPool.purge();
	}
	
	@SuppressWarnings("synthetic-access")
	private static class SingletonHolder
	{
		protected static final ThreadPoolManager _instance = new ThreadPoolManager();
	}
}
