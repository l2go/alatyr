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
			return attempt.hasSpoken(object) && attempt.effects().isEmpty() ? DONE : UNKNOWN;
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

	static int learn(Attempt attempt, int skill, int level)
	{
		if (attempt.isFailed() || attempt.isLedgerIncomplete() || !attempt.hasLearningCost())
			return UNKNOWN;
		int skills = 0;
		int sp = 0;
		int coins = 0;
		long items = 0;
		try
		{
			for (BridgeEffect effect : attempt.effects())
			{
				if (effect.kind() == BridgeEffect.SKILL && effect.skill() == skill
						&& effect.oldValue() < level && effect.newValue() == level)
				{
					if (++skills != 1)
						return UNKNOWN;
				}
				else if (effect.kind() == BridgeEffect.SP && effect.delta() == -attempt.learningSpCost()
						&& effect.delta() < 0)
				{
					if (++sp != 1)
						return UNKNOWN;
				}
				else if ((effect.kind() == BridgeEffect.ITEM && effect.item() == attempt.learningItemId()
						&& effect.item() != 57) || (effect.kind() == BridgeEffect.COIN && attempt.learningItemId() == 57))
				{
					if (effect.delta() >= 0)
						return UNKNOWN;
					if (effect.kind() == BridgeEffect.COIN && ++coins != 1)
						return UNKNOWN;
					items = Math.addExact(items, effect.delta());
				}
				else
					return UNKNOWN;
			}
		}
		catch (ArithmeticException overflow)
		{
			return UNKNOWN;
		}
		return skills == 1 && sp == (attempt.learningSpCost() == 0 ? 0 : 1)
				&& items == -attempt.learningItemCount() ? DONE : UNKNOWN;
	}
}
