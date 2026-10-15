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
package com.l2jfree.smoke;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Builds the payload of a client packet. All numbers are little-endian, strings are UTF-16LE
 * with a two-byte terminator, as in every Lineage II packet.
 */
final class PacketWriter
{
	private final ByteArrayOutputStream _out = new ByteArrayOutputStream(128);

	PacketWriter writeC(int value)
	{
		_out.write(value & 0xff);
		return this;
	}

	PacketWriter writeH(int value)
	{
		_out.write(value & 0xff);
		_out.write((value >>> 8) & 0xff);
		return this;
	}

	PacketWriter writeD(int value)
	{
		_out.write(value & 0xff);
		_out.write((value >>> 8) & 0xff);
		_out.write((value >>> 16) & 0xff);
		_out.write((value >>> 24) & 0xff);
		return this;
	}

	/** A little-endian 64-bit word, the final-protocol form of a compressed quantity. */
	PacketWriter writeQ(long value)
	{
		return writeD((int)value).writeD((int)(value >>> 32));
	}

	PacketWriter writeB(byte[] data)
	{
		_out.writeBytes(data);
		return this;
	}

	PacketWriter writeZeros(int count)
	{
		_out.writeBytes(new byte[count]);
		return this;
	}

	PacketWriter writeS(String text)
	{
		_out.writeBytes(text.getBytes(StandardCharsets.UTF_16LE));
		return writeH(0);
	}

	byte[] toByteArray()
	{
		return _out.toByteArray();
	}
}
