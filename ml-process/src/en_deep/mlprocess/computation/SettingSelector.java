/*
 *  Copyright (c) 2010 Ondrej Dusek
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

package en_deep.mlprocess.computation;

import en_deep.mlprocess.exception.TaskException;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.Hashtable;
import java.util.Vector;

/**
 * This class tries to run the given WEKA classifier on the same data with several
 * different sets of settings and then selects the one with the best results.
 *
 * @author Ondrej Dusek
 */
public class SettingSelector extends WekaSettingTrials {

    /* METHODS */

    /**
     * <p>
     * This constructs a {@link SettingSelector} object. The constructor just checks
     * the validity of parameters, inputs and outputs.
     * </p>
     * <p>
     * There are the same parameters as in {@link WekaClassifier}, except that the individual
     * classifier parameters are now space-separated lists of possible values (the number of
     * values must be the same). There are additional compulsory parameters:
     * </p>
     * <ul>
     * <li><tt>measure</tt> -- the measure selected for comparing the results</li>
     * <li><tt>tempfile</tt> -- the temporary files pattern (must contain one asterisk), without
     * the file extension (automatically adds ".arff" for data and ".txt" for statistics)</li>
     * </ul>
     * <p>
     * There must be exactly two inputs (first of which is the training data and second the
     * testing data) and three outputs (one is for the classification output, one for
     * the best classification statistics and one for the best parameter set).
     * </p>
     * There is a special parameter reserved for the program (the process ends with this
     * parameter). If the task is run with this parameter, more inputs are allowed.
     * <ul>
     * <li><tt>select_from_evaluations</tt> -- starts the selection from finished evaluations, if it's set
     * </ul>
     *
     * @param id
     * @param parameters
     * @param input
     * @param output
     */
    public SettingSelector(String id, Hashtable<String, String> parameters,
            Vector<String> input, Vector<String> output) throws TaskException {

        super(id, parameters, input, output);

        // check the number of inputs and outputs
        if ((!this.evalMode && input.size() != 2) || (input.size() < 2) || (input.size() % 2 != 0)){
            throw new TaskException(TaskException.ERR_WRONG_NUM_INPUTS, this.id);
        }
    }


    /**
     * This prepares the parameter sets for the individual classifier tasks.
     * @return the parameter sets for the individual classification trials
     */
    protected Hashtable<String, String>[] prepareParamSets() throws TaskException {

        Hashtable<String, String>[] paramSets = null;
        // differentiate the needed sets of classifier parameters
        for (String paramName : this.parameters.keySet()) {

            String[] paramVals = this.parameters.get(paramName).split("\\s+");

            if (paramSets == null) {
                paramSets = new Hashtable[paramVals.length];
            }
            if (paramVals.length != paramSets.length) {
                throw new TaskException(TaskException.ERR_INVALID_PARAMS, this.id,
                        "Numbers of the individual parameters vary.");
            }
            for (int i = 0; i < paramSets.length; ++i) {
                if (paramSets[i] == null) {
                    paramSets[i] = new Hashtable<String, String>(this.parameters.size());
                    paramSets[i].put(CLASS_ARG, this.classArg);
                    paramSets[i].put(WEKA_CLASS, this.wekaClass);
                }
                paramSets[i].put(paramName, paramVals[i]);
            }
        }
        return paramSets;
    }


    @Override
    protected Hashtable<String, String> getEvalParams() {

        Hashtable<String, String> evalParams = super.getEvalParams();
        evalParams.putAll(this.parameters);
        return evalParams;
    }

    @Override
    protected void writeBestStats(String outFile, Vector<Integer> settingNos) throws IOException {

        PrintStream out = new PrintStream(outFile);
        String [] paramSet = this.parameters.keySet().toArray(new String [0]);

        Arrays.sort(paramSet);
        for (int i = 0; i < paramSet.length; ++i){
            String [] vals = this.parameters.get(paramSet[i]).split("\\s+");
            out.print(paramSet[i] + ":");

            boolean first = true;
            for (Integer settingNo : settingNos){
                if (first){
                    first = false;
                }
                else {
                    out.print(",");
                }
                out.print(vals[settingNo]);
            }
            out.println();
        }
        out.close();
    }

    

}
