/*******************************************************************************
 * Copyright (C) 2026 Anton Sokolov.
 *
 * This file is part of Flash-MAS and is distributed under the terms of the GNU
 * General Public License, version 3 or later.
 *******************************************************************************/
package net.xqhs.flash.core.support;

/**
 * Optional pre-dispatch enforcement point for an outgoing message.
 *
 * Returning {@code null} or throwing a runtime exception fails closed. A gate
 * that produces external evidence should persist it before returning and place
 * its stable reference in the returned {@link MessageDecision}.
 */
public interface OutgoingMessageGate {
	MessageDecision authorize(OutgoingMessageContext context);
}
