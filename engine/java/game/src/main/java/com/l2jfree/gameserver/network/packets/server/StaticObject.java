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
package com.l2jfree.gameserver.network.packets.server;

import com.l2jfree.gameserver.gameobjects.instance.L2DoorInstance;
import com.l2jfree.gameserver.gameobjects.instance.L2StaticObjectInstance;
import com.l2jfree.gameserver.network.packets.L2ServerPacket;

public class StaticObject extends L2ServerPacket
{
	private final static String S_9F_STATICOBJECT = "[S] 9F StaticObject";
	
	private final int _staticObjectId;
	private final int _objectId;
	private final int _type;
	private final boolean _targetable;
	private final int _meshIndex;
	private final boolean _closed;
	private final boolean _enemy;
	private final int _maxHp;
	private final int _currentHp;
	private final boolean _hpShown;
	private final int _damageGrade;
	
	/**
	 * Default static object packet.
	 * @param staticObject A static object (throne, bulletin, etc)
	 */
	public StaticObject(L2StaticObjectInstance staticObject)
	{
		_staticObjectId = staticObject.getStaticObjectId();
		_objectId = staticObject.getObjectId();
		_type = 0;
		_targetable = true;
		_meshIndex = staticObject.getMeshIndex();
		_closed = false;
		_enemy = false;
		_maxHp = 0;
		_currentHp = 0;
		_hpShown = false;
		_damageGrade = 0;
	}
	
	/**
	 * Creates a default info packet for a door.
	 * Targeting is allowed and HP is shown.
	 * @param door A door instance
	 */
	public StaticObject(L2DoorInstance door)
	{
		this(door, true, true);
	}
	
	/**
	 * Advanced door packet creation.
	 * @param door A door instance
	 * @param target Can the player target the object
	 * @param showHp Should we show HP when targeted
	 */
	public StaticObject(L2DoorInstance door, boolean targetable, boolean hpShown)
	{
		_staticObjectId = door.getDoorId();
		_objectId = door.getObjectId();
		_type = 1;
		_targetable = targetable;
		_meshIndex = 1;
		_closed = !door.isOpen();
		_maxHp = door.getMaxHp();
		if (_targetable)
		{
			_enemy = door.isEnemy();
			_currentHp = (int)door.getCurrentHp();
		}
		else
		{
			_enemy = false;
			_currentHp = _maxHp;
		}
		_hpShown = hpShown;
		_damageGrade = door.getDamageGrade();
	}
	
	@Override
	protected final void writeImpl()
	{
		writeC(0x9F);
		
		writeD(_staticObjectId);
		writeD(_objectId);
		writeD(_type);
		writeD(_targetable);
		writeD(_meshIndex);
		writeD(_closed);
		writeD(_enemy);
		writeD(_currentHp);
		writeD(_maxHp);
		writeD(_hpShown);
		writeD(_damageGrade);
	}
	
	@Override
	public String getType()
	{
		return S_9F_STATICOBJECT;
	}
}
