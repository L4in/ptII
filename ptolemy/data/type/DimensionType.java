/** A class representing the type of a multi-dimensional array.

 Copyright (c) 1997-1999 The Regents of the University of California.
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

@ProposedRating Red (neuendor@eecs.berkeley.edu)
@AcceptedRating Red (cxh@eecs.berkeley.edu)

*/

package ptolemy.data.type;

import ptolemy.graph.*;
import ptolemy.kernel.util.IllegalActionException;
import ptolemy.kernel.util.InternalErrorException;
import java.util.Enumeration;
import collections.*;

//////////////////////////////////////////////////////////////////////////
//// DimensionType
/**
A class representing the size of a multi-dimensional array as a type.

@author Steve Neuendorffer
$Id$

*/

public class DimensionType implements Type
{
    /** Create a new dimension type variable, initialized to bottom.
     */
    public DimensionType() {
        _value = Dimension.BOTTOM;
        _isSettable = true;
    }

    /** 
     * Create a new dimension type constant with the given value.
     */
    public DimensionType(Dimension value) {
        _value = value;
        _isSettable = false;
    }
       
    /** Given a constraint on this Type, return an enumeration of constraints
     *  on other types, on which this type depends.
     *  In this base class, we assume there is nothing to expand, so return
     *  an enumeration with a single element of the given constraint.
     */
    public Enumeration expandConstraint(Inequality constraint) {
        LinkedList list = new LinkedList();
        list.insertFirst(constraint);
        return list.elements();
    }

    public boolean equals(Object type) {
        if(!(type instanceof DimensionType)) return false;        
        return _value.equals(((DimensionType)type)._value);
    }

    /** REturn the object associated with this type
     */
    public Object getAssociatedObject() {
        return null;
    }

    /** Return the value of this term.  If this term is a constant,
     *  return that constant; if this term is a variable, return the
     *  current value of that variable; if this term is a function,
     *  return the evaluation of that function based on the current
     *  value of variables in the function.
     *  @return an Object representing an element in the underlying CPO.
     */
    public Object getValue() {
        return _value;
    }

    /** Return an array of variables contained in this term.
     *  If this term is a constant, return an array of size zero;
     *  if this term is a variable, return an array of size one that
     *  contains this variable; if this term is a function, return an
     *  array containing all the variables in the function.
     *  @return an array of InequalityTerms
     */
    public InequalityTerm[] getVariables() {
        InequalityTerm termArray[];
        if(isSettable()) {
            termArray = new InequalityTerm[1];
            termArray[0] = this;
        } else {
            termArray = new InequalityTerm[0];
        }
        return termArray;
    }

    /** Check whether this term can be set to a specific element of the
     *  underlying CPO. Only variable terms are settable, constant
     *  and function terms are not.
     *  @return <code>true</code> if this term is a variable;
     *   <code>false</code> otherwise.
     */
    public boolean isSettable() {
        return _isSettable;
    }

    /** Check whether the current value of this term is acceptable,
     *  and return true if it is.
     *  @return True if the current value is acceptable.
     */
    public boolean isValueAcceptable() {
        return _value.isInstantiable();
    }

    /** Set the value of this term to the specified CPO element.
     *  Only terms consisting of a single variable can have their
     *  values set.
     *  @param e an Object representing an element in the
     *   underlying CPO.
     *  @exception IllegalActionException If this term is not a variable.
     */
    public void setValue(Object e)
            throws IllegalActionException {
        if(isSettable()) {
            if(e instanceof Dimension)
                _value = (Dimension)e;
            else
                throw new IllegalActionException("Value of " +
                        "type must be compatible with " +
                        "Dimension.");
        } else
            throw new IllegalActionException("This " +
                    "type cannot be set to the value because it " +
                    "is not a variable.");
    }

    /** The value of this type
     */
    protected Dimension _value;

    /** True if this type is a variable type, and its value can be set.
     */
    protected boolean _isSettable;
}



