package en_deep.mlprocess;

import en_deep.mlprocess.utils.Pair;
import en_deep.mlprocess.exception.DataException;
import en_deep.mlprocess.utils.StringUtils;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Vector;

/**
 * This creates the process plan from the input scenario file. It {@link ScenarioParse#parse() parses} the file and
 * determines the dependencies between the tasks. The output is a {@link Vector} of {@link TaskDescription}s, which is
 * to be retrieved and examined.
 */
class ScenarioParser {

    /* CONSTANTS */
    
    /** Maximum number of clauses in one task description: opening & ending, algorithm, parameters, input, output */
    private static final int MAX_CLAUSES = 6;

    /* DATA */

    /** The ready process plan */
    private Vector<TaskDescription> tasks;
    /** All occurrences of files */
    private Hashtable<String,Occurrences> fileOccurrences;
    /** All used task names with the corresponding tasks */
    private Hashtable<String,TaskDescription> tasksByName;
    
    /** The name of the processed file */
    private String fileName;
    /** The current line */
    private int line;
    /** The open input file */
    private RandomAccessFile input = null;
    /** The size of the open input file */
    private long fileSize = -1;


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
        this.tasksByName = new Hashtable<String, TaskDescription>();
    }



    /**
     * Performs the actual parsing of the input file. Reads all the Task descriptions and stores
     * them for later retrieval. Marks all file occurrences and sets up the dependencies for the
     * tasks according to them. Relates the files in input and output to the given working directory,
     * but leaves all other parameters untouched.
     *
     * @throws DataException if there is a syntax error in the input file
     */
    void parse() throws DataException, IOException {
    
        String taskSection;
        
        // get all task descriptions and sort them out
        while ((taskSection = this.getNextSection()) != null){

            TaskDescription task = null;
            String taskName = null;
            String taskAlgorithm = null;
            Vector<String> taskInput = null;
            Vector<String> taskOutput = null;
            Hashtable<String, String> taskParameters = null;

            // split into clauses: task name, input, output, algorithm, parameters, end.
            String [] clauses = taskSection.split(";", 7);
            for (int i = 0; i < clauses.length; ++i){
                clauses[i] = clauses[i].trim();
            }
            // check for correct section ending
            if (!clauses[clauses.length - 2].equals("end") || !clauses[clauses.length - 1].equals("")){
                throw new DataException(DataException.ERR_END_EXPECTED, this.fileName, this.line);
            }
            // check for correct section beginning
            if (!clauses[0].startsWith("task ")){
                throw new DataException(DataException.ERR_TASK_EXPECTED, this.fileName, this.line);
            }
            taskName = clauses[0].split("\\s+", 2)[1]; // set the task name and check for duplicity
            if (this.tasksByName.containsKey(taskName)){
                throw new DataException(DataException.ERR_DUPLICATE_TASK_NAME, this.fileName, this.line);
            }
          
            // handle all the section contents
            for (int i = 1; i < clauses.length - 2; ++i){

                String [] clause = clauses[i].split("\\s+", 2);

                if (clause[0].equals("in:")){
                    if (taskInput != null){
                        throw new DataException(DataException.ERR_DUPLICATE_CLAUSE, this.fileName, this.line);
                    }
                    taskInput = this.getFileList(clause[1]);
                }
                else if (clause[0].equals("out:")){
                    if (taskOutput != null){
                        throw new DataException(DataException.ERR_DUPLICATE_CLAUSE, this.fileName, this.line);
                    }
                    taskOutput = this.getFileList(clause[1]);
                }
                else if (clause[0].equals("algorithm:")){
                    if (taskAlgorithm != null){
                        throw new DataException(DataException.ERR_DUPLICATE_CLAUSE, this.fileName, this.line);
                    }
                    taskAlgorithm = clause[1];
                }
                else if (clause[0].equals("params:")){
                    if (taskParameters != null){
                        throw new DataException(DataException.ERR_DUPLICATE_CLAUSE, this.fileName, this.line);
                    }
                    taskParameters = this.getParameters(clause[1]);
                }
                else {
                    throw new DataException(DataException.ERR_UNKNOWN_CLAUSE, this.fileName, this.line);
                }
            }

            // check if the task description is complete
            if (taskInput == null || taskOutput == null || taskAlgorithm == null){
                throw new DataException(DataException.ERR_MISSING_CLAUSE, this.fileName, this.line);
            }
            // parameters section is not compulsory - create empty parameters clause if needed
            if (taskParameters == null){
                taskParameters = new Hashtable<String, String>();
            }

            // build the actual task
            task = new TaskDescription(taskName, taskAlgorithm, taskParameters, taskInput, taskOutput);

            // mark all file occurences
            this.markFileUsages(task);

            // store the task
            this.tasks.add(task);
            this.tasksByName.put(taskName, task);
        }

        // set the task dependencies
        this.setDependencies();
    }


    /**
     * Returns the parsing results -- descriptions of all tasks.
     * @return descriptions of all tasks
     */
     Vector<TaskDescription> getTasks(){
         return this.tasks;
     }


    /**
     * Marks all the given data sources of the current Task to the {@link Occurrences} table.
     *
     * @param task the task to be recorded
     * @throws DataException if there are multiple tasks that output the same data source
     */
    private void markFileUsages(TaskDescription task) throws DataException {

        for (String file : task.getInput()){
            this.markFileUsage(file, task, Occurrences.Purpose.INPUT);
        }
        for (String file: task.getOutput()){
            this.markFileUsage(file, task, Occurrences.Purpose.OUTPUT);
        }
    }

    /**
     * Marks one usage of a file, given the task it's used in and a purpose it's used for.
     *
     * @param file the file name
     * @param task the task the file is used in
     * @param purpose the purpose of the file (input / output)
     * @throws DataException
     */
    private void markFileUsage(String file, TaskDescription task, Occurrences.Purpose purpose) throws DataException {

        Occurrences oc;
        file = StringUtils.getOccurencePattern(file);

        oc = this.fileOccurrences.get(file);

        if (oc == null) {
            oc = new Occurrences();
            this.fileOccurrences.put(file, oc);
        }
        oc.add(task, purpose, this.fileName, this.line);
    }

    /**
     * Parse a list of files within a clause. Removes quotes and spaces from the list elements,
     * then prepends all the resulting file names with the working directory.
     *
     * @param clause the clause string to be parsed
     * @return a list of file names, related to the working directory
     * @throws DataException if there are invalid characters in the file names
     */
    private Vector<String> getFileList(String clause) throws DataException {

        Vector<String> list = StringUtils.parseCSV(clause); // raw parsing

        // remove quotes & spaces
        for(int i = 0; i < list.size(); ++i){

            String s = list.elementAt(i).trim();

            if (s.startsWith("\"") && s.endsWith("\"")){
                s = this.unquote(s);
            }
            else if (s.contains("\"") || s.matches("(?s).*\\s.*")){
                throw new DataException(DataException.ERR_INVALID_CHAR_IN_FILE_NAME, this.fileName, this.line);
            }
            list.setElementAt(StringUtils.getPath(s), i); // prepend with working directory, if needed
        }
        
        // return the result
        return list;
    }

    /**
     * Unquotes a quoted string, i.e\. removes quotes from the beginning and the end and
     * converts double quotes to single. Throws an exception for a single quote within the string.
     * @param string the quoted string to be processed
     * @return the string, unquoted
     */
    private String unquote(String string) throws DataException {

        string = StringUtils.unquote(string);
        if (string == null){
            throw new DataException(DataException.ERR_QUOTES_MISMATCH, this.fileName, this.line);
        }
        return string;
    }

    /**
     * Parse a string that contains comma-separated name = value pairs, values are not compulsory (empty string
     * will be set) and may be enclosed in quotes. Parameter names must be unique.
     *
     * @param string the string to be parsed
     * @return the resulting name - value list
     * @throws DataException if there are duplicate parameters or illegal characters
     */
    private Hashtable<String, String> getParameters(String string) throws DataException {

        Hashtable<String, String> parameters = new Hashtable<String, String>();

        // remove quotes & spaces, split names and values
        for(String listMember : StringUtils.parseCSV(string)){

            String [] nameVal = listMember.split("=", 2);

            nameVal[0] = nameVal[0].trim();

            if (nameVal[0].matches("[^a-zA-Z0-9_\\.-]")){
                throw new DataException(DataException.ERR_INVALID_CHAR_IN_PARAMETER, this.fileName, this.line);
            }
            if (nameVal.length == 1){
                parameters.put(nameVal[0], "");
                continue;
            }
            nameVal[1] = nameVal[1].trim();
            if (nameVal[1].startsWith("\"") && nameVal[1].endsWith("\"")){
                nameVal[1] = this.unquote(nameVal[1]);
            }
            else if (nameVal[1].contains("\"") || nameVal[1].matches("(?s).*\\s.*")){
                throw new DataException(DataException.ERR_INVALID_CHAR_IN_PARAMETER, this.fileName, this.line);
            }

            parameters.put(nameVal[0], nameVal[1]);
        }

        // return the result
        return parameters;
    }

    /**
     * Parses the input file with respect to the individual task descriptions. Reads up to
     * MAX_CLAUSES sections - or stops at the first "end" section. Returns null if there is
     * nothing more than whitespace in the input file. Opens the file if it's not already
     * open and closes it if there's nothing more to be read
     * 
     * @return the next task description section
     */
    private String getNextSection() throws IOException, DataException {

        StringBuilder buf = new StringBuilder();
        int clausesRead = 0;
        String currentClause = ";";
        String retVal;

        // open the file, if it's not already open
        if (this.input == null){
            this.input = new RandomAccessFile(this.fileName, "r");
            this.fileSize = this.input.length();
        }

        // read the desired number of clauses, if there is enough of them (or until the end-clause)
        while (clausesRead < MAX_CLAUSES && currentClause!= null
                && currentClause.endsWith(";") && !currentClause.startsWith("end")){

            currentClause = this.readClause();
            clausesRead++;
            if (currentClause != null){
                buf.append(currentClause);
            }
        }

        retVal = buf.toString().trim();
        if (retVal.equals("")){
            this.input.close(); // close the file if we're at the end
            return null;
        }
        return retVal;
    }

    /**
     * Reads one semicolon-separated clause line from the input file. Finds an unquoted semicolon and reads
     * everything up to it, then makes a string out of it. Counts lines.
     * @return the next line from the open file
     */
    private String readClause() throws IOException, DataException {

        long pos1 = this.input.getFilePointer();
        long curPos; // we need to prevent from reaching the end-of-file, for the
                     // damn thing would freeze otherwise
        long pos2;
        int c = this.input.read();
        int lastC = -1;
        byte [] rawLine;
        boolean quoted = false;
        boolean commented = false;
        boolean escaped = false;
        // starts and ends of comments, relative to pos1
        Vector<Pair<Integer, Integer>> comments = new Vector<Pair<Integer, Integer>>();

        curPos = this.input.getFilePointer();
        if (curPos == this.fileSize){ // EOF check
            return null;
        }

        while(curPos < this.fileSize && (c != ';' || quoted || commented)){

            // line counting: ignore two subsequent windows new-line characters
            if (c == '\n' && lastC == '\r'){
                
            }
            // line counting: count all else; new-line ends comments
            else if (c == '\n' || c == '\r'){
                this.line++;
                if (commented){
                    comments.lastElement().second = (int) (curPos - pos1);
                }
                commented = false;
            }
            // escape-char
            else if (c == '\\'){
                escaped = !escaped;
            }
            // begin comments (unquoted and uncommented hash-char begins comments)
            else if (!escaped && !quoted && !commented && c == '#'){
                commented = true;
                comments.add(new Pair<Integer, Integer>((int)(curPos - pos1)));
            }
            // heed the quote characters, if not in comments/not escaped
            else if (!escaped && !commented && c == '"'){
                quoted = !quoted;
            }

            // end escaping
            if (escaped && c != '\\'){
                escaped = false;
            }

            c = this.input.read();
            curPos++;
            lastC = c;
        }
        pos2 = this.input.getFilePointer();
        if (commented){
            comments.lastElement().second = (int) (pos2 - pos1);
        }
        if (curPos == this.fileSize && quoted){
            throw new DataException(DataException.ERR_UNEXPECTED_EOF, this.fileName, this.line);
        }

        // since we know if we're at the end of the clause, we don't need curPos anymore
        rawLine = new byte [(int)(pos2 - pos1)];
        this.input.seek(pos1);
        this.input.read(rawLine);

        return this.stripComments(rawLine, comments);
    }


    /**
     * Set the dependencies according to file usages in the data and check them.
     * <p>
     * All the files that are not written as output are assumed to exist before the {@link Process}
     * begins.
     * </p>
     *
     */
    private void setDependencies() {

        Enumeration<String> files = this.fileOccurrences.keys();

        while (files.hasMoreElements()){

            String file = files.nextElement();
            Occurrences oc = this.fileOccurrences.get(file);

            if (oc.asOutput == null){ // file(s) must exist before the process begins
                Logger.getInstance().message("File(s): " + file + " must be provided as input to the process.",
                        Logger.V_INFO);
                continue;
            }
            for (TaskDescription dep : oc.asInput){
                dep.setDependency(oc.asOutput);
            }
        }
    }

    /**
     * Strips all comments sections from the raw input clause and returns it as a string, trimmed.
     * @param rawLine the original raw input clause, as it appears in the data
     * @param comments the previously found comments sections
     * @return the input clause, comments-free and trimmed
     */
    private String stripComments(byte[] rawLine, Vector<Pair<Integer, Integer>> comments) {

        StringBuilder sb = new StringBuilder();

        if (comments.isEmpty()){
            sb.append(new String(rawLine));
        }
        else {
            sb.append(new String(rawLine, 0, comments.firstElement().first - 1));
            for (int i = 1; i < comments.size(); ++i){
                sb.append(new String(rawLine, comments.get(i-1).second,
                        comments.get(i).first - comments.get(i-1).second - 1));
            }
            sb.append(new String(rawLine, comments.lastElement().second, 
                    rawLine.length - comments.lastElement().second));
        }

        return sb.toString().trim();
    }


}
