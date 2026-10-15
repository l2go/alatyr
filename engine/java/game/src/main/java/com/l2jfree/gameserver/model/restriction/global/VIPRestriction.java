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
import com.l2jfree.gameserver.Announcements;
import com.l2jfree.gameserver.ThreadPoolManager;
import com.l2jfree.gameserver.gameobjects.L2Creature;
import com.l2jfree.gameserver.gameobjects.L2Npc;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.model.entity.events.VIP;
import com.l2jfree.gameserver.model.entity.events.VIP.VIPPlayerInfo;

/**
 * @author NB4L1
 */
public final class VIPRestriction extends AbstractFunEventRestriction
{
	private static final class SingletonHolder
	{
		private static final VIPRestriction INSTANCE = new VIPRestriction();
	}
	
	public static VIPRestriction getInstance()
	{
		return SingletonHolder.INSTANCE;
	}
	
	private VIPRestriction()
	{
	}
	
	@Override
	boolean started()
	{
		return VIP._started;
	}
	
	@Override
	boolean allowSummon()
	{
		return true;
	}
	
	@Override
	boolean allowPotions()
	{
		return Config.VIP_ALLOW_POTIONS;
	}
	
	@Override
	boolean allowInterference()
	{
		return Config.VIP_ALLOW_INTERFERENCE;
	}
	
	@Override
	boolean sitForced()
	{
		return VIP._sitForced;
	}
	
	@Override
	boolean teamEquals(L2Player participant1, L2Player participant2)
	{
		final VIPPlayerInfo info1 = participant1.as(VIPPlayerInfo.class);
		final VIPPlayerInfo info2 = participant2.as(VIPPlayerInfo.class);
		
		return info1._isVIP == info2._isVIP && info1._isNotVIP == info2._isNotVIP;
	}
	
	@Override
	boolean isInFunEvent(L2Player player)
	{
		return player.isInEvent(VIPPlayerInfo.class);
	}
	
	@Override
	public boolean canRequestRevive(L2Player activeChar)
	{
		return true;
	}
	
	@Override
	public int getNameColor(L2Player activeChar)
	{
		VIPPlayerInfo info = activeChar.getPlayerInfo(VIPPlayerInfo.class);
		
		return info != null ? info._nameColourVIP : -1;
	}
	
	@Override
	public void playerLoggedIn(L2Player activeChar)
	{
		if (VIP._savePlayers.contains(activeChar.getName()))
			VIP.addDisconnectedPlayer(activeChar);
	}
	
	@Override
	public boolean playerKilled(L2Creature activeChar, final L2Player target, L2Player killer)
	{
		final VIPPlayerInfo targetInfo = target.getPlayerInfo(VIPPlayerInfo.class);
		
		if (targetInfo == null)
			return false;
		
		if (killer != null)
		{
			if (VIP._started)
			{
				if (targetInfo._isTheVIP && killer.isInEvent(VIPPlayerInfo.class))
				{
					VIP.vipDied();
				}
				else if (targetInfo._isTheVIP && !killer.isInEvent(VIPPlayerInfo.class))
				{
					Announcements.getInstance().announceToAll(
							"VIP Killed by non-event character. VIP going back to initial spawn.");
					target.doRevive();
					target.teleToLocation(VIP._startX, VIP._startY, VIP._startZ);
				}
				else if (targetInfo._isTheVIP && killer.as(VIPPlayerInfo.class)._isVIP)
				{
					Announcements.getInstance().announceToAll(
							"VIP Killed by same team player. VIP going back to initial spawn.");
					target.doRevive();
					target.teleToLocation(VIP._startX, VIP._startY, VIP._startZ);
				}
				else
				{
					target.sendMessage("You will be revived and teleported to team spot in 20 seconds!");
					ThreadPoolManager.getInstance().scheduleGeneral(new Runnable() {
						@Override
						public void run()
						{
							target.doRevive();
							if (targetInfo._isVIP)
								target.teleToLocation(VIP._startX, VIP._startY, VIP._startZ);
							else
								target.teleToLocation(VIP._endX, VIP._endY, VIP._endZ);
						}
					}, 20000);
				}
			}
			
			return true;
		}
		
		return false;
	}
	
	@Override
	public boolean onBypassFeedback(L2Npc npc, L2Player activeChar, String command)
	{
		if (command.startsWith("vip_joinVIPTeam"))
		{
			VIP.addPlayerVIP(activeChar);
			return true;
		}
		else if (command.startsWith("vip_joinNotVIPTeam"))
		{
			VIP.addPlayerNotVIP(activeChar);
			return true;
		}
		else if (command.startsWith("vip_finishVIP"))
		{
			VIP.vipWin(activeChar);
			return true;
		}
		
		return false;
	}
	
	@Override
	public boolean onAction(L2Npc npc, L2Player activeChar)
	{
		if (npc._isEventVIPNPC)
		{
			VIP.showJoinHTML(activeChar, String.valueOf(npc.getObjectId()));
			return true;
		}
		else if (npc._isEventVIPNPCEnd)
		{
			VIP.showEndHTML(activeChar, String.valueOf(npc.getObjectId()));
			return true;
		}
		
		return false;
	}
}
