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

import org.junit.jupiter.api.Test;

import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.network.L2Client;
import com.l2jfree.gameserver.network.bridge.Attempt;
import com.l2jfree.gameserver.network.bridge.BridgeEffect;
import com.l2jfree.gameserver.network.bridge.EffectLedger;
import com.l2jfree.gameserver.network.packets.L2ClientPacket;

class BridgeExecutionTest
{
	@Test
	void anExceptionAfterAReceiptIsUnknownAndClearsTheThreadLocal()
	{
		L2Player player = BridgeTestPlayers.player();
		L2Client client = mock(L2Client.class);
		when(client.isBridge()).thenReturn(true);
		when(client.getActiveChar()).thenReturn(player);
		L2ClientPacket packet = new L2ClientPacket()
		{
			@Override
			protected void readImpl()
			{
				throw new AssertionError("direct packets never read");
			}

			@Override
			protected void runImpl()
			{
				EffectLedger.record(player, BridgeEffect.item(1060, 70, 2));
				throw new IllegalStateException("fixture");
			}

			@Override
			public String getType()
			{
				return "bridge test";
			}
		};
		Attempt attempt = BridgeExecution.run(client, packet);
		assertThat(attempt.isStarted()).isTrue();
		assertThat(attempt.isFailed()).isTrue();
		assertThat(attempt.effects()).hasSize(1);
		assertThat(BridgeOutcome.match(attempt, 7, 1060, 0, 2)).isEqualTo(BridgeOutcome.UNKNOWN);
		assertThat(EffectLedger.isActive()).isFalse();
	}
}
