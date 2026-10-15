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

/**
 * A frame that breaks the framing rules: its length is shorter than the 2-byte header or longer than the configured
 * maximum. The connection that sent it cannot be resynchronized and is closed.
 */
public final class InvalidFrameException extends RuntimeException
{
	private static final long serialVersionUID = 4631845092772406163L;
	
	private final int _length;
	
	public InvalidFrameException(String message, int length)
	{
		super(message, null, false, false); // no stack trace: the cause is the bytes of a client
		
		_length = length;
	}
	
	/**
	 * @return the length that the frame header announced, header included
	 */
	public int getLength()
	{
		return _length;
	}
}
