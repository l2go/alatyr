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
package com.l2jfree.gameserver.network.packets.client;

import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.gameobjects.instance.L2BoatInstance;
import com.l2jfree.gameserver.instancemanager.BoatManager;
import com.l2jfree.gameserver.network.packets.L2ClientPacket;
import com.l2jfree.gameserver.network.packets.server.ActionFailed;
import com.l2jfree.gameserver.network.packets.server.GetOnVehicle;
import com.l2jfree.tools.geometry.Point3D;

/**
 * This class ...
 * 
 * @version $Revision: 1.1.4.3 $ $Date: 2005/03/27 15:29:30 $
 */
public class RequestGetOnVehicle extends L2ClientPacket
{
	private static final String _C__5C_GETONVEHICLE = "[C] 5C GetOnVehicle";
	
	private int _id, _x, _y, _z;
	
	@Override
	protected void readImpl()
	{
		_id = readD();
		_x = readD();
		_y = readD();
		_z = readD();
	}
	
	@Override
	protected void runImpl()
	{
		L2Player activeChar = getClient().getActiveChar();
		if (activeChar == null)
			return;
		
		L2BoatInstance boat = BoatManager.getInstance().getBoat(_id);
		if (boat != null)
		{
			GetOnVehicle gon = new GetOnVehicle(activeChar, boat, _x, _y, _z);
			activeChar.setInBoatPosition(new Point3D(_x, _y, _z));
			activeChar.getPosition().setXYZ(boat.getPosition().getX(), boat.getPosition().getY(),
					boat.getPosition().getZ());
			activeChar.broadcastPacket(gon);
			activeChar.revalidateZone(true);
		}
		
		sendPacket(ActionFailed.STATIC_PACKET);
	}
	
	@Override
	public String getType()
	{
		return _C__5C_GETONVEHICLE;
	}
}
