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
package com.l2jfree.gameserver.bridge.wire;

import java.util.List;

/**
 * One decoded envelope. {@code encodedBytes} and {@code unsupportedHash} are receive-side metadata and are
 * never written back.
 */
public final class Envelope
{
	private final long type;
	private final List<Field> fields;
	private final boolean unsupported;
	private final int encodedBytes;
	private final byte[] unsupportedHash;
	
	Envelope(long type, List<Field> fields, boolean unsupported, int encodedBytes, byte[] unsupportedHash)
	{
		this.type = type;
		this.fields = fields;
		this.unsupported = unsupported;
		this.encodedBytes = encodedBytes;
		this.unsupportedHash = unsupportedHash;
	}
	
	/**
	 * A record to encode. The codec checks the registry and the profile when it writes the record.
	 * 
	 * @param type the envelope type
	 * @param fields the fields, in number order
	 * @return the envelope
	 */
	public static Envelope of(long type, List<Field> fields)
	{
		if (fields == null)
			throw new IllegalArgumentException("fields");
		
		return new Envelope(type, List.copyOf(fields), false, 0, null);
	}
	
	/**
	 * @return the envelope type
	 */
	public long type()
	{
		return type;
	}
	
	/**
	 * @return the fields in the order they were read, or the order supplied to {@link Codec#encode}
	 */
	public List<Field> fields()
	{
		return fields;
	}
	
	/**
	 * @return whether an essential extension was skipped
	 */
	public boolean unsupported()
	{
		return unsupported;
	}
	
	/**
	 * @return how many frame bytes this envelope occupied, including skipped fields
	 */
	public int encodedBytes()
	{
		return encodedBytes;
	}
	
	/**
	 * @return the opaque digest of an unsupported request, or null
	 */
	public byte[] unsupportedHash()
	{
		return unsupportedHash;
	}
	
	/**
	 * @param number the field number
	 * @return the field, or null when it is absent
	 */
	public Field field(int number)
	{
		return Field.find(fields, number);
	}
}
