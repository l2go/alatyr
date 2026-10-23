package com.l2jfree.gameserver.bridge.endpoint;

import java.util.function.Consumer;
import java.util.function.Supplier;

/** Registers on the reader, executes in FIFO, and retains the first response before publishing it. */
final class BridgeAttemptSession
{
	private final BridgeIntents intents = new BridgeIntents();
	private final Consumer<Runnable> submit;
	private final Consumer<byte[]> outbound;

	BridgeAttemptSession(Consumer<Runnable> submit, Consumer<byte[]> outbound)
	{
		this.submit = submit;
		this.outbound = outbound;
	}

	void ready()
	{
		intents.markReady();
	}

	BridgeIntents.Decision accept(long n, byte[] normalized, Supplier<byte[]> execution)
	{
		BridgeIntents.Decision decision = intents.offer(n, normalized);
		if (decision.kind == BridgeIntents.Kind.REPLAY)
		{
			byte[] result = intents.result(n);
			if (result != null)
				outbound.accept(result);
		}
		else if (decision.kind == BridgeIntents.Kind.ACCEPTED)
		{
			submit.accept(() ->
			{
				byte[] result = execution.get();
				intents.complete(n, result);
				outbound.accept(intents.result(n));
			});
		}
		return decision;
	}
}
