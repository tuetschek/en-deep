/*
 *  Copyright (c) 2009 Ondrej Dusek
 *  All rights reserved.
 * 
 *  Redistribution and use in source and binary forms, with or without modification, 
 *  are permitted provided that the following conditions are met:
 *  Redistributions of source code must retain the above copyright notice, this list 
 *  of conditions and the following disclaimer.
 *  Redistributions in binary form must reproduce the above copyright notice, this 
 *  list of conditions and the following disclaimer in the documentation and/or other 
 *  materials provided with the distribution.
 *  Neither the name of Ondrej Dusek nor the names of their contributors may be
 *  used to endorse or promote products derived from this software without specific 
 *  prior written permission.
 * 
 *  THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND 
 *  ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED 
 *  WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. 
 *  IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, 
 *  INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, 
 *  BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, 
 *  DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF 
 *  LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE 
 *  OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED 
 *  OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package en_deep.mlprocess;

import en_deep.mlprocess.Task.TaskType;
import en_deep.mlprocess.TaskData.DataSourcesSection;
import en_deep.mlprocess.exception.DataException;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileLock;
import java.util.Vector;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.InputSource;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.XMLReaderFactory;


/**
 * This component is responsible for building the planFile of the whole computation,
 * according to the input scenario.
 *
 * @author Ondrej Dusek
 */
public class Plan {

    /* DATA */

    /** The planFile file */
    private File planFile;

    /** The only instance of {@link Plan}. */
    private static Plan instance = null;

    /** The tasks and their features and pending statuses */
    private static Vector<TaskData> tasks;

    /* METHODS */

    /**
     * Creates a new instance of {@link Plan}. All objects should call
     * {@link Plan.getInstance()} to acquire an instance of {@link Plan}.
     */
    private Plan(){
        
        // open the planFile file (and create it if necessary)
        this.planFile = new File(Process.getInstance().getInputFile() + ".todo");

        try {
            // this ensures we never have an exception, but the file may be empty
            planFile.createNewFile();
        }
        catch(IOException ex){
            Logger.getInstance().message(ex.getMessage(), Logger.V_IMPORTANT);
        }
    }

    /**
     * Retrieves the only instance of the {@link Plan} singleton. This calls
     * the {@link Plan} constructor upon first call.
     *
     * @return the only instance of {@link Plan}
     */
    public Plan getInstance(){

        if (Plan.instance == null){
            Plan.instance = new Plan();
        }
        return Plan.instance;
    }


    /**
     * Tries to get the next pending task from the to-do file.
     * <p>
     * Locks the to-do file
     * to avoid concurrent access from within several instances. If the to-do file does
     * not exist or is empty, creates it and fills it with a planFile.
     * </p><p>
     * Returns null in case of an error or nothing else to do. All errors are logged with
     * the highest importance setting.
     * <p>
     *
     * @return the next pending task to be done, or null if there are no tasks to be done (or an
     *         error occurred)
     */
    public synchronized Task getNextPendingTask() {

        FileLock lock = null;
        Task nextPending = null;
        
        // try to acquire lock on the to-do file and get a planned task
        try {
            RandomAccessFile planFileIO = new RandomAccessFile(this.planFile, "rw");
            lock = planFileIO.getChannel().lock();

            if (planFileIO.length() == 0){ // the planFile file - the planFile has not yet been created
                this.createPlan(planFileIO);
            }

            nextPending = this.getNextPendingTask(planFileIO);
        }
        catch(IOException ex){
            Logger.getInstance().message(ex.getMessage(), Logger.V_IMPORTANT);
            return null;
        }
        catch(SAXException ex){
            Logger.getInstance().message(ex.getMessage(), Logger.V_IMPORTANT);
            return null;
        }

        // always releas the lock on the to-do file
        finally {
            if (lock != null && lock.isValid()){
                try {
                    lock.release();
                }
                catch(IOException ex){
                    Logger.getInstance().message(ex.getMessage(), Logger.V_IMPORTANT);
                    return null;
                }
            }
        }

        return nextPending;
    }

    /**
     * Creates the process planFile, so that {@link Worker}s may retrieve pending {@link Task}s
     * later.
     * Tries to read the process description XML file and create the to-do file according to
     * it, using DAG and parallelizations (up to the specified number of {@Worker}s for all
     * instances of the {@link Process}.
     *
     * @param planFileIO the to-do file, locked and opened for writing
     * @throws SAXException if the input XML file is invalid
     * @throws IOException if there are some I/O problems with the file
     */
    private void createPlan(RandomAccessFile planFileIO) throws SAXException, IOException {

        XMLReader parser;
        ScenarioParser dataCollector = new ScenarioParser(this.planFile.getName());

        parser = XMLReaderFactory.createXMLReader();
        parser.setContentHandler(dataCollector);
        parser.parse(new InputSource(new FileReader(planFileIO.getFD())));

        // TODO tak dataCollector.tasks, resolve dependencies and parallelize!
        
    }

    /**
     * Reads the to-do file structure and retrieves the next pending {@link Task}, updating its
     * progress status.
     *
     * @param planFileIO the to-do file, locked and opened for writing
     * @return the next pending task from the TODO file
     */
    private Task getNextPendingTask(RandomAccessFile planFileIO) {

        // TODO samotne nacteni DAGu tasku ze souboru (hlavne jmen, spojnic a stavu provadeni)
        // nic jineho by asi nebylo potreba, kdyz se bude XML jen cist ... ale zas uz bude rozparsovane,
        // tak mozna lepsi tam nacpat vsechna metadata.
        // DAG by mel mit format, kterym se nactou {@link TaskData} a z nich se pomoci {@link TaskFactory}
        // bude dat stvorit skutecny {@link Task}. TaskData musi obsahovat id primych predchudcu a nasledniku
        // - podle pointru by z nich mel jit autom. sestavit graf
        throw new UnsupportedOperationException("Not yet implemented");
    }

    /**
     * This creates the process plan from the input scenario XML file. It parses the XML file and
     * determines the dependencies between the tasks. The output is a {@link Vector} of {@link TaskData}s
     * that have the correct dependencies set. All the {@link Task}s corresponding to the {@link TaskData}
     * items are marked as undone.
     */
    private class ScenarioParser implements ContentHandler {

        /* DATA */

        /** The ready process plan */
        Vector<TaskData> tasks;

        /** Document {@link Locator} for procesing error messages */
        Locator locator;

        /** The name of the currently processed file */
        String fileName;

        /** The currently processed {@link TaskData} element */
        TaskData current;

        /** Is the root element opened? */
        boolean open;
        /** Has the root element been closed? */
        boolean closed;


        /* METHODS */

        /** 
         * Constructor, stores just the file name for error messages.
         * @param fileName the name of the processed file
         */
        ScenarioParser(String fileName){
            this.fileName = fileName;
        }

        /**
         * Using the {@link locator} item, creates a String representation of the current
         * file processing location for displaying error messages.
         * The format is: file: "XX.xml", line: X, column: X.
         *
         * @return a String representation of the current file location
         */
        private String getLocationInfo() {
            return "file: \"" + this.fileName + "\", line: " + this.locator.getLineNumber()
                    + ", column: " + this.locator.getColumnNumber();
        }


        /* INTERFACE ContentHandler */

        /**
         * This sets the document {@link Locator}, used to process error messages.
         * @param locator
         */
        public void setDocumentLocator(Locator locator) {
            this.locator = locator;
        }

        /**
         * Document start - empty
         * @throws SAXException
         */
        public void startDocument() throws SAXException {            
        }

        /**
         * Document end - empty
         * @throws SAXException
         */
        public void endDocument() throws SAXException {
        }

        /**
         * XML prefix mapping start - empty
         * @param prefix
         * @param uri
         * @throws SAXException
         */
        public void startPrefixMapping(String prefix, String uri) throws SAXException {
        }

        /**
         * XML prefix mapping end - empty
         * @param prefix
         * @param uri
         * @throws SAXException
         */
        public void endPrefixMapping(String prefix) throws SAXException {            
        }


        /**
         * Starts an element - tries to add new data to the element currently processed.
         */
        public void startElement(String uri, String localName, String qName, Attributes atts) throws SAXException {

            if (this.closed){
                throw new SAXException("Elements past the end of input at " + this.getLocationInfo());
            }
            if (localName.equals("process")){
                if (!this.open){
                    this.open = true;
                    return;
                }
                throw new SAXException("Only one <process> is allowed in the file at " + this.getLocationInfo());
            }
            if (!this.open){
                throw new SAXException("The root element must be opened first at " + this.getLocationInfo());
            }

            // start new tasks of different types
            if (localName.equals("computation") || localName.equals("evaluation") || localName.equals("manipulation")){
                if (this.current != null){
                    throw new SAXException("Cannot nest tasks at " + this.getLocationInfo());
                }
                try {
                    this.current = new TaskData(TaskType.valueOf(localName.toUpperCase()), atts.getValue("id"));
                } catch (DataException ex) {
                    throw new SAXException(ex.getErrorMessage() + " at " + this.getLocationInfo());
                }

                // checks for unique id
                for (TaskData td: this.tasks){
                    if (td.getId().equals(this.current.getId())){
                        throw new SAXException("Duplicate task id at " + this.getLocationInfo());
                    }
                }
                return;
            }
            // opens a new data sources section
            if (localName.equals("train") || localName.equals("devel") || localName.equals("eval")
                    || localName.equals("data") || localName.equals("input") || localName.equals("output")){

                try{
                    this.current.openDataSection(DataSourcesSection.valueOf(localName.toUpperCase()));
                    
                }
                catch(DataException ex){
                    throw new SAXException(ex.getErrorMessage() + " at " + this.getLocationInfo());
                }
                return;
            }
            // adds data sources specifications
            try {
                if (localName.equals("dataSet")){
                    this.current.addDataSource(new DataSetDescription(atts.getValue("id")));
                    return;
                }
                else if (localName.equals("feature")){
                    this.current.addDataSource(new FeatureDescription(atts.getValue("id")));
                    return;
                }
                else if (localName.equals("file")){
                    this.current.addDataSource(new FileDescription(atts.getValue("name")));
                    return;
                }
            }
            catch (DataException ex){
                throw new SAXException(ex.getErrorMessage() + " at " + this.getLocationInfo());
            }
            // adds an algorithm description
            if (localName.equals("algorithm") || localName.equals("filter") || localName.equals("metric")){
                try {
                    this.current.setAlgorithm(TaskData.AlgorithmType.valueOf(localName.toUpperCase()),
                            atts.getValue("class"), atts.getValue("parameters"),
                            atts.getValue("parallelizable").equals("true"));
                }
                catch(DataException ex){
                    throw new SAXException(ex.getErrorMessage() + " at " + this.getLocationInfo());
                }
            }
            else {
                throw new SAXException("Unknown element at " + this.getLocationInfo());
            }
        }

        /**
         * End of an element - checks for completeness and issues an error, if there are any problems.
         * This also tries to parallelize and find out all dependencies.
         */
        public void endElement(String uri, String localName, String qName) throws SAXException {

            // this ends the main element
            if (localName.equals("process")){
                if (this.current != null){
                    throw new SAXException("Cannot close <process> if a task description has not finished at "
                            + this.getLocationInfo());
                }
                this.closed = true;
            }
            // ends a task - checks for compulsory elements
            else if (localName.equals("computation") || localName.equals("evaluation") || localName.equals("manipulation")){

                if (this.current.getType() != TaskType.valueOf(localName.toUpperCase())){
                    throw new SAXException("Opening and closing type of Tasks don't match at " + this.getLocationInfo());
                }
                if (this.current.getAlgorithm() == null){
                    throw new SAXException("No algortithm has been set for Task at " + this.getLocationInfo());
                }
                try {
                    this.current.checkDataSets();
                }
                catch(DataException ex){
                    throw new SAXException(ex.getErrorMessage() + " at " + this.getLocationInfo());
                }
                this.tasks.add(this.current);
                this.current = null;
            }
            // ends a data sources section
            else if (localName.equals("train") || localName.equals("devel") || localName.equals("eval")
                    || localName.equals("data") || localName.equals("input") || localName.equals("output")){

                try{
                    this.current.closeDataSection(DataSourcesSection.valueOf(localName.toUpperCase()));

                }
                catch(DataException ex){
                    throw new SAXException(ex.getErrorMessage() + " at " + this.getLocationInfo());
                }
            }
            // ends non-pair tags
            else if (!localName.equals("dataSet") && !localName.equals("file") && !localName.equals("feature")
                    && !localName.equals("metric") && !localName.equals("algorithm") && !localName.equals("filter")){
                throw new SAXException("Unknown element at " + this.getLocationInfo());
            }

        }

        /**
         * Some loose characters encountered - throws an exception instantly.
         */
        public void characters(char[] ch, int start, int length) throws SAXException {
            throw new SAXException("Trailing characters at " + this.getLocationInfo());
        }

        /**
         * Whitespace - ignored
         */
        public void ignorableWhitespace(char[] ch, int start, int length) throws SAXException {            
        }

        /**
         * Processing instructions are being ignored
         */
        public void processingInstruction(String target, String data) throws SAXException {
        }

        /**
         * Skipped entities are being ignored
         */
        public void skippedEntity(String name) throws SAXException {
        }

    }
}
