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

import com.l2jfree.gameserver.gameobjects.L2Decoy;
import com.l2jfree.gameserver.gameobjects.position.ObjectPosition;

/**
 * @author NB4L1
 */
public final class DecoyView extends CreatureView<L2Decoy> implements IPlayerView
{
	public DecoyView(L2Decoy activeChar)
	{
		super(activeChar);
	}
	
	@Override
	protected void refreshImpl()
	{
		super.refreshImpl();
		
		_ownerView = _activeChar.getOwner().getView();
		_ownerView.refresh();
		
		final ObjectPosition position = _activeChar.getPosition();
		
		_objectId = _activeChar.getObjectId();
		_x = position.getX();
		_y = position.getY();
		_z = position.getZ();
	}
	
	private IPlayerView _ownerView;
	
	private int _objectId;
	private int _x;
	private int _y;
	private int _z;
	
	@Override
	public int getObjectId()
	{
		return _objectId;
	}
	
	@Override
	public int getX()
	{
		return _x;
	}
	
	@Override
	public int getY()
	{
		return _y;
	}
	
	@Override
	public int getZ()
	{
		return _z;
	}
	
	@Override
	public int getHeading()
	{
		return _ownerView.getHeading();
	}
	
	@Override
	public float getMovementSpeedMultiplier()
	{
		return _ownerView.getMovementSpeedMultiplier();
	}
	
	@Override
	public float getAttackSpeedMultiplier()
	{
		return _ownerView.getAttackSpeedMultiplier();
	}
	
	@Override
	public double getCollisionRadius()
	{
		return _ownerView.getCollisionRadius();
	}
	
	@Override
	public double getCollisionHeight()
	{
		return _ownerView.getCollisionHeight();
	}
	
	@Override
	public int getRunSpd()
	{
		return _ownerView.getRunSpd();
	}
	
	@Override
	public int getWalkSpd()
	{
		return _ownerView.getWalkSpd();
	}
	
	@Override
	public int getSwimRunSpd()
	{
		return 0x32; // swim run speed (50)
	}
	
	@Override
	public int getSwimWalkSpd()
	{
		return 0x32; // swim walk speed (50)
	}
	
	@Override
	public int getFlRunSpd()
	{
		return _ownerView.getFlRunSpd();
	}
	
	@Override
	public int getFlWalkSpd()
	{
		return _ownerView.getFlWalkSpd();
	}
	
	@Override
	public int getFlyRunSpd()
	{
		return _ownerView.getFlyRunSpd();
	}
	
	@Override
	public int getFlyWalkSpd()
	{
		return _ownerView.getFlyWalkSpd();
	}
	
	// ---
	
	@Override
	public int getMAtkSpd()
	{
		return _ownerView.getMAtkSpd();
	}
	
	@Override
	public int getPAtkSpd()
	{
		return _ownerView.getPAtkSpd();
	}
	
	// ---
	
	@Override
	public int getCursedWeaponLevel()
	{
		return _ownerView.getCursedWeaponLevel();
	}
	
	@Override
	public int getTransformationGraphicalId()
	{
		return _ownerView.getTransformationGraphicalId();
	}
	
	@Override
	public int getNameColor()
	{
		return _ownerView.getNameColor();
	}
	
	@Override
	public int getTitleColor()
	{
		return _ownerView.getTitleColor();
	}
}
