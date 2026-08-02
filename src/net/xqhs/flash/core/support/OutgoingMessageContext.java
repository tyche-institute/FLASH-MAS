/*******************************************************************************
 * Copyright (C) 2026 Anton Sokolov.
 *
 * This file is part of Flash-MAS and is distributed under the terms of the GNU
 * General Public License, version 3 or later.
 *******************************************************************************/
package net.xqhs.flash.core.support;

import java.util.Objects;

import net.xqhs.flash.core.agent.AgentWave;

/**
 * Immutable view of the message that is about to cross a messaging pylon.
 *
 * The context deliberately exposes values rather than the mutable
 * {@link AgentWave}. A security gate can therefore inspect the exact source,
 * destination and serialized content without being able to rewrite the wave
 * after making its decision.
 */
public final class OutgoingMessageContext {
	private final String source;
	private final String destination;
	private final String serializedContent;

	private OutgoingMessageContext(String source, String destination, String serializedContent) {
		this.source = source;
		this.destination = destination;
		this.serializedContent = serializedContent;
	}

	/**
	 * Creates a context for a classic source/destination/content message.
	 */
	public static OutgoingMessageContext of(String source, String destination, String serializedContent) {
		return new OutgoingMessageContext(source, destination, serializedContent);
	}

	/**
	 * Creates a context from the final routing and content values of a wave.
	 */
	public static OutgoingMessageContext fromWave(AgentWave wave) {
		if(wave == null)
			throw new IllegalArgumentException("wave must not be null");
		return of(wave.getCompleteSource(), wave.getCompleteDestination(), wave.getSerializedContent());
	}

	public String getSource() {
		return source;
	}

	public String getDestination() {
		return destination;
	}

	public String getSerializedContent() {
		return serializedContent;
	}

	@Override
	public boolean equals(Object other) {
		if(this == other)
			return true;
		if(!(other instanceof OutgoingMessageContext))
			return false;
		OutgoingMessageContext that = (OutgoingMessageContext) other;
		return Objects.equals(source, that.source) && Objects.equals(destination, that.destination)
				&& Objects.equals(serializedContent, that.serializedContent);
	}

	@Override
	public int hashCode() {
		return Objects.hash(source, destination, serializedContent);
	}
}
