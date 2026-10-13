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
package com.l2jfree.gameserver.network.packets.server;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.nio.ByteBuffer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.l2jfree.gameserver.gameobjects.base.ClassId;
import com.l2jfree.gameserver.gameobjects.base.Race;
import com.l2jfree.gameserver.gameobjects.templates.L2PlayerTemplate;
import com.l2jfree.network.SendablePacket;

class NewCharacterSuccessTest
{
	@Test
	@DisplayName("the character-creation count equals the one distinct template that is written")
	void oneTemplateIsCountedOnce() throws Exception
	{
		L2PlayerTemplate template = mock(L2PlayerTemplate.class);
		when(template.getRace()).thenReturn(Race.Human);
		when(template.getClassId()).thenReturn(ClassId.HumanFighter);
		
		NewCharacterSuccess packet = new NewCharacterSuccess();
		packet.addChar(template);
		packet.addChar(template);
		packet.addChar(null);
		assertThat(packet.templateCount()).isEqualTo(1);
		
		ByteBuffer buffer = ByteBuffer.allocate(256);
		ThreadLocal<ByteBuffer> writing = writingBuffer();
		writing.set(buffer);
		try
		{
			packet.writeImpl();
		}
		finally
		{
			writing.remove();
		}
		
		buffer.flip();
		assertThat(buffer.get()).isEqualTo((byte)0x0D);
		assertThat(buffer.getInt()).isEqualTo(1);
		assertThat(buffer.remaining()).isEqualTo(20 * 4);
	}
	
	@SuppressWarnings("unchecked")
	private static ThreadLocal<ByteBuffer> writingBuffer() throws Exception
	{
		Field field = SendablePacket.class.getDeclaredField("WRITING");
		field.setAccessible(true);
		return (ThreadLocal<ByteBuffer>)field.get(null);
	}
}
