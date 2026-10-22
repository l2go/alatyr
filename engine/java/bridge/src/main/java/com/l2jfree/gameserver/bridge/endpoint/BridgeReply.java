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

import com.l2jfree.gameserver.bridge.wire.Envelope;

/**
 * What one decoded frame asks the listener to do. Admission is scheduled, never run on the selector.
 */
final class BridgeReply
{
	final List<Envelope> records;
	final boolean close;
	final boolean admit;
	final String account;
	final String character;
	
	private BridgeReply(List<Envelope> records, boolean close, boolean admit, String account, String character)
	{
		this.records = records;
		this.close = close;
		this.admit = admit;
		this.account = account;
		this.character = character;
	}
	
	static BridgeReply of(List<Envelope> records, boolean close)
	{
		return new BridgeReply(records, close, false, null, null);
	}
	
	static BridgeReply admission(String account, String character)
	{
		return new BridgeReply(List.of(), false, true, account, character);
	}
}
