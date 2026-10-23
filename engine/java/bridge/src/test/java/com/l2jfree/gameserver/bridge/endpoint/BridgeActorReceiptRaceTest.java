/*
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version. This program is distributed without any warranty.
 * See <http://www.gnu.org/licenses/> for details.
 */
package com.l2jfree.gameserver.bridge.endpoint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import com.l2jfree.gameserver.gameobjects.L2Creature;
import com.l2jfree.gameserver.gameobjects.L2Object;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.gameobjects.base.SubClass;
import com.l2jfree.gameserver.gameobjects.stat.PlayerStat;
import com.l2jfree.gameserver.network.bridge.Attempt;
import com.l2jfree.gameserver.network.bridge.BridgeEffect;
import com.l2jfree.gameserver.network.bridge.EffectLedger;

/** Uses real setters and latches, not hand-written receipts for competing mutations. */
class BridgeActorReceiptRaceTest
{
	@Test
	void spWriterWaitsForTheStorageLeafBeforeCapturingItsReceipt() throws Exception
	{
		L2Player player = BridgeTestPlayers.player();
		when(player.isBridgeSession()).thenReturn(true);
		PlayerStat stat = new PlayerStat(player);
		stat.setSp(100);
		var field = com.l2jfree.gameserver.gameobjects.stat.CreatureStat.class.getDeclaredField("_bridgeStatLock");
		field.setAccessible(true);
		Object leaf = field.get(stat);
		CountDownLatch beforeLock = new CountDownLatch(1);
		AtomicReference<Throwable> failure = new AtomicReference<>();
		AtomicReference<Attempt> recorded = new AtomicReference<>();
		Thread main = Thread.currentThread();
		when(player.isBridgeSession()).thenAnswer(call ->
		{
			if (Thread.currentThread() != main) beforeLock.countDown();
			return true;
		});
		Thread own = worker(failure, () ->
		{
			Attempt attempt = new Attempt(player);
			EffectLedger.open(attempt);
			try { stat.setSp(90); recorded.set(attempt); }
			finally { EffectLedger.close(); }
		});
		try
		{
			synchronized (leaf)
			{
				own.start();
				await(beforeLock);
				long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
				while (own.getState() != Thread.State.BLOCKED && own.isAlive() && System.nanoTime() < deadline)
					Thread.onSpinWait();
				assertThat(own.getState()).isEqualTo(Thread.State.BLOCKED);
				stat.setSp(110);
			}
		}
		finally { own.join(5000); }
		assertThat(own.isAlive()).isFalse();
		assertThat(failure.get()).isNull();
		assertThat(recorded.get().effects()).containsExactly(BridgeEffect.sp(-20, 110, 90));
	}

	@Test
	void competingSpWriteCannotSupplyTheExpectedLearningDelta() throws Exception
	{
		for (boolean subclass : new boolean[] { false, true })
		{
			L2Player player = BridgeTestPlayers.player();
			when(player.isBridgeSession()).thenReturn(true);
			when(player.isSubClassActive()).thenReturn(subclass);
			SubClass storage = new SubClass();
			when(player.getSubClasses()).thenReturn(new java.util.HashMap<>(java.util.Map.of(0, storage)));
			PlayerStat stat = new PlayerStat(player);
			stat.setSp(100);
			CountDownLatch staleValueCalculated = new CountDownLatch(1);
			CountDownLatch foreignWritten = new CountDownLatch(1);
			AtomicReference<Throwable> failure = new AtomicReference<>();
			Thread foreign = worker(failure, () ->
			{
				await(staleValueCalculated);
				if (subclass) storage.setSp(110); else stat.setSp(110);
				foreignWritten.countDown();
			});
			Attempt attempt = new Attempt(player);
			EffectLedger.open(attempt);
			try
			{
				int staleValue = stat.getSp() - 10;
				foreign.start();
				staleValueCalculated.countDown();
				await(foreignWritten);
				stat.setSp(staleValue);
				assertThat(attempt.effects()).containsExactly(BridgeEffect.sp(-20, 110, 90));
				EffectLedger.expectLearningCost(player, 0, 10, 0, 0);
				EffectLedger.record(player, BridgeEffect.skill(12, 0, 1));
				assertThat(BridgeOutcome.learn(attempt, 12, 1)).isEqualTo(BridgeOutcome.UNKNOWN);
			}
			finally { EffectLedger.close(); foreign.join(5000); }
			assertThat(foreign.isAlive()).isFalse();
			assertThat(failure.get()).isNull();
		}
	}

	@Test
	void directSubclassWriteWithoutOwnerInvalidatesTheAttempt()
	{
		Attempt attempt = new Attempt(BridgeTestPlayers.player());
		EffectLedger.open(attempt);
		try
		{
			new SubClass().setSp(90);
			assertThat(attempt.isLedgerIncomplete()).isTrue();
			assertThat(BridgeOutcome.learn(attempt, 12, 1)).isEqualTo(BridgeOutcome.UNKNOWN);
		}
		finally { EffectLedger.close(); }
	}

	@Test
	void competingTargetSelectionCannotCreateANewSelectionReceipt() throws Exception
	{
		L2Player player = BridgeTestPlayers.player();
		var npc = mock(com.l2jfree.gameserver.gameobjects.L2Npc.class);
		when(npc.getObjectId()).thenReturn(70);
		when(player.getKnownList()).thenReturn(mock(com.l2jfree.gameserver.gameobjects.knownlist.PlayerKnownList.class));
		when(npc.getKnownList()).thenReturn(mock(com.l2jfree.gameserver.gameobjects.knownlist.NpcKnownList.class));
		var lock = L2Creature.class.getDeclaredField("_bridgeTargetLock");
		lock.setAccessible(true);
		lock.set(player, new Object()); // Mockito bypasses the actor constructor.
		var refresh = L2Creature.class.getDeclaredMethod("refreshTarget", L2Object.class);
		refresh.setAccessible(true);
		doCallRealMethod().when(player);
		refresh.invoke(player, npc);
		CountDownLatch selected = new CountDownLatch(1);
		CountDownLatch ownReached = new CountDownLatch(1);
		AtomicReference<Throwable> failure = new AtomicReference<>();
		Thread ownThread = Thread.currentThread();
		when(player.isBridgeSession()).thenAnswer(call ->
		{
			if (Thread.currentThread() == ownThread) { ownReached.countDown(); await(selected); }
			return true;
		});
		Thread foreign = worker(failure, () ->
		{
			await(ownReached);
			refresh.invoke(player, npc);
			selected.countDown();
		});
		Attempt attempt = new Attempt(player);
		EffectLedger.open(attempt);
		try
		{
			foreign.start();
			refresh.invoke(player, npc);
			assertThat(attempt.effects()).isEmpty();
			assertThat(BridgeOutcome.match(attempt, 2, 0, 70, 0)).isEqualTo(BridgeOutcome.UNKNOWN);
		}
		finally { EffectLedger.close(); foreign.join(5000); }
		assertThat(foreign.isAlive()).isFalse();
		assertThat(failure.get()).isNull();
	}

	private interface Work { void run() throws Exception; }
	private static Thread worker(AtomicReference<Throwable> failure, Work work)
	{
		return new Thread(() -> { try { work.run(); } catch (Throwable error) { failure.set(error); } });
	}
	private static void await(CountDownLatch latch) throws InterruptedException
	{
		assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
	}
}
