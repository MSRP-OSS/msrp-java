/* Copyright © João Antunes 2008
 * This file is part of MSRP Java Stack.
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
 * Callback interface of the MSRP session.
 * 
 * It provides callbacks for events at a message level (associated with some
 * action regarding a message) in the session that is associated with the class
 * that implements this interface.
 * 
 * @author João André Pereira Antunes 2008
 */
public interface SessionListener
    extends EventListener
{

    /**
     * Accept or reject the incoming message. SHOULD ALWAYS
     * assign a {@code DataContainer} to the given message, otherwise it is
     * discarded.
     * <p>
     * <strong>Note:</strong> if the message is rejected one should call
     * 		 {@link IncomingMessage#setResult(int)} to specify why, defaults to 413
     * 
     * @param session the session on which we have an incoming message
     * @param message the message to be accepted or rejected
     * @return true if the message is to be accepted, false otherwise
     */
    public boolean acceptHook(Session session, IncomingMessage message);

    /**
     * Signal a received message
     * 
     * @param session the session on which the message was received
     * @param message the message received
     */
    public void receivedMessage(Session session, IncomingMessage message);

    public void receivedNickname(Session session, Transaction request);

    /**
     * Signal a received REPORT
     * 
     * @param session the Session on which the REPORT was received
     * @param report the Transaction associated with the REPORT
     * @see Transaction#getStatusHeader()
     */
    public void receivedReport(Session session, Transaction report);

    /** A response to a NICKNAME request has been received. 
     * @param session	the session on which the request was done
     * @param result	the response to this request.
     */
    public void receivedNickNameResult(Session session, TransactionResponse result);

    /**
     * Signal an aborted message
     * 
     * @param abortEvent the abort-event with additional info.
     * @see MessageAbortedEvent
     */
    public void abortedMessageEvent(MessageAbortedEvent abortEvent);

    /**
     * Signal updates on the sending status of a message.
     * The granularity of such updates can be set by
     * implementing {@link ReportMechanism#shouldTriggerSentHook(Message, Session, long)
     * 
     * @param session	the session used
     * @param message	the message it pertains to
     * @param numberBytesSent the total number of sent bytes from the message
     */
    public void updateSendStatus(Session session, Message message, long numberBytesSent);

	/** Signal that underlying connection to this session has ceased to be.
	 * 
	 * @param session	the session it pertains to
	 * @param cause		why was it lost?
	 */
    public void connectionLost(Session session, Throwable cause);
}
