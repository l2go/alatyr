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

import com.l2jfree.Config;
import com.l2jfree.gameserver.gameobjects.L2Creature;
import com.l2jfree.gameserver.gameobjects.L2Npc;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.gameobjects.ai.CtrlIntention;
import com.l2jfree.gameserver.gameobjects.status.CreatureStatus;
import com.l2jfree.gameserver.gameobjects.status.SiegeFlagStatus;
import com.l2jfree.gameserver.gameobjects.templates.L2NpcTemplate;
import com.l2jfree.gameserver.instancemanager.CCHManager;
import com.l2jfree.gameserver.instancemanager.FortSiegeManager;
import com.l2jfree.gameserver.instancemanager.SiegeManager;
import com.l2jfree.gameserver.model.clan.L2Clan;
import com.l2jfree.gameserver.model.clan.L2SiegeClan;
import com.l2jfree.gameserver.model.entity.CCHSiege;
import com.l2jfree.gameserver.model.entity.FortSiege;
import com.l2jfree.gameserver.model.entity.Siege;
import com.l2jfree.gameserver.network.SystemMessageId;
import com.l2jfree.gameserver.network.packets.server.ActionFailed;
import com.l2jfree.gameserver.network.packets.server.StatusUpdate;

public final class L2SiegeFlagInstance extends L2Npc
{
	private final L2Clan _clan;
	private final L2Player _player;
	private final Siege _siege;
	private final FortSiege _fortSiege;
	private final CCHSiege _contSiege;
	private final boolean _isAdvanced;
	private long _talkProtectionTime;
	
	public L2SiegeFlagInstance(L2Player player, int objectId, L2NpcTemplate template, boolean advanced)
	{
		super(objectId, template);
		
		_isAdvanced = advanced;
		_player = player;
		_clan = player == null ? null : player.getClan();
		_talkProtectionTime = 0;
		_siege = SiegeManager.getInstance().getSiege(_player);
		_fortSiege = FortSiegeManager.getInstance().getSiege(_player);
		_contSiege = CCHManager.getInstance().getSiege(_player);
		
		if (_clan == null)
		{
			deleteMe();
			return;
		}
		
		if (_siege == null && _fortSiege == null && _contSiege == null)
		{
			deleteMe();
			return;
		}
		
		L2SiegeClan sc = null;
		if (_siege != null && _fortSiege == null && _contSiege == null)
			sc = _siege.getAttackerClan(_player.getClan());
		else if (_siege == null && _fortSiege != null && _contSiege == null)
			sc = _fortSiege.getAttackerClan(_player.getClan());
		else if (_siege == null && _fortSiege == null && _contSiege != null)
			sc = _contSiege.getAttackerClan(_player.getClan());
		
		if (sc == null)
			deleteMe();
		else
			sc.addFlag(this);
		
		setIsInvul(false);
	}
	
	@Override
	public boolean isAttackable()
	{
		return true;
	}
	
	@Override
	public boolean isAutoAttackable(L2Creature attacker)
	{
		return true;
	}
	
	@Override
	public boolean doDie(L2Creature killer)
	{
		if (!super.doDie(killer))
			return false;
		
		L2SiegeClan sc = null;
		if (_siege != null)
			sc = _siege.getAttackerClan(_player.getClan());
		else if (_fortSiege != null)
			sc = _fortSiege.getAttackerClan(_player.getClan());
		else if (_contSiege != null)
			sc = _contSiege.getAttackerClan(_player.getClan());
		
		if (sc != null)
			sc.removeFlag(this);
		
		return true;
	}
	
	@Override
	public void onForcedAttack(L2Player player)
	{
		onAction(player);
	}
	
	@Override
	public void onAction(L2Player player)
	{
		if (!_player.canBeTargetedByAtSiege(player) && Config.SIEGE_ONLY_REGISTERED)
			return;
		
		if (player == null || !canTarget(player))
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
			if (isAutoAttackable(player) && Math.abs(player.getZ() - getZ()) < 100)
				player.getAI().setIntention(CtrlIntention.AI_INTENTION_ATTACK, this);
			else
			{
				// Send a Server->Client ActionFailed to the L2Player in order to avoid that the client wait another packet
				player.sendPacket(ActionFailed.STATIC_PACKET);
			}
		}
	}
	
	public void flagAttacked()
	{
		// send warning to owners of headquarters that theirs base is under attack
		if (_clan != null && canTalk())
			_clan.broadcastToOnlineMembers(SystemMessageId.BASE_UNDER_ATTACK.getSystemMessage());
		
		_talkProtectionTime = System.currentTimeMillis() + 20000;
	}
	
	public boolean canTalk()
	{
		return System.currentTimeMillis() > _talkProtectionTime;
	}
	
	@Override
	protected CreatureStatus initStatus()
	{
		return new SiegeFlagStatus(this);
	}
	
	@Override
	public SiegeFlagStatus getStatus()
	{
		return (SiegeFlagStatus)_status;
	}
	
	public boolean isAdvanced()
	{
		return _isAdvanced;
	}
}
