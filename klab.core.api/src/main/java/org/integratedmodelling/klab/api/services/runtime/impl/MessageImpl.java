/*
 * This file is part of k.LAB.
 * 
 * k.LAB is free software: you can redistribute it and/or modify
 * it under the terms of the Affero GNU General Public License as published
 * by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * A copy of the GNU Affero General Public License is distributed in the root
 * directory of the k.LAB distribution (LICENSE.txt). If this cannot be found 
 * see <http://www.gnu.org/licenses/>.
 * 
 * Copyright (C) 2007-2018 integratedmodelling.org and any authors mentioned
 * in author tags. All rights reserved.
 */
package org.integratedmodelling.klab.api.services.runtime.impl;

import java.io.Serializable;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiFunction;

import org.integratedmodelling.klab.api.services.runtime.Message;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.integratedmodelling.klab.api.utils.Utils;

/**
 * Typed message with potential payload to be transferred through a message bus.
 * Used for fast, duplex engine/client communication.
 * <p>
 * Payloads that are maps can be optionally translated to
 * implementation-dependent types by supplying a static translator function.
 *
 * @author ferdinando.villa
 * @version $Id: $Id
 */
public class MessageImpl implements Message, Serializable {

	private static final long serialVersionUID = 4889814573447834819L;

	private static AtomicLong nextId = new AtomicLong(0L);
	
	private MessageType messageType;
	private MessageClass messageClass;
	private String identity;
	private String payloadClass;
	private Object payload;
	private long id = nextId.incrementAndGet();
	private long inResponseTo;
	private Notification.Type notificationType;
	private long timestamp = System.currentTimeMillis();
	private Repeatability repeatability = Repeatability.Once;

	private static BiFunction<Map<?, ?>, Class<?>, Object> translator;

	public static void setPayloadMapTranslator(BiFunction<Map<?, ?>, Class<?>, Object> function) {
		translator = function;
	}

	@Override
	public String toString() {
		return "{" + messageClass + "/" + messageType + ": " + payload + "}";
	}

	public MessageImpl inResponseTo(Message message) {
		this.inResponseTo = ((MessageImpl) message).id;
		return this;
	}

	/**
	 * Gets the type.
	 *
	 * @return the type
	 */
	@Override
	public MessageType getMessageType() {
		return messageType;
	}

	@Override
	public MessageClass getMessageClass() {
		return messageClass;
	}

	/**
	 * Sets the type.
	 *
	 * @param type the new type
	 */
	public void setMessageType(MessageType type) {
		this.messageType = type;
	}

	/**
	 * Gets the payload.
	 *
	 * @return the payload
	 */
	public Object getPayload() {
		return payload;
	}

	/**
	 * Sets the payload.
	 *
	 * @param payload the new payload
	 */
	public void setPayload(Object payload) {
		this.payload = payload;
	}

	@Override
	public String getIdentity() {
		return identity;
	}

	public long getId() {
		return id;
	}

	@Override
	public Message respondingTo(Message message) {
		return this;
	}

	public void setId(long id) {
		this.id = id;
	}

	public long getInResponseTo() {
		return inResponseTo;
	}

	public MessageImpl inResponseTo(long inResponseTo) {
		this.inResponseTo = inResponseTo;
		return this;
	}

	public void setInResponseTo(long inResponseTo) {
		this.inResponseTo = inResponseTo;
	}

	public void setMessageClass(MessageClass messageClass) {
		this.messageClass = messageClass;
	}

	public void setIdentity(String identity) {
		this.identity = identity;
	}

	public String getPayloadClass() {
		return payloadClass;
	}

	public void setPayloadClass(String payloadClass) {
		this.payloadClass = payloadClass;
	}

	@Override
	public long getTimestamp() {
		return timestamp;
	}

	public void setTimestamp(long timestamp) {
		this.timestamp = timestamp;
	}

	/**
	 * Make an exact copy of this message using a different identity. Used for
	 * relaying.
	 * 
	 * @param relayId
	 * @return a new message identified by relayId
	 */
	public MessageImpl copyWithIdentity(String relayId) {
		MessageImpl ret = new MessageImpl();
		ret.identity = relayId;
		ret.messageClass = this.messageClass;
		ret.payload = this.payload;
		ret.payloadClass = this.payloadClass;
		ret.messageType = this.messageType;
		ret.inResponseTo = this.inResponseTo;
		ret.timestamp = this.timestamp;
		return ret;
	}

	@Override
	public <T> T getPayload(Class<? extends T> cls) {

		if (payload == null) {
			return null;
		}

		Object p = payload;

		if (payload instanceof Map && translator != null) {
			p = translator.apply((Map<?, ?>) p, cls);
		}

		return Utils.Data.asType(p, cls);
	}

	public Notification.Type getNotificationType() {
		return notificationType;
	}

	public void setNotificationType(Notification.Type notificationType) {
		this.notificationType = notificationType;
	}

	@Override
	public Repeatability getRepeatability() {
		return repeatability;
	}

	public void setRepeatability(Repeatability repeatability) {
		this.repeatability = repeatability;
	}
}
