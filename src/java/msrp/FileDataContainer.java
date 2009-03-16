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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

import msrp.exceptions.IllegalUseException;
import msrp.exceptions.NotEnoughDataException;
import msrp.exceptions.NotEnoughStorageException;

/**
 * @author João André Pereira Antunes 2008
 * 
 */
public class FileDataContainer
    extends DataContainer
{

    /**
     * Creates a new DataContainer based on the given file, The file must be
     * readable and writable. Note: if the file exists it's content will be
     * overwritten
     * 
     * @throws FileNotFoundException if the file was not found
     * @throws SecurityException if a security manager exists and its checkRead
     *             method denies read access to the file or the mode is "rw" and
     *             the security manager's checkWrite method denies write access
     *             to the file
     */
    public FileDataContainer(File file)
        throws FileNotFoundException,
        SecurityException
    {
        this.file = file;
        randomAccessFile = new RandomAccessFile(file, "rw");
        fileChannel = randomAccessFile.getChannel();
    }

    private Long currentReadOffset = new Long(0);

    /**
     * WORK IN PROGRESS TODO(?!) field that will be used to optimize the writes
     * on the file so that we don't have too much of a overhead with IOs
     * 
     * currently used for all of the operations of file channel that require a
     * ByteBuffer
     */
    private ByteBuffer auxByteBuffer;

    /**
     * Original File reference
     */
    private File file;

    /**
     * Object that contains the file
     */
    private RandomAccessFile randomAccessFile;

    /**
     * The FileChannel used to read and write bytes
     */
    private FileChannel fileChannel;

    /*
     * (non-Javadoc)
     * 
     * @see msrp.DataContainer#currentReadOffset()
     */
    @Override
    public long currentReadOffset()
    {
        synchronized (currentReadOffset)
        {
            return currentReadOffset.longValue();
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see msrp.DataContainer#get()
     */
    @Override
    public byte get() throws NotEnoughDataException, IOException
    {

        synchronized (currentReadOffset)
        {

            long oldPosition = fileChannel.position();
            if (oldPosition != currentReadOffset.longValue())
            {
                fileChannel.position(currentReadOffset.longValue());
            }
            auxByteBuffer = ByteBuffer.allocate(1);
            int result = fileChannel.read(auxByteBuffer);
            if (result == 0 || result == -1)
                throw new NotEnoughDataException();

            currentReadOffset++;
            fileChannel.position(oldPosition);
            return auxByteBuffer.get(0);
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
        Exception
    {

        auxByteBuffer = ByteBuffer.wrap(dataToPut);
        fileChannel.write(auxByteBuffer, startingIndex);

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
        byte[] auxByteArray = new byte[1];
        auxByteArray[1] = byteToPut;
        auxByteBuffer = ByteBuffer.wrap(auxByteArray);
        int result = fileChannel.write(auxByteBuffer);
        if (result == 0 || result == -1)
            throw new NotEnoughStorageException();
    }

    @Override
    public boolean hasDataToRead()
    {
        synchronized (currentReadOffset)
        {
            try
            {
                if (currentReadOffset.longValue() < fileChannel.size())
                    return true;
            }
            catch (IOException e)
            {
                // TODO log it
                e.printStackTrace();
            }
            return false;
        }
    }

    @Override
    public void dispose()
    {
        try
        {
            fileChannel.close();
            randomAccessFile.close();
        }
        catch (IOException e)
        {
            // TODO log it
            e.printStackTrace();
        }

    }

    /**
     * TODO: if needed use a small buffer in order to make more efficient the
     * writes here!
     */
    @Override
    public void put(long startingIndex, byte byteToPut)
        throws NotEnoughStorageException,
        IOException
    {
        byte[] auxByteArray = new byte[1];
        auxByteArray[1] = byteToPut;
        auxByteBuffer = ByteBuffer.wrap(auxByteArray);
        int result = fileChannel.write(auxByteBuffer, startingIndex);
        if (result == 0 || result == -1)
            throw new NotEnoughStorageException();
    }

    @Override
    public ByteBuffer get(long offsetIndex, long size)
        throws NotEnoughDataException,
        IOException,
        IllegalUseException
    {
        if (size > MAXIMUMNUMBERBYTES)
            throw new IllegalUseException("Can't retrieve more than "
                + MAXIMUMNUMBERBYTES + "bytes to memory");
        if (size == ALLBYTES)
        {
            if ((fileChannel.size() - offsetIndex) > MAXIMUMNUMBERBYTES)
                throw new IllegalUseException("Can't retrieve more than "
                    + MAXIMUMNUMBERBYTES + "bytes to memory");
            auxByteBuffer =
                ByteBuffer.allocate((int) (fileChannel.size() - offsetIndex));
            int result =
                fileChannel.read(auxByteBuffer, auxByteBuffer.capacity());
            if (result == 0 || result == -1)
                throw new NotEnoughDataException();
            return auxByteBuffer;
        }
        else
        {
            auxByteBuffer = ByteBuffer.allocate((int) size);
            int result = fileChannel.read(auxByteBuffer, offsetIndex);
            if (result == -1 || result == 0 || result != size)
                throw new NotEnoughDataException();
            return auxByteBuffer;
        }

    }

    /**
     * Specific FileDataContainer method used to retrieve the File associated
     * with this container
     * 
     * @return the File object that was used to store the data
     */
    public File getFile()
    {
        return file;
    }

    @Override
    public long size()
    {
        try
        {
            return fileChannel.size();
        }
        catch (IOException e)
        {
            // TODO log it
            e.printStackTrace();
        }
        return 0;
    }

    @Override
    public void rewindRead(long nrPositions)
    {
        /*
         * It's only needed to change the value of the currentReadOffset
         * variable because the get method makes sure that the position of the
         * reader of the file is put into the position specified by that
         * variable prior to reading data from it
         */
        synchronized (currentReadOffset)
        {
            currentReadOffset -= nrPositions;
        }

    }
}
