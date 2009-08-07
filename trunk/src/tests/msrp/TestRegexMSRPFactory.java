/*
 * Copyright © João Antunes 2009 This file is part of MSRP Java Stack.
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
package msrp;

import java.util.regex.Matcher;

import org.junit.*;

/**
 * Class that tests thorougly the RegexMSRPFactory class
 * 
 * @see RegexMSRPFactory
 * @author João André Pereira Antunes
 * 
 */
public class TestRegexMSRPFactory
{
    @Test
    public void testTokenRegex()
    {
        Matcher testMatcher;
        // Test with the empty string
        testMatcher = RegexMSRPFactory.token.matcher("");
        Assert.assertFalse(
            "Error, the empty string shouldn't be considered a token",
            testMatcher.matches());

        // with a single character from one of the groups
        testMatcher = RegexMSRPFactory.token.matcher("~");
        Assert.assertTrue("Error, the ~ char should be considered a token",
            testMatcher.matches());

        // with one character from each group
        testMatcher = RegexMSRPFactory.token.matcher("!#*-1A^");
        Assert
            .assertTrue(
                "Error, token regex badly defined: !#*-1A^ should be considered a valid token",
                testMatcher.matches());
        
        // a string with a blank space shouldn't be a token
        testMatcher = RegexMSRPFactory.token.matcher("test ");
        Assert
            .assertFalse(
                "Error, a string with a space shouldn't be a considered a token",
                testMatcher.matches());

        // Just something extra to remember this issue by:
        testMatcher =
            RegexMSRPFactory.token.matcher("prs.genericfile/prs.rawbyte");
        Assert
            .assertTrue(
                "oh nooo 'prs.genericfile/prs.rawbyte' should definitely be considered a token",
                testMatcher.matches());

    }

}
