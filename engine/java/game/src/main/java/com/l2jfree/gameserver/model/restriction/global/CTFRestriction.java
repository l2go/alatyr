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
import com.l2jfree.gameserver.model.entity.events.CTF;
import com.l2jfree.gameserver.model.entity.events.CTF.CTFPlayerInfo;

/**
 * @author NB4L1
 */
public final class CTFRestriction extends AbstractFunEventRestriction
{
	private static final class SingletonHolder
	{
		private static final CTFRestriction INSTANCE = new CTFRestriction();
	}
	
	public static CTFRestriction getInstance()
	{
		return SingletonHolder.INSTANCE;
	}
	
	private CTFRestriction()
	{
	}
	
	@Override
	boolean started()
	{
		return CTF._started;
	}
	
	@Override
	boolean allowSummon()
	{
		return Config.CTF_ALLOW_SUMMON;
	}
	
	@Override
	boolean allowPotions()
	{
		return Config.CTF_ALLOW_POTIONS;
	}
	
	@Override
	boolean allowInterference()
	{
		return Config.CTF_ALLOW_INTERFERENCE;
	}
	
	@Override
	boolean sitForced()
	{
		return CTF._sitForced;
	}
	
	@Override
	boolean joinCursed()
	{
		return Config.CTF_JOIN_CURSED;
	}
	
	@Override
	boolean reviveRecovery()
	{
		return Config.CTF_REVIVE_RECOVERY;
	}
	
	@Override
	boolean teamEquals(L2Player participant1, L2Player participant2)
	{
		return participant1.as(CTFPlayerInfo.class)._teamNameCTF
				.equals(participant2.as(CTFPlayerInfo.class)._teamNameCTF);
	}
	
	@Override
	boolean isInFunEvent(L2Player player)
	{
		return player.isInEvent(CTFPlayerInfo.class);
	}
	
	@Override
	public int getNameColor(L2Player activeChar)
	{
		CTFPlayerInfo info = activeChar.getPlayerInfo(CTFPlayerInfo.class);
		
		return info != null ? info._nameColorCTF : -1;
	}
	
	@Override
	public void levelChanged(L2Player activeChar)
	{
		if (activeChar.isInEvent(CTFPlayerInfo.class) && CTF._maxlvl == activeChar.getLevel() && !CTF._started)
		{
			CTF.removePlayer(activeChar);
			
			activeChar.sendMessage("Your event sign up was canceled.");
		}
	}
	
	@Override
	public void playerLoggedIn(L2Player activeChar)
	{
		if (CTF._savePlayers.contains(activeChar.getName()))
			CTF.addDisconnectedPlayer(activeChar);
	}
	
	@Override
	public boolean playerKilled(L2Creature activeChar, final L2Player target, L2Player killer)
	{
		final CTFPlayerInfo targetInfo = target.getPlayerInfo(CTFPlayerInfo.class);
		
		if (targetInfo == null)
			return false;
		
		if (CTF._teleport || CTF._started)
		{
			target.sendMessage("You will be revived and teleported to team flag in " + Config.CTF_REVIVE_DELAY / 1000
					+ " seconds!");
			
			if (targetInfo._haveFlagCTF)
			{
				CTF._flagsTaken.set(CTF._teams.indexOf(targetInfo._teamNameHaveFlagCTF), false);
				CTF.spawnFlag(targetInfo._teamNameHaveFlagCTF);
				CTF.removeFlagFromPlayer(target);
				target.broadcastUserInfo();
				targetInfo._haveFlagCTF = false;
				CTF.AnnounceToPlayers(false, CTF._eventName + "(CTF): " + targetInfo._teamNameHaveFlagCTF
						+ "'s flag returned.");
			}
			
			ThreadPoolManager.getInstance().scheduleGeneral(new Runnable() {
				@Override
				public void run()
				{
					int x = CTF._teamsX.get(CTF._teams.indexOf(targetInfo._teamNameCTF));
					int y = CTF._teamsY.get(CTF._teams.indexOf(targetInfo._teamNameCTF));
					int z = CTF._teamsZ.get(CTF._teams.indexOf(targetInfo._teamNameCTF));
					
					target.teleToLocation(x, y, z, false);
					target.doRevive();
				}
			}, Config.CTF_REVIVE_DELAY);
		}
		
		return true;
	}
	
	@Override
	public boolean onBypassFeedback(L2Npc npc, L2Player activeChar, String command)
	{
		if (command.startsWith("ctf_player_join "))
		{
			if (CTF._joining)
				CTF.addPlayer(activeChar, command.substring(16));
			else
				activeChar.sendMessage("The event is already started. You can not join now!");
			return true;
		}
		else if (command.startsWith("ctf_player_leave"))
		{
			if (CTF._joining)
				CTF.removePlayer(activeChar);
			else
				activeChar.sendMessage("The event is already started. You can not leave now!");
			return true;
		}
		
		return false;
	}
	
	@Override
	public boolean onAction(L2Npc npc, L2Player activeChar)
	{
		if (npc._isEventMobCTF)
		{
			CTF.showEventHtml(activeChar, String.valueOf(npc.getObjectId()));
			return true;
		}
		else if (npc._isCTF_Flag && activeChar.isInEvent(CTFPlayerInfo.class))
		{
			CTF.showFlagHtml(activeChar, String.valueOf(npc.getObjectId()), npc._CTF_FlagTeamName);
			return true;
		}
		else if (npc._isCTF_throneSpawn)
		{
			CTF.CheckRestoreFlags();
			return true;
		}
		
		return false;
	}
}
