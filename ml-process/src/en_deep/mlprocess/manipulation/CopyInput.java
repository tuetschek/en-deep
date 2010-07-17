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

import en_deep.mlprocess.Task;
import en_deep.mlprocess.Logger;
import en_deep.mlprocess.exception.TaskException;
import en_deep.mlprocess.utils.FileUtils;
import en_deep.mlprocess.utils.StringUtils;
import java.util.Hashtable;
import java.util.Vector;

/**
 * A dummy task that just copies its selected inputs to outputs.
 * @author Ondrej Dusek
 */
public class CopyInput extends Task {
    
    /* CONSTANTS */

    /** The input_no parameter name */
    private static final String INPUT_NO = "input_no";

    /* DATA */

    /** Numbers of inputs that should be copied to the outputs */
    private final int [] inputNos;

    /* METHODS */

    /**
     * This creates a new {@link CopyInput} task. It checks the numbers of inputs and outputs
     * (must be both one or more) and the necessary parameters:
     * <ul>
     * <li><tt>input_no</tt> list of input numbers that should be copied to the outputs, respectively.
     * </ul>
     */
    public CopyInput(String id, Hashtable<String, String> parameters,
            Vector<String> input, Vector<String> output) throws TaskException {

        super(id, parameters, input, output);

        if (this.input.isEmpty()){
            throw new TaskException(TaskException.ERR_WRONG_NUM_INPUTS, this.id);
        }
        if (this.output.isEmpty()){
            throw new TaskException(TaskException.ERR_WRONG_NUM_OUTPUTS, this.id);
        }
        this.eliminatePatterns(this.input);
        this.eliminatePatterns(this.output);

        if (!this.hasParameter(INPUT_NO)){
            throw new TaskException(TaskException.ERR_INVALID_PARAMS, this.id, "Missing input_no parameter.");
        }
        try {
            this.inputNos = StringUtils.readListOfInts(this.getParameterVal(INPUT_NO));
        }
        catch (NumberFormatException e){
            throw new TaskException(TaskException.ERR_INVALID_PARAMS, this.id, "Parameter input_no malformed.");
        }
        if (this.inputNos.length != this.output.size()){
            throw new TaskException(TaskException.ERR_INVALID_PARAMS, this.id, "Number of input indexes must"
                    + " be the same as the number of outputs.");
        }
        for (int i = 0; i < this.inputNos.length; ++i){
            if (this.inputNos[i] < 0 || this.inputNos[i] >= this.input.size()){
                throw new TaskException(TaskException.ERR_INVALID_PARAMS, this.id, "Input number out of "
                        + "range: " + this.inputNos[i]);
            }
        }
    }



    @Override
    public void perform() throws TaskException {
        
        try {
            for (int i = 0; i < this.inputNos.length; ++i){
                FileUtils.copyFile(this.input.get(this.inputNos[i]), this.output.get(i));
            }
        }
        catch (Exception e) {
            Logger.getInstance().logStackTrace(e, Logger.V_DEBUG);
            throw new TaskException(TaskException.ERR_IO_ERROR, this.id, e.getMessage());
        }
    }

}
