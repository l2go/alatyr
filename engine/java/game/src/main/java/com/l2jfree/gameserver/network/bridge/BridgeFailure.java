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
package com.l2jfree.gameserver.network.bridge;

/**
 * Why a bridge client is being disconnected. The numbers are the transport close reasons: 0 finished, 7 internal.
 */
public final class BridgeFailure
{
	private static final BridgeFailure NORMAL = new BridgeFailure(0);
	private static final BridgeFailure INTERNAL = new BridgeFailure(7);
	
	private final int _byeReason;
	
	private BridgeFailure(int byeReason)
	{
		_byeReason = byeReason;
	}
	
	public static BridgeFailure normal()
	{
		return NORMAL;
	}
	
	public static BridgeFailure internal()
	{
		return INTERNAL;
	}
	
	public int byeReason()
	{
		return _byeReason;
	}
}
