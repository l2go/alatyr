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

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.l2jfree.testing.TestDatabase;
import com.l2jfree.testing.TestDatabases;

/** Only one server runs on a world database at a time. */
@Tag("integration")
class L2DatabaseFactoryWorldLockTest
{
	private static final TestDatabase POSTGRES = TestDatabases.postgres();
	
	@Test
	void aSecondServerOnTheSameDatabaseDoesNotStartUntilTheFirstStops() throws Exception
	{
		L2DatabaseFactory first = factory();
		L2DatabaseFactory second = factory();
		try
		{
			first.lockWorld();
			assertThatThrownBy(second::lockWorld).isInstanceOf(IllegalStateException.class)
					.hasMessageContaining("Another server");
			first.shutdown();
			assertThatCode(second::lockWorld).doesNotThrowAnyException();
		}
		finally
		{
			second.shutdown();
		}
	}
	
	@Test
	void serversOnDifferentDatabasesDoNotBlockEachOther() throws Exception
	{
		L2DatabaseFactory one = factory();
		TestDatabase other = TestDatabases.postgres();
		L2DatabaseFactory two = new L2DatabaseFactory(
				L2DatabaseFactory.poolConfig(other.jdbcUrl(), other.user(), other.password(), 3, 0));
		try
		{
			one.lockWorld();
			assertThatCode(two::lockWorld).doesNotThrowAnyException();
		}
		finally
		{
			one.shutdown();
			two.shutdown();
		}
	}
	
	private static L2DatabaseFactory factory()
	{
		return new L2DatabaseFactory(
				L2DatabaseFactory.poolConfig(POSTGRES.jdbcUrl(), POSTGRES.user(), POSTGRES.password(), 3, 0));
	}
}
