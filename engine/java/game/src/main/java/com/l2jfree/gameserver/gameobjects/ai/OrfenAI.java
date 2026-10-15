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
package com.l2jfree.gameserver.gameobjects.ai;

import com.l2jfree.gameserver.gameobjects.L2Creature;
import com.l2jfree.gameserver.gameobjects.L2Creature.AIAccessor;
import com.l2jfree.gameserver.gameobjects.instance.OrfenInstance;
import com.l2jfree.gameserver.gameobjects.instance.OrfenInstance.Position;
import com.l2jfree.gameserver.network.packets.server.NpcSay;
import com.l2jfree.gameserver.util.Util;
import com.l2jfree.tools.random.Rnd;

/**
 * @author hex1r0
 */
public class OrfenAI extends L2AttackableAI
{
	public OrfenAI(AIAccessor accessor)
	{
		super(accessor);
	}
	
	@Override
	protected void thinkActive()
	{
		super.thinkActive();
		
		OrfenInstance actor = (OrfenInstance)_actor;
		if (actor.getCurrentHp() == actor.getMaxHp() && actor.getPos() == Position.NEST)
		{
			actor.setPos(Position.FIELD);
			actor.teleToLocation(OrfenInstance.FIELD_POS, false);
		}
	}
	
	@Override
	protected void thinkAttack()
	{
		super.thinkAttack();
		
		OrfenInstance actor = (OrfenInstance)_actor;
		if (actor.getCurrentHp() < (actor.getMaxHp() / 2) && actor.getPos() == Position.FIELD)
		{
			actor.setPos(Position.NEST);
			actor.teleToLocation(OrfenInstance.NEST_POS, false);
		}
	}
	
	@Override
	protected void onEvtAttacked(L2Creature attacker)
	{
		super.onEvtAttacked(attacker);
		
		OrfenInstance actor = (OrfenInstance)_actor;
		double distance = Util.calculateDistance(actor, attacker, true);
		if (distance > 300D && Rnd.get(100) < 10)
		{
			actor.broadcastPacket(new NpcSay(actor, OrfenInstance.MESSAGES[Rnd.get(OrfenInstance.MESSAGES.length)]
					.replace("%s", attacker.getName())));
			attacker.teleToLocation(actor.getLoc(), true);
		}
	}
}
