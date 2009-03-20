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
package msrp.examples;

import msrp.*;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.*;

import msrp.exceptions.IllegalUseException;
import msrp.exceptions.InternalErrorException;
import msrp.testutils.*;
import msrp.utils.TextUtils;

/**
 * 
 * Simple class that exemplifies the use of the library in two distinct pcs,
 * used to reply to Thomas's question read comments for further info
 * 
 * Note: the MSRP connection is always both ways but for simplification here, we
 * will have a receiving and a sending endpoint (represented by two private
 * classes on this class) but on the same session both should be able to receive
 * and send messages
 * 
 * @author Jo�o Andr� Pereira Antunes
 * 
 */
public class UsingLibraryByTwoNetworkEndpoints
{
    /**
     * Constant (-1) used mostly on System.exit(int status) calls.
     * 
     * @see System#exit(int)
     */
    public final static int ERROR = -1;

    InetAddress address;

    static Random randomGenerator = new Random();

    File tempFile;

    String tempFileDir;

    /**
     * Class that represents the message receiver, similar code as this one
     * should be on the receiver endpoint
     * 
     * @author Jo�o Andr� Pereira Antunes
     * 
     */
    static class MessageReceiver
    {
        /**
         * This example uses the MockMSRPSessionListener, the SessionListener is
         * the interface between the application and the library, see the
         * MSRPSessionListener javadoc:
         * 
         * @see msrp.MSRPSessionListener
         */
        static MockMSRPSessionListener receivingSessionListener =
            new MockMSRPSessionListener("receivingSessionListener");

        /**
         * The created session object for the receiving endpoint
         */
        static Session receivingSession;

        /**
         * Method that sets up the connection for the MessageReceiver
         * 
         * @param uriEndpoint an URI that was generated by the other endpoint,
         *            this parameter needs to be exchanged somehow.
         */
        protected static URI setUpConnection(URI uriEndpoint) throws Exception
        {

            /*
             * This variable represents the address to which the MessageReceiver
             * will bound it's sockets to, if for instance the MessageSender is
             * running on a pc with ip 192.168.1.1 the 10.0.0.1 should be
             * replaced by 192.168.1.1
             */
            InetAddress receivingBoundAddress =
                InetAddress.getByName("10.0.0.1");

            receivingSession =
                new Session(false, false, uriEndpoint, receivingBoundAddress);

            /* bound the session with the created MSRPSessionListener */
            receivingSession.addMSRPSessionListener(receivingSessionListener);
            System.out.println("Generated URI by the receiver:"
                + receivingSession.getURI());
            return receivingSession.getURI();
        }

    }

    /**
     * Class that represents the message sender, similar code as this one should
     * be on the sender endpoint
     * 
     * @author Jo�o Andr� Pereira Antunes
     * 
     */
    static class MessageSender
    {
        /**
         * Sender MSRPSessionListener that also uses the MockMSRPSessionListener
         * More info:
         * 
         * @see MessageReceiver#receivingSessionListener
         */
        static MockMSRPSessionListener sendingSessionListener =
            new MockMSRPSessionListener("sendinSessionListener");

        /**
         * The created session object for the receiving endpoint
         */
        static Session sendingSession;

        protected static URI setUpConnection() throws Exception
        {
            /*
             * This variable represents the address to which the MessageSender
             * will bound it's sockets to, if for instance the MessageSender is
             * running on a pc with ip 192.168.1.2 the 10.0.0.1 should be
             * replaced by 192.168.1.2
             */
            InetAddress sendingBoundAddress = InetAddress.getByName("10.0.0.1");

            /*
             * This initializes the Session endpoint of the sender, this session
             * creation will generate the MSRP URI that needs to be passed to
             * the receiver NOTE: the URI could be also generated by the sender,
             * but the use of the protocol requires that the generated URI be
             * exchanged somehow (by an external entity to the library)
             */
            sendingSession = new Session(false, false, sendingBoundAddress);

            System.out.println("The generated URI for the MessageSender is:"
                + sendingSession.getURI());

            /* bound the session with the created MSRPSessionListener */
            sendingSession.addMSRPSessionListener(sendingSessionListener);
            return sendingSession.getURI();

        }

        /**
         * Method used to generate a random content message of size bytes that
         * is stored in the memory, for file messages follow the link:
         * 
         * @see msrp.TestSendingASCIIMessages#test300KBMessageFileToFile() Note:
         *      one should have into consideration that a message too big will
         *      can exhaust the Java heap space
         * @param size the number of bytes that the message should have
         * @return the Message object created
         * @throws IllegalUseException thrown if the Message creation originated
         *             an exception @see
         *             {@link Message#Message(Session, String, byte[])}:w
         * 
         */
        protected static Message generateRandomMemoryMessage(int size)
            throws IllegalUseException
        {
            byte[] data = new byte[size];
            randomGenerator.nextBytes(data);
            /*
             * Create the message and bind it from the beginning to the
             * sendingSession:
             */
            Message messageToReturn =
                new Message(sendingSession, "raw/whatever", data);
            /* let us disable the success report for this example */
            messageToReturn.setSuccessReport(false);

            return messageToReturn;

        }
    }

    /**
     * Method that describes the usage of this example class.
     */
    private static void usage()
    {
        System.out.println("Usage: programName" + " automatic");
        System.out.println("or, instead: programName"
            + " manual [receiver URIsender|sender]");
        System.out.println();

    }

    /**
     * Method called when there is an error on one parameter
     */
    private static void parameterError()
    {
        usage();
        System.exit(ERROR);
    }

    public static void main(String[] args)
    {

        URI uriOtherEndPoint;
        if (args.length != 1 && args.length != 2 && args.length != 3)
        {
            System.out.println("Error, called with an invalid number of args:");
            usage();
            return;
        }
        switch (args.length)
        {
        case 1:
            if (args[0].compareToIgnoreCase("automatic") != 0)
                parameterError();
            else
            {
                try
                {

                    /*
                     * "Automatic" mode, create the message receiver and sender,
                     * generate the message, send it and await its receiver
                     */

                    URI senderUri = MessageSender.setUpConnection();

                    MessageSender.generateRandomMemoryMessage(3000);
                    /* connect the two sessions: */
                    ArrayList<URI> toPathSendSession = new ArrayList<URI>();
                    toPathSendSession.add(MessageReceiver
                        .setUpConnection(senderUri));

                    MessageSender.sendingSession.addToPath(toPathSendSession);
                    /* make the mocklistener accept the message */
                    synchronized (MessageReceiver.receivingSessionListener)
                    {
                        DataContainer dc = new MemoryDataContainer(3000);
                        MessageReceiver.receivingSessionListener
                            .setDataContainer(dc);
                        MessageReceiver.receivingSessionListener
                            .setAcceptHookResult(new Boolean(true));
                        MessageReceiver.receivingSessionListener.notify();
                        MessageReceiver.receivingSessionListener.wait(3000);
                    }
                    /*
                     * message should be transfered or in the process of being
                     * completely transfered
                     */

                    if (MessageReceiver.receivingSessionListener
                        .getAcceptHookMessage() == null
                        || MessageReceiver.receivingSessionListener
                            .getAcceptHookSession() == null)
                    {
                        System.out
                            .println("The Mock didn't worked and the message didn't got "
                                + "accepted");
                        System.exit(ERROR);
                    }
                    synchronized (MessageReceiver.receivingSessionListener)
                    {
                        /*
                         * allow the message to be received
                         */
                        MessageReceiver.receivingSessionListener.wait();
                    }
                    if (MessageReceiver.receivingSessionListener
                        .getReceiveMessage() != null
                        && MessageReceiver.receivingSessionListener
                            .getReceiveMessage().getSize() == 3000)
                    {
                        System.out
                            .println("Successfully received the message!");
                        return;
                    }

                }
                catch (Exception e)
                {
                    System.out
                        .println("An exception occurred in the automatic mode:");
                    e.printStackTrace();
                    System.exit(ERROR);
                }

            }
            break;
        case 2:
            if (args[0].compareToIgnoreCase("manual") != 0
                || args[1].compareToIgnoreCase("sender") != 0)
                parameterError();
            else
            {
                try
                {
                    /*
                     * "manual" sender mode, just generate the message and URI
                     * and wait the URI that should come when the receiving end
                     * is run with the generated URI from this sender, after
                     * that it connects to the receiver
                     */

                    URI senderUri = MessageSender.setUpConnection();

                    MessageSender.generateRandomMemoryMessage(3000);

                    /* prompt and get the uri from the standard input: */
                    System.out
                        .print("Please input the sender's one-URI to-path (the receiver URI that was generated by the program in the receiver mode):");
                    BufferedReader br =
                        new BufferedReader(new InputStreamReader(System.in));
                    String uriString;
                    uriString = br.readLine();
                    /*
                     * create the needed URI ArrayList and add the URI that was
                     * given via the standard input:
                     */
                    ArrayList<URI> toPathSendSession = new ArrayList<URI>();
                    URI toPathUri = URI.create(uriString);
                    toPathSendSession.add(toPathUri);
                    /*
                     * add it to the sendingSession's toPath (connecting the two
                     * end-points and starting the message transfer):
                     */
                    MessageSender.sendingSession.addToPath(toPathSendSession);

                }
                catch (Exception e)
                {
                    System.out
                        .println("An exception occurred in the sender in the manual mode:");
                    e.printStackTrace();
                    System.exit(ERROR);
                }
            }
            break;
        case 3:
            if (args[0].compareToIgnoreCase("manual") != 0
                || args[1].compareToIgnoreCase("receiver") != 0)
                parameterError();
            else
            {
                /*
                 * generate the URI and await that the message is completely
                 * received
                 */
                try
                {
                    uriOtherEndPoint = new URI(args[2]);
                    MessageReceiver.setUpConnection(uriOtherEndPoint);

                    /* make the mocklistener accept the message */
                    synchronized (MessageReceiver.receivingSessionListener)
                    {
                        DataContainer dc = new MemoryDataContainer(3000);
                        MessageReceiver.receivingSessionListener
                            .setDataContainer(dc);
                        MessageReceiver.receivingSessionListener
                            .setAcceptHookResult(new Boolean(true));
                        MessageReceiver.receivingSessionListener.notify();
                        /*
                         * wait indefinitely to give time for the generated URI
                         * to be typed on the other endpoint
                         */
                        MessageReceiver.receivingSessionListener.wait();
                    }
                    /*
                     * message should be transfered or in the process of being
                     * completely transfered
                     */

                    if (MessageReceiver.receivingSessionListener
                        .getAcceptHookMessage() == null
                        || MessageReceiver.receivingSessionListener
                            .getAcceptHookSession() == null)
                    {
                        System.out
                            .println("The Mock didn't worked and the message didn't got "
                                + "accepted");
                        System.exit(ERROR);
                    }
                    synchronized (MessageReceiver.receivingSessionListener)
                    {
                        /*
                         * allow the message to be received
                         */
                        MessageReceiver.receivingSessionListener.wait();
                    }
                    if (MessageReceiver.receivingSessionListener
                        .getReceiveMessage() != null
                        && MessageReceiver.receivingSessionListener
                            .getReceiveMessage().getSize() == 3000)
                    {
                        System.out
                            .println("Successfully received the message!");
                        return;
                    }

                }
                catch (URISyntaxException syntaxExcptn)
                {
                    System.out.println("Error: Wrong provided URI syntax:");
                    usage();
                    syntaxExcptn.printStackTrace();
                    System.exit(ERROR);
                }
                catch (Exception e)
                {
                    System.out
                        .println("An exception occurred in the receiver in the manual mode:");
                    e.printStackTrace();
                    System.exit(ERROR);
                }

            }

        }

    }
}
