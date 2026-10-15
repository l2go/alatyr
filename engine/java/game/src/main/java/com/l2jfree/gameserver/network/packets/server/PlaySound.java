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

import com.l2jfree.gameserver.gameobjects.L2Object;
import com.l2jfree.gameserver.network.packets.L2ServerPacket;

public class PlaySound extends L2ServerPacket
{
	public static final int TYPE_SOUND = 0;
	public static final int TYPE_MUSIC = 1;
	public static final int TYPE_VOICE = 2;
	private static final String _S__9E_PLAYSOUND = "[S] 9E PlaySound [dSddddd]";
	
	private final int _mode;
	private final String _soundFile;
	private final int _bound;
	private final int _objectId;
	private final int _x;
	private final int _y;
	private final int _z;
	private final int _unknown8;
	
	public PlaySound(int mode, String soundFile)
	{
		_mode = mode;
		_soundFile = soundFile;
		_bound = 0;
		_objectId = 0;
		_x = 0;
		_y = 0;
		_z = 0;
		_unknown8 = 0;
	}
	
	/**
	 * Creates an advanced sound packet. Using a type other than TYPE_MUSIC
	 * is highly not recommended.
	 * @param mode Sound type
	 * @param obj Source of the sound/music
	 * @param radiusOrDuration ??? (0 by default)
	 * @param soundFile sound name
	 */
	public PlaySound(int mode, L2Object obj, int radiusOrDuration, String soundFile)
	{
		_mode = mode;
		_soundFile = soundFile;
		_bound = 1;
		_objectId = obj.getObjectId();
		_x = obj.getX();
		_y = obj.getY();
		_z = obj.getZ();
		_unknown8 = radiusOrDuration; // ????? radius?
	}
	
	@Override
	protected final void writeImpl()
	{
		writeC(0x9e);
		writeD(_mode); // 0 - sound, 1 - music, 2 - voice
		writeS(_soundFile);
		writeD(_bound); // 1 to bind to object for 3D effect
		writeD(_objectId);
		writeD(_x);
		writeD(_y);
		writeD(_z);
		writeD(_unknown8); // radius ???
	}
	
	@Override
	public String getType()
	{
		return _S__9E_PLAYSOUND;
	}
}
