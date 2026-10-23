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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.l2jfree.gameserver.gameobjects.L2Player;

/**
 * The changes recorded on one thread while a bridge action runs. It is not a cause and it is not shared across threads.
 * At most 64 effects are kept; one more marks the ledger incomplete and is not stored.
 */
public final class Attempt
{
	static final int MAX_EFFECTS = 64;
	
	private final L2Player _player;
	private final com.l2jfree.gameserver.network.L2Client _client;
	private final int _classIndex;
	private final ArrayList<BridgeEffect> _effects = new ArrayList<BridgeEffect>();
	private final ArrayList<Integer> _refusals = new ArrayList<Integer>();
	
	private boolean _started;
	private boolean _failed;
	private boolean _incomplete;
	private boolean _spoke;
	private int _spokenNpc;
	private Cost _cost;
	
	public Attempt(L2Player player)
	{
		this(null, player);
	}

	public Attempt(com.l2jfree.gameserver.network.L2Client client, L2Player player)
	{
		if (player == null)
			throw new IllegalArgumentException("player");
		_player = player;
		_client = client;
		_classIndex = player.getClassIndex();
	}

	boolean belongsTo(com.l2jfree.gameserver.network.L2Client client)
	{
		return _client != null && _client == client && client.getActiveChar() == _player;
	}
	
	public L2Player player()
	{
		return _player;
	}
	
	public int classIndex()
	{
		return _classIndex;
	}
	
	public boolean isStarted()
	{
		return _started;
	}
	
	public void markStarted()
	{
		_started = true;
	}
	
	public boolean isFailed()
	{
		return _failed;
	}
	
	public void markFailed()
	{
		_failed = true;
	}
	
	public boolean isLedgerIncomplete()
	{
		return _incomplete || _player.getClassIndex() != _classIndex;
	}

	void markIncomplete(L2Player owner)
	{
		if (owner == _player)
			_incomplete = true;
	}
	
	public boolean hasSpoken()
	{
		return _spoke;
	}
	
	public void markSpoken()
	{
		_spoke = true;
	}

	public boolean hasSpoken(int npcId)
	{
		return _spoke && _spokenNpc == npcId;
	}

	void noteSpoken(L2Player owner, int npcId)
	{
		if (owner == _player && npcId > 0)
		{
			_spoke = true;
			_spokenNpc = npcId;
		}
	}
	
	public void addRefusal(int systemMessageId)
	{
		if (systemMessageId < 0)
		{
			_incomplete = true;
			return;
		}
		if (_refusals.contains(systemMessageId))
			return;
		if (_refusals.size() >= MAX_EFFECTS)
		{
			_incomplete = true;
			return;
		}
		_refusals.add(systemMessageId);
	}
	
	public boolean hasRefusal(int systemMessageId)
	{
		return systemMessageId >= 0 && _refusals.contains(systemMessageId);
	}

	public List<Integer> refusals()
	{
		return List.copyOf(_refusals);
	}
	
	public List<BridgeEffect> effects()
	{
		return Collections.unmodifiableList(_effects);
	}

	/** Immutable expected cost captured before the selected learning branch mutates the player. */
	public boolean hasLearningCost()
	{
		return _cost != null && _player.getClassIndex() == _classIndex;
	}

	public int learningSpCost()
	{
		return _cost == null ? 0 : _cost._spCost;
	}

	public int learningItemId()
	{
		return _cost == null ? 0 : _cost._itemId;
	}

	public long learningItemCount()
	{
		return _cost == null ? 0 : _cost._itemCount;
	}
	
	void add(L2Player owner, BridgeEffect effect)
	{
		if (_incomplete)
			return;
		if (owner == null || effect == null)
		{
			_incomplete = true;
			return;
		}
		// Another character's change is not this attempt's receipt.
		if (owner != _player)
			return;
		if (_effects.size() >= MAX_EFFECTS)
		{
			_incomplete = true;
			return;
		}
		_effects.add(effect);
	}
	
	void expectLearningCost(L2Player owner, int branch, int spCost, int itemId, long itemCount)
	{
		if (_incomplete)
			return;
		if (owner == null)
		{
			_incomplete = true;
			return;
		}
		if (owner != _player)
			return;
		if (owner.getClassIndex() != _classIndex)
		{
			_incomplete = true;
			return;
		}
		if (branch < 0 || branch > 5 || branch == 5 || spCost < 0 || itemCount < 0 || itemId < 0
				|| (itemId == 0 && itemCount != 0))
		{
			_incomplete = true;
			return;
		}
		Cost cost = new Cost(branch, spCost, itemId, itemCount, owner.getClassIndex());
		if (_cost == null)
		{
			_cost = cost;
			return;
		}
		if (!_cost.same(cost))
			_incomplete = true;
	}
	
	private static final class Cost
	{
		private final int _branch;
		private final int _spCost;
		private final int _itemId;
		private final long _itemCount;
		private final int _classIndex;
		
		Cost(int branch, int spCost, int itemId, long itemCount, int classIndex)
		{
			_branch = branch;
			_spCost = spCost;
			_itemId = itemId;
			_itemCount = itemCount;
			_classIndex = classIndex;
		}
		
		boolean same(Cost other)
		{
			return _branch == other._branch && _spCost == other._spCost && _itemId == other._itemId
					&& _itemCount == other._itemCount && _classIndex == other._classIndex;
		}
	}
}
