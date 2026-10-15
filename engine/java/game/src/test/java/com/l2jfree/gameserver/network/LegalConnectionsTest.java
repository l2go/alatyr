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
package com.l2jfree.gameserver.network;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;

class LegalConnectionsTest
{
	private static final int THREADS = 8;
	private static final int PER_THREAD = 5000;
	
	@Test
	void anAnnouncementAllowsOneConnectionAndNoMore()
	{
		LegalConnections legal = new LegalConnections();
		
		assertThat(legal.consume("198.51.100.7")).isFalse();
		legal.legalize("198.51.100.7");
		legal.legalize("198.51.100.7");
		
		assertThat(legal.consume("198.51.100.7")).isTrue();
		assertThat(legal.consume("198.51.100.7")).isTrue();
		assertThat(legal.consume("198.51.100.7")).isFalse();
		assertThat(legal.pending("198.51.100.7")).isZero();
	}
	
	@Test
	void announcementsMadeAtTheSameTimeAreAllCounted() throws Exception
	{
		LegalConnections legal = new LegalConnections();
		
		runTogether(() -> {
			for (int i = 0; i < PER_THREAD; i++)
				legal.legalize("203.0.113.1");
			return null;
		});
		
		assertThat(legal.pending("203.0.113.1")).isEqualTo(THREADS * PER_THREAD);
	}
	
	@Test
	void connectionsMadeAtTheSameTimeEachTakeOneAnnouncement() throws Exception
	{
		LegalConnections legal = new LegalConnections();
		for (int i = 0; i < THREADS * PER_THREAD; i++)
			legal.legalize("203.0.113.2");
		
		List<Integer> accepted = runTogether(() -> {
			int count = 0;
			for (int i = 0; i < PER_THREAD * 2; i++)
				if (legal.consume("203.0.113.2"))
					count++;
			return count;
		});
		
		assertThat(accepted.stream().mapToInt(Integer::intValue).sum()).isEqualTo(THREADS * PER_THREAD);
		assertThat(legal.pending("203.0.113.2")).isZero();
	}
	
	private static <T> List<T> runTogether(Callable<T> task) throws Exception
	{
		ExecutorService executor = Executors.newFixedThreadPool(THREADS);
		try
		{
			CountDownLatch start = new CountDownLatch(1);
			List<Future<T>> futures = new ArrayList<>();
			for (int i = 0; i < THREADS; i++)
			{
				futures.add(executor.submit(() -> {
					start.await();
					return task.call();
				}));
			}
			start.countDown();
			List<T> results = new ArrayList<>();
			for (Future<T> future : futures)
				results.add(future.get());
			return results;
		}
		finally
		{
			executor.shutdownNow();
		}
	}
}
