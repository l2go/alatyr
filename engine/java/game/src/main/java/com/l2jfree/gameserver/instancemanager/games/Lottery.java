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
package com.l2jfree.gameserver.instancemanager.games;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Calendar;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.l2jfree.Config;
import com.l2jfree.L2DatabaseFactory;
import com.l2jfree.gameserver.Announcements;
import com.l2jfree.gameserver.ThreadPoolManager;
import com.l2jfree.gameserver.model.items.L2ItemInstance;
import com.l2jfree.gameserver.network.SystemMessageId;
import com.l2jfree.gameserver.network.packets.server.SystemMessage;
import com.l2jfree.lang.L2Math;
import com.l2jfree.tools.random.Rnd;

public class Lottery
{
	public static final long SECOND = 1000;
	public static final long MINUTE = 60000;
	
	protected static Logger _log = LoggerFactory.getLogger(Lottery.class);
	
	private static final String INSERT_LOTTERY =
			"INSERT INTO lottery_round (id, end_at, prize, next_prize) VALUES (?, ?, ?, ?)";
	private static final String UPDATE_PRICE = "UPDATE lottery_round SET prize = ?, next_prize = ? WHERE id = ?";
	private static final String UPDATE_LOTTERY =
			"UPDATE lottery_round SET is_finished = true, prize = ?, next_prize = ?, winning_mask_low = ?, winning_mask_high = ?, "
					+ "first_prize = ?, second_prize = ?, third_prize = ? WHERE id = ?";
	private static final String SELECT_LAST_LOTTERY =
			"SELECT id, prize, next_prize, end_at, is_finished FROM lottery_round ORDER BY id DESC LIMIT 1";
	private static final String SELECT_LOTTERY_ITEM =
			"SELECT enchant_level, custom_type2 FROM item WHERE item_template_id = 4442 AND custom_type1 = ?";
	private static final String SELECT_LOTTERY_TICKET =
			"SELECT winning_mask_low, winning_mask_high, first_prize, second_prize, third_prize FROM lottery_round WHERE id = ?";
	
	protected int _number;
	protected long _prize;
	protected boolean _isSellingTickets;
	protected boolean _isStarted;
	protected long _enddate;
	
	private Lottery()
	{
		_number = 1;
		_prize = Config.ALT_LOTTERY_PRIZE;
		_isSellingTickets = false;
		_isStarted = false;
		_enddate = System.currentTimeMillis();
		
		if (Config.ALLOW_LOTTERY)
			(new startLottery()).run();
	}
	
	public static Lottery getInstance()
	{
		return SingletonHolder._instance;
	}
	
	public int getId()
	{
		return _number;
	}
	
	public long getPrize()
	{
		return _prize;
	}
	
	public long getEndDate()
	{
		return _enddate;
	}
	
	public void increasePrize(long count)
	{
		_prize += count;
		Connection con = null;
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection(con);
			PreparedStatement statement;
			statement = con.prepareStatement(UPDATE_PRICE);
			statement.setLong(1, getPrize());
			statement.setLong(2, getPrize());
			statement.setInt(3, getId());
			statement.execute();
			statement.close();
		}
		catch (SQLException e)
		{
			_log.warn("Lottery: Could not increase current lottery prize: ", e);
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
	}
	
	public boolean isSellableTickets()
	{
		return _isSellingTickets;
	}
	
	public boolean isStarted()
	{
		return _isStarted;
	}
	
	private class startLottery implements Runnable
	{
		protected startLottery()
		{
			// Do nothing
		}
		
		@Override
		public void run()
		{
			if (restoreLotteryData())
			{
				announceLottery();
				
				scheduleEndOfLottery();
				
				createNewLottery();
			}
		}
		
		private boolean restoreLotteryData()
		{
			Connection con = null;
			PreparedStatement statement;
			try
			{
				con = L2DatabaseFactory.getInstance().getConnection(con);
				statement = con.prepareStatement(SELECT_LAST_LOTTERY);
				ResultSet rset = statement.executeQuery();
				
				if (rset.next())
				{
					_number = rset.getInt("id");
					
					if (rset.getBoolean("is_finished"))
					{
						_number++;
						_prize = rset.getLong("next_prize");
					}
					else
					{
						_prize = rset.getLong("prize");
						_enddate = rset.getTimestamp("end_at").getTime();
						
						if (_enddate <= System.currentTimeMillis() + 2 * MINUTE)
						{
							(new finishLottery()).run();
							rset.close();
							statement.close();
							return false;
						}
						
						if (_enddate > System.currentTimeMillis())
						{
							_isStarted = true;
							ThreadPoolManager.getInstance().scheduleGeneral(new finishLottery(),
									_enddate - System.currentTimeMillis());
							
							if (_enddate > System.currentTimeMillis() + 12 * MINUTE)
							{
								_isSellingTickets = true;
								ThreadPoolManager.getInstance().scheduleGeneral(new stopSellingTickets(),
										_enddate - System.currentTimeMillis() - 10 * MINUTE);
							}
							rset.close();
							statement.close();
							return false;
						}
					}
				}
				rset.close();
				statement.close();
			}
			catch (SQLException e)
			{
				_log.warn("Lottery: Could not restore lottery data: ", e);
			}
			finally
			{
				L2DatabaseFactory.close(con);
			}
			
			return true;
		}
		
		private void announceLottery()
		{
			if (_log.isDebugEnabled())
				_log.info("Lottery: Starting ticket sell for lottery #" + getId() + ".");
			_isSellingTickets = true;
			_isStarted = true;
			
			Announcements.getInstance().announceToAll(
					"Lottery tickets are now available for Lucky Lottery #" + getId() + ".");
		}
		
		private void scheduleEndOfLottery()
		{
			Calendar finishtime = Calendar.getInstance();
			finishtime.setTimeInMillis(_enddate);
			finishtime.set(Calendar.MINUTE, 0);
			finishtime.set(Calendar.SECOND, 0);
			finishtime.add(Calendar.DAY_OF_MONTH, 7);
			finishtime.set(Calendar.DAY_OF_WEEK, 6);
			finishtime.set(Calendar.HOUR_OF_DAY, 7);
			_enddate = finishtime.getTimeInMillis();
			
			ThreadPoolManager.getInstance().scheduleGeneral(new stopSellingTickets(),
					_enddate - System.currentTimeMillis() - 10 * MINUTE);
			ThreadPoolManager.getInstance().scheduleGeneral(new finishLottery(), _enddate - System.currentTimeMillis());
		}
		
		/**
		 * @param statement
		 */
		private void createNewLottery()
		{
			Connection con = null;
			PreparedStatement statement;
			try
			{
				con = L2DatabaseFactory.getInstance().getConnection(con);
				statement = con.prepareStatement(INSERT_LOTTERY);
				statement.setInt(1, getId());
				statement.setTimestamp(2, new Timestamp(getEndDate()));
				statement.setLong(3, getPrize());
				statement.setLong(4, getPrize());
				statement.execute();
				statement.close();
			}
			catch (SQLException e)
			{
				_log.warn("Lottery: Could not store new lottery data: ", e);
			}
			finally
			{
				L2DatabaseFactory.close(con);
			}
		}
	}
	
	private class stopSellingTickets implements Runnable
	{
		protected stopSellingTickets()
		{
			// Do nothing
		}
		
		@Override
		public void run()
		{
			if (_log.isDebugEnabled())
				_log.info("Lottery: Stopping ticket sell for lottery #" + getId() + ".");
			_isSellingTickets = false;
			
			Announcements.getInstance().announceToAll(SystemMessageId.LOTTERY_TICKET_SALES_TEMP_SUSPENDED);
		}
	}
	
	private class finishLottery implements Runnable
	{
		protected finishLottery()
		{
			// Do nothing
		}
		
		@Override
		public void run()
		{
			if (_log.isDebugEnabled())
				_log.info("Lottery: Ending lottery #" + getId() + ".");
			
			int[] luckynums = new int[5];
			int luckynum = 0;
			
			for (int i = 0; i < 5; i++)
			{
				boolean found = true;
				
				while (found)
				{
					luckynum = Rnd.get(20) + 1;
					found = false;
					
					for (int j = 0; j < i; j++)
						if (luckynums[j] == luckynum)
							found = true;
				}
				
				luckynums[i] = luckynum;
			}
			
			if (_log.isDebugEnabled())
				_log.debug("Lottery: The lucky numbers are " + luckynums[0] + ", " + luckynums[1] + ", " + luckynums[2]
						+ ", " + luckynums[3] + ", " + luckynums[4] + ".");
			
			int enchant = 0;
			int type2 = 0;
			
			for (int i = 0; i < 5; i++)
			{
				if (luckynums[i] < 17)
					enchant = (int) (enchant + L2Math.pow(2, luckynums[i] - 1));
				else
					type2 = (int) (type2 + L2Math.pow(2, luckynums[i] - 17));
			}
			
			if (_log.isDebugEnabled())
				_log.debug("Lottery: Encoded lucky numbers are " + enchant + ", " + type2);
			
			int count1 = 0;
			int count2 = 0;
			int count3 = 0;
			int count4 = 0;
			
			Connection con = null;
			PreparedStatement statement;
			try
			{
				con = L2DatabaseFactory.getInstance().getConnection(con);
				statement = con.prepareStatement(SELECT_LOTTERY_ITEM);
				statement.setInt(1, getId());
				ResultSet rset = statement.executeQuery();
				
				while (rset.next())
				{
					int curenchant = rset.getInt("enchant_level") & enchant;
					int curtype2 = rset.getInt("custom_type2") & type2;
					
					if (curenchant == 0 && curtype2 == 0)
						continue;
					
					int count = 0;
					
					for (int i = 1; i <= 16; i++)
					{
						int val = curenchant / 2;
						
						if (val != (double)curenchant / 2)
							count++;
						
						int val2 = curtype2 / 2;
						
						if (val2 != (double)curtype2 / 2)
							count++;
						
						curenchant = val;
						curtype2 = val2;
					}
					
					if (count == 5)
						count1++;
					else if (count == 4)
						count2++;
					else if (count == 3)
						count3++;
					else if (count > 0)
						count4++;
				}
				rset.close();
				statement.close();
			}
			catch (SQLException e)
			{
				_log.warn("Lottery: Could restore lottery data: ", e);
			}
			finally
			{
				L2DatabaseFactory.close(con);
			}
			
			long prize4 = count4 * Config.ALT_LOTTERY_2_AND_1_NUMBER_PRIZE;
			long prize1 = 0;
			long prize2 = 0;
			long prize3 = 0;
			
			if (count1 > 0)
				prize1 = (long)((getPrize() - prize4) * Config.ALT_LOTTERY_5_NUMBER_RATE / count1);
			
			if (count2 > 0)
				prize2 = (long)((getPrize() - prize4) * Config.ALT_LOTTERY_4_NUMBER_RATE / count2);
			
			if (count3 > 0)
				prize3 = (long)((getPrize() - prize4) * Config.ALT_LOTTERY_3_NUMBER_RATE / count3);
			
			if (_log.isDebugEnabled())
			{
				_log.info("Lottery: " + count1 + " players with all FIVE numbers each win " + prize1 + ".");
				_log.info("Lottery: " + count2 + " players with FOUR numbers each win " + prize2 + ".");
				_log.info("Lottery: " + count3 + " players with THREE numbers each win " + prize3 + ".");
				_log.info("Lottery: " + count4 + " players with ONE or TWO numbers each win " + prize4 + ".");
			}
			
			long newprize = getPrize() - (prize1 + prize2 + prize3 + prize4);
			if (_log.isDebugEnabled())
				_log.info("Lottery: Jackpot for next lottery is " + newprize + ".");
			
			SystemMessage sm;
			if (count1 > 0)
			{
				// There are winners.
				sm = new SystemMessage(SystemMessageId.AMOUNT_FOR_WINNER_S1_IS_S2_ADENA_WE_HAVE_S3_PRIZE_WINNER);
				sm.addNumber(getId());
				sm.addItemNumber(getPrize());
				sm.addItemNumber(count1);
				Announcements.getInstance().announceToAll(sm);
			}
			else
			{
				// There are no winners.
				sm = new SystemMessage(SystemMessageId.AMOUNT_FOR_LOTTERY_S1_IS_S2_ADENA_NO_WINNER);
				sm.addNumber(getId());
				sm.addItemNumber(getPrize());
				Announcements.getInstance().announceToAll(sm);
			}
			con = null;
			try
			{
				
				con = L2DatabaseFactory.getInstance().getConnection(con);
				statement = con.prepareStatement(UPDATE_LOTTERY);
				statement.setLong(1, getPrize());
				statement.setLong(2, newprize);
				statement.setInt(3, enchant);
				statement.setInt(4, type2);
				statement.setLong(5, prize1);
				statement.setLong(6, prize2);
				statement.setLong(7, prize3);
				statement.setInt(8, getId());
				statement.execute();
				statement.close();
			}
			catch (SQLException e)
			{
				_log.warn("Lottery: Could not store finished lottery data: ", e);
			}
			finally
			{
				L2DatabaseFactory.close(con);
			}
			
			ThreadPoolManager.getInstance().scheduleGeneral(new startLottery(), 1 * MINUTE);
			_number++;
			
			_isStarted = false;
		}
	}
	
	public int[] decodeNumbers(int enchant, int type2)
	{
		int res[] = new int[5];
		int id = 0;
		int nr = 1;
		
		while (enchant > 0)
		{
			int val = enchant / 2;
			if (val != (double)enchant / 2)
			{
				res[id++] = nr;
			}
			enchant /= 2;
			nr++;
		}
		
		nr = 17;
		
		while (type2 > 0)
		{
			int val = type2 / 2;
			if (val != (double)type2 / 2)
			{
				res[id++] = nr;
			}
			type2 /= 2;
			nr++;
		}
		
		return res;
	}
	
	public long[] checkTicket(L2ItemInstance item)
	{
		return checkTicket(item.getCustomType1(), item.getEnchantLevel(), item.getCustomType2());
	}
	
	public long[] checkTicket(int id, int enchant, int type2)
	{
		long res[] = { 0, 0 };
		
		Connection con = null;
		PreparedStatement statement;
		
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection(con);
			statement = con.prepareStatement(SELECT_LOTTERY_TICKET);
			statement.setInt(1, id);
			ResultSet rset = statement.executeQuery();
			
			if (rset.next())
			{
				int curenchant = rset.getInt("winning_mask_low") & enchant;
				int curtype2 = rset.getInt("winning_mask_high") & type2;
				
				if (curenchant == 0 && curtype2 == 0)
				{
					rset.close();
					statement.close();
					return res;
				}
				
				int count = 0;
				
				for (int i = 1; i <= 16; i++)
				{
					int val = curenchant / 2;
					if (val != (double)curenchant / 2)
						count++;
					int val2 = curtype2 / 2;
					if (val2 != (double)curtype2 / 2)
						count++;
					curenchant = val;
					curtype2 = val2;
				}
				
				switch (count)
				{
					case 0:
						break;
					case 5:
						res[0] = 1;
						res[1] = rset.getLong("first_prize");
						break;
					case 4:
						res[0] = 2;
						res[1] = rset.getLong("second_prize");
						break;
					case 3:
						res[0] = 3;
						res[1] = rset.getLong("third_prize");
						break;
					default:
						res[0] = 4;
						res[1] = 200;
				}
				
				if (_log.isDebugEnabled())
					_log.warn("count: " + count + ", id: " + id + ", enchant: " + enchant + ", type2: " + type2);
			}
			
			rset.close();
			statement.close();
		}
		catch (SQLException e)
		{
			_log.warn("Lottery: Could not check lottery ticket #" + id + ": ", e);
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
		
		return res;
	}
	
	@SuppressWarnings("synthetic-access")
	private static class SingletonHolder
	{
		protected static final Lottery _instance = new Lottery();
	}
}
