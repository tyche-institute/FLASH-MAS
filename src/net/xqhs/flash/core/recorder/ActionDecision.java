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

/**
 * The verdict returned by an {@link ActionPolicy} for one action.
 * <p>
 * This is the one thing {@link RecorderInterface} cannot express: every <code>record</code> method returns
 * <code>void</code>, so the recorder seam observes an action but cannot refuse it. A decision carries a reason so
 * that a refusal is a recordable outcome rather than a silence.
 *
 * @author Tyche Institute, for the FLASH-MAS recorder-seam experiment
 */
public class ActionDecision {
	/** Whether the action may proceed. */
	protected final boolean	permitted;
	/** Short, stable identifier of the rule that decided; may be null for the default permit. */
	protected final String	reason;
	
	protected ActionDecision(boolean permitted, String reason) {
		this.permitted = permitted;
		this.reason = reason;
	}
	
	/** The action may proceed. */
	public static ActionDecision permit(String reason) {
		return new ActionDecision(true, reason);
	}
	
	/** The action must not proceed. */
	public static ActionDecision deny(String reason) {
		return new ActionDecision(false, reason);
	}
	
	/** @return true if the action may proceed. */
	public boolean isPermitted() {
		return permitted;
	}
	
	/** @return the identifier of the deciding rule, or null. */
	public String getReason() {
		return reason;
	}
	
	@Override
	public String toString() {
		return (permitted ? "PERMIT" : "DENY") + (reason != null ? "(" + reason + ")" : "");
	}
}
