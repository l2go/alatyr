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
package com.l2jfree.gameserver.model.skills;

import org.apache.commons.lang3.ArrayUtils;

import com.l2jfree.gameserver.gameobjects.L2Creature;
import com.l2jfree.gameserver.gameobjects.L2Playable;
import com.l2jfree.gameserver.handler.SkillHandler;
import com.l2jfree.gameserver.model.Elementals;
import com.l2jfree.gameserver.model.skills.effects.L2Effect;
import com.l2jfree.gameserver.model.skills.templates.L2SkillType;
import com.l2jfree.gameserver.network.packets.server.MagicSkillLaunched;
import com.l2jfree.gameserver.network.packets.server.MagicSkillUse;
import com.l2jfree.util.L2Arrays;

/**
 * @author kombat/crion
 */
public final class ChanceSkillList
{
	private IChanceSkillTrigger[] _triggers = IChanceSkillTrigger.EMPTY_ARRAY;
	
	private final L2Creature _owner;
	
	public ChanceSkillList(L2Creature owner)
	{
		_owner = owner;
	}
	
	public synchronized void add(IChanceSkillTrigger trigger)
	{
		int nullIndex = -1;
		for (int i = 0; i < _triggers.length; i++)
		{
			final IChanceSkillTrigger tmp = _triggers[i];
			
			if (tmp == null)
				nullIndex = i;
			
			if (trigger.equals(tmp))
				return;
		}
		
		if (nullIndex < 0)
			_triggers = ArrayUtils.add(_triggers, trigger);
		
		else
			_triggers[nullIndex] = trigger;
	}
	
	public synchronized void remove(IChanceSkillTrigger trigger)
	{
		int nullCount = 0;
		for (int i = 0; i < _triggers.length; i++)
		{
			final IChanceSkillTrigger tmp = _triggers[i];
			
			if (trigger.equals(tmp))
			{
				_triggers[i] = null;
				
				nullCount++;
			}
			else if (tmp == null)
				nullCount++;
		}
		
		if (nullCount > 5)
			_triggers = L2Arrays.compact(_triggers);
	}
	
	public void onHit(L2Creature evtInitiator, boolean ownerWasHit, boolean wasCrit, boolean wasRange)
	{
		int event;
		if (ownerWasHit)
		{
			event = ChanceCondition.EVT_ATTACKED | ChanceCondition.EVT_ATTACKED_HIT;
			if (wasCrit)
				event |= ChanceCondition.EVT_ATTACKED_CRIT;
		}
		else
		{
			event = ChanceCondition.EVT_HIT;
			
			if (wasRange)
				event |= ChanceCondition.EVT_RANGE;
			else
				event |= ChanceCondition.EVT_MELEE;
			
			if (wasCrit)
				event |= ChanceCondition.EVT_CRIT;
		}
		
		onEvent(event, evtInitiator, Elementals.NONE);
	}
	
	public void onEvadedHit(L2Creature attacker)
	{
		onEvent(ChanceCondition.EVT_EVADED_HIT, attacker, Elementals.NONE);
	}
	
	public void onSkillHit(L2Creature evtInitiator, boolean ownerWasHit, L2Skill trigger)
	{
		int event;
		if (ownerWasHit)
		{
			event = ChanceCondition.EVT_HIT_BY_SKILL;
			if (trigger.isOffensive())
			{
				event |= ChanceCondition.EVT_HIT_BY_OFFENSIVE_SKILL;
				event |= ChanceCondition.EVT_ATTACKED;
			}
			else
			{
				event |= ChanceCondition.EVT_HIT_BY_GOOD_MAGIC;
			}
		}
		else
		{
			event = ChanceCondition.EVT_CAST;
			event |= trigger.isMagic() ? ChanceCondition.EVT_MAGIC : ChanceCondition.EVT_PHYSICAL;
			event |= trigger.isOffensive() ? ChanceCondition.EVT_MAGIC_OFFENSIVE : ChanceCondition.EVT_MAGIC_GOOD;
		}
		
		onEvent(event, evtInitiator, trigger.getElement());
	}
	
	public static boolean canTriggerByCast(L2Creature caster, L2Creature target, L2Skill trigger)
	{
		// crafting does not trigger any chance skills
		// possibly should be unhardcoded
		switch (trigger.getSkillType())
		{
			case COMMON_CRAFT:
			case DWARVEN_CRAFT:
				return false;
		}
		
		if (trigger.isToggle() || trigger.isPotion())
			return false; // No buffing with toggle skills or potions
			
		if (1320 <= trigger.getId() && trigger.getId() <= 1322)
			return false; // No buffing with Common and Dwarven Craft
			
		if (trigger.isOffensive() && !Formulas.calcMagicSuccess(caster, target, trigger))
			return false; // Low grade skills won't trigger for high level targets
			
		return true;
	}
	
	public void onEvent(int event, L2Creature evtInitiator, byte element)
	{
		final boolean playable = evtInitiator instanceof L2Playable;
		for (IChanceSkillTrigger trigger : _triggers)
		{
			if (trigger != null && trigger.getChanceCondition().trigger(event, element, playable))
			{
				makeCast(trigger.getChanceTriggeredSkill(_owner, evtInitiator), evtInitiator);
			}
		}
	}
	
	private void makeCast(L2Skill skill, L2Creature evtInitiator)
	{
		if (skill == null || skill.getSkillType() == L2SkillType.NOTDONE)
			return;
		
		if (_owner.isSkillDisabled(skill.getId()))
			return;
		
		if (skill.getReuseDelay() > 0)
			_owner.disableSkill(skill.getId(), skill.getReuseDelay());
		
		L2Creature[] targets = skill.getTargetList(_owner, false, evtInitiator);
		
		if (targets == null || targets.length == 0)
			return;
		
		//anyone has a better idea?
		boolean hasValidTarget = false;
		for (int i = 0; i < targets.length; i++)
		{
			final L2Creature target = targets[i];
			
			if (target == null)
				continue;
			
			final L2Effect effect = target.getFirstEffect(skill.getId());
			
			// if we already have a greater effect of it
			if (effect != null && effect.getSkill().getLevel() > skill.getLevel())
			{
				targets[i] = null;
				continue;
			}
			else if (!skill.checkCondition(_owner, target))
			{
				targets[i] = null;
				continue;
			}
			
			hasValidTarget = true;
		}
		
		if (!hasValidTarget)
			return;
		
		targets = L2Arrays.compact(targets);
		
		_owner.broadcastPacket(new MagicSkillUse(_owner, skill, 0, 0));
		_owner.broadcastPacket(new MagicSkillLaunched(_owner, skill, targets));
		
		// Launch the magic skill and calculate its effects
		// TODO: once core will support all posible effects, use effects (not handler)
		SkillHandler.getInstance().useSkill(_owner, skill, targets);
	}
}
