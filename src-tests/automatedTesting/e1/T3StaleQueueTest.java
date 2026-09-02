/*******************************************************************************
 * Copyright (C) 2026 Anton Sokolov.
 *
 * This file is part of Flash-MAS. The CONTRIBUTORS.md file lists people who have been previously involved with this project.
 *
 * Flash-MAS is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or any later version.
 *
 * Flash-MAS is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with Flash-MAS.  If not, see <http://www.gnu.org/licenses/>.
 ******************************************************************************/
package automatedTesting.e1;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import net.xqhs.flash.FlashBoot;

import static org.junit.Assert.assertTrue;

/**
 * Experiment 1, test T3 — "stale queue".
 * <p>
 * Property under test, P3 {@code NoStaleQueuedDispatch}: an action queued before a migration must be re-authorized on
 * arrival against the CURRENT authority, not dispatched under the authority that held when it was enqueued.
 * <p>
 * The agent performs a pre-move action (authority intact) and a queued action executed after arrival. While the agent
 * is in transit, the test revokes the authority ({@code StaleQueueShard.authorityRevoked = true}). Two arms:
 * <ul>
 * <li><b>naive</b> — gate registered once; per T1 it is lost on arrival, so the queued action dispatches UNGATED.
 * <li><b>reinstall</b> — gate re-installed on arrival; it reads the live (revoked) policy and denies the queued action.
 * </ul>
 * The finding: P3 is not threatened by stale decision caching — the seam authorizes at dispatch time — but by gate
 * survival (T1). Re-authorization works iff the gate is reconstructed on arrival.
 *
 * @author Tyche Institute, for the FLASH-MAS action-gate experiment
 */
public class T3StaleQueueTest {
	protected static String deployment(boolean reinstall, String wsPort) {
		final String P = wsPort;
		return "-package testing test.compositeMobility automatedTesting.e1 "
				+ "-loader agent:composite -loader agent:mobileComposite "
				+ "-node nodeA keep:-1 -pylon webSocket:pylonA serverPort:" + P + " "
				+ "-agent mobileComposite:agentA1 -shard messaging "
				+ "-shard StaleQueue peer:agentPeer reinstall:" + reinstall + " "
				+ "-shard MobilityTest to:nodeB time:4000 "
				+ "-agent agentPeer -shard messaging -shard StaleQueueSink "
				+ "-node nodeB keep:-1 -pylon webSocket:pylonB connectTo:ws://localhost:" + P;
	}

	private final ByteArrayOutputStream	outContent	= new ByteArrayOutputStream();
	private final PrintStream			originalOut	= System.out;

	@Before
	public void setUpStreams() {
		System.setOut(new PrintStream(outContent));
	}

	@After
	public void restoreStreams() {
		System.setOut(originalOut);
	}

	protected String runArm(boolean reinstall, String wsPort) throws Exception {
		StaleQueueShard.reset();
		Thread th = new Thread(() -> FlashBoot.main(deployment(reinstall, wsPort).split(" ")));
		th.setDaemon(true);
		th.start();

		// revoke authority mid-flight: after the pre-move action, during/after the move (time:4000)
		for(int i = 0; i < 60 && StaleQueueShard.agentStarts.get() < 1; i++)
			Thread.sleep(100);
		Thread.sleep(3000); // let the pre-move action happen and the move begin
		StaleQueueShard.authorityRevoked = true;

		// wait for the queued post-arrival action to resolve
		for(int i = 0; i < 100 && StaleQueueShard.queuedSendResult.get() < 0; i++)
			Thread.sleep(100);
		Thread.sleep(1500);
		return outContent.toString();
	}

	protected void report(String arm, String out) {
		originalOut.println("=== E1/T3 arm: " + arm + " ===");
		originalOut.println("  agentStarts (2 = boot + arrival) : " + StaleQueueShard.agentStarts.get());
		originalOut.println("  authorityRevoked at dispatch     : " + StaleQueueShard.authorityRevoked);
		originalOut.println("  gate consultations               : " + StaleQueueShard.gateCalls.get());
		originalOut.println("  queued action sendMessage returned: " + StaleQueueShard.queuedSendResult.get());
		originalOut.println("  queued action received by peer   : " + StaleQueueShard.queuedReceived.get());
		originalOut.println("  moved successfully               : " + out.contains("agent has moved successfully"));
		try {
			java.nio.file.Files.writeString(logPath("e1-t3-" + arm.split(" ")[0]), out);
		} catch(Exception e) {
			// diagnostics only
		}
	}

	@Test
	public void t3_naiveArm_queuedActionDispatchesUngated() throws Exception {
		String out = runArm(false, "8989");
		report("naive (register once)", out);
		assertTrue("the agent must migrate", out.contains("agent has moved successfully"));
		// per T1 the gate is gone, so the queued action dispatches despite revoked authority
		assertTrue("queued action must reach the peer ungated in the naive arm",
				StaleQueueShard.queuedReceived.get() >= 1);
	}

	@Test
	public void t3_reinstallArm_queuedActionReauthorizedAndDenied() throws Exception {
		String out = runArm(true, "8988");
		report("reinstall on every AGENT_START", out);
		assertTrue("the agent must migrate", out.contains("agent has moved successfully"));
		// gate survives and reads the revoked policy -> denied
		assertTrue("queued action must be denied against current authority",
				StaleQueueShard.queuedSendResult.get() == 0 && StaleQueueShard.queuedReceived.get() == 0);
	}

	/**
	 * Where a run's console capture is written, for diagnostics only. Under <code>target/</code> so a clone of this
	 * repository writes inside itself and needs no directory that happens to exist on the author's machine.
	 */
	protected static java.nio.file.Path logPath(String name) throws java.io.IOException {
		java.nio.file.Path dir = java.nio.file.Path.of("target", "e1-logs");
		java.nio.file.Files.createDirectories(dir);
		return dir.resolve(name + ".log");
	}

}
