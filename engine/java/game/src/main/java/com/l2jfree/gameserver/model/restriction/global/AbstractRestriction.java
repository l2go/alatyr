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

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.l2jfree.gameserver.gameobjects.L2Creature;
import com.l2jfree.gameserver.gameobjects.L2Npc;
import com.l2jfree.gameserver.gameobjects.L2Playable;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.gameobjects.instance.L2PetInstance;
import com.l2jfree.gameserver.handler.IItemHandler;
import com.l2jfree.gameserver.model.items.L2ItemInstance;
import com.l2jfree.gameserver.model.restriction.global.GlobalRestrictions.CombatState;
import com.l2jfree.gameserver.model.skills.L2Skill;
import com.l2jfree.gameserver.model.skills.L2Skill.SkillTargetType;
import com.l2jfree.gameserver.model.skills.effects.L2Effect;

/**
 * @author NB4L1
 */
public abstract class AbstractRestriction implements GlobalRestriction
{
	static final Logger _log = LoggerFactory.getLogger(AbstractRestriction.class);
	
	public void activate()
	{
		GlobalRestrictions.activate(this);
	}
	
	public void deactivate()
	{
		GlobalRestrictions.deactivate(this);
	}
	
	@Override
	public int hashCode()
	{
		return getClass().hashCode();
	}
	
	/**
	 * To avoid accidentally multiple times activated restrictions.
	 */
	@Override
	public boolean equals(Object obj)
	{
		return getClass().equals(obj.getClass());
	}
	
	@Override
	@DisabledRestriction
	public boolean isRestricted(L2Player activeChar, Class<? extends GlobalRestriction> callingRestriction)
	{
		throw new AbstractMethodError();
	}
	
	@Override
	@DisabledRestriction
	public boolean canInviteToParty(L2Player activeChar, L2Player target)
	{
		throw new AbstractMethodError();
	}
	
	@Override
	@DisabledRestriction
	public boolean canCreateEffect(L2Creature activeChar, L2Creature target, L2Skill skill)
	{
		throw new AbstractMethodError();
	}
	
	@Override
	@DisabledRestriction
	public boolean isInvul(L2Creature activeChar, L2Creature target, L2Skill skill, boolean sendMessage,
			L2Player attacker_, L2Player target_, boolean isOffensive)
	{
		throw new AbstractMethodError();
	}
	
	@Override
	@DisabledRestriction
	public boolean isProtected(L2Creature activeChar, L2Creature target, L2Skill skill, boolean sendMessage,
			L2Player attacker_, L2Player target_, boolean isOffensive)
	{
		throw new AbstractMethodError();
	}
	
	@Override
	@DisabledRestriction
	public boolean canTarget(L2Creature activeChar, L2Creature target, boolean sendMessage, L2Player attacker_,
			L2Player target_)
	{
		throw new AbstractMethodError();
	}
	
	@Override
	@DisabledRestriction
	public boolean canRequestRevive(L2Player activeChar)
	{
		throw new AbstractMethodError();
	}
	
	@Override
	@DisabledRestriction
	public boolean canTeleport(L2Player activeChar)
	{
		throw new AbstractMethodError();
	}
	
	@Override
	@DisabledRestriction
	public boolean canUseItemHandler(Class<? extends IItemHandler> clazz, int itemId, L2Playable activeChar,
			L2ItemInstance item, L2Player player)
	{
		throw new AbstractMethodError();
	}
	
	@Override
	@DisabledRestriction
	public boolean canDropItem(L2Player player, int itemId, L2ItemInstance item)
	{
		throw new AbstractMethodError();
	}
	
	@Override
	@DisabledRestriction
	public boolean canDestroyItem(L2Player player, int itemId, L2ItemInstance item)
	{
		throw new AbstractMethodError();
	}
	
	@Override
	@DisabledRestriction
	public CombatState getCombatState(L2Player activeChar, L2Player target)
	{
		throw new AbstractMethodError();
	}
	
	@Override
	@DisabledRestriction
	public boolean canStandUp(L2Player activeChar)
	{
		throw new AbstractMethodError();
	}
	
	@Override
	@DisabledRestriction
	public boolean canPickUp(L2Player activeChar, L2ItemInstance item, L2PetInstance pet)
	{
		throw new AbstractMethodError();
	}
	
	@Override
	@DisabledRestriction
	public int getNameColor(L2Player activeChar)
	{
		throw new AbstractMethodError();
	}
	
	@Override
	@DisabledRestriction
	public int getTitleColor(L2Player activeChar)
	{
		throw new AbstractMethodError();
	}
	
	// TODO
	
	@Override
	@DisabledRestriction
	public Boolean isInsideZone(L2Creature activeChar, byte zone)
	{
		throw new AbstractMethodError();
	}
	
	@Override
	@DisabledRestriction
	public double calcDamage(L2Creature activeChar, L2Creature target, double damage, L2Skill skill)
	{
		throw new AbstractMethodError();
	}
	
	@Override
	@DisabledRestriction
	public List<L2Creature> getTargetList(SkillTargetType type, L2Creature activeChar, L2Skill skill,
			L2Creature target)
	{
		throw new AbstractMethodError();
	}
	
	// TODO
	
	@Override
	@DisabledRestriction
	public void levelChanged(L2Player activeChar)
	{
		throw new AbstractMethodError();
	}
	
	@Override
	@DisabledRestriction
	public void effectCreated(L2Effect effect)
	{
		throw new AbstractMethodError();
	}
	
	@Override
	@DisabledRestriction
	public void playerLoggedIn(L2Player activeChar)
	{
		throw new AbstractMethodError();
	}
	
	@Override
	@DisabledRestriction
	public void playerDisconnected(L2Player activeChar)
	{
		throw new AbstractMethodError();
	}
	
	@Override
	@DisabledRestriction
	public boolean playerKilled(L2Creature activeChar, L2Player target, L2Player killer)
	{
		throw new AbstractMethodError();
	}
	
	@Override
	@DisabledRestriction
	public void playerRevived(L2Player player)
	{
		throw new AbstractMethodError();
	}
	
	@Override
	@DisabledRestriction
	public void isInsideZoneStateChanged(L2Creature activeChar, byte zone, boolean isInsideZone)
	{
		throw new AbstractMethodError();
	}
	
	@Override
	@DisabledRestriction
	public boolean onBypassFeedback(L2Npc npc, L2Player activeChar, String command)
	{
		throw new AbstractMethodError();
	}
	
	@Override
	@DisabledRestriction
	public boolean onAction(L2Npc npc, L2Player activeChar)
	{
		throw new AbstractMethodError();
	}
	
	@Override
	@DisabledRestriction
	public boolean useVoicedCommand(String command, L2Player activeChar, String target)
	{
		throw new AbstractMethodError();
	}
	
	@Override
	@DisabledRestriction
	public void instanceChanged(L2Player activeChar, int oldInstance, int newInstance)
	{
		throw new AbstractMethodError();
	}
	
	// TODO
}
