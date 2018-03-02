/* This class implements an analysis reporter for the HLA/CERTI framwework.

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
import java.math.RoundingMode;
import java.text.DateFormat;
import java.text.DecimalFormat;
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
 * This class implements a HLA Reporter which collects and writes several
 * statistics to analyze the correct usage of HLA services.
 * 
 *  @author Gilles Lasnier
 *  @version $Id$
 *  @since Ptolemy II 10.0
 *
 *  @Pt.ProposedRating Yellow (glasnier)
 *  @Pt.AcceptedRating Red (glasnier)
 */
public class HlaReporter {

    /** Constructs a HLA analysis reporter.
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

    /** Initialize the variables that are going to be used to create the reports
     *  in the files {@link #_txtFile} and {@link #_csvFile}
     */
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

        _hlaAttributesUAV = new HashMap<String, Object[]>();
        _hlaAttributesRAV = new HashMap<String, Object[]>();
        //_nbrTicksByTags = new HashMap<String, Object[]>();

        // Define number format
        int numberOfDecimalDigits;
        if (_timeStepped) {
            System.out.println(_hlaTimeStep);
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
    }


    /**
     * 
     * @param hlaAttributesToPublish
     */
    public void initializeAttributesToPublishVariables(HashMap<String, Object[]> hlaAttributesToPublish) {
        // XXX: FIXME: GiL: to improve, instead of using String[] and StringBuffer[] just set a
        // HashMap<String, Object[]> where the key is the HLAPublisher fullName ?

        // XXX: FIXME: GiL: todo, _nameOfTheAttributesToPublish => _hlaPublisherFullname,
        //                        _numberOfAttributesToPublish => _numberOfHlaPublisher
        _numberOfAttributesToPublish = hlaAttributesToPublish.size();

        _nameOfTheAttributesToPublish = new String[_numberOfAttributesToPublish];

        _UAVsValues = new StringBuffer[_numberOfAttributesToPublish];

        Object attributesToPublish[] = hlaAttributesToPublish.keySet().toArray();
        for (int i = 0; i < _numberOfAttributesToPublish; i++) {
            // hlaAttributesToPublish is a HashMap where the key is the HlaPublisher fullName, so
            // toString() will print the HlaPublisher fullName.
            _nameOfTheAttributesToPublish[i] = attributesToPublish[i].toString();
            _UAVsValues[i] = new StringBuffer("");
        }

    }

    /**
     * 
     * @param hlaAttributesToPublish
     */
    public void initializeAttributesSubscribedToVariables(HashMap<String, Object[]> hlaAttributesSubscribedTo) {
        // XXX: FIXME: GiL: to improve, instead of using String[] and StringBuffer[] just set a
        // HashMap<String, Object[]> where the key is the HLAPublisher fullName ?

        // XXX: FIXME: GiL: todo, _nameOfTheAttributesSubscribedTo => _hlaSubscriberFullname,
        //                        _numberOfAttributesSubscribedTo => _numberOfHlaSubscriber
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

    /**
     * 
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

    /**
     * 
     */
    public void updateRAVsInformation(HlaSubscriber hs, HlaTimedEvent te, HashMap<String, Object[]> hlaAttributesToSubscribeTo, Object value) {
        String hlaSubscriberName = hs.getFullName();

        String pRAVTimeStamp = printTimes(te.timeStamp) + ";";

        if (_numberOfRAVs > 0 
                && (_pRAVsTimes.length() - _pRAVsTimes.lastIndexOf(pRAVTimeStamp)) == pRAVTimeStamp.length()) {
            System.out.println("DEBUG HLA-REPORTER: RAV: IF CASE : _numberOfRAVs=" + _numberOfRAVs);

            int indexOfAttribute = 0;

            for (int j = 0; j < _numberOfAttributesSubscribedTo; j++) {
                if (hlaSubscriberName.equals(_nameOfTheAttributesSubscribedTo[j])) {
                    indexOfAttribute = j;
                    break;
                }  
            }

            _RAVsValues[indexOfAttribute].replace(_RAVsValues[indexOfAttribute].length() - 2, _RAVsValues[indexOfAttribute].length(), value.toString() + ";");

        } else {
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
        }
    }

    /**
     * 
     */
    public void updateFolRAVsTimes(Time ravTimeStamp) {
        if (_folRAVsTimes.lastIndexOf("*") >= 0) {
            _folRAVsTimes.replace(_folRAVsTimes.lastIndexOf("*"), _folRAVsTimes.length(), ravTimeStamp + ";");
        }

    }

    /** TBC
     * 
     * @param value
     * @return
     */
    private String _printFormatedNumbers(double value) {
        DecimalFormat df = new DecimalFormat(_decimalFormat);
        df.setRoundingMode(RoundingMode.HALF_DOWN);
        return df.format(value);
    }

    /** TBC
     * 
     * @param time
     * @return
     */
    public String printTimes(Time time) {
        return _printFormatedNumbers(time.getDoubleValue());
    }

    /** TBC
     * 
     * @param reason
     * @param hlaCurrentTime
     * @param directorTime
     */
    public void storeTimes(String reason, Time hlaCurrentTime, Time directorTime) {
        String tHLA = printTimes(hlaCurrentTime);
        String tPTII = printTimes(directorTime);

        _tPTII.append(tPTII + ";");
        _tHLA.append(tHLA + ";");

        _reasonsToPrintTheTime.append(reason + ";");
    }

    /** Verify the existence of a folder, if it doesn't exist, the function tries
     *  to create it.
     *
     *  @param folderName The name of the folder that will be created.
     *  @return The full address of the folder in a string.
     *  @exception IOException If the folder cannot be created.
     */
    public String createFolder(String folderName) throws IOException {
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
     *  @param name the name to of the file
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
     *  @param name the name to of the file
     *  @param header
     *  @return
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

    /** Return the total number of time advance grants that this federate has received.
     *  @return The number of time advance grants that this federate has received.
     *  @see #_numberOfTAGs
     *  @see #setNumberOfTAGs
     */
    public int getNumberOfTAGs() {
        return _numberOfTAGs;
    }

    /** TBC
     *  @param _numberOfTAGs The number of time advance grants that this federate has received.
     *  @see #_numberOfTAGs
     *  @see #getNumberOfTAGs
     */
    public void incrNumberOfTAGs() {
        this._numberOfTAGs++;
    }

    /** Return the number of next event requests that this federate has made.
     *  @return The number of next event requests that this federate has made.
     */
    public int getNumberOfNERs() {
        return _numberOfNERs;
    }

    /** TBC.
     *  @param _numberOfNERs The number of next event requests that this federate has made.
     */
    public void incrNumberOfNERs() {
        this._numberOfNERs++;
    }

    /** Return the number of time advance requests that this federate has made.
     *  @return The number of time advance requests that this federate has made.
     */
    public int getNumberOfTARs() {
        return _numberOfTARs;
    }

    /** TBC
     *  @param _numberOfTARs The number of time advance requests that this federate has made.
     */
    public void incrNumberOfTARs() {
        this._numberOfTARs++;
    }

    /** Return the start Time of the execution of the federation.
     * @return The start Time of the execution of the federation.
     */
    public double getStartTime() {
        return _startTime;
    }

    /** Set the start Time of the execution of the federation. */
    public void setStartTime() {
        _startTime = System.nanoTime();
    }

    /** Calculate the duration of the execution of the federation. */
    public void calculateRuntime() {
        double duration = System.nanoTime() - _startTime;
        duration = duration / (Math.pow(10, 9));

        _runtime = duration;
    }

    /** Write the UAV information. */
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

    /** Write the RAV information. */
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

    /** Write the number of HLA calls of each federate, along with informations about the
     *  time step and the runtime, in a file.
     *  The name and location of this file are specified in the initialization of the
     *  variable file.
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

    /** Write information in a txt file.
     *  @param data The information you want to write.
     *  @param file The file in which you want to write.
     *  @return Return true if the information was successfully written in the file.
     */
    public boolean writeInTextFile(File file, String data) {

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

    /** Write the time file to 'times.csv'. */
    public void writeTimes() {
        File timesFile = createTextFile(_federateName + "-times"+ _FILE_EXT_CSV);
        writeInTextFile(timesFile,
                _date + ";Reason:;" + _reasonsToPrintTheTime
                + "\nt_ptII:;" + _tPTII 
                + "\nt_hla:;" + _tHLA);
    }

    /** TBC
     *  @param data
     *  @return
     */
    public boolean writeToCsvFile(String data) {
        return writeInTextFile(_csvFile, data);
    }

    /** TBC
     *  @param data
     *  @return
     */
    public boolean writeToTextFile(String data) {
        return writeInTextFile(_txtFile, data);
    }

    /** TBC
     *  @param tagIndex
     */
    public void incrTickCounterAt(int tagIndex) {
        // Get tick() counter for the current TAG index.
        int counter = _numberOfTicks.get(tagIndex);

        // Update tick() counter.
        counter++;

        // Update with new counter.
        _numberOfTicks.set(tagIndex, counter);
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

    ///////////////////////////////////////////////////////////////////
    ////                         private variables                 ////


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
    public int _numberOfTAGs;


    /** Represents the number of time advance requests(TAR) this federate 
     *  has made.
     *
     *  Time-stepped federates advance with a fixed step in time. In order
     *  to complete the advancement, they have to ask the federation's
     *  permission to do so using a TAR call.
     */
    private int _numberOfTARs;

    /** .. */
    public double _runtime;

    /** .. */
    public int _numberOfTicks2;


    /** .. */
    private String _reportsFolder;

    /** .. */
    private StringBuffer _tPTII;

    /** .. */
    private StringBuffer _tHLA;

    /** .. */
    private StringBuffer _reasonsToPrintTheTime;

    /** Represents a text file that is going to keep track of the number of
     *  HLA calls of the federate. */
    private File _txtFile;

    /** Represents a ".csv" file that is going to keep track of the number of
     *  HLA calls of the federate. */
    private File _csvFile;

    /** Represents a ".csv" file that is going to keep track of the number of
     *  HLA calls of the federate. */
    private File _reportFile;

    /** Represents the file that tracks the values that have been updated and
     *  the time of their update. */
    private File _UAVsValuesFile;

    /** .. */
    private Date _date;

    /** .. */
    private String _decimalFormat;

    /** .. */
    private StringBuffer[] _UAVsValues;

    /**
     * @return the _UAVsValues
     */
    public StringBuffer[] getUAVsValues() {
        return _UAVsValues;
    }

    /**
     * @param _UAVsValues the _UAVsValues to set
     */
    public void setUAVsValues(StringBuffer[] _UAVsValues) {
        this._UAVsValues = _UAVsValues;
    }

    /** .. */
    private int _numberOfAttributesToPublish;

    /**
     * @return the _numberOfAttributesToPublish
     */
    public int getNumberOfAttributesToPublish() {
        return _numberOfAttributesToPublish;
    }

    /** .. */
    private String[] _nameOfTheAttributesToPublish;

    /**
     * @return the _nameOfTheAttributesToPublish
     */
    public String[] getNameOfTheAttributesToPublish() {
        return _nameOfTheAttributesToPublish;
    }


    /** .. */
    private int _numberOfAttributesSubscribedTo;

    public int getNumberOfAttributesSubscribedTo() {
        return _numberOfAttributesSubscribedTo;
    }

    /** .. */
    private String[] _nameOfTheAttributesSubscribedTo;

    /** .. */
    public String[] getNameOfTheAttributesSubscribedTo() {
        return _nameOfTheAttributesSubscribedTo;
    }

    /** .. */
    private StringBuffer _pUAVsTimes;

    /**
     * @return the _pUAVsTimes
     */
    public StringBuffer getPUAVsTimes() {
        return _pUAVsTimes;
    }

    /**
     * @return the _preUAVsTimes
     */
    public StringBuffer getPreUAVsTimes() {
        return _preUAVsTimes;
    }

    /** .. */
    private StringBuffer _preUAVsTimes;

    /** .. */
    private File _RAVsValuesFile;

    /** OK */
    private StringBuffer[] _RAVsValues;

    /** OK */
    private StringBuffer _pRAVsTimes;

    /** OK */
    private StringBuffer _folRAVsTimes;

    /** Represents the instant when the simulation is fully started
     *  (when the last federate starts running).
     */
    private static double _startTime;

    /** The time of the last TAR or last NER. */
    private double _timeOfTheLastAdvanceRequest;

    public void setTimeOfTheLastAdvanceRequest(long value) {
        _timeOfTheLastAdvanceRequest = value;
    }

    /** .. */
    public double getTimeOfTheLastAdvanceRequest() {
        return _timeOfTheLastAdvanceRequest;
    }

    /** Array that contains the number of ticks between a NER or TAR and its respective TAG. */
    public ArrayList<Integer> _numberOfTicks;

    /** Represents the number of the ticks that were not considered in the variable {@link #_numberOfTicks} */
    public int _numberOfOtherTicks;

    /** Represents the number of received RAV. */
    private int _numberOfRAVs;

    /**
     * @return the _numberOfRAVs
     */
    public int getNumberOfRAVs() {
        return _numberOfRAVs;
    }

    /**
     * @param _numberOfUAVs the _numberOfUAVs to set
     */
    public void incrNumberOfRAVs() {
        _numberOfRAVs++;
    }

    /**
     * @param _numberOfRAVs the _numberOfRAVs to set
     */
    public void resetNumberOfRAVs() {
        _numberOfRAVs = 0;
    }

    /** Represents the number of received UAV. */
    private int _numberOfUAVs;

    /** OK */
    public int getNumberOfUAVs() {
        return _numberOfUAVs;
    }

    /**
     * @param _numberOfUAVs the _numberOfUAVs to set
     */
    public void incrNumberOfUAVs() {
        _numberOfUAVs++;
    }

    /** OK */
    public void resetNumberOfUAVs() {
        _numberOfUAVs = 0;
    }

    /** Array that contains the delays between a NER or TAR and its respective TAG. */
    public ArrayList<Double> _TAGDelay;

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
     *  point.
     */
    private Boolean _isCreator;

    /** Indicates the use of the nextEventRequest() service. */
    private Boolean _eventBased;

    /** Indicates the use of the timeAdvanceRequest() service. */
    private Boolean _timeStepped;

    /** HLA attributes tables. */

    /* String = HlaPublisherName, [] HlaPublisherReference HlaAttributeName HlaUAVTimestamp. */
    protected HashMap<String, Object[]> _hlaAttributesUAV = new HashMap<String, Object[]>();
    protected HashMap<String, Object[]> _hlaAttributesRAV = new HashMap<String, Object[]>();
    //protected HashMap<String, Object[]> _nbrTicksByTags = new HashMap<String, Object[]>();

    /** CSV file extension suffix. */
    private static final String _FILE_EXT_CSV = ".csv";

    /** TXT file extension suffix. */
    private static final String _FILE_EXT_TXT = ".txt";

    /** XML file extension suffix. */
    private static final String _FILE_EXT_XML = ".xml";
}
