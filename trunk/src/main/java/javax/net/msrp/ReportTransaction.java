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
package javax.net.msrp;

import java.net.URI;

import javax.net.msrp.exceptions.ImplementationException;
import javax.net.msrp.exceptions.InternalErrorException;
import javax.net.msrp.messages.Message;
import javax.net.msrp.utils.TextUtils;


/**
 * A class that inherits from the Transaction to represent the transactions for
 * the REPORT requests
 * 
 * @author João André Pereira Antunes
 * 
 */
public class ReportTransaction
    extends Transaction
{
	/** Construct a report to send.
	 * @param message the associated message to report on.
	 * @param session the originating session.
	 * @param transaction	the originating transaction.
	 * @throws InternalErrorException invalid arguments or object states.
	 */
	public ReportTransaction(Message message, Session session, Transaction transaction)
							throws InternalErrorException
	{
        /*
         * "Must check to see if session is valid" as specified in RFC4975 valid
         * being for now if it exists or not FIXME(?!)
         */
        if (session == null || !session.isActive())
            throw new InternalErrorException("not a valid session: " + session);

        if (!session.equals(message.getSession()))
            throw new InternalErrorException("Generating report: this session"
            				+ "and associated message session differ!");

        if (transaction == null ||
    		transaction.getTotalMessageBytes() == Message.UNINTIALIZED)
            throw new InternalErrorException(
    			"Invalid argument or in generating a report, the total number "
        		+ "of bytes of this message was unintialized");

        direction = OUT;
        readIndex[HEADER] = readIndex[ENDLINE] = 0;
        transactionType = TransactionType.REPORT;

        this.message = message;
        transactionManager = session.getTransactionManager();
        tID = transactionManager.generateNewTID();
        continuation_flag = FLAG_END;
	}

	/** Utility routine to make an outgoing report.
	 * @param transaction	the originating transaction to report on
	 * @param namespace		status namespace
	 * @param statusCode	status code to return
	 * @param comment		optional comment for the status field.
	 */
	protected void makeReportHeader(Transaction transaction, String namespace,
								int statusCode, String comment)
	{
		StringBuilder header = new StringBuilder(256);

        header.append("MSRP ").append(tID).append(" REPORT\r\nTo-Path:");

        URI[] toPathURIs = transaction.getFromPath();
        for (int i = 0; i < toPathURIs.length; i++)
        {
            header.append(" ").append(toPathURIs[i]);
        }
        header	.append("\r\nFrom-Path: ").append(message.getSession().getURI())
        		.append("\r\nMessage-ID: ").append(message.getMessageID());

        long totalBytes = transaction.getTotalMessageBytes();
        header.append("\r\nByte-Range: 1-").append(message.getCounter()
        		.getNrConsecutiveBytes()).append("/");
        if (totalBytes == Message.UNKNOWN)
            header.append("*");
        else
            header.append(totalBytes);
        /*
         * TODO validate the comment with a regex in RegexMSRPFactory that
         * validates the comment is utf8text, if not, log it and skip comment
         */
        header.append("\r\nStatus: ").append(namespace).append(" ").append(statusCode);
        if (comment != null)
        	header.append(" ").append(comment);
        header.append("\r\n");

        headerBytes = header.toString().getBytes(TextUtils.utf8);
	}

	@Override
    protected byte get() throws ImplementationException
    {
        if (readIndex[HEADER] < headerBytes.length)
            return headerBytes[(int) readIndex[HEADER]++];
        else if (readIndex[ENDLINE] <= (7 + tID.length() + 2))
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
    public int getData(byte[] outData, int offset)
        throws	ImplementationException, IndexOutOfBoundsException,
        		InternalErrorException
    {
        // sanity checks:
        if (interrupted && readIndex[ENDLINE] <= (7 + tID.length() + 2))
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

            if (readIndex[HEADER] < headerBytes.length)
            { // if we are processing the header
                int bytesToCopy = 0;
                if ((outData.length - offset) < (headerBytes.length - readIndex[HEADER]))
                    // if the remaining bytes on the outdata is smaller than the
                    // remaining bytes on the header then fill the outdata with
                    // that length
                    bytesToCopy = (outData.length - offset);
                else
                    bytesToCopy =
                        (int) (headerBytes.length - readIndex[HEADER]);
                System.arraycopy(headerBytes, (int) readIndex[HEADER],
                    outData, offset, bytesToCopy);
                readIndex[HEADER] += bytesToCopy;
                bytesCopied += bytesToCopy;
                offset += bytesCopied;
            }
            if (!interrupted && (readIndex[HEADER] >= headerBytes.length))
                stopCopying = true;
        }
        return bytesCopied;
    }

    @Override
    /**
     * @return true if still has data, excluding end of line, false otherwise
     */
    public boolean hasData()
    {
        if (readIndex[HEADER] >= headerBytes.length)
            return false;
        if (interrupted)
            return false;
        return true;
    }
}
