/* A DD3D Shape consisting of a sphere

 Copyright (c) 1998-2000 The Regents of the University of California.
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

@ProposedRating Red (chf@eecs.berkeley.edu)
@AcceptedRating Red (chf@eecs.berkeley.edu)
*/

package ptolemy.domains.dd3d.lib;

import ptolemy.kernel.util.*;
import ptolemy.data.*;
import ptolemy.data.expr.Parameter;
import ptolemy.data.type.BaseType;
import ptolemy.actor.*;
import ptolemy.actor.lib.*;
import ptolemy.domains.dt.kernel.DTDebug;
import ptolemy.domains.dd3d.kernel.*;

import com.sun.j3d.utils.geometry.*;
import javax.media.j3d.*;
import javax.vecmath.*;

//////////////////////////////////////////////////////////////////////////
//// SphereActor

/** This actor contains the geometry and appearance specifications for a DD3D
sphere.  The output port is used to connect this actor to the Java3D scene
graph. This actor may be used along with the Scale3D transformer to produce
ellipsoid shapes. This actor will only have meaning in the DD3D domain.

@author C. Fong
*/
public class SphereActor extends Shaded3DActor {

    /** Construct an actor with the given container and name.
     *  @param container The container.
     *  @param name The name of this actor.
     *  @exception IllegalActionException If the actor cannot be contained
     *   by the proposed container.
     *  @exception NameDuplicationException If the container already has an
     *   actor with this name.
     */
    public SphereActor(TypedCompositeActor container, String name)
            throws IllegalActionException, NameDuplicationException {
        super(container, name);
        
        radius = new Parameter(this, "radius", new DoubleToken(0.5));
    }
    
    ///////////////////////////////////////////////////////////////////
    ////                         parameters                        ////

    /** The radius of the sphere
     *  This parameter should contain a doubleToken
     *  The default value of this parameter is the DoubleToken 0.5
     */
    public Parameter radius;


    ///////////////////////////////////////////////////////////////////
    ////                         public methods                    ////
    
    /** Clone the actor into the specified workspace. This calls the
     *  base class and then sets the parameters of the new actor.
     *  @param ws The workspace for the new object.
     *  @return A new actor.
     *  @exception CloneNotSupportedException If a derived class contains
     *   an attribute that cannot be cloned.
     */
    public Object clone(Workspace workspace) throws CloneNotSupportedException {
        SphereActor newobj = (SphereActor)super.clone(workspace);
        newobj.radius = (Parameter)newobj.getAttribute("radius");
        return newobj;
    }

    /** Return the encapsulated Java3D node of this 3D actor. The encapsulated
     *  node for this actor is a Java3D sphere.
     *  @return the Java3D Sphere
     */    
    public Node getNodeObject() {
        return (Node) containedNode;
    }
    
    ///////////////////////////////////////////////////////////////////
    ////                         protected methods                 ////

    /** Create the shape and appearance of the encapsulated sphere
     *  @exception IllegalActionException If the value of some parameters can't
     *   be obtained
     */
    protected void _createModel() throws IllegalActionException {
        super._createModel();
        containedNode = new Sphere((float)_getRadius(),Sphere.GENERATE_NORMALS,_appearance);
    }

    
    ///////////////////////////////////////////////////////////////////
    ////                         private methods                   ////

    /**  Return the value of the radius parameter
     *  @return the radius of the sphere
     *  @exception IllegalActionException If the value of some parameters can't
     *   be obtained
     */
    private double _getRadius() throws IllegalActionException {
        return ((DoubleToken) radius.getToken()).doubleValue();
    }

    ///////////////////////////////////////////////////////////////////
    ////                         private variables                 ////

    private Sphere containedNode;
}
