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

import com.l2jfree.gameserver.network.packets.L2ServerPacket;

/**
 * Shows a movie to the player. After it is shown, client
 * sends us EndScenePlayer with the specified scene ID.<BR>
 * The client MUST be in the correct position, because the
 * camera's position depend's on current character's position.
 * @author savormix
 */
public final class ExStartScenePlayer extends L2ServerPacket
{
	private static final String _S__FE_99_EXSTARTSCENEPLAYER = "[S] FE:99 ExStartScenePlayer";
	
	private final int _scene;
	
	public ExStartScenePlayer(int sceneId)
	{
		_scene = sceneId;
	}
	
	@Override
	protected void writeImpl()
	{
		writeC(0xfe);
		writeH(0x99);
		
		writeD(_scene);
	}
	
	@Override
	public String getType()
	{
		return _S__FE_99_EXSTARTSCENEPLAYER;
	}
}
