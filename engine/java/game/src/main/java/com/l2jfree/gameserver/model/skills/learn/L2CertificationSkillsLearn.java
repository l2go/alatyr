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
package com.l2jfree.gameserver.model.skills.learn;

/**
 * @author Psycho(killer1888) / L2jFree
 */

public final class L2CertificationSkillsLearn
{
	private final int _skill_id;
	private final int _item_id;
	private final int _level;
	private final String _name;
	
	public L2CertificationSkillsLearn(int skill_id, int item_id, int level, String name)
	{
		_skill_id = skill_id;
		_item_id = item_id;
		_level = level;
		_name = name.intern();
	}
	
	/**
	 * @return Returns the skill_id.
	 */
	public int getId()
	{
		return _skill_id;
	}
	
	/**
	 * @return Returns the level.
	 */
	public int getLevel()
	{
		return _level;
	}
	
	/**
	 * @return Returns the name.
	 */
	public String getName()
	{
		return _name;
	}
	
	public int getItemId()
	{
		return _item_id;
	}
	
	public static boolean isCertificationSkill(int id)
	{
		switch (id)
		{
			case 631: // Emergent Ability - Attack
			case 632: // Emergent Ability - Defense
			case 633: // Emergent Ability - Empower
			case 634: // Emergent Ability - Magic Defense
			case 637: // Master Ability - Attack
			case 638: // Master Ability - Empower
			case 639: // Master Ability - Casting
			case 640: // Master Ability - Focus
			case 641: // Knight Ability - Boost HP
			case 642: // Enchanter Ability - Boost Mana
			case 643: // Summoner Ability - Boost HP/MP
			case 644: // Rogue Ability - Evasion
			case 645: // Rogue Ability - Long Shot
			case 646: // Wizard Ability - Mana Gain
			case 647: // Enchanter Ability - Mana Recovery
			case 648: // Healer Ability - Prayer
			case 650: // Warrior Ability - Resist Trait
			case 651: // Warrior Ability - Haste
			case 652: // Knight Ability - Defense
			case 653: // Rogue Ability - Critical Chance
			case 654: // Wizard Ability - Mana Steal
			case 655: // Enchanter Ability - Barrier
			case 656: // Transform Divine Warrior
			case 657: // Transform Divine Knight
			case 658: // Transform Divine Rogue
			case 659: // Transform Divine Wizard
			case 660: // Transform Divine Summoner
			case 661: // Transform Divine Healer
			case 662: // Transform Divine Enchanter
			case 799: // Master Ability - Defense
			case 800: // Master Ability - Magic Defense
			case 801: // Warrior Ability - Boost CP
			case 802: // Wizard Ability - Anti-magic
			case 803: // Healer Ability - Divine Protection
			case 804: // Knight Ability - Resist Critical
			case 1489: // Summoner Ability - Resist Attribute
			case 1490: // Healer Ability - Heal
			case 1491: // Summoner Ability - Spirit
				return true;
		}
		return false;
	}
}
