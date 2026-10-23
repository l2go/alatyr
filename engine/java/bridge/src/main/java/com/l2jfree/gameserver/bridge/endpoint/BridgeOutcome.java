package com.l2jfree.gameserver.bridge.endpoint;

import com.l2jfree.gameserver.network.bridge.Attempt;
import com.l2jfree.gameserver.network.bridge.BridgeEffect;

/** Matches actual synchronous receipts, never before/after inventory snapshots. */
final class BridgeOutcome
{
	static final int SENT = 1;
	static final int DONE = 2;
	static final int UNKNOWN = 4;

	private BridgeOutcome()
	{
	}

	static int match(Attempt attempt, int verb, int item, int object, long count)
	{
		if (attempt.isFailed() || attempt.isLedgerIncomplete())
			return UNKNOWN;
		if (verb == 1 || verb == 3 || verb == 9 || verb == 12)
			return SENT;
		if (verb == 5 || verb == 6)
			return attempt.hasSpoken() && attempt.effects().isEmpty() ? DONE : UNKNOWN;
		if (verb == 2 || verb == 10)
			return attempt.effects().size() == 1 && attempt.effects().get(0).kind() == BridgeEffect.TARGET
					&& attempt.effects().get(0).target() == object ? DONE : UNKNOWN;
		if (verb != 7 && verb != 8 && verb != 4)
			return UNKNOWN;
		long total = 0;
		int coins = 0;
		int targets = 0;
		try
		{
			for (BridgeEffect effect : attempt.effects())
			{
				if (effect.kind() == BridgeEffect.ITEM)
				{
					if (effect.item() != item || (verb == 8 && effect.object() != object)
							|| (verb == 8 ? effect.delta() >= 0 : effect.delta() <= 0))
						return UNKNOWN;
					total = Math.addExact(total, effect.delta());
				}
				else if (effect.kind() == BridgeEffect.COIN && verb != 4)
				{
					if (++coins != 1 || (verb == 7 ? effect.delta() >= 0 : effect.delta() <= 0))
						return UNKNOWN;
				}
				else if (effect.kind() == BridgeEffect.TARGET && verb == 4 && effect.target() == object)
				{
					if (++targets != 1)
						return UNKNOWN;
				}
				else
					return UNKNOWN;
			}
		}
		catch (ArithmeticException overflow)
		{
			return UNKNOWN;
		}
		return count > 0 && total == (verb == 8 ? -count : count) && coins == (verb == 4 ? 0 : 1)
				? DONE : UNKNOWN;
	}
}
