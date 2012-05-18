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
package msrp.messages;

import java.util.Vector;

import msrp.MSRPSessionListener;
import msrp.Session;
import msrp.Transaction;
import msrp.TransactionManager;
import msrp.TransactionType;
import msrp.exceptions.IllegalUseException;
import msrp.exceptions.InternalErrorException;

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
     * trigger a call to the abortedMessageEvent method on MSRPSessionListener
     * binded to the session.
     * 
     * @param reason Irrelevant for an OutgoingMessage
     * @param extraReasonInfo Irrelevant for an OutgoingMessage
     * 
     * @throws InternalErrorException If we have a failure on the sanity checks
     * 
     * @see MSRPSessionListener#abortedMessageEvent(msrp.event.MessageAbortedEvent)
     */
    public void abort(int reason, String extraReasonInfo)
        throws InternalErrorException
    {
        logger.debug("Going to abort an OutgoingMessage, reason: " + reason
            + " comment: " + extraReasonInfo);
        /*
         * Sanity checks:
         */
        if (this.isComplete())
            throw new InternalErrorException("The pause method"
                + " was called on a complete message!");
        if (session == null)
            throw new InternalErrorException(
                "The session on this message is null"
                    + " and the pause method was called upon it");
        TransactionManager transactionManager = session.getTransactionManager();
        if (transactionManager == null)
            throw new InternalErrorException("The transaction manager "
                + "associated with this message is null"
                + " and the pause method was called upon it");
        /*
         * End of sanity checks.
         */

        /* internally signal this message as aborted */
        aborted = true;

        /* remove this message from the list of messages to send of the session */
        session.delMessageToSend(this);

        /*
         * find the first transaction belonging to a SEND of this message and
         * abort it, remove eventually other transactions that serve the purpose
         * of sending this message
         */
        synchronized (transactionManager) {
	        Vector<Transaction> toSend =
	            transactionManager.getTransactionsToSend();
	        boolean firstTransactionFound = false;
	        for (Transaction t : toSend)
	        {
	            if (t.transactionType == TransactionType.SEND
	                && t.getMessage().equals(this))
	            {
	                logger.debug("Found transaction: " + t
	                			+ " associated with message[" + this + "]");
	                if (!firstTransactionFound)
	                {
	                    t.abort();
	                    firstTransactionFound = true;
	                }
	                else
	                {
	                	transactionManager.removeTransactionToSend(t);
	                }
	            }
	        }
        }
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
     * @see msrp.Message#isComplete()
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
