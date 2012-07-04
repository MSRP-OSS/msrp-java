/*
 * Copyright © João Antunes 2008 This file is part of MSRP Java Stack.
 * 
 * MSRP Java Stack is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by the
 * Free Software Foundation, either version 3 of the License, or (at your
 * option) any later version.
 * 
 * MSRP Java Stack is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License
 * for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with MSRP Java Stack. If not, see <http://www.gnu.org/licenses/>.
 */
package javax.net.msrp;

import java.util.EventListener;

import javax.net.msrp.events.*;


/**
 * Interface used for callbacks by the MSRP stack.
 * 
 * It provides callbacks for events at a message level (associated with some
 * action regarding a message) in the session that is associated with the class
 * that implements this interface.
 * 
 * @author João André Pereira Antunes
 */
public interface SessionListener
    extends EventListener
{

    /**
     * Accept or reject the message by the stack SHOULD ALWAYS
     * assign a {@code DataContainer} to the given message otherwise it is
     * discarded
     * 
     * @param session the session on which we have an incoming message
     * @param message the message to decide upon accepting or rejecting
     * @return true if the message is to be accepted, false otherwise
     * 
     * Note: if the message is rejected one should call
     * 		 {@code message.reject(code)} to
     *       specify the reason why, default is 413
     */
    public boolean acceptHook(Session session, IncomingMessage message);

    /**
     * Signal a received message
     * 
     * @param session the session on which the message was received
     * @param message the message received
     */
    public void receiveMessage(Session session, Message message);

    /**
     * Signal a received REPORT
     * 
     * @param session the Session on which the REPORT was received
     * @param report the Transaction associated with the REPORT
     */
    public void receivedReport(Session session, Transaction report);

    /**
     * Signal an aborted message
     * 
     * @deprecated use abortedMessageEvent instead
     * @param session the session associated with the message
     * @param message the IncomingMessage that was aborted
     */
    public void abortedMessage(Session session, IncomingMessage message);

    /**
     * Aignal an aborted message
     * 
     * @param abortEvent the Message aborted event used
     */
    public void abortedMessageEvent(MessageAbortedEvent abortEvent);

    /**
     * Notify the application of updates on the sending status of a message.
     * The granularity of such updates can be set by
     * implementing {@code shouldTriggerSentHook} of {@code ReportMechanism}
     * 
     * @param session	the session used
     * @param message	the message it pertains to
     * @param numberBytesSent the total number of sent bytes from the message
     */
    public void updateSendStatus(Session session, Message message, long numberBytesSent);

	/** Notify application that underlying connection to this session has
	 * ceased to be.
	 * 
	 * @param session	the session it pertains to
	 * @param cause		why was it lost?
	 */
    public void connectionLost(Session session, Throwable cause);
}
