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
import en_deep.mlprocess.manipulation.DataReader.WordInfo;
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
public class StToArff extends StLikeConvertor {

    /* CONSTANTS */

    /** The divide_ams parameter name */
    static final String DIVIDE_AMS = "divide_ams";
    /** The pred_only parameter name */
    private static final String PRED_ONLY = "pred_only";
    /** The prune parameter name */
    private static final String PRUNE = "prune";
    /** The divide_senses parameter name */
    private static final String DIVIDE_SENSES = "divide_senses";
    /** The 'filt_pos' parameter name */
    private static final String FILTER_POS = "filt_pos";
    /** The 'one_file' parameter name */
    private static final String ONE_FILE_MODE = "one_file";   
    /** The omit_semclass parameter name */
    private static final String OMIT_SEMCLASS = "omit_semclass";
   
    /* DATA */

    /** Output predicates only ? */
    private boolean predOnly;
    /** Omit semantic class in the output ? */
    protected boolean omitSemClass;
    /** Divide the data according to pred, not lemma ? */
    private boolean divideSenses;
    /** Prune the argument candidates to the syntactical neighborhood of the predicate ? */
    private boolean prune;
    /** Is the whole thing running in one-file mode */
    private boolean oneFileMode;
    /** List of POS which should be filtered on the output (or null if none) */
    private String [] filteredPOS;

 
    /** Used output files (for re-processing) */
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
     * <li><tt>lang_conf</tt> -- path to the language reader file, that contains:
     * <ul>
     *   <li>a FEAT usage indication (name of the handling class derived from {@link Feature}, or empty line)</li>
     *   <li>noun and verb tag regexp patterns (each on separate line)</li>
     *   <li>list of all possible semantic roles (one line, space-separated)</li>
     *   <li>a regexp that catches all adverbial modifier semantic roles</li>
     *   <li>a space-separated list of additional columns in the ST file, if any</li>
     * </ul></li>
     * <li><tt>predicted</tt> -- if set to non-false, work with predicted lemma, POS and only </li>
     * <li><tt>divide_ams</tt> -- if set to non-false, there will be two semantic relation attributes -- separate for
     * valency arguments and for adverbials &amp; references.</li>
     * <li><tt>pred_only</tt> -- if set to non-false, only predicates are output, omitting all other words in the sentence</li>
     * <li><tt>divide_senses</tt> -- if set to non-false, the data are divided according to the sense of predicates, too</li>
     * <li><tt>prune</tt> -- if set, the argument candidates are pruned (syntactical neighborhood of the predicate only)</li>
     * <li><tt>filt_pos</tt> -- (optional) provide a space-separated list of POS which should be filtered at the output,
     * e.g. meaningful for English are: "'' ( ) , . : `` EX HYPH LS NIL POS"</li>
     * <li><tt>one_file</tt> -- this turns the one-file-mode on. If set, the headers won't be set to nominal and the output
     * will go into one file only</li>
     * </ul>
     * <p>
     * Additional parameters may be required by the individual generated {@link en_deep.mlprocess.manipulation.genfeat.Feature Feature}s,
     * or by the super-classes.
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

        // initialize boolean parameters (some of them are handled by the reader)
        this.omitSemClass = this.getBooleanParameterVal(OMIT_SEMCLASS);
        this.predOnly = this.getBooleanParameterVal(PRED_ONLY);
        this.divideSenses = this.getBooleanParameterVal(DIVIDE_SENSES);
        this.prune = this.getBooleanParameterVal(PRUNE);
        this.oneFileMode = this.getBooleanParameterVal(ONE_FILE_MODE);

        // initialize string parameter
        if (this.getParameterVal(FILTER_POS) != null){
            this.filteredPOS = this.getParameterVal(FILTER_POS).split("\\s+");
        }

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
        StReader stData = (StReader) this.reader; // for easier access to methods not in DataReader

        stData.setInputFile(st);

        if (this.oneFileMode){
            os = new FileOutputStream(arff);
            out = new PrintStream(os);
            this.writeHeader(out, StringUtils.truncateFileName(arff), true, !this.omitSemClass);
        }

        while (stData.loadNextSentence()){

            predNums = stData.getPredicates();
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

                for (int j = 0; j < stData.getSentenceLength(); ++j){

                    // skip non-predicate or pruned lines or filtered PsOS if such setting is imposed
                    if (this.predOnly && j != predNums[i]
                            || this.prune && !stData.isInNeighborhood(predNums[i], j)
                            || this.isFiltered(stData.getWordInfo(j, WordInfo.POS))){
                        continue;
                    }

                    // print the compulsory fields
                    if (this.oneFileMode){
                        out.print("\"" + StringUtils.escape(outputs.get(i).first) + "\",");
                    }
                    out.print(stData.getSentenceId());

                    out.print(stData.getInputFields(j));
                    
                    // add generated features
                    for (Feature f : this.genFeats){
                        out.print("," + f.generate(j, predNums[i]));
                    }

                    // print the resulting semantic relation to the given predicate
                    if (!this.omitSemClass){
                        out.print("," + stData.getSemRole(j, i));
                    }

                    out.println();
                }

                if (!this.oneFileMode){
                    out.close();
                    out = null;
                }
            }

            if (stData.getSentenceId() % 1000 == 0){
                Logger.getInstance().message(this.id + ": Input: " + st + ", sentence: " + stData.getSentenceId(),
                        Logger.V_DEBUG);
            }
        }

        if (this.oneFileMode){
            out.close();
            out = null;
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

            this.writeHeader(out, predName, false, !this.omitSemClass);

            out.close();
            out = null;

            // store the filename so that we don't write the headers again
            this.writtenHeaders.add(fileName);
        }
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

            predicate = (this.divideSenses ?  this.reader.getWordInfo(predNums[i], WordInfo.PRED)
                    : this.reader.getWordInfo(predNums[i], WordInfo.LEMMA))
                    + ((StReader) this.reader).getPredicateType(this.reader.getWordInfo(predNums[i], WordInfo.POS));
            fileName = StringUtils.replace(pattern, this.outPrefix + FileUtils.fileNameEncode(predicate));
            this.usedFiles.put(predicate, fileName); // store the used file name
            outputs.add(new Pair<String, String>(predicate, fileName));
        }

        return outputs;
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

    protected void initReader() throws TaskException{
        // initialize the ST reader
        try {
            this.reader = new StReader(this);
        }
        catch (IOException e){
            throw new TaskException(TaskException.ERR_INVALID_PARAMS, this.id,
                    "Cannot initialize ST reader, probably lang_conf file error:" + e.getMessage());
        }
    }
    
}
