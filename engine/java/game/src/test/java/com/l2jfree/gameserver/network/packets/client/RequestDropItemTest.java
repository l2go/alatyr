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
package com.l2jfree.gameserver.network.packets.client;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.lang.reflect.Field;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.l2jfree.gameserver.network.L2Client;
import com.l2jfree.gameserver.network.packets.server.ActionFailed;
import com.l2jfree.network.ReceivablePacket;

class RequestDropItemTest
{
	@Test
	@DisplayName("an item manipulation failure rejects the drop without accessing or dropping an item")
	void missingItemFailsTheRequest() throws Exception
	{
		L2Client client = mock(L2Client.class);
		RequestDropItem packet = new RequestDropItem();
		setField(ReceivablePacket.class, packet, "_client", client);

		assertTrue(packet.failIfItemMissing(null));
		verify(client).sendPacket(ActionFailed.STATIC_PACKET);
	}

	private static void setField(final Class<?> declaringClass, final Object target, final String name,
			final Object value) throws Exception
	{
		Field field = declaringClass.getDeclaredField(name);
		field.setAccessible(true);
		field.set(target, value);
	}
}
