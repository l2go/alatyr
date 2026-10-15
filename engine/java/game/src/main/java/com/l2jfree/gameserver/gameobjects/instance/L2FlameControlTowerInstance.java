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

import com.l2jfree.gameserver.gameobjects.L2Creature;
import com.l2jfree.gameserver.gameobjects.L2Npc;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.gameobjects.ai.CtrlIntention;
import com.l2jfree.gameserver.gameobjects.templates.L2NpcTemplate;
import com.l2jfree.gameserver.geodata.GeoData;
import com.l2jfree.gameserver.network.packets.server.ActionFailed;
import com.l2jfree.gameserver.network.packets.server.StatusUpdate;

public class L2FlameControlTowerInstance extends L2Npc
{
	private final boolean _east;
	
	public L2FlameControlTowerInstance(int objectId, L2NpcTemplate template, boolean east)
	{
		super(objectId, template);
		_east = east;
	}
	
	@Override
	public boolean isAttackable()
	{
		// Attackable during siege by attacker only
		return (getCastle() != null && getCastle().getCastleId() > 0 && getCastle().getSiege().getIsInProgress());
	}
	
	@Override
	public boolean isAutoAttackable(L2Creature attacker)
	{
		// Attackable during siege by attacker only
		return (attacker != null && attacker instanceof L2Player && getCastle() != null
				&& getCastle().getCastleId() > 0 && getCastle().getSiege().getIsInProgress() && getCastle().getSiege()
				.checkIsAttacker(((L2Player)attacker).getClan()));
	}
	
	@Override
	public void onForcedAttack(L2Player player)
	{
		onAction(player);
	}
	
	@Override
	public void onAction(L2Player player)
	{
		if (!canTarget(player))
			return;
		
		// Check if the L2Player already target the L2NpcInstance
		if (this != player.getTarget())
		{
			// Set the target of the L2Player player
			player.setTarget(this);
			
			// Send a Server->Client packet StatusUpdate of the L2NpcInstance to the L2Player to update its HP bar
			StatusUpdate su = new StatusUpdate(getObjectId());
			su.addAttribute(StatusUpdate.CUR_HP, (int)getStatus().getCurrentHp());
			su.addAttribute(StatusUpdate.MAX_HP, getMaxHp());
			player.sendPacket(su);
		}
		else
		{
			if (isAutoAttackable(player) && Math.abs(player.getZ() - getZ()) < 100 // Less then max height difference, delete check when geo
					&& GeoData.getInstance().canSeeTarget(player, this))
			{
				// Notify the L2Player AI with AI_INTENTION_INTERACT
				player.getAI().setIntention(CtrlIntention.AI_INTENTION_ATTACK, this);
				
				// Send a Server->Client ActionFailed to the L2Player in order to avoid that the client wait another packet
				player.sendPacket(ActionFailed.STATIC_PACKET);
			}
		}
	}
	
	/**
	 * Remove the L2ControlTowerInstance from the world.<BR><BR>
	 *
	 * <B><U> Actions</U> :</B><BR><BR>
	 * <li>Manage Siege task (killFlag, killCT) </li><BR><BR>
	 *
	 * <FONT COLOR=#FF0000><B> <U>Caution</U> : This method DOESN'T REMOVE the object from _allObjects of L2World </B></FONT><BR>
	 * <FONT COLOR=#FF0000><B> <U>Caution</U> : This method DOESN'T SEND Server->Client packets to players</B></FONT><BR><BR>
	 *
	 * @see com.l2jfree.gameserver.gameobjects.instance.L2NpcInstance#decayMe()
	 */
	@Override
	public final void decayMe()
	{
		if (getCastle().getSiege().getIsInProgress())
			getCastle().getSiege().killedFlameCT(_east);
		super.decayMe();
		// TODO: now spawn another NPC (id + 1) which represents dead tower
	}
}
