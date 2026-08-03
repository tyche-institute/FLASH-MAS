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

import net.xqhs.flash.core.mobileComposite.MobileCompositeAgent;
import net.xqhs.flash.core.node.Node;

/**
 * Test-only {@link Node} subclass for Experiment 1 test T2 ("double activation").
 * <p>
 * It does two things a plain node does not:
 * <ul>
 * <li>{@link #sendAgent(String, String, String)} records the serialized agent payload before delegating, so a test
 * can capture the exact bytes that cross the wire during a real migration;
 * <li>{@link #deliverAgentBlob(String)} replays that payload into this node's {@code receive_agent} path. It is a
 * byte-for-byte copy of the RECEIVE_AGENT branch of {@code Node.parseReceivedMsg}, i.e. exactly what a replayed
 * network message would trigger — so delivering the same blob to a second node faithfully models one serialized
 * agent being activated twice.
 * </ul>
 * Deployed with {@code -node classpath:automatedTesting.e1.CapturingNode name:...}.
 *
 * @author Tyche Institute, for the FLASH-MAS action-gate experiment
 */
public class CapturingNode extends Node {
	/** The most recent serialized agent payload this node sent onward during a move. */
	public static volatile String	capturedBlob	= null;

	@Override
	protected void sendAgent(String destination, String agentName, String agentData) {
		capturedBlob = agentData;
		super.sendAgent(destination, agentName, agentData);
	}

	/**
	 * Activates a serialized agent on this node, exactly as {@code parseReceivedMsg} does for a {@code receive_agent}
	 * message. Models a replayed transfer payload.
	 *
	 * @param agentData
	 *            - the serialized agent (as captured from a real move).
	 */
	public void deliverAgentBlob(String agentData) {
		MobileCompositeAgent agent = MobileCompositeAgent.deserializeAgent(agentData);
		registerEntity("agent", agent, agent.getName());
		agent.addGeneralContext(asContext());
		agent.addContext(nodePylonProxy);
		agent.start();
	}
}
