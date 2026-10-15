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
import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.l2jfree.Config;
import com.l2jfree.L2DatabaseFactory;
import com.l2jfree.gameserver.ThreadPoolManager;
import com.l2jfree.gameserver.datatables.ClanTable;
import com.l2jfree.gameserver.datatables.NpcTable;
import com.l2jfree.gameserver.gameobjects.L2Creature;
import com.l2jfree.gameserver.gameobjects.L2Npc;
import com.l2jfree.gameserver.gameobjects.L2Object;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.gameobjects.instance.L2DoorInstance;
import com.l2jfree.gameserver.gameobjects.instance.L2FortCommanderInstance;
import com.l2jfree.gameserver.gameobjects.templates.L2NpcTemplate;
import com.l2jfree.gameserver.instancemanager.FortManager;
import com.l2jfree.gameserver.instancemanager.FortSiegeGuardManager;
import com.l2jfree.gameserver.instancemanager.FortSiegeManager;
import com.l2jfree.gameserver.instancemanager.FortSiegeManager.SiegeSpawn;
import com.l2jfree.gameserver.model.CombatFlag;
import com.l2jfree.gameserver.model.clan.L2Clan;
import com.l2jfree.gameserver.model.clan.L2SiegeClan;
import com.l2jfree.gameserver.model.clan.L2SiegeClan.SiegeClanType;
import com.l2jfree.gameserver.model.mapregion.TeleportWhereType;
import com.l2jfree.gameserver.model.world.spawn.L2Spawn;
import com.l2jfree.gameserver.model.zone.L2SiegeZone;
import com.l2jfree.gameserver.network.SystemMessageId;
import com.l2jfree.gameserver.network.packets.server.NpcSay;
import com.l2jfree.gameserver.network.packets.server.SystemMessage;
import com.l2jfree.gameserver.persistence.WorldTransaction;

public class FortSiege extends AbstractSiege
{
	protected static final Logger _log = LoggerFactory.getLogger(FortSiege.class);
	
	public static enum TeleportWhoType
	{
		All,
		Attacker,
		Owner
	}
	
	// ===============================================================
	// Schedule task
	public class ScheduleEndSiegeTask implements Runnable
	{
		private final Fort _fortInst;
		
		public ScheduleEndSiegeTask(Fort pFort)
		{
			_fortInst = pFort;
		}
		
		@Override
		public void run()
		{
			if (!getIsInProgress())
				return;
			
			try
			{
				_fortInst.getSiege().endSiege();
			}
			catch (Exception e)
			{
				_log.error(e.getMessage(), e);
			}
		}
	}
	
	public class ScheduleStartSiegeTask implements Runnable
	{
		private final Fort _fortInst;
		private final int _time;
		
		public ScheduleStartSiegeTask(Fort pFort, int time)
		{
			_fortInst = pFort;
			_time = time;
		}
		
		@Override
		public void run()
		{
			if (getIsInProgress())
				return;
			
			try
			{
				if (_time == 3600) // 1hr remains
				{
					ThreadPoolManager.getInstance()
							.scheduleGeneral(new ScheduleStartSiegeTask(_fortInst, 600), 3000000); // Prepare task for 10 minutes left.
				}
				else if (_time == 600) // 10min remains
				{
					getFort().getSpawnManager().despawnSuspiciousMerchant();
					announceToPlayer(new SystemMessage(SystemMessageId.S1_MINUTES_UNTIL_THE_FORTRESS_BATTLE_STARTS),
							10, false);
					ThreadPoolManager.getInstance().scheduleGeneral(new ScheduleStartSiegeTask(_fortInst, 300), 300000); // Prepare task for 5 minutes left.
				}
				else if (_time == 300) // 5min remains
				{
					announceToPlayer(new SystemMessage(SystemMessageId.S1_MINUTES_UNTIL_THE_FORTRESS_BATTLE_STARTS), 5,
							false);
					ThreadPoolManager.getInstance().scheduleGeneral(new ScheduleStartSiegeTask(_fortInst, 60), 240000); // Prepare task for 1 minute left.
				}
				else if (_time == 60) // 1min remains
				{
					announceToPlayer(new SystemMessage(SystemMessageId.S1_MINUTES_UNTIL_THE_FORTRESS_BATTLE_STARTS), 1,
							false);
					ThreadPoolManager.getInstance().scheduleGeneral(new ScheduleStartSiegeTask(_fortInst, 30), 30000); // Prepare task for 30 seconds left.
				}
				else if (_time == 30) // 30seconds remains
				{
					announceToPlayer(new SystemMessage(SystemMessageId.S1_SECONDS_UNTIL_THE_FORTRESS_BATTLE_STARTS),
							30, false);
					ThreadPoolManager.getInstance().scheduleGeneral(new ScheduleStartSiegeTask(_fortInst, 10), 20000); // Prepare task for 10 seconds left.
				}
				else if (_time == 10) // 10seconds remains
				{
					announceToPlayer(new SystemMessage(SystemMessageId.S1_SECONDS_UNTIL_THE_FORTRESS_BATTLE_STARTS),
							10, false);
					ThreadPoolManager.getInstance().scheduleGeneral(new ScheduleStartSiegeTask(_fortInst, 5), 5000); // Prepare task for 5 seconds left.
				}
				else if (_time == 5) // 5seconds remains
				{
					announceToPlayer(new SystemMessage(SystemMessageId.S1_SECONDS_UNTIL_THE_FORTRESS_BATTLE_STARTS), 5,
							false);
					ThreadPoolManager.getInstance().scheduleGeneral(new ScheduleStartSiegeTask(_fortInst, 1), 4000); // Prepare task for 1 seconds left.
				}
				else if (_time == 1) // 1seconds remains
				{
					announceToPlayer(new SystemMessage(SystemMessageId.S1_SECONDS_UNTIL_THE_FORTRESS_BATTLE_STARTS), 1,
							false);
					ThreadPoolManager.getInstance().scheduleGeneral(new ScheduleStartSiegeTask(_fortInst, 0), 1000); // Prepare task start siege.
				}
				else if (_time == 0)// start siege
				{
					_fortInst.getSiege().startSiege();
				}
				else
					_log.warn("Exception: ScheduleStartSiegeTask(): unknown siege time: " + String.valueOf(_time));
			}
			catch (Exception e)
			{
				_log.error(e.getMessage(), e);
			}
		}
	}
	
	public class ScheduleSuspicoiusMerchantSpawn implements Runnable
	{
		private final Fort _fortInst;
		
		public ScheduleSuspicoiusMerchantSpawn(Fort pFort)
		{
			_fortInst = pFort;
		}
		
		@Override
		public void run()
		{
			if (!getIsInProgress())
				return;
			
			try
			{
				_fortInst.getSpawnManager().spawnSuspiciousMerchant();
			}
			catch (Exception e)
			{
				_log.warn(
						"Exception: ScheduleSuspicoiusMerchantSpawn() for Fort: " + _fortInst.getName() + " "
								+ e.getMessage(), e);
			}
		}
	}
	
	public class ScheduleSiegeRestore implements Runnable
	{
		private final Fort _fortInst;
		
		public ScheduleSiegeRestore(Fort pFort)
		{
			_fortInst = pFort;
		}
		
		@Override
		public void run()
		{
			if (!getIsInProgress())
				return;
			
			try
			{
				_siegeRestore = null;
				_fortInst.getSiege().resetSiege();
				announceToPlayer(SystemMessageId.BARRACKS_FUNCTION_RESTORED.getSystemMessage(), 0, false);
			}
			catch (Exception e)
			{
				_log.warn("Exception: ScheduleSiegeRestore() for Fort: " + _fortInst.getName() + " " + e.getMessage(),
						e);
			}
		}
	}
	
	// =========================================================
	// Data Field
	// Attacker and Defender
	// registrations, the countdown and the relation updates of players run on different threads
	private final List<L2SiegeClan> _attackerClans = new CopyOnWriteArrayList<L2SiegeClan>();
	
	// Fort setting
	protected Map<Integer, List<L2Spawn>> _commanders = new LinkedHashMap<Integer, List<L2Spawn>>();
	protected List<L2Spawn> _commandersSpawns;
	private final Fort[] _fort;
	private boolean _isInProgress = false;
	private FortSiegeGuardManager _siegeGuardManager;
	private ScheduledFuture<?> _siegeEnd = null;
	private ScheduledFuture<?> _siegeRestore = null;
	private ScheduledFuture<?> _siegeStartTask = null;
	
	// =========================================================
	// Constructor
	public FortSiege(Fort[] fort)
	{
		_fort = fort;
		//_siegeGuardManager = new SiegeGuardManager(getFort());
		
		checkAutoTask();
	}
	
	// =========================================================
	// Siege phases
	/**
	 * When siege ends<BR><BR>
	 */
	public void endSiege()
	{
		if (getIsInProgress())
		{
			announceToPlayer(new SystemMessage(SystemMessageId.THE_FORTRESS_BATTLE_OF_S1_HAS_FINISHED), getFort()
					.getFortId(), true);
			
			removeFlags(); // Removes all flags. Note: Remove flag before teleporting players
			unSpawnFlags();
			teleportPlayer(FortSiege.TeleportWhoType.Attacker, TeleportWhereType.Town);
			_isInProgress = false; // Flag so that siege instance can be started
			getZone().updateSiegeStatus();
			// The next siege date and the cleared registrations are the stored result of the siege
			WorldTransaction.run("Fort siege end", () -> {
				saveFortSiege(); // Save fort specific data
				clearSiegeClan(); // Clear siege clan from db
			});
			removeCommanders(); // Remove commander from this fort
			getFort().getSpawnManager().spawnNpcCommanders(); // Spawn NPC commanders
			getSiegeGuardManager().unspawnSiegeGuard(); // Remove all spawned siege guard from this fort
			getFort().resetDoors(); // Respawn door to fort
			updatePlayerSiegeStateFlags(true);
			ThreadPoolManager.getInstance().scheduleGeneral(new ScheduleSuspicoiusMerchantSpawn(getFort()),
					Config.FORTSIEGE_MERCHANT_DELAY * 60 * 1000); // Prepare 3hr task for suspicious merchant respawn
			if (_siegeEnd != null)
				_siegeEnd.cancel(false);
			if (_siegeRestore != null)
				_siegeRestore.cancel(false);
			if (getFort().getOwnerClan() != null && getFort().getFlagPole().getMeshIndex() == 0)
				getFort().setVisibleFlag(true);
		}
	}
	
	/**
	 * When siege starts<BR><BR>
	 */
	public void startSiege()
	{
		if (!getIsInProgress())
		{
			if (_siegeStartTask != null) // used admin command "admin_startfortsiege"
				_siegeStartTask.cancel(false);
			_siegeStartTask = null;
			
			if (getAttackerClans().size() <= 0)
			{
				return;
			}
			_isInProgress = true; // Flag so that same siege instance cannot be started again
			
			loadSiegeClan(); // Load siege clan from db
			updatePlayerSiegeStateFlags(false);
			teleportPlayer(FortSiege.TeleportWhoType.Attacker, TeleportWhereType.Town); // Teleport to the closest town
			getFort().getSpawnManager().despawnNpcCommanders(); // Despawn NPC commanders
			spawnCommanders(); // Spawn commanders
			getFort().resetDoors(); // Spawn door
			spawnSiegeGuard(); // Spawn siege guard
			getFort().setVisibleFlag(false);
			
			getZone().updateSiegeStatus();
			
			// Schedule a task to prepare auto siege end
			_siegeEnd =
					ThreadPoolManager.getInstance().scheduleGeneral(new ScheduleEndSiegeTask(getFort()),
							Config.FORTSIEGE_LENGTH_MINUTES * 60 * 1000); // Prepare auto end task
			
			announceToPlayer(new SystemMessage(SystemMessageId.THE_FORTRESS_BATTLE_S1_HAS_BEGUN),
					getFort().getFortId(), true);
			saveFortSiege();
		}
	}
	
	// =========================================================
	// Method - Public
	/**
	 * Announce to player.<BR><BR>
	 * @param message The String of the message to send to player
	 * @param inAreaOnly The boolean flag to show message to players in area only.
	 */
	public void announceToPlayer(SystemMessage sm, int val, boolean useFortId)
	{
		if (useFortId)
			sm.addFortId(val);
		else if (val > 0)
			sm.addNumber(val);
		
		// announce messages only for participants
		L2Clan clan;
		for (L2SiegeClan siegeclan : getAttackerClans())
		{
			clan = ClanTable.getInstance().getClan(siegeclan.getClanId());
			for (L2Player member : clan.getOnlineMembers(0))
			{
				member.sendPacket(sm);
			}
		}
		if (getFort().getOwnerClan() != null)
		{
			clan = ClanTable.getInstance().getClan(getFort().getOwnerClan().getClanId());
			for (L2Player member : clan.getOnlineMembers(0))
			{
				member.sendPacket(sm);
			}
		}
	}
	
	/**
	 * Announce to player.<BR><BR>
	 * @param message The String of the message to send to player
	 * @param inAreaOnly The boolean flag to show message to players in area only.
	 */
	public void announceToPlayer(SystemMessage sm, String text)
	{
		sm.addString(text);
		// announce messages only for participants
		L2Clan clan;
		for (L2SiegeClan siegeclan : getAttackerClans())
		{
			clan = ClanTable.getInstance().getClan(siegeclan.getClanId());
			for (L2Player member : clan.getOnlineMembers(0))
			{
				member.sendPacket(sm);
			}
		}
		if (getFort().getOwnerClan() != null)
		{
			clan = ClanTable.getInstance().getClan(getFort().getOwnerClan().getClanId());
			for (L2Player member : clan.getOnlineMembers(0))
			{
				member.sendPacket(sm);
			}
		}
	}
	
	public void updatePlayerSiegeStateFlags(boolean clear)
	{
		L2Clan clan;
		for (L2SiegeClan siegeclan : getAttackerClans())
		{
			clan = ClanTable.getInstance().getClan(siegeclan.getClanId());
			for (L2Player member : clan.getOnlineMembers(0))
			{
				if (clear)
					member.setSiegeState((byte)0);
				else
					member.setSiegeState((byte)1);
				member.broadcastUserInfo();
			}
		}
		if (getFort().getOwnerClan() != null)
		{
			clan = ClanTable.getInstance().getClan(getFort().getOwnerClan().getClanId());
			for (L2Player member : clan.getOnlineMembers(0))
			{
				if (clear)
					member.setSiegeState((byte)0);
				else
					member.setSiegeState((byte)2);
				member.broadcastUserInfo();
			}
		}
	}
	
	/** Return true if object is inside the zone */
	public boolean checkIfInZone(L2Object object)
	{
		return checkIfInZone(object.getX(), object.getY(), object.getZ());
	}
	
	/** Return true if object is inside the zone */
	public boolean checkIfInZone(int x, int y, int z)
	{
		return (getIsInProgress() && (getFort().checkIfInZone(x, y, z))); // Fort zone during siege
	}
	
	/**
	 * Return true if clan is attacker<BR><BR>
	 * @param clan The L2Clan of the player
	 */
	@Override
	public boolean checkIsAttacker(L2Clan clan)
	{
		return (getAttackerClan(clan) != null);
	}
	
	/**
	 * Return true if clan is defender<BR><BR>
	 * @param clan The L2Clan of the player
	 */
	@Override
	public boolean checkIsDefender(L2Clan clan)
	{
		return clan != null && getFort().getOwnerClan() == clan;
	}
	
	/** Clear all registered siege clans from database for fort */
	public void clearSiegeClan()
	{
		Connection con = null;
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection(con);
			PreparedStatement statement = con.prepareStatement("DELETE FROM fort_siege_clan WHERE fort_id=?");
			statement.setInt(1, getFort().getFortId());
			statement.execute();
			statement.close();
			
			if (getFort().getOwnerClan() != null)
			{
				PreparedStatement statement2 = con.prepareStatement("DELETE FROM fort_siege_clan WHERE clan_id=?");
				statement2.setInt(1, getFort().getOwnerClan().getClanId());
				statement2.execute();
				statement2.close();
			}
			
			getAttackerClans().clear();
			
			// if siege is in progress, end siege
			if (getIsInProgress())
				endSiege();
			// if siege isnt in progress (1hr waiting time till siege starts), cancel waiting time and spawn Suspicious Merchant
			if (_siegeStartTask != null)
			{
				_siegeStartTask.cancel(true);
				_siegeStartTask = null;
				ThreadPoolManager.getInstance().executeTask(new ScheduleSuspicoiusMerchantSpawn(getFort()));
			}
		}
		catch (Exception e)
		{
			_log.warn("Exception: clearSiegeClan(): " + e.getMessage(), e);
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
	}
	
	/** Set the date for the next siege. */
	private void clearSiegeDate()
	{
		getFort().getSiegeDate().setTimeInMillis(0);
	}
	
	/** Return list of L2Player in the zone. */
	public List<L2Player> getPlayersInZone()
	{
		List<L2Player> lst = new ArrayList<L2Player>();
		for (L2Creature cha : getZone().getCharactersInside())
		{
			if (cha instanceof L2Player)
				lst.add((L2Player)cha);
		}
		return lst;
	}
	
	/** Return list of L2Player owning the fort in the zone. */
	public List<L2Player> getOwnersInZone()
	{
		List<L2Player> lst = new ArrayList<L2Player>();
		for (L2Creature cha : getZone().getCharactersInside())
		{
			if (cha instanceof L2Player && ((L2Player)cha).getClan() != null
					&& ((L2Player)cha).getClan() == getFort().getOwnerClan())
				lst.add((L2Player)cha);
		}
		return lst;
	}
	
	/** Return list of L2Player registered as attacker in the zone. */
	public List<L2Player> getAttackersInZone()
	{
		List<L2Player> players = new ArrayList<L2Player>();
		L2Clan clan;
		for (L2SiegeClan siegeclan : getAttackerClans())
		{
			clan = ClanTable.getInstance().getClan(siegeclan.getClanId());
			for (L2Player player : clan.getOnlineMembers(0))
			{
				if (checkIfInZone(player.getX(), player.getY(), player.getZ()))
					players.add(player);
			}
		}
		return players;
	}
	
	/** Commander was killed */
	public void killedCommander(L2FortCommanderInstance instance)
	{
		if (_commanders != null && !_commanders.get(getFort().getFortId()).isEmpty())
		{
			L2Spawn spawn = instance.getSpawn();
			if (spawn != null)
			{
				List<SiegeSpawn> commanders =
						FortSiegeManager.getInstance().getCommanderSpawnList(getFort().getFortId());
				for (SiegeSpawn spawn2 : commanders)
				{
					if (spawn2.getNpcId() == spawn.getNpcid())
					{
						String text = "";
						switch (spawn2.getId())
						{
							case 1:
								text =
										"You may have broken our arrows, but you will never break our will! Archers retreat!";
								break;
							case 2:
								text = "Aieeee! Command Center! This is guard unit! We need backup right away!";
								break;
							case 3:
								text =
										"At last! The Magic Field that protects the fortress has weakened! Volunteers, stand back!";
								break;
							case 4:
								text =
										"I feel so much grief that I can't even take care of myself. There isn't any reason for me to stay here any longer.";
								break;
						}
						if (!text.isEmpty())
							instance.broadcastPacket(new NpcSay(instance.getObjectId(), 1, instance.getNpcId(), text));
					}
				}
				_commanders.get(getFort().getFortId()).remove(spawn);
				if (_commanders.get(getFort().getFortId()).size() == 0)
				{
					// spawn fort flags
					spawnFlag(getFort().getFortId());
					// cancel door/commanders respawn
					if (_siegeRestore != null)
					{
						_siegeRestore.cancel(false);
					}
					// open doors in main building
					for (L2DoorInstance door : getFort().getDoors())
					{
						if (!door.isCommanderDoor())
							continue;
						door.openMe();
					}
					getFort().getSiege().announceToPlayer(SystemMessageId.ALL_BARRACKS_OCCUPIED.getSystemMessage(), 0,
							false);
				}
				// schedule restoring doors/commanders respawn
				else if (_siegeRestore == null)
				{
					getFort().getSiege().announceToPlayer(SystemMessageId.SEIZED_BARRACKS.getSystemMessage(), 0, false);
					_siegeRestore =
							ThreadPoolManager.getInstance().scheduleGeneral(new ScheduleSiegeRestore(getFort()),
									Config.FORTSIEGE_COUNTDOWN_LENGTH * 60 * 1000);
				}
				else
					getFort().getSiege().announceToPlayer(SystemMessageId.SEIZED_BARRACKS.getSystemMessage(), 0, false);
			}
			else
				_log.warn("FortSiege.killedCommander(): killed commander, but commander not registered for fortress. NpcId: "
						+ instance.getNpcId() + " FortId: " + getFort().getFortId());
		}
	}
	
	/** Remove the flag that was killed */
	public void killedFlag(L2Npc flag)
	{
		if (flag == null)
			return;
		for (L2SiegeClan clan : getAttackerClans())
		{
			if (clan.removeFlag(flag))
				return;
		}
	}
	
	/**
	 * Register clan as attacker<BR><BR>
	 * @param player The L2Player of the player trying to register
	 */
	public boolean registerAttacker(L2Player player, boolean force)
	{
		if (player.getClan() == null)
			return false;
		if (force || checkIfCanRegister(player))
		{
			saveSiegeClan(player.getClan()); // Save to database
			// if the first registering we start the timer
			if (getAttackerClans().size() == 1)
			{
				if (!force)
					player.reduceAdena("siege", 250000, null, true);
				startAutoTask(true);
			}
			return true;
		}
		return false;
	}
	
	/**
	 * Remove clan from siege<BR><BR>
	 * @param clanId The int of player's clan id
	 */
	public void removeSiegeClan(int clanId)
	{
		
		Connection con = null;
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection(con);
			PreparedStatement statement;
			if (clanId != 0)
			{
				statement = con.prepareStatement("DELETE FROM fort_siege_clan WHERE fort_id=? AND clan_id=?");
				statement.setInt(1, getFort().getFortId());
				statement.setInt(2, clanId);
			}
			else
			{
				statement = con.prepareStatement("DELETE FROM fort_siege_clan WHERE fort_id=?");
				statement.setInt(1, getFort().getFortId());
			}
			statement.execute();
			statement.close();
			
			loadSiegeClan();
			if (getAttackerClans().size() == 0)
			{
				if (getIsInProgress())
					endSiege();
				if (_siegeStartTask != null)
				{
					_siegeStartTask.cancel(true);
					_siegeStartTask = null;
					ThreadPoolManager.getInstance().executeTask(new ScheduleSuspicoiusMerchantSpawn(getFort()));
				}
			}
		}
		catch (Exception e)
		{
			_log.error(e.getMessage(), e);
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
	}
	
	/**
	 * Remove clan from siege<BR><BR>
	 * @param clan
	 */
	public void removeSiegeClan(L2Clan clan)
	{
		if (clan == null || clan.getHasFort() == getFort().getFortId()
				|| !FortSiegeManager.getInstance().checkIsRegistered(clan, getFort().getFortId()))
			return;
		removeSiegeClan(clan.getClanId());
	}
	
	/**
	 * Remove clan from siege<BR><BR>
	 * @param player The L2Player of player/clan being removed
	 */
	public void removeSiegeClan(L2Player player)
	{
		removeSiegeClan(player.getClan());
	}
	
	/**
	 * Start the auto tasks<BR>
	 * <BR>
	 */
	public void checkAutoTask()
	{
		if (getFort().getSiegeDate().getTimeInMillis() < System.currentTimeMillis())
		{
			clearSiegeDate();
			saveSiegeDate();
			removeSiegeClan(0); // remove all clans
			return;
		}
		
		startAutoTask(false);
	}
	
	/**
	 * Start the auto tasks<BR><BR>
	 */
	public void startAutoTask(boolean setTime)
	{
		if (setTime)
			setSiegeDateTime();
		if (getFort().getOwnerClan() != null)
		{
			for (L2Player member : getFort().getOwnerClan().getOnlineMembers(0))
			{
				member.sendPacket(SystemMessageId.A_FORTRESS_IS_UNDER_ATTACK.getSystemMessage());
			}
		}
		//System.out.println("Siege of " + getFort().getName() + ": " + getFort().getSiegeDate().getTime());
		loadSiegeClan();
		// Execute siege auto start
		_siegeStartTask =
				ThreadPoolManager.getInstance().scheduleGeneral(new FortSiege.ScheduleStartSiegeTask(getFort(), 3600),
						0);
	}
	
	/**
	 * Teleport players
	 */
	public void teleportPlayer(TeleportWhoType teleportWho, TeleportWhereType teleportWhere)
	{
		List<L2Player> players;
		switch (teleportWho)
		{
			case Owner:
				players = getOwnersInZone();
				break;
			case Attacker:
				players = getAttackersInZone();
				break;
			default:
				players = getPlayersInZone();
		}
		
		for (L2Player player : players)
		{
			if (player.isGM() || player.isInJail())
				continue;
			player.teleToLocation(teleportWhere);
		}
	}
	
	// =========================================================
	// Method - Private
	/**
	 * Add clan as attacker<BR><BR>
	 * @param clanId The int of clan's id
	 */
	private void addAttacker(int clanId)
	{
		getAttackerClans().add(new L2SiegeClan(clanId, SiegeClanType.ATTACKER)); // Add registered attacker to attacker list
	}
	
	/**
	 * Return true if the player can register.<BR><BR>
	 * @param player The L2Player of the player trying to register
	 */
	public boolean checkIfCanRegister(L2Player player)
	{
		boolean b = true;
		if (player.getClan() == null || player.getClan().getLevel() < Config.FORTSIEGE_CLAN_MIN_LEVEL)
		{
			b = false;
			player.sendMessage("Only clans with Level " + Config.FORTSIEGE_CLAN_MIN_LEVEL
					+ " and higher may register for a fortress siege.");
		}
		else if (!player.isClanLeader())
		{
			b = false;
			player.sendPacket(SystemMessageId.ONLY_THE_CLAN_LEADER_IS_ENABLED);
		}
		else if (player.getClan() == getFort().getOwnerClan())
		{
			b = false;
			player.sendPacket(SystemMessageId.CLAN_THAT_OWNS_CASTLE_IS_AUTOMATICALLY_REGISTERED_DEFENDING);
		}
		else if (getFort().getOwnerClan() != null && player.getClan().getHasCastle() > 0
				&& player.getClan().getHasCastle() == getFort().getCastleId())
		{
			b = false;
			player.sendPacket(SystemMessageId.CANT_REGISTER_TO_SIEGE_DUE_TO_CONTRACT);
		}
		else if (getFort().getSiege().getAttackerClans().isEmpty() && player.getInventory().getAdena() < 250000)
		{
			b = false;
			player.sendMessage("You need 250,000 adena to register"); // replace me with html
		}
		else
		{
			for (Fort fort : FortManager.getInstance().getForts())
			{
				if (fort.getSiege().getAttackerClan(player.getClanId()) != null)
				{
					b = false;
					player.sendPacket(SystemMessageId.ALREADY_REQUESTED_SIEGE_BATTLE);
					break;
				}
				if (fort.getOwnerClan() == player.getClan()
						&& (fort.getSiege().getIsInProgress() || fort.getSiege()._siegeStartTask != null))
				{
					b = false;
					player.sendPacket(SystemMessageId.ALREADY_REQUESTED_SIEGE_BATTLE);
					break;
				}
			}
		}
		return b;
	}
	
	/**
	 * Return true if the clan has already registered to a siege for the same day.<BR><BR>
	 * @param clan The L2Clan of the player trying to register
	 */
	public boolean checkIfAlreadyRegisteredForSameDay(L2Clan clan)
	{
		for (FortSiege siege : FortSiegeManager.getInstance().getSieges())
		{
			if (siege == this)
				continue;
			if (siege.getSiegeDate().get(Calendar.DAY_OF_WEEK) == getSiegeDate().get(Calendar.DAY_OF_WEEK))
			{
				if (siege.checkIsAttacker(clan))
					return true;
				if (siege.checkIsDefender(clan))
					return true;
			}
		}
		return false;
	}
	
	private void setSiegeDateTime()
	{
		Calendar newDate = Calendar.getInstance();
		newDate.add(Calendar.MINUTE, 60);
		getFort().setSiegeDate(newDate);
		saveSiegeDate();
	}
	
	/** Load siege clans. */
	private void loadSiegeClan()
	{
		Connection con = null;
		try
		{
			getAttackerClans().clear();
			
			PreparedStatement statement = null;
			ResultSet rs = null;
			
			con = L2DatabaseFactory.getInstance().getConnection(con);
			
			statement = con.prepareStatement("SELECT clan_id FROM fort_siege_clan WHERE fort_id=?");
			statement.setInt(1, getFort().getFortId());
			rs = statement.executeQuery();
			
			while (rs.next())
			{
				addAttacker(rs.getInt("clan_id"));
			}
			
			rs.close();
			statement.close();
		}
		catch (Exception e)
		{
			_log.warn("Exception: loadSiegeClan(): " + e.getMessage(), e);
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
	}
	
	/** Remove commanders. */
	private void removeCommanders()
	{
		if (_commanders != null && !_commanders.isEmpty())
		{
			int fortId = getFort().getFortId();
			// Remove all instance of commanders for this fort
			for (L2Spawn spawn : _commanders.get(fortId))
			{
				if (spawn != null)
				{
					spawn.stopRespawn();
					spawn.getLastSpawn().deleteMe();
				}
			}
			_commanders.get(fortId).clear();
		}
	}
	
	/** Remove all flags. */
	private void removeFlags()
	{
		for (L2SiegeClan sc : getAttackerClans())
		{
			if (sc != null)
				sc.removeFlags();
		}
	}
	
	/** Save fort siege related to database. */
	private void saveFortSiege()
	{
		clearSiegeDate(); // clear siege date
		saveSiegeDate(); // Save the new date
	}
	
	/** Save siege date to database. */
	private void saveSiegeDate()
	{
		Connection con = null;
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection(con);
			PreparedStatement statement = con.prepareStatement("UPDATE fort SET siege_at = ? WHERE id = ?");
			long siegeDate = getSiegeDate().getTimeInMillis();
			statement.setTimestamp(1, siegeDate > 0 ? new Timestamp(siegeDate) : null);
			statement.setInt(2, getFort().getFortId());
			statement.execute();
			
			statement.close();
		}
		catch (Exception e)
		{
			_log.warn("Exception: saveSiegeDate(): " + e.getMessage(), e);
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
	}
	
	/**
	 * Save registration to database.<BR><BR>
	 * @param clan The L2Clan of player
	 * @param typeId -1 = owner 0 = defender, 1 = attacker, 2 = defender waiting
	 */
	private void saveSiegeClan(L2Clan clan)
	{
		Connection con = null;
		try
		{
			if (getAttackerClans().size() >= Config.FORTSIEGE_MAX_ATTACKER)
				return;
			
			con = L2DatabaseFactory.getInstance().getConnection(con);
			PreparedStatement statement;
			statement = con.prepareStatement("INSERT INTO fort_siege_clan (clan_id, fort_id) VALUES (?,?) ON CONFLICT (fort_id, clan_id) DO NOTHING");
			statement.setInt(1, clan.getClanId());
			statement.setInt(2, getFort().getFortId());
			statement.execute();
			statement.close();
			
			addAttacker(clan.getClanId());
		}
		catch (Exception e)
		{
			_log.warn(
					"Exception: saveSiegeClan(L2Clan clan, int typeId, boolean isUpdateRegistration): "
							+ e.getMessage(), e);
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
	}
	
	/** Spawn commanders. */
	private void spawnCommanders()
	{
		//Set commanders array size if one does not exist
		try
		{
			_commanders.clear();
			L2Spawn spawnDat;
			L2NpcTemplate template1;
			_commandersSpawns = new ArrayList<L2Spawn>();
			for (SiegeSpawn _sp : FortSiegeManager.getInstance().getCommanderSpawnList(getFort().getFortId()))
			{
				template1 = NpcTable.getInstance().getTemplate(_sp.getNpcId());
				if (template1 != null)
				{
					spawnDat = new L2Spawn(template1);
					spawnDat.setAmount(1);
					spawnDat.setLocx(_sp.getLocation().getX());
					spawnDat.setLocy(_sp.getLocation().getY());
					spawnDat.setLocz(_sp.getLocation().getZ());
					spawnDat.setHeading(_sp.getLocation().getHeading());
					spawnDat.setRespawnDelay(60);
					spawnDat.doSpawn();
					spawnDat.stopRespawn();
					_commandersSpawns.add(spawnDat);
				}
				else
				{
					_log.warn("FortSiege.spawnCommander: Data missing in NPC table for ID: " + _sp.getNpcId() + ".");
				}
				_commanders.put(getFort().getFortId(), _commandersSpawns);
			}
		}
		catch (Exception e)
		{
			// problem with initializing spawn, go to next one
			_log.warn("FortSiege.spawnCommander: Spawn could not be initialized: " + e.getMessage(), e);
		}
	}
	
	private void spawnFlag(int Id)
	{
		List<CombatFlag> list = FortSiegeManager.getInstance().getFlagList(Id);
		if (list == null)
			return;
		
		for (CombatFlag cf : list)
		{
			cf.spawnMe();
		}
	}
	
	private void unSpawnFlags()
	{
		List<CombatFlag> list = FortSiegeManager.getInstance().getFlagList(getFort().getFortId());
		if (list == null)
			return;
		
		for (CombatFlag cf : list)
		{
			cf.unSpawnMe();
		}
	}
	
	/**
	 * Spawn siege guard.<BR><BR>
	 */
	private void spawnSiegeGuard()
	{
		getSiegeGuardManager().spawnSiegeGuard();
	}
	
	/**
	 * Always returns null.
	 * @param clan A clan
	 */
	@Override
	public final L2SiegeClan getDefenderClan(L2Clan clan)
	{
		return null;
	}
	
	@Override
	public final L2SiegeClan getAttackerClan(L2Clan clan)
	{
		if (clan == null)
			return null;
		return getAttackerClan(clan.getClanId());
	}
	
	public final L2SiegeClan getAttackerClan(int clanId)
	{
		for (L2SiegeClan sc : getAttackerClans())
			if (sc != null && sc.getClanId() == clanId)
				return sc;
		return null;
	}
	
	public final List<L2SiegeClan> getAttackerClans()
	{
		return _attackerClans;
	}
	
	public final Fort getFort()
	{
		if (_fort == null || _fort.length <= 0)
			return null;
		return _fort[0];
	}
	
	@Override
	public final boolean getIsInProgress()
	{
		return _isInProgress;
	}
	
	public final Calendar getSiegeDate()
	{
		return getFort().getSiegeDate();
	}
	
	public Set<L2Npc> getFlag(L2Clan clan)
	{
		if (clan != null)
		{
			L2SiegeClan sc = getAttackerClan(clan);
			if (sc != null)
				return sc.getFlag();
		}
		return null;
	}
	
	public L2Npc getClosestFlag(L2Object obj)
	{
		if ((obj != null) && (obj instanceof L2Player))
		{
			if (((L2Player)obj).getClan() != null)
			{
				L2SiegeClan sc = getAttackerClan(((L2Player)obj).getClan());
				if (sc != null)
					return sc.getClosestFlag(obj);
			}
		}
		return null;
	}
	
	public final FortSiegeGuardManager getSiegeGuardManager()
	{
		if (_siegeGuardManager == null)
		{
			_siegeGuardManager = new FortSiegeGuardManager(getFort());
		}
		return _siegeGuardManager;
	}
	
	public final L2SiegeZone getZone()
	{
		return getFort().getBattlefield();
	}
	
	public void resetSiege()
	{
		// reload commanders and repair doors
		removeCommanders();
		spawnCommanders();
		getFort().resetDoors();
	}
	
	public Map<Integer, List<L2Spawn>> getCommanders()
	{
		return _commanders;
	}
	
}
