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
package com.l2jfree.loginserver.manager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.l2jfree.loginserver.manager.LoginManager.AuthLoginResult;
import com.l2jfree.loginserver.network.L2Client;

class LoginOccupantTest
{
	@Test
	@DisplayName("a refused login keeps the client it saw, and a later client stays in the map")
	void refusalRemovesOnlyTheClientItSaw() throws Exception
	{
		LoginManager manager = mock(LoginManager.class, CALLS_REAL_METHODS);
		Map<String, L2Client> clients = new ConcurrentHashMap<String, L2Client>();
		setField(manager, "_loginServerClients", clients);
		L2Client first = mock(L2Client.class);
		L2Client refused = mock(L2Client.class);
		L2Client later = mock(L2Client.class);
		clients.put("alice", first);

		assertThat(manager.reserveLoginSlot("alice", refused)).isEqualTo(AuthLoginResult.ALREADY_ON_LS);

		clients.remove("alice", first);
		clients.put("alice", later);

		L2Client seen = manager.takeOccupiedLogin(refused);
		assertThat(seen).isSameAs(first);
		manager.removeAuthedLoginClient("alice", seen);
		assertThat(clients.get("alice")).isSameAs(later);
		assertThat(manager.takeOccupiedLogin(refused)).isNull();
	}

	@Test
	@DisplayName("a free login slot is taken by the client that asked, with nobody to close")
	void freeSlotHasNoOccupiedClient() throws Exception
	{
		LoginManager manager = mock(LoginManager.class, CALLS_REAL_METHODS);
		Map<String, L2Client> clients = new ConcurrentHashMap<String, L2Client>();
		setField(manager, "_loginServerClients", clients);
		L2Client client = mock(L2Client.class);

		assertThat(manager.reserveLoginSlot("alice", client)).isEqualTo(AuthLoginResult.AUTH_SUCCESS);
		assertThat(clients.get("alice")).isSameAs(client);
		assertThat(manager.takeOccupiedLogin(client)).isNull();
	}

	private static void setField(LoginManager manager, String name, Object value) throws Exception
	{
		Field field = LoginManager.class.getDeclaredField(name);
		field.setAccessible(true);
		field.set(manager, value);
	}
}
