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
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.model.entity.events.AbstractFunEvent.FunEventState;
import com.l2jfree.util.L2Collections;
import com.l2jfree.util.L2FastSet;

/**
 * @author NB4L1
 */
public abstract class AbstractFunEventTeam<Info extends AbstractFunEventPlayerInfo>
{
	private final String _name;
	
	/**
	 * used to store current online players in this team
	 */
	private final Map<Integer, Info> _players = new ConcurrentHashMap<Integer, Info>();
	
	/**
	 * used to store disconnected/offline players' objectId in case they reconnect
	 */
	private final Set<Integer> _offlinePlayers = new L2FastSet<Integer>().setShared(true);
	
	/**
	 * @param name must be unique, as it's the index of the team
	 */
	protected AbstractFunEventTeam(String name)
	{
		_name = name;
	}
	
	/**
	 * @return name of the team, which must be unique, as it's the index of the team
	 */
	public final String getName()
	{
		return _name;
	}
	
	/**
	 * @return map of currently online participants with associated infos
	 */
	public final Map<Integer, Info> getPlayerInfos()
	{
		return _players;
	}
	
	/**
	 * @return currently online participants
	 */
	public final Iterable<L2Player> getPlayers()
	{
		return L2Collections.convertingIterable(_players.values(), new L2Collections.Converter<Info, L2Player>() {
			@Override
			public L2Player convert(Info info)
			{
				return info.getPlayer();
			}
		});
	}
	
	/**
	 * @param player
	 * @return true, if the addition was successful, false otherwise
	 */
	public synchronized boolean addPlayer(L2Player player)
	{
		if (_players.containsKey(player.getObjectId()))
			return false;
		
		_players.put(player.getObjectId(), wrap(player));
		return true;
	}
	
	protected abstract Info wrap(L2Player player);
	
	/**
	 * @param player the character, who just logged in
	 * @return true, if the player was stored in this team as a disconnected player, false otherwise
	 */
	public synchronized boolean playerLoggedIn(L2Player player)
	{
		return _offlinePlayers.remove(player.getObjectId());
	}
	
	/**
	 * Applies the state change on the team.
	 * 
	 * @param prevState
	 * @param nextState
	 */
	public synchronized void setState(final FunEventState prevState, final FunEventState nextState)
	{
		switch (nextState)
		{
			case INACTIVE:
			{
				break;
			}
			case REGISTRATION:
			{
				break;
			}
			case PREPARATION:
			{
				break;
			}
			case RUNNING:
			{
				break;
			}
			case COOLDOWN:
			{
				break;
			}
		}
	}
}
