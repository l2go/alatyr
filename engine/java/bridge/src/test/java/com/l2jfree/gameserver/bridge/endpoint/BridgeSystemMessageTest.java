/*
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */
package com.l2jfree.gameserver.bridge.endpoint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import com.l2jfree.gameserver.network.L2Client;
import com.l2jfree.gameserver.network.bridge.Attempt;
import com.l2jfree.gameserver.network.bridge.BridgeFact;
import com.l2jfree.gameserver.network.bridge.BridgeMessageArg;
import com.l2jfree.gameserver.network.bridge.EffectLedger;
import com.l2jfree.gameserver.network.bridge.FactSink;
import com.l2jfree.gameserver.network.packets.server.SystemMessage;

class BridgeSystemMessageTest
{
	@Test
	void messageElementsDescribeTheirOwnTypedFieldsInOriginalOrder()
	{
		var packet = new SystemMessage(100000);
		packet.addString("exact\0text").addNumber(-7).addNpcName(10001).addItemName(57)
				.addSkillName(12, 2).addFortId(3).addExpNumber(Long.MAX_VALUE).addZoneName(-1, 2, 3);
		var captured = new ArrayList<BridgeMessageArg>();
		packet.describe(new FactSink()
		{
			@Override public void emit(BridgeFact fact) { throw new AssertionError("unexpected fact"); }
			@Override public long now() { return 0; }
			@Override public void systemMessage(int id, List<BridgeMessageArg> arguments)
			{
				assertThat(id).isEqualTo(100000);
				captured.addAll(arguments);
			}
		}, null);
		assertThat(captured).extracting(BridgeMessageArg::type).containsExactly(0, 1, 2, 3, 4, 5, 6, 7);
		assertThat(captured.get(0).text()).isEqualTo("exact\0text");
		assertThat(captured.get(1).number()).isEqualTo(-7);
		assertThat(captured.get(6).number()).isEqualTo(Long.MAX_VALUE);
		assertThat(captured.get(7)).isEqualTo(new BridgeMessageArg(7, null, -1, 2, 3));
	}

	@Test
	void refusalDeliveryRequiresTheSameClientOwnerAndThread() throws Exception
	{
		var player = BridgeTestPlayers.player();
		var client = mock(L2Client.class);
		var other = mock(L2Client.class);
		when(client.getActiveChar()).thenReturn(player);
		when(other.getActiveChar()).thenReturn(player);
		var attempt = new Attempt(client, player);
		EffectLedger.open(attempt);
		try
		{
			EffectLedger.systemMessage(other, player, 279);
			EffectLedger.systemMessage(client, BridgeTestPlayers.player(), 279);
			var failure = new AtomicReference<Throwable>();
			var thread = new Thread(() ->
			{
				try { EffectLedger.systemMessage(client, player, 279); }
				catch (Throwable problem) { failure.set(problem); }
			});
			thread.start();
			thread.join(5000);
			assertThat(thread.isAlive()).isFalse();
			assertThat(failure.get()).isNull();
			assertThat(attempt.refusals()).isEmpty();
			EffectLedger.systemMessage(client, player, 350);
			EffectLedger.systemMessage(client, player, 279);
			EffectLedger.systemMessage(client, player, 350);
			assertThat(attempt.refusals()).containsExactly(350, 279);
			assertThat(BridgeRefusals.firstReason(7, attempt.refusals())).isEqualTo(17);
		}
		finally { EffectLedger.close(); }
		EffectLedger.systemMessage(client, player, 129);
		assertThat(attempt.refusals()).containsExactly(350, 279);
	}
}
