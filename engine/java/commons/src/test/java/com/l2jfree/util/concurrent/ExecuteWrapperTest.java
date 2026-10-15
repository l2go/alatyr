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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

class ExecuteWrapperTest
{
	@Test
	void anErrorDoesNotEscapeTheWrapper()
	{
		ExecuteWrapper.execute(() -> {
			throw new StackOverflowError("in a task");
		});
		ExecuteWrapper.execute(() -> {
			throw new IllegalStateException("in a task");
		});
	}
	
	/** A periodic task that throws is never run again by the executor, so the wrapper must absorb the Error. */
	@Test
	void aPeriodicTaskSurvivesAnError() throws Exception
	{
		ScheduledExecutorService pool = Executors.newSingleThreadScheduledExecutor();
		try
		{
			AtomicInteger runs = new AtomicInteger();
			ScheduledFuture<?> future = pool.scheduleAtFixedRate(new ExecuteWrapper(() -> {
				if (runs.incrementAndGet() == 1)
					throw new AssertionError("first run fails");
			}), 0, 10, TimeUnit.MILLISECONDS);
			
			long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
			while (runs.get() < 3 && System.nanoTime() < deadline)
				Thread.sleep(10);
			
			assertThat(runs.get()).isGreaterThanOrEqualTo(3);
			assertThat(future.isDone()).isFalse();
			future.cancel(false);
		}
		finally
		{
			pool.shutdownNow();
		}
	}
}
