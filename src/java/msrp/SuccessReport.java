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

import msrp.exceptions.ImplementationException;
import msrp.exceptions.InternalErrorException;
import msrp.exceptions.NonValidSessionSuccessReportException;
import msrp.exceptions.ProtocolViolationException;
import msrp.messages.Message;

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
    protected byte get() throws ImplementationException
    {
        if (offsetRead[HEADERINDEX] < headerBytes.length)
            return headerBytes[(int) offsetRead[HEADERINDEX]++];
        else if (offsetRead[ENDLINEINDEX] <= (7 + tID.length() + 2))
            try
            {
                return getEndLineByte();
            }
            catch (InternalErrorException e)
            {
                throw new ImplementationException(e);
            }
        throw new ImplementationException(
            "Error the .get() of the transaction was called without available bytes to get");

    }

    /**
     * Method that fills the given array with DATA (header and content excluding
     * end of line) bytes starting from offset and stopping at the array limit
     * or end of data and returns the number of bytes filled
     * 
     * @param outData the byte array to fill
     * @param offset the offset index to start filling the outData
     * @return the number of bytes filled
     * @throws ImplementationException if this function was called when there
     *             was no more data or if it was interrupted
     * @throws IndexOutOfBoundsException if the offset is bigger than the length
     *             of the byte buffer to fill
     * @throws InternalErrorException if something went wrong while trying to
     *             get this data
     */
    @Override
    public int get(byte[] outData, int offset)
        throws ImplementationException,
        IndexOutOfBoundsException,
        InternalErrorException
    {
        // sanity checks:
        if (interrupted && offsetRead[ENDLINEINDEX] <= (7 + tID.length() + 2))
        {
            // old line: FIXME to remove these lines if no problems are
            // encountered running the tests return getEndLineByte();
            throw new ImplementationException("The Transaction.get() "
                + "when it should have been the "
                + "Transaction.getEndLineByte");

        }
        if (interrupted)
        {
            throw new ImplementationException("The Transaction.get() "
                + "when it should have been the "
                + "Transaction.getEndLineByte");

        }
        // end of sanity checks
        int bytesCopied = 0;
        boolean stopCopying = false;
        int spaceRemainingOnBuffer = outData.length - offset;
        while ((bytesCopied < spaceRemainingOnBuffer) && !stopCopying)
        {

            if (offset > (outData.length - 1))
                throw new IndexOutOfBoundsException();

            if (offsetRead[HEADERINDEX] < headerBytes.length)
            { // if we are processing the header
                int bytesToCopy = 0;
                if ((outData.length - offset) < (headerBytes.length - offsetRead[HEADERINDEX]))
                    // if the remaining bytes on the outdata is smaller than the
                    // remaining bytes on the header then fill the outdata with
                    // that length
                    bytesToCopy = (outData.length - offset);
                else
                    bytesToCopy =
                        (int) (headerBytes.length - offsetRead[HEADERINDEX]);
                System.arraycopy(headerBytes, (int) offsetRead[HEADERINDEX],
                    outData, offset, bytesToCopy);
                offsetRead[HEADERINDEX] += bytesToCopy;
                bytesCopied += bytesToCopy;
                offset += bytesCopied;
                continue;
            }
            if (!interrupted && 
                 (offsetRead[HEADERINDEX] >= headerBytes.length))
                stopCopying = true;
        }

        return bytesCopied;
    }

    @Override
    public boolean hasData()
    {
        if (offsetRead[HEADERINDEX] >= headerBytes.length)
            return false;
        if (offsetRead[ENDLINEINDEX] > (7 + tID.length() + 2))
            return false;
        return true;
    }

}
