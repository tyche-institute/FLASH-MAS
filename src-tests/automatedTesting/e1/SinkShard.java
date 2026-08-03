package automatedTesting.e1;

import java.util.concurrent.atomic.AtomicInteger;

import net.xqhs.flash.core.agent.AgentEvent;
import net.xqhs.flash.core.agent.AgentEvent.AgentEventType;
import net.xqhs.flash.core.agent.AgentWave;
import net.xqhs.flash.core.shard.AgentShardDesignation;
import net.xqhs.flash.core.shard.AgentShardGeneral;

/**
 * Counts probe messages that actually arrive at the peer agent. This is the ground truth for "a dispatch happened":
 * the gate's own counters say what enforcement saw, this says what the network delivered.
 *
 * @author Tyche Institute, for the FLASH-MAS action-gate experiment
 */
public class SinkShard extends AgentShardGeneral {
	private static final long				serialVersionUID	= 1L;

	/** Pre-move probe messages received. */
	public static final AtomicInteger		preReceived			= new AtomicInteger(0);
	/** Post-move probe messages received. */
	public static final AtomicInteger		postReceived		= new AtomicInteger(0);

	/** Resets the counters. */
	public static void reset() {
		preReceived.set(0);
		postReceived.set(0);
	}

	/** Default constructor; the designation is fixed at construction. */
	public SinkShard() {
		super(AgentShardDesignation.customShard("e1Sink"));
	}

	@Override
	public void signalAgentEvent(AgentEvent event) {
		super.signalAgentEvent(event);
		if(event.getType() != AgentEventType.AGENT_WAVE)
			return;
		String content = ((AgentWave) event).getContent();
		if(content == null)
			return;
		if(content.contains(GateProbeShard.PRE_CONTENT)) {
			preReceived.incrementAndGet();
			System.out.println("E1-SINK received PRE marker");
		}
		else if(content.contains(GateProbeShard.POST_CONTENT)) {
			postReceived.incrementAndGet();
			System.out.println("E1-SINK received POST marker");
		}
	}
}
