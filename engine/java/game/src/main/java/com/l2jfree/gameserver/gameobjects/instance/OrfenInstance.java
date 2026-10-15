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
package com.l2jfree.gameserver.gameobjects.instance;

import com.l2jfree.gameserver.gameobjects.ai.L2CreatureAI;
import com.l2jfree.gameserver.gameobjects.ai.OrfenAI;
import com.l2jfree.gameserver.gameobjects.templates.L2NpcTemplate;
import com.l2jfree.gameserver.model.Location;

/**
 * @author hex1r0
 */
public class OrfenInstance extends L2GrandBossInstance
{
	public static enum Position
	{
		FIELD,
		NEST
	}
	
	public static final Location FIELD_POS = new Location(55024, 17368, -5412, 0);
	public static final Location NEST_POS = new Location(43728, 17220, -4342, 0);
	
	public static final String[] MESSAGES = { "%s, stop kidding yourthis about your own powerlessness!",
			"%s, I'll make you feel what true fear is!", "You're really stupid to have challenged me. %s! Get ready!",
			"%s, do you think that's going to work?!" };
	
	private Position _pos = Position.FIELD;
	
	public OrfenInstance(int objectId, L2NpcTemplate template)
	{
		super(objectId, template);
	}
	
	@Override
	protected L2CreatureAI initAI()
	{
		return new OrfenAI(new AIAccessor());
	}
	
	public Position getPos()
	{
		return _pos;
	}
	
	public void setPos(Position pos)
	{
		_pos = pos;
	}
}
