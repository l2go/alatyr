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
package com.l2jfree.gameserver.network.bridge;

import com.l2jfree.gameserver.gameobjects.L2Player;

/**
 * Thread-local ledger of one bridge attempt. With no attempt open, {@link #record} and {@link #expectLearningCost}
 * read the thread local and return, and allocate nothing.
 * <p>
 * Branch 0 is normal, 1 transform, 2 certification, 3 fishing, 4 collection, and 5 an unsupported clan or quest cost.
 * An item id of 0 means there is no item cost, and then the count is 0. A repeated identical cost is not a receipt.
 */
public final class EffectLedger
{
	private static final ThreadLocal<Attempt> ACTIVE = new ThreadLocal<Attempt>();
	
	private EffectLedger()
	{
	}
	
	public static boolean isActive()
	{
		return ACTIVE.get() != null;
	}
	
	public static void open(Attempt attempt)
	{
		if (attempt == null)
			throw new IllegalArgumentException("attempt");
		if (ACTIVE.get() != null)
			throw new IllegalStateException("an attempt is already open");
		ACTIVE.set(attempt);
	}
	
	public static void close()
	{
		ACTIVE.remove();
	}

	/** Attributes only synchronous dialogue from the owner's currently observed NPC. */
	public static void spoken(com.l2jfree.gameserver.network.L2Client client, L2Player owner, int npcId)
	{
		Attempt attempt = ACTIVE.get();
		if (attempt == null || !attempt.belongsTo(client) || owner != attempt.player() || npcId <= 0)
			return;
		var npc = owner.getLastFolkNPC();
		if (npc != null && owner.getTarget() == npc && npc.getObjectId() == npcId)
			attempt.noteSpoken(owner, npcId);
	}
	
	public static void record(L2Player owner, BridgeEffect effect)
	{
		Attempt attempt = ACTIVE.get();
		if (attempt == null)
			return;
		attempt.add(owner, effect);
	}

	/** Records delivery only in this client's current synchronous attempt. */
	public static void systemMessage(com.l2jfree.gameserver.network.L2Client client, L2Player owner, int id)
	{
		Attempt attempt = ACTIVE.get();
		if (attempt != null && attempt.belongsTo(client) && owner == attempt.player())
			attempt.addRefusal(id);
	}

	/** Marks an owner mutation path whose complete receipt set is not represented by this profile. */
	public static void incomplete(L2Player owner)
	{
		Attempt attempt = ACTIVE.get();
		if (attempt != null)
			attempt.markIncomplete(owner);
	}

	/** A direct storage mutation without an owner cannot certify an active attempt. */
	public static void incompleteUnattributedMutation()
	{
		Attempt attempt = ACTIVE.get();
		if (attempt != null)
			attempt.markIncomplete(attempt.player());
	}
	
	public static void expectLearningCost(L2Player owner, int branch, int spCost, int itemId, long itemCount)
	{
		Attempt attempt = ACTIVE.get();
		if (attempt == null)
			return;
		attempt.expectLearningCost(owner, branch, spCost, itemId, itemCount);
	}
}
