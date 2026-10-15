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
package com.l2jfree.gameserver.gameobjects.instance;

import com.l2jfree.gameserver.datatables.SpawnTable;
import com.l2jfree.gameserver.gameobjects.L2Creature;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.gameobjects.ai.CtrlEvent;
import com.l2jfree.gameserver.gameobjects.templates.L2NpcTemplate;
import com.l2jfree.gameserver.model.world.spawn.L2Spawn;
import com.l2jfree.gameserver.network.SystemChatChannelId;
import com.l2jfree.gameserver.network.packets.server.CreatureSay;
import com.l2jfree.tools.random.Rnd;

public class L2PenaltyMonsterInstance extends L2MonsterInstance
{
	private L2Player _ptk;
	
	public L2PenaltyMonsterInstance(int objectId, L2NpcTemplate template)
	{
		super(objectId, template);
	}
	
	@Override
	public L2Creature getMostHated()
	{
		return _ptk;
	}
	
	@Deprecated
	public void notifyPlayerDead()
	{
		// Monster kill player and can by deleted
		deleteMe();
		
		L2Spawn spawn = getSpawn();
		if (spawn != null)
		{
			spawn.stopRespawn();
			SpawnTable.getInstance().deleteSpawn(spawn, false);
		}
	}
	
	public void setPlayerToKill(L2Player ptk)
	{
		if (Rnd.nextInt(100) <= 80)
		{
			CreatureSay cs =
					new CreatureSay(getObjectId(), SystemChatChannelId.Chat_Normal, getName(),
							"Mmm, your bait was delicious!");
			broadcastPacket(cs);
		}
		_ptk = ptk;
		addDamageHate(ptk, 10, 10);
		getAI().notifyEvent(CtrlEvent.EVT_ATTACKED, ptk);
		addAttackerToAttackByList(ptk);
	}
	
	@Override
	public boolean doDie(L2Creature killer)
	{
		if (!super.doDie(killer))
			return false;
		
		if (Rnd.nextInt(100) <= 75)
		{
			CreatureSay cs =
					new CreatureSay(getObjectId(), SystemChatChannelId.Chat_Normal, getName(),
							"I will tell fishes not to take your bait!");
			broadcastPacket(cs);
		}
		return true;
	}
}
