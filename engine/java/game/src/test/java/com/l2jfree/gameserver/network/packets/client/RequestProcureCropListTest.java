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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.nio.ByteBuffer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.l2jfree.Config;
import com.l2jfree.gameserver.model.items.L2ItemInstance;

class RequestProcureCropListTest
{
	@Test
	@DisplayName("a crop claim cannot consume an inventory object with another item ID")
	void cropClaimMustMatchInventoryItem()
	{
		L2ItemInstance item = mock(L2ItemInstance.class);
		when(item.getItemId()).thenReturn(5000);
		when(item.getCount()).thenReturn(10L);

		assertThat(RequestProcureCropList.matchesClaimedCrop(item, 5001, 5)).isFalse();
		assertThat(RequestProcureCropList.matchesClaimedCrop(item, 5000, 11)).isFalse();
		assertThat(RequestProcureCropList.matchesClaimedCrop(item, 5000, 10)).isTrue();
		assertThat(RequestProcureCropList.matchesClaimedCrop(null, 5000, 5)).isFalse();
		assertThat(RequestProcureCropList.needsAdditionalSlot(item, 5001)).isTrue();
		assertThat(RequestProcureCropList.needsAdditionalSlot(item, 5000)).isFalse();
		assertThat(RequestProcureCropList.needsAdditionalSlot(null, 5000)).isTrue();
	}

	@Test
	@DisplayName("crop capacity uses the quantity that the exchange will reward")
	void rewardCapacityUsesActualQuantityAndRejectsOverflow()
	{
		assertThat(RequestProcureCropList.addCapacityRequirement(0, 4, 100)).isEqualTo(400);
		assertThat(RequestProcureCropList.addCapacityRequirement(Integer.MAX_VALUE - 100, 2, 100)).isEqualTo(-1);
		assertThat(RequestProcureCropList.addCapacityRequirement(0, Long.MAX_VALUE, 100)).isEqualTo(-1);
	}

	@Test
	@DisplayName("zero and negative crop quantities are rejected while decoding")
	void invalidCropQuantityIsRejectedDuringDecoding() throws Exception
	{
		final boolean packetFinal = Config.PACKET_FINAL;
		Config.PACKET_FINAL = false;
		try
		{
			assertThat(decode(0)).isFalse();
			assertThat(decode(-1)).isFalse();
		}
		finally
		{
			Config.PACKET_FINAL = packetFinal;
		}
	}

	private static boolean decode(final int quantity) throws Exception
	{
		final TestableRequestProcureCropList packet = new TestableRequestProcureCropList();
		final ByteBuffer buffer = ByteBuffer.allocate(20);
		buffer.putInt(1).putInt(1).putInt(2).putInt(3).putInt(quantity).flip();

		final Field bufferField = Class.forName("com.l2jfree.network.AbstractPacket")
				.getDeclaredField("_buf");
		bufferField.setAccessible(true);
		bufferField.set(packet, buffer);
		packet.decode();

		final Field itemsField = RequestProcureCropList.class.getDeclaredField("_items");
		itemsField.setAccessible(true);
		return itemsField.get(packet) != null;
	}

	private static final class TestableRequestProcureCropList extends RequestProcureCropList
	{
		void decode()
		{
			readImpl();
		}
	}
}
