/* An actor that converts a string to an array of bytes.

 Copyright (c) 1998-2013 The Regents of the University of California.
 All rights reserved.
 Permission is hereby granted, without written agreement and without
 license or royalty fees, to use, copy, modify, and distribute this
 software and its documentation for any purpose, provided that the above
 copyright notice and the following two paragraphs appear in all copies
 of this software.

 IN NO EVENT SHALL THE UNIVERSITY OF CALIFORNIA BE LIABLE TO ANY PARTY
 FOR DIRECT, INDIRECT, SPECIAL, INCIDENTAL, OR CONSEQUENTIAL DAMAGES
 ARISING OUT OF THE USE OF THIS SOFTWARE AND ITS DOCUMENTATION, EVEN IF
 THE UNIVERSITY OF CALIFORNIA HAS BEEN ADVISED OF THE POSSIBILITY OF
 SUCH DAMAGE.

 THE UNIVERSITY OF CALIFORNIA SPECIFICALLY DISCLAIMS ANY WARRANTIES,
 INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE. THE SOFTWARE
 PROVIDED HEREUNDER IS ON AN "AS IS" BASIS, AND THE UNIVERSITY OF
 CALIFORNIA HAS NO OBLIGATION TO PROVIDE MAINTENANCE, SUPPORT, UPDATES,
 ENHANCEMENTS, OR MODIFICATIONS.

 PT_COPYRIGHT_VERSION_2
 COPYRIGHTENDKEY

 */
package org.terraswarm.lib;

import ptolemy.actor.lib.conversions.Converter;
import ptolemy.data.DoubleToken;
import ptolemy.data.StringToken;
import ptolemy.data.type.BaseType;
import ptolemy.kernel.CompositeEntity;
import ptolemy.kernel.util.IllegalActionException;
import ptolemy.kernel.util.NameDuplicationException;

///////////////////////////////////////////////////////////////////
/// StringToUnsignedByteArray

/**
 Convert a string to an array of unsigned byte.  The conversion is performed
 using the default character set, returned by the system property
 "file.encoding".

 @author Winthrop Williams, Steve Neuendorffer
 @version $Id$
 @since Ptolemy II 2.0
 @Pt.ProposedRating Red (winthrop)
 @Pt.AcceptedRating Red (winthrop)
 */
public class DoubleToString extends Converter {
    /** Construct an actor with the given container and name.
     *  @param container The container.
     *  @param name The name of this actor.
     *  @exception IllegalActionException If the actor cannot be contained
     *   by the proposed container.
     *  @exception NameDuplicationException If the container already has an
     *   actor with this name.
     */
    public DoubleToString(CompositeEntity container, String name)
            throws NameDuplicationException, IllegalActionException {
        super(container, name);

        input.setTypeEquals(BaseType.DOUBLE);

        output.setTypeEquals(BaseType.STRING);
    }

    ///////////////////////////////////////////////////////////////////
    ////                         public methods                    ////

    /** Consume one string token on the input port and output a new array
     *  token of integer tokens on the output port.  The low byte of each
     *  integer is the byte form of one of the characters.  The other
     *  three bytes of each integer may be 0x000000 or 0xFFFFFF.  The
     *  first character of the string is copied to the first element of
     *  the array, and so on.  NOTE: Assumes an 8-bit character set is
     *  the default setting for this implementation of Java.
     *
     *  @exception IllegalActionException If there is no director.
     *  FIXME: Does this method actually check if there is a director?
     */
    public void fire() throws IllegalActionException {
        super.fire();
        double inputValue = ((DoubleToken) input.get(0)).doubleValue();

        String value = String.valueOf(inputValue);
        
        output.send(0, new StringToken(value));
    }

    /** Return false if the input port has no token, otherwise return
     *  what the superclass returns (presumably true).
     *  @exception IllegalActionException If there is no director.
     */
    public boolean prefire() throws IllegalActionException {
        if (!input.hasToken(0)) {
            return false;
        }

        return super.prefire();
    }
}