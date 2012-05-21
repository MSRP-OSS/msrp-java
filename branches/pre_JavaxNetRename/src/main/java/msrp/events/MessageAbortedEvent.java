/*
 * Copyright © João Antunes 2009 This file is part of MSRP Java Stack.
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
package msrp.events;

import java.util.*;

import msrp.messages.*;

import msrp.*;

/**
 * The <tt>MessageAbortedEvent</tt> is the event indicating that a MSRP message
 * has been aborted. More details can be accessed through its methods
 * 
 * This class captures all the cases where an MSRP can be seen as being aborted.
 * Depending on the reasons, different actions should be performed
 * 
 * @author João André Pereira Antunes
 * 
 */
public class MessageAbortedEvent
    extends EventObject
{
    /**
     * 
     */
    private static final long serialVersionUID = 1L;

    /**
     * The message got a continuation flag of # that means it was aborted
     */
    public static final int CONTINUATIONFLAG = 0;

    /**
     * The message got a 400 response - request unintelligible - message can be
     * resent if the sender corrects the error
     */
    public static final int RESPONSE400 = 400;

    /**
     * The message got a 403 response - attempted action is not allowed - the
     * sender should not try the request again, therefore the Message was
     * aborted
     */
    public static final int RESPONSE403 = 403;

    /**
     * The message got a 413 response - the receiver wishes that the sender
     * stops sending this message
     */
    public static final int RESPONSE413 = 413;

    /**
     * The Message was wrapped in a Content/Media type not understood by the
     * receiver
     */
    public static final int RESPONSE415 = 415;

    /**
     * The session does not exist! the sender should terminate the session
     */
    public static final int RESPONSE481 = 481;

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
     * @param reason the reason, one of: CONTINUATIONFLAG; RESPONSE4XX
     * @see #CONTINUATIONFLAG
     * @see #RESPONSE400
     * @see #RESPONSE403
     * @see #RESPONSE413
     * @see #RESPONSE415
     * @see #RESPONSE481
     * @param extraReasonInfo this can be the string that can be on the body of
     *            a REPORT or null if it doesn't exist
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
     * 
     * The reason of the abort
     * 
     * @return the reason, one of:
     * @see #CONTINUATIONFLAG
     * @see #RESPONSE400
     * @see #RESPONSE403
     * @see #RESPONSE413
     * @see #RESPONSE415
     * @see #RESPONSE481
     */
    public int getReason()
    {
        return reason;
    }

    /**
     * Returns the message that got aborted
     * 
     * @return the message that got aborted
     */
    public Message getMessage()
    {
        return (Message) source;
    }

    /**
     * Returns the extra info that can be on the Status comment
     * 
     * @return the extra info or null if it doesn't exist
     */
    public String reasonInfo()
    {
        return extraReasonInfo;
    }

    /**
     * Returns the session associated with this abortion event
     * 
     * @return the session associated with this abor event
     */
    public Session getSession()
    {
        return session;
    }

}
