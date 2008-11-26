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
package msrp;

import java.util.EventListener;

/**
 * @author D
 * 
 *         Gives the class that is going to be used by the client application
 * 
 */
public interface MSRPSessionListener
    extends EventListener
{

    /**
     * Method used to accept or reject the message by the stack SHOULD ALWAYS
     * ASSIGN A DataContainer to the given message or else the message is
     * discarded
     * 
     * @param session the session on which we have an incoming message
     * @param message the message on which we should decide upon accepting or
     *            rejecting it
     * @return true if the message is to be accepted, false otherwise Note: if
     *         the message is rejected one should call message.reject(code) to
     *         specify the reason why, default reason is 413
     */
    boolean acceptHook(Session session, IncomingMessage message);

    /**
     * Method used by the API to signal the application of a received message
     * 
     * @param session the session on which the message was received
     * @param message the message received
     */
    void receiveMessage(Session session, Message message);

    /**
     * Method used by the API to signal the application of a received REPORT
     * request
     * 
     * @param session the Session on which the REPORT was received
     * @param report the Transaction associated with the REPORT
     */
    void receivedReport(Session session, Transaction report);

    /**
     * Method used by the API to signal the application of an aborted message
     * 
     * @param session the session associated with the message
     * @param message the IncomingMessage that was aborted
     */
    void abortedMessage(Session session, IncomingMessage message);

    /**
     * Used by the stack to notify the using app of updates on the sending
     * status of a message. The granularity of such updates can be set by
     * implementing the shouldTriggerSentHook of the ReportMechanism
     * 
     * @param session
     * @param message
     * @param numberBytesSent the total number of sent bytes belonging to the
     *            message
     * 
     */
    void updateSendStatus(Session session, Message message, long numberBytesSent);

}
