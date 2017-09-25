/* This actor implements a subscriber in a HLA/CERTI federation.

@Copyright (c) 2013-2016 The Regents of the University of California.
All rights reserved.

Permission is hereby granted, without written agreement and without
license or royalty fees, to use, copy, modify, and distribute this
software and its documentation for any purpose, provided that the
above copyright notice and the following two paragraphs appear in all
copies of this software.

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

package org.hlacerti.lib;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import ptolemy.actor.CompositeActor;
import ptolemy.actor.Director;
import ptolemy.actor.SuperdenseTimeDirector;
import ptolemy.actor.TypeEvent;
import ptolemy.actor.TypeListener;
import ptolemy.actor.TypedAtomicActor;
import ptolemy.actor.TypedIOPort;
import ptolemy.actor.util.Time;
import ptolemy.actor.util.TimedEvent;
import ptolemy.data.BooleanToken;
import ptolemy.data.DoubleToken;
import ptolemy.data.FloatToken;
import ptolemy.data.IntToken;
import ptolemy.data.LongToken;
import ptolemy.data.ShortToken;
import ptolemy.data.StringToken;
import ptolemy.data.Token;
import ptolemy.data.UnsignedByteToken;
import ptolemy.data.expr.Parameter;
import ptolemy.data.expr.StringParameter;
import ptolemy.data.type.BaseType;
import ptolemy.data.type.Type;
import ptolemy.kernel.CompositeEntity;
import ptolemy.kernel.util.Attribute;
import ptolemy.kernel.util.IllegalActionException;
import ptolemy.kernel.util.NameDuplicationException;
import ptolemy.kernel.util.Settable;
import ptolemy.kernel.util.Workspace;

///////////////////////////////////////////////////////////////////
//// HlaSubscriber

/**
 * This actor implements a subscriber in a HLA/CERTI federation.
 *
 * <p> This subscriber is associated to one HLA attribute. Reflected
 * values of the HLA attribute are received from the HLA/CERTI
 * Federation by the {@link HlaManager} attribute. The {@link
 * HlaManager} invokes the putReflectedAttribute() to put the received
 * value in the subscriber tokens queue and to program its next firing
 * times, using the _fireAt() method.</p>
 *
 * <p>The name of this actor is mapped to the name of the HLA attribute in the
 * federation and need to match the Federate Object Model (FOM) specified for
 * the Federation. The data type of the output port has to be the same type of
 * the HLA attribute. The parameter <i>classObjectHandle</i> needs to match the
 * attribute object class describes in the FOM.
 *
 *  @author Gilles Lasnier, Contributors: Patricia Derler, David Come
 *  @version $Id$
 *  @since Ptolemy II 10.0
 *
 *  @Pt.ProposedRating Yellow (glasnier)
 *  @Pt.AcceptedRating Red (glasnier)
 */
public class HlaSubscriber extends TypedAtomicActor {

    /** Construct a HlaSubscriber actor.
     *  @param container The container.
     *  @param name The name of this actor.
     *  @exception IllegalActionException If the entity cannot be contained
     *  by the proposed container.
     *  @exception NameDuplicationException If the container already has an
     *  actor with this name.
     */
    public HlaSubscriber(CompositeEntity container, String name)
            throws NameDuplicationException, IllegalActionException {
        super(container, name);

        // The single output port of the actor.
        output = new TypedIOPort(this, "output", false, true);

        // Basic token types available.
        typeSelector = new StringParameter(this, "typeSelector");
        typeSelector.setDisplayName("type of the parameter"); 
        typeSelector.addChoice("int");
        typeSelector.addChoice("double");
        typeSelector.addChoice("string");
        typeSelector.addChoice("boolean");

        // Allow the user to change the output port's type directly.
        // Useful for setting a value to typeSelector after reading the MomL file.
        output.addTypeListener(new TypeListener() {
            @Override
            public void typeChanged(TypeEvent event) {
                typeSelector.setExpression(event.getNewType().toString());
            }
        }
                );

        useCertiMessageBuffer = new Parameter(this, "useCertiMessageBuffer");
        useCertiMessageBuffer.setTypeEquals(BaseType.BOOLEAN);
        useCertiMessageBuffer.setExpression("false");
        useCertiMessageBuffer.setDisplayName("use CERTI message buffer");
        attributeChanged(useCertiMessageBuffer);

        classObjectName = new Parameter(this, "classObjectName");
        classObjectName.setDisplayName("Object class in FOM");
        classObjectName.setTypeEquals(BaseType.STRING);
        classObjectName.setExpression("\"myObjectClass\"");
        attributeChanged(classObjectName);

        // XXX: FIXME: really needed ?
        objectName = new Parameter(this, "objectName");
        objectName.setVisibility(Settable.NOT_EDITABLE);
        objectName.setDisplayName("Object name provided by the RTI");
        objectName.setTypeEquals(BaseType.STRING);
        objectName.setExpression("\"objectName\"");

        attributeName = new Parameter(this, "attributeName");
        attributeName.setDisplayName("Name of the attribute to receive");
        attributeName.setTypeEquals(BaseType.STRING);
        attributeName.setExpression("\"HLA attribute name\"");

        attributeChanged(objectName);
        attributeChanged(attributeName);

        // Initialize default private values.
        _reflectedAttributeValues = new LinkedList<TimedEvent>();
        _useCertiMessageBuffer = false;

        // Set handle to impossible values <= XXX: FIXME: GiL: true ?
        _attributeHandle = Integer.MIN_VALUE;
        _classHandle = Integer.MIN_VALUE;
        _objectHandle = Integer.MIN_VALUE;
    }

    ///////////////////////////////////////////////////////////////////
    ////                         public variables                  ////

    /** The object class of the HLA attribute to publish. */
    public Parameter classObjectName;

    /** Name of the object who owns the attribute. */
    public Parameter objectName;

    /** The HLA attribute the HLASubscriber is interested in. */
    public Parameter attributeName;

    /** Indicate if the event is wrapped in a CERTI message buffer. */
    public Parameter useCertiMessageBuffer;

    /** Parameter used to set up the type of the output port. Synced with
     * output port's type (changes go both way).
     */
    public StringParameter typeSelector;

    /** The output port. */
    public TypedIOPort output = null;

    ///////////////////////////////////////////////////////////////////
    ////                         public methods                    ////

    /** Call the attributeChanged method of the parent. Check if the
     *  user as set the object class of the HLA attribute to subscribe to.
     *  @param attribute The attribute that changed.
     *  @exception IllegalActionException If there is zero or more than one
     *  {@link HlaManager} per Ptolemy model.
     */
    @Override
    public void attributeChanged(Attribute attribute)
            throws IllegalActionException {
        if (attribute == classObjectName) {
            String value = ((StringToken) classObjectName.getToken())
                    .stringValue();
            if (value.compareTo("") == 0) {
                throw new IllegalActionException(this,
                        "Cannot have empty name !");
            }
        } else if (attribute == useCertiMessageBuffer) {
            _useCertiMessageBuffer = ((BooleanToken) useCertiMessageBuffer
                    .getToken()).booleanValue();
        } else if (attribute == attributeName || attribute == objectName) {
            // XXX: FIXME: to check ?
            StringToken objNameToken = (StringToken) objectName.getToken();
            StringToken paramNameToken = (StringToken) attributeName.getToken();
            String paramName = "";
            String objectName = "";

            if (objNameToken == null) {
                throw new IllegalActionException(this,
                        "Cannot have empty name !");
            } else {
                objectName = objNameToken.stringValue();
            }

            if (paramNameToken == null) {
                throw new IllegalActionException(this,
                        "Cannot have empty name !");
            } else {
                paramName = paramNameToken.stringValue();
            }

            if (!"objectName".equals(objectName) || !"parameterName".equals(paramName)) {
                setDisplayName(objectName + " " + paramName);
            }

        } else if (attribute == typeSelector) {
            String newPotentialTypeName = typeSelector.stringValue();
            if (newPotentialTypeName == null) {
                return;
            }

            Type newPotentialType = BaseType.forName(newPotentialTypeName);
            if (newPotentialType != null && !newPotentialType.equals(output.getType())) {
                output.setTypeEquals(newPotentialType);
            }
        }

        super.attributeChanged(attribute);
    }

    /** Clone the actor into the specified workspace.
     *  @param workspace The workspace for the new object.
     *  @return A new actor.
     *  @exception CloneNotSupportedException If a derived class contains
     *  an attribute that cannot be cloned.
     */
    @Override
    public Object clone(Workspace workspace) throws CloneNotSupportedException {
        // Manage public members ? <= XXX: FIXME: GiL: true ?
        HlaSubscriber newObject = (HlaSubscriber) super.clone(workspace);

        // Manage private members.
        newObject._reflectedAttributeValues = new LinkedList<TimedEvent>();
        newObject._hlaManager = _hlaManager; // XXX: FIXME: GiL: init to null ?
        newObject._useCertiMessageBuffer = _useCertiMessageBuffer;

        // XXX: FIXME: GiL: missing parameter init. TBC

        newObject._attributeHandle = _attributeHandle;
        newObject._classHandle = _classHandle;
        newObject._objectHandle = Integer.MIN_VALUE;

        return newObject;
    }

    /** Check if there is one and only one {@link HlaManager} deployed in the
     *  Ptolemy model.
     *  @exception IllegalActionException If there is zero or more than one
     *  {@link HlaManager} per Ptolemy model.
     */
    @Override
    public void initialize() throws IllegalActionException {
        super.initialize();

        // Find the HlaManager by looking into container
        // (recursively if needed).
        CompositeActor ca = (CompositeActor) this.getContainer();
        List<HlaManager> hlaManagers = null;

        // XXX: FIXME: GiL: ca may be null ?
        while (ca != null) {
            hlaManagers = ca.attributeList(HlaManager.class);
            if (hlaManagers.size() < 1) {
                ca = (CompositeActor) ca.getContainer();
            } else {
                break;
            }
        }

        if (hlaManagers == null || hlaManagers.size() < 1) {
            throw new IllegalActionException(this,
                    "A HlaManager attribute is required to use this actor");
        } else if (hlaManagers.size() > 1) {
            throw new IllegalActionException(this,
                    "Only one HlaManager attribute is allowed per model");
        }
        // Here, we are sure that there is one and only one instance of the
        // HlaManager in the Ptolemy model.
        _hlaManager = hlaManagers.get(0);

    }

    /** Send each update value of the HLA attribute (mapped to this actor) as
     *  token when its time.
     *  @exception IllegalActionException Not thrown here.
     */
    @Override
    public void fire() throws IllegalActionException {
        super.fire();
        Time currentTime = getDirector().getModelTime();

        Iterator<TimedEvent> it = _reflectedAttributeValues.iterator();
        it.hasNext();
        TimedEvent te = it.next();
        if (te.timeStamp.compareTo(currentTime) == 0) {

            Token content = _buildToken((Object[]) te.contents);
            int origin = -1;
            if (te instanceof OriginatedEvent) {
                OriginatedEvent oe = (OriginatedEvent) te;
                origin = oe.objectID;
            }

            //either it is NOT OriginatedEvent and we let it go
            //either it is and it has to match the origin

            // FIXME: XXX: fix usage of object ID instead of 
            //if (origin == -1 || origin == getAttributeHandle()) {
            this.outputPortList().get(0).send(0, content);

            if (_debugging) {
                _debug(this.getDisplayName()
                        + " Called fire() - An updated value"
                        + " of the HLA attribute \"" + getAttributeName() + " from "
                        + origin
                        + "\" has been sent at \"" + te.timeStamp + "\" ("
                        +content.toString()+")");

            } //end debug
            //} //end test fire
            it.remove();
        } //end if (te.timeStamp.compareTo(currentTime) == 0) { ...
        
        // Refire if token to process at same time
        if (it.hasNext()) {
            TimedEvent tNext = it.next();
            if (tNext.timeStamp.compareTo(currentTime) == 0) {
            // Refiring the actor to handle the other tokens
            // that are still in channels
            getDirector().fireAt(this, currentTime);
            }
        }

        
        /*
        while (it.hasNext()) {
            it.hasNext();
            TimedEvent te = it.next();
            if (te.timeStamp.compareTo(currentTime) == 0) {

                Token content = _buildToken((Object[]) te.contents);
                int origin = -1;
                if (te instanceof OriginatedEvent) {
                    OriginatedEvent oe = (OriginatedEvent) te;
                    origin = oe.objectID;
                }

                //either it is NOT OriginatedEvent and we let it go
                //either it is and it has to match the origin

                // FIXME: XXX: fix usage of object ID instead of 
                //if (origin == -1 || origin == getAttributeHandle()) {
                this.outputPortList().get(0).send(0, content);

                if (_debugging) {
                    _debug(this.getDisplayName()
                            + " Called fire() - An updated value"
                            + " of the HLA attribute \"" + getAttributeName() + " from "
                            + origin
                            + "\" has been sent at \"" + te.timeStamp + "\" ("
                            +content.toString()+")");

                } //end debug
                //} //end test fire
                it.remove();
            } //end current time
        } //end of while
        */
    } //end of fire

    /**
     * Return a string that is used to identify the HlaSubscriber.
     * @return A string that is used to identify the HlaSubscriber.
     */
    public String getIdentity() {
        return _getObjectName() + "-" + getAttributeName();
    }

    /** Return the attribute handle value.
     * @return the attributeHandle
     * @see #setAttributeHandle
     */
    public int getAttributeHandle() {
        return _attributeHandle;
    }

    /** Return the class handle value.
     * @return the classHandle
     * @see #setClassHandle
     */
    public int getClassHandle() {
        return _classHandle;
    }

    /** Returns the object handle value.
     * @return the objectHandle
     * @see #setObjectHandle
     */
    //public int getObjectHandle() {
    //    return _objectHandle;
    //}

    /** Set the attribute handle value.
     * @param attributeHandle the attributeHandle to set
     * @see #getAttributeHandle
     */
    public void setAttributeHandle(int attributeHandle) {
        _attributeHandle = attributeHandle;
    }

    /** Set the class handle value.
     * @param classHandle the classHandle to set
     * @see #getClassHandle
     */
    public void setClassHandle(int classHandle) {
        _classHandle = classHandle;
    }

    /** Set the object handle value.
     * @param objectHandle the objectHandle to set
     * @see #getObjectHandle
     */
    //public void setObjectHandle(int objectHandle) {
    //    _objectHandle = objectHandle;
    //}

    /** Store each updated value of the HLA attribute (mapped to this actor) in
     *  the tokens queue. Then, program the next firing time of this actor to
     *  send the token at its expected time. This method is called by the
     *  {@link HlaManager} attribute.
     *  @param event The event containing the updated value of the HLA attribute
     *  and its time-stamp.
     *  @exception IllegalActionException Not thrown here.
     */
    public void putReflectedHlaAttribute(TimedEvent event)
            throws IllegalActionException {
        // Add the update value to the queue.
        _reflectedAttributeValues.add(event);

        System.out.println(this.getFullName() + " : putReflectedHlaAttribute: event timestamp = " + event.timeStamp.toString());
        System.out.println(this.getFullName() + " : putReflectedHlaAttribute: event value = " + event.contents.toString());

        // Program the next firing time for the received update value.
        _fireAt(event.timeStamp);
    }

    /** Indicate if the HLA subscriber actor uses the CERTI message
     *  buffer API.
     *  @return true if the HLA publisher actor uses the CERTI message and false if
     *  it doesn't.
     *  @exception IllegalActionException
     */
    public boolean useCertiMessageBuffer() throws IllegalActionException {
        return _useCertiMessageBuffer;
    }

    /**
     * Return the kind of HLA Attribute handled by the HLASubscriber.
     * @return The kind of HLA Attribute the HLASuscriber is handling.
     */
    public String getAttributeName() {
        String parameter = "";
        try {
            parameter = ((StringToken) attributeName.getToken()).stringValue();
        } catch (IllegalActionException illegalActionException) {
            // XXX: FIXME: GiL: TBC
            System.out.println("HlaSubscriber.getAttributeName(): bad attributeName token string value");
        }
        return parameter;
    }

    /**
     * Return the class object name of HLA attribute handled by the HlaSubscriber.
     * @return The class object name of HLA attribute handled by this HlaSubscriber.
     */
    public String getClassObjectName() {
        String name = "";
        try {
            name = ((StringToken) classObjectName.getToken()).stringValue();
        } catch (IllegalActionException illegalActionException) {
            // XXX: FIXME: GiL: TBC
            System.out.println("HlaSubscriber.getClassObjectName(): bad classObjectName token string value");
        }
        return name;
    }


    /**
     * TBC
     */
    @Override
    public void wrapup() throws IllegalActionException {
        super.wrapup();

        // Set HLA handles to impossible values <= XXX: FIXME: GiL: impossible, does
        // CERTI may attribute negative value ?
        _attributeHandle = Integer.MIN_VALUE;
        _classHandle = Integer.MIN_VALUE; // XXX: FIXME: must be objectClassHandle
        _objectHandle = Integer.MIN_VALUE; // XXX: FIXME: must be removed
    }

    ///////////////////////////////////////////////////////////////////
    ////                         private methods                   ////

    /** Build the corresponding typed token from the contents of a
     *  {@link TimedEvent}s stored in the reflectedAttributeValues queue. The
     *  structure of the contents is an array object <i>obj</i> where:
     *  obj[0] is the expected data type and; obj[1] is the object buffer
     *  which contains the typed value.
     * @param obj The array object containing data type indication and buffer.
     * @return value The corresponding typed token.
     * @exception IllegalActionException If the expected data type is not handled
     * Due to previous check this case .
     */
    private Token _buildToken(Object[] obj) throws IllegalActionException {
        Token value = null;

        BaseType type = (BaseType) obj[0];
        if (!type.equals(output.getType())) {
            throw new IllegalActionException(this,
                    "The type of the token to build doesn't match the output port type of "
                            + this.getDisplayName());
        }

        if (type.equals(BaseType.BOOLEAN)) {
            value = new BooleanToken((Boolean) obj[1]);
        } else if (type.equals(BaseType.UNSIGNED_BYTE)) {
            Integer valInt = (Integer) obj[1];
            value = new UnsignedByteToken(valInt.byteValue());
        } else if (type.equals(BaseType.DOUBLE)) {
            value = new DoubleToken((Double) obj[1]);
        } else if (type.equals(BaseType.FLOAT)) {
            value = new FloatToken((Float) obj[1]);
        } else if (type.equals(BaseType.INT)) {
            Integer valInt = (Integer) obj[1];
            value = new IntToken(valInt.intValue());
        } else if (type.equals(BaseType.LONG)) {
            value = new LongToken((Long) obj[1]);
        } else if (type.equals(BaseType.SHORT)) {
            value = new ShortToken((Short) obj[1]);
        } else if (type.equals(BaseType.STRING)) {
            value = new StringToken((String) obj[1]);
        } else {
            throw new IllegalActionException(this,
                    "The current type is not supported by this implementation");
        }

        return value;
    }

    ///////////////////////////////////////////////////////////////////
    ////                         private method                    ////

    /**
     * Return the object name used in the HLA Federation
     */
    private String _getObjectName() {
        try {
            return ((StringToken) objectName.getToken()).stringValue();
        } catch (IllegalActionException illegalActionException) {
            // XXX: FIXME: GiL: TBC
            System.out.println("HlaSubscriber._getObjectName(): bad objectName token string value");
        }
        return "";
    }

    ///////////////////////////////////////////////////////////////////
    ////                         private variables                 ////

    /** List of updated values for the HLA attribute. */
    private LinkedList<TimedEvent> _reflectedAttributeValues;

    /** A reference to the associated {@link HlaManager}. */
    private HlaManager _hlaManager;

    /** Indicate if the event is wrapped in a CERTI message buffer. */
    private boolean _useCertiMessageBuffer;

    /**
     * Handle (object ID) provided by the RTI for the attribute published.
     */
    private int _attributeHandle;

    /**
     * Handle provided by the RTI for the object's class
     * owning the attribute to receive.
     */
    private int _classHandle;

    /**
     * Handle provided by the RTI for the object owning the attribute
     * to publish.
     */
    private int _objectHandle;
}
