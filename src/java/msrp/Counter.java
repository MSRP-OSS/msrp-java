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

import java.util.*;

import org.slf4j.*;


import msrp.messages.*;

/**
 * Class to abstract the counting of the received bytes of a message there are
 * two main types of messages, the ones with known max size and the ones without
 * known max size.
 * 
 * Also the class takes into consideration the fact that the reported bytes to
 * count can overlap
 * 
 * @author João André Pereira Antunes 2008
 * 
 */
public class Counter
{
    /**
     * The logger associated with this class
     */
    private static final Logger logger =
        LoggerFactory.getLogger(Counter.class);
    
    /**
     * The array used to register the bytes
     */
    private BitSet counter;
    
    private static final short STARTINGPOSITION = 0;
    
    /**
     * Stores the value of the index of the previous clear bit
     */
    private long indexPreviousClearBit;
    
    /**
     * Stores the number of bytes that the counter has
     */
    private long count = 0;
    
    /**
     * Stores the number of consecutive bytes from the start without "holes"
     */
    private long nrConsecutiveBytes = 0;




    /**
     * Creates the counter structure based on the message size
     * 
     * @param message
     */
    protected Counter(Message message)
    {
            counter = new BitSet();

    }

  
    /**
     * Registers the given position as received
     * updates the count number of bytes and the nr of consecutive bytes private variables
     * 
     * @param startingPosition the index position, in the overall message
     *            content that this count refers to
     * @param numberBytes the offset regarding the startingPosition to which one
     *            should count the byte
     * @return true if there was a change on the number of contiguous bytes accounted for,
     *         or false otherwise
     */
    protected boolean register(long startingPosition,
        int numberBytes)
    {
        logger.trace("going to register: " + numberBytes
            + " received bytes starting at position:" + startingPosition);

        int intStartingPosition = (int) startingPosition;
        counter.set(intStartingPosition, intStartingPosition + numberBytes);
        nrConsecutiveBytes = counter.nextClearBit(STARTINGPOSITION); 
        boolean toReturn;
        if (indexPreviousClearBit != nrConsecutiveBytes)
            toReturn = true;
        else
            toReturn = false;
        indexPreviousClearBit = nrConsecutiveBytes;
        //update the count and nrConsecutiveBytes:
        count = counter.cardinality();
        return toReturn;
    }

    /**
     * @return the count of bytes received
     */
    public long getCount()
    {
        return count;
    }

    /**
     * This method gives the number of consecutive received bytes. e.g. if the
     * bitset is: 111011 this method will return 3;
     * 
     * @return the number of consecutive received bytes counted from the
     *         beginning.
     */
    protected long getNrConsecutiveBytes()
    {
        return nrConsecutiveBytes;
    }

    /**
     * field that is used to register the receipt or not of the end of message
     * continuation flag
     */
    private boolean dollarContinuationFlag = false;

    /**
     * Method used to notify the counter the receipt of $ continuation flag
     * (which doesn't mean that the message is fully received due to the fact
     * that the transactions could have been received in a different order *as
     * in RFC*)
     */
    protected void receivedEndOfMessage()
    {
        dollarContinuationFlag = true;
    }

    /**
     * @return true if we have a complete message false otherwise (note: in
     *         order for a message to be complete one needs to have received the
     *         $ continuation flag and the message must not have "holes" in it)
     */
    public boolean isComplete()
    {
        if (!dollarContinuationFlag)
            return false;
        
        /* Checking for holes */
        if (count != nrConsecutiveBytes)
            return false;
        return true;
    }
}
