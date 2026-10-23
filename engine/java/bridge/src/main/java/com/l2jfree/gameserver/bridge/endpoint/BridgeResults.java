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

import java.util.ArrayList;
import java.util.List;

import com.l2jfree.gameserver.bridge.wire.Codec;
import com.l2jfree.gameserver.bridge.wire.Envelope;
import com.l2jfree.gameserver.bridge.wire.Field;
import com.l2jfree.gameserver.bridge.wire.Schemas;
import com.l2jfree.gameserver.bridge.wire.Value;
import com.l2jfree.gameserver.network.bridge.Attempt;
import com.l2jfree.gameserver.network.bridge.BridgeEffect;

/** Encodes terminal receipts; partial or failed execution never emits a done effect list. */
final class BridgeResults
{
	private BridgeResults()
	{
	}

	static byte[] encode(long n, long verb, int stage, long reason, Attempt attempt)
	{
		// Lack of evidence is quiet uncertainty; only failure or an incomplete ledger is a backend error.
		if (stage == BridgeOutcome.UNKNOWN && reason == 0)
			reason = attempt != null && (attempt.isFailed() || attempt.isLedgerIncomplete()) ? 24 : 17;
		ArrayList<Field> fields = new ArrayList<>(List.of(Field.u(1, true, n), Field.u(2, true, stage),
				Field.u(3, true, reason), Field.u(5, true, verb), Field.u(6, true, knownVerb(verb) ? verb : 0),
				Field.u(7, true, stage == 3 || stage == 5 ? 0 : 2)));
		long bridgeReason = switch ((int)reason)
		{
			case 1 -> 7;
			case 7 -> 8;
			case 10 -> 6;
			case 24 -> 20;
			case 20 -> 21;
			default -> 0;
		};
		if (bridgeReason != 0)
		{
			fields.add(Field.data(64, true, "x.l2jfree/bridge".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
			fields.add(Field.u(65, true, bridgeReason));
		}
		if (stage == BridgeOutcome.DONE)
		{
			ArrayList<Value> effects = new ArrayList<>();
			for (BridgeEffect effect : attempt.effects())
			{
				ArrayList<Field> entry = new ArrayList<>();
				entry.add(Field.u(1, true, effect.kind()));
				switch (effect.kind())
				{
					case BridgeEffect.ITEM -> {
						entry.add(Field.s(2, true, effect.item()));
						entry.add(Field.s(3, true, effect.object()));
						entry.add(Field.s(4, true, effect.delta()));
					}
					case BridgeEffect.COIN -> entry.add(Field.s(4, true, effect.delta()));
					case BridgeEffect.SKILL -> {
						entry.add(Field.s(5, true, effect.skill()));
						entry.add(Field.s(6, true, effect.oldValue()));
						entry.add(Field.s(7, true, effect.newValue()));
					}
					case BridgeEffect.SP -> {
						entry.add(Field.s(4, true, effect.delta()));
						entry.add(Field.s(6, true, effect.oldValue()));
						entry.add(Field.s(7, true, effect.newValue()));
					}
					case BridgeEffect.TARGET -> entry.add(Field.s(8, true, effect.target()));
					default -> throw new IllegalArgumentException("effect");
				}
				effects.add(Field.obj(1, true, entry).value());
			}
			fields.add(Field.items(66, true, effects));
		}
		byte[] encoded = Codec.bridge().encode(List.of(Envelope.of(Schemas.RESULT, fields)));
		if (encoded.length > 8192)
			return encode(n, verb, BridgeOutcome.UNKNOWN, 24, null);
		return encoded;
	}

	private static boolean knownVerb(long verb)
	{
		return verb >= 1 && verb <= 10 || verb == 12 || verb == 13;
	}
}
