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

import java.util.HashMap;

/**
 * Public "interface" that allows one to define it's own report mechanisms of
 * receiving/sending of messages
 * 
 * Receiving: It implements mechanisms to choose the granularity of successful
 * REPORT requests
 * 
 * TODO: Sending: Implement mechanisms to choose the granularity of the
 * updateSendStatus callbacks
 * 
 * @author João André Pereira Antunes 2008
 * 
 */
public abstract class ReportMechanism
{
    private HashMap<Message, Counter> messageCounters =
        new HashMap<Message, Counter>();

    /**
     * Method that accounts for blocks of received body of the given message
     * starting at the offset given by the byte-range header field of the
     * associated transaction plus the chunkStartOffset
     * 
     * @param message the message of which the body bytes where received
     * @param transaction the transaction associated with this block
     * @param chunkStartOffset the absolute offset within the given message
     * @param chunkNrBytes the number of bytes accounted for starting from the
     */
    public final void countReceivedBodyBlock(Message message,
        Transaction transaction, long chunkStartOffset, int chunkNrBytes)
    {
        Counter counter = getCounter(message);
        counter.register(chunkStartOffset, chunkNrBytes);
        long callCount = counter.getCount();

        triggerSuccessReport(message, transaction, message.lastCallReportCount,
            callCount);

        message.lastCallReportCount = callCount;
    }

    /**
     * Method called upon when a write cycle is done.
     * 
     * This method uses the abstract ShouldTriggerSentHook to decide upon
     * calling the updateSendStatus or not and also flags the Connection
     * Prioritizer so that this one can act and eventually pause the sending of
     * the current message.
     * 
     * 
     * @param outgoingMessage the Message that is being sent and accounted for
     * @param numberBytesSent the number of bytes that were sent
     *            (useful+overhead) in order to serve the sending of the
     *            outgoingMessage
     * @throws IllegalArgumentException if this method was called with an
     *             incoming message as argument
     */
    protected final void countSentBodyBytes(OutgoingMessage outgoingMessage,
        int numberBytesSent)
    {
        Session session = outgoingMessage.getSession();
        if (shouldTriggerSentHook(outgoingMessage, session,
            outgoingMessage.lastCallSentData))
            session.triggerUpdateSendStatus(session, outgoingMessage);
        if (outgoingMessage.getDataContainer() != null)
	        outgoingMessage.lastCallSentData =
	            outgoingMessage.getDataContainer().currentReadOffset();

        // TODO call the connection prioritizer method so far we will only check
        // to see if message is complete and account it on the session's sent
        // messages, if the connectionprioritizer is called the next lines
        // should be removed:
        // Store the sent message based on the success report
        if (outgoingMessage.wantSuccessReport())
            session.addSentOrSendingMessage(outgoingMessage);
    }

    /**
     * Retrieve the counter associated with the given message
     * 
     * @param message the message to retrieve the counter from
     * @return the counter associated with the given message
     */
    public final Counter getCounter(Message message)
    {
        Counter counterToRetrieve = messageCounters.get(message);
        if (counterToRetrieve == null)
        {
            counterToRetrieve = new Counter(message);
            messageCounters.put(message, counterToRetrieve);
        }
        return counterToRetrieve;
    }

    /**
     * Trigger a success report when appropriate
     * 
     * @param message the message that triggered this call
     * @param transaction the transaction that triggered this call
     * @param callCount
     * @param l
     */
    protected void triggerSuccessReport(Message message,
        Transaction transaction, long lastCallCount, long callCount)
    {
        if (message.wantSuccessReport())
        {
            /*
             * use this mechanism also as a way of asserting if a message
             * with a negative success report is complete or not
             */
            if (shouldGenerateReport(message, lastCallCount, callCount))
            {
                /*
                 * if there was a change in the number of bytes accounted for
                 */
                Stack.generateAndSendSuccessReport(message, transaction, null);
            }
        }
    }

    /**
     * @return How many body bytes should be received before
     *         calling {@link #shouldGenerateReport(Message, long, long)}.
     */
    public abstract int getTriggerGranularity();

    /**
     * Called whenever {@link #getTriggerGranularity()} change to the number
     * of received bytes is detected on the message.
     * <P>
     * Indicates whether a {@link SuccessReport} should be generated now.
     * <P>
     * Hint: access the totalNrBytes of the message. May be useful to generate
     * reports based on percentage (if applicable, totalNrBytes may be unspecified/unknown)
     * 
     * @param message Message this change pertains to (message being received)
     * @param lastCallCount number of received bytes on the last call to this method
     * @param callCount the number of bytes received so far
     * @return true if a {@link SuccessReport} should be sent now, false otherwise
     */
    public abstract boolean shouldGenerateReport(Message message,
    		long lastCallCount, long callCount);

    /**
     * Called whenever any significant change to the number of sent bytes is
     * detected on the message.
     * <P> 
     * Implementation of this method allows the application to determine
     * the granularity of call-backs to
     * {@link SessionListener#updateSendStatus(Session, Message, long)}.
     * 
     * @param message message that triggered the call
     * @param session the session to which the message belongs
     * @param nrBytesLastCall number of bytes accounted for on the last call
     *			to this method (we don't have a fixed granularity for this call)
     * @return true if 
     * 			{@link SessionListener#updateSendStatus(Session, Message, long)}
     * 			should be called
     */
    public abstract boolean shouldTriggerSentHook(Message message,
    		Session session, long nrBytesLastCall);

    /**
     * Removes this message from the reporting mechanism.
     *
     * @param message the message to remove.
     */
    public void removeMessage(Message message) 
    {
    	messageCounters.remove(message);
    }
}
