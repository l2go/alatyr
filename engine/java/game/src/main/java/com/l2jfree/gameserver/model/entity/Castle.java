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
import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;

import com.l2jfree.Config;
import com.l2jfree.L2DatabaseFactory;
import com.l2jfree.gameserver.Announcements;
import com.l2jfree.gameserver.ThreadPoolManager;
import com.l2jfree.gameserver.datatables.ClanTable;
import com.l2jfree.gameserver.datatables.DoorTable;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.gameobjects.instance.L2DoorInstance;
import com.l2jfree.gameserver.gameobjects.itemcontainer.PlayerInventory;
import com.l2jfree.gameserver.instancemanager.CastleManager;
import com.l2jfree.gameserver.instancemanager.CastleManorManager;
import com.l2jfree.gameserver.instancemanager.CastleManorManager.CropProcure;
import com.l2jfree.gameserver.instancemanager.CastleManorManager.SeedProduction;
import com.l2jfree.gameserver.instancemanager.CrownManager;
import com.l2jfree.gameserver.instancemanager.FortManager;
import com.l2jfree.gameserver.model.L2Manor;
import com.l2jfree.gameserver.model.clan.L2Clan;
import com.l2jfree.gameserver.model.sevensigns.SevenSigns;
import com.l2jfree.gameserver.model.zone.L2SiegeDangerZone;
import com.l2jfree.gameserver.network.packets.server.PlaySound;
import com.l2jfree.gameserver.network.packets.server.PledgeShowInfoUpdate;
import com.l2jfree.gameserver.persistence.WorldTransaction;

public class Castle extends Siegeable<Siege>
{
	private List<CropProcure> _procure = new ArrayList<CropProcure>();
	private List<SeedProduction> _production = new ArrayList<SeedProduction>();
	private List<CropProcure> _procureNext = new ArrayList<CropProcure>();
	private List<SeedProduction> _productionNext = new ArrayList<SeedProduction>();
	private boolean _isNextPeriodApproved = false;
	
	private static final String CASTLE_MANOR_DELETE_PRODUCTION =
			"DELETE FROM castle_manor_production WHERE castle_id=?";
	private static final String CASTLE_MANOR_DELETE_PRODUCTION_PERIOD =
			"DELETE FROM castle_manor_production WHERE castle_id=? AND period=?";
	private static final String CASTLE_MANOR_DELETE_PROCURE = "DELETE FROM castle_manor_procure WHERE castle_id=?";
	private static final String CASTLE_MANOR_DELETE_PROCURE_PERIOD =
			"DELETE FROM castle_manor_procure WHERE castle_id=? AND period=?";
	private static final String CASTLE_MANOR_INSERT_PRODUCTION =
			"INSERT INTO castle_manor_production (castle_id, seed_item_template_id, period, remaining_amount, start_amount, price) VALUES (?,?,?,?,?,?) "
					+ "ON CONFLICT (castle_id, seed_item_template_id, period) DO UPDATE SET remaining_amount = EXCLUDED.remaining_amount, start_amount = EXCLUDED.start_amount, price = EXCLUDED.price";
	private static final String CASTLE_MANOR_INSERT_PROCURE =
			"INSERT INTO castle_manor_procure (castle_id, crop_item_template_id, period, remaining_amount, start_amount, price, reward_type) VALUES (?,?,?,?,?,?,?) "
					+ "ON CONFLICT (castle_id, crop_item_template_id, period) DO UPDATE SET remaining_amount = EXCLUDED.remaining_amount, start_amount = EXCLUDED.start_amount, price = EXCLUDED.price, reward_type = EXCLUDED.reward_type";
	private static final String CASTLE_UPDATE_CROP =
			"UPDATE castle_manor_procure SET remaining_amount=? WHERE crop_item_template_id=? AND castle_id=? AND period=?";
	private static final String CASTLE_UPDATE_SEED =
			"UPDATE castle_manor_production SET remaining_amount=? WHERE seed_item_template_id=? AND castle_id=? AND period=?";
	
	private static final String CASTLE_TAX_UPDATE_INSTANT =
			"UPDATE castle SET tax_percent=?, tax_set_at=NULL WHERE id=?";
	private static final String CASTLE_TAX_UPDATE_DELAYED =
			"UPDATE castle SET pending_tax_percent=?, tax_set_at=? WHERE id=?";
	
	private static final String CASTLE_TRAP_ADD =
			"INSERT INTO castle_trap_upgrade (level, castle_id, side) VALUES (?,?,?)";
	private static final String CASTLE_TRAP_UPGRADE =
			"UPDATE castle_trap_upgrade SET level=? WHERE castle_id=? AND side=?";
	private static final String CASTLE_TRAP_LOAD = "SELECT level FROM castle_trap_upgrade WHERE castle_id=? AND side=?";
	private static final String CASTLE_TRAPS_REMOVE = "DELETE FROM castle_trap_upgrade WHERE castle_id=?";
	
	private final List<L2DoorInstance> _doors = new ArrayList<L2DoorInstance>();
	private final List<String> _doorDefault = new ArrayList<String>();
	private int _castleId = 0;
	private Siege _siege = null;
	private Calendar _siegeDate;
	private boolean _isTimeRegistrationOver = true; // true if Castle Lords set the time, false if 24h are elapsed after the siege
	private Calendar _siegeTimeRegistrationEndDate; // last siege end date + 1 day
	private int _taxPercent = 0;
	private int _taxPercentNew = 0;
	private double _taxRate = 1.0;
	private long _treasury = 0;
	private int _nbArtifact = 1;
	private final Map<Integer, Integer> _engrave = new LinkedHashMap<Integer, Integer>();
	private final int[] _gate = { Integer.MIN_VALUE, 0, 0 };
	private final Map<Integer, CastleFunction> _function;
	private ScheduledFuture<?> _taxUpdate = null;
	
	/** Castle Functions */
	public static final int FUNC_TELEPORT = 1;
	public static final int FUNC_RESTORE_HP = 2;
	public static final int FUNC_RESTORE_MP = 3;
	public static final int FUNC_RESTORE_EXP = 4;
	public static final int FUNC_SUPPORT = 5;
	public static final int FUNC_SECURITY = 9;
	
	public class CastleFunction
	{
		private final int _type;
		private int _lvl;
		protected int _fee;
		protected int _tempFee;
		private final long _rate;
		private long _endDate;
		protected boolean _inDebt;
		public boolean _cwh;
		
		public CastleFunction(int type, int lvl, int lease, int tempLease, long rate, long time, boolean cwh)
		{
			_type = type;
			_lvl = lvl;
			_fee = lease;
			_tempFee = tempLease;
			_rate = rate;
			_endDate = time;
			initializeTask(cwh);
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
		
		private void initializeTask(boolean cwh)
		{
			if (getOwnerId() <= 0)
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
				if (getOwnerId() <= 0)
					return;
				if (ClanTable.getInstance().getClan(getOwnerId()).getWarehouse().getAdena() >= _fee || !_cwh)
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
						ClanTable.getInstance().getClan(getOwnerId()).getWarehouse()
								.destroyItemByItemId("CS_function_fee", PlayerInventory.ADENA_ID, fee, null, null);
						if (_log.isDebugEnabled())
							_log.warn("deducted " + fee + " adena from " + getName()
									+ " owner's cwh for function id : " + getType());
					}
					ThreadPoolManager.getInstance().scheduleGeneral(new FunctionTask(true), getRate());
				}
				else
					removeFunction(getType());
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
							con.prepareStatement("INSERT INTO castle_function (castle_id, function_type, level, lease, rate_ms, end_at) VALUES (?,?,?,?,?,?)");
					statement.setInt(1, getCastleId());
					statement.setInt(2, getType());
					statement.setInt(3, getLvl());
					statement.setInt(4, getLease());
					statement.setLong(5, getRate());
					statement.setTimestamp(6, moment(getEndTime()));
				}
				else
				{
					statement =
							con.prepareStatement("UPDATE castle_function SET level=?, lease=?, end_at=? WHERE castle_id=? AND function_type=?");
					statement.setInt(1, getLvl());
					statement.setInt(2, getLease());
					statement.setTimestamp(3, moment(getEndTime()));
					statement.setInt(4, getCastleId());
					statement.setInt(5, getType());
				}
				statement.execute();
				statement.close();
			}
			catch (Exception e)
			{
				_log.error(
						"Exception: Castle.updateFunctions(int type, int lvl, int lease, long rate, long time, boolean addNew): "
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
	
	public Castle(int castleId)
	{
		super(castleId);
		_castleId = castleId;
		if (_castleId == 7 || castleId == 9) // Goddard and Schuttgart
			_nbArtifact = 2;
		
		load();
		loadDoor();
		_function = new LinkedHashMap<Integer, CastleFunction>();
		if (getOwnerId() != 0)
		{
			loadFunctions();
		}
	}
	
	/** Return function with id */
	public CastleFunction getFunction(int type)
	{
		if (_function.get(type) != null)
			return _function.get(type);
		return null;
	}
	
	public void engrave(L2Clan clan, int objId)
	{
		_engrave.put(objId, clan.getClanId());
		if (_engrave.size() == _nbArtifact)
		{
			boolean rst = true;
			for (int id : _engrave.values())
			{
				if (id != clan.getClanId())
					rst = false;
			}
			if (rst)
			{
				_engrave.clear();
				setOwner(clan);
			}
			else
				getSiege().announceToPlayer("Clan " + clan.getName() + " has finished to engrave one of the rulers.",
						true);
		}
		else
			getSiege().announceToPlayer("Clan " + clan.getName() + " has finished to engrave one of the rulers.", true);
	}
	
	/** Add amount to castle instance's treasury (warehouse). */
	public void addToTreasury(long amount)
	{
		// check if owned
		if (getOwnerId() <= 0)
		{
			return;
		}
		
		if (_name.equalsIgnoreCase("Schuttgart") || _name.equalsIgnoreCase("Goddard"))
		{
			Castle rune = CastleManager.getInstance().getCastleByName("Rune");
			if (rune != null)
			{
				long runeTax = (long)(amount * rune.getTaxPercent() / 100.);
				if (rune.getOwnerId() > 0)
					rune.addToTreasuryNoTax(runeTax);
				amount -= runeTax;
			}
		}
		if (!_name.equalsIgnoreCase("Aden") && !_name.equalsIgnoreCase("Rune") && !_name.equalsIgnoreCase("Schuttgart")
				&& !_name.equalsIgnoreCase("Goddard")) // If current castle instance is not Aden, Rune, Goddard or Schuttgart.
		{
			Castle aden = CastleManager.getInstance().getCastleByName("Aden");
			if (aden != null)
			{
				long adenTax = (long)(amount * aden.getTaxPercent() / 100.); // Find out what Aden gets from the current castle instance's income
				if (aden.getOwnerId() > 0)
					aden.addToTreasuryNoTax(adenTax); // Only bother to really add the tax to the treasury if not npc owned
					
				amount -= adenTax; // Subtract Aden's income from current castle instance's income
			}
		}
		
		addToTreasuryNoTax(amount);
	}
	
	/** Add amount to castle instance's treasury (warehouse), no tax paying. */
	public synchronized boolean addToTreasuryNoTax(long amount)
	{
		if (getOwnerId() <= 0)
			return false;
		
		if (amount < 0)
		{
			amount *= -1;
			if (_treasury < amount)
				return false;
			_treasury -= amount;
		}
		else
		{
			if (_treasury + amount > PlayerInventory.MAX_ADENA) // TODO is this valid after gracia final?
				_treasury = PlayerInventory.MAX_ADENA;
			else
				_treasury += amount;
		}
		
		Connection con = null;
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection(con);
			PreparedStatement statement = con.prepareStatement("UPDATE castle SET treasury = ? WHERE id = ?");
			statement.setLong(1, getTreasury());
			statement.setInt(2, getCastleId());
			statement.execute();
			statement.close();
		}
		catch (Exception e)
		{
			_log.error(e.getMessage(), e);
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
		return true;
	}
	
	public void closeDoor(L2Player activeChar, int doorId)
	{
		openCloseDoor(activeChar, doorId, false);
	}
	
	public void openDoor(L2Player activeChar, int doorId)
	{
		openCloseDoor(activeChar, doorId, true);
	}
	
	public void openCloseDoor(L2Player activeChar, int doorId, boolean open)
	{
		if (activeChar.getClanId() != getOwnerId())
			return;
		
		L2DoorInstance door = getDoor(doorId);
		if (door != null)
		{
			if (open)
				door.openMe();
			else
				door.closeMe();
		}
	}
	
	// This method is used to begin removing all castle upgrades
	public void removeUpgrade()
	{
		WorldTransaction.run("Castle upgrades removal", () -> {
			removeDoorUpgrade();
			// removeFunction() removes from _function, so walk a copy of the keys
			for (Integer functionType : new ArrayList<Integer>(_function.keySet()))
				removeFunction(functionType);
			_function.clear();
		});
	}
	
	public void removeOwner(L2Clan clan)
	{
		if (clan != null)
		{
			_formerOwner = clan;
			if (Config.ALT_REMOVE_CASTLE_CIRCLETS)
			{
				CastleManager.getInstance().removeCirclet(_formerOwner, getCastleId());
			}
			clan.setHasCastle(0);
			Announcements.getInstance().announceToAll(clan.getName() + " has lost " + getName() + " castle.");
			clan.broadcastToOnlineMembers(new PledgeShowInfoUpdate(clan));
			
			for (L2Player member : clan.getOnlineMembers(0))
			{
				removeResidentialSkills(member);
			}
		}
		
		// The owner and its paid functions are removed together.
		WorldTransaction.run("Castle owner removal", () -> {
			updateOwnerInDB(null);
			if (getSiege().getIsInProgress())
				getSiege().midVictory();
		
			// removeFunction() removes from _function, so walk a copy of the keys
			for (Integer functionType : new ArrayList<Integer>(_function.keySet()))
				removeFunction(functionType);
			_function.clear();
		});
	}
	
	// This method updates the castle owner
	public void setOwner(L2Clan clan)
	{
		// Remove old owner
		if (getOwnerId() > 0 && (clan == null || clan.getClanId() != getOwnerId()))
		{
			L2Clan oldOwner = ClanTable.getInstance().getClan(getOwnerId()); // Try to find clan instance
			if (oldOwner != null)
			{
				
				if (_formerOwner == null)
				{
					_formerOwner = oldOwner;
					if (Config.ALT_REMOVE_CASTLE_CIRCLETS)
						CastleManager.getInstance().removeCirclet(_formerOwner, getCastleId());
				}
				
				L2Player oldLord = oldOwner.getLeader().getPlayerInstance();
				if (oldLord != null && oldLord.getMountType() == 2)
					oldLord.dismount();
				
				oldOwner.setHasCastle(0); // Unset has castle flag for old owner
				Announcements.getInstance().announceToAll(oldOwner.getName() + " has lost " + getName() + " castle!");
				
				// remove crowns
				CrownManager.checkCrowns(oldOwner);
			}
		}
		
		// The new owner and the loss of its fortress are stored together.
		WorldTransaction.run("Castle owner change", () -> {
			updateOwnerInDB(clan); // Update in database
		
			// if clan have fortress, remove it
			if (clan != null && clan.getHasFort() > 0)
			{
				Fort fort = FortManager.getInstance().getFortByOwner(clan);
				if (fort != null)
					fort.removeOwner(true);
			}
		});
		
		if (clan == null)
		{
			if (getSiege().getIsInProgress())
				getSiege().endSiege();
			return;
		}
		
		if (getSiege().getIsInProgress()) // If siege in progress
			getSiege().midVictory(); // Mid victory phase of siege
			
		for (L2Player member : clan.getOnlineMembers(0))
		{
			giveResidentialSkills(member);
		}
	}
	
	/**
	 * Sets the tax rate for current castle and updates data in the database.
	 * @param taxPercent Tax percentage
	 * @param check Validate if the specified percentage can be set as tax?
	 * @param delayed Is a player changing the tax rate?
	 */
	public boolean setTaxPercent(int taxPercent, boolean check, boolean delayed)
	{
		if (check && !validateTax(taxPercent))
			return false;
		
		stopUpdateTask();
		if (delayed)
			delayed = Config.ALT_TAX_CHANGE_DELAYED;
		Connection con = null;
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection(con);
			PreparedStatement statement = null;
			_taxPercentNew = taxPercent;
			if (delayed && taxPercent != _taxPercent)
			{
				statement = con.prepareStatement(CASTLE_TAX_UPDATE_DELAYED);
				statement.setInt(1, taxPercent);
				statement.setTimestamp(2, new Timestamp(System.currentTimeMillis()));
				statement.setInt(3, getCastleId());
				startUpdateTask();
			}
			else
			{
				statement = con.prepareStatement(CASTLE_TAX_UPDATE_INSTANT);
				statement.setInt(1, taxPercent);
				statement.setInt(2, getCastleId());
				_taxPercent = taxPercent;
				_taxRate = (_taxPercent + 100) / 100.0;
			}
			statement.executeUpdate();
			statement.close();
			return true;
		}
		catch (Exception e)
		{
			_log.error(e.getMessage(), e);
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
		return false;
	}
	
	/**
	 * Respawn all doors on castle grounds<BR><BR>
	 */
	public void spawnDoor()
	{
		spawnDoor(false);
	}
	
	/**
	 * Respawn all doors on castle grounds<BR><BR>
	 */
	public void spawnDoor(boolean isDoorWeak)
	{
		for (int i = 0; i < getDoors().size(); i++)
		{
			L2DoorInstance door = getDoors().get(i);
			if (door.getStatus().getCurrentHp() <= 0)
			{
				door.decayMe(); // Kill current if not killed already
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
		loadDoorUpgrade(); // Check for any upgrade the doors may have
	}
	
	// This method upgrade door
	public void upgradeDoor(int doorId, int hp, int pDef, int mDef)
	{
		L2DoorInstance door = getDoor(doorId);
		if (door == null)
			return;
		
		if (door.getDoorId() == doorId)
		{
			door.getStatus().setCurrentHp(door.getMaxHp() + hp);
			
			saveDoorUpgrade(doorId, hp, pDef, mDef);
		}
	}
	
	// This method loads castle
	private void load()
	{
		Connection con = null;
		try
		{
			PreparedStatement statement;
			ResultSet rs;
			
			con = L2DatabaseFactory.getInstance().getConnection(con);
			
			statement =
					con.prepareStatement("SELECT name, siege_at, registration_end_at, is_registration_over, treasury, tax_percent, pending_tax_percent, tax_set_at FROM castle WHERE id = ?");
			statement.setInt(1, getCastleId());
			rs = statement.executeQuery();
			
			while (rs.next())
			{
				_name = rs.getString("name");
				//_ownerId = rs.getInt("ownerId");
				
				_siegeDate = Calendar.getInstance();
				_siegeDate.setTimeInMillis(millis(rs.getTimestamp("siege_at")));
				
				_siegeTimeRegistrationEndDate = Calendar.getInstance();
				_siegeTimeRegistrationEndDate.setTimeInMillis(millis(rs.getTimestamp("registration_end_at")));
				_isTimeRegistrationOver = rs.getBoolean("is_registration_over");
				
				_treasury = rs.getLong("treasury");
				_taxPercent = rs.getInt("tax_percent");
				_taxPercentNew = rs.getInt("pending_tax_percent");
				if (_taxPercentNew != 0)
				{
					Calendar update = Calendar.getInstance();
					update.setTimeInMillis(millis(rs.getTimestamp("tax_set_at")));
					if (update.get(Calendar.HOUR_OF_DAY) >= 0)
						update.add(Calendar.DAY_OF_MONTH, 1);
					update.set(Calendar.HOUR_OF_DAY, 0);
					if (update.getTimeInMillis() < System.currentTimeMillis())
						setTaxPercent(_taxPercentNew, true, false);
					else
						startUpdateTask();
				}
				else
					_taxPercentNew = _taxPercent;
			}
			
			rs.close();
			statement.close();
			
			_taxRate = (_taxPercent + 100) / 100.0;
			
			statement = con.prepareStatement("SELECT id FROM clan WHERE castle_id = ?");
			statement.setInt(1, getCastleId());
			rs = statement.executeQuery();
			
			while (rs.next())
			{
				_ownerId = rs.getInt("id");
			}
			
			if (getOwnerId() > 0)
			{
				L2Clan clan = ClanTable.getInstance().getClan(getOwnerId()); // Try to find clan instance
				ThreadPoolManager.getInstance().scheduleGeneral(new CastleUpdater(clan, 1), 3600000); // Schedule owner tasks to start running
			}
			
			rs.close();
			statement.close();
		}
		catch (Exception e)
		{
			_log.error("Exception: loadCastleData(): " + e.getMessage(), e);
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
	}
	
	public void activateInstance()
	{
		for (final L2DoorInstance door : _doors)
		{
			door.spawnMe(door.getX(), door.getY(), door.getZ());
		}
	}
	
	// This method loads castle door data from database
	private void loadDoor()
	{
		Connection con = null;
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection(con);
			PreparedStatement statement =
					con.prepareStatement("SELECT id, name, x, y, z, range_x_min, range_y_min, range_z_min, range_x_max, range_y_max, range_z_max, hp, p_def, m_def FROM castle_door WHERE castle_id = ?");
			statement.setInt(1, getCastleId());
			ResultSet rs = statement.executeQuery();
			
			while (rs.next())
			{
				// Create list of the door default for use when respawning dead doors
				_doorDefault.add(rs.getString("name") + ";" + rs.getInt("id") + ";" + rs.getInt("x") + ";"
						+ rs.getInt("y") + ";" + rs.getInt("z") + ";" + rs.getInt("range_x_min") + ";"
						+ rs.getInt("range_y_min") + ";" + rs.getInt("range_z_min") + ";" + rs.getInt("range_x_max") + ";"
						+ rs.getInt("range_y_max") + ";" + rs.getInt("range_z_max") + ";" + rs.getInt("hp") + ";"
						+ rs.getInt("p_def") + ";" + rs.getInt("m_def"));
				
				L2DoorInstance door = DoorTable.parseLine(_doorDefault.get(_doorDefault.size() - 1));
				door.spawnMe(door.getX(), door.getY(), door.getZ());
				_doors.add(door);
				DoorTable.getInstance().putDoor(door);
				door.closeMe();
			}
			
			rs.close();
			statement.close();
		}
		catch (Exception e)
		{
			_log.error("Exception: loadCastleDoor(): " + e.getMessage(), e);
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
	}
	
	// This method loads castle door upgrade data from database
	private void loadDoorUpgrade()
	{
		/* TODO: outdated method, doors would lose the additional HP on first hit
		Connection con = null;
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection(con);
			PreparedStatement statement = con
					.prepareStatement("SELECT door_id, hp, physical_defense, magic_defense FROM castle_door_upgrade WHERE door_id IN (SELECT id FROM castle_door WHERE castle_id = ?)");
			statement.setInt(1, getCastleId());
			ResultSet rs = statement.executeQuery();

			while (rs.next())
			{
				upgradeDoor(rs.getInt("door_id"), rs.getInt("hp"), rs.getInt("physical_defense"), rs.getInt("magic_defense"));
			}

			rs.close();
			statement.close();
		}
		catch (Exception e)
		{
			_log.error("Exception: loadCastleDoorUpgrade(): " + e.getMessage(), e);
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
		 */
	}
	
	private void removeDoorUpgrade()
	{
		Connection con = null;
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection(con);
			PreparedStatement statement =
					con.prepareStatement("DELETE FROM castle_door_upgrade WHERE door_id IN (SELECT id FROM castle_door WHERE castle_id = ?)");
			statement.setInt(1, getCastleId());
			statement.execute();
			statement.close();
		}
		catch (Exception e)
		{
			_log.error("Exception: removeDoorUpgrade(): " + e.getMessage(), e);
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
	}
	
	private void saveDoorUpgrade(int doorId, int hp, int pDef, int mDef)
	{
		Connection con = null;
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection(con);
			PreparedStatement statement =
					con.prepareStatement("INSERT INTO castle_door_upgrade (door_id, hp, physical_defense, magic_defense) VALUES (?,?,?,?) "
							+ "ON CONFLICT (door_id) DO UPDATE SET hp = EXCLUDED.hp, physical_defense = EXCLUDED.physical_defense, magic_defense = EXCLUDED.magic_defense");
			statement.setInt(1, doorId);
			statement.setInt(2, hp);
			statement.setInt(3, pDef);
			statement.setInt(4, mDef);
			statement.execute();
			statement.close();
		}
		catch (Exception e)
		{
			_log.error("Exception: saveDoorUpgrade(int doorId, int hp, int pDef, int mDef): " + e.getMessage(), e);
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
	}
	
	private void updateOwnerInDB(L2Clan clan)
	{
		if (clan != null)
			_ownerId = clan.getClanId(); // Update owner id property
		else
			_ownerId = 0; // Remove owner
			
		// The previous owner loses the castle and the new one gets it, or none of it happens.
		WorldTransaction.run("Castle owner change", () -> {
			Connection con = null;
			try
			{
				con = L2DatabaseFactory.getInstance().getConnection(con);
				PreparedStatement statement;
			
				// The owner is stored in the clan row (clan.castle_id, unique).
				statement = con.prepareStatement("UPDATE clan SET castle_id = NULL WHERE castle_id = ?");
				statement.setInt(1, getCastleId());
				statement.execute();
				statement.close();
			
				statement = con.prepareStatement("UPDATE clan SET castle_id = ? WHERE id = ?");
				statement.setInt(1, getCastleId());
				statement.setInt(2, getOwnerId());
				statement.execute();
				statement.close();
			}
			catch (Exception e)
			{
				_log.error("Exception: updateOwnerInDB(L2Clan clan): " + e.getMessage(), e);
			}
			finally
			{
				L2DatabaseFactory.close(con);
			}
		});
			
		try
		{
			// Announce to clan memebers
			if (clan != null)
			{
				clan.setHasCastle(getCastleId()); // Set has castle flag for new owner
				Announcements.getInstance().announceToAll(clan.getName() + " has taken " + getName() + " castle!");
				clan.broadcastToOnlineMembers(new PledgeShowInfoUpdate(clan));
				clan.broadcastToOnlineMembers(new PlaySound(1, "Siege_Victory"));
				
				// give crowns
				CrownManager.checkCrowns(clan);
				
				ThreadPoolManager.getInstance().scheduleGeneral(new CastleUpdater(clan, 1), 3600000); // Schedule owner tasks to start running
			}
		}
		catch (Exception e)
		{
			_log.error("Exception: updateOwnerInDB(L2Clan clan): " + e.getMessage(), e);
		}
	}
	
	@Override
	public final int getCastleId()
	{
		return _castleId;
	}
	
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
	
	public final List<L2DoorInstance> getDoors()
	{
		return _doors;
	}
	
	@Override
	public final Siege getSiege()
	{
		if (_siege == null)
			_siege = new Siege(this);
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
	
	/** @return current tax percentage to be applied at midnight */
	public final int getTaxPercent()
	{
		return _taxPercent;
	}
	
	/** @return tax percentage to be applied at midnight */
	public final int getTaxPercentNew()
	{
		return _taxPercentNew;
	}
	
	public final double getTaxRate()
	{
		return _taxRate;
	}
	
	public final long getTreasury()
	{
		return _treasury;
	}

	/** Publishes a treasury value already committed in the multisell transaction. */
	public synchronized void publishCommittedTreasury(long value)
	{
		_treasury = value;
	}
	
	public List<SeedProduction> getSeedProduction(int period)
	{
		return (period == CastleManorManager.PERIOD_CURRENT ? _production : _productionNext);
	}
	
	public List<CropProcure> getCropProcure(int period)
	{
		return (period == CastleManorManager.PERIOD_CURRENT ? _procure : _procureNext);
	}
	
	public void setSeedProduction(List<SeedProduction> seed, int period)
	{
		if (period == CastleManorManager.PERIOD_CURRENT)
			_production = seed;
		else
			_productionNext = seed;
	}
	
	public void setCropProcure(List<CropProcure> crop, int period)
	{
		if (period == CastleManorManager.PERIOD_CURRENT)
			_procure = crop;
		else
			_procureNext = crop;
	}
	
	public synchronized SeedProduction getSeed(int seedId, int period)
	{
		for (SeedProduction seed : getSeedProduction(period))
		{
			if (seed.getId() == seedId)
			{
				return seed;
			}
		}
		return null;
	}
	
	public synchronized CropProcure getCrop(int cropId, int period)
	{
		for (CropProcure crop : getCropProcure(period))
		{
			if (crop.getId() == cropId)
			{
				return crop;
			}
		}
		return null;
	}
	
	public long getManorCost(int period)
	{
		List<CropProcure> procure;
		List<SeedProduction> production;
		
		if (period == CastleManorManager.PERIOD_CURRENT)
		{
			procure = _procure;
			production = _production;
		}
		else
		{
			procure = _procureNext;
			production = _productionNext;
		}
		
		long total = 0;
		if (production != null)
		{
			for (SeedProduction seed : production)
			{
				total += L2Manor.getInstance().getSeedBuyPrice(seed.getId()) * seed.getStartProduce();
			}
		}
		if (procure != null)
		{
			for (CropProcure crop : procure)
			{
				total += crop.getPrice() * crop.getStartAmount();
			}
		}
		return total;
	}
	
	//save manor production data
	public void saveSeedData()
	{
		// The old rows are replaced as a whole: all of it is stored or none of it.
		WorldTransaction.run("Castle manor seed data", () -> {
			Connection con = null;
			try
			{
				con = L2DatabaseFactory.getInstance().getConnection(con);
			
				PreparedStatement statement = con.prepareStatement(CASTLE_MANOR_DELETE_PRODUCTION);
				statement.setInt(1, getCastleId());
				statement.execute();
				statement.close();
			
				insertSeeds(con, _production, CastleManorManager.PERIOD_CURRENT);
				insertSeeds(con, _productionNext, CastleManorManager.PERIOD_NEXT);
			}
			catch (Exception e)
			{
				_log.info("Error adding seed production data for castle " + getName() + ": " + e.getMessage(), e);
			}
			finally
			{
				L2DatabaseFactory.close(con);
			}
		});
	}
	
	//save manor production data for specified period
	public void saveSeedData(int period)
	{
		WorldTransaction.run("Castle manor seed data", () -> {
			Connection con = null;
			try
			{
				con = L2DatabaseFactory.getInstance().getConnection(con);
			
				PreparedStatement statement = con.prepareStatement(CASTLE_MANOR_DELETE_PRODUCTION_PERIOD);
				statement.setInt(1, getCastleId());
				statement.setInt(2, period);
				statement.execute();
				statement.close();
			
				insertSeeds(con, getSeedProduction(period), period);
			}
			catch (Exception e)
			{
				_log.info("Error adding seed production data for castle " + getName() + ": " + e.getMessage(), e);
			}
			finally
			{
				L2DatabaseFactory.close(con);
			}
		});
	}
	
	private void insertSeeds(Connection con, List<SeedProduction> seeds, int period) throws SQLException
	{
		if (seeds == null || seeds.isEmpty())
			return;
		
		PreparedStatement statement = con.prepareStatement(CASTLE_MANOR_INSERT_PRODUCTION);
		try
		{
			for (SeedProduction s : seeds)
			{
				statement.setInt(1, getCastleId());
				statement.setInt(2, s.getId());
				statement.setInt(3, period);
				statement.setLong(4, s.getCanProduce());
				statement.setLong(5, s.getStartProduce());
				statement.setLong(6, s.getPrice());
				statement.addBatch();
			}
			statement.executeBatch();
		}
		finally
		{
			statement.close();
		}
	}
	
	//save crop procure data
	public void saveCropData()
	{
		// The old rows are replaced as a whole: all of it is stored or none of it.
		WorldTransaction.run("Castle manor crop data", () -> {
			Connection con = null;
			try
			{
				con = L2DatabaseFactory.getInstance().getConnection(con);
			
				PreparedStatement statement = con.prepareStatement(CASTLE_MANOR_DELETE_PROCURE);
				statement.setInt(1, getCastleId());
				statement.execute();
				statement.close();
				
				insertCrops(con, _procure, CastleManorManager.PERIOD_CURRENT);
				insertCrops(con, _procureNext, CastleManorManager.PERIOD_NEXT);
			}
			catch (Exception e)
			{
				_log.info("Error adding crop data for castle " + getName() + ": " + e.getMessage(), e);
			}
			finally
			{
				L2DatabaseFactory.close(con);
			}
		});
	}
	
	//	save crop procure data for specified period
	public void saveCropData(int period)
	{
		WorldTransaction.run("Castle manor crop data", () -> {
			Connection con = null;
			try
			{
				con = L2DatabaseFactory.getInstance().getConnection(con);
			
				PreparedStatement statement = con.prepareStatement(CASTLE_MANOR_DELETE_PROCURE_PERIOD);
				statement.setInt(1, getCastleId());
				statement.setInt(2, period);
				statement.execute();
				statement.close();
			
				insertCrops(con, getCropProcure(period), period);
			}
			catch (Exception e)
			{
				_log.info("Error adding crop data for castle " + getName() + ": " + e.getMessage(), e);
			}
			finally
			{
				L2DatabaseFactory.close(con);
			}
		});
	}
	
	private void insertCrops(Connection con, List<CropProcure> crops, int period) throws SQLException
	{
		if (crops == null || crops.isEmpty())
			return;
		
		PreparedStatement statement = con.prepareStatement(CASTLE_MANOR_INSERT_PROCURE);
		try
		{
			for (CropProcure cp : crops)
			{
				statement.setInt(1, getCastleId());
				statement.setInt(2, cp.getId());
				statement.setInt(3, period);
				statement.setLong(4, cp.getAmount());
				statement.setLong(5, cp.getStartAmount());
				statement.setLong(6, cp.getPrice());
				statement.setInt(7, cp.getReward());
				statement.addBatch();
			}
			statement.executeBatch();
		}
		finally
		{
			statement.close();
		}
	}
	
	public void updateCrop(int cropId, long amount, int period)
	{
		Connection con = null;
		PreparedStatement statement;
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection(con);
			
			statement = con.prepareStatement(CASTLE_UPDATE_CROP);
			statement.setLong(1, amount);
			statement.setInt(2, cropId);
			statement.setInt(3, getCastleId());
			statement.setInt(4, period);
			statement.execute();
			statement.close();
		}
		catch (Exception e)
		{
			_log.info("Error adding crop data for castle " + getName() + ": " + e.getMessage(), e);
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
	}
	
	public void updateSeed(int seedId, long amount, int period)
	{
		Connection con = null;
		PreparedStatement statement;
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection(con);
			
			statement = con.prepareStatement(CASTLE_UPDATE_SEED);
			statement.setLong(1, amount);
			statement.setInt(2, seedId);
			statement.setInt(3, getCastleId());
			statement.setInt(4, period);
			statement.execute();
			statement.close();
		}
		catch (Exception e)
		{
			_log.info("Error adding seed production data for castle " + getName() + ": " + e.getMessage(), e);
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
	}
	
	public boolean isNextPeriodApproved()
	{
		return _isNextPeriodApproved;
	}
	
	public void setNextPeriodApproved(boolean val)
	{
		_isNextPeriodApproved = val;
	}
	
	public void updateClansReputation()
	{
		if (_formerOwner != null)
		{
			if (_formerOwner != ClanTable.getInstance().getClan(getOwnerId()))
			{
				int maxreward = Math.max(0, _formerOwner.getReputationScore());
				_formerOwner.setReputationScore(_formerOwner.getReputationScore() - Config.LOOSE_CASTLE_POINTS, true);
				L2Clan owner = ClanTable.getInstance().getClan(getOwnerId());
				if (owner != null)
				{
					owner.setReputationScore(
							owner.getReputationScore() + Math.min(Config.TAKE_CASTLE_POINTS, maxreward), true);
				}
			}
			else
				_formerOwner
						.setReputationScore(_formerOwner.getReputationScore() + Config.CASTLE_DEFENDED_POINTS, true);
		}
		else
		{
			L2Clan owner = ClanTable.getInstance().getClan(getOwnerId());
			if (owner != null)
			{
				owner.setReputationScore(owner.getReputationScore() + Config.TAKE_CASTLE_POINTS, true);
			}
		}
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
					con.prepareStatement("SELECT function_type, level, lease, rate_ms, end_at FROM castle_function WHERE castle_id = ?");
			statement.setInt(1, getCastleId());
			rs = statement.executeQuery();
			while (rs.next())
			{
				_function.put(
						rs.getInt("function_type"),
						new CastleFunction(rs.getInt("function_type"), rs.getInt("level"), rs.getInt("lease"), 0, rs
								.getLong("rate_ms"), millis(rs.getTimestamp("end_at")), true));
			}
			statement.close();
		}
		catch (Exception e)
		{
			_log.error("Exception: Castle.loadFunctions(): " + e.getMessage(), e);
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
	}
	
	/** Remove function In List and in DB */
	public void removeFunction(int functionType)
	{
		_function.remove(functionType);
		Connection con = null;
		try
		{
			PreparedStatement statement;
			con = L2DatabaseFactory.getInstance().getConnection(con);
			statement = con.prepareStatement("DELETE FROM castle_function WHERE castle_id=? AND function_type=?");
			statement.setInt(1, getCastleId());
			statement.setInt(2, functionType);
			statement.execute();
			statement.close();
		}
		catch (Exception e)
		{
			_log.error("Exception: Castle.removeFunctions(int functionType): " + e.getMessage(), e);
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
			_log.warn("Called Castle.updateFunctions(int type, int lvl, int lease, long rate, boolean addNew) Owner : "
					+ getOwnerId());
		if (lease > 0)
			if (!player.destroyItemByItemId("Consume", PlayerInventory.ADENA_ID, lease, null, true))
				return false;
		if (addNew)
		{
			_function.put(type, new CastleFunction(type, lvl, lease, 0, rate, 0, false));
		}
		else
		{
			if (lvl == 0 && lease == 0)
				removeFunction(type);
			else
			{
				int diffLease = lease - _function.get(type).getLease();
				if (_log.isDebugEnabled())
					_log.warn("Called Castle.updateFunctions diffLease : " + diffLease);
				if (diffLease > 0)
				{
					_function.put(type, new CastleFunction(type, lvl, lease, 0, rate, -1, false));
				}
				else
				{
					_function.get(type).setLease(lease);
					_function.get(type).setLvl(lvl);
					_function.get(type).dbSave(false);
				}
			}
		}
		return true;
	}
	
	/**
	 * Sets the portal's coordinates
	 * @param x
	 * @param y
	 * @param z
	 */
	public void createClanGate(int x, int y, int z)
	{
		_gate[0] = x;
		_gate[1] = y;
		_gate[2] = z;
	}
	
	/** Removes the lord's portal. Optimized as much as possible. */
	public void destroyClanGate()
	{
		_gate[0] = Integer.MIN_VALUE;
	}
	
	/**
	 * <B>This method must always be called before using gate coordinate retrieval methods!</B>
	 * <BR>Optimized as much as possible.
	 * @return is a Clan Gate available
	 */
	public boolean isGateOpen()
	{
		return _gate[0] != Integer.MIN_VALUE;
	}
	
	/**
	 * @see #getGateY()
	 * @see #getGateZ()
	 * @return Clan gate location - <B>X</B>
	 */
	public int getGateX()
	{
		return _gate[0];
	}
	
	/**
	 * @see #getGateX()
	 * @see #getGateZ()
	 * @return Clan gate location - <B>Y</B>
	 */
	public int getGateY()
	{
		return _gate[1];
	}
	
	/**
	 * @see #getGateX()
	 * @see #getGateY()
	 * @return Clan gate location - <B>Z</B>
	 */
	public int getGateZ()
	{
		return _gate[2];
	}
	
	/** Checks the tax rate and decreases if invalid. Saves data to the database. */
	public void revalidateTax()
	{
		if (getTaxPercent() > getMaxTax())
			setTaxPercent(getMaxTax(), false, false);
		_taxPercentNew = _taxPercent;
	}
	
	/**
	 * Validates if the given number is higher than 0 and lower than {@link #getMaxTax()}
	 * @return whether the tax percentage is valid
	 * @see #getMaxTax()
	 */
	public static boolean validateTax(int percent)
	{
		return (percent >= 0 && percent <= getMaxTax());
	}
	
	/** @return maximum tax percentage allowed by Seven Signs */
	public static int getMaxTax()
	{
		switch (SevenSigns.getInstance().getSealOwner(SevenSigns.SEAL_STRIFE))
		{
			case SevenSigns.CABAL_DAWN:
				return 25;
			case SevenSigns.CABAL_DUSK:
				return 5;
			default:
				return 15;
		}
	}
	
	private class TaxUpdater implements Runnable
	{
		@Override
		public void run()
		{
			setTaxPercent(_taxPercentNew, true, false);
		}
	}
	
	private void startUpdateTask()
	{
		stopUpdateTask();
		Calendar update = Calendar.getInstance();
		if (update.get(Calendar.HOUR_OF_DAY) >= 0)
			update.add(Calendar.DAY_OF_MONTH, 1);
		update.set(Calendar.HOUR_OF_DAY, 0);
		_taxUpdate =
				ThreadPoolManager.getInstance().schedule(new TaxUpdater(),
						update.getTimeInMillis() - System.currentTimeMillis());
	}
	
	private void stopUpdateTask()
	{
		if (_taxUpdate != null)
			_taxUpdate.cancel(false);
	}
	
	/**
	 * Called when zones are being [re]loaded. Adds the zone to the danger zone array,
	 * then loads the upgrade data from the database and upgrades it if needed.
	 * @param sdz The newly created danger zone
	 */
	public void loadDangerZone(L2SiegeDangerZone sdz)
	{
		boolean east = (sdz.getName().endsWith("e") || sdz.getName().endsWith("i"));
		getSiege().registerZone(sdz, east);
		Connection con = null;
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection();
			PreparedStatement statement = con.prepareStatement(CASTLE_TRAP_LOAD);
			statement.setInt(1, getCastleId());
			statement.setInt(2, east ? 1 : 2);
			ResultSet rs = statement.executeQuery();
			if (rs.next())
				getSiege().activateOnLoad(east, rs.getInt(1));
			statement.close();
		}
		catch (SQLException e)
		{
			_log.error("Failed to load siege danger zone [" + sdz.getName() + "] data!", e);
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
	}
	
	/**
	 * <B>Upgrades the danger zones and saves data to the database.</B><BR>
	 * <B>level</B> indicates the first cell in the zone array to be upgraded.<BR>
	 * <B>newLevel</B> decides the last cell in the zone array to be upgraded.<BR><BR>
	 * Only the Aden castle has more than one zone/side.
	 * Level also decides which SQL statement will be used (INSERT/UPDATE).
	 * @param east Inner/Eastern zones? Otherwise Outer/Western
	 * @param level Previous upgrade level
	 * @param newLevel New upgrade level
	 */
	public void upgradeDangerZones(boolean east, int level, int newLevel)
	{
		Connection con = null;
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection();
			String sql = CASTLE_TRAP_ADD;
			if (level > 0)
				sql = CASTLE_TRAP_UPGRADE;
			PreparedStatement statement = con.prepareStatement(sql);
			statement.setInt(1, newLevel);
			statement.setInt(2, getCastleId());
			statement.setInt(3, east ? 1 : 2);
			statement.executeUpdate();
			statement.close();
			getSiege().activateZones(east, level, newLevel);
		}
		catch (SQLException e)
		{
			_log.error("Failed to upgrade siege danger zone data!", e);
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
	}
	
	/**	Remove danger zone data from the database. Doesn't downgrade the loaded zones. */
	public void resetDangerZones()
	{
		Connection con = null;
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection();
			PreparedStatement statement = con.prepareStatement(CASTLE_TRAPS_REMOVE);
			statement.setInt(1, getCastleId());
			statement.executeUpdate();
			statement.close();
		}
		catch (SQLException e)
		{
			_log.error("Failed to delete siege danger zone data!", e);
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
	}
}
