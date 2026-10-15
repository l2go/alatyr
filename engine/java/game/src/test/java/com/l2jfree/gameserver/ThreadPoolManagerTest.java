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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.l2jfree.Config;

/** Blocking work runs on virtual threads; world tasks stay on the platform pools (plan 10.3). */
class ThreadPoolManagerTest
{
	@BeforeAll
	static void configure()
	{
		Config.THREAD_POOL_SIZE = 4;
	}
	
	@Test
	void longRunningTasksRunOnVirtualThreads() throws Exception
	{
		CompletableFuture<Thread> executed = new CompletableFuture<>();
		ThreadPoolManager.getInstance().executeLongRunning(() -> executed.complete(Thread.currentThread()));
		assertThat(executed.get(5, TimeUnit.SECONDS).isVirtual()).isTrue();
		
		CompletableFuture<Thread> submitted = new CompletableFuture<>();
		ThreadPoolManager.getInstance().submitLongRunning(() -> submitted.complete(Thread.currentThread()))
				.get(5, TimeUnit.SECONDS);
		assertThat(submitted.get().isVirtual()).isTrue();
	}
	
	@Test
	void worldTasksStayOnPlatformThreads() throws Exception
	{
		CompletableFuture<Thread> instant = new CompletableFuture<>();
		ThreadPoolManager.getInstance().execute(() -> instant.complete(Thread.currentThread()));
		assertThat(instant.get(5, TimeUnit.SECONDS).isVirtual()).isFalse();
		
		CompletableFuture<Thread> scheduled = new CompletableFuture<>();
		ThreadPoolManager.getInstance().schedule(() -> scheduled.complete(Thread.currentThread()), 1);
		assertThat(scheduled.get(5, TimeUnit.SECONDS).isVirtual()).isFalse();
	}
	
	@Test
	void statsDescribeTheLongRunningTasks() throws Exception
	{
		ThreadPoolManager.getInstance().submitLongRunning(() -> {}).get(5, TimeUnit.SECONDS);
		assertThat(ThreadPoolManager.getInstance().getStats()).contains("Long running tasks (virtual threads):")
				.anyMatch(line -> line.startsWith("\tgetCompletedTaskCount: ") && !line.endsWith(" 0"));
	}
}
