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
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;

import com.l2jfree.Config;
import com.l2jfree.L2DatabaseFactory;
import com.l2jfree.gameserver.ThreadPoolManager;
import com.l2jfree.gameserver.datatables.ClanTable;
import com.l2jfree.gameserver.datatables.DoorTable;
import com.l2jfree.gameserver.datatables.StaticObjects;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.gameobjects.instance.L2DoorInstance;
import com.l2jfree.gameserver.gameobjects.instance.L2StaticObjectInstance;
import com.l2jfree.gameserver.gameobjects.itemcontainer.PlayerInventory;
import com.l2jfree.gameserver.instancemanager.FortManager;
import com.l2jfree.gameserver.model.clan.L2Clan;
import com.l2jfree.gameserver.model.world.L2World;
import com.l2jfree.gameserver.network.SystemMessageId;
import com.l2jfree.gameserver.network.packets.server.PlaySound;
import com.l2jfree.gameserver.network.packets.server.PledgeShowInfoUpdate;
import com.l2jfree.gameserver.network.packets.server.SystemMessage;
import com.l2jfree.gameserver.persistence.WorldTransaction;

public class Fort extends Siegeable<FortSiege>
{
	// =========================================================
	// Data Field
	private int _fortId = 0;
	private final List<L2DoorInstance> _doors = new ArrayList<L2DoorInstance>();
	private L2StaticObjectInstance _flagPole = null;
	private final List<String> _doorDefault = new ArrayList<String>();
	private final List<String> _flagPoleStats = new ArrayList<String>();
	private FortSiege _siege = null;
	private Calendar _siegeDate;
	private long _lastOwnedTime;
	private final FortManager _spawnManager;
	private L2Clan _fortOwner = null;
	private int _fortType = 0;
	private int _state = 0;
	private int _castleId = 0;
	
	private final Map<Integer, FortFunction> _function;
	
	private int _blood = 0;
	private ScheduledFuture<?> _fortUpdater;
	
	/** Fortress Functions */
	public static final int FUNC_TELEPORT = 1;
	public static final int FUNC_RESTORE_HP = 2;
	public static final int FUNC_RESTORE_MP = 3;
	public static final int FUNC_RESTORE_EXP = 4;
	public static final int FUNC_SUPPORT = 5;
	
	public class FortFunction
	{
		private final int _type;
		private int _lvl;
		protected int _fee;
		protected int _tempFee;
		private final long _rate;
		private long _endDate;
		protected boolean _inDebt;
		public boolean _cwh;
		
		public FortFunction(int type, int lvl, int lease, int tempLease, long rate, long time, boolean cwh)
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
			if (getOwnerClan() == null)
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
				if (getOwnerClan() == null)
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
						getOwnerClan().getWarehouse().destroyItemByItemId("CS_function_fee", PlayerInventory.ADENA_ID, fee,
								null, null);
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
							con.prepareStatement("INSERT INTO fort_function (fort_id, function_type, level, lease, rate_ms, end_at) VALUES (?,?,?,?,?,?)");
					statement.setInt(1, getFortId());
					statement.setInt(2, getType());
					statement.setInt(3, getLvl());
					statement.setInt(4, getLease());
					statement.setLong(5, getRate());
					statement.setTimestamp(6, moment(getEndTime()));
				}
				else
				{
					statement =
							con.prepareStatement("UPDATE fort_function SET level=?, lease=?, end_at=? WHERE fort_id=? AND function_type=?");
					statement.setInt(1, getLvl());
					statement.setInt(2, getLease());
					statement.setTimestamp(3, moment(getEndTime()));
					statement.setInt(4, getFortId());
					statement.setInt(5, getType());
				}
				statement.execute();
				statement.close();
			}
			catch (Exception e)
			{
				_log.error(
						"Exception: Fort.updateFunctions(int type, int lvl, int lease, long rate, long time, boolean addNew): "
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
	
	// =========================================================
	// Constructor
	public Fort(int fortId)
	{
		super(fortId);
		_fortId = fortId;
		load();
		loadDoor();
		loadFlagPoles();
		_function = new LinkedHashMap<Integer, FortFunction>();
		if (getOwnerClan() != null)
		{
			setVisibleFlag(true);
			loadFunctions();
		}
		_spawnManager = new FortManager(this);
	}
	
	/** Return function with id */
	public FortFunction getFunction(int type)
	{
		return _function.get(type);
	}
	
	public class ScheduleSpecialEnvoysDeSpawn implements Runnable
	{
		private final Fort _fortInst;
		
		public ScheduleSpecialEnvoysDeSpawn(Fort pFort)
		{
			_fortInst = pFort;
		}
		
		@Override
		public void run()
		{
			try
			{
				// if state not decided, change state to indenpendent
				if (_fortInst.getFortState() == 0)
					_fortInst.setFortState(1, 0);
				_fortInst.getSpawnManager().despawnSpecialEnvoys();
			}
			catch (Exception e)
			{
				_log.warn(
						"Exception: ScheduleSpecialEnvoysSpawn() for Fort: " + _fortInst.getName() + " "
								+ e.getMessage(), e);
			}
		}
	}
	
	public void endOfSiege(L2Clan clan)
	{
		ThreadPoolManager.getInstance().scheduleGeneral(new endFortressSiege(this, clan), 1000);
		
	}
	
	/**
	 * @param clan
	 * @param objId
	 */
	public void Engrave(L2Clan clan)
	{
		setOwner(clan, true);
	}
	
	public void closeDoor(int doorId)
	{
		openCloseDoor(doorId, false);
	}
	
	public void openDoor(int doorId)
	{
		openCloseDoor(doorId, true);
	}
	
	public void openCloseDoor(int doorId, boolean open)
	{
		L2DoorInstance door = getDoor(doorId);
		if (door != null)
		{
			if (open)
				door.openMe();
			else
				door.closeMe();
		}
		else
			_log.info("Door " + doorId + " not found at fort " + _fortId + "!");
	}
	
	// This method is used to begin removing all fort upgrades
	public void removeUpgrade()
	{
		removeDoorUpgrade();
	}
	
	/**
	 * This method will set owner for Fort
	 * @param clan
	 * @param updateClanPoints
	 */
	public boolean setOwner(final L2Clan clan, final boolean updateClansReputation)
	{
		// The reputation, the old and the new owner, the contract state and the siege end are stored together.
		final boolean[] result = new boolean[1];
		WorldTransaction.run("Fort owner change", () -> result[0] = setOwnerInTransaction(clan, updateClansReputation));
		return result[0];
	}
	
	private boolean setOwnerInTransaction(L2Clan clan, boolean updateClansReputation)
	{
		if (updateClansReputation)
		{
			// update reputation first
			updateClansReputation(clan, false);
		}
		
		// Remove old owner
		if (getOwnerClan() != null && (clan != null && clan != getOwnerClan()))
		{
			updateClansReputation(clan, true);
			
			L2Player oldLord = getOwnerClan().getLeader().getPlayerInstance();
			if (oldLord != null && oldLord.getMountType() == 2)
				oldLord.dismount();
			
			removeOwner(true);
		}
		setFortState(0, 0); // initialize fort state
		//	if clan already have castle, don't store him in fortress
		if (clan != null && clan.getHasCastle() > 0)
		{
			getSiege().announceToPlayer(SystemMessageId.NPCS_RECAPTURED_FORTRESS.getSystemMessage(), 0, false);
			return false;
		}
		else
		{
			getSpawnManager().spawnSpecialEnvoys();
			ThreadPoolManager.getInstance().scheduleGeneral(new ScheduleSpecialEnvoysDeSpawn(this), 1 * 60 * 60 * 1000); // Prepare 1hr task for special envoys despawn
			// if clan have already fortress, remove it
			if (clan != null && clan.getHasFort() > 0)
				FortManager.getInstance().getFortByOwner(clan).removeOwner(true);
			
			setBloodOathReward(0);
			setOwnerClan(clan);
			if (clan != null)
				_ownerId = clan.getClanId();
			else
				_ownerId = 0;
			updateOwnerInDB(); // Update in database
			
			if (getSiege().getIsInProgress()) // If siege in progress
				getSiege().endSiege();
			return true;
		}
	}
	
	public void removeOwner(final boolean updateDB)
	{
		final L2Clan clan = getOwnerClan();
		
		if (clan != null)
		{
			clan.setHasFort(0);
			clan.broadcastToOnlineMembers(new PledgeShowInfoUpdate(clan));
			setOwnerClan(null);
			// The blood oath reward and the owner are stored together
			WorldTransaction.run("Fort owner removal", () -> {
				setBloodOathReward(0);
				if (updateDB)
					updateOwnerInDB();
			});
		}
	}
	
	public void setBloodOathReward(int val)
	{
		_blood = val;
		Connection con = null;
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection(con);
			PreparedStatement statement;
			
			statement = con.prepareStatement("UPDATE fort SET blood_oath_count=? WHERE id = ?");
			statement.setInt(1, _blood);
			statement.setInt(2, getFortId());
			statement.execute();
			statement.close();
		}
		catch (Exception e)
		{
			_log.error("Exception: setBloodOathReward(int val): " + e.getMessage(), e);
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
	}
	
	public int getBloodOathReward()
	{
		return _blood;
	}
	
	/**
	 * Show or hide flag inside flagpole<BR><BR>
	 */
	public void setVisibleFlag(boolean val)
	{
		L2StaticObjectInstance flagPole = getFlagPole();
		if (flagPole != null)
			flagPole.setMeshIndex(val ? 1 : 0);
	}
	
	/**
	 * Respawn all doors on fort grounds<BR><BR>
	 */
	public void resetDoors()
	{
		for (int i = 0; i < getDoors().size(); i++)
		{
			L2DoorInstance door = getDoors().get(i);
			if (door.isOpen())
				door.closeMe();
			if (door.getStatus().getCurrentHp() <= 0)
				door.doRevive();
			if (door.getCurrentHp() < door.getMaxHp())
				door.getStatus().setCurrentHp(door.getMaxHp());
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
	
	// =========================================================
	// Method - Private
	// This method loads fort
	private void load()
	{
		Connection con = null;
		try
		{
			PreparedStatement statement;
			ResultSet rs;
			
			con = L2DatabaseFactory.getInstance().getConnection(con);
			
			statement =
					con.prepareStatement("SELECT name, siege_at, owned_since_at, owner_clan_id, is_large, contract_state, contract_castle_id, blood_oath_count FROM fort WHERE id = ?");
			statement.setInt(1, getFortId());
			rs = statement.executeQuery();
			
			while (rs.next())
			{
				_name = rs.getString("name");
				_siegeDate = Calendar.getInstance();
				_siegeDate.setTimeInMillis(millis(rs.getTimestamp("siege_at")));
				_lastOwnedTime = millis(rs.getTimestamp("owned_since_at"));
				_ownerId = rs.getInt("owner_clan_id"); // NULL is 0: held by NPCs
				_fortType = rs.getBoolean("is_large") ? 1 : 0;
				_state = rs.getInt("contract_state");
				_castleId = rs.getInt("contract_castle_id"); // NULL is 0: no contract
				_blood = rs.getInt("blood_oath_count");
			}
			
			rs.close();
			statement.close();
			
			if (_ownerId > 0)
			{
				L2Clan clan = ClanTable.getInstance().getClan(_ownerId); // Try to find clan instance
				setOwnerClan(clan);
				clan.setHasFort(getFortId());
				int runCount = getOwnedTime() / (Config.FS_BLOOD_OATH_FRQ * 60);
				long initial = System.currentTimeMillis() - _lastOwnedTime;
				while (initial > (Config.FS_BLOOD_OATH_FRQ * 60000))
					initial -= (Config.FS_BLOOD_OATH_FRQ * 60000);
				initial = (Config.FS_BLOOD_OATH_FRQ * 60000) - initial;
				_fortUpdater =
						ThreadPoolManager.getInstance().scheduleGeneralAtFixedRate(
								new FortUpdater(this, clan, runCount), initial, Config.FS_BLOOD_OATH_FRQ * 60000); // Schedule owner tasks to start running
			}
			else
				setOwnerClan(null);
		}
		catch (Exception e)
		{
			_log.warn("Exception: loadFortData(): " + e.getMessage(), e);
		}
		finally
		{
			L2DatabaseFactory.close(con);
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
					con.prepareStatement("SELECT function_type, level, lease, rate_ms, end_at FROM fort_function WHERE fort_id = ?");
			statement.setInt(1, getFortId());
			rs = statement.executeQuery();
			while (rs.next())
			{
				_function.put(rs.getInt("function_type"),
						new FortFunction(rs.getInt("function_type"), rs.getInt("level"), rs.getInt("lease"), 0,
								rs.getLong("rate_ms"), millis(rs.getTimestamp("end_at")), true));
			}
			statement.close();
		}
		catch (Exception e)
		{
			_log.error("Exception: Fort.loadFunctions(): " + e.getMessage(), e);
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
			statement = con.prepareStatement("DELETE FROM fort_function WHERE fort_id=? AND function_type=?");
			statement.setInt(1, getFortId());
			statement.setInt(2, functionType);
			statement.execute();
			statement.close();
		}
		catch (Exception e)
		{
			_log.error("Exception: Fort.removeFunctions(int functionType): " + e.getMessage(), e);
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
			_log.warn("Called Fort.updateFunctions(int type, int lvl, int lease, long rate, boolean addNew) Owner : "
					+ getOwnerId());
		if (lease > 0)
			if (!player.destroyItemByItemId("Consume", PlayerInventory.ADENA_ID, lease, null, true))
				return false;
		if (addNew)
		{
			_function.put(type, new FortFunction(type, lvl, lease, 0, rate, 0, false));
		}
		else
		{
			if (lvl == 0 && lease == 0)
				removeFunction(type);
			else
			{
				int diffLease = lease - _function.get(type).getLease();
				if (_log.isDebugEnabled())
					_log.warn("Called Fort.updateFunctions diffLease : " + diffLease);
				if (diffLease > 0)
				{
					_function.put(type, new FortFunction(type, lvl, lease, 0, rate, -1, false));
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
	
	public void activateInstance()
	{
		for (final L2DoorInstance door : _doors)
		{
			door.spawnMe(door.getX(), door.getY(), door.getZ());
		}
	}
	
	// This method loads fort door data from database
	private void loadDoor()
	{
		Connection con = null;
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection(con);
			PreparedStatement statement =
					con.prepareStatement("SELECT id, name, x, y, z, range_x_min, range_y_min, range_z_min, range_x_max, range_y_max, range_z_max, hp, p_def, m_def, is_unlockable, starts_open FROM fort_static_object WHERE fort_id = ? AND object_type = 'DOOR'");
			statement.setInt(1, getFortId());
			ResultSet rs = statement.executeQuery();
			
			while (rs.next())
			{
				// Create list of the door default for use when respawning dead doors
				_doorDefault.add(rs.getString("name") + ";" + rs.getInt("id") + ";" + rs.getInt("x") + ";"
						+ rs.getInt("y") + ";" + rs.getInt("z") + ";" + rs.getInt("range_x_min") + ";"
						+ rs.getInt("range_y_min") + ";" + rs.getInt("range_z_min") + ";" + rs.getInt("range_x_max") + ";"
						+ rs.getInt("range_y_max") + ";" + rs.getInt("range_z_max") + ";" + rs.getInt("hp") + ";"
						+ rs.getInt("p_def") + ";" + rs.getInt("m_def") + ";" + rs.getBoolean("is_unlockable") + ";"
						+ rs.getBoolean("starts_open"));
				L2DoorInstance door;
				_doors.add(door = DoorTable.parseLine(_doorDefault.get(_doorDefault.size() - 1)));
				door.spawnMe(door.getX(), door.getY(), door.getZ());
				DoorTable.getInstance().putDoor(door);
			}
			
			rs.close();
			statement.close();
		}
		catch (Exception e)
		{
			_log.warn("Exception: loadFortDoor(): " + e.getMessage(), e);
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
	}
	
	// This method loads fort door upgrade data from database
	private void loadDoorUpgrade()
	{
		Connection con = null;
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection(con);
			PreparedStatement statement =
					con.prepareStatement("SELECT door_id, hp, physical_defense, magic_defense FROM fort_door_upgrade WHERE fort_id = ?");
			statement.setInt(1, getFortId());
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
			_log.warn("Exception: loadFortDoorUpgrade(): " + e.getMessage(), e);
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
	}
	
	private void removeDoorUpgrade()
	{
		Connection con = null;
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection(con);
			PreparedStatement statement =
					con.prepareStatement("DELETE FROM fort_door_upgrade WHERE fort_id = ?");
			statement.setInt(1, getFortId());
			statement.execute();
			statement.close();
		}
		catch (Exception e)
		{
			_log.warn("Exception: removeDoorUpgrade(): " + e.getMessage(), e);
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
					con.prepareStatement("INSERT INTO fort_door_upgrade (door_id, fort_id, hp, physical_defense, magic_defense) VALUES (?,?,?,?,?) "
							+ "ON CONFLICT (door_id) DO UPDATE SET fort_id = EXCLUDED.fort_id, hp = EXCLUDED.hp, physical_defense = EXCLUDED.physical_defense, magic_defense = EXCLUDED.magic_defense");
			statement.setInt(1, doorId);
			statement.setInt(2, getFortId());
			statement.setInt(3, hp);
			statement.setInt(4, pDef);
			statement.setInt(5, mDef);
			statement.execute();
			statement.close();
		}
		catch (Exception e)
		{
			_log.warn("Exception: saveDoorUpgrade(int doorId, int hp, int pDef, int mDef): " + e.getMessage(), e);
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
	}
	
	// This method loads fort flagpoles data from database
	private void loadFlagPoles()
	{
		Connection con = null;
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection();
			PreparedStatement statement =
					con.prepareStatement("SELECT id, name, x, y, z FROM fort_static_object WHERE fort_id = ? AND object_type = 'FLAG_POLE'");
			statement.setInt(1, getFortId());
			ResultSet rs = statement.executeQuery();
			while (rs.next())
			{
				//Create list of the door default for use when respawning dead doors
				_flagPoleStats.add(rs.getString("name") + ";" + rs.getInt("id") + ";" + rs.getInt("x") + ";"
						+ rs.getInt("y") + ";" + rs.getInt("z") + ";3;none;0;0");
				_flagPole = StaticObjects.parse(_flagPoleStats.get(_flagPoleStats.size() - 1));
				StaticObjects.getInstance().putObject(_flagPole);
			}
			rs.close();
			statement.close();
		}
		catch (Exception e)
		{
			_log.warn("Exception: loadFlagPoles(): " + e.getMessage(), e);
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
	}
	
	private void updateOwnerInDB()
	{
		L2Clan clan = getOwnerClan();
		int clanId = 0;
		
		if (clan != null)
		{
			clanId = clan.getClanId();
			_lastOwnedTime = System.currentTimeMillis();
		}
		else
			_lastOwnedTime = 0;
		
		Connection con = null;
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection(con);
			PreparedStatement statement;
			
			statement =
					con.prepareStatement("UPDATE fort SET owner_clan_id=?, owned_since_at=?, contract_state=?, contract_castle_id=?, blood_oath_count=? WHERE id = ?");
			if (clanId > 0)
				statement.setInt(1, clanId);
			else
				statement.setNull(1, Types.INTEGER); // held by NPCs
			statement.setTimestamp(2, moment(_lastOwnedTime));
			statement.setInt(3, 0);
			statement.setNull(4, Types.SMALLINT); // no contract with a castle
			statement.setInt(5, getBloodOathReward());
			statement.setInt(6, getFortId());
			statement.execute();
			statement.close();
			
			// ============================================================================
			// Announce to clan memebers
			if (clan != null)
			{
				clan.setHasFort(getFortId()); // Set has fort flag for new owner
				SystemMessage sm;
				sm = new SystemMessage(SystemMessageId.S1_CLAN_IS_VICTORIOUS_IN_THE_FORTRESS_BATTLE_OF_S2);
				sm.addString(clan.getName());
				sm.addFortId(getFortId());
				Collection<L2Player> pls = L2World.getInstance().getAllPlayers();
				for (L2Player player : pls)
				{
					player.sendPacket(sm);
				}
				clan.broadcastToOnlineMembers(new PledgeShowInfoUpdate(clan));
				clan.broadcastToOnlineMembers(new PlaySound(1, "Siege_Victory"));
				int runCount = getOwnedTime() / (Config.FS_BLOOD_OATH_FRQ * 60);
				long initial = System.currentTimeMillis() - _lastOwnedTime;
				while (initial > (Config.FS_BLOOD_OATH_FRQ * 60000))
					initial -= (Config.FS_BLOOD_OATH_FRQ * 60000);
				if (_fortUpdater != null)
					_fortUpdater.cancel(false);
				_fortUpdater =
						ThreadPoolManager.getInstance().scheduleGeneralAtFixedRate(
								new FortUpdater(this, clan, runCount), initial, Config.FS_BLOOD_OATH_FRQ * 60000); // Schedule owner tasks to start running
			}
			else
			{
				_fortUpdater.cancel(false);
				_fortUpdater = null;
			}
		}
		catch (Exception e)
		{
			_log.warn("Exception: updateOwnerInDB(L2Clan clan): " + e.getMessage(), e);
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
	}
	
	@Override
	public final int getFortId()
	{
		return _fortId;
	}
	
	public final L2Clan getOwnerClan()
	{
		return _fortOwner;
	}
	
	public final void setOwnerClan(L2Clan clan)
	{
		setVisibleFlag(clan != null ? true : false);
		_fortOwner = clan;
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
	
	public final L2StaticObjectInstance getFlagPole()
	{
		return _flagPole;
	}
	
	@Override
	public final FortSiege getSiege()
	{
		if (_siege == null)
		{
			_siege = new FortSiege(new Fort[] { this });
		}
		return _siege;
	}
	
	public final Calendar getSiegeDate()
	{
		return _siegeDate;
	}
	
	public final void setSiegeDate(Calendar siegeDate)
	{
		_siegeDate = siegeDate;
	}
	
	public final int getOwnedTime()
	{
		if (_lastOwnedTime == 0)
			return 0;
		
		return (int)((System.currentTimeMillis() - _lastOwnedTime) / 1000);
	}
	
	public void updateClansReputation(L2Clan owner, boolean removePoints)
	{
		if (owner != null)
		{
			if (removePoints)
				owner.setReputationScore(owner.getReputationScore() - Config.LOOSE_FORT_POINTS, true);
			else
				owner.setReputationScore(owner.getReputationScore() + Config.TAKE_FORT_POINTS, true);
		}
	}
	
	private class endFortressSiege implements Runnable
	{
		private final Fort _f;
		private final L2Clan _clan;
		
		public endFortressSiege(Fort f, L2Clan clan)
		{
			_f = f;
			_clan = clan;
		}
		
		@Override
		public void run()
		{
			_f.Engrave(_clan);
		}
		
	}
	
	/**
	 * @return Returns state of fortress.<BR><BR>
	 * 0 - not decided yet<BR>
	 * 1 - independent<BR>
	 * 2 - contracted with castle<BR>
	 */
	public final int getFortState()
	{
		return _state;
	}
	
	/**
	 * @param State.<BR>
	 * 0 - not decided yet<BR>
	 * 1 - independent<BR>
	 * 2 - contracted with castle<BR><BR>
	 * @param CastleId.<BR>
	 * set Castle Id for contracted fort
	 */
	public final void setFortState(int state, int castleId)
	{
		_state = state;
		_castleId = castleId;
		Connection con = null;
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection();
			PreparedStatement statement;
			
			statement = con.prepareStatement("UPDATE fort SET contract_state=?, contract_castle_id=? WHERE id = ?");
			statement.setInt(1, getFortState());
			if (getCastleId() > 0)
				statement.setInt(2, getCastleId());
			else
				statement.setNull(2, Types.SMALLINT); // no contract with a castle
			statement.setInt(3, getFortId());
			statement.execute();
			statement.close();
		}
		catch (Exception e)
		{
			_log.warn("Exception: updateOwnerInDB(L2Clan clan): " + e.getMessage(), e);
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
	}
	
	/**
	 * @return Returns Castle Id of fortress contracted with castle.
	 */
	@Override
	public final int getCastleId()
	{
		return _castleId;
	}
	
	/**
	 * @return Returns fortress type.<BR><BR>
	 * 0 - small (3 commanders) <BR>
	 * 1 - big (4 commanders + control room)
	 */
	public final int getFortType()
	{
		return _fortType;
	}
	
	public final int getCastleIdFromEnvoy(int npcId)
	{
		return getSpawnManager().getEnvoyCastle(npcId);
	}
	
	/**
	 * @return Returns amount of barracks.
	 */
	public final int getFortSize()
	{
		return getFortType() == 0 ? 3 : 5;
	}
	
	public FortManager getSpawnManager()
	{
		return _spawnManager;
	}
}
