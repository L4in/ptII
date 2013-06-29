/* MappingConstraintSolverTest is a unit test for MappingConstraintSolver.

 Copyright (c) 2012-2013 The Regents of the University of California.
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
package ptolemy.domains.metroII.kernel.test.junit;

import java.util.ArrayList;
import static org.junit.Assert.assertEquals;

import ptolemy.domains.metroII.kernel.MappingConstraintSolver;
import ptolemy.domains.metroII.kernel.util.ProtoBuf.metroIIcomm.Event;
import ptolemy.domains.metroII.kernel.util.ProtoBuf.metroIIcomm.Event.Builder;

///////////////////////////////////////////////////////////////////
//// MappingConstraintSolverTest

/** 
 * MappingConstraintSolverTest is a unit test for MappingConstraintSolver. It tests 
 * <ol>
 * <li> whether the mapping constraints can be correctly added even with duplicate inputs</li>
 * <li> whether the constraints are correctly resolved when some event are in presence and some are not</li>
 * </ol>
 *
 * @author Liangpeng Guo
 * @version $Id$
 * @since Ptolemy II 9.1
 * @Pt.ProposedRating Red (glp)
 * @Pt.AcceptedRating Red (glp)
 *
*/
public class MappingConstraintSolverTest {

    /**
     * Constraints: A<>B, C<>D, E<>D, D<>F, A<>C, C<>D, D<>E, D<>C
     * Events: A, B, D, E, F, G
     * Expected results: A,B,D,E,G are notified, F is waiting
     */
    //@Test
    public void test() {
        MappingConstraintSolver solver = new MappingConstraintSolver();
        solver.addMapping("A", "B");
        solver.addMapping("C", "D");
        solver.addMapping("E", "D");
        solver.addMapping("D", "F");
        solver.addMapping("A", "C");
        // duplicate constraints
        solver.addMapping("C", "D");
        solver.addMapping("D", "E");
        solver.addMapping("D", "C");

        ArrayList<Event.Builder> eventList = new ArrayList<Event.Builder>();

        Event.Builder eventA = _createMetroIIEvent("A");
        Event.Builder eventB = _createMetroIIEvent("B");
        Event.Builder eventC = _createMetroIIEvent("C"); 
        Event.Builder eventD = _createMetroIIEvent("D");
        Event.Builder eventE = _createMetroIIEvent("E");
        Event.Builder eventF = _createMetroIIEvent("F");
        Event.Builder eventG = _createMetroIIEvent("G");

        eventList.add(eventA);
        eventList.add(eventB);
        eventList.add(eventF);
        eventList.add(eventE);
        eventList.add(eventD);
        eventList.add(eventG);

        solver.resolve(eventList);

        assertEquals(Event.Status.NOTIFIED, eventA.getStatus());
        assertEquals(Event.Status.NOTIFIED, eventB.getStatus());
        assertEquals(Event.Status.PROPOSED, eventC.getStatus());
        assertEquals(Event.Status.NOTIFIED, eventD.getStatus());
        assertEquals(Event.Status.NOTIFIED, eventE.getStatus());
        assertEquals(Event.Status.WAITING,  eventF.getStatus());
        assertEquals(Event.Status.NOTIFIED, eventG.getStatus());

        assertEquals(solver.numConstraints(), 5);

    }

    private Builder _createMetroIIEvent(String name) {
        Event.Builder builder = Event.newBuilder();
        builder.setName(name);
        builder.setStatus(Event.Status.PROPOSED);
        builder.setType(Event.Type.DEFAULT_NOTIFIED);
        return builder;
    }

}
