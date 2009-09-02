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
package msrp;

import msrp.exceptions.ImplementationException;
import msrp.exceptions.InternalErrorException;

/**
 * A generic class that represents the transactions for the REPORT requests
 * 
 * @author João André Pereira Antunes
 * 
 */
public class ReportTransaction
    extends Transaction
{
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
    public int getData(byte[] outData, int offset)
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
            }
            if (!interrupted && (offsetRead[HEADERINDEX] >= headerBytes.length))
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
        if (offsetRead[HEADERINDEX] >= headerBytes.length)
            return false;
        if (interrupted)
            return false;
        return true;
    }

}
