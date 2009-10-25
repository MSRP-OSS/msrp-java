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

import java.util.HashMap;
import java.util.HashSet;

import javax.xml.ws.handler.MessageContext;

import msrp.messages.*;

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
     * This method is called by the stack to sinalize an incoming body byte on a
     * send transaction request it's called after the addition of the byte to
     * the transaction It's up to this method to store the info about the
     * received message bytes, it should take into consideration that chunks can
     * overlap and be out of order
     * 
     * @deprecated this method represented too much of a performance decrease
     *             due to the overhead of accounting byte by byte the received
     *             data
     * 
     * @param message the message to which a byte was received
     * @param transaction the transaction that received the body byte
     * 
     * @throws IllegalArgumentException if this method was called with a Message
     *             other than an IncomingMessage
     * 
     *             public final void countReceivedBodyByte(Message message,
     *             Transaction transaction) { if (!(message instanceof
     *             IncomingMessage)) throw new
     *             IllegalArgumentException("Illegal kind of message" +
     *             " this method should only be called with an IncomingMessage"
     *             ); Counter counter = getCounter(message); long
     *             startingPosition = transaction.getByteRange()[0]; int
     *             numberBytes = transaction.getNrBodyBytes();
     * 
     *             if (counter.register(startingPosition, numberBytes)) {
     *             triggerSuccessReport(message, transaction,
     *             message.getCounter() .getCount() - 1,
     *             message.getCounter().getCount()); }
     * 
     *             }
     */

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
        outgoingMessage.lastCallSentData =
            outgoingMessage.getDataContainer().currentReadOffset();

        // TODO call the connection prioritizer method so far we will only check
        // to see if message is complete and account it on the session's sent
        // messages, if the connectionprioritizer is called the next lines
        // should be removed:
        // Store the sent message based on the success report
        if (outgoingMessage.getSuccessReport())
            outgoingMessage.getSession().addSentOrSendingMessage(
                outgoingMessage);

    }

    /**
     * Method that is used to retrieve the counter associated with the given
     * message
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
     * method used to eventually trigger an success report
     * 
     * @param message the message that triggered this call
     * @param transaction the transaction that triggered this call
     * @param callCount
     * @param l
     */
    protected void triggerSuccessReport(Message message,
        Transaction transaction, long lastCallCount, long callCount)
    {

        if (message.getSuccessReport())
        {
            /*
             * use this mechanism also as a way of asserting also if a message
             * with a negative success report is complete or not
             */
            if (shouldGenerateReport(message, lastCallCount, callCount))
            {
                /*
                 * if there was a change in the number of bytes accounted for
                 */

                MSRPStack.generateAndSendSuccessReport(message, transaction, null);
            }

        }
    }

    /**
     * @return an int with the default value of the triggerReportGranularity
     *         basicly this value sets the number of received body bytes before
     *         calling the triggerSuccessReport method
     * 
     *         All of the implementing classes must implement this method
     */
    public abstract int getTriggerGranularity();

    /**
     * Method to be implemented by the actual reportMechanism This method is
     * called whenever any alteration to the number of bytes is detected on the
     * message
     * 
     * Hint: one always has access to the totalNrBytes of the message that may
     * be useful to generate reports based on percentage (if applicable, because
     * the totalNrBytes of the message may be unspecified/unknown)
     * 
     * @param message Message to which the alteration of the number of bytes
     *            ocurred (message being received)
     * @param lastCallCount the number of bytes that were received on the last
     *            call to this method
     * @param callCount the number of bytes received so far by the message
     * @return true if it's reasoned that a success report should be sent now,
     *         false otherwise
     */
    public abstract boolean shouldGenerateReport(Message message,
        long lastCallCount, long callCount);

    /**
     * Method to be implemented by the actual reportMechanism This method is
     * called whenever any significant alteration to the number of bytes is
     * detected on the message.
     * 
     * The implementation of this method allows the specific application to
     * decide upon the granularity of the callbacks to the updateSendStatus on
     * the MSRPSessionListener
     * 
     * 
     * @param outgoingMessage the message that triggered the call
     * @param session the session to which the message belongs
     * @param nrBytesLastCall the number of bytes accounted for on the last call
     *            to this method - needed because we don't have a fixed
     *            granularity for this method call -
     * @return true if one should call the updateSendStatus trigger and false
     *         otherwise
     * 
     */
    public abstract boolean shouldTriggerSentHook(Message outgoingMessage,
        Session session, long nrBytesLastCall);
}
