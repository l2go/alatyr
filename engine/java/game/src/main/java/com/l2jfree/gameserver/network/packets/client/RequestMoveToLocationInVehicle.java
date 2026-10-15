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
import com.l2jfree.gameserver.gameobjects.ai.CtrlIntention;
import com.l2jfree.gameserver.gameobjects.instance.L2BoatInstance;
import com.l2jfree.gameserver.instancemanager.BoatManager;
import com.l2jfree.gameserver.model.L2CharPosition;
import com.l2jfree.gameserver.model.items.templates.L2WeaponType;
import com.l2jfree.gameserver.network.packets.L2ClientPacket;
import com.l2jfree.gameserver.network.packets.server.ActionFailed;
import com.l2jfree.tools.geometry.Point3D;

public class RequestMoveToLocationInVehicle extends L2ClientPacket
{
	private static final String _C__75_MOVETOLOCATIONINVEHICLE = "[C] 75 RequestMoveToLocationInVehicle";
	
	private final Point3D _pos = new Point3D(0, 0, 0);
	private final Point3D _origin_pos = new Point3D(0, 0, 0);
	private int _boatId;
	
	@Override
	protected void readImpl()
	{
		int _x, _y, _z;
		_boatId = readD(); //objectId of boat
		_x = readD();
		_y = readD();
		_z = readD();
		_pos.setXYZ(_x, _y, _z);
		_x = readD();
		_y = readD();
		_z = readD();
		_origin_pos.setXYZ(_x, _y, _z);
	}
	
	/* (non-Javadoc)
	 * @see com.l2jfree.gameserver.clientpackets.ClientBasePacket#runImpl()
	 */
	@Override
	protected void runImpl()
	{
		L2Player activeChar = getClient().getActiveChar();
		if (activeChar == null)
			return;
		
		if (activeChar.isAttackingNow() && activeChar.getActiveWeaponItem() != null
				&& (activeChar.getActiveWeaponItem().getItemType() == L2WeaponType.BOW))
		{
			activeChar.sendPacket(ActionFailed.STATIC_PACKET);
		}
		else
		{
			L2BoatInstance boat = BoatManager.getInstance().getBoat(_boatId);
			if (boat == null)
				return;
			activeChar.setBoat(boat);
			activeChar.setInBoatPosition(_pos);
			activeChar.getAI().setIntention(CtrlIntention.AI_INTENTION_MOVE_TO_IN_A_BOAT,
					new L2CharPosition(_pos.getX(), _pos.getY(), _pos.getZ(), 0),
					new L2CharPosition(_origin_pos.getX(), _origin_pos.getY(), _origin_pos.getZ(), 0));
		}
		
	}
	
	/* (non-Javadoc)
	 * @see com.l2jfree.gameserver.BasePacket#getType()
	 */
	@Override
	public String getType()
	{
		return _C__75_MOVETOLOCATIONINVEHICLE;
	}
}
