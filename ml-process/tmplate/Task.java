<#assign licenseFirst = "/*">
<#assign licensePrefix = " * ">
<#assign licenseLast = " */">
<#include "../Licenses/license-${project.license}.txt">

<#if package?? && package != "">
package ${package};
</#if>

import en_deep.mlprocess.Task;
import en_deep.mlprocess.Logger;
import en_deep.mlprocess.exception.TaskException;
import java.util.Hashtable;
import java.util.Vector;

/**
 *
 * @author ${user}
 */
public class ${name} extends Task {
    
    /**
     * This creates a new {@link ${name}} task. It checks the numbers of inputs and outputs
     * and the necessary parameters:
     * <ul>
     * </ul>
     */
    public ${name}(String id, Hashtable<String, String> parameters,
            Vector<String> input, Vector<String> output) throws TaskException {

        super(id, parameters, input, output);
    }



    @Override
    public void perform() throws TaskException {
        
        try {

        }
        catch (TaskException e){
            throw e;
        }
        catch (Exception e) {
            Logger.getInstance().logStackTrace(e, Logger.V_DEBUG);
            throw new TaskException(TaskException.ERR_IO_ERROR, this.id, e.getMessage());
        }
    }

}
