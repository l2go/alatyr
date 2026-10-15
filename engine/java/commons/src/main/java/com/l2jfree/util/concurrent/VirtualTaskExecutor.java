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
package com.l2jfree.util.concurrent;

import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;

/**
 * Runs every task on its own virtual thread, for blocking work: database, files, admin commands. It counts the tasks
 * for the thread pool statistics. Like {@link L2RejectedExecutionHandler}, it drops tasks once it is shut down.
 */
public final class VirtualTaskExecutor extends AbstractExecutorService
{
	private final ExecutorService _executor;
	private final AtomicInteger _active = new AtomicInteger();
	private final AtomicInteger _largestActive = new AtomicInteger();
	private final LongAdder _completed = new LongAdder();
	
	/** Threads are named {@code name-0}, {@code name-1} and so on. */
	public VirtualTaskExecutor(String name)
	{
		_executor = Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name(name + "-", 0).factory());
	}
	
	@Override
	public void execute(Runnable task)
	{
		try
		{
			_executor.execute(() -> {
				_largestActive.accumulateAndGet(_active.incrementAndGet(), Math::max);
				try
				{
					task.run();
				}
				finally
				{
					_active.decrementAndGet();
					_completed.increment();
				}
			});
		}
		catch (RejectedExecutionException e)
		{
			if (!isShutdown())
				throw e;
		}
	}
	
	public int getActiveCount()
	{
		return _active.get();
	}
	
	public int getLargestActiveCount()
	{
		return _largestActive.get();
	}
	
	public long getCompletedTaskCount()
	{
		return _completed.sum();
	}
	
	@Override
	public void shutdown()
	{
		_executor.shutdown();
	}
	
	@Override
	public List<Runnable> shutdownNow()
	{
		return _executor.shutdownNow();
	}
	
	@Override
	public boolean isShutdown()
	{
		return _executor.isShutdown();
	}
	
	@Override
	public boolean isTerminated()
	{
		return _executor.isTerminated();
	}
	
	@Override
	public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException
	{
		return _executor.awaitTermination(timeout, unit);
	}
}
