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
import en_deep.mlprocess.Task;
import en_deep.mlprocess.exception.TaskException;
import java.io.File;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Vector;

/**
 * This serves as a super-class for all classes that use several groups of inputs which need to be treated
 * in parallel.
 * @todo this should be absolutely eliminated by introducing some improvement in task expansion, so that the task
 *      remembers its 'here'-expansions.
 * @author Ondrej Dusek
 */
public abstract class GroupInputsTask extends Task {

    /* CONSTANTS */

    /** Prefix for the pattern parameters */
    private static final String PATTERN_PREFIX = "pattern";

    /* DATA */

    /** The patterns for sorting the inputs */
    protected String [] patterns;

    /* METHODS */

    /**
     * This just checks the inputs and outputs. 
     * 
     * @param id
     * @param parameters
     * @param input
     * @param output
     * @throws TaskException
     */
    protected GroupInputsTask(String id, Hashtable<String, String> parameters,
            Vector<String> input, Vector<String> output) throws TaskException {

        super(id, parameters, input, output);

        for (String file : this.output){
            if (!file.contains("**")){
                throw new TaskException(TaskException.ERR_OUTPUT_PATTERNS, this.id, "Outputs must contain '**' patterns.");
            }
        }
        if (this.input.isEmpty()){
            throw new TaskException(TaskException.ERR_WRONG_NUM_INPUTS, this.id, "Must have some inputs.");
        }
        if (this.output.isEmpty()){
            throw new TaskException(TaskException.ERR_WRONG_NUM_OUTPUTS, this.id, "Must have some outputs.");
        }
    }

    /**
     * This sorts the inputs according to the given input patterns.
     * @return the inputs, sorted according to the given input patterns.
     * @throws TaskException
     */
    protected Hashtable[] sortInputs() throws TaskException {

        Hashtable[] tables = new Hashtable[this.patterns.length];

        for (int i = 0; i < tables.length; ++i) {
            tables[i] = new Hashtable<String, String>();
        }

        for (String path : this.input) {

            String file = path.substring(path.lastIndexOf(File.separator) + 1);
            int matchingPatNo = -1;
            String matchingPatStart = "";
            String matchingPatEnd = "";

            for (int i = 0; i < this.patterns.length; ++i) {

                String pattern = this.patterns[i];
                String patternStart = pattern.substring(0, pattern.indexOf("*"));
                String patternEnd = pattern.endsWith("*") ? "" : pattern.substring(pattern.indexOf("*") + 1);

                if (file.startsWith(patternStart) && file.endsWith(patternEnd)) {

                    if (matchingPatNo != -1) {
                        Logger.getInstance().message(this.id + " : more patterns match " + file + ".",
                                Logger.V_WARNING);
                    }
                    // longest matching pattern is prefered
                    if (matchingPatNo == -1 || pattern.length() > this.patterns[matchingPatNo].length()) {
                        matchingPatNo = i;
                        matchingPatStart = patternStart;
                        matchingPatEnd = patternEnd;
                    }
                }
            }
            if (matchingPatNo == -1) {
                throw new TaskException(TaskException.ERR_INVALID_PARAMS, this.id, "Input file "
                        + file + " doesn\'t match " + " any of the input patterns.");
            }
            tables[matchingPatNo].put(file.substring(matchingPatStart.length(),
                    file.length() - matchingPatEnd.length()), path);
        }
        return tables;
    }

    /**
     * This extract the pattern-related parameters from the task parameters. The number of patterns extracted
     * must be the same as that of outputs / divideBy (so that the outputs divide the inputs in several groups).
     * No pattern may be missing.
     *
     * @param divideBy how many groups the inputs shall form, therefore how many times less input patterns is
     *   transferred to the outputs
     * @throws TaskException if some of the patterns are missing.
     */
    protected  void extractPatterns(int divideBy) throws TaskException {

        Enumeration<String> params = this.parameters.keys();
        this.patterns = new String[this.output.size() / divideBy];

        while (params.hasMoreElements()) {

            String param = params.nextElement();

            if (param.startsWith(PATTERN_PREFIX)) {
                try {
                    int paramNum = Integer.parseInt(param.substring(PATTERN_PREFIX.length()));
                    this.patterns[paramNum] = this.parameters.get(param);
                }
                catch (Exception e) {
                    throw new TaskException(TaskException.ERR_INVALID_PARAMS, this.id,
                            "Pattern params must end " + "with a number that matches the number of outputs / "
                            + divideBy + ".");
                }
            }
        }
        for (int i = 0; i < this.patterns.length; ++i) {
            if (this.patterns[i] == null) {
                throw new TaskException(TaskException.ERR_INVALID_PARAMS, this.id,
                        "Parameter pattern" + i + " is missing.");
            }
        }
    }

    /**
     * This selects all the pattern expansions that are represented in every hashtable for every output.
     * It provides them with an order for selection.
     *
     * @param tables The tables with sorted file names according to the patterns they match (output of {@link #sortInputs()}
     * @return All viable sample candidates.
     */
    protected  Vector<String> selectViableExpansions(Hashtable[] tables) {

        Enumeration<String> keys = tables[0].keys();
        Vector<String> viableCandidates = new Vector<String>();

        while (keys.hasMoreElements()) {

            String key = keys.nextElement();
            boolean viable = true;

            for (int i = 0; i < tables.length; ++i) {
                if (!tables[i].containsKey(key)) {
                    viable = false;
                    break;
                }
            }
            if (viable) {
                viableCandidates.add(key);
            }
        }
        return viableCandidates;
    }

}
