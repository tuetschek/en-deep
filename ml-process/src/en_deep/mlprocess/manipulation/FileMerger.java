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

import en_deep.mlprocess.Task;
import en_deep.mlprocess.Logger;
import en_deep.mlprocess.exception.TaskException;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.FileOutputStream;
import java.util.Hashtable;
import java.util.List;
import java.util.Vector;

/**
 * This class merges several files into one by writing them sequentially one after another.
 * @author Ondrej Dusek
 */
public class FileMerger extends Task {

    /* CONSTANTS */

    /** Buffer for file rewrites */
    private static final int BUFFER_SIZE = 1048576;

    /* DATA */

    /* METHODS */

    /**
     * This creates a new {@link FileMerger} task. It doesn't take any parameter except the
     * input and output files' descriptions. Therefore, the number of output
     * data sources must be divisible by the number of input data sources.
     *
     * @param id the task id
     * @param parameters have no sense here
     * @param input the input files
     * @param output the output files
     */
    public FileMerger(String id, Hashtable<String, String> parameters, Vector<String> input, Vector<String> output) {
        super(id, parameters, input, output);

        if (parameters.size() > 0){
            Logger.getInstance().message("FileMerger parameters are ignored", Logger.V_WARNING);
        }
    }


    /**
     * Tries to merge the input sources to the output sources.
     * Checks if the number of inputs is divisible by the number of outputs, then tries to read all the
     * inputs and write the outputs.
     *
     * @throws TaskException for wrong number of inputs, or if an I/O error occurs
     */
    @Override
    public void perform() throws TaskException {

        int ratio = this.input.size() / this.output.size();

        if (this.input.size() % this.output.size() !=  0){
            throw new TaskException(TaskException.ERR_WRONG_NUM_INPUTS, this.id, "Number of outputs must be divisible" +
                    "by the number of inputs.");
        }

        for (int j = 0; j < this.output.size(); ++j){

            try {
                this.mergeData(this.input.subList(ratio * j, ratio * j + ratio), this.output.get(j));
            }
            catch(IOException e){
                Logger.getInstance().logStackTrace(e, Logger.V_DEBUG);
                throw new TaskException(TaskException.ERR_IO_ERROR, this.id, e.getMessage());
            }
        }
    }

    /**
     * Merges the given list of files into the given output file.
     *
     * @param in the list of input file names
     * @param out the output file name
     */
    private void mergeData(List<String> in, String out) throws IOException {

        FileOutputStream os = new FileOutputStream(out);
        byte [] buffer = new byte [BUFFER_SIZE];

        for (String file : in){

            FileInputStream is = new FileInputStream(file);
            int bytesRead;

            while ((bytesRead = is.read(buffer)) >= 0){
                os.write(buffer, 0, bytesRead);
            }

            is.close();
            os.flush();
        }

        os.close();
    }

}
