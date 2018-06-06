/* Copyright © João Antunes 2008
 This file is part of MSRP Java Stack.

    MSRP Java Stack is free software: you can redistribute it and/or modify
    it under the terms of the GNU Lesser General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    MSRP Java Stack is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Lesser General Public License for more details.

    You should have received a copy of the GNU Lesser General Public License
    along with MSRP Java Stack.  If not, see <http://www.gnu.org/licenses/>.

 */
package javax.net.msrp;

import java.util.Collection;
import java.util.HashMap;

import javax.net.msrp.exceptions.InternalErrorException;


/**
 * Abstract class used to allow a fair share of bandwidth between sessions that
 * are using the same connection
 * 
 * *NOTE* not used atm
 * 
 * @author João André Pereira Antunes 2008
 * 
 */
public abstract class ConnectionPrioritizer
{
    /**
     * Field that contains the list of sessions that have messages to send in
     * the queue (active?! sessions) and the total number of useful bytes
     * already sent by those sessions since they have been active
     */
    private final HashMap<Session, Long> sessionsTotalUsefulBytesSent =
        new HashMap<Session, Long>();

    /**
     * Method that registers the useful sent bytes and is also responsible of
     * swapping the messageBeingSent and call the TransactionManager
     * generateTransactions if the sending of the message is complete
     * 
     * if the message is complete and success report set to yes the message is
     * stored in order to be retrieved when the REPORT arrives
     * 
     * Also calls the unimplemented shouldSwap method
     * 
     * @param session Session where the message bytes are being sent
     * @param message Message the message for which the bytes are accounted for
     * @param numberBytesSent Long that has the number of bytes that were sent
     *            for this given message
     */
    protected final void accountSentBytes(Session session, OutgoingMessage message,
        int numberBytesSent)
    {
        Long numberBytesAccounted = sessionsTotalUsefulBytesSent.get(session);
        if (numberBytesAccounted == null)
            numberBytesAccounted = new Long(numberBytesSent);
        else
            numberBytesAccounted =
                new Long(numberBytesAccounted.longValue() + numberBytesSent);
        sessionsTotalUsefulBytesSent.put(session, numberBytesAccounted);

        Session sessionToRetrieveMessage;
        TransactionManager tm = session.getTransactionManager();
        if (message.isComplete())
        {
            sessionToRetrieveMessage = nextSession(tm.getAssociatedSessions());
            Message messageToSend = sessionToRetrieveMessage.getMessageToSend();
            tm.generateTransactionsToSend(messageToSend);

            // Store the sent message based on the success report
            if (message.wantSuccessReport())
                session.addSentOrSendingMessage(message);
        }
        else if (shouldSwap(tm.getAssociatedSessions(), session, message))
        {
            sessionToRetrieveMessage = nextSession(tm.getAssociatedSessions());
            Message messageToSend = sessionToRetrieveMessage.getMessageToSend();
            try
            {
                message.pause();
            }
            catch (InternalErrorException e)
            {
                // TODO log it
                e.printStackTrace();
            }
            tm.generateTransactionsToSend(messageToSend);
        }
    }

    /**
     * Method that decides if the session should be swapped or not
     * 
     * @param sessions  which ones
     * @param session   the one
     * @param message   to swap
     * @return true if the message should be swapped by another of another
     *         session false otherwise
     * Note:    it must return true only if there
     *          is another session with messages to be sent
     */
    protected abstract boolean shouldSwap(Collection<Session> sessions,
        Session session, Message message);

    /**
     * function that tells what should be the next session to be served the next
     * session that should be served must have a message to send (FIXME TODO the
     * initialize method of the transaction manager)
     * 
     * @param sessions the collection of the sessions associated with this
     *            connection to choose from
     * @return Session that must have at least one message to send to which the
     *         next message will be sent from
     */
    protected abstract Session nextSession(Collection<Session> sessions);

}
