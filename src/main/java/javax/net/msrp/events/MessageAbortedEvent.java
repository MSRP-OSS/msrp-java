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
package javax.net.msrp.events;

import java.util.*;

import javax.net.msrp.*;

/**
 * Indicates an MSRP message has been aborted.
 * <br>
 * More details can be accessed through its methods
 * <p>
 * This class captures all the cases where an MSRP can be seen as being aborted.
 * Depending on the reasons, different actions should be performed
 * 
 * @author João André Pereira Antunes 2008
 */
public class MessageAbortedEvent
    extends EventObject
{
    private static final long serialVersionUID = 1L;

    /**
     * Message got a continuation flag of # meaning it was aborted
     */
    public static final int CONTINUATIONFLAG = 0;

    /**
     * The reason for this abort event
     */
    private int reason;

    /**
     * The reason for the Message abortion. This is equivalent to the comment of
     * the Status header in the formal syntax it is informational only as the
     * main reason is determined by the response code
     */
    private String extraReasonInfo;

    /**
     * The Session where the message was aborted
     */
    private Session session;

    /**
     * Constructor used to create the abort event
     * 
     * @param message the message that got aborted
     * @param session on session...
     * @param reason the reason, one of: CONTINUATIONFLAG; RC4XX
     * @param extraReasonInfo additional info-string as body of a REPORT or
     * 							null if it doesn't exist
     * @param transaction the Tx involved.
     * @see #CONTINUATIONFLAG
     */
    public MessageAbortedEvent(Message message, Session session, int reason,
        String extraReasonInfo, Transaction transaction)
    {
        super(message);
        this.reason = reason;
        this.extraReasonInfo = extraReasonInfo;
        this.session = session;
    }

    /**
     * @return The reason of the abort
     */
    public int getReason()
    {
        return reason;
    }

    /**
     * @return the message that got aborted
     */
    public Message getMessage()
    {
        return (Message) source;
    }

    /**
     * @return the extra abort-info or null if it wasn't given.
     */
    public String getReasonInfo()
    {
        return extraReasonInfo;
    }

    /**
     * @return the session associated with this abort-event
     */
    public Session getSession()
    {
        return session;
    }
}
