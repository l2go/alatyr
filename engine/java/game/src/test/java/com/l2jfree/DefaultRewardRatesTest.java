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
package com.l2jfree;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.l2jfree.config.L2Properties;
import com.l2jfree.gameserver.gameobjects.HuntTestPlayers;
import com.l2jfree.gameserver.gameobjects.L2Npc;
import com.l2jfree.gameserver.gameobjects.stat.NpcStat;
import com.l2jfree.gameserver.gameobjects.stat.PlayerStat;
import com.l2jfree.gameserver.gameobjects.templates.L2NpcTemplate;
import com.l2jfree.gameserver.model.skills.Stats;

class DefaultRewardRatesTest
{
	private final Map<Field, Object> saved = new LinkedHashMap<>();

	@BeforeEach
	void loadShippedRewardDefaults() throws Exception
	{
		// The loaders write legacy static fields. Restore every mutable field so
		// this test cannot change the economy seen by another test in the JVM.
		for (Field field : Config.class.getFields())
			if (Modifier.isStatic(field.getModifiers()) && !Modifier.isFinal(field.getModifiers()))
				saved.put(field, field.get(null));
		load("RatesConfig", properties("rates.properties"));
		load("VitalityConfig", properties("vitality.properties"));
		load("ChampionsConfig", properties("champions.properties"));
	}

	@AfterEach
	void restoreRewardSettings() throws Exception
	{
		for (Map.Entry<Field, Object> entry : saved.entrySet())
			entry.getKey().set(null, entry.getValue());
	}

	@Test
	void shippedDefaultsKeepRewardMultipliersAtOne() throws Exception
	{
		assertThat(Config.ENABLE_VITALITY).isFalse();
		assertThat(Config.CHAMPION_FREQUENCY).isZero();
		for (String name : new String[] {
				"RATE_XP", "RATE_SP", "RATE_PARTY_XP", "RATE_PARTY_SP",
				"RATE_QUESTS_REWARD_EXPSP", "RATE_QUESTS_REWARD_ADENA", "RATE_QUESTS_REWARD_ITEMS",
				"RATE_DROP_SEALSTONE", "RATE_DROP_ADENA", "RATE_DROP_ADENA_RAID", "RATE_DROP_ADENA_GRAND_BOSS",
				"RATE_DROP_ITEMS", "RATE_DROP_ITEMS_RAID", "RATE_DROP_ITEMS_GRAND_BOSS", "RATE_DROP_ITEMS_JEWEL",
				"RATE_DROP_SPOIL", "RATE_DROP_SPOIL_RAID", "RATE_DROP_SPOIL_GRAND_BOSS", "RATE_DROP_MANOR", "RATE_DROP_QUEST" })
			assertThat(((Number)Config.class.getField(name).get(null)).doubleValue()).as(name).isEqualTo(1.0);
	}

	@Test
	void missingVitalityKeyKeepsTheDefaultOff() throws Exception
	{
		load("VitalityConfig", new L2Properties().setLog(false));
		assertThat(Config.ENABLE_VITALITY).isFalse();
	}

	@Test
	void fullVitalityDoesNotMultiplyANewPlayersKillReward() throws Exception
	{
		L2Npc mob = forager();
		assertThat(mob.getExpReward()).isEqualTo(88);
		assertThat(mob.getSpReward()).isEqualTo(8);
		PlayerStat player = fullVitality();
		player.addExpAndSp(mob.getExpReward(), mob.getSpReward(), true);
		verify(player).addExpAndSp(88, 8);
	}

	@Test
	void operatorCanEnableTheExistingVitalityMechanic() throws Exception
	{
		L2Properties operator = properties("vitality.properties");
		operator.setProperty("EnableVitality", "True");
		load("VitalityConfig", operator);
		assertThat(Config.ENABLE_VITALITY).isTrue();
		PlayerStat player = fullVitality();
		player.addExpAndSp(88, 8, true);
		verify(player).addExpAndSp(264, 24);
	}

	private static L2Properties properties(String file) throws Exception
	{
		return new L2Properties(Files.newInputStream(Path.of("config", file)));
	}

	private static void load(String name, L2Properties properties) throws Exception
	{
		// Exercise the actual loaders without booting a server or changing their visibility.
		Class<?> type = Class.forName(Config.class.getName() + "$" + name);
		var constructor = type.getDeclaredConstructor();
		constructor.setAccessible(true);
		var method = type.getDeclaredMethod("loadImpl", L2Properties.class);
		method.setAccessible(true);
		method.invoke(constructor.newInstance(), properties);
	}

	private static PlayerStat fullVitality()
	{
		PlayerStat player = spy(new PlayerStat(HuntTestPlayers.mockPlayer()));
		player.setVitalityPoints(PlayerStat.MAX_VITALITY_POINTS, true);
		// Observe the actual three-argument multiplier, without level-up, packets or persistence.
		doReturn(true).when(player).addExpAndSp(anyLong(), anyInt());
		return player;
	}

	private static L2Npc forager() throws Exception
	{
		var rows = Files.readAllLines(Path.of("..", "..", "..", "world", "catalog", "npc_template.csv"));
		var columns = Arrays.asList(rows.getFirst().split(",", -1));
		String[] row = rows.stream().filter(line -> line.startsWith("18003,")).findFirst().orElseThrow().split(",", -1);
		L2NpcTemplate template = mock(L2NpcTemplate.class);
		when(template.getRewardExp()).thenReturn(Integer.parseInt(row[columns.indexOf("reward_exp")]));
		when(template.getRewardSp()).thenReturn(Integer.parseInt(row[columns.indexOf("reward_sp")]));
		L2Npc mob = mock(L2Npc.class);
		NpcStat stat = mock(NpcStat.class);
		when(mob.getTemplate()).thenReturn(template);
		when(mob.getStat()).thenReturn(stat);
		when(stat.calcStat(Stats.MAX_HP, 1, mob, null)).thenReturn(1.0);
		doCallRealMethod().when(mob).getExpReward();
		doCallRealMethod().when(mob).getSpReward();
		return mob;
	}
}
