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
     * Creates the counter structure based on the message size
     * 
     * @param message
     */
    protected Counter(Message message)
    {
        count = 0;
        if (message.getSize() == Message.UNKNWON)
        {
            counter = new short[STEPDYNAMICCOUNTER];
            fixedCapacity = false;
        }
        else
        {
            counter = new short[(int) message.getSize()];
            fixedCapacity = true;
        }

    }

    /**
     * The array used to register the bytes
     */
    private short counter[];

    /**
     * the total number of bytes received by this counter
     */
    private long count;

    private static final short EXISTS = 1;

    /**
     * if this isn't a fixed capacity counter this is the counter's initial
     * capacity and subsequent capacity augments will be done by adding this
     * number to the counter's capacity
     */
    private static final int STEPDYNAMICCOUNTER = 1024;

    /**
     * tells if this counter has fixedCapacity or if it should be dynamic
     */
    private boolean fixedCapacity;

    /**
     * Registers the given position as received
     * 
     * @param startingPosition the index position, in the overall message
     *            content that this count refers to
     * @param numberBytes the offset regarding the startingPosition to which one
     *            should count the byte
     * @return true if there was a change on the number of bytes accounted for,
     *         or false otherwise
     */
    protected boolean register(long startingPosition, int numberBytes)
    {
        int indexStartingPosition = (int) startingPosition;

        if (!fixedCapacity && counter.length < (startingPosition + numberBytes))
        {
            /* if we reached the limit of this counter: */
            /*
             * Create the new counter given by the new found limit and add more
             * STEPDYNAMICCOUNTER
             */
            short[] newCounter =
                new short[(int) (startingPosition + numberBytes + STEPDYNAMICCOUNTER)];
            /*
             * Copy the old counter contents to the new one
             */
            for (int i = 0; i < counter.length; i++)
                newCounter[i] = counter[i];

            /*
             * replace the old counter
             */
            counter = newCounter;
        }
        boolean toReturn = false;
        for (int i = 0; i < numberBytes; i++)
        {
            
            if (counter[(int) (i+indexStartingPosition)] != EXISTS)
            {
                count++;
                toReturn=true;
            }
            counter[(int) (i + indexStartingPosition)] = EXISTS;
        }

        return toReturn;
    }

    /**
     * @return the count of bytes received
     */
    protected long getCount()
    {
        return count;
    }
    
    /**
     * This method gives the number of consecutive received bytes.
     * e.g. if the counter array is: 111011 this method will return 3;
     * @return the number of consecutive received bytes counted from the beginning.
     */
    protected long getNrConsecutiveBytes() 
    {
        int i;
        for (i=0; i < counter.length; i++)
            if (counter[i] != EXISTS)
                return i;
        return i;
        
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
    protected boolean isComplete()
    {
        if (!dollarContinuationFlag)
            return false;
        /* Checking for holes */
        if (fixedCapacity)
        {
            for (int i=0;i<counter.length;i++)
            {
                if (counter[i] != EXISTS)
                    return false;
            }
            return true;
        }
        else
        {
            for (int i=0;i<count;i++)
            {
                if (counter[i] != EXISTS)
                    return false;
            }
            return true;
        }
    }
}
