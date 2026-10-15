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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.l2jfree.gameserver.threadmanager.FIFORunnableQueue;

class DisconnectionQueueTest
{
	@Test
	@DisplayName("the disconnect grace offers the save to the packet queue and does not run it itself")
	void graceDoesNotRunBesideTheQueue()
	{
		L2Client client = mock(L2Client.class);
		@SuppressWarnings("unchecked")
		FIFORunnableQueue<Runnable> queue = mock(FIFORunnableQueue.class);
		when(client.getPacketQueue()).thenReturn(queue);
		AtomicBoolean ran = new AtomicBoolean();
		Runnable work = new Runnable() {
			@Override
			public void run()
			{
				ran.set(true);
			}
		};

		Disconnection.offerToPacketQueue(client, work);

		verify(queue).execute(work);
		assertThat(ran).isFalse();
	}

	@Test
	@DisplayName("a disconnect with no client runs the save, because there is no packet queue")
	void noClientRunsTheWork()
	{
		AtomicBoolean ran = new AtomicBoolean();
		Disconnection.offerToPacketQueue(null, new Runnable() {
			@Override
			public void run()
			{
				ran.set(true);
			}
		});

		assertThat(ran).isTrue();
	}
}
