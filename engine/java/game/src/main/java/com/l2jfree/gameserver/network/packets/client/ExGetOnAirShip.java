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

import com.l2jfree.gameserver.network.SystemMessageId;
import com.l2jfree.gameserver.network.packets.L2ClientPacket;

/**
 * Format: (c) dddd
 * d: dx
 * d: dy
 * d: dz
 * d: AirShip id ??
 * @author -Wooden-
 */
public class ExGetOnAirShip extends L2ClientPacket
{
	private int _x;
	private int _y;
	private int _z;
	private int _shipId;
	
	@Override
	protected void readImpl()
	{
		_x = readD();
		_y = readD();
		_z = readD();
		_shipId = readD();
	}
	
	@Override
	protected void runImpl()
	{
		//TODO: implement
		System.out.println("[T1:ExGetOnAirShip] x: " + _x);
		System.out.println("[T1:ExGetOnAirShip] y: " + _y);
		System.out.println("[T1:ExGetOnAirShip] z: " + _z);
		System.out.println("[T1:ExGetOnAirShip] ship ID: " + _shipId);
		requestFailed(SystemMessageId.NOT_WORKING_PLEASE_TRY_AGAIN_LATER);
	}
	
	@Override
	public String getType()
	{
		return "[C] 0xD0:0x35 ExGetOnAirShip";
	}
}
