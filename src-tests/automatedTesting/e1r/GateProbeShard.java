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

import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicInteger;

import net.xqhs.flash.core.agent.AgentEvent;
import net.xqhs.flash.core.agent.AgentEvent.AgentEventType;
import net.xqhs.flash.core.shard.AgentShardDesignation;
import net.xqhs.flash.core.shard.AgentShardGeneral;
import net.xqhs.flash.core.util.MultiTreeMap;

/**
 * Experiment 1R, test T1R - the same probe as T1, with the gate installation removed.
 * <p>
 * This shard sends one message before migration and one after arrival, and counts what reached the peer. It
 * <b>installs nothing</b>: there is no gate to register, because in this arm the policy belongs to the host process
 * and is consulted by {@link net.xqhs.flash.core.recorder.PolicyService} at the seam the recorder already occupies.
 * That absence is the experiment. In T1 the agent had to carry its enforcement and lost it in transit; here the agent
 * carries nothing and the destination already has the policy.
 * <p>
 * The class keeps the name and the marker constants of the T1 probe so the two results can be compared line by line.
 *
 * @author Tyche Institute, for the FLASH-MAS recorder-seam experiment
 */
public class GateProbeShard extends AgentShardGeneral {
	private static final long		serialVersionUID	= 1L;
	
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
	
	/** Return value of sendMessage for the pre-move send; -1 = not attempted. */
	public static final AtomicInteger	preSendResult	= new AtomicInteger(-1);
	/** Return value of sendMessage for the post-move send; -1 = not attempted. */
	public static final AtomicInteger	postSendResult	= new AtomicInteger(-1);
	/** How many AGENT_START events this shard saw (2 = one boot + one arrival). */
	public static final AtomicInteger	agentStarts		= new AtomicInteger(0);
	
	/** Resets all counters; call from the test before booting. */
	public static void reset() {
		preSendResult.set(-1);
		postSendResult.set(-1);
		agentStarts.set(0);
	}
	
	/** Default constructor; the designation is fixed at construction, as in the framework's own shards. */
	public GateProbeShard() {
		super(AgentShardDesignation.customShard("e1rProbe"));
	}
	
	private String			peer	= null;
	private long			preAt	= 1500;
	private long			postAt	= 9000;
	/** Transient so the shard itself serializes; a fresh timer is created on demand after arrival. */
	private transient Timer	timer	= null;
	
	@Override
	public boolean configure(MultiTreeMap configuration) {
		if(!super.configure(configuration))
			return false;
		if(configuration.isSimple(PEER_PARAMETER))
			peer = configuration.getAValue(PEER_PARAMETER);
		if(configuration.isSimple(PRE_PARAMETER))
			preAt = Long.parseLong(configuration.getAValue(PRE_PARAMETER));
		if(configuration.isSimple(POST_PARAMETER))
			postAt = Long.parseLong(configuration.getAValue(POST_PARAMETER));
		return true;
	}
	
	@Override
	public void signalAgentEvent(AgentEvent event) {
		super.signalAgentEvent(event);
		if(event.getType() != AgentEventType.AGENT_START)
			return;
		int starts = agentStarts.incrementAndGet();
		li("E1R: AGENT_START [], nothing installed by the agent", Integer.valueOf(starts));
		if(starts > 1)
			schedule(500, POST_CONTENT, postSendResult);
		else
			schedule(preAt, PRE_CONTENT, preSendResult);
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
				System.out.println("E1R-PROBE-SEND content=[" + content + "] sendMessageReturned=[" + sent
						+ "] policyCallsSoFar=[" + CountingDenyAllPolicy.policyCalls.get() + "]");
			}
		}, delay);
	}
}
