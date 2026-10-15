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
import com.l2jfree.gameserver.gameobjects.L2Creature;
import com.l2jfree.gameserver.gameobjects.L2Npc;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.model.entity.events.TvTInstanced.TvTIMain;
import com.l2jfree.gameserver.model.quest.Quest;

/**
 * @author NB4L1
 */
public final class TvTiRestriction extends AbstractFunEventRestriction
{
	private static final class SingletonHolder
	{
		private static final TvTiRestriction INSTANCE = new TvTiRestriction();
	}
	
	public static TvTiRestriction getInstance()
	{
		return SingletonHolder.INSTANCE;
	}
	
	private TvTiRestriction()
	{
	}
	
	@Override
	boolean started()
	{
		return true;
	}
	
	@Override
	boolean allowSummon()
	{
		return Config.TVTI_ALLOW_SUMMON;
	}
	
	@Override
	boolean allowPotions()
	{
		return Config.TVTI_ALLOW_POTIONS;
	}
	
	@Override
	boolean allowInterference()
	{
		return Config.TVTI_ALLOW_INTERFERENCE;
	}
	
	@Override
	boolean joinCursed()
	{
		return Config.TVTI_JOIN_CURSED;
	}
	
	@Override
	boolean reviveRecovery()
	{
		return Config.TVTI_REVIVE_RECOVERY;
	}
	
	@Override
	boolean teamEquals(L2Player participant1, L2Player participant2)
	{
		return TvTIMain.checkSameTeam(participant1, participant2);
	}
	
	@Override
	boolean isInFunEvent(L2Player player)
	{
		return player._inEventTvTi;
	}
	
	@Override
	public boolean canStandUp(L2Player activeChar)
	{
		if (activeChar._isSitForcedTvTi)
		{
			activeChar.sendMessage("The Admin/GM handle if you sit or stand in this match!");
			return false;
		}
		
		return true;
	}
	
	@Override
	public void playerLoggedIn(L2Player activeChar)
	{
		if (TvTIMain.isPlayerInList(activeChar))
			TvTIMain.addDisconnectedPlayer(activeChar);
	}
	
	@Override
	public boolean playerKilled(L2Creature activeChar, final L2Player target, L2Player killer)
	{
		if (!target._inEventTvTi)
			return false;
		
		if (killer == null || !killer._inEventTvTi || !target._inEventTvTi)
			return false;
		
		if (!TvTIMain.checkSameTeam(killer, target))
		{
			killer._countTvTiKills++;
			killer.getAppearance().setVisibleTitle("Kills: " + killer._countTvTiKills);
			killer.sendPacket(Quest.SND_ITEM_GET);
			TvTIMain.addKill(killer);
		}
		else if (TvTIMain.checkSameTeam(killer, target))
		{
			killer.sendMessage("You are a teamkiller! Teamkills are not allowed, you will get death penalty and your team will lose one kill!");
			killer._countTvTITeamKills++;
			// Give Penalty for Team-Kill:
			// 1. Death Penalty + 5
			// 2. Team will lost 1 Kill
			if (killer.getDeathPenaltyBuffLevel() < 10)
			{
				killer.setDeathPenaltyBuffLevel(killer.getDeathPenaltyBuffLevel() + 4);
				killer.increaseDeathPenaltyBuffLevel();
			}
			TvTIMain.removePoint(killer);
			if (killer._countTvTITeamKills >= 2)
				TvTIMain.kickPlayerFromEvent(killer, 1);
		}
		TvTIMain.respawnPlayer(target);
		
		return true;
	}
	
	@Override
	public boolean onBypassFeedback(L2Npc npc, L2Player activeChar, String command)
	{
		if (command.startsWith("tvti_player_join_page"))
		{
			TvTIMain.showInstancesHtml(activeChar, String.valueOf(TvTIMain.getJoinNpc().getLastSpawn().getObjectId()));
			return true;
		}
		else if (command.startsWith("tvti_player_join "))
		{
			int instanceId = Integer.parseInt(command.substring(17));
			
			TvTIMain.addPlayer(activeChar, instanceId);
			return true;
		}
		else if (command.startsWith("tvti_player_leave"))
		{
			TvTIMain.removePlayer(activeChar);
			return true;
		}
		
		return false;
	}
	
	@Override
	public boolean onAction(L2Npc npc, L2Player activeChar)
	{
		if (npc._isEventMobTvTi)
		{
			TvTIMain.showEventHtml(activeChar, String.valueOf(npc.getObjectId()));
			return true;
		}
		
		return false;
	}
}
