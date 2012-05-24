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


import javax.net.msrp.Connection;
import javax.net.msrp.testutils.*;

import static org.junit.Assert.*;

import org.junit.*;

import org.slf4j.*;

/**
 * Test the success reports
 * 
 * @author João André Pereira Antunes 2008
 * 
 */
public class TestReportMechanism extends TestFrame
{
    private static final Logger logger =
        LoggerFactory.getLogger(TestReportMechanism.class);
    /**
     * This method tests sending a small (499 KBytes) message and at the end
     * makes sure the success report was called one time
     */
    @Test
    public void testDefaultReportMechanismSmall()
    {
        int messageSize = 499 * 1024;
        try
        {
            /* transfer the 499 kbytes message: */
            byte[] data = new byte[messageSize];
            fillText(data);

            memory2Memory(data, true);

            synchronized (sendingSessionListener.successReportCounter)
            {
                sendingSessionListener.successReportCounter.wait(100);
            }
            assertTrue("Error the success report was called: "
                + sendingSessionListener.successReportCounter.size()
                + " times and not 1",
                sendingSessionListener.successReportCounter.size() == 1);

            /*
             * assert that the received report message id belongs to the sent
             * and therefore received message
             */
            assertEquals(receivingSessionListener.getReceiveMessage()
                .getMessageID(), sendingSessionListener
                .getReceivedReportTransaction().getMessageID());

            /*
             * Assert that the updateSendStatus was called the due amount of
             * times: . one for the barrier of the 49% to 50% (if the
             * message+headers < buffers of the connection size this won't
             * appear) [not the case actually] . another when the message is
             * completely sent. Be tolerant (+/- Connection.OUTPUTBUFFERLENGTH)
             * about the values
             */
            assertEquals(
                "The updateSendStatus wasn't called the expected number "
                    + "of times", 2,
                sendingSessionListener.updateSendStatusCounter.size());
            /*
             * make sure it was called with the 50% and 100% it must have a
             * tolerance of +/- the buffer (Connection.OUTPUTBUFFERLENGTH bytes)
             */
            long receivedMessageSize =
                sendingSessionListener.getUpdateSendStatusMessage().getSize();
            // Extra check, the size of the sent content must be the same as the
            // reported that was received:
            assertEquals(
                "The reported size of the received message is different from the size of bytes sent",
                (long) messageSize, receivedMessageSize);
            // Calculate the maximum and minimum percentage values with the
            // tolerance

            // number of bytes to which 50% corresponds to
            int idealValue = (int) receivedMessageSize / 2;
            int obtainedPValue =
                (int) (sendingSessionListener.updateSendStatusCounter.get(0)
                    .longValue() * 100 / receivedMessageSize);
            int maxPValue =
                (int) (idealValue + Connection.OUTPUTBUFFERLENGTH * 100
                    / receivedMessageSize);
            int minPValue =
                (int) (idealValue - Connection.OUTPUTBUFFERLENGTH * 100
                    / receivedMessageSize);
            logger.debug("50% Check, maxPValue:" + maxPValue + " minPValue:"
                + minPValue + " value obtained:" + obtainedPValue);
            assertTrue(
                "The updateSendStatus was called with a value of percentage out of the expected!"
                    + " minPValue:"
                    + minPValue
                    + " maxPValue:"
                    + maxPValue
                    + " obtained value:" + obtainedPValue,
                (obtainedPValue < minPValue || obtainedPValue > maxPValue));
            // no tolerance for the 100% check:
            assertEquals("The updateSendStatus was called with a "
                + "strange/unexpected number of bytes sent as argument", 100,
                (sendingSessionListener.updateSendStatusCounter.get(1)
                    .longValue() * 100)
                    / sendingSessionListener.getUpdateSendStatusMessage()
                        .getSize());
        }
        catch (Exception e)
        {
            e.printStackTrace();
            fail(e.getMessage());
        }
    }

    /**
     * This method tests sending a big (5 Mbytes) binary message and at the end
     * makes sure the success report was called one time
     */
    @Test
    public void testDefaultReportMechanismBig()
    {
        try
        {
            byte[] data = new byte[5 * 1024 * 1024];
            fillTempFile(data, true);

            file2Memory(true);
            assertTrue("Error the success report was called: "
                + sendingSessionListener.successReportCounter
                + " times and not 1",
                sendingSessionListener.successReportCounter.size() == 1);

            /*
             * assert that the received report message id belongs to the sent
             * and therefore received message
             */
            assertEquals(receivingSessionListener.getReceiveMessage()
                .getMessageID(), sendingSessionListener
                .getReceivedReportTransaction().getMessageID());

            /*
             * Assert that the updateSendStatus was called the due amount of
             * times: . at each 10% of progress obtained . another when the
             * message is completely sent
             */
            assertEquals(
                "The updateSendStatus wasn't called the expected number "
                    + "of times", 10,
                sendingSessionListener.updateSendStatusCounter.size());
            /*
             * make sure it was called with the right percentages, with the
             * Connection.OUTPUTBUFFERLENGTH tolerance
             */

            // let's have a different approach, let's calculate how much
            // percentual points the buffer size represent and give a tolerance
            // between the ideal percentage and the ideal + the calculated value

            long receivedMessageSize =
                sendingSessionListener.getUpdateSendStatusMessage().getSize();
            // how much is Connection.OUTPUTBUFFERLENGTH
            int tolerancePValue =
                (int) (Connection.OUTPUTBUFFERLENGTH * 100 / receivedMessageSize);

            for (int i = 0; i < 10; i++)
            {
                int expectedIdealPValue = (i + 1) * 10;
                int expectedMaximumPValue =
                    expectedIdealPValue + tolerancePValue;
                int obtainedPValue =
                    (int) ((int) (sendingSessionListener.updateSendStatusCounter
                        .get(i).longValue() * 100) / receivedMessageSize);
                assertTrue(
                    "The updateSendStatus was called with a strange/unexpected number of bytes sent as argument, obtained percentage value:"
                        + obtainedPValue
                        + " maximum value tolerated:"
                        + expectedMaximumPValue,
                    (obtainedPValue >= expectedIdealPValue && obtainedPValue <= expectedMaximumPValue));
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
            fail(e.getMessage());
        }
    }

    /**
     * This method tests sending a small (499 KBytes) message and at the end
     * makes sure the success report was called one time
     */
    @Test
    public void testCustomReportMechanismSmall()
    {
        try
        {
            /* change the report mechanism of the sessions: */
            receivingSession.setReportMechanism(CustomExampleReportMechanism
                .getInstance());
            sendingSession.setReportMechanism(CustomExampleReportMechanism
                .getInstance());
            /* transfer the 499 bytes message: */
            byte[] data = new byte[499 * 1024];
            fillText(data);

            memory2Memory(data, true);

            assertTrue("Error the success report was called: "
                + sendingSessionListener.successReportCounter.size()
                + " times and not 2",
                sendingSessionListener.successReportCounter.size() == 2);

            /*
             * make sure it was called with the 50% and 100% it must have a
             * tolerance of +/- the buffer (Connection.OUTPUTBUFFERLENGTH bytes)
             */
            long receivedMessageSize =
                sendingSessionListener.getUpdateSendStatusMessage().getSize();
            // Calculate the maximum and minimum percentage values with the
            // tolerance

            // number of bytes to which 50% corresponds to
            int idealValue = (int) receivedMessageSize / 2;
            int obtainedPValue =
                (int) (sendingSessionListener.updateSendStatusCounter.get(0)
                    .longValue() * 100 / receivedMessageSize);
            int maxPValue =
                (int) (idealValue + Connection.OUTPUTBUFFERLENGTH * 100
                    / receivedMessageSize);
            int minPValue =
                (int) (idealValue - Connection.OUTPUTBUFFERLENGTH * 100
                    / receivedMessageSize);
            logger.debug("50% Check, maxPValue:" + maxPValue + " minPValue:"
                + minPValue + " value obtained:" + obtainedPValue);
            assertTrue(
                "The updateSendStatus was called with a value of percentage out of the expected!"
                    + " minPValue:"
                    + minPValue
                    + " maxPValue:"
                    + maxPValue
                    + " obtained value:" + obtainedPValue,
                (obtainedPValue < minPValue || obtainedPValue > maxPValue));
            
            /* no tolerance for the 100% check */
            assertEquals(
                "The receivedReport was called with a "
                    + "strange/unexpected number of bytes sent as argument",
                100,
                (sendingSessionListener.successReportCounter.get(1).longValue() * 100)
                    / outMessage.getSize());

            /*
             * assert that the received report message id belongs to the sent
             * and therefore received message
             */
            assertEquals(receivingSessionListener.getReceiveMessage()
                .getMessageID(), sendingSessionListener
                .getReceivedReportTransaction().getMessageID());

            /*
             * Assert that the updateSendStatus was called the due amount of
             * times: . Just one for the completion
             */
            assertEquals(
                "The updateSendStatus wasn't called the expected number "
                    + "of times", 1,
                sendingSessionListener.updateSendStatusCounter.size());
            /* make sure that it was called when the message was at 100% */
            assertEquals("The updateSendStatus was called with a "
                + "strange/unexpected number of bytes sent as argument", 100,
                (sendingSessionListener.updateSendStatusCounter.get(0)
                    .longValue() * 100)
                    / sendingSessionListener.getUpdateSendStatusMessage()
                        .getSize());
        }
        catch (Exception e)
        {
            e.printStackTrace();
            fail(e.getMessage());
        }
    }

    /**
     * This method tests sending a big (5 Mbytes) binary message and at the end
     * makes sure the success report was called one time
     */
    @Test
    public void testCustomReportMechanismBig()
    {
        try
        {
            /* change the report mechanism of the sessions: */
            receivingSession.setReportMechanism(CustomExampleReportMechanism
                .getInstance());
            sendingSession.setReportMechanism(CustomExampleReportMechanism
                .getInstance());
            byte[] bigData = new byte[5 * 1024 * 1024];
            fillTempFile(bigData, true);

            file2Memory(true);
            /*
             * 11 Times might be one time too many times, because the sender
             * receives two reports of the message being completely received.
             * However this kind of behavior isn't incompatible with the one
             * present on the standards. For more info see Issue #10
             */
            assertTrue("Error the success report was called: "
                + sendingSessionListener.successReportCounter.size()
                + " times and not 11",
                sendingSessionListener.successReportCounter.size() == 11);
            /*
             * assert that the received report message id belongs to the sent
             * and therefore received message
             */
            assertEquals(receivingSessionListener.getReceiveMessage()
                .getMessageID(), sendingSessionListener
                .getReceivedReportTransaction().getMessageID());

            /*
             * Assert that the updateSendStatus was called the due amount of
             * times: . when the message is completely sent
             */
            assertEquals(
                "The updateSendStatus wasn't called the expected number "
                    + "of times", 1,
                sendingSessionListener.updateSendStatusCounter.size());
            /*
             * make sure it was called with the right percentages
             */
            assertEquals("The updateSendStatus was called with a "
                + "strange/unexpected number of bytes sent as argument", 100,
                (sendingSessionListener.updateSendStatusCounter.get(0)
                    .longValue() * 100)
                    / sendingSessionListener.getUpdateSendStatusMessage()
                        .getSize());
        }
        catch (Exception e)
        {
            e.printStackTrace();
            fail(e.getMessage());
        }
    }
}
