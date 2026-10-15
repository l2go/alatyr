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
import java.util.List;
import java.util.Set;

import org.apache.commons.lang3.ArrayUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.l2jfree.Config;
import com.l2jfree.L2DatabaseFactory;
import com.l2jfree.gameserver.Announcements;
import com.l2jfree.gameserver.datatables.ClanTable;
import com.l2jfree.gameserver.datatables.NpcTable;
import com.l2jfree.gameserver.gameobjects.L2Npc;
import com.l2jfree.gameserver.gameobjects.L2Object;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.gameobjects.instance.L2ControlTowerInstance;
import com.l2jfree.gameserver.gameobjects.instance.L2FlameControlTowerInstance;
import com.l2jfree.gameserver.gameobjects.templates.L2NpcTemplate;
import com.l2jfree.gameserver.idfactory.IdFactory;
import com.l2jfree.gameserver.instancemanager.SiegeGuardManager;
import com.l2jfree.gameserver.instancemanager.SiegeManager;
import com.l2jfree.gameserver.instancemanager.SiegeManager.SiegeSpawn;
import com.l2jfree.gameserver.instancemanager.TownManager;
import com.l2jfree.gameserver.model.clan.L2Clan;
import com.l2jfree.gameserver.model.clan.L2SiegeClan;
import com.l2jfree.gameserver.model.clan.L2SiegeClan.SiegeClanType;
import com.l2jfree.gameserver.model.mapregion.TeleportWhereType;
import com.l2jfree.gameserver.model.sevensigns.SevenSigns;
import com.l2jfree.gameserver.model.world.L2World;
import com.l2jfree.gameserver.model.world.spawn.L2Spawn;
import com.l2jfree.gameserver.model.zone.L2SiegeDangerZone;
import com.l2jfree.gameserver.model.zone.L2SiegeZone;
import com.l2jfree.gameserver.model.zone.L2Zone;
import com.l2jfree.gameserver.network.SystemMessageId;
import com.l2jfree.gameserver.network.packets.server.SiegeInfo;
import com.l2jfree.gameserver.network.packets.server.SystemMessage;
import com.l2jfree.gameserver.persistence.WorldTransaction;
import com.l2jfree.gameserver.threadmanager.ExclusiveTask;
import com.l2jfree.gameserver.util.Broadcast;
import com.l2jfree.util.L2FastSet;

public class Siege extends AbstractSiege
{
	private final static Logger _log = LoggerFactory.getLogger(Siege.class);
	
	// ==========================================================================================
	// Message to add/check
	//  id=17  msg=[Castle siege has begun.] c3_attr1=[SystemMsg_k.17]
	//  id=18  msg=[Castle siege is over.]   c3_attr1=[SystemMsg_k.18]
	//  id=288 msg=[The castle gate has been broken down.]
	//  id=291 msg=[Clan $s1 is victorious over $s2's castle siege!]
	//  id=292 msg=[$s1 has announced the castle siege time.]
	//  - id=293 msg=[The registration term for $s1 has ended.]
	//  - id=358 msg=[$s1 hour(s) until castle siege conclusion.]
	//  - id=359 msg=[$s1 minute(s) until castle siege conclusion.]
	//  - id=360 msg=[Castle siege $s1 second(s) left!]
	//  id=640 msg=[You have failed to refuse castle defense aid.]
	//  id=641 msg=[You have failed to approve castle defense aid.]
	//  id=644 msg=[You are not yet registered for the castle siege.]
	//  - id=645 msg=[Only clans with Level 4 and higher may register for a castle siege.]
	//  id=646 msg=[You do not have the authority to modify the castle defender list.]
	//  - id=688 msg=[The clan that owns the castle is automatically registered on the defending side.]
	//  id=689 msg=[A clan that owns a castle cannot participate in another siege.]
	//  id=690 msg=[You cannot register on the attacking side because you are part of an alliance with the clan that owns the castle.]
	//  id=718 msg=[The castle gates cannot be opened and closed during a siege.]
	//  - id=295 msg=[$s1's siege was canceled because there were no clans that participated.]
	//  id=659 msg=[This is not the time for siege registration and so registrations cannot be accepted or rejected.]
	//  - id=660 msg=[This is not the time for siege registration and so registration and cancellation cannot be done.]
	//  id=663 msg=[The siege time has been declared for $s. It is not possible to change the time after a siege time has been declared. Do you want to continue?]
	//  id=667 msg=[You are registering on the attacking side of the $s1 siege. Do you want to continue?]
	//  id=668 msg=[You are registering on the defending side of the $s1 siege. Do you want to continue?]
	//  id=669 msg=[You are canceling your application to participate in the $s1 siege battle. Do you want to continue?]
	//  id=707 msg=[You cannot teleport to a village that is in a siege.]
	//  - id=711 msg=[The siege of $s1 has started.]
	//  - id=712 msg=[The siege of $s1 has finished.]
	//  id=844 msg=[The siege to conquer $s1 has begun.]
	//  - id=845 msg=[The deadline to register for the siege of $s1 has passed.]
	//  - id=846 msg=[The siege of $s1 has been canceled due to lack of interest.]
	//  - id=856 msg=[The siege of $s1 has ended in a draw.]
	//  id=285 msg=[Clan $s1 has succeeded in engraving the ruler!]
	//  - id=287 msg=[The opponent clan has begun to engrave the ruler.]
	
	public static enum TeleportWhoType
	{
		All,
		Attacker,
		DefenderNotOwner,
		Owner,
		Spectator
	}
	
	private int _controlTowerCount;
	private int _controlTowerMaxCount;
	
	private L2SiegeDangerZone[] _dangerZonesE = null;
	private L2SiegeDangerZone[] _dangerZonesW = null;
	
	// ===============================================================
	// Schedule task
	
	private final ExclusiveTask _endSiegeTask = new ExclusiveTask() {
		@Override
		protected void onElapsed()
		{
			if (!getIsInProgress())
			{
				cancel();
				return;
			}
			
			final long timeRemaining = _siegeEndDate.getTimeInMillis() - System.currentTimeMillis();
			
			if (timeRemaining <= 0)
			{
				endSiege();
				cancel();
				return;
			}
			
			if (3600000 > timeRemaining)
			{
				if (timeRemaining > 120000)
					announceToPlayer(
							new SystemMessage(SystemMessageId.S1_MINUTES_UNTIL_SIEGE_CONCLUSION).addNumber(Math
									.round(timeRemaining / 60000)), true);
				else
					announceToPlayer(new SystemMessage(SystemMessageId.CASTLE_SIEGE_S1_SECONDS_LEFT).addNumber(Math
							.round(timeRemaining / 1000)), true);
			}
			
			int divider;
			
			if (timeRemaining > 3600000)
				divider = 3600000; // 1 hour
				
			else if (timeRemaining > 600000)
				divider = 600000; // 10 min
				
			else if (timeRemaining > 60000)
				divider = 60000; // 1 min
				
			else if (timeRemaining > 10000)
				divider = 10000; // 10 sec
				
			else
				divider = 1000; // 1 sec
				
			schedule(timeRemaining % divider);
		}
	};
	
	private final ExclusiveTask _startSiegeTask = new ExclusiveTask() {
		@Override
		protected void onElapsed()
		{
			if (getIsInProgress())
			{
				cancel();
				return;
			}
			
			if (!getIsTimeRegistrationOver())
			{
				long regTimeRemaining = getTimeRegistrationOverDate().getTimeInMillis() - System.currentTimeMillis();
				
				if (regTimeRemaining > 0)
				{
					schedule(regTimeRemaining);
					return;
				}
				
				endTimeRegistration(true);
			}
			
			final long timeRemaining = getSiegeDate().getTimeInMillis() - System.currentTimeMillis();
			
			if (timeRemaining <= 0)
			{
				startSiege();
				cancel();
				return;
			}
			
			if (86400000 > timeRemaining)
			{
				if (!_isRegistrationOver)
				{
					_isRegistrationOver = true;
					announceToPlayer(
							new SystemMessage(SystemMessageId.REGISTRATION_TERM_FOR_S1_ENDED).addString(getCastle()
									.getName()), false);
					clearSiegeWaitingClan();
				}
				
				if (timeRemaining > 7200000)
					announceToPlayer(Math.round(timeRemaining / 3600000.0) + " hour(s) until " + getCastle().getName()
							+ " siege begin.", false);
				
				else if (timeRemaining > 120000)
					announceToPlayer(Math.round(timeRemaining / 60000.0) + " minute(s) until " + getCastle().getName()
							+ " siege begin.", false);
				
				else
					announceToPlayer(getCastle().getName() + " siege " + Math.round(timeRemaining / 1000.0)
							+ " second(s) to start!", false);
			}
			
			int divider;
			
			if (timeRemaining > 86400000)
				divider = 86400000; // 1 day
				
			else if (timeRemaining > 3600000)
				divider = 3600000; // 1 hour
				
			else if (timeRemaining > 600000)
				divider = 600000; // 10 min
				
			else if (timeRemaining > 60000)
				divider = 60000; // 1 min
				
			else if (timeRemaining > 10000)
				divider = 10000; // 10 sec
				
			else
				divider = 1000; // 1 sec
				
			schedule(timeRemaining % divider);
		}
	};
	
	// =========================================================
	// Data Field
	// Attacker and Defender
	private final Set<L2SiegeClan> _attackerClans = new L2FastSet<L2SiegeClan>().setShared(true);
	
	private final Set<L2SiegeClan> _defenderClans = new L2FastSet<L2SiegeClan>().setShared(true);
	private final Set<L2SiegeClan> _defenderWaitingClans = new L2FastSet<L2SiegeClan>().setShared(true);
	
	// Castle setting
	private final Set<L2ControlTowerInstance> _controlTowers = new L2FastSet<L2ControlTowerInstance>().setShared(true);
	private final Set<L2FlameControlTowerInstance> _flameTowers = new L2FastSet<L2FlameControlTowerInstance>()
			.setShared(true);
	
	private final Castle _castle;
	private boolean _isInProgress = false;
	protected boolean _isRegistrationOver = false;
	protected Calendar _siegeEndDate;
	private final SiegeGuardManager _siegeGuardManager;
	private int oldOwner = 0; // 0 - NPC, > 0 - clan
	
	// =========================================================
	// Constructor
	public Siege(Castle castle)
	{
		_castle = castle;
		_siegeGuardManager = new SiegeGuardManager(getCastle());
		startAutoTask();
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
			announceToPlayer(SystemMessageId.CASTLE_SIEGE_HAS_ENDED.getSystemMessage(), true);
			SystemMessage sm;
			sm = new SystemMessage(SystemMessageId.SIEGE_OF_S1_FINISHED);
			sm.addString(getCastle().getName());
			announceToPlayer(sm, false);
			
			announceToParticipants(SystemMessageId.TEMPORARY_ALLIANCE_DISSOLVED.getSystemMessage());
			
			getCastle().destroyClanGate();
			
			if (getCastle().getOwnerId() <= 0)
			{
				sm = new SystemMessage(SystemMessageId.SIEGE_S1_DRAW);
				sm.addString(getCastle().getName());
				announceToPlayer(sm, false);
			}
			if (oldOwner != getCastle().getOwnerId())
			{
				announceToPlayer(SystemMessageId.NEW_CASTLE_LORD.getSystemMessage(), true); //is it really true?
				sm = new SystemMessage(SystemMessageId.CLAN_S1_VICTORIOUS_OVER_S2_S_SIEGE);
				sm.addString(ClanTable.getInstance().getClan(getCastle().getOwnerId()).getName());
				sm.addString(getCastle().getName());
				announceToPlayer(sm, false);
			}
			
			getCastle().updateClansReputation();
			removeFlags(); // Removes all flags. Note: Remove flag before teleporting players
			teleportPlayer(Siege.TeleportWhoType.Attacker, TeleportWhereType.Town); // Teleport to the second closest town
			teleportPlayer(Siege.TeleportWhoType.DefenderNotOwner, TeleportWhereType.Town); // Teleport to the second closest town
			teleportPlayer(Siege.TeleportWhoType.Spectator, TeleportWhereType.Town); // Teleport to the second closest town
			_isInProgress = false; // Flag so that siege instance can be started
			updatePlayerSiegeStateFlags(true);
			getZone().updateSiegeStatus();
			// The next siege date and the cleared registrations are the stored result of the siege
			WorldTransaction.run("Castle siege end", () -> {
				saveCastleSiege(); // Save castle specific data
				clearSiegeClan(); // Clear siege clan from db
			});
			removeControlTower(); // Remove all control tower from this castle
			deactivateZones(); // Control towers removed - bye to danger zone effects
			_siegeGuardManager.unspawnSiegeGuard(); // Remove all spawned siege guard from this castle
			getCastle().spawnDoor(); // Respawn door to castle
		}
	}
	
	private void removeDefender(L2SiegeClan sc)
	{
		if (sc != null)
			getDefenderClans().remove(sc);
	}
	
	private void removeAttacker(L2SiegeClan sc)
	{
		if (sc != null)
			getAttackerClans().remove(sc);
	}
	
	private void addDefender(L2SiegeClan sc, SiegeClanType type)
	{
		if (sc == null)
			return;
		sc.setType(type);
		getDefenderClans().add(sc);
	}
	
	private void addAttacker(L2SiegeClan sc)
	{
		if (sc == null)
			return;
		sc.setType(SiegeClanType.ATTACKER);
		getAttackerClans().add(sc);
	}
	
	/**
	 * When control of castle changed during siege<BR><BR>
	 */
	public void midVictory()
	{
		if (getIsInProgress()) // Siege still in progress
		{
			if (getCastle().getOwnerId() > 0)
				_siegeGuardManager.unspawnSiegeGuard();
			
			if (getDefenderClans().size() == 0 && // If defender doesn't exist (Pc vs Npc)
					getAttackerClans().size() == 1 // Only 1 attacker
			)
			{
				L2SiegeClan sc_newowner = getAttackerClan(getCastle().getOwnerId());
				removeAttacker(sc_newowner);
				addDefender(sc_newowner, SiegeClanType.OWNER);
				endSiege();
				return;
			}
			if (getCastle().getOwnerId() > 0)
			{
				int allyId = ClanTable.getInstance().getClan(getCastle().getOwnerId()).getAllyId();
				if (getDefenderClans().size() == 0) // If defender doesn't exist (Pc vs Npc)
				// and only an alliance attacks
				{
					// The player's clan is in an alliance
					if (allyId != 0)
					{
						boolean allinsamealliance = true;
						for (L2SiegeClan sc : getAttackerClans())
						{
							if (sc != null)
							{
								if (ClanTable.getInstance().getClan(sc.getClanId()).getAllyId() != allyId)
									allinsamealliance = false;
							}
						}
						if (allinsamealliance)
						{
							L2SiegeClan sc_newowner = getAttackerClan(getCastle().getOwnerId());
							removeAttacker(sc_newowner);
							addDefender(sc_newowner, SiegeClanType.OWNER);
							endSiege();
							return;
						}
					}
				}
				
				for (L2SiegeClan sc : getDefenderClans())
				{
					if (sc != null)
					{
						removeDefender(sc);
						addAttacker(sc);
					}
				}
				
				L2SiegeClan sc_newowner = getAttackerClan(getCastle().getOwnerId());
				removeAttacker(sc_newowner);
				addDefender(sc_newowner, SiegeClanType.OWNER);
				
				// The player's clan is in an alliance
				if (allyId != 0)
				{
					L2Clan[] clanList = ClanTable.getInstance().getClans();
					
					for (L2Clan clan : clanList)
					{
						if (clan.getAllyId() == allyId)
						{
							L2SiegeClan sc = getAttackerClan(clan.getClanId());
							if (sc != null)
							{
								removeAttacker(sc);
								addDefender(sc, SiegeClanType.DEFENDER);
							}
						}
					}
				}
				teleportPlayer(Siege.TeleportWhoType.Attacker, TeleportWhereType.SiegeFlag); // Teleport to the second closest town
				teleportPlayer(Siege.TeleportWhoType.Spectator, TeleportWhereType.Town); // Teleport to the second closest town
				
				removeDefenderFlags(); // Removes defenders' flags
				getCastle().removeUpgrade(); // Remove all castle upgrade
				getCastle().spawnDoor(true); // Respawn door to castle but make them weaker (50% hp)
				removeControlTower(); // Remove all control tower from this castle
				_controlTowerCount = 0;//Each new siege midvictory CT are completely respawned.
				_controlTowerMaxCount = 0;
				spawnControlTower(getCastle().getCastleId());
				updatePlayerSiegeStateFlags(false);
				
				announceToParticipants(SystemMessageId.TEMPORARY_ALLIANCE.getSystemMessage());
			}
		}
	}
	
	/**
	 * When siege starts<BR><BR>
	 */
	public void startSiege()
	{
		if (!getIsInProgress())
		{
			if (getAttackerClans().size() <= 0)
			{
				SystemMessage sm;
				if (getCastle().getOwnerId() <= 0)
					sm = new SystemMessage(SystemMessageId.SIEGE_OF_S1_HAS_BEEN_CANCELED_DUE_TO_LACK_OF_INTEREST);
				else
					sm = new SystemMessage(SystemMessageId.S1_SIEGE_WAS_CANCELED_BECAUSE_NO_CLANS_PARTICIPATED);
				sm.addString(getCastle().getName());
				Announcements.getInstance().announceToAll(sm);
				saveCastleSiege();
				return;
			}
			
			_isInProgress = true; // Flag so that same siege instance cannot be started again
			loadSiegeClan(); // Load siege clan from db
			updatePlayerSiegeStateFlags(false);
			teleportPlayer(Siege.TeleportWhoType.Attacker, TeleportWhereType.Town); // Teleport to the closest town
			//teleportPlayer(Siege.TeleportWhoType.Spectator, MapRegionTable.TeleportWhereType.Town);	  // Teleport to the second closest town
			_controlTowerCount = 0;
			_controlTowerMaxCount = 0;
			spawnControlTower(getCastle().getCastleId()); // Spawn control tower
			getCastle().spawnDoor(); // Spawn door
			spawnSiegeGuard(); // Spawn siege guard
			getZone().updateSiegeStatus();
			
			// Schedule a task to prepare auto siege end
			_siegeEndDate = Calendar.getInstance();
			_siegeEndDate.add(Calendar.MINUTE, Config.SIEGE_LENGTH_MINUTES);
			_endSiegeTask.schedule(1000);
			
			announceToPlayer(SystemMessageId.CASTLE_SIEGE_HAS_BEGUN.getSystemMessage(), true);
			SystemMessage sm;
			sm = new SystemMessage(SystemMessageId.SIEGE_OF_S1_HAS_STARTED);
			sm.addString(getCastle().getName());
			announceToPlayer(sm, false);
			sm = new SystemMessage(SystemMessageId.PARTICIPATING_IN_SIEGE_OF_S1);
			sm.addString(getCastle().getName());
			announceToParticipants(sm);
			oldOwner = getCastle().getOwnerId();
		}
	}
	
	// =========================================================
	// Method - Public
	/**
	 * Announce to player.<BR><BR>
	 * @param message The String of the message to send to player
	 * @param inAreaOnly The boolean flag to show message to players in area only.
	 */
	public void announceToPlayer(String message, boolean inAreaOnly)
	{
		// Get all players
		for (L2Player player : L2World.getInstance().getAllPlayers())
		{
			if (!inAreaOnly || (inAreaOnly && checkIfInZone(player.getX(), player.getY(), player.getZ())))
				player.sendMessage(message);
		}
	}
	
	public void announceToPlayer(SystemMessage sm, boolean inAreaOnly)
	{
		for (L2Player player : L2World.getInstance().getAllPlayers())
		{
			if (!inAreaOnly || (inAreaOnly && checkIfInZone(player.getX(), player.getY(), player.getZ())))
				player.sendPacket(sm);
		}
	}
	
	public void announceToParticipants(SystemMessage sm)
	{
		for (L2SiegeClan siegeclan : getAttackerClans())
			ClanTable.getInstance().getClan(siegeclan.getClanId()).broadcastToOnlineMembers(sm);
		for (L2SiegeClan siegeclan : getDefenderClans())
			ClanTable.getInstance().getClan(siegeclan.getClanId()).broadcastToOnlineMembers(sm);
	}
	
	public void announceToOpponent(SystemMessage sm, boolean toAtk)
	{
		Set<L2SiegeClan> clans = (toAtk ? getAttackerClans() : getDefenderClans());
		for (L2SiegeClan siegeclan : clans)
			ClanTable.getInstance().getClan(siegeclan.getClanId()).broadcastToOnlineMembers(sm);
	}
	
	public void announceToOpponent(SystemMessage sm, L2Clan self)
	{
		if (self != null)
		{
			boolean atk = true;
			if (getAttackerClan(self) != null)
				atk = false;
			else if (getDefenderClan(self) == null)
				return;
			announceToOpponent(sm, atk);
		}
	}
	
	public void updatePlayerSiegeStateFlags(boolean clear)
	{
		L2Clan clan;
		for (L2SiegeClan siegeclan : getAttackerClans())
		{
			if (siegeclan == null)
				continue;
			clan = ClanTable.getInstance().getClan(siegeclan.getClanId());
			for (L2Player member : clan.getOnlineMembers(0))
			{
				if (clear)
					member.setSiegeState((byte)0);
				else
					member.setSiegeState((byte)1);
				member.revalidateZone(true);
				member.broadcastUserInfo();
			}
		}
		for (L2SiegeClan siegeclan : getDefenderClans())
		{
			if (siegeclan == null)
				continue;
			clan = ClanTable.getInstance().getClan(siegeclan.getClanId());
			for (L2Player member : clan.getOnlineMembers(0))
			{
				if (clear)
					member.setSiegeState((byte)0);
				else
					member.setSiegeState((byte)2);
				member.revalidateZone(true);
				member.broadcastUserInfo();
			}
		}
	}
	
	/**
	 * Approve clan as defender for siege<BR><BR>
	 * @param clanId The int of player's clan id
	 */
	public void approveSiegeDefenderClan(int clanId)
	{
		if (clanId <= 0)
			return;
		saveSiegeClan(ClanTable.getInstance().getClan(clanId), 0, true);
		loadSiegeClan();
	}
	
	/** Return true if object is inside the zone */
	public boolean checkIfInZone(L2Object object)
	{
		return checkIfInZone(object.getX(), object.getY(), object.getZ());
	}
	
	/** Return true if object is inside the zone */
	public boolean checkIfInZone(int x, int y, int z)
	{
		Town town = TownManager.getInstance().getTown(x, y, z);
		return (getIsInProgress() && (getCastle().checkIfInZone(x, y, z) || getZone().isInsideZone(x, y) || (town != null && getCastle()
				.getCastleId() == town.getCastleId())));
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
		return (getDefenderClan(clan) != null);
	}
	
	/**
	 * Return true if clan is defender waiting approval<BR><BR>
	 * @param clan The L2Clan of the player
	 */
	public boolean checkIsDefenderWaiting(L2Clan clan)
	{
		return (getDefenderWaitingClan(clan) != null);
	}
	
	/** Clear all registered siege clans from database for castle */
	public void clearSiegeClan()
	{
		Connection con = null;
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection(con);
			PreparedStatement statement = con.prepareStatement("DELETE FROM castle_siege_clan WHERE castle_id=?");
			statement.setInt(1, getCastle().getCastleId());
			statement.execute();
			statement.close();
			
			if (getCastle().getOwnerId() > 0)
			{
				// The owner clan leaves every registration, also those for clan hall sieges
				PreparedStatement statement2 = con.prepareStatement("DELETE FROM castle_siege_clan WHERE clan_id=?");
				statement2.setInt(1, getCastle().getOwnerId());
				statement2.execute();
				statement2.close();

				statement2 = con.prepareStatement("DELETE FROM clan_hall_siege_clan WHERE clan_id=?");
				statement2.setInt(1, getCastle().getOwnerId());
				statement2.execute();
				statement2.close();
			}
			
			getAttackerClans().clear();
			getDefenderClans().clear();
			getDefenderWaitingClans().clear();
		}
		catch (Exception e)
		{
			_log.error("Exception: clearSiegeClan(): " + e.getMessage(), e);
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
	}
	
	/** Clear all siege clans waiting for approval from database for castle */
	public void clearSiegeWaitingClan()
	{
		Connection con = null;
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection(con);
			PreparedStatement statement =
					con.prepareStatement("DELETE FROM castle_siege_clan WHERE castle_id=? AND siege_role = 'DEFENDER_PENDING'");
			statement.setInt(1, getCastle().getCastleId());
			statement.execute();
			statement.close();
			
			getDefenderWaitingClans().clear();
		}
		catch (Exception e)
		{
			_log.error("Exception: clearSiegeWaitingClan(): " + e.getMessage(), e);
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
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
	
	/** Return list of L2Player registered as defender but not owner in the zone. */
	public List<L2Player> getDefendersButNotOwnersInZone()
	{
		List<L2Player> players = new ArrayList<L2Player>();
		L2Clan clan;
		for (L2SiegeClan siegeclan : getDefenderClans())
		{
			clan = ClanTable.getInstance().getClan(siegeclan.getClanId());
			if (clan.getClanId() == getCastle().getOwnerId())
				continue;
			for (L2Player player : clan.getOnlineMembers(0))
			{
				if (checkIfInZone(player.getX(), player.getY(), player.getZ()))
					players.add(player);
			}
		}
		return players;
	}
	
	/** Return list of L2Player in the zone. */
	public List<L2Player> getPlayersInZone()
	{
		List<L2Player> players = new ArrayList<L2Player>();
		
		for (L2Player player : L2World.getInstance().getAllPlayers())
		{
			// quick check from player states, which don't include siege number however
			if (!player.isInsideZone(L2Zone.FLAG_SIEGE))
				continue;
			if (checkIfInZone(player.getX(), player.getY(), player.getZ()))
				players.add(player);
		}
		
		return players;
	}
	
	/** Return list of L2Player owning the castle in the zone. */
	public List<L2Player> getOwnersInZone()
	{
		List<L2Player> players = new ArrayList<L2Player>();
		L2Clan clan;
		for (L2SiegeClan siegeclan : getDefenderClans())
		{
			clan = ClanTable.getInstance().getClan(siegeclan.getClanId());
			if (clan.getClanId() != getCastle().getOwnerId())
				continue;
			for (L2Player player : clan.getOnlineMembers(0))
			{
				if (checkIfInZone(player.getX(), player.getY(), player.getZ()))
					players.add(player);
			}
		}
		return players;
	}
	
	/** Return list of L2Player not registered as attacker or defender in the zone. */
	public List<L2Player> getSpectatorsInZone()
	{
		List<L2Player> players = new ArrayList<L2Player>();
		
		for (L2Player player : L2World.getInstance().getAllPlayers())
		{
			// quick check from player states, which don't include siege number however
			if (!player.isInsideZone(L2Zone.FLAG_SIEGE) || player.getSiegeState() != 0)
				continue;
			if (checkIfInZone(player.getX(), player.getY(), player.getZ()))
				players.add(player);
		}
		
		return players;
	}
	
	/**
	 * Control Tower was killed
	 * @param ct
	 */
	public void killedCT(L2Npc ct)
	{
		_controlTowerCount--;
		if (_controlTowerCount < 0)
			_controlTowerCount = 0;
	}
	
	public void killedFlameCT(boolean side)
	{
		deactivateZones(side);
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
	
	/** Display list of registered clans */
	public void listRegisterClan(L2Player player)
	{
		player.sendPacket(new SiegeInfo(getCastle()));
	}
	
	/**
	 * Register clan as attacker<BR><BR>
	 * @param player The L2Player of the player trying to register
	 */
	public void registerAttacker(L2Player player)
	{
		registerAttacker(player, false);
	}
	
	public void registerAttacker(L2Player player, boolean force)
	{
		if (!force)
		{
			int allyId = 0;
			if (getCastle().getOwnerId() != 0)
				allyId = ClanTable.getInstance().getClan(getCastle().getOwnerId()).getAllyId();
			
			if (allyId != 0 && player.getClan().getAllyId() == allyId)
			{
				player.sendPacket(SystemMessageId.CANNOT_ATTACK_ALLIANCE_CASTLE);
				return;
			}
		}
		
		if ((force && player.getClan() != null) || checkIfCanRegister(player, 1))
			saveSiegeClan(player.getClan(), 1, false); // Save to database
	}
	
	/**
	 * Register clan as defender<BR><BR>
	 * @param player The L2Player of the player trying to register
	 */
	public void registerDefender(L2Player player)
	{
		registerDefender(player, false);
	}
	
	public void registerDefender(L2Player player, boolean force)
	{
		if (!force)
		{
			if (getCastle().getOwnerId() <= 0)
			{
				player.sendPacket(SystemMessageId.DEFENDER_SIDE_FULL);
				return;
			}
			if (SevenSigns.getInstance().getSealOwner(SevenSigns.SEAL_STRIFE) == SevenSigns.CABAL_DUSK)
			{
				//strife restricts defenders to only clan's alliance
				int allyId = 0;
				if (getCastle().getOwnerId() != 0)
					allyId = ClanTable.getInstance().getClan(getCastle().getOwnerId()).getAllyId();
				if (allyId != 0 && player.getClan().getAllyId() != allyId)
				{
					player.sendPacket(SystemMessageId.DEFENDER_SIDE_FULL);
					return;
				}
			}
		}
		
		if ((force && player.getClan() != null) || checkIfCanRegister(player, 2))
			saveSiegeClan(player.getClan(), 2, false); // Save to database
	}
	
	/**
	 * Remove clan from siege<BR><BR>
	 * @param clanId The int of player's clan id
	 */
	public void removeSiegeClan(int clanId)
	{
		if (clanId <= 0)
			return;
		
		Connection con = null;
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection(con);
			PreparedStatement statement =
					con.prepareStatement("DELETE FROM castle_siege_clan WHERE castle_id=? AND clan_id=?");
			statement.setInt(1, getCastle().getCastleId());
			statement.setInt(2, clanId);
			statement.execute();
			statement.close();
			
			loadSiegeClan();
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
	 * @param player The L2Player of player/clan being removed
	 */
	public void removeSiegeClan(L2Clan clan)
	{
		if (clan == null || clan.getHasCastle() == getCastle().getCastleId()
				|| !SiegeManager.getInstance().checkIsRegistered(clan, getCastle().getCastleId()))
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
	 * Start the auto tasks<BR><BR>
	 */
	public void startAutoTask()
	{
		correctSiegeDateTime();
		
		_log.info("Siege of " + getCastle().getName() + ": " + getCastle().getSiegeDate().getTime());
		
		loadSiegeClan();
		
		// Schedule siege auto start
		_startSiegeTask.schedule(1000);
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
			case DefenderNotOwner:
				players = getDefendersButNotOwnersInZone();
				break;
			case Spectator:
				players = getSpectatorsInZone();
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
	 * Add clan as defender<BR><BR>
	 * @param clanId The int of clan's id
	 */
	private void addDefender(int clanId)
	{
		getDefenderClans().add(new L2SiegeClan(clanId, SiegeClanType.DEFENDER)); // Add registered defender to defender list
	}
	
	/**
	 * <p>Add clan as defender with the specified type</p>
	 * @param clanId The int of clan's id
	 * @param type the type of the clan
	 */
	private void addDefender(int clanId, SiegeClanType type)
	{
		getDefenderClans().add(new L2SiegeClan(clanId, type));
	}
	
	/**
	 * Add clan as defender waiting approval<BR><BR>
	 * @param clanId The int of clan's id
	 */
	private void addDefenderWaiting(int clanId)
	{
		getDefenderWaitingClans().add(new L2SiegeClan(clanId, SiegeClanType.DEFENDER_PENDING)); // Add registered defender to defender list
	}
	
	/**
	 * Return true if the player can register.<BR><BR>
	 * @param player The L2Player of the player trying to register
	 */
	private boolean checkIfCanRegister(L2Player player, int typeId)
	{
		L2Clan clan = player.getClan();
		if (clan == null || clan.getLevel() < Config.SIEGE_CLAN_MIN_LEVEL)
		{
			if (Config.SIEGE_CLAN_MIN_LEVEL == 4) //default retail
				player.sendPacket(SystemMessageId.ONLY_CLAN_LEVEL_5_ABOVE_MAY_SIEGE);
			else
				player.sendMessage("Only clans with Level " + Config.SIEGE_CLAN_MIN_LEVEL
						+ " and higher may register for a castle siege.");
			return false;
		}
		else if (getIsRegistrationOver())
		{
			player.sendPacket(new SystemMessage(SystemMessageId.DEADLINE_FOR_SIEGE_S1_PASSED).addString(getCastle()
					.getName()));
			return false;
		}
		else if (getIsInProgress())
		{
			player.sendPacket(SystemMessageId.NOT_SIEGE_REGISTRATION_TIME2);
			return false;
		}
		else if (clan.getHasCastle() > 0)
		{
			player.sendPacket(SystemMessageId.CLAN_THAT_OWNS_CASTLE_CANNOT_PARTICIPATE_OTHER_SIEGE);
			return false;
		}
		else if (clan.getClanId() == getCastle().getOwnerId())
		{
			player.sendPacket(SystemMessageId.CLAN_THAT_OWNS_CASTLE_IS_AUTOMATICALLY_REGISTERED_DEFENDING);
			return false;
		}
		else if (checkIfAlreadyRegisteredForSameDay(player.getClan()))
		{
			player.sendPacket(SystemMessageId.APPLICATION_DENIED_BECAUSE_ALREADY_SUBMITTED_A_REQUEST_FOR_ANOTHER_SIEGE_BATTLE);
			return false;
		}
		else
		{
			for (int i = 0; i < 10; i++)
			{
				if (SiegeManager.getInstance().checkIsRegistered(player.getClan(), i))
				{
					player.sendPacket(SystemMessageId.ALREADY_REQUESTED_SIEGE_BATTLE);
					return false;
				}
			}
		}
		
		if (typeId == 0 || typeId == 2 || typeId == -1)
		{
			if (getDefenderClans().size() + getDefenderWaitingClans().size() >= Config.SIEGE_MAX_DEFENDER)
			{
				player.sendPacket(SystemMessageId.DEFENDER_SIDE_FULL);
				return false;
			}
		}
		if (typeId == 1)
		{
			if (getAttackerClans().size() >= Config.SIEGE_MAX_ATTACKER)
			{
				player.sendPacket(SystemMessageId.ATTACKER_SIDE_FULL);
				return false;
			}
		}
		
		return true;
	}
	
	/**
	 * Return true if the clan has already registered to a siege for the same day.<BR><BR>
	 * @param clan The L2Clan of the player trying to register
	 */
	public boolean checkIfAlreadyRegisteredForSameDay(L2Clan clan)
	{
		for (Siege siege : SiegeManager.getInstance().getSieges())
		{
			if (siege == this)
				continue;
			if (siege.getSiegeDate().get(Calendar.DAY_OF_WEEK) == getSiegeDate().get(Calendar.DAY_OF_WEEK))
			{
				if (siege.checkIsAttacker(clan))
					return true;
				if (siege.checkIsDefender(clan))
					return true;
				if (siege.checkIsDefenderWaiting(clan))
					return true;
			}
		}
		return false;
	}
	
	/**
	 * Return the correct siege date as Calendar.<BR><BR>
	 * @param siegeDate The Calendar siege date and time
	 */
	public void correctSiegeDateTime()
	{
		boolean corrected = false;
		
		if (getCastle().getSiegeDate().getTimeInMillis() < System.currentTimeMillis())
		{
			// Since siege has past reschedule it to the next one
			// This is usually caused by server being down
			corrected = true;
			setNextSiegeDate();
		}
		
		if (!SevenSigns.getInstance().isDateInSealValidPeriod(getCastle().getSiegeDate()))
		{
			// no sieges in Quest period! reschedule it to the next SealValidationPeriod
			// This is usually caused by server being down
			corrected = true;
			setNextSiegeDate();
		}
		
		if (corrected)
			saveSiegeDate();
	}
	
	/** Load siege clans. */
	private void loadSiegeClan()
	{
		Connection con = null;
		try
		{
			getAttackerClans().clear();
			getDefenderClans().clear();
			getDefenderWaitingClans().clear();
			
			// Add castle owner as defender (add owner first so that they are on the top of the defender list)
			if (getCastle().getOwnerId() > 0)
				addDefender(getCastle().getOwnerId(), SiegeClanType.OWNER);
			
			PreparedStatement statement = null;
			ResultSet rs = null;
			
			con = L2DatabaseFactory.getInstance().getConnection(con);
			
			statement = con.prepareStatement("SELECT clan_id, siege_role FROM castle_siege_clan WHERE castle_id=?");
			statement.setInt(1, getCastle().getCastleId());
			rs = statement.executeQuery();
			
			String role;
			while (rs.next())
			{
				role = rs.getString("siege_role");
				if (role.equals(SiegeClanType.DEFENDER.name()))
					addDefender(rs.getInt("clan_id"));
				else if (role.equals(SiegeClanType.ATTACKER.name()))
					addAttacker(rs.getInt("clan_id"));
				else if (role.equals(SiegeClanType.DEFENDER_PENDING.name()))
					addDefenderWaiting(rs.getInt("clan_id"));
			}
			
			statement.close();
		}
		catch (Exception e)
		{
			_log.error("Exception: loadSiegeClan(): " + e.getMessage(), e);
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
	}
	
	/** Remove all control tower spawned. */
	private void removeControlTower()
	{
		// Remove all instance of control tower for this castle
		for (L2ControlTowerInstance ct : _controlTowers)
			if (ct != null)
				ct.decayMe();
		for (L2FlameControlTowerInstance fct : _flameTowers)
			if (fct != null)
				fct.decayMe();
		
		_controlTowers.clear();
		_flameTowers.clear();
	}
	
	/** Remove all flags. */
	private void removeFlags()
	{
		for (L2SiegeClan sc : getAttackerClans())
		{
			if (sc != null)
				sc.removeFlags();
		}
		for (L2SiegeClan sc : getDefenderClans())
		{
			if (sc != null)
				sc.removeFlags();
		}
	}
	
	/** Remove flags from defenders. */
	private void removeDefenderFlags()
	{
		for (L2SiegeClan sc : getDefenderClans())
		{
			if (sc != null)
				sc.removeFlags();
		}
	}
	
	/** Save castle siege related to database. */
	private void saveCastleSiege()
	{
		setNextSiegeDate(); // Set the next set date for 2 weeks from now
		// Schedule Time registration end
		getTimeRegistrationOverDate().setTimeInMillis(System.currentTimeMillis());
		getTimeRegistrationOverDate().add(Calendar.DAY_OF_MONTH, 1);
		getCastle().setIsTimeRegistrationOver(false);
		
		saveSiegeDate(); // Save the new date
		startAutoTask(); // Prepare auto start siege and end registration
	}
	
	/** Epoch milliseconds as a database moment; 0 or less means "not set" and is NULL. */
	private static Timestamp moment(long millis)
	{
		return millis > 0 ? new Timestamp(millis) : null;
	}

	/**
	 * @param typeId 0 = defender, 1 = attacker, 2 = defender waiting
	 * @return the stored siege role of that registration type
	 */
	private static String siegeRole(int typeId)
	{
		switch (typeId)
		{
			case 0:
				return SiegeClanType.DEFENDER.name();
			case 1:
				return SiegeClanType.ATTACKER.name();
			case 2:
				return SiegeClanType.DEFENDER_PENDING.name();
			default:
				throw new IllegalArgumentException("Unknown siege registration type " + typeId);
		}
	}

	/** Save siege date to database. */
	public void saveSiegeDate()
	{
		if (_startSiegeTask.isScheduled())
			_startSiegeTask.schedule(1000);
		
		Connection con = null;
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection(con);
			PreparedStatement statement =
					con.prepareStatement("UPDATE castle SET siege_at = ?, registration_end_at = ?, is_registration_over = ? WHERE id = ?");
			statement.setTimestamp(1, moment(getSiegeDate().getTimeInMillis()));
			statement.setTimestamp(2, moment(getTimeRegistrationOverDate().getTimeInMillis()));
			statement.setBoolean(3, getIsTimeRegistrationOver());
			statement.setInt(4, getCastle().getCastleId());
			statement.execute();
			
			statement.close();
		}
		catch (Exception e)
		{
			_log.error("Exception: saveSiegeDate(): " + e.getMessage(), e);
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
	private void saveSiegeClan(L2Clan clan, int typeId, boolean isUpdateRegistration)
	{
		if (clan.getHasCastle() > 0)
			return;
		
		Connection con = null;
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection(con);
			PreparedStatement statement;
			if (!isUpdateRegistration)
			{
				statement =
						con.prepareStatement("INSERT INTO castle_siege_clan (clan_id, castle_id, siege_role) VALUES (?,?,?) "
								+ "ON CONFLICT (castle_id, clan_id) DO UPDATE SET siege_role = EXCLUDED.siege_role");
				statement.setInt(1, clan.getClanId());
				statement.setInt(2, getCastle().getCastleId());
				statement.setString(3, siegeRole(typeId));
				statement.execute();
				statement.close();
			}
			else
			{
				statement = con.prepareStatement("UPDATE castle_siege_clan SET siege_role = ? WHERE castle_id = ? AND clan_id = ?");
				statement.setString(1, siegeRole(typeId));
				statement.setInt(2, getCastle().getCastleId());
				statement.setInt(3, clan.getClanId());
				statement.execute();
				statement.close();
			}
			
			if (typeId == 0 || typeId == -1)
			{
				addDefender(clan.getClanId());
				announceToPlayer(clan.getName() + " has been registered to defend " + getCastle().getName(), false);
			}
			else if (typeId == 1)
			{
				addAttacker(clan.getClanId());
				announceToPlayer(clan.getName() + " has been registered to attack " + getCastle().getName(), false);
			}
			else if (typeId == 2)
			{
				addDefenderWaiting(clan.getClanId());
				announceToPlayer(clan.getName() + " has requested to defend " + getCastle().getName(), false);
			}
		}
		catch (Exception e)
		{
			_log.error(
					"Exception: saveSiegeClan(L2Clan clan, int typeId, boolean isUpdateRegistration): "
							+ e.getMessage(), e);
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
	}
	
	/** Set the date for the next siege. */
	private void setNextSiegeDate()
	{
		while (getCastle().getSiegeDate().getTimeInMillis() < System.currentTimeMillis())
		{
			if (getCastle().getSiegeDate().get(Calendar.DAY_OF_WEEK) != Calendar.SATURDAY
					&& getCastle().getSiegeDate().get(Calendar.DAY_OF_WEEK) != Calendar.SUNDAY)
				getCastle().getSiegeDate().set(Calendar.DAY_OF_WEEK, Calendar.SATURDAY);
			
			// from CT2.3 Castle sieges are on Sunday, but if server admins allow to set day of the siege
			// than sieges can occur on Saturdays as well
			if (getCastle().getSiegeDate().get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY
					&& !Config.CL_SET_SIEGE_TIME_LIST.contains("day"))
				getCastle().getSiegeDate().set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY);
			
			// set the next siege day to the next weekend
			getCastle().getSiegeDate().add(Calendar.DAY_OF_MONTH, 7);
		}
		
		if (!SevenSigns.getInstance().isDateInSealValidPeriod(getCastle().getSiegeDate()))
			getCastle().getSiegeDate().add(Calendar.DAY_OF_MONTH, 7);
		
		_isRegistrationOver = false; // Allow registration for next siege
	}
	
	/** Spawn control tower. */
	private void spawnControlTower(int Id)
	{
		SiegeSpawn ss = SiegeManager.getInstance().getFlameControlTowerSpawn(Id, true);
		L2NpcTemplate template = NpcTable.getInstance().getTemplate(ss.getNpcId());
		template.setBaseHpMax(ss.getHp());
		L2FlameControlTowerInstance ft =
				new L2FlameControlTowerInstance(IdFactory.getInstance().getNextId(), template, true);
		ft.getStatus().setCurrentHpMp(ss.getHp(), ft.getMaxMp());
		ft.spawnMe(ss.getLocation().getX(), ss.getLocation().getY(), ss.getLocation().getZ() + 20);
		_flameTowers.add(ft);
		ss = SiegeManager.getInstance().getFlameControlTowerSpawn(Id, false);
		template = NpcTable.getInstance().getTemplate(ss.getNpcId());
		template.setBaseHpMax(ss.getHp());
		ft = new L2FlameControlTowerInstance(IdFactory.getInstance().getNextId(), template, false);
		ft.getStatus().setCurrentHpMp(ss.getHp(), ft.getMaxMp());
		ft.spawnMe(ss.getLocation().getX(), ss.getLocation().getY(), ss.getLocation().getZ() + 20);
		_flameTowers.add(ft);
		
		for (SiegeSpawn _sp : SiegeManager.getInstance().getControlTowerSpawnList(Id))
		{
			template = NpcTable.getInstance().getTemplate(_sp.getNpcId());
			template.setBaseHpMax(_sp.getHp());
			
			L2ControlTowerInstance ct = new L2ControlTowerInstance(IdFactory.getInstance().getNextId(), template);
			ct.getStatus().setCurrentHpMp(_sp.getHp(), ct.getMaxMp());
			ct.spawnMe(_sp.getLocation().getX(), _sp.getLocation().getY(), _sp.getLocation().getZ() + 20);
			
			_controlTowerCount++;
			_controlTowerMaxCount++;
			_controlTowers.add(ct);
		}
	}
	
	/**
	 * Spawn siege guard.<BR><BR>
	 */
	private void spawnSiegeGuard()
	{
		getSiegeGuardManager().spawnSiegeGuard();
		
		// Register guard to the closest Control Tower
		// When CT dies, so do all the guards that it controls
		if (!getSiegeGuardManager().getSiegeGuardSpawn().isEmpty() && !_controlTowers.isEmpty())
		{
			L2ControlTowerInstance closestCt;
			double distance, x, y, z;
			double distanceClosest = 0;
			for (L2Spawn spawn : getSiegeGuardManager().getSiegeGuardSpawn())
			{
				if (spawn == null)
					continue;
				closestCt = null;
				distanceClosest = 0;
				for (L2ControlTowerInstance ct : _controlTowers)
				{
					if (ct == null)
						continue;
					x = (spawn.getLocx() - ct.getX());
					y = (spawn.getLocy() - ct.getY());
					z = (spawn.getLocz() - ct.getZ());
					
					distance = (x * x) + (y * y) + (z * z);
					
					if (closestCt == null || distance < distanceClosest)
					{
						closestCt = ct;
						distanceClosest = distance;
					}
				}
				
				if (closestCt != null)
					closestCt.registerGuard(spawn);
			}
		}
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
	
	public final Set<L2SiegeClan> getAttackerClans()
	{
		return _attackerClans;
	}
	
	public final int getAttackerRespawnDelay()
	{
		return (Config.SIEGE_RESPAWN_DELAY_ATTACKER);
	}
	
	public final Castle getCastle()
	{
		return _castle;
	}
	
	@Override
	public final L2SiegeClan getDefenderClan(L2Clan clan)
	{
		if (clan == null)
			return null;
		return getDefenderClan(clan.getClanId());
	}
	
	public final L2SiegeClan getDefenderClan(int clanId)
	{
		for (L2SiegeClan sc : getDefenderClans())
			if (sc != null && sc.getClanId() == clanId)
				return sc;
		return null;
	}
	
	public final Set<L2SiegeClan> getDefenderClans()
	{
		return _defenderClans;
	}
	
	public final L2SiegeClan getDefenderWaitingClan(L2Clan clan)
	{
		if (clan == null)
			return null;
		return getDefenderWaitingClan(clan.getClanId());
	}
	
	public final L2SiegeClan getDefenderWaitingClan(int clanId)
	{
		for (L2SiegeClan sc : getDefenderWaitingClans())
			if (sc != null && sc.getClanId() == clanId)
				return sc;
		return null;
	}
	
	public final Set<L2SiegeClan> getDefenderWaitingClans()
	{
		return _defenderWaitingClans;
	}
	
	@Override
	public final boolean getIsInProgress()
	{
		return _isInProgress;
	}
	
	public final boolean getIsRegistrationOver()
	{
		return _isRegistrationOver;
	}
	
	public final boolean getIsTimeRegistrationOver()
	{
		return getCastle().getIsTimeRegistrationOver();
	}
	
	public final Calendar getSiegeDate()
	{
		return getCastle().getSiegeDate();
	}
	
	public final Calendar getTimeRegistrationOverDate()
	{
		return getCastle().getTimeRegistrationOverDate();
	}
	
	public void endTimeRegistration(boolean automatic)
	{
		getCastle().setIsTimeRegistrationOver(true);
		if (!automatic)
		{
			saveSiegeDate();
			Broadcast.toAllOnlinePlayers(new SystemMessage(SystemMessageId.S1_ANNOUNCED_SIEGE_TIME)
					.addString(getCastle().getName()));
		}
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
	
	public final SiegeGuardManager getSiegeGuardManager()
	{
		return _siegeGuardManager;
	}
	
	public final L2SiegeZone getZone()
	{
		return getCastle().getBattlefield();
	}
	
	public int getControlTowerCount()
	{
		return _controlTowerCount;
	}
	
	/**
	 * Returns all zones in the given castle side
	 * @param side Inner/Eastern = true, Outer/Western = false
	 * @return Danger Zone Array
	 */
	public L2SiegeDangerZone[] getDangerZones(boolean side)
	{
		if (side)
			return _dangerZonesE;
		else
			return _dangerZonesW;
	}
	
	/**
	 * <B>Adds the given zone to the managed zone array.</B><BR><BR>
	 * Called when loading zones.<BR>
	 * <U>The first zone registered will <I>always</I> be at the 0th cell.</U>
	 * @param sdz Siege danger zone
	 * @param east Side of the castle
	 */
	public void registerZone(L2SiegeDangerZone sdz, boolean east)
	{
		if (east)
		{
			if (_dangerZonesE == null)
				_dangerZonesE = new L2SiegeDangerZone[] { sdz };
			else
				_dangerZonesE = ArrayUtils.add(_dangerZonesE, sdz);
		}
		else
		{
			if (_dangerZonesW == null)
				_dangerZonesW = new L2SiegeDangerZone[] { sdz };
			else
				_dangerZonesW = ArrayUtils.add(_dangerZonesW, sdz);
		}
	}
	
	/**
	 * @param east Side of the castle
	 * @return How many danger zones are activated
	 */
	public int getZoneAreaLevel(boolean east)
	{
		L2SiegeDangerZone[] list = getDangerZones(east);
		if (list == null) //no zones registered
			return 0;
		else if (_castle.getCastleId() == 5)
		{
			int level = 0;
			for (L2SiegeDangerZone finalElement : list)
				if (finalElement.isActive())
					level++;
			return level;
		}
		else
			return (list[0].isActive() ? 1 : 0);
	}
	
	/**
	 * Enforces only (area level)/(list.length) zones to be active.
	 * @param east Which side of the castle?
	 * @param level Area level
	 */
	public void activateOnLoad(boolean east, int level)
	{
		L2SiegeDangerZone[] list = getDangerZones(east);
		if (list != null && level <= list.length) //if enough zones are loaded
			activateZones(east, 0, level);
	}
	
	/**
	 * Activates siege danger zones.
	 * @param east Which side of the castle?
	 * @param oldLevel First zone activated in the array
	 * @param newLevel Last zone activated in the array
	 */
	public void activateZones(boolean east, int oldLevel, int newLevel)
	{
		L2SiegeDangerZone[] list = getDangerZones(east);
		if (list != null)
			for (int i = oldLevel; i < newLevel; i++)
				list[i].activate();
	}
	
	private void deactivateZones(boolean side)
	{
		L2SiegeDangerZone[] list;
		if ((list = getDangerZones(side)) != null)
			for (L2SiegeDangerZone sdz : list)
				sdz.deactivate();
	}
	
	/** Downgrades all managed zones and deletes their upgrade data from the database */
	public void deactivateZones()
	{
		deactivateZones(true);
		deactivateZones(false);
		_castle.resetDangerZones();
	}
	
	/** Clears the managed zone arrays */
	public void onZoneReload()
	{
		_dangerZonesE = null;
		_dangerZonesW = null;
	}
}
