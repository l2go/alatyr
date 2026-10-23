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
