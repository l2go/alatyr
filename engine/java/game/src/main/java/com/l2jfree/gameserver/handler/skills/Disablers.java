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
package com.l2jfree.gameserver.handler.skills;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import org.apache.commons.lang3.ArrayUtils;

import com.l2jfree.gameserver.datatables.HeroSkillTable;
import com.l2jfree.gameserver.gameobjects.L2Attackable;
import com.l2jfree.gameserver.gameobjects.L2Creature;
import com.l2jfree.gameserver.gameobjects.L2Npc;
import com.l2jfree.gameserver.gameobjects.L2Playable;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.gameobjects.L2Summon;
import com.l2jfree.gameserver.gameobjects.ai.CtrlEvent;
import com.l2jfree.gameserver.gameobjects.ai.CtrlIntention;
import com.l2jfree.gameserver.gameobjects.ai.L2AttackableAI;
import com.l2jfree.gameserver.gameobjects.instance.L2CubicInstance;
import com.l2jfree.gameserver.gameobjects.instance.L2SiegeSummonInstance;
import com.l2jfree.gameserver.handler.ICubicSkillHandler;
import com.l2jfree.gameserver.handler.SkillHandler;
import com.l2jfree.gameserver.model.items.L2ItemInstance;
import com.l2jfree.gameserver.model.skills.Env;
import com.l2jfree.gameserver.model.skills.Formulas;
import com.l2jfree.gameserver.model.skills.L2Skill;
import com.l2jfree.gameserver.model.skills.effects.L2Effect;
import com.l2jfree.gameserver.model.skills.effects.impl.EffectBuff;
import com.l2jfree.gameserver.model.skills.templates.L2EffectType;
import com.l2jfree.gameserver.model.skills.templates.L2SkillType;
import com.l2jfree.gameserver.network.SystemMessageId;
import com.l2jfree.gameserver.network.packets.server.SystemMessage;
import com.l2jfree.tools.random.Rnd;

/**
 * This Handles Disabler skills
 * @author _drunk_
 */
public class Disablers implements ICubicSkillHandler
{
	private static final L2SkillType[] SKILL_IDS = { L2SkillType.STUN, L2SkillType.ROOT, L2SkillType.SLEEP,
			L2SkillType.CONFUSION, L2SkillType.AGGDAMAGE, L2SkillType.AGGREDUCE, L2SkillType.AGGREDUCE_CHAR,
			L2SkillType.AGGREMOVE, L2SkillType.MUTE, L2SkillType.CONFUSE_MOB_ONLY, L2SkillType.NEGATE,
			L2SkillType.CANCEL, L2SkillType.CANCEL_STATS, L2SkillType.CANCEL_DEBUFF, L2SkillType.PARALYZE,
			L2SkillType.BETRAY, L2SkillType.ERASE, L2SkillType.MAGE_BANE, L2SkillType.WARRIOR_BANE, L2SkillType.DISARM,
			L2SkillType.STEAL_BUFF };
	
	@Override
	public void useSkill(L2Creature activeChar, L2Skill skill, L2Creature... targets)
	{
		L2SkillType type = skill.getSkillType();
		
		byte shld = 0;
		boolean ss = false;
		boolean sps = false;
		boolean bss = false;
		
		L2ItemInstance weaponInst = activeChar.getActiveWeaponInstance();
		
		if (activeChar instanceof L2Player)
		{
			if (weaponInst == null && skill.isOffensive())
			{
				activeChar.sendPacket(SystemMessageId.WEAPON_CAN_USE_ONLY_WEAPON_SKILL);
				return;
			}
		}
		
		if (skill.useSpiritShot())
		{
			if (activeChar.isBlessedSpiritshotCharged())
			{
				bss = true;
				activeChar.useBlessedSpiritshotCharge();
			}
			else if (activeChar.isSpiritshotCharged())
			{
				sps = true;
				activeChar.useSpiritshotCharge();
			}
		}
		else
		//if (skill.useSoulShot())
		{
			ss = true;
		}
		
		for (L2Creature target : targets)
		{
			if (target == null)
				continue;
			
			if (target.isDead() || target.isInvul()) //bypass if target is null, invul or dead
				continue;
			
			// With Mystic Immunity you can't be buffed/debuffed
			if (target.isPreventedFromReceivingBuffs())
				continue;
			
			shld = Formulas.calcShldUse(activeChar, target, skill);
			
			switch (type)
			{
				case BETRAY:
				{
					if (Formulas.calcSkillSuccess(activeChar, target, skill, shld, ss, sps, bss))
						skill.getEffects(activeChar, target);
					else
						activeChar.sendResistedMyEffectMessage(target, skill);
					break;
				}
				case ROOT:
				case DISARM:
				case STUN:
				{
					if (Formulas.calcSkillReflect(target, skill) == Formulas.SKILL_REFLECT_SUCCEED)
						target = activeChar;
					
					if (Formulas.calcSkillSuccess(activeChar, target, skill, shld, ss, sps, bss))
					{
						skill.getEffects(activeChar, target);
					}
					else
						activeChar.sendResistedMyEffectMessage(target, skill);
					break;
				}
				case SLEEP:
				case PARALYZE: //use same as root for now
				{
					if (Formulas.calcSkillReflect(target, skill) == Formulas.SKILL_REFLECT_SUCCEED)
						target = activeChar;
					
					if (target instanceof L2Npc)
					{
						target.getAI().notifyEvent(CtrlEvent.EVT_AGGRESSION, activeChar, 50);
					}
					if (Formulas.calcSkillSuccess(activeChar, target, skill, shld, ss, sps, bss))
					{
						skill.getEffects(activeChar, target);
					}
					else
						activeChar.sendResistedMyEffectMessage(target, skill);
					break;
				}
				case CONFUSION:
				case MUTE:
				{
					if (Formulas.calcSkillReflect(target, skill) == Formulas.SKILL_REFLECT_SUCCEED)
						target = activeChar;
					
					if (target instanceof L2Npc)
					{
						target.getAI().notifyEvent(CtrlEvent.EVT_AGGRESSION, activeChar, 50);
					}
					if (Formulas.calcSkillSuccess(activeChar, target, skill, shld, ss, sps, bss))
					{
						skill.getEffects(activeChar, target);
					}
					else
						activeChar.sendResistedMyEffectMessage(target, skill);
					break;
				}
				case CONFUSE_MOB_ONLY:
				{
					// Do nothing if not on mob
					if (target instanceof L2Attackable)
					{
						if (Formulas.calcSkillSuccess(activeChar, target, skill, shld, ss, sps, bss))
						{
							skill.getEffects(activeChar, target);
						}
						else
							activeChar.sendResistedMyEffectMessage(target, skill);
					}
					else
						activeChar.sendPacket(SystemMessageId.TARGET_IS_INCORRECT);
					break;
				}
				case AGGDAMAGE:
				{
					if (target instanceof L2Attackable)
						target.getAI().notifyEvent(CtrlEvent.EVT_AGGRESSION, activeChar,
								(int)((150 * skill.getPower()) / (target.getLevel() + 7)));
					
					// TODO [Nemesiss] should this have 100% chance?
					skill.getEffects(activeChar, target);
					break;
				}
				case AGGREDUCE:
				{
					// These skills needs to be rechecked
					if (target instanceof L2Attackable)
					{
						skill.getEffects(activeChar, target);
						
						if (skill.getPower() > 0)
							((L2Attackable)target).reduceHate(null, (int)skill.getPower());
					}
					// when fail, target.getAI().notifyEvent(CtrlEvent.EVT_ATTACKED, activeChar);
					break;
				}
				case AGGREDUCE_CHAR:
				{
					// These skills needs to be rechecked
					if (Formulas.calcSkillSuccess(activeChar, target, skill, shld, ss, sps, bss))
					{
						if (target instanceof L2Attackable)
						{
							L2Attackable targ = (L2Attackable)target;
							targ.stopHating(activeChar);
							if (targ.getMostHated() == null)
							{
								if (targ.getAI() instanceof L2AttackableAI)
									((L2AttackableAI)targ.getAI()).setGlobalAggro(-25);
								targ.clearAggroList();
								targ.getAI().setIntention(CtrlIntention.AI_INTENTION_ACTIVE);
								targ.setWalking();
							}
						}
						skill.getEffects(activeChar, target);
					}
					else
					{
						activeChar.sendResistedMyEffectMessage(target, skill);
						target.getAI().notifyEvent(CtrlEvent.EVT_ATTACKED, activeChar);
					}
					break;
				}
				case AGGREMOVE:
				{
					// 1034 = repose, 1049 = requiem
					// These skills needs to be rechecked
					if (target instanceof L2Attackable && !target.isRaid())
					{
						if (Formulas.calcSkillSuccess(activeChar, target, skill, shld, ss, sps, bss))
						{
							((L2Attackable)target).reduceHate(null,
									((L2Attackable)target).getHating(((L2Attackable)target).getMostHated()));
						}
						else
						{
							activeChar.sendResistedMyEffectMessage(target, skill);
							target.getAI().notifyEvent(CtrlEvent.EVT_ATTACKED, activeChar);
						}
					}
					//else
					//	target.getAI().notifyEvent(CtrlEvent.EVT_ATTACKED, activeChar);
					break;
				}
				case ERASE: // Doesn't affect siege golem, wild hog cannon or swoop cannon
				{
					if (Formulas.calcSkillSuccess(activeChar, target, skill, shld, ss, sps, bss)
							&& !(target instanceof L2SiegeSummonInstance))
					{
						L2Player summonOwner = null;
						L2Summon summonPet = null;
						summonOwner = ((L2Summon)target).getOwner();
						summonPet = summonOwner.getPet();
						if (summonPet != null)
						{
							summonPet.unSummon(summonOwner);
							summonOwner.sendPacket(SystemMessageId.YOUR_SERVITOR_HAS_VANISHED);
						}
					}
					else
						activeChar.sendResistedMyEffectMessage(target, skill);
					break;
				}
				case MAGE_BANE:
				{
					if (Formulas.calcSkillReflect(target, skill) == Formulas.SKILL_REFLECT_SUCCEED)
						target = activeChar;
					
					if (!Formulas.calcSkillSuccess(activeChar, target, skill, shld, ss, sps, bss))
					{
						activeChar.sendResistedMyEffectMessage(target, skill);
						continue;
					}
					
					for (L2Effect e : target.getEffects().getAllEffects("casting_time_down"))
						// Acumen
						if (e.getSkill().isPositive())
							e.exit();
					for (L2Effect e : target.getEffects().getAllEffects("ma_up"))
						// Empower
						if (e.getSkill().isPositive())
							e.exit();
					break;
				}
				case WARRIOR_BANE:
				{
					if (Formulas.calcSkillReflect(target, skill) == Formulas.SKILL_REFLECT_SUCCEED)
						target = activeChar;
					
					if (!Formulas.calcSkillSuccess(activeChar, target, skill, shld, ss, sps, bss))
					{
						activeChar.sendResistedMyEffectMessage(target, skill);
						continue;
					}
					
					for (L2Effect e : target.getEffects().getAllEffects("attack_time_down"))
						// Haste
						if (e.getSkill().isPositive())
							e.exit();
					for (L2Effect e : target.getEffects().getAllEffects("speed_up"))
						// Wind Walk
						if (e.getSkill().isPositive())
							e.exit();
					break;
				}
				case CANCEL_DEBUFF:
				{
					L2Effect[] effects = target.getAllEffects();
					
					if (effects.length == 0)
						break;
					
					int count = (skill.getMaxNegatedEffects() > 0) ? 0 : -2;
					for (L2Effect e : effects)
					{
						if (count < skill.getMaxNegatedEffects())
						{
							if (e.tryNegateDebuff())
							{
								if (count > -1)
									count++;
							}
						}
					}
					
					break;
				}
				case STEAL_BUFF:
				{
					if (!(target instanceof L2Playable))
						return;
					
					L2Effect[] effects = target.getAllEffects();
					
					if (effects == null || effects.length < 1)
						return;
					
					// Reversing array
					ArrayUtils.reverse(effects = effects.clone());
					
					List<L2Effect> toSteal = new ArrayList<L2Effect>();
					int count = 0;
					int lastSkill = 0;
					
					for (L2Effect e : effects)
					{
						if (e == null
								|| (!(e instanceof EffectBuff) && e.getEffectType() != L2EffectType.TRANSFORMATION)
								|| e.getSkill().getSkillType() == L2SkillType.HEAL || e.getSkill().isToggle()
								|| e.getSkill().isDebuff() || HeroSkillTable.isHeroSkill(e.getSkill().getId())
								|| e.getSkill().isPotion() || e.isHerbEffect()
								|| e.getEffectType() == L2EffectType.ENVIRONMENT)
							continue;
						
						if (e.getSkill().getId() == lastSkill)
						{
							if (count == 0)
								count = 1;
							toSteal.add(e);
						}
						else if (count < skill.getPower())
						{
							toSteal.add(e);
							count++;
						}
						else
							break;
					}
					if (!toSteal.isEmpty())
						stealEffects(activeChar, target, toSteal);
					break;
				}
				case CANCEL:
				case CANCEL_STATS: // almost same as CANCEL
				{
					if (Formulas.calcSkillReflect(target, skill) == Formulas.SKILL_REFLECT_SUCCEED)
						target = activeChar;
					
					if (Formulas.calcSkillSuccess(activeChar, target, skill, shld, ss, sps, bss))
					{
						L2Effect[] effects = target.getAllEffects();
						
						double max = skill.getMaxNegatedEffects();
						if (max == 0)
							max = Integer.MAX_VALUE; //this is for RBcancells and stuff...
							
						if (effects.length >= max)
							effects = sortEffects(effects.clone());
						
						double count = 1;
						
						for (L2Effect e : effects)
						{
							// do not delete signet effects!
							switch (e.getEffectType())
							{
								case SIGNET_GROUND:
								case SIGNET_EFFECT:
									continue;
							}
							
							// do not delete signet effects!
							switch (e.getSkill().getId())
							{
								case 110:
								case 111:
								case 1323:
								case 1325:
								case 4082:
								case 4215:
								case 4515:
								case 5182:
									continue;
							}
							
							//do note delete songs / dances
							if (e.getSkill().isDanceOrSong())
							{
								continue;
							}
							
							//Such effects cannot be removed by player
							if (e.getEffectType() == L2EffectType.ENVIRONMENT)
								continue;
							
							if (type == L2SkillType.CANCEL) // works only on buff like skills
							{
								switch (e.getSkill().getSkillType())
								{
									case BUFF:
									case HEAL_PERCENT:
									case REFLECT:
									case COMBATPOINTHEAL:
										break;
									default:
										continue;
								}
							}
							
							double rate = 1 - (count / max);
							if (rate < 0.33)
								rate = 0.33;
							else if (rate > 0.95)
								rate = 0.95;
							if (Rnd.get(1000) < (rate * 1000))
							{
								if (type == L2SkillType.CANCEL)
								{
									e.exit();
									count++;
								}
								else if (type == L2SkillType.CANCEL_STATS)
								{
									for (L2SkillType skillType : skill.getNegateStats())
									{
										if (skillType == e.getSkill().getEffectType())
										{
											e.exit();
											count++;
											break;
										}
									}
								}
							}
							
							if (count > max)
								break;
						}
						
						// apply effects - for example Touch of Death
						skill.getEffects(activeChar, target);
					}
					else
						activeChar.sendResistedMyEffectMessage(target, skill);
					break;
				}
				case NEGATE:
				{
					if (Formulas.calcSkillReflect(target, skill) == Formulas.SKILL_REFLECT_SUCCEED)
						target = activeChar;
					
					if (skill.getNegateId().length > 0)
					{
						for (int id : skill.getNegateId())
						{
							//if someone is dumb enough to set a skill to negate an ENVIRONMENT skill,
							//it will be applied again in less than 3 seconds. No check here.
							target.getEffects().stopEffects(id);
						}
					}
					// all others negate type skills
					else
					{
						int removedBuffs = (skill.getMaxNegatedEffects() > 0) ? 0 : -2;
						
						for (L2SkillType skillType : skill.getNegateStats())
						{
							if (removedBuffs > skill.getMaxNegatedEffects())
								break;
							
							switch (skillType)
							{
								case BUFF:
									if (Formulas.calcSkillSuccess(90.0, activeChar, target, skill, shld, ss, sps, bss))
									{
										removedBuffs += negateEffect(target, L2SkillType.BUFF, -1, skill);
									}
									break;
								case HEAL:
									SkillHandler.getInstance().useSkill(L2SkillType.HEAL, activeChar, skill, target);
									break;
								default:
									removedBuffs += negateEffect(target, skillType, -1, skill);
									break;
							}//end switch
						}//end for
					}//end else
					
					if (Formulas.calcSkillSuccess(activeChar, target, skill, shld, ss, sps, bss))
					{
						skill.getEffects(activeChar, target);
					}
				}
			}
		}
	}
	
	@Override
	public void useCubicSkill(L2CubicInstance activeCubic, L2Skill skill, L2Creature... targets)
	{
		if (_log.isDebugEnabled())
			_log.info("Disablers: useCubicSkill()");
		
		L2SkillType type = skill.getSkillType();
		
		for (L2Creature target : targets)
		{
			if (target == null)
				continue;
			
			if (target.isDead()) // Bypass if target is null or dead
				continue;
			
			byte shld = Formulas.calcShldUse(activeCubic.getOwner(), target, skill);
			switch (type)
			{
				case STUN:
				case PARALYZE:
				case ROOT:
					if (Formulas.calcCubicSkillSuccess(activeCubic, target, skill, shld))
					{
						skill.getEffects(activeCubic, target);
						
						if (_log.isDebugEnabled())
							_log.info("Disablers: useCubicSkill() -> success");
					}
					else
					{
						if (_log.isDebugEnabled())
							_log.info("Disablers: useCubicSkill() -> failed");
					}
					break;
				case CANCEL_DEBUFF:
					L2Effect[] effects = target.getAllEffects();
					
					if (effects.length == 0)
						break;
					
					int count = (skill.getMaxNegatedEffects() > 0) ? 0 : -2;
					for (L2Effect e : effects)
					{
						if (count < skill.getMaxNegatedEffects())
						{
							if (e.tryNegateDebuff())
							{
								if (count > -1)
									count++;
							}
						}
					}
					break;
				case AGGDAMAGE:
					if (Formulas.calcCubicSkillSuccess(activeCubic, target, skill, shld))
					{
						if (target instanceof L2Attackable)
							target.getAI().notifyEvent(CtrlEvent.EVT_AGGRESSION, activeCubic.getOwner(),
									(int)((150 * skill.getPower()) / (target.getLevel() + 7)));
						skill.getEffects(activeCubic, target);
						if (_log.isDebugEnabled())
							_log.info("Disablers: useCubicSkill() -> success");
					}
					else
					{
						if (_log.isDebugEnabled())
							_log.info("Disablers: useCubicSkill() -> failed");
					}
					break;
			}
		}
	}
	
	private int negateEffect(L2Creature target, L2SkillType type, double negateLvl, L2Skill skill)
	{
		return negateEffect(target, type, negateLvl, 0, skill);
	}
	
	private int negateEffect(L2Creature target, L2SkillType type, double negateLvl, int skillId, L2Skill skill)
	{
		int maxNegatedEffects = skill.getMaxNegatedEffects();
		L2Effect[] effects = target.getAllEffects();
		int count = (maxNegatedEffects <= 0) ? -2 : 0;
		for (L2Effect e : effects)
		{
			//players may not remove these effects under any circumstances
			if (e.getEffectType() == L2EffectType.ENVIRONMENT)
				continue;
			if (negateLvl == -1) // If power is -1 the effect is always removed without power/lvl check ^^
			{
				if (e.getSkill().getSkillType() == type
						|| (e.getSkill().getEffectType() != null && e.getSkill().getEffectType() == type))
				{
					if (skill.getNegatePhysicalOnly())
						if (e.getSkill().isMagic())
							continue;
					
					if (skillId != 0)
					{
						if (skillId == e.getSkill().getId() && count < maxNegatedEffects)
						{
							e.exit();
							if (count > -1)
								count++;
						}
					}
					else if (count < maxNegatedEffects)
					{
						e.exit();
						if (count > -1)
							count++;
					}
				}
			}
			else
			{
				boolean cancel = false;
				if (e.getSkill().getEffectType() != null && e.getSkill().getEffectAbnormalLvl() >= 0)
				{
					if (e.getSkill().getEffectType() == type && e.getSkill().getEffectAbnormalLvl() <= negateLvl)
						cancel = true;
				}
				else if (e.getSkill().getSkillType() == type && e.getSkill().getAbnormalLvl() <= negateLvl)
					cancel = true;
				
				if (cancel)
				{
					if (skill.getNegatePhysicalOnly())
						if (e.getSkill().isMagic())
							continue;
					
					if (skillId != 0)
					{
						if (skillId == e.getSkill().getId() && count < maxNegatedEffects)
						{
							e.exit();
							if (count > -1)
								count++;
						}
					}
					else if (count < maxNegatedEffects)
					{
						e.exit();
						if (count > -1)
							count++;
					}
				}
			}
		}
		
		return (maxNegatedEffects <= 0) ? count + 2 : count;
	}
	
	private void stealEffects(L2Creature stealer, L2Creature stolen, List<L2Effect> stolenEffects)
	{
		for (L2Effect eff : stolenEffects)
		{
			// If eff time is smaller than 1 sec, will not be stolen, just to save CPU,
			// avoid synchronization(?) problems and NPEs
			if (eff.getPeriod() - eff.getTime() < 1)
				continue;
			
			Env env = new Env();
			env.player = stolen;
			env.target = stealer;
			env.skill = eff.getSkill();
			L2Effect e = eff.getEffectTemplate().getStolenEffect(env, eff);
			
			// Since there is a previous check that limits allowed effects to those which come from L2SkillType.BUFF,
			// it is not needed another check for L2SkillType
			if (stealer instanceof L2Player && e != null)
			{
				SystemMessage smsg = new SystemMessage(SystemMessageId.YOU_FEEL_S1_EFFECT);
				smsg.addSkillName(eff);
				((L2Player)stealer).sendPacket(smsg);
			}
			// Finishing stolen effect
			eff.exit();
		}
	}
	
	private L2Effect[] sortEffects(L2Effect[] initial)
	{
		Arrays.sort(initial, EFFECT_MAGIC_LEVEL_COMPARATOR);
		
		return initial;
	}
	
	private static final Comparator<L2Effect> EFFECT_MAGIC_LEVEL_COMPARATOR = new Comparator<L2Effect>() {
		@Override
		public int compare(L2Effect e1, L2Effect e2)
		{
			int magicLvl1 = e1.getSkill().getMagicLevel();
			int magicLvl2 = e2.getSkill().getMagicLevel();
			
			return (magicLvl1 < magicLvl2 ? -1 : (magicLvl1 == magicLvl2 ? 0 : 1));
		}
	};
	
	@Override
	public L2SkillType[] getSkillIds()
	{
		return SKILL_IDS;
	}
}
