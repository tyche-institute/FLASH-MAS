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
import java.util.Arrays;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import net.xqhs.flash.core.deployment.Deployment;
import net.xqhs.flash.core.node.Node;

import static org.junit.Assert.assertTrue;

/**
 * Experiment 1, test T2 — "double activation".
 * <p>
 * Property under test, P2 {@code AtMostOneActiveEpoch}: at most one incarnation of a migrated agent may act under one
 * identity at a time.
 * <p>
 * A mobile agent on node A migrates to node B (real transport). The source node — a {@link CapturingNode} — records
 * the exact serialized payload it sent. The test then delivers that same payload to a third node, C, via
 * {@link CapturingNode#deliverAgentBlob(String)}, which is a copy of the framework's own {@code receive_agent}
 * activation path. Both incarnations then send to a peer, each tagged with an object-unique incarnation id. The peer
 * counts distinct tags.
 * <p>
 * If two post-move incarnations both dispatch, P2 fails — which is expected here, because FLASH-MAS has no epoch,
 * lease or single-activation notion and {@code Node.registerEntity} appends an agent with no duplicate check.
 *
 * @author Tyche Institute, for the FLASH-MAS action-gate experiment
 */
public class T2DoubleActivationTest {
	protected static final String	WS_PORT	= "8991";

	protected static String deployment() {
		final String P = WS_PORT;
		return "-package testing test.compositeMobility automatedTesting.e1 "
				+ "-loader agent:composite -loader agent:mobileComposite "
				// node A: server + the mobile agent + the peer that both incarnations will message
				+ "-node nodeA classpath:automatedTesting.e1.CapturingNode keep:-1 "
				+ "-pylon webSocket:pylonA serverPort:" + P + " "
				+ "-agent mobileComposite:agentA1 -shard messaging "
				+ "-shard DoubleActivation peer:agentPeer -shard MobilityTest to:nodeB time:4000 "
				+ "-agent agentPeer -shard messaging -shard DoubleActivationSink "
				// node B: destination of the real move
				+ "-node nodeB classpath:automatedTesting.e1.CapturingNode keep:-1 "
				+ "-pylon webSocket:pylonB connectTo:ws://localhost:" + P + " "
				// node C: receives the replayed payload
				+ "-node nodeC classpath:automatedTesting.e1.CapturingNode keep:-1 "
				+ "-pylon webSocket:pylonC connectTo:ws://localhost:" + P;
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
	public void t2_oneBlob_twoNodes_bothDispatch() throws Exception {
		DoubleActivationShard.reset();
		DoubleActivationSinkShard.reset();
		CapturingNode.capturedBlob = null;

		List<Node> nodes = Deployment.get().loadDeployment(Arrays.asList(deployment().split(" ")));
		CapturingNode nodeC = null;
		for(Node n : nodes) {
			if("nodeC".equals(n.getName()) && n instanceof CapturingNode)
				nodeC = (CapturingNode) n;
			n.start();
		}
		assertTrue("nodeC must be a CapturingNode", nodeC != null);

		// wait for the real A->B move to produce a captured payload
		String blob = null;
		for(int i = 0; i < 150 && blob == null; i++) {
			Thread.sleep(100);
			blob = CapturingNode.capturedBlob;
		}
		assertTrue("the A->B move must have produced a serialized payload", blob != null);

		// replay the SAME payload onto node C — models a duplicated/replayed transfer
		int distinctBeforeReplay = DoubleActivationSinkShard.received.size();
		nodeC.deliverAgentBlob(blob);

		// let both incarnations dispatch
		Thread.sleep(4000);

		int dispatched = DoubleActivationShard.dispatched.size();
		int received = DoubleActivationSinkShard.received.size();
		originalOut.println("=== E1/T2 double activation ===");
		originalOut.println("  distinct incarnations that dispatched : " + dispatched);
		originalOut.println("  distinct incarnations received by peer: " + received);
		originalOut.println("  (received before the replay onto C)   : " + distinctBeforeReplay);
		originalOut.println("  registerEntity duplicate check        : none (Node.registerEntity appends)");
		try {
			java.nio.file.Files.writeString(java.nio.file.Path.of("/tmp/claude-1000/e1-t2.log"), outContent.toString());
		} catch(Exception e) {
			// diagnostics only
		}

		assertTrue("at least two incarnations must dispatch for the double-activation finding", dispatched >= 2);
	}
}
