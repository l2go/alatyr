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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;

import com.l2jfree.gameserver.network.L2Client;

/**
 * Shutdown waits for a slow save, and it does not wait past its budget.
 */
class BridgeShutdownTest
{
	@org.junit.jupiter.params.ParameterizedTest
	@org.junit.jupiter.params.provider.CsvSource({ "0,0", "200,180", "1000,900", "30000,29000" })
	void aSmallShutdownBudgetStillLeavesTimeForItsSave(long budgetMs, long saveMs)
	{
		long end = budgetMs * 1_000_000L;
		assertThat(BridgeLoopback.shutdownSaveEnd(end, end)).isEqualTo(saveMs * 1_000_000L);
	}

	@Test
	void shutdownWaitsForASlowSaveAndNotPastTheBudget() throws Exception
	{
		BridgeLoopback loop = listener();
		try
		{
			AtomicBoolean finished = new AtomicBoolean();
			L2Client slow = mock(L2Client.class);
			doAnswer(invocation ->
			{
				new Thread(() ->
				{
					try
					{
						Thread.sleep(200);
					}
					catch (InterruptedException e)
					{
						Thread.currentThread().interrupt();
					}
					finished.set(true);
				}).start();
				return null;
			}).when(slow).requestBridgeDisconnect();
			when(slow.isBridgeDisconnectFinished()).thenAnswer(invocation -> finished.get());
			loop.watch(slow);
			
			long started = System.nanoTime();
			loop.close(1_000);
			long elapsedMs = (System.nanoTime() - started) / 1_000_000L;
			assertThat(finished).isTrue();
			assertThat(elapsedMs).isBetween(150L, 1_500L);
			
			BridgeLoopback limited = listener();
			AtomicBoolean late = new AtomicBoolean();
			L2Client slower = mock(L2Client.class);
			doAnswer(invocation ->
			{
				new Thread(() ->
				{
					try
					{
						Thread.sleep(5_000);
					}
					catch (InterruptedException e)
					{
						Thread.currentThread().interrupt();
					}
					late.set(true);
				}).start();
				return null;
			}).when(slower).requestBridgeDisconnect();
			when(slower.isBridgeDisconnectFinished()).thenAnswer(invocation -> late.get());
			limited.watch(slower);
			started = System.nanoTime();
			limited.close(200);
			elapsedMs = (System.nanoTime() - started) / 1_000_000L;
			assertThat(late).isFalse();
			assertThat(elapsedMs).isLessThan(1_500L);
		}
		finally
		{
			// close is idempotent only while the thread is still the same call; the first loop is already closed
		}
	}
	
	private static BridgeLoopback listener()
	{
		StringBuilder hex = new StringBuilder();
		for (int i = 0; i < 32; i++)
			hex.append("ab");
		for (int port = 19050; port < 19070; port++)
		{
			Properties properties = new Properties();
			properties.setProperty("bridge.enabled", "true");
			properties.setProperty("bridge.port", Integer.toString(port));
			properties.setProperty("bridge.secret", hex.toString());
			try
			{
				return BridgeLoopback.open(BridgeSettings.parse(properties), null);
			}
			catch (IllegalStateException e)
			{
				// try the next port
			}
		}
		throw new IllegalStateException("no port");
	}
}
