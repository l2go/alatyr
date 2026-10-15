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
import com.l2jfree.gameserver.model.entity.events.TvT;
import com.l2jfree.gameserver.model.entity.events.TvT.TvTPlayerInfo;
import com.l2jfree.gameserver.model.quest.Quest;

/**
 * @author NB4L1
 */
public final class TvTRestriction extends AbstractFunEventRestriction
{
	private static final class SingletonHolder
	{
		private static final TvTRestriction INSTANCE = new TvTRestriction();
	}
	
	public static TvTRestriction getInstance()
	{
		return SingletonHolder.INSTANCE;
	}
	
	private TvTRestriction()
	{
	}
	
	@Override
	boolean started()
	{
		return TvT._started;
	}
	
	@Override
	boolean allowSummon()
	{
		return Config.TVT_ALLOW_SUMMON;
	}
	
	@Override
	boolean allowPotions()
	{
		return Config.TVT_ALLOW_POTIONS;
	}
	
	@Override
	boolean allowInterference()
	{
		return Config.TVT_ALLOW_INTERFERENCE;
	}
	
	@Override
	boolean sitForced()
	{
		return TvT._sitForced;
	}
	
	@Override
	boolean joinCursed()
	{
		return Config.TVT_JOIN_CURSED;
	}
	
	@Override
	boolean reviveRecovery()
	{
		return Config.TVT_REVIVE_RECOVERY;
	}
	
	@Override
	boolean teamEquals(L2Player participant1, L2Player participant2)
	{
		return participant1.as(TvTPlayerInfo.class)._teamNameTvT
				.equals(participant2.as(TvTPlayerInfo.class)._teamNameTvT);
	}
	
	@Override
	boolean isInFunEvent(L2Player player)
	{
		return player.isInEvent(TvTPlayerInfo.class);
	}
	
	@Override
	public int getNameColor(L2Player activeChar)
	{
		TvTPlayerInfo info = activeChar.getPlayerInfo(TvTPlayerInfo.class);
		
		return info != null ? info._nameColorTvT : -1;
	}
	
	@Override
	public void levelChanged(L2Player activeChar)
	{
		if (activeChar.isInEvent(TvTPlayerInfo.class) && TvT._maxlvl == activeChar.getLevel() && !TvT._started)
		{
			TvT.removePlayer(activeChar);
			
			activeChar.sendMessage("Your event sign up was canceled.");
		}
	}
	
	@Override
	public void playerLoggedIn(L2Player activeChar)
	{
		if (TvT._savePlayers.contains(activeChar.getName()))
			TvT.addDisconnectedPlayer(activeChar);
	}
	
	@Override
	public boolean playerKilled(L2Creature activeChar, final L2Player target, L2Player killer)
	{
		final TvTPlayerInfo targetInfo = target.getPlayerInfo(TvTPlayerInfo.class);
		
		if (targetInfo == null)
			return false;
		
		if (TvT._teleport || TvT._started)
		{
			if (killer != null && killer.isInEvent(TvTPlayerInfo.class))
			{
				final TvTPlayerInfo killerInfo = killer.getPlayerInfo(TvTPlayerInfo.class);
				
				if (!(killerInfo._teamNameTvT.equals(targetInfo._teamNameTvT)))
				{
					targetInfo._countTvTdies++;
					killerInfo._countTvTkills++;
					killer.getAppearance().setVisibleTitle("Kills: " + killerInfo._countTvTkills);
					killer.sendPacket(Quest.SND_ITEM_GET);
					TvT.setTeamKillsCount(killerInfo._teamNameTvT, TvT.teamKillsCount(killerInfo._teamNameTvT) + 1);
				}
				else
				{
					killer.sendMessage("You are a teamkiller! Teamkills are not allowed, you will get death penalty and your team will lose one kill!");
					
					// Give Penalty for Team-Kill:
					// 1. Death Penalty + 5
					// 2. Team will lost 1 Kill
					if (killer.getDeathPenaltyBuffLevel() < 10)
					{
						killer.setDeathPenaltyBuffLevel(killer.getDeathPenaltyBuffLevel() + 4);
						killer.increaseDeathPenaltyBuffLevel();
					}
					TvT.setTeamKillsCount(targetInfo._teamNameTvT, TvT.teamKillsCount(targetInfo._teamNameTvT) - 1);
				}
			}
			
			target.sendMessage("You will be revived and teleported to team spot in " + Config.TVT_REVIVE_DELAY / 1000
					+ " seconds!");
			
			ThreadPoolManager.getInstance().scheduleGeneral(new Runnable() {
				@Override
				public void run()
				{
					int x = TvT._teamsX.get(TvT._teams.indexOf(targetInfo._teamNameTvT));
					int y = TvT._teamsY.get(TvT._teams.indexOf(targetInfo._teamNameTvT));
					int z = TvT._teamsZ.get(TvT._teams.indexOf(targetInfo._teamNameTvT));
					
					target.teleToLocation(x, y, z, false);
					target.doRevive();
				}
			}, Config.TVT_REVIVE_DELAY);
		}
		
		return true;
	}
	
	@Override
	public boolean onBypassFeedback(L2Npc npc, L2Player activeChar, String command)
	{
		if (command.startsWith("tvt_player_join "))
		{
			if (TvT._joining)
				TvT.addPlayer(activeChar, command.substring(16));
			else
				activeChar.sendMessage("The event is already started. You can not join now!");
			return true;
		}
		else if (command.startsWith("tvt_player_leave"))
		{
			if (TvT._joining)
				TvT.removePlayer(activeChar);
			else
				activeChar.sendMessage("The event is already started. You can not leave now!");
			return true;
		}
		
		return false;
	}
	
	@Override
	public boolean onAction(L2Npc npc, L2Player activeChar)
	{
		if (npc._isEventMobTvT)
		{
			TvT.showEventHtml(activeChar, String.valueOf(npc.getObjectId()));
			return true;
		}
		
		return false;
	}
}
