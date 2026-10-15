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
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.l2jfree.L2DatabaseFactory;
import com.l2jfree.gameserver.model.entity.Auction;

public class AuctionManager
{
	protected static Logger _log = LoggerFactory.getLogger(AuctionManager.class);
	private final List<Auction> _auctions;
	
	/** Names and starting bids (in adena) of the clan halls sold by NPCs, in the order of {@link #ITEM_INIT_IDS}. */
	private static final String[] ITEM_INIT_NAMES = {
			"Moonstone Hall", "Onyx Hall", "Topaz Hall", "Ruby Hall", "Crystal Hall", "Onyx Hall", "Sapphire Hall",
			"Moonstone Hall", "Emerald Hall", "The Atramental Barracks", "The Scarlet Barracks", "The Viridian Barracks",
			"The Golden Chamber", "The Silver Chamber", "The Mithril Chamber", "Silver Manor", "Gold Manor",
			"The Bronze Chamber", "The Golden Chamber", "The Silver Chamber", "The Mithril Chamber",
			"The Bronze Chamber", "Silver Manor", "Moonstone Hall", "Onyx Hall", "Emerald Hall", "Sapphire Hall",
			"Mont Chamber", "Astaire Chamber", "Aria Chamber", "Yiana Chamber", "Roien Chamber", "Luna Chamber",
			"Traban Chamber", "Eisen Hall", "Heavy Metal Hall", "Molten Ore Hall", "Titan Hall" };

	private static final long[] ITEM_INIT_BIDS = {
			20000000L, 20000000L, 20000000L, 20000000L, 20000000L, 20000000L, 20000000L, 20000000L, 20000000L, 8000000L,
			8000000L, 8000000L, 50000000L, 50000000L, 50000000L, 50000000L, 50000000L, 50000000L, 50000000L, 50000000L,
			50000000L, 50000000L, 50000000L, 50000000L, 50000000L, 50000000L, 50000000L, 50000000L, 50000000L, 50000000L,
			50000000L, 50000000L, 50000000L, 50000000L, 50000000L, 50000000L, 50000000L, 50000000L };

	/** The first auction of an NPC clan hall ended on this moment (epoch milliseconds). */
	private static final long ITEM_INIT_END = 1164841200000L;
	
	private static final int[] ITEM_INIT_IDS = { 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 36, 37, 38, 39, 40,
			41, 42, 43, 44, 45, 46, 47, 48, 49, 50, 51, 52, 53, 54, 55, 56, 57, 58, 59, 60, 61 };
	
	public static final AuctionManager getInstance()
	{
		return SingletonHolder._instance;
	}
	
	private AuctionManager()
	{
		_auctions = new ArrayList<Auction>();
		load();
	}
	
	public void reload()
	{
		_auctions.clear();
		load();
	}
	
	private final void load()
	{
		Connection con = null;
		try
		{
			PreparedStatement statement;
			ResultSet rs;
			con = L2DatabaseFactory.getInstance().getConnection(con);
			statement = con.prepareStatement("SELECT id FROM clan_hall_auction ORDER BY id");
			rs = statement.executeQuery();
			while (rs.next())
				_auctions.add(new Auction(rs.getInt("id")));
			statement.close();
			_log.info("AuctionManager: loaded " + getAuctions().size() + " auction(s)");
		}
		catch (SQLException e)
		{
			_log.error("Exception: AuctionManager.load(): " + e.getMessage(), e);
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
	}
	
	public final Auction getAuction(int auctionId)
	{
		int index = getAuctionIndex(auctionId);
		if (index >= 0)
			return getAuctions().get(index);
		return null;
	}
	
	public final int getAuctionIndex(int auctionId)
	{
		Auction auction;
		for (int i = 0; i < getAuctions().size(); i++)
		{
			auction = getAuctions().get(i);
			if (auction != null && auction.getId() == auctionId)
				return i;
		}
		return -1;
	}
	
	public final List<Auction> getAuctions()
	{
		return _auctions;
	}
	
	/** Init Clan NPC aution */
	public void initNPC(int id)
	{
		Connection con = null;
		int found = -1;
		for (int i = 0; i < ITEM_INIT_IDS.length; i++)
		{
			if (ITEM_INIT_IDS[i] == id)
			{
				found = i;
				break;
			}
		}
		
		if (found == -1)
		{
			_log.warn("Clan Hall auction not found for Id :" + id);
			return;
		}
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection(con);
			PreparedStatement statement;
			statement =
					con.prepareStatement("INSERT INTO clan_hall_auction (id, item_type, item_name, item_quantity, seller_name, seller_clan_name, starting_bid, current_bid, end_at) VALUES (?, 'ClanHall', ?, 1, 'NPC', 'NPC Clan', ?, 0, ?)");
			statement.setInt(1, id);
			statement.setString(2, ITEM_INIT_NAMES[found]);
			statement.setLong(3, ITEM_INIT_BIDS[found]);
			statement.setTimestamp(4, new Timestamp(ITEM_INIT_END));
			statement.execute();
			statement.close();
			_auctions.add(new Auction(id));
		}
		catch (Exception e)
		{
			_log.error("Exception: Auction.initNPC(): " + e.getMessage(), e);
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
	}
	
	@SuppressWarnings("synthetic-access")
	private static class SingletonHolder
	{
		protected static final AuctionManager _instance = new AuctionManager();
	}
}
