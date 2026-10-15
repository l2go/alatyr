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

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.l2jfree.L2DatabaseFactory;
import com.l2jfree.gameserver.ThreadPoolManager;
import com.l2jfree.gameserver.datatables.ClanTable;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.gameobjects.itemcontainer.PlayerInventory;
import com.l2jfree.gameserver.instancemanager.AuctionManager;
import com.l2jfree.gameserver.instancemanager.ClanHallManager;
import com.l2jfree.gameserver.model.clan.L2Clan;
import com.l2jfree.gameserver.model.world.L2World;
import com.l2jfree.gameserver.network.SystemMessageId;
import com.l2jfree.gameserver.persistence.WorldTransaction;

public class Auction
{
	protected static Logger _log = LoggerFactory.getLogger(Auction.class);
	private int _id = 0;
	private long _endDate;
	private int _highestBidderId = 0;
	private String _highestBidderName = "";
	private int _highestBidderMaxBid = 0;
	private int _itemId = 0;
	private String _itemName = "";
	private int _itemObjectId = 0;
	private final int _itemQuantity = 0;
	private String _itemType = "";
	private int _sellerId = 0;
	private String _sellerClanName = "";
	private String _sellerName = "";
	private int _currentBid = 0;
	private int _startingBid = 0;
	
	private final Map<Integer, Bidder> _bidders = new LinkedHashMap<Integer, Bidder>();
	
	private static final String[] ItemTypeName = { "ClanHall" };
	
	public static enum ItemTypeEnum
	{
		ClanHall
	}
	
	public class Bidder
	{
		private final String _name;
		private final String _clanName;
		private int _bid;
		private final Calendar _timeBid;
		
		public Bidder(String name, String clanName, int bid, long timeBid)
		{
			_name = name;
			_clanName = clanName;
			_bid = bid;
			_timeBid = Calendar.getInstance();
			_timeBid.setTimeInMillis(timeBid);
		}
		
		public String getName()
		{
			return _name;
		}
		
		public String getClanName()
		{
			return _clanName;
		}
		
		public int getBid()
		{
			return _bid;
		}
		
		public Calendar getTimeBid()
		{
			return _timeBid;
		}
		
		public void setTimeBid(long timeBid)
		{
			_timeBid.setTimeInMillis(timeBid);
		}
		
		public void setBid(int bid)
		{
			_bid = bid;
		}
	}
	
	/** Task Sheduler for endAuction */
	public class AutoEndTask implements Runnable
	{
		@Override
		public void run()
		{
			endAuction();
		}
	}
	
	/** Constructor */
	public Auction(int auctionId)
	{
		_id = auctionId;
		load();
		startAutoTask();
	}
	
	public Auction(int itemId, L2Clan Clan, long delay, int bid, String name)
	{
		_id = itemId;
		_endDate = System.currentTimeMillis() + delay;
		_itemId = itemId;
		_itemName = name;
		_itemType = "ClanHall";
		_sellerId = Clan.getLeaderId();
		_sellerName = Clan.getLeaderName();
		_sellerClanName = Clan.getName();
		_startingBid = bid;
	}
	
	/** Load auctions */
	private void load()
	{
		Connection con = null;
		try
		{
			PreparedStatement statement;
			ResultSet rs;
			
			con = L2DatabaseFactory.getInstance().getConnection(con);
			
			statement =
					con.prepareStatement("SELECT current_bid, end_at, item_name, item_type, seller_player_id, seller_clan_name, seller_name, starting_bid FROM clan_hall_auction WHERE id = ?");
			statement.setInt(1, getId());
			rs = statement.executeQuery();
			
			while (rs.next())
			{
				_currentBid = Math.toIntExact(rs.getLong("current_bid"));
				_endDate = rs.getTimestamp("end_at").getTime();
				_itemId = getId(); // the auction and the clan hall on sale share the id
				_itemName = rs.getString("item_name");
				_itemObjectId = 0;
				_itemType = rs.getString("item_type");
				_sellerId = rs.getInt("seller_player_id"); // NULL is 0: NPCs sell the hall
				_sellerClanName = rs.getString("seller_clan_name");
				_sellerName = rs.getString("seller_name");
				_startingBid = Math.toIntExact(rs.getLong("starting_bid"));
			}
			statement.close();
			loadBid();
		}
		catch (Exception e)
		{
			_log.error("Exception: Auction.load(): ", e);
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
	}
	
	/** Load bidders **/
	private void loadBid()
	{
		_highestBidderId = 0;
		_highestBidderName = "";
		_highestBidderMaxBid = 0;
		
		Connection con = null;
		try
		{
			PreparedStatement statement;
			ResultSet rs;
			
			con = L2DatabaseFactory.getInstance().getConnection(con);
			
			statement =
					con.prepareStatement("SELECT clan_id, bidder_name, max_bid, clan_name, bid_at FROM clan_hall_auction_bid WHERE clan_hall_auction_id = ? ORDER BY max_bid DESC");
			statement.setInt(1, getId());
			rs = statement.executeQuery();
			
			while (rs.next())
			{
				if (rs.isFirst())
				{
					_highestBidderId = rs.getInt("clan_id");
					_highestBidderName = rs.getString("bidder_name");
					_highestBidderMaxBid = Math.toIntExact(rs.getLong("max_bid"));
				}
				_bidders.put(rs.getInt("clan_id"), new Bidder(rs.getString("bidder_name"), rs.getString("clan_name"),
						Math.toIntExact(rs.getLong("max_bid")), rs.getTimestamp("bid_at").getTime()));
			}
			
			statement.close();
		}
		catch (Exception e)
		{
			_log.error("Exception: Auction.loadBid(): ", e);
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
	}
	
	/** Task Manage */
	private void startAutoTask()
	{
		long currentTime = System.currentTimeMillis();
		long taskDelay = 0;
		if (_endDate <= currentTime)
		{
			_endDate = currentTime + 7 * 24 * 60 * 60 * 1000;
			saveAuctionDate();
		}
		else
		{
			taskDelay = _endDate - currentTime;
		}
		ThreadPoolManager.getInstance().scheduleGeneral(new AutoEndTask(), taskDelay);
	}
	
	public static String getItemTypeName(ItemTypeEnum value)
	{
		return ItemTypeName[value.ordinal()];
	}
	
	/** Save Auction Data End */
	private void saveAuctionDate()
	{
		Connection con = null;
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection(con);
			PreparedStatement statement = con.prepareStatement("UPDATE clan_hall_auction SET end_at = ? WHERE id = ?");
			statement.setTimestamp(1, new Timestamp(_endDate));
			statement.setInt(2, _id);
			statement.execute();
			
			statement.close();
		}
		catch (Exception e)
		{
			_log.error("Exception: saveAuctionDate(): " + e.getMessage(), e);
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
	}
	
	/** Set a bid */
	public synchronized void setBid(final L2Player bidder, final int bid)
	{
		int adena = bid;
		if (getHighestBidderName().equals(bidder.getClan().getLeaderName()))
			adena = bid - getHighestBidderMaxBid();
		final int requiredAdena = adena;
		
		if ((getHighestBidderId() > 0 && bid > getHighestBidderMaxBid())
				|| (getHighestBidderId() == 0 && bid >= getStartingBid()))
		{
			// The adena taken from the clan warehouse and the bid are stored together
			final boolean[] placed = new boolean[1];
			WorldTransaction.run("Clan hall auction bid", () -> {
				if (takeItem(bidder, PlayerInventory.ADENA_ID, requiredAdena))
				{
					updateInDB(bidder, bid);
					bidder.getClan().setAuctionBiddedAt(_id, true);
					placed[0] = true;
				}
			});
			if (placed[0])
				return;
		}
		if ((bid < getStartingBid()) || (bid <= getHighestBidderMaxBid()))
			bidder.sendPacket(SystemMessageId.BID_PRICE_MUST_BE_HIGHER);
	}
	
	/** Return Item in WHC
	 * @param Clan
	 * @param itemId
	 * @param quantity
	 * @param penalty
	 */
	private void returnItem(String Clan, int itemId, int quantity, boolean penalty)
	{
		if (penalty)
			quantity = (int) (quantity * 0.9); //take 10% tax fee if needed
		ClanTable.getInstance().getClanByName(Clan).getWarehouse()
				.addItem("Outbidded", PlayerInventory.ADENA_ID, quantity, null, null);
	}
	
	/** Take Item in WHC
	 * @param bidder
	 * @param itemId
	 * @param quantity
	 */
	private boolean takeItem(L2Player bidder, int itemId, int quantity)
	{
		if (bidder.getClan() != null && bidder.getClan().getWarehouse().getAdena() >= quantity)
		{
			bidder.getClan().getWarehouse()
					.destroyItemByItemId("Auction", PlayerInventory.ADENA_ID, quantity, bidder, bidder);
			return true;
		}
		bidder.sendPacket(SystemMessageId.NOT_ENOUGH_ADENA_IN_CWH);
		return false;
	}
	
	/** Update auction in DB */
	private void updateInDB(L2Player bidder, int bid)
	{
		Connection con = null;
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection(con);
			PreparedStatement statement;
			
			if (getBidders().get(bidder.getClanId()) != null)
			{
				statement =
						con.prepareStatement("UPDATE clan_hall_auction_bid SET bidder_name=?, max_bid=?, bid_at=? WHERE clan_hall_auction_id=? AND clan_id=?");
				statement.setString(1, bidder.getClan().getLeaderName());
				statement.setLong(2, bid);
				statement.setTimestamp(3, new Timestamp(System.currentTimeMillis()));
				statement.setInt(4, getId());
				statement.setInt(5, bidder.getClanId());
				statement.execute();
				statement.close();
			}
			else
			{
				statement =
						con.prepareStatement("INSERT INTO clan_hall_auction_bid (clan_hall_auction_id, clan_id, bidder_name, max_bid, clan_name, bid_at) VALUES (?, ?, ?, ?, ?, ?)");
				statement.setInt(1, getId());
				statement.setInt(2, bidder.getClanId());
				statement.setString(3, bidder.getName());
				statement.setLong(4, bid);
				statement.setString(5, bidder.getClan().getName());
				statement.setTimestamp(6, new Timestamp(System.currentTimeMillis()));
				statement.execute();
				statement.close();
				L2Player highest = L2World.getInstance().getPlayer(_highestBidderName);
				if (highest != null)
					highest.sendMessage("You have been out bidded");
			}
			_highestBidderId = bidder.getClanId();
			_highestBidderMaxBid = bid;
			_highestBidderName = bidder.getClan().getLeaderName();
			if (_bidders.get(_highestBidderId) == null)
			{
				_bidders.put(_highestBidderId,
						new Bidder(_highestBidderName, bidder.getClan().getName(), bid, System.currentTimeMillis()));
			}
			else
			{
				_bidders.get(_highestBidderId).setBid(bid);
				_bidders.get(_highestBidderId).setTimeBid(System.currentTimeMillis());
			}
			bidder.sendPacket(SystemMessageId.BID_IN_CLANHALL_AUCTION);
		}
		catch (Exception e)
		{
			_log.error("Exception: Auction.updateInDB(L2Player bidder, int bid): ", e);
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
	}
	
	/** Remove bids */
	private void removeBids()
	{
		Connection con = null;
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection(con);
			PreparedStatement statement;
			
			statement = con.prepareStatement("DELETE FROM clan_hall_auction_bid WHERE clan_hall_auction_id=?");
			statement.setInt(1, getId());
			statement.execute();
			
			statement.close();
		}
		catch (Exception e)
		{
			_log.error("Exception: Auction.deleteFromDB(): " + e.getMessage(), e);
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
		
		for (Bidder b : _bidders.values())
		{
			if (ClanTable.getInstance().getClanByName(b.getClanName()).getHasHideout() == 0)
				returnItem(b.getClanName(), PlayerInventory.ADENA_ID, b.getBid(), true); // 10 % tax
			else
			{
				L2Player bidder = L2World.getInstance().getPlayer(b.getName());
				if (bidder != null)
					bidder.sendMessage("Congratulations! You have won a ClanHall!");
			}
			ClanTable.getInstance().getClanByName(b.getClanName()).setAuctionBiddedAt(0, true);
		}
		_bidders.clear();
	}
	
	/** Remove auctions */
	public void deleteAuctionFromDB()
	{
		AuctionManager.getInstance().getAuctions().remove(this);
		Connection con = null;
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection(con);
			PreparedStatement statement;
			statement = con.prepareStatement("DELETE FROM clan_hall_auction WHERE id=?");
			statement.setInt(1, _itemId);
			statement.execute();
			statement.close();
		}
		catch (Exception e)
		{
			_log.error("Exception: Auction.deleteFromDB(): " + e.getMessage(), e);
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
	}
	
	/** End of auction */
	public void endAuction()
	{
		if (ClanHallManager.loaded())
		{
			if (_highestBidderId == 0 && _sellerId == 0)
			{
				startAutoTask();
				return;
			}
			if (_highestBidderId == 0 && _sellerId > 0)
			{
				/** If seller haven't sell ClanHall, auction removed,
				 *  THIS MUST BE CONFIRMED */
				int aucId = AuctionManager.getInstance().getAuctionIndex(_id);
				AuctionManager.getInstance().getAuctions().remove(aucId);
				return;
			}
			// The seller refund, the bid refunds and the new owner of the clan hall are stored together
			WorldTransaction.run("Clan hall auction settlement", () -> {
				if (_sellerId > 0)
				{
					returnItem(_sellerClanName, PlayerInventory.ADENA_ID, _highestBidderMaxBid, true);
					returnItem(_sellerClanName, PlayerInventory.ADENA_ID, ClanHallManager.getInstance()
							.getClanHallById(_itemId).getLease(), false);
				}
				deleteAuctionFromDB();
				L2Clan Clan = ClanTable.getInstance().getClanByName(_bidders.get(_highestBidderId).getClanName());
				_bidders.remove(_highestBidderId);
				Clan.setAuctionBiddedAt(0, true);
				removeBids();
				ClanHallManager.getInstance().setOwner(_itemId, Clan);
			});
		}
		else
		{
			/** Task waiting ClanHallManager is loaded every 3s */
			ThreadPoolManager.getInstance().scheduleGeneral(new AutoEndTask(), 3000);
		}
	}
	
	/** Cancel bid */
	public synchronized void cancelBid(final int bidder)
	{
		// The bid and the refund to the clan warehouse are stored together
		WorldTransaction.run("Clan hall auction bid cancel", () -> {
			Connection con = null;
			try
			{
				con = L2DatabaseFactory.getInstance().getConnection(con);
				PreparedStatement statement;
			
				statement = con.prepareStatement("DELETE FROM clan_hall_auction_bid WHERE clan_hall_auction_id=? AND clan_id=?");
				statement.setInt(1, getId());
				statement.setInt(2, bidder);
				statement.execute();
			
				statement.close();
			}
			catch (Exception e)
			{
				_log.error("Exception: Auction.cancelBid(String bidder): " + e.getMessage(), e);
			}
			finally
			{
				L2DatabaseFactory.close(con);
			}
		
			returnItem(_bidders.get(bidder).getClanName(), PlayerInventory.ADENA_ID, _bidders.get(bidder).getBid(), true);
			ClanTable.getInstance().getClanByName(_bidders.get(bidder).getClanName()).setAuctionBiddedAt(0, true);
			_bidders.clear();
			loadBid();
		});
	}
	
	/** Cancel auction */
	public void cancelAuction()
	{
		// The auction and the refunds of its bids are stored together
		WorldTransaction.run("Clan hall auction cancel", () -> {
			deleteAuctionFromDB();
			removeBids();
		});
	}
	
	/** Confirm an auction */
	public void confirmAuction()
	{
		AuctionManager.getInstance().getAuctions().add(this);
		Connection con = null;
		try
		{
			PreparedStatement statement;
			con = L2DatabaseFactory.getInstance().getConnection(con);
			
			statement =
					con.prepareStatement("INSERT INTO clan_hall_auction (id, seller_player_id, seller_name, seller_clan_name, item_type, item_name, item_quantity, starting_bid, current_bid, end_at) VALUES (?,?,?,?,?,?,?,?,?,?)");
			statement.setInt(1, getId());
			if (_sellerId > 0)
				statement.setInt(2, _sellerId);
			else
				statement.setNull(2, Types.INTEGER); // NPCs sell the hall
			statement.setString(3, _sellerName);
			statement.setString(4, _sellerClanName);
			statement.setString(5, _itemType);
			statement.setString(6, _itemName);
			statement.setInt(7, _itemQuantity);
			statement.setLong(8, _startingBid);
			statement.setLong(9, _currentBid);
			statement.setTimestamp(10, new Timestamp(_endDate));
			statement.execute();
			statement.close();
			loadBid();
		}
		catch (Exception e)
		{
			_log.error("Exception: Auction.load(): " + e.getMessage(), e);
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
	}
	
	/** Get var auction */
	public final int getId()
	{
		return _id;
	}
	
	public final int getCurrentBid()
	{
		return _currentBid;
	}
	
	public final long getEndDate()
	{
		return _endDate;
	}
	
	public final int getHighestBidderId()
	{
		return _highestBidderId;
	}
	
	public final String getHighestBidderName()
	{
		return _highestBidderName;
	}
	
	public final int getHighestBidderMaxBid()
	{
		return _highestBidderMaxBid;
	}
	
	public final int getItemId()
	{
		return _itemId;
	}
	
	public final String getItemName()
	{
		return _itemName;
	}
	
	public final int getItemObjectId()
	{
		return _itemObjectId;
	}
	
	public final int getItemQuantity()
	{
		return _itemQuantity;
	}
	
	public final String getItemType()
	{
		return _itemType;
	}
	
	public final int getSellerId()
	{
		return _sellerId;
	}
	
	public final String getSellerName()
	{
		return _sellerName;
	}
	
	public final String getSellerClanName()
	{
		return _sellerClanName;
	}
	
	public final int getStartingBid()
	{
		return _startingBid;
	}
	
	public final Map<Integer, Bidder> getBidders()
	{
		return _bidders;
	}
}
