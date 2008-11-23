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

import java.net.ProtocolException;
import java.net.URI;

import msrp.exceptions.InternalErrorException;
import msrp.exceptions.NonValidSessionSuccessReportException;
import msrp.exceptions.ProtocolViolationException;

/**
 * Class used as a specific implementation of a Success report that comes from
 * the general transaction
 * 
 * @author João André Pereira Antunes 2008
 * 
 */
public class SuccessReport
    extends Transaction
{
    /**
     * This constructor creates a success report based on the success report
     * rules defined in the RFC
     * 
     * @param message the message associated to the report
     * @param session the session associated with the report
     * @param transaction the transaction that originally triggered this success
     *            report or null if the message is complete
     * @throws NonValidSessionSuccessReportException if the constructor was
     *             called without having a "valid" session
     * @throws InternalErrorException
     * @throws ProtocolViolationException
     * 
     */
    public SuccessReport(Message message, Session session,
        Transaction transaction)
        throws NonValidSessionSuccessReportException,
        InternalErrorException,
        ProtocolViolationException
    {
        /*
         * "Must check to see if session is valid" as specified in RFC4975 valid
         * being for now if it exists or not FIXME(?!)
         */
        if (session == null || !session.isActive())
        {
            throw new NonValidSessionSuccessReportException();
        }
        /* sanity checks: */
        if (!session.equals(message.getSession()))
            throw new InternalErrorException("Generating a success report: "
                + "the session and the one associated with the message differ!");

        if (transaction != null
            && transaction.getSuccessReport() != message.getSuccessReport())
            throw new ProtocolViolationException(
                "The value of success report of the originating transaction and of the message differ");

        if (!message.getSuccessReport())
            throw new InternalErrorException(
                "It was called the successreport constructor for a message that doesn't request success reports");
        /* end sanity checks */

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
                "Generating the success report, the total number of bytes of this message was unintialized");
        if (totalBytes == Message.UNKNWON)
        {
            header = header.concat("*\r\n");
        }
        else
            header = header.concat(totalBytes + "\r\n");

        /* Status header (rest of headers) */
        header = header.concat("Status: 000 200\r\n");

        headerBytes = header.getBytes(usascii);
        continuationFlagByte = ENDMESSAGE;
    }

    @Override
    protected byte get() throws InternalErrorException
    {
        if (offsetRead[HEADERINDEX] < headerBytes.length)
            return headerBytes[offsetRead[HEADERINDEX]++];
        else if (offsetRead[ENDLINEINDEX] <= (7 + tID.length() + 2))
            return getEndLineByte();
        throw new InternalErrorException(
            "Error the .get() of the transaction was called without available bytes to get");

    }

    @Override
    public boolean hasData()
    {
        if (offsetRead[HEADERINDEX] >= headerBytes.length
            && offsetRead[ENDLINEINDEX] > (7 + tID.length() + 2))
            return false;
        if (offsetRead[ENDLINEINDEX] > (7 + tID.length() + 2))
            return false;
        return true;
    }

}
