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
    private static final Logger logger = LoggerFactory.getLogger(Counter.class);

    /**
     * The ArrayList used to register the bytes
     */
    ArrayList<long[]> counter;

    private static final short STARTINGPOSITION = 0;

    private static final int VMIN = 0;

    private static final int NRBYTESPOS = 1;

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
        counter = new ArrayList<long[]>();

    }

    /**
     * Registers the given position as received updates the count number of
     * bytes and the nr of consecutive bytes private variables
     * 
     * @param startingPosition the index position, in the overall message
     *            content that this count refers to
     * @param numberBytes the offset regarding the startingPosition to which one
     *            should count the byte
     * @return true if there was a change on the number of contiguous bytes
     *         accounted for, or false otherwise
     */
    protected synchronized boolean register(long startingPosition,
        long numberBytes)
    {
        logger.trace("going to register: " + numberBytes
            + " received bytes starting at position:" + startingPosition);
        long previousValueConsecutiveBytes = nrConsecutiveBytes;
        long[] valueToRegister =
        { startingPosition, numberBytes };

        // Go through the list to find the position to insert the value
        ListIterator<long[]> counterIterator = counter.listIterator();
        boolean inserted = false;
        while (counterIterator.hasNext() && !inserted)
        {
            long[] toEvaluate = counterIterator.next();
            long vMaxExisting = toEvaluate[VMIN] + toEvaluate[NRBYTESPOS];
            long vMinToInsert = valueToRegister[0];
            long vMaxToInsert = vMinToInsert + valueToRegister[1];
            // case one - separate cluster before
            if (vMinToInsert < toEvaluate[VMIN]
                && vMaxToInsert < toEvaluate[VMIN])
            {
                logger.debug("Case one - separate cluster before - detected");
                // insert the new element before
                int indexToInsert = counterIterator.previousIndex();
                if (indexToInsert == -1)
                    counter.add(0, valueToRegister);
                else
                    counter.add(indexToInsert, valueToRegister);
                inserted = true;
                // add the second position to the count
                count += valueToRegister[NRBYTESPOS];
                // update the nr consecutive bytes if it's the case:
                if (vMinToInsert == 0)
                    nrConsecutiveBytes = valueToRegister[NRBYTESPOS];
                // end the cycle
                break;
            }// if (vMinToInsert < toEvaluate[VMIN] && vMaxtoInsert <
            // toEvaluate[VMIN])
            // case two - Intersects and extends it in the beginning
            else if (vMinToInsert < toEvaluate[VMIN]
                && vMaxExisting >= vMaxToInsert
                && vMaxToInsert >= toEvaluate[VMIN])
            {
                logger.debug("Case two - Intersects and extends it in the "
                    + " beginning - detected");
                valueToRegister[NRBYTESPOS] =
                    vMaxExisting - valueToRegister[VMIN];

                // replace the to evaluate with this one:
                counterIterator.remove();
                counterIterator.add(valueToRegister);
                inserted = true;
                count += (toEvaluate[VMIN] - valueToRegister[VMIN]);
                // update the nr consecutive bytes if it's the case:
                if (vMinToInsert == 0)
                    nrConsecutiveBytes = valueToRegister[NRBYTESPOS];
                // end the cycle
                break;
            }// else if (vMinToInsert < toEvaluate[VMIN] && toEvaluate[VMAX] >=
            // vMaxToInsert && vMaxToInsert >= toEvaluate[VMIN])
            // case three - Within an existing cluster
            else if (vMinToInsert >= toEvaluate[VMIN]
                && vMaxToInsert <= vMaxExisting)
            {
                logger.debug("Case three - Within an existing cluster - "
                    + "detected");
                // don't quite do anything, just break the cycle
                inserted = true;
                break;
            }
            // case four - Intersects and extends it further to the end
            else if (vMinToInsert >= toEvaluate[VMIN]
                && vMinToInsert <= vMaxExisting && vMaxToInsert >= vMaxExisting)
            {
                logger.debug("Case four - Intersects and extends it further "
                    + "to the end - detected");
                // remove the existing one and create the new one
                counterIterator.remove();
                valueToRegister[VMIN] = toEvaluate[VMIN];
                valueToRegister[NRBYTESPOS] =
                    vMaxToInsert - valueToRegister[VMIN];
                count -= toEvaluate[NRBYTESPOS];
                if (toEvaluate[VMIN] == 0)
                    nrConsecutiveBytes = 0;

                // go to the next iteration
                continue;

            }// else if (vMinToInsert >= toEvaluate[VMIN] && vMinToInsert <=
            // vMaxExisting && vMaxToInsert >= vMaxExisting )
            // case five - Separate cluster after
            else if (vMinToInsert > toEvaluate[VMIN]
                && vMinToInsert > vMaxExisting)
            // don't do anything
            {
                logger.debug("Case five - Separate cluster after - detected");
                // go to the next iteration
                continue;

            }// else if (vMinToInsert > toEvaluate[VMIN] && vMinToInsert >
            // vMaxExisting)
            else if (vMinToInsert < toEvaluate[VMIN]
                && vMaxToInsert > vMaxExisting)
            // case six - bigger new cluster
            {
                logger.debug("Case six - bigger new cluster - detected");
                // remove this one
                counterIterator.remove();
                count -= toEvaluate[NRBYTESPOS];
                if (toEvaluate[VMIN] == 0)
                    nrConsecutiveBytes = 0;
                continue;
            }
            else
            {
                logger.error("Counter algorithm serious error, please "
                    + "report this error to the developers");
            }
        }
        if (!inserted)
        {
            logger.debug("Not inserted - and cycle ended - detected");
            counter.add(valueToRegister);
            // update the number of counted and consecutive bytes
            count += valueToRegister[NRBYTESPOS];
            if (valueToRegister[VMIN] == 0)
                nrConsecutiveBytes = valueToRegister[NRBYTESPOS];
        }
        if (nrConsecutiveBytes != previousValueConsecutiveBytes)
            return true;
        return false;
    }

    /**
     * @return the count of bytes received
     */
    public synchronized long getCount()
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
    protected synchronized long getNrConsecutiveBytes()
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
    public synchronized boolean isComplete()
    {
        if (!dollarContinuationFlag)
            return false;

        /* Checking for holes */
        if (count != nrConsecutiveBytes)
            return false;
        return true;
    }
}
