/*
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */
package com.l2jfree.gameserver.bridge.endpoint;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

/** Structural checks complement the controlled transition tests; they do not replace race assertions. */
class BridgeTransportAutomatonContractTest
{
	private static String source() throws Exception
	{
		return Files.readString(Path.of("src/main/java/com/l2jfree/gameserver/bridge/endpoint/BridgeLoopback.java"));
	}

	private static String method(String source, String signature)
	{
		int start = source.indexOf(signature);
		assertThat(start).as(signature).isGreaterThanOrEqualTo(0);
		int brace = source.indexOf('{', start);
		int depth = 1;
		int end = brace + 1;
		while (depth != 0)
		{
			char next = source.charAt(end++);
			if (next == '{') depth++;
			if (next == '}') depth--;
		}
		return source.substring(brace, end);
	}

	@Test
	void sinkPublicationNeverExpiresOrEntersSession()
	{
		String source;
		try { source = source(); }
		catch (Exception failure) { throw new AssertionError(failure); }
		assertThat(method(source, "void fromSink(")).doesNotContain("expire(", "synchronized (owner)", "synchronized (this)");
		assertThat(method(source, "void enqueue(")).doesNotContain("expire(");
		assertThat(method(source, "void publishCloseResult(")).doesNotContain("synchronized", "completeCloseResult(", "requestBridgeDisconnect(");
	}

	@Test
	void pendingCancellationAndAdoptionUseTheSameOuterSessionMonitor() throws Exception
	{
		String source = source();
		assertThat(method(source, "void finishTerminal(")).contains("Thread.holdsLock(transport)", "cancelRecords", "synchronized (owner)");
		assertThat(method(source, "void expire(Transport transport, long now)")).contains("synchronized (owner)");
		String resume = method(source, "void resume(Transport replacement");
		assertThat(resume.indexOf("synchronized (this)")).isLessThan(resume.indexOf("synchronized (replacement)"));
		assertThat(resume).contains("cancelRecords", "slowRequested", "afterResumeGuard", "recovery.resume(");
		assertThat(method(resume, "synchronized (replacement)")).doesNotContain("recovery.", "attempts.", "submit(");
		assertThat(method(source, "boolean notePendingResume(")).contains("synchronized (this)", "cancelRecords", "return false");
	}

	@Test
	void producersCannotResetOrDebitTheBorrowedWriter() throws Exception
	{
		String source = source();
		for (String signature : new String[] { "void publishCloseResult(", "void retireOtherTransports(", "void claimTerminal(" })
			assertThat(method(source, signature)).doesNotContain(".writing =", ".writingAt =", ".held =", ".queued.clear(");
		assertThat(method(source, "static int flush(")).contains("transport.writing != writing", "transport.held -= n", "writeDeadline");
		assertThat(method(source, "static void applyOutputFlags(")).doesNotContain("Math.max");
	}

	@Test
	void theSelectorOwnsDeadlineChecksAndPhysicalShutdownDisposal() throws Exception
	{
		String source = source();
		assertThat(method(source, "List<Envelope> takeFrame(")).contains("synchronized (transport) { transport.readDeadline = 0; }");
		assertThat(method(source, "long nextWaitMs(")).contains("writeDeadline", "readDeadline", "shutdownDeadline");
		assertThat(method(source, "void run()")).contains("finally", "drop(key)");
		assertThat(method(source, "void close(long budgetMs)")).contains("saveEnd", "thread.join", "unfinished saves");
	}

	@Test
	void theEventModelIsWiredIntoSelectorCaptureAndShutdown() throws Exception
	{
		String source = source();
		String flush = method(source, "void flushKeyThrows(");
		assertThat(flush.indexOf("owner.serviceCloseResult()")).isLessThan(flush.indexOf("applyOutputFlags(transport)"));
		assertThat(flush).contains("outputDrained(transport)");
		assertThat(method(source, "void serviceCloseResult(long now)")).contains("closeResultExpired(saveDeadline, now)");
		assertThat(method(source, "void completeCloseResult(")).contains("if (closeOutcome != null)", "List.copyOf(records)");
		String capture = method(source, "void captureCheckpointChecked(");
		assertThat(capture.indexOf("releaseAfterCheckpointCount =")).isLessThan(capture.indexOf("current.checkpoint = new"));
		assertThat(method(source, "void close(long budgetMs)")).contains("shutdownBudget.claim(", "shutdownBudget.deadline()", "BridgeShutdownBudget.canStop(");
		assertThat(method(source, "boolean shutdownOutputDrained()")).contains("ownerOf(source)", "synchronized (owner)", "owner.saveNotification.get()", "owner.sessionTransports");
		assertThat(method(source, "boolean transportOutputDrained(")).contains("synchronized (source)", "source.cancelRecords", "outputDrained(source)");
	}
}
