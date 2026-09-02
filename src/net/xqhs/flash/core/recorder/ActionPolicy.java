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
 * A policy consulted before an entity's action takes effect.
 * <p>
 * The methods mirror {@link RecorderInterface} deliberately: the same call sites that already record an action can
 * consult a policy about it, with the same arguments and at the same moment. The only difference is the return type,
 * and that difference is the whole point — the recorder observes, the policy decides.
 * <p>
 * Implementations must be safe for use by several entities on one host: the entity name is passed on every call, and
 * no state may be kept per caller.
 *
 * @author Tyche Institute, for the FLASH-MAS recorder-seam experiment
 */
public interface ActionPolicy {
	/**
	 * Decides on a classic (source, destination, content) message send.
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
	ActionDecision check(String entityName, String source, String destination, String content);
	
	/**
	 * Decides on a wave send.
	 * 
	 * @param entityName
	 *            - the address of the entity acting.
	 * @param wave
	 *            - the wave about to be dispatched.
	 * @param eventType
	 *            - the event label used by the recorder for the same action.
	 * @return the decision; never null.
	 */
	ActionDecision check(String entityName, AgentWave wave, String eventType);
}
