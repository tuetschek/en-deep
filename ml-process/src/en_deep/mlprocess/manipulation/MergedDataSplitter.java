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
import java.io.FileOutputStream;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.Vector;
import weka.core.Attribute;
import weka.core.Instance;
import weka.core.Instances;

/**
 * This splits the given files, if they've been previously merged and contain an attribute
 * indicating the original file name. If the attribute is not found, such files are copied into the
 * output unchanged.
 * @author Ondrej Dusek
 */
public class MergedDataSplitter extends Task {
    
    /* CONSTANTS */

    /** Name of the file_attr parameter */
    private static final String FILE_ATTR = "file_attr";
    /** The name of the pattern parameter */
    private static final String PATTERN = "pattern";

    /* DATA */

    /** The splitting attribute name */
    private String fileAttr;
    /** Pattern for input files to match the output file name for non-merged */
    private String pattern;
    /** This stores the names of the written files (that have their headers written) */
    private HashSet<String> written;

    /* METHODS */

    /**
     * This creates a new {@link MergedDataSplitter} task. It checks the numbers of inputs and outputs
     * and the necessary parameters:
     * <ul>
     * <li><tt>file_attr</tt> -- the name of the splitting attribute.</li>
     * <li><tt>pattern</tt> -- a pattern to match input files that were not merged. Will fail only
     * if such files are found.</li>
     * </ul>
     * There must be one or more inputs and just one output.
     */
    public MergedDataSplitter(String id, Hashtable<String, String> parameters,
            Vector<String> input, Vector<String> output) throws TaskException {

        super(id, parameters, input, output);

        this.written = new HashSet<String>();

        if (!this.hasParameter(FILE_ATTR)){
            throw new TaskException(TaskException.ERR_INVALID_PARAMS, this.id, "Missing parameter file_attr");
        }
        this.fileAttr = this.getParameterVal(FILE_ATTR);
        if (!this.hasParameter(PATTERN)){
            Logger.getInstance().message("No pattern given. Non-merged files will trigger an error.", Logger.V_WARNING);
        }
        else {
            this.pattern = StringUtils.getPath(this.getParameterVal(PATTERN));
        }

        if (this.input.isEmpty()){
            throw new TaskException(TaskException.ERR_WRONG_NUM_INPUTS, this.id);
        }
        if (this.output.size() != 1){
            throw new TaskException(TaskException.ERR_WRONG_NUM_OUTPUTS, this.id);
        }
        if (!StringUtils.hasPatternVariables(this.output.get(0), true)){
            throw new TaskException(TaskException.ERR_OUTPUT_PATTERNS, this.id);
        }
    }


    /**
     * This performs the splitting of one file into the multiple outputs given by the pattern.
     * @param inputFile the input file name
     * @param outputPattern the output file pattern
     */
    protected void split(String inputFile, String outputPattern) throws Exception {

        Instances data = FileUtils.readArff(inputFile);

        // no file attribute found
        if (data.attribute(this.fileAttr) == null && this.pattern == null){
            throw new TaskException(TaskException.ERR_INVALID_DATA, this.id, "File attribute not found "
                    + " in " + inputFile);
        }
        if (data.attribute(this.fileAttr) == null){
            String outputFile = StringUtils.matches(inputFile, this.pattern);
            if (outputFile == null){
                throw new TaskException(TaskException.ERR_INVALID_DATA, this.id, "Non-split file does not match"
                        + " the input file pattern: " + inputFile);
            }
            
            Logger.getInstance().message("Copying unsplit " + outputFile + " ...", Logger.V_DEBUG);
            outputFile = StringUtils.replace(outputPattern, outputFile);
            FileUtils.writeArff(outputFile, data);
            return;
        }

        // file attribute found -> needs more complicated splitting
        String lastFile = null;
        Instances header = this.getStringToNomHeader(data);
        Enumeration<Instance> insts = data.enumerateInstances();
        int fileAttrIdx = data.attribute(this.fileAttr).index();
        FileOutputStream outFile = null;

        while (insts.hasMoreElements()){

            Instance inst = insts.nextElement();
            String file = inst.stringValue(fileAttrIdx);

            if (!file.equals(lastFile)){
                if (outFile != null){
                    outFile.close();
                }
                outFile = new FileOutputStream(StringUtils.replace(outputPattern, file),
                        this.written.contains(file));
                if (!this.written.contains(file)){
                    header.setRelationName(FileUtils.fileNameDecode(file));
                    outFile.write(header.toString().getBytes());
                }
                this.written.add(file);
                lastFile = file;
                Logger.getInstance().message("Writing to " + file + " ...", Logger.V_DEBUG);
            }
            outFile.write((inst.toString() + "\n").getBytes());
        }
    }

    @Override
    public void perform() throws TaskException {

        try {
            for (int i = 0; i < this.input.size(); ++i){
                this.split(this.input.get(i), this.output.get(0));
            }
            for (String file : this.written){
                this.rewriteHeader(StringUtils.replace(this.output.get(0), file));
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
     * Returns data header with all nominal attributes converted to string.
     * @param data
     * @return
     */
    private Instances getStringToNomHeader(Instances data) {

        Instances header = new Instances(data, 0);

        for (int i = 0; i < header.numAttributes(); ++i){
            if (header.attribute(i).isNominal()){
                Attribute newAtt = new Attribute(header.attribute(i).name(), (List<String>) null);
                header.deleteAttributeAt(i);
                header.insertAttributeAt(newAtt, i);
            }
        }
        return header;
    }

    /**
     * This converts string attributes back to nominal in the split file and removes the file attribute.
     * @param file the file name
     */
    private void rewriteHeader(String file) throws Exception {

        Logger.getInstance().message("Rewriting headers in " + file + " ...", Logger.V_DEBUG);

        Instances data = FileUtils.readArff(file);
        data.deleteAttributeAt(data.attribute(this.fileAttr).index());
        data = FileUtils.allStringToNominal(data);
        FileUtils.writeArff(file, data);
    }

}
