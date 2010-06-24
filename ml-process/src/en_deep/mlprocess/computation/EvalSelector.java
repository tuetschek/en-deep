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
import en_deep.mlprocess.Process;
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
    public static final String MEASURE = "measure";

    /** File extension for classification tempfiles */
    protected static final String CLASS_EXT = ".arff";
    /** Extension for statistics tempfiles */
    protected static final String STATS_EXT = ".txt";
    /** The name of the "tempfile" parameter */
    static final String TEMPFILE = "tempfile";
    /** The 'delete_tempfiles' parameter name */
    protected static final String DELETE_TEMPFILES = "delete_tempfiles";

    /** The name of the "class_arg" parameter */
    protected static final String CLASS_ARG = GeneralClassifier.CLASS_ARG;
    /** The name of the "weka_class" parameter */
    protected static final String WEKA_CLASS = WekaClassifier.WEKA_CLASS;

    /* DATA */

    /** The name of the evaluation measure */
    protected String measure;
    /** Pattern used to create tempfiles */
    protected String tempFilePattern;
    /** The expanded id, the part of the ID that originated in task expansions */
    protected String expandedId;
    /** Delete temporary files after selecting from the possibilities ? */
    protected boolean deleteTempfiles;

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

        this.measure = this.parameters.remove(MEASURE);
        this.deleteTempfiles = this.getBooleanParameterVal(DELETE_TEMPFILES);
        this.parameters.remove(DELETE_TEMPFILES);
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

    /**
     * This writes the statistics for the best result into the given output file.
     * @param outFile the output file name
     * @param settingNo the number of the best setting
     */
    protected abstract void writeBestStats(String outFile, int settingNo) throws IOException;



    /**
     * Temporary file types used in the computation
     */
    protected enum TempfileTypes {

        CLASSIF, STATS, ROUND_STATS, BEST_CLASSIF, BEST_STATS
    }


    /**
     * This is the same as {@link #getTempfileName(en_deep.mlprocess.computation.EvalSelector.TempfileTypes, int, int)},
     * with the omission of the round parameter.
     *
     * @param type type of the tempfile
     * @param order the number of the file (not used for ROUND_STATS, BEST_STATS and BEST_CLASSIF)
     * @return
     */
    protected String getTempfileName(TempfileTypes type, int order){
        return this.getTempfileName(type, -1, order);
    }

    /**
     * Creates a file name out of the {@link #tempFilePattern} and the given
     * {@link GreedyAttributeSearch.TempfileTypes type}.
     *
     * @param type the type of the file
     * @param round the round for which the file is ment (-1 if not used)
     * @param order the number of the file (not used for ROUND_STATS, BEST_STATS and BEST_CLASSIF)
     * @return the file name
     */
    protected String getTempfileName(TempfileTypes type, int round, int order) {

        String lbr = round >= 0 ? "(" + round + "-" : "(";

        switch (type) {
            case CLASSIF:
                return Process.getInstance().getWorkDir() + this.tempFilePattern.replace("*",
                        this.expandedId + lbr + order + ")") + CLASS_EXT;
            case STATS:
                return Process.getInstance().getWorkDir() + this.tempFilePattern.replace("*",
                        this.expandedId + lbr + order + ")") + STATS_EXT;
            case ROUND_STATS:
                return Process.getInstance().getWorkDir() + this.tempFilePattern.replace("*",
                        this.expandedId + lbr + "stats)") + STATS_EXT;
            case BEST_STATS:
                return Process.getInstance().getWorkDir() + this.tempFilePattern.replace("*",
                        this.expandedId + lbr + "best)") + STATS_EXT;
            case BEST_CLASSIF:
                return Process.getInstance().getWorkDir() + this.tempFilePattern.replace("*",
                        this.expandedId + lbr + "best)") + CLASS_EXT;
            default:
                return "";
        }
    }

    /**
     * This retrieves the part of the id that resulted from task expansion, so that the tempfiles generated differ for
     * different tasks. This must be called before any tempfile names are generated.
     */
    protected void setExpandedId() {
        this.expandedId = this.id.indexOf('#') == -1 ? "" : this.id.substring(this.id.indexOf('#'));
        if (this.expandedId.startsWith("#")) {
            this.expandedId = this.expandedId.substring(1);
        }
        this.expandedId = this.expandedId.replace('#', '_');
    }


    /**
     * This deletes all the tempfiles that are listed among the inputs of the task.
     */
    protected abstract void deleteTempfiles();
}
