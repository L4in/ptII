# Tests for the Port class
#
# @Author: Christopher Hylands
#
# @Version: $Id$
#
# @Copyright (c) 1997 The Regents of the University of California.
# All rights reserved.
# 
# Permission is hereby granted, without written agreement and without
# license or royalty fees, to use, copy, modify, and distribute this
# software and its documentation for any purpose, provided that the
# above copyright notice and the following two paragraphs appear in all
# copies of this software.
# 
# IN NO EVENT SHALL THE UNIVERSITY OF CALIFORNIA BE LIABLE TO ANY PARTY
# FOR DIRECT, INDIRECT, SPECIAL, INCIDENTAL, OR CONSEQUENTIAL DAMAGES
# ARISING OUT OF THE USE OF THIS SOFTWARE AND ITS DOCUMENTATION, EVEN IF
# THE UNIVERSITY OF CALIFORNIA HAS BEEN ADVISED OF THE POSSIBILITY OF
# SUCH DAMAGE.
# 
# THE UNIVERSITY OF CALIFORNIA SPECIFICALLY DISCLAIMS ANY WARRANTIES,
# INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
# MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE. THE SOFTWARE
# PROVIDED HEREUNDER IS ON AN "AS IS" BASIS, AND THE UNIVERSITY OF
# CALIFORNIA HAS NO OBLIGATION TO PROVIDE MAINTENANCE, SUPPORT, UPDATES,
# ENHANCEMENTS, OR MODIFICATIONS.
# 
# 						PT_COPYRIGHT_VERSION_2
# 						COPYRIGHTENDKEY
#######################################################################

# Tycho test bed, see $TYCHO/doc/coding/testing.html for more information.

# Load up the test definitions.
if {[string compare test [info procs test]] == 1} then { 
    source testDefs.tcl
} {}

# Uncomment this to get a full report, or set in your Tcl shell window.
# set VERBOSE 1

# If a file contains non-graphical tests, then it should be named .tcl
# If a file contains graphical tests, then it should be called .itcl
# 
# It would be nice if the tests would work in a vanilla itkwish binary.
# Check for necessary classes and adjust the auto_path accordingly.
#

######################################################################
#### _testGetRelationList 
# Given a Port, return a Tcl List containing its contents
#
proc _testGetRelationList {port} {
    set results {}
    if {$port == [java::null]} {
	return $results
    } 
    for {set enum [$port enumRelations]} \
	    {$enum != [java::null] && \
	    [$enum hasMoreElements] == 1} \
	    {} {
	set relation [$enum nextElement]
	lappend results [list [$relation getName]]
    }
    return $results
}

######################################################################
####
# 
test Port-1.1 {Get information about an instance of Port} {
    # If anything changes, we want to know about it so we can write tests.
    set n [java::new pt.kernel.Port]
    list [getJavaInfo $n]
} {{
  class:         pt.kernel.Port
  fields:        
  methods:       getClass hashCode {equals java.lang.Object} toString notify notifyAll {wait long} {wait long int} wait getName {setName java.lang.String} getParams {connectToRelation pt.kernel.Relation} disconnectAllRelations {disconnectRelation pt.kernel.Relation} enumRelations getEntity numRelations {setEntity pt.kernel.Entity}
  constructors:  pt.kernel.Port {pt.kernel.Port java.lang.String}
  properties:    class params name entity
  superclass:    pt.kernel.NamedObj
}}

######################################################################
####
# 
test Port-2.1 {Construct Ports} {
    set p1 [java::new pt.kernel.Port]
    set p2 [java::new pt.kernel.Port "My Port"]
    list [$p1 getName] [$p2 getName] \
	    [$p1 numRelations] [$p2 numRelations]
} {{} {My Port} 0 0}

######################################################################
####
# 
test Port-3.1 {Test connectToRelation with one port, one relation} {
    set p1 [java::new pt.kernel.Port "My Port"]
    set r1 [java::new pt.kernel.test.RelationTest "My Relation"]
    $p1 connectToRelation $r1
    list [_testGetRelationList $p1]
} {{{{My Relation}}}}

######################################################################
####
# 
test Port-3.1.1 {Test connectToRelation with one port, one relation twice} {
    set p1 [java::new pt.kernel.Port "My Port"]
    set r1 [java::new pt.kernel.test.RelationTest "My Relation"]
    $p1 connectToRelation $r1
    # This should throw an exception
    $p1 connectToRelation $r1
    list [_testGetRelationList $p1]
} {{{{My Relation}}}}

######################################################################
####
# 
test Port-3.1.2 {Test connectToRelation with one port to a null relation} {
    set p1 [java::new pt.kernel.Port "My Port"]
    catch {$p1 connectToRelation [java::null]} errmsg
    list $errmsg [_testGetRelationList $p1]
} {{pt.kernel.NullReferenceException: Null Relation passed to Port.connectToRelation()} {}}

######################################################################
####
# 
test Port-3.2 {Test connectToRelation with one port, two relations} {
    set p1 [java::new pt.kernel.Port "My Port"]
    set r1 [java::new pt.kernel.test.RelationTest "My Relation"]
    set r2 [java::new pt.kernel.test.RelationTest "My Other Relation"]
    $p1 connectToRelation $r1
    $p1 connectToRelation $r2
    list [_testGetRelationList $p1]
} {{{{My Other Relation}} {{My Relation}}}}

######################################################################
####
# 
test Port-3.3 {Test connectToRelation with two ports, one relation} {
    set p1 [java::new pt.kernel.Port "My Port"]
    set p2 [java::new pt.kernel.Port "My Other Port"]
    set r1 [java::new pt.kernel.test.RelationTest "My Relation"]
    $p1 connectToRelation $r1
    $p2 connectToRelation $r1
    list [_testGetRelationList $p1] [_testGetRelationList $p2]
} {{{{My Relation}}} {{{My Relation}}}}

######################################################################
####
# 
test Port-3.4 {Test connectToRelation with two ports, two relations} {
    set p1 [java::new pt.kernel.Port "My Port"]
    set p2 [java::new pt.kernel.Port "My Other Port"]
    set r1 [java::new pt.kernel.test.RelationTest "My Relation"]
    set r2 [java::new pt.kernel.test.RelationTest "My Other Relation"]
    $p1 connectToRelation $r1
    $p2 connectToRelation $r1
    $p1 connectToRelation $r2
    $p2 connectToRelation $r2
    list [_testGetRelationList $p1] \
	    [_testGetRelationList $p2] \
	    [$p1 numRelations] \
	    [$p2 numRelations]
} {{{{My Other Relation}} {{My Relation}}} {{{My Other Relation}} {{My Relation}}} 2 2}

######################################################################
####
# 
test Port-4.1 {Test disconnect allRelations} {
    set p1 [java::new pt.kernel.Port "port1"]
    set p2 [java::new pt.kernel.Port "port2"]
    set r1 [java::new pt.kernel.test.RelationTest "relation1"]
    set r2 [java::new pt.kernel.test.RelationTest "relation2"]
    $p1 connectToRelation $r1
    $p2 connectToRelation $r1
    $p1 connectToRelation $r2
    $p2 connectToRelation $r2
    $p1 disconnectAllRelations
    set result1 [list [_testGetRelationList $p1] [_testGetRelationList $p2]]
    # We call this twice to make sure that if there are no relations,
    # we don't cause an error.
    $p1 disconnectAllRelations
    set result2 [list [_testGetRelationList $p1] [_testGetRelationList $p2]]
    $p2 disconnectAllRelations 
    set result3 [list [_testGetRelationList $p1] [_testGetRelationList $p2]]
   list "$result1\n$result2\n$result3"
} {{{} {relation2 relation1}
{} {relation2 relation1}
{} {}}}

######################################################################
####
# 
test Port-5.1 {Test disconnectRelation} {
    set p1 [java::new pt.kernel.Port "port1"]
    set p2 [java::new pt.kernel.Port "port2"]
    set r1 [java::new pt.kernel.test.RelationTest "relation1"]
    set r2 [java::new pt.kernel.test.RelationTest "relation2"]
    $p1 connectToRelation $r1
    $p2 connectToRelation $r1
    $p1 connectToRelation $r2
    $p2 connectToRelation $r2
    $p1 disconnectRelation $r1
    set result1 [list [_testGetRelationList $p1] [_testGetRelationList $p2]]
    $p2 disconnectRelation $r2
    set result2 [list [_testGetRelationList $p1] [_testGetRelationList $p2]]
    $p2 disconnectRelation $r1
    set result3 [list [_testGetRelationList $p1] [_testGetRelationList $p2]]
    $p1 disconnectRelation $r2
    set result4 [list [_testGetRelationList $p1] [_testGetRelationList $p2]]
   list "$result1\n$result2\n$result3\n$result4"
} {{relation2 {relation2 relation1}
relation2 relation1
relation2 {}
{} {}}}

######################################################################
####
# 
test Port-6.1 {Test enumRelations.  Note that enumRelations is also tested in \
	our _testGetRelationList test proc} {
    set p1 [java::new pt.kernel.Port "port1"]
    set enum [$p1 enumRelations]
    catch {$enum nextElement} errmsg
    list $errmsg [$enum hasMoreElements]
} {{java.util.NoSuchElementException: List is empty.} 0}

######################################################################
####
# 
test Port-7.1 {Test getEntity on a Port that has no entity } {
    set p1 [java::new pt.kernel.Port "port1"]
    list [expr { [java::null] == [$p1 getEntity] } ]
} {1}

######################################################################
####
# 
test Port-7.2 {Test getEntity on a Port that has an entity } {
    set p1 [java::new pt.kernel.Port "port1"]
    set e1 [java::new pt.kernel.Entity "entity1"]
    $p1 setEntity $e1
    list [expr { $e1 == [$p1 getEntity] } ]
} {1}
