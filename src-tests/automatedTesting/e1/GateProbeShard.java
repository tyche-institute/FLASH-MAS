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

import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicInteger;

import net.xqhs.flash.core.agent.AgentEvent;
import net.xqhs.flash.core.agent.AgentEvent.AgentEventType;
import net.xqhs.flash.core.shard.AgentShardDesignation;
import net.xqhs.flash.core.shard.AgentShardDesignation.StandardAgentShard;
import net.xqhs.flash.core.shard.AgentShardGeneral;
import net.xqhs.flash.core.support.GatedMessagingShard;
import net.xqhs.flash.core.support.MessageDecision;
import net.xqhs.flash.core.support.OutgoingMessageGate;
import net.xqhs.flash.core.util.MultiTreeMap;

/**
 * Experiment 1, test T1 ("vanishing gate").
 * <p>
 * Installs a deny-all {@link OutgoingMessageGate} on the agent's messaging shard, then sends one message before
 * migration and one after, counting what the gate saw and what actually reached the peer.
 * <p>
 * Two arms, selected by the {@value #REINSTALL_PARAMETER} parameter:
 * <ul>
 * <li><code>false</code> — the naive consumer: register the gate once, on the first AGENT_START.
 * <li><code>true</code> — the proposed repair: re-install the gate on every AGENT_START, including the one posted
 * after arrival at the destination node.
 * </ul>
 * This shard is test-only instrumentation; it is not part of the prototype under test.
 *
 * @author Tyche Institute, for the FLASH-MAS action-gate experiment
 */
public class GateProbeShard extends AgentShardGeneral {
	private static final long		serialVersionUID	= 1L;

	/** Whether to re-install the gate on every AGENT_START. */
	public static final String		REINSTALL_PARAMETER	= "reinstall";
	/** Name of the peer agent to send to. */
	public static final String		PEER_PARAMETER		= "peer";
	/** Delay, in ms, before the pre-move send. */
	public static final String		PRE_PARAMETER		= "preAt";
	/** Delay, in ms, before the post-move send. */
	public static final String		POST_PARAMETER		= "postAt";

	/** Endpoint used for the probe messages. */
	public static final String		SHARD_ENDPOINT		= "e1probe";
	/** Marker content of the pre-move send. */
	public static final String		PRE_CONTENT			= "E1-PRE-MOVE";
	/** Marker content of the post-move send. */
	public static final String		POST_CONTENT		= "E1-POST-MOVE";

	/** How many times the gate was consulted (i.e. enforcement was actually on the path). */
	public static final AtomicInteger	gateCalls		= new AtomicInteger(0);
	/** How many times a gate was installed onto a messaging shard. */
	public static final AtomicInteger	gateInstalls	= new AtomicInteger(0);
	/** How many times installation was attempted but the shard was not gate-capable. */
	public static final AtomicInteger	installFailures	= new AtomicInteger(0);
	/** Return value of sendMessage for the pre-move send; -1 = not attempted. */
	public static final AtomicInteger	preSendResult	= new AtomicInteger(-1);
	/** Return value of sendMessage for the post-move send; -1 = not attempted. */
	public static final AtomicInteger	postSendResult	= new AtomicInteger(-1);
	/** How many AGENT_START events this shard saw (2 = one boot + one arrival). */
	public static final AtomicInteger	agentStarts		= new AtomicInteger(0);

	/** Resets all counters; call from the test before booting. */
	public static void reset() {
		gateCalls.set(0);
		gateInstalls.set(0);
		installFailures.set(0);
		preSendResult.set(-1);
		postSendResult.set(-1);
		agentStarts.set(0);
	}

	/** The deny-all gate. It counts every consultation and refuses every dispatch. */
	static class DenyAllGate implements OutgoingMessageGate {
		@Override
		public MessageDecision authorize(net.xqhs.flash.core.support.OutgoingMessageContext context) {
			gateCalls.incrementAndGet();
			return MessageDecision.deny("e1.deny-all", "policy://e1/deny-all", "evidence://e1/none");
		}
	}

	/** Default constructor; the designation is fixed at construction, as in the framework's own shards. */
	public GateProbeShard() {
		super(AgentShardDesignation.customShard("e1GateProbe"));
	}

	private boolean			reinstall	= false;
	private String			peer		= null;
	private long			preAt		= 1500;
	private long			postAt		= 9000;
	/**
	 * Survives serialization deliberately: in the naive arm the shard must arrive still believing its gate is
	 * installed, exactly as a real consumer's persistent state would.
	 */
	private boolean			installed	= false;
	/** Transient so the shard itself serializes; a fresh timer is created on demand after arrival. */
	private transient Timer	timer		= null;

	@Override
	public boolean configure(MultiTreeMap configuration) {
		if(!super.configure(configuration))
			return false;
		if(configuration.isSimple(REINSTALL_PARAMETER))
			reinstall = Boolean.parseBoolean(configuration.getAValue(REINSTALL_PARAMETER));
		if(configuration.isSimple(PEER_PARAMETER))
			peer = configuration.getAValue(PEER_PARAMETER);
		if(configuration.isSimple(PRE_PARAMETER))
			preAt = Long.parseLong(configuration.getAValue(PRE_PARAMETER));
		if(configuration.isSimple(POST_PARAMETER))
			postAt = Long.parseLong(configuration.getAValue(POST_PARAMETER));
		return true;
	}

	/**
	 * Installs the deny-all gate on the agent's messaging shard, if that shard supports gates.
	 */
	protected void installGate() {
		Object messaging = getAgent() == null ? null
				: getAgent().getAgentShard(StandardAgentShard.MESSAGING.toAgentShardDesignation());
		if(messaging instanceof GatedMessagingShard) {
			((GatedMessagingShard) messaging).addOutgoingMessageGate(new DenyAllGate());
			gateInstalls.incrementAndGet();
			installed = true;
			li("E1: deny-all gate installed on []", messaging.getClass().getSimpleName());
		}
		else {
			installFailures.incrementAndGet();
			le("E1: messaging shard is not gate-capable: []", messaging);
		}
	}

	@Override
	public void signalAgentEvent(AgentEvent event) {
		super.signalAgentEvent(event);
		if(event.getType() != AgentEventType.AGENT_START)
			return;
		int starts = agentStarts.incrementAndGet();
		boolean afterMove = starts > 1;
		if(!installed || reinstall)
			installGate();
		else
			li("E1: gate NOT re-installed after event [] (naive arm)", Integer.valueOf(starts));

		if(!afterMove)
			schedule(preAt, PRE_CONTENT, preSendResult);
		else
			schedule(500, POST_CONTENT, postSendResult);
	}

	/** Schedules one probe send. */
	protected void schedule(long delay, final String content, final AtomicInteger sink) {
		if(timer == null)
			timer = new Timer();
		timer.schedule(new TimerTask() {
			@Override
			public void run() {
				boolean sent = sendMessage(content, SHARD_ENDPOINT, peer, SHARD_ENDPOINT);
				sink.set(sent ? 1 : 0);
				System.out.println("E1-PROBE-SEND content=[" + content + "] sendMessageReturned=[" + sent
						+ "] gateCallsSoFar=[" + gateCalls.get() + "]");
			}
		}, delay);
	}
}
