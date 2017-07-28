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

import java.nio.ByteBuffer;

import javax.net.msrp.exceptions.IllegalUseException;
import javax.net.msrp.exceptions.NotEnoughDataException;
import javax.net.msrp.exceptions.NotEnoughStorageException;

/**
 * Provides an abstraction to the container of the actual data in the message.
 * <p>
 * Includes a mechanism to allow validation of the received data,
 * the validator.
 * 
 * @author João André Pereira Antunes 2008
 */
public abstract class DataContainer
{
    /**
     * The maximum number of bytes that can be stored in memory.
     * 
     * Is defined by the Stack short message
     * 
     * @see Stack#setShortMessageBytes(int)
     */
    public final int MAXIMUMNUMBERBYTES = Stack.getShortMessageBytes();

    /**
     * Convenience constant, used as size argument on the get operations
     * represents all of the remaining bytes
     */
    public final int ALLBYTES = 0;

    /* TODO: The validator should act as an interface, it's WORK IN PROGRESS.
     * The main idea is that this class will be responsible for validating the
     * content type contained by this data container
     * 
     * The validator makes more sense in a receiving data context although it
     * can be used while sending the data
     */
    /**
     * Holds the validator of the content type to this class
     */ 
    protected Validator validator = null;

    /**
     * Set the validator to be used in order to validate this message content
     * 
     * @param validator
     */
    public final void setValidator(Validator validator)
    {
        this.validator = validator;
    }

    /**
     * Retrieve data to fill the destination buffer or until
     * there is no more data
     * 
     * @param dst the byte array to fill
     * @param offset the offset where to start to fill the byte array
     * @return the number of bytes that got copied to dst
     * @throws IndexOutOfBoundsException if the offset is bigger than the length
     *             of the dst byte array
     * @throws Exception if there was any other kind of Exception
     */
    public abstract int get(byte[] dst, int offset)
        throws IndexOutOfBoundsException, Exception;

    /**
     * Retrieve data and fill the destination buffer or until
     * there is no more data
     * 
     * @param dst the byte array to fill
     * @param offset the offset to start filling the byte array
     * @param limit the max limit to fill this array with.
     * @return the number of bytes that got copied to dst
     * @throws IndexOutOfBoundsException if the offset is bigger than the length
     *             of the dst byte array
     * @throws Exception if there was any other kind of Exception
     */
    public abstract int get(byte[] dst, int offset, int limit)
        throws IndexOutOfBoundsException, Exception;

    /**
     * Retrieve the data from the data container
     * <p>
     * not advisable to use when retrieving data from a file
     * 
     * @param offsetIndex the offset index to start reading the data
     * @param size the number of bytes to read or zero to get all the remaining
     *            data counting from the offset position
     * @return a Byte Buffer containing a copy of the requested data. To note:
     *         one can write in this byte buffer without altering the actual
     *         content;
     * @throws NotEnoughDataException if the request couldn't be satisfied due
     *             to the fact that there's less available data than requested.
     * @throws IllegalUseException if the number of bytes to retrieve is bigger
     *             than the fixed limit of MAXIMUMNUMBERBYTES
     * @throws Exception if there was any other kind of Exception
     */
    public abstract ByteBuffer get(long offsetIndex, long size)
        throws NotEnoughDataException, IllegalUseException, Exception;

    /**
     * Retrieve the current read offset of this container
     * 
     * @return the current read offset (bytes)
     */
    public abstract long currentReadOffset();

    /**
     * Stores the given data into this container.
     * 
     * @param startingIndex where to start putting the data in this container.
     * @param dataToPut the byte array to store.
     * @throws NotEnoughStorageException if there is no more storage
     *             available in this Container
     * @throws Exception if there was any other kind of Exception
     */
    public abstract void put(long startingIndex, byte[] dataToPut)
        throws NotEnoughStorageException, Exception;

    /**
     * Store the given byte.
     * 
     * @param byteToPut the byte to put in this container
     * @throws NotEnoughStorageException if there is no more storage
     *             available in this Container
     * @throws Exception if there was any other kind of Exception
     */
    public abstract void put(byte byteToPut)
        throws NotEnoughStorageException, Exception;

    /**
     * Stores the given byte.
     * 
     * @param startingIndex where to put the data in this container.
     * @param byteToPut the byte to store.
     * @throws NotEnoughStorageException if there is no more storage
     *             available in this Container
     * @throws Exception if there was any other kind of Exception
     */
    public abstract void put(long startingIndex, byte byteToPut)
        throws NotEnoughStorageException, Exception;

    /**
     * Anymore data available for reading?
     * 
     * @return true if this container still has data to retrieve
     */
    public abstract boolean hasDataToRead();

    /**
     * Dispose this containers' resources. Frees up any memory
     * blocks of data reserved.
     * <p>
     * Should be called explicitly!
     */
    public abstract void dispose();

    /**
     * @return the number of bytes in this DataContainer
     */
    public abstract long size();

    /**
     * Rewind the read buffer by the given number of positions.
     * <p>
     * Example: a call to the {@link #get()} followed by a call to this function
     * with nrPositions = 1 followed by another call to {@link #get()} will
     * return the same value
     * 
     * @param nrPositions number of positions to rewind.
     */
    public abstract void rewindRead(long nrPositions);
}
