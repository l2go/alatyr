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

import org.junit.jupiter.api.Test;

import com.l2jfree.gameserver.bridge.wire.Codec;
import com.l2jfree.gameserver.network.bridge.Attempt;
import com.l2jfree.gameserver.network.bridge.BridgeEffect;
import com.l2jfree.gameserver.network.bridge.EffectLedger;

class BridgeResultsTest
{
	@Test
	void anUnmatchedOutcomeEncodesAValidUnknownResult()
	{
		var result = Codec.bridge().decode(BridgeResults.encode(1, 7, 4, 0, null)).get(0);
		assertThat(result.field(2).value().u()).isEqualTo(4);
		assertThat(result.field(3).value().u()).isEqualTo(17);
		assertThat(result.field(7).value().u()).isEqualTo(2);
		assertThat(result.field(64)).isNull();
		assertThat(result.field(65)).isNull();
		assertThat(result.field(66)).isNull();
	}

	@Test
	void failedAndIncompleteOutcomesRetainTheBackendReason()
	{
		for (boolean failed : new boolean[] { true, false })
		{
			var player = BridgeTestPlayers.player();
			Attempt attempt = new Attempt(player);
			if (failed)
				attempt.markFailed();
			else
			{
				EffectLedger.open(attempt);
				try { EffectLedger.incomplete(player); }
				finally { EffectLedger.close(); }
			}
			var result = Codec.bridge().decode(BridgeResults.encode(1, 7, 4, 0, attempt)).get(0);
			assertThat(result.field(2).value().u()).isEqualTo(4);
			assertThat(result.field(3).value().u()).isEqualTo(24);
			assertThat(result.field(7).value().u()).isEqualTo(2);
			assertThat(result.field(65).value().u()).isEqualTo(20);
			assertThat(result.field(66)).isNull();
		}
	}

	@Test
	void unknownVerbPreservesRequestedButHasNoEffectiveVerb()
	{
		var result = Codec.bridge().decode(BridgeResults.encode(1, 99, 3, 10, null)).get(0);
		assertThat(result.field(5).value().u()).isEqualTo(99);
		assertThat(result.field(6).value().u()).isZero();
	}

	@Test
	void admissionRefusalsCarryThePairedBridgeReason()
	{
		var result = Codec.bridge().decode(BridgeResults.encode(1, 9, 3, 1, null)).get(0);
		assertThat(result.field(64).value().data()).containsExactly("x.l2jfree/bridge".getBytes(java.nio.charset.StandardCharsets.UTF_8));
		assertThat(result.field(65).value().u()).isEqualTo(7);
		assertThat(result.field(7).value().u()).isZero();
	}

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
			assertThat(BridgeResults.encode(1, 7, 2, 0, attempt)).containsExactly(
					java.util.HexFormat.of().parseHex("0c1f180128023800580768077802aa08100a180129c810398c0149040418024919"));
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
