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
package com.l2jfree.gameserver.model.rewards;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class HuntContributionsTest
{
	@Test
	void overkillCreditsOnlyTheRemainingHp()
	{
		HuntContributions contributions = new HuntContributions();
		assertThat(contributions.add(1, 1, 1000, 10)).isEqualTo(10);
		assertThat(contributions.damage()).containsEntry(1L, 10.0);
	}

	@Test
	void changingPartyDoesNotMoveThePlayersContribution()
	{
		HuntContributions contributions = new HuntContributions();
		contributions.add(1, -1, 100, 1000);
		contributions.add(1, -2, 200, 900);
		assertThat(contributions.damage()).containsOnlyKeys(-1L).containsEntry(-1L, 300.0);
	}

	@Test
	void zeroAndRejectedDamageCannotBindASide()
	{
		HuntContributions contributions = new HuntContributions();
		contributions.add(1, 1, 0, 1000);
		contributions.add(1, 1, 10, 0);
		contributions.add(1, 1, Double.NaN, 1000);
		assertThat(contributions.damage()).isEmpty();
		assertThat(contributions.sideOf(1)).isNull();
	}

	@Test
	void resetClearsCreditAndSpawnAlsoChangesLifeId()
	{
		HuntContributions contributions = new HuntContributions();
		long life = contributions.lifeId();
		contributions.add(1, 1, 100, 1000);
		contributions.reset();
		assertThat(contributions.damage()).isEmpty();
		assertThat(contributions.sideOf(1)).isNull();
		assertThat(contributions.lifeId()).isEqualTo(life);
		contributions.add(2, 2, 100, 1000);
		contributions.spawn();
		assertThat(contributions.damage()).isEmpty();
		assertThat(contributions.lifeId()).isGreaterThan(life);
		assertThat(new HuntContributions().lifeId()).isGreaterThan(contributions.lifeId());
	}
}
