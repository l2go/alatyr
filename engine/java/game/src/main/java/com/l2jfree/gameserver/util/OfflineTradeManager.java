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
package com.l2jfree.gameserver.util;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.l2jfree.L2DatabaseFactory;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.model.TradeList;
import com.l2jfree.gameserver.model.TradeList.TradeItem;
import com.l2jfree.gameserver.model.items.manufacture.L2ManufactureItem;
import com.l2jfree.gameserver.model.items.manufacture.L2ManufactureList;
import com.l2jfree.gameserver.model.world.L2World;
import com.l2jfree.gameserver.network.packets.server.RecipeShopMsg;
import com.l2jfree.gameserver.persistence.WorldTransaction;

/**
 * @author hex1r0
 */
public final class OfflineTradeManager
{
	private static final Logger _log = LoggerFactory.getLogger(OfflineTradeManager.class);
	
	private static final String SELECT_STORES = "SELECT player_id, store_type, title FROM offline_store";
	private static final String SELECT_STORE_ITEMS =
			"SELECT item_id, item_template_id, recipe_id, count, price FROM offline_store_item WHERE player_id = ? ORDER BY id";
	private static final String INSERT_STORE = "INSERT INTO offline_store (player_id, store_type, title) VALUES (?, ?, ?)";
	private static final String INSERT_STORE_ITEM =
			"INSERT INTO offline_store_item (player_id, item_id, item_template_id, recipe_id, count, price) VALUES (?, ?, ?, ?, ?, ?)";
	private static final String DELETE_STORES = "DELETE FROM offline_store";
	
	private int _playerCount = 0;
	private int _itemCount = 0;
	private int _recipeCount = 0;
	
	public static OfflineTradeManager getInstance()
	{
		return SingletonHolder.INSTANCE;
	}
	
	public final void restore()
	{
		_log.info("OfflineTradeManager: Restorring...");
		_playerCount = 0;
		_itemCount = 0;
		_recipeCount = 0;
		
		Connection con = null;
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection(con);
			
			PreparedStatement statement = con.prepareStatement(SELECT_STORES);
			ResultSet rset = statement.executeQuery();
			
			while (rset.next())
			{
				int charId = rset.getInt("player_id");
				int privateStoreType = storeType(rset.getString("store_type"));
				// A store without a title has none
				String msg = rset.getString("title");
				if (msg == null)
					msg = "";
				
				L2Player p = L2Player.load(charId);
				if (p == null)
					continue;
				
				p.setOnlineStatus(true);
				L2World.getInstance().storeObject(p);
				L2World.getInstance().addOnlinePlayer(p);
				p.spawnMe();
				
				PreparedStatement st2 = con.prepareStatement(SELECT_STORE_ITEMS);
				st2.setInt(1, charId);
				ResultSet rset2 = st2.executeQuery();
				
				L2ManufactureList manufactureList = new L2ManufactureList();
				while (rset2.next())
				{
					switch (privateStoreType)
					{
						case L2Player.STORE_PRIVATE_PACKAGE_SELL:
						case L2Player.STORE_PRIVATE_SELL:
							p.getSellList().addItem(rset2.getInt("item_id"), rset2.getLong("count"), rset2.getLong("price"));
							_itemCount++;
							break;
						case L2Player.STORE_PRIVATE_BUY:
							p.getBuyList().addItemByItemId(rset2.getInt("item_template_id"), rset2.getLong("count"),
									rset2.getLong("price"));
							_itemCount++;
							break;
						case L2Player.STORE_PRIVATE_MANUFACTURE:
							manufactureList.add(new L2ManufactureItem(rset2.getInt("recipe_id"), rset2.getLong("price")));
							_recipeCount++;
							break;
					}
				}
				rset2.close();
				st2.close();
				
				switch (privateStoreType)
				{
					case L2Player.STORE_PRIVATE_PACKAGE_SELL:
						p.getSellList().setPackaged(true);
						//$FALL-THROUGH$
					case L2Player.STORE_PRIVATE_SELL:
						p.getSellList().setTitle(msg);
						p.tryOpenPrivateSellStore(p.getSellList().isPackaged());
						break;
					case L2Player.STORE_PRIVATE_BUY:
						p.getBuyList().setTitle(msg);
						p.tryOpenPrivateBuyStore();
						break;
					case L2Player.STORE_PRIVATE_MANUFACTURE:
						manufactureList.setStoreName(msg);
						p.setCreateList(manufactureList);
						p.broadcastPacket(new RecipeShopMsg(p));
						break;
				}
				
				p.setPrivateStoreType(privateStoreType);
				p.sitDown();
				p.enterOfflineMode();
				p.broadcastUserInfo();
				
				_playerCount++;
			}
			
			rset.close();
			statement.close();
		}
		catch (Exception e)
		{
			_log.error("Could not restore char private store list: ", e);
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
		_log.info("OfflineTradeManager: Restored " + _playerCount + " offline traders with " + _itemCount
				+ " items and " + _recipeCount + " recipes!");
	}
	
	public void store()
	{
		_log.info("OfflineTradeManager: Storring...");
		_playerCount = 0;
		_itemCount = 0;
		_recipeCount = 0;
		
		cleanTables();
		try
		{
			for (L2Player p : L2World.getInstance().getAllPlayers())
			{
				try
				{
					if (p.isInOfflineMode())
						storePlayer(p);
					//new Disconnection(p).defaultSequence(true);
				}
				catch (Throwable t)
				{
					t.printStackTrace();
				}
			}
		}
		catch (Exception e)
		{
			_log.error("OfflineTradeManager: Could not store char private store list: ", e);
		}
		_log.info("OfflineTradeManager: Stored " + _playerCount + " offline traders with " + _itemCount + " items and "
				+ _recipeCount + " recipes!");
	}
	
	/** Stores the private store of one offline player with its lines, all or nothing. */
	private void storePlayer(final L2Player p)
	{
		final int privateStoreType = p.getPrivateStoreType();
		final int[] counts = new int[2]; // items, recipes
		
		boolean stored = WorldTransaction.run("Storing the offline store of player " + p.getObjectId(), () -> {
			Connection con = null;
			try
			{
				con = L2DatabaseFactory.getInstance().getConnection(con);
				
				// The store row comes first, the lines refer to it
				TradeList tradeList = null;
				L2ManufactureList manufactureList = null;
				String title = "";
				switch (privateStoreType)
				{
					case L2Player.STORE_PRIVATE_SELL:
					case L2Player.STORE_PRIVATE_PACKAGE_SELL:
						tradeList = p.getSellList();
						break;
					case L2Player.STORE_PRIVATE_BUY:
						tradeList = p.getBuyList();
						break;
					case L2Player.STORE_PRIVATE_MANUFACTURE:
						manufactureList = p.getCreateList();
						break;
				}
				if (manufactureList != null)
					title = manufactureList.getStoreName();
				else if (tradeList != null)
					title = tradeList.getTitle();
				
				PreparedStatement st = con.prepareStatement(INSERT_STORE);
				st.setInt(1, p.getObjectId());
				st.setString(2, storeTypeName(privateStoreType));
				// A store without a title has none
				if (title == null || title.isEmpty())
					st.setNull(3, Types.VARCHAR);
				else
					st.setString(3, title);
				st.execute();
				st.close();
				
				switch (privateStoreType)
				{
					case L2Player.STORE_PRIVATE_SELL:
					case L2Player.STORE_PRIVATE_PACKAGE_SELL:
						for (TradeItem i : tradeList.getItems())
						{
							insertLine(con, p.getObjectId(), i.getObjectId(), null, null, i.getCount(), i.getPrice());
							counts[0]++;
						}
						break;
					case L2Player.STORE_PRIVATE_BUY:
						for (TradeItem i : tradeList.getItems())
						{
							insertLine(con, p.getObjectId(), null, i.getItem().getItemId(), null, i.getCount(),
									i.getPrice());
							counts[0]++;
						}
						break;
					case L2Player.STORE_PRIVATE_MANUFACTURE:
						if (manufactureList != null)
						{
							for (L2ManufactureItem i : manufactureList.getList())
							{
								// A recipe has no count
								insertLine(con, p.getObjectId(), null, null, i.getRecipeId(), null, i.getCost());
								counts[1]++;
							}
						}
						break;
				}
			}
			catch (SQLException e)
			{
				throw new IllegalStateException(e);
			}
			finally
			{
				L2DatabaseFactory.close(con);
			}
		});
		
		if (stored)
		{
			_playerCount++;
			_itemCount += counts[0];
			_recipeCount += counts[1];
		}
	}
	
	/** Stores one line of a store; the line has an item, an item template, or a recipe, and a count unless it is a recipe. */
	private static void insertLine(Connection con, int playerId, Integer itemId, Integer itemTemplateId,
			Integer recipeId, Long count, long price) throws SQLException
	{
		PreparedStatement st = con.prepareStatement(INSERT_STORE_ITEM);
		try
		{
			st.setInt(1, playerId);
			setInteger(st, 2, itemId);
			setInteger(st, 3, itemTemplateId);
			setInteger(st, 4, recipeId);
			if (count == null)
				st.setNull(5, Types.BIGINT);
			else
				st.setLong(5, count);
			st.setLong(6, price);
			st.execute();
		}
		finally
		{
			st.close();
		}
	}
	
	private static void setInteger(PreparedStatement st, int index, Integer value) throws SQLException
	{
		if (value == null)
			st.setNull(index, Types.INTEGER);
		else
			st.setInt(index, value);
	}
	
	/** @return the name the table stores for a private store type (the name of the constant in L2Player) */
	private static String storeTypeName(int privateStoreType)
	{
		switch (privateStoreType)
		{
			case L2Player.STORE_PRIVATE_SELL:
				return "STORE_PRIVATE_SELL";
			case L2Player.STORE_PRIVATE_BUY:
				return "STORE_PRIVATE_BUY";
			case L2Player.STORE_PRIVATE_MANUFACTURE:
				return "STORE_PRIVATE_MANUFACTURE";
			case L2Player.STORE_PRIVATE_PACKAGE_SELL:
				return "STORE_PRIVATE_PACKAGE_SELL";
			default:
				throw new IllegalArgumentException("Not a store that is kept while the player is offline: "
						+ privateStoreType);
		}
	}
	
	/** @return the private store type of a name the table stores */
	private static int storeType(String name)
	{
		switch (name)
		{
			case "STORE_PRIVATE_SELL":
				return L2Player.STORE_PRIVATE_SELL;
			case "STORE_PRIVATE_BUY":
				return L2Player.STORE_PRIVATE_BUY;
			case "STORE_PRIVATE_MANUFACTURE":
				return L2Player.STORE_PRIVATE_MANUFACTURE;
			case "STORE_PRIVATE_PACKAGE_SELL":
				return L2Player.STORE_PRIVATE_PACKAGE_SELL;
			default:
				throw new IllegalArgumentException("Unknown private store type " + name);
		}
	}
	
	private void cleanTables()
	{
		Connection con = null;
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection(con);
			// The lines of the stores go with them
			PreparedStatement statement = con.prepareStatement(DELETE_STORES);
			statement.execute();
			statement.close();
		}
		catch (Exception e)
		{
			_log.warn("OfflineTradeManager: Could not clear table: ", e);
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
	}
	
	private static final class SingletonHolder
	{
		public static final OfflineTradeManager INSTANCE = new OfflineTradeManager();
	}
}
