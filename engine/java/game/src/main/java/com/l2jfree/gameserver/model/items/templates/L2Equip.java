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
package com.l2jfree.gameserver.model.items.templates;


import java.util.ArrayList;
import java.util.List;
import org.apache.commons.lang3.ArrayUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.l2jfree.gameserver.datatables.SkillTable;
import com.l2jfree.gameserver.model.skills.L2Skill;
import com.l2jfree.gameserver.model.skills.funcs.FuncTemplate;
import com.l2jfree.gameserver.templates.StatsSet;

public abstract class L2Equip extends L2Item
{
	protected static final Logger _log = LoggerFactory.getLogger(L2Equip.class);
	private L2Skill[] _itemSkills = null;
	private L2Skill[] _enchant4Skills = null; // skill that activates when item is enchanted +4 (for duals)
	
	// TODO: Replace by chance skills
	public static class WeaponSkill
	{
		public L2Skill skill;
		public int chance;
	}
	
	public L2Equip(AbstractL2ItemType type, StatsSet set)
	{
		super(type, set);
		
		String[] itemSkillDefs = set.getString("skills_item").split(";");
		String[] enchant4SkillDefs = set.getString("enchant4_skill").split(";");
		
		List<L2Skill> itemSkills = null;
		List<L2Skill> enchant4Skills = null;
		
		// Item skills
		if (itemSkillDefs != null && itemSkillDefs.length > 0)
		{
			itemSkills = parseSkills(itemSkillDefs, "item", (type instanceof L2ArmorType) ? "armor" : "weapon");
		}
		
		// Enchant4 skills
		if (enchant4SkillDefs != null && enchant4SkillDefs.length > 0)
		{
			enchant4Skills =
					parseSkills(enchant4SkillDefs, "enchant4", (type instanceof L2ArmorType) ? "armor" : "weapon");
		}
		
		if (itemSkills != null)
			_itemSkills = itemSkills.toArray(new L2Skill[itemSkills.size()]);
		
		if (enchant4Skills != null && !enchant4Skills.isEmpty())
			_enchant4Skills = enchant4Skills.toArray(new L2Skill[enchant4Skills.size()]);
	}
	
	protected List<Integer> parseRestriction(String[] from, String restrictType, String itemType)
	{
		List<Integer> values = null;
		for (String strVal : from)
		{
			int intVal = -1;
			try
			{
				intVal = Integer.parseInt(strVal);
			}
			catch (Exception e)
			{
				_log.error("Cannot parse " + restrictType + " restriction \"" + strVal + "\" for " + itemType + " "
						+ getItemId(), e);
				continue;
			}
			
			if (intVal < 0)
				continue;
			
			if (values == null)
				values = new ArrayList<Integer>();
			values.add(intVal);
		}
		return values;
	}
	
	protected List<L2Skill> parseSkills(String[] from, String skillType, String itemType)
	{
		List<L2Skill> itemSkills = null;
		for (String skillStr : from)
		{
			if (skillStr.length() == 0)
				continue;
			
			int skillId = 0;
			int skillLevel = 0;
			L2Skill skill = null;
			try
			{
				String[] skillDef = skillStr.split("-");
				skillId = Integer.parseInt(skillDef[0]);
				skillLevel = Integer.parseInt(skillDef[1]);
			}
			catch (Exception e)
			{
				_log.error("Cannot parse " + skillType + " skill \"" + skillStr + "\" for " + itemType + " item "
						+ getItemId(), e);
				continue;
			}
			
			skill = SkillTable.getInstance().getInfo(skillId, skillLevel);
			if (skill == null)
			{
				_log.error("Cannot find " + skillType + " skill (" + skillId + "," + skillLevel + ") for " + itemType
						+ " item " + getItemId());
			}
			else
			{
				if (itemSkills == null)
					itemSkills = new ArrayList<L2Skill>();
				itemSkills.add(skill);
			}
		}
		return itemSkills;
	}
	
	protected List<WeaponSkill> parseChanceSkills(String[] from, String skillType, String itemType)
	{
		List<WeaponSkill> itemSkills = null;
		for (String skillStr : from)
		{
			if (skillStr.length() == 0)
				continue;
			
			int skillId = 0;
			int skillLevel = 0;
			int chance = 0;
			L2Skill skill = null;
			try
			{
				String[] skillDef = skillStr.split("-");
				skillId = Integer.parseInt(skillDef[0]);
				skillLevel = Integer.parseInt(skillDef[1]);
				chance = Integer.parseInt(skillDef[2]);
			}
			catch (Exception e)
			{
				_log.error("Cannot parse " + skillType + " skill \"" + skillStr + "\" for " + itemType + " item "
						+ getItemId(), e);
				continue;
			}
			
			skill = SkillTable.getInstance().getInfo(skillId, skillLevel);
			if (skill == null)
			{
				_log.error("Cannot find " + skillType + " skill (" + skillId + "," + skillLevel + ") for " + itemType
						+ " item " + getItemId());
			}
			else
			{
				//skill.attach(new ConditionGameChance(chance), true);
				if (itemSkills == null)
					itemSkills = new ArrayList<WeaponSkill>();
				WeaponSkill ws = new WeaponSkill();
				ws.skill = skill;
				ws.chance = chance;
				itemSkills.add(ws);
			}
		}
		return itemSkills;
	}
	
	/**
	* Returns passive skills linked to that item
	* @return
	*/
	public L2Skill[] getSkills()
	{
		return _itemSkills;
	}
	
	/**
	 * Returns skill that player get when has equiped item +4 or more
	 * 
	 * @return
	 */
	public L2Skill[] getEnchant4Skills()
	{
		return _enchant4Skills;
	}
	
	public abstract int getItemMask();
	
	private FuncTemplate[] _funcTemplates;
	
	public FuncTemplate[] getFuncTemplates()
	{
		return _funcTemplates;
	}
	
	/**
	 * Add the FuncTemplate f to the list of functions used with the item
	 * @param f : FuncTemplate to add
	 */
	public void attach(FuncTemplate f)
	{
		if (_funcTemplates == null)
			_funcTemplates = new FuncTemplate[] { f };
		else
			_funcTemplates = ArrayUtils.add(_funcTemplates, f);
	}
}
