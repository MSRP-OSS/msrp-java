package javax.net.msrp;

import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;

import javax.net.msrp.exceptions.ParseException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/** Pre-parse incoming data.
 * Main goal is to separate header data from any -optional- content-stuff.
 * These are fed separately to the connection parser.
 */
class PreParser
{
    private static final Logger logger =
            LoggerFactory.getLogger(PreParser.class);

    private final Connection connection;

	/**
	 * @param connection the receiving stream.
	 */
	PreParser(Connection connection) {
		this.connection = connection;
	}

	/**
     * Is this instance currently receiving header data or content-stuff?
     */
    private boolean inContentStuff = false;

    private short preState = 0;

    /**
     * Save the possible start of end-line.
     * Max size:
     * 		2 bytes for CRLF after data;
     * 		7 for '-';
     * 		32 for transactid;
     * 		1 for continuation flag;
     * 		2 for closing CRLF
     * Total: 44 bytes.
     */
    private ByteBuffer wrapBuffer = ByteBuffer.allocate(44);

    /**
     * A tiny state machine to identify if 
     * the incomingData belongs to header-data or is content-stuff.
     * It is responsible for changing the value of inContentStuff
     * and then calling the actual parser (gluing data chunks together).
     * 
     * @param incomingData buffer containing received data
     * @param length number of bytes of received data
     * @throws ParseException an exception occurred
     *             calling the parser method of this class
     */
    void preParse(byte[] incomingData, int length)
        throws ParseException
    {
        ByteBuffer data = ByteBuffer.wrap(incomingData, 0, length);
        /*
         * Index of data already processed
         */
        int indexProcessed = 0;

        if (wrapBuffer.position() != 0)
        {
            /* in case we have data to prepend, prepend it */
            int bytesToPrepend = wrapBuffer.position();
            byte[] prependedData = new byte[(bytesToPrepend + length)];
            wrapBuffer.flip();
            wrapBuffer.get(prependedData, 0, bytesToPrepend);
            wrapBuffer.clear();
            data.get(prependedData, bytesToPrepend, length);
            /*
             * now substitute the buffer with prepended bytes but start
             * processing after the prepend.
             */
            data = ByteBuffer.wrap(prependedData);
            data.position(bytesToPrepend);
        }

        while (data.hasRemaining())
        {
            /*
             * 2 distinct points of start for the algorithm: headers or content-stuff
             */
            if (!inContentStuff)
            {					// hunt for 2CRLF (start of data in content-stuff)
                switch (preState)
                {
                case 0:
                    if (data.get() == '\r')
                        preState++;
                    break;
                case 1:
                    if (data.get() == '\n')
                        preState++;
                    else
                        reset(data);
                    break;
                case 2:
                    if (data.get() == '\r')
                        preState++;
                    else
                        reset(data);
                    break;
                case 3:
                    if (data.get() == '\n')
                    {
                        preState = 0;
                        connection.parser(data.array(), indexProcessed,
                            data.position() - indexProcessed,
                            inContentStuff);
                        if (connection.getCurrentIncomingTid() == null)
                        	throw new ParseException(
                        					"no transaction found");

                        indexProcessed = data.position();
                        inContentStuff = true;
                    }
                    else
                        reset(data);
                }
            }
            else					// data, hunt for end-line
            {
                switch (preState)
                {
                case 0:
                    if (data.get() == '\r')
                        preState++;
                    break;
                case 1:
                    if (data.get() == '\n')
                        preState++;
                    else
                        reset(data);
                    break;
                case 2:
                case 3:
                case 4:
                case 5:
                case 6:
                case 7:
                case 8:
                    if (data.get() == '-')
                        preState++;
                    else
                        reset(data);
                    break;
                default:
                    if (preState >= 9)
                    {
                        if (connection.getCurrentIncomingTid() == null)
                        	throw new ParseException(
                        					"no transaction found");

                        int tidSize = connection.getCurrentIncomingTid().length();
                        if (tidSize == (preState - 9))
                        {
                            /*
                             * End of Tx-id, look for valid continuation flag.
                             */
                            char incChar = (char) data.get();
                            if (incChar == '+' || incChar == '$' ||
                                incChar == '#')
                            {
                                preState++;
                            }
                            else
                                reset(data);
                        }
                        else if ((preState - 9) > tidSize)
                        {
                            if ((preState - 9) == tidSize + 1)
                            {		/* expect the CR here */
                                if (data.get() == '\r')
                                    preState++;
                                else
                                    reset(data);
                            }
                            else if ((preState - 9) == tidSize + 2)
                            {		/* expect the LF here */
                                if (data.get() == '\n')
                                {
                                    preState++;
                                    /*
                                     * body received so process all of the
                                     * data we have so far excluding CRLF
                                     * and "end-line" that later must be
                                     * parsed as text.
                                     */
                                    data.position(data.position() - preState);
                                    connection.parser(data.array(), indexProcessed,
                                		data.position() - indexProcessed,
                                		inContentStuff);
                                    indexProcessed = data.position();
                                    inContentStuff = false;
                                    preState = 0;
                                }
                                else
                                    reset(data);
                            }
                        }
                        else
                        {
                        	if ((tidSize > (preState - 9)) &&
                        		(data.get() == connection.getCurrentIncomingTid().charAt(preState - 9)))
                        		preState++;
                        	else
                        		reset(data);
                        }
                    }				// end of default:
                    break;
                }
            }
        }							// while (data.hasRemaining())
        /*
         * We scanned everything, process remaining data.
         * Exclude any end-line state (when scanning for end-line after
         * content-stuff) to be wrapped to next scan.
         */
        int endOfData = data.position();
        if (inContentStuff && preState != 0)
        {
            endOfData -= preState;		/* here we save the state */
            try
            {
                wrapBuffer.put(data.array(), endOfData, preState);
            }
            catch (BufferOverflowException e)
            {
            	logger.error(String.format(
        			"Error wrapping %d bytes (from[%d] to[%d])\nContent:[%s]",
        			preState, endOfData, data.position(),
                    new String(data.array()).substring(endOfData, data.position())
    			));
                throw e;
            }
        }
        connection.parser(	data.array(), indexProcessed,
        					endOfData - indexProcessed, inContentStuff);
    }

    /**
     * Rewind 1 position in given buffer (if possible) and reset state.
     * 
     * @param buffer the buffer to rewind
     */
    private void reset(ByteBuffer buffer)
    {
    	preState = 0;
        int position = buffer.position();
        if (position != 0)
        	buffer.position(position - 1);
    }
}