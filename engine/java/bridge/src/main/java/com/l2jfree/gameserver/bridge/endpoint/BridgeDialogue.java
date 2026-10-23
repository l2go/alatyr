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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

import com.l2jfree.gameserver.bridge.wire.Envelope;
import com.l2jfree.gameserver.bridge.wire.Schemas;

/**
 * The frame rules of one connection. It does not look up an account and it does not wait on the client queue.
 */
final class BridgeDialogue
{
	private final byte[] secret;
	private boolean bound;
	private boolean closed;
	
	BridgeDialogue(byte[] secret)
	{
		if (secret == null || secret.length != 32)
			throw new IllegalArgumentException("secret");
		
		this.secret = secret.clone();
	}
	
	BridgeReply accept(List<Envelope> records)
	{
		if (closed)
			return BridgeReply.of(List.of(), true);
		if (records == null || records.isEmpty())
			return fail(BridgeFrames.BYE_FRAME);
		
		Envelope first = records.get(0);
		if (!bound)
			return firstFrame(records, first);
		if (first.type() == Schemas.RESUME && records.size() == 1)
		{
			if (!MessageDigest.isEqual(secret, first.field(3).value().data()))
				return fail(BridgeFrames.BYE_AUTH);
			return BridgeReply.resume(first);
		}
		
		if (first.type() == Schemas.PING && records.size() == 1)
			return BridgeReply.of(List.of(BridgeFrames.pong(first.field(1).value().u())), false);
		if (records.stream().allMatch(record -> record.type() == Schemas.INTENT))
			return BridgeReply.of(List.of(), false);
		if (first.type() == Schemas.CLOSE && records.size() == 1)
		{
			closed = true;
			return BridgeReply.closeSession();
		}
		
		return fail(BridgeFrames.BYE_FRAME);
	}
	
	private BridgeReply firstFrame(List<Envelope> records, Envelope first)
	{
		if (records.size() != 1 || (first.type() != Schemas.OPEN && first.type() != Schemas.RESUME))
			return fail(BridgeFrames.BYE_FRAME);
		
		byte[] presented = first.type() == Schemas.OPEN ? first.field(4).value().data() : first.field(3).value().data();
		if (!MessageDigest.isEqual(secret, presented))
			return fail(BridgeFrames.BYE_AUTH);
		
		if (first.type() == Schemas.RESUME)
		{
			bound = true;
			return BridgeReply.resume(first);
		}
		
		String account = new String(first.field(5).value().data(), StandardCharsets.UTF_8);
		String character = new String(first.field(6).value().data(), StandardCharsets.UTF_8);
		bound = true;
		return BridgeReply.admission(account, character);
	}
	
	private BridgeReply fail(long reason)
	{
		closed = true;
		return BridgeReply.of(List.of(BridgeFrames.bye(reason)), true);
	}
}
