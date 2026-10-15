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
package com.l2jfree.gameserver.model.entity;

import com.l2jfree.gameserver.datatables.ResidentialSkillTable;
import com.l2jfree.gameserver.gameobjects.L2Creature;
import com.l2jfree.gameserver.gameobjects.L2Object;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.model.clan.L2Clan;
import com.l2jfree.gameserver.model.skills.L2Skill;
import com.l2jfree.gameserver.model.zone.L2SiegeZone;
import com.l2jfree.gameserver.model.zone.L2Zone;

public abstract class Siegeable<T extends AbstractSiege> extends Entity
{
	protected String _name;
	protected int _ownerId = 0;
	protected L2Clan _formerOwner = null;
	
	private L2Zone _zoneHQ;
	private L2SiegeZone _zoneBF;
	private L2Zone _zoneDS;
	private L2Zone _zoneTP;
	
	private L2Skill[] _residentialSkills = null;
	
	public Siegeable(int entityId)
	{
		super();
		_residentialSkills = ResidentialSkillTable.getInstance().getSkills(entityId);
	}
	
	public final String getName()
	{
		return _name;
	}
	
	public final int getOwnerId()
	{
		return _ownerId;
	}
	
	public void registerHeadquartersZone(L2Zone zone)
	{
		_zoneHQ = zone;
	}
	
	public void registerSiegeZone(L2SiegeZone zone)
	{
		_zoneBF = zone;
	}
	
	public void registerDefenderSpawn(L2Zone zone)
	{
		_zoneDS = zone;
	}
	
	public void registerTeleportZone(L2Zone zone)
	{
		_zoneTP = zone;
	}
	
	public void oustAllPlayers()
	{
		for (L2Creature player : _zoneTP.getCharactersInside())
		{
			// To random spot in defender spawn zone
			if (player instanceof L2Player)
				player.teleToLocation(_zoneDS.getRandomLocation(), true);
		}
	}
	
	@Override
	public boolean checkBanish(L2Player cha)
	{
		return cha.getClanId() != getOwnerId() && !cha.isGM();
	}
	
	/**
	 * Return true if object is inside the zone
	 */
	public boolean checkIfInZoneBattlefield(L2Object obj)
	{
		return checkIfInZoneBattlefield(obj.getX(), obj.getY(), obj.getZ());
	}
	
	/**
	 * Return true if object is inside the zone
	 */
	public boolean checkIfInZoneBattlefield(int x, int y, int z)
	{
		return getBattlefield().isInsideZone(x, y, z);
	}
	
	/**
	 * Return true if object is inside the zone
	 */
	public boolean checkIfInZoneHeadQuarters(L2Object obj)
	{
		return checkIfInZoneHeadQuarters(obj.getX(), obj.getY(), obj.getZ());
	}
	
	/**
	 * Return true if object is inside the zone
	 */
	public boolean checkIfInZoneHeadQuarters(int x, int y, int z)
	{
		return getHeadQuarters().isInsideZone(x, y, z);
	}
	
	public final L2Zone getHeadQuarters()
	{
		return _zoneHQ;
	}
	
	public final L2SiegeZone getBattlefield()
	{
		return _zoneBF;
	}
	
	public final L2Zone getDefenderSpawn()
	{
		return _zoneDS;
	}
	
	public final L2Zone getTeleZone()
	{
		return _zoneTP;
	}
	
	public abstract T getSiege();
	
	public L2Skill[] getResidentialSkills()
	{
		return _residentialSkills;
	}
	
	public void giveResidentialSkills(L2Player player)
	{
		if (_residentialSkills != null)
		{
			for (L2Skill sk : _residentialSkills)
				player.addSkill(sk, false);
		}
	}
	
	public void removeResidentialSkills(L2Player player)
	{
		if (_residentialSkills != null)
		{
			for (L2Skill sk : _residentialSkills)
				player.removeSkill(sk, false);
		}
	}
}
