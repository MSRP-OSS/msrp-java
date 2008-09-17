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
package msrp;

import msrp.exceptions.IllegalUseException;
import msrp.exceptions.ImplementationException;

/**
 * This class is used to generate incoming messages
 * 
 * @author João André Pereira Antunes 2008
 * 
 */
public class IncomingMessage
    extends Message
{

    /**
     * @param session
     * @param messageId
     * @param contentType
     * @param size
     * @param reportMechanism
     */
    public IncomingMessage(Session session, String messageId,
        String contentType, long size, ReportMechanism reportMechanism)
    {
        this(session, messageId, contentType, size);
        constructorAssociateReport(reportMechanism);
    }

    /**
     * Constructor called internally when receiving an incoming message used by
     * the IncomingMessage class
     * 
     * @param session
     * @param messageId
     * @param contentType
     * @param reportMechanism the report mechanism to be used for this message
     *            or null to use the default one associated with the session
     * @param size the size of the incoming message (should be bigger than -2
     *            and -1 for unknown (*) total size
     */
    public IncomingMessage(Session session, String messageId,
        String contentType, long size)
    {
        super();
        if (size <= UNINTIALIZED)
            try
            {
                throw new ImplementationException(
                    "Error! constructor of message "
                        + "called with an invalid size field");
            }
            catch (ImplementationException e)
            {
                e.printStackTrace();
            }

        this.size = size;
        this.session = session;
        constructorAssociateReport(reportMechanism);
        this.messageId = messageId;
        this.contentType = contentType;
    }

    /**
     * Method used to reject an incoming message
     * 
     * @param code one of 400, 403, 408, 413, 415. (see RFC 4975 for details on
     *            the meaning of each one)
     * @throws IllegalUseException if this method was called with an invalid
     *             response code
     */
    public void reject(int code) throws IllegalUseException
    {
        switch (code)
        {
        case 403:
        case 400:
        case 413:
        case 415:
            result = code;
            break;
        default:
            throw new IllegalUseException(
                "Non-valid response code given when calling Message.reject(code)");

        }
    }
    /**
     * Method that uses the associated counter of this message to assert if the
     * message is complete or not
     * 
     * @return true if the message is complete, false otherwise
     */
    @Override
    public boolean isComplete()
    {
        return getCounter().isComplete();
    }

    /**
     * Contains the response code of the accept hook call
     */
    protected int result = 413;

}
