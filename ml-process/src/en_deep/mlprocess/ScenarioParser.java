package en_deep.mlprocess;

import en_deep.mlprocess.DataSourceDescription.DataSourceType;
import en_deep.mlprocess.Task.TaskType;
import en_deep.mlprocess.TaskSection.DataSourcePurpose;
import en_deep.mlprocess.TaskSection.DataSourcesSection;
import en_deep.mlprocess.exception.DataException;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Vector;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;

/**
 * This creates the process plan from the input scenario XML file. It parses the XML file and
 * determines the dependencies between the tasks. The output is a {@link Vector} of {@link TaskSection}s
 * that have the correct dependencies set. All the {@link Task}s corresponding to the {@link TaskSection}
 * items are marked as undone.
 */
class ScenarioParser implements ContentHandler {

    /* DATA */

    /** The ready process plan */
    Vector<TaskDescription> tasks;
    
    /** Document {@link Locator} for procesing error messages */
    Locator locator;
    /** The name of the currently processed file */
    String fileName;
    /** The currently processed {@link TaskSection} element */
    TaskSection current;
    /** Is the root element opened? */
    boolean open;
    /** Has the root element been closed? */
    boolean closed;
    /** All occurrences of files */
    Hashtable<String,Occurrences> fileOccurrences;
    /** All occurrences of data sets */
    Hashtable<String,Occurrences> dataSetOccurrences;
    /** All occurrences of features */
    Hashtable<String,Occurrences> featureOccurrences;

    /** All ID occurrences */
    HashSet<String> idOccurrences;


    /* METHODS */

    /**
     * Parser initialization, just sets the file name and initializes data
     * containers.
     * @param fileName the name of the parsed file
     */
    ScenarioParser(String fileName) {

        this.fileName = fileName;
        this.tasks = new Vector<TaskDescription>();
        this.fileOccurrences = new Hashtable<String, Occurrences>();
        this.featureOccurrences = new Hashtable<String, Occurrences>();
        this.dataSetOccurrences = new Hashtable<String, Occurrences>();
        this.idOccurrences = new HashSet<String>();
    }

    /**
     * Using the {@link locator} item, creates a String representation of the current
     * file processing location for displaying error messages.
     * The format is: file: "XX.xml", line: X, column: X.
     *
     * @return a String representation of the current file location
     */
    private String getLocationInfo() {
        return "file: \"" + this.fileName + "\", line: " + this.locator.getLineNumber() + ", column: " + this.locator.getColumnNumber();
    }

    /**
     * Adds a data source to the current {@link TaskSection}.
     *
     * @param type type of the data source to add
     * @param id the data source identifier, i.e\. file name or feature / data set id
     * @throws DataException if the data source is not added properly
     */
    private void addDataSource(DataSourceType type, String id) throws DataException {
        DataSourceDescription dsd = null;
        switch (type) {
            case DATA_SET:
                dsd = new DataSetDescription(id);
                break;
            case FEATURE:
                dsd = new FeatureDescription(id, null);
                break;
            case FILE:
                dsd = new FileDescription(id);
                break;
        }
        this.current.addDataSource(dsd);
    }

    /**
     * Marks all the given data sources of the current {@link TaskSection} to their respective
     * {@link Occurrences} tables.
     *
     * @param dss the input or output data sources of the current {@link TaskSection}
     * @param purpose the purpose ({@link TaskSection.DataSourcePurpose}) of the given data sources ({@link dss}, i.e\. input or output)
     * @throws DataException if there are multiple tasks that output the same data source
     */
    private void markDataSources(TaskDescription task, DataSourcePurpose purpose) throws DataException {

        Vector<DataSourceDescription> dss =
                purpose == DataSourcePurpose.INPUT ? task.getInputDataSources() : task.getOutputDataSources();
        Occurrences oc;

        for (DataSourceDescription ds : dss) {

            switch (ds.type) {

                case DATA_SET:
                    DataSetDescription dsd = (DataSetDescription) ds;
                    oc = this.dataSetOccurrences.get(dsd.id);
                    if (oc == null) {
                        oc = new Occurrences();
                        this.dataSetOccurrences.put(dsd.id, oc);
                    }
                    oc.add(task, purpose);
                    break;

                case FEATURE:

                    FeatureDescription fed = (FeatureDescription) ds;
                    oc = this.featureOccurrences.get(fed.id);
                    if (oc == null) {
                        oc = new Occurrences();
                        this.featureOccurrences.put(fed.id, oc);
                    }
                    oc.add(task, purpose);
                    break;

                case FILE:

                    FileDescription fid = (FileDescription) ds;
                    oc = this.fileOccurrences.get(fid.fileName);
                    if (oc == null) {
                        oc = new Occurrences();
                        this.fileOccurrences.put(fid.fileName, oc);
                    }
                    oc.add(task, purpose);
                    break;
            }
        }
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

        if (this.closed) {
            throw new SAXException("Elements past the end of input at " + this.getLocationInfo());
        }
        if (localName.equals("process")) {
            if (!this.open) {
                this.open = true;
                return;
            }
            throw new SAXException("Only one <process> is allowed in the file at " + this.getLocationInfo());
        }
        if (!this.open) {
            throw new SAXException("The root element must be opened first at " + this.getLocationInfo());
        }
        // start new tasks of different types
        if (localName.equals("computation") || localName.equals("evaluation") || localName.equals("manipulation")) {

            if (this.current != null) {
                throw new SAXException("Cannot nest tasks at " + this.getLocationInfo());
            }
            try {
                this.current = new TaskSection(TaskType.valueOf(localName.toUpperCase()), atts.getValue("id"));
            }
            catch (DataException ex) {
                throw new SAXException(ex.getMessage() + " at " + this.getLocationInfo());
            }
            // checks for unique id and saves the current one
            if (this.idOccurrences.contains(this.current.getId())){
                throw new SAXException("Duplicate task id at " + this.getLocationInfo());
            }
            this.idOccurrences.add(this.current.getId());
            return;
        }
        // opens a new data sources section
        if (localName.equals("train") || localName.equals("devel") || localName.equals("eval")
                || localName.equals("data") || localName.equals("input") || localName.equals("output")
                || localName.equals("needed") || localName.equals("created")) {

            try {
                this.current.openDataSection(DataSourcesSection.valueOf(localName.toUpperCase()));
            }
            catch (DataException ex) {
                throw new SAXException(ex.getMessage() + " at " + this.getLocationInfo());
            }
            return;
        }
        // adds data sources specifications
        if (localName.equals("dataSet") || localName.equals("feature") || localName.equals("file")) {

            try {
                this.addDataSource(DataSourceDescription.DataSourceType.valueOf(localName.toUpperCase()),
                        localName.equals("file") ? atts.getValue("name") : atts.getValue("id"));
            }
            catch (DataException ex) {
                throw new SAXException(ex.getMessage() + " at " + this.getLocationInfo());
            }
        }
        // adds an algorithm description
        if (localName.equals("algorithm") || localName.equals("filter") || localName.equals("metric")) {
            try {
                this.current.setAlgorithm(TaskSection.AlgorithmType.valueOf(localName.toUpperCase()),
                        atts.getValue("class"), atts.getValue("parameters"), atts.getValue("parallelizable").equals("true"));
            }
            catch (DataException ex) {
                throw new SAXException(ex.getMessage() + " at " + this.getLocationInfo());
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
        if (localName.equals("process")) {
            if (this.current != null) {
                throw new SAXException("Cannot close <process> if a task description has not finished at " + this.getLocationInfo());
            }
            this.closed = true;
        }
        else if (localName.equals("computation") || localName.equals("evaluation") || localName.equals("manipulation")) {

            if (this.current.getType() != TaskType.valueOf(localName.toUpperCase())) {
                throw new SAXException("Opening and closing type of Tasks don't match at " + this.getLocationInfo());
            }
            if (this.current.getAlgorithm() == null) {
                throw new SAXException("No algortithm has been set for Task at " + this.getLocationInfo());
            }
            try {

                Vector<TaskDescription> newTasks = this.current.getDescriptions();

                for (TaskDescription task : newTasks) {
                    this.markDataSources(task, DataSourcePurpose.INPUT);
                    this.markDataSources(task, DataSourcePurpose.OUTPUT);
                }

                this.tasks.addAll(newTasks);
            }
            catch (DataException ex) {
                throw new SAXException(ex.getMessage() + " at " + this.getLocationInfo());
            }
            this.current = null;
        }
        else if (localName.equals("train") || localName.equals("devel") || localName.equals("eval")
                || localName.equals("data") || localName.equals("input") || localName.equals("output")
                || localName.equals("created") || localName.equals("needed")) {

            try {
                this.current.closeDataSection(DataSourcesSection.valueOf(localName.toUpperCase()));
            }
            catch (DataException ex) {
                throw new SAXException(ex.getMessage() + " at " + this.getLocationInfo());
            }
        }
        else if (!localName.equals("dataSet") && !localName.equals("file") && !localName.equals("feature")
                && !localName.equals("metric") && !localName.equals("algorithm") && !localName.equals("filter")) {

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
