/* List of different handlers.

Copyright (c) 2005 The Regents of the University of California.
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

package ptolemy.backtrack.ast.transform;

import java.util.LinkedList;
import java.util.List;

import ptolemy.backtrack.ast.transform.AssignmentHandler;
import ptolemy.backtrack.ast.transform.ClassHandler;

//////////////////////////////////////////////////////////////////////////
//// HandlerList
/**
    List of different handlers to be called back by {@link TypeAnalyzer}
    during the traversal of the AST.
   
    @author Thomas Feng
    @version $Id$
    @since Ptolemy II 5.1
    @Pt.ProposedRating Red (tfeng)
    @Pt.AcceptedRating Red (tfeng)
*/
public class HandlerList {
    
    /** Add an assignment handler to the list.
     * 
     *  @param handler The assignment handler.
     *  @see AssignmentHandler
     */
    public void addAssignmentHandler(AssignmentHandler handler) {
        _assignmentHandlers.add(handler);
    }
    
    /** Add a class declaration handler to the list.
     * 
     *  @param handler The class declaration handler.
     *  @see ClassHandler
     */
    public void addClassHandler(ClassHandler handler) {
        _classHandlers.add(handler);
    }
    
    /** Add a constructor handler to the list.
     * 
     *  @param handler The constructor handler.
     *  @see ConstructorHandler
     */
    public void addConstructorHandler(ConstructorHandler handler) {
        _constructorHandlers.add(handler);
    }
    
    /** Add a cross-analysis handler to the list.
     * 
     *  @param handler The cross-analysis handler.
     *  @see CrossAnalysisHandler
     */
    public void addCrossAnalysisHandler(CrossAnalysisHandler handler) {
        _crossAnalysisHandlers.add(handler);
    }
    
    /** Get the list of assignment handlers.
     * 
     *  @return The list of assignment handlers.
     */
    public List getAssignmentHandlers() {
        return _assignmentHandlers;
    }
    
    /** Get the list of class declaration handlers.
     * 
     *  @return The list of class declaration handlers.
     */
    public List getClassHandlers() {
        return _classHandlers;
    }
    
    /** Get the list of constructor handlers.
     * 
     *  @return The list of constructor handlers.
     */
    public List getConstructorHandlers() {
        return _constructorHandlers;
    }
    
    /** Get the list of cross-analysis handlers.
     * 
     *  @return The list of cross-analysis handlers.
     */
    public List getCrossAnalysisHandlers() {
        return _crossAnalysisHandlers;
    }
    
    /** Test if there is any assignment handler.
     * 
     *  @return <tt>true</tt> if there are assignment handlers.
     */
    public boolean hasAssignmentHandler() {
        return !_assignmentHandlers.isEmpty();
    }
    
    /** Test if there is any class declaration handler.
     * 
     *  @return <tt>true</tt> if there are class declaration handlers.
     */
    public boolean hasClassHandler() {
        return !_classHandlers.isEmpty();
    }
    
    /** Test is there is any constructor handler.
     * 
     *  @return <tt>true</tt> if there are constructor handlers.
     */
    public boolean hasConstructorHandler() {
        return !_constructorHandlers.isEmpty();
    }
    
    /** Test is there is any cross-analysis handler.
     * 
     *  @return <tt>true</tt> if there are cross-analysis handlers.
     */
    public boolean hasCrossAnalysisHandler() {
        return !_crossAnalysisHandlers.isEmpty();
    }
    
    /** Remove an assignment handler.
     * 
     *  @param handler The assignment handler to be removed.
     */
    public void removeAssignmentHandler(AssignmentHandler handler) {
        _assignmentHandlers.remove(handler);
    }

    /** Remove a class declaration handler.
     * 
     *  @param handler The class declaration handler to be removed.
     */
    public void removeClassHandler(ClassHandler handler) {
        _classHandlers.remove(handler);
    }
    
    /** Remove a constructor handler.
     * 
     *  @param handler The constructor handler to be removed.
     */
    public void removeConstructorHandler(ConstructorHandler handler) {
        _constructorHandlers.remove(handler);
    }

    /** Remove a cross-analysis handler.
     * 
     *  @param handler The cross-analysis handler to be removed.
     */
    public void removeCrossAnalysisHandler(CrossAnalysisHandler handler) {
        _crossAnalysisHandlers.remove(handler);
    }

    /** The list of assignment handlers.
     */
    private List _assignmentHandlers = new LinkedList();
    
    /** The list of class declaration handlers.
     */
    private List _classHandlers = new LinkedList();
    
    /** The list of constructor handlers.
     */
    private List _constructorHandlers = new LinkedList();
    
    /** The list of cross-analysis handlers.
     */
    private List _crossAnalysisHandlers = new LinkedList();
}
