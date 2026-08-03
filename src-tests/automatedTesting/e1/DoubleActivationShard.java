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

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

import net.xqhs.flash.core.agent.AgentEvent;
import net.xqhs.flash.core.agent.AgentEvent.AgentEventType;
import net.xqhs.flash.core.shard.AgentShardDesignation;
import net.xqhs.flash.core.shard.AgentShardGeneral;
import net.xqhs.flash.core.util.MultiTreeMap;

/**
 * Experiment 1, test T2 ("double activation"). Rides a mobile agent. On every AGENT_START it sends one message to a
 * fixed peer, tagged with an incarnation identifier unique to this shard object. Because a deserialized copy of the
 * agent is a fresh object, each incarnation carries a distinct tag — so counting distinct tags that both dispatch and
 * arrive measures how many incarnations of one serialized agent are simultaneously able to act under one identity.
 * <p>
 * Property P2 {@code AtMostOneActiveEpoch} fails if more than one incarnation dispatches.
 *
 * @author Tyche Institute, for the FLASH-MAS action-gate experiment
 */
public class DoubleActivationShard extends AgentShardGeneral {
	private static final long				serialVersionUID	= 1L;

	/** Peer agent to send to. */
	public static final String				PEER_PARAMETER		= "peer";
	/** Endpoint used for the probe messages. */
	public static final String				SHARD_ENDPOINT		= "t2probe";
	/** Content prefix; the incarnation tag follows. */
	public static final String				TAG_PREFIX			= "T2-ACT:";

	/** Distinct incarnations whose dispatch returned true. */
	public static final Set<String>			dispatched			= Collections.synchronizedSet(new HashSet<>());

	/** Resets shared state. */
	public static void reset() {
		dispatched.clear();
	}

	/** Default constructor; fixed designation as in the framework's own shards. */
	public DoubleActivationShard() {
		super(AgentShardDesignation.customShard("t2Probe"));
	}

	private String			peer	= null;
	private transient Timer	timer	= null;

	@Override
	public boolean configure(MultiTreeMap configuration) {
		if(!super.configure(configuration))
			return false;
		if(configuration.isSimple(PEER_PARAMETER))
			peer = configuration.getAValue(PEER_PARAMETER);
		return true;
	}

	@Override
	public void signalAgentEvent(AgentEvent event) {
		super.signalAgentEvent(event);
		if(event.getType() != AgentEventType.AGENT_START)
			return;
		final String tag = TAG_PREFIX + Integer.toHexString(System.identityHashCode(this));
		if(timer == null)
			timer = new Timer();
		timer.schedule(new TimerTask() {
			@Override
			public void run() {
				boolean sent = sendMessage(tag, SHARD_ENDPOINT, peer, SHARD_ENDPOINT);
				if(sent)
					dispatched.add(tag);
				System.out.println("T2-DISPATCH tag=[" + tag + "] returned=[" + sent + "] distinctDispatched=["
						+ dispatched.size() + "]");
			}
		}, 800);
	}
}
