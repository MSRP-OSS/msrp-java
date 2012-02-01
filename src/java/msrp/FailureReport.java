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

import java.net.*;

import msrp.*;
import msrp.exceptions.*;
import msrp.messages.*;

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
     * This constructor creates a failure report based on the failure report
     * rules defined in the RFC
     * 
     * @param message the message associated to the report
     * @param session the session associated with the report
     * @param transaction the transaction that originally triggered this success
     *            report or null if the message is complete
     * @param namespace the three digit namespace associated with the status
     * @param responseCode one of: 400; 403; 408; 413; 415; 423; 481; 501; 506
     * @param comment the optional comment as defined in RFC 4975 syntax. If
     *            null it's not included
     * @throws InternalErrorException if the objects used have invalid states or
     *             any other kind of internal error
     * @throws IllegalUseException if something like trying to send success
     *             reports on messages that don't want them is done or also if
     *             the arguments are invalid
     */
    public FailureReport(Message message, Session session,
        Transaction transaction, String namespace, int responseCode,
        String comment)
        throws IllegalUseException,
        InternalErrorException
    {
        /*
         * "Must check to see if session is valid" as specified in RFC4975 valid
         * being for now if it exists or not FIXME(?!)
         */
        if (session == null || !session.isActive())
        {
            throw new InternalErrorException("not a valid session: " + session);
        }
        /* sanity checks: */
        if (!session.equals(message.getSession()))
            throw new InternalErrorException("Generating a failure report: "
                + "the session and the one associated with the message"
                + " differ!");

        if (transaction != null
            && !transaction.getFailureReport().equals(
                message.getFailureReport()))
            throw new InternalErrorException(
                "The value of failure report of the originating transaction"
                    + " and of the message differ");

        if (message.getFailureReport().equals(Message.NO))
            throw new InternalErrorException(
                "It was called the failure report constructor for a message"
                    + " that explicitly doesn't want failure reports");
        /* end sanity checks */

        this.direction = OUT;
        offsetRead[HEADERINDEX] = offsetRead[ENDLINEINDEX] = 0;
        transactionType = TransactionType.REPORT;

        /*
         * Determine the byte-range to report of the message
         */
        Counter messageCounter = message.getCounter();
        /*
         * TODO fill the ID X field if we have the complete message there will
         * be no byte-range field on the message, only if we have parts of it ID
         * X
         */
        this.message = message;
        transactionManager = session.getTransactionManager();
        tID = transactionManager.generateNewTID();
        String header = "MSRP " + tID + " REPORT\r\n" + "To-Path:";
        URI[] toPathURIs = transaction.getFromPath();
        URI fromPath = session.getURI();

        for (int i = 0; i < toPathURIs.length; i++)
        {
            header = header.concat(" " + toPathURIs[i]);
        }
        header =
            header.concat("\r\n" + "From-Path: " + fromPath + "\r\n"
                + "Message-ID: " + message + "\r\n");

        /* Byte-Range: */
        /*
         * Only if the message isn't complete we will insert the Byte Range
         * header
         */
        long[] byteRange = transaction.getByteRange();
        header =
            header.concat("Byte-Range: 1" + "-"
                + message.getCounter().getNrConsecutiveBytes() + "/");
        long totalBytes = transaction.getTotalMessageBytes();
        if (totalBytes == Message.UNINTIALIZED)
            throw new InternalErrorException(
                "Generating the failure report, the total number of bytes of this message was unintialized");
        if (totalBytes == Message.UNKNWON)
        {
            header = header.concat("*\r\n");
        }
        else
            header = header.concat(totalBytes + "\r\n");

        /* Status header (rest of headers) */
        // TODO validate the namespace with a reg. exp. pattern in
        // RegexMSRPFactory. If it's not valid, send an IllegalUseException
        if (responseCode != 400 && responseCode != 403 && responseCode != 408
            && responseCode != 413 && responseCode != 415
            && responseCode != 423 && responseCode != 481
            && responseCode != 501 && responseCode != 506)
            throw new IllegalUseException("Wrong response code! it must be "
                + "a valid response code as defined in RFC 4975");
        header = header.concat("Status: " + namespace + " " + responseCode);
        if (comment == null)
            header = header.concat("\r\n");
        else
        {
            // TODO validate the comment with a reg. exp. pattern in
            // RegexMSRPFactory that validates that comment is utf8text, if it's
            // not, log it and use comment=null
            header = header.concat(" " + comment + "\r\n");
        }

        headerBytes = header.getBytes(usascii);
        continuationFlagByte = ENDMESSAGE;
    }

}
