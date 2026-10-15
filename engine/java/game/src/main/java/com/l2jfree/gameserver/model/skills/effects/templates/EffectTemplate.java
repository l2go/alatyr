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
package com.l2jfree.gameserver.model.skills.effects.templates;

import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.HashSet;
import java.util.NoSuchElementException;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.l2jfree.gameserver.model.skills.ChanceCondition;
import com.l2jfree.gameserver.model.skills.Env;
import com.l2jfree.gameserver.model.skills.L2Skill;
import com.l2jfree.gameserver.model.skills.TriggeredSkill;
import com.l2jfree.gameserver.model.skills.effects.AbnormalEffect;
import com.l2jfree.gameserver.model.skills.effects.L2Effect;
import com.l2jfree.gameserver.model.skills.effects.SpecialEffect;
import com.l2jfree.gameserver.model.skills.effects.impl.IEffectMarker;
import com.l2jfree.gameserver.model.skills.funcs.FuncTemplate;
import com.l2jfree.gameserver.model.skills.templates.L2SkillType;
import com.l2jfree.gameserver.templates.StatsSet;

/**
 * @author mkizub
 */
public final class EffectTemplate
{
	private static final Logger _log = LoggerFactory.getLogger(EffectTemplate.class);
	
	private final Constructor<?> _constructor;
	private final Constructor<?> _stolenConstructor;
	
	public final String name;
	public final double lambda;
	public final int count;
	public final int period;
	// Effects in mask format due to merging needs
	public int abnormalEffect;
	public int specialEffect;
	public String[] stackTypes;
	public float stackOrder;
	public boolean showIcon;
	public final double effectPower; // to handle chance
	public final L2SkillType effectType; // to handle resistances etc...
	
	public final TriggeredSkill triggeredSkill;
	public final ChanceCondition chanceCondition;
	
	public FuncTemplate[] funcTemplates;
	
	public EffectTemplate(StatsSet set, L2Skill skill)
	{
		name = set.getString("name");
		lambda = set.getDouble("val", 0);
		count = Math.max(1, set.getInteger("count", 1));
		
		int time = set.getInteger("time", 1) * skill.getTimeMulti();
		
		if (time < 0)
		{
			if (count == 1)
				period = (int)TimeUnit.DAYS.toSeconds(10); // 'infinite' - still in integer range, even in msec
			else
				throw new IllegalStateException("Invalid count (> 1) for effect with infinite duration!");
		}
		else
			period = Math.max(1, time);
		
		if (set.contains("abnormal"))
		{
			final String abnormal = set.getString("abnormal").toLowerCase();
			abnormalEffect = AbnormalEffect.getByName(abnormal).getMask();
		}
		else
			abnormalEffect = 0;
		
		if (set.contains("special"))
		{
			final String special = set.getString("special").toLowerCase();
			specialEffect = SpecialEffect.getByName(special).getMask();
		}
		else
			specialEffect = 0;
		
		stackTypes = set.getString("stackType", skill.generateUniqueStackType()).split(";");
		stackOrder = set.getFloat("stackOrder", skill.generateStackOrder());
		
		for (int i = 0; i < stackTypes.length; i++)
			stackTypes[i] = stackTypes[i].intern();
		
		if (stackTypes.length > 1 && stackOrder != 99)
			throw new IllegalStateException("'stackOrder' should be 99 for merged effects!");
		
		showIcon = set.getInteger("noicon", 0) == 0;
		
		effectPower = set.getDouble("effectPower", -1);
		effectType = set.getEnum("effectType", L2SkillType.class, null);
		
		if ((effectPower == -1) != (effectType == null))
			throw new IllegalArgumentException("Missing effectType/effectPower for effect: " + name);
		
		triggeredSkill = TriggeredSkill.parse(set);
		chanceCondition = ChanceCondition.parse(set);
		
		if ("ChanceSkillTrigger".equals(name))
		{
			if (triggeredSkill == null)
				throw new NoSuchElementException(name + " requires proper TriggeredSkill parameters!");
			
			if (chanceCondition == null)
				throw new NoSuchElementException(name + " requires proper ChanceCondition parameters!");
		}
		else
		{
			if (triggeredSkill != null)
				throw new NoSuchElementException(name + " can't have TriggeredSkill parameters!");
			
			if (chanceCondition != null)
				throw new NoSuchElementException(name + " can't have ChanceCondition parameters!");
		}
		
		try
		{
			final StringBuilder sb = new StringBuilder();
			sb.append(IEffectMarker.class.getPackage().getName());
			sb.append(".Effect");
			sb.append(name);
			final Class<?> clazz = Class.forName(sb.toString());
			
			_constructor = clazz.getConstructor(Env.class, EffectTemplate.class);
			
			Constructor<?> stolenConstructor = null;
			try
			{
				stolenConstructor = clazz.getConstructor(Env.class, L2Effect.class);
			}
			catch (NoSuchMethodException e)
			{
			}
			_stolenConstructor = stolenConstructor;
		}
		catch (Exception e)
		{
			throw new RuntimeException(e);
		}
	}
	
	public L2Effect getEffect(Env env)
	{
		try
		{
			return (L2Effect)_constructor.newInstance(env, this);
		}
		catch (Exception e)
		{
			_log.warn("", e);
		}
		
		return null;
	}
	
	public L2Effect getStolenEffect(Env env, L2Effect stolen)
	{
		try
		{
			if (_stolenConstructor != null)
				return (L2Effect)_stolenConstructor.newInstance(env, stolen);
		}
		catch (Exception e)
		{
			_log.warn("", e);
		}
		
		return null;
	}
	
	public void attach(FuncTemplate f)
	{
		if (funcTemplates == null)
			funcTemplates = new FuncTemplate[1];
		else
			funcTemplates = Arrays.copyOf(funcTemplates, funcTemplates.length + 1);
		
		funcTemplates[funcTemplates.length - 1] = f;
	}
	
	/**
	 * Support for improved buffs, in case it gets overwritten in DP
	 * 
	 * @param skill
	 * @param template
	 * @return
	 */
	public boolean merge(L2Skill skill, EffectTemplate template)
	{
		if (!name.equals(template.name))
			return false;
		
		if (lambda != 0 || template.lambda != 0)
			return false;
		
		if (count != template.count || period != template.period)
			return false;
		
		if (effectPower != template.effectPower || effectType != template.effectType)
			return false;
		
		if (triggeredSkill != null || template.triggeredSkill != null)
			return false;
		
		if (chanceCondition != null || template.chanceCondition != null)
			return false;
		
		abnormalEffect |= template.abnormalEffect;
		specialEffect |= template.specialEffect;
		
		final HashSet<String> tmp = new HashSet<String>();
		
		for (String s : stackTypes)
			tmp.add(s);
		
		for (String s : template.stackTypes)
			tmp.add(s);
		
		stackTypes = tmp.toArray(new String[tmp.size()]);
		stackOrder = 99;
		
		showIcon = showIcon || template.showIcon;
		
		for (FuncTemplate f : template.funcTemplates)
			attach(f);
		
		_log.info("Effect templates merged for " + skill);
		return true;
	}
}
