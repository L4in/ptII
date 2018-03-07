/* This class implements an analysis reporter for the HLA/CERTI framework.

@Copyright (c) 2017 The Regents of the University of California.
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
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;

import certi.rti.impl.CertiLogicalTime;

import ptolemy.actor.util.Time;
import ptolemy.data.Token;
import ptolemy.kernel.util.IllegalActionException;

///////////////////////////////////////////////////////////////////
//// HlaReporter

/**
 *  This class implements a HLA Reporter which collects and writes several
 *  statistics to analyze the correct usage of HLA services.
 * 
 *  TBC: this is a simple reformatting of the functions used to write the
 *  HLA analysis reports. The usage of StringBuffer[] must be removed.
 *  
 *  @author Gilles Lasnier, Tarciana Cabral de Brito Guerra
 *  @version $Id$
 *  @since Ptolemy II 10.0
 *
 *  @Pt.ProposedRating Yellow (glasnier)
 *  @Pt.AcceptedRating Red (glasnier)
 */
public class HlaReporter {

    /** Construct a HLA analysis reporter object instance.
     *  @throws IOException 
     */
    public HlaReporter(String folderName, String federateName, String modelName) throws IOException
    {
        // Set model filename.
        _modelName = modelName;
        _modelFileName = modelName + _FILE_EXT_XML;

        // Get current system date.
        _date = new Date();

        // Format current system date.
        DateFormat yearDateFormat = new SimpleDateFormat("yyyyMMdd");

        // Files and folder creation. 'folderName' will be append to 'user.home' directory.
        _reportsFolder = createFolder(folderName + "/" + yearDateFormat.format(_date).toString() + "/" + modelName);

        // Create textual data file.
        _txtFile = createTextFile(federateName + "-HLA-report" + _FILE_EXT_TXT);

        // Create CSV data file.
        _csvFile = createTextFile(federateName + "-HLA-report" + _FILE_EXT_CSV);

        // Write date to files.
        writeToTextFile(_date.toString());
        writeToCsvFile( _date.toString());
    }

    ///////////////////////////////////////////////////////////////////
    ////                         public methods                    ////
    
    /** Calculate the duration of the execution of the federation. */
    public void calculateRuntime() {
        double duration = System.nanoTime() - _startTime;
        duration = duration / (Math.pow(10, 9));
        _runtime = duration;
    }
    
    /** Display some analysis results as string. */
    public String displayAnalysisValues() {
        calculateRuntime();

        return "HLA analysis report:"
        + "\n number of TARs: " + _numberOfTARs
        + "\n number of NERs: " + _numberOfNERs 
        + "\n number of TAGs: " + _numberOfTAGs
        + "\n number of Ticks2: " + _numberOfTicks2
        + "\n number of (other) Ticks: " +_numberOfOtherTicks
        + "\n number of Ticks during TAR/TAG or NER/TAG:" + _numberOfTicks.toString()
        + "\n number of delays between TAR/TAG or NER/TAG:" + _TAGDelay.toString()
        + "\n number of UAVs:" + _numberOfUAVs
        + "\n number of RAVs:" + _numberOfRAVs
        + "\n runtime:        " + _runtime;
    }

    /** Increment the counter of NERs. */
    public void incrNumberOfNERs() {
        _numberOfNERs++;
    }
    
    /** Increment the counter of RAV. */
    public void incrNumberOfRAVs() {
        _numberOfRAVs++;
    }
    
    /** Increment the counter of UAV. */
    public void incrNumberOfUAVs() {
        _numberOfUAVs++;
    }
    
    /** Increment the counter of TARs. */
    public void incrNumberOfTARs() {
        _numberOfTARs++;
    }
    
    /** Initialize variables specific to HLA attribute value publication.
     *  @param hlaAttributesToPublish the HashMap of HlaPublisher names and instances.
     */
    public void initializeAttributesToPublishVariables(HashMap<String, Object[]> hlaAttributesToPublish) {
        // XXX: FIXME: to improve, instead of using String[] and StringBuffer[] just set a
        // HashMap<String, Object[]> where the key is the HlaPublisher fullName.

        // XXX: FIXME: to replace, _nameOfTheAttributesToPublish => _hlaPublisherFullname,
        //                         _numberOfAttributesToPublish => _numberOfHlaPublisher
        _numberOfAttributesToPublish = hlaAttributesToPublish.size();

        _nameOfTheAttributesToPublish = new String[_numberOfAttributesToPublish];

        _UAVsValues = new StringBuffer[_numberOfAttributesToPublish];

        Object attributesToPublish[] = hlaAttributesToPublish.keySet().toArray();
        for (int i = 0; i < _numberOfAttributesToPublish; i++) {
            // hlaAttributesToPublish is a HashMap where the key is the HlaPublisher fullName, 
            // so toString() prints the HlaPublisher fullName.
            _nameOfTheAttributesToPublish[i] = attributesToPublish[i].toString();
            _UAVsValues[i] = new StringBuffer("");
        }

    }

    /** Initialize variables specific to HLA attribute value subscription.
     *  @param hlaAttributesSubscribedTo the HashMap of HlaSubcribers names and instances.
     */
    public void initializeAttributesSubscribedToVariables(HashMap<String, Object[]> hlaAttributesSubscribedTo) {
        // XXX: FIXME: to improve, instead of using String[] and StringBuffer[] just set a
        // HashMap<String, Object[]> where the key is the HlaSubscrbier fullName.

        // XXX: FIXME: GiL: to replace, _nameOfTheAttributesSubscribedTo => _hlaSubscriberFullname,
        //                              _numberOfAttributesSubscribedTo => _numberOfHlaSubscriber
        _numberOfAttributesSubscribedTo = hlaAttributesSubscribedTo.size();
        _nameOfTheAttributesSubscribedTo = new String[_numberOfAttributesSubscribedTo];


        _RAVsValues = new StringBuffer[_numberOfAttributesSubscribedTo];

        Object attributesSubscribedTo[] = hlaAttributesSubscribedTo.keySet().toArray();
        for (int i = 0; i < _numberOfAttributesSubscribedTo; i++) {
            // hlaAttributesToPublish is a HashMap where the key is the HlaPublisher fullName, so
            // toString() will print the HlaPublisher fullName.
            _nameOfTheAttributesSubscribedTo[i] = attributesSubscribedTo[i].toString();
            _RAVsValues[i] = new StringBuffer("");
        }
    }

    /** Initialize all variables used in reports. */
    public void initializeReportVariables(double hlaLookAHead, 
            double hlaTimeStep, 
            double hlaTimeUnitValue, 
            double startTime,
            Time stopTime, 
            String federateName, 
            String fedFilePath,
            Boolean isCreator, 
            Boolean timeStepped, 
            Boolean eventBased) {
        _hlaLookAHead = hlaLookAHead;
        _hlaTimeStep = hlaTimeStep;
        _stopTime = stopTime;
        _startTime = startTime;
        _federateName = federateName;
        _fedFilePath = fedFilePath;
        _hlaTimeUnitValue = hlaTimeUnitValue;

        _isCreator = isCreator;
        _timeStepped = timeStepped;
        _eventBased = eventBased;

        _numberOfTARs       = 0;
        _numberOfTicks2     = 0;
        _numberOfOtherTicks = 0;
        _numberOfNERs       = 0;
        _numberOfTAGs       = 0;

        _runtime        = 0;

        _timeOfTheLastAdvanceRequest = 0;

        _tPTII = new StringBuffer("");
        _tHLA = new StringBuffer("");

        _reasonsToPrintTheTime = new StringBuffer("");

        _pUAVsTimes = new StringBuffer("");
        _preUAVsTimes = new StringBuffer("");

        _pRAVsTimes = new StringBuffer("");
        _folRAVsTimes = new StringBuffer("");

        _TAGDelay = new ArrayList<Double>();

        _numberOfTicks = new ArrayList<Integer>();
        _numberOfRAVs = 0;
        _numberOfUAVs = 0;
    }

    /** This method updates the _folRAVsTimes (StringBuffer) with the RAV's timestamp value.
     *  @param ravTimeStamp The RAV timestamp.
     */
    public void updateFolRAVsTimes(Time ravTimeStamp) {
      if ( _folRAVsTimes.indexOf("*") >= 0) {
      _folRAVsTimes.replace(_folRAVsTimes.indexOf("*"), _folRAVsTimes.length() - (_folRAVsTimes.length() - (_folRAVsTimes.indexOf("*") + 1)) , ravTimeStamp + ";");
      }
    }

    /** This method records in StringBuffer arrays all information about RAVs
     *  during the simulation execution.
     *  @param hs HlaSubscriber actor which has received the RAV.
     *  @param te HLA timed event associated to the RAV in Ptolemy's DE domain.
     *  @param hlaAttributesToSubscribeTo List of HlaSubscribers.
     *  @param value The HLA update attribute value received.
     *  NOTE: this method is responsible of the setup of all variable for the RAVs reporting part.
     */
    public void updateRAVsInformation(HlaSubscriber hs, HlaTimedEvent te, 
            HashMap<String, Object[]> hlaAttributesToSubscribeTo, Object value) {
        String hlaSubscriberName = hs.getFullName();

        String pRAVTimeStamp = te.timeStamp.toString() + ";";

        /*if (_numberOfRAVs > 0 
                && (_pRAVsTimes.length() - _pRAVsTimes.lastIndexOf(pRAVTimeStamp)) == pRAVTimeStamp.length()) {
            
            System.out.println("HLA REPORTER ========== _pRAVsTimes.length()=" + _pRAVsTimes.length());
            System.out.println("HLA REPORTER ========== _pRAVsTimes.lastIndexOf(pRAVTimeStamp)=" + _pRAVsTimes.lastIndexOf(pRAVTimeStamp));
            System.out.println("HLA REPORTER ========== _pRAVsTimes.length() - _pRAVsTimes.lastIndexOf(pRAVTimeStamp)=" + (_pRAVsTimes.length() - _pRAVsTimes.lastIndexOf(pRAVTimeStamp)));
            System.out.println("HLA REPORTER ========== pRAVTimeStamp.length()=" + pRAVTimeStamp.length());

            int indexOfAttribute = 0;

            for (int j = 0; j < _numberOfAttributesSubscribedTo; j++) {
                if (hlaSubscriberName.equals(_nameOfTheAttributesSubscribedTo[j])) {
                    indexOfAttribute = j;
                    break;
                }  
            }

            System.out.println("HLA REPORTER ========== before _RAVsValues[indexOfAttribute]= " + _RAVsValues[indexOfAttribute]);
            _RAVsValues[indexOfAttribute].replace(_RAVsValues[indexOfAttribute].length() - 2, _RAVsValues[indexOfAttribute].length(), value.toString() + ";");
            System.out.println("HLA REPORTER ========== after _RAVsValues[indexOfAttribute]= " + _RAVsValues[indexOfAttribute]);


        } else {*/
            if (_numberOfRAVs < 1) {
                // Initialize RAVs data structures.
                initializeAttributesSubscribedToVariables(hlaAttributesToSubscribeTo);
            }
            int indexOfAttribute = 0;

            for (int j = 0; j < _numberOfAttributesSubscribedTo; j++) {
                if (hlaSubscriberName.equals(_nameOfTheAttributesSubscribedTo[j])) {
                    indexOfAttribute = j;
                    break;
                }
            }

            _folRAVsTimes.append("*");
            _pRAVsTimes.append(pRAVTimeStamp);

            for (int j = 0; j < _numberOfAttributesSubscribedTo; j++) {
                if (j == indexOfAttribute) {
                    _RAVsValues[j].append(value.toString() + ";");
                } else {
                    _RAVsValues[j].append("-;");
                }
            }
        //}
    }

    /** This method records in StringBuffer arrays all information about UAVs
     *  during the simulation execution.
     *  @param hp HlaPublisher actor responsible of the UAV.
     *  @param in The emitted token.
     *  @param hlaTime HLA logical time as Ptolemy's time.
     *  @param ptTime PTII time as Ptolemy's time.
     *  @param microstep The current DE director's microstep.
     *  @param uavTimeStamp The timestamp of the UAV.
     *  @throws IllegalActionException If the HlaPublisher attribute name is not retrieved.
     */
    public void updateUAVsInformation(HlaPublisher hp, Token in, Time hlaTime, Time ptTime, int microstep, CertiLogicalTime uavTimeStamp) throws IllegalActionException {
        String hlaPublisherName = hp.getFullName();

        int attributeIndex = 0;
        for (int i = 0; i < _numberOfAttributesToPublish; i++) {
            if (hlaPublisherName.equals(_nameOfTheAttributesToPublish[i])) {
                attributeIndex = i;
                break;
            }
        }

        String pUAVTimeStamp = uavTimeStamp.getTime() + ";";
        String preUAVTimeStamp = "(" + ptTime + "," + microstep + ");";
        storeTimes("UAV " + hlaPublisherName + "." + hp.getAttributeName(), hlaTime, ptTime);

        if (_numberOfUAVs > 0
                && (_preUAVsTimes.length() - _preUAVsTimes.lastIndexOf(preUAVTimeStamp)) == preUAVTimeStamp.length()
                && (_preUAVsTimes.length() - _preUAVsTimes.lastIndexOf(pUAVTimeStamp)) == pUAVTimeStamp.length()) {

            // 'in' is the Token.
            _UAVsValues[attributeIndex].replace(_UAVsValues[attributeIndex].length() - 2, _UAVsValues[attributeIndex].length(), in.toString() + ";");
        } else {
            _preUAVsTimes.append(preUAVTimeStamp);
            _pUAVsTimes.append(pUAVTimeStamp);

            for (int i = 0; i < _numberOfAttributesToPublish; i++) {
                if (i == attributeIndex) {
                    _UAVsValues[i].append(in.toString() + ";");
                } else {
                    _UAVsValues[i].append("-;");
                }
            }
        }
    }

    /** Set the time of the last advance request call. */
    public void setTimeOfTheLastAdvanceRequest(long value) {
        _timeOfTheLastAdvanceRequest = value;
    }

    /** Get the time of the last advance request call.  */
    public double getTimeOfTheLastAdvanceRequest() {
        return _timeOfTheLastAdvanceRequest;
    }
 
    /** This method records the PTII time and the HLA time as Ptolemy's time.
     *  @param reason Reason to store the time values.
     *  @param hlaCurrentTime HLA logical time as Ptolemy's time.
     *  @param directorTime PTII time as Ptolemy's time.
     */
    public void storeTimes(String reason, Time hlaCurrentTime, Time ptolemyCurrentTime) {
        _tPTII.append(ptolemyCurrentTime.getDoubleValue() + ";");
        _tHLA.append(hlaCurrentTime.getDoubleValue() + ";");
        _reasonsToPrintTheTime.append(reason + ";");
    }

    /** Write a report containing(in a .csv file {@link #_csvFile}), among other informations,
     *  the number of ticks, the delay between a NER or a TAR and its respective TAG, the number of UAVs and RAVs.
     */
    public void writeDelays() {
        StringBuffer info = new StringBuffer("\nFederate: " + _federateName + ";in the model:;" + _modelFileName);

        info.append("\nhlaTimeUnit: ;" + _hlaTimeUnitValue + ";lookAhead: ;" + _hlaLookAHead + ";runtime: ;" + _runtime);

        info.append("\nApproach:;");

        if (_timeStepped) {
            info.append("TAR;Time step:;" + _hlaTimeStep + ";Number of TARs:;" + _numberOfTARs + "\n");
        } else if (_eventBased) {
            info.append("NER;Number of NERs:;" + _numberOfNERs + "\n");
        }

        info.append("Number of UAVs:;" + _numberOfUAVs + ";Number of RAVs:;" + _numberOfRAVs + ";Number of TAGs:;" + _numberOfTAGs);

        String strNumberOfTicks = "\nNumber of ticks:;";
        String strDelay = "\nDelay :;";

        double averageNumberOfTicks = 0;
        double averageDelay = 0;

        String strDelayPerTick = "\nDelay per tick;";

        StringBuffer header = new StringBuffer("\nInformation :;");

        for (int i = 0; i < _numberOfTAGs; i++) {
            if (i < 10) {
                header.append((i + 1) + ";");
                strNumberOfTicks = strNumberOfTicks + _numberOfTicks.get(i) + ";";
                strDelay = strDelay + _TAGDelay.get(i) + ";";
                if (_numberOfTicks.get(i) > 0) {
                    strDelayPerTick = strDelayPerTick
                            + (_TAGDelay.get(i) / _numberOfTicks.get(i))
                            + ";";
                } else {
                    strDelayPerTick = strDelayPerTick + "0;";
                }
            }
            averageNumberOfTicks = averageNumberOfTicks + _numberOfTicks.get(i);
            averageDelay = averageDelay + _TAGDelay.get(i);
        }
        header.append("Sum;");
        int totalNumberOfHLACalls = _numberOfOtherTicks
                + (int) averageNumberOfTicks + _numberOfTARs + _numberOfNERs
                + _numberOfRAVs + _numberOfUAVs + _numberOfTAGs;
        strNumberOfTicks = strNumberOfTicks + averageNumberOfTicks + ";";
        strDelay = strDelay + averageDelay + ";";
        strDelayPerTick = strDelayPerTick + ";";
        header.append("Average;");
        if (_timeStepped) {
            // FIXME: XXX: GiL: check if _reportFile is used in an other part ?
            _reportFile = createTextFile(
                    _federateName + "-TAR" + _FILE_EXT_CSV,
                    "date;timeStep;lookahead;runtime;total number of calls;TARs;TAGs;RAVs;UAVs;Ticks2;inactive Time");
            writeInTextFile(_reportFile,
                    _date + ";" + _hlaTimeStep + ";" + _hlaLookAHead + ";"
                            + _runtime + ";" + totalNumberOfHLACalls + ";"
                            + _numberOfTARs + ";" + _numberOfTAGs + ";"
                            + _numberOfRAVs + ";" + _numberOfUAVs + ";"
                            + _numberOfTicks2 + ";" + averageDelay);
        } else {
            _reportFile = createTextFile(
                    _federateName + "-NER" + _FILE_EXT_CSV,
                    "date;lookahead;runtime;total number of calls;NERs;TAGs;RAVs;UAVs;Ticks2;inactive Time");
            writeInTextFile(_reportFile,
                    _date + ";" + _hlaLookAHead + ";" + _runtime + ";"
                            + totalNumberOfHLACalls + ";" + _numberOfNERs
                            + ";" + _numberOfTAGs + ";" + _numberOfRAVs
                            + ";" + _numberOfUAVs + ";" + _numberOfTicks2
                            + ";" + averageDelay);
        }

        averageNumberOfTicks = averageNumberOfTicks / _numberOfTAGs;
        averageDelay = averageDelay / _numberOfTAGs;
        strDelayPerTick = strDelayPerTick + (averageDelay / averageNumberOfTicks)
                + ";";
        strNumberOfTicks = strNumberOfTicks + averageNumberOfTicks + ";";
        strDelay = strDelay + averageDelay + ";";

        info.append(header + strDelay + strNumberOfTicks
                + strDelayPerTick + "\nOther ticks:;" + _numberOfOtherTicks
                + "\nTotal number of HLA Calls:;" + totalNumberOfHLACalls);
        writeInTextFile(_csvFile, info.toString());
    }

    /** Write information in a '.txt' file.
     *  @param data The information you want to write.
     *  @param file The file in which you want to write.
     *  @return Return true if the information was successfully written in the file.
     */
    private boolean writeInTextFile(File file, String data) {

        boolean noExceptionOccured = true;

        BufferedWriter writer = null;

        try {
            writer = new BufferedWriter(new FileWriter(file, true));
            writer.write(data);
            writer.newLine();
            writer.flush();
        } catch (Exception e) {
            noExceptionOccured = false;
        } finally {
            if (writer != null) {
                try {
                    writer.close();
                } catch (IOException e) {
                    System.out.println("The file failed to be closed.");
                }
            }
        }

        return noExceptionOccured;
    }

    /** Write the number of HLA calls of each federate, along with information about the
     *  time step and the runtime, in a file. The name and location of this file are 
     *  specified in the initialization of the variable file.
     */
    public void writeNumberOfHLACalls() {
        // Get RKSolver value.
        String RKSolver = "<property name=\"ODESolver\" class=\"ptolemy.data.expr.StringParameter\" value=\"ExplicitRK";

        // FIXME: XXX: GiL: TO REWORK
        // Get FOM file.
        String folderPath = _fedFilePath.substring(0, _fedFilePath.lastIndexOf("/") + 1);

        File file = new File(folderPath + _modelFileName);

        // Create first line as StringBuffer
        // ex: Federate AutoPilot in the model f14AutoPilot.xml
        StringBuffer info = new StringBuffer("Federate " + _federateName + " in the model " + _modelFileName);

        // Add line
        // ex: RKSolver: UNDEFINED
        // ex: RKSolver: ExplicitRK23Solver
        try {
            RKSolver = AutomaticSimulation.findParameterValue(file, RKSolver);
            info.append("\nRKSolver: " + RKSolver);
        } catch (IllegalActionException e) {
            // FIXME: XXX: GiL: HANDLE EXCEPTION
            e.printStackTrace();
        }

        // Add line
        // ex: stopTime: 12.0    hlaTimeUnit: 1.0    lookAhead: 0.005
        info.append("\n" + "stopTime: " + _stopTime + "    hlaTimeUnit: " + _hlaTimeUnitValue + "    lookAhead: " + _hlaLookAHead);

        // Add information it is the synchronization point register
        if (_isCreator) {
            info = new StringBuffer("SP register -> " + info);
        }

        // Handle time stepped or event based time management information
        if (_timeStepped) {
            info.append("    Time Step: " + _hlaTimeStep + "\n" + "Number of TARs: " + _numberOfTARs);
        } else if (_eventBased) {
            info.append("\nNumber of NERs: " + _numberOfNERs);
        }

        // Add information UAV, TAGS, RAVS
        info.append("    Number of UAVs:" + _numberOfUAVs
                + "\nNumber of TAGs: " + _numberOfTAGs
                + "    Number of RAVs:" + _numberOfRAVs + "\n" + "Runtime: "
                + _runtime + "\n");

        // Write to file.
        writeInTextFile(_txtFile, info.toString());
    }

    /** Write all recorded RAV information to file. */
    public void writeRAVsInformation() {
        if (_numberOfRAVs > 0) {
            StringBuffer header = new StringBuffer("LookAhead;TimeStep;StopTime;Information;");
            int count = String.valueOf(_RAVsValues[0]).split(";").length;
            for (int i = 0; i < count; i++) {
                header.append("RAV" + i + ";");
            }

            StringBuffer info = new StringBuffer(
                    _date.toString() + "\n"
                            + header + "\n" 
                            + _hlaLookAHead + ";" + _hlaTimeStep + ";" + _stopTime + ";" + "pRAV TimeStamp:;" + _pRAVsTimes + "\n"
                            + ";;;" + "folRAV TimeStamp:;" + _folRAVsTimes + "\n");
            for (int i = 0; i < _numberOfAttributesSubscribedTo; i++) {
                info.append(";;;" + _nameOfTheAttributesSubscribedTo[i] + ";" + _RAVsValues[i] + "\n");
            }
            _RAVsValuesFile = createTextFile(_federateName + "-RAV" + _FILE_EXT_CSV);
            writeInTextFile(_RAVsValuesFile, info.toString());
        }
    }

    /** Write the time file to '...-times.csv' file. */
    public void writeTimes() {
        File timesFile = createTextFile(_federateName + "-times"+ _FILE_EXT_CSV);
        writeInTextFile(timesFile,
                _date 
                + ";Reason:;" + _reasonsToPrintTheTime
                + "\nt_ptII:;" + _tPTII 
                + "\nt_hla:;" + _tHLA);
    }
 
    /** Write all recorded UAV information to file. */
    public void writeUAVsInformation() {
        if (_numberOfUAVs > 0) {
            StringBuffer header = new StringBuffer("LookAhead;TimeStep;StopTime;Information;");
            int count = String.valueOf(_UAVsValues[0]).split(";").length;
            for (int i = 0; i < count; i++) {
                header.append("UAV" + i + ";");
            }
            StringBuffer info = new StringBuffer(
                    _date.toString() + "\n" 
                            + header + "\n" 
                            + _hlaLookAHead + ";" + _hlaTimeStep + ";" + _stopTime + ";" + "preUAV TimeStamp:;" + _preUAVsTimes + "\n" 
                            + ";;;" + "pUAV TimeStamp:;" + _pUAVsTimes + "\n");
            for (int i = 0; i < _numberOfAttributesToPublish; i++) {
                info.append(";;;" + _nameOfTheAttributesToPublish[i] + ";" + _UAVsValues[i] + "\n");
            }
            _UAVsValuesFile = createTextFile(_federateName + "-UAV" + _FILE_EXT_CSV);
            writeInTextFile(_UAVsValuesFile, info.toString());
        }
    }

    ///////////////////////////////////////////////////////////////////
    ////                         public  variables                 ////
    /** Represents the number of time advance grants this federate has received.
     *  Federates have to ask permission to the federation in order to advance in time.
     *  If there is no events happening in an inferior time to the proposed one, the
     *  federation sends a TAG to the federate and this last one advances in time.
     */
    public int _numberOfTAGs;
    
    /** Array that contains the delays between a NER or TAR and its respective TAG. */
    public ArrayList<Double> _TAGDelay;

    ///////////////////////////////////////////////////////////////////
    ////                         protected methods                 ////
    
    ///////////////////////////////////////////////////////////////////
    ////                         protected variables               ////
    
    ///////////////////////////////////////////////////////////////////
    ////                         private methods                   ////

    /** Verify the existence of a folder, if it doesn't exist, the function tries
     *  to create it.
     *  @param folderName The name of the folder that will be created.
     *  @return The full address of the folder in a string.
     *  @exception IOException If the folder cannot be created.
     */
    private String createFolder(String folderName) throws IOException {
        String homeDirectory = System.getProperty("user.home");
        folderName = homeDirectory + "/" + folderName;
        File folder = new File(folderName);
        if (!folder.exists()) {
            try {
                // Create multiple directories if needed.
                if (!folder.mkdirs()) {
                    throw new IOException(
                            "Failed to create" + folder + " directory.");
                } else {
                    System.out.println("Folder " + folderName + " created.");
                }
                return folderName;
            } catch (SecurityException se) {
                throw new IOException(
                        "Could not create the folder " + folderName + ".");
            }
        } else {
            return folderName;
        }
    }

    /** Associate the object file with a file in the computer, creating it, if it doesn't
     *  already exist.
     *  @param name Name of the file.
     *  @return The object file instance or null.
     */
    private File createTextFile(String name) {
        if (_reportsFolder != null) {
            name = _reportsFolder + "/" + name;
            if (name == null || name.length() < 3) {
                System.out.println("Choose a valid name for the txt file.");
                return null;
            } else {
                if (!(name.endsWith(_FILE_EXT_TXT) || name.endsWith(_FILE_EXT_CSV))) {
                    name = name.concat(_FILE_EXT_TXT);
                }
                try {
                    File file = new File(name);

                    boolean verify = false;
                    if (!file.exists()) {
                        verify = file.createNewFile();
                    } else {
                        verify = true;
                    }

                    if (!verify) {
                        throw new IOException("Cannot create the file.");
                    }
                    return file;
                } catch (IOException e) {
                    System.out.println("Cannot create the file.");
                    return null;
                }
            }
        } else {
            return null;
        }
    }

    /** Associate the object file with a file in the computer, creating it, if it doesn't
     *  already exist.
     *  @param name Name of the file.
     *  @param header File's header.
     *  @return The object file instance or null.
     */
    private File createTextFile(String name, String header) {
        if (_reportsFolder != null) {
            name = _reportsFolder + "/" + name;
            if (name == null || name.length() < 3) {
                System.out.println("Choose a valid name for the txt file.");
                return null;
            } else {
                if (!(name.endsWith(_FILE_EXT_TXT) || name.endsWith(_FILE_EXT_CSV))) {
                    name = name.concat(_FILE_EXT_TXT);
                }
                try {
                    File file = new File(name);
                    boolean verify = false;
                    if (!file.exists()) {
                        verify = file.createNewFile();
                        writeInTextFile(file, header);
                    } else {
                        verify = true;
                    }
                    if (!verify) {
                        throw new Exception();
                    }
                    System.out.println(name);
                    return file;
                } catch (Exception e) {
                    System.out.println("Couldn't create the file.");
                    return null;
                }
            }
        } else {
            return null;
        }
    }
    
    /** Write data to CSV file.
     *  @param data Data to write in file
     *  @return True if the data has been successfully written to the file, False otherwise.
     */
    private boolean writeToCsvFile(String data) {
        return writeInTextFile(_csvFile, data);
    }

    /** Write data to TXT file.
     *  @param data Data to write in file
     *  @return True if the data has been successfully written to the file, False otherwise.
     */
    private boolean writeToTextFile(String data) {
        return writeInTextFile(_txtFile, data);
    }

    ///////////////////////////////////////////////////////////////////
    ////                         private variables                 ////

    /** Represents the number of next event request this federate has made.
     *
     * Event driven federates advance to the time-stamp of the next event. In order to complete the
     * advancement, they have to ask the federation's permission to do so using a NER call.
     */
    private int _numberOfNERs;

    /** Represents the number of time advance requests(TAR) this federate 
     *  has made.
     *
     *  Time-stepped federates advance with a fixed step in time. In order
     *  to complete the advancement, they have to ask the federation's
     *  permission to do so using a TAR call.
     */
    private int _numberOfTARs;

    /** TBC. */
    private double _runtime;

    /** TBC. */
    public int _numberOfTicks2;

    /** TBC. */
    private String _reportsFolder;

    /** TBC. */
    private StringBuffer _tPTII;

    /** TBC. */
    private StringBuffer _tHLA;

    /** TBC. */
    private StringBuffer _reasonsToPrintTheTime;

    /** Represents a text '.txt' file that is going to keep track of the number of
     *  HLA calls of the federate. */
    private File _txtFile;

    /** Represents a '.csv' file that is going to keep track of the number of
     *  HLA calls of the federate (csv-compliant). */
    private File _csvFile;

    /** Represents a ".csv" file that is going to keep track of the number of
     *  HLA calls of the federate. */
    private File _reportFile;

    /** Represents the file that tracks the values that have been updated and
     *  the time of their update. */
    private File _UAVsValuesFile;

    /** TBC. */
    private Date _date;
    
    /** TBC. */
    private int _numberOfAttributesToPublish;

    /** TBC. */
    private String[] _nameOfTheAttributesToPublish;

    /** TBC. */
    private int _numberOfAttributesSubscribedTo;

    /** TBC. */
    private String[] _nameOfTheAttributesSubscribedTo;
    
    /** TBC. */
    private StringBuffer[] _UAVsValues;

    /** TBC. */
    private StringBuffer _pUAVsTimes;

    /** TBC. */
    private StringBuffer _preUAVsTimes;

    /** TBC. */
    private File _RAVsValuesFile;

    /** TBC. */
    private StringBuffer[] _RAVsValues;

    /** TBC. */
    private StringBuffer _pRAVsTimes;

    /** TBC. */
    private StringBuffer _folRAVsTimes;

    /** Represents the instant when the simulation is fully started
     *  (when the last federate starts running). */
    private static double _startTime;

    /** The time of the last TAR or last NER. */
    private double _timeOfTheLastAdvanceRequest;

    /** Array that contains the number of ticks between a NER or TAR and its respective TAG. */
    public ArrayList<Integer> _numberOfTicks;

    /** Represents the number of the ticks that were not considered in the variable {@link #_numberOfTicks} */
    public int _numberOfOtherTicks;

    /** Represents the number of received RAV. */
    private int _numberOfRAVs;

    /** Represents the number of received UAV. */
    private int _numberOfUAVs;

    /** The lookahead value of the Ptolemy Federate. */
    private Double _hlaLookAHead;

    /** Time step of the Ptolemy Federate. */
    private Double _hlaTimeStep;

    /** The simulation stop time. */
    private Time _stopTime;

    /** Name of the current model. */
    private String _modelName;

    /** FileName of the current model. */
    private String _modelFileName;

    /** Name of the current Ptolemy federate. */
    private String _federateName;

    /** Path as string of the FOM (.fed file). */
    private String _fedFilePath;

    /** The actual value for hlaTimeUnit parameter. */
    private double _hlaTimeUnitValue;

    /** Indicates if the Ptolemy Federate is the creator of the synchronization
     *  point. */
    private Boolean _isCreator;

    /** Indicates the use of the nextEventRequest() service. */
    private Boolean _eventBased;

    /** Indicates the use of the timeAdvanceRequest() service. */
    private Boolean _timeStepped;

    /** CSV file extension suffix. */
    private static final String _FILE_EXT_CSV = ".csv";

    /** TXT file extension suffix. */
    private static final String _FILE_EXT_TXT = ".txt";

    /** XML file extension suffix. */
    private static final String _FILE_EXT_XML = ".xml";
}
