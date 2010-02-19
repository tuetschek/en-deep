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
import en_deep.mlprocess.exception.TaskException;
import java.util.Vector;
import java.util.Hashtable;

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
    }


    /**
     * Tries to merge the input sources to the output sources.
     * Checks if the number of inputs is divisible by the number of outputs, then tries to read all the
     * inputs and write the outputs.
     * @throws TaskException
     */
    @Override
    public void perform() throws TaskException {
        // TODO write DataMerger code
        // use weka.core.Instances to read ARFF files, create support for more - using the DataSet class (internally: Instances,
        // with I/O support for csv ?) + I/O support for my input data format
        //
        // refactoring: Somehow distinguish b/t FileDescription & DataSetDescription x FeatureDescription
        // - find out where it's used (stored) and how could this be done -- totally split / create inter-class ?
        // - the only problem is probably the TaskSection class (possibly dependency descriptions?)
        //
        // or: create method DataSourceDescription.createDataSet() which throws an exception if called on feats (not so nice)?
    }

}
