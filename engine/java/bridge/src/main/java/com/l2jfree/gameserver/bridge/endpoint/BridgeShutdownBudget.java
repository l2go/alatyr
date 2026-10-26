/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.l2jfree.gameserver.bridge.endpoint;

/** First shutdown owns the only total budget. No caller can renew it. */
final class BridgeShutdownBudget
{
	private boolean claimed;
	private long deadline;

	synchronized boolean claim(long now, long budgetNs)
	{
		if (claimed)
			return false;
		claimed = true;
		deadline = now + budgetNs;
		return true;
	}

	synchronized long deadline()
	{
		return deadline;
	}

	static boolean canStop(long now, long deadline, boolean outputDrained)
	{
		return outputDrained || now >= deadline;
	}
}
