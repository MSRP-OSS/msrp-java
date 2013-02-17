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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

import javax.net.msrp.exceptions.IllegalUseException;
import javax.net.msrp.exceptions.NotEnoughDataException;
import javax.net.msrp.exceptions.NotEnoughStorageException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An implementation of the data container class.
 * <p>
 * It uses a file read from/write to the data associated with an MSRP message
 * @see DataContainer
 * 
 * @author Jo�o Andr� Pereira Antunes 2008
 * 
 */
public class FileDataContainer
    extends DataContainer
{
	private static final String IOERR = "I/O problems: ";

	/** The logger associated with this class */
    private static final Logger logger =
        LoggerFactory.getLogger(FileDataContainer.class);

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
     * TODO: WORK IN PROGRESS field that will be used to optimise the writes
     * on the file so that we don't have too much of an overhead with IOs
     * 
     * currently used for all operations of file channel that require a ByteBuffer
     */
    private ByteBuffer auxByteBuffer;

    private final Object readOffsetLock = new Object();
    private Long currentReadOffset = new Long(0);

    /**
     * Creates a new DataContainer based on the given file, The file must be
     * readable and writable.
     * <p>
     * Note: if the file exists it's content will be overwritten
     * 
     * @throws FileNotFoundException if the file was not found
     * @throws SecurityException if a security manager exists and its checkRead
     *             method denies read access to the file or the mode is "rw" and
     *             the security manager's checkWrite method denies write access
     *             to the file
     */
    public FileDataContainer(File file)
        throws FileNotFoundException, SecurityException
    {
        this.file = file;
        randomAccessFile = new RandomAccessFile(file, "rw");
        fileChannel = randomAccessFile.getChannel();
        logger.trace("Created a FileDataContainer for file: " +
        			file.getAbsolutePath());
    }

    /**
     * Specific method to retrieve the File associated with this container
     * 
     * @return the File object, used to store the data
     */
    public File getFile()
    {
        return file;
    }

    /* (non-Javadoc)
     * @see javax.net.msrp.DataContainer#size()
     */
    @Override
    public long size()
    {
        try
        {
            return fileChannel.size();
        }
        catch (IOException e)
        {
        	logger.error(IOERR, e);
        }
        return 0;
    }

    /* (non-Javadoc)
     * @see javax.net.msrp.DataContainer#currentReadOffset()
     */
    @Override
    public long currentReadOffset()
    {
        synchronized (readOffsetLock)
        {
            return currentReadOffset.longValue();
        }
    }

    /* (non-Javadoc)
     * @see javax.net.msrp.DataContainer#hasDataToRead()
     */
    @Override
    public boolean hasDataToRead()
    {
        synchronized (readOffsetLock)
        {
            try
            {
                if (currentReadOffset.longValue() < fileChannel.size())
                    return true;
            }
            catch (IOException e)
            {
                logger.error(IOERR, e);
            }
            return false;
        }
    }

    /* (non-Javadoc)
     * @see javax.net.msrp.DataContainer#put(long, byte[])
     */
    @Override
    public void put(long startingIndex, byte[] dataToPut)
        throws NotEnoughStorageException,
        Exception
    {
        auxByteBuffer = ByteBuffer.wrap(dataToPut);
        fileChannel.write(auxByteBuffer, startingIndex);
    }

    /* (non-Javadoc)
     * @see javax.net.msrp.DataContainer#put(byte)
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

    /* (non-Javadoc)
     * @see javax.net.msrp.DataContainer#put(long, byte)
     * 
     * TODO: if needed use a small buffer in order to make writes more efficient.
     */
    @Override
    public void put(long startingIndex, byte byteToPut)
        throws NotEnoughStorageException, IOException
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

    @Override
    public void rewindRead(long nrPositions)
    {
        /*
         * It's only needed to change the value of the currentReadOffset
         * variable because the get method makes sure that the position of the
         * reader of the file is put into the position specified by that
         * variable prior to reading data from it
         */
        synchronized (readOffsetLock)
        {
            currentReadOffset -= nrPositions;
        }

    }

    @Override
    public int get(byte[] dst, int offset)
        throws IndexOutOfBoundsException,
        Exception
    {
        if (offset > dst.length - 1)
            throw new IndexOutOfBoundsException();
        synchronized (readOffsetLock)
        {
            int bytesToCopy = 0;
            long remainingDataBytes =
                fileChannel.size() - currentReadOffset.longValue();
            if (remainingDataBytes < dst.length - offset)
                bytesToCopy = (int) remainingDataBytes;
            else
                bytesToCopy = dst.length - offset;

            auxByteBuffer = ByteBuffer.allocate(bytesToCopy);
            int result = fileChannel.read(auxByteBuffer);
            if (result == 0 || result == -1)
                throw new NotEnoughDataException();
            if (result != bytesToCopy)
                throw new Exception(
                    "Something went wrong, it should have copied "
                        + bytesToCopy + " but instead copied " + result);

            System.arraycopy(auxByteBuffer.array(), 0, dst, offset, result);
            currentReadOffset += result;
            return result;
        }

    }

    /* (non-Javadoc)
     * @see javax.net.msrp.DataContainer#dispose()
     */
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
        	logger.error(IOERR, e);
        }
    }
}
