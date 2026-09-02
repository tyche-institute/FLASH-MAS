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

import java.util.concurrent.atomic.AtomicInteger;

import net.xqhs.flash.core.agent.AgentWave;
import net.xqhs.flash.core.recorder.ActionDecision;
import net.xqhs.flash.core.recorder.ActionPolicy;

/**
 * The host-side counterpart of T1's deny-all gate: it refuses every action of one named entity.
 * <p>
 * It is named in the deployment through the <code>flash.policy.class</code> system property, so nothing about it is
 * reachable from the agent. That is the intended difference from T1's gate, and it is the difference the experiment
 * measures.
 * <p>
 * <b>Why it is scoped to one entity, and why that is a finding rather than a convenience.</b> The first run of this
 * experiment used a policy that refused everything, exactly as T1's gate did. The agent never migrated: a policy at
 * this seam is consulted for <i>every</i> messaging component in the host process, including the ones the platform
 * uses to carry out the move, so refusing everything refuses the migration itself. A gate installed on one agent's
 * own component cannot do that, because it can only ever see that agent. Moving enforcement to the host therefore
 * widens its blast radius as well as its coverage, and a host-side policy has to be written per entity from the
 * start. The entity name is the first argument of every {@link ActionPolicy} method precisely so that it can be.
 *
 * @author Tyche Institute, for the FLASH-MAS recorder-seam experiment
 */
public class CountingDenyAllPolicy implements ActionPolicy {
	/** The entity whose actions are refused; every other entity is permitted. */
	public static final String TARGET = System.getProperty("flash.policy.e1r.target", "agentA1");
	
	/** How many times the policy was consulted about the target entity, i.e. enforcement was on its path. */
	public static final AtomicInteger	policyCalls		= new AtomicInteger(0);
	/** How many times the policy was consulted about anything at all, target or not. */
	public static final AtomicInteger	totalCalls		= new AtomicInteger(0);
	/** How many distinct entities the policy was consulted about. */
	public static final java.util.Set<String> entitiesSeen = java.util.concurrent.ConcurrentHashMap.newKeySet();
	
	/** Resets the counters; call from the test before booting. */
	public static void reset() {
		policyCalls.set(0);
		totalCalls.set(0);
		entitiesSeen.clear();
	}
	
	/** Records the consultation and decides, refusing only the target entity. */
	protected ActionDecision decide(String entityName) {
		totalCalls.incrementAndGet();
		if(entityName != null)
			entitiesSeen.add(entityName);
		if(entityName != null && entityName.contains(TARGET)) {
			policyCalls.incrementAndGet();
			return ActionDecision.deny("e1r.deny-all");
		}
		return ActionDecision.permit("e1r.not-in-scope");
	}
	
	@Override
	public ActionDecision check(String entityName, String source, String destination, String content) {
		return decide(entityName);
	}
	
	@Override
	public ActionDecision check(String entityName, AgentWave wave, String eventType) {
		return decide(entityName);
	}
}
