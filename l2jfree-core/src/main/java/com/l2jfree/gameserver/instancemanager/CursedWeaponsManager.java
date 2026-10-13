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

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import javax.xml.parsers.DocumentBuilderFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import com.l2jfree.Config;
import com.l2jfree.L2DatabaseFactory;
import com.l2jfree.gameserver.gameobjects.L2Attackable;
import com.l2jfree.gameserver.gameobjects.L2Boss;
import com.l2jfree.gameserver.gameobjects.L2Creature;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.gameobjects.instance.L2FeedableBeastInstance;
import com.l2jfree.gameserver.gameobjects.instance.L2FestivalMonsterInstance;
import com.l2jfree.gameserver.gameobjects.instance.L2FortCommanderInstance;
import com.l2jfree.gameserver.gameobjects.instance.L2FortSiegeGuardInstance;
import com.l2jfree.gameserver.gameobjects.instance.L2GuardInstance;
import com.l2jfree.gameserver.gameobjects.instance.L2RiftInvaderInstance;
import com.l2jfree.gameserver.gameobjects.instance.L2SiegeGuardInstance;
import com.l2jfree.gameserver.model.CursedWeapon;
import com.l2jfree.gameserver.model.items.L2ItemInstance;
import com.l2jfree.gameserver.model.world.L2World;
import com.l2jfree.gameserver.network.SystemMessageId;
import com.l2jfree.gameserver.network.packets.server.SystemMessage;
import com.l2jfree.gameserver.persistence.WorldTransaction;

/**
 * 
 * @author Micht
 */
public class CursedWeaponsManager
{
	private static final Logger _log = LoggerFactory.getLogger(CursedWeaponsManager.class);
	
	public static final CursedWeaponsManager getInstance()
	{
		return SingletonHolder._instance;
	}
	
	private Map<Integer, CursedWeapon> _cursedWeapons;
	
	private CursedWeaponsManager()
	{
		_cursedWeapons = new LinkedHashMap<Integer, CursedWeapon>();
		load();
	}
	
	public final void reload()
	{
		_cursedWeapons = new LinkedHashMap<Integer, CursedWeapon>();
		load();
	}
	
	private final void load()
	{
		File cursedWeapons = new File(Config.DATAPACK_ROOT, "data/cursedWeapons.xml");
		if (!cursedWeapons.isFile())
		{
			_log.info("World has no cursed weapons");
			return;
		}
		
		Connection con = null;
		
		try
		{
			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			factory.setValidating(true);
			factory.setIgnoringComments(true);
			
			File file = new File(Config.DATAPACK_ROOT, "data/cursedWeapons.xml");
			if (!file.exists())
				throw new IOException();
			
			Document doc = factory.newDocumentBuilder().parse(file);
			
			for (Node n = doc.getFirstChild(); n != null; n = n.getNextSibling())
			{
				if ("list".equalsIgnoreCase(n.getNodeName()))
				{
					for (Node d = n.getFirstChild(); d != null; d = d.getNextSibling())
					{
						if ("item".equalsIgnoreCase(d.getNodeName()))
						{
							NamedNodeMap attrs = d.getAttributes();
							int id = Integer.parseInt(attrs.getNamedItem("id").getNodeValue());
							int skillId = Integer.parseInt(attrs.getNamedItem("skillId").getNodeValue());
							String name = attrs.getNamedItem("name").getNodeValue();
							
							CursedWeapon cw = new CursedWeapon(id, skillId, name);
							
							int val;
							for (Node cd = d.getFirstChild(); cd != null; cd = cd.getNextSibling())
							{
								if ("dropRate".equalsIgnoreCase(cd.getNodeName()))
								{
									attrs = cd.getAttributes();
									val = Integer.parseInt(attrs.getNamedItem("val").getNodeValue());
									cw.setDropRate(val);
								}
								else if ("duration".equalsIgnoreCase(cd.getNodeName()))
								{
									attrs = cd.getAttributes();
									val = Integer.parseInt(attrs.getNamedItem("val").getNodeValue());
									cw.setDuration(val);
								}
								else if ("durationLost".equalsIgnoreCase(cd.getNodeName()))
								{
									attrs = cd.getAttributes();
									val = Integer.parseInt(attrs.getNamedItem("val").getNodeValue());
									cw.setDurationLost(val);
								}
								else if ("disapearChance".equalsIgnoreCase(cd.getNodeName()))
								{
									attrs = cd.getAttributes();
									val = Integer.parseInt(attrs.getNamedItem("val").getNodeValue());
									cw.setDisapearChance(val);
								}
								else if ("stageKills".equalsIgnoreCase(cd.getNodeName()))
								{
									attrs = cd.getAttributes();
									val = Integer.parseInt(attrs.getNamedItem("val").getNodeValue());
									cw.setStageKills(val);
								}
								else if ("transformId".equalsIgnoreCase(cd.getNodeName()))
								{
									attrs = cd.getAttributes();
									val = Integer.parseInt(attrs.getNamedItem("val").getNodeValue());
									cw.setTransformId(val);
								}
							}
							
							// Store cursed weapon
							_cursedWeapons.put(id, cw);
						}
					}
				}
			}
			
			// Retrieve the L2Player from the characters table of the database
			con = L2DatabaseFactory.getInstance().getConnection();
			
			if (Config.ALLOW_CURSED_WEAPONS)
			{
				PreparedStatement statement =
						con.prepareStatement("SELECT item_template_id, player_id, previous_karma, previous_pk_kills, kill_count, end_at FROM cursed_weapon");
				ResultSet rset = statement.executeQuery();
				
				while (rset.next())
				{
					int itemId = rset.getInt("item_template_id");
					int playerId = rset.getInt("player_id");
					int playerKarma = rset.getInt("previous_karma");
					int playerPkKills = rset.getInt("previous_pk_kills");
					int nbKills = rset.getInt("kill_count");
					Timestamp endAt = rset.getTimestamp("end_at");
					long endTime = endAt == null ? 0L : endAt.getTime(); // NULL: no end set
					
					CursedWeapon cw = _cursedWeapons.get(itemId);
					cw.setPlayerId(playerId);
					cw.setPlayerKarma(playerKarma);
					cw.setPlayerPkKills(playerPkKills);
					cw.setNbKills(nbKills);
					cw.setEndTime(endTime);
					cw.reActivate();
				}
				
				rset.close();
				statement.close();
			}
			else
			{
				PreparedStatement statement = con.prepareStatement("DELETE FROM cursed_weapon");
				statement.executeUpdate();
				statement.close();
			}
			
			//L2DatabaseFactory.close(con);
			
			// Retrieve the L2Player from the characters table of the database
			//con = L2DatabaseFactory.getInstance().getConnection(con);
			
			for (CursedWeapon cw : _cursedWeapons.values())
			{
				if (cw.isActivated())
					continue;
				
				// Do an item check to be sure that the cursed weapon isn't hold by someone
				removeUnexpectedHolder(cw);
			}
		}
		catch (Exception e)
		{
			_log.warn("Could not load CursedWeapons data: ", e);
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
		
		_log.info("CursedWeaponsManager: loaded " + _cursedWeapons.size() + " cursed weapon(s).");
	}
	
	public synchronized void checkDrop(L2Attackable attackable, L2Player player)
	{
		if (Config.ALLOW_CURSED_WEAPONS)
		{
			if (attackable instanceof L2SiegeGuardInstance || attackable instanceof L2RiftInvaderInstance
					|| attackable instanceof L2FestivalMonsterInstance || attackable instanceof L2GuardInstance
					|| attackable instanceof L2Boss || attackable instanceof L2FeedableBeastInstance
					|| attackable instanceof L2FortSiegeGuardInstance || attackable instanceof L2FortCommanderInstance)
				return;
			
			for (CursedWeapon cw : _cursedWeapons.values())
			{
				if (cw.isActive())
					continue;
				
				if (cw.checkDrop(attackable, player))
					break;
			}
		}
	}
	
	public boolean activate(L2Player player, L2ItemInstance item)
	{
		if (Config.ALLOW_CURSED_WEAPONS)
		{
			CursedWeapon cw = _cursedWeapons.get(item.getItemId());
			
			if (player.isCursedWeaponEquipped()) // cannot own 2 cursed swords
			{
				CursedWeapon cw2 = _cursedWeapons.get(player.getCursedWeaponEquippedId());
				
				cw2.setNbKills(cw2.getStageKills() - 1);
				cw2.increaseKills();
				
				// Erase the newly obtained cursed weapon
				cw.setPlayer(player); // NECESSARY in order to find which inventory the weapon is in!
				cw.endOfLife(); // expire the weapon and clean up.
				return true;
			}
			else
				return cw.activate(player, item);
		}
		else
			return false;
	}
	
	public void drop(int itemId, L2Creature killer)
	{
		CursedWeapon cw = _cursedWeapons.get(itemId);
		
		cw.dropIt(killer);
	}
	
	public void increaseKills(int itemId)
	{
		CursedWeapon cw = _cursedWeapons.get(itemId);
		
		cw.increaseKills();
	}
	
	public int getLevel(int itemId)
	{
		CursedWeapon cw = _cursedWeapons.get(itemId);
		
		return cw.getLevel();
	}
	
	public static void announce(SystemMessage sm)
	{
		for (L2Player player : L2World.getInstance().getAllPlayers())
		{
			if (player == null)
				continue;
			
			player.sendPacket(sm);
		}
	}
	
	public void onEnter(L2Player player)
	{
		if (player == null)
			return;
		
		for (CursedWeapon cw : _cursedWeapons.values())
		{
			if (cw.isActivated() && player.getObjectId() == cw.getPlayerId())
			{
				cw.setPlayer(player);
				cw.setItem(player.getInventory().getItemByItemId(cw.getItemId()));
				cw.giveSkill();
				player.setCursedWeaponEquippedId(cw.getItemId());
				
				SystemMessage sm = new SystemMessage(SystemMessageId.S2_MINUTE_OF_USAGE_TIME_ARE_LEFT_FOR_S1);
				sm.addString(cw.getName());
				//sm.addItemName(cw.getItemId());
				sm.addNumber((int)((cw.getEndTime() - System.currentTimeMillis()) / 60000));
				player.sendPacket(sm);
			}
		}
	}
	
	public void onExit(L2Player player)
	{
		if (player == null)
			return;
		
		for (CursedWeapon cw : _cursedWeapons.values())
		{
			if (cw.isActivated() && player.getObjectId() == cw.getPlayerId())
			{
				cw.setPlayer(null);
				cw.setItem(null);
			}
		}
	}
	
	/**
	 * Takes the cursed weapon away from a player who owns it although the weapon is not active.
	 */
	private static void removeUnexpectedHolder(CursedWeapon cw)
	{
		final int itemId = cw.getItemId();
		
		// Deleting the item, restoring the karma and cleaning the table belong together
		WorldTransaction.run("Cleanup of the cursed weapon " + itemId, () -> {
			Connection con = null;
			try
			{
				con = L2DatabaseFactory.getInstance().getConnection();
				
				PreparedStatement statement =
						con.prepareStatement("SELECT owner_player_id FROM item WHERE item_template_id=? AND owner_player_id IS NOT NULL");
				statement.setInt(1, itemId);
				ResultSet rset = statement.executeQuery();
				
				if (rset.next())
				{
					// A player has the cursed weapon in his inventory ...
					int playerId = rset.getInt("owner_player_id");
					_log.info("PROBLEM : Player " + playerId + " owns the cursed weapon " + itemId
							+ " but he shouldn't.");
					
					// Delete the item
					PreparedStatement statement2 =
							con.prepareStatement("DELETE FROM item WHERE owner_player_id=? AND item_template_id=?");
					statement2.setInt(1, playerId);
					statement2.setInt(2, itemId);
					if (statement2.executeUpdate() != 1)
					{
						_log.warn("Error while deleting cursed weapon " + itemId + " from userId " + playerId);
					}
					statement2.close();
					
					// Restore the player's old karma and pk count
					statement2 = con.prepareStatement("UPDATE player SET karma=?, pk_kills=? WHERE id = ?");
					statement2.setInt(1, cw.getPlayerKarma());
					statement2.setInt(2, cw.getPlayerPkKills());
					statement2.setInt(3, playerId);
					if (statement2.executeUpdate() != 1)
					{
						_log.warn("Error while updating karma & pkkills for charId " + cw.getPlayerId());
					}
					statement2.close();
					// clean up the cursedweapons table.
					removeFromDb(itemId);
				}
				rset.close();
				statement.close();
			}
			catch (SQLException e)
			{
				throw new IllegalStateException("Could not clean up the cursed weapon " + itemId, e);
			}
			finally
			{
				L2DatabaseFactory.close(con);
			}
		});
	}
	
	public static void removeFromDb(int itemId)
	{
		Connection con = null;
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection(con);
			
			// Delete datas
			PreparedStatement statement = con.prepareStatement("DELETE FROM cursed_weapon WHERE item_template_id = ?");
			statement.setInt(1, itemId);
			statement.executeUpdate();
			
			statement.close();
		}
		catch (SQLException e)
		{
			_log.error("CursedWeaponsManager: Failed to remove data: ", e);
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
	}
	
	public void saveData()
	{
		for (CursedWeapon cw : _cursedWeapons.values())
		{
			cw.saveData();
		}
	}
	
	public boolean isCursed(int itemId)
	{
		return _cursedWeapons.containsKey(itemId);
	}
	
	public Collection<CursedWeapon> getCursedWeapons()
	{
		return _cursedWeapons.values();
	}
	
	public Set<Integer> getCursedWeaponsIds()
	{
		return _cursedWeapons.keySet();
	}
	
	public CursedWeapon getCursedWeapon(int itemId)
	{
		return _cursedWeapons.get(itemId);
	}
	
	public void givePassive(int itemId)
	{
		try
		{
			_cursedWeapons.get(itemId).giveSkill();
		}
		catch (Exception e)
		{
			_log.warn("", e);
		}
	}
	
	@SuppressWarnings("synthetic-access")
	private static class SingletonHolder
	{
		protected static final CursedWeaponsManager _instance = new CursedWeaponsManager();
	}
}
