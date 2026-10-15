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


import java.util.ArrayList;
import java.util.List;
import com.l2jfree.gameserver.gameobjects.L2Creature;
import com.l2jfree.gameserver.instancemanager.CursedWeaponsManager;
import com.l2jfree.gameserver.model.CursedWeapon;
import com.l2jfree.gameserver.model.Location;
import com.l2jfree.gameserver.network.packets.L2ClientPacket;
import com.l2jfree.gameserver.network.packets.server.ExCursedWeaponLocation;
import com.l2jfree.gameserver.network.packets.server.ExCursedWeaponLocation.CursedWeaponInfo;

/**
 * Format: (ch)
 * @author  -Wooden-
 */
public class RequestCursedWeaponLocation extends L2ClientPacket
{
	private static final String _C__D0_23_REQUESTCURSEDWEAPONLOCATION = "[C] D0:23 RequestCursedWeaponLocation";
	
	@Override
	protected void readImpl()
	{
	}
	
	@Override
	protected void runImpl()
	{
		L2Creature activeChar = getClient().getActiveChar();
		if (activeChar == null)
			return;
		
		Location loc = null;
		List<CursedWeaponInfo> list = new ArrayList<CursedWeaponInfo>();
		for (CursedWeapon cw : CursedWeaponsManager.getInstance().getCursedWeapons())
		{
			if (!cw.isActive())
				continue;
			loc = cw.getCurrentLocation();
			if (loc != null)
				list.add(new CursedWeaponInfo(loc, cw.getItemId(), cw.isActivated() ? 1 : 0));
		}
		loc = null;
		
		sendPacket(new ExCursedWeaponLocation(list));
	}
	
	@Override
	public String getType()
	{
		return _C__D0_23_REQUESTCURSEDWEAPONLOCATION;
	}
}
