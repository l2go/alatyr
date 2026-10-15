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

import com.l2jfree.gameserver.gameobjects.L2Creature;

/**
 * An Env object is just a class to pass parameters to a calculator such as L2Player, L2ItemInstance, Initial value.
 */
public final class Env
{
	public L2Creature player;
	//	Disabled until it's really used...
	//	public L2CubicInstance	cubic;
	public L2Creature target;
	//	Disabled until it's really used...
	//	public L2ItemInstance	item;
	public L2Skill skill;
	//	Disabled until it's really used...
	//	public L2Effect			effect;
	public double value;
	public double baseValue = Double.NaN;
	public boolean skillMastery;
	
	public Env()
	{
	}
}
