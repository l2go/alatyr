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
package com.l2jfree.network;

import java.nio.ByteBuffer;

/**
 * @author KenM
 */
/**
 * Creates the packet of an opcode. It is called on the I/O thread of the connection, after the frame was decrypted,
 * with the buffer positioned right behind the opcode.
 */
public interface PacketHandler<T extends Connection<T, RP, SP>, RP extends ReceivablePacket<T, RP, SP>, SP extends SendablePacket<T, RP, SP>>
{
	/**
	 * @param buf the decrypted payload of the frame, little-endian by default, positioned behind the opcode
	 * @param client the connection the frame came from
	 * @param opcode the first byte of the payload, unsigned
	 * @return the packet to read, or null when the opcode is unknown (the packet is dropped)
	 */
	public RP handlePacket(ByteBuffer buf, T client, int opcode);
}
