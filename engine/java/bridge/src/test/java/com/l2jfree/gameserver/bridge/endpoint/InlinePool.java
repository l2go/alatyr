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
package com.l2jfree.gameserver.bridge.endpoint;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

import org.mockito.MockedStatic;

import com.l2jfree.gameserver.ThreadPoolManager;

/**
 * Runs the real client queue without starting the process-wide pool. That pool reads the server configuration, which
 * these listener tests do not set up.
 */
final class InlinePool implements AutoCloseable
{
	private final MockedStatic<ThreadPoolManager> pools;
	private final java.util.List<Thread> workers = new java.util.concurrent.CopyOnWriteArrayList<>();
	private final java.util.concurrent.atomic.AtomicReference<Throwable> failure = new java.util.concurrent.atomic.AtomicReference<>();
	final ThreadPoolManager pool;
	
	private InlinePool()
	{
		pools = mockStatic(ThreadPoolManager.class);
		pool = mock(ThreadPoolManager.class);
		pools.when(ThreadPoolManager::getInstance).thenReturn(pool);
		doAnswer(invocation ->
		{
			Runnable task = invocation.getArgument(0);
			Thread thread = new Thread(() ->
			{
				// FIFO release may reschedule on the worker, not the submitting test thread.
				// Mockito static mocks are thread-local; that path must not start the real server pool.
				try (var workerPools = mockStatic(ThreadPoolManager.class))
				{
					workerPools.when(ThreadPoolManager::getInstance).thenReturn(pool);
					task.run();
				}
				catch (Throwable problem) { failure.compareAndSet(null, problem); }
			}, "bridge-test-queue");
			thread.setDaemon(true);
			workers.add(thread);
			thread.start();
			return null;
		}).when(pool).execute(any(Runnable.class));
	}
	
	static InlinePool open()
	{
		return new InlinePool();
	}
	
	@Override
	public void close()
	{
		try
		{
			// A joined worker cannot enqueue another worker after this walk passes it.
			for (int i = 0; i < workers.size(); i++)
			{
				Thread worker = workers.get(i);
				try { worker.join(5000); }
				catch (InterruptedException problem) { Thread.currentThread().interrupt(); throw new AssertionError(problem); }
				if (worker.isAlive()) throw new AssertionError("bridge test worker did not stop");
			}
			if (failure.get() != null) throw new AssertionError("bridge test worker failed", failure.get());
		}
		finally { pools.close(); }
	}
}
