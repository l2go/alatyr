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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

class VirtualTaskExecutorTest
{
	@Test
	void runsEveryTaskOnItsOwnNamedVirtualThread() throws Exception
	{
		VirtualTaskExecutor executor = new VirtualTaskExecutor("probe");
		AtomicReference<Thread> thread = new AtomicReference<>();
		executor.submit(() -> thread.set(Thread.currentThread())).get(5, TimeUnit.SECONDS);
		assertThat(thread.get().isVirtual()).isTrue();
		assertThat(thread.get().getName()).startsWith("probe-");
		executor.shutdown();
	}
	
	@Test
	void countsActiveAndCompletedTasks() throws Exception
	{
		VirtualTaskExecutor executor = new VirtualTaskExecutor("probe");
		CountDownLatch release = new CountDownLatch(1);
		CountDownLatch started = new CountDownLatch(2);
		for (int i = 0; i < 2; i++)
		{
			executor.execute(() -> {
				started.countDown();
				awaitQuietly(release);
			});
		}
		assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
		assertThat(executor.getActiveCount()).isEqualTo(2);
		assertThat(executor.getLargestActiveCount()).isEqualTo(2);
		
		release.countDown();
		executor.shutdown();
		assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
		assertThat(executor.getActiveCount()).isZero();
		assertThat(executor.getCompletedTaskCount()).isEqualTo(2);
	}
	
	@Test
	void aFailingTaskStillCountsAsCompleted() throws Exception
	{
		VirtualTaskExecutor executor = new VirtualTaskExecutor("probe");
		executor.execute(() -> {
			throw new IllegalStateException("expected by the test");
		});
		executor.shutdown();
		assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
		assertThat(executor.getActiveCount()).isZero();
		assertThat(executor.getCompletedTaskCount()).isEqualTo(1);
	}
	
	private static void awaitQuietly(CountDownLatch latch)
	{
		try
		{
			latch.await();
		}
		catch (InterruptedException e)
		{
			Thread.currentThread().interrupt();
		}
	}
}
