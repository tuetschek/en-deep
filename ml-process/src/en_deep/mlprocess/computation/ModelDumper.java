/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package en_deep.mlprocess.computation;

import en_deep.mlprocess.computation.wekaclassifier.Model;
import en_deep.mlprocess.Logger;
import en_deep.mlprocess.Task;
import en_deep.mlprocess.computation.wekaclassifier.Model.BinarizationTypes;
import en_deep.mlprocess.exception.TaskException;
import en_deep.mlprocess.utils.FileUtils;
import en_deep.mlprocess.utils.StringUtils;
import java.util.Hashtable;
import java.util.Vector;
import weka.classifiers.functions.LibLINEAR;
import weka.core.Instances;

/**
 * This is a helper class that dumps the trained model weights for LibLINEAR models.
 * <p>
 * It takes two input files (model, some compatible input data) per output file (feature weights dump),
 * there is one possible parameter (<tt>threshold</tt> -- minimum absolute weight for a feature
 * to be printed).
 * </p>
 * @author Ondrej Dusek
 */
public class ModelDumper extends Task {

    /* CONSTANTS */
    
    /** Name of the 'threshold' parameter */
    private static final String THRESHOLD = "threshold";
    
    /* DATA */
    
    /** Minimum absolute value to be printed */
    private double threshold = 0.0;
    
    
    /* METHODS */
    
    public ModelDumper(String id, Hashtable<String, String> parameters,
            Vector<String> input, Vector<String> output) throws TaskException {
        super(id, parameters, input, output);
        
        this.eliminatePatterns(this.input);
        this.eliminatePatterns(this.output);
        
        if (this.hasParameter(THRESHOLD)){
            this.threshold = this.getDoubleParameterVal(THRESHOLD);
        }
        
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
    
        Model model = new Model(this.id, from);       
        Instances data = FileUtils.readArff(dataFile);
        
        model.load();

        data.setClassIndex(model.classAttrib);       
        
        // prepare the model input data "as if" for classification (just to know the attribute labels)
        Instances modelInput = FileUtils.filterAttributes(data, model.selectedAttributes);
        modelInput.setClassIndex(model.attribsMask[model.classAttrib]);

        if (model.binarize != BinarizationTypes.NONE){
            Logger.getInstance().message(this.id + ": binarizing... (" + modelInput.relationName() + ")", Logger.V_DEBUG);
            modelInput = WekaClassifier.sparseNominalToBinary(modelInput, model.binarize);
        }

        // retrieve the model weights
        de.bwaldvogel.liblinear.Model internal = (de.bwaldvogel.liblinear.Model) ((LibLINEAR) model.classif).getModel();
        double [] weights = internal.getFeatureWeights();
        StringBuilder sb = new StringBuilder();
        
        int labelsNo = internal.getNrClass();
        int featsNo = modelInput.numAttributes();
        
        int [] labelNums = internal.getLabels();
            
        // for each feature, print weights for all target classes
        for (int feat = 0; feat < featsNo; ++feat){
            for (int label = 0; label < labelsNo; ++label){
                if (Math.abs(weights[feat * labelsNo + label]) < this.threshold){
                    continue;
                }
                sb.append(modelInput.attribute(feat)).append(" / ").append(modelInput.classAttribute().value(labelNums[label]));
                sb.append(" :\t").append(weights[feat * labelsNo + label]).append("\n");
            }
        }
        // do the same for the additional "BIAS" feature, if applicable
        if (featsNo * labelsNo < weights.length){
            for (int label = 0; label < labelsNo; ++label){
                if (Math.abs(weights[featsNo * labelsNo + label]) < this.threshold){
                    continue;
                }
                sb.append("BIAS / ").append(modelInput.classAttribute().value(labelNums[label]));
                sb.append(" :\t").append(weights[featsNo * labelsNo + label]).append("\n");                
            }
        }
        
        FileUtils.writeString(to, sb.toString());
    }
    
}
