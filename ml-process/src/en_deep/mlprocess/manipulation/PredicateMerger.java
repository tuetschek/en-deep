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

import com.google.common.collect.HashMultimap;
import en_deep.mlprocess.Process;
import en_deep.mlprocess.Logger;
import en_deep.mlprocess.Pair;
import en_deep.mlprocess.exception.TaskException;
import en_deep.mlprocess.utils.FileUtils;
import en_deep.mlprocess.utils.StringUtils;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Hashtable;
import java.util.TreeSet;
import java.util.Vector;

/**
 *
 * @author Ondrej Dusek
 */
public class PredicateMerger extends GroupInputsTask {

    /* CONSTANTS */

    /** The pred_info parameter name */
    private static final String PRED_INFO = "pred_info";
    /** The min_sent parameter name */
    private static final String MIN_SENT = "min_sent";

    /* DATA */
    /** Information about all predicates */
    private Hashtable<String, PredInfo> predInfo;
    /** The minimum number of occurrences for a predicate not to be merged */
    private int minSent;
    /** The location of the predicate information file */
    private String predInfoFile;
    /** The grouped inputs */
    HashMultimap<String, String> grouped;
    /** The single outputs */
    ArrayList<Pair<String, String>> single;

    /* METHODS */

    /**
     * This creates a new {@link PredicateMerger} task, checking the numbers of inputs and grouped
     * and the necessary parameters:
     * <ul>
     * <li><tt>pred_info</tt> -- the predicate information file that contains statistics about all possible
     * predicates</li>
     * <li><tt>min_sent</tt> -- the minimum number of sentences so that the predicate is not merged</li>
     * <li><tt>pattern0</tt> -- the input pattern</li>
     * </ul>
     * There must be one or more inputs and just one output, which must be a "**" pattern. The output pattern
     * replacements are either the predicate (for frequent predicates that don't get merged) or the frame (+POS).
     */
    public PredicateMerger(String id, Hashtable<String, String> parameters,
            Vector<String> input, Vector<String> output) throws TaskException {

        super(id, parameters, input, output);

        if (this.getParameterVal(PRED_INFO) == null || this.getParameterVal(MIN_SENT) == null){
            throw new TaskException(TaskException.ERR_INVALID_PARAMS, this.id, "Missing parameters.");
        }
        try {
            this.minSent = Integer.parseInt(this.getParameterVal(MIN_SENT));
        }
        catch (NumberFormatException e){
            throw new TaskException(TaskException.ERR_INVALID_PARAMS, this.id, "Parameter min_sent must be numeric.");
        }

        this.predInfoFile = this.getParameterVal(PRED_INFO);
        if (!this.predInfoFile.contains(File.separator)){
            this.predInfoFile = Process.getInstance().getWorkDir() + this.predInfoFile;
        }

        if (this.input.isEmpty()){
            throw new TaskException(TaskException.ERR_WRONG_NUM_INPUTS, this.id, "Must have some inputs.");
        }
        if (this.output.size () != 1 || !this.output.get(0).contains("**")){
            throw new TaskException(TaskException.ERR_WRONG_NUM_OUTPUTS, this.id, "Must have 1 output pattern.");
        }

        this.grouped = HashMultimap.create();
        this.single = new ArrayList<Pair<String, String>>();
        this.predInfo = new Hashtable<String, PredInfo>();

        this.extractPatterns(1);
    }



    @Override
    public void perform() throws TaskException {
        
        try {
            this.loadPredInfos();
            this.createOutputSets();
            this.copySingle();
            this.mergeGroups();
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
     * This loads all the predicate information from the pred_info file. For the file format, see
     * {@link PredInfo#PredInfo(java.lang.String)}.
     */
    private void loadPredInfos() throws IOException {

        RandomAccessFile in = new RandomAccessFile(this.predInfoFile, "r");
        String line;

        Logger.getInstance().message("Loading predicate information ...", Logger.V_INFO);
        while ((line = in.readLine()) != null){
            if (line.isEmpty()){
                continue;
            }
            PredInfo pi = new PredInfo(line);
            this.predInfo.put(pi.name, pi);
        }
        in.close();
    }

    /**
     * According to the {@link #predInfo}, this merges the inputs and creates the output sets.
     */
    private void createOutputSets() throws TaskException {

        Hashtable<String, String> predicates = this.sortInputs()[0];

        for (String pred : predicates.keySet()){

            PredInfo pi = this.predInfo.get(pred);

            if (pi == null){
                throw new TaskException(TaskException.ERR_INVALID_DATA, this.id, "Predicate "
                        + pred + " not found in the predicate information file.");
            }
            if (pi.count < this.minSent){
                this.grouped.put(pi.getFrameName(), predicates.get(pred));
            }
            else {
                this.single.add(new Pair(pred, predicates.get(pred)));
            }
        }
    }

    /**
     * This copies the inputs that stayed single to the output files.
     */
    private void copySingle() throws IOException {

        String out = this.output.get(0);
        for (Pair<String, String> in : this.single){

            Logger.getInstance().message(this.id + ": Copying single " + in.second + " to the output ...",
                    Logger.V_INFO);
            FileUtils.copyFile(in.second, out.replace("**", in.first));
        }
    }

    /**
     * This merges all the input groups to the output files by creating {@link DataMerger} tasks.
     * The sub-tasks are created with main task id and synchronously, so that they can't be distinguished
     * from the main task.
     */
    private void mergeGroups() throws TaskException {

        Hashtable<String, String> dummyParams = new Hashtable<String, String>();

        for (String frame : this.grouped.keySet()){

            Vector<String> mergeIn = new Vector<String>(this.grouped.get(frame));
            Vector<String> mergeOut = new Vector<String>(1);
            mergeOut.add(this.output.get(0).replace("**", frame));

            DataMerger merger = new DataMerger(this.id, dummyParams, mergeIn, mergeOut);
            merger.perform();
        }
    }


    /**
     * This is a storage for the information about one predicate.
     * @todo formalize statistics and valency frames
     */
    private static class PredInfo {

        /** The name of the predicate, consisting of lemma, sense number and POS-type */
        final String name;
        /** The number of occurrences of the given predicate in the training data */
        final int count;
        /** The argument occurrence statistics */
        final String stats;
        /** The predicate valency frame */
        final TreeSet<String> frame;
        /** The predicate POS (noun/verb/error) */
        final String pos;


        /**
         * This creates a new PredInfo object out of a line in the pred_info file. The format
         * of the file must be:
         * <pre>
         * (predicate id):(count):(statistics):(frame)
         * predicate.01.v:10:A0 10 A1 9:A0 A1
         * </pre>
         */
        public PredInfo(String dataLine) {
            
            String [] fields = dataLine.trim().split(":");

            this.name = fields[0];
            this.pos = fields[0].substring(fields[0].length() - 1);
            this.count = Integer.parseInt(fields[1]);
            this.stats = fields.length > 2 ? fields[2] : null;
            this.frame = fields.length > 3 ? new TreeSet<String>(Arrays.asList(fields[3].split("\\s+"))) : null;
        }

        /**
         * This returns the string representation of the predicate frame. The members of the
         * frame are alphabetically sorted, so if two predicates have the same frame, this string
         * representation will also be equal for both of them.
         * @return a canonical string representation of the frame name
         */
        public String getFrameName(){
            return this.pos.toUpperCase() + "-" + (this.frame != null ? StringUtils.join(this.frame, "_") : "NULL");
        }
    }
}
