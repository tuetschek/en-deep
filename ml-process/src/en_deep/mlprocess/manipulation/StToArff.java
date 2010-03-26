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

import en_deep.mlprocess.Task;
import en_deep.mlprocess.exception.TaskException;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.RandomAccessFile;
import java.util.Hashtable;
import java.util.Vector;

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

    /** Number of compulsory fields that are in each sentence */
    private static final int COMPULSORY_FIELDS = 14;

    /** Output file suffix for noun predicates */
    private static final String NOUN = ".n";
    /** Output file suffix for verb predicates */
    private static final String VERB = ".v";
    /** Output file suffix for errorneously tagged predicates */
    private static final String TAG_ERR = ".e";

    /** Caption of ARFF files */
    private static final String RELATION = "@RELATION";

    /** List of attributes in ARFF files */
    private static final String [] HEADER = {
        "@ATTRIBUTE sent-id INTEGER",
        "@ATTRIBUTE word-id INTEGER",
        "@ATTRIBUTE form STRING",
        "@ATTRIBUTE lemma STRING",
        "@ATTRIBUTE p-lemma STRING",
        "@ATTRIBUTE pos CLASS",
        "@ATTRIBUTE p-pos CLASS",
        "@ATTRIBUTE feat CLASS",
        "@ATTRIBUTE p-feat CLASS",
        "@ATTRIBUTE head INTEGER",
        "@ATTRIBUTE p-head INTEGER",
        "@ATTRIBUTE deprel CLASS",
        "@ATTRIBUTE p-deprel CLASS",
        "@ATTRIBUTE fillpred CLASS {Y,-}",
        "@ATTRIBUTE pred STRING"
    };

    /** Start of the data section in ARFF files */
    private static final String DATA = "@DATA";

    /** Index of the FEAT attribute in the output ARFF file */
    private static final int IDXO_FEAT = 7;
    /** Index of the POS attribute in the output ARFF file */
    private static final int IDXO_POS = 5;
    /** Index of the DEPREL attribute in the output ARFF file */
    private static final int IDXO_DEPREL = 11;
    /** Index of the FILLPRED attribute in the ST file */
    private static final int IDXI_FILLPRED = 12;
    /** Index of the POS attribute in the ST file */
    private static final int IDXI_POS = 4;
    /** Index of the lemma attribute in the ST file */
    private static final int IDXI_LEMMA = 2;

    /* DATA */

    /** Possible POS tags in the ST file */
    private String [] pos;
    /** Possible FEAT values in the ST file (null if not used at all) */
    private String [] feat;
    /** Possible DEPREL values in the ST file */
    private String [] deprel;
    /** Possible semantic roles */
    private String [] semRoles;

    /** Tag pattern for verbs in the ST file */
    private String nounPat;
    /** Tag pattern for nouns in the ST file */
    private String verbPat;

    /** Sentence ID generation -- last used value */
    private static int lastId = 0;


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
     * <li><tt>lang_conf</tt> -- path to the language config file, that contains a list of POS, FEAT and DEPREL for the current
     * language (on three lines and space-separated, FEAT specification should be left blank if FEAT is not used), followed
     * by noun and verb tag regexp patterns (each on separate line) and a list of semantic roles (space-separated)</li>
     * <li><tt>predicted</tt> TODO work with predicted arguments only ?? </li>
     * </ul>
     *
     * @param id the task id
     * @param parameters the task parameters
     * @param input the input data sets or files
     * @param output the output data sets or files
     */
    public StToArff(String id, Hashtable<String, String> parameters,
            Vector<String> input, Vector<String> output) throws TaskException {
        
        super(id, parameters, input, output);

        // check parameters
        if (parameters.get(LANG_CONF) == null){
            throw new TaskException(TaskException.ERR_INVALID_PARAMS, this.id);
        }

        if (input.size() != output.size()){
            throw new TaskException(TaskException.ERR_WRONG_NUM_OUTPUTS, this.id);
        }
        // checks if there are "**" patterns in outputs (just simple string check is sufficient, Task expansion
        // ensures that there are no unwanted "*"'s.
        for (String outputFile: this.output){
            if (!outputFile.contains("**")){
                throw new TaskException(TaskException.ERR_OUTPUT_PATTERNS, this.id);
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
            this.readLangConf();

            for (int i = 0; i < this.input.size(); ++i){
                // convert the files
                this.convert(this.input.get(i), this.output.get(i));
            }
        }
        catch (TaskException e){
            throw e;
        }
        catch (Exception e){
            throw new TaskException(TaskException.ERR_IO_ERROR, this.id);
        }
    }

    /**
     * Performs one file conversion.
     * @param st the input file name
     * @param arff the output file name
     * @throws TaskException
     */
    private void convert(String st, String arff) throws TaskException, FileNotFoundException, IOException {

        RandomAccessFile in = new RandomAccessFile("st", "r");
        Vector<String []> sentence = this.readSentence(in);
        Vector<String> outFiles;
        int sentenceId;

        while (sentence != null){

            outFiles = this.findOutputs(sentence, arff); // find predicate names (output file names)
            this.writeHeaders(outFiles); // prepare output file headers
            sentenceId = generateSentenceId();

            // for all predicates, write the sentence to an output file
            for (int i = 0; i < sentence.get(0).length - COMPULSORY_FIELDS; ++i){

                FileOutputStream os = new FileOutputStream(outFiles.get(i), true);
                PrintStream out = new PrintStream(os);

                for (String [] word : sentence){

                    out.print(sentenceId);
                    for (int j = 0; j < COMPULSORY_FIELDS; ++j){
                        if (this.feat == null && (j == IDXO_FEAT || j == IDXO_FEAT + 1)){
                            continue;
                        }
                        out.print("," + word[j]);
                    }
                    // TODO add generated features
                    // TODO add 0/1 for argument types !
                }

                out.close();
            }

            sentence = this.readSentence(in); // read next sentence
        }

        in.close();
    }


    /**
     * Reads the language configuration from a file, i.e\. all POS, FEAT and DEPREL values,
     * noun and verb POS tag pattern for the current ST file language.
     * 
     * @throws FileNotFoundException
     * @throws IOException
     * @throws TaskException
     */
    private void readLangConf() throws FileNotFoundException, IOException, TaskException {

        RandomAccessFile in = new RandomAccessFile(this.parameters.get(LANG_CONF), "r");
        String posStr = in.readLine();
        String featStr = in.readLine();
        String deprelStr = in.readLine();
        this.nounPat = in.readLine();
        this.verbPat = in.readLine();
        String semRolesStr = in.readLine();

        in.close();

        if (pos == null || feat == null || deprel == null || this.nounPat == null || this.verbPat == null){
            throw new TaskException(TaskException.ERR_IO_ERROR, this.id);
        }

        this.pos = posStr.split("\\s+");
        this.feat = featStr.split("\\s+");
        if (this.feat.length == 0){
            this.feat = null;
        }
        this.deprel = deprelStr.split("\\s+");
        this.semRoles = semRolesStr.split("\\s+");

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
     * Writes output ARFF files headers for the given file names.
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

            for (int i = 0; i < HEADER.length; ++i){

                if (this.feat != null && (i == IDXO_FEAT || i == IDXO_FEAT + 1)){
                    out.print(HEADER[i] + " {");
                    out.print(this.feat[0]);
                    for (int j = 1; j < this.feat.length; ++j){
                        out.print("," + this.feat[j]);
                    }
                    out.println("}");
                }
                else if (i == IDXO_FEAT || i == IDXO_FEAT + 1){
                    continue;
                }
                else if (i == IDXO_POS || i == IDXO_POS + 1){
                    out.print(HEADER[i] + " {");
                    out.print(this.pos[0]);
                    for (int j = 1; j < this.pos.length; ++j){
                        out.print("," + this.pos[j]);
                    }
                    out.println("}");
                }
                else if (i == IDXO_DEPREL || i == IDXO_DEPREL + 1){
                    out.print(HEADER[i] + " {");
                    out.print(this.deprel[0]);
                    for (int j = 1; j < this.deprel.length; ++j){
                        out.print("," + this.deprel[j]);
                    }
                    out.println("}");
                }
                else {
                    out.println(HEADER[i]);
                }
            }

            out.println(DATA);

            out.close();
        }
    }

    /**
     * Finds out the names of the output ARFF files (which contain the names of the predicates
     * in the output pattern).
     *
     * @param sentence the sentence, containing all the needed predicates
     * @param pattern output file name pattern
     * @return
     */
    private Vector<String> findOutputs(Vector<String[]> sentence, String pattern) {

        Vector<String> out = new Vector<String>(sentence.get(0).length - COMPULSORY_FIELDS);

        for (int i = 0; i < sentence.size(); ++i){ // search for predicates

            if (sentence.get(i)[IDXI_FILLPRED].equals("Y")){ // a predicate has been found -> fill output file details

                String posSuffix = TAG_ERR;
                if (sentence.get(i)[IDXI_POS].matches(this.nounPat)){
                    posSuffix = NOUN;
                }
                else if (sentence.get(i)[IDXI_POS].matches(this.verbPat)){
                    posSuffix = VERB;
                }
                out.add(pattern.replace("**", sentence.get(i)[IDXI_LEMMA] + posSuffix));
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
}
