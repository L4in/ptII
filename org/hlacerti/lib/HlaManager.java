/* This attribute implements a HLA Manager to cooperate with a HLA/CERTI Federation.

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

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NoSuchElementException;
import java.util.logging.Level;
import java.util.logging.Logger;

import certi.rti.impl.CertiLogicalTime;
import certi.rti.impl.CertiLogicalTimeInterval;
import certi.rti.impl.CertiRtiAmbassador;
import hla.rti.ArrayIndexOutOfBounds;
import hla.rti.AttributeHandleSet;
import hla.rti.AttributeNotDefined;
import hla.rti.AttributeNotKnown;
import hla.rti.ConcurrentAccessAttempted;
import hla.rti.CouldNotDiscover;
import hla.rti.EnableTimeConstrainedPending;
import hla.rti.EnableTimeConstrainedWasNotPending;
import hla.rti.EnableTimeRegulationPending;
import hla.rti.EnableTimeRegulationWasNotPending;
import hla.rti.EventRetractionHandle;
import hla.rti.FederateAmbassador;
import hla.rti.FederateInternalError;
import hla.rti.FederateNotExecutionMember;
import hla.rti.FederateOwnsAttributes;
import hla.rti.FederatesCurrentlyJoined;
import hla.rti.FederationExecutionAlreadyExists;
import hla.rti.FederationExecutionDoesNotExist;
import hla.rti.FederationTimeAlreadyPassed;
import hla.rti.InvalidFederationTime;
import hla.rti.LogicalTime;
import hla.rti.LogicalTimeInterval;
import hla.rti.NameNotFound;
import hla.rti.ObjectAlreadyRegistered;
import hla.rti.ObjectClassNotDefined;
import hla.rti.ObjectClassNotKnown;
import hla.rti.ObjectClassNotPublished;
import hla.rti.ObjectNotKnown;
import hla.rti.OwnershipAcquisitionPending;
import hla.rti.RTIambassador;
import hla.rti.RTIexception;
import hla.rti.RTIinternalError;
import hla.rti.ReflectedAttributes;
import hla.rti.ResignAction;
import hla.rti.RestoreInProgress;
import hla.rti.SaveInProgress;
import hla.rti.SpecifiedSaveLabelDoesNotExist;
import hla.rti.SuppliedAttributes;
import hla.rti.TimeAdvanceAlreadyInProgress;
import hla.rti.TimeAdvanceWasNotInProgress;
import hla.rti.jlc.EncodingHelpers;
import hla.rti.jlc.NullFederateAmbassador;
import hla.rti.jlc.RtiFactory;
import hla.rti.jlc.RtiFactoryFactory;
import ptolemy.actor.AbstractInitializableAttribute;
import ptolemy.actor.Actor;
import ptolemy.actor.CompositeActor;
import ptolemy.actor.IOPort;
import ptolemy.actor.TimeRegulator;
import ptolemy.actor.TypedIOPort;
import ptolemy.actor.lib.CurrentMicrostep;
import ptolemy.actor.util.SuperdenseTime;
import ptolemy.actor.util.Time;
import ptolemy.actor.util.TimedEvent;
import ptolemy.data.BooleanToken;
import ptolemy.data.DoubleToken;
import ptolemy.data.StringToken;
import ptolemy.data.Token;
import ptolemy.data.expr.ChoiceParameter;
import ptolemy.data.expr.FileParameter;
import ptolemy.data.expr.Parameter;
import ptolemy.data.type.BaseType;
import ptolemy.data.type.Type;
import ptolemy.domains.de.kernel.DEDirector;
import ptolemy.kernel.ComponentEntity;
import ptolemy.kernel.ComponentRelation;
import ptolemy.kernel.CompositeEntity;
import ptolemy.kernel.util.Attribute;
import ptolemy.kernel.util.ChangeRequest;
import ptolemy.kernel.util.IllegalActionException;
import ptolemy.kernel.util.Instantiable;
import ptolemy.kernel.util.NameDuplicationException;
import ptolemy.kernel.util.NamedObj;
import ptolemy.kernel.util.Settable;
import ptolemy.kernel.util.Workspace;

///////////////////////////////////////////////////////////////////
//// HlaManager

/**
 * This class implements a HLA Manager which allows a Ptolemy model to
 * cooperate with a HLA/CERTI Federation. The main goal is to allow a Ptolemy
 * simulation as Federate of a Federation.
 *
 * <p>The High Level Architecture (HLA) [1][2] is a standard for distributed
 * discrete-event simulation. A complex simulation in HLA is called a HLA
 * Federation. A Federation is a collection of Federates (e.g. simpler simulators),
 * each performing a sequence of computations, interconnected by a Run
 * Time Infrastructure (RTI).</p>
 *
 * <p>CERTI is an Open-Source middleware RTI compliant with HLA [NRS09] which
 * manages every part of federation. It also ensures a real-time behavior of
 * a federation execution. CERTI is implemented in C++ and bindings are
 * provided as JCERTI for Java and PyHLA for Python. For more information see:
 * <a href="http://savannah.nongnu.org/projects/certi" target="_top">http://savannah.nongnu.org/projects/certi</a></p>
 *
 * <p>The {@link HlaManager} attribute handles the time synchronization between
 * Ptolemy model time and HLA logical time by implementing the {@link TimeRegulator}
 * interface. It also manages objects that implement interfaces provided by
 * JCERTI relatives to Federation, Declaration, Object and Time management
 * areas in HLA (each management areas provides a set of services).
 * </p>
 * To develop a HLA Federation it is required to specify a Federate Object
 * Model (FOM) which describes the architecture of the Federation (HLA version,
 * name of Federates which belong to, shared HLA attributes) and the interaction
 * between Federates and shared attributes. Data exchanged in a HLA Federation
 * are called HLA attributes and their interaction mechanism is based on the
 * publish/subscribe paradigm. The FOM is specified in a .fed file used by
 * the RTI (e.g. by the RTIG process when using CERTI). More information in [3].
 * <p> <a href="http://savannah.nongnu.org/projects/certi" target="_top">http://savannah.nongnu.org/projects/certi</a></p>
 * <p> To enable a Ptolemy model as a Federate, the {@link HlaManager} has to be
 * deployed and configured (by double-clicking on the attribute).
 * Parameters <i>federateName</i>, <i>federationName</i> have to match the
 * declaration in the FOM (.fed file). <i>fedFile</i> specifies the FOM file and
 * its path.</p>
 *
 * <p>Parameters <i>useNextEventRequest</i>, <i>UseTimeAdvanceRequest</i>,
 * <i>isTimeConstrained</i> and <i>isTimeRegulator</i> are
 * used to configure the HLA time management services of the Federate. A
 * Federate can only specify the use of the <i>nextEventRequest()
 * service</i> or the <i>timeAdvanceRequest()</i> service at a time.
 * <i>istimeConstrained</i> is used to specify time-constrained Federate and
 * <i>istimeRegulator</i> to specify time-regulator Federate. The combination of
 * both parameters is possible and is recommended.</p>
 *
 * <p>Parameters, <i>hlaStepTime</i> and <i>hlaLookAHead</i>
 * are used to specify Hla Timing attributes of a Federate.</p>
 *
 * <p>Parameters <i>requireSynchronization</i>, <i>synchronizationPointName</i>
 * and <i>isCreatorSyncPt</i> are used to configure HLA synchronization point.
 * This mechanism is usually used to synchronize the Federates, during their
 * initialization, to avoid that Federates that only consume some HLA
 * attributes finished their simulation before the other federates have started.
 * <i>isCreatorSyncPt</i> indicates if the Federate is the creator of the
 * synchronization. Only one Federate can create the named synchronization
 * point for the whole HLA Federation.</p>
 *
 * <p>{@link HlaPublisher} and {@link HlaSubscriber} actors are used to
 * respectively publish and subscribe to HLA attributes. The name of those
 * actors and their <i>classObjectHandle</i> parameter have to match the
 * identifier of the shared HLA attributes and their object class that they
 * belong to, specified in the FOM (.fed file).</p>
 *
 * <p>For a correct execution, the <i>CERTI_HOME</i> environment variable has to
 * be set. It could be set in the shell (by running one of the scripts provided
 * by CERTI) where Vergil is executed, or as a parameter of the Ptolemy model
 * or as a parameter of the {@link HlaManager}:</p>
 * <pre>
 * CERTI_HOME="/absolute/path/to/certi/"
 * </pre>
 *
 * <p>Otherwise, the current implementation is not able to find the CERTI
 * environment, the RTIG binary and to perform its execution. See also
 * the {@link CertiRtig} class.</p>
 *
 * <p>NOTE: For a correct behavior CERTI has to be compiled with the option
 * "CERTI_USE_NULL_PRIME_MESSAGE_PROTOCOL"
 * </p>
 *
 * <p><b>References</b>:</p>
 *
 * <p>[1] Dpt. of Defense (DoD) Specifications, "High Level Architecture Interface
 *     Specification, Version 1.3", DOD/DMSO HLA IF 1.3, Tech. Rep., Apr 1998.</p>
 * <p>[2] IEEE, "IEEE standard for modeling and simulation High Level Architecture
 *     (HLA)", IEEE Std 1516-2010, vol. 18, pp. 1-38, 2010.</p>
 * <p>[3] D. of Defense (DoD) Specifications, "High Level Architecture Object Model
 *     Template, Version 1.3", DOD/DMSO OMT 1.3, Tech. Rep., Feb 1998.</p>
 * <p>[4] E. Noulard, J.-Y. Rousselot, and P. Siron, "CERTI, an open source RTI,
 *     why and how ?", Spring Simulation Interoperability Workshop, pp. 23-27,
 *     Mar 2009.</p>
 * <p>[5] Y. Li, J. Cardoso, and P. Siron, "A distributed Simulation Environment for
 *     Cyber-Physical Systems", Sept 2015.</p>
 *
 *
 *  @author Gilles Lasnier, Contributors: Patricia Derler, Edward A. Lee, David Come, Yanxuan LI
 *  @version $Id$
 *  @since Ptolemy II 10.0
 *
 *  @Pt.ProposedRating Yellow (glasnier)
 *  @Pt.AcceptedRating Red (glasnier)
 */
public class HlaManager extends AbstractInitializableAttribute
implements TimeRegulator {

    /** Construct a HlaManager with a name and a container. The container
     *  argument must not be null, or a NullPointerException will be thrown.
     *  This actor will use the workspace of the container for synchronization
     *  and version counts. If the name argument is null, then the name is set
     *  to the empty string. Increment the version of the workspace.
     *  @param container Container of this attribute.
     *  @param name Name of this attribute.
     *  @exception IllegalActionException If the container is incompatible
     *  with this actor.
     *  @exception NameDuplicationException If the name coincides with
     *  an actor already in the container.
     */
    public HlaManager(CompositeEntity container, String name)
            throws IllegalActionException, NameDuplicationException {
        super(container, name);

        /*
        // XXX: FIXME: GiL: begin HLA Reporter code ?
        try {
            _testsFolder = _createFolder("testsResults");
        } catch (IOException ex) {
            throw new IllegalActionException(this, ex,
                    "Failed to create folder \"testResults/\".");
        }

        _file    = _createTextFile("data.txt");
        _csvFile = _createTextFile("data.csv");
        // XXX: FIXME: GiL: end HLA Reporter code ?
         */

        // XXX: FIXME: GiL: check usage ?
        //_noObjectDicovered = true;
        //_structuralInformation = new HashMap<Integer, StructuralInformation>();
        _registerObjectInstanceMap = new HashMap<String, Integer>();
        _discoverObjectInstanceMap = new HashMap<Integer, String>();


        _rtia = null;
        _federateAmbassador = null;

        _hlaAttributesToPublish = new HashMap<String, Object[]>();
        _hlaAttributesToSubscribeTo = new HashMap<String, Object[]>();
        _fromFederationEvents = new HashMap<String, LinkedList<TimedEvent>>();
        _objectIdToClassHandle = new HashMap<Integer, Integer>();

        _hlaTimeStep = null;
        _hlaLookAHead = null;

        // HLA Federation management parameters.
        federateName = new Parameter(this, "federateName");
        federateName.setDisplayName("Federate's name");
        federateName.setTypeEquals(BaseType.STRING);
        federateName.setExpression("\"HlaManager\"");
        attributeChanged(federateName);

        federationName = new Parameter(this, "federationName");
        federationName.setDisplayName("Federation's name");
        federationName.setTypeEquals(BaseType.STRING);
        federationName.setExpression("\"HLAFederation\"");
        attributeChanged(federationName);

        fedFile = new FileParameter(this, "fedFile");
        fedFile.setDisplayName("Federate Object Model (.fed) file path");
        new Parameter(fedFile, "allowFiles", BooleanToken.TRUE);
        new Parameter(fedFile, "allowDirectories", BooleanToken.FALSE);
        fedFile.setExpression("$CWD/HLAFederation.fed");

        // HLA Time management parameters.
        timeManagementService = new ChoiceParameter(this,
                "timeManagementService", ETimeManagementService.class);
        timeManagementService.setDisplayName("Time Management Service");
        attributeChanged(timeManagementService);

        hlaTimeStep = new Parameter(this, "hlaTimeStep");
        hlaTimeStep.setDisplayName("*** If TAR is used, time step (s)");
        hlaTimeStep.setExpression("0.0");
        hlaTimeStep.setTypeEquals(BaseType.DOUBLE);
        attributeChanged(hlaTimeStep);

        isTimeConstrained = new Parameter(this, "isTimeConstrained");
        isTimeConstrained.setTypeEquals(BaseType.BOOLEAN);
        isTimeConstrained.setExpression("true");
        isTimeConstrained.setDisplayName("isTimeConstrained ?");
        isTimeConstrained.setVisibility(Settable.NOT_EDITABLE);
        attributeChanged(isTimeConstrained);

        isTimeRegulator = new Parameter(this, "isTimeRegulator");
        isTimeRegulator.setTypeEquals(BaseType.BOOLEAN);
        isTimeRegulator.setExpression("true");
        isTimeRegulator.setDisplayName("isTimeRegulator ?");
        isTimeRegulator.setVisibility(Settable.NOT_EDITABLE);
        attributeChanged(isTimeRegulator);

        hlaLookAHead = new Parameter(this, "hlaLookAHead");
        hlaLookAHead.setDisplayName("lookahead (s)");
        hlaLookAHead.setExpression("0.1");
        hlaLookAHead.setTypeEquals(BaseType.DOUBLE);
        attributeChanged(hlaLookAHead);

        // HLA Synchronization parameters.
        requireSynchronization = new Parameter(this, "requireSynchronization");
        requireSynchronization.setTypeEquals(BaseType.BOOLEAN);
        requireSynchronization.setExpression("true");
        requireSynchronization.setDisplayName("Require synchronization ?");
        attributeChanged(requireSynchronization);

        synchronizationPointName = new Parameter(this,
                "synchronizationPointName");
        synchronizationPointName.setDisplayName("Synchronization point name");
        synchronizationPointName.setTypeEquals(BaseType.STRING);
        synchronizationPointName.setExpression("\"Simulating\"");
        attributeChanged(synchronizationPointName);

        isCreator = new Parameter(this, "isCreator");
        isCreator.setTypeEquals(BaseType.BOOLEAN);
        isCreator.setExpression("false");
        isCreator.setDisplayName("Is synchronization point register ?");
        attributeChanged(isCreator);

        hlaTimeUnit = new Parameter(this, "hlaTimeUnit");
        hlaTimeUnit.setTypeEquals(BaseType.DOUBLE);
        hlaTimeUnit.setExpression("1.0");
        hlaTimeUnit.setDisplayName("HLA time unit");
        attributeChanged(hlaTimeUnit);

    }

    ///////////////////////////////////////////////////////////////////
    ////                         public variables                  ////

    /** Name of the Ptolemy Federate. This parameter must contain an
     *  StringToken. */
    public Parameter federateName;

    /** Name of the federation. This parameter must contain an StringToken. */
    public Parameter federationName;

    /** Path and name of the Federate Object Model (FOM) file. This parameter
     *  must contain an StringToken. */
    public FileParameter fedFile;

    /**
     * Double value for representing how much is a unit of time in the simulation.
     * Has an impact on TAR/NER/RAV/UAV.
     */
    public Parameter hlaTimeUnit;

    /** Boolean value, 'true' if the Federate is the creator of the
     *  synchronization point 'false' if not. This parameter must contain
     *  an BooleanToken. */
    public Parameter isCreator;

    /** Boolean value, 'true' if the Federate is declared time constrained
     *  'false' if not. This parameter must contain an BooleanToken. */
    public Parameter isTimeConstrained;

    /** Boolean value, 'true' if the Federate is declared time regulator
     *  'false' if not. This parameter must contain an BooleanToken. */
    public Parameter isTimeRegulator;

    /** Value of the time step of the Federate. This parameter must contain
     *  an DoubleToken. */
    public Parameter hlaTimeStep;

    /** Value of the lookahead of the HLA ptII federate. This parameter
     *  must contain an DoubleToken. */
    public Parameter hlaLookAHead;

    /** Boolean value, 'true' if the Federate is synchronised with other
     *  Federates using a HLA synchronization point, 'false' if not. This
     *  parameter must contain an BooleanToken. */
    public Parameter requireSynchronization;

    /** Name of the synchronization point (if required). This parameter must
     *  contain an StringToken. */
    public Parameter synchronizationPointName;

    /** Choice of time advancement service (NER or exclusive TAR). */
    public ChoiceParameter timeManagementService;

    /** Enumeration which represents both time advancement services NER or TAR. */
    public enum ETimeManagementService {
        /** The Federate uses the Next Event Request (NER) calls to advance time. */
        NextEventRequest,

        /** The Federate uses the Time Advance Request (TAR) calls to advance in time. */
        TimeAdvancementRequest;

        /** Override the toString of enum class.
         * @return The string associated for each enum value.
         */
        @Override
        public String toString() {
            switch (this) {
            case NextEventRequest:
                return "Next Event Request (NER)";
            case TimeAdvancementRequest:
                return "Time Advancement Request (TAR)";
            default:
                throw new IllegalArgumentException();
            }
        }
    };

    ///////////////////////////////////////////////////////////////////
    ////                         public methods                    ////

    /** Checks constraints on the changed attribute (when it is required) and
     *  associates his value to its corresponding local variables.
     *  @param attribute The attribute that changed.
     *  @exception IllegalActionException If the attribute is empty or negative.
     */
    @Override
    public void attributeChanged(Attribute attribute)
            throws IllegalActionException {
        super.attributeChanged(attribute);

        if (attribute == federateName) {
            String value = ((StringToken) federateName.getToken())
                    .stringValue();
            if (value.compareTo("") == 0) {
                throw new IllegalActionException(this,
                        "Cannot have empty name !");
            }
            _federateName = value;
        } else if (attribute == federationName) {
            String value = ((StringToken) federationName.getToken())
                    .stringValue();
            if (value.compareTo("") == 0) {
                throw new IllegalActionException(this,
                        "Cannot have empty name !");
            }
            _federationName = value;
        } else if (attribute == timeManagementService) {
            if (timeManagementService
                    .getChosenValue() == ETimeManagementService.NextEventRequest) {
                _timeStepped = false;
                _eventBased = true;
            } else if (timeManagementService
                    .getChosenValue() == ETimeManagementService.TimeAdvancementRequest) {
                _timeStepped = true;
                _eventBased = false;
            }
        } else if (attribute == isTimeConstrained) {
            _isTimeConstrained = ((BooleanToken) isTimeConstrained.getToken())
                    .booleanValue();

        } else if (attribute == isTimeRegulator) {
            _isTimeRegulator = ((BooleanToken) isTimeRegulator.getToken())
                    .booleanValue();

        } else if (attribute == hlaTimeStep) {
            Double value = ((DoubleToken) hlaTimeStep.getToken()).doubleValue();
            if (value < 0) {
                throw new IllegalActionException(this,
                        "Cannot have negative value !");
            }
            _hlaTimeStep = value;
        } else if (attribute == hlaLookAHead) {
            Double value = ((DoubleToken) hlaLookAHead.getToken())
                    .doubleValue();
            if (value < 0) {
                throw new IllegalActionException(this,
                        "Cannot have negative value !");
            }
            _hlaLookAHead = value;
        } else if (attribute == requireSynchronization) {
            _requireSynchronization = ((BooleanToken) requireSynchronization
                    .getToken()).booleanValue();

        } else if (attribute == synchronizationPointName) {
            String value = ((StringToken) synchronizationPointName.getToken())
                    .stringValue();
            if (value.compareTo("") == 0) {
                throw new IllegalActionException(this,
                        "Cannot have empty name !");
            }
            _synchronizationPointName = value;
        } else if (attribute == isCreator) {
            _isCreator = ((BooleanToken) isCreator.getToken()).booleanValue();

        } else if (attribute == hlaTimeUnit) {
            _hlaTimeUnitValue = ((DoubleToken) hlaTimeUnit.getToken())
                    .doubleValue();
        } else {
            super.attributeChanged(attribute);
        }
    }

    /** Clone the actor into the specified workspace.
     *  @param workspace The workspace for the new object.
     *  @return A new actor.
     *  @exception CloneNotSupportedException If a derived class contains
     *  an attribute that cannot be cloned.
     */
    @Override
    public Object clone(Workspace workspace) throws CloneNotSupportedException {
        HlaManager newObject = (HlaManager) super.clone(workspace);

        /*
        // XXX: FIXME: GiL: begin HLA Reporter code ?
        newObject._csvFile = _createTextFile("data.csv");
        newObject._file = _createTextFile("data.txt");
        // XXX: FIXME: GiL: end HLA Reporter code ?
         */

        newObject._hlaAttributesToPublish = new HashMap<String, Object[]>();
        newObject._hlaAttributesToSubscribeTo = new HashMap<String, Object[]>();

        newObject._rtia = null;
        newObject._federateAmbassador = null;
        newObject._federateName = _federateName;
        newObject._federationName = _federationName;

        newObject._isTimeConstrained = _isTimeConstrained;
        newObject._isTimeRegulator = _isTimeRegulator;

        newObject._requireSynchronization = _requireSynchronization;
        newObject._synchronizationPointName = _synchronizationPointName;
        newObject._isCreator = _isCreator;
        newObject._eventBased = _eventBased;
        newObject._timeStepped = _timeStepped;

        newObject._fromFederationEvents = new HashMap<String, LinkedList<TimedEvent>>();
        newObject._objectIdToClassHandle = new HashMap<Integer, Integer>();
        //newObject._structuralInformation = new HashMap<Integer, StructuralInformation>();
        newObject._registerObjectInstanceMap = new HashMap<String, Integer>();
        newObject._discoverObjectInstanceMap = new HashMap<Integer, String>();

        newObject._hlaTimeUnitValue = _hlaTimeUnitValue;

        //newObject._noObjectDicovered = _noObjectDicovered;

        try {
            newObject._hlaTimeStep = ((DoubleToken) hlaTimeStep.getToken())
                    .doubleValue();
            newObject._hlaLookAHead = ((DoubleToken) hlaLookAHead.getToken())
                    .doubleValue();
        } catch (IllegalActionException ex) {
            // XXX: FIXME: GiL: check if working
            throw new CloneNotSupportedException("Failed to get a token.");
        }

        return newObject;
    }

    /** Initializes the {@link HlaManager} attribute. This method: calls the
     *  _populateHlaAttributeTables() to initialize HLA attributes to publish
     *  or subscribe to; instantiates and initializes the {@link RTIambassador}
     *  and {@link FederateAmbassador} which handle the communication
     *  Federate &lt;-&gt; RTIA &lt;-&gt; RTIG. RTIA and RTIG are both external communicant
     *  processes (see JCERTI); create the HLA/CERTI Federation (if not exists);
     *  allows the Federate to join the Federation; set the Federate time
     *  management policies (regulator and/or contrained); creates a
     *  synchronisation point (if required); and synchronizes the Federate with
     *  a synchronization point (if declared).
     *  @exception IllegalActionException If the container of the class is not
     *  an Actor or If a CERTI exception is raised and has to be displayed to
     *  the user.
     */
    @Override
    public void initialize() throws IllegalActionException {
        super.initialize();

        NamedObj container = getContainer();
        if (!(container instanceof Actor)) {
            throw new IllegalActionException(this,
                    "HlaManager has to be contained by an Actor");
        }

        // Get the corresponding director associated to the HlaManager attribute.
        _director = (DEDirector) ((CompositeActor) this.getContainer())
                .getDirector();

        // Initialize HLA attribute tables for publication/subscription.
        _populateHlaAttributeTables();

        // Get a link to the RTI.
        RtiFactory factory = null;
        try {
            factory = RtiFactoryFactory.getRtiFactory();
        } catch (RTIinternalError e) {
            throw new IllegalActionException(this, e, "RTIinternalError ");
        }

        try {
            _rtia = (CertiRtiAmbassador) factory.createRtiAmbassador();
        } catch (RTIinternalError e) {
            throw new IllegalActionException(this, e,
                    "RTIinternalError. "
                            + "If the error is \"Connection to RTIA failed\", "
                            + "then the problem is likely that the rtig "
                            + "binary could not be started by CertRtig. "
                            + "One way to debug this is to set the various "
                            + "environment variables by sourcing certi/share/scripts/myCERTI_env.sh, "
                            + "then invoking rtig on the .fed file "
                            + "then rerunning the model.");
        }

        // Create the Federation or raise a warning it the Federation already exits.
        try {
            _rtia.createFederationExecution(_federationName,
                    fedFile.asFile().toURI().toURL());
        } catch (FederationExecutionAlreadyExists e) {
            if (_debugging) {
                _debug("initialize() - WARNING: FederationExecutionAlreadyExists");
            }
        } catch (Exception e) {
            throw new IllegalActionException(this, e, e.getMessage());
        }

        _federateAmbassador = new PtolemyFederateAmbassadorInner();

        // Join the Federation.
        try {
            _rtia.joinFederationExecution(_federateName, _federationName,
                    _federateAmbassador);

            if (_debugging) {
                _debug("initialize() - federation joined");
            }
        } catch (RTIexception e) {
            throw new IllegalActionException(this, e, e.getMessage());
        }

        // Initialize the Federate Ambassador.
        try {
            _federateAmbassador.initialize(_rtia);
        } catch (RTIexception e) {
            throw new IllegalActionException(this, e, e.getMessage());
        }

        // Initialize HLA time management aspects for a Federate
        // (constrained by and/or participate to the time management).
        _initializeTimeAspects();

        System.out.println("HlaManager: initialize(): debug: before _doInitialSynchronization()");
        // Set initial synchronization point.
        _doInitialSynchronization();
        System.out.println("HlaManager: initialize(): debug: after _doInitialSynchronization()");

    }

    /** Return true if at least an object has been discovered during the time
     *   advance phase.
     */
    @Override
    public boolean noNewActors() {
        // XXX: FIXME: GiL: check usage ?
        boolean old = _noObjectDicovered;
        _noObjectDicovered = true;
        return old;
        //return true;
    }

    /** Launch the HLA/CERTI RTIG process as subprocess. The RTIG has to be
     *  launched before the initialization of a Federate.
     *  NOTE: if another HlaManager (e.g. Federate) has already launched a RTIG,
     *  the subprocess creates here is no longer required, then we destroy it.
     *  @exception IllegalActionException If the initialization of the
     *  CertiRtig or the execution of the RTIG as subprocess has failed.
     */
    @Override
    public void preinitialize() throws IllegalActionException {
        super.preinitialize();
        _startTime = System.nanoTime();

        // Try to launch the HLA/CERTI RTIG subprocess.
        _certiRtig = new CertiRtig(this, _debugging);
        _certiRtig.initialize(fedFile.asFile().getAbsolutePath());

        _certiRtig.exec();
        if (_debugging) {
            _debug("Federate " + this.getDisplayName() + "\npreinitialize() - "
                    + "Launch RTIG process");
        }

        if (_certiRtig.isAlreadyLaunched()) {
            _certiRtig.terminateProcess();
            _certiRtig = null;

            if (_debugging) {
                _debug("preinitialize() - "
                        + "Destroy RTIG process as another one is already "
                        + "launched");
            }
        }
    }

    /** Propose a time to advance to. This method is the one implementing the
     *  TimeRegulator interface and using the HLA/CERTI Time Management services
     *  (if required). Following HLA and CERTI recommendations, if the Time
     *  Management is required then we have the following behavior:
     *  Case 1: If lookahead = 0
     *   -a) if time-stepped Federate, then the timeAdvanceRequestAvailable()
     *       (TARA) service is used;
     *   -b) if event-based Federate, then the nextEventRequestAvailable()
     *       (NERA) service is used
     *  Case 2: If lookahead &gt; 0
     *   -c) if time-stepped Federate, then timeAdvanceRequest() (TAR) is used;
     *   -d) if event-based Federate, then the nextEventRequest() (NER) is used;
     *  Otherwise the proposedTime is returned.
     *  NOTE: For the Ptolemy II - HLA/CERTI cooperation the default (and correct)
     *  behavior is the case 1 and CERTI has to be compiled with the option
     *  "CERTI_USE_NULL_PRIME_MESSAGE_PROTOCOL".
     *  @param proposedTime The proposed time.
     *  @return The proposed time or a smaller time.
     *  @exception IllegalActionException If this attribute is not
     *  contained by an Actor.
     */
    @Override
    public Time proposeTime(Time proposedTime) throws IllegalActionException {
        //This variable is used to avoid rounding the Time more than once
        String proposedTimeInString = _printTimes(proposedTime);
        if (_debugging) {
            if (_eventBased) {
                _debug("starting proposeTime(t(lastFoundEvent)="
                        + proposedTimeInString + ") - current status - "
                        + "t_ptII = " + _printTimes(_director.getModelTime())
                        + "; t_hla = " + _federateAmbassador.logicalTimeHLA);
            } else {
                _debug("starting proposeTime(" + proposedTimeInString
                        + ")) - current status - " + "t_ptII = "
                        + _printTimes(_director.getModelTime()) + "; t_hla = "
                        + _federateAmbassador.logicalTimeHLA);
            }
        }
        Time currentTime = _getModelTime();

        if (proposedTime.compareTo(_stopTime) > 0) {
            if (_debugging) {
                _debug("    proposeTime(" + proposedTimeInString + ") -"
                        + " called but the proposedTime is bigger than the stopTime -> SKIP RTI -> returning stopTime");
            }
            return _stopTime;
        }
        // This test is used to avoid exception when the RTIG subprocess is
        // shutdown before the last call of this method.
        // GL: FIXME: see Ptolemy team why this is called again after STOPTIME ?
        if (_rtia == null) {
            if (_debugging) {
                _debug("    proposeTime(" + proposedTimeInString
                        + ") - called but the _rtia is null -> SKIP RTI ->  returning proposedTime");
            }
            return proposedTime;
        }

        // If the proposedTime is equal to current time
        // so it has no need to ask for the HLA service
        // then return the currentTime.

        if (currentTime.equals(proposedTime)) {
            // Even if we avoid the multiple calls of the HLA Time management
            // service for optimization, it could be possible to have events
            // from the Federation in the Federate's priority timestamp queue,
            // so we tick() to get these events (if they exist).
            if (_debugging) {
                _debug("    proposeTime(" + proposedTimeInString
                        + ") - called but the currentTime is equal to"
                        + " the proposedTime -> SKIP RTI -> returning currentTime");
            }
            try {
                _rtia.tick();

                if (_timeOfTheLastAdvanceRequest > 0) {
                    _numberOfTicks.set(_numberOfTAGs,
                            _numberOfTicks.get(_numberOfTAGs) + 1);
                } else {
                    _numberOfOtherTicks++;
                }

            } catch (ConcurrentAccessAttempted e) {
                throw new IllegalActionException(this, e,
                        "ConcurrentAccessAttempted ");
            } catch (RTIinternalError e) {
                throw new IllegalActionException(this, e, "RTIinternalError ");
            }

            return currentTime;
        }

        // If the HLA Time Management is required, ask to the HLA/CERTI
        // Federation (the RTI) the authorization to advance its time.
        if (_isTimeRegulator && _isTimeConstrained) {
            synchronized (this) {
                // Call the corresponding HLA Time Management service.
                try {
                    if (_eventBased) {
                        if (_debugging) {
                            _debug("    proposeTime(t(lastFoudEvent)=("
                                    + proposedTimeInString
                                    + ") - calling _eventsBasedTimeAdvance("
                                    + proposedTimeInString + ")");
                        }
                        return _eventsBasedTimeAdvance(proposedTime);
                    } else {
                        if (_debugging) {
                            _debug("    proposeTime(" + proposedTimeInString
                                    + ") - calling _timeSteppedBasedTimeAdvance("
                                    + proposedTimeInString + ")");
                        }
                        return _timeSteppedBasedTimeAdvance(proposedTime);
                    }
                } catch (InvalidFederationTime e) {
                    throw new IllegalActionException(this, e,
                            "InvalidFederationTime ");
                } catch (FederationTimeAlreadyPassed e) {
                    throw new IllegalActionException(this, e,
                            "FederationTimeAlreadyPassed ");
                } catch (TimeAdvanceAlreadyInProgress e) {
                    throw new IllegalActionException(this, e,
                            "TimeAdvanceAlreadyInProgress ");
                } catch (EnableTimeRegulationPending e) {
                    throw new IllegalActionException(this, e,
                            "EnableTimeRegulationPending ");
                } catch (EnableTimeConstrainedPending e) {
                    throw new IllegalActionException(this, e,
                            "EnableTimeConstrainedPending ");
                } catch (FederateNotExecutionMember e) {
                    throw new IllegalActionException(this, e,
                            "FederateNotExecutionMember ");
                } catch (SaveInProgress e) {
                    throw new IllegalActionException(this, e,
                            "SaveInProgress ");
                } catch (RestoreInProgress e) {
                    throw new IllegalActionException(this, e,
                            "RestoreInProgress ");
                } catch (RTIinternalError e) {
                    throw new IllegalActionException(this, e,
                            "RTIinternalError ");
                } catch (ConcurrentAccessAttempted e) {
                    throw new IllegalActionException(this, e,
                            "ConcurrentAccessAttempted ");
                } catch (NoSuchElementException e) {
                    // GL: FIXME: to investigate.
                    if (_debugging) {
                        _debug("    proposeTime(" + proposedTimeInString + ") -"
                                + " NoSuchElementException " + " for _rtia");
                    }
                    return proposedTime;
                } catch (SpecifiedSaveLabelDoesNotExist ex) {
                    Logger.getLogger(HlaManager.class.getName())
                    .log(Level.SEVERE, null, ex);
                }
            }
        }

        return null;
    }

    /** Update the HLA attribute <i>attributeName</i> with the containment of
     *  the token <i>in</i>. The updated attribute is sent to the HLA/CERTI
     *  Federation.
     *  @param hp The HLA publisher actor (HLA attribute) to update.
     *  @param in The updated value of the HLA attribute to update.
     *  @param senderName the name of the federate that sent the attribute.
     *  @exception IllegalActionException If a CERTI exception is raised then
     *  displayed it to the user.
     */
    public void updateHlaAttribute(HlaPublisher hp, Token in, String senderName)
            throws IllegalActionException {

        // XXX: FIXME: GiL: changer senderName => classInstancename
        // XXX: FIXME: GiL: why superdensetime here ?
        SuperdenseTime currentSuperdenseTime = _getModelSuperdenseTime();

        // XXX: FIXME: GiL: why here ?
        Time currentTime = new Time(_director,
                currentSuperdenseTime.timestamp().getDoubleValue());

        int microstep = currentSuperdenseTime.index();

        // The following operations build the different arguments required
        // to use the updateAttributeValues() (UAV) service provided by HLA/CERTI.

        // Retrieve information of the HLA attribute to publish.
        Object[] tObj = _hlaAttributesToPublish.get(hp.getFullName());

        // Encode the value to be sent to the CERTI.
        byte[] bAttributeValue = MessageProcessing.encodeHlaValue(hp, in);
        if (_debugging) {
            _debug("starting updateHlaAttribute() - current status t_ptII = "
                    + _printTimes(currentTime) + "; t_hla = "
                    + _federateAmbassador.logicalTimeHLA
                    + " - A HLA value from ptolemy has been"
                    + " encoded as CERTI MessageBuffer");
        }
        SuppliedAttributes suppAttributes = null;
        try {
            suppAttributes = RtiFactoryFactory.getRtiFactory()
                    .createSuppliedAttributes();
        } catch (RTIinternalError e) {
            throw new IllegalActionException(this, e, "RTIinternalError ");
        }
        suppAttributes.add(_getAttributeHandleFromTab(tObj), bAttributeValue);

        //byte[] tag = EncodingHelpers
        //        .encodeString(_getPortFromTab(tObj).getContainer().getFullName());

        HlaPublisher pub = (HlaPublisher)_getPortFromTab(tObj).getContainer();
        byte[] tag = EncodingHelpers
                .encodeString(pub.getClassInstanceName());

        // Create a representation of uav-event timestamp for CERTI.
        // HLA implies to send event in the future when using NER or TAR services with lookahead > 0.
        // Let us recall the lookahead rule: a federate promises that no events will be sent
        // before hlaCurrentTime + lookahead.
        // To avoid CERTI exception when calling UAV service
        // with condition: uav(tau) tau >= hlaCurrentTime + lookahead.
        Time uavTimeStamp = null;
        if (_eventBased) {
            // In the NER case, we have the equality currentTime = hlaCurrentTime.
            // So, we chose tau <- currentTime + lookahead and we respect the condition
            // above.
            uavTimeStamp = currentTime.add(_hlaLookAHead);
        } else {
            // In the TAR case, currentTime >= hlaCurrentTime.
            // So, we have two possible cases:
            // case 1:  currentTime >= hlaCurrentTime + lookAhead
            //          We will not break the lookahead rule, therefore tau <- currentTime.
            // case 2: hlaCurrentTime <= currentTime < hlaCurrentTime + lookAhead
            //         In order not to break the lookahead rule, we must delay the uav.
            //         tau <- hlaCurrentTime + lookahead
            CertiLogicalTime certiCurrentTime = (CertiLogicalTime) _federateAmbassador.logicalTimeHLA;
            Time hlaCurrentTime = _convertToPtolemyTime(certiCurrentTime);
            /*            if (currentTime.compareTo(hlaCurrentTime.add(_hlaTimeStep))==0) {
                hlaCurrentTime= hlaCurrentTime.add(_hlaTimeStep);
            }*/
            if (hlaCurrentTime.add(_hlaLookAHead).compareTo(currentTime) > 0) {
                uavTimeStamp = hlaCurrentTime.add(_hlaLookAHead);
            } else {
                uavTimeStamp = currentTime;
            }
        }
        CertiLogicalTime ct = _convertToCertiLogicalTime(uavTimeStamp);
        try {
            int objectInstanceId = _registerObjectInstanceMap.get(senderName);


            /*
            // XXX: FIXME: GiL: begin HLA Reporter code ?
            int indexOfAttribute = 0;
            String attributeName = _getPortFromTab(tObj).getContainer()
                    .getName();
            for (int i = 0; i < _numberOfAttributesToPublish; i++) {
                if (attributeName.equals(_nameOfTheAttributesToPublish[i])) {
                    indexOfAttribute = i;
                    break;
                }
            }

            String pUAVTimeStamp = ct.getTime() + ";";
            ;
            String preUAVTimeStamp = "(" + _printTimes(currentTime) + ","
                    + microstep + ");";
            _storeTimes(
                    "UAV " + _getPortFromTab(tObj).getContainer().getName());

            if (_numberOfUAVs > 0
                    && (_preUAVsTimes.length() - _preUAVsTimes
                            .lastIndexOf(preUAVTimeStamp)) == preUAVTimeStamp
                                    .length()
                    && (_pUAVsTimes.length() - _pUAVsTimes
                            .lastIndexOf(pUAVTimeStamp)) == pUAVTimeStamp
                                    .length()) {
                //System.out.println(_UAVsValues[indexOfAttribute].toString().substring(_UAVsValues.length-2, _UAVsValues.length));
                _UAVsValues[indexOfAttribute].replace(
                        _UAVsValues[indexOfAttribute].length() - 2,
                        _UAVsValues[indexOfAttribute].length(),
                        in.toString() + ";");
            } else {
                _preUAVsTimes.append(preUAVTimeStamp);
                _pUAVsTimes.append(pUAVTimeStamp);
                for (int i = 0; i < _numberOfAttributesToPublish; i++) {
                    if (i == indexOfAttribute) {
                        _UAVsValues[i].append(in.toString() + ";");
                    } else {
                        _UAVsValues[i].append("-;");
                    }
                }
            }
            // XXX: FIXME: GiL: end HLA Reporter code ?
             */
            System.out.println("updateHlaAttribute: UAV -"
                    + " id = " + objectInstanceId + " suppAttributes = " + suppAttributes + " tag = " + tag + " ct = " + ct);
            _rtia.updateAttributeValues(objectInstanceId, suppAttributes, tag, ct);

            // XXX: FIXME: GiL: begin HLA Reporter code ?
            //_numberOfUAVs++;
            // XXX: FIXME: GiL: end HLA Reporter code ?
            if (_debugging) {
                _debug("    updateHlaAttribute() - sending UAV( hla attribute = "
                        + _getPortFromTab(tObj).getContainer().getFullName()
                        + ",timestamp=" + ct.getTime() + ", value=" + in.toString()
                        + ")");
            }

        } catch (Exception e) {
            throw new IllegalActionException(this, e, e.getMessage());
        }
    }

    /** Manage the correct termination of the {@link HlaManager}. Call the
     *  HLA services to: unsubscribe to HLA attributes, unpublish HLA attributes,
     *  resign a Federation and destroy a Federation if the current Federate is
     *  the last participant.
     *  @exception IllegalActionException If the parent class throws it
     *  of if a CERTI exception is raised then displayed it to the user.
     */
    @Override
    public void wrapup() throws IllegalActionException {
        if (_debugging) {
            _debug("wrapup() - ... so termination");
        }

        super.wrapup();

        // XXX: FIXME: GiL: check usage
        //_structuralInformation.clear();
        _registerObjectInstanceMap.clear();
        _discoverObjectInstanceMap.clear();

        // XXX: FIXME: GiL: set a function HlaReporter.writeReports()
        if (_debugging) {
            _debug("Data" + "\n number of TARs: " + _numberOfTARs
                    + "\n number of NERs: " + _numberOfNERs + "\n number of TAGs: "
                    + _numberOfTAGs);
        }

        /*
        // XXX: FIXME: GiL: begin HLA Reporter code ?
        HlaReporter.calculateRuntime();
        HlaReporter.writeNumberOfHLACalls();
        HlaReporter.writeDelays();
        HlaReporter.writeUAVsInformations();
        HlaReporter.writeRAVsInformations();
        HlaReporter.writeTimes();
        // XXX: FIXME: GiL: end HLA Reporter code ?
         */

        // Unsubscribe to HLA attributes
        for (Object[] obj : _hlaAttributesToSubscribeTo.values()) {
            try {
                _rtia.unsubscribeObjectClass(_getClassHandleFromTab(obj));
            } catch (RTIexception e) {
                throw new IllegalActionException(this, e, e.getMessage());
            }
            if (_debugging) {
                _debug("wrapup() - unsubscribe "
                        + _getPortFromTab(obj).getContainer().getFullName()
                        + "(classHandle = " + _getClassHandleFromTab(obj)
                        + ")");
            }
        }

        // Unpublish HLA attributes.
        for (Object[] obj : _hlaAttributesToPublish.values()) {
            try {
                _rtia.unpublishObjectClass(_getClassHandleFromTab(obj));
            } catch (RTIexception e) {
                throw new IllegalActionException(this, e, e.getMessage());
            }
            if (_debugging) {
                _debug("wrapup() - unpublish "
                        + _getPortFromTab(obj).getContainer().getFullName()
                        + "(classHandle = " + _getClassHandleFromTab(obj)
                        + ")");
            }
        }

        // Resign HLA/CERTI Federation execution.
        try {
            _rtia.resignFederationExecution(
                    ResignAction.DELETE_OBJECTS_AND_RELEASE_ATTRIBUTES);
        } catch (RTIexception e) {
            throw new IllegalActionException(this, e, e.getMessage());
        }
        if (_debugging) {
            _debug("wrapup() - Resign Federation execution");
        }

        boolean canDestroyRtig = false;
        while (!canDestroyRtig) {

            // Destroy federation execution - nofail.
            try {
                _rtia.destroyFederationExecution(_federationName);
            } catch (FederatesCurrentlyJoined e) {
                if (_debugging) {
                    _debug("wrapup() - WARNING: FederatesCurrentlyJoined");
                }
            } catch (FederationExecutionDoesNotExist e) {
                // GL: FIXME: This should be an IllegalActionExeception
                if (_debugging) {
                    _debug("wrapup() - WARNING: FederationExecutionDoesNotExist");
                }
                canDestroyRtig = true;
            } catch (RTIinternalError e) {
                throw new IllegalActionException(this, e, "RTIinternalError ");
            } catch (ConcurrentAccessAttempted e) {
                throw new IllegalActionException(this, e,
                        "ConcurrentAccessAttempted ");
            }
            if (_debugging) {
                _debug("wrapup() - "
                        + "Destroy Federation execution - no fail");
            }

            canDestroyRtig = true;
        }

        // Terminate RTIG subprocess.
        if (_certiRtig != null) {
            _certiRtig.terminateProcess();

            if (_debugging) {
                _debug("wrapup() - " + "Destroy RTIG process (if authorized)");
            }
        }

        // Clean HLA attribute tables.
        _hlaAttributesToPublish.clear();
        _hlaAttributesToSubscribeTo.clear();
        _fromFederationEvents.clear();
        _objectIdToClassHandle.clear();

        if (_debugging) {
            _debug("-----------------------");
        }
    }


    ///////////////////////////////////////////////////////////////////
    ////                         protected variables               ////

    /** Table of HLA attributes (and their HLA information) that are published
     *  by the current {@link HlaManager} to the HLA/CERTI Federation. This
     *  table is indexed by the {@link HlaPublisher} actors present in the model.
     */
    protected HashMap<String, Object[]> _hlaAttributesToPublish;

    /** Table of HLA attributes (and their HLA information) that the current
     *  {@link HlaManager} is subscribed to. This table is indexed by the
     *  {@link HlaSubscriber} actors present in the model.
     */
    protected HashMap<String, Object[]> _hlaAttributesToSubscribeTo;

    /** List of events received from the HLA/CERTI Federation and indexed by the
     *  {@link HlaSubscriber} actors present in the model.
     */
    protected HashMap<String, LinkedList<TimedEvent>> _fromFederationEvents;

    /** Table of object class handles associate to object ids received by
     *  discoverObjectInstance and reflectAttributesValues services (e.g. from
     *  the RTI).
     */
    protected HashMap<Integer, Integer> _objectIdToClassHandle;

    ///////////////////////////////////////////////////////////////////
    ////                         private methods                   ////

    /**
     * make a conversion from ptolemy time to certi logical time
     * @param pt ptolemy time
     * @return certi logical time
     */
    private CertiLogicalTime _convertToCertiLogicalTime(Time pt) {

        return new CertiLogicalTime(pt.getDoubleValue() * _hlaTimeUnitValue);
    }

    /**
     * Make a conversion from certi logical time to ptolemy time
     * @param ct certi logical time
     * @return ptolemy time
     * @exception IllegalActionException
     */
    private Time _convertToPtolemyTime(CertiLogicalTime ct)
            throws IllegalActionException {
        return new Time(_director, ct.getTime() / _hlaTimeUnitValue);
    }

    /**
     * RTI service for event-based federate (NER or NERA)
     * is used for proposing a time to advance to.
     * @param proposedTime time stamp of lastFoundEvent
     * @return
     */
    private Time _eventsBasedTimeAdvance(Time proposedTime)
            throws IllegalActionException, InvalidFederationTime,
            FederationTimeAlreadyPassed, TimeAdvanceAlreadyInProgress,
            FederateNotExecutionMember, SaveInProgress,
            EnableTimeRegulationPending, EnableTimeConstrainedPending,
            RestoreInProgress, RTIinternalError, ConcurrentAccessAttempted,
            SpecifiedSaveLabelDoesNotExist {

        CertiLogicalTime certiProposedTime = _convertToCertiLogicalTime(
                proposedTime);

        String proposedTimeInString = _printTimes(proposedTime);
        _storeTimes("NER(" + proposedTimeInString + ")");

        if (_hlaLookAHead > 0) {
            // Event-based + lookahead > 0 => NER.
            if (_debugging) {
                _debug("        proposeTime(t(lastFoundEvent)="
                        + proposedTimeInString + ") - _eventsBasedTimeAdvance("
                        + proposedTimeInString + ")"
                        + " - calling CERTI NER(proposedTime*hlaTimeUnitValue = "
                        + certiProposedTime.getTime() + ")");
            }
            _rtia.nextEventRequest(certiProposedTime);

            _numberOfNERs++;
            _timeOfTheLastAdvanceRequest = System.nanoTime();

        } else {
            // Event-based + lookahead = 0 => NERA + NER.
            // Start the time advancement loop with one NERA call.
            if (_debugging) {
                _debug("        proposeTime(t(lastFoundEvent)="
                        + proposedTimeInString + ") - _eventsBasedTimeAdvance("
                        + proposedTimeInString + ")- "
                        + "call CERTI NERA(proposedTime*hlaTimeUnitValue = "
                        + certiProposedTime.getTime() + ")");
            }
            _rtia.nextEventRequestAvailable(certiProposedTime);
            _numberOfNERs++;
            _timeOfTheLastAdvanceRequest = System.nanoTime();

            // Wait the time grant from the HLA/CERTI Federation (from the RTI).
            _federateAmbassador.timeAdvanceGrant = false;
            while (!(_federateAmbassador.timeAdvanceGrant)) {
                if (_debugging) {
                    _debug("        proposeTime(t(lastFoundEvent)="
                            + proposedTimeInString
                            + ") - _eventsBasedTimeAdvance("
                            + proposedTimeInString + ") - "
                            + " waiting for CERTI TAG("
                            + certiProposedTime.getTime()
                            + ") by calling tick2()");
                }
                _rtia.tick2();

                // XXX: FIXME: GiL: begin HLA Reporter code ?
                _numberOfTicks2++;
                _numberOfTicks.set(_numberOfTAGs,
                        _numberOfTicks.get(_numberOfTAGs) + 1);
                // XXX: FIXME: GiL: end HLA Reporter code ?


            }

            // End the loop with one NER call.
            if (_debugging) {
                _debug("        proposeTime(t(lastFoundEvent)="
                        + proposedTimeInString + ") - _eventsBasedTimeAdvance("
                        + proposedTimeInString + ")"
                        + " - calling CERTI NER(proposedTime*hlaTimeUnitValue = "
                        + certiProposedTime.getTime() + ")");
            }
            _rtia.nextEventRequest(certiProposedTime);

            // XXX: FIXME: GiL: begin HLA Reporter code ?
            _numberOfNERs++;
            _timeOfTheLastAdvanceRequest = System.nanoTime();
            // XXX: FIXME: GiL: end HLA Reporter code ?

        }

        // XXX: FIXME: GiL: begin HLA Reporter code ?
        int cntTick = 0;
        // XXX: FIXME: GiL: end HLA Reporter code ?

        // Wait the time grant from the HLA/CERTI Federation (from the RTI).
        _federateAmbassador.timeAdvanceGrant = false;
        while (!(_federateAmbassador.timeAdvanceGrant)) {
            if (_debugging) {
                _debug("        proposeTime(t(lastFoundEvent)="
                        + proposedTimeInString + ") - _eventsBasedTimeAdvance("
                        + proposedTimeInString + ") - "
                        + " waiting for CERTI TAG("
                        + certiProposedTime.getTime() + ") by calling tick2()");
            }
            _rtia.tick2();

            // XXX: FIXME: GiL: begin HLA Reporter code ?
            _numberOfTicks2++;
            cntTick++;
            // XXX: FIXME: GiL: end HLA Reporter code ?

        }


        _numberOfTicks.set(_numberOfTAGs,
                _numberOfTicks.get(_numberOfTAGs) + cntTick);

        // If we get any rav-event
        if (_debugging) {
            _debug("        proposeTime(" + proposedTimeInString
                    + ") - _eventBasedTimeAdvance(" + proposedTimeInString
                    + ")  - Checking if we've received any RAV events.");
        }

        // XXX: FIXME: GiL: reviewing the code, cntTick is always != 1, so why ?
        if (cntTick != 1) {
            System.out.println("_eventsBasedTimeAdvance: call _putReflectedAttributesOnHlaSubscribers()");
            // Store reflected attributes RAV as events on HLASubscriber actors.
            _putReflectedAttributesOnHlaSubscribers();

            // At this step we are sure that the HLA logical time of the
            // Federate has been updated (by the reception of the TAG callback
            // (timeAdvanceGrant()) and its value is the proposedTime or
            // less, so we have a breakpoint time.
            try {
                CertiLogicalTime hlaTimeGranted = (CertiLogicalTime) _federateAmbassador.logicalTimeHLA;
                Time breakpoint = _convertToPtolemyTime(hlaTimeGranted);
                if (_debugging) {
                    _debug("        proposeTime(t(lastFoundEvent)="
                            + proposedTimeInString
                            + ") - _eventsBasedTimeAdvance("
                            + proposedTimeInString
                            + ") - RAV event detected -> TAG(" + hlaTimeGranted
                            + ") received, model moves to the breakpoint time = "
                            + _printTimes(breakpoint));
                }
                // So we'd like to propose the breakpoint time instead.
                proposedTime = breakpoint;
            } catch (IllegalActionException e) {
                throw new IllegalActionException(this, e,
                        "The breakpoint time is not a valid Ptolemy time");
            }
        }

        return proposedTime;
    }

    /**
     * RTI service for time-stepped federate (TAR or TARA)
     * is used for proposing a time to advance to.
     * @param proposedTime time stamp of lastFoundEvent
     * @return a valid time to advance to
     */
    private Time _timeSteppedBasedTimeAdvance(Time proposedTime)
            throws IllegalActionException, InvalidFederationTime,
            FederationTimeAlreadyPassed, TimeAdvanceAlreadyInProgress,
            FederateNotExecutionMember, SaveInProgress,
            EnableTimeRegulationPending, EnableTimeConstrainedPending,
            RestoreInProgress, RTIinternalError, ConcurrentAccessAttempted {

        String proposedTimeInString = _printTimes(proposedTime);
        if (_hlaTimeStep > 0) {

            // Calculate the next point in time for making a TAR(hlaNextPointInTime)
            // or TARA(hlaNextPointInTime)
            Time hlaNextPointInTime = _getHlaNextPointInTime();
            CertiLogicalTime certiNextPointInTime = _convertToCertiLogicalTime(
                    hlaNextPointInTime);

            // There are two types of events in a federate model:
            // - rav et uav events via RTI
            // - all events in ptolemy eventQueue

            if (_hlaLookAHead > 0) {
                // Time-stepped + lookahead > 0 => TAR.
                // LastFoundEvent is the earlist event in calendar queue,
                // We'd like to see if it is still the earlist one after
                // a registration of a rav-event.

                // There are two cases:
                // case 1:
                // WHILE time stamp of LastFoundEvent(proposedTime) > hlaNextPointInTime,
                // THEN TAR(hlaNextPointInTime) service is called.
                //      Tick() doesn't stop Until it receives a TAG(hlaNextPointInTime),
                //      At the end of tick,
                //      IF we get any RAV(t)
                //             all rav should be put in the queue
                //             with a time stamp delay to hlaNextPointInTime;
                //             IF hlaNextPointInTime < proposedTime which means LastFoundEvent
                //             is no more our earliest event;
                //             THEN proposedTime should be replaced by hlaNextPointInTime.
                //             END IF
                //      END IF
                // END WHILE
                // case 2: LastFoundEvent is directly or indirectly(via case 1)
                //         in (hlacurrentTime, hlaNextPointInTime)
                //         we don't want to advance the certi time.
                //         so return proposedTime, this event is valid to execute.

                // case 1:

                // Before the loop, is proposeTime < hlaNextPointInTime ?
                //if (_debugging) {
                //	_debug("Comparing proposeTime " + proposedTimeInString
                //	       + "and hlaNextPointInTime "
                //	       + hlaNextPointInTime.getDoubleValue());
                //}

                while (proposedTime.compareTo(hlaNextPointInTime) > 0) {
                    if (_debugging) {
                        _debug("        proposeTime(" + proposedTimeInString
                                + ") - _timeSteppedBasedTimeAdvance("
                                + proposedTimeInString
                                + ") - the lastFoundEvent's "
                                + "timestamp >= hlaNextPointInTime- calling CERTI TAR(proposedTime*hlaTimeUnitValue = "
                                + certiNextPointInTime.getTime() + ")");
                    }
                    _rtia.timeAdvanceRequest(certiNextPointInTime);
                    _numberOfTARs++;
                    _timeOfTheLastAdvanceRequest = System.nanoTime();

                    // Wait the time grant from the HLA/CERTI Federation (from the RTI).
                    _federateAmbassador.timeAdvanceGrant = false;
                    int cntTick = 0;
                    while (!(_federateAmbassador.timeAdvanceGrant)) {
                        if (_debugging) {
                            _debug("        proposeTime(" + proposedTimeInString
                                    + ") - _timeSteppedBasedTimeAdvance("
                                    + proposedTimeInString + ") - "
                                    + " waiting for CERTI TAG("
                                    + certiNextPointInTime.getTime()
                                    + ") by calling tick2()");
                        }

                        try {
                            _rtia.tick2();

                            _numberOfTicks2++;
                            cntTick++;

                        } catch (RTIexception e) {
                            if (_debugging) {
                                _debug("        proposeTime("
                                        + proposedTimeInString
                                        + ") - _timeSteppedBasedTimeAdvance("
                                        + proposedTimeInString
                                        + ") - Failed to make "
                                        + "tick2() - there is a problem with the RTIambassador");
                            }
                            throw new IllegalActionException(this, e,
                                    e.getMessage());
                        }
                    }

                    _numberOfTicks.set(_numberOfTAGs, _numberOfTicks.get(_numberOfTAGs) + cntTick);

                    // If we get any rav-event
                    if (_debugging) {
                        _debug("        proposeTime(" + proposedTimeInString
                                + ") - _timeSteppedBasedTimeAdvance("
                                + proposedTimeInString
                                + ")  - Checking if we've received any RAV events.");
                    }
                    if (cntTick != 1) {
                        System.out.println("_timeSteppedBasedTimeAdvance: call _putReflectedAttributesOnHlaSubscribers()");
                        // Store reflected attributes as events on HLASubscriber actors.
                        _putReflectedAttributesOnHlaSubscribers();
                        // If the new rav-event will arrive before our lastFoundEvent,
                        if (hlaNextPointInTime.compareTo(proposedTime) < 0)
                            proposedTime = hlaNextPointInTime;
                    }
                    // Advance the hlaNextPointInTime
                    hlaNextPointInTime = _getHlaNextPointInTime();
                    certiNextPointInTime = _convertToCertiLogicalTime(
                            hlaNextPointInTime);
                }

                // case 2:
                if (_debugging) {
                    _debug("        proposeTime(" + proposedTimeInString
                            + ") - _timeSteppedBasedTimeAdvance("
                            + proposedTimeInString + ") - the lastFoundEvent "
                            + "(timeStamp = " + proposedTimeInString
                            + ") has its timeStamp < nextPointInTime - no TAR will be made and the event will be processed.");
                }
                return proposedTime;

            } else {
                // Time-stepped + lookahead = 0 => TARA + TAR.
                // Start the loop with one TARA call.
                if (_debugging) {
                    _debug("        proposeTime(" + proposedTimeInString
                            + ") - _timeSteppedBasedTimeAdvance("
                            + proposedTimeInString + ") -"
                            + " waiting for CERTI TAG("
                            + certiNextPointInTime.getTime()
                            + ") by calling tick2()");
                }
                _rtia.timeAdvanceRequest(certiNextPointInTime);

                _numberOfTARs++;
                _timeOfTheLastAdvanceRequest = System.nanoTime();

                try {
                    _rtia.tick2();

                    _numberOfTicks2++;
                    _numberOfTicks.set(_numberOfTAGs,
                            _numberOfTicks.get(_numberOfTAGs) + 1);
                } catch (SpecifiedSaveLabelDoesNotExist e) {
                    throw new IllegalActionException(this, e,
                            "SpecifiedSaveLabelDoesNotExist ");
                } catch (ConcurrentAccessAttempted e) {
                    throw new IllegalActionException(this, e,
                            "ConcurrentAccessAttempted ");
                } catch (RTIinternalError e) {
                    throw new IllegalActionException(this, e,
                            "RTIinternalError ");
                }

                // End the loop with one TAR call.
                if (_debugging) {
                    _debug("        proposeTime(" + proposedTimeInString
                            + ") - _timeSteppedBasedTimeAdvance("
                            + proposedTimeInString + ") - "
                            + " calling CERTI TAR(proposedTime*hlaTimeUnitValue = "
                            + certiNextPointInTime.getTime() + ")");
                }
                _rtia.timeAdvanceRequest(certiNextPointInTime);

                _numberOfTARs++;
                _timeOfTheLastAdvanceRequest = System.nanoTime();
            }
        }

        if (_debugging) {
            _debug("        proposeTime(" + proposedTimeInString
                    + ") - _timeSteppedBasedTimeAdvance(" + proposedTimeInString
                    + ") - TAR not successful");
        }
        return null;

    }

    /** The method {@link #_populatedHlaValueTables()} populates the tables
     *  containing information of HLA attributes required to publish and to
     *  subscribe value attributes in a HLA Federation.
     *  @exception IllegalActionException If a HLA attribute is declared twice.
     */
    private void _populateHlaAttributeTables() throws IllegalActionException {

        //CompositeActor ca = (CompositeActor) this.getContainer();
        CompositeEntity ce = (CompositeEntity) getContainer();

        // HlaPublishers.
        _hlaAttributesToPublish.clear();
        List<HlaPublisher> _hlaPublishers = ce.entityList(HlaPublisher.class);
        for (HlaPublisher hp : _hlaPublishers) {
            if (_hlaAttributesToPublish.get(hp.getFullName()) != null) {
                throw new IllegalActionException(this,
                        "A HLA attribute with the same name is already "
                                + "registered for publication");
            }
            // Only one input port is allowed per HlaPublisher actor.
            TypedIOPort tIOPort = hp.inputPortList().get(0);

            _hlaAttributesToPublish.put(
                    hp.getFullName(),
                    // XXX: FIXME: simply replace by a HlaPublisher instance ?
                    // tObj[] object as the following structure:

                    // tObj[0] => input port which receives the token to transform
                    //            as an updated value of a HLA attribute,
                    // tObj[1] => type of the port (e.g. of the attribute),
                    // tObj[2] => object class name of the attribute,
                    // tObj[3] => instance class name

                    // tObj[4] => ID of the object class to handle,
                    // tObj[5] => ID of the attribute to handle

                    // tObj[0 .. 3] are extracted from the Ptolemy model.
                    // tObj[3 .. 5] are provided by the RTI (CERTI).
                    new Object[] {
                            tIOPort,                  // XXX: FIXME: GiL need more check ?
                            tIOPort.getType(),        // XXX: FIXME: GiL need more check ?
                            hp.getClassObjectName(),  // XXX: FIXME: GiL need more check ?
                            hp.getClassInstanceName() // XXX: FIXME: GiL need more check ?
                    });
        }

        System.out.println("_populateHlaAttributeTables: _hlaAttributesToPublish = " + _hlaAttributesToPublish.toString());

        // HlaSubscribers.
        _hlaAttributesToSubscribeTo.clear();
        List<HlaSubscriber> _hlaSubscribers = getHlaSubscribers(ce);

        for (HlaSubscriber hs : _hlaSubscribers) {
            //System.out.println("ciele_debug: " +  hs.getName() + " " + hs.getFullName());
            if (_hlaAttributesToSubscribeTo.get(hs.getFullName()) != null) {
                throw new IllegalActionException(this,
                        "A HLA attribute with the same name is already "
                                + "registered for subscription");
            }
            // Only one output port is allowed per HlaSubscriber actor.
            TypedIOPort tiop = hs.outputPortList().get(0);

            _hlaAttributesToSubscribeTo.put(
                    hs.getFullName(),

                    // XXX: FIXME: simply replace by a HlaSubscriber instance ?
                    // tObj[] object as the following structure:

                    // tObj[0] => input port which receives the token to transform
                    //            as an updated value of a HLA attribute,
                    // tObj[1] => type of the port (e.g. of the attribute),
                    // tObj[2] => object class name of the attribute,
                    // tObj[3] => instance class name

                    // tObj[4] => ID of the object class to handle,
                    // tObj[5] => ID of the attribute to handle

                    // tObj[0 .. 3] are extracted from the Ptolemy model.
                    // tObj[3 .. 5] are provided by the RTI (CERTI).
                    new Object[] {
                            tiop,
                            tiop.getType(),
                            hs.getClassObjectName(),
                            hs.getClassInstanceName()
                    });

            // The events list to store updated values of HLA attribute,
            // (received by callbacks) from the RTI, is indexed by the HLA
            // Subscriber actors present in the model.
            _fromFederationEvents.put(hs.getFullName(), new LinkedList<TimedEvent>());
        }

        System.out.println("_populateHlaAttributeTables: _hlaAttributesToSubscribeTo = " + _hlaAttributesToSubscribeTo.toString());

    }

    /**
     * Get all HlaSubcribers across model.
     */
    private List<HlaSubscriber> getHlaSubscribers(CompositeEntity ce) {
        // The list of HLA subscribers to return.
        LinkedList<HlaSubscriber> hlaSubcribers = new LinkedList<HlaSubscriber>();

        // List all classes from top level model.
        List<CompositeEntity> entities = ce.entityList();
        for (ComponentEntity classElt : entities) {
            System.out.println("getHlaSubscribers: classElt in entities ="
                    + " " + classElt.getClassName()
                    + " " + classElt.getDisplayName() 
                    + " " + classElt.toString() 
                    + " container = " + classElt.getContainer().toString());

            // XXX: FIXME: GiL: to optimize with Ptolemy's 
            if (classElt instanceof HlaSubscriber) {
                hlaSubcribers.add((HlaSubscriber) classElt);
            }
            else if (classElt instanceof ptolemy.actor.TypedCompositeActor) {
                hlaSubcribers.addAll(getHlaSubscribers((CompositeEntity) classElt));
            }
        }

        return hlaSubcribers;
    }

    /** This method is called when a time advancement phase is performed. Every
     *  updated HLA attributes received by callbacks (from the RTI) during the
     *  time advancement phase is saved as {@link TimedEvent} and stored in a
     *  queue. Then, every {@link TimedEvent} is moved from this queue to the
     *  output port of their corresponding {@link HLASubscriber} actors
     *  @exception IllegalActionException If the parent class throws it.
     */
    private void _putReflectedAttributesOnHlaSubscribers()
            throws IllegalActionException {
        // Reflected HLA attributes, e.g. updated values of HLA attributes
        // received by callbacks (from the RTI) from the whole HLA/CERTI
        // Federation, are store in the _subscribedValues queue (see
        // reflectAttributeValues() in PtolemyFederateAmbassadorInner class).

        if (_debugging) {
            _debug("starting _putReflectedAttributesOnHlaSubscribers() - current status - "
                    + "t_ptII = " + _printTimes(_director.getModelTime())
                    + "; t_hla = " + _federateAmbassador.logicalTimeHLA);
        }

        Iterator<Entry<String, LinkedList<TimedEvent>>> it = _fromFederationEvents
                .entrySet().iterator();

        while (it.hasNext()) {
            Map.Entry<String, LinkedList<TimedEvent>> elt = it.next();

            System.out.println("_putReflectedAttributesOnHlaSubscribers: key " + elt.getKey());
            System.out.println("_putReflectedAttributesOnHlaSubscribers: value " + elt.getValue().toString());

            //multiple events can occur at the same time
            LinkedList<TimedEvent> events = elt.getValue();
            while (events.size() > 0) {

                TimedEvent ravevent = events.get(0);

                // All rav-events received by HlaSubscriber actors, RAV(tau) with tau < hlaCurrentTime
                // are put in the event queue with timestamp hlaCurrentTime
                if (_timeStepped) {
                    ravevent.timeStamp = _getHlaCurrentTime();
                }
                // If any rav-event received by HlaSubscriber actors, RAV(tau) with tau < ptolemy startTime
                // are put in the event queue with timestamp startTime
                //FIXME: Or should it be an exception because there is something wrong with
                //the overall simulation ??
                if (ravevent.timeStamp
                        .compareTo(_director.getModelStartTime()) < 0) {
                    ravevent.timeStamp = _director.getModelStartTime();
                }

                // Get the HLA subscriber actor to which the event is destined to.
                String identity = elt.getKey();
                System.out.println("_putReflectedAttributesOnHlaSubscribers: identity = " + identity);

                TypedIOPort tiop = _getPortFromTab(
                        _hlaAttributesToSubscribeTo.get(identity));
                HlaSubscriber hs = (HlaSubscriber) tiop.getContainer();

                System.out.println("_putReflectedAttributesOnHlaSubscribers: HlaSubscriber = " + hs.getFullName());

                hs.putReflectedHlaAttribute(ravevent);

                System.out.println("_putReflectedAttributesOnHlaSubscribers(): put event: HlaAttribute = " + hs.getDisplayName() + ", timestamp = " +_printTimes(ravevent.timeStamp));

                if (_debugging) {
                    _debug("    _putReflectedAttributesOnHlaSubscribers() - put Event: folRAV( Hla attribute = "
                            + hs.getDisplayName() + ", timestamp = "
                            + _printTimes(ravevent.timeStamp) + ") "
                            + " in the Hla Subscriber");
                }

                /*
                // XXX: FIXME: GiL: begin HLA Reporter code ?
                if (_folRAVsTimes.lastIndexOf("*") >= 0) {
                    _folRAVsTimes.replace(_folRAVsTimes.lastIndexOf("*"),
                            _folRAVsTimes.length(), ravevent.timeStamp + ";");
                }
                // XXX: FIXME: GiL: end HLA Reporter code ?
                 */

                events.removeFirst();
            }
        }
    }

    /**
     * Get hlaNextPointInTime in HLA to advance to when TAR is used.
     * hlaNextPointInTime = hlaCurrentTime + Ts.
     * @return next point in time to advance to.
     * @exception IllegalActionException if hlaTimeStep is NULL.
     */
    private Time _getHlaNextPointInTime() throws IllegalActionException {
        return _getHlaCurrentTime().add(_hlaTimeStep);
    }

    /**
     * Get the current time in HLA which is advanced after a TAG callback.
     * @return hla current time
     */
    private Time _getHlaCurrentTime() throws IllegalActionException {
        CertiLogicalTime certiCurrentTime = (CertiLogicalTime) _federateAmbassador.logicalTimeHLA;
        return _convertToPtolemyTime(certiCurrentTime);
    }

    /**
     * Initialize the variables that are going to be used to create the reports
     * in the files {@link #_file} and {@link #_csvFile}
     */
    private void _initializeReportVariables() {
        _numberOfTARs = 0;
        _numberOfTicks2 = 0;
        _numberOfNERs = 0;
        _numberOfTAGs = 0;
        _runtime = 0;
        _timeOfTheLastAdvanceRequest = 0;
        _numberOfOtherTicks = 0;

        //_numberOfAttributesToPublish = _hlaAttributesToPublish.size();
        //_nameOfTheAttributesToPublish = new String[_numberOfAttributesToPublish];
        //Object attributesToPublish[] = _hlaAttributesToPublish.keySet()
        //        .toArray();
        //System.out.println("Attributes to publish: ");
        //_UAVsValues = new StringBuffer[_numberOfAttributesToPublish];
        //_RAVsValues = null;
        //for (int i = 0; i < _numberOfAttributesToPublish; i++) {
        //    _nameOfTheAttributesToPublish[i] = attributesToPublish[i]
        //            .toString();
        //    _UAVsValues[i] = new StringBuffer("");
        //    System.out.println(_nameOfTheAttributesToPublish[i]);
        //}
        _tPTII = new StringBuffer("");
        _tHLA = new StringBuffer("");

        _reasonsToPrintTheTime = new StringBuffer("");
        //_pUAVsTimes = new StringBuffer("");
        //_preUAVsTimes = new StringBuffer("");
        //_pRAVsTimes = new StringBuffer("");
        //_folRAVsTimes = new StringBuffer("");
        _TAGDelay = new ArrayList<Double>();
        _numberOfTicks = new ArrayList<Integer>();
        _numberOfRAVs = 0;
        _numberOfUAVs = 0;
    }
    /*
    /**This function was created with the sole purpose of solving the
     * java problem with mathematical operations of real numbers.
     * In time stepped systems, we used to have a situation where instead of,
     * for example, TAR(0.001), we had TAR(0.0010000001) or TAR(0.0009999999).
     * In order to prevent that, as we already know that a time value can't have
     * more decimal digits than the time step + lookAhead, we round it
     * to this number of digits.
     * @param value The time value that is going to be rounded.
     * @return A double representing a rounded time value.
     *
    private double _roundDoubles(double value) {
        //Forcing the number to have the same amount of decimal digits
        //than the time step;
        return Double.parseDouble(_printFormatedNumbers(value));
    }
     */


    // XXX: FIXME: GiL: begin HLA Reporter code ?
    private String _printFormatedNumbers(double value) {
        DecimalFormat df = new DecimalFormat(_decimalFormat);
        df.setRoundingMode(RoundingMode.HALF_DOWN);
        return df.format(value);
    }

    private String _printTimes(Time time) {
        return _printFormatedNumbers(time.getDoubleValue());
    }
    // XXX: FIXME: GiL: end HLA Reporter code ?

    private Time _getModelTime() {
        return _director.getModelTime();
    }

    private SuperdenseTime _getModelSuperdenseTime() {
        return new SuperdenseTime(_director.getModelTime(),
                _director.getMicrostep());
    }

    private void _storeTimes(String reason) {
        try {
            String tHLA = _printTimes(_getHlaCurrentTime());
            String tPTII = _printTimes(_director.getModelTime());
            //_tPTII.append(tPTII + ";");
            //_tHLA.append(tHLA + ";");
            _reasonsToPrintTheTime.append(reason + ";");

        } catch (IllegalActionException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

    }

    ///////////////////////////////////////////////////////////////////
    ////                         private variables                 ////

    /** Name of the current Ptolemy federate ({@link HlaManager}).*/
    private String _federateName;

    /**-Name of the HLA/CERTI federation to create or to join. */
    private String _federationName;

    /** RTI Ambassador for the Ptolemy Federate. */
    private CertiRtiAmbassador _rtia;

    /** Federate Ambassador for the Ptolemy Federate. */
    private PtolemyFederateAmbassadorInner _federateAmbassador;

    /** Indicates the use of the nextEventRequest() service. */
    private Boolean _eventBased;

    /** Indicates the use of the timeAdvanceRequest() service. */
    private Boolean _timeStepped;

    /** Indicates the use of the enableTimeConstrained() service. */
    private Boolean _isTimeConstrained;

    /** Indicates the use of the enableTimeRegulation() service. */
    private Boolean _isTimeRegulator;

    /** Time step of the Ptolemy Federate. */
    private Double _hlaTimeStep;

    /** The lookahead value of the Ptolemy Federate. */
    private Double _hlaLookAHead;

    /** Indicates if the Ptolemy Federate will use a synchronization point. */
    private Boolean _requireSynchronization;

    /** Name of the synchronization point to create or to reach. */
    private String _synchronizationPointName;

    /** Indicates if the Ptolemy Federate is the creator of the synchronization
     *  point.
     */
    private Boolean _isCreator;

    private Time _stopTime;

    /** Represents the number of next event request this federate has made.
     *
     * Event driven federates advance to the time-stamp of the next event. In order to complete the
     * advancement, they have to ask the federation's permission to do so using a NER call.
     */
    private int _numberOfNERs;

    /** Represents the number of time advance grants this federate has received.
     *
     * Federates have to ask permission to the federation in order to advance in time.
     * If there is no events happening in an inferior time to the proposed one, the
     * federation sends a TAG to the federate and this last one advances in time.
     */
    private int _numberOfTAGs;

    /** Represents the number of time advance requests(TAR) this federate has made.
     *
     * Time-stepped federates advance with a fixed step in time. In order to complete the
     * advancement, they have to ask the federation's permission to do so using a TAR call.
     */
    private int _numberOfTARs;

    private double _runtime;

    private int _numberOfTicks2;

    private String _testsFolder;

    private StringBuffer _tPTII;

    private StringBuffer _tHLA;

    private StringBuffer _reasonsToPrintTheTime;

    /** Represents a text file that is going to keep track of the number of HLA calls of the
     * federate.
     */
    private File _file;

    /** Represents a ".csv" file that is going to keep track of the number of HLA calls of the
     * federate.
     */
    private File _csvFile;

    /** Represents a ".csv" file that is going to keep track of the number of HLA calls of the
     * federate.
     */
    private File _reportFile;

    /**
     * Represents the file that tracks the values that have been updated and the time of their update.
     */
    //private File _UAVsValuesFile;
    //private Date _date;
    private String _decimalFormat;
    //FIXME: add comments
    //private StringBuffer[] _UAVsValues;
    //private int _numberOfAttributesToPublish;
    //private int _numberOfAttributesSubscribedTo;
    //private String[] _nameOfTheAttributesToPublish;
    //private String[] _nameOfTheAttributesSubscribedTo;
    //private StringBuffer _pUAVsTimes;
    //private StringBuffer _preUAVsTimes;
    //private File _RAVsValuesFile;
    //private StringBuffer[] _RAVsValues;
    //private StringBuffer _pRAVsTimes;
    //private StringBuffer _folRAVsTimes;
    /** Represents the instant when the simulation is fully started
     * (when the last federate starts running).
     */
    private static double _startTime;

    /** Records the last proposed time to avoid multiple HLA time advancement
     *  requests at the same time.
     */
    //    private Time _lastProposedTime;

    /** A reference to the enclosing director. */
    private DEDirector _director;

    /** The RTIG subprocess. */
    private CertiRtig _certiRtig;

    /**
     * Map between an Class ID given by the RTI and all we need to know
     * about it in the model
     */
    private HashMap<Integer, StructuralInformation> _structuralInformation;

    /** Map class intance name and object instance ID. Those information are set
     *  using discoverObjectInstance() callback and used by the RAV service.
     */
    private HashMap<Integer, String> _discoverObjectInstanceMap;

    /**
     * Map <Sender actor + HlaPublishers> and ROI ID for an object instance. See HlaPublishers.
     * 
     * HashMap for HlaPublishers to remember which actor's ID has
     * been registered (as an object instance) to the Federation.
     */
    private HashMap<String, Integer> _registerObjectInstanceMap;

    /**
     * The actual value for hlaTimeUnit parameter.
     */
    private double _hlaTimeUnitValue;

    /**
    Set to false once in discoverObjectInstance and resert to true in noNewActors
     */
    private boolean _noObjectDicovered;

    /**
     * The time of the last TAR or last NER.
     */
    private double _timeOfTheLastAdvanceRequest;

    /**
     * Array that contains the delays between a NER or TAR and its respective TAG..
     */
    private ArrayList<Double> _TAGDelay;

    /**
     * Array that contains the number of ticks between a NER or TAR and its respective TAG.
     */
    private ArrayList<Integer> _numberOfTicks;

    /**
     * Represents the number of the ticks that were not considered in the variable {@link #_numberOfTicks}
     */
    private int _numberOfOtherTicks;

    private int _numberOfRAVs;

    private int _numberOfUAVs;

    ///////////////////////////////////////////////////////////////////
    ////                    private  methods                 ////

    /**
     * Will do the initial synchronization loop among the fedeate
     * and register the synchronization point if the federate is
     */
    private void _doInitialSynchronization() throws IllegalActionException {
        if (!_requireSynchronization) {
            return;
        }
        // If the current Federate is the creator then create the
        // synchronization point.
        if (_isCreator) {
            try {
                byte[] rfspTag = EncodingHelpers
                        .encodeString(_synchronizationPointName);
                _rtia.registerFederationSynchronizationPoint(
                        _synchronizationPointName, rfspTag);
            } catch (RTIexception e) {
                throw new IllegalActionException(this, e, e.getMessage());
            }

            // Wait synchronization point callbacks.
            while (!(_federateAmbassador.synchronizationSuccess)
                    && !(_federateAmbassador.synchronizationFailed)) {
                try {
                    _rtia.tick2();

                    // XXX: FIXME: GiL: begin HLA Reporter code ?
                    _numberOfTicks2++;
                    if (_timeOfTheLastAdvanceRequest > 0) {
                        _numberOfTicks.set(_numberOfTAGs,
                                _numberOfTicks.get(_numberOfTAGs) + 1);
                    } else {
                        _numberOfOtherTicks++;
                    }
                    // XXX: FIXME: GiL: end HLA Reporter code ?
                } catch (RTIexception e) {
                    throw new IllegalActionException(this, e, e.getMessage());
                }
            }

            if (_federateAmbassador.synchronizationFailed) {
                throw new IllegalActionException(this,
                        "CERTI: Synchronization error ! ");
            }
        } // End block for synchronization point creation case.

        // Wait synchronization point announcement.
        while (!(_federateAmbassador.inPause)) {
            try {
                _rtia.tick2();

                // XXX: FIXME: GiL: begin HLA Reporter code ?
                _numberOfTicks2++;
                if (_timeOfTheLastAdvanceRequest > 0) {
                    _numberOfTicks.set(_numberOfTAGs,
                            _numberOfTicks.get(_numberOfTAGs) + 1);
                } else {
                    _numberOfOtherTicks++;
                }
                // XXX: FIXME: GiL: end HLA Reporter code ?

            } catch (RTIexception e) {
                throw new IllegalActionException(this, e, e.getMessage());
            }
        }

        // Satisfied synchronization point.
        try {
            _rtia.synchronizationPointAchieved(_synchronizationPointName);
            if (_debugging) {
                _debug("_doInitialSynchronization() - initialize() - Synchronisation point "
                        + _synchronizationPointName + " satisfied !");
            }
        } catch (RTIexception e) {
            throw new IllegalActionException(this, e, e.getMessage());
        }

        // Wait federation synchronization.
        while (_federateAmbassador.inPause) {
            if (_debugging) {
                _debug("_doInitialSynchronization() - initialize() - Waiting for simulation phase !");
            }

            try {
                _rtia.tick2();

                System.out.println("tick2");
                // XXX: FIXME: GiL: begin HLA Reporter code ?
                _numberOfTicks2++;
                if (_timeOfTheLastAdvanceRequest > 0) {
                    _numberOfTicks.set(_numberOfTAGs,
                            _numberOfTicks.get(_numberOfTAGs) + 1);
                } else {
                    _numberOfOtherTicks++;
                }
                // XXX: FIXME: GiL: end HLA Reporter code ?

            } catch (RTIexception e) {
                throw new IllegalActionException(this, e, e.getMessage());
            }
        }
    }

    /**
     * Will enable all time regulating aspect for the federate. After this call
     * the federate as stated to the RTI if it time regulating and/or time regulator
     * and has enable asynchronous delivery for RO messages
     * @exception IllegalActionException
     */
    private void _initializeTimeAspects() throws IllegalActionException {

        // Initialize Federate timing values.
        _federateAmbassador.initializeTimeValues(0.0, _hlaLookAHead);

        // Declare the Federate time constrained (if true).
        if (_isTimeConstrained) {
            try {
                _rtia.enableTimeConstrained();
            } catch (RTIexception e) {
                throw new IllegalActionException(this, e, e.getMessage());
            }
        }

        // Declare the Federate time regulator (if true).
        if (_isTimeRegulator) {
            try {
                _rtia.enableTimeRegulation(_federateAmbassador.logicalTimeHLA,
                        _federateAmbassador.effectiveLookAHead);
            } catch (RTIexception e) {
                throw new IllegalActionException(this, e, e.getMessage());
            }
        }

        // Wait the response of the RTI towards Federate time policies that has
        // been declared. The only way to get a response is to invoke the tick()
        // method to receive callbacks from the RTI. We use here the tick2()
        // method which is blocking and saves more CPU than the tick() method.
        if (_isTimeRegulator && _isTimeConstrained) {
            while (!(_federateAmbassador.timeConstrained)) {
                try {
                    _rtia.tick2();

                    // XXX: FIXME: GiL: begin HLA Reporter code ?
                    _numberOfTicks2++;
                    if (_timeOfTheLastAdvanceRequest > 0) {
                        _numberOfTicks.set(_numberOfTAGs,
                                _numberOfTicks.get(_numberOfTAGs) + 1);
                    } else {
                        _numberOfOtherTicks++;
                    }
                    // XXX: FIXME: GiL: end HLA Reporter code ?

                } catch (RTIexception e) {
                    throw new IllegalActionException(this, e, e.getMessage());
                }
            }

            while (!(_federateAmbassador.timeRegulator)) {
                try {
                    _rtia.tick2();

                    // XXX: FIXME: GiL: begin HLA Reporter code ?
                    _numberOfTicks2++;
                    if (_timeOfTheLastAdvanceRequest > 0) {
                        _numberOfTicks.set(_numberOfTAGs,
                                _numberOfTicks.get(_numberOfTAGs) + 1);
                    } else {
                        _numberOfOtherTicks++;
                    }
                    // XXX: FIXME: GiL: end HLA Reporter code ?

                } catch (RTIexception e) {
                    throw new IllegalActionException(this, e, e.getMessage());
                }
            }

            if (_debugging) {
                _debug("_initializeTimeAspects() - initialize() -"
                        + " Time Management policies:" + " is Constrained = "
                        + _federateAmbassador.timeConstrained
                        + " and is Regulator = "
                        + _federateAmbassador.timeRegulator);
            }

            // The following service is required to allow the reception of
            // callbacks from the RTI when a Federate used the Time management.
            try {
                _rtia.enableAsynchronousDelivery();
            } catch (RTIexception e) {
                throw new IllegalActionException(this, e, e.getMessage());
            }
        }
    }

    ///////////////////////////////////////////////////////////////////
    ////                    private static methods                 ////

    /*
     * This set of function is just here to hide (a bit) the fact we are using
     * an array of Object as value for  _hlaAttributesToPublish
     * and for _hlaAttributesSubscribedTo. So instead of using magic indexes
     * and do each time the downgrade, you can use these functions.
     */

    /**
     * 
     * @param tab
     * @return
     */
    private static TypedIOPort _getPortFromTab(Object[] tab) {
        return (TypedIOPort) tab[0];
    }

    /**
     * 
     * @param tab
     * @return
     */
    static private Type _getTypeFromTab(Object[] tab) {
        return (Type) tab[1];
    }

    /**
     * 
     * @param tab
     * @return
     */
    static private String _getClassObjectNameFromTab(Object[] tab) {
        return (String) tab[2];
    }

    /**
     * 
     * @param tab
     * @return
     */
    static private String _getClassInstanceNameFromTab(Object[] tab) {
        return (String) tab[3];
    }

    /**
     * 
     * @param tab
     * @return
     */
    static private Integer _getClassHandleFromTab(Object[] tab) {
        return (Integer) tab[4];
    }

    /**
     * 
     * @param tab
     * @return
     */
    static private Integer _getAttributeHandleFromTab(Object[] tab) {
        return (Integer) tab[5];
    }

    ///////////////////////////////////////////////////////////////////
    ////                         inner class                       ////

    /** This class extends the {@link NullFederateAmbassador} class which
     *  implements the basics HLA services provided by the JCERTI bindings.
     *  @author Gilles Lasnier
     */
    private class PtolemyFederateAmbassadorInner
    extends NullFederateAmbassador {

        ///////////////////////////////////////////////////////////////////
        ////                         public variables                  ////

        /** Indicates if the Federate is declared as time regulator in the
         *  HLA/CERTI Federation. This value is set by callback by the RTI.
         */
        public Boolean timeRegulator;

        /** Indicates if the Federate is declared as time constrained in the
         *  HLA/CERTI Federation. This value is set by callback by the RTI.
         */
        public Boolean timeConstrained;

        /** Indicates if the Federate has received the time advance grant from
         *  the HLA/CERTI Federation. This value is set by callback by the RTI.
         */
        public Boolean timeAdvanceGrant;

        /** Indicates the current HLA logical time of the Federate. This value
         *  is set by callback by the RTI.
         */
        public LogicalTime logicalTimeHLA;

        /** Indicates if the request of synchronization by the Federate is
         *  validated by the HLA/CERTI Federation. This value is set by callback
         *  by the RTI.
         */
        public Boolean synchronizationSuccess;

        /** Indicates if the request of synchronization by the Federate
         *  has failed. This value is set by callback by the RTI.
         */
        public Boolean synchronizationFailed;

        /** Indicates if the Federate is currently synchronize to others. This
         * value is set by callback by the RTI.
         */
        public Boolean inPause;

        /** The lookahead value set by the user and used by CERTI to handle
         *  time management and to order TSO events.
         */
        public LogicalTimeInterval effectiveLookAHead;

        ///////////////////////////////////////////////////////////////////
        ////                         public methods                    ////

        /** Initialize the {@link PtolemyFederateAmbassadorInner} which handles
         *  the communication from RTI -> to RTIA -> to FEDERATE. The
         *  <i>rtia</i> manages the interaction with the external communicant
         *  process RTIA. This method called the Declaration Management
         *  services provide by HLA/CERTI to publish/subscribe to HLA attributes
         *  in a HLA Federation.
         *  @param rtia
         *  @exception NameNotFound
         *  @exception ObjectClassNotDefined
         *  @exception FederateNotExecutionMember
         *  @exception RTIinternalError
         *  @exception AttributeNotDefined
         *  @exception SaveInProgress
         *  @exception RestoreInProgress
         *  @exception ConcurrentAccessAttempted
         *  All those exceptions are from the HLA/CERTI implementation.
         */
        public void initialize(RTIambassador rtia) throws NameNotFound,
        ObjectClassNotDefined, FederateNotExecutionMember,
        RTIinternalError, AttributeNotDefined, SaveInProgress,
        RestoreInProgress, ConcurrentAccessAttempted {

            // Retrieve model stop time
            _stopTime = _director.getModelStopTime();


            // XXX: FIXME: GiL: begin HLA Reporter code ?
            _initializeReportVariables();
            /*_date = new Date();
            if (_isCreator) {
                writeInTextFile(_csvFile,
                        "\n" + _date.toString() + "\nSTART OF THE FEDERATION;");
                writeInTextFile(_file, "------------------\n" + _date.toString()
                        + "\nSTART OF THE FEDERATION");
            }
             */
            int numberOfDecimalDigits;
            if (_timeStepped) {
                System.out.println("PtolemyFederateAmbassadorInner: initialize(): hlaTimeStep=" + _hlaTimeStep);
                String s = _hlaTimeStep.toString();
                s = s.substring(s.indexOf(".") + 1);
                int n1 = s.length();
                s = _hlaLookAHead.toString();
                s = s.substring(s.indexOf(".") + 1);
                int n2 = s.length();
                if (n1 > n2) {
                    numberOfDecimalDigits = n1;
                } else {
                    numberOfDecimalDigits = n2;
                }
            } else {
                numberOfDecimalDigits = 10;
            }
            StringBuffer format = new StringBuffer("#.#");
            for (int i = 1; i < numberOfDecimalDigits; i++) {
                format.append("#");
            }
            _decimalFormat = format.toString();

            _numberOfTicks.add(0);
            // XXX: FIXME: GiL: end HLA Reporter code ?

            this.timeAdvanceGrant = false;
            this.timeConstrained = false;
            this.timeRegulator = false;
            this.synchronizationSuccess = false;
            this.synchronizationFailed = false;
            this.inPause = false;

            // Configure HlaPublisher actors from model */
            if (!_hlaAttributesToPublish.isEmpty()){
                setupHlaPublishers(rtia);
            } else {
                System.out.println("PtolemyFederateAmbassadorInner: initialize(): _hlaAttributesToPublish is empty");
            }
            // Configure HlaSubscriber actors from model */
            if (!_hlaAttributesToSubscribeTo.isEmpty()) {
                setupHlaSubscribers(rtia);
            } else {
                System.out.println("PtolemyFederateAmbassadorInner: initialize(): _hlaAttributesToSubscribeTo is empty");
            }

        }

        /** Initialize Federate's timing properties provided by the user.
         *  @param startTime The start time of the Federate logical clock.
         *  @param timeStep The time step of the Federate.
         *  @param lookAHead The contract value used by HLA/CERTI to synchronize
         *  the Federates and to order TSO events.
         *  @exception IllegalActionException
         */
        public void initializeTimeValues(Double startTime, Double lookAHead)
                throws IllegalActionException {
            if (lookAHead <= 0) {
                throw new IllegalActionException(null, null, null,
                        "LookAhead field in HLAManager must be greater than 0.");
            }
            logicalTimeHLA = new CertiLogicalTime(startTime);

            effectiveLookAHead = new CertiLogicalTimeInterval(
                    lookAHead * _hlaTimeUnitValue);
            if (_debugging) {
                _debug("initializeTimeValues() - Effective HLA lookahead is "
                        + effectiveLookAHead.toString());
            }
            timeAdvanceGrant = false;

        }

        // HLA Object Management services (callbacks).

        /** Callback to receive updated value of a HLA attribute from the
         *  whole Federation (delivered by the RTI (CERTI)).
         */
        @Override
        public void reflectAttributeValues(int theObject,
                ReflectedAttributes theAttributes, byte[] userSuppliedTag,
                LogicalTime theTime, EventRetractionHandle retractionHandle)
                        throws ObjectNotKnown, AttributeNotKnown,
                        FederateOwnsAttributes, InvalidFederationTime,
                        FederateInternalError {

            // XXX: FIXME: GiL: what is the purpose of this boolean ?
            //boolean done = false;

            if (_debugging) {
                _debug("starting reflectAttributeValues() - current status - "
                        + "t_ptII = " + _printTimes(_director.getModelTime())
                        + "; t_hla = " + _federateAmbassador.logicalTimeHLA);
            }

            System.out.println("callback: reflectAttributeValues:"
                    + " theObject=" + theObject + " theAttributes" + theAttributes + " userSuppliedTag=" + userSuppliedTag + " theTime=" + theTime);
            try {
                // Get the object class handle corresponding to
                // received "theObject" id.
                int classHandle = _objectIdToClassHandle.get(theObject);
                String classInstanceName = _discoverObjectInstanceMap.get(theObject);

                System.out.println("callback: reflectAttributeValues: _objectIdToClassHandle.get(theObject) classHandle = " + classHandle);
                System.out.println("callback: reflectAttributeValues: _discoverObjectInstanceMap.get(theObject) classInstanceName = " + classInstanceName);

                for (int i = 0; i < theAttributes.size(); i++) {

                    Iterator<Entry<String, Object[]>> ot = _hlaAttributesToSubscribeTo
                            .entrySet().iterator();

                    while (ot.hasNext()) {
                        Map.Entry<String, Object[]> elt = ot.next();
                        Object[] tObj = elt.getValue();

                        Time ts = null;
                        TimedEvent te = null;
                        Object value = null;
                        HlaSubscriber hs = (HlaSubscriber) _getPortFromTab(tObj).getContainer();

                        System.out.println("callback: reflectAttributeValues: hlaSubscriber=" + hs.getFullName());
                        System.out.println("callback: reflectAttributeValues: hlaSubscriber classObjectName in FOM " + hs.getClassObjectName());
                        System.out.println("callback: reflectAttributeValues: hlaSubscriber classInstanceName " + hs.getClassInstanceName());
                        System.out.println("callback: reflectAttributeValues: hlaSubscriber classHandle " + hs.getClassHandle());
                        System.out.println("callback: reflectAttributeValues: hlaSubscriber attributeName in FOM " + hs.getAttributeName());
                        System.out.println("callback: reflectAttributeValues: hlaSubscriber AttributeHandle " + hs.getAttributeHandle());

                        System.out.println("callback: reflectAttributeValues: theAttributes.getAttributeHandle(i) = " + theAttributes.getAttributeHandle(i));
                        System.out.println("callback: reflectAttributeValues: classHandle = " + classHandle);
                        System.out.println("callback: reflectAttributeValues: classInstanceName = " + classInstanceName);
                        System.out.println("callback: reflectAttributeValues: (objectInstanceId) theObject = " + theObject);

                        // The tuple (attributeHandle, classHandle) allows to
                        // identify the object attribute
                        // (i.e. one of the HlaSubscribers)
                        // where the updated value has to be put.
                        //if (theAttributes.getAttributeHandle(i) == _getAttributeHandleFromTab(tObj)
                        //        && _getClassHandleFromTab(tObj) == classHandle
                        //        && hs.getClassInstanceName() == classInstanceName) {
                        // && hs.XXX == theObject {
                        if (theAttributes.getAttributeHandle(i) == hs.getAttributeHandle()
                                && classHandle == hs.getClassHandle()
                                && hs.getClassInstanceName().compareTo(classInstanceName) == 0) {
                            // && hs.XXX == theObject {
                            try {

                                double timeValue = ((CertiLogicalTime) theTime)
                                        .getTime() / _hlaTimeUnitValue;

                                ts = new Time(_director, timeValue);

                                value = MessageProcessing.decodeHlaValue(hs,
                                        (BaseType) _getTypeFromTab(tObj),
                                        theAttributes.getValue(i));

                                te = new OriginatedEvent(ts, new Object[] {
                                        (BaseType) _getTypeFromTab(tObj),
                                        value }, theObject);

                                //_fromFederationEvents.get(hs.getIdentity())
                                _fromFederationEvents.get(hs.getFullName()).add(te);
                                if (_debugging) {
                                    _debug("    reflectAttributeValues() - pRAV("
                                            + "HLA attribute= "
                                            + hs.getAttributeName()
                                            + ", timestamp="
                                            + _printTimes(te.timeStamp)
                                            + " ,val=" + value.toString()
                                            + ") has been received and stored for "
                                            + hs.getDisplayName());
                                }
                                System.out.println("callback: reflectAttributeValues: HLA attribute = " + hs.getAttributeName());
                                System.out.println("callback: reflectAttributeValues: timestamp = " + _printTimes(te.timeStamp));
                                System.out.println("callback: reflectAttributeValues: val = " + value.toString());
                                System.out.println("callback: reflectAttributeValues: received and stored for = " + hs.getFullName());


                                // XXX: FIXME: GiL: what is the purpose of this boolean ?
                                //done = true;

                                /*
                                // XXX: FIXME: GiL: begin HLA Reporter code ?
                                String attributeName = hs.getParameterName();

                                String pRAVTimeStamp = _printTimes(te.timeStamp)
                                        + ";";
                                if (_numberOfRAVs > 0 && (_pRAVsTimes.length()
                                        - _pRAVsTimes.lastIndexOf(
                                                pRAVTimeStamp)) == pRAVTimeStamp
                                                        .length()) {
                                    int indexOfAttribute = 0;
                                    for (int j = 0; j < _numberOfAttributesSubscribedTo; j++) {
                                        if (_nameOfTheAttributesSubscribedTo[j]
                                                .substring(
                                                        _nameOfTheAttributesSubscribedTo[j]
                                                                .lastIndexOf(
                                                                        "-" + attributeName)
                                                                + 1)
                                                .equals(attributeName)) {
                                            indexOfAttribute = j;
                                            break;
                                        }
                                    }
                                    _RAVsValues[indexOfAttribute].replace(
                                            _RAVsValues[indexOfAttribute]
                                                    .length() - 2,
                                            _RAVsValues[indexOfAttribute]
                                                    .length(),
                                            value.toString() + ";");
                                    //_UAVsValues[indexOfAttribute].replace(_UAVsValues[indexOfAttribute].length()-2,_UAVsValues[indexOfAttribute].length(),in.toString()+";");
                                } else {
                                    if (_numberOfRAVs < 1) {
                                        _numberOfAttributesSubscribedTo = _hlaAttributesSubscribedTo
                                                .size();
                                        _nameOfTheAttributesSubscribedTo = new String[_numberOfAttributesSubscribedTo];
                                        Object attributesSubscribedTo[] = _hlaAttributesSubscribedTo
                                                .keySet().toArray();
                                        System.out.println(
                                                "Attributes subscribed to: ");
                                        _RAVsValues = new StringBuffer[_numberOfAttributesSubscribedTo];
                                        for (int y = 0; y < _numberOfAttributesSubscribedTo; y++) {
                                            _nameOfTheAttributesSubscribedTo[y] = attributesSubscribedTo[y]
                                                    .toString();
                                            _RAVsValues[y] = new StringBuffer(
                                                    "");
                                            System.out.println(
                                                    _nameOfTheAttributesSubscribedTo[y]);
                                        }
                                    }

                                    int indexOfAttribute = 0;
                                    for (int j = 0; j < _numberOfAttributesSubscribedTo; j++) {
                                        if (_nameOfTheAttributesSubscribedTo[j]
                                                .substring(
                                                        _nameOfTheAttributesSubscribedTo[j]
                                                                .lastIndexOf(
                                                                        "-" + attributeName)
                                                                + 1)
                                                .equals(attributeName)) {
                                            indexOfAttribute = j;
                                            break;
                                        }
                                    }
                                    _folRAVsTimes.append("*");
                                    _pRAVsTimes.append(pRAVTimeStamp);
                                    for (int j = 0; j < _numberOfAttributesSubscribedTo; j++) {
                                        if (j == indexOfAttribute) {
                                            _RAVsValues[j].append(
                                                    value.toString() + ";");
                                        } else {
                                            _RAVsValues[j].append("-;");
                                        }
                                    }
                                }
                                _numberOfRAVs++;
                                //_RAVsValues=_RAVsValues + value.toString()+";";
                                // XXX: FIXME: GiL: end HLA Reporter code ?
                                 */
                            } catch (IllegalActionException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                }
            } catch (ArrayIndexOutOfBounds e) {
                e.printStackTrace();
            }
            /*This may trigger, not 100% sure. If so, need to review the code
            to store RAV no matter what*/
            /* if (!done) {
                throw new FederateInternalError(
                        "Received a RAV but could not put in "
                                + "anyobject. Means the ChangeRequest has not been processed yet");
            }*/ 
            catch (IllegalActionException e1) {
                // TODO Auto-generated catch block
                e1.printStackTrace();
            }
        }

        /** Callback delivered by the RTI (CERTI) to discover attribute instance
         *  of HLA attribute that the Federate is subscribed to.
         */
        @Override
        public void discoverObjectInstance(int objectInstanceId, int classHandle,
                String classInstanceName) throws CouldNotDiscover,
        ObjectClassNotKnown, FederateInternalError {

            System.out.println("callback: discoverObjectInstance:"
                    + " objectInstanceId=" + objectInstanceId
                    + " classHandle=" + classHandle
                    + " classIntanceName=" + classInstanceName);

            if (_discoverObjectInstanceMap.containsKey(objectInstanceId)) {
                System.out.println("callback: discoverObjectInstance: found an instance class already registered: "
                        + classInstanceName);
                // XXX: FIXME: GiL: do something???

            } else {
                _discoverObjectInstanceMap.put(objectInstanceId, classInstanceName);
                _objectIdToClassHandle.put(objectInstanceId, classHandle);
            }

            // 1. Get classHandle and attributeHandle IDs for each attribute
            // value to subscribe (i.e. HlaSubcriber). Update the HlaSubcribers.
            Iterator<Entry<String, Object[]>> it1 = _hlaAttributesToSubscribeTo
                    .entrySet().iterator();

            while (it1.hasNext()) {
                Map.Entry<String, Object[]> elt = it1.next();
                // elt.getKey()   => HlaSubscriber actor full name.
                // elt.getValue() => tObj[] array.
                Object[] tObj = elt.getValue();

                // Get corresponding HlaSubcriber actor.
                HlaSubscriber sub = (HlaSubscriber) ((TypedIOPort) tObj[0]).getContainer();
                try {
                    if (sub.getClassInstanceName().compareTo(classInstanceName) == 0) {
                        sub.setObjectInstanceId(objectInstanceId);
                    }
                } catch (IllegalActionException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }

            /*
            //if we discover it means we registered
            // it meands there is a class to instanciate and then classToInstantiate is not null
            final CompositeActor classToInstantiate = (CompositeActor) _structuralInformation
                    .get(classHandle).classToInstantiate;
            _noObjectDicovered = false;


            //Build a change request
            ChangeRequest request = new ChangeRequest(this,
                    "Adding " + objectName, true) {
                //
                 // Sum up of the structural change :
                 // Get the class instantiate and its container
                 // If no free actor, instantiate one otherwise use an existing one
                 // Map the actor (set its name, ObjectHandle and put it in _hlaAttributesSubscribedTo)
                 //
                @Override
                protected void _execute() throws IllegalActionException {

                    CompositeActor container = (CompositeActor) classToInstantiate
                            .getContainer();
                    CompositeActor newActor = null;
                    try {
                        Instantiable instance = null;
                        StructuralInformation info = _structuralInformation
                                .get(classHandle);
                        LinkedList<ComponentEntity> actors = info.freeActors;

                        //if it is a new actor, then we has to connect the ports
                        if (actors.size() == 0) {
                            instance = classToInstantiate.instantiate(container,
                                    objectName);
                            newActor = (CompositeActor) instance;

                            LinkedList<IOPort> outputPortList = (LinkedList<IOPort>) newActor
                                    .outputPortList();

                            container.notifyConnectivityChange();

                            for (IOPort out : outputPortList) {
                                ComponentRelation r = null;
                                HashSet<IOPort> ports = info
                                        .getPortReceiver(out.getName());

                                //if we dont know what to do with the port, just skip it
                                if (ports == null) {
                                    continue;
                                }

                                for (IOPort recv : ports) {
                                    if (r == null) {
                                        //connect output port to new relation
                                        r = container.connect(out, recv,
                                                objectName + " "
                                                        + out.getName());
                                    } else {
                                        //connect destination to relation
                                        recv.link(r);
                                    }
                                }
                            }
                            if (_debugging) {
                                _debug(" discoverObjectInstance() - New object will do object "
                                        + objectName);
                            }

                        } else {
                            //retrieve and remove head
                            instance = actors.poll();
                            if (instance == null) {
                                throw new IllegalActionException(
                                        "A null actor requested to be inserted in the model");
                            } else {
                                newActor = (CompositeActor) instance;
                                newActor.setDisplayName(objectName);
                                if (_debugging) {
                                    _debug(instance.getName()
                                            + "  discoverObjectInstance() - will do object "
                                            + objectName);
                                }
                            }
                        }

                        //if the actor as an attribute called temp block
                        //then set it up to the actual name
                        {
                            Attribute name = newActor
                                    .getAttribute("objectName");
                            if (name != null) {
                                Parameter p = (Parameter) name;
                                p.setTypeEquals(BaseType.STRING);
                                p.setExpression("\"" + objectName + "\"");
                            }
                        }

                        // List all HlaSubscriber inside the instance and set them up
                        List<HlaSubscriber> subscribers = newActor
                                .entityList(HlaSubscriber.class);
                        for (int i = 0; i < subscribers.size(); ++i) {
                            HlaSubscriber sub = subscribers.get(i);
                            sub.objectName
                                    .setExpression("\"" + objectName + "\"");
                            ;
                            sub.setObjectHandle(objectHandle);
                            _hlaAttributesSubscribedTo.put(sub.getIdentity(),
                                    new Object[] { sub.output,
                                            sub.output.getType(), "", //empty string because it is parameter no longer used, but
                                            // some functions rely on classHandle and attributeHandle
                                            // being at index 3 and 4
                                            classHandle,
                                            sub.getAttributeHandle() });
                            _fromFederationEvents.put(sub.getIdentity(),
                                    new LinkedList<TimedEvent>());
                        }
                    } catch (NameDuplicationException
                            | CloneNotSupportedException ex) {
                        ex.printStackTrace();
                    }
                }
            };
            request.setPersistent(false);
            requestChange(request);
             */
            /*        if (_debugging) {
                String toLog = "INNER"
                        + " discoverObjectInstance() - the object " + objectName
                        + " has been discovered" + " (ID=" + objectHandle
                        + ", class'ID=" + classHandle + ")";
                _debug(toLog);
            }*/
        }

        // HLA Time Management services (callbacks).

        /** Callback delivered by the RTI (CERTI) to validate that the Federate
         *  is declared as time-regulator in the HLA Federation.
         */
        @Override
        public void timeRegulationEnabled(LogicalTime theFederateTime)
                throws InvalidFederationTime, EnableTimeRegulationWasNotPending,
                FederateInternalError {
            timeRegulator = true;
            if (_debugging) {
                _debug("INNER" + " timeRegulationEnabled() - timeRegulator = "
                        + timeRegulator);
            }
        }

        /** Callback delivered by the RTI (CERTI) to validate that the Federate
         *  is declared as time-constrained in the HLA Federation.
         */
        @Override
        public void timeConstrainedEnabled(LogicalTime theFederateTime)
                throws InvalidFederationTime,
                EnableTimeConstrainedWasNotPending, FederateInternalError {
            timeConstrained = true;
            if (_debugging) {
                _debug("INNER"
                        + " timeConstrainedEnabled() - timeConstrained = "
                        + timeConstrained);
            }
        }

        /** Callback (TAG) delivered by the RTI (CERTI) to notify that the
         *  Federate is authorized to advance its time to <i>theTime</i>.
         *  This time is the same or smaller than the time specified
         *  when calling the nextEventRequest() or the timeAdvanceRequest()
         *  services.
         */
        @Override
        public void timeAdvanceGrant(LogicalTime theTime)
                throws InvalidFederationTime, TimeAdvanceWasNotInProgress,
                FederateInternalError {
            double time = ((CertiLogicalTime) theTime).getTime();

            logicalTimeHLA = new CertiLogicalTime(time);
            //* ((CertiLogicalTime) theTime).getTime());
            // Time spent between the last TAR or NER and the TAG.
            _TAGDelay.add((System.nanoTime() - _timeOfTheLastAdvanceRequest)
                    / Math.pow(10, 9));

            _timeOfTheLastAdvanceRequest = 0;
            timeAdvanceGrant = true;

            _numberOfTAGs++;
            _numberOfTicks.add(0);

            if (_debugging) {
                _debug("timeAdvanceGrant() - TAG(" + logicalTimeHLA.toString()
                + "(HLA Time Unit)) received");
            }
            // XXX: FIXME: GiL: debug
            System.out.println("DBG == _TAGDelay => " + _TAGDelay);
            System.out.println("DBG == timeAdvanceGrant() - TAG(" + logicalTimeHLA.toString() + "(HLA Time Unit)) received");
        }

        // HLA Federation Management services (callbacks).
        // Synchronization point services.

        /** Callback delivered by the RTI (CERTI) to notify if the synchronization
         *  point registration has failed.
         */
        @Override
        public void synchronizationPointRegistrationFailed(
                String synchronizationPointLabel) throws FederateInternalError {
            synchronizationFailed = true;
            if (_debugging) {
                _debug("INNER" + " synchronizationPointRegistrationFailed()"
                        + " - synchronizationFailed = "
                        + synchronizationFailed);
            }
        }

        /** Callback delivered by the RTI (CERTI) to notify if the synchronization
         *  point registration has succeed.
         */
        @Override
        public void synchronizationPointRegistrationSucceeded(
                String synchronizationPointLabel) throws FederateInternalError {
            synchronizationSuccess = true;
            if (_debugging) {
                _debug("INNER" + " synchronizationPointRegistrationSucceeded()"
                        + " - synchronizationSuccess = "
                        + synchronizationSuccess);
            }
        }

        /** Callback delivered by the RTI (CERTI) to notify the announcement of
         *  a synchronization point in the HLA Federation.
         */
        @Override
        public void announceSynchronizationPoint(
                String synchronizationPointLabel, byte[] userSuppliedTag)
                        throws FederateInternalError {
            inPause = true;
            if (_debugging) {
                _debug("INNER" + " announceSynchronizationPoint() - inPause = "
                        + inPause);
            }
        }

        /** Callback delivered by the RTI (CERTI) to notify that the Federate is
         *  synchronized to others Federates using the same synchronization point
         *  in the HLA Federation.
         */
        @Override
        public void federationSynchronized(String synchronizationPointLabel)
                throws FederateInternalError {
            inPause = false;
            if (_debugging) {
                _debug("INNER" + " federationSynchronized() - inPause = "
                        + inPause);
            }
        }

        ///////////////////////////////////////////////////////////////////
        ////                         private methods                   ////

        private void setupHlaPublishers(RTIambassador rtia) throws NameNotFound,
        ObjectClassNotDefined, FederateNotExecutionMember,
        RTIinternalError, AttributeNotDefined, SaveInProgress,
        RestoreInProgress, ConcurrentAccessAttempted {

            // For each HlaPublisher actors deployed in the model we declare
            // to the HLA/CERTI Federation a HLA attribute to publish.

            System.out.println("ciele_debug: setupHlaPublishers: step 1");

            // 1. Get classHandle and attributeHandle IDs for each attribute
            // value to publish (i.e. HlaPublisher). Update the HlaPublishers
            // table with the information.
            Iterator<Entry<String, Object[]>> it = _hlaAttributesToPublish.entrySet().iterator();

            while (it.hasNext()) {
                Map.Entry<String, Object[]> elt = it.next();
                // elt.getKey()   => HlaPublisher actor full name.
                // elt.getValue() => tObj[] array.
                Object[] tObj = elt.getValue();

                // Object class handle and object attribute handle are IDs that
                // allow to identify an HLA attribute.
                int classHandle = rtia
                        .getObjectClassHandle(_getClassObjectNameFromTab(tObj));

                // XXX: FIXME: GiL: warning, the HlaPublisher actor name is the HLA attribute name...
                int attributeHandle = rtia
                        .getAttributeHandle(_getPortFromTab(tObj).getContainer().getName(),
                                classHandle);

                System.out.println("setupHlaPublishers: classHandle = " + classHandle);
                System.out.println("setupHlaPublishers: attributeHandle = " + attributeHandle);

                // Update HLA attribute information (for publication)
                // from HLA services. In this case, the tObj[] object as
                // the following structure:
                // tObj[0] => input port which receives the token to transform
                //            as an updated value of a HLA attribute,
                // tObj[1] => type of the port (e.g. of the attribute),
                // tObj[2] => object class name of the attribute,
                // tObj[3] => instance class name
                // tObj[4] => ID of the object class to handle,
                // tObj[5] => ID of the attribute to handle

                // tObj[0 .. 3] are extracted from the Ptolemy model.
                // tObj[3 .. 5] are provided by the RTI (CERTI).

                // All these information are required to publish/unpublish
                // updated value of a HLA attribute.
                elt.setValue(new Object[] {
                        _getPortFromTab(tObj),
                        _getTypeFromTab(tObj),
                        _getClassObjectNameFromTab(tObj),
                        _getClassInstanceNameFromTab(tObj),
                        classHandle,
                        attributeHandle });
            }

            System.out.println("ciele_debug: setupHlaPublishers: classInstanceName list: step 2.1");

            // 2.1 Create a table of HlaPublishers indexed by their corresponding
            //     classInstanceName (no duplication).
            HashMap<String, LinkedList<String>> classInstanceNameHlaPublisherTable = new HashMap<String, LinkedList<String>>();

            Iterator<Entry<String, Object[]>> it21 = _hlaAttributesToPublish.entrySet().iterator();

            while (it21.hasNext()) {
                Map.Entry<String, Object[]> elt = it21.next();
                // elt.getKey()   => HlaPublisher actor full name.
                // elt.getValue() => tObj[] array.

                Object[] tObj = elt.getValue();

                // The classInstanceName where the HLA attribute belongs to (see FOM).
                //String classHandleName = _getClassNameFromTab(tObj);
                String classInstanceName = _getClassInstanceNameFromTab(tObj);

                if (classInstanceNameHlaPublisherTable.containsKey(classInstanceName)) {
                    classInstanceNameHlaPublisherTable.get(classInstanceName).add(elt.getKey());
                } else {
                    LinkedList<String> list = new LinkedList<String>();
                    list.add(elt.getKey());
                    classInstanceNameHlaPublisherTable.put(classInstanceName, list);
                }
            }

            System.out.println("ciele_debug: setupHlaPublishers: classHandle list: step 2.2");

            // 2.2 Create a table of HlaPublishers indexed by their corresponding
            //     classInstanceName (no duplication).
            HashMap<Integer, LinkedList<String>> classHandleHlaPublisherTable = new HashMap<Integer, LinkedList<String>>();

            Iterator<Entry<String, Object[]>> it22 = _hlaAttributesToPublish.entrySet().iterator();

            while (it22.hasNext()) {
                Map.Entry<String, Object[]> elt = it22.next();
                // elt.getKey()   => HlaPublisher actor full name.
                // elt.getValue() => tObj[] array.

                Object[] tObj = elt.getValue();

                // The classInstanceName where the HLA attribute belongs to (see FOM).
                //String classHandleName = _getClassNameFromTab(tObj);
                int classHandle = _getClassHandleFromTab(tObj);

                if (classHandleHlaPublisherTable.containsKey(classHandle)) {
                    classHandleHlaPublisherTable.get(classHandle).add(elt.getKey());
                } else {
                    LinkedList<String> list = new LinkedList<String>();
                    list.add(elt.getKey());
                    classHandleHlaPublisherTable.put(classHandle, list);
                }
            }

            System.out.println("ciele_debug: setupHlaPublishers: step 3");

            // 3. Declare to the Federation the HLA attributes to publish. If
            // these attributes belongs to the same object class then only
            // one publishObjectClass() call is performed.
            Iterator<Entry<Integer, LinkedList<String>>> it3 = classHandleHlaPublisherTable
                    .entrySet().iterator();

            while (it3.hasNext()) {
                Map.Entry<Integer, LinkedList<String>> elt = it3.next();
                // elt.getKey()   => HLA class instance name.
                // elt.getValue() => list of HlaPublisher actor full names.
                LinkedList<String> hlaPublishersFullnames = elt.getValue();

                // XXX: FIXME: GiL: optimize as we already call to get classHandle?
                //int classHandle = rtia.getObjectClassHandle(elt.getKey());

                // The attribute handle set to declare all attributes to publish
                // for one object class.
                AttributeHandleSet _attributesLocal = RtiFactoryFactory
                        .getRtiFactory().createAttributeHandleSet();

                // Fill the attribute handle set with all attribute to publish.
                for (String sPub : hlaPublishersFullnames) {
                    _attributesLocal.add(_getAttributeHandleFromTab(_hlaAttributesToPublish.get(sPub)));
                }

                // At this point, all HlaPublishers have been initialized and own their
                // corresponding HLA class handle and HLA attribute handle. Just retrieve
                // the first from the list to get those information.
                Object[] tObj = _hlaAttributesToPublish.get(hlaPublishersFullnames.getFirst());
                int classHandle = _getClassHandleFromTab(tObj);

                // Declare to the Federation the HLA attribute(s) to publish.
                try {
                    rtia.publishObjectClass(classHandle, _attributesLocal);
                    System.out.println("setupHlaPublishers: publish classHandle = " + classHandle + " attributesLocal = " + _attributesLocal.toString());
                } catch (OwnershipAcquisitionPending e) {
                    System.out.println("setupHlaPublishers: exception OwnershipAcquisitionPending " + e.getMessage());
                    e.printStackTrace();
                }
            }

            // 4. Register object instances. Only one registerObjectInstance() call is performed
            // by class instance (name). Finally, update the hash map of class instance name and
            // with the returned object instance ID.
            Iterator<Entry<String, LinkedList<String>>> it4 = classInstanceNameHlaPublisherTable
                    .entrySet().iterator();

            while (it4.hasNext()) {
                Map.Entry<String, LinkedList<String>> elt = it4.next();
                // elt.getKey()   => HLA class instance name.
                // elt.getValue() => list of HlaPublisher actor full names.
                LinkedList<String> hlaPublishersFullnames = elt.getValue();

                // At this point, all HlaPublishers on the list have been initialized
                // and own their class handle and class instance name. Just retrieve
                // the first from the list to get those information.
                Object[] tObj = _hlaAttributesToPublish.get(hlaPublishersFullnames.getFirst());

                int classHandle = _getClassHandleFromTab(tObj);

                // _federateName is used to do deal with the fact we might run
                // several federates from different threads (instead of processes
                // as it should be) then we end up with some attributes being
                // not owned because another object with the same name as already
                // been registered from another thread. Since _federateName
                // are unique across a federation, its a good workaround.

                // XXX: FIXME: rename senderName by "class instance name"
                //String senderName = _federateName + "%%" + _getClassInstanceNameFromTab(tObj);
                String classInstanceName = _getClassInstanceNameFromTab(tObj);

                if (!_registerObjectInstanceMap.containsKey(classInstanceName)) {
                    int objectInstanceId = -1;
                    try {
                        objectInstanceId = rtia.registerObjectInstance(classHandle, classInstanceName);

                        System.out.println("setupHlaPublishers: register object instance:"
                                + " classHandle = " + classHandle
                                + " classIntanceName = " + classInstanceName
                                + " objectInstanceId = " + objectInstanceId);

                        _registerObjectInstanceMap.put(classInstanceName, objectInstanceId);
                    } catch (ObjectClassNotPublished e) {
                        e.printStackTrace();
                    } catch (ObjectAlreadyRegistered e) {
                        e.printStackTrace();
                    } // end catch ...
                } // end if ...
            }
        }

        /**
         * Configure the different HLASubscribers (ie will make them subscribe
         * to what they should)
         */
        private void setupHlaSubscribers(RTIambassador rtia) throws NameNotFound,
        ObjectClassNotDefined, FederateNotExecutionMember,
        RTIinternalError, AttributeNotDefined, SaveInProgress,
        RestoreInProgress, ConcurrentAccessAttempted {
            // XXX: FIXME: check mixing betwen tObj[] and HlaSubcriber getter/setter.

            // For each HlaSubscriber actors deployed in the model we declare
            // to the HLA/CERTI Federation a HLA attribute to subscribe to.

            System.out.println("ciele_debug: setupHlaSubscribers: step 1");

            // 1. Get classHandle and attributeHandle IDs for each attribute
            // value to subscribe (i.e. HlaSubcriber). Update the HlaSubcribers.
            Iterator<Entry<String, Object[]>> it1 = _hlaAttributesToSubscribeTo
                    .entrySet().iterator();

            while (it1.hasNext()) {
                Map.Entry<String, Object[]> elt = it1.next();
                // elt.getKey()   => HlaSubscriber actor full name.
                // elt.getValue() => tObj[] array.
                Object[] tObj = elt.getValue();

                // Get corresponding HlaSubcriber actor.
                HlaSubscriber sub = (HlaSubscriber) ((TypedIOPort) tObj[0]).getContainer();

                // Object class handle and object attribute handle are IDs that
                // allow to identify an HLA attribute.

                // Retrieve HLA class handle from RTI.
                int classHandle = Integer.MIN_VALUE;
                try {
                    System.out.println("setupHlaSubscribers: objectClassName (in FOM) = " + (String) tObj[2]);
                    classHandle = rtia.getObjectClassHandle(_getClassObjectNameFromTab(tObj));
                    System.out.println("setupHlaSubscribers: classHandle = " + classHandle);
                } catch (Exception e) {
                    // XXX: FIXME: GiL: so what???
                    System.out.println("setupHlaSubscribers: attempt to reference a HLA class not in FOM (see fed file), so just skip it.");
                    continue;
                }

                int attributeHandle = Integer.MIN_VALUE;
                try {
                    System.out.println("setupHlaSubscribers: container name = " + ((HlaSubscriber)((TypedIOPort) tObj[0]).getContainer()).getFullName());
                    System.out.println("setupHlaSubscribers: object name = " + ((HlaSubscriber)((TypedIOPort) tObj[0]).getContainer()).getAttributeName());

                    attributeHandle = rtia.getAttributeHandle(sub.getAttributeName(), classHandle);

                    System.out.println("setupHlaSubscribers: objAttributeHandle = " + attributeHandle);
                } catch (Exception e) {
                    // XXX: FIXME: GiL: so what???
                    System.out.println("setupHlaSubscribers: getAttributeHandle() " + e.getMessage());
                    System.out.println("setupHlaSubscribers: getAttributeHandle() failed");
                }

                // Subscribe to HLA attribute information (for subscription)
                // from HLA services. In this case, the tObj[] object as
                // the following structure:
                // tObj[0] => input port which receives the token to transform
                //            as an updated value of a HLA attribute,
                // tObj[1] => type of the port (e.g. of the attribute),
                // tObj[2] => object class name of the attribute,
                // tObj[3] => instance class name
                // tObj[4] => ID of the object class to handle,
                // tObj[5] => ID of the attribute to handle

                // tObj[0 .. 3] are extracted from the Ptolemy model.
                // tObj[3 .. 5] are provided by the RTI (CERTI).

                // All these information are required to subscribe/unsubscribe
                // updated value of a HLA attribute.
                elt.setValue(new Object[] {
                        _getPortFromTab(tObj),
                        _getTypeFromTab(tObj),
                        _getClassObjectNameFromTab(tObj),
                        _getClassInstanceNameFromTab(tObj),
                        classHandle,
                        attributeHandle 
                });

                sub.setClassHandle(classHandle);
                sub.setAttributeHandle(attributeHandle);
            }

            System.out.println("ciele_debug: setupHlaSubscribers: step 2.1");

            // 2.1 Create a table of HlaSubscribers indexed by their corresponding
            //     instance class name (no duplication).
            HashMap<String, LinkedList<String>> classInstanceNameHlaSubscriberTable = null;
            classInstanceNameHlaSubscriberTable = new HashMap<String, LinkedList<String>>();

            Iterator<Entry<String, Object[]>> it21 = _hlaAttributesToSubscribeTo
                    .entrySet().iterator();

            while (it21.hasNext()) {
                Map.Entry<String, Object[]> elt = it21.next();
                // elt.getKey()   => HlaSubscriber actor full name.
                // elt.getValue() => tObj[] array.
                Object[] tObj = elt.getValue();

                // The classInstanceName where the HLA attribute belongs to (see FOM).
                String classInstanceName = _getClassInstanceNameFromTab(tObj);

                if (classInstanceNameHlaSubscriberTable.containsKey(classInstanceName)) {
                    classInstanceNameHlaSubscriberTable.get(classInstanceName).add(
                            elt.getKey());
                } else {
                    LinkedList<String> list = new LinkedList<String>();
                    list.add(elt.getKey());
                    classInstanceNameHlaSubscriberTable.put(classInstanceName, list);
                }
            }

            System.out.println("ciele_debug: setupHlaSubscribers: step 2.2");

            // 2.2 Create a table of HlaSubscribers indexed by their corresponding
            //     class handle (no duplication).
            HashMap<Integer, LinkedList<String>> classHandleHlaSubscriberTable = null;
            classHandleHlaSubscriberTable = new HashMap<Integer, LinkedList<String>>();

            Iterator<Entry<String, Object[]>> it22 = _hlaAttributesToSubscribeTo
                    .entrySet().iterator();

            while (it22.hasNext()) {
                Map.Entry<String, Object[]> elt = it22.next();
                // elt.getKey()   => HlaSubscriber actor full name.
                // elt.getValue() => tObj[] array.
                Object[] tObj = elt.getValue();

                // The classInstanceName where the HLA attribute belongs to (see FOM).
                int classHandle = _getClassHandleFromTab(tObj);

                if (classHandleHlaSubscriberTable.containsKey(classHandle)) {
                    classHandleHlaSubscriberTable.get(classHandle).add(
                            elt.getKey());
                } else {
                    LinkedList<String> list = new LinkedList<String>();
                    list.add(elt.getKey());
                    classHandleHlaSubscriberTable.put(classHandle, list);
                }
            }

            System.out.println("ciele_debug: setupHlaSubscribers: step 3");

            // 3. Declare to the Federation the HLA attributes to subscribe to.
            // If these attributes belongs to the same object class then only
            // one subscribeObjectClass() call is performed.
            Iterator<Entry<Integer, LinkedList<String>>> it3 = classHandleHlaSubscriberTable
                    .entrySet().iterator();

            while (it3.hasNext()) {
                Map.Entry<Integer, LinkedList<String>> elt = it3.next();
                // elt.getKey()   => HLA class instance name.
                // elt.getValue() => list of HlaSubscriber actor full names.
                LinkedList<String> hlaSubscribersFullnames = elt.getValue();

                // XXX: FIXME: GiL: optimize as we already get the objectClassHandle ?
                //System.out.println("setupHlaSubscribers: elt.getKey() = " + elt.getKey());
                //int classHandle = rtia.getObjectClassHandle(elt.getKey());

                // The attribute handle set to declare all subscribed attributes
                // for one object class.
                AttributeHandleSet _attributesLocal = RtiFactoryFactory
                        .getRtiFactory().createAttributeHandleSet();

                for (String sSub : hlaSubscribersFullnames) {
                    //_attributesLocal.add((Integer) _hlaAttributesToSubscribeTo.get(sSub)[4]);
                    _attributesLocal.add(_getAttributeHandleFromTab(_hlaAttributesToSubscribeTo.get(sSub)));
                }

                // At this point, all HlaSubscribers have been initialized and own their
                // corresponding HLA class handle and HLA attribute handle. Just retrieve
                // the first from the list to get those information.
                Object[] tObj = _hlaAttributesToSubscribeTo.get(hlaSubscribersFullnames.getFirst());
                int classHandle = _getClassHandleFromTab(tObj);

                _rtia.subscribeObjectClassAttributes(classHandle, _attributesLocal);
                System.out.println("setupHlaSubscribers: subscribeObjectClassAttributes: register classHandle = " + classHandle + " attributesLocal = " + _attributesLocal.toString());
            }
            /*
            // 3. Declare to the Federation the HLA attributes to subscribe to.
            // If these attributes belongs to the same object class then only
            // one subscribeObjectClass() call is performed.
            Iterator<Entry<String, LinkedList<String>>> it3 = classInstanceNameHlaSubscriberTable
                    .entrySet().iterator();

            while (it3.hasNext()) {
                Map.Entry<String, LinkedList<String>> elt = it3.next();
                // elt.getKey()   => HLA class instance name.
                // elt.getValue() => list of HlaSubscriber actor full names.
                LinkedList<String> hlaSubscribersFullnames = elt.getValue();

                // XXX: FIXME: GiL: optimize as we already get the objectClassHandle ?
                //System.out.println("setupHlaSubscribers: elt.getKey() = " + elt.getKey());
                //int classHandle = rtia.getObjectClassHandle(elt.getKey());

                // The attribute handle set to declare all subscribed attributes
                // for one object class.
                AttributeHandleSet _attributesLocal = RtiFactoryFactory
                        .getRtiFactory().createAttributeHandleSet();

                for (String sSub : hlaSubscribersFullnames) {
                    //_attributesLocal.add((Integer) _hlaAttributesToSubscribeTo.get(sSub)[4]);
                    _attributesLocal.add(_getAttributeHandleFromTab(_hlaAttributesToSubscribeTo.get(sSub)));
                }

                // At this point, all HlaSubscribers have been initialized and own their
                // corresponding HLA class handle and HLA attribute handle. Just retrieve
                // the first from the list to get those information.
                Object[] tObj = _hlaAttributesToSubscribeTo.get(hlaSubscribersFullnames.getFirst());
                int classHandle = _getClassHandleFromTab(tObj);

                _rtia.subscribeObjectClassAttributes(classHandle, _attributesLocal);
                System.out.println("setupHlaSubscribers: subscribeObjectClassAttributes: register classHandle = " + classHandle + " attributesLocal = " + _attributesLocal.toString());
            }
             */
        } // end 'private void setupHlaSubscribers(RTIambassador rtia) ...'

    } // end 'private class PtolemyFederateAmbassadorInner extends NullFederateAmbassador { ...'
}
