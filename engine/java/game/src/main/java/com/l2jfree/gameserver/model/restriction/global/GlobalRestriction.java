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
public interface GlobalRestriction
{
	public boolean isRestricted(L2Player activeChar, Class<? extends GlobalRestriction> callingRestriction);
	
	public boolean canInviteToParty(L2Player activeChar, L2Player target);
	
	public boolean canCreateEffect(L2Creature activeChar, L2Creature target, L2Skill skill);
	
	public boolean isInvul(L2Creature activeChar, L2Creature target, L2Skill skill, boolean sendMessage,
			L2Player attacker_, L2Player target_, boolean isOffensive);
	
	public boolean isProtected(L2Creature activeChar, L2Creature target, L2Skill skill, boolean sendMessage,
			L2Player attacker_, L2Player target_, boolean isOffensive);
	
	public boolean canTarget(L2Creature activeChar, L2Creature target, boolean sendMessage, L2Player attacker_,
			L2Player target_);
	
	public boolean canRequestRevive(L2Player activeChar);
	
	public boolean canTeleport(L2Player activeChar);
	
	public boolean canUseItemHandler(Class<? extends IItemHandler> clazz, int itemId, L2Playable activeChar,
			L2ItemInstance item, L2Player player);
	
	public boolean canDropItem(L2Player player, int itemId, L2ItemInstance item);
	
	public boolean canDestroyItem(L2Player player, int itemId, L2ItemInstance item);
	
	public CombatState getCombatState(L2Player activeChar, L2Player target);
	
	public boolean canStandUp(L2Player activeChar);
	
	public boolean canPickUp(L2Player activeChar, L2ItemInstance item, L2PetInstance pet);
	
	public int getNameColor(L2Player activeChar);
	
	public int getTitleColor(L2Player activeChar);
	
	// TODO
	
	public Boolean isInsideZone(L2Creature activeChar, byte zone);
	
	public double calcDamage(L2Creature activeChar, L2Creature target, double damage, L2Skill skill);
	
	public List<L2Creature> getTargetList(SkillTargetType type, L2Creature activeChar, L2Skill skill,
			L2Creature target);
	
	// TODO
	
	public void levelChanged(L2Player activeChar);
	
	public void effectCreated(L2Effect effect);
	
	public void playerLoggedIn(L2Player activeChar);
	
	public void playerDisconnected(L2Player activeChar);
	
	public boolean playerKilled(L2Creature activeChar, L2Player target, L2Player killer);
	
	public void playerRevived(L2Player player);
	
	public void isInsideZoneStateChanged(L2Creature activeChar, byte zone, boolean isInsideZone);
	
	public void instanceChanged(L2Player activeChar, int oldInstance, int newInstance);
	
	public boolean onBypassFeedback(L2Npc npc, L2Player activeChar, String command);
	
	public boolean onAction(L2Npc npc, L2Player activeChar);
	
	public boolean useVoicedCommand(String command, L2Player activeChar, String target);
	
	// TODO
}
