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
import en_deep.mlprocess.utils.StringUtils;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Vector;
import weka.core.Attribute;
import weka.core.Instance;
import weka.core.Instances;
import weka.core.converters.ArffLoader;
import weka.core.converters.ArffSaver;
import weka.core.converters.Saver;

/**
 * This will split a big ARFF file into chunks based on the number of input instances and then optionally convert all string
 * headers to nominal.
 * @author Ondrej Dusek
 */
public class BigDataSplitter extends Task {
    
    /* CONSTANTS */

    /** The 'chunk_length' parameter name */
    private static final String CHUNK_LENGTH = "chunk_length";
    /** The 'chunks_no' parameter name */
    private static final String CHUNKS_NO = "chunks_no";
    /** The 'equal_chunks' parameter name */
    private static final String EQUAL_CHUNKS = "equal_chunks";
    /** The 'by_attribute' parameter name */
    private static final String BY_ATTRIBUTE = "by_attribute";
    /** The 'string_to_nom' parameter name */
    private static final String STRING_TO_NOM = "string_to_nom";

    /* DATA */

    /** Length of one produced data chunk */
    private int chunkLength;
    /** Number of chunks that will be one instance longer, if chunks_no parameter is set, 0 otherwise */
    private int greaterChunks = 0;
    /** Number of chunks */
    private int chunksNo;
    /** The header of the original file */
    private Instances header;
    /** The attribute to be used for splitting */
    private String splitAttribute;
    /** Should all string attributes be converted to nominal ? */
    private boolean stringToNom;

    /* METHODS */

    /**
     * This creates a new {@link BigDataSplitter} task, checking the numbers of inputs and outputs
     * and the necessary parameters.
     * <ul>
     * <li><tt>by_attribute</tt> -- name of the attribute to be used for splitting
     * <li><tt>chunk_length</tt> -- length of one chunk (in number of instances)
     * <li><tt>chunks_no</tt> -- number of (equally large) parts to be produced
     * </ul>
     * All parameters are mutually exclusive. There is also a voluntary parameter, if <tt>chunk_length</tt>
     * is set:
     * <ul>
     * <li><tt>equal_chunks</tt> -- if set, the data will be split into as many chunks as the preset
     * length would cause, but in equal amounts (the last chunk won't be shorter than the previous ones).
     * </ul>
     * Another optional parameter:
     * <ul>
     * <li><tt>string_to_nom</tt> -- if set, all string attributes will be converted to nominal ones
     * </ul>
     * The number of input files must be equal to the number of output patterns.
     *
     */
    public BigDataSplitter(String id, Hashtable<String, String> parameters,
            Vector<String> input, Vector<String> output) throws TaskException {

        super(id, parameters, input, output);

        if (!this.hasParameter(BY_ATTRIBUTE) && !this.hasParameter(CHUNK_LENGTH) && !this.hasParameter(CHUNKS_NO)){
            throw new TaskException(TaskException.ERR_INVALID_PARAMS, this.id, "Missing parameters.");
        }
        if (this.hasParameter(BY_ATTRIBUTE)){
            this.splitAttribute = this.getParameterVal(BY_ATTRIBUTE);
        }
        else if(this.hasParameter(CHUNKS_NO))
        {
            this.chunksNo = this.getIntParameterVal(CHUNKS_NO);
            this.chunkLength = -1;
        }
        else {
            this.chunkLength = this.getIntParameterVal(CHUNK_LENGTH);
        }

        if (this.input.size() != this.output.size()){
            throw new TaskException(TaskException.ERR_WRONG_NUM_OUTPUTS, this.id, "Number of output patterns must"
                    + "be equal to the number of inputs.");
        }
        if (this.chunksNo != 1){
            for (String pattern : this.output){
                if (!pattern.contains("**")){
                    throw new TaskException(TaskException.ERR_OUTPUT_PATTERNS, this.id, "All outputs must be patterns.");
                }
            }
        }
    }



    @Override
    public void perform() throws TaskException {
        
        try {
            for (int fileNo = 0; fileNo < this.input.size(); ++fileNo){

                this.loadHeader(fileNo);
                if (this.splitAttribute != null){
                    this.splitByAttribute(fileNo);
                }
                else {
                    if (this.chunkLength == -1 || this.getBooleanParameterVal(EQUAL_CHUNKS)){
                        this.determineChunkLength(fileNo);
                    }
                    this.splitToChunks(fileNo);
                }
                this.header = null;
            }
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
     * @param  fileNo number of the input file to be processed
     * @throws Exception
     */
    private void loadHeader(int fileNo) throws Exception {
        this.header = FileUtils.readArffStructure(this.input.get(fileNo));
    }

    /**
     * This processes the whole input file, splits it into chunks of the given size and converts the string
     * attributes to nominal along the way.
     * @param fileNo number of the input file to be processed
     * @throws Exception
     */
    private void splitToChunks(int fileNo) throws Exception {

        BufferedReader inRead = openAndSkipHeader(this.input.get(fileNo));

        boolean eof = false;
        int curChunkNo = 0;

        while (!eof){
            Instances curChunk = new Instances(this.header, 0);
            ArffLoader.ArffReader instRead = new ArffLoader.ArffReader(inRead, curChunk, 0, chunkLength);
            Instance inst;

            for (int i = 0; i < (curChunkNo < this.greaterChunks ? this.chunkLength + 1 : this.chunkLength); ++i){
                if ((inst = instRead.readInstance(curChunk)) == null){
                    eof = true;
                    break;
                }
                curChunk.add(inst);
            }

            if (curChunk.numInstances() > 0){
                // convert string to nominal
                FileUtils.writeArff(StringUtils.replace(this.output.get(fileNo), Integer.toString(curChunkNo)),
                        FileUtils.allStringToNominal(curChunk));

                Logger.getInstance().message(this.id + ": chunk " + curChunkNo + " written ... ", Logger.V_DEBUG);
                curChunkNo++;
            }
        }

        inRead.close();
        inRead = null;
    }

    /**
     * This opens an ARFF file and skips its header.
     * @param fileName the file name
     * @return the open file input stream, after the initial @data line.
     */
    private BufferedReader openAndSkipHeader(String fileName) throws IOException, FileNotFoundException {

        FileReader in = new FileReader(fileName);
        BufferedReader inRead = new BufferedReader(in);
        String line = inRead.readLine();

        while (line != null && !line.matches("^@[dD][aA][tT][aA]\\s*")) {
            line = inRead.readLine();
        }
        return inRead;
    }

    /**
     * Given the input file and the desired number of chunks or the desired chunk length (with 
     * equal_chunks setting), this finds out the length of the file and therefore the number of
     * instances for one chunk.
     * @param  fileNo number of the input file to be processed
     */
    private void determineChunkLength(int fileNo) throws IOException {

        BufferedReader inRead = openAndSkipHeader(this.input.get(fileNo));
        String line = inRead.readLine();
        int numInst = 0;

        Logger.getInstance().message("Determining file size ...", Logger.V_DEBUG);

        while (line != null){
            if (!line.isEmpty() && !line.startsWith("%")){
                numInst++;
            }
            line = inRead.readLine();
        }
        inRead.close();
        inRead = null;

        if (this.chunkLength == -1){
            this.chunkLength = numInst / this.chunksNo;
            this.greaterChunks = numInst % this.chunksNo;
        }
        else { // equal_chunks set
            this.chunksNo = (int) Math.ceil(numInst / (double)this.chunkLength);
            this.chunkLength = numInst / this.chunksNo;
            this.greaterChunks = numInst % this.chunksNo;
        }

        Logger.getInstance().message("Found " + numInst + " instances, chunk length "
                + this.chunkLength + ", greater " + this.greaterChunks, Logger.V_DEBUG);

    }

    private void splitByAttribute(int fileNo) throws TaskException, IOException {

        Attribute splitAttrib;

        // find out all attribute values (end if the attribute does not exist or is not string)
        if ((splitAttrib = this.header.attribute(this.parameters.get(BY_ATTRIBUTE))) == null
                || !(splitAttrib.isNominal() || splitAttrib.isString())){

            throw new TaskException(TaskException.ERR_INVALID_PARAMS, this.id, "Attribute "
                    + this.splitAttribute + " not found or not nominal/string in file " + this.input.get(fileNo));
        }

        BufferedReader inRead = openAndSkipHeader(this.input.get(fileNo));
        Hashtable<String, ArffSaver> out = new Hashtable<String, ArffSaver>();

        boolean eof = false;
        ArffLoader.ArffReader instRead = new ArffLoader.ArffReader(inRead, this.header, 0, 0);
        Instance inst;

        while (!eof){

            if ((inst = instRead.readInstance(this.header)) == null){
                eof = true;
                break;
            }
            ArffSaver writer;
            String val = inst.stringValue(splitAttrib);

            if ((writer = out.get(val)) == null){
                
                writer = new ArffSaver();
                out.put(val, writer);

                File file = new File(StringUtils.replace(this.output.get(fileNo), FileUtils.fileNameEncode(val)));

                writer.setFile(file);
                writer.setDestination(file);
                writer.setStructure(this.header);
                writer.setRetrieval(Saver.INCREMENTAL);
            }
            writer.writeIncremental(inst);
        }

        for (ArffSaver writer : out.values()){
            writer.writeIncremental(null);
        }

        inRead.close();
        inRead = null;
    }

}
