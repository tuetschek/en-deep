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

package en_deep.mlprocess.manipulation;

import com.google.common.collect.HashMultimap;
import en_deep.mlprocess.Logger;
import en_deep.mlprocess.Task;
import en_deep.mlprocess.exception.TaskException;
import en_deep.mlprocess.Process;
import en_deep.mlprocess.manipulation.genfeat.Feature;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.RandomAccessFile;
import java.util.Hashtable;
import java.util.Set;
import java.util.Vector;
import weka.core.Instances;
import weka.core.converters.ConverterUtils;
import weka.filters.Filter;
import weka.filters.unsupervised.attribute.StringToNominal;

/**
 * This class converts the original ST file format of the CoNLL task to ARFF file format
 * and creates specified features.
 *
 * @author Ondrej Dusek
 */
public class StToArff extends Task {

    /* CONSTANTS */

    /** The lang_conf parameter name */
    private static final String LANG_CONF = "lang_conf";
    /** The multiclass parameter name */
    private static final String MULTICLASS = "multiclass";
    /** The predicted parameter name */
    private static final String PREDICTED = "predicted";
    /** The pred_only parameter name */
    private static final String PRED_ONLY = "pred_only";
    /** The generate parameter name */
    private static final String GENERATE = "generate";
    /** The omit_semclass parameter name */
    private static final String OMIT_SEMCLASS = "omit_semclass";

    /** Number of compulsory fields that are in each sentence */
    private static final int COMPULSORY_FIELDS = 14;

    /** Output file suffix for noun predicates */
    private static final String NOUN = ".n";
    /** Output file suffix for verb predicates */
    private static final String VERB = ".v";
    /** Output file suffix for errorneously tagged predicates */
    private static final String TAG_ERR = ".e";

    /** Attribute definition start in ARFF files */
    public static final String ATTRIBUTE = "@ATTRIBUTE";
    /** Specification of an attribute as CLASS in ARFF files */
    public static final String CLASS = "";
    /** Specification of an attribute as INTEGER in ARFF files */
    public static final String INTEGER = "INTEGER";
    /** Specification of an attribute as STRING in ARFF files */
    public static final String STRING = "STRING";

    /** Semantic relation (multiclass) attribute name in ARFF files */
    private static final String SEM_REL = "semrel";

    /** Start of the data section in ARFF files */
    private static final String DATA = "@DATA";

    /** Caption of ARFF files */
    private static final String RELATION = "@RELATION";

    /** List of attributes in ARFF files */
    private static final String [] HEADER = {
        "@ATTRIBUTE sent-id INTEGER",
        "@ATTRIBUTE word-id INTEGER",
        "@ATTRIBUTE form STRING",
        "@ATTRIBUTE lemma STRING",
        "@ATTRIBUTE p-lemma STRING",
        "@ATTRIBUTE pos STRING",
        "@ATTRIBUTE p-pos STRING",
        "@ATTRIBUTE feat STRING",
        "@ATTRIBUTE p-feat STRING",
        "@ATTRIBUTE head INTEGER",
        "@ATTRIBUTE p-head INTEGER",
        "@ATTRIBUTE deprel STRING",
        "@ATTRIBUTE p-deprel STRING",
        "@ATTRIBUTE fillpred {Y,_}",
        "@ATTRIBUTE pred STRING"
    };

    
    /** Index of the FEAT attribute in the output ARFF file */
    private static final int IDXO_FEAT = 7;

    /* DATA */

    /** Create a multiclass semantic relation description ? */
    private boolean useMulticlass;
    /** Output predicates only ? */
    private boolean predOnly;
    /** Omit semantic class in the ouptut ? */
    private boolean omitSemClass;

    /** Sentence ID generation -- last used value */
    private static int lastId = 0;

    /** Features to be generated */
    private Vector<Feature> genFeats;

    /** The input configuration */
    private StToArffConfig config;

    /** Used output files (for reprocessing) */
    private HashMultimap<String, String> usedFiles;


    /* METHODS */

    /**
     * This creates a new {@link StToArff} task.
     * <p>
     * The output specification must have a "**" pattern, in order to produce more output files. If there
     * are more input files, the exactly same number of outputs (with "**") must be given.
     * </p><p>
     * <strong>Parameters:</strong>
     * </p>
     * <ul>
     * <li><tt>lang_conf</tt> -- path to the language config file, that contains a FEAT usage indication ("1"/empty line), followed
     * by noun and verb tag regexp patterns (each on separate line) and a list of semantic roles (one line, space-separated).</li>
     * <li><tt>predicted</tt> -- if set to non-false, work with predicted lemma, POS and only </li>
     * <li><tt>multiclass</tt> -- if set to non-false, one attribute named "semrel" is created, otherwise, multiple classes
     * (one semantic class each) with 0/1 values are created.</li>
     * <li><tt>generate</tt> -- comma-separated list of features to be generated</li>
     * <li><tt>pred_only</tt> -- if set to non-false, only predicates are outputted, ommiting all other words in a sentence</li>
     * <li><tt>omit_semclass</tt> -- if set to non-false, the semantic class is not outputted at all</li>
     * </ul>
     * <p>
     * Additional parameters may be required by the individual generated {@link en_deep.mlprocess.manipulation.genfeat.Feature Feature}s.
     * </p>
     *
     * @todo no need for possible list of POS, FEAT and DEPREL in the lang_conf file, exclude it
     * @param id the task id
     * @param parameters the task parameters
     * @param input the input data sets or files
     * @param output the output data sets or files
     */
    public StToArff(String id, Hashtable<String, String> parameters,
            Vector<String> input, Vector<String> output) throws TaskException {
        
        super(id, parameters, input, output);

        // check parameters
        if (this.parameters.get(LANG_CONF) == null){
            throw new TaskException(TaskException.ERR_INVALID_PARAMS, this.id, "Parameter lang_conf is missing.");
        }

        // find the config file
        String configFile = Process.getInstance().getWorkDir() + this.parameters.get(LANG_CONF);
        if (!new File(configFile).exists()){
            throw new TaskException(TaskException.ERR_INVALID_PARAMS, this.id, "Cannot find config file.");
        }
        this.config = new StToArffConfig(this);

        // initialize boolean parameters
        this.useMulticlass = this.getBooleanParameterVal(MULTICLASS);
        this.predOnly = this.getBooleanParameterVal(PRED_ONLY);
        this.omitSemClass = this.getBooleanParameterVal(OMIT_SEMCLASS);       

        // initialize features to be generated
        this.initGenFeats();

        // initialize the list of used output files
        this.usedFiles = HashMultimap.create();

        // check outputs
        if (input.size() != output.size()){
            throw new TaskException(TaskException.ERR_WRONG_NUM_OUTPUTS, this.id);
        }
        // checks if there are "**" patterns in outputs (just simple string check is sufficient, Task expansion
        // ensures that there are no unwanted "*"'s.
        for (String outputFile: this.output){
            if (!outputFile.contains("**")){
                throw new TaskException(TaskException.ERR_OUTPUT_PATTERNS, this.id, "Outputs must contain '**' pattern.");
            }
        }
    }


    /**
     * Performs the task operation -- converts all the given input file(s) according to the parameters.
     * @throws TaskException
     */
    @Override
    public void perform() throws TaskException {

        try {
            // read the list of possible POS, FEAT and DEPREL tags
            this.config.init();

            for (int i = 0; i < this.input.size(); ++i){
                // convert the files
                this.convert(this.input.get(i), this.output.get(i));
            }

            // now convert the string attributes to nominal types in the output
            for (String predicate : this.usedFiles.keySet()){
                
                Logger.getInstance().message(this.id + ": Rewriting header(s) for " + predicate + " ...", Logger.V_DEBUG);
                this.stringToNominal(predicate);
            }
        }
        catch (TaskException e){
            Logger.getInstance().logStackTrace(e.getStackTrace(), Logger.V_DEBUG);
            throw e;
        }
        catch (Exception e){
            Logger.getInstance().logStackTrace(e.getStackTrace(), Logger.V_DEBUG);
            throw new TaskException(TaskException.ERR_IO_ERROR, this.id, e.getMessage());
        }
    }

    /**
     * Performs one file conversion.
     * @param st the input file name
     * @param arff the output file name
     * @throws TaskException
     */
    private void convert(String st, String arff) throws TaskException, FileNotFoundException, IOException {

        RandomAccessFile in = new RandomAccessFile(st, "r");
        Vector<String []> sentence = this.readSentence(in);
        int [] predNums;
        Vector<String> outFiles;
        int sentenceId;

        while (sentence != null){

            sentenceId = generateSentenceId();
            predNums = this.findPredicates(sentence, sentenceId, st); // find predicate positions
            outFiles = this.findOutputs(sentence, predNums, arff); // find correpsonding output file names
            this.writeHeaders(outFiles); // prepare output file headers

            // for all predicates, write the sentence to an output file
            for (int i = 0; i < predNums.length; ++i){

                FileOutputStream os = new FileOutputStream(outFiles.get(i), true);
                PrintStream out = new PrintStream(os);

                for (int j = 0; j < sentence.size(); ++j){

                    String [] word = sentence.get(j);

                    // skip non-predicate lines if such setting is imposed
                    if (this.predOnly && j != predNums[i]){
                        continue;
                    }

                    // print the compulsory fields
                    out.print(sentenceId);

                    for (int k = 0; k < COMPULSORY_FIELDS; ++k){
                        if (this.config.posFeat == false &&
                                (k == this.config.IDXI_FEAT || k == this.config.IDXI_FEAT + this.config.predictedNon)){
                            continue; // skip FEAT if we're not using them
                        }
                        if (k == this.config.IDXI_WORDID || k == this.config.IDXI_HEAD
                                || k == this.config.IDXI_HEAD + this.config.predictedNon){
                            out.print("," + word[k]);
                        }
                        else { // quote non-numeric fields
                            out.print(",\"" + word[k] + "\"");
                        }
                    }
                    
                    // add generated features
                    for (Feature f : this.genFeats){
                        out.print("," + f.generate(sentence, j, predNums[i]));
                    }

                    // print the resulting semantic relation to the given predicate
                    if (!this.omitSemClass){
                        if (!this.useMulticlass){
                            for (String role : this.config.semRoles){
                                if (word[this.config.IDXI_SEMROLE + i].equals(role)){
                                    out.print(",1");
                                }
                                else {
                                    out.print(",0");
                                }
                            }
                        }
                        else {
                            out.print(",\"" + word[this.config.IDXI_SEMROLE + i] + "\"");
                        }
                    }

                    out.println();
                }

                out.close();
                out = null;
            }

            sentence = this.readSentence(in); // read next sentence

            if (sentenceId % 1000 == 0){
                Logger.getInstance().message(this.id + ": Input: " + st + ", sentence: " + sentenceId, Logger.V_DEBUG);
            }
        }

        in.close();
        in = null;
    }
 

    /**
     * Returns a unique sentence ID.
     *
     * @return the generated ID
     */
    private static synchronized int generateSentenceId(){

        lastId++;
        return lastId;
    }

    /**
     * Writes output ARFF files headers for the given file names. Heeds the "multiclass" parameter
     * (see {@link StToArff}). Some parameter types are left as STRING at first. They are converted
     * to class values later.
     *
     * @param outFiles a list of file names to write
     */
    private void writeHeaders(Vector<String> fileNames) throws FileNotFoundException {

        for (String fileName : fileNames){
            if (new File(fileName).exists()){ // only for non-existent files
                continue;
            }
            FileOutputStream os = new FileOutputStream(fileName);
            PrintStream out = new PrintStream(os);

            out.println(RELATION + " " + fileName);

            // print the constant fields that are always present
            for (int i = 0; i < HEADER.length; ++i){

                if (this.config.posFeat && (i == IDXO_FEAT || i == IDXO_FEAT + 1)){
                    out.println(HEADER[i]);
                }
                else if (i == IDXO_FEAT || i == IDXO_FEAT + 1){ // do not print FEAT headers if we're not using them
                    continue;
                }
                else {
                    out.println(HEADER[i]);
                }
            }

            // print generated features' headers
            for (Feature f : this.genFeats){
                out.println(f.getHeader());
            }

            // print the target class / classes header(s) (according to the "multiclass" parameter),
            // if supposed to do so at all (heed the "omit_semclass" parameter)
            if (!this.omitSemClass){
                if (!this.useMulticlass){
                    for (String role : this.config.semRoles){
                        out.println(ATTRIBUTE + " " + role + " " + INTEGER);
                    }
                }
                else {
                    out.print(ATTRIBUTE + " " + SEM_REL + " " + CLASS + " {_,");
                    out.print(this.config.getSemRoles());
                    out.println("}");
                }
            }

            out.println(DATA);

            out.close();
            out = null;
        }
    }

    /**
     * Finds out the names of the output ARFF files (which contain the names of the predicates
     * in the output pattern) and stores them for later use. Heeds the "predicted"
     * parameter (see {@link StToArff}).
     *
     * @param sentence the sentence, containing all the needed predicates
     * @param pattern output file name pattern
     * @return
     */
    private Vector<String> findOutputs(Vector<String[]> sentence, int [] predNums, String pattern) {

        Vector<String> out = new Vector<String>(predNums.length);

        for (int i = 0; i < predNums.length; ++i){ // search for predicates

            if (sentence.get(predNums[i])[this.config.IDXI_FILLPRED].equals("Y")){ // a predicate has been found -> fill output file details

                String posSuffix = TAG_ERR;
                String predicate, fileName;

                if (sentence.get(predNums[i])[this.config.IDXI_POS].matches(this.config.nounPat)){
                    posSuffix = NOUN;
                }
                else if (sentence.get(predNums[i])[this.config.IDXI_POS].matches(this.config.verbPat)){
                    posSuffix = VERB;
                }
                predicate = sentence.get(predNums[i])[this.config.IDXI_LEMMA] + posSuffix;
                fileName = pattern.replace("**", predicate);
                this.usedFiles.put(predicate, fileName); // store the used file name
                out.add(fileName);
            }
        }

        return out;
    }

    /**
     * Reads one sentence from the input ST file.
     * @param in the input ST file, already open
     * @return the next sentence or null upon EOF
     */
    private Vector<String[]> readSentence(RandomAccessFile in) throws IOException {

        Vector<String []> res = new Vector<String []>();
        String word = in.readLine();

        while (word != null && !word.matches("^\\s*$")){

            res.add(word.split("\\t"));
            word = in.readLine();
        }

        return res.size() > 0 ? res : null;
    }

    /**
     * Parse the parameter with generated features setting and initialize all needed.
     */
    private void initGenFeats() {

        String [] featList;

        this.genFeats = new Vector<Feature>();

        if (this.parameters.get(GENERATE) == null){
            return;
        }
        
        featList = this.parameters.get(GENERATE).split(",");

        for (String featName : featList){

            Feature feat = Feature.createFeature(featName.trim(), this.config);

            if (feat == null){
                Logger.getInstance().message(this.id + ": Feature " + featName + " has not been found, skipping.",
                        Logger.V_WARNING);
            }
            else {
                this.genFeats.add(feat);
            }
        }
    }


    /**
     * Returns a list of positions where the predicates in this sentence are.
     *
     * @param sentence the sentence to be processed
     * @param sentenceId the current sentence id
     * @param fileName the file name
     * @return the list of predicate positions in the sentence
     */
    private int[] findPredicates(Vector<String[]> sentence, int sentenceId, String fileName) throws TaskException {

        int [] ret = new int [sentence.get(0).length - COMPULSORY_FIELDS];
        int pos = 0;

        for (int i = 0; i < sentence.size(); ++i){ // collect all predicate positions
            if (sentence.get(i)[this.config.IDXI_FILLPRED].equals("Y")){
                ret[pos] = i;
                pos++;
            }
        }
        if (pos != ret.length){ // check their number
            throw new TaskException(TaskException.ERR_IO_ERROR, this.id,
                    "Predicate and fillpred column numbers mismatch in " + fileName + " at sentence "
                    + sentenceId + ".");
        }
        return ret;
    }


    /**
     * Converts all the STRING attributes in the output files with the given predicate
     * to NOMINAL, using the StringToNominal WEKA filter. Uses values from all output files
     * for the same predicate, so that there's no problem with the classification later.
     *
     * @param predicate a predicate for which the files are to be converted
     */
    private void stringToNominal(String predicate) throws Exception {

        Instances bulk = this.getAllData(this.usedFiles.get(predicate)); // read all instances
        StringToNominal filter = new StringToNominal();        
        StringBuilder toConvert = new StringBuilder();
        String newHeader;

        // get the list of attributes to be converted
        for (int i = 0; i < bulk.numAttributes(); ++i){
            if (bulk.attribute(i).isString()){
                if (toConvert.length() != 0){
                    toConvert.append(",");
                }
                toConvert.append(Integer.toString(i+1));
            }
        }

        // convert the strings to nominal
        filter.setAttributeRange(toConvert.toString());
        filter.setInputFormat(bulk);
        bulk = Filter.useFilter(bulk, filter);

        // write the new nominal header into the old files
        bulk.delete();
        newHeader = bulk.toString();
        newHeader = newHeader.substring(0, newHeader.indexOf("\n@data\n") + 1);
        for (String file : this.usedFiles.get(predicate)){
            this.rewriteHeader(file, newHeader);
        }
    }

    /**
     * Adds-up all instances from the given list of output ARFF files.
     *
     * @param files the list of files to read
     * @return all the instances in the files
     * @throws Exception if an I/O error occurs
     */
    private Instances getAllData(Set<String> files) throws Exception {

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        boolean first = true;

        // write all parts into a buffer
        for (String file : files){

            ConverterUtils.DataSource in = new ConverterUtils.DataSource(file);
            Instances structure = in.getStructure();

            if (first == true){
                bos.write(structure.toString().getBytes("UTF-8"));
                first = false;
            }

            while (in.hasMoreElements(structure)){
                bos.write(in.nextElement(structure).toString().getBytes("UTF-8"));
                bos.write(System.getProperty("line.separator").getBytes());
            }
            in.reset();
        }

        // read the results from the buffer
        ConverterUtils.DataSource bulkIn = new ConverterUtils.DataSource(new ByteArrayInputStream(bos.toByteArray()));
        return bulkIn.getDataSet();
    }

    /**
     * Rewrites a header of an ARFF file with a new one, preserving the data untouched.
     * Keeps the file in place.
     *
     * @param file the file to be rewritten
     * @param newHeader the new header contents
     */
    private void rewriteHeader(String file, String newHeader) throws IOException, TaskException {

        RandomAccessFile in = new RandomAccessFile(file, "rw");
        String line = in.readLine();
        long origLength = in.length();
        long dataStart = 0;
        int distance;
        byte [] buf;

        // find the length of the original header
        while (line != null && !line.startsWith("@DATA")){
            dataStart = in.getFilePointer();
            line = in.readLine();
        }
        if (line == null){
            throw new TaskException(TaskException.ERR_IO_ERROR, this.id, "Cannot find data in " + file + ".");
        }

        // correct file length
        distance = (int)(- dataStart + newHeader.getBytes("UTF-8").length);

        // move the data
        buf = new byte [(int)(origLength - dataStart)];
        in.seek(dataStart);
        in.readFully(buf);
        in.setLength(origLength + distance);
        in.seek(dataStart + distance);
        in.write(buf);

        // write the new header
        in.seek(0);
        in.write(newHeader.getBytes("UTF-8"));

        in.close();
        in = null;
    }


    /**
     * This stores all the configuration needed for the conversion an the generated
     * features as well.
     */
    public class StToArffConfig {

        /* CONSTANTS */

        /** Index of the word id in the ST file */
        public final int IDXI_WORDID = 0;
        /** Index of the FORM attribute in the ST file */
        public final int IDXI_FORM = 1;
        /** Index of the FILLPRED attribute in the ST file */
        public final int IDXI_FILLPRED = 12;
        /** Starting index of the semantic roles (APRED) in the ST file */
        public final int IDXI_SEMROLE = 14;
        /** Index of the POS attribute in the ST file */
        public final int IDXI_POS;
        /** Index of the lemma attribute in the ST file */
        public final int IDXI_LEMMA;
        /** Index of the HEAD attribute in the ST file */
        public final int IDXI_HEAD;
        /** Index of the DEPREL attribute in the ST file */
        public final int IDXI_DEPREL;
        /** Index of the DEPREL attribute in the ST file */
        public final int IDXI_FEAT;

        /* VARIABLES */

        /** Will be FEAT values used for this language? */
        public boolean posFeat;
        /** Possible semantic roles */
        public String [] semRoles;

        /** Tag pattern for verbs in the ST file */
        public String nounPat;
        /** Tag pattern for nouns in the ST file */
        public String verbPat;

        /** Path to config file */
        private final String configFile;

        /** Use predicted POS and DEPREL values ? */
        private final boolean usePredicted;
        /** 
         * Always -1, if usePredicted is true, 1 otherwise -- in order to cover the second 
         * (predicted or non-predicted) member by IDXI_ .. + predictedNon.
         */
        private final int predictedNon;

        /** The parameters of the {@link StToArff} task. */
        private final Hashtable<String, String> taskParameters;
        /** The id of the {@link StToArff} task */
        private final String taskId;

        /* METHODS */

        StToArffConfig(StToArff task){

            this.taskParameters = task.parameters;
            this.configFile = this.taskParameters.get(StToArff.LANG_CONF);
            this.usePredicted = task.getBooleanParameterVal(StToArff.PREDICTED);
            this.taskId = task.getId();

            if (this.usePredicted){
                IDXI_POS = 5;
                IDXI_LEMMA = 3;
                IDXI_HEAD = 9;
                IDXI_DEPREL = 11;
                IDXI_FEAT = 7;
                this.predictedNon = -1;
            }
            else {
                IDXI_POS = 4;
                IDXI_LEMMA = 2;
                IDXI_HEAD = 8;
                IDXI_DEPREL = 10;
                IDXI_FEAT = 6;
                this.predictedNon = 1;
            }
        }

        /**
         * Reads the language configuration from a file, i.e\. all SEMREL values,
         * noun and verb POS tag pattern and usage of FEAT for the current ST file language.
         *
         * @throws FileNotFoundException
         * @throws IOException
         * @throws TaskException
         */
        private void init() throws FileNotFoundException, IOException {

            RandomAccessFile in = new RandomAccessFile(this.configFile, "r");
            this.posFeat = in.readLine().matches("\\s*") ? false : true;
            this.nounPat = in.readLine();
            this.verbPat = in.readLine();
            String semRolesStr = in.readLine();

            in.close();
            in = null;

            if (semRolesStr == null || this.nounPat == null || this.verbPat == null){
                throw new IOException();
            }

            this.semRoles = semRolesStr.split("\\s+");
        }

        /**
         * Return a comma-separated list of possible semantic roles
         * @return list of semantic roles
         */
        String getSemRoles(){
            return this.listMembers(this.semRoles);
        }

        /**
         * Return a comma-separated list of items from an array
         * @param arr the array to be transformed to a string
         * @return a comma-separated list in string form
         */
        private String listMembers(String [] arr){

            StringBuilder sb = new StringBuilder();

            if (arr == null || arr.length == 0){
                return "";
            }

            sb.append("\"" + this.escape(arr[0]) + "\"");
            for (int j = 1; j < arr.length; ++j){
                sb.append(",\"" + this.escape(arr[j]) + "\"");
            }
            return sb.toString();
        }

        /**
         * Escapes a string to be used in quotes in an ARFF file.
         * 
         * @param str the input string
         * @return the escaped version
         */
        public String escape(String str){
            return str.replace("\\", "\\\\").replace("\"", "\\\"");
        }

        /**
         * Returns the value of the given task parameter.
         * @param paramName the desired parameter name
         * @return the value of the given task parameter
         */
        public String getTaskParameter(String paramName){
            return this.taskParameters.get(paramName);
        }

        /**
         * Returns the id of the current task.
         * @return the id of the current task
         */
        public String getTaskId(){
            return this.taskId;
        }
    }
}
