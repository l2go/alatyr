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
package com.l2jfree.gameserver.gameobjects.view;

/**
 * @author NB4L1
 */
public interface IPlayerView extends ICreatureView
{
	public int getObjectId();
	
	public int getX();
	
	public int getY();
	
	public int getZ();
	
	public int getHeading();
	
	public float getMovementSpeedMultiplier();
	
	public float getAttackSpeedMultiplier();
	
	public double getCollisionRadius();
	
	public double getCollisionHeight();
	
	public int getRunSpd();
	
	public int getWalkSpd();
	
	public int getSwimRunSpd();
	
	public int getSwimWalkSpd();
	
	public int getFlRunSpd();
	
	public int getFlWalkSpd();
	
	public int getFlyRunSpd();
	
	public int getFlyWalkSpd();
	
	// --
	
	public int getPAtkSpd();
	
	public int getMAtkSpd();
	
	// --
	
	public int getCursedWeaponLevel();
	
	public int getTransformationGraphicalId();
	
	public int getNameColor();
	
	public int getTitleColor();
}
