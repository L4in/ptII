/* A transformer that removes dead token and type creations.

 Copyright (c) 2001-2002 The Regents of the University of California.
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
@ProposedRating Red (cxh@eecs.berkeley.edu)
@AcceptedRating Red (cxh@eecs.berkeley.edu)
*/

package ptolemy.copernicus.java;

import soot.*;
import soot.jimple.*;
import soot.jimple.toolkits.invoke.ClassHierarchyAnalysis;
import soot.jimple.toolkits.invoke.InvokeGraph;
import soot.jimple.toolkits.invoke.MethodCallGraph;
import soot.jimple.toolkits.invoke.SiteInliner;
import soot.toolkits.graph.*;
import soot.toolkits.scalar.*;
import soot.dava.*;
import soot.util.*;
import java.io.*;
import java.util.*;

import ptolemy.kernel.util.*;
import ptolemy.kernel.*;
import ptolemy.actor.*;
import ptolemy.moml.*;
import ptolemy.domains.sdf.kernel.SDFDirector;
import ptolemy.data.*;
import ptolemy.data.type.Typeable;
import ptolemy.data.expr.Variable;
import ptolemy.copernicus.kernel.SootUtilities;

//////////////////////////////////////////////////////////////////////////
//// UnreachableMethodRemover
/**
A transformer that removes methods that are not reachable.  Note that
this is a fairly braindead implementation.  Specifically,
it does not attempt any
Variable Type Analysis to reduce what methods may be called.  However,
for the purposes of code generation, we will have already done most
of the interesting type analysis by the time this runs, so it is not
really a big deal.

@author Stephen Neuendorffer
@version $Id$
@since Ptolemy II 2.0
*/
// FIXME: This is currently unsafe, because method bodies
// for context classes don't exist in the method call graph.
// We need a lightweight way of getting the method call graph for
// these methods without creating a JimpleBody which is expensive.
// FIXME: This also doesn't work because it doesn't take interfaces
// into account.
public class UnreachableMethodRemover extends SceneTransformer {
    /** Construct a new transformer
     */
    private UnreachableMethodRemover() {}

    /* Return the instance of this transformer.
     */
    public static UnreachableMethodRemover v() {
        return instance;
    }

    public String getDefaultOptions() {
        return "";
    }

    public String getDeclaredOptions() {
        return super.getDeclaredOptions() + " debug";
    }

    protected void internalTransform(String phaseName, Map options) {
        System.out.println("UnreachableMethodRemover.internalTransform("
                + phaseName + ", " + options + ")");

        boolean debug = Options.getBoolean(options, "debug");

        // Construct the graph of all method invocations, so we know what
        // method contains each invocation and what method(s) can be
        // targeted by that invocation.
        InvokeGraph invokeGraph =
            ClassHierarchyAnalysis.newInvokeGraph();

        // Temporary hack to deal with interfaces...  assume that methods of 
        // interfaces are automatically reachable.
        HashSet interfaceMethodSet= new HashSet();
        // Loop over all the classes...
        for (Iterator i = Scene.v().getApplicationClasses().iterator();
             i.hasNext();) {
            SootClass theClass = (SootClass)i.next();
            
            // Assume that any method that is part of an interface that this
            // object implements, is reachable.
            for (Iterator interfaces = theClass.getInterfaces().iterator();
                 interfaces.hasNext();) {
                SootClass theInterface = (SootClass)interfaces.next();
                if (theInterface.getName().equals(
                            "ptolemy.graph.InequalityTerm")) {
                    continue;
                }
                for (Iterator methods =
                         theInterface.getMethods().snapshotIterator();
                     methods.hasNext();) {
                    SootMethod method = (SootMethod)methods.next();
                    SootMethod aMethod = theClass.getMethod(
                            method.getSubSignature());
                    if(aMethod != null) {
                        System.out.println("Assuming interface method " +
                                aMethod + " is reachable");
                        interfaceMethodSet.add(aMethod);
                    }
                }
            }
        }

        // Construct the graph of methods that are directly reachable
        // from any method.
        MethodCallGraph methodCallGraph =
            (MethodCallGraph)invokeGraph.newMethodGraph(interfaceMethodSet);

        // Compute the transitive closure of the method call graph,
        // starting from main(), finalize(), etc..
        HashSet reachableMethodSet = new HashSet();
        reachableMethodSet.addAll(methodCallGraph.getReachableMethods());

        // Loop over all the classes...
        for (Iterator i = Scene.v().getApplicationClasses().iterator();
             i.hasNext();) {
            SootClass theClass = (SootClass)i.next();

            // Loop through all the methods...
            for (Iterator methods = theClass.getMethods().snapshotIterator();
                 methods.hasNext();) {
                SootMethod method = (SootMethod)methods.next();

                // And remove any methods that aren't reachable.
                if (!reachableMethodSet.contains(method)) {
                    if (debug) System.out.println("removing method " + method);
                    theClass.removeMethod(method);
                }
            }
        }
    }

    private static UnreachableMethodRemover instance =
    new UnreachableMethodRemover();
}













