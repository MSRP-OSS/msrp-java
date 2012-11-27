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
package javax.net.msrp.examples;

import java.io.File;
import java.io.FileNotFoundException;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.ArrayList;

import javax.net.msrp.*;
import javax.net.msrp.events.MessageAbortedEvent;
import javax.net.msrp.exceptions.*;

/**
 * This class contains examples, used to demonstrate the functionality that
 * should be present upon first milestone completion.
 * Functionality not yet implemented/validated is in commented form, with a
 * description of what it should do.
 * 
 * TODO Custom ConnectionPrioritizer example, however no point at this moment
 * because not fully implemented
 * 
 * Purpose of this class is to serve as general instruction and also act as a
 * manual for the user that wants to use this library. The comments herein along
 * with the javadoc of the classes and methods should be sufficient for common
 * programmers to become enlightened in the use of this lib. If you, lib user, feel
 * that I failed to do that here, please let me know.
 * 
 * @author João André Pereira Antunes
 */
public class FirstMilestoneFunctionalities
{
    /**
     * This method describes the use of simple sending and receiving sessions.
     * 
     * Note that both sessions can receive and send messages. They have
     * different constructors due to the fact that one sends a URI and the other
     * one receives a URI, to be able to distinguish these two scenarios is
     * important for Connection reuse.
     */
    @SuppressWarnings("unused")
	private void implementedBehavior()
    {
        InetAddress address = null;
        try
        {
            // the address argument is the InetAddress used to bind the new
            // connection to. (or reuse an existing one TODO)
            address = InetAddress.getByName("IP OF THE INTERFACE TO USE");
        }
        catch (UnknownHostException e)
        {
            // if the address doesn't exist this could be problematic
            e.printStackTrace();
            return;
        }
        Session sendingSession;
        Session receivingSession;
        try
        {
            /*
             * This assigns a connection to a session.
             * 
             * Note: currently we support neither TLS nor Relays, so
             * setting the first two arguments to something different than false
             * has no practical effect.
             */
            sendingSession = Session.create(false, false, address);
            URI sendingSessionUri = sendingSession.getURI();
            /*
             * at this point usually one retrieves the URI of the sending
             * session and sends it to the other endpoint via some negotiation
             * mechanism.
             * 
             * the received URI from the sendingSession is used to set up the
             * "receiving" session:
             * (note: the receiving session needs the uri in order to validate
             * incoming connections [however the timer isn't yet implemented,
             * so illegitimate connections done here don't get disconnected see
             * planning.
             */
            receivingSession =
                Session.create(false, false, sendingSessionUri, address);

            /*
             * We have to add a Listener to both sessions so that the
             * Stack interfaces with the application using it.
             * More info about these Listeners can be found on the comments of
             * the private class created for demonstration's and instruction
             * sake.
             */
            MSRPExampleSessionListener receivingSessionListener =
                new MSRPExampleSessionListener();
            MSRPExampleSessionListener sendingSessionListener =
                new MSRPExampleSessionListener();

            /*
             * as required per RFC upon sessions connect, the connecting
             * (active) session must send a SEND request in order for the
             * receiving session to validate the URIs and therefore the
             * connection. If at that point the session has no messages to send,
             * it generates and sends an bodiless SEND.
             */
            try
            {
                byte[] someData = null;
                Message exampleMessage = sendingSession.sendMessage(
                		"MIMEType/MIMEsubType", someData);
            }
            catch (Exception e)
            {
            	/*
                 * this can be caused by too big a message, see
                 * Stack#setShortMessageBytes(int) for more info
                 */
                e.printStackTrace();
            }

            // or alternatively:
            File someFile = null;
            try
            {
                Message exampleFileMessage = sendingSession.sendMessage(
                        "MIMEType/MIMEsubType", someFile);
            }
            catch (FileNotFoundException e)
            {
                // if the file wasn't found
                e.printStackTrace();
            }
            catch (SecurityException e)
            {
                // if there was a problem while accessing file's content
                e.printStackTrace();
            }

            /*
             * ALTERNATIVE: reportMechanisms could be set, in that case we
             * would have a different report mechanism
             * specific to this Message
             * Message exampleMessage = new OutgoingMessage(
             * 							"MIMEType/MIMEsubType", someData);
             * exampleMessage.setReportMechanism(customReportMechanism);
             * 
             * Message exampleFileMessage = new OutgoingMessage(
             * 					"MIMEType/MIMEsubType", * someFile);
             * exampleFileMessage.setReportMechanism(cuustomReportMechanism);
             * 
             * now the ToPath (collection of URIs if using relays, or just one
             * URI) gets transfered to the sending end point
             * and we just have to add the ToPath received to establish
             * connection and start sending the Messages already bound to this
             * session
             */
            ArrayList<URI> toPath = new ArrayList<URI>();
            toPath.add(receivingSession.getURI());

            // now the connection is established and transfer is started.
        }
        catch (InternalErrorException e)
        {
            e.printStackTrace();
        }
    }

    /**
     * This is the class that implements the SessionListener which -through
     * the stack- communicates with it's user
     * 
     * @author João André Pereira Antunes
     */
    private class MSRPExampleSessionListener
        implements SessionListener
    {
        /*
         * (non-Javadoc)
         * 
         * @see javax.net.msrp.SessionListener#acceptHook(javax.net.msrp.Session,
         * javax.net.msrp.IncomingMessage)
         */
        /*
         * Called by the stack when we receive an incoming message.
         */
        public boolean acceptHook(Session session, IncomingMessage message)
        {
            boolean weFeelLikeIt = false;

            if (weFeelLikeIt)
            {
                /*
                 * this message doesn't get accepted and therefore the method
                 * returns false we can always set the response code with the
                 * reason, see RFC 4975 Response Codes section for more
                 * information about this, or MessageAbortEvent. The extra info
                 * corresponds to the comment as defined by the formal syntax
                 * and An extra info of null corresponds to not including the
                 * comment as the comment is always defined as optional on RFC
                 * 4975
                 */
                try
                {
                    message.abort(ResponseCode.RC413, null);
                    // we also have a convenience method that is equivalent to
                    // the above call
                    message.reject();
                }
                catch (InternalErrorException e)
                {
                    // if something went wrong while aborting
                    e.printStackTrace();
                }
                catch (IllegalUseException e)
                {
                    //if the abort was called with erroneous arguments
                    e.printStackTrace();
                }
                return false;
            }
            else
            {
            	/*
                 * if we don't feel like it or have any other reason, we can
                 * accept the message
                 * however we need to generate a new data container, either a
                 * memory or a file one with appropriate size (in case of being
                 * a memory one)
                 */
                DataContainer dataContainer = new MemoryDataContainer(300);
                /*
                 * for alternatives to the MemoryDataContainer see javadoc of
                 * FileDataContainer
                 * 
                 * now we have to assign the newly generated data container to
                 * the message
                 */
                message.setDataContainer(dataContainer);

                return true;
            }
        }

        /*
         * (non-Javadoc)
         * 
         * @see javax.net.msrp.SessionListener#receiveMessage(javax.net.msrp.Session,
         * javax.net.msrp.Message)
         */
        /*
         * This method is called upon successfully receiving a message
         */
        public void receivedMessage(Session sessionThatReceivedMessage,
            IncomingMessage receivedMessage)
        {
            // here you can do whatever you want with the received message

            // usually you would want to use extract it's content
            // you can do it like this:
            try
            {
                ByteBuffer receivedContent =
                    receivedMessage.getDataContainer().get(0, 0);
            }
            catch (Exception e)
            {
                // see DataContainer.get(long, long) javadoc for more info
                // on the exceptions thrown
                e.printStackTrace();
            }
            // that is usually more appropriate usage of a MemoryContainer
            // or you can simply extract the file if we have a FileContainer
            // associated with this message
            File receivedFile =
                ((FileDataContainer) receivedMessage.getDataContainer())
                    .getFile();

            // either way it's wise to dispose of the resources associated with
            // the file container after the data is used
            receivedMessage.discard();
        }

        /*
         * (non-Javadoc)
         * 
         * @see javax.net.msrp.SessionListener#receivedReport(javax.net.msrp.Session,
         * javax.net.msrp.Transaction)
         */
        /*
         * This is the method called upon receiving a REPORT request from the
         * other MSRP peer. Note that REPORT requests could be reporting on the
         * whole message or on parts of it Note as well that this method is only
         * called if the Success Report of the message sent is set to true with
         * Message.setSuccessReport(true); by default it's set to false
         * 
         * FIXME this mechanism of the success report has a big issue associated
         * with it, please see Issue #5 of MSRP Stack and Issue #17 of SC
         * integration project
         */
        public void receivedReport(Session session, Transaction report)
        {
            // to get more info about on what bytes is this REPORT reporting at:
            report.getByteRange();
            // see javax.net.msrp.Transaction.byteRange field javadoc's comments for more
            // info

            // we can retrieve the Status header field like this:
            report.getStatusHeader();
            // more info on StatusHeader class javadoc

            // we can also get the message this report refers to with this
            // command:
            report.getMessage();

            // or just it's id
            report.getMessageID();
        }

        /*
         * (non-Javadoc)
         * 
         * @see javax.net.msrp.SessionListener#updateSendStatus(javax.net.msrp.Session,
         * javax.net.msrp.Message, long)
         */
        /*
         * this method is called regularly, see DefaultReportMechanism
         * shouldTriggerSentHook for more info This granularity can be
         * Customised by implementing a ReportMechanism class and changing this
         * method
         */
        public void updateSendStatus(Session session, Message message,
            long numberBytesSent)
        {
            System.out.println("This means we sent " + numberBytesSent
                + " of Message with id: " + message.getMessageID());
        }

		@Override
        public void abortedMessageEvent(MessageAbortedEvent abortEvent)
        {
            // TODO Auto-generated method stub
        }

		@Override
		public void connectionLost(Session session, Throwable cause) {
			session.tearDown();
			cause.printStackTrace();
		}

		@Override
		public void receivedNickname(Session session, Transaction request) {
		}

		@Override
		public void receivedNickNameResult(Session session, TransactionResponse result) {
		}
    }
}
