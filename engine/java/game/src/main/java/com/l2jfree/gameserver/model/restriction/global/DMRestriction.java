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
package com.l2jfree.gameserver.model.restriction.global;

import com.l2jfree.Config;
import com.l2jfree.gameserver.ThreadPoolManager;
import com.l2jfree.gameserver.gameobjects.L2Creature;
import com.l2jfree.gameserver.gameobjects.L2Npc;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.model.entity.events.DM;
import com.l2jfree.gameserver.model.entity.events.DM.DMPlayerInfo;

/**
 * @author NB4L1
 */
public final class DMRestriction extends AbstractFunEventRestriction
{
	private static final class SingletonHolder
	{
		private static final DMRestriction INSTANCE = new DMRestriction();
	}
	
	public static DMRestriction getInstance()
	{
		return SingletonHolder.INSTANCE;
	}
	
	private DMRestriction()
	{
	}
	
	@Override
	boolean started()
	{
		return DM._started;
	}
	
	@Override
	boolean allowSummon()
	{
		return Config.DM_ALLOW_SUMMON;
	}
	
	@Override
	boolean allowPotions()
	{
		return Config.DM_ALLOW_POTIONS;
	}
	
	@Override
	boolean allowInterference()
	{
		return Config.DM_ALLOW_INTERFERENCE;
	}
	
	@Override
	boolean sitForced()
	{
		return DM._sitForced;
	}
	
	@Override
	boolean teamEquals(L2Player participant1, L2Player participant2)
	{
		return false;
	}
	
	@Override
	boolean isInFunEvent(L2Player player)
	{
		return player.isInEvent(DMPlayerInfo.class);
	}
	
	@Override
	public int getNameColor(L2Player activeChar)
	{
		DMPlayerInfo info = activeChar.getPlayerInfo(DMPlayerInfo.class);
		
		return info != null ? info._nameColorDM : -1;
	}
	
	@Override
	public void levelChanged(L2Player activeChar)
	{
		if (activeChar.isInEvent(DMPlayerInfo.class) && DM._maxlvl == activeChar.getLevel() && !DM._started)
		{
			DM.removePlayer(activeChar);
			
			activeChar.sendMessage("Your event sign up was canceled.");
		}
	}
	
	@Override
	public void playerLoggedIn(L2Player activeChar)
	{
		if (DM._savePlayers.contains(activeChar.getName()))
			DM.addDisconnectedPlayer(activeChar);
	}
	
	@Override
	public boolean playerKilled(L2Creature activeChar, final L2Player target, L2Player killer)
	{
		final DMPlayerInfo targetInfo = target.getPlayerInfo(DMPlayerInfo.class);
		
		if (targetInfo == null)
			return false;
		
		if (DM._teleport || DM._started)
		{
			if (killer != null && killer.isInEvent(DMPlayerInfo.class))
				killer.as(DMPlayerInfo.class)._countDMkills++;
			
			target.sendMessage("You will be revived and teleported to spot in " + Config.DM_REVIVE_DELAY / 1000
					+ " seconds!");
			
			ThreadPoolManager.getInstance().scheduleGeneral(new Runnable() {
				@Override
				public void run()
				{
					target.teleToLocation(DM._playerX, DM._playerY, DM._playerZ, false);
					target.doRevive();
				}
			}, Config.DM_REVIVE_DELAY);
		}
		
		return true;
	}
	
	@Override
	public boolean onBypassFeedback(L2Npc npc, L2Player activeChar, String command)
	{
		if (command.startsWith("dmevent_player_join"))
		{
			if (DM._joining)
				DM.addPlayer(activeChar);
			else
				activeChar.sendMessage("The event is already started. You can not join now!");
			return true;
		}
		else if (command.startsWith("dmevent_player_leave"))
		{
			if (DM._joining)
				DM.removePlayer(activeChar);
			else
				activeChar.sendMessage("The event is already started. You can not leave now!");
			return true;
		}
		
		return false;
	}
	
	@Override
	public boolean onAction(L2Npc npc, L2Player activeChar)
	{
		if (npc._isEventMobDM)
		{
			DM.showEventHtml(activeChar, String.valueOf(npc.getObjectId()));
			return true;
		}
		
		return false;
	}
}
