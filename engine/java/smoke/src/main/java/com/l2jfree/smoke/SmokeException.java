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

/**
 * A step of the smoke test failed: the server sent something unexpected, refused a request,
 * closed the connection, or did not answer in time.
 */
final class SmokeException extends Exception
{
	private static final long serialVersionUID = 1L;

	enum Kind
	{
		/** The server sent a wrong packet or the client could not build or read a packet. */
		FAILURE,
		/** No answer within the time that was allowed. */
		TIMEOUT,
		/** The connection is closed or broken. */
		CLOSED,
		/** The server answered with a failure packet that carries a reason. */
		REFUSED;
	}

	private final Kind _kind;
	private final int _reason;

	SmokeException(String message)
	{
		this(Kind.FAILURE, message, null, -1);
	}

	SmokeException(String message, Throwable cause)
	{
		this(Kind.FAILURE, message, cause, -1);
	}

	SmokeException(Kind kind, String message)
	{
		this(kind, message, null, -1);
	}

	SmokeException(Kind kind, String message, Throwable cause)
	{
		this(kind, message, cause, -1);
	}

	private SmokeException(Kind kind, String message, Throwable cause, int reason)
	{
		super(message, cause);
		_kind = kind;
		_reason = reason;
	}

	static SmokeException refused(String message, int reason)
	{
		return new SmokeException(Kind.REFUSED, message, null, reason);
	}

	Kind kind()
	{
		return _kind;
	}

	/** @return the reason code of a failure packet, or -1 */
	int reason()
	{
		return _reason;
	}
}
