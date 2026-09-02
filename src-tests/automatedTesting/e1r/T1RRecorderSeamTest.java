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
package automatedTesting.e1r;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import net.xqhs.flash.FlashBoot;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Experiment 1R, test T1R - enforcement placed where the recorder already is.
 * <p>
 * Property under test, P1 <code>NoUngatedDispatch</code>: a migrated incarnation must not dispatch without the
 * enforcement point being consulted.
 * <p>
 * This is T1's deployment with one change. In T1 the enforcement point was a gate the agent installed on its own
 * messaging shard, held in a <code>transient</code> field; it did not survive serialization, so the naive arm's
 * post-move send went out unchecked. Here the enforcement point is an {@link net.xqhs.flash.core.recorder.ActionPolicy}
 * named by the host process in <code>flash.policy.class</code> and reached through
 * {@link net.xqhs.flash.core.recorder.PolicyService}, the same static-facade construction the recorder already uses.
 * The agent installs nothing and carries nothing.
 * <p>
 * The arm to compare against is T1's <b>naive</b> arm, because the agent here is as naive as an agent can be: it does
 * no enforcement work at all. If the policy is still consulted after the move, the difference is placement and
 * nothing else.
 * <p>
 * <b>Scope of what this shows.</b> Both hosts run in one JVM, so they share one static facade. What is demonstrated
 * is that enforcement is consulted after relocation with <b>no agent-side re-installation</b>, which is precisely
 * what T1's naive arm failed. That a second process builds its own policy from its own configuration follows from the
 * construction rather than from this test, and the write-up says so.
 *
 * @author Tyche Institute, for the FLASH-MAS recorder-seam experiment
 */
public class T1RRecorderSeamTest {
	
	protected static final String PRELUDE = "-package testing test.compositeMobility automatedTesting.e1r -loader agent:composite -loader agent:mobileComposite ";
	
	protected static String deployment(String wsPort) {
		return PRELUDE + "-node nodeA keep:-1 -pylon webSocket:pylonA serverPort:" + wsPort + " "
				+ "-agent mobileComposite:agentA1 -shard messaging -shard EchoTesting "
				+ "-shard GateProbe peer:agentB1 preAt:2000 "
				+ "-shard MobilityTest to:nodeB time:5000 "
				+ "-node nodeB keep:-1 -pylon webSocket:pylonB connectTo:ws://localhost:" + wsPort + " "
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
	
	@Test
	public void t1r_hostOwnedPolicySurvivesMigration() throws Exception {
		GateProbeShard.reset();
		SinkShard.reset();
		CountingDenyAllPolicy.reset();
		
		Thread th = new Thread(() -> FlashBoot.main(deployment("8997").split(" ")));
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
		String output = outContent.toString();
		
		originalOut.println("=== E1R/T1R arm: host-owned policy, agent installs nothing ===");
		originalOut.println("  agentStarts (2 = boot + arrival) : " + GateProbeShard.agentStarts.get());
		originalOut.println("  policy configured in process     : "
				+ net.xqhs.flash.core.recorder.PolicyService.isConfigured());
		originalOut.println("  policyCalls (about the agent)    : " + CountingDenyAllPolicy.policyCalls.get());
		originalOut.println("  totalCalls (about any entity)    : " + CountingDenyAllPolicy.totalCalls.get());
		originalOut.println("  entities the policy saw          : " + CountingDenyAllPolicy.entitiesSeen);
		originalOut.println("  pre-move  sendMessage returned   : " + GateProbeShard.preSendResult.get());
		originalOut.println("  post-move sendMessage returned   : " + GateProbeShard.postSendResult.get());
		originalOut.println("  peer received PRE  markers       : " + SinkShard.preReceived.get());
		originalOut.println("  peer received POST markers       : " + SinkShard.postReceived.get());
		originalOut.println("  moved successfully in log        : " + output.contains("agent has moved successfully"));
		
		assertTrue("the agent must actually migrate for this test to mean anything",
				output.contains("agent has moved successfully"));
		assertTrue("the deployment must have a policy configured; check flash.policy.class",
				net.xqhs.flash.core.recorder.PolicyService.isConfigured());
		assertEquals("the agent must have started twice: boot and arrival", 2, GateProbeShard.agentStarts.get());
		assertTrue("the policy must be consulted on both sides of the move, with no agent-side installation",
				CountingDenyAllPolicy.policyCalls.get() >= 2);
		assertEquals("the post-move send must be refused", 0, GateProbeShard.postSendResult.get());
		assertEquals("no post-move message may reach the peer", 0, SinkShard.postReceived.get());
	}
}
