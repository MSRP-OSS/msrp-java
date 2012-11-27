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


/**
 * Default report mechanism offered by this stack as a singleton
 * 
 * When receiving: It generates success reports upon message completion.
 * 
 * When sending: It generates sent bytes notifications in the following manner:
 * If the message is smaller than 500KB, twice, when half is sent and when all
 * is sent For bigger than 500KB messages, for every ~10% of sent bytes of the
 * message, the sent hook is triggered
 * 
 * See comments and details of the inherited methods for more information
 * 
 * TODO change the shouldGenerateReport method
 * 
 * @author João André Pereira Antunes 2008
 * 
 */
public class DefaultReportMechanism
    extends ReportMechanism
{
    protected DefaultReportMechanism()
    {
    	;
    }

    private static class Singleton
    {
        private final static DefaultReportMechanism INSTANCE =
            new DefaultReportMechanism();
    }

    public static DefaultReportMechanism getInstance()
    {
        return Singleton.INSTANCE;
    }

    /**
     * Generate {@link SuccessReport} only when message is complete.
     */
    @Override
    public boolean shouldGenerateReport(Message message, long lastCallCount,
        long CallCount)
    {
        return message.isComplete();
    }

    /**
     * Trigger the update after every 10% of the total size sent.
     * when message size over 500Kb, trigger only when exceeding 50%.
     * Always trigger when all is sent.
     */
    @Override
    public boolean shouldTriggerSentHook(Message message, Session session,
    		long nrBytesLastCall)
    {
        if (message.isComplete())
            return true;

        long size = message.getSize();
        if (size < 0)
            return false;
        else
        {
            long lastPercentage = nrBytesLastCall * 100 / size;
            long currentPercentage =
                message.getDataContainer().currentReadOffset() * 100 / size;
            if (size <= 500 * 1024)
                return lastPercentage < 50 && currentPercentage >= 50;
            else
                 return lastPercentage / 10 == (currentPercentage / 10) - 1;
        }
    }

    @Override
    public int getTriggerGranularity()
    {
        return 1024;
    }
}
