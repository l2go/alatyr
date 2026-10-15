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
package com.l2jfree.gameserver.datatables;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.l2jfree.Config;
import com.l2jfree.L2DatabaseFactory;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.gameobjects.base.ClassId;
import com.l2jfree.gameserver.model.skills.L2Skill;
import com.l2jfree.gameserver.model.skills.effects.L2Effect;
import com.l2jfree.gameserver.model.skills.learn.L2CertificationSkillsLearn;
import com.l2jfree.gameserver.model.skills.learn.L2EnchantSkillLearn;
import com.l2jfree.gameserver.model.skills.learn.L2EnchantSkillLearn.EnchantSkillDetail;
import com.l2jfree.gameserver.model.skills.learn.L2PledgeSkillLearn;
import com.l2jfree.gameserver.model.skills.learn.L2SkillLearn;
import com.l2jfree.gameserver.model.skills.learn.L2TransformSkillLearn;
import com.l2jfree.util.ArrayBunch;
import com.l2jfree.util.L2Collections;

@SuppressWarnings("unchecked")
public class SkillTreeTable
{
	public static final int NORMAL_ENCHANT_COST_MULTIPLIER = 1;
	public static final int SAFE_ENCHANT_COST_MULTIPLIER = 3;
	
	public static final int NORMAL_ENCHANT_BOOK = 6622;
	public static final int SAFE_ENCHANT_BOOK = 9627;
	public static final int CHANGE_ENCHANT_BOOK = 9626;
	public static final int UNTRAIN_ENCHANT_BOOK = 9625;
	
	private final static Logger _log = LoggerFactory.getLogger(SkillTreeTable.class);
	
	private final Map<Integer, L2SkillLearn>[] _skillTrees = new Map[ClassId.values().length];
	private List<L2SkillLearn> _fishingSkillTrees; // all common skills (teached by Fisherman)
	private List<L2SkillLearn> _expandDwarfCraftSkillTrees; // list of special skill for dwarf (expand dwarf craft) learned by class teacher
	private List<L2PledgeSkillLearn> _pledgeSkillTrees; // pledge skill list
	private Map<Integer, L2EnchantSkillLearn> _enchantSkillTrees; // enchant skill list
	private List<L2TransformSkillLearn> _transformSkillTrees; // Transform Skills (Test)
	private List<L2SkillLearn> _specialSkillTrees;
	private List<L2CertificationSkillsLearn> _certificationSkillsTrees; // Special Ability Skills (Hellbound)
	
	public static SkillTreeTable getInstance()
	{
		return SingletonHolder._instance;
	}
	
	/**
	 * Return the minimum level needed to have this Expertise.<BR>
	 * <BR>
	 * 
	 * @param grade The grade level searched
	 */
	public int getExpertiseLevel(int grade)
	{
		// since expertise comes at same level for all classes we use paladin for now
		return Expertise.minimumLevel(getSkillTrees()[ClassId.Paladin.ordinal()], grade);
	}
	
	/**
	 * Each class receives new skill on certain levels, this methods allow the
	 * retrieval of the minimun character level of given class required to learn
	 * a given skill
	 * 
	 * @param skillId The iD of the skill
	 * @param classID The classId of the character
	 * @param skillLvl The SkillLvl
	 * @return The min level
	 */
	public int getMinSkillLevel(int skillId, ClassId classId, int skillLvl)
	{
		Map<Integer, L2SkillLearn> map = getSkillTrees()[classId.ordinal()];
		
		int skillHashCode = SkillTable.getSkillUID(skillId, skillLvl);
		
		if (map.containsKey(skillHashCode))
		{
			return map.get(skillHashCode).getMinLevel();
		}
		
		return 0;
	}
	
	public int getMinSkillLevel(int skillId, int skillLvl)
	{
		int skillHashCode = SkillTable.getSkillUID(skillId, skillLvl);
		
		// Look on all classes for this skill (takes the first one found)
		for (Map<Integer, L2SkillLearn> map : getSkillTrees())
		{
			if (map == null)
				continue;
			
			// checks if the current class has this skill
			if (map.containsKey(skillHashCode))
			{
				return map.get(skillHashCode).getMinLevel();
			}
		}
		return 0;
	}
	
	private SkillTreeTable()
	{
		int classId = 0;
		int count = 0;
		
		Connection con = null;
		
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection(con);
			PreparedStatement statement = con.prepareStatement("SELECT id, COALESCE(parent_class_id, -1) AS parent_id FROM player_class ORDER BY id");
			ResultSet classlist = statement.executeQuery();
			
			Map<Integer, L2SkillLearn> map;
			int parentClassId;
			L2SkillLearn skillLearn;
			
			while (classlist.next())
			{
				map = new LinkedHashMap<Integer, L2SkillLearn>();
				parentClassId = classlist.getInt("parent_id");
				classId = classlist.getInt("id");
				PreparedStatement statement2 =
						con.prepareStatement("SELECT skill_id, skill_level, skill_name, sp, min_level FROM skill_tree WHERE player_class_id = ? ORDER BY skill_id, skill_level");
				statement2.setInt(1, classId);
				ResultSet skilltree = statement2.executeQuery();
				
				if (parentClassId != -1)
				{
					Map<Integer, L2SkillLearn> parentMap = getSkillTrees()[parentClassId];
					map.putAll(parentMap);
				}
				
				int prevSkillId = -1;
				
				while (skilltree.next())
				{
					int id = skilltree.getInt("skill_id");
					int lvl = skilltree.getInt("skill_level");
					String name = skilltree.getString("skill_name");
					int minLvl = skilltree.getInt("min_level");
					int cost = skilltree.getInt("sp");
					
					if (prevSkillId != id)
						prevSkillId = id;
					
					skillLearn = new L2SkillLearn(id, lvl, minLvl, name, cost, 0, 0);
					map.put(SkillTable.getSkillUID(id, lvl), skillLearn);
				}
				
				getSkillTrees()[classId] = map;
				skilltree.close();
				statement2.close();
				
				count += map.size();
				if (_log.isDebugEnabled())
					_log.info("SkillTreeTable: skill tree for class " + classId + " has " + map.size() + " skills");
			}
			
			classlist.close();
			statement.close();
		}
		catch (Exception e)
		{
			_log.error("Error while creating skill tree (Class ID " + classId + "):", e);
		}
		
		_log.info("SkillTreeTable:               Loaded " + count + " skills.");
		if (getExpertiseLevel(1) < 0)
			_log.info("World has no expertise");
		
		//Skill tree for fishing skill (from Fisherman)
		int count2 = 0;
		int count3 = 0;
		
		try
		{
			_fishingSkillTrees = new ArrayList<L2SkillLearn>();
			_expandDwarfCraftSkillTrees = new ArrayList<L2SkillLearn>();
			
			con = L2DatabaseFactory.getInstance().getConnection(con);
			PreparedStatement statement =
					con.prepareStatement("SELECT skill_id, skill_level, skill_name, sp, min_level, cost_item_template_id, cost_item_count, is_dwarven_craft FROM fishing_skill_tree ORDER BY skill_id, skill_level");
			ResultSet skilltree2 = statement.executeQuery();
			
			int prevSkillId = -1;
			
			while (skilltree2.next())
			{
				int id = skilltree2.getInt("skill_id");
				int lvl = skilltree2.getInt("skill_level");
				String name = skilltree2.getString("skill_name");
				int minLvl = skilltree2.getInt("min_level");
				int cost = skilltree2.getInt("sp");
				int costId = skilltree2.getInt("cost_item_template_id");
				int costCount = skilltree2.getInt("cost_item_count");
				boolean isDwarven = skilltree2.getBoolean("is_dwarven_craft");
				
				if (prevSkillId != id)
					prevSkillId = id;
				
				L2SkillLearn skill = new L2SkillLearn(id, lvl, minLvl, name, cost, costId, costCount);
				
				if (!isDwarven)
					_fishingSkillTrees.add(skill);
				else
					_expandDwarfCraftSkillTrees.add(skill);
			}
			
			skilltree2.close();
			statement.close();
			
			count2 = _fishingSkillTrees.size();
			count3 = _expandDwarfCraftSkillTrees.size();
		}
		catch (Exception e)
		{
			_log.error("Error while creating fishing skill table: ", e);
		}
		
		int count4 = 0;
		try
		{
			_enchantSkillTrees = new LinkedHashMap<Integer, L2EnchantSkillLearn>();
			
			con = L2DatabaseFactory.getInstance().getConnection(con);
			PreparedStatement statement =
					con.prepareStatement("SELECT skill_id, level, base_level, sp, min_skill_level, exp, success_rate_76, success_rate_77, success_rate_78, success_rate_79, success_rate_80, success_rate_81, success_rate_82, success_rate_83, success_rate_84, success_rate_85 FROM enchant_skill_tree ORDER BY skill_id, level");
			ResultSet skilltree3 = statement.executeQuery();
			
			int prevSkillId = -1;
			
			while (skilltree3.next())
			{
				int id = skilltree3.getInt("skill_id");
				int lvl = skilltree3.getInt("level");
				int baseLvl = skilltree3.getInt("base_level");
				int minSkillLvl = skilltree3.getInt("min_skill_level");
				int sp = skilltree3.getInt("sp");
				int exp = skilltree3.getInt("exp");
				byte rate76 = skilltree3.getByte("success_rate_76");
				byte rate77 = skilltree3.getByte("success_rate_77");
				byte rate78 = skilltree3.getByte("success_rate_78");
				byte rate79 = skilltree3.getByte("success_rate_79");
				byte rate80 = skilltree3.getByte("success_rate_80");
				byte rate81 = skilltree3.getByte("success_rate_81");
				byte rate82 = skilltree3.getByte("success_rate_82");
				byte rate83 = skilltree3.getByte("success_rate_83");
				byte rate84 = skilltree3.getByte("success_rate_84");
				byte rate85 = skilltree3.getByte("success_rate_85");
				
				if (prevSkillId != id)
					prevSkillId = id;
				
				L2EnchantSkillLearn skill = _enchantSkillTrees.get(id);
				if (skill == null)
				{
					skill = new L2EnchantSkillLearn(id, baseLvl);
					_enchantSkillTrees.put(id, skill);
				}
				EnchantSkillDetail esd =
						new EnchantSkillDetail(lvl, minSkillLvl, sp, exp, rate76, rate77, rate78, rate79, rate80,
								rate81, rate82, rate83, rate84, rate85);
				skill.addEnchantDetail(esd);
			}
			
			skilltree3.close();
			statement.close();
			
			count4 = _enchantSkillTrees.size();
		}
		catch (Exception e)
		{
			_log.error("Error while creating enchant skill table: ", e);
		}
		
		int count5 = 0;
		try
		{
			_pledgeSkillTrees = new ArrayList<L2PledgeSkillLearn>();
			
			con = L2DatabaseFactory.getInstance().getConnection(con);
			PreparedStatement statement =
					con.prepareStatement("SELECT skill_id, skill_level, skill_name, min_clan_level, reputation_cost, cost_item_template_id, cost_item_count FROM clan_skill_tree ORDER BY skill_id, skill_level");
			ResultSet skilltree4 = statement.executeQuery();
			
			int prevSkillId = -1;
			
			while (skilltree4.next())
			{
				int id = skilltree4.getInt("skill_id");
				int lvl = skilltree4.getInt("skill_level");
				String name = skilltree4.getString("skill_name");
				int baseLvl = skilltree4.getInt("min_clan_level");
				int sp = skilltree4.getInt("reputation_cost");
				int itemId = skilltree4.getInt("cost_item_template_id");
				long itemCount = skilltree4.getLong("cost_item_count");
				
				if (prevSkillId != id)
					prevSkillId = id;
				
				L2PledgeSkillLearn skill = new L2PledgeSkillLearn(id, lvl, baseLvl, name, sp, itemId, itemCount);
				
				_pledgeSkillTrees.add(skill);
			}
			
			skilltree4.close();
			statement.close();
			
			count5 = _pledgeSkillTrees.size();
		}
		catch (Exception e)
		{
			_log.error("Error while creating pledge skill table: ", e);
		}
		
		int count6 = 0;
		try
		{
			_transformSkillTrees = new ArrayList<L2TransformSkillLearn>();
			
			con = L2DatabaseFactory.getInstance().getConnection(con);
			PreparedStatement statement =
					con.prepareStatement("SELECT COALESCE(race_id, -1) AS race_id, skill_id, required_item_template_id, skill_level, skill_name, sp, min_level FROM transform_skill_tree ORDER BY race_id NULLS FIRST, skill_id, skill_level");
			ResultSet skilltree5 = statement.executeQuery();
			
			int prevSkillId = -1;
			
			while (skilltree5.next())
			{
				int race_id = skilltree5.getInt("race_id");
				int skill_id = skilltree5.getInt("skill_id");
				int item_id = skilltree5.getInt("required_item_template_id");
				int level = skilltree5.getInt("skill_level");
				String name = skilltree5.getString("skill_name");
				int sp = skilltree5.getInt("sp");
				int min_level = skilltree5.getInt("min_level");
				
				if (prevSkillId != skill_id)
					prevSkillId = skill_id;
				
				L2TransformSkillLearn skill =
						new L2TransformSkillLearn(race_id, skill_id, item_id, level, name, sp, min_level);
				
				_transformSkillTrees.add(skill);
			}
			
			skilltree5.close();
			statement.close();
			
			count6 = _transformSkillTrees.size();
		}
		catch (Exception e)
		{
			_log.error("Error while creating Transformation skill table ", e);
		}
		
		int count7 = 0;
		try
		{
			_specialSkillTrees = new ArrayList<L2SkillLearn>();
			
			con = L2DatabaseFactory.getInstance().getConnection(con);
			PreparedStatement statement =
					con.prepareStatement("SELECT skill_id, skill_level, skill_name, cost_item_template_id, cost_item_count FROM special_skill_tree ORDER BY skill_id, skill_level");
			ResultSet skilltree6 = statement.executeQuery();
			
			int prevSkillId = -1;
			
			while (skilltree6.next())
			{
				int id = skilltree6.getInt("skill_id");
				int lvl = skilltree6.getInt("skill_level");
				String name = skilltree6.getString("skill_name");
				int costId = skilltree6.getInt("cost_item_template_id");
				int costCount = skilltree6.getInt("cost_item_count");
				
				if (prevSkillId != id)
					prevSkillId = id;
				
				L2SkillLearn skill = new L2SkillLearn(id, lvl, 0, name, 0, costId, costCount);
				
				_specialSkillTrees.add(skill);
			}
			
			skilltree6.close();
			statement.close();
			
			count7 = _specialSkillTrees.size();
		}
		catch (Exception e)
		{
			_log.error("Error while creating SpecialSkillTree skill table ", e);
		}
		int count8 = 0;
		try
		{
			_certificationSkillsTrees = new ArrayList<L2CertificationSkillsLearn>();
			
			con = L2DatabaseFactory.getInstance().getConnection(con);
			PreparedStatement statement =
					con.prepareStatement("SELECT skill_id, required_item_template_id, skill_level, skill_name FROM certification_skill_tree ORDER BY skill_id, skill_level");
			ResultSet skilltree6 = statement.executeQuery();
			
			int prevSkillId = -1;
			
			while (skilltree6.next())
			{
				int skill_id = skilltree6.getInt("skill_id");
				int item_id = skilltree6.getInt("required_item_template_id");
				int level = skilltree6.getInt("skill_level");
				String name = skilltree6.getString("skill_name");
				
				if (prevSkillId != skill_id)
					prevSkillId = skill_id;
				
				L2CertificationSkillsLearn skill = new L2CertificationSkillsLearn(skill_id, item_id, level, name);
				
				_certificationSkillsTrees.add(skill);
			}
			
			skilltree6.close();
			statement.close();
			
			count8 = _certificationSkillsTrees.size();
		}
		catch (Exception e)
		{
			_log.error("Error while creating Certification skill table ", e);
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
		
		_log.info("FishingSkillTreeTable:        Loaded " + count2 + " general skills.");
		_log.info("DwarvenSkillTreeTable:        Loaded " + count3 + " dwarven skills.");
		_log.info("EnchantSkillTreeTable:        Loaded " + count4 + " enchant skills.");
		_log.info("PledgeSkillTreeTable:         Loaded " + count5 + " pledge skills.");
		_log.info("TransformSkillTreeTable:      Loaded " + count6 + " transform skills.");
		_log.info("SpecialSkillTreeTable:        Loaded " + count7 + " special skills");
		_log.info("CertificationSkillsTreeTable: Loaded " + count8 + " certification skills.");
	}
	
	private Map<Integer, L2SkillLearn>[] getSkillTrees()
	{
		return _skillTrees;
	}
	
	public L2SkillLearn[] getAvailableSkills(L2Player cha, ClassId classId)
	{
		ArrayBunch<L2SkillLearn> result = new ArrayBunch<L2SkillLearn>();
		Collection<L2SkillLearn> skills = getAllowedSkills(classId);
		
		if (skills == null)
		{
			// the skilltree for this class is undefined, so we give an empty list
			_log.warn("Skilltree for class " + classId + " is not defined !");
			return new L2SkillLearn[0];
		}
		
		L2Skill[] oldSkills = cha.getAllSkills();
		
		for (L2SkillLearn temp : skills)
		{
			if (temp.getMinLevel() <= cha.getLevel())
			{
				boolean knownSkill = false;
				
				for (int j = 0; j < oldSkills.length && !knownSkill; j++)
				{
					if (oldSkills[j].getId() == temp.getId())
					{
						knownSkill = true;
						
						if (oldSkills[j].getLevel() == temp.getLevel() - 1)
						{
							// this is the next level of a skill that we know
							result.add(temp);
						}
					}
				}
				
				if (!knownSkill && temp.getLevel() == 1)
				{
					// this is a new skill
					result.add(temp);
				}
			}
		}
		
		return result.moveToArray(new L2SkillLearn[result.size()]);
	}
	
	public String giveAvailableSkills(L2Player activeChar)
	{
		Map<Integer, L2Skill> skillsToAdd = new HashMap<Integer, L2Skill>();
		
		for (L2SkillLearn temp : getAllowedSkills(activeChar.getClassId()))
		{
			if (temp.getMinLevel() > activeChar.getLevel())
				continue;
			
			if (temp.getId() == L2Skill.SKILL_EXPERTISE)
				continue;
			
			if (temp.getId() == L2Skill.SKILL_LUCKY)
				continue;
			
			if (temp.getId() == L2Skill.SKILL_DIVINE_INSPIRATION && !Config.ALT_AUTO_LEARN_DIVINE_INSPIRATION)
				continue;
			
			L2Skill knownSkill = activeChar.getKnownSkill(temp.getId());
			
			if (knownSkill != null && knownSkill.getLevel() >= temp.getLevel())
				continue;
			
			L2Skill mappedSkill = skillsToAdd.get(temp.getId());
			
			if (mappedSkill != null && mappedSkill.getLevel() >= temp.getLevel())
				continue;
			
			L2Skill skill = SkillTable.getInstance().getInfo(temp.getId(), temp.getLevel());
			
			if (skill == null)
				continue;
			
			skillsToAdd.put(temp.getId(), skill);
		}
		
		long skillsAdded = 0;
		long newSkillsAdded = 0;
		
		for (L2Skill skill : skillsToAdd.values())
		{
			skillsAdded++;
			
			if (!activeChar.hasSkill(skill.getId()))
				newSkillsAdded++;
			
			// fix when learning toggle skills
			if (skill.isToggle())
			{
				L2Effect toggleEffect = activeChar.getFirstEffect(skill.getId());
				if (toggleEffect != null)
				{
					// stop old toggle skill effect, and give new toggle skill effect back
					toggleEffect.exit();
					skill.getEffects(activeChar, activeChar);
				}
			}
			
			activeChar.addSkill(skill, true);
		}
		
		return skillsAdded + " (" + newSkillsAdded + " new) skill(s)";
	}
	
	public L2SkillLearn[] getAvailableSpecialSkills(L2Player cha)
	{
		ArrayBunch<L2SkillLearn> result = new ArrayBunch<L2SkillLearn>();
		
		L2Skill[] oldSkills = cha.getAllSkills();
		
		for (L2SkillLearn temp : _specialSkillTrees)
		{
			boolean knownSkill = false;
			
			for (int j = 0; j < oldSkills.length && !knownSkill; j++)
			{
				if (oldSkills[j].getId() == temp.getId())
				{
					knownSkill = true;
					
					if (oldSkills[j].getLevel() == temp.getLevel() - 1)
					{
						// this is the next level of a skill that we know
						result.add(temp);
					}
				}
			}
			
			if (!knownSkill && temp.getLevel() == 1)
			{
				// this is a new skill
				result.add(temp);
			}
		}
		
		return result.moveToArray(new L2SkillLearn[result.size()]);
	}
	
	public L2SkillLearn[] getAvailableFishingSkills(L2Player cha)
	{
		ArrayBunch<L2SkillLearn> result = new ArrayBunch<L2SkillLearn>();
		
		//if (skills == null)
		//{
		//    // the skilltree for this class is undefined, so we give an empty list
		//    _log.warn("Skilltree for fishing is not defined !");
		//    return new L2SkillLearn[0];
		//}
		
		Iterable<L2SkillLearn> iterable =
				cha.hasDwarvenCraft() ? L2Collections.concatenatedIterable(_fishingSkillTrees,
						_expandDwarfCraftSkillTrees) : _fishingSkillTrees;
		
		L2Skill[] oldSkills = cha.getAllSkills();
		
		for (L2SkillLearn temp : iterable)
		{
			if (temp.getMinLevel() <= cha.getLevel())
			{
				boolean knownSkill = false;
				
				for (int j = 0; j < oldSkills.length && !knownSkill; j++)
				{
					if (oldSkills[j].getId() == temp.getId())
					{
						knownSkill = true;
						
						if (oldSkills[j].getLevel() == temp.getLevel() - 1)
						{
							// this is the next level of a skill that we know
							result.add(temp);
						}
					}
				}
				
				if (!knownSkill && temp.getLevel() == 1)
				{
					// this is a new skill
					result.add(temp);
				}
			}
		}
		
		return result.moveToArray(new L2SkillLearn[result.size()]);
	}
	
	public L2EnchantSkillLearn getSkillEnchantmentForSkill(L2Skill skill)
	{
		L2EnchantSkillLearn esl = getSkillEnchantmentBySkillId(skill.getId());
		// there is enchantment for this skill and we have the required level of it
		if (esl != null && skill.getLevel() >= esl.getBaseLevel())
		{
			return esl;
		}
		return null;
	}
	
	public L2EnchantSkillLearn getSkillEnchantmentBySkillId(int skillId)
	{
		return _enchantSkillTrees.get(skillId);
	}
	
	public Collection<L2EnchantSkillLearn> getSkillEnchantments()
	{
		return _enchantSkillTrees.values();
	}
	
	public L2PledgeSkillLearn[] getAvailablePledgeSkills(L2Player cha)
	{
		ArrayBunch<L2PledgeSkillLearn> result = new ArrayBunch<L2PledgeSkillLearn>();
		List<L2PledgeSkillLearn> skills = _pledgeSkillTrees;
		
		if (skills == null)
		{
			// the skilltree for this class is undefined, so we give an empty list
			_log.warn("No clan skills defined!");
			return new L2PledgeSkillLearn[0];
		}
		
		L2Skill[] oldSkills = cha.getClan().getAllSkills();
		
		for (L2PledgeSkillLearn temp : skills)
		{
			if (temp.getBaseLevel() <= cha.getClan().getLevel())
			{
				boolean knownSkill = false;
				
				for (int j = 0; j < oldSkills.length && !knownSkill; j++)
				{
					if (oldSkills[j].getId() == temp.getId())
					{
						knownSkill = true;
						if (oldSkills[j].getLevel() == temp.getLevel() - 1)
						{
							// this is the next level of a skill that we know
							result.add(temp);
						}
					}
				}
				
				if (!knownSkill && temp.getLevel() == 1)
				{
					// this is a new skill
					result.add(temp);
				}
			}
		}
		return result.moveToArray(new L2PledgeSkillLearn[result.size()]);
	}
	
	public L2TransformSkillLearn[] getAvailableTransformSkills(L2Player cha)
	{
		ArrayBunch<L2TransformSkillLearn> result = new ArrayBunch<L2TransformSkillLearn>();
		List<L2TransformSkillLearn> skills = _transformSkillTrees;
		
		if (skills == null)
		{
			// the skilltree for this class is undefined, so we give an empty list
			
			_log.warn("No Transform skills defined!");
			return new L2TransformSkillLearn[0];
		}
		
		L2Skill[] oldSkills = cha.getAllSkills();
		
		for (L2TransformSkillLearn temp : skills)
		{
			if (temp.getMinLevel() <= cha.getLevel()
					&& (temp.getRace() == cha.getRace().ordinal() || temp.getRace() == -1))
			{
				boolean knownSkill = false;
				
				for (int j = 0; j < oldSkills.length && !knownSkill; j++)
				{
					if (oldSkills[j].getId() == temp.getId())
					{
						knownSkill = true;
						
						if (oldSkills[j].getLevel() == temp.getLevel() - 1)
						{
							// this is the next level of a skill that we know
							result.add(temp);
						}
					}
				}
				
				if (!knownSkill && temp.getLevel() == 1)
				{
					// this is a new skill
					result.add(temp);
				}
			}
		}
		
		return result.moveToArray(new L2TransformSkillLearn[result.size()]);
	}
	
	public L2CertificationSkillsLearn[] getAvailableCertificationSkills(L2Player cha)
	{
		ArrayBunch<L2CertificationSkillsLearn> result = new ArrayBunch<L2CertificationSkillsLearn>();
		List<L2CertificationSkillsLearn> skills = _certificationSkillsTrees;
		
		if (skills == null)
		{
			// the skilltree for this class is undefined, so we give an empty list
			
			_log.warn("No certification skills defined!");
			return new L2CertificationSkillsLearn[0];
		}
		
		if (cha.isSubClassActive())
			return new L2CertificationSkillsLearn[0];
		
		L2Skill[] oldSkills = cha.getAllSkills();
		
		for (L2CertificationSkillsLearn temp : skills)
		{
			boolean knownSkill = false;
			
			for (int j = 0; j < oldSkills.length && !knownSkill; j++)
			{
				if (oldSkills[j].getId() == temp.getId()
						&& cha.getInventory().getItemByItemId(temp.getItemId()) != null)
				{
					knownSkill = true;
					
					if (oldSkills[j].getLevel() == temp.getLevel() - 1)
					{
						// this is the next level of a skill that we know
						result.add(temp);
					}
				}
			}
			
			if (!knownSkill && temp.getLevel() == 1 && cha.getInventory().getItemByItemId(temp.getItemId()) != null)
			{
				// this is a new skill
				result.add(temp);
			}
		}
		
		return result.moveToArray(new L2CertificationSkillsLearn[result.size()]);
	}
	
	/**
	 * Returns all allowed skills for a given class.
	 * 
	 * @param classId
	 * @return all allowed skills for a given class.
	 */
	public Collection<L2SkillLearn> getAllowedSkills(ClassId classId)
	{
		Map<Integer, L2SkillLearn> tree = getSkillTrees()[classId.ordinal()];
		if (tree == null)
			return List.of();
		return tree.values();
	}
	
	public Set<Integer> getAllowedSkillUIDs(ClassId classId)
	{
		return getSkillTrees()[classId.ordinal()].keySet();
	}
	
	public int getMinLevelForNewSkill(L2Player cha, ClassId classId)
	{
		int minLevel = 0;
		Collection<L2SkillLearn> skills = getAllowedSkills(classId);
		
		if (skills == null)
		{
			// the skilltree for this class is undefined, so we give an empty list
			_log.warn("Skilltree for class " + classId + " is not defined !");
			return minLevel;
		}
		
		for (L2SkillLearn temp : skills)
		{
			if (temp.getMinLevel() > cha.getLevel() && temp.getSpCost() != 0)
				if (minLevel == 0 || temp.getMinLevel() < minLevel)
					minLevel = temp.getMinLevel();
		}
		
		return minLevel;
	}
	
	public int getMinLevelForNewFishingSkill(L2Player cha)
	{
		int minLevel = 0;
		List<L2SkillLearn> skills = new ArrayList<L2SkillLearn>();
		
		skills.addAll(_fishingSkillTrees);
		
		//if (skills == null)
		//{
		//    // the skilltree for this class is undefined, so we give an empty list
		//    _log.warn("SkillTree for fishing is not defined !");
		//    return minLevel;
		//}
		
		if (cha.hasDwarvenCraft() && _expandDwarfCraftSkillTrees != null)
		{
			skills.addAll(_expandDwarfCraftSkillTrees);
		}
		
		for (L2SkillLearn s : skills)
		{
			if (s.getMinLevel() > cha.getLevel())
				if (minLevel == 0 || s.getMinLevel() < minLevel)
					minLevel = s.getMinLevel();
		}
		
		return minLevel;
	}
	
	public int getMinLevelForNewTransformSkill(L2Player cha)
	{
		int minLevel = 0;
		List<L2TransformSkillLearn> skills = new ArrayList<L2TransformSkillLearn>();
		
		skills.addAll(_transformSkillTrees);
		
		if (skills.size() == 0)
		{
			// the skilltree for this class is undefined, so we give an empty list
			_log.warn("SkillTree for transformation is not defined !");
			return minLevel;
		}
		
		for (L2TransformSkillLearn s : skills)
		{
			if ((s.getMinLevel() > cha.getLevel()) && (s.getRace() == cha.getRace().ordinal()))
				if (minLevel == 0 || s.getMinLevel() < minLevel)
					minLevel = s.getMinLevel();
		}
		
		return minLevel;
	}
	
	public int getSkillCost(L2Player player, L2Skill skill)
	{
		int skillCost = 100000000;
		ClassId classId = player.getSkillLearningClassId();
		int skillHashCode = SkillTable.getSkillUID(skill);
		
		if (getSkillTrees()[classId.ordinal()].containsKey(skillHashCode))
		{
			L2SkillLearn skillLearn = getSkillTrees()[classId.ordinal()].get(skillHashCode);
			if (skillLearn.getMinLevel() <= player.getLevel())
			{
				skillCost = skillLearn.getSpCost();
				if (!player.getClassId().equalsOrChildOf(classId))
					return skillCost;
			}
		}
		
		return skillCost;
	}
	
	public int getEnchantSkillSpCost(L2Skill skill)
	{
		L2EnchantSkillLearn enchantSkillLearn = _enchantSkillTrees.get(skill.getId());
		if (enchantSkillLearn != null)
		{
			EnchantSkillDetail esd = enchantSkillLearn.getEnchantSkillDetail(skill.getLevel());
			if (esd != null)
			{
				return esd.getSpCost();
			}
		}
		
		return Integer.MAX_VALUE;
	}
	
	public int getEnchantSkillExpCost(L2Skill skill)
	{
		L2EnchantSkillLearn enchantSkillLearn = _enchantSkillTrees.get(skill.getId());
		if (enchantSkillLearn != null)
		{
			EnchantSkillDetail esd = enchantSkillLearn.getEnchantSkillDetail(skill.getLevel());
			if (esd != null)
			{
				return esd.getExp();
			}
		}
		
		return Integer.MAX_VALUE;
	}
	
	public byte getEnchantSkillRate(L2Player player, L2Skill skill)
	{
		L2EnchantSkillLearn enchantSkillLearn = _enchantSkillTrees.get(skill.getId());
		if (enchantSkillLearn != null)
		{
			EnchantSkillDetail esd = enchantSkillLearn.getEnchantSkillDetail(skill.getLevel());
			if (esd != null)
			{
				return esd.getRate(player);
			}
		}
		
		return 0;
	}
	
	@SuppressWarnings("synthetic-access")
	private static class SingletonHolder
	{
		protected static final SkillTreeTable _instance = new SkillTreeTable();
	}
}
