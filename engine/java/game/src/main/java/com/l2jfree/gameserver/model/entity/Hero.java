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

/**
 * @author godson
 */

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.l2jfree.Config;
import com.l2jfree.L2DatabaseFactory;
import com.l2jfree.gameserver.datatables.ClanTable;
import com.l2jfree.gameserver.datatables.HeroSkillTable;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.gameobjects.itemcontainer.Inventory;
import com.l2jfree.gameserver.model.clan.L2Clan;
import com.l2jfree.gameserver.model.items.L2ItemInstance;
import com.l2jfree.gameserver.model.olympiad.Olympiad;
import com.l2jfree.gameserver.model.skills.L2Skill;
import com.l2jfree.gameserver.model.world.L2World;
import com.l2jfree.gameserver.network.SystemMessageId;
import com.l2jfree.gameserver.network.packets.server.InventoryUpdate;
import com.l2jfree.gameserver.network.packets.server.SocialAction;
import com.l2jfree.gameserver.network.packets.server.SystemMessage;
import com.l2jfree.gameserver.persistence.WorldTransaction;
import com.l2jfree.gameserver.templates.StatsSet;

public class Hero
{
	private final static Logger _log = LoggerFactory.getLogger(Hero.class);
	
	private static final String GET_HEROES = "SELECT hero.player_id, "
			+ "player.name, hero.class_id, hero.hero_count, hero.is_current "
			+ "FROM hero JOIN player ON player.id = hero.player_id WHERE hero.is_current";
	private static final String GET_ALL_HEROES = "SELECT hero.player_id, "
			+ "player.name, hero.class_id, hero.hero_count, hero.is_current "
			+ "FROM hero JOIN player ON player.id = hero.player_id";
	private static final String UPDATE_ALL = "UPDATE hero SET is_current = false";
	private static final String INSERT_HERO = "INSERT INTO hero (player_id, class_id, hero_count, is_current) VALUES (?,?,?,?)";
	private static final String UPDATE_HERO = "UPDATE hero SET hero_count = ?, " + "is_current = ?" + " WHERE player_id = ?";
	private static final String GET_CLAN_ALLY = "SELECT player.clan_id AS clan_id, "
			+ "coalesce(clan.alliance_id, 0) AS alliance_id FROM player "
			+ "LEFT JOIN clan ON clan.id = player.clan_id " + "WHERE player.id = ?";
	private static final String GET_CLAN_NAME = "SELECT name FROM clan "
			+ "WHERE id = (SELECT clan_id FROM player WHERE name = ?)";
	// delete hero items
	private static final String DELETE_ITEMS = "DELETE FROM item WHERE item_template_id IN "
			+ "(6842, 6611, 6612, 6613, 6614, 6615, 6616, 6617, 6618, 6619, 6620, 6621, 9388, 9389, 9390) "
			+ "AND (owner_player_id IS NULL OR owner_player_id NOT IN (SELECT id FROM player WHERE access_level > 0))";
	private static final String DELETE_SKILLS = "DELETE FROM player_skill WHERE skill_id IN "
			+ "(395, 396, 1374, 1375, 1376) "
			+ "AND player_id NOT IN (SELECT id FROM player WHERE access_level > 0)";
	
	private static Map<Integer, StatsSet> _heroes;
	private static Map<Integer, StatsSet> _completeHeroes;
	
	public static final String COUNT = "count";
	public static final String PLAYED = "played";
	public static final String CLAN_NAME = "clan_name";
	public static final String CLAN_CREST = "clan_crest";
	public static final String ALLY_NAME = "ally_name";
	public static final String ALLY_CREST = "ally_crest";
	
	public static Hero getInstance()
	{
		return SingletonHolder._instance;
	}
	
	private Hero()
	{
		init();
	}
	
	private void init()
	{
		_heroes = new LinkedHashMap<Integer, StatsSet>();
		_completeHeroes = new LinkedHashMap<Integer, StatsSet>();
		
		Connection con = null;
		Connection con2 = null;
		
		PreparedStatement statement;
		PreparedStatement statement2;
		
		ResultSet rset;
		ResultSet rset2;
		
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection(con);
			con2 = L2DatabaseFactory.getInstance().getConnection(con2);
			statement = con.prepareStatement(GET_HEROES);
			rset = statement.executeQuery();
			
			while (rset.next())
			{
				StatsSet hero = new StatsSet();
				int charId = rset.getInt("player_id");
				hero.set(Olympiad.CHAR_NAME, rset.getString("name"));
				hero.set(Olympiad.CLASS_ID, rset.getInt("class_id"));
				hero.set(COUNT, rset.getInt("hero_count"));
				hero.set(PLAYED, rset.getBoolean("is_current") ? 1 : 0);
				
				statement2 = con2.prepareStatement(GET_CLAN_ALLY);
				statement2.setInt(1, charId);
				rset2 = statement2.executeQuery();
				
				initRelationBetweenHeroAndClan(rset2, hero);
				
				rset2.close();
				statement2.close();
				
				_heroes.put(charId, hero);
			}
			
			rset.close();
			statement.close();
			
			statement = con.prepareStatement(GET_ALL_HEROES);
			rset = statement.executeQuery();
			
			while (rset.next())
			{
				StatsSet hero = new StatsSet();
				int charId = rset.getInt("player_id");
				hero.set(Olympiad.CHAR_NAME, rset.getString("name"));
				hero.set(Olympiad.CLASS_ID, rset.getInt("class_id"));
				hero.set(COUNT, rset.getInt("hero_count"));
				hero.set(PLAYED, rset.getBoolean("is_current") ? 1 : 0);
				
				statement2 = con2.prepareStatement(GET_CLAN_ALLY);
				statement2.setInt(1, charId);
				rset2 = statement2.executeQuery();
				
				initRelationBetweenHeroAndClan(rset2, hero);
				
				rset2.close();
				statement2.close();
				
				_completeHeroes.put(charId, hero);
			}
			
			rset.close();
			statement.close();
		}
		catch (SQLException e)
		{
			_log.warn("HeroSystem: Couldnt load Heroes", e);
		}
		finally
		{
			L2DatabaseFactory.close(con);
			L2DatabaseFactory.close(con2);
		}
		
		_log.info("HeroSystem: Loaded " + _heroes.size() + " Heroes.");
		_log.info("HeroSystem: Loaded " + _completeHeroes.size() + " all time Heroes.");
	}
	
	/**
	 * @param resultSet
	 * @param hero
	 * @throws SQLException
	 */
	private void initRelationBetweenHeroAndClan(ResultSet resultSet, StatsSet hero) throws SQLException
	{
		if (resultSet.next())
		{
			int clanId = resultSet.getInt("clan_id");
			int allyId = resultSet.getInt("alliance_id");
			
			String clanName = "";
			String allyName = "";
			int clanCrest = 0;
			int allyCrest = 0;
			
			L2Clan clan = ClanTable.getInstance().getClan(clanId);
			
			if (clan != null)
			{
				if (clanId > 0)
				{
					clanName = clan.getName();
					clanCrest = clan.getCrestId();
					
					if (allyId > 0)
					{
						allyName = clan.getAllyName();
						allyCrest = clan.getAllyCrestId();
					}
				}
				
				hero.set(CLAN_CREST, clanCrest);
				hero.set(CLAN_NAME, clanName);
				hero.set(ALLY_CREST, allyCrest);
				hero.set(ALLY_NAME, allyName);
			}
			else if (hero.getInteger(PLAYED, 1) == 1)
			{
				_log.info("Hero: initRelationBetweenHeroAndClan: " + hero.getString(Olympiad.CHAR_NAME));
			}
		}
	}
	
	public Map<Integer, StatsSet> getHeroes()
	{
		return _heroes;
	}
	
	public synchronized void computeNewHeroes(List<StatsSet> newHeroes)
	{
		if (!_heroes.isEmpty())
		{
			for (StatsSet hero : _heroes.values())
			{
				String name = hero.getString(Olympiad.CHAR_NAME);
				
				L2Player player = L2World.getInstance().getPlayer(name);
				
				if (player == null)
					continue;
				try
				{
					player.setHero(false);
					
					for (int i = 0; i < Inventory.PAPERDOLL_TOTALSLOTS; i++)
					{
						L2ItemInstance equippedItem = player.getInventory().getPaperdollItem(i);
						if (equippedItem != null && equippedItem.isHeroItem())
							player.getInventory().unEquipItemInSlotAndRecord(i);
					}
					
					for (L2ItemInstance item : player.getInventory().getAvailableItems(false, true))
					{
						if (item != null && item.isHeroItem())
						{
							player.destroyItem("Hero", item, null, true);
							
							InventoryUpdate iu = new InventoryUpdate();
							iu.addRemovedItem(item);
							player.sendPacket(iu);
						}
					}
					
					player.broadcastUserInfo();
				}
				catch (NullPointerException e)
				{
					_log.warn("", e);
				}
			}
		}
		
		if (newHeroes.size() == 0)
		{
			updateHeroes(true);
			_heroes.clear();
			return;
		}
		
		Map<Integer, StatsSet> heroes = new LinkedHashMap<Integer, StatsSet>();
		
		for (StatsSet hero : newHeroes)
		{
			int charId = hero.getInteger(Olympiad.CHAR_ID);
			
			if (_completeHeroes != null && _completeHeroes.containsKey(charId))
			{
				StatsSet oldHero = _completeHeroes.get(charId);
				int count = oldHero.getInteger(COUNT);
				oldHero.set(COUNT, count + 1);
				oldHero.set(PLAYED, 1);
				
				heroes.put(charId, oldHero);
			}
			else
			{
				StatsSet newHero = new StatsSet();
				newHero.set(Olympiad.CHAR_NAME, hero.getString(Olympiad.CHAR_NAME));
				newHero.set(Olympiad.CLASS_ID, hero.getInteger(Olympiad.CLASS_ID));
				newHero.set(COUNT, 1);
				newHero.set(PLAYED, 1);
				
				heroes.put(charId, newHero);
			}
		}
		
		_heroes.clear();
		_heroes.putAll(heroes);
		heroes.clear();
		
		// The old heroes are reset, their items and skills are removed and the new heroes are stored together.
		WorldTransaction.run("Hero selection", () -> {
			updateHeroes(true);
			deleteItemsInDb();
			deleteSkillsInDb();
			updateHeroes(false);
		});
		
		for (StatsSet hero : _heroes.values())
		{
			String name = hero.getString(Olympiad.CHAR_NAME);
			
			L2Player player = L2World.getInstance().getPlayer(name);
			
			if (player != null)
			{
				player.broadcastPacket(new SocialAction(player.getObjectId(), 16));
				player.setHero(true);
				L2Clan clan = player.getClan();
				if (clan != null)
				{
					clan.setReputationScore(clan.getReputationScore() + Config.HERO_POINTS, true);
					SystemMessage sm =
							new SystemMessage(
									SystemMessageId.CLAN_MEMBER_C1_BECAME_HERO_AND_GAINED_S2_REPUTATION_POINTS);
					sm.addString(name);
					sm.addNumber(Config.HERO_POINTS);
					clan.broadcastToOnlineMembers(sm);
				}
				player.broadcastUserInfo();
				
				for (L2Skill skill : HeroSkillTable.getHeroSkills())
					player.addSkill(skill);
			}
			else
			{
				Connection con = null;
				
				try
				{
					con = L2DatabaseFactory.getInstance().getConnection(con);
					PreparedStatement statement = con.prepareStatement(GET_CLAN_NAME);
					statement.setString(1, name);
					ResultSet rset = statement.executeQuery();
					if (rset.next())
					{
						String clanName = rset.getString("name");
						if (clanName != null)
						{
							L2Clan clan = ClanTable.getInstance().getClanByName(clanName);
							if (clan != null)
							{
								clan.setReputationScore(clan.getReputationScore() + Config.HERO_POINTS, true);
								SystemMessage sm =
										new SystemMessage(
												SystemMessageId.CLAN_MEMBER_C1_BECAME_HERO_AND_GAINED_S2_REPUTATION_POINTS);
								sm.addString(name);
								sm.addNumber(Config.HERO_POINTS);
								clan.broadcastToOnlineMembers(sm);
							}
						}
					}
					
					rset.close();
					statement.close();
				}
				catch (Exception e)
				{
					_log.warn("HeroSystem: Couldnt get Clanname of " + name, e);
				}
				finally
				{
					L2DatabaseFactory.close(con);
				}
			}
		}
	}
	
	public void updateHeroes(boolean setDefault)
	{
		Connection con = null;
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection();
			if (setDefault)
			{
				try
				{
					PreparedStatement statement = con.prepareStatement(UPDATE_ALL);
					statement.execute();
					statement.close();
				}
				catch (SQLException e)
				{
					_log.warn("HeroSystem: Couldnt update all Heroes", e);
				}
			}
			else
			{
				PreparedStatement statement;
				
				for (Integer heroId : _heroes.keySet())
				{
					StatsSet hero = _heroes.get(heroId);
					
					if (_completeHeroes == null || !_completeHeroes.containsKey(heroId))
					{
						try
						{
							statement = con.prepareStatement(INSERT_HERO);
							statement.setInt(1, heroId);
							statement.setInt(2, hero.getInteger(Olympiad.CLASS_ID));
							statement.setInt(3, hero.getInteger(COUNT));
							statement.setBoolean(4, hero.getInteger(PLAYED) == 1);
							statement.execute();
							statement.close();
							
							PreparedStatement statement2 = con.prepareStatement(GET_CLAN_ALLY);
							statement2.setInt(1, heroId);
							ResultSet rset2 = statement2.executeQuery();
							
							initRelationBetweenHeroAndClan(rset2, hero);
							
							rset2.close();
							statement2.close();
							
							_heroes.put(heroId, hero);
							
							_completeHeroes.put(heroId, hero);
						}
						catch (SQLException e)
						{
							_log.warn("HeroSystem: Couldnt insert Heroes", e);
						}
					}
					else
					{
						try
						{
							statement = con.prepareStatement(UPDATE_HERO);
							statement.setInt(1, hero.getInteger(COUNT));
							statement.setBoolean(2, hero.getInteger(PLAYED) == 1);
							statement.setInt(3, heroId);
							statement.execute();
							statement.close();
						}
						catch (SQLException e)
						{
							_log.warn("HeroSystem: Couldnt update Heroes", e);
						}
					}
				}
			}
		}
		catch (Exception e)
		{
			_log.error("", e);
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
	}
	
	private void deleteItemsInDb()
	{
		Connection con = null;
		
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection(con);
			PreparedStatement statement = con.prepareStatement(DELETE_ITEMS);
			statement.execute();
			statement.close();
		}
		catch (SQLException e)
		{
			_log.error("", e);
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
	}
	
	private void deleteSkillsInDb()
	{
		Connection con = null;
		
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection(con);
			PreparedStatement statement = con.prepareStatement(DELETE_SKILLS);
			statement.execute();
			statement.close();
		}
		catch (SQLException e)
		{
			_log.error(e.getMessage(), e);
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
	}
	
	@SuppressWarnings("synthetic-access")
	private static class SingletonHolder
	{
		protected static final Hero _instance = new Hero();
	}
}
