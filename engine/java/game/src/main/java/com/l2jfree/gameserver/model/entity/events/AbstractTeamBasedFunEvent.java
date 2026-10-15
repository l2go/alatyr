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
package com.l2jfree.gameserver.model.entity.events;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author NB4L1
 */
public abstract class AbstractTeamBasedFunEvent<Team extends AbstractFunEventTeam<Info>, Info extends AbstractFunEventPlayerInfo>
		extends AbstractFunEvent
{
	private final Map<String, Team> _teams = new ConcurrentHashMap<String, Team>();
	
	protected AbstractTeamBasedFunEvent()
	{
	}
	
	/**
	 * @return current teams
	 */
	public Map<String, Team> getTeams()
	{
		return _teams;
	}
	
	/*
	 * (non-Javadoc)
	 * @see com.l2jfree.gameserver.model.entity.events.AbstractFunEvent#setState(com.l2jfree.gameserver.model.entity.events.AbstractFunEvent.FunEventState, com.l2jfree.gameserver.model.entity.events.AbstractFunEvent.FunEventState)
	 */
	@Override
	protected synchronized void setState(FunEventState expectedPrevState, FunEventState nextState)
			throws IllegalStateException
	{
		super.setState(expectedPrevState, nextState);
		
		for (Team team : _teams.values())
			team.setState(expectedPrevState, nextState);
	}
}
