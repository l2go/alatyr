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
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.l2jfree.L2DatabaseFactory;
import com.l2jfree.gameserver.ThreadPoolManager;
import com.l2jfree.gameserver.datatables.ClanTable;
import com.l2jfree.gameserver.datatables.DoorTable;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.gameobjects.instance.L2DoorInstance;
import com.l2jfree.gameserver.gameobjects.itemcontainer.PlayerInventory;
import com.l2jfree.gameserver.instancemanager.AuctionManager;
import com.l2jfree.gameserver.instancemanager.ClanHallManager;
import com.l2jfree.gameserver.model.clan.L2Clan;
import com.l2jfree.gameserver.network.SystemMessageId;
import com.l2jfree.gameserver.network.packets.server.PledgeShowInfoUpdate;
import com.l2jfree.gameserver.network.packets.server.SystemMessage;
import com.l2jfree.gameserver.persistence.WorldTransaction;

public class ClanHall extends Siegeable<CCHSiege>
{
	private int _clanHallId;
	private List<L2DoorInstance> _doors;
	private List<String> _doorDefault;
	private L2Clan _ownerClan;
	private int _lease;
	private String _desc;
	private String _location;
	protected long _paidUntil;
	private int _grade;
	protected final int _chRate = 604800000;
	protected boolean _isFree = true;
	private Map<Integer, ClanHallFunction> _functions;
	protected boolean _paid;
	
	// Only for contestable clan halls
	private CCHSiege _siege;
	private Calendar _siegeDate;
	private boolean _isTimeRegistrationOver = true;
	private Calendar _siegeTimeRegistrationEndDate;
	
	/** Clan Hall Functions */
	public static final int FUNC_TELEPORT = 1;
	public static final int FUNC_ITEM_CREATE = 2;
	public static final int FUNC_RESTORE_HP = 3;
	public static final int FUNC_RESTORE_MP = 4;
	public static final int FUNC_RESTORE_EXP = 5;
	public static final int FUNC_SUPPORT = 6;
	public static final int FUNC_DECO_FRONTPLATEFORM = 7;
	public static final int FUNC_DECO_CURTAINS = 8;
	
	public class ClanHallFunction
	{
		private int _type;
		private int _lvl;
		protected int _fee;
		protected int _tempFee;
		private long _rate;
		private long _endDate;
		protected boolean _inDebt;
		public boolean _cwh; // first activating clanhall function is payed from player inventory, any others from clan warehouse
		
		public ClanHallFunction(int type, int lvl, int lease, int tempLease, long rate, long time, boolean cwh)
		{
			_type = type;
			_lvl = lvl;
			_fee = lease;
			_tempFee = tempLease;
			_rate = rate;
			_endDate = time;
			initializeFunctionTask(cwh);
		}
		
		public int getType()
		{
			return _type;
		}
		
		public int getLvl()
		{
			return _lvl;
		}
		
		public int getLease()
		{
			return _fee;
		}
		
		public long getRate()
		{
			return _rate;
		}
		
		public long getEndTime()
		{
			return _endDate;
		}
		
		public void setLvl(int lvl)
		{
			_lvl = lvl;
		}
		
		public void setLease(int lease)
		{
			_fee = lease;
		}
		
		public void setEndTime(long time)
		{
			_endDate = time;
		}
		
		private void initializeFunctionTask(boolean cwh)
		{
			if (_isFree)
				return;
			long currentTime = System.currentTimeMillis();
			if (_endDate > currentTime)
				ThreadPoolManager.getInstance().scheduleGeneral(new FunctionTask(cwh), _endDate - currentTime);
			else
				ThreadPoolManager.getInstance().scheduleGeneral(new FunctionTask(cwh), 0);
		}
		
		private class FunctionTask implements Runnable
		{
			public FunctionTask(boolean cwh)
			{
				_cwh = cwh;
			}
			
			@Override
			public void run()
			{
				try
				{
					if (_isFree)
						return;
					if (getOwnerClan().getWarehouse().getAdena() >= _fee || !_cwh)
					{
						int fee = _fee;
						boolean newfc = true;
						if (getEndTime() == 0 || getEndTime() == -1)
						{
							if (getEndTime() == -1)
							{
								newfc = false;
								fee = _tempFee;
							}
						}
						else
							newfc = false;
						setEndTime(System.currentTimeMillis() + getRate());
						dbSave(newfc);
						if (_cwh)
						{
							getOwnerClan().getWarehouse().destroyItemByItemId("CH_function_fee", PlayerInventory.ADENA_ID,
									fee, null, null);
							if (_log.isDebugEnabled())
								_log.warn("deducted " + fee + " adena from " + getName()
										+ " owner's cwh for function id : " + getType());
						}
						ThreadPoolManager.getInstance().scheduleGeneral(new FunctionTask(true), getRate());
					}
					else
						removeFunction(getType());
				}
				catch (Exception e)
				{
					_log.error(e.getMessage(), e);
				}
			}
		}
		
		public void dbSave(boolean newFunction)
		{
			Connection con = null;
			try
			{
				PreparedStatement statement;
				
				con = L2DatabaseFactory.getInstance().getConnection(con);
				if (newFunction)
				{
					statement =
							con.prepareStatement("INSERT INTO clan_hall_function (clan_hall_id, function_type, level, lease, rate_ms, end_at) VALUES (?,?,?,?,?,?)");
					statement.setInt(1, getId());
					statement.setInt(2, getType());
					statement.setInt(3, getLvl());
					statement.setInt(4, getLease());
					statement.setLong(5, getRate());
					statement.setTimestamp(6, moment(getEndTime()));
				}
				else
				{
					statement =
							con.prepareStatement("UPDATE clan_hall_function SET level=?, lease=?, end_at=? WHERE clan_hall_id=? AND function_type=?");
					statement.setInt(1, getLvl());
					statement.setInt(2, getLease());
					statement.setTimestamp(3, moment(getEndTime()));
					statement.setInt(4, getId());
					statement.setInt(5, getType());
				}
				statement.execute();
				statement.close();
			}
			catch (Exception e)
			{
				_log.error(
						"Exception: ClanHall.updateFunctions(int type, int lvl, int lease, long rate, long time, boolean addNew): "
								+ e.getMessage(), e);
			}
			finally
			{
				L2DatabaseFactory.close(con);
			}
		}
	}
	
	/** Epoch milliseconds as a database moment; 0 or less means "not set" and is NULL. */
	private static Timestamp moment(long millis)
	{
		return millis > 0 ? new Timestamp(millis) : null;
	}
	
	/** A database moment as epoch milliseconds; NULL means "not set" and is 0. */
	private static long millis(Timestamp moment)
	{
		return moment == null ? 0 : moment.getTime();
	}
	
	public ClanHall(int clanHallId, String name, int ownerId, int lease, String desc, String location, long paidUntil,
			int Grade, boolean paid)
	{
		super(clanHallId);
		_clanHallId = clanHallId;
		_name = name;
		_ownerId = ownerId;
		if (_log.isDebugEnabled())
			_log.warn("Init Owner : " + _ownerId);
		_lease = lease;
		_desc = desc;
		_location = location;
		_paidUntil = paidUntil;
		_grade = Grade;
		_paid = paid;
		//_doorDefault = new ArrayList<String>();
		_functions = new LinkedHashMap<Integer, ClanHallFunction>();
		if (ownerId != 0)
		{
			_isFree = false;
			initializeTask(false);
			loadFunctions();
		}
		loadSiegeData();
	}
	
	/** Return if clanHall is paid or not */
	public final boolean getPaid()
	{
		return _paid;
	}
	
	/** Return Id Of Clan hall */
	public final int getId()
	{
		return _clanHallId;
	}
	
	/** Return lease*/
	public final int getLease()
	{
		return _lease;
	}
	
	/** Return Desc */
	public final String getDesc()
	{
		return _desc;
	}
	
	/** Return Location */
	public final String getLocation()
	{
		return _location;
	}
	
	/** Return PaidUntil */
	public final long getPaidUntil()
	{
		return _paidUntil;
	}
	
	/** Return Grade */
	public final int getGrade()
	{
		return _grade;
	}
	
	/** Return all DoorInstance */
	public final List<L2DoorInstance> getDoors()
	{
		if (_doors == null)
			_doors = new ArrayList<L2DoorInstance>();
		return _doors;
	}
	
	/** Return Door */
	public final L2DoorInstance getDoor(int doorId)
	{
		if (doorId <= 0)
			return null;
		for (L2DoorInstance door : getDoors())
		{
			if (door.getDoorId() == doorId)
				return door;
		}
		return null;
	}
	
	/** Return function with id */
	public ClanHallFunction getFunction(int type)
	{
		if (_functions.get(type) != null)
			return _functions.get(type);
		return null;
	}
	
	/** Free this clan hall */
	public void free()
	{
		_ownerId = 0;
		if (_ownerClan != null)
		{
			_ownerClan.setHasHideout(0);
			_ownerClan.broadcastToOnlineMembers(new PledgeShowInfoUpdate(_ownerClan));
			_ownerClan = null;
		}
		_isFree = true;
		// The paid functions and the owner are removed together
		WorldTransaction.run("Clan hall release", () -> {
			// removeFunction() removes from _functions, so walk a copy of the keys
			for (Integer functionType : new ArrayList<Integer>(_functions.keySet()))
				removeFunction(functionType);
			_functions.clear();
			_paidUntil = 0;
			updateDb();
		});
	}
	
	/** Set owner if clan hall is free */
	public void setOwner(final L2Clan clan)
	{
		// Releasing the old owner and storing the new one are one change
		WorldTransaction.run("Clan hall owner change", () -> setOwnerInTransaction(clan));
	}
	
	private void setOwnerInTransaction(L2Clan clan)
	{
		// An owner exists - must not mess with auction, as GM commands call a different method
		if (!isSiegeable() && _ownerId > 0)
			return;
		
		// An owner might exist, but we wont mess anything
		if (isSiegeable())
			free();
		// New owner doesn't exist. Stop here.
		if (clan == null)
			return;
		
		_ownerId = clan.getClanId();
		_ownerClan = clan;
		_isFree = false;
		if (isSiegeable())
		{
			_paidUntil = Long.MAX_VALUE;
			_paid = true;
			clan.setHasHideout(getId());
		}
		else
			_paidUntil = System.currentTimeMillis();
		initializeTask(true);
		// Announce to Online member new ClanHall
		clan.broadcastToOnlineMembers(new PledgeShowInfoUpdate(clan));
		updateDb();
	}
	
	/** Respawn all doors */
	public void spawnDoor()
	{
		spawnDoor(false);
	}
	
	/** Respawn all doors */
	public void spawnDoor(boolean isDoorWeak)
	{
		for (int i = 0; i < getDoors().size(); i++)
		{
			L2DoorInstance door = getDoors().get(i);
			if (door.getStatus().getCurrentHp() <= 0)
			{
				door.decayMe(); // Kill current if not killed already
				// This doesn't even work
				door = DoorTable.parseLine(_doorDefault.get(i));
				DoorTable.getInstance().putDoor(door); //Read the new door to the DoorTable By Erb
				if (isDoorWeak)
					door.getStatus().setCurrentHp(door.getMaxHp() / 2);
				door.spawnMe(door.getX(), door.getY(), door.getZ());
				getDoors().set(i, door);
			}
			else if (door.isOpen())
				door.closeMe();
		}
	}
	
	/** Open or Close Door */
	public void openCloseDoor(L2Player activeChar, int doorId, boolean open)
	{
		if (activeChar != null && activeChar.getClanId() == getOwnerId())
			openCloseDoor(doorId, open);
	}
	
	public void openCloseDoor(int doorId, boolean open)
	{
		openCloseDoor(getDoor(doorId), open);
	}
	
	public void openCloseDoor(L2DoorInstance door, boolean open)
	{
		if (door != null)
		{
			if (open)
				door.openMe();
			else
				door.closeMe();
		}
	}
	
	public void openCloseDoors(L2Player activeChar, boolean open)
	{
		if (activeChar != null && activeChar.getClanId() == getOwnerId())
			openCloseDoors(open);
	}
	
	public void openCloseDoors(boolean open)
	{
		for (L2DoorInstance door : getDoors())
		{
			if (door != null)
			{
				if (open)
					door.openMe();
				else
					door.closeMe();
			}
		}
	}
	
	@Override
	public boolean checkBanish(L2Player cha)
	{
		return cha.getClanId() != getOwnerId();
	}
	
	/** Load All Functions */
	private void loadFunctions()
	{
		Connection con = null;
		try
		{
			PreparedStatement statement;
			ResultSet rs;
			con = L2DatabaseFactory.getInstance().getConnection(con);
			statement =
					con.prepareStatement("SELECT function_type, level, lease, rate_ms, end_at FROM clan_hall_function WHERE clan_hall_id = ?");
			statement.setInt(1, getId());
			rs = statement.executeQuery();
			while (rs.next())
			{
				_functions.put(
						rs.getInt("function_type"),
						new ClanHallFunction(rs.getInt("function_type"), rs.getInt("level"), rs.getInt("lease"), 0, rs
								.getLong("rate_ms"), millis(rs.getTimestamp("end_at")), true));
			}
			statement.close();
		}
		catch (SQLException e)
		{
			_log.error("Exception: ClanHall.loadFunctions(): " + e.getMessage(), e);
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
	}
	
	/** Remove function In List and in DB */
	public void removeFunction(int functionType)
	{
		_functions.remove(functionType);
		Connection con = null;
		try
		{
			PreparedStatement statement;
			con = L2DatabaseFactory.getInstance().getConnection(con);
			statement = con.prepareStatement("DELETE FROM clan_hall_function WHERE clan_hall_id=? AND function_type=?");
			statement.setInt(1, getId());
			statement.setInt(2, functionType);
			statement.execute();
			statement.close();
		}
		catch (SQLException e)
		{
			_log.error("Exception: ClanHall.removeFunctions(int functionType): " + e.getMessage(), e);
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
	}
	
	public boolean updateFunctions(L2Player player, int type, int lvl, int lease, long rate, boolean addNew)
	{
		if (player == null)
			return false;
		
		if (_log.isDebugEnabled())
			_log.warn("Called ClanHall.updateFunctions(int type, int lvl, int lease, long rate, boolean addNew) Owner : "
					+ getOwnerId());
		
		if (lease > 0)
		{
			if (!player.destroyItemByItemId("Consume", PlayerInventory.ADENA_ID, lease, null, true))
				return false;
		}
		
		if (addNew)
		{
			_functions.put(type, new ClanHallFunction(type, lvl, lease, 0, rate, 0, false));
		}
		else
		{
			if (lvl == 0 && lease == 0)
				removeFunction(type);
			else
			{
				int diffLease = lease - _functions.get(type).getLease();
				if (_log.isDebugEnabled())
					_log.warn("Called ClanHall.updateFunctions diffLease : " + diffLease);
				if (diffLease > 0)
				{
					_functions.put(type, new ClanHallFunction(type, lvl, lease, 0, rate, -1, false));
				}
				else
				{
					_functions.get(type).setLease(lease);
					_functions.get(type).setLvl(lvl);
					_functions.get(type).dbSave(false);
				}
			}
		}
		return true;
	}
	
	/** Update DB */
	public void updateDb()
	{
		Connection con = null;
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection(con);
			PreparedStatement statement;
			
			statement = con.prepareStatement("UPDATE clan_hall SET owner_clan_id=?, paid_until_at=?, is_paid=? WHERE id=?");
			if (_ownerId > 0)
				statement.setInt(1, _ownerId);
			else
				statement.setNull(1, Types.INTEGER); // the hall is free
			if (_paidUntil == Long.MAX_VALUE)
				statement.setString(2, "infinity"); // won in a siege: no rent
			else
				statement.setTimestamp(2, moment(_paidUntil));
			statement.setBoolean(3, _paid);
			statement.setInt(4, _clanHallId);
			statement.execute();
			statement.close();
		}
		catch (SQLException e)
		{
			_log.error("Exception: updateOwnerInDB(L2Clan clan): " + e.getMessage(), e);
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
	}
	
	/** Initialyze Fee Task */
	private void initializeTask(boolean forced)
	{
		long currentTime = System.currentTimeMillis();
		if (_paidUntil > currentTime)
		{
			ThreadPoolManager.getInstance().scheduleGeneral(new FeeTask(), _paidUntil - currentTime);
		}
		else if (!_paid && !forced)
		{
			if (System.currentTimeMillis() + (1000 * 60 * 60 * 24) <= _paidUntil + _chRate)
				ThreadPoolManager.getInstance().scheduleGeneral(new FeeTask(), 1000 * 60 * 60 * 24);
			else
				ThreadPoolManager.getInstance().scheduleGeneral(new FeeTask(),
						(_paidUntil + _chRate) - System.currentTimeMillis());
		}
		else
			ThreadPoolManager.getInstance().scheduleGeneral(new FeeTask(), 0);
	}
	
	/** Fee Task */
	private class FeeTask implements Runnable
	{
		public FeeTask()
		{
		}
		
		@Override
		public void run()
		{
			try
			{
				if (_isFree)
					return;
				L2Clan Clan = getOwnerClan();
				if (getOwnerClan().getWarehouse().getAdena() >= getLease())
				{
					if (_paidUntil != 0)
					{
						while (_paidUntil < System.currentTimeMillis())
							_paidUntil += _chRate;
					}
					else
						_paidUntil = System.currentTimeMillis() + _chRate;
					
					getOwnerClan().getWarehouse().destroyItemByItemId("CH_rental_fee", PlayerInventory.ADENA_ID,
							getLease(), null, null);
					if (_log.isDebugEnabled())
						_log.warn("deducted " + getLease() + " adena from " + getName()
								+ " owner's cwh for ClanHall _paidUntil" + _paidUntil);
					ThreadPoolManager.getInstance().scheduleGeneral(new FeeTask(),
							_paidUntil - System.currentTimeMillis());
					_paid = true;
					updateDb();
				}
				else
				{
					_paid = false;
					if (System.currentTimeMillis() > _paidUntil + _chRate)
					{
						if (ClanHallManager.loaded())
						{
							AuctionManager.getInstance().initNPC(getId());
							ClanHallManager.getInstance().setFree(getId());
							Clan.broadcastToOnlineMembers(SystemMessageId.CLAN_HALL_PAYMENT_OVERDUE.getSystemMessage());
						}
						else
							ThreadPoolManager.getInstance().scheduleGeneral(new FeeTask(), 3000);
					}
					else
					{
						updateDb();
						// TODO: fix this...
						Clan.broadcastToOnlineMembers(new SystemMessage(
								SystemMessageId.MAKE_CLAN_HALL_PAYMENT_BY_S1_TOMORROW).addNumber(12));
						if (System.currentTimeMillis() + (1000 * 60 * 60 * 24) <= _paidUntil + _chRate)
							ThreadPoolManager.getInstance().scheduleGeneral(new FeeTask(), 1000 * 60 * 60 * 24);
						else
							ThreadPoolManager.getInstance().scheduleGeneral(new FeeTask(),
									(_paidUntil + _chRate) - System.currentTimeMillis());
					}
				}
			}
			catch (Exception e)
			{
				_log.error(e.getMessage(), e);
			}
		}
	}
	
	public L2Clan getOwnerClan()
	{
		if (_ownerId == 0)
			return null;
		
		if (_ownerClan == null)
			_ownerClan = ClanTable.getInstance().getClan(getOwnerId());
		
		return _ownerClan;
	}
	
	private final boolean isSiegeable()
	{
		switch (getId())
		{
			case 34:
			case 64:
				return true;
			default:
				return false;
		}
	}
	
	@Override
	public final CCHSiege getSiege()
	{
		if (isSiegeable() && _siege == null)
			_siege = new CCHSiege(this);
		return _siege;
	}
	
	public final Calendar getSiegeDate()
	{
		return _siegeDate;
	}
	
	public boolean getIsTimeRegistrationOver()
	{
		return _isTimeRegistrationOver;
	}
	
	public void setIsTimeRegistrationOver(boolean val)
	{
		_isTimeRegistrationOver = val;
	}
	
	public Calendar getTimeRegistrationOverDate()
	{
		if (_siegeTimeRegistrationEndDate == null)
			_siegeTimeRegistrationEndDate = Calendar.getInstance();
		return _siegeTimeRegistrationEndDate;
	}
	
	private final void loadSiegeData()
	{
		if (!isSiegeable())
			return;
		
		Connection con = null;
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection();
			PreparedStatement ps =
					con.prepareStatement("SELECT siege_at, registration_end_at, is_registration_over FROM clan_hall_siege WHERE clan_hall_id=?");
			ps.setInt(1, getId());
			ResultSet rs = ps.executeQuery();
			if (rs.next())
			{
				_siegeDate = Calendar.getInstance();
				_siegeDate.setTimeInMillis(millis(rs.getTimestamp("siege_at")));
				_siegeTimeRegistrationEndDate = Calendar.getInstance();
				_siegeTimeRegistrationEndDate.setTimeInMillis(millis(rs.getTimestamp("registration_end_at")));
				_isTimeRegistrationOver = rs.getBoolean("is_registration_over");
			}
			else
				_log.warn("Missing contest schedule data for " + getName());
		}
		catch (Exception e)
		{
			_log.error("Failed loading contest data!", e);
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
	}
}
