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

import java.util.Arrays;

/**
 * Reads the fields of a decrypted server packet. All numbers are little-endian, strings are
 * UTF-16LE with a two-byte terminator. A read past the end is a protocol failure.
 */
final class PacketReader
{
	private final byte[] _data;
	private int _position;

	PacketReader(byte[] data)
	{
		_data = data;
	}

	int position()
	{
		return _position;
	}

	int remaining()
	{
		return _data.length - _position;
	}

	int readC() throws SmokeException
	{
		require(1);
		return _data[_position++] & 0xff;
	}

	int readH() throws SmokeException
	{
		require(2);
		int value = (_data[_position] & 0xff) | ((_data[_position + 1] & 0xff) << 8);
		_position += 2;
		return value;
	}

	int readD() throws SmokeException
	{
		require(4);
		int value = (_data[_position] & 0xff) | ((_data[_position + 1] & 0xff) << 8)
				| ((_data[_position + 2] & 0xff) << 16) | ((_data[_position + 3] & 0xff) << 24);
		_position += 4;
		return value;
	}

	long readQ() throws SmokeException
	{
		long low = readD() & 0xffffffffL;
		long high = readD() & 0xffffffffL;
		return low | (high << 32);
	}

	byte[] readB(int count) throws SmokeException
	{
		require(count);
		byte[] result = Arrays.copyOfRange(_data, _position, _position + count);
		_position += count;
		return result;
	}

	void skip(int count) throws SmokeException
	{
		require(count);
		_position += count;
	}

	String readS() throws SmokeException
	{
		StringBuilder text = new StringBuilder();
		while (true)
		{
			char c = (char)readH();
			if (c == 0)
				return text.toString();

			text.append(c);
		}
	}

	private void require(int count) throws SmokeException
	{
		if (count < 0 || remaining() < count)
		{
			throw new SmokeException("the packet is too short: " + count + " more bytes expected at offset "
					+ _position + ", but only " + remaining() + " are left");
		}
	}
}
