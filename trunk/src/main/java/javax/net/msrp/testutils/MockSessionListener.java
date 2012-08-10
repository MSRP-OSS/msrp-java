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
package javax.net.msrp.testutils;

import java.util.ArrayList;

import javax.net.msrp.DataContainer;
import javax.net.msrp.IncomingMessage;
import javax.net.msrp.Message;
import javax.net.msrp.OutgoingMessage;
import javax.net.msrp.ResponseCode;
import javax.net.msrp.SessionListener;
import javax.net.msrp.MemoryDataContainer;
import javax.net.msrp.Session;
import javax.net.msrp.Transaction;
import javax.net.msrp.events.MessageAbortedEvent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Class used to test the callbacks of the Stack
 * 
 * @author João André Pereira Antunes 2008
 * 
 */
public class MockSessionListener
    implements SessionListener
{
    /** The logger associated with this class */
    private static final Logger logger =
        LoggerFactory.getLogger(MockSessionListener.class);

    /* The field results of the calls: */
    private Session acceptHookSession;

    private Message acceptHookMessage;

    private Session receiveMessageSession;

    private Message receiveMessage;

    private Session receivedReportSession;

    private Transaction receivedReportTransaction;

    private Transaction receivedNicknameTransaction;

    private Session updateSendStatusSession;

    private Message updateSendStatusMessage;

    private Session abortedMessageSession;

    private Message abortedMessage;

    private Boolean acceptHookResult;

    private String nickname;

    /**
     * The external to be set, or not data container object that is going to be
     * used on the acceptHook if it exists upon the calling of the trigger
     */
    private DataContainer externalDataContainer;

    /**
     * Counter for the send status update, the number of elements reflect the
     * number of calls to the updateSendStatus method and its values the number
     * of bytes that the function was called with
     * 
     * @see SessionListener#updateSendStatus(Session, Message, long)
     */
    public ArrayList<Long> updateSendStatusCounter = new ArrayList<Long>();

    /**
     * A counter for the number of success reports received
     */
    public ArrayList<Long> successReportCounter = new ArrayList<Long>();

    /**
     * A counter for the number of message aborts received. the size of this
     * array counts how many message abortions and its values the bytes that
     * were received before the message was aborted
     */
    public ArrayList<Long> abortMessageCounter = new ArrayList<Long>();

    public ArrayList<MessageAbortedEvent> messageAbortEvents =
    new ArrayList<MessageAbortedEvent>();

    /**
     * Constructor of the mock session listener
     * 
     * @param name the String that names this constructor, used for debug
     *            purposes
     */
    public MockSessionListener(String name)
    {
    }

    @Override
    public boolean acceptHook(Session session, IncomingMessage message)
    {
        logger.trace("AcceptHook called");
        while (acceptHookResult == null)
        {
            synchronized (this)
            {
                try
                {
                    wait();
                }
                catch (InterruptedException e)
                {
                    throw new RuntimeException();
                }
            }
        }
        boolean toReturn = acceptHookResult.booleanValue();

        logger.debug("AcceptHook will return: " + toReturn);

        acceptHookMessage = message;
        acceptHookSession = session;
        if (externalDataContainer == null)
        {
            MemoryDataContainer dataContainer =
                new MemoryDataContainer((int) message.getSize());
            message.setDataContainer(dataContainer);
        }
        else
        {
            message.setDataContainer(externalDataContainer);
        }
        acceptHookResult = null;
        synchronized (this)
        {
            this.notifyAll();
            logger.debug("AcceptHook returns: " + toReturn);
            return toReturn;
        }
    }

    @Override
    public void receivedMessage(Session session, IncomingMessage message)
    {
        logger.debug("receiveMessage(id=[" + message.getMessageID() + "])");
        receiveMessage = message;
        receiveMessageSession = session;
        /* 
         * delay this thread to allow the other thread to be scheduled
         * before acquiring a lock again.
         */
        try { Thread.sleep(200); } catch (InterruptedException e) { ; }

        synchronized (this)
        {
            this.notifyAll();
        }
    }

    @Override
    public void receivedReport(Session session, Transaction tReport)
    {
        if (tReport.getStatusHeader().getStatusCode() != ResponseCode.RC200)
        {
            logger.debug("Received report with code different from 200"
                + ", with code: " + tReport.getStatusHeader().getStatusCode()
                + " returning");
            return;
        }
        logger.debug("Received report, confirming "
            + tReport.getByteRange()[1] + " bytes were sent(== "
            + (tReport.getByteRange()[1] * 100)
            / tReport.getTotalMessageBytes() + "%) Tx-"
            + tReport.getTransactionType() + ", status:"
            + tReport.getStatusHeader().getStatusCode());
        receivedReportSession = session;
        receivedReportTransaction = tReport;
        synchronized (successReportCounter)
        {
            successReportCounter.add(tReport.getByteRange()[1]);
            successReportCounter.notifyAll();
        }
    }

    @Override
    public void updateSendStatus(Session session, Message message,
        long numberBytesSent)
    {
    	final long size = message.getSize();
        logger.debug("updateSendStatus() bytes sent: " + numberBytesSent
            + " == " + (size == 0 ? 100 : (numberBytesSent * 100) / size) + "%");
        updateSendStatusSession = session;
        updateSendStatusMessage = message;
        synchronized (updateSendStatusCounter)
        {
            updateSendStatusCounter.add(numberBytesSent);
            updateSendStatusCounter.notifyAll();
        }
    }

    @Override
    public void abortedMessageEvent(MessageAbortedEvent abortEvent)
    {
    	final long size;
        messageAbortEvents.add(abortEvent);
        boolean incoming = false;
        if (abortEvent.getMessage().getDirection() == Message.IN)
        {
            incoming = true;

            IncomingMessage message = (IncomingMessage) abortEvent.getMessage();
        	size = message.getSize();
            logger.debug("abortedMessageEvent() bytes received: "
                + message.getReceivedBytes() + " == "
                + (size == 0 ? 100 : (message.getReceivedBytes() * 100) / size) + "%");
        }
        else if (abortEvent.getMessage().getDirection() == Message.OUT)
        {
            OutgoingMessage message = (OutgoingMessage) abortEvent.getMessage();
        	size = message.getSize();
            logger.debug("abortedMessageEvent() bytes sent: "
                + message.getSentBytes() + " == "
                + (size == 0 ? 100 : (message.getSentBytes() * 100) / size) + "%");
        }
        abortedMessage = abortEvent.getMessage();
        abortedMessageSession = abortEvent.getSession();
        synchronized (abortMessageCounter)
        {
            if (incoming)
                abortMessageCounter.add(((IncomingMessage) abortedMessage)
                    .getReceivedBytes());
            else
                abortMessageCounter.add(((OutgoingMessage) abortedMessage)
                    .getSentBytes());
            abortMessageCounter.notifyAll();
        }
    }

    /**
     * @return the acceptHookSession
     */
    public Session getAcceptHookSession()
    {
        return acceptHookSession;
    }

    /**
     * @return the acceptHookMessage
     */
    public Message getAcceptHookMessage()
    {
        return acceptHookMessage;
    }

    /**
     * @return the receiveMessageSession
     */
    public Session getReceiveMessageSession()
    {
        return receiveMessageSession;
    }

    /**
     * @return the receiveMessage
     */
    public Message getReceiveMessage()
    {
        return receiveMessage;
    }

    /**
     * @return the receivedReportSession
     */
    public Session getReceivedReportSession()
    {
        return receivedReportSession;
    }

    /**
     * @return the receivedReportTransaction
     */
    public Transaction getReceivedReportTransaction()
    {
        return receivedReportTransaction;
    }

    /**
     * @return the receivedNicknameTransaction
     */
    public Transaction getReceivedNicknameTransaction()
    {
        return receivedNicknameTransaction;
    }

    /**
     * @return the updateSendStatusSession
     */
    public Session getUpdateSendStatusSession()
    {
        return updateSendStatusSession;
    }

    /**
     * @return the updateSendStatusMessage
     */
    public Message getUpdateSendStatusMessage()
    {
        return updateSendStatusMessage;
    }

    /**
     * @return the abortedMessageSession
     */
    public Session getAbortedMessageSession()
    {
        return abortedMessageSession;
    }

    /**
     * @return the abortedMessage
     */
    public Message getAbortedMessage()
    {
        return abortedMessage;
    }

    /**
     * @param acceptHookResult the acceptHookResult to set
     */
    public void setAcceptHookResult(Boolean acceptHookResult)
    {
        this.acceptHookResult = acceptHookResult;
    }

    public void setDataContainer(DataContainer dc)
    {
        externalDataContainer = dc;
    }

	@Override
	public void connectionLost(Session session, Throwable cause) {
		logger.warn("Connection broke, reason: " + cause.getMessage());
		session.tearDown();
	}

	@Override
	public void receivedNickname(Session session, Transaction request) {
		receivedNicknameTransaction = request;
		nickname = request.getNickname();
        synchronized (this)
        {
            this.notifyAll();
        }
	}

	@Override
	public void receivedNickResult(Session session, Transaction result) {
		int code = result.getStatusHeader().getStatusCode(); 
		if (code != ResponseCode.RC200)
		{
			logger.warn("Bad nickname result: " + ResponseCode.toString(code));
		}
	}

	/**
	 * @return the nickname
	 */
	public String getNickname() {
		return nickname;
	}
}
