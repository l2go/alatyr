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
package com.l2jfree.gameserver.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

class JdbcCallSitesTest
{
	@Test
	void findsStatementsWithTheirBindingsAndColumnReads() throws Exception
	{
		List<JdbcCallSites.Site> sites = JdbcCallSites.scan(Path.of("src/main/java"));
		assertThat(sites).hasSizeGreaterThan(300);
		
		JdbcCallSites.Site privileges = sites.stream().filter(
				site -> site.location().contains("ClanRepository.java:") && site.sql().contains("FROM clan_rank_privilege"))
				.findFirst().orElseThrow();
		assertThat(privileges.setters()).containsEntry(1, "setInt");
		assertThat(privileges.namedGetters()).containsKey("pledge_rank");
	}
}
