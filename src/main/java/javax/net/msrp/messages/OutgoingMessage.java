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
package javax.net.msrp.messages;

import javax.net.msrp.SessionListener;
import javax.net.msrp.Session;
import javax.net.msrp.TransactionManager;
import javax.net.msrp.exceptions.IllegalUseException;
import javax.net.msrp.exceptions.InternalErrorException;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Generic outgoing MSRP message
 * @author João André Pereira Antunes
 * 
 */
public class OutgoingMessage
    extends Message
{
    /**
     * The logger associated with this class
     */
    private static final Logger logger =
        LoggerFactory.getLogger(OutgoingMessage.class);

    public OutgoingMessage(Session sendingSession, String contentType,
        byte[] someData)
        throws IllegalUseException
    {
        super(sendingSession, contentType, someData);
    }

    /**
     * Constructor used internally
     */
    protected OutgoingMessage()
    {

    }

    /**
     * This method is intended to abort a message's send process. If the message
     * is not being sent it won't be anymore. If the message is being sent, the
     * current SEND transaction will end with the # continuation-flag char and
     * further data belonging to the message will not be sent. On the other end,
     * if the message is being sent, receiving the # continuation-flag will
     * trigger a call to the abortedMessageEvent method on SessionListener
     * binded to the session.
     * 
     * @param reason Irrelevant for an OutgoingMessage
     * @param extraReasonInfo Irrelevant for an OutgoingMessage
     * 
     * @throws InternalErrorException If we have a failure on the sanity checks
     * 
     * @see SessionListener#abortedMessageEvent(javax.net.msrp.event.MessageAbortedEvent)
     */
    public void abort(int reason, String extraReasonInfo)
        throws InternalErrorException
    {
        logger.debug("Going to abort an OutgoingMessage, reason: " + reason +
        			" comment: " + extraReasonInfo);
        if (this.isComplete())			/* Sanity checks */
            throw new InternalErrorException(
            		"pause() called on a complete message!");
        if (session == null)
            throw new InternalErrorException(
				                "pause() called on message with no session.");
        TransactionManager transactionManager = session.getTransactionManager();
        if (transactionManager == null)
            throw new InternalErrorException(
	                "pause() called on message with no transaction manager.");

        aborted = true;			/* signal this message internally as aborted */

        /* remove from the list of messages to send in session */
        session.delMessageToSend(this);

        transactionManager.abortMessage(this);
    }

    /**
     * Returns the sent bytes determined by the offset of the data container
     * 
     * @return the number of sent bytes
     */
    public long getSentBytes()
    {
        return dataContainer.currentReadOffset();
    }

    /*
     * (non-Javadoc)
     * 
     * @see javax.net.msrp.Message#isComplete()
     */
    @Override
    public boolean isComplete()
    {
        return outgoingIsComplete(getSentBytes());
    }

    @Override
    public int getDirection()
    {
        return OUT;
    }

    public void validate() throws Exception
    {
    	;
    }
}
