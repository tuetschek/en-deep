/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package en_deep.mlprocess.computation;

import en_deep.mlprocess.Logger;
import en_deep.mlprocess.Task;
import en_deep.mlprocess.exception.TaskException;
import en_deep.mlprocess.utils.FileUtils;
import en_deep.mlprocess.utils.StringUtils;
import java.util.Hashtable;
import java.util.Vector;
import weka.classifiers.functions.LibLINEAR;
import weka.core.Instances;

/**
 *
 * @author odusek
 */
public class ModelDumper extends Task {

    public ModelDumper(String id, Hashtable<String, String> parameters,
            Vector<String> input, Vector<String> output) throws TaskException {
        super(id, parameters, input, output);
        
        this.eliminatePatterns(this.input);
        this.eliminatePatterns(this.output);
        
        if (this.input.size() != 2 * this.output.size()){
            throw new TaskException(TaskException.ERR_WRONG_NUM_OUTPUTS, this.id, "There must be twice as much inputs as outputs.");
        }
        
    }
    
    @Override
    public void perform() throws TaskException {
        
        for (int i = 0; i < this.output.size(); ++i){
            try {
                this.dumpModel(this.input.get(i), this.input.get(i + this.output.size()), this.output.get(i));
            }
            catch(Exception e){
                throw new TaskException(TaskException.ERR_IO_ERROR, this.id, e.getClass().toString() + ": " + e.getMessage());
            }
        }
    }

    private void dumpModel(String from, String dataFile, String to) throws Exception {
    
        WekaClassifier.Model model = new WekaClassifier.Model(this.id, from);       
        Instances data = FileUtils.readArff(dataFile);
        
        model.load();

        data.setClassIndex(model.classAttrib);       
        
        // create copies of evaluation data for all models and prepare them
        Instances modelInput = FileUtils.filterAttributes(data, model.selectedAttributes);
        modelInput.setClassIndex(model.attribsMask[model.classAttrib]);

        // binarize, if supposed to
        if (model.binarize){
            Logger.getInstance().message(this.id + ": binarizing... (" + modelInput.relationName() + ")", Logger.V_DEBUG);
            modelInput = WekaClassifier.sparseNominalToBinary(modelInput);
        }

        // LibLINEAR internal = (LibLINEAR) model.classif;
        liblinear.Model internal = (liblinear.Model) ((LibLINEAR) model.classif).getModel();
        double [] weights = internal.getFeatureWeights();
        // String [] weights = internal.getWeights().split("\\s+");
        StringBuilder sb = new StringBuilder();
        
        for (int i = 0; i < weights.length; ++i){
            sb.append(modelInput.attribute(i)).append(" -- ").append(weights[i]).append("\n");            
        }
        FileUtils.writeString(to, sb.toString());
    }
    
}
