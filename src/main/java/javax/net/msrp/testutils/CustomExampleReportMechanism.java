/* Copyright © João Antunes 2008
 * This file is part of MSRP Java Stack.
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

import javax.net.msrp.*;

/**
 * Custom Report Mechanism for test purposes to validate functionality of using
 * different report mechanisms with the protocol.
 * 
 * This class is used to test both the Success report generation and the sent
 * bytes call on the MSRP Listener
 * 
 * @author João André Pereira Antunes 2008
 */
public class CustomExampleReportMechanism
    extends DefaultReportMechanism
{
    protected CustomExampleReportMechanism()
    {
    	;
    }

    private static class Singleton
    {
        private final static CustomExampleReportMechanism INSTANCE =
            new CustomExampleReportMechanism();
    }

    public static CustomExampleReportMechanism getInstance()
    {
        return Singleton.INSTANCE;
    }

    /**
     * Custom value, different from the default 1024 just
     * to make sure that it can work with a different value.
     */
    @Override
    public int getTriggerGranularity()
    {
        return 2014;
    }

    /**
     * for testing purposes, we reversed the behaviour on the default methods.
     * @see DefaultReportMechanism#shouldTriggerSentHook(Message, Session, long)
     */
    @Override
    public boolean shouldGenerateReport(Message message, long lastCallCount,
        long callCount)
    {
        if (message.isComplete())
            return true;

        long size = message.getSize();
        if (size == Message.UNKNOWN)
        	return true;
        if (size < 0)
        	return false;
        else
        {
            long lastPercentage = lastCallCount * 100 / size;
            long currentPercentage =
                message.getDataContainer().currentReadOffset() * 100 / size;
            if (size <= 500 * 1024)
                return lastPercentage < 50 && currentPercentage >= 50;
            else
                 return lastPercentage / 10 == (currentPercentage / 10) - 1;
        }
    }

    /**
     * for testing purposes, we reversed the behaviour on the default methods.
     * @see DefaultReportMechanism#shouldGenerateReport(Message, long, long)
     */
    @Override
    public boolean shouldTriggerSentHook(Message message, Session session,
    		long nrBytesLastCall)
    {
        return message.isComplete();
    }
}
