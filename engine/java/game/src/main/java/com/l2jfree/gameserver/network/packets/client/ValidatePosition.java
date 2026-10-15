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

import com.l2jfree.Config;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.geoeditorcon.GeoEditorListener;
import com.l2jfree.gameserver.model.zone.L2Zone;
import com.l2jfree.gameserver.network.packets.L2ClientPacket;
import com.l2jfree.gameserver.network.packets.server.ExValidateLocationInAirShip;
import com.l2jfree.gameserver.network.packets.server.ValidateLocation;
import com.l2jfree.gameserver.network.packets.server.ValidateLocationInVehicle;

/**
 * Sent by client during movement.
 */
public final class ValidatePosition extends L2ClientPacket
{
	private static final String _C__VALIDATEPOSITION = "[C] 59 ValidatePosition c[ddddd]";
	
	private int _x;
	private int _y;
	private int _z;
	private int _heading;
	
	// usually 0
	//private int _unk;
	
	@Override
	protected void readImpl()
	{
		_x = readD();
		_y = readD();
		_z = readD();
		_heading = readD();
		/*_unk = */readD();
	}
	
	@Override
	protected void runImpl()
	{
		final L2Player activeChar = getActiveChar();
		if (activeChar == null || activeChar.isTeleporting() || activeChar.isDead())
			return;
		
		final int realX = activeChar.getX();
		final int realY = activeChar.getY();
		final int realZ = activeChar.getZ();
		
		if (_x == 0 && _y == 0)
		{
			if (realX != 0) // in this case this seems like a client error
				return;
		}
		
		final double dx = _x - realX;
		final double dy = _y - realY;
		final double dz = _z - realZ;
		final double diffSq = (dx * dx + dy * dy);
		
		if (Config.DEVELOPER)
		{
			_log.info("client pos: " + _x + " " + _y + " " + _z + " head " + _heading);
			_log.info("server pos: " + realX + " " + realY + " " + realZ + " head " + activeChar.getHeading());
		}
		if (Config.ACCEPT_GEOEDITOR_CONN)
			if (GeoEditorListener.getInstance().getThread() != null
					&& GeoEditorListener.getInstance().getThread().isWorking()
					&& GeoEditorListener.getInstance().getThread().isSend(activeChar))
				GeoEditorListener.getInstance().getThread().sendGmPosition(_x, _y, (short)_z);
		
		if (activeChar.isFlying() || activeChar.isInsideZone(L2Zone.FLAG_WATER))
		{
			activeChar.getPosition().setXYZ(realX, realY, _z);
			if (diffSq > 90000) // validate packet, may also cause z bounce if close to land
			{
				if (activeChar.isInBoat())
					sendPacket(new ValidateLocationInVehicle(activeChar));
				else if (activeChar.isInAirShip())
					sendPacket(new ExValidateLocationInAirShip(activeChar));
				else
					sendPacket(new ValidateLocation(activeChar));
			}
		}
		else if (diffSq < 250000) // if too large, messes observation
		{
			if (Config.COORD_SYNCHRONIZE == -1) // Only Z coordinate synced to server, mainly used when no geodata but can be used also with geodata
			{
				activeChar.getPosition().setXYZ(realX, realY, _z);
			}
			else if (Config.COORD_SYNCHRONIZE == 1) // Trusting also client x, y coordinates (should not be used with geodata)
			{
				if (!activeChar.isMoving() || !activeChar.validateMovementHeading(_heading)) // Heading changed on client = possible obstacle
				{
					// character is not moving, take coordinates from client
					if (diffSq < 2500) // 50*50 - attack won't work fluently if even small differences are corrected
						activeChar.getPosition().setXYZ(realX, realY, _z);
					else
						activeChar.getPosition().setXYZ(_x, _y, _z);
				}
				else
					activeChar.getPosition().setXYZ(realX, realY, _z);
				activeChar.setHeading(_heading);
			}
			// Sync 2 (or other),
			// intended for geodata. Sends a validation packet to client
			// when too far from server calculated true coordinate.
			// Due to geodata/zone errors, some Z axis checks are made. (maybe a temporary solution)
			// Important: this code part must work together with L2Creature.updatePosition
			else if (Config.GEODATA > 0 && (diffSq > 10000 || Math.abs(dz) > 200))
			{
				if (Math.abs(dz) > 200 && Math.abs(dz) < 1500 && Math.abs(_z - activeChar.getClientZ()) < 800)
				{
					activeChar.getPosition().setXYZ(realX, realY, _z);
				}
				else
				{
					if (Config.DEVELOPER)
						_log.info(activeChar.getName() + ": Synchronizing position Server --> Client");
					if (activeChar.isInBoat())
						sendPacket(new ValidateLocationInVehicle(activeChar));
					else if (activeChar.isInAirShip())
						sendPacket(new ExValidateLocationInAirShip(activeChar));
					else
						sendPacket(new ValidateLocation(activeChar));
				}
			}
		}
		
		activeChar.setClientX(_x);
		activeChar.setClientY(_y);
		activeChar.setClientZ(_z);
		activeChar.setClientHeading(_heading); // No real need to validate heading.
	}
	
	@Override
	public String getType()
	{
		return _C__VALIDATEPOSITION;
	}
}
