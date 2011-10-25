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
import en_deep.mlprocess.computation.GeneralClassifier;
import en_deep.mlprocess.computation.WekaClassifier;
import en_deep.mlprocess.exception.TaskException;
import en_deep.mlprocess.utils.FileUtils;
import en_deep.mlprocess.utils.StringUtils;
import java.lang.reflect.Constructor;
import java.util.Hashtable;
import java.util.Vector;
import weka.core.Attribute;
import weka.core.Instances;
import weka.core.OptionHandler;
import weka.filters.Filter;

/**
 * This applies a WEKA filter to all data on the input and saves the result to the output.
 * @author Ondrej Dusek
 */
public class WekaFilter extends Task {

    /* CONSTANTS */

    /** The name of the 'filter_class' parameter */
    private static final String FILTER_CLASS = "filter_class";

    /* DATA */
    private String filterName;

    /* METHODS */

    /**
     * This just checks if the number of inputs and outputs is the same an that the required parameters
     * are set.
     * The parameters:
     * <ul>
     * <li><tt>filter_class</tt> -- the WEKA filter class to be used</li>
     * <li><tt>class_arg</tt> -- the name of the target argument that will be used for classification
     * (since this influences the behavior of some WEKA filters)</li>
     * </ul>
     * <p>
     * All the input data must have the same headers.
     * </p>
     * <p>
     * All other (one-character) parameters are treated as parameters of the corresponding WEKA class. Please
     * see {@link WekaClassifier} for the exact handling of these parameters.
     * </p>
     * 
     */
    public WekaFilter(String id, Hashtable<String, String> parameters, Vector<String> input, Vector<String> output)
            throws TaskException {

        super(id, parameters, input, output);

        if (this.input.isEmpty()){
            throw new TaskException(TaskException.ERR_WRONG_NUM_INPUTS, this.id, "Must have some input.");
        }
        if (this.input.size() != this.output.size()){
            throw new TaskException(TaskException.ERR_WRONG_NUM_OUTPUTS, this.id, "The numbers of inputs and"
                    + " outputs are not the same.");
        }

        if (!this.hasParameter(FILTER_CLASS)){
            throw new TaskException(TaskException.ERR_INVALID_PARAMS, this.id, "Missing parameter: " + FILTER_CLASS);
        }
        this.filterName = this.parameters.remove(FILTER_CLASS);
    }

    @Override
    public void perform() throws TaskException {

        try {
            Instances [] data = this.readAndCheckData();
            
            for (int i = 0; i < data.length; ++i){
                String oldName = data[i].relationName();
                Filter filter = this.initFilter(data[i]);
                data[i] = Filter.useFilter(data[i], filter);
                data[i].setRelationName(oldName); // keep the old relation name
                FileUtils.writeArff(this.output.get(i), data[i]);                
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
     * This reads all the input data and checks their headers if they're identical and sets the class
     * attribute if provided in the input parameters.
     * @return all the data read from the individual files
     */
    private Instances[] readAndCheckData() throws Exception {

        Instances [] data = new Instances [this.input.size()];

        for (int i = 0; i < this.input.size(); ++i){
            data[i] = FileUtils.readArff(this.input.get(i));
        }
        for (int i = 1; i < this.input.size(); ++i){
            if (!data[i].equalHeaders(data[0])){
                throw new TaskException(TaskException.ERR_INVALID_DATA, this.id, "Files "
                        + this.input.get(i) + " and " + this.input.get(0) + " don't have equal headers.");
            }
        }

        // set class attribute if set in the data
        if (this.hasParameter(GeneralClassifier.CLASS_ARG)){
            String className = this.parameters.remove(GeneralClassifier.CLASS_ARG);

            for (int i = 0; i < data.length; ++i){
                Attribute classAttr = data[i].attribute(className);
                if (classAttr == null){
                    throw new TaskException(TaskException.ERR_INVALID_DATA, this.id,
                            "Class attribute " + className + " not found in the input data.");
                }
                data[i].setClass(classAttr);
            }
        }

        return data;
    }


    /**
     * This initializes the WEKA filter from class parameters to be used for the given data and the given indexes.
     * @param data the data to be filtered (later)
     * @param indexes the attribute indexes for the application of this filter
     * @return the initialized filter object
     */
    private Filter initFilter(Instances data) throws TaskException {

        Filter filter;

        try {
            Class filterClass = Class.forName(this.filterName);
            Constructor filterConstructor = filterClass.getConstructor();
            filter = (Filter) filterConstructor.newInstance();

            if (filter instanceof OptionHandler){
                ((OptionHandler) filter).setOptions(StringUtils.getWekaOptions(this.parameters));
            }
            filter.setInputFormat(data); // first, parameters must be set, then the input format
        }
        catch (Exception e){
            e.printStackTrace();
            throw new TaskException(TaskException.ERR_INVALID_PARAMS, this.id, "Filter class not found or"
                    + " invalid: " + this.filterName);
        }

        return filter;
    }

}
