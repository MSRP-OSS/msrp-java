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

import java.nio.ByteBuffer;

import msrp.exceptions.IllegalUseException;
import msrp.exceptions.NotEnoughDataException;
import msrp.exceptions.NotEnoughStorageException;

/**
 * Class used to provide abstraction to the container of the actual data on the
 * message Also has a mechanism to allow the validation of the received data
 * 
 * @author João André Pereira Antunes 2008
 * 
 */
public abstract class DataContainer
{

    /**
     * field used to compose the validator of the content type to this class
     * 
     * The validator should act as an interface and it's WORK IN PROGRESS, the
     * main idea is that this class will be responsible for validating the
     * content type contained by this data container
     * 
     * The validator makes more sense in a receiving data context although it
     * can be used while sending the data
     */
    public Validator validator = null;

    /**
     * Final method that allows one to add the given validator to be used in
     * order to validate this message content
     * 
     * @param validator
     */
    public final void setValidator(Validator validator)
    {
        this.validator = validator;
    }

    /**
     * Method used to retrieve the data from the data container
     * 
     * @return a byte of data
     * @throws NotEnoughDataException if the request couldn't be satisfied due
     *             to the fact that there isn't anymore available data to
     *             retrieve
     * @throws Exception if there was any other kind of Exception
     */
    public abstract byte get() throws NotEnoughDataException, Exception;

    /**
     * Method used to retrieve the data from the data container
     * 
     * method not advisable to use when retrieving data from a file
     * 
     * @param offsetIndex the offset index to start reading the data
     * @param size the number of bytes to read or zero to get all the remaining
     *            data counting from the offset position
     * @return a Byte Buffer containing a copy of the requested data. To note:
     *         one can write in this byte buffer without altering the actual
     *         content;
     * @throws NotEnoughDataException if the request couldn't be satisfied due
     *             to the fact that there isn't enough available data to
     *             retrieve as requested
     * @throws Exception if there was any other kind of Exception
     * @throws IllegalUseException if the number of bytes to retrieve is bigger
     *             than the fixed limit of MAXIMUMNUMBERBYTES
     */
    public abstract ByteBuffer get(long offsetIndex, long size)
        throws NotEnoughDataException,
        IllegalUseException,
        Exception;

    /**
     * The maximum number of bytes that can be stored on memory.
     * 
     * this maximum number of bytes is defined by the MSRPStack short message
     * 
     * @see MSRPStack#setShortMessageBytes(int)
     *      MSRPStack.setShortMessageBytes(int)
     */
    public final int MAXIMUMNUMBERBYTES =
        MSRPStack.getInstance().getShortMessageBytes();

    /**
     * Convenience number 0 that used as size argument on the get operations
     * represents all of the remaining bytes
     */
    public final int ALLBYTES = 0;

    /**
     * Method used to retrieve the current number of bytes, also called the read
     * offset, of the given data container
     * 
     * @return the current offset (number of bytes
     */
    public abstract long currentReadOffset();

    /**
     * Puts the given data relative to the startingIndex position
     * 
     * @param startingIndex the given index to start putting the data on the
     *            appropriate data container
     * @param dataToPut the byte array to store starting at startingIndex
     * @throws NotEnoughStorageException if there isn't anymore storage
     *             available on this data Container
     * @throws Exception if there was any other kind of Exception
     */
    public abstract void put(long startingIndex, byte[] dataToPut)
        throws NotEnoughStorageException,
        Exception;

    /**
     * sequential put of byte
     * 
     * @param byteToPut the byte to put in the relative
     * @throws NotEnoughStorageException if there isn't anymore storage
     *             available on this data Container
     * @throws Exception if there was any other kind of Exception
     */
    public abstract void put(byte byteToPut)
        throws NotEnoughStorageException,
        Exception;

    /**
     * Method used to assert if this data container still has data available for
     * reading
     * 
     * @return true if this data container still has data to retrieve
     */
    public abstract boolean hasDataToRead();

    /**
     * Method used to dispose this data container resources. Frees up any memory
     * blocks of data reserved Should be called explicitly!
     */
    public abstract void dispose();

    /**
     * Puts the given single byte of data relative to the startingIndex position
     * 
     * @param startingIndex the given index to start putting the data on the
     *            appropriate data container
     * @param byteToPut the single byte to store starting at startingIndex
     * @throws NotEnoughStorageException if there isn't anymore storage
     *             available on this data Container
     * @throws Exception if there was any other kind of Exception
     */
    public abstract void put(long startingIndex, byte byteToPut)
        throws NotEnoughStorageException,
        Exception;

    /**
     * @return the number of bytes in this DataContainer
     */
    public abstract long size();

    /**
     * Method used to rewind the read buffer the supplied number of positions.
     * Example: a call to the {@link #get()} followed by a call to this function
     * with nrPositions = 1 followed by another call to {@link #get()} will
     * return the same value
     * 
     * @param nrPositions
     */
    public abstract void rewindRead(long nrPositions);

}
