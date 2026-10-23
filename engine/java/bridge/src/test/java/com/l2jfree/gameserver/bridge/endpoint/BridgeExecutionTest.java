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
		L2Player player = mock(L2Player.class);
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
