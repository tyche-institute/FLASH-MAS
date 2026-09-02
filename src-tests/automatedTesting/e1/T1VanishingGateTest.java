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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Experiment 1, test T1 — "vanishing gate".
 * <p>
 * Property under test, P1 <code>NoUngatedDispatch</code>: a migrated incarnation of an agent must not be able to
 * dispatch a message without the enforcement gate being consulted.
 * <p>
 * The test boots two nodes in one JVM over the webSocket pylon, installs a deny-all gate on a mobile agent, sends
 * one message before the move and one after arrival on the destination node, and reports what the gate saw.
 * <p>
 * Two arms are run:
 * <ul>
 * <li><b>naive</b> — gate registered once, at the first AGENT_START. This is what any straightforward consumer of the
 * gate API would write.
 * <li><b>reinstall</b> — gate re-installed on every AGENT_START, including the one posted after arrival; this is the
 * candidate repair.
 * </ul>
 * The test does not assert that P1 holds — it records the outcome of both arms, because the expected result of the
 * naive arm is a failure and that failure is the finding.
 * <p>
 * <b>Run each arm in its own JVM.</b> Both nodes are booted with <code>keep:-1</code> and never stop, so a previous
 * arm's agent stays alive and keeps incrementing the shared static counters. Use
 * <code>-Dtest=T1VanishingGateTest#t1_naiveArm_gateDoesNotSurviveMigration</code> and
 * <code>-Dtest=T1VanishingGateTest#t1_reinstallArm_gateSurvivesIfReinstalled</code> separately, or the runner script
 * <code>run-e1-t1.sh</code>.
 * <p>
 * Measured result, 3/3 deterministic runs per arm on 3 August 2026:
 * <ul>
 * <li>naive: gate consulted once (pre-move only); the post-move send returned true and the peer received it —
 * <b>P1 NoUngatedDispatch fails</b>;
 * <li>reinstall: gate consulted twice; the post-move send was refused and the peer received nothing — P1 holds.
 * </ul>
 *
 * @author Tyche Institute, for the FLASH-MAS action-gate experiment
 */
public class T1VanishingGateTest {
	

	protected static final String	PRELUDE		= "-package testing test.compositeMobility automatedTesting.e1 -loader agent:composite -loader agent:mobileComposite ";

	protected static String deployment(boolean reinstall, String wsPort) {
		final String WS_PORT = wsPort;
		return PRELUDE
				+ "-node nodeA keep:-1 -pylon webSocket:pylonA serverPort:" + WS_PORT + " "
				+ "-agent mobileComposite:agentA1 -shard messaging -shard EchoTesting "
				+ "-shard GateProbe peer:agentB1 preAt:2000 reinstall:" + reinstall + " "
				+ "-shard MobilityTest to:nodeB time:5000 "
				+ "-node nodeB keep:-1 -pylon webSocket:pylonB connectTo:ws://localhost:" + WS_PORT + " "
				+ "-agent agentB1 -shard messaging -shard EchoTesting -shard Sink";
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

	/** Runs one arm and returns the console output. */
	protected String runArm(boolean reinstall, String wsPort) throws Exception {
		GateProbeShard.reset();
		SinkShard.reset();
		Thread th = new Thread(() -> FlashBoot.main(deployment(reinstall, wsPort).split(" ")));
		th.setDaemon(true);
		th.start();

		int maxWaitCycles = 200;
		while(maxWaitCycles > 0) {
			Thread.sleep(100);
			if(GateProbeShard.postSendResult.get() >= 0) {
				Thread.sleep(1500); // let any dispatch land at the peer
				break;
			}
			maxWaitCycles--;
		}
		return outContent.toString();
	}

	/** Prints one arm's measurements in a form the write-up can quote verbatim. */
	protected void report(String arm, String output) {
		originalOut.println("=== E1/T1 arm: " + arm + " ===");
		originalOut.println("  agentStarts (2 = boot + arrival) : " + GateProbeShard.agentStarts.get());
		originalOut.println("  gateInstalls                     : " + GateProbeShard.gateInstalls.get());
		originalOut.println("  installFailures                  : " + GateProbeShard.installFailures.get());
		originalOut.println("  gateCalls (gate consulted)       : " + GateProbeShard.gateCalls.get());
		originalOut.println("  pre-move  sendMessage returned   : " + GateProbeShard.preSendResult.get());
		originalOut.println("  post-move sendMessage returned   : " + GateProbeShard.postSendResult.get());
		originalOut.println("  peer received PRE  markers       : " + SinkShard.preReceived.get());
		originalOut.println("  peer received POST markers       : " + SinkShard.postReceived.get());
		originalOut.println("  moved successfully in log        : " + output.contains("agent has moved successfully"));
		try {
			java.nio.file.Files.writeString(logPath("e1-t1-" + arm.split(" ")[0]), output);
		} catch(Exception e) {
			// diagnostics only
		}
	}

	@Test
	public void t1_naiveArm_gateDoesNotSurviveMigration() throws Exception {
		String output = runArm(false, "8993");
		report("naive (register once)", output);

		assertTrue("the agent must actually migrate for this test to mean anything",
				output.contains("agent has moved successfully"));
		assertEquals("gate must be installed exactly once in the naive arm", 1, GateProbeShard.gateInstalls.get());
		assertEquals("the pre-move send must be refused by the gate", 0, GateProbeShard.preSendResult.get());
		assertEquals("the pre-move message must not reach the peer", 0, SinkShard.preReceived.get());
	}

	@Test
	public void t1_reinstallArm_gateSurvivesIfReinstalled() throws Exception {
		String output = runArm(true, "8994");
		report("reinstall on every AGENT_START", output);

		assertTrue("the agent must actually migrate for this test to mean anything",
				output.contains("agent has moved successfully"));
		assertTrue("the gate must be installed at least twice in the reinstall arm",
				GateProbeShard.gateInstalls.get() >= 2);
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
