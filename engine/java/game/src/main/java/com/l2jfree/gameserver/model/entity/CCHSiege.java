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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.l2jfree.Config;
import com.l2jfree.L2DatabaseFactory;
import com.l2jfree.gameserver.datatables.ClanTable;
import com.l2jfree.gameserver.gameobjects.L2Npc;
import com.l2jfree.gameserver.gameobjects.L2Object;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.instancemanager.CCHManager;
import com.l2jfree.gameserver.instancemanager.ClanHallManager;
import com.l2jfree.gameserver.instancemanager.ContestableHideoutGuardManager;
import com.l2jfree.gameserver.instancemanager.SiegeManager;
import com.l2jfree.gameserver.model.clan.L2Clan;
import com.l2jfree.gameserver.model.clan.L2SiegeClan;
import com.l2jfree.gameserver.model.clan.L2SiegeClan.SiegeClanType;
import com.l2jfree.gameserver.model.entity.Siege.TeleportWhoType;
import com.l2jfree.gameserver.model.mapregion.TeleportWhereType;
import com.l2jfree.gameserver.model.world.L2World;
import com.l2jfree.gameserver.model.zone.L2SiegeZone;
import com.l2jfree.gameserver.model.zone.L2Zone;
import com.l2jfree.gameserver.network.SystemMessageId;
import com.l2jfree.gameserver.network.packets.server.SiegeInfo;
import com.l2jfree.gameserver.network.packets.server.SystemMessage;
import com.l2jfree.gameserver.persistence.WorldTransaction;
import com.l2jfree.gameserver.threadmanager.ExclusiveTask;
import com.l2jfree.gameserver.util.Broadcast;
import com.l2jfree.util.L2FastSet;

/**
 * Clan hall siege, without unnecessary clutter.
 * @author Savormix
 */
public final class CCHSiege extends AbstractSiege
{
	private final static Logger _log = LoggerFactory.getLogger(CCHSiege.class);
	
	private final ClanHall _hideout;
	private final ContestableHideoutGuardManager _guardManager;
	private final Set<L2SiegeClan> _attackerClans;
	
	private boolean _isInProgress;
	private boolean _isRegistrationOver;
	private Calendar _siegeEndDate;
	private int _oldOwner; // 0 - NPC, > 0 - clan
	
	public CCHSiege(ClanHall hideout)
	{
		_hideout = hideout;
		_guardManager = new ContestableHideoutGuardManager(hideout);
		_attackerClans = new L2FastSet<L2SiegeClan>().setShared(true);
		_isInProgress = false;
		_oldOwner = hideout.getOwnerId();
		startAutoTask();
	}
	
	public final ContestableHideoutGuardManager getGuardManager()
	{
		return _guardManager;
	}
	
	public void announceToPlayer(String message, boolean inAreaOnly)
	{
		// Get all players
		for (L2Player player : L2World.getInstance().getAllPlayers())
			if (!inAreaOnly || (inAreaOnly && checkIfInZone(player.getX(), player.getY(), player.getZ())))
				player.sendMessage(message);
	}
	
	public void announceToPlayer(SystemMessage sm, boolean inAreaOnly)
	{
		for (L2Player player : L2World.getInstance().getAllPlayers())
			if (!inAreaOnly || (inAreaOnly && checkIfInZone(player.getX(), player.getY(), player.getZ())))
				player.sendPacket(sm);
	}
	
	public void announceToParticipants(SystemMessage sm)
	{
		for (L2SiegeClan siegeclan : _attackerClans)
			ClanTable.getInstance().getClan(siegeclan.getClanId()).broadcastToOnlineMembers(sm);
	}
	
	private int calculateRepChange(L2Clan winner, L2Clan loser)
	{
		int result = 300;
		if (!Config.ALT_CCH_REPUTATION || winner == null || loser == null)
			return result;
		// absolutely custom
		if (winner.getReputationScore() < loser.getReputationScore())
			result = (int) (result * 1.2);
		else
			result = (int) (result * 0.8);
		if (winner.getLevel() < loser.getLevel())
			result = (int) (result * 1.4);
		else
			result = (int) (result * 0.75);
		if (winner.getOnlineMembersList().size() < loser.getOnlineMembersList().size())
			result = (int) (result * 1.05);
		else
			result = (int) (result * 0.9);
		return result;
	}
	
	public void startSiege()
	{
		if (!getIsInProgress())
		{
			if (_attackerClans.size() <= 0)
			{
				announceToPlayer(SystemMessageId.CLANHALL_WAR_CANCELLED.getSystemMessage(), true);
				saveCastleSiege();
				return;
			}
			
			_isInProgress = true; // Flag so that same siege instance cannot be started again
			loadSiegeClan(); // Load siege clan from db
			updatePlayerSiegeStateFlags(false);
			teleportPlayer(Siege.TeleportWhoType.All, TeleportWhereType.Town);
			_hideout.spawnDoor(); // Spawn door
			_guardManager.spawnSiegeGuards(); // Spawn siege guard
			getZone().updateSiegeStatus();
			
			// Schedule a task to prepare auto siege end
			_siegeEndDate = Calendar.getInstance();
			_siegeEndDate.add(Calendar.MINUTE, 60);
			_endSiegeTask.schedule(1000);
			
			if (Config.USE_MISSING_CCH_MESSAGES)
				announceToPlayer(SystemMessageId.CLANHALL_SIEGE_BEGUN.getSystemMessage(), true);
			_oldOwner = _hideout.getOwnerId();
		}
	}
	
	public void endSiege(L2Clan bossKiller)
	{
		if (getIsInProgress())
		{
			if (Config.USE_MISSING_CCH_MESSAGES)
				announceToPlayer(SystemMessageId.CLANHALL_SIEGE_ENDED.getSystemMessage(), true);
			// The new owner (or none) and the reputation changes are stored together
			WorldTransaction.run("Clan hall siege result", () -> {
				if (bossKiller != null)
					ClanHallManager.getInstance().setOwner(_hideout.getId(), bossKiller);
				else
					ClanHallManager.getInstance().setFree(_hideout.getId());
				//_hideout.setOwner(bossKiller);
				SystemMessage sm;
			
				if (_oldOwner > 0)
				{
					if (_hideout.getOwnerId() <= 0)
					{
						L2Clan c = ClanTable.getInstance().getClan(_oldOwner);
						c.setReputationScore(c.getReputationScore() - calculateRepChange(null, c), true);
						c.broadcastToOnlineMembers(SystemMessageId.CLAN_LOST_CONTESTED_CLAN_HALL_AND_300_POINTS
								.getSystemMessage());
					}
					else if (_hideout.getOwnerId() != _oldOwner)
					{
						L2Clan old = ClanTable.getInstance().getClan(_oldOwner);
						L2Clan owner = _hideout.getOwnerClan();
						int pts = calculateRepChange(owner, old);
						old.setReputationScore(old.getReputationScore() - pts, true);
						sm =
								new SystemMessage(
										SystemMessageId.OPPOSING_CLAN_CAPTURED_CLAN_HALL_AND_YOUR_CLAN_LOSES_S1_POINTS);
						sm.addNumber(pts);
						old.broadcastToOnlineMembers(sm);
						owner.setReputationScore(owner.getReputationScore() + pts, true);
						sm =
								new SystemMessage(
										SystemMessageId.CLAN_CAPTURED_CONTESTED_CLAN_HALL_AND_S1_POINTS_DEDUCTED_FROM_OPPONENT);
						sm.addNumber(pts);
						owner.broadcastToOnlineMembers(sm);
					}
				}
				else if (_hideout.getOwnerId() > 0)
				{
					L2Clan c = _hideout.getOwnerClan();
					int pts = calculateRepChange(c, null);
					c.setReputationScore(c.getReputationScore() + pts, true);
					sm = new SystemMessage(SystemMessageId.CLAN_ACQUIRED_CONTESTED_CLAN_HALL_AND_S1_REPUTATION_POINTS);
					sm.addNumber(pts);
					c.broadcastToOnlineMembers(sm);
				}
			});
			
			removeFlags(); // Removes all flags. Note: Remove flag before teleporting players
			teleportPlayer(Siege.TeleportWhoType.Attacker, TeleportWhereType.Town); // Teleport to the second closest town
			teleportPlayer(Siege.TeleportWhoType.DefenderNotOwner, TeleportWhereType.Town); // Teleport to the second closest town
			teleportPlayer(Siege.TeleportWhoType.Spectator, TeleportWhereType.Town); // Teleport to the second closest town
			_isInProgress = false; // Flag so that siege instance can be started
			updatePlayerSiegeStateFlags(true);
			getZone().updateSiegeStatus();
			// The next siege date and the cleared registrations are the stored result of the siege
			WorldTransaction.run("Clan hall siege end", () -> {
				saveCastleSiege(); // Save castle specific data
				clearSiegeClan(); // Clear siege clan from db
			});
			loadSiegeClan();
			_guardManager.despawnSiegeGuards(); // Remove all spawned siege guard from this hall
			_hideout.spawnDoor(); // Respawn door to hideout
		}
	}
	
	public void updatePlayerSiegeStateFlags(boolean clear)
	{
		L2Clan clan;
		for (L2SiegeClan siegeclan : _attackerClans)
		{
			if (siegeclan == null)
				continue;
			clan = ClanTable.getInstance().getClan(siegeclan.getClanId());
			for (L2Player member : clan.getOnlineMembers(0))
			{
				if (clear)
					member.setSiegeState((byte)0);
				// TODO: not so sure about this part
				else if (_hideout.getOwnerId() == siegeclan.getClanId())
					member.setSiegeState((byte)2);
				else
					member.setSiegeState((byte)1);
				member.revalidateZone(true);
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
		return (getIsInProgress() && (_hideout.checkIfInZone(x, y, z) || getZone().isInsideZone(x, y)));
	}
	
	public void clearSiegeClan()
	{
		Connection con = null;
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection();
			PreparedStatement statement = con.prepareStatement("DELETE FROM clan_hall_siege_clan WHERE clan_hall_id=?");
			statement.setInt(1, _hideout.getId());
			statement.executeUpdate();
			statement.close();
			
			if (_hideout.getOwnerId() > 0)
			{
				statement = con.prepareStatement("DELETE FROM clan_hall_siege_clan WHERE clan_id=?");
				statement.setInt(1, _hideout.getOwnerId());
				statement.executeUpdate();
				statement.close();
			}
			
			_attackerClans.clear();
		}
		catch (Exception e)
		{
			_log.error("Failed clearing registered clan list!", e);
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
	}
	
	public List<L2Player> getAttackersInZone()
	{
		List<L2Player> players = new ArrayList<L2Player>();
		L2Clan clan;
		for (L2SiegeClan siegeclan : _attackerClans)
		{
			clan = ClanTable.getInstance().getClan(siegeclan.getClanId());
			for (L2Player player : clan.getOnlineMembers(0))
				if (checkIfInZone(player.getX(), player.getY(), player.getZ()))
					players.add(player);
		}
		return players;
	}
	
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
	
	public void killedFlag(L2Npc flag)
	{
		if (flag == null)
			return;
		for (L2SiegeClan clan : _attackerClans)
			if (clan.removeFlag(flag))
				return;
	}
	
	public void listRegisterClan(L2Player player)
	{
		player.sendPacket(new SiegeInfo(_hideout));
	}
	
	public void registerAttacker(L2Player player, boolean force)
	{
		if ((force && player.getClan() != null) || checkIfCanRegister(player))
			saveSiegeClan(player.getClan(), false); // Save to database
	}
	
	private void removeSiegeClan(int clanId)
	{
		if (clanId <= 0)
			return;
		
		Connection con = null;
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection(con);
			PreparedStatement statement =
					con.prepareStatement("DELETE FROM clan_hall_siege_clan WHERE clan_hall_id=? AND clan_id=?");
			statement.setInt(1, _hideout.getId());
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
		if (clan == null || clan.getHasHideout() == _hideout.getId()
				|| !CCHManager.getInstance().checkIsRegistered(clan, _hideout.getId()))
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
	
	public void startAutoTask()
	{
		correctSiegeDateTime();
		
		_log.info("Contest/Siege of " + _hideout.getName() + ": " + getSiegeDate().getTime());
		
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
			case Attacker:
				players = getAttackersInZone();
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
	
	private void addAttacker(int clanId)
	{
		_attackerClans.add(new L2SiegeClan(clanId, SiegeClanType.ATTACKER));
	}
	
	private boolean checkIfCanRegister(L2Player player)
	{
		L2Clan clan = player.getClan();
		if (clan == null || clan.getLevel() < Config.SIEGE_CLAN_MIN_LEVEL)
		{
			if (Config.SIEGE_CLAN_MIN_LEVEL == 5) //default retail
				player.sendPacket(SystemMessageId.ONLY_CLAN_LEVEL_5_ABOVE_MAY_SIEGE);
			else
				player.sendMessage("Only clans with Level " + Config.SIEGE_CLAN_MIN_LEVEL
						+ " and higher may register for a castle siege.");
			return false;
		}
		else if (_isRegistrationOver)
		{
			player.sendPacket(SystemMessageId.REGISTRATION_CLOSED);
			return false;
		}
		else if (getIsInProgress())
		{
			player.sendPacket(SystemMessageId.NOT_SIEGE_REGISTRATION_TIME2);
			return false;
		}
		else if (clan.getHasHideout() > 0)
		{
			player.sendPacket(SystemMessageId.CLAN_OWNING_CLANHALL_MAY_NOT_SIEGE_CLANHALL);
			return false;
		}
		else if (checkIfAlreadyRegisteredForSameDay(player.getClan()))
		{
			player.sendPacket(SystemMessageId.APPLICATION_DENIED_BECAUSE_ALREADY_SUBMITTED_A_REQUEST_FOR_ANOTHER_SIEGE_BATTLE);
			return false;
		}
		else
		{
			if (CCHManager.getInstance().checkIsRegistered(player.getClan()))
			{
				player.sendPacket(SystemMessageId.ALREADY_REQUESTED_SIEGE_BATTLE);
				return false;
			}
		}
		
		if (_attackerClans.size() >= Config.SIEGE_MAX_ATTACKER)
		{
			player.sendPacket(SystemMessageId.ATTACKER_SIDE_FULL);
			return false;
		}
		
		return true;
	}
	
	public boolean checkIfAlreadyRegisteredForSameDay(L2Clan clan)
	{
		for (Siege siege : SiegeManager.getInstance().getSieges())
		{
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
		for (CCHSiege siege : CCHManager.getInstance().getSieges())
		{
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
	
	public void correctSiegeDateTime()
	{
		if (getSiegeDate().getTimeInMillis() < System.currentTimeMillis())
		{
			// Since siege has past reschedule it to the next one
			// This is usually caused by server being down
			setNextSiegeDate();
			saveSiegeDate();
		}
	}
	
	private void loadSiegeClan()
	{
		Connection con = null;
		try
		{
			_attackerClans.clear();
			
			// Add hideout owner as attacker
			if (_hideout.getOwnerId() > 0)
				addAttacker(_hideout.getOwnerId());
			
			con = L2DatabaseFactory.getInstance().getConnection(con);
			
			PreparedStatement statement = con.prepareStatement("SELECT clan_id FROM clan_hall_siege_clan WHERE clan_hall_id=?");
			statement.setInt(1, _hideout.getId());
			ResultSet rs = statement.executeQuery();
			while (rs.next())
				addAttacker(rs.getInt("clan_id"));
			
			statement.close();
		}
		catch (Exception e)
		{
			_log.error("Couldn't load contest clan list from the db!", e);
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
	}
	
	private void removeFlags()
	{
		for (L2SiegeClan sc : _attackerClans)
			if (sc != null)
				sc.removeFlags();
	}
	
	private void saveCastleSiege()
	{
		setNextSiegeDate(); // Set the next set date for a week from now
		// Schedule Time registration end
		getTimeRegistrationOverDate().setTimeInMillis(Calendar.getInstance().getTimeInMillis());
		getTimeRegistrationOverDate().add(Calendar.DAY_OF_MONTH, 1);
		_hideout.setIsTimeRegistrationOver(false);
		
		saveSiegeDate(); // Save the new date
		startAutoTask(); // Prepare auto start siege and end registration
	}
	
	public void saveSiegeDate()
	{
		if (_startSiegeTask.isScheduled())
			_startSiegeTask.schedule(1000);
		
		Connection con = null;
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection(con);
			PreparedStatement statement =
					con.prepareStatement("UPDATE clan_hall_siege SET siege_at=?, registration_end_at=?, is_registration_over=? WHERE clan_hall_id=?");
			long siegeDate = getSiegeDate().getTimeInMillis();
			long registrationEnd = getTimeRegistrationOverDate().getTimeInMillis();
			statement.setTimestamp(1, siegeDate > 0 ? new Timestamp(siegeDate) : null);
			statement.setTimestamp(2, registrationEnd > 0 ? new Timestamp(registrationEnd) : null);
			statement.setBoolean(3, getIsTimeRegistrationOver());
			statement.setInt(4, _hideout.getId());
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
	
	private void saveSiegeClan(L2Clan clan, boolean isUpdateRegistration)
	{
		if (clan.getHasCastle() > 0)
			return;
		
		Connection con = null;
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection(con);
			// Clan hall registrations are always attackers and carry no other data, so an update has nothing to store
			if (!isUpdateRegistration)
			{
				PreparedStatement statement =
						con.prepareStatement("INSERT INTO clan_hall_siege_clan (clan_id, clan_hall_id) VALUES (?,?) ON CONFLICT (clan_hall_id, clan_id) DO NOTHING");
				statement.setInt(1, clan.getClanId());
				statement.setInt(2, _hideout.getId());
				statement.execute();
				statement.close();
			}
			
			addAttacker(clan.getClanId());
			announceToPlayer(clan.getName() + " has been registered to siege " + _hideout.getName(), false);
		}
		catch (Exception e)
		{
			_log.error("Could not save contest clan registration!", e);
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
	}
	
	private void setNextSiegeDate()
	{
		while (getSiegeDate().getTimeInMillis() < System.currentTimeMillis())
		{
			if (getSiegeDate().get(Calendar.DAY_OF_WEEK) != Calendar.SATURDAY
					&& getSiegeDate().get(Calendar.DAY_OF_WEEK) != Calendar.SUNDAY)
				getSiegeDate().set(Calendar.DAY_OF_WEEK, Calendar.SATURDAY);
			// set the next siege day to the next weekend
			getSiegeDate().add(Calendar.DAY_OF_MONTH, 7);
			getSiegeDate().set(Calendar.HOUR_OF_DAY, 20);
		}
		_isRegistrationOver = false; // Allow registration for next siege
	}
	
	public Set<L2Npc> getFlag(L2Clan clan)
	{
		L2SiegeClan sc = getAttackerClan(clan);
		if (sc != null)
			return sc.getFlag();
		else
			return null;
	}
	
	public L2Npc getClosestFlag(L2Object obj)
	{
		if (obj instanceof L2Player && ((L2Player)obj).getClan() != null)
		{
			L2SiegeClan sc = getAttackerClan(((L2Player)obj).getClan());
			if (sc != null)
				return sc.getClosestFlag(obj);
		}
		return null;
	}
	
	public final L2SiegeZone getZone()
	{
		return _hideout.getBattlefield();
	}
	
	public final boolean getIsTimeRegistrationOver()
	{
		return _hideout.getIsTimeRegistrationOver();
	}
	
	public final Calendar getSiegeDate()
	{
		return _hideout.getSiegeDate();
	}
	
	public final Calendar getTimeRegistrationOverDate()
	{
		return _hideout.getTimeRegistrationOverDate();
	}
	
	public void endTimeRegistration(boolean automatic)
	{
		_hideout.setIsTimeRegistrationOver(true);
		if (!automatic)
		{
			saveSiegeDate();
			Broadcast.toAllOnlinePlayers(new SystemMessage(SystemMessageId.S1_ANNOUNCED_SIEGE_TIME).addString(_hideout
					.getName()));
		}
	}
	
	public final ClanHall getHideout()
	{
		return _hideout;
	}
	
	public final Set<L2SiegeClan> getAttackerClans()
	{
		return _attackerClans;
	}
	
	/* (non-Javadoc)
	 * @see com.l2jfree.gameserver.model.entity.AbstractSiege#checkIsAttacker(com.l2jfree.gameserver.model.L2Clan)
	 */
	@Override
	public boolean checkIsAttacker(L2Clan clan)
	{
		return getAttackerClan(clan) != null;
	}
	
	/* (non-Javadoc)
	 * @see com.l2jfree.gameserver.model.entity.AbstractSiege#checkIsDefender(com.l2jfree.gameserver.model.L2Clan)
	 */
	@Override
	public boolean checkIsDefender(L2Clan clan)
	{
		return false;
	}
	
	/* (non-Javadoc)
	 * @see com.l2jfree.gameserver.model.entity.AbstractSiege#getAttackerClan(com.l2jfree.gameserver.model.L2Clan)
	 */
	@Override
	public L2SiegeClan getAttackerClan(L2Clan clan)
	{
		if (clan == null)
			return null;
		return getAttackerClan(clan.getClanId());
	}
	
	private final L2SiegeClan getAttackerClan(int clanId)
	{
		for (L2SiegeClan sc : _attackerClans)
			if (sc != null && sc.getClanId() == clanId)
				return sc;
		return null;
	}
	
	/* (non-Javadoc)
	 * @see com.l2jfree.gameserver.model.entity.AbstractSiege#getDefenderClan(com.l2jfree.gameserver.model.L2Clan)
	 */
	@Override
	public L2SiegeClan getDefenderClan(L2Clan clan)
	{
		return null;
	}
	
	/* (non-Javadoc)
	 * @see com.l2jfree.gameserver.model.entity.AbstractSiege#getIsInProgress()
	 */
	@Override
	public boolean getIsInProgress()
	{
		return _isInProgress;
	}
	
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
				endSiege(null);
				cancel();
				return;
			}
			
			if (3600000 > timeRemaining)
			{
				if (timeRemaining > 120000)
					announceToPlayer(new SystemMessage(SystemMessageId.S1_MINUTES_REMAINING).addNumber(Math
							.round(timeRemaining / 60000)), true);
				else
					announceToPlayer(new SystemMessage(SystemMessageId.S1_SECONDS_REMAINING).addNumber(Math
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
			
			if (7200000 > timeRemaining)
			{
				if (!_isRegistrationOver)
				{
					_isRegistrationOver = true;
					announceToPlayer(SystemMessageId.CLANHALL_WAR_REGISTRATION_PERIOD_ENDED.getSystemMessage(), true);
				}
				
				if (timeRemaining > 120000)
					announceToParticipants(new SystemMessage(SystemMessageId.CONTEST_BEGIN_IN_S1_MINUTES)
							.addNumber((int)Math.round(timeRemaining / 60000.0)));
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
}
