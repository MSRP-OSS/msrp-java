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

import javax.net.msrp.exceptions.*;

/**
 * Class used as a specific implementation of a Failure report that comes from
 * the general transaction
 * 
 * @author João André Pereira Antunes 2008
 * 
 */
public class FailureReport
    extends ReportTransaction
{
    /**
     * Create an outgoing failure report based on the RFC rules.
     * 
     * @param message the message associated to the report
     * @param session the session associated with the report
     * @param transaction the originating transaction triggering this report.
     * @param namespace the three digit namespace associated with the status
     * @param responseCode [400; 403; 408; 413; 415; 423; 481; 501; 506]
     * @param comment the optional comment as defined in RFC 4975.
     * @throws InternalErrorException if the objects used have invalid states or
     *             any other kind of internal error
     * @throws IllegalUseException if something like trying to send success
     *             reports on messages that don't want them is done.
     */
    public FailureReport(Message message, Session session, Transaction transaction,
    					String namespace, int responseCode, String comment)
        		throws IllegalUseException, InternalErrorException
    {
    	super(message, session, transaction);

    	if (!transaction.getFailureReport().equals(message.getFailureReport()))
            throw new InternalErrorException(
			                "Report request of originating transaction "
			        		+ "differs from that of the message");

        if (message.getFailureReport().equals(Message.NO))
            throw new IllegalUseException(
                "Constructing a failure report for a message"
                    + " that explicitly didn't want them?");

        if (!ResponseCode.isError(responseCode))
            throw new IllegalUseException("Wrong response code! Must be " +
            		"a valid code as defined in RFC 4975");

        makeReportHeader(transaction, namespace, responseCode, comment);
     }
}
