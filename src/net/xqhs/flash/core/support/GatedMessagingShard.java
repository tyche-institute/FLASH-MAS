/*******************************************************************************
 * Copyright (C) 2026 Anton Sokolov.
 *
 * This file is part of Flash-MAS and is distributed under the terms of the GNU
 * General Public License, version 3 or later.
 *******************************************************************************/
package net.xqhs.flash.core.support;

/**
 * Optional capability implemented by messaging shards that support
 * pre-dispatch enforcement.
 *
 * It is separate from {@link MessagingShard} so existing third-party
 * implementations of that interface remain source and binary compatible.
 */
public interface GatedMessagingShard extends MessagingShard {
	void addOutgoingMessageGate(OutgoingMessageGate gate);
}
