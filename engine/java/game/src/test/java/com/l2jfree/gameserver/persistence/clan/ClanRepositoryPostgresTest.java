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
package com.l2jfree.gameserver.persistence.clan;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.l2jfree.L2DatabaseFactory;
import com.l2jfree.gameserver.persistence.WorldDatabase;
import com.l2jfree.gameserver.persistence.clan.ClanRepository.ClanRecord;
import com.l2jfree.gameserver.persistence.clan.ClanRepository.CrestRecord;
import com.l2jfree.gameserver.persistence.clan.ClanRepository.MemberRecord;
import com.l2jfree.gameserver.persistence.clan.ClanRepository.RankPrivilegeRecord;
import com.l2jfree.gameserver.persistence.clan.ClanRepository.SkillRecord;
import com.l2jfree.gameserver.persistence.clan.ClanRepository.SubPledgeRecord;
import com.l2jfree.gameserver.persistence.clan.ClanRepository.WarList;
import com.l2jfree.gameserver.persistence.clan.ClanRepository.WarOpponentRecord;
import com.l2jfree.gameserver.persistence.clan.ClanRepository.WarRecord;

/** Clans, their members, notices, rank privileges, skills, sub-units, wars and crests on a real PostgreSQL 18. */
@Tag("integration")
class ClanRepositoryPostgresTest
{
	/** Ids that no other test uses. */
	private static final int LEADER = 981_000_001;
	private static final int SECOND = 981_000_002;
	private static final int THIRD = 981_000_003;
	private static final int CLAN_A = 981_000_101;
	private static final int CLAN_B = 981_000_102;
	private static final int HALL = 32_001;
	private static final int CLAN_C = 981_000_103;
	private static final int CREST_1 = 981_000_201;
	private static final int CREST_2 = 981_000_202;
	private static final int CREST_3 = 981_000_203;
	private static final String PREFIX = "ClanRepositoryPostgresTest_";

	private final ClanRepository _repository = ClanRepository.getInstance();

	@BeforeEach
	void createThePlayers()
	{
		WorldDatabase.start();
		cleanUp();
		for (int id : new int[] { LEADER, SECOND, THIRD })
		{
			update("INSERT INTO player (id, account_name, name, race_id, active_class_id, base_class_id) "
					+ "VALUES (?, ?, ?, 0, 0, 0)", id, PREFIX + "account", PREFIX + "player" + id);
		}
	}

	@AfterEach
	void cleanUp()
	{
		update("DELETE FROM clan WHERE id BETWEEN 981000101 AND 981000199");
		update("DELETE FROM crest WHERE id BETWEEN 981000201 AND 981000299");
		update("DELETE FROM player WHERE id BETWEEN 981000001 AND 981000099");
	}

	private ClanRecord clan(int id, String name)
	{
		return clan(id, name, LEADER);
	}
	
	private ClanRecord clan(int id, String name, int leaderId)
	{
		return new ClanRecord(id, name, 0, 0, 0, null, leaderId, 0, 0, 0, 0, 0, 0, 0, 0, 0);
	}
	
	private MemberRecord member(int playerId) throws Exception
	{
		return _repository.loadMembers(CLAN_A).stream().filter(m -> m.objectId() == playerId).findFirst().orElseThrow();
	}
	
	private CrestRecord crest(int id) throws Exception
	{
		return _repository.loadCrests().stream().filter(c -> c.id() == id).findFirst().orElseThrow();
	}

	@Test
	@DisplayName("a clan is stored with its notice and read back")
	void roundTrip() throws Exception
	{
		ClanRecord stored = new ClanRecord(CLAN_A, PREFIX + "Alpha", 3, 9, 0, null, LEADER, CREST_1, CREST_2, 0, 0, 0,
				0, 0, 0, 0);

		assertThat(_repository.createClan(stored, "Change me")).isTrue();

		ClanRecord loaded = _repository.loadClan(CLAN_A);
		assertThat(loaded).isEqualTo(stored);
		assertThat(_repository.loadClanIds()).contains(CLAN_A);
		assertThat(_repository.loadNotice(CLAN_A)).isEqualTo("Change me");
		assertThat(_repository.loadNoticeEnabled(CLAN_A)).isFalse();
		assertThat(_repository.loadClan(CLAN_B)).isNull();
	}

	@Test
	@DisplayName("0 is NULL in the database and NULL is 0 in the game")
	void noneIsNull() throws Exception
	{
		assertThat(_repository.createClan(clan(CLAN_A, PREFIX + "None"), "x")).isTrue();

		assertThat(string("SELECT concat_ws(',', castle_id, alliance_id, alliance_name, crest_id, large_crest_id, "
				+ "alliance_crest_id, clan_hall_auction_id, alliance_penalty_expire_at, alliance_penalty_type, "
				+ "member_penalty_expire_at, dissolve_at) FROM clan WHERE id = ?", CLAN_A)).isEmpty();

		ClanRecord loaded = _repository.loadClan(CLAN_A);
		assertThat(loaded.castleId()).isZero();
		assertThat(loaded.allianceId()).isZero();
		assertThat(loaded.allianceName()).isNull();
		assertThat(loaded.crestId()).isZero();
		assertThat(loaded.allyPenaltyExpiryTime()).isZero();
		assertThat(loaded.allyPenaltyType()).isZero();
		assertThat(loaded.dissolveTime()).isZero();
	}

	@Test
	@DisplayName("the leader, the alliance, the reputation and the penalties are updated")
	void updatesWhatChanges() throws Exception
	{
		assertThat(_repository.createClan(clan(CLAN_A, PREFIX + "Update"), "x")).isTrue();
		long now = System.currentTimeMillis();
		long penalty = now + 3_600_000L;

		_repository.updateClan(new ClanRecord(CLAN_A, PREFIX + "Update", 0, 0, CLAN_A, PREFIX + "Ally", SECOND, 0, 0,
				CREST_3, -5000, 0, penalty, 4, now + 1_000L, now + 2_000L));
		_repository.updateLevel(CLAN_A, 5);
		// A clan can only bid on a hall that is on auction.
		WorldDatabase.execute("DELETE FROM clan_hall WHERE id = " + HALL);
		WorldDatabase.execute("INSERT INTO clan_hall (id, name, town_name) VALUES (" + HALL + ", 'Test Hall', 'Test Town')");
		WorldDatabase.execute("INSERT INTO clan_hall_auction (id, item_name, starting_bid, end_at) VALUES (" + HALL
				+ ", 'Test Hall', 100, now())");
		_repository.updateAuctionBid(CLAN_A, HALL);

		ClanRecord loaded = _repository.loadClan(CLAN_A);
		assertThat(loaded.leaderId()).isEqualTo(SECOND);
		assertThat(loaded.allianceId()).isEqualTo(CLAN_A);
		assertThat(loaded.allianceName()).isEqualTo(PREFIX + "Ally");
		assertThat(loaded.allianceCrestId()).isEqualTo(CREST_3);
		assertThat(loaded.reputationScore()).isEqualTo(-5000);
		assertThat(loaded.allyPenaltyExpiryTime()).isEqualTo(penalty);
		assertThat(loaded.allyPenaltyType()).isEqualTo(4);
		assertThat(loaded.memberPenaltyExpiryTime()).isEqualTo(now + 1_000L);
		assertThat(loaded.dissolveTime()).isEqualTo(now + 2_000L);
		assertThat(loaded.level()).isEqualTo(5);
		assertThat(loaded.auctionBidAt()).isEqualTo(HALL);
		assertThat(string("SELECT alliance_penalty_type FROM clan WHERE id = ?", CLAN_A)).isEqualTo("DISSOLVE_ALLY");

		// A penalty that is over (expiry 0) is stored as no penalty at all, and the alliance name needs an alliance.
		_repository.updateClan(new ClanRecord(CLAN_A, PREFIX + "Update", 0, 0, 0, PREFIX + "Ally", SECOND, 0, 0, 0, 0,
				0, 0, 3, 0, 0));
		_repository.updateAuctionBid(CLAN_A, 0);
		loaded = _repository.loadClan(CLAN_A);
		assertThat(loaded.allianceId()).isZero();
		assertThat(loaded.allianceName()).isNull();
		assertThat(loaded.allyPenaltyType()).isZero();
		assertThat(loaded.auctionBidAt()).isZero();
		assertThat(string("SELECT alliance_penalty_type FROM clan WHERE id = ?", CLAN_A)).isEmpty();
		WorldDatabase.execute("DELETE FROM clan_hall WHERE id = " + HALL);
	}

	@Test
	@DisplayName("a clan name is unique without regard to case, and a failed creation leaves nothing behind")
	void clanNameIsCaseInsensitive() throws Exception
	{
		assertThat(_repository.createClan(clan(CLAN_A, PREFIX + "Casey"), "first")).isTrue();

		assertThat(_repository.createClan(clan(CLAN_B, PREFIX.toUpperCase() + "CASEY"), "second")).isFalse();

		assertThat(_repository.loadClan(CLAN_B)).isNull();
		assertThat(_repository.loadNotice(CLAN_B)).isNull();
		assertThat(_repository.loadClan(CLAN_A).name()).isEqualTo(PREFIX + "Casey");
		assertThat(string("SELECT id FROM clan WHERE name = ?", PREFIX.toLowerCase() + "casey")).isEqualTo(
				String.valueOf(CLAN_A));
	}

	@Test
	@DisplayName("the notice is updated and shown or hidden")
	void notice() throws Exception
	{
		assertThat(_repository.createClan(clan(CLAN_A, PREFIX + "Notice"), "Change me")).isTrue();

		_repository.updateNotice(CLAN_A, "Line one<br>Line two");
		_repository.updateNoticeEnabled(CLAN_A, true);

		assertThat(_repository.loadNotice(CLAN_A)).isEqualTo("Line one<br>Line two");
		assertThat(_repository.loadNoticeEnabled(CLAN_A)).isTrue();

		// insertNotice does not overwrite an existing notice
		_repository.insertNotice(CLAN_A, "Other", false);
		assertThat(_repository.loadNotice(CLAN_A)).isEqualTo("Line one<br>Line two");

		// A clan without a notice row reads as "none"
		_repository.createClan(clan(CLAN_B, PREFIX + "NoNotice"), "x");
		update("DELETE FROM clan_notice WHERE clan_id = ?", CLAN_B);
		assertThat(_repository.loadNotice(CLAN_B)).isNull();
		assertThat(_repository.loadNoticeEnabled(CLAN_B)).isNull();
	}

	@Test
	@DisplayName("each rank of a clan has its own privileges, and saving a rank again replaces them")
	void privilegesPerRank() throws Exception
	{
		assertThat(_repository.createClan(clan(CLAN_A, PREFIX + "Ranks"), "x")).isTrue();
		assertThat(_repository.createClan(clan(CLAN_B, PREFIX + "Other"), "x")).isTrue();

		_repository.saveRankPrivileges(CLAN_A, 1, 16_777_214);
		_repository.saveRankPrivileges(CLAN_A, 2, 2);
		_repository.saveRankPrivileges(CLAN_A, 9, 0);
		_repository.saveRankPrivileges(CLAN_B, 1, 8);
		_repository.saveRankPrivileges(CLAN_A, 2, 2 | 4);

		assertThat(_repository.loadRankPrivileges(CLAN_A)).containsExactlyInAnyOrder(
				new RankPrivilegeRecord(1, 16_777_214), new RankPrivilegeRecord(2, 6), new RankPrivilegeRecord(9, 0));
		assertThat(_repository.loadRankPrivileges(CLAN_B)).containsExactly(new RankPrivilegeRecord(1, 8));
	}

	@Test
	@DisplayName("a clan skill is added and raised")
	void skills() throws Exception
	{
		assertThat(_repository.createClan(clan(CLAN_A, PREFIX + "Skills"), "x")).isTrue();

		_repository.saveSkill(CLAN_A, 370, 1);
		_repository.saveSkill(CLAN_A, 371, 1);
		_repository.saveSkill(CLAN_A, 370, 2);

		assertThat(_repository.loadSkills(CLAN_A)).containsExactlyInAnyOrder(new SkillRecord(370, 2),
				new SkillRecord(371, 1));
	}

	@Test
	@DisplayName("sub-units keep their name and leader, and a unit without a leader has none")
	void subPledges() throws Exception
	{
		assertThat(_repository.createClan(clan(CLAN_A, PREFIX + "Units"), "x")).isTrue();

		_repository.insertSubPledge(CLAN_A, -1, "Academy", 0);
		_repository.insertSubPledge(CLAN_A, 100, "Guards", SECOND);

		assertThat(_repository.loadSubPledges(CLAN_A)).containsExactlyInAnyOrder(
				new SubPledgeRecord(-1, "Academy", 0), new SubPledgeRecord(100, "Guards", SECOND));
		assertThat(string("SELECT leader_player_id FROM clan_subpledge WHERE clan_id = ? AND subpledge_type = -1",
				CLAN_A)).isEmpty();

		_repository.updateSubPledge(CLAN_A, 100, "Royal Guards", 0);
		_repository.updateSubPledge(CLAN_A, -1, "Academy", THIRD);

		assertThat(_repository.loadSubPledges(CLAN_A)).containsExactlyInAnyOrder(
				new SubPledgeRecord(-1, "Academy", THIRD), new SubPledgeRecord(100, "Royal Guards", 0));
	}

	@Test
	@DisplayName("a war is directed, is recorded once, and shows in the lists of both clans")
	void wars() throws Exception
	{
		assertThat(_repository.createClan(clan(CLAN_A, PREFIX + "WarA"), "x")).isTrue();
		assertThat(_repository.createClan(new ClanRecord(CLAN_B, PREFIX + "WarB", 0, 0, CLAN_B, PREFIX + "AllyB", SECOND,
				0, 0, 0, 0, 0, 0, 0, 0, 0), "x")).isTrue();
		assertThat(_repository.createClan(clan(CLAN_C, PREFIX + "WarC", THIRD), "x")).isTrue();

		_repository.insertWar(CLAN_A, CLAN_B);
		_repository.insertWar(CLAN_A, CLAN_B);
		_repository.insertWar(CLAN_C, CLAN_A);

		assertThat(_repository.loadWars()).contains(new WarRecord(CLAN_A, CLAN_B), new WarRecord(CLAN_C, CLAN_A));
		assertThat(count("SELECT count(*) FROM clan_war WHERE declaring_clan_id = ? AND target_clan_id = ?", CLAN_A,
				CLAN_B)).isEqualTo(1);
		assertThat(_repository.loadWarOpponents(CLAN_A, WarList.DECLARED)).containsExactly(
				new WarOpponentRecord(CLAN_B, PREFIX + "WarB", CLAN_B, PREFIX + "AllyB"));
		assertThat(_repository.loadWarOpponents(CLAN_A, WarList.RECEIVED)).containsExactly(
				new WarOpponentRecord(CLAN_C, PREFIX + "WarC", 0, null));
		assertThat(_repository.loadWarOpponents(CLAN_A, WarList.MUTUAL)).isEmpty();

		// B answers: the war is now in both directions and is the only one A and B share
		_repository.insertWar(CLAN_B, CLAN_A);
		assertThat(_repository.loadWarOpponents(CLAN_A, WarList.DECLARED)).isEmpty();
		assertThat(_repository.loadWarOpponents(CLAN_A, WarList.MUTUAL)).extracting(WarOpponentRecord::clanId)
				.containsExactly(CLAN_B);
		assertThat(_repository.loadWarOpponents(CLAN_B, WarList.MUTUAL)).extracting(WarOpponentRecord::clanId)
				.containsExactly(CLAN_A);

		_repository.deleteWar(CLAN_A, CLAN_B);
		assertThat(_repository.loadWarOpponents(CLAN_A, WarList.MUTUAL)).isEmpty();
		assertThat(_repository.loadWarOpponents(CLAN_A, WarList.RECEIVED)).extracting(WarOpponentRecord::clanId)
				.containsExactlyInAnyOrder(CLAN_B, CLAN_C);
	}

	@Test
	@DisplayName("crest images are stored by kind, found again, and removed only as the kind they have")
	void crests() throws Exception
	{
		assertThat(_repository.createClan(clan(CLAN_A, PREFIX + "Crest"), "x")).isTrue();
		byte[] small = { 1, 2, 3 };
		byte[] large = { 4, 5, 6, 7 };
		byte[] alliance = { 8 };

		_repository.saveCrest(CREST_1, ClanRepository.KIND_CLAN, small);
		_repository.saveCrest(CREST_2, ClanRepository.KIND_CLAN_LARGE, large);
		_repository.saveCrest(CREST_3, ClanRepository.KIND_ALLIANCE, alliance);
		_repository.updateClanCrest(CLAN_A, CREST_1);
		_repository.updateClanLargeCrest(CLAN_A, CREST_2);

		List<CrestRecord> crests = _repository.loadCrests().stream().filter(c -> c.id() >= CREST_1 && c.id() <= CREST_3)
				.toList();
		assertThat(crests).extracting(CrestRecord::id).containsExactlyInAnyOrder(CREST_1, CREST_2, CREST_3);
		assertThat(crest(CREST_2).kind()).isEqualTo(ClanRepository.KIND_CLAN_LARGE);
		assertThat(crest(CREST_2).image()).isEqualTo(large);
		assertThat(crest(CREST_3).kind()).isEqualTo(ClanRepository.KIND_ALLIANCE);
		assertThat(crest(CREST_3).image()).isEqualTo(alliance);
		ClanRecord loaded = _repository.loadClan(CLAN_A);
		assertThat(loaded.crestId()).isEqualTo(CREST_1);
		assertThat(loaded.largeCrestId()).isEqualTo(CREST_2);

		// The alliance crest goes to every clan of the alliance
		update("UPDATE clan SET alliance_id = ?, alliance_name = ? WHERE id = ?", CLAN_A, PREFIX + "CrestAlly", CLAN_A);
		_repository.updateAllianceCrest(CLAN_A, CREST_3);
		assertThat(_repository.loadClan(CLAN_A).allianceCrestId()).isEqualTo(CREST_3);

		// Removing needs the right kind
		assertThat(_repository.deleteCrest(CREST_1, ClanRepository.KIND_CLAN_LARGE)).isFalse();
		assertThat(_repository.deleteCrest(CREST_1, ClanRepository.KIND_CLAN)).isTrue();
		assertThat(_repository.deleteCrest(CREST_1, ClanRepository.KIND_CLAN)).isFalse();

		_repository.updateClanCrest(CLAN_A, 0);
		assertThat(_repository.loadClan(CLAN_A).crestId()).isZero();
		assertThat(string("SELECT crest_id FROM clan WHERE id = ?", CLAN_A)).isEmpty();

		// A new image under the id of an old one replaces it
		_repository.saveCrest(CREST_2, ClanRepository.KIND_CLAN_LARGE, small);
		assertThat(crest(CREST_2).image()).isEqualTo(small);
	}

	@Test
	@DisplayName("the members of a clan are the players that name it, and leaving the clan clears the links")
	void members() throws Exception
	{
		assertThat(_repository.createClan(clan(CLAN_A, PREFIX + "Members"), "x")).isTrue();
		update("UPDATE player SET clan_id = ?, title = 'Chief', pledge_rank = 5, pledge_type = 100, is_female = true, "
				+ "level = 40 WHERE id = ?", CLAN_A, SECOND);
		update("UPDATE player SET clan_id = ?, apprentice_player_id = ? WHERE id = ?", CLAN_A, THIRD, LEADER);
		update("UPDATE player SET clan_id = ?, sponsor_player_id = ?, pledge_type = -1, academy_join_level = 12, "
				+ "wants_peace = true WHERE id = ?", CLAN_A, LEADER, THIRD);

		List<MemberRecord> members = _repository.loadMembers(CLAN_A);
		assertThat(members).extracting(MemberRecord::objectId).containsExactlyInAnyOrder(LEADER, SECOND, THIRD);
		MemberRecord second = member(SECOND);
		assertThat(second.name()).isEqualTo(PREFIX + "player" + SECOND);
		assertThat(second.title()).isEqualTo("Chief");
		assertThat(second.level()).isEqualTo(40);
		assertThat(second.pledgeRank()).isEqualTo(5);
		assertThat(second.pledgeType()).isEqualTo(100);
		assertThat(second.sex()).isEqualTo(1);
		assertThat(second.apprenticeId()).isZero();
		assertThat(second.sponsorId()).isZero();
		assertThat(member(LEADER).apprenticeId()).isEqualTo(THIRD);
		assertThat(member(THIRD).sponsorId()).isEqualTo(LEADER);

		_repository.updateMemberPledgeRank(SECOND, 7);
		_repository.updateMemberPledgeType(SECOND, 200);
		_repository.updateMemberSponsors(SECOND, THIRD, 0);
		assertThat(member(SECOND).pledgeRank()).isEqualTo(7);
		assertThat(member(SECOND).pledgeType()).isEqualTo(200);
		assertThat(member(SECOND).apprenticeId()).isEqualTo(THIRD);

		// The academy member leaves: nobody keeps pointing at it, and it cannot rejoin before the moment given
		long joinAllowed = System.currentTimeMillis() + 86_400_000L;
		assertThat(_repository.removeMember(THIRD, joinAllowed, 0)).isTrue();

		assertThat(string("SELECT concat_ws(',', clan_id, pledge_type, academy_join_level, wants_peace, sponsor_player_id, "
				+ "clan_create_allowed_at) FROM player WHERE id = ?", THIRD)).isEqualTo("0,0,f");
		assertThat(string("SELECT clan_join_allowed_at FROM player WHERE id = ?", THIRD)).isNotEmpty();
		assertThat(Math.abs(timestamp("SELECT clan_join_allowed_at FROM player WHERE id = ?", THIRD) - joinAllowed))
				.isLessThan(1_000L);
		assertThat(string("SELECT apprentice_player_id FROM player WHERE id = ?", LEADER)).isEmpty();
		assertThat(string("SELECT apprentice_player_id FROM player WHERE id = ?", SECOND)).isEmpty();
		assertThat(_repository.loadMembers(CLAN_A)).extracting(MemberRecord::objectId)
				.containsExactlyInAnyOrder(LEADER, SECOND);
	}

	@Test
	@DisplayName("deleting a clan removes what belongs to it and clears the links to it, through the foreign keys")
	void deleteClan() throws Exception
	{
		assertThat(_repository.createClan(clan(CLAN_A, PREFIX + "Dissolved"), "x")).isTrue();
		assertThat(_repository.createClan(clan(CLAN_B, PREFIX + "Survivor", SECOND), "x")).isTrue();
		assertThat(_repository.createClan(clan(CLAN_C, PREFIX + "Member", THIRD), "x")).isTrue();
		_repository.updateClan(new ClanRecord(CLAN_A, PREFIX + "Dissolved", 0, 0, CLAN_A, PREFIX + "DissolvedAlly", LEADER,
				0, 0, CREST_3, 0, 0, 0, 0, 0, 0));
		_repository.updateClan(new ClanRecord(CLAN_C, PREFIX + "Member", 0, 0, CLAN_A, PREFIX + "DissolvedAlly", THIRD,
				0, 0, CREST_3, 0, 0, 0, 0, 0, 0));
		_repository.saveRankPrivileges(CLAN_A, 1, 2);
		_repository.saveSkill(CLAN_A, 370, 1);
		_repository.insertSubPledge(CLAN_A, 100, "Guards", SECOND);
		_repository.insertWar(CLAN_A, CLAN_B);
		_repository.insertWar(CLAN_B, CLAN_A);
		_repository.insertWar(CLAN_B, CLAN_C);
		update("UPDATE player SET clan_id = ? WHERE id = ?", CLAN_A, LEADER);
		update("UPDATE castle SET tax_percent = 15 WHERE id = 9");
		update("UPDATE clan SET castle_id = 9 WHERE id = ?", CLAN_A);

		try
		{
			assertThat(_repository.deleteClan(CLAN_A, 9)).isTrue();

			assertThat(_repository.loadClan(CLAN_A)).isNull();
			assertThat(_repository.loadNotice(CLAN_A)).isNull();
			assertThat(_repository.loadRankPrivileges(CLAN_A)).isEmpty();
			assertThat(_repository.loadSkills(CLAN_A)).isEmpty();
			assertThat(_repository.loadSubPledges(CLAN_A)).isEmpty();
			assertThat(_repository.loadWars()).noneMatch(w -> w.declaringClanId() == CLAN_A || w.targetClanId() == CLAN_A);
			assertThat(_repository.loadWars()).contains(new WarRecord(CLAN_B, CLAN_C));
			assertThat(string("SELECT clan_id FROM player WHERE id = ?", LEADER)).isEmpty();
			assertThat(string("SELECT tax_percent FROM castle WHERE id = 9")).isEqualTo("0");

			// The clans that were in the alliance are in no alliance, and keep no copy of its name or crest
			ClanRecord member = _repository.loadClan(CLAN_C);
			assertThat(member).isNotNull();
			assertThat(member.allianceId()).isZero();
			assertThat(member.allianceName()).isNull();
			assertThat(member.allianceCrestId()).isZero();
			assertThat(_repository.loadClan(CLAN_B)).isNotNull();
			assertThat(_repository.loadNotice(CLAN_B)).isEqualTo("x");
		}
		finally
		{
			update("UPDATE castle SET tax_percent = 0 WHERE id = 9");
		}
	}

	@Test
	@DisplayName("a transaction of several operations is rolled back as a whole")
	void transactionRollsBack() throws Exception
	{
		assertThat(_repository.createClan(clan(CLAN_A, PREFIX + "Atomic"), "x")).isTrue();

		boolean committed = ClanRepository.transaction("test", () -> {
			_repository.updateLevel(CLAN_A, 7);
			_repository.insertSubPledge(CLAN_A, 12345, "invalid type", 0);
		});

		assertThat(committed).isFalse();
		assertThat(_repository.loadClan(CLAN_A).level()).isZero();
		assertThat(_repository.loadSubPledges(CLAN_A)).isEmpty();
	}

	private static void update(String sql, Object... parameters)
	{
		try (Connection con = L2DatabaseFactory.getInstance().getPoolConnection();
				PreparedStatement statement = con.prepareStatement(sql))
		{
			bind(statement, parameters);
			statement.executeUpdate();
		}
		catch (java.sql.SQLException e)
		{
			throw new IllegalStateException(e);
		}
	}

	/** @return the first column of the first row as text, "" for NULL, and null when there is no row */
	private static String string(String sql, Object... parameters)
	{
		try (Connection con = L2DatabaseFactory.getInstance().getPoolConnection();
				PreparedStatement statement = con.prepareStatement(sql))
		{
			bind(statement, parameters);
			try (ResultSet rset = statement.executeQuery())
			{
				if (!rset.next())
					return null;
				String value = rset.getString(1);
				return value == null ? "" : value;
			}
		}
		catch (java.sql.SQLException e)
		{
			throw new IllegalStateException(e);
		}
	}

	private static long count(String sql, Object... parameters)
	{
		return Long.parseLong(string(sql, parameters));
	}

	private static long timestamp(String sql, Object... parameters)
	{
		try (Connection con = L2DatabaseFactory.getInstance().getPoolConnection();
				PreparedStatement statement = con.prepareStatement(sql))
		{
			bind(statement, parameters);
			try (ResultSet rset = statement.executeQuery())
			{
				rset.next();
				Timestamp moment = rset.getTimestamp(1);
				return moment.getTime();
			}
		}
		catch (java.sql.SQLException e)
		{
			throw new IllegalStateException(e);
		}
	}

	private static void bind(PreparedStatement statement, Object... parameters) throws java.sql.SQLException
	{
		for (int i = 0; i < parameters.length; i++)
			statement.setObject(i + 1, parameters[i]);
	}
}
