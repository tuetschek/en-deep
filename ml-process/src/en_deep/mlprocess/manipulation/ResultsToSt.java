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

import en_deep.mlprocess.Logger;
import en_deep.mlprocess.Task;
import en_deep.mlprocess.exception.TaskException;
import en_deep.mlprocess.utils.FileUtils;
import java.io.PrintStream;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Vector;
import weka.core.Instances;

/**
 * This adds the results from the classification files back to the ST file, possibly rewriting some fields.
 * @author Ondrej Dusek
 */
public class ResultsToSt extends StManipulation {

    /* CONSTANTS */

    /** The 'mode' parameter name */
    private static final String MODE = "mode";


    /** This represents the possible work modes of this tasks */
    private enum Mode {

        PRED,
        ARG;

        /**
         * This constructs a {@link Mode} object from a string that contains one of the allowed values
         * @return the mode-representation of the string, or null.
         */
        static Mode getFromString(String str){
            if (str == null){
                return null;
            }
            if (str.equalsIgnoreCase("pred")){
                return PRED;
            }
            if (str.equalsIgnoreCase("arg")){
                return ARG;
            }
            return null;
        }
    }

    /* DATA */

    /** The current work mode of the task */
    private final Mode mode;

    private Hashtable<String, PredicatePrediction> predicatePredictions;

    /* METHODS */

    /**
     * This {@link Task} has the following compulsory parameters:
     * <ul>
     * <li>mode</li> -- must be <tt>pred</tt> (filling predicates) or <tt>arg</tt> filling arguments
     * <li><tt>lang_conf</tt> -- path to the language reader file, that contains a FEAT usage indication ("1"/isEmpty line), followed
     * by noun and verb tag regexp patterns (each on separate line) and a list of semantic roles (one line, space-separated).</li>
     * <li><tt>predicted</tt> -- if set to non-false, work with predicted lemma, POS and only </li>
     * </ul>
     * This checks inputs and outputs -- the first input must be the original ST file, the output must be only one
     * ST output file. The rest of inputs must be the classified predicates / arguments from the original ST file.
     *
     * @param id
     * @param parameters
     * @param input
     * @param output
     */
    public ResultsToSt(String id, Hashtable<String, String> parameters, Vector<String> input, Vector<String> output) 
            throws TaskException {

        super(id, parameters, input, output);

        if (this.input.size() < 2){
            throw new TaskException(TaskException.ERR_WRONG_NUM_INPUTS, this.id, "Not enough inputs.");
        }
        if (this.output.size() != 1){
            throw new TaskException(TaskException.ERR_WRONG_NUM_OUTPUTS, this.id, "This task requires one output ST file.");
        }

        this.mode = Mode.getFromString(this.getParameterVal(MODE));
        if (this.mode == null){
            throw new TaskException(TaskException.ERR_INVALID_PARAMS, this.id, "'mode' parameter missing or incorrect.");
        }
        if (this.mode == Mode.PRED){
            this.predicatePredictions = new Hashtable<String, PredicatePrediction>();
        }
    }


    @Override
    public void perform() throws TaskException {
        
        try {
            // initialize
            String stIn = this.input.remove(0);
            PrintStream out = new PrintStream(this.output.get(0));
            this.reader.setInputFile(stIn);
            
            // first, load all the predicted values
            for (String file: this.input){
                Logger.getInstance().message("Loading predictions from " + file + "...", Logger.V_DEBUG);
                this.loadPrediction(file);
            }

            // then, stream-process the ST file
            int ctr = 0;
            while (this.reader.loadNextSentence()){

                switch (this.mode){
                    case PRED:
                        this.rewritePredicates();
                        out.print(this.reader.getSentenceST());
                        out.print("\n"); // force unix-LF as in original format
                        break;
                }

                if (ctr > 0 && ctr % 1000 == 0){
                    Logger.getInstance().message("Processing sentence " + ctr + "...", Logger.V_DEBUG);
                }
                ctr++;
            }
            out.close();
        }
        catch (TaskException e){
            throw e;
        }
        catch (Exception e) {
            Logger.getInstance().logStackTrace(e, Logger.V_DEBUG);
            Logger.getInstance().message(e.getMessage(), Logger.V_IMPORTANT);
        }
        
    }


    /**
     * This loads all the predicted values (according to {@link #mode}) from the given file.
     * @param file the file to be read
     */
    private void loadPrediction(String file) throws Exception {
        Instances data = FileUtils.readArff(file);
        String predicateName = data.instance(0).stringValue(data.attribute(this.reader.LEMMA))
                + this.reader.getPredicateType(data.instance(0).stringValue(data.attribute(this.reader.POS)));

        this.predicatePredictions.put(predicateName, new PredicatePrediction(data));
    }

    /**
     * This stores all the predictions for one of the predicates (in the order they appear in the ST file.
     */
    private static class PredicatePrediction {

        /* CONSTANT */

        /** The predicate attribute name */
        private static final String ATTR_NAME = "pred";

        /* DATA */
        
        /** The predicate name (to be prepended before the senses) */
        private String predPrefix;
        /** The current instance index */
        private int current = 0;
        /** List of all the predicate senses */
        private int [] senses;

        /* METHODS */

        /**
         * This loads all the values of the prediction and sets the current predicate index to 0.
         * @param data
         */
        public PredicatePrediction(Instances data) {
            
            this.predPrefix = data.attribute(ATTR_NAME).value(0);
            this.predPrefix = this.predPrefix.substring(0, this.predPrefix.indexOf("."));

            this.senses = new int [data.numInstances()];

            int [] valOrder = new int [data.attribute(ATTR_NAME).numValues()];
            Enumeration<String> vals = data.attribute(ATTR_NAME).enumerateValues();
            int j = 0;
            while (vals.hasMoreElements()){
                String val = vals.nextElement();
                valOrder[j] = Integer.parseInt(val.substring(val.indexOf(".") + 1));
                j++;
            }

            double [] instVals = data.attributeToDoubleArray(data.attribute(ATTR_NAME).index());
            this.senses  = new int [instVals.length];
            for (int i = 0; i < instVals.length; ++i){
                this.senses[i] = valOrder[(int) instVals[i]];
            }
        }

        /**
         * This returns the next predicted predicate sense value.
         * @return the next predicate
         */
        public String getNext(){

            String ret = this.predPrefix + "." + (this.senses[this.current] < 10 ? "0" : "")
                    + Integer.toString(this.senses[this.current]);
            this.current++;
            return ret;
        }
    }

    /**
     * This rewrites all the predicates in the current sentence with their predicted values that
     * are loaded in {@link #predicatePredictions}.
     */
    private void rewritePredicates() {

        for (int i = 0; i < this.reader.length(); ++i){
            if (this.reader.getWordInfo(i, this.reader.IDXI_FILLPRED).equals("Y")){

                String predName = this.reader.getWordInfo(i, this.reader.IDXI_LEMMA) +
                        this.reader.getPredicateType(this.reader.getWordInfo(i, this.reader.IDXI_POS));

                this.reader.setField(this.reader.IDXI_PRED, i, this.predicatePredictions.get(predName).getNext());
            }
        }
    }
}
