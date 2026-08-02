/*******************************************************************************
 * Copyright (C) 2026 Anton Sokolov.
 *
 * This file is part of Flash-MAS and is distributed under the terms of the GNU
 * General Public License, version 3 or later.
 *******************************************************************************/
package net.xqhs.flash.core.support;

/**
 * Structured result returned by an {@link OutgoingMessageGate}.
 *
 * Policy and evidence references are opaque to Flash-MAS. This keeps the core
 * independent of a policy engine or evidence wire format while allowing a gate
 * to bind its own audit record to the decision.
 */
public final class MessageDecision {
	public enum Effect {
		PERMIT,
		DENY
	}

	private final Effect effect;
	private final String reasonCode;
	private final String policyReference;
	private final String evidenceReference;

	private MessageDecision(Effect effect, String reasonCode, String policyReference, String evidenceReference) {
		this.effect = effect;
		this.reasonCode = reasonCode;
		this.policyReference = policyReference;
		this.evidenceReference = evidenceReference;
	}

	public static MessageDecision permit(String policyReference, String evidenceReference) {
		return new MessageDecision(Effect.PERMIT, null, policyReference, evidenceReference);
	}

	public static MessageDecision deny(String reasonCode, String policyReference, String evidenceReference) {
		if(reasonCode == null || reasonCode.trim().isEmpty())
			throw new IllegalArgumentException("a denial must carry a reason code");
		return new MessageDecision(Effect.DENY, reasonCode, policyReference, evidenceReference);
	}

	public Effect getEffect() {
		return effect;
	}

	public boolean isPermitted() {
		return effect == Effect.PERMIT;
	}

	public String getReasonCode() {
		return reasonCode;
	}

	public String getPolicyReference() {
		return policyReference;
	}

	public String getEvidenceReference() {
		return evidenceReference;
	}
}
