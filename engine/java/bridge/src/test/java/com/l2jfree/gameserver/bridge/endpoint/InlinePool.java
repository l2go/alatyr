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
	
	private InlinePool()
	{
		pools = mockStatic(ThreadPoolManager.class);
		ThreadPoolManager pool = mock(ThreadPoolManager.class);
		pools.when(ThreadPoolManager::getInstance).thenReturn(pool);
		doAnswer(invocation ->
		{
			Runnable task = invocation.getArgument(0);
			Thread thread = new Thread(task, "bridge-test-queue");
			thread.setDaemon(true);
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
		pools.close();
	}
}
