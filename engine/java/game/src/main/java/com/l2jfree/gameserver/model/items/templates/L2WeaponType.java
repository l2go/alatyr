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

import com.l2jfree.gameserver.model.skills.Stats;

/**
 * @author mkizub <BR>
 *         Description of Weapon Type
 */
public enum L2WeaponType implements AbstractL2ItemType
{
	NONE(1, "Shield", null),
	SWORD(2, "Sword", Stats.SWORD_WPN_VULN),
	BLUNT(3, "Blunt", Stats.BLUNT_WPN_VULN),
	DAGGER(4, "Dagger", Stats.DAGGER_WPN_VULN),
	BOW(5, "Bow", Stats.BOW_WPN_VULN),
	POLE(6, "Pole", Stats.POLE_WPN_VULN),
	ETC(7, "Etc", null),
	FIST(8, "Fist", null),
	DUAL(9, "Dual Sword", Stats.DUAL_WPN_VULN),
	DUALFIST(10, "Dual Fist", Stats.DUALFIST_WPN_VULN),
	BIGSWORD(11, "Big Sword", Stats.BIGSWORD_WPN_VULN),
	PET(12, "Pet", Stats.PET_WPN_VULN),
	ROD(13, "Rod", null),
	BIGBLUNT(14, "Big Blunt", Stats.BLUNT_WPN_VULN),
	ANCIENT_SWORD(15, "Ancient", Stats.ANCIENT_WPN_VULN),
	CROSSBOW(16, "Crossbow", Stats.CROSSBOW_WPN_VULN),
	RAPIER(17, "Rapier", Stats.RAPIER_WPN_VULN),
	DUAL_DAGGER(18, "Dual Daggers", Stats.DUALDAGGER_WPN_VULN);
	
	private final int _mask;
	private final String _name;
	private final Stats _stat;
	
	/**
	 * Constructor of the L2WeaponType.
	 * 
	 * @param id : int designating the ID of the WeaponType
	 * @param name : String designating the name of the WeaponType
	 */
	private L2WeaponType(int id, String name, Stats stat)
	{
		_mask = 1 << id;
		_name = name;
		_stat = stat;
	}
	
	/**
	 * Returns the ID of the item after applying the mask.
	 * 
	 * @return int : ID of the item
	 */
	public int mask()
	{
		return _mask;
	}
	
	/**
	 * Returns the name of the WeaponType
	 * 
	 * @return String
	 */
	@Override
	public String toString()
	{
		return _name;
	}
	
	public Stats getStat()
	{
		return _stat;
	}
	
	public boolean isBowType()
	{
		switch (this)
		{
			case BOW:
			case CROSSBOW:
				return true;
				
			default:
				return false;
		}
	}
	
	public static final L2WeaponType[] VALUES = L2WeaponType.values();
}
