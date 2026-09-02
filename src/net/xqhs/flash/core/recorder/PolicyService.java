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
package net.xqhs.flash.core.recorder;

import net.xqhs.flash.core.agent.AgentWave;

/**
 * PolicyService - Singleton Facade for the action-authorization subsystem.
 * <p>
 * Deliberately built the same way as {@link RecorderService}: a static facade whose backend is chosen once, from
 * configuration read when the class is loaded in a host process. That construction is the reason this class exists in
 * this package rather than beside the messaging shards. A policy held here belongs to the <b>process</b>, so an agent
 * that arrives from elsewhere arrives into it; a policy held in an agent's own shard state travels with the agent and
 * is lost when that state is serialized. The first survives relocation by construction, the second does not.
 * <p>
 * Configuration, read from system properties:
 * <ul>
 * <li><code>flash.policy.enabled</code> - default <code>false</code>. When false, this class permits everything and
 * costs one boolean test per dispatch, so existing deployments are unaffected.
 * <li><code>flash.policy.class</code> - fully qualified name of an {@link ActionPolicy} implementation with a public
 * no-argument constructor.
 * <li><code>flash.policy.declared_mediated</code> - default <code>false</code>. When true, a deployment asserts that
 * it is mediated, and this class <b>refuses every action</b> if no policy could be constructed. This is the
 * difference between a deployment that is not enforcing and a deployment whose enforcement went missing: without it,
 * the two are indistinguishable at the seam, which is exactly how enforcement disappears silently.
 * </ul>
 *
 * @author Tyche Institute, for the FLASH-MAS recorder-seam experiment
 */
public class PolicyService {
	
	/** The configured policy, or null when none is configured. */
	protected static final ActionPolicy	backend;
	/** Whether the deployment asserted that it is mediated. */
	protected static final boolean		declaredMediated;
	
	static {
		boolean enabled = Boolean.parseBoolean(System.getProperty("flash.policy.enabled", "false"));
		declaredMediated = Boolean.parseBoolean(System.getProperty("flash.policy.declared_mediated", "false"));
		String policyClass = System.getProperty("flash.policy.class", null);
		
		ActionPolicy constructed = null;
		if(enabled && policyClass != null)
			try {
				constructed = (ActionPolicy) Class.forName(policyClass).getDeclaredConstructor().newInstance();
				System.out.println("[POLICY] Active policy: " + policyClass);
			} catch(Exception e) {
				System.out.println("[POLICY] FAILED to construct " + policyClass + ": " + e);
			}
		else if(enabled)
			System.out.println("[POLICY] Enabled but no flash.policy.class given.");
		backend = constructed;
		
		if(backend == null && declaredMediated)
			System.out.println("[POLICY] Deployment declares itself mediated and has no policy: refusing all actions.");
	}
	
	private PolicyService() {}
	
	/**
	 * Decides whether a classic message send may proceed.
	 * 
	 * @param entityName
	 *            - the address of the entity acting.
	 * @param source
	 *            - the complete source endpoint.
	 * @param destination
	 *            - the complete destination endpoint.
	 * @param content
	 *            - the message content.
	 * @return the decision; never null.
	 */
	public static ActionDecision check(String entityName, String source, String destination, String content) {
		if(backend == null)
			return absentPolicy();
		return nonNull(backend.check(entityName, source, destination, content));
	}
	
	/**
	 * Decides whether a wave send may proceed.
	 * 
	 * @param entityName
	 *            - the address of the entity acting.
	 * @param wave
	 *            - the wave about to be dispatched.
	 * @param eventType
	 *            - the event label used by the recorder for the same action.
	 * @return the decision; never null.
	 */
	public static ActionDecision check(String entityName, AgentWave wave, String eventType) {
		if(backend == null)
			return absentPolicy();
		return nonNull(backend.check(entityName, wave, eventType));
	}
	
	/** @return true if an action policy is configured in this process. */
	public static boolean isConfigured() {
		return backend != null;
	}
	
	/**
	 * The verdict when no policy is configured: permit, unless the deployment declared itself mediated, in which case
	 * the absence of a policy is itself the failure and the action is refused.
	 */
	protected static ActionDecision absentPolicy() {
		return declaredMediated ? ActionDecision.deny("policy.absent.declared-mediated")
				: ActionDecision.permit(null);
	}
	
	/** A policy that returns null is treated as a refusal; a decision is mandatory. */
	protected static ActionDecision nonNull(ActionDecision decision) {
		return decision != null ? decision : ActionDecision.deny("policy.returned-null");
	}
}
