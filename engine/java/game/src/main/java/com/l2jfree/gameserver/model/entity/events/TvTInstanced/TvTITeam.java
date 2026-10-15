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
package com.l2jfree.gameserver.model.entity.events.TvTInstanced;

/**
 * 
 * @author Vital
 * 
 */

import java.util.concurrent.CopyOnWriteArrayList;

import com.l2jfree.Config;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.gameobjects.L2Summon;
import com.l2jfree.gameserver.gameobjects.instance.L2PetInstance;
import com.l2jfree.gameserver.util.Util;
import com.l2jfree.tools.random.Rnd;

public class TvTITeam
{
	private String _teamName = new String();
	
	private int _teamColor = 0;
	private int _spawnLocX = 0;
	private int _spawnLocY = 0;
	private int _spawnLocZ = 0;
	private int _teamScore = 0;
	private int _spawnRadius = 0;
	
	private boolean _sitForced = false;
	
	private final CopyOnWriteArrayList<L2Player> _players = new CopyOnWriteArrayList<L2Player>();
	
	public TvTITeam(String teamName)
	{
		_teamName = teamName;
	}
	
	public TvTITeam(String teamName, String teamColor, int spawnLocX, int spawnLocY, int spawnLocZ, int spawnRadius)
	{
		_teamName = teamName;
		_teamColor = Integer.decode("0x" + Util.reverseColor(teamColor));
		_spawnLocX = spawnLocX;
		_spawnLocY = spawnLocY;
		_spawnLocZ = spawnLocZ;
		_spawnRadius = spawnRadius;
	}
	
	public TvTITeam(String teamName, int teamColor, int spawnLocX, int spawnLocY, int spawnLocZ, int spawnRadius)
	{
		_teamName = teamName;
		_teamColor = teamColor;
		_spawnLocX = spawnLocX;
		_spawnLocY = spawnLocY;
		_spawnLocZ = spawnLocZ;
		_spawnRadius = spawnRadius;
	}
	
	public boolean isSetUp()
	{
		if (_teamName.equals("") || _teamColor == 0 || _spawnLocX == 0 || _spawnLocY == 0 || _spawnLocZ == 0)
			return false;
		return true;
	}
	
	public void setUserData(int i)
	{
		for (L2Player player : _players)
		{
			switch (i)
			{
				case 0:
					player._countTvTiKills = 0;
					player._originalNameColorTvTi = player.getAppearance().getNameColor();
					player.getAppearance().setNameColor(_teamColor);
					player.getAppearance().setVisibleTitle("Kills: 0");
					player._originalKarmaTvTi = player.getKarma();
					player.setKarma(0);
					player._joiningTvTi = false;
					player._countTvTITeamKills = 0;
					player.broadcastUserInfo();
					break;
				case 1:
					player.getAppearance().setNameColor(player._originalNameColorTvTi);
					player.getAppearance().setVisibleTitle(null);
					player.setKarma(player._originalKarmaTvTi);
					player.setKarmaFlag(0);
					player._inEventTvTi = false;
					player.broadcastUserInfo();
					break;
			}
		}
	}
	
	public void sit()
	{
		if (isSitForced())
			setSitForce(false);
		else
			setSitForce(true);
		
		for (L2Player player : getPlayers())
		{
			if (player != null)
			{
				if (isSitForced())
				{
					player.stopMove(null, false);
					player.abortAttack();
					player.abortCast();
					
					if (!player.isSitting())
						player.sitDown();
					
					player._isSitForcedTvTi = true;
					
				}
				else
				{
					if (player.isSitting())
					{
						player._isSitForcedTvTi = false;
						player.standUp();
					}
				}
			}
		}
		
	}
	
	public void removeBuffs()
	{
		if (!Config.TVTI_ON_START_REMOVE_ALL_EFFECTS)
			return;
		
		for (L2Player player : _players)
			if (player != null)
				player.stopAllEffects();
	}
	
	public void removeParty()
	{
		for (L2Player player : _players)
			if (player != null)
				// Remove player from thier party
				if (player.getParty() != null)
					player.getParty().removePartyMember(player);
	}
	
	public void unsummon()
	{
		if (!Config.TVTI_ON_START_UNSUMMON_PET)
			return;
		
		for (L2Player player : _players)
			if (player != null)
				// Remove Summon's buffs
				if (player.getPet() != null)
				{
					L2Summon summon = player.getPet();
					summon.stopAllEffects();
					
					if (summon instanceof L2PetInstance)
						summon.unSummon(player);
				}
	}
	
	public void teleportToSpawn()
	{
		for (L2Player player : _players)
			if (player != null)
			{
				int offSetX = Rnd.get(-getSpawnRadius(), getSpawnRadius());
				int offSetY = Rnd.get(-getSpawnRadius(), getSpawnRadius());
				player.teleToLocation(getSpawnX() + offSetX, getSpawnY() + offSetY, getSpawnZ(), false);
			}
	}
	
	public void teleportToSpawn(L2Player player)
	{
		int offSetX = Rnd.get(-getSpawnRadius(), getSpawnRadius());
		int offSetY = Rnd.get(-getSpawnRadius(), getSpawnRadius());
		player.teleToLocation(getSpawnX() + offSetX, getSpawnY() + offSetY, getSpawnZ(), false);
	}
	
	public void teleportToFinish()
	{
		for (L2Player player : _players)
			if (player != null)
				if (player.isOnline() == 1)
				{
					int offSetX = Rnd.get(-TvTIMain.getSpawnRadius(), TvTIMain.getSpawnRadius());
					int offSetY = Rnd.get(-TvTIMain.getSpawnRadius(), TvTIMain.getSpawnRadius());
					player.teleToLocation(TvTIMain.getNpcX() + offSetX, TvTIMain.getNpcY() + offSetY,
							TvTIMain.getNpcZ(), false);
				}
				else
					player.getPosition().setWorldPosition(TvTIMain.getNpcX(), TvTIMain.getNpcY(), TvTIMain.getNpcZ());
	}
	
	public void addPlayer(L2Player player)
	{
		_players.add(player);
		player._inEventTvTi = true;
	}
	
	public void removePlayer(L2Player player)
	{
		player._inEventTvTi = false;
		player.getAppearance().setNameColor(player._originalNameColorTvTi);
		player.getAppearance().setVisibleTitle(null);
		player.setKarma(player._originalKarmaTvTi);
		_players.remove(player);
		
	}
	
	public void setInstance(int instanceId)
	{
		for (L2Player player : _players)
			player.setInstanceId(instanceId);
	}
	
	public CopyOnWriteArrayList<L2Player> getPlayers()
	{
		return _players;
	}
	
	public void setTeamScore(int score)
	{
		_teamScore = score;
	}
	
	public int getTeamScore()
	{
		return _teamScore;
	}
	
	public void setTeamName(String teamName)
	{
		_teamName = teamName;
	}
	
	public String getTeamName()
	{
		return _teamName;
	}
	
	public void setTeamColor(int color)
	{
		_teamColor = color;
	}
	
	public int getTeamColor()
	{
		return _teamColor;
	}
	
	public void setSpawn(int locX, int locY, int locZ)
	{
		_spawnLocX = locX;
		_spawnLocY = locY;
		_spawnLocZ = locZ;
	}
	
	public void setSpawn(L2Player activeChar)
	{
		_spawnLocX = activeChar.getX();
		_spawnLocY = activeChar.getY();
		_spawnLocZ = activeChar.getZ();
	}
	
	public int getSpawnX()
	{
		return _spawnLocX;
	}
	
	public int getSpawnY()
	{
		return _spawnLocY;
	}
	
	public int getSpawnZ()
	{
		return _spawnLocZ;
	}
	
	public void setSpawnRadius(int radius)
	{
		_spawnRadius = radius;
	}
	
	public int getSpawnRadius()
	{
		return _spawnRadius;
	}
	
	public void setSitForce(boolean sitForced)
	{
		_sitForced = sitForced;
	}
	
	public boolean isSitForced()
	{
		return _sitForced;
	}
}
