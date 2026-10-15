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
import com.l2jfree.gameserver.gameobjects.L2Playable;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.handler.IItemHandler;
import com.l2jfree.gameserver.model.entity.events.AutomatedTvT;
import com.l2jfree.gameserver.model.items.L2ItemInstance;
import com.l2jfree.gameserver.model.restriction.global.GlobalRestrictions.CombatState;
import com.l2jfree.gameserver.model.zone.L2Zone;
import com.l2jfree.gameserver.network.SystemMessageId;

/**
 * @author NB4L1
 */
public final class AutomatedTvTRestriction extends AbstractRestriction
{
	private static final class SingletonHolder
	{
		private static final AutomatedTvTRestriction INSTANCE = new AutomatedTvTRestriction();
	}
	
	public static AutomatedTvTRestriction getInstance()
	{
		return SingletonHolder.INSTANCE;
	}
	
	private AutomatedTvTRestriction()
	{
	}
	
	@Override
	public boolean isRestricted(L2Player activeChar, Class<? extends GlobalRestriction> callingRestriction)
	{
		if (callingRestriction == getClass())
			return false;
		
		if (AutomatedTvT.isReged(activeChar) || AutomatedTvT.isPlaying(activeChar))
		{
			activeChar.sendMessage("Already participating in a fun event!");
			return true;
		}
		
		return false;
	}
	
	@Override
	public boolean canRequestRevive(L2Player activeChar)
	{
		if (!Config.AUTO_TVT_REVIVE_SELF && AutomatedTvT.isPlaying(activeChar))
			return false;
		
		return true;
	}
	
	@Override
	public boolean canTeleport(L2Player activeChar)
	{
		if (AutomatedTvT.isPlaying(activeChar))
		{
			activeChar.sendPacket(SystemMessageId.NOT_WORKING_PLEASE_TRY_AGAIN_LATER);
			return false;
		}
		
		return true;
	}
	
	@Override
	public boolean canUseItemHandler(Class<? extends IItemHandler> clazz, int itemId, L2Playable activeChar,
			L2ItemInstance item, L2Player player)
	{
		if (player != null && AutomatedTvT.isPlaying(player) && !AutomatedTvT.canUse(itemId))
		{
			player.sendPacket(SystemMessageId.NOT_WORKING_PLEASE_TRY_AGAIN_LATER);
			return false;
		}
		
		return true;
	}
	
	@Override
	public CombatState getCombatState(L2Player activeChar, L2Player target)
	{
		if (AutomatedTvT.isPlaying(activeChar) && AutomatedTvT.isPlaying(target))
		{
			final int team1 = AutomatedTvT.getTeam(activeChar);
			final int team2 = AutomatedTvT.getTeam(target);
			
			return team1 == team2 ? CombatState.FRIEND : CombatState.ENEMY;
		}
		
		return CombatState.NEUTRAL;
	}
	
	@Override
	public int getNameColor(L2Player activeChar)
	{
		return AutomatedTvT.isPlaying(activeChar) ? AutomatedTvT.getNameColor(activeChar) : -1;
	}
	
	@Override
	public Boolean isInsideZone(L2Creature activeChar, byte zone)
	{
		if (activeChar instanceof L2Playable && AutomatedTvT.isPlaying(activeChar.getActingPlayer()))
		{
			switch (zone)
			{
				case L2Zone.FLAG_NOSUMMON:
				{
					return Boolean.TRUE;
				}
				case L2Zone.FLAG_PEACE:
				{
					return Boolean.FALSE;
				}
				case L2Zone.FLAG_PVP:
				{
					return Boolean.TRUE;
				}
			}
		}
		
		return null;
	}
	
	@Override
	public void playerLoggedIn(L2Player activeChar)
	{
		AutomatedTvT.getInstance().addDisconnected(activeChar);
	}
	
	@Override
	public void playerDisconnected(L2Player activeChar)
	{
		AutomatedTvT.getInstance().onDisconnection(activeChar);
	}
	
	@Override
	public boolean playerKilled(L2Creature activeChar, L2Player target, L2Player killer)
	{
		if (AutomatedTvT.isPlaying(killer) && AutomatedTvT.isPlaying(target))
		{
			AutomatedTvT.getInstance().onKill(killer, target);
			return true;
		}
		else
			return false;
	}
	
	@Override
	public void playerRevived(L2Player player)
	{
		AutomatedTvT.getInstance().recover(player);
	}
	
	@Override
	public boolean useVoicedCommand(String command, L2Player activeChar, String target)
	{
		if (!Config.AUTO_TVT_ENABLED)
			return false;
		
		if (command.equals("jointvt"))
		{
			AutomatedTvT.getInstance().registerPlayer(activeChar);
			return true;
		}
		else if (Config.AUTO_TVT_REGISTER_CANCEL && command.equals("leavetvt"))
		{
			AutomatedTvT.getInstance().cancelRegistration(activeChar);
			return true;
		}
		
		return false;
	}
}
