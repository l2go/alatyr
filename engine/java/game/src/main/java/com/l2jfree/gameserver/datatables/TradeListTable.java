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
package com.l2jfree.gameserver.datatables;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.l2jfree.L2DatabaseFactory;
import com.l2jfree.gameserver.ThreadPoolManager;
import com.l2jfree.gameserver.model.L2TradeList;
import com.l2jfree.gameserver.model.items.L2ItemInstance;
import com.l2jfree.gameserver.persistence.WorldTransaction;

/**
 *  This class manages buylists from database
 * 
 * @version $Revision: 1.5.4.13 $ $Date: 2005/04/06 16:13:38 $
 */
public class TradeListTable
{
	private final static Logger _log = LoggerFactory.getLogger(TradeListTable.class);
	
	private static final String SELECT_SHOPS = "SELECT id, npc_template_id FROM merchant_shop";
	private static final String SELECT_CUSTOM_SHOPS = "SELECT id, npc_template_id FROM custom_merchant_shop";
	
	/** The goods of one shop, with the stock the shop has left (no row in merchant_stock means the full count). */
	private static final String SELECT_GOODS =
			"SELECT b.item_template_id, b.price, b.stock_count, b.restock_interval_s, s.current_count"
					+ " FROM merchant_buylist b LEFT JOIN merchant_stock s ON s.shop_id = b.merchant_shop_id"
					+ " AND s.item_template_id = b.item_template_id WHERE b.merchant_shop_id = ? ORDER BY b.position";
	private static final String SELECT_CUSTOM_GOODS =
			"SELECT b.item_template_id, b.price, b.stock_count, b.restock_interval_s, s.current_count"
					+ " FROM custom_merchant_buylist b LEFT JOIN merchant_stock s ON s.shop_id = b.merchant_shop_id"
					+ " AND s.item_template_id = b.item_template_id WHERE b.merchant_shop_id = ? ORDER BY b.position";
	
	/** The restock intervals in use and the moment each one restocks next (NULL when it was never saved). */
	private static final String SELECT_RESTOCKS =
			"SELECT DISTINCT b.restock_interval_s, r.next_restock_at FROM merchant_buylist b"
					+ " LEFT JOIN merchant_restock r ON r.restock_interval_s = b.restock_interval_s"
					+ " WHERE b.restock_interval_s IS NOT NULL ORDER BY b.restock_interval_s";
	private static final String SELECT_CUSTOM_RESTOCKS =
			"SELECT DISTINCT b.restock_interval_s, r.next_restock_at FROM custom_merchant_buylist b"
					+ " LEFT JOIN merchant_restock r ON r.restock_interval_s = b.restock_interval_s"
					+ " WHERE b.restock_interval_s IS NOT NULL ORDER BY b.restock_interval_s";
	
	private static final String SAVE_RESTOCK =
			"INSERT INTO merchant_restock (restock_interval_s, next_restock_at) VALUES (?, ?)"
					+ " ON CONFLICT (restock_interval_s) DO UPDATE SET next_restock_at = EXCLUDED.next_restock_at";
	private static final String SAVE_STOCK =
			"INSERT INTO merchant_stock (shop_id, item_template_id, current_count) VALUES (?, ?, ?)"
					+ " ON CONFLICT (shop_id, item_template_id) DO UPDATE SET current_count = EXCLUDED.current_count";
	private static final String DELETE_STOCK = "DELETE FROM merchant_stock WHERE shop_id = ? AND item_template_id = ?";
	
	private int _nextListId;
	private final Map<Integer, L2TradeList> _lists = new LinkedHashMap<Integer, L2TradeList>();
	
	/** Task launching the function for restore count of Item (Clan Hall); the timer is the restock interval in seconds */
	public class RestoreCount implements Runnable
	{
		private final int timer;
		
		public RestoreCount(int time)
		{
			timer = time;
		}
		
		@Override
		public void run()
		{
			restoreCount(timer);
			dataTimerSave(timer);
			ThreadPoolManager.getInstance().scheduleGeneral(new RestoreCount(timer), (long)timer * 1000);
		}
	}
	
	public static TradeListTable getInstance()
	{
		return SingletonHolder._instance;
	}
	
	private TradeListTable()
	{
		_lists.clear();
		load();
	}
	
	private void load(boolean custom)
	{
		Connection con = null;
		/*
		 * Initialize Shop buylist
		 */
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection(con);
			PreparedStatement statement1 = con.prepareStatement(custom ? SELECT_CUSTOM_SHOPS : SELECT_SHOPS);
			ResultSet rset1 = statement1.executeQuery();
			while (rset1.next())
			{
				PreparedStatement statement = con.prepareStatement(custom ? SELECT_CUSTOM_GOODS : SELECT_GOODS);
				statement.setInt(1, rset1.getInt("id"));
				ResultSet rset = statement.executeQuery();
				
				L2TradeList buylist = new L2TradeList(rset1.getInt("id"));
				
				// A shop without an NPC is a GM shop
				int npcTemplateId = rset1.getInt("npc_template_id");
				boolean gmShop = rset1.wasNull();
				buylist.setNpcId(gmShop ? "gm" : String.valueOf(npcTemplateId));
				buylist.setCustom(custom);
				int _itemId = 0;
				int _itemCount = 0;
				int _price = 0;
				
				if (!buylist.isGm() && NpcTable.getInstance().getTemplate(npcTemplateId) == null)
					_log.warn("TradeListTable: Merchant id " + npcTemplateId + " with" + (custom ? " custom " : " ")
							+ "buylist " + buylist.getListId() + " not exist.");
				
				try
				{
					while (rset.next())
					{
						_itemId = rset.getInt("item_template_id");
						// No price means the reference price of the item
						long price = rset.getLong("price");
						_price = rset.wasNull() ? -1 : (int)price;
						// No stock limit means unlimited
						int count = rset.getInt("stock_count");
						if (rset.wasNull())
							count = -1;
						// No stock row means that nothing was sold since the last restock
						int currentCount = rset.getInt("current_count");
						if (rset.wasNull())
							currentCount = -1;
						// No restock interval means that the stock is never restocked; the interval is kept in seconds
						int restoreTime = rset.getInt("restock_interval_s");
						if (rset.wasNull())
							restoreTime = 0;
						
						L2ItemInstance buyItem = ItemTable.getInstance().createDummyItem(_itemId);
						if (buyItem == null)
							continue;
						_itemCount++;
						if (count > -1)
							buyItem.setCountDecrease(true);
						if (_price <= -1)
							_price = ItemTable.getInstance().getTemplate(_itemId).getReferencePrice();
						
						buyItem.setPriceToSell(_price);
						buyItem.setRestoreTime(restoreTime);
						buyItem.setInitCount(count);
						if (currentCount > -1)
							buyItem.setCount(currentCount);
						else
							buyItem.setCount(count);
						
						buylist.addItem(buyItem);
						if (!buylist.isGm() && buyItem.getReferencePrice() > _price && _price != -1)
							_log.warn("TradeListTable: Reference price of item " + _itemId + " in"
									+ (custom ? " custom " : " ") + "buylist " + buylist.getListId()
									+ " higher then sell price.");
					}
				}
				catch (Exception e)
				{
					_log.warn("TradeListTable: Problem with" + (custom ? " custom " : " ") + "buylist "
							+ buylist.getListId() + " item " + _itemId + ".");
				}
				
				if (_itemCount > 0)
				{
					_lists.put(buylist.getListId(), buylist);
					_nextListId = Math.max(_nextListId, buylist.getListId() + 1);
				}
				else
					_log.warn("TradeListTable: Empty " + (custom ? "custom " : "") + " buylist " + buylist.getListId()
							+ ".");
				
				rset.close();
				statement.close();
			}
			rset1.close();
			statement1.close();
			
			_log.info("TradeListTable: Loaded " + _lists.size() + (custom ? " custom " : " ") + "buylists.");
			/*
			 *  Restore Task for reinitialize count of buy item
			 */
			try
			{
				int time = 0;
				long savetimer = 0;
				long currentMillis = System.currentTimeMillis();
				PreparedStatement statement2 = con.prepareStatement(custom ? SELECT_CUSTOM_RESTOCKS : SELECT_RESTOCKS);
				ResultSet rset2 = statement2.executeQuery();
				while (rset2.next())
				{
					time = rset2.getInt("restock_interval_s");
					// An interval that was never saved restocks at once
					Timestamp nextRestock = rset2.getTimestamp("next_restock_at");
					savetimer = nextRestock == null ? 0 : nextRestock.getTime();
					if (savetimer - currentMillis > 0)
						ThreadPoolManager.getInstance().scheduleGeneral(new RestoreCount(time),
								savetimer - System.currentTimeMillis());
					else
						ThreadPoolManager.getInstance().scheduleGeneral(new RestoreCount(time), 0);
				}
				rset2.close();
				statement2.close();
			}
			catch (Exception e)
			{
				_log.warn("TradeListTable:" + (custom ? " custom " : " ") + "could not restore Timer for Item count.",
						e);
			}
		}
		catch (Exception e)
		{
			// problem with initializing buylists, go to next one
			_log.warn("TradeListTable:" + (custom ? " custom " : " ") + "buylists could not be initialized.", e);
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
	}
	
	public void load()
	{
		load(false); // not custom
		load(true); //custom
	}
	
	public void reloadAll()
	{
		_lists.clear();
		
		load();
	}
	
	public L2TradeList getBuyList(int listId)
	{
		if (_lists.containsKey(listId))
			return _lists.get(listId);
		return null;
	}
	
	public List<L2TradeList> getBuyListByNpcId(int npcId)
	{
		List<L2TradeList> lists = new ArrayList<L2TradeList>();
		
		for (L2TradeList list : _lists.values())
		{
			if (list.isGm())
				continue;
			if (npcId == list.getNpcId())
				lists.add(list);
		}
		
		return lists;
	}
	
	protected void restoreCount(int time)
	{
		if (_lists == null)
			return;
		for (L2TradeList list : _lists.values())
		{
			list.restoreCount(time);
		}
	}
	
	protected void dataTimerSave(int time)
	{
		long timerSave = System.currentTimeMillis() + (long)time * 1000;
		Connection con = null;
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection(con);
			PreparedStatement statement = con.prepareStatement(SAVE_RESTOCK);
			statement.setLong(1, time);
			statement.setTimestamp(2, new Timestamp(timerSave));
			statement.executeUpdate();
			statement.close();
		}
		catch (Exception e)
		{
			_log.error("TradeController: Could not update Timer save in Buylist");
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
	}
	
	public void dataCountStore()
	{
		if (_lists == null)
			return;
		
		// The stock of all shops is stored together
		WorldTransaction.run("Storing the stock of the merchants", () -> {
			Connection con = null;
			try
			{
				con = L2DatabaseFactory.getInstance().getConnection(con);
				
				for (L2TradeList list : _lists.values())
				{
					if (list == null)
						continue;
					int listId = list.getListId();
					
					for (L2ItemInstance Item : list.getItems())
					{
						// Only the limited goods have a stock
						if (!Item.getCountDecrease())
							continue;
						
						if (Item.getCount() < Item.getInitCount())
						{
							// A row exists only while the stock is below the initial count
							PreparedStatement statement = con.prepareStatement(SAVE_STOCK);
							statement.setInt(1, listId);
							statement.setInt(2, Item.getItemId());
							statement.setInt(3, (int)Item.getCount());
							statement.executeUpdate();
							statement.close();
						}
						else
						{
							PreparedStatement statement = con.prepareStatement(DELETE_STOCK);
							statement.setInt(1, listId);
							statement.setInt(2, Item.getItemId());
							statement.executeUpdate();
							statement.close();
						}
					}
				}
			}
			catch (SQLException e)
			{
				throw new IllegalStateException("TradeController: Could not store Count Item", e);
			}
			finally
			{
				L2DatabaseFactory.close(con);
			}
		});
	}
	
	@SuppressWarnings("synthetic-access")
	private static class SingletonHolder
	{
		protected static final TradeListTable _instance = new TradeListTable();
	}
}
