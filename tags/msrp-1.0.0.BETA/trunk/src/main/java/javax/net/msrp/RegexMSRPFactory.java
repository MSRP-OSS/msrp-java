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

import java.util.regex.Pattern;

/**
 * This class will make the parsers comply with the MSRP specification (more
 * specifically with the formal syntax and will solve Issue #16). All of the
 * regex patterns used by the parsers must come from this class.
 * 
 * TODO the majority of the formal syntax of the MSRP rfc should be represented
 * here, which isn't done yet.
 * 
 * @author João André Pereira Antunes
 * 
 */
public class RegexMSRPFactory
{

    // Starting with the basic MSRP building blocks
    public static final Pattern token =
        Pattern
            .compile("[\\x21|\\x23-\\x27|\\x2A-2B|\\x2D-\\x2E|\\x30-\\x39|\\x41-\\x5A|\\x5E-\\x7E]{1,}");

}
