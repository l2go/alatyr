package com.l2jfree.gameserver.bridge.endpoint;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.l2jfree.gameserver.bridge.wire.Codec;
import com.l2jfree.gameserver.network.bridge.Attempt;
import com.l2jfree.gameserver.network.bridge.BridgeEffect;
import com.l2jfree.gameserver.network.bridge.EffectLedger;

class BridgeResultsTest
{
	@Test
	void doneCarriesActualReceiptsAndUnknownNeverCarriesEffects()
	{
		var player = BridgeTestPlayers.player();
		Attempt attempt = new Attempt(player);
		EffectLedger.open(attempt);
		try
		{
			EffectLedger.record(player, BridgeEffect.item(1060, 70, 2));
			EffectLedger.record(player, BridgeEffect.coin(-13));
			var done = Codec.bridge().decode(BridgeResults.encode(1, 7, 2, 0, attempt)).get(0);
			assertThat(done.field(66).value().items()).hasSize(2);
			assertThat(done.field(2).value().u()).isEqualTo(2);
			var unknown = Codec.bridge().decode(BridgeResults.encode(1, 7, 4, 24, attempt)).get(0);
			assertThat(unknown.field(66)).isNull();
			assertThat(unknown.field(7).value().u()).isEqualTo(2);
		}
		finally
		{
			EffectLedger.close();
		}
	}
}
