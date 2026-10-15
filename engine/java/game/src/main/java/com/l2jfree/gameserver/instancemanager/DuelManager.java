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
package com.l2jfree.gameserver.instancemanager;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.model.entity.Duel;
import com.l2jfree.gameserver.model.restriction.global.DuelRestriction;
import com.l2jfree.gameserver.model.restriction.global.GlobalRestrictions;
import com.l2jfree.gameserver.model.skills.effects.L2Effect;
import com.l2jfree.gameserver.network.packets.L2ServerPacket;

public class DuelManager
{
	private final static Logger _log = LoggerFactory.getLogger(DuelManager.class);
	
	private static final class SingletonHolder
	{
		private static final DuelManager INSTANCE = new DuelManager();
	}
	
	public static DuelManager getInstance()
	{
		return SingletonHolder.INSTANCE;
	}
	
	// =========================================================
	// Data Field
	private final Map<Integer, Duel> _duels = new ConcurrentHashMap<Integer, Duel>();
	private int _currentDuelId = 0x90;
	
	// =========================================================
	// Constructor
	private DuelManager()
	{
		_log.info("Initializing DuelManager");
	}
	
	// =========================================================
	// Method - Private
	
	private synchronized int getNextDuelId()
	{
		// In case someone wants to run the server forever :)
		if (++_currentDuelId >= 2147483640)
			_currentDuelId = 1;
		return _currentDuelId;
	}
	
	// =========================================================
	// Method - Public
	
	public Duel getDuel(int duelId)
	{
		return _duels.get(duelId);
	}
	
	public void addDuel(L2Player playerA, L2Player playerB, int partyDuel)
	{
		if (playerA == null || playerB == null)
			return;
		
		// Return if a player has PvPFlag
		String engagedInPvP = "The duel was canceled because a duelist engaged in PvP combat.";
		if (partyDuel == 1)
		{
			boolean playerInPvP = false;
			boolean isRestricted = false;
			for (L2Player temp : playerA.getParty().getPartyMembers())
			{
				if (temp.getPvpFlag() != 0)
				{
					playerInPvP = true;
					break;
				}
				else if (GlobalRestrictions.isRestricted(temp, DuelRestriction.class))
				{
					isRestricted = true;
					break;
				}
			}
			if (!playerInPvP && !isRestricted)
			{
				for (L2Player temp : playerB.getParty().getPartyMembers())
				{
					if (temp.getPvpFlag() != 0)
					{
						playerInPvP = true;
						break;
					}
					else if (GlobalRestrictions.isRestricted(temp, DuelRestriction.class))
					{
						isRestricted = true;
						break;
					}
				}
			}
			// A player has PvP flag
			if (playerInPvP)
			{
				for (L2Player temp : playerA.getParty().getPartyMembers())
				{
					temp.sendMessage(engagedInPvP);
				}
				for (L2Player temp : playerB.getParty().getPartyMembers())
				{
					temp.sendMessage(engagedInPvP);
				}
				return;
			}
			else if (isRestricted)
			{
				for (L2Player temp : playerA.getParty().getPartyMembers())
				{
					temp.sendMessage("The duel was canceled because a duelist is in a restricted state."); // TODO
				}
				for (L2Player temp : playerB.getParty().getPartyMembers())
				{
					temp.sendMessage("The duel was canceled because a duelist is in a restricted state."); // TODO
				}
				return;
			}
		}
		else
		{
			if (playerA.getPvpFlag() != 0 || playerB.getPvpFlag() != 0)
			{
				playerA.sendMessage(engagedInPvP);
				playerB.sendMessage(engagedInPvP);
				return;
			}
			else if (GlobalRestrictions.isRestricted(playerA, DuelRestriction.class)
					|| GlobalRestrictions.isRestricted(playerB, DuelRestriction.class))
			{
				playerA.sendMessage("The duel was canceled because a duelist is in a restricted state.");
				playerB.sendMessage("The duel was canceled because a duelist is in a restricted state.");
				return;
			}
		}
		
		Duel duel = new Duel(playerA, playerB, partyDuel, getNextDuelId());
		_duels.put(duel.getId(), duel);
	}
	
	public void removeDuel(Duel duel)
	{
		_duels.remove(duel.getId());
	}
	
	public void doSurrender(L2Player player)
	{
		if (player == null || !player.isInDuel())
			return;
		Duel duel = getDuel(player.getDuelId());
		duel.doSurrender(player);
	}
	
	/**
	 * Updates player states.
	 * @param player - the dieing player
	 */
	public void onPlayerDefeat(L2Player player)
	{
		if (player == null || !player.isInDuel())
			return;
		Duel duel = getDuel(player.getDuelId());
		if (duel != null)
			duel.onPlayerDefeat(player);
	}
	
	/**
	 * Registers a debuff which will be removed if the duel ends
	 * @param player
	 * @param debuff
	 */
	public void onBuff(L2Player player, L2Effect buff)
	{
		if (player == null || !player.isInDuel() || buff == null)
			return;
		Duel duel = getDuel(player.getDuelId());
		if (duel != null)
			duel.onBuff(player, buff);
	}
	
	/**
	 * Removes player from duel.
	 * @param player - the removed player
	 */
	public void onRemoveFromParty(L2Player player)
	{
		if (player == null || !player.isInDuel())
			return;
		Duel duel = getDuel(player.getDuelId());
		if (duel != null)
			duel.onRemoveFromParty(player);
	}
	
	/**
	 * Broadcasts a packet to the team opposing the given player.
	 * @param player
	 * @param packet
	 */
	public void broadcastToOppositTeam(L2Player player, L2ServerPacket packet)
	{
		if (player == null || !player.isInDuel())
			return;
		Duel duel = getDuel(player.getDuelId());
		if (duel == null)
			return;
		
		if (duel.getPlayerA() == null || duel.getPlayerB() == null)
			return;
		
		if (duel.getPlayerA() == player)
		{
			duel.broadcastToTeam2(packet);
		}
		else if (duel.getPlayerB() == player)
		{
			duel.broadcastToTeam1(packet);
		}
		else if (duel.isPartyDuel())
		{
			if (duel.getPlayerA().getParty() != null && duel.getPlayerA().getParty().getPartyMembers().contains(player))
			{
				duel.broadcastToTeam2(packet);
			}
			else if (duel.getPlayerB().getParty() != null
					&& duel.getPlayerB().getParty().getPartyMembers().contains(player))
			{
				duel.broadcastToTeam1(packet);
			}
		}
	}
}
