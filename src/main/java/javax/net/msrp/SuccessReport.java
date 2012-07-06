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

import javax.net.msrp.exceptions.*;

/**
 * Class used as a specific implementation of a Success report that comes from
 * the general transaction class.
 * 
 * @author João André Pereira Antunes 2008
 * 
 */
public class SuccessReport
    extends ReportTransaction
{
    /**
     * Create an outgoing success report based on the RFC rules.
     * 
     * @param message the message associated to the report
     * @param session the session associated with the report
     * @param transaction the originating transaction that triggered this report.
     * @param comment optional comment as defined in RFC 4975.
     * @throws InternalErrorException if the objects used have invalid states or
     *             any other kind of internal error.
     * @throws IllegalUseException if something like trying to send success
     *             reports on messages that don't want them.
     */
    public SuccessReport(Message message, Session session,
        Transaction transaction, String comment)
        throws IllegalUseException, InternalErrorException
    {
    	super(message, session, transaction);

    	if (transaction.wantSuccessReport() != message.wantSuccessReport())
            throw new InternalErrorException(
		                "Report request of the originating transaction "
		        		+ "differs from that of the message");

        if (!message.wantSuccessReport())
            throw new IllegalUseException(
                "Constructing a success report for a message that didn't want one?");

        makeReportHeader(transaction, "000", 200, comment);
    }
}
