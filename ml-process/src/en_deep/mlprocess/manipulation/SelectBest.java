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

package en_deep.mlprocess.manipulation;

import en_deep.mlprocess.Logger;
import en_deep.mlprocess.Pair;
import en_deep.mlprocess.Task;
import en_deep.mlprocess.computation.EvalSelector;
import en_deep.mlprocess.evaluation.Stats;
import en_deep.mlprocess.exception.TaskException;
import en_deep.mlprocess.utils.FileUtils;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Hashtable;
import java.util.Vector;

/**
 * This selects the best performing set of parameters from several given classifier results.
 * @author Ondrej Dusek
 */
public class SelectBest extends Task {


    /** The name of the 'measure' parameter */
    private static final String MEASURE = EvalSelector.MEASURE;

    /**
     * The constructor just checks the parameters, inputs and outputs.
     * <p>The class
     * has just one parameter: <tt>measure</tt> -- the measure of comparison.
     * </p>
     * <p>
     * The inputs must be triplets of classification, statistics and set of best parameters/attributes.
     * The output must be just one triplet.
     * </p>
     */
    public SelectBest(String id, Hashtable<String, String> parameters, Vector<String> input, Vector<String> output)
            throws TaskException {

        super(id, parameters, input, output);

        if (this.input.size() < 6 && this.input.size() % 3 != 0){
            throw new TaskException(TaskException.ERR_WRONG_NUM_INPUTS, this.id, "Inputs must be (at least 2) triplets.");
        }
        if (this.output.size() != 3){
            throw new TaskException(TaskException.ERR_WRONG_NUM_OUTPUTS, this.id, "Output must be 1 triplet.");
        }

        if (this.getParameterVal(MEASURE) == null){
            throw new TaskException(TaskException.ERR_INVALID_PARAMS, this.id, "Missing parameter.");
        }
    }

    @Override
    public void perform() throws TaskException {

        try {

            Pair<Stats,Stats> [] stats = new Pair [this.input.size() / 3];
            for (int i = 1; i < this.input.size(); i += 3) {
                stats[i/3] = this.readStats(this.input.get(i));
            }

            this.copyToTarget(this.selectBest(stats));
        }
        catch (TaskException e){
            throw e;
        }
        catch (Exception e) {
            Logger.getInstance().logStackTrace(e, Logger.V_DEBUG);
            throw new TaskException(TaskException.ERR_IO_ERROR, this.id, e.getMessage());
        }
        
    }

    /**
     * This reads both the labeled and unlabeled statistics from the first two lines of the given file
     * @param fileName the stats file to be read
     * @return the labeled and unlabeled statistics
     * @throws IOException
     */
    private Pair<Stats,Stats> readStats(String fileName) throws IOException {

        RandomAccessFile in = new RandomAccessFile(fileName, "r");
        Pair<Stats, Stats> data = new Pair<Stats, Stats>(new Stats(in.readLine()), new Stats(in.readLine()));
        in.close();
        return data;
    }

    /**
     * This selects the best result according to the 'measure' parameter from the given
     * statistics.
     * @param stats the list of statistics for several classification results
     * @return
     */
    private int selectBest(Pair<Stats, Stats> [] stats) throws Exception{

        int bestIndex = -1;
        double bestval = Double.NEGATIVE_INFINITY;
        String measure = this.getParameterVal(MEASURE);
        boolean unlabeled = measure.startsWith("unlabeled");

        if (measure.startsWith("labeled ") || measure.startsWith("unlabeled ")){
            measure = measure.substring(measure.indexOf(" ") + 1);
        }

        for (int i = 0; i < stats.length; i++) {
            double val = unlabeled ? stats[i].second.getMeasure(measure) : stats[i].first.getMeasure(measure);
            if (val > bestval){
                bestIndex = i;
                bestval = val;
            }
        }
        return bestIndex;
    }

    /**
     * This copies the best input files triple to the destination location.
     * @param inputNo the number of the best input triple
     */
    private void copyToTarget(int inputNo) throws IOException {

        for (int i = inputNo * 3; i < inputNo * 3 + 3; ++i){
            FileUtils.copyFile(this.input.get(i), this.output.get(i-inputNo*3));
        }
    }

}
