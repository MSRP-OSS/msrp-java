/* Copyright © João Antunes 2008
 This file is part of MSRP Java Stack.

    MSRP Java Stack is free software: you can redistribute it and/or modify
    it under the terms of the GNU Lesser General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    MSRP Java Stack is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Lesser General Public License for more details.

    You should have received a copy of the GNU Lesser General Public License
    along with MSRP Java Stack.  If not, see <http://www.gnu.org/licenses/>.

 */
package msrp.examples;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.ArrayList;

import msrp.*;
import msrp.event.MessageAbortedEvent;
import msrp.exceptions.*;
import msrp.messages.*;

/**
 * This class is used to demonstrate examples of all the functionalities that
 * should be present upon the first milestone completion. Currently
 * functionalities that aren't yet implemented/validated are still here
 * commented and with a description of what they do.
 * 
 * TODO Custom ConnectionPrioritizer example, however no point at this moment
 * because this is not fully implemented
 * 
 * This class purpose is to serve as general instructions and act also as a
 * manual for the user that wants to use this library. The comments herein along
 * with the javadoc of the classes and methods should be sufficient for common
 * programmer to be enlightened of the use of this lib. If you, lib user, feel
 * that i failed to do such thing in this class, please let me know.
 * 
 * @author João André Pereira Antunes
 * 
 */
public class FirstMilestoneFunctionalities
{

    /**
     * This method describes the use of simple sending and receiving sessions.
     * 
     * TODO To note that both sessions can receive and send messages. They have
     * different constructors due to the fact that one sends a URI and the other
     * one receives a URI, to be able to distinguish these two scenarios is
     * important for Connection reuse.
     * 
     */
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
            // the constructor is responsible for assigning a connection to a
            // session.
            /*
             * Note: currently we don't support neither TLS nor Relays, so
             * setting the first two arguments to something different than false
             * has no practical effect.
             */
            sendingSession = new Session(false, false, address);
            URI sendingSessionUri = sendingSession.getURI();
            /*
             * at this point usually one retrieves the URI of the sending
             * session and sends it to the other endpoint via some negotiation
             * mechanism.
             */
            // the received URI from the sendingSession is used to set up the
            // "receiving" session:
            // (note: the receiving session needs the uri in order to validate
            // incoming connections [however the timer isn't yet implemented,
            // so unlegitimate connections done here don't get disconnected see
            // planning]
            receivingSession =
                new Session(false, false, sendingSessionUri, address);

            // ALTERNATIVE: for both or one of the sessions, one could easily
            // add another report mechanism, however this isn't tested yet. See
            // the javadoc for the ReportMechanism class for more information on
            // this functionality

            // receivingSession = new Session(false, false, sendingSessionUri,
            // address, customReportMechanism);
            // sendingSession = new Session(false, false, address,
            // customReportMechanism);

            // We have to add a Listener to both the sessions so that the
            // MSRPStack interfaces with the application that is using it.
            // More info about these Listener can be found on the comments of
            // the private class created for demonstration's and instruction
            // sake

            MSRPExampleSessionHandler receivingSessionHandler =
                new MSRPExampleSessionHandler();
            MSRPExampleSessionHandler sendingSessionHandler =
                new MSRPExampleSessionHandler();

            // as required on RFC upon two sessions connection occurs, the
            // connecting session must send a SEND request in order for the
            // receiving session to validate the URIs and therefore the
            // connection. If at that point the session has no messages to send,
            // it should generate and send an empty body SEND, however that
            // behavior isn't yet implemented so it's required for one to assign
            // a Message to a Session like this:
            try
            {
                byte[] someData = null;
                Message exampleMessage =
                    new OutgoingMessage(sendingSession, "MIMEType/MIMEsubType",
                        someData);
            }
            catch (IllegalUseException e)
            {
                // this is caused by a too big message, see
                // MSRPStack.setShortMessageBytes(int) for more info
                e.printStackTrace();
            }

            // or alternatively:
            File someFile = null;
            try
            {
                Message exampleFileMessage =
                    new OutgoingFileMessage(sendingSession, "MIMEType/MIMEsubType",
                        someFile);
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

            // ALTERNATIVE: also the reportMechanisms could be set like in the
            // Session, in such case we would have a different report mechanism
            // specific to this Message
            // Message exampleMessage = new Message(sendingSession,
            // "MIMEType/MIMEsubType",
            // someData, customReportMechanism);
            // Message exampleFileMessage = new FileMessage(sendingSession,
            // "MIMEType/MIMEsubType",
            // someFile, customReportMechanism);

            // now the ToPath (collection of URIs if using relays, or just one
            // URI) gets transfered to the sending endpoint
            // and we just have to add the ToPath received to establish
            // connection and start sending the Messages already bound to this
            // session

            // seen that we only implement MSRP without relays the to-path will
            // have only one URI
            // however support for multiple URIs is already implemented here
            ArrayList<URI> toPath = new ArrayList<URI>();
            toPath.add(receivingSession.getURI());

            // now the connection is established and the transfer is started.

        }
        catch (InternalErrorException e)
        {
            // in case the Sessions constructors failed we can get more info
            // from printing the stack trace of the InternalErrorException, as
            // this exception is usually used as a wrapper for other exceptions
            e.printStackTrace();
        }

    }

    /**
     * This is the class that implements the MSRPSessionListener which through
     * the stack communicates with it's user
     * 
     * @author João André Pereira Antunes
     * 
     */
    private class MSRPExampleSessionHandler
        implements MSRPSessionListener
    {

        /*
         * (non-Javadoc)
         * 
         * @see msrp.MSRPSessionListener#acceptHook(msrp.Session,
         * msrp.IncomingMessage)
         */
        @Override
        /*
         * This is the method that is called by the stack when we receive an
         * incoming message.
         */
        public boolean acceptHook(Session session, IncomingMessage message)
        {
            boolean weFeelLikeIt = false;

            if (weFeelLikeIt)
            {
                // if we feel like it, or have a better reason to, we can reject
                // the message
                int validCode = 0;
                // this message doesn't get accepted and therefore the method
                // returns false
                // we can always set the response code to deny the reason, if
                // this isn't specified the default is 413, see RFC 4975
                // Response Codes section for more information about this.
                try
                {
                    message.reject(validCode);
                }
                catch (IllegalUseException e)
                {
                    // oh no no no no no!, we must put a valid core up there,
                    // consult Message.reject(int) javadoc and RFC 4975 for more
                    // information
                    e.printStackTrace();
                }
                return false;
            }
            else
            {
                // if we don't feel like it or have any other reason, we can
                // accept the message
                // however we need to generate a new data container, either a
                // memory or a file one with appropriate size (in case of being
                // a memory one)
                int reportedSizeMessage = (int) message.getSize();
                DataContainer dataContainer = new MemoryDataContainer(300);
                // for alternatives to the MemoryDataContainer see javadoc of
                // FileDataContainer

                // now we have to assign the newly generated data container to
                // the message
                message.setDataContainer(dataContainer);

                return true;

            }
        }

        /*
         * (non-Javadoc)
         * 
         * @see msrp.MSRPSessionListener#receiveMessage(msrp.Session,
         * msrp.Message)
         */
        @Override
        /*
         * This method is called upon successfully receiving a message
         */
        public void receiveMessage(Session sessionThatReceivedMessage,
            Message receivedMessage)
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
            receivedMessage.getDataContainer().dispose();

        }

        /*
         * (non-Javadoc)
         * 
         * @see msrp.MSRPSessionListener#receivedReport(msrp.Session,
         * msrp.Transaction)
         */
        @Override
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
            // see msrp.Transaction.byteRange field javadoc's comments for more
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
         * @see msrp.MSRPSessionListener#updateSendStatus(msrp.Session,
         * msrp.Message, long)
         */
        @Override
        /*
         * this method is called regularly, see DefaultReportMechanism
         * shouldTriggerSentHook for more info This granularity can be
         * customized by implementing a ReportMechanism class and changing this
         * method
         */
        public void updateSendStatus(Session session, Message message,
            long numberBytesSent)
        {
            System.out.println("This means we sent " + numberBytesSent
                + " of Message with id: " + message.getMessageID());

        }

        @Override
        public void abortedMessage(Session session, IncomingMessage message)
        {
            //Deprecated, use abortedMessageEvent
            
        }

        @Override
        public void abortedMessageEvent(MessageAbortedEvent abortEvent)
        {
            // TODO Auto-generated method stub
            
        }

    }

}
