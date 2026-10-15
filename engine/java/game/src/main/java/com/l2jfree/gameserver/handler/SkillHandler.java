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
package com.l2jfree.gameserver.handler;

import com.l2jfree.gameserver.gameobjects.L2Creature;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.gameobjects.instance.L2CubicInstance;
import com.l2jfree.gameserver.handler.skills.BalanceLife;
import com.l2jfree.gameserver.handler.skills.BallistaBomb;
import com.l2jfree.gameserver.handler.skills.BeastFeed;
import com.l2jfree.gameserver.handler.skills.ChangeFace;
import com.l2jfree.gameserver.handler.skills.CombatPointHeal;
import com.l2jfree.gameserver.handler.skills.Continuous;
import com.l2jfree.gameserver.handler.skills.CpDam;
import com.l2jfree.gameserver.handler.skills.Craft;
import com.l2jfree.gameserver.handler.skills.DeluxeKey;
import com.l2jfree.gameserver.handler.skills.Detection;
import com.l2jfree.gameserver.handler.skills.Disablers;
import com.l2jfree.gameserver.handler.skills.DrainSoul;
import com.l2jfree.gameserver.handler.skills.Dummy;
import com.l2jfree.gameserver.handler.skills.Extractable;
import com.l2jfree.gameserver.handler.skills.Fishing;
import com.l2jfree.gameserver.handler.skills.FishingSkill;
import com.l2jfree.gameserver.handler.skills.GetPlayer;
import com.l2jfree.gameserver.handler.skills.GiveSp;
import com.l2jfree.gameserver.handler.skills.GiveVitality;
import com.l2jfree.gameserver.handler.skills.Harvest;
import com.l2jfree.gameserver.handler.skills.Heal;
import com.l2jfree.gameserver.handler.skills.InstantJump;
import com.l2jfree.gameserver.handler.skills.LearnSkill;
import com.l2jfree.gameserver.handler.skills.MakeKillable;
import com.l2jfree.gameserver.handler.skills.MakeQuestDropable;
import com.l2jfree.gameserver.handler.skills.ManaHeal;
import com.l2jfree.gameserver.handler.skills.Manadam;
import com.l2jfree.gameserver.handler.skills.Mdam;
import com.l2jfree.gameserver.handler.skills.OpenDoor;
import com.l2jfree.gameserver.handler.skills.Pdam;
import com.l2jfree.gameserver.handler.skills.Recall;
import com.l2jfree.gameserver.handler.skills.Resurrect;
import com.l2jfree.gameserver.handler.skills.ShiftTarget;
import com.l2jfree.gameserver.handler.skills.SiegeFlag;
import com.l2jfree.gameserver.handler.skills.Soul;
import com.l2jfree.gameserver.handler.skills.Sow;
import com.l2jfree.gameserver.handler.skills.Spoil;
import com.l2jfree.gameserver.handler.skills.StrSiegeAssault;
import com.l2jfree.gameserver.handler.skills.SummonFriend;
import com.l2jfree.gameserver.handler.skills.SummonTreasureKey;
import com.l2jfree.gameserver.handler.skills.Sweep;
import com.l2jfree.gameserver.handler.skills.TakeCastle;
import com.l2jfree.gameserver.handler.skills.TakeFort;
import com.l2jfree.gameserver.handler.skills.TransformDispel;
import com.l2jfree.gameserver.handler.skills.Trap;
import com.l2jfree.gameserver.handler.skills.Unlock;
import com.l2jfree.gameserver.handler.skills.ZakenTeleport;
import com.l2jfree.gameserver.model.skills.Formulas;
import com.l2jfree.gameserver.model.skills.L2Skill;
import com.l2jfree.gameserver.model.skills.l2skills.L2SkillDrain;
import com.l2jfree.gameserver.model.skills.templates.L2SkillType;
import com.l2jfree.util.EnumHandlerRegistry;
import com.l2jfree.util.HandlerRegistry;

public final class SkillHandler extends EnumHandlerRegistry<L2SkillType, ISkillHandler>
{
	public static SkillHandler getInstance()
	{
		return SingletonHolder._instance;
	}
	
	private SkillHandler()
	{
		super(L2SkillType.class);
		
		registerSkillHandler(new BalanceLife());
		registerSkillHandler(new BallistaBomb());
		registerSkillHandler(new BeastFeed());
		registerSkillHandler(new ChangeFace());
		registerSkillHandler(new CombatPointHeal());
		registerSkillHandler(new Continuous());
		registerSkillHandler(new CpDam());
		registerSkillHandler(new Craft());
		registerSkillHandler(new DeluxeKey());
		registerSkillHandler(new Detection());
		registerSkillHandler(new Disablers());
		registerSkillHandler(new DrainSoul());
		registerSkillHandler(new Dummy());
		registerSkillHandler(new Extractable());
		registerSkillHandler(new Fishing());
		registerSkillHandler(new FishingSkill());
		registerSkillHandler(new GetPlayer());
		registerSkillHandler(new GiveSp());
		registerSkillHandler(new GiveVitality());
		registerSkillHandler(new Harvest());
		registerSkillHandler(new Heal());
		registerSkillHandler(new InstantJump());
		registerSkillHandler(new LearnSkill());
		registerSkillHandler(new MakeKillable());
		registerSkillHandler(new MakeQuestDropable());
		registerSkillHandler(new ManaHeal());
		registerSkillHandler(new Manadam());
		registerSkillHandler(new Mdam());
		registerSkillHandler(new OpenDoor());
		registerSkillHandler(new Pdam());
		registerSkillHandler(new Recall());
		registerSkillHandler(new Resurrect());
		registerSkillHandler(new ShiftTarget());
		registerSkillHandler(new SiegeFlag());
		registerSkillHandler(new Soul());
		registerSkillHandler(new Sow());
		registerSkillHandler(new Spoil());
		registerSkillHandler(new StrSiegeAssault());
		registerSkillHandler(new SummonFriend());
		registerSkillHandler(new SummonTreasureKey());
		registerSkillHandler(new Sweep());
		registerSkillHandler(new TakeCastle());
		registerSkillHandler(new TakeFort());
		registerSkillHandler(new TransformDispel());
		registerSkillHandler(new Trap());
		registerSkillHandler(new Unlock());
		registerSkillHandler(new ZakenTeleport());
		
		HandlerRegistry._log.info("SkillHandler: Loaded " + size() + " handlers.");
	}
	
	public void registerSkillHandler(ISkillHandler handler)
	{
		registerAll(handler, handler.getSkillIds());
	}
	
	public void useSkill(L2SkillType skillType, L2Creature activeChar, L2Skill skill, L2Creature... targets)
	{
		final ISkillHandler handler = get(skillType);
		
		if (handler != null)
			handler.useSkill(activeChar, skill, targets);
		else
			skill.useSkill(activeChar, targets);
	}
	
	public void useSkill(L2Creature activeChar, L2Skill skill, L2Creature... targets)
	{
		if (activeChar.isAlikeDead())
			return;
		
		useSkill(skill.getSkillType(), activeChar, skill, targets);
		
		for (L2Creature target : targets)
		{
			Formulas.calcLethalHit(activeChar, target, skill);
		}
		
		// Increase Charges, Souls, Etc
		if (activeChar instanceof L2Player)
		{
			((L2Player)activeChar).increaseChargesBySkill(skill);
			((L2Player)activeChar).increaseSoulsBySkill(skill);
		}
		
		skill.getEffectsSelf(activeChar);
		
		if (skill.isSuicideAttack())
			activeChar.doDie(activeChar);
	}
	
	public void useCubicSkill(L2CubicInstance cubic, L2Skill skill, L2Creature... targets)
	{
		final ISkillHandler handler = get(skill.getSkillType());
		
		if (handler instanceof ICubicSkillHandler)
			((ICubicSkillHandler)handler).useCubicSkill(cubic, skill, targets);
		else if (skill instanceof L2SkillDrain)
			((L2SkillDrain)skill).useCubicSkill(cubic, targets);
		else if (handler != null)
			handler.useSkill(cubic.getOwner(), skill, targets);
		else
			skill.useSkill(cubic.getOwner(), targets);
	}
	
	public boolean checkConditions(L2Creature activeChar, L2Skill skill)
	{
		final ISkillHandler handler = get(skill.getSkillType());
		
		if (handler instanceof ISkillConditionChecker)
			return ((ISkillConditionChecker)handler).checkConditions(activeChar, skill);
		
		return true;
	}
	
	public boolean checkConditions(L2Creature activeChar, L2Skill skill, L2Creature target)
	{
		final ISkillHandler handler = get(skill.getSkillType());
		
		if (handler instanceof ISkillConditionChecker)
			return ((ISkillConditionChecker)handler).checkConditions(activeChar, skill, target);
		
		return true;
	}
	
	@SuppressWarnings("synthetic-access")
	private static class SingletonHolder
	{
		protected static final SkillHandler _instance = new SkillHandler();
	}
}
