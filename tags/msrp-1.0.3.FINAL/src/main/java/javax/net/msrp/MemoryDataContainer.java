/*
 * Copyright � Jo�o Antunes 2008 This file is part of MSRP Java Stack.
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

import java.io.IOException;
import java.nio.BufferOverflowException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;

import javax.net.msrp.exceptions.IllegalUseException;
import javax.net.msrp.exceptions.NotEnoughDataException;
import javax.net.msrp.exceptions.NotEnoughStorageException;


/**
 * 
 * An implementation of the data container class.
 * 
 * It uses a file read from/write to the data associated with an MSRP message
 * 
 * @see DataContainer 
 * 
 * @author Jo�o Andr� Pereira Antunes 2008
 * 
 */
public class MemoryDataContainer
    extends DataContainer
{
    private ByteBuffer	byteBuffer;
    private byte[]		content;

    /**
     * Creates a blank DataContainer that stores the data in memory
     * 
     * @param size the maximum size of the data
     */
    public MemoryDataContainer(int size)
    {
        content = new byte[size];
        byteBuffer = ByteBuffer.wrap(content);
    }

    /**
     * Creates a DataContainer using the given data byte array
     * 
     * @param data the byte[] containing the data
     */
    public MemoryDataContainer(byte[] data)
    {
        content = data;
        byteBuffer = ByteBuffer.wrap(content);
    }

    /* (non-Javadoc)
     * @see javax.net.msrp.DataContainer#size()
     */
    @Override
    public long size()
    {
        return content.length;
    }

    /* (non-Javadoc)
     * @see javax.net.msrp.DataContainer#currentReadOffset()
     */
    @Override
    public long currentReadOffset()
    {
        return byteBuffer.position();
    }

    /* (non-Javadoc)
     * @see javax.net.msrp.DataContainer#hasDataToRead()
     */
    @Override
    public boolean hasDataToRead()
    {
        if (byteBuffer == null)
            return false;
        return byteBuffer.hasRemaining();
    }

    /* (non-Javadoc)
     * @see javax.net.msrp.DataContainer#put(long, byte[])
     */
    @Override
    public void put(long startingIndex, byte[] dataToPut)
        throws NotEnoughStorageException, IOException
    {
        try
        {
            byteBuffer.position((int) startingIndex);
            byteBuffer.put(dataToPut, 0, dataToPut.length);
        }
        catch (BufferOverflowException e)
        {
            throw new NotEnoughStorageException("Putting " + dataToPut.length +
                " bytes of data starting in " + startingIndex +
                " on a buffer with " + byteBuffer.capacity(), e);
        }
    }

    /* (non-Javadoc)
     * @see javax.net.msrp.DataContainer#put(byte)
     */
    @Override
    public void put(byte byteToPut)
        throws NotEnoughStorageException,
        IOException
    {
        try
        {
            byteBuffer.put(byteToPut);
        }
        catch (BufferOverflowException e)
        {
            throw new NotEnoughStorageException(e);
        }
    }

    /* (non-Javadoc)
     * @see javax.net.msrp.DataContainer#put(long, byte)
     */
    @Override
    public void put(long startingIndex, byte byteToPut)
        throws NotEnoughStorageException
    {
        if (startingIndex < 0)
            throw new IllegalArgumentException("Starting index should be >= 0");
        try
        {
            byteBuffer.put((int) startingIndex, byteToPut);
        }
        catch (IndexOutOfBoundsException e)
        {
            throw new NotEnoughStorageException();
        }
    }

    /* (non-Javadoc)
     * @see javax.net.msrp.DataContainer#get(long, long)
     */
    @Override
    public ByteBuffer get(long offsetIndex, long size)
        throws NotEnoughDataException, IllegalUseException, IOException
    {
        if (size < 0 || offsetIndex < 0)
            throw new IllegalUseException("negative size or index");
        try
        {
            byte[] newByteArray;
            ByteBuffer auxByteBuffer = byteBuffer.duplicate();
            if (size == 0)
            {
                newByteArray = content.clone();
            }
            else
            {
                newByteArray = new byte[(int) size];
                auxByteBuffer.position((int) offsetIndex);
                auxByteBuffer.get(newByteArray);
            }
            ByteBuffer newByteBuffer = ByteBuffer.wrap(newByteArray);
            return newByteBuffer;
        }
        catch (IllegalArgumentException e)
        {
            throw new NotEnoughDataException(e);
        }
        catch (BufferUnderflowException e)
        {
            throw new NotEnoughDataException(e);
        }
    }

    /* (non-Javadoc)
     * @see javax.net.msrp.DataContainer#get(byte[], int)
     */
    @Override
    public int get(byte[] dst, int offset)
        throws IndexOutOfBoundsException,
        Exception
    {
        int bytesToCopy = 0;
        if (byteBuffer.remaining() < dst.length - offset)
            bytesToCopy = byteBuffer.remaining();
        else
            bytesToCopy = dst.length - offset;
        byteBuffer.get(dst, offset, bytesToCopy);
        return bytesToCopy;
    }

    /* (non-Javadoc)
     * @see javax.net.msrp.DataContainer#rewindRead(long)
     */
    @Override
    public void rewindRead(long nrPositions)
    {
        byteBuffer.position((int) (byteBuffer.position() - nrPositions));
    }

    /* (non-Javadoc)
     * @see javax.net.msrp.DataContainer#dispose()
     */
    @Override
    public void dispose()
    {
        content = null;
        byteBuffer = null;
    }
}
