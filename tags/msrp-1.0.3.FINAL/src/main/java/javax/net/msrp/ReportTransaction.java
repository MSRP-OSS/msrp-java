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

import javax.net.msrp.exceptions.IllegalUseException;
import javax.net.msrp.exceptions.ImplementationException;
import javax.net.msrp.exceptions.InternalErrorException;
import javax.net.msrp.utils.TextUtils;

/**
 * Represents the transaction for REPORT requests
 * 
 * @author João André Pereira Antunes
 */
public class ReportTransaction
    extends Transaction
{
	/** Construct a report to send.
	 * @param message the associated message to report on.
	 * @param session the originating session.
	 * @param transaction	the originating transaction.
	 * @throws InternalErrorException invalid arguments or object states.
	 * @throws IllegalUseException invalid parameter...
	 */
	public ReportTransaction(Message message, Session session, Transaction transaction)
							throws InternalErrorException, IllegalUseException
	{
        super(	transaction.getTransactionManager().generateNewTID(),
        		TransactionType.REPORT, transaction.getTransactionManager(), Direction.OUT);
        /* 
         * "Must check to see if session is valid" as specified in RFC4975 valid
         * being for now if it exists or not
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

        this.message = message;
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
         * TODO validate the comment in RegEx that
         * it is utf8text, if not, log it and skip comment
         */
        header.append("\r\nStatus: ").append(namespace).append(" ").append(statusCode);
        if (comment != null)
        	header.append(" ").append(comment);
        header.append("\r\n");

        headerBytes = header.toString().getBytes(TextUtils.utf8);
	}

    /* (non-Javadoc)
     * @see javax.net.msrp.Transaction#getData(byte[], int)
     */
    @Override
    public int getData(byte[] outData, int offset)
        throws	ImplementationException, IndexOutOfBoundsException,
        		InternalErrorException
    {
        if (interrupted && readIndex[ENDLINE] <= (7 + tID.length() + 2))
            throw new ImplementationException(
            		"Data already retrieved, should be retrieving endline");

        if (interrupted)
            throw new ImplementationException(
        			"Message interrupted, should be retrieving endline");

        int bytesCopied = 0;
        boolean stopCopying = false;
        int spaceRemainingOnBuffer = outData.length - offset;
        while ((bytesCopied < spaceRemainingOnBuffer) && !stopCopying)
        {
            if (offset > (outData.length - 1))
                throw new IndexOutOfBoundsException();

            if (readIndex[HEADER] < headerBytes.length)
            {							// if we are processing the header
                int bytesToCopy = 0;
                /*
                 * if the remaining bytes on outdata is smaller than
                 * remaining bytes on header, fill the outdata with that length
                 */
                if ((outData.length - offset) < (headerBytes.length - readIndex[HEADER]))
                    bytesToCopy = (outData.length - offset);
                else
                    bytesToCopy = (int) (headerBytes.length - readIndex[HEADER]);
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

    /* (non-Javadoc)
     * @see javax.net.msrp.Transaction#hasData()
     */
    @Override
    public boolean hasData()
    {
        if (readIndex[HEADER] >= headerBytes.length)
            return false;
        if (interrupted)
            return false;
        return true;
    }
}
