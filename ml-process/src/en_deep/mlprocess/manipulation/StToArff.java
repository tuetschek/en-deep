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
import en_deep.mlprocess.Pair;
import en_deep.mlprocess.exception.TaskException;
import en_deep.mlprocess.manipulation.genfeat.Feature;
import en_deep.mlprocess.utils.FileUtils;
import en_deep.mlprocess.utils.StringUtils;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.RandomAccessFile;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Set;
import java.util.Vector;
import weka.core.Instances;
import weka.core.converters.ConverterUtils;

/**
 * This class converts the original ST file format of the CoNLL task to ARFF file format
 * and creates specified features.
 *
 * @author Ondrej Dusek
 */
public class StToArff extends StManipulation {

    /* CONSTANTS */

    /** The divide_ams parameter name */
    private static final String DIVIDE_AMS = "divide_ams";
    /** The pred_only parameter name */
    private static final String PRED_ONLY = "pred_only";
    /** The prune parameter name */
    private static final String PRUNE = "prune";
    /** The generate parameter name */
    private static final String GENERATE = "generate";
    /** The omit_semclass parameter name */
    private static final String OMIT_SEMCLASS = "omit_semclass";
    /** The divide_senses parameter name */
    private static final String DIVIDE_SENSES = "divide_senses";
    /** The 'filt_pos' parameter name */
    private static final String FILTER_POS = "filt_pos";
    /** The 'one_file' parameter name */
    private static final String ONE_FILE_MODE = "one_file";

    /** Attribute definition start in ARFF files */
    public static final String ATTRIBUTE = "@ATTRIBUTE";
    /** Specification of an attribute as CLASS in ARFF files @todo move to StReader */
    public static final String CLASS = "";
    /** Specification of an attribute as INTEGER in ARFF files @todo move to StReader */
    public static final String INTEGER = "INTEGER";
    /** Specification of an attribute as STRING in ARFF files @todo move to StReader */
    public static final String STRING = "STRING";

    /** Semantic relation (all/valency args) attribute name in ARFF files */
    private static final String SEM_REL = "semrel";
    /** Semantic relation (adveribials/references) name */
    private static final String SEM_REL_AMS = "semrel-ams";

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

    /** The header for the "file attribute */
    private static final String FILE_ATTR_HEADER = "@ATTRIBUTE file STRING";

    
    /** Index of the FEAT attribute in the output ARFF file */
    private static final int IDXO_FEAT = 7;

    /* DATA */

    /** Divide AM-s and references ? */
    private boolean divideAMs;
    /** Output predicates only ? */
    private boolean predOnly;
    /** Omit semantic class in the ouptut ? */
    private boolean omitSemClass;
    /** Divide the data according to pred, not lemma ? */
    private boolean divideSenses;
    /** Prune the argument candidates to the syntactical neighborhood of the predicate ? */
    private boolean prune;
    /** Is the whole thing running in one-file mode */
    private boolean oneFileMode;
    /** List of POS which should be filtered on the output (or null if none) */
    private String [] filteredPOS;

    /** Features to be generated */
    private Vector<Feature> genFeats;

    /** Used output files (for reprocessing) */
    private HashMultimap<String, String> usedFiles;
    /** File names with already written headers */
    private HashSet<String> writtenHeaders;

    /** The expanded part of the id, or empty string */
    private String outPrefix;

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
     * <li><tt>lang_conf</tt> -- path to the language reader file, that contains a FEAT usage indication ("1"/isEmpty line), followed
     * by noun and verb tag regexp patterns (each on separate line) and a list of semantic roles (one line, space-separated).</li>
     * <li><tt>predicted</tt> -- if set to non-false, work with predicted lemma, POS and only </li>
     * <li><tt>divide_ams</tt> -- if set to non-false, there will be two semantic relation attributes -- separate for
     * valency arguments and for adverbials &amp; references.</li>
     * <li><tt>generate</tt> -- comma-separated list of features to be generated</li>
     * <li><tt>pred_only</tt> -- if set to non-false, only predicates are outputted, omitting all other words in a sentence</li>
     * <li><tt>omit_semclass</tt> -- if set to non-false, the semantic class is not outputted at all</li>
     * <li><tt>divide_senses</tt> -- if set to non-false, the data are divided according to the sense of predicates, too</li>
     * <li><tt>prune</tt> -- if set, the argument candidates are pruned (syntactical neighborhood of the predicate only)</li>
     * <li><tt>filt_pos</tt> -- (optional) provide a space-separated list of POS which should be filtered at the output,
     * e.g. meaningful for English are: "'' ( ) , . : `` EX HYPH LS NIL POS"</li>
     * <li><tt>one_file</tt> -- this turns the one-file-mode on. If set, the headers won't be set to nominal and the output
     * will go into one file only</li>
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

        // initialize boolean parameters
        this.divideAMs = this.getBooleanParameterVal(DIVIDE_AMS);
        this.predOnly = this.getBooleanParameterVal(PRED_ONLY);
        this.omitSemClass = this.getBooleanParameterVal(OMIT_SEMCLASS);
        this.divideSenses = this.getBooleanParameterVal(DIVIDE_SENSES);
        this.prune = this.getBooleanParameterVal(PRUNE);
        this.oneFileMode = this.getBooleanParameterVal(ONE_FILE_MODE);

        // initialize string parameter
        if (this.getParameterVal(FILTER_POS) != null){
            this.filteredPOS = this.getParameterVal(FILTER_POS).split("\\s+");
        }

        // initialize features to be generated
        this.initGenFeats();

        // initialize the used output files lists
        this.usedFiles = HashMultimap.create();
        if (!this.oneFileMode){
            this.writtenHeaders = new HashSet<String>();
        }


        // check outputs
        if (input.size() != output.size()){
            throw new TaskException(TaskException.ERR_WRONG_NUM_OUTPUTS, this.id);
        }
        if (!this.oneFileMode){
            // checks if there are "**" patterns in outputs (just simple string check is sufficient, Task expansion
            // ensures that there are no unwanted "*"'s.
            for (String outputFile: this.output){
                if (!outputFile.contains("**")){
                    throw new TaskException(TaskException.ERR_OUTPUT_PATTERNS, this.id, "Outputs must contain '**' pattern.");
                }
            }
        }
        else {
            // however, we don't want any patterns in one-file mode !
            this.eliminatePatterns(this.output);
        }

        this.outPrefix = this.getExpandedPartOfId();
        if (!this.outPrefix.equals("")){
            this.outPrefix += "_";
        }
    }


    /**
     * Performs the task operation -- converts all the given input file(s) according to the parameters.
     * @throws TaskException
     */
    @Override
    public void perform() throws TaskException {

        try {            

            for (int i = 0; i < this.input.size(); ++i){
                // convert the files
                this.convert(this.input.get(i), this.output.get(i));
            }

            if (!this.oneFileMode){
                // now convert the string attributes to nominal types in the output
                for (String predicate : this.usedFiles.keySet()){
                    Logger.getInstance().message(this.id + ": Rewriting header(s) for " + predicate + " ...",
                            Logger.V_DEBUG);
                    this.stringToNominal(predicate);
                }
            }
        }
        catch (TaskException e){
            Logger.getInstance().message("Sentence: " + this.reader.getSentenceId() + " -- "
                    + this.reader.getSentenceText(), Logger.V_DEBUG);
            Logger.getInstance().logStackTrace(e, Logger.V_DEBUG);
            throw e;
        }
        catch (Exception e){
            Logger.getInstance().message("Sentence: " + this.reader.getSentenceId() + " -- "
                    + this.reader.getSentenceText(), Logger.V_DEBUG);
            Logger.getInstance().logStackTrace(e, Logger.V_DEBUG);
            throw new TaskException(TaskException.ERR_IO_ERROR, this.id, e.getMessage());
        }
    }


    /**
     * Performs the conversion of one ST-file into multiple (or one) ARFF files.
     * @param st the input file name
     * @param arff the output file name
     * @throws TaskException
     */
    private void convert(String st, String arff) throws TaskException, FileNotFoundException, IOException {

        int [] predNums;
        Vector<Pair<String, String>> outputs = null;
        FileOutputStream os = null;
        PrintStream out = null;

        this.reader.setInputFile(st);

        if (this.oneFileMode){
            os = new FileOutputStream(arff);
            out = new PrintStream(os);
            this.writeHeader(out, StringUtils.truncateFileName(arff));
        }

        while (this.reader.loadNextSentence()){

            predNums = this.reader.getPredicates();
            outputs = this.findOutputs(predNums, arff); // find corresponding output predicate & file names
            if (!this.oneFileMode){
                this.writeHeaders(outputs); // prepare output file headers
            }

            // for all predicates, write the sentence to an output file
            for (int i = 0; i < predNums.length; ++i){

                if (!this.oneFileMode){
                    os = new FileOutputStream(outputs.get(i).second, true);
                    out = new PrintStream(os);
                }

                for (int j = 0; j < this.reader.length(); ++j){

                    String [] word = this.reader.getWord(j);

                    // skip non-predicate or pruned lines or filtered PsOS if such setting is imposed
                    if (this.predOnly && j != predNums[i]
                            || this.prune && !this.reader.isInNeighborhood(predNums[i], j)
                            || this.isFiltered(word[this.reader.IDXI_POS])){
                        continue;
                    }

                    // print the compulsory fields
                    if (this.oneFileMode){
                        out.print("\"" + StringUtils.escape(outputs.get(i).first) + "\",");
                    }
                    out.print(this.reader.getSentenceId());

                    for (int k = 0; k < this.reader.COMPULSORY_FIELDS; ++k){

                        if (k >= word.length){ // treat missing values as missing values (evaluation file)
                            out.print(",?");
                            continue;
                        }
                        if (this.reader.posFeat == false &&
                                (k == this.reader.IDXI_FEAT || k == this.reader.IDXI_FEAT + this.reader.predictedNon)){
                            continue; // skip FEAT if we're not using them
                        }
                        if (k == this.reader.IDXI_WORDID || k == this.reader.IDXI_HEAD
                                || k == this.reader.IDXI_HEAD + this.reader.predictedNon){
                            out.print("," + word[k]);
                        }
                        else { // quote non-numeric fields
                            out.print(",\"" + word[k] + "\"");
                        }
                    }
                    
                    // add generated features
                    for (Feature f : this.genFeats){
                        out.print("," + f.generate(j, predNums[i]));
                    }

                    // print the resulting semantic relation to the given predicate
                    if (!this.omitSemClass){
                        
                        if (word.length < this.reader.IDXI_SEMROLE + 1){ // evaluation file -> missing value(s)
                            out.print(this.divideAMs ? ",?,?" : ",?");
                        }
                        else if (this.divideAMs){
                            if (word[this.reader.IDXI_SEMROLE + i].matches(this.reader.amsPat)){
                                out.print(",_,\"" + word[this.reader.IDXI_SEMROLE + i] + "\"");
                            }
                            else {
                                out.print(",\"" + word[this.reader.IDXI_SEMROLE + i] + "\",_");
                            }
                        }
                        else {
                            out.print(",\"" + word[this.reader.IDXI_SEMROLE + i] + "\"");
                        }
                    }

                    out.println();
                }

                if (!this.oneFileMode){
                    out.close();
                    out = null;
                }
            }

            if (this.reader.getSentenceId() % 1000 == 0){
                Logger.getInstance().message(this.id + ": Input: " + st + ", sentence: " + this.reader.getSentenceId(),
                        Logger.V_DEBUG);
            }
        }
    }


    /**
     * Writes output ARFF files headers for the given predicates and file names. Heeds the "multiclass" parameter
     * (see {@link StToArff}). Some parameter types are left as STRING at first. They are converted
     * to class values later. 
     * </p><p>
     * Keeps all the file names where the headers have already been written in the
     * {@link #writtenHeaders} member, so that no headers are written twice.
     * <p>
     *
     * @param outputs a list of predicate-file name pairs
     */
    private void writeHeaders(Vector<Pair<String, String>> outputs) throws FileNotFoundException {

        for (Pair<String, String> pfNames : outputs){

            String predName = pfNames.first;
            String fileName = pfNames.second;

            if (this.writtenHeaders.contains(fileName)){ // only for files that haven't been written yet
                continue;
            }
            FileOutputStream os = new FileOutputStream(fileName);
            PrintStream out = new PrintStream(os);

            this.writeHeader(out, predName);

            out.close();
            out = null;

            // store the filename so that we don't write the headers again
            this.writtenHeaders.add(fileName);
        }
    }

    /**
     * This writes one ARFF file header with STRING fields into the given output stream.
     * @param out the output stream to write to
     * @param relationName the new ARFF relation name
     */
    private void writeHeader(PrintStream out, String relationName) {

        out.println(RELATION + " \"" + StringUtils.escape(relationName) + "\"");

        // print the "file" parameter, if in one-file mode
        if (this.oneFileMode){
            out.println(FILE_ATTR_HEADER);
        }

        // print the constant fields that are always present
        for (int i = 0; i < HEADER.length; ++i) {
            if (this.reader.posFeat && (i == IDXO_FEAT || i == IDXO_FEAT + 1)) {
                out.println(HEADER[i]);
            }
            else if (i == IDXO_FEAT || i == IDXO_FEAT + 1) {
                // do not print FEAT headers if we're not using them
                continue;
            }
            else {
                out.println(HEADER[i]);
            }
        }
        // print generated features' headers
        for (Feature f : this.genFeats) {
            out.println(f.getHeader());
        }
        // print the target class / classes header(s) (according to the "multiclass" parameter),
        // if supposed to do so at all (heed the "omit_semclass" parameter)
        if (!this.omitSemClass) {
            if (this.divideAMs) {
                out.print(ATTRIBUTE + " " + SEM_REL + " " + CLASS + " {_,");
                out.print(this.reader.getSemRoles(false));
                out.println("}");
                out.print(ATTRIBUTE + " " + SEM_REL_AMS + " " + CLASS + " {_,");
                out.print(this.reader.getSemRoles(true));
                out.println("}");
            }
            else {
                out.print(ATTRIBUTE + " " + SEM_REL + " " + CLASS + " {_,");
                out.print(this.reader.getSemRoles());
                out.println("}");
            }
        }
        out.println(DATA);
    }

    /**
     * Finds the names of the predicates and of the output ARFF files (which contain the names of the predicates
     * in the output pattern) and stores them for later use in {@link #usedFiles}. Heeds the "predicted"
     * parameter (see {@link StToArff}).
     *
     * @param predNums word numbers in the current sentence that contain predicates
     * @param pattern output file name pattern
     * @return a list of predicate-file name pairs
     */
    private Vector<Pair<String, String>> findOutputs(int [] predNums, String pattern) {

        Vector<Pair<String, String>> outputs = new Vector<Pair<String, String>>(predNums.length);

        for (int i = 0; i < predNums.length; ++i){ // search for predicates

            String predicate, fileName;

            predicate = (this.divideSenses ?  this.reader.getWordInfo(predNums[i], this.reader.IDXI_PRED)
                    : this.reader.getWordInfo(predNums[i], this.reader.IDXI_LEMMA))
                    + this.reader.getPredicateType(this.reader.getWordInfo(predNums[i], this.reader.IDXI_POS));
            fileName = StringUtils.replace(pattern, this.outPrefix + predicate);
            this.usedFiles.put(predicate, fileName); // store the used file name
            outputs.add(new Pair<String, String>(predicate, fileName));
        }

        return outputs;
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

            Feature feat = Feature.createFeature(featName.trim(), this.reader);

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
     * Converts all the STRING attributes in the output files with the given predicate
     * to NOMINAL, using the StringToNominal WEKA filter. Uses values from all output files
     * for the same predicate, so that there's no problem with the classification later.
     *
     * @param predicate a predicate for which the files are to be converted
     */
    private void stringToNominal(String predicate) throws Exception {

        Instances bulk = this.getAllData(this.usedFiles.get(predicate)); // read all instances
        bulk = FileUtils.allStringToNominal(bulk);

        // write the new nominal header into the old files
        bulk.delete();
        String newHeader = bulk.toString();
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
     * Returns true if, according to the user settings, the given part-of-speech should be filtered on the output.
     * @param pos the part of speech
     * @return true if the given part-of-speech should be filtered
     */
    private boolean isFiltered(String pos) {

        if (this.filteredPOS != null){
            for (int i = 0; i < this.filteredPOS.length; i++) {
                if (this.filteredPOS[i].equals(pos)){
                    return true;
                }
            }
        }
        return false;
    }
    
}
