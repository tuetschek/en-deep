/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package en_deep.mlprocess.manipulation;

import en_deep.mlprocess.Logger;
import en_deep.mlprocess.Task;
import en_deep.mlprocess.exception.TaskException;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Vector;
import weka.core.Instance;
import weka.core.Instances;

/**
 * This unsets all values for some attributes in an ARFF file.
 * @author odusek
 */
public class UnsetValues extends AbstractAttributeManipulation {

    /* CONSTANTS */

    /** The 'attribs' parameter name */
    private static final String ATTRIBS = "attribs";

    /* METHODS */
    /**
     * This creates a new {@link UnsetValues} {@link Task}. There must be the same number of
     * inputs and outputs and one parameter is expected:
     * <ul>
     * <li><tt>attribs</tt> -- names of the attributes to be added (space-separated)</li>
     * </ul>
     */
    public UnsetValues(String id, Hashtable<String, String> parameters,
            Vector<String> input, Vector<String> output) throws TaskException {

        super(id, parameters, input, output);

        if (!this.hasParameter(ATTRIBS)){
            throw new TaskException(TaskException.ERR_INVALID_PARAMS, this.id, "Missing parameter 'attribs'.");
        }
    }


    @Override
    protected void manipulateAttributes(Instances data) throws TaskException {

        String [] attribs = this.getParameterVal(ATTRIBS).split("\\s+");

        for (int i = 0; i < attribs.length; ++i){
            if (data.attribute(attribs[i]) == null){
                Logger.getInstance().message("Attribute " + attribs[i] + " not found in data set " + data.relationName(),
                        Logger.V_WARNING);
                continue;
            }
            Enumeration<Instance> insts = data.enumerateInstances();
            int attrIndex = data.attribute(attribs[i]).index();

            while(insts.hasMoreElements()){
                Instance inst = insts.nextElement();
                inst.setMissing(attrIndex);
            }
        }
    }

}
