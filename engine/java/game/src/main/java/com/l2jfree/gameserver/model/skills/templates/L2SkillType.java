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
package com.l2jfree.gameserver.model.skills.templates;

import java.lang.reflect.Constructor;

import com.l2jfree.gameserver.model.skills.L2Skill;
import com.l2jfree.gameserver.model.skills.l2skills.L2SkillAgathion;
import com.l2jfree.gameserver.model.skills.l2skills.L2SkillAppearance;
import com.l2jfree.gameserver.model.skills.l2skills.L2SkillChangeWeapon;
import com.l2jfree.gameserver.model.skills.l2skills.L2SkillChargeNegate;
import com.l2jfree.gameserver.model.skills.l2skills.L2SkillCpDrain;
import com.l2jfree.gameserver.model.skills.l2skills.L2SkillCreateItem;
import com.l2jfree.gameserver.model.skills.l2skills.L2SkillDecoy;
import com.l2jfree.gameserver.model.skills.l2skills.L2SkillDrain;
import com.l2jfree.gameserver.model.skills.l2skills.L2SkillFusion;
import com.l2jfree.gameserver.model.skills.l2skills.L2SkillLearnSkill;
import com.l2jfree.gameserver.model.skills.l2skills.L2SkillMount;
import com.l2jfree.gameserver.model.skills.l2skills.L2SkillPdam;
import com.l2jfree.gameserver.model.skills.l2skills.L2SkillRecall;
import com.l2jfree.gameserver.model.skills.l2skills.L2SkillRecover;
import com.l2jfree.gameserver.model.skills.l2skills.L2SkillSiegeFlag;
import com.l2jfree.gameserver.model.skills.l2skills.L2SkillSignet;
import com.l2jfree.gameserver.model.skills.l2skills.L2SkillSignetCasttime;
import com.l2jfree.gameserver.model.skills.l2skills.L2SkillSummon;
import com.l2jfree.gameserver.model.skills.l2skills.L2SkillSweep;
import com.l2jfree.gameserver.model.skills.l2skills.L2SkillTeleport;
import com.l2jfree.gameserver.model.skills.l2skills.L2SkillTrap;
import com.l2jfree.gameserver.templates.StatsSet;

public enum L2SkillType
{
	PDAM(L2SkillPdam.class),
	MDAM,
	CPDAM,
	CPDAMPERCENT,
	CPDRAIN(L2SkillCpDrain.class),
	AGGDAMAGE,
	DOT,
	HOT,
	BLEED,
	POISON,
	CPHOT,
	MPHOT,
	BUFF,
	DEBUFF,
	STUN,
	ROOT,
	CONT,
	CONFUSION,
	FUSION(L2SkillFusion.class),
	PARALYZE,
	FEAR,
	SLEEP,
	HEAL,
	HEAL_MOB,
	COMBATPOINTHEAL,
	MANAHEAL,
	MANAHEAL_PERCENT,
	MANARECHARGE,
	RESURRECT,
	PASSIVE,
	UNLOCK,
	GIVE_SP,
	GIVE_VITALITY,
	NEGATE,
	CANCEL,
	CANCEL_DEBUFF,
	AGGREDUCE,
	AGGREMOVE,
	AGGREDUCE_CHAR,
	CONFUSE_MOB_ONLY,
	DEATHLINK(MDAM),
	BLOW(PDAM),
	FATALCOUNTER(PDAM),
	DETECT_WEAKNESS,
	ENCHANT_ARMOR, // should be deprecated
	ENCHANT_WEAPON, // should be deprecated
	FEED_PET, // should be deprecated
	HEAL_PERCENT,
	HEAL_STATIC,
	LUCK, // should be deprecated
	MANADAM,
	MAKE_KILLABLE,
	MAKE_QUEST_DROPABLE,
	MDOT,
	MUTE,
	RECALL(L2SkillRecall.class),
	REFLECT, // should be depreacted
	SUMMON_FRIEND,
	SOULSHOT, // should be deprecated
	SPIRITSHOT, // should be deprecated
	SPOIL,
	WEAKNESS, // should be deprecated
	DISARM,
	STEAL_BUFF,
	SIEGEFLAG(L2SkillSiegeFlag.class),
	TAKECASTLE,
	TAKEFORT,
	BEAST_FEED, // should be deprecated
	DRAIN_SOUL, // should be deprecated
	COMMON_CRAFT,
	DWARVEN_CRAFT,
	DELUXE_KEY_UNLOCK, // should be deprecated
	SOW,
	HARVEST,
	CHARGESOUL,
	GET_PLAYER,
	FISHING,
	PUMPING,
	REELING,
	AGGDEBUFF,
	CPHEAL_PERCENT,
	SUMMON_TREASURE_KEY,
	ERASE,
	MAGE_BANE,
	WARRIOR_BANE,
	STRSIEGEASSAULT,
	BETRAY,
	BALANCE_LIFE,
	TRANSFORMDISPEL,
	DETECT_TRAP,
	REMOVE_TRAP,
	SHIFT_TARGET,
	INSTANT_JUMP,
	DETECTION,
	BALLISTA,
	EXTRACTABLE,
	LEARN_SKILL(L2SkillLearnSkill.class),
	CANCEL_STATS,
	
	AGATHION(L2SkillAgathion.class),
	MOUNT(L2SkillMount.class),
	CHANGEWEAPON(L2SkillChangeWeapon.class),
	CHARGEDAM(PDAM),
	CHARGE_NEGATE(L2SkillChargeNegate.class), // should be merged into NEGATE
	CREATE_ITEM(L2SkillCreateItem.class),
	DECOY(L2SkillDecoy.class),
	DRAIN(L2SkillDrain.class, MDAM),
	SWEEP(L2SkillSweep.class),
	RECOVER(L2SkillRecover.class),
	SIGNET(L2SkillSignet.class),
	SIGNET_CASTTIME(L2SkillSignetCasttime.class),
	SUMMON(L2SkillSummon.class),
	SUMMON_TRAP(L2SkillTrap.class),
	// Skill that has no effect.
	DUMMY,
	// Skill is done within the core.
	COREDONE,
	// Unimplemented
	NOTDONE,
	TELEPORT(L2SkillTeleport.class),
	CHANGE_APPEARANCE(L2SkillAppearance.class),
	OPEN_DOOR,
	ZAKEN_TELEPORT, ;
	
	private final Class<? extends L2Skill> _clazz;
	private final Constructor<? extends L2Skill> _constructor;
	private final L2SkillType _parent;
	
	private L2SkillType()
	{
		this(null, null);
	}
	
	private L2SkillType(L2SkillType parent)
	{
		this(null, parent);
	}
	
	private L2SkillType(Class<? extends L2Skill> clazz)
	{
		this(clazz, null);
	}
	
	private L2SkillType(Class<? extends L2Skill> clazz, L2SkillType parent)
	{
		_parent = parent;
		
		if (_parent == null)
		{
			_clazz = (clazz == null ? L2Skill.class : clazz);
			try
			{
				_constructor = _clazz.getConstructor(StatsSet.class);
			}
			catch (Exception e)
			{
				throw new RuntimeException(e);
			}
		}
		else
		{
			if (clazz == null)
			{
				_clazz = _parent._clazz;
				_constructor = _parent._constructor;
			}
			else
			{
				if (_parent._clazz.isAssignableFrom(clazz))
					_clazz = clazz;
				else
					throw new IllegalStateException();
				
				try
				{
					_constructor = _clazz.getConstructor(StatsSet.class);
				}
				catch (Exception e)
				{
					throw new RuntimeException(e);
				}
			}
		}
	}
	
	public L2Skill makeSkill(StatsSet set) throws Exception
	{
		return _constructor.newInstance(set);
	}
	
	public L2SkillType getRoot()
	{
		if (_parent == null)
			return this;
		
		return _parent.getRoot();
	}
}
