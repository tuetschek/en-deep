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
import en_deep.mlprocess.exception.TaskException;
import en_deep.mlprocess.utils.FileUtils;
import en_deep.mlprocess.utils.StringUtils;
import java.io.IOException;
import java.util.Hashtable;
import java.util.Random;
import java.util.Vector;

/**
 * This selects a random sample from the given input files and passes it to the output.
 * @author Ondrej Dusek
 */
public class RandomSampleSelector extends GroupInputsTask {


    /* CONSTANTS */

    /** The sample_size parameter name */
    private static final String SAMPLE_SIZE = "sample_size";

    /* DATA */

    /** The size of the random sample that should be selected */
    private int sampleSize;

    /* METHODS */

    /**
     * This creates a new {@link RandomSampleSelector}. It just checks the parameters and inputs.
     * <p>
     * There is one compulsory parameter:
     * </p>
     * <ul>
     * <li><tt>sample_size</tt> -- number of files to be selected (for one output)</li>
     * </ul>
     * There are additional parameters -- '*'-patterns to one of which the inputs must match and according
     * to which the input files are separated. Corresponding samples are selected for each pattern.
     * The number of patterns must be the same as the number of the outputs. These parameters' names must
     * start with <tt>pattern</tt> and end with a number (order of patterns matching the order of outputs, 
     * zero-based). In a usual case, the patterns are identical to the original input patterns but
     * have '*' instead of '**' (this is not neccessary since the inputs may be selected in some other way).
     * <p>
     * The outputs must contain "**" patterns. The selected files are copied to the output destinations.
     * </p>
     */
    public RandomSampleSelector(String id, Hashtable<String, String> parameters, Vector<String> input, Vector<String> output)
            throws TaskException{

        super(id, parameters, input, output);

        if (this.parameters.get(SAMPLE_SIZE) == null){
            throw new TaskException(TaskException.ERR_INVALID_PARAMS, this.id, "Sample size must be set.");
        }
        else {
            try {
                this.sampleSize = Integer.parseInt(this.parameters.get(SAMPLE_SIZE));
            }
            catch(NumberFormatException e){
                throw new TaskException(TaskException.ERR_INVALID_PARAMS, this.id, "Sample size must be a number.");
            }
        }

        this.extractPatterns(1);
    }

    @Override
    public void perform() throws TaskException {

        Random rand = new Random();
        Hashtable<String, String> [] tables = this.sortInputs();
        Vector<String> possibleKeys = this.selectViableExpansions(tables);

        if (possibleKeys.size() < this.sampleSize){
            throw new TaskException(TaskException.ERR_INVALID_DATA, this.id, "Not enough matching files to select from.");
        }

        Vector<String> selectedKeys = new Vector<String>();

        for (int i = 0; i < this.sampleSize; ++i){

            int sel = rand.nextInt(possibleKeys.size());
            selectedKeys.add(possibleKeys.remove(sel));
        }

        for (String key : selectedKeys){
            for (int i = 0; i < tables.length; ++i){

                String dest = StringUtils.replace(this.output.get(i), this.outPrefix + key);
                String src = tables[i].get(key);

                try {
                    FileUtils.copyFile(src, dest);
                }
                catch (IOException ex) {
                    Logger.getInstance().logStackTrace(ex, Logger.V_DEBUG);
                    throw new TaskException(TaskException.ERR_IO_ERROR, this.id, "Cannot copy " + src + " to " + dest);
                }
            }
        }
    }

}
