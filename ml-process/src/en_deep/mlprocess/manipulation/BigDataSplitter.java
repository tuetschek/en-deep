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
import en_deep.mlprocess.utils.FileUtils;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.util.Hashtable;
import java.util.Vector;
import weka.core.Instance;
import weka.core.Instances;
import weka.core.converters.ArffLoader;
import weka.filters.unsupervised.attribute.StringToNominal;

/**
 * This will split a big ARFF file into chunks based on the number of input instances and then convert all string
 * headers to nominal.
 * @author Ondrej Dusek
 */
public class BigDataSplitter extends Task {
    
    /* CONSTANTS */

    /** The 'chunk_length' parameter name */
    private static final String CHUNK_LENGTH = "chunk_length";

    /* DATA */

    /** Length of one produced data chunk */
    private int chunkLength;
    /** The header of the original file */
    private Instances header;

    /* METHODS */

    /**
     * This creates a new {@link BigDataSplitter} task, checking the numbers of inputs and outputs
     * and the necessary parameters:
     * <ul>
     * <li><tt>chunk_length</tt> -- length of one chunk (in number of instances)
     * </ul>
     * The input must be one file, the output must be one pattern.
     */
    public BigDataSplitter(String id, Hashtable<String, String> parameters,
            Vector<String> input, Vector<String> output) throws TaskException {

        super(id, parameters, input, output);

        try {
            this.chunkLength = Integer.parseInt(this.getParameterVal(CHUNK_LENGTH));
        }
        catch (Exception e){
            throw new TaskException(TaskException.ERR_INVALID_PARAMS, this.id, "Parameter " + CHUNK_LENGTH + " must"
                    + " be set and numeric.");
        }

        if (this.input.size() != 1){
            throw new TaskException(TaskException.ERR_WRONG_NUM_INPUTS, this.id, "Must have 1 input.");
        }
        if (this.output.size() != 1 || !this.output.get(0).contains("**")){
            throw new TaskException(TaskException.ERR_WRONG_NUM_OUTPUTS, this.id, "Must have 1 output pattern.");
        }
    }



    @Override
    public void perform() throws TaskException {
        
        try {
            this.loadHeader();
            this.processData();
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
     * This loads the header of the original ARFF file.
     * @throws Exception
     */
    private void loadHeader() throws Exception {
        this.header = FileUtils.readArffStructure(this.input.get(0));
    }

    /**
     * This processes the whole input file, splits it into chunks of the given size and converts the string
     * attributes to nominal along the way.
     * @throws Exception
     */
    private void processData() throws Exception {
        
        FileReader in = new FileReader(this.input.get(0));
        BufferedReader inRead = new BufferedReader(in);
        String line = inRead.readLine();

        while(line != null && !line.matches("^@[dD][aA][tT][aA]\\s*")){
            line = inRead.readLine();
        }

        boolean eof = false;
        int curChunkNo = 0;

        while (!eof){
            Instances curChunk = new Instances(this.header, 0);
            ArffLoader.ArffReader instRead = new ArffLoader.ArffReader(inRead, curChunk, 0, chunkLength);
            Instance inst;

            for (int i = 0; i < this.chunkLength; ++i){
                if ((inst = instRead.readInstance(curChunk)) == null){
                    eof = true;
                    break;
                }
                curChunk.add(inst);
            }

            // convert string to nominal
            FileUtils.writeArff(this.output.get(0).replace("**", Integer.toString(curChunkNo)),
                    FileUtils.allStringToNominal(curChunk));

            Logger.getInstance().message(this.id + ": chunk " + curChunkNo + " written ... ", Logger.V_DEBUG);
            curChunkNo++;
        }

        inRead.close();
    }

}
