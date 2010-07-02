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
import en_deep.mlprocess.utils.MathUtils;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Vector;
import weka.core.Attribute;
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

    /** This contains predictions for all predicates from the evaluation data results, if {@link #mode} is PRED */
    private Hashtable<String, PredicatePrediction> predicatePredictions;
    /** This contains predictions for all arguments, if {@link #mode} is ARG */
    private Hashtable<String, ArgumentPrediction> argumentPredictions;

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
        switch (this.mode){
            case PRED:
                this.predicatePredictions = new Hashtable<String, PredicatePrediction>();
                break;
            case ARG:
                this.argumentPredictions = new Hashtable<String, ArgumentPrediction>();
                break;
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
                        break;
                    case ARG:
                        this.rewriteArguments();
                        break;
                }
                out.print(this.reader.getSentenceST());
                out.print("\n"); // force unix-LF as in original format

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
     * @param file the name of the file to be read
     */
    private void loadPrediction(String file) throws Exception {

        Instances data = FileUtils.readArff(file);
        String predicateName = data.relationName();

        switch (this.mode){
            case PRED:
                this.predicatePredictions.put(predicateName, new PredicatePrediction(data));
                break;

            case ARG:
                this.argumentPredictions.put(predicateName, new ArgumentPrediction(data));
                break;
        }
    }

    /**
     * This rewrites all the predicates in the current sentence with their predicted values that
     * are loaded in {@link #predicatePredictions}.
     */
    private void rewritePredicates() {

        for (int i = 0; i < this.reader.length(); ++i){
            if (!this.reader.getWordInfo(i, this.reader.IDXI_FILLPRED).equals(this.reader.EMPTY_VALUE)){

                String predName = this.reader.getWordInfo(i, this.reader.IDXI_LEMMA) +
                        this.reader.getPredicateType(this.reader.getWordInfo(i, this.reader.IDXI_POS));

                this.reader.setField(this.reader.IDXI_PRED, i, this.predicatePredictions.get(predName).getNext());
            }
        }
    }

    /**
     * This rewrites all the predicate arguments in the current sentence with their predicted values loaded
     * in {@link #argumentPredictions}.
     */
    private void rewriteArguments() {

        int predicateNo = 0;
        int sentId = this.reader.getSentenceId();

        for (int i = 0; i < this.reader.length(); ++i){           

            if (!this.reader.getWordInfo(i, this.reader.IDXI_PRED).equals(this.reader.EMPTY_VALUE)){ // predicate found

                String predName = this.reader.getWordInfo(i, this.reader.IDXI_PRED) +
                        this.reader.getPredicateType(this.reader.getWordInfo(i, this.reader.IDXI_POS));
                ArgumentPrediction predicts = this.argumentPredictions.get(predName);

                if (predicts == null){
                    Logger.getInstance().message("Prediction data for " + predName + " missing.", Logger.V_WARNING);
                    continue;
                }

                for (int j = 0; j < this.reader.length(); ++j){
                    this.reader.setField(this.reader.IDXI_SEMROLE + predicateNo, j, predicts.get(sentId, j));
                }
                predicateNo++;
            }
        }
    }


    /* INNER CLASSES */

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
     * This stores all argument predictions for one predicate.
     */
    private static class ArgumentPrediction {

        /* CONSTANT */

        /** The semrel attribute name */
        private static final String ATTR_NAME = "semrel";
        /** The word-id attribute name */
        private static final String WORD_ID = "word-id";
        /** The sent-id attribute name */
        private static final String SENT_ID = "sent-id";
        /** The empty value for semantic relation */
        private static final String EMPTY = "_";

        /* DATA */

        /** The current instance index */
        private int curSentBase = 0;
        /** The current sentence index */
        private int curSent = 0;
        /** The current sentence id */
        private int curSentId = -1;
        /** List of all the predictions */
        private int [] values;
        /** List of word ids for each of the values */
        private int [] [] wordIds;
        /** The string values of semrel to be returned */
        private String [] semVals;

        /* METHODS */

        /**
         * This loads all the argument predictions for the given predicate, along with the possible values
         * of the semrel attribute.
         * 
         * @param data the data that contain the predictions
         */
        public ArgumentPrediction(Instances data) {

            Attribute semRel = data.attribute(ATTR_NAME);
            Attribute wordId = data.attribute(WORD_ID);
            Attribute sentId = data.attribute(SENT_ID);

            // initialize the list of possible values
            this.semVals = new String [semRel.numValues()];
            for (int i = 0; i < this.semVals.length; ++i){
                this.semVals[i] = semRel.value(i);
            }

            // read all the predictions
            this.values = MathUtils.toInts(data.attributeToDoubleArray(semRel.index()));

            // divide the word ids by sentence
            int [] sentIds = MathUtils.toInts(data.attributeToDoubleArray(sentId.index()));
            int [] wordIdsPlain = MathUtils.toInts(data.attributeToDoubleArray(wordId.index()));
            int sentNum = 0;
            for (int i = 1; i < sentIds.length; i++) { // count sentences
                if (sentIds[i] != sentIds[i-1]){
                    sentNum++;
                }
            }
            this.wordIds = new int [sentNum] [];
            int sentBase = 0, sentIdx = 0;
            for (int i = 1; i < sentIds.length; i++){ // fill the word ids for each sentence
                if (sentIds[i] != sentIds[i-1]){
                    this.wordIds[sentIdx] = Arrays.copyOfRange(wordIdsPlain, sentBase, i);
                    sentIdx++;
                    sentBase = i;
                }
            }
        }

        /**
         * This retrieves the argument prediction for the given word in the given sentence. Please note that
         * the sentence ids are just for comparison among subsequent calls -- if the sentId is different
         * from the last one, this simply moves to the next sentence
         * @param sentId the current sentence id
         * @param wordId the id of the current word
         * @return the argument prediction for the given word in the current sentence
         */
        public String get(int sentId, int wordId){

            if (sentId != this.curSentId){

                if (this.curSentId != -1){
                    this.curSentBase += this.wordIds[this.curSent].length;
                    this.curSent++;
                }
                this.curSentId = sentId;
            }
            int pos = Arrays.binarySearch(wordIds[this.curSent], wordId);
            if (pos < 0){
                return EMPTY;
            }
            else {
                return this.semVals[this.values[this.curSentBase + pos]];
            }
        }
    }

}
