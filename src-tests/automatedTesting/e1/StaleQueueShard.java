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
import net.xqhs.flash.core.support.OutgoingMessageContext;
import net.xqhs.flash.core.support.OutgoingMessageGate;
import net.xqhs.flash.core.util.MultiTreeMap;

/**
 * Experiment 1, test T3 ("stale queue"). Models an action queued before a migration and executed after arrival, while
 * the agent's authority is narrowed in transit.
 * <p>
 * The shard installs a gate whose decision is read live from {@link #authorityRevoked}. It performs a normal action
 * before the move (authority intact) and a "queued" action after arrival (authority revoked in transit). The question
 * is whether the post-arrival action is authorized against the CURRENT policy (re-authorized, P3 holds) or slips
 * through (P3 fails).
 * <p>
 * Two arms via {@value #REINSTALL_PARAMETER}: naive (gate registered once, lost on arrival per T1) and reinstall
 * (gate re-installed on every AGENT_START).
 *
 * @author Tyche Institute, for the FLASH-MAS action-gate experiment
 */
public class StaleQueueShard extends AgentShardGeneral {
	private static final long			serialVersionUID	= 1L;

	/** Whether to re-install the gate on every AGENT_START. */
	public static final String			REINSTALL_PARAMETER	= "reinstall";
	/** Peer to send to. */
	public static final String			PEER_PARAMETER		= "peer";

	/** Endpoint used for the probe messages. */
	public static final String			SHARD_ENDPOINT		= "t3probe";
	/** Content of the pre-move action. */
	public static final String			PRE_CONTENT			= "T3-PRE";
	/** Content of the queued, post-arrival action. */
	public static final String			QUEUED_CONTENT		= "T3-QUEUED";

	/** Live authority state; the gate reads this at dispatch. Flipped to true by the test while the agent is moving. */
	public static volatile boolean		authorityRevoked	= false;

	/** Times the gate was consulted. */
	public static final AtomicInteger	gateCalls			= new AtomicInteger(0);
	/** sendMessage return for the queued post-arrival action; -1 = not attempted. */
	public static final AtomicInteger	queuedSendResult	= new AtomicInteger(-1);
	/** Peer receipts of the queued action. */
	public static final AtomicInteger	queuedReceived		= new AtomicInteger(0);
	/** AGENT_START events seen. */
	public static final AtomicInteger	agentStarts			= new AtomicInteger(0);

	/** Resets shared state. */
	public static void reset() {
		authorityRevoked = false;
		gateCalls.set(0);
		queuedSendResult.set(-1);
		queuedReceived.set(0);
		agentStarts.set(0);
	}

	/** A gate that permits only while authority is not revoked; reads the live flag at dispatch. */
	static class PolicyGate implements OutgoingMessageGate {
		@Override
		public MessageDecision authorize(OutgoingMessageContext context) {
			gateCalls.incrementAndGet();
			if(authorityRevoked)
				return MessageDecision.deny("t3.authority-revoked", "policy://t3/revoked", "evidence://t3/none");
			return MessageDecision.permit("policy://t3/intact", "evidence://t3/none");
		}
	}

	/** Fixed designation. */
	public StaleQueueShard() {
		super(AgentShardDesignation.customShard("t3Probe"));
	}

	private boolean			reinstall	= false;
	private String			peer		= null;
	private boolean			installed	= false;
	private transient Timer	timer		= null;

	@Override
	public boolean configure(MultiTreeMap configuration) {
		if(!super.configure(configuration))
			return false;
		if(configuration.isSimple(REINSTALL_PARAMETER))
			reinstall = Boolean.parseBoolean(configuration.getAValue(REINSTALL_PARAMETER));
		if(configuration.isSimple(PEER_PARAMETER))
			peer = configuration.getAValue(PEER_PARAMETER);
		return true;
	}

	protected void installGate() {
		Object messaging = getAgent() == null ? null
				: getAgent().getAgentShard(StandardAgentShard.MESSAGING.toAgentShardDesignation());
		if(messaging instanceof GatedMessagingShard) {
			((GatedMessagingShard) messaging).addOutgoingMessageGate(new PolicyGate());
			installed = true;
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

		if(timer == null)
			timer = new Timer();
		if(!afterMove)
			// pre-move action, authority intact
			timer.schedule(new TimerTask() {
				@Override
				public void run() {
					sendMessage(PRE_CONTENT, SHARD_ENDPOINT, peer, SHARD_ENDPOINT);
				}
			}, 800);
		else
			// the queued action, executed after arrival, when authority may have been revoked in transit
			timer.schedule(new TimerTask() {
				@Override
				public void run() {
					boolean sent = sendMessage(QUEUED_CONTENT, SHARD_ENDPOINT, peer, SHARD_ENDPOINT);
					queuedSendResult.set(sent ? 1 : 0);
					System.out.println("T3-QUEUED-DISPATCH returned=[" + sent + "] authorityRevoked=["
							+ authorityRevoked + "] gateCalls=[" + gateCalls.get() + "]");
				}
			}, 800);
	}
}
