/*
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */
package com.l2jfree.gameserver.bridge.endpoint;

import com.l2jfree.gameserver.network.L2Client;
import com.l2jfree.gameserver.network.bridge.Attempt;
import com.l2jfree.gameserver.network.bridge.EffectLedger;
import com.l2jfree.gameserver.network.packets.L2ClientPacket;

/** Executes on the existing client FIFO; the reader must only enqueue this work. */
final class BridgeExecution
{
	private BridgeExecution()
	{
	}

	static Attempt run(L2Client client, L2ClientPacket packet)
	{
		return run(client, packet, attempt -> { });
	}

	static Attempt run(L2Client client, L2ClientPacket packet, java.util.function.Consumer<Attempt> continuation)
	{
		if (client == null || !client.isBridge() || client.getActiveChar() == null || packet == null)
			throw new IllegalArgumentException("execution");
		Attempt attempt = new Attempt(client.getActiveChar());
		packet.bindDirect(client);
		EffectLedger.open(attempt);
		try
		{
			attempt.markStarted();
			packet.run();
			if (packet.failed())
				attempt.markFailed();
			if (!attempt.isFailed())
				continuation.accept(attempt);
		}
		catch (RuntimeException | Error failure)
		{
			attempt.markFailed();
		}
		finally
		{
			EffectLedger.close();
		}
		return attempt;
	}
}
