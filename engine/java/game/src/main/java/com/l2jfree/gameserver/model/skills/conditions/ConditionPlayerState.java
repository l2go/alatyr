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
package com.l2jfree.gameserver.model.skills.conditions;

import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.gameobjects.base.PlayerState;
import com.l2jfree.gameserver.model.skills.Env;

/**
 * @author mkizub
 */
public final class ConditionPlayerState extends Condition
{
	private final PlayerState _check;
	private final boolean _required;
	
	public ConditionPlayerState(PlayerState check, boolean required)
	{
		_check = check;
		_required = required;
	}
	
	@Override
	boolean testImpl(Env env)
	{
		L2Player player;
		switch (_check)
		{
			case RESTING:
				if (env.player instanceof L2Player)
					return ((L2Player)env.player).isSitting() == _required;
				break;
			case MOVING:
				return env.player.isMoving() == _required;
			case RUNNING:
				return (env.player.isMoving() && env.player.isRunning()) == _required;
			case WALKING:
				return (env.player.isMoving() && !env.player.isRunning()) == _required;
			case BEHIND:
				if (env.target == null)
					return false;
				return env.player.isBehind(env.target) == _required;
			case FRONT:
				if (env.target == null)
					return false;
				return env.player.isInFrontOf(env.target) == _required;
			case CHAOTIC:
				player = env.player.getActingPlayer();
				if (player != null)
					return player.getKarma() > 0 == _required;
				break;
			case OLYMPIAD:
				player = env.player.getActingPlayer();
				if (player != null)
					return player.isInOlympiadMode() == _required;
				break;
			case FLYING:
				return env.player.isFlying() == _required;
		}
		
		return !_required;
	}
}
