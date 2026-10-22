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
package com.l2jfree.gameserver.bridge.endpoint;

import java.util.List;

import com.l2jfree.gameserver.bridge.wire.Codec;
import com.l2jfree.gameserver.bridge.wire.Envelope;
import com.l2jfree.gameserver.bridge.wire.Field;
import com.l2jfree.gameserver.bridge.wire.Schemas;

/**
 * Records the listener is allowed to send. Encoding goes through the bridge codec, so an illegal record fails here.
 */
final class BridgeFrames
{
	static final long BYE_NORMAL = 0;
	static final long BYE_UNSUPPORTED = 1;
	static final long BYE_FRAME = 2;
	static final long BYE_FULL = 4;
	static final long BYE_AUTH = 5;
	static final long BYE_INTERNAL = 7;
	
	static final long OPENED_REFUSED = 2;
	static final long REASON_ALREADY_ONLINE = 11;
	static final long REASON_NO_CHARACTER = 12;
	static final long REASON_CLOSED = 13;
	static final long REASON_NOT_ALLOWED = 16;
	static final long REASON_BACKEND = 20;
	
	private static final byte[] WORLD = new byte[] { 'w', 'o', 'r', 'l', 'd' };
	
	private BridgeFrames()
	{
	}
	
	static Envelope bye(long reason)
	{
		return Envelope.of(Schemas.BYE, List.of(Field.u(1, true, reason)));
	}
	
	static Envelope pong(long nonce)
	{
		return Envelope.of(Schemas.PONG, List.of(Field.u(1, true, nonce)));
	}
	
	static Envelope checkpoint(long phase, long epoch, boolean ready)
	{
		return Envelope.of(Schemas.CHECKPOINT, List.of(Field.u(1, true, phase), Field.u(2, true, epoch),
				Field.u(3, true, ready ? 1 : 0)));
	}
	
	static Envelope openedRefused(long reason)
	{
		return Envelope.of(Schemas.OPENED, List.of(Field.u(1, true, OPENED_REFUSED), Field.u(5, true, reason)));
	}
	
	/**
	 * Accepted opening. Ready stays false until a later slice has a scene to end on.
	 */
	static List<Envelope> accepted(long session, long epoch)
	{
		Envelope opened = Envelope.of(Schemas.OPENED, List.of(Field.u(1, true, 1), Field.u(2, true, session),
				Field.data(3, true, WORLD.clone()), Field.u(4, true, epoch), Field.u(5, true, 0), Codec.openedLimits(),
				Field.u(7, true, 1), Field.data(8, true, Codec.profileName())));
		return List.of(checkpoint(Schemas.BEGIN, epoch, false), checkpoint(Schemas.END, epoch, false), opened);
	}
}
