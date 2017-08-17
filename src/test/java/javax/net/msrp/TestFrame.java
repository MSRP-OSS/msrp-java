/**
 * 
 */
package javax.net.msrp;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Properties;
import java.util.Random;

import javax.net.msrp.DataContainer;
import javax.net.msrp.FileDataContainer;
import javax.net.msrp.Stack;
import javax.net.msrp.MemoryDataContainer;
import javax.net.msrp.Session;
import javax.net.msrp.testutils.MockSessionListener;
import javax.net.msrp.utils.TextUtils;


import org.junit.After;
import org.junit.Before;
import org.junit.internal.ArrayComparisonFailure;

/**
 * Base abstract class for send/receive tests.
 * Basic connections are set-up/torn-down prior/after the actual tests.
 * Convenience routines are also supplied to fill data buffers and do standard
 * copy/send/receive operations.
 * @author tuijldert
 */
public abstract class TestFrame {
    static Random randomGenerator = new Random();

    InetAddress address;

    Session sendingSession;
    Session receivingSession;

    MockSessionListener sendingSessionListener =
        new MockSessionListener("Tx");

    MockSessionListener receivingSessionListener =
            new MockSessionListener("Rx");

    String tempFileDir;
    File tempFile;
    File receivingTempFile;

    OutgoingMessage outMessage = null;

    /** fill array with random data. Time and log when chunk is large
     * @param data the array to fill
     */
    static void fillText(byte[] data) {
    	fill(data, false);
    }

    static void fillBinary(byte[] data) {
    	fill(data, true);
    }

    static void fill(byte[] data, boolean withBinary) {
    	int size = data.length;
    	int kB = size / 1024;
    	Long now, then = 0L;
    	if (kB > 1023) {
	    	then = System.currentTimeMillis();
	    	System.out.print("Generating random data...");
    	}
    	if (withBinary)
    		randomGenerator.nextBytes(data);
    	else
    		TextUtils.generateRandom(data);
    	if (kB > 1023) {
     		now = System.currentTimeMillis();
    		System.out.println(String.format(
    				" done generating %d KB of data, took: %d ms", kB, now - then));
    	}
    }

	/**
	 * @param data
	 * @throws FileNotFoundException
	 * @throws IOException
	 */
	void fillTempFile(byte[] data, boolean withBinary)
	{
		@SuppressWarnings("resource")
        FileOutputStream stream = null;
		try
		{
			fill(data, withBinary);
			stream = new FileOutputStream(tempFile);
			stream.write(data);
            stream.flush();
		}
		catch (Exception e)
		{
            e.printStackTrace();
            fail(e.getMessage());
        }
		finally
		{
    		try { if (stream != null) stream.close(); } catch (Exception e2) { ; }
        }
	}

	void wait4Report(boolean wantSuccessReport)
	{
	    if (wantSuccessReport)
	    {
            synchronized (sendingSessionListener.successReportCounter)
            {
                try
                {
                    sendingSessionListener.successReportCounter.wait(1000L);
                }
                catch (InterruptedException e)
                {
                    ; /* ignore, let's just continue processing */
                }
            }
	    }
	}

	void wait4ComleteMessage()
	{
	    synchronized (receivingSessionListener.messageComplete)
	    {
	        try
	        {
	            receivingSessionListener.messageComplete.wait(2000L);
	        }
	        catch(InterruptedException e)
	        {
	            ;
	        }
	    }
	}

	@Before
    public void setUpConnection()
    {
        /* fetch the address to which the sessions are going to be bound: */
        Properties testProperties = new Properties();
        try
        {
            /* Set the limit to be of 30 MB of messages allowed in memory */
            Stack.setShortMessageBytes(30024 * 1024);
            try
            {
                testProperties.load(TestFrame.class
                    .getResourceAsStream("/test.properties"));
            }
            catch (Exception e1)
            {
                System.err.println(
                    "Test properties have not been defined, reverting to defaults... " +
                    "Sure you wanna continue?");
            }
            /*
             * When not given, address to use defaults to local loopback.
             */
            String addressString = testProperties.getProperty("address");
            address = InetAddress.getByName(addressString);
            sendingSession = new Session(false, false, address);
            receivingSession =
                new Session(false, false, sendingSession.getURI(), address);

            sendingSession.setListener(sendingSessionListener);
            receivingSession.setListener(receivingSessionListener);
            /*
             * checks if we want the temp files on a specific directory. if the
             * property doesn't exist the default dir used by the JVM is used
             */
            tempFileDir = testProperties.getProperty("tempdirectory");
            if (tempFileDir != null)
            {
                System.out.println("Using temporary file directory: "
                    + tempFileDir);
                tempFile =
                    File.createTempFile(Long.toString(System
                        .currentTimeMillis()), null, new File(tempFileDir));
            }
            else
                tempFile =
                    File.createTempFile(Long.toString(System
                        .currentTimeMillis()), null, null);

            receivingTempFile = null;
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    @After
    public void tearDownConnection()
    {
        /*
         * write thread could be long done while reading still continues,
         * allow for some delay before wrapping up 
         */
//        delay(500);
        Message m = receivingSessionListener.getReceiveMessage();
        if (m != null)
        	m.discard();
    	if (outMessage != null)
    		outMessage.discard();
    	sendingSession.tearDown();
    	receivingSession.tearDown();
        tempFile.delete();
        if (receivingTempFile != null)
        	receivingTempFile.delete();

        System.gc();
    }

    public byte[] memory2Memory(byte[] data, boolean wantSuccessReport)
    {
        return memory2Memory(data, wantSuccessReport, 0);
    }

    public byte[] memory2Memory(byte[] data, boolean wantSuccessReport, long chunkSize)
    {
        String testName =
        		Thread.currentThread().getStackTrace()[2].getMethodName();
    	Long startTime;
        try
        {
            outMessage =
                new OutgoingMessage("plain/text", data);
            outMessage.setSuccessReport(wantSuccessReport);
            if (chunkSize > 0)
                sendingSession.setChunkSize(chunkSize);
            sendingSession.sendMessage(outMessage);

            startTime = System.currentTimeMillis();
            triggerSendReceive(data);
            wait4Report(wantSuccessReport);
            wait4ComleteMessage();

            System.out.println(testName + "() took: " +
                    	(System.currentTimeMillis() - startTime) + " ms");

            ByteBuffer receivedByteBuffer =
                receivingSessionListener.getReceiveMessage().getDataContainer().get(0, 0);
            return receivedByteBuffer.array();
        }
        catch (Exception e)
        {
            e.printStackTrace();
            fail(e.getMessage());
        }
        return null;
    }

	/** send data from sending to receiving.
	 * @param data			the data to send. If null don't reserve a memory container..
	 * @throws IOException
	 * @throws InterruptedException
	 */
	void triggerSendReceive(byte[] data) throws IOException, InterruptedException {
		DataContainer dc = null;
		if (data != null)
			dc = new MemoryDataContainer(data.length);
		triggerSendReceive(dc);
	}

	void triggerSendReceive(DataContainer dc) throws IOException, InterruptedException {
		/* connect the two sessions: */
		ArrayList<URI> toPathSendSession = new ArrayList<URI>();
		toPathSendSession.add(receivingSession.getURI());

		sendingSession.setToPath(toPathSendSession);

		/* message should be transferred or in the process of... */

		/* make MockListener accept the message */
		if (dc != null)
			receivingSessionListener.setDataContainer(dc);
	    receivingSessionListener.setAcceptHookResult(true);
	    receivingSessionListener.triggerReception();

	    if (receivingSessionListener.getAcceptHookMessage() == null ||
		    receivingSessionListener.getAcceptHookSession() == null)
		    fail("The Mock didn't work, message not accepted");
	}

	public byte[] file2Memory(boolean wantSuccessReport)
    {
        String testName =
        		Thread.currentThread().getStackTrace()[2].getMethodName();
    	Long startTime;
        try
        {
            outMessage =
                new OutgoingMessage("plain/text", tempFile);
            outMessage.setSuccessReport(wantSuccessReport);
            sendingSession.sendMessage(outMessage);

            startTime = System.currentTimeMillis();
            triggerSendReceive((byte[]) null);
            wait4Report(wantSuccessReport);

            System.out.println(testName + "() took: " +
                    	(System.currentTimeMillis() - startTime) + " ms");

            ByteBuffer receivedByteBuffer =
                receivingSessionListener.getReceiveMessage().getDataContainer().get(0, 0);
            return receivedByteBuffer.array();
        }
        catch (Exception e)
        {
            e.printStackTrace();
            fail(e.getMessage());
        }
        return null;
    }

	public void file2File(boolean wantSuccessReport)
    {
        String testName =
        		Thread.currentThread().getStackTrace()[2].getMethodName();
    	Long startTime;
        try
        {
            outMessage =
                new OutgoingMessage("plain/text", tempFile);
            outMessage.setSuccessReport(wantSuccessReport);
            sendingSession.sendMessage(outMessage);

            /*
             * generate a new temporary file to receive data and wrap it on a
             * FileDataContainer:
             */
            if (tempFileDir != null)
                receivingTempFile = File.createTempFile("recv" +
	                        Long.toString(System.currentTimeMillis()), null,
	                        new File(tempFileDir));
            else
                receivingTempFile = File.createTempFile(
                		Long.toString(System .currentTimeMillis()), null, null);

            FileDataContainer fdc = new FileDataContainer(receivingTempFile);

            startTime = System.currentTimeMillis();
            triggerSendReceive(fdc);
            wait4Report(wantSuccessReport);

            System.out.println(testName + "() took: " +
                    	(System.currentTimeMillis() - startTime) + " ms");
        }
        catch (Exception e)
        {
            e.printStackTrace();
            fail(e.getMessage());
        }
    }

	/**
	 * @throws FileNotFoundException
	 * @throws IOException
	 * @throws ArrayComparisonFailure
	 */
	void assertFilesEqual() {
		try {
			/*
			 * compare the two files content byte by byte:
			 */
			FileInputStream originalFileStream = new FileInputStream(tempFile);
	
			/* Get the FileDataContainer's file from the received message: */
			FileDataContainer fdc = (FileDataContainer) receivingSessionListener
										.getReceiveMessage().getDataContainer();
	
			FileInputStream receivedFileStream = new FileInputStream(fdc.getFile());
	
			byte[] original = new byte[1024];
			byte[] copied = new byte[1024];
			int oBytes;
			int cBytes;
			do
			{
			    oBytes = originalFileStream.read(original);
			    cBytes = receivedFileStream.read(copied);
			    assertEquals("File's length differed:", oBytes, cBytes);
			    assertArrayEquals("File's content differed:", original, copied);
			}
			while (oBytes != -1 && cBytes != -1);

			assertTrue("Didn't reach end of file content at the same time, " +
						"file size/content differ", oBytes == -1 && cBytes == -1);

			/* free the resources: */
			receivedFileStream.close();
			originalFileStream.close();
		} catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
		}
	}

    /**
     * Wait a given bit without interruption
     * @param millis the given milliseconds to wait 
     */
	protected void delay(long millis) {
        boolean interrupted = true;
        while (interrupted) {
            try {
                Thread.sleep(millis);
                interrupted = false;
            }
            catch (InterruptedException e) { ; }
        }
	}
}
