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
package com.l2jfree.gameserver.instancemanager.grandbosses;

import com.l2jfree.gameserver.ThreadPoolManager;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.model.entity.Entity;
import com.l2jfree.gameserver.model.entity.GrandBossState;
import com.l2jfree.gameserver.model.mapregion.TeleportWhereType;
import com.l2jfree.gameserver.model.quest.QuestState;

/**
 * 
 * This class ...
 * control for sequence of fight against Antharas.
 * @version $Revision: $ $Date: $
 * @author  L2J_JP SANDMAN
 */
public abstract class BossLair extends Entity
{
	public abstract void init();
	
	public abstract void setUnspawn();
	
	protected GrandBossState _state;
	protected String _questName;
	
	public GrandBossState.StateEnum getState()
	{
		return _state.getState();
	}
	
	public boolean isEnableEnterToLair()
	{
		return _state.getState() == GrandBossState.StateEnum.NOTSPAWN;
	}
	
	public synchronized boolean isPlayersAnnihilated()
	{
		for (L2Player pc : getPlayersInside())
		{
			if (!pc.isDead())
				return false;
		}
		return true;
	}
	
	public void checkAnnihilated()
	{
		if (isPlayersAnnihilated())
		{
			ThreadPoolManager.getInstance().scheduleGeneral(new Runnable() {
				@Override
				public void run()
				{
					setUnspawn();
				}
			}, 5000);
		}
	}
	
	@Override
	public void banishForeigners()
	{
		for (L2Player player : getPlayersInside())
		{
			if (_questName != null)
			{
				QuestState qs = player.getQuestState(_questName);
				if (qs != null)
					qs.exitQuest(true);
			}
			player.teleToLocation(TeleportWhereType.Town);
		}
	}
}
