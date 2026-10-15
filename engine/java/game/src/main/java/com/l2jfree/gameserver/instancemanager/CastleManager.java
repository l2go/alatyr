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
package com.l2jfree.gameserver.instancemanager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.l2jfree.L2DatabaseFactory;
import com.l2jfree.gameserver.gameobjects.L2Object;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.model.clan.L2Clan;
import com.l2jfree.gameserver.model.clan.L2ClanMember;
import com.l2jfree.gameserver.model.entity.Castle;
import com.l2jfree.gameserver.model.items.L2ItemInstance;

public class CastleManager implements InstanceListManager
{
	protected static Logger _log = LoggerFactory.getLogger(CastleManager.class);
	
	private Map<Integer, Castle> _castles;
	
	public static final CastleManager getInstance()
	{
		return SingletonHolder._instance;
		
	}
	
	private static final int _castleCirclets[] = { 0, 6838, 6835, 6839, 6837, 6840, 6834, 6836, 8182, 8183 };
	
	private CastleManager()
	{
	}
	
	public final Castle getClosestCastle(L2Object activeObject)
	{
		return nearest(getCastle(activeObject), getCastles().values(), activeObject.getX(), activeObject.getY());
	}
	
	/**
	 * The castle the object is already inside, or the nearest castle that has a zone. A castle with no
	 * zone is not a candidate: {@link Castle#getDistanceToZone(int, int)} still reports that absence when
	 * it is called, and this search does not call it.
	 */
	static Castle nearest(Castle inside, Collection<Castle> castles, int x, int y)
	{
		if (inside != null)
			return inside;
		
		Castle chosen = null;
		double closestDistance = Double.MAX_VALUE;
		for (Castle candidate : castles)
		{
			if (candidate == null || candidate.getZone() == null)
				continue;
			
			double distance = candidate.getDistanceToZone(x, y);
			if (closestDistance > distance)
			{
				closestDistance = distance;
				chosen = candidate;
			}
		}
		return chosen;
	}
	
	public void reload()
	{
		getCastles().clear();
		loadInstances();
	}
	
	@Override
	public void loadInstances()
	{
		Connection con = null;
		try
		{
			PreparedStatement statement;
			ResultSet rs;
			
			con = L2DatabaseFactory.getInstance().getConnection(con);
			
			statement = con.prepareStatement("SELECT id FROM castle ORDER BY id");
			rs = statement.executeQuery();
			
			while (rs.next())
			{
				int id = rs.getInt("id");
				getCastles().put(id, new Castle(id));
			}
			
			statement.close();
			
			_log.info("Loaded: " + getCastles().size() + " castles");
		}
		catch (SQLException e)
		{
			_log.warn("Exception: loadCastleData(): " + e.getMessage(), e);
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
	}
	
	public final Castle getCastleById(int castleId)
	{
		return getCastles().get(castleId);
	}
	
	public final Castle getCastle(L2Object activeObject)
	{
		return getCastle(activeObject.getX(), activeObject.getY(), activeObject.getZ());
	}
	
	public final Castle getCastle(int x, int y, int z)
	{
		Castle castle;
		for (int i = 1; i <= getCastles().size(); i++)
		{
			castle = getCastles().get(i);
			if (castle != null && castle.checkIfInZone(x, y, z))
				return castle;
		}
		return null;
	}
	
	public final Castle getCastleByName(String name)
	{
		Castle castle;
		for (int i = 1; i <= getCastles().size(); i++)
		{
			castle = getCastles().get(i);
			if (castle != null && castle.getName().equalsIgnoreCase(name.trim()))
				return castle;
		}
		return null;
	}
	
	public final Castle getCastleByOwner(L2Clan clan)
	{
		if (clan == null)
			return null;
		
		Castle castle;
		for (int i = 1; i <= getCastles().size(); i++)
		{
			castle = getCastles().get(i);
			if (castle != null && castle.getOwnerId() == clan.getClanId())
				return castle;
		}
		return null;
	}
	
	public final Map<Integer, Castle> getCastles()
	{
		if (_castles == null)
			_castles = new LinkedHashMap<Integer, Castle>();
		return _castles;
	}
	
	public final void validateTaxes(int sealStrifeOwner)
	{
		for (Castle castle : _castles.values())
			castle.revalidateTax();
	}
	
	int _castleId = 1; // from this castle
	
	public int getCirclet()
	{
		return getCircletByCastleId(_castleId);
	}
	
	public int getCircletByCastleId(int castleId)
	{
		if (castleId > 0 && castleId < 10)
			return _castleCirclets[castleId];
		
		return 0;
	}
	
	// Remove this castle's circlets from the clan
	public void removeCirclet(L2Clan clan, int castleId)
	{
		for (L2ClanMember member : clan.getMembers())
			removeCirclet(member, castleId);
	}
	
	public void removeCirclet(L2ClanMember member, int castleId)
	{
		if (member == null)
			return;
		L2Player player = member.getPlayerInstance();
		int circletId = getCircletByCastleId(castleId);
		
		if (circletId != 0)
		{
			// Online Player circlet removal
			if (player != null && player.getInventory() != null)
			{
				L2ItemInstance circlet = player.getInventory().getItemByItemId(circletId);
				if (circlet != null)
				{
					if (circlet.isEquipped())
						player.getInventory().unEquipItemInSlotAndRecord(circlet.getLocationSlot());
					player.destroyItemByItemId("CastleCircletRemoval", circletId, 1, player, true);
				}
				return;
			}
			// Else Offline Player circlet removal
			Connection con = null;
			try
			{
				con = L2DatabaseFactory.getInstance().getConnection();
				PreparedStatement statement =
						con.prepareStatement("DELETE FROM item WHERE owner_player_id = ? AND item_template_id = ?");
				statement.setInt(1, member.getObjectId());
				statement.setInt(2, circletId);
				statement.execute();
				statement.close();
			}
			catch (SQLException e)
			{
				_log.error("Failed to remove castle circlets offline for player " + member.getName(), e);
			}
			finally
			{
				L2DatabaseFactory.close(con);
			}
		}
	}
	
	@Override
	public void updateReferences()
	{
	}
	
	@Override
	public void activateInstances()
	{
		for (final Castle castle : _castles.values())
		{
			castle.activateInstance();
		}
	}
	
	@SuppressWarnings("synthetic-access")
	private static class SingletonHolder
	{
		protected static final CastleManager _instance = new CastleManager();
	}
}
