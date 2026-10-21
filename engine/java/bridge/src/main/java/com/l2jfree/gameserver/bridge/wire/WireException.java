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

/**
 * A frame was rejected. No record from that frame is applied.
 */
public final class WireException extends RuntimeException
{
	/**
	 * Why the frame was rejected.
	 */
	public enum Kind
	{
		/** The frame is not well-formed for its schema. */
		FRAME,
		/** An essential meaning is not in this profile. */
		UNSUPPORTED,
		/** Limits or a schema cannot be used. */
		CONFIG
	}
	
	private final Kind kind;
	
	WireException(Kind kind)
	{
		super(kind.name());
		this.kind = kind;
	}
	
	/**
	 * @return the rejection class
	 */
	public Kind kind()
	{
		return kind;
	}
}
