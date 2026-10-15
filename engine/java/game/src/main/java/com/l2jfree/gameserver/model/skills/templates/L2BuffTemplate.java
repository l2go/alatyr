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

import com.l2jfree.gameserver.datatables.SkillTable;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.gameobjects.base.Race;
import com.l2jfree.gameserver.model.skills.L2Skill;
import com.l2jfree.gameserver.templates.StatsSet;

/**
 * This class represents a Buff Template
 * 
 * Author: G1ta0
 */

public class L2BuffTemplate
{
	/** Id of buff template */
	private final int _templateId;
	
	/** Name of the buff template */
	private final String _templateName;
	
	/** Identifier of the skill (buff) to cast */
	private final int _skillId;
	
	/** Order of the skill in template */
	private final int _skillOrder;
	
	private final L2Skill _skill;
	
	/** Level of the skill (buff) to cast */
	private int _skillLevel;
	
	/** Force cast, even if same effect present */
	private final boolean _forceCast;
	
	/** Condition that player must have to obtain this buff */
	/** Min player level */
	private final int _minLevel;
	
	/** Max player level */
	private final int _maxLevel;
	
	/** Player's faction */
	private final int _faction;
	
	/** Players's race */
	private final int _race;
	
	/** Magus/Fighter class of the player */
	private final int _class;
	
	/** Adena price */
	private final int _adena;
	
	/** Faction points price */
	private final int _points;
	
	/**
	 * Constructor of L2BuffTemplat.<BR>
	 * <BR>
	 */
	public L2BuffTemplate(StatsSet set)
	{
		_templateId = set.getInteger("id");
		_templateName = set.getString("name");
		_skillId = set.getInteger("skillId");
		_skillLevel = set.getInteger("skillLevel");
		_skillOrder = set.getInteger("skillOrder");
		
		if (_skillLevel == 0)
			_skillLevel = SkillTable.getInstance().getMaxLevel(_skillId);
		
		_skill = SkillTable.getInstance().getInfo(_skillId, _skillLevel);
		
		_forceCast = (set.getInteger("forceCast") == 1);
		_minLevel = set.getInteger("minLevel");
		_maxLevel = set.getInteger("maxLevel");
		_race = set.getInteger("race");
		_class = set.getInteger("class");
		_faction = set.getInteger("faction");
		_adena = set.getInteger("adena");
		_points = set.getInteger("points");
	}
	
	/**
	 * @return Returns the Id of the buff template
	 */
	public int getId()
	{
		return _templateId;
	}
	
	/**
	 * @return Returns the Name of the buff template
	 */
	public String getName()
	{
		return _templateName;
	}
	
	/**
	 * @return Returns the Id of the buff that the L2Player will receive
	 */
	public int getSkillId()
	{
		return _skillId;
	}
	
	/**
	 * @return Returns the Id of the buff that the L2Player will receive
	 */
	public int getSkillOrder()
	{
		return _skillOrder;
	}
	
	/**
	 * @return Returns the Level of the buff that the L2Player will receive
	 */
	public int getSkillLevel()
	{
		return _skillLevel;
	}
	
	/**
	 * @return Returns the Skill that the L2Player will receive
	 */
	public L2Skill getSkill()
	{
		return _skill;
	}
	
	/**
	 * @return Returns the L2Player minimum level to receive buff
	 */
	public int getMinLevel()
	{
		return _minLevel;
	}
	
	/**
	 * @return Returns the L2Player maximum level to receive buff
	 */
	public int getMaxLevel()
	{
		return _maxLevel;
	}
	
	/**
	 * @return Returns the requirement faction to receive buff
	 */
	public int getFaction()
	{
		return _faction;
	}
	
	/**
	 * @return Returns the price for buff in Adena
	 */
	public int getAdenaPrice()
	{
		return _adena;
	}
	
	/**
	 * @return Returns the price for buff in Faction Points
	 */
	public int getPointsPrice()
	{
		return _points;
	}
	
	/**
	 * @return Is cast animation will be shown
	 */
	public boolean forceCast()
	{
		return _forceCast;
	}
	
	/**
	 * @return Returns the result of level check
	 */
	public boolean checkLevel(L2Player player)
	{
		return ((_minLevel == 0 || player.getLevel() >= _minLevel) && (_maxLevel == 0 || player.getLevel() <= _maxLevel));
	}
	
	/**
	 * @return Returns the result of race check
	 */
	public boolean checkRace(L2Player player)
	{
		boolean cond = false;
		if (_race == 0 || _race == 31)
			return true;
		if ((player.getRace() == Race.Human) && (_race & 16) != 0)
			cond = true;
		if ((player.getRace() == Race.Elf) && (_race & 8) != 0)
			cond = true;
		if ((player.getRace() == Race.Darkelf) && (_race & 4) != 0)
			cond = true;
		if ((player.getRace() == Race.Orc) && (_race & 2) != 0)
			cond = true;
		if ((player.getRace() == Race.Dwarf) && (_race & 1) != 0)
			cond = true;
		return cond;
	}
	
	/**
	 * @return Returns the result of Magus/Fighter class check
	 */
	public boolean checkClass(L2Player player)
	{
		return ((_class == 0 || _class == 3) || (_class == 1 && !player.isMageClass()) || (_class == 2 && player
				.isMageClass()));
	}
	
	/**
	 * @param player
	 * @return Returns the result of faction check
	 */
	public boolean checkFaction(L2Player player)
	{
		return true;
		// return ((_faction == 0 ||player.getFaction = _faction)
	}
	
	/**
	 * @return Returns the result of price check
	 */
	public boolean checkPrice(L2Player player)
	{
		return ((_adena == 0 || player.getInventory().getAdena() >= _adena));
		// return ((_adena == 0 || player.getInventory().getAdena()>=_adena) &&
		// (_points == 0 || (player.getFactionPoints>=_points)));
	}
	
	/**
	 * @return Returns the result of all player related conditions check
	 */
	public boolean checkPlayer(L2Player player)
	{
		return (checkLevel(player) && checkRace(player) && checkClass(player) && checkFaction(player));
	}
	
}
