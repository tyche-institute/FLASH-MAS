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

import net.xqhs.flash.core.agent.AgentEvent;
import net.xqhs.flash.core.agent.AgentEvent.AgentEventType;
import net.xqhs.flash.core.agent.AgentWave;
import net.xqhs.flash.core.shard.AgentShardDesignation;
import net.xqhs.flash.core.shard.AgentShardGeneral;

/**
 * Peer-side counter for Experiment 1 test T2: records the distinct incarnation tags actually delivered, which is the
 * ground truth for how many incarnations of one serialized agent acted.
 *
 * @author Tyche Institute, for the FLASH-MAS action-gate experiment
 */
public class DoubleActivationSinkShard extends AgentShardGeneral {
	private static final long		serialVersionUID	= 1L;

	/** Distinct incarnation tags received from the peer. */
	public static final Set<String>	received			= Collections.synchronizedSet(new HashSet<>());

	/** Resets shared state. */
	public static void reset() {
		received.clear();
	}

	/** Default constructor; fixed designation. */
	public DoubleActivationSinkShard() {
		super(AgentShardDesignation.customShard("t2Sink"));
	}

	@Override
	public void signalAgentEvent(AgentEvent event) {
		super.signalAgentEvent(event);
		if(event.getType() != AgentEventType.AGENT_WAVE)
			return;
		String content = ((AgentWave) event).getContent();
		if(content != null && content.contains(DoubleActivationShard.TAG_PREFIX)) {
			int i = content.indexOf(DoubleActivationShard.TAG_PREFIX);
			String tag = content.substring(i).trim();
			received.add(tag);
			System.out.println("T2-SINK received tag=[" + tag + "] distinctReceived=[" + received.size() + "]");
		}
	}
}
