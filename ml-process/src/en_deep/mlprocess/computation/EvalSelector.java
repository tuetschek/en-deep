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

import en_deep.mlprocess.Pair;
import en_deep.mlprocess.Task;
import en_deep.mlprocess.exception.TaskException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Hashtable;
import java.util.Vector;

/**
 * This class unifies some functions for {@link Task}s that include selection from various
 * classification results based on some metric.
 *
 * @author Ondrej Dusek
 */
public abstract class EvalSelector extends Task {

    /* CONSTANTS */

    /** The name of the "measure" parameter */
    protected static final String MEASURE = "measure";

    /* DATA */

    /** The name of the evaluation measure */
    protected String measure;

    /* METHODS */

    /**
     * This just sets the inputs and outputs and checks the measure-related parameter. All subclasses
     * of {@link EvalSelector} must have the <tt>measure</tt> parameter which indicates what measure
     * to use for the evaluation of the tasks.
     * 
     * @param id
     * @param parameters
     * @param input
     * @param output
     */
    protected EvalSelector(String id, Hashtable<String, String> parameters,
            Vector<String> input, Vector<String> output) throws TaskException{
        super(id, parameters, input, output);

        if (this.parameters.get(MEASURE) == null){
            throw new TaskException(TaskException.ERR_INVALID_PARAMS, this.id, "No measure specified.");
        }
        this.measure = this.parameters.get(MEASURE);
    }

    /**
     * Opens the given files with evaluation statistics as output by
     * {@link en_deep.mlprocess.evaluation.EvalClassification} and selects the one which has the
     * best characteristics according to {@link #measure}.
     *
     * @param evalFiles a list of statistics files
     * @param testingOrder -- the order in which the files should be examined, or null for continuous examination
     * @return the number of the best statistics within evalFiles and its value
     */
    protected Pair<Integer, Double> selectBest(String [] evalFiles, int [] testingOrder) throws IOException,
            TaskException {

        int bestIndex = -1;
        double bestVal = -1.0;

        for (int i = 0; i < evalFiles.length; ++i) {

            int index = testingOrder != null ? testingOrder[i] : i;
            RandomAccessFile stats = new RandomAccessFile(evalFiles[index], "r");
            String line = stats.readLine();
            boolean measureFound = false;

            while (line != null) {

                String[] args = line.split(":");
                args[0] = args[0].trim();
                args[1] = args[1].trim();

                if (args[0].equalsIgnoreCase(this.measure)) {

                    double val = 0.0;

                    try {
                        val = Double.parseDouble(args[1]);
                    }
                    catch (NumberFormatException e){
                        throw new TaskException(TaskException.ERR_INVALID_DATA, this.id, "File : "
                                + evalFiles[index] + " : measure " + this.measure + " not numeric.");
                    }

                    measureFound = true;
                    if (val > bestVal) {
                        bestIndex = index;
                        bestVal = val;
                    }
                    break;
                }
                line = stats.readLine();
            }

            stats.close();
            if (!measureFound){
                throw new TaskException(TaskException.ERR_INVALID_DATA, this.id, "File : " + evalFiles[index] +
                        " : measure " +  this.measure + " not found.");
            }
        }
        return new Pair<Integer, Double>(bestIndex, bestVal);
    }
}
