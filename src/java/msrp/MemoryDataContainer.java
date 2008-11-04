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

import java.io.IOException;
import java.nio.BufferOverflowException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;

import msrp.exceptions.IllegalUseException;
import msrp.exceptions.NotEnoughDataException;
import msrp.exceptions.NotEnoughStorageException;

/**
 * @author João André Pereira Antunes 2008
 * 
 */
public class MemoryDataContainer
    extends DataContainer
{

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
     * Creates a DataContainer used to interface with the given data byte array
     * 
     * @param data the byte[] containing the data
     */
    public MemoryDataContainer(byte[] data)
    {
        content = data;
        byteBuffer = ByteBuffer.wrap(content);
    }

    private ByteBuffer byteBuffer;

    /**
     * The content of this DataContainer
     */
    private byte[] content;

    /*
     * (non-Javadoc)
     * 
     * @see msrp.DataContainer#currentReadOffset()
     */
    @Override
    public long currentReadOffset()
    {
        return byteBuffer.position();
    }

    /*
     * (non-Javadoc)
     * 
     * @see msrp.DataContainer#get()
     */
    @Override
    public byte get() throws NotEnoughDataException, IOException
    {
        try
        {
            return byteBuffer.get();
        }
        catch (BufferUnderflowException e)
        {
            throw new NotEnoughDataException(e);
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see msrp.DataContainer#put(int, byte[])
     */
    @Override
    public void put(long startingIndex, byte[] dataToPut)
        throws NotEnoughStorageException,
        IOException
    {
        try
        {
            byteBuffer.position((int) startingIndex);
            byteBuffer.put(dataToPut, 0, dataToPut.length);
        }
        catch (BufferOverflowException e)
        {
            throw new NotEnoughStorageException("Putting " + dataToPut.length
                + " bytes of data starting in " + startingIndex
                + " on a buffer with " + byteBuffer.capacity(), e);
        }

    }

    /*
     * (non-Javadoc)
     * 
     * @see msrp.DataContainer#put(byte)
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

    @Override
    public void dispose()
    {
        content = null;
        byteBuffer = null;

    }

    @Override
    public boolean hasDataToRead()
    {
        return byteBuffer.hasRemaining();
    }

    @Override
    public void put(long startingIndex, byte byteToPut)
        throws NotEnoughStorageException
    {
        if (startingIndex < 0)
            throw new IllegalArgumentException(
                "The starting index should be >=0");
        try
        {

            byteBuffer.put((int) startingIndex, byteToPut);
        }
        catch (IndexOutOfBoundsException e)
        {
            throw new NotEnoughStorageException();
        }

    }

    @Override
    public ByteBuffer get(long offsetIndex, long size)
        throws NotEnoughDataException,
        IllegalUseException,
        IOException
    {
        if (size < 0 || offsetIndex < 0)
            throw new IllegalUseException("negative size or offsetindex");
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

    @Override
    public long size()
    {
        return content.length;
    }
}
