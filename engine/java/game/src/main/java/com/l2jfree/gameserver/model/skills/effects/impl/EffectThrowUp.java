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
package com.l2jfree.gameserver.model.skills.effects.impl;

import com.l2jfree.Config;
import com.l2jfree.gameserver.geodata.GeoData;
import com.l2jfree.gameserver.model.Location;
import com.l2jfree.gameserver.model.skills.Env;
import com.l2jfree.gameserver.model.skills.effects.L2Effect;
import com.l2jfree.gameserver.model.skills.effects.templates.EffectTemplate;
import com.l2jfree.gameserver.model.skills.templates.L2EffectType;
import com.l2jfree.gameserver.network.packets.server.FlyToLocation;
import com.l2jfree.gameserver.network.packets.server.FlyToLocation.FlyType;
import com.l2jfree.gameserver.network.packets.server.ValidateLocation;

public class EffectThrowUp extends L2Effect
{
	private int _x, _y, _z;
	
	public EffectThrowUp(Env env, EffectTemplate template)
	{
		super(env, template);
	}
	
	@Override
	public L2EffectType getEffectType()
	{
		return L2EffectType.THROW_UP;
	}
	
	@Override
	protected boolean onStart()
	{
		// Get current position of the L2Creature
		final int curX = getEffected().getX();
		final int curY = getEffected().getY();
		final int curZ = getEffected().getZ();
		
		// Calculate distance between effector and effected current position
		double dx = getEffector().getX() - curX;
		double dy = getEffector().getY() - curY;
		double dz = getEffector().getZ() - curZ;
		double distance = Math.sqrt(dx * dx + dy * dy);
		if (distance > 2000)
		{
			_log.info("EffectThrowUp was going to use invalid coordinates for characters, getEffected: " + curX + ","
					+ curY + " and getEffector: " + getEffector().getX() + "," + getEffector().getY());
			return false;
		}
		int offset = Math.min((int)distance + getSkill().getFlyRadius(), 1400);
		
		double cos;
		double sin;
		
		// approximation for moving futher when z coordinates are different
		// TODO: handle Z axis movement better
		offset += Math.abs(dz);
		if (offset < 5)
			offset = 5;
		
		// If no distance
		if (distance < 1)
			return false;
		
		// Calculate movement angles needed
		sin = dy / distance;
		cos = dx / distance;
		
		// Calculate the new destination with offset included
		_x = getEffector().getX() - (int)(offset * cos);
		_y = getEffector().getY() - (int)(offset * sin);
		_z = getEffected().getZ();
		
		if (Config.GEODATA > 0)
		{
			Location destiny =
					GeoData.getInstance().moveCheck(getEffected().getX(), getEffected().getY(), getEffected().getZ(),
							_x, _y, _z, getEffected().getInstanceId());
			_x = destiny.getX();
			_y = destiny.getY();
		}
		getEffected().startStunning();
		getEffected().broadcastPacket(new FlyToLocation(getEffected(), _x, _y, _z, FlyType.THROW_UP));
		return true;
	}
	
	@Override
	protected void onExit()
	{
		getEffected().stopStunning(false);
		getEffector().getPosition().setXYZ(_x, _y, _z);
		getEffected().broadcastPacket(new ValidateLocation(getEffected()));
	}
}
