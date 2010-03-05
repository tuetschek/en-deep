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
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.List;
import java.util.Vector;
import java.util.Hashtable;
import weka.core.Instances;

/**
 * This class merges several data sets into one.
 * @author Ondrej Dusek
 */
public class DataMerger extends Task {

    /* DATA */

    /* METHODS */

    /**
     * This creates a new {@link DataMerger} task. It doesn't take any parameter except the
     * input and output data sets' descriptions. Therefore, the number of output
     * data sources must be divisible by the number of input data sources.
     *
     * @param id the task id
     * @param parameters have no sense here
     * @param input the input data sets or files
     * @param output the output data sets or files
     */
    public DataMerger(String id, Hashtable<String, String> parameters, Vector<String> input, Vector<String> output) {
        super(id, parameters, input, output);

        if (parameters.size() > 0){
            Logger.getInstance().message("DataMerger parameters are ignored", Logger.V_WARNING);
        }
    }


    /**
     * Tries to merge the input sources to the output sources.
     * Checks if the number of inputs is divisible by the number of outputs, then tries to read all the
     * inputs and write the outputs.
     * @throws TaskException
     */
    @Override
    public void perform() throws TaskException {

        int ratio = this.input.size() / this.output.size();
        // TODO write DataMerger code
        // use weka.core.Instances to read ARFF files, create support for more - using the DataSet class (internally: Instances,
        // with I/O support for csv ?) + I/O support for my input data format
        if (this.input.size() % this.output.size() !=  0){
            throw new TaskException(TaskException.ERR_WRONG_NUM_INPUTS, this.id);
        }

        for (int j = 0; j < this.output.size(); ++j){

            try {
                this.mergeData(this.input.subList(ratio * j, ratio * j + ratio), this.output.get(j));
            }
            catch(IOException e){
                Logger.getInstance().message(this.id + ": I/O Error:" + e.getMessage(), Logger.V_IMPORTANT);
                throw new TaskException(TaskException.ERR_IO_ERROR, this.id);
            }
        }
    }

    /**
     * Tries to merge several input files into one output, using WEKA code.
     * @param in
     * @param out
     */
    private void mergeData(List<String> in, String out) throws IOException {

        File temp1 = File.createTempFile(this.id, "");
        File temp2 = File.createTempFile(this.id, "");
        PrintStream ps;
        PrintStream origOut = System.out; // remember original output stream

        String [] args = new String[3];
        args[0] = "append";


        // merge first two files
        args[1] = in.get(0);
        args[2] = in.get(1);

        Logger.getInstance().message(this.id + ": merge " + args[1] + " + " + args[2], Logger.V_DEBUG);

        System.setOut(ps = new PrintStream(temp1));
        Instances.main(args);
        ps.close();

        // append more files, one at a time (swapping usage of temp files)
        for (int i = 2; i < in.size(); ++i){

            args[1] = i % 2 == 0 ? temp1.getCanonicalPath() : temp2.getCanonicalPath();
            args[2] = in.get(i);

            Logger.getInstance().message(this.id + ": adding " + args[2] + " to merged data file.", Logger.V_DEBUG);

            System.setOut(ps = (i % 2 == 0 ? new PrintStream(temp2) : new PrintStream(temp1)));
            Instances.main(args);
            ps.close();
        }

        Logger.getInstance().message(this.id + ": moving tempfile to its final destination.", Logger.V_DEBUG);

        // move the last temp file to the file destination and delete the other
        if (in.size() % 2 == 0){
            temp1.renameTo(new File(out));
            temp2.delete();
        }
        else {
            temp2.renameTo(new File(out));
            temp1.delete();
        }

        // restore the original output stream
        System.setOut(origOut);
    }


}
