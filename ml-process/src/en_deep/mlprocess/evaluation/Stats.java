package en_deep.mlprocess.evaluation;

import en_deep.mlprocess.utils.StringUtils;

/**
 * This is a holder for the statistics -- true / false negs / positives.
 * @author Ondrej Dusek
 */
public class Stats {

    /** True positives */
    int tp;
    /** False positives */
    int fp;
    /** True negatives */
    int tn;
    /** False negatives */
    int fn;
    /** Number of instances */
    int n;

    /**
     * This creates empty stats with zero fields.
     */
    public Stats() {
    }

    /**
     * From a text representation as given by {@link #toString()}, this loads the statistics.
     * @param textRepresentation the text representation of the statistics
     * @throws NumberFormatException if the text is not well-formed
     */
    public Stats(String textRepresentation) throws NumberFormatException {

        this.tp = StringUtils.findVariableVal(textRepresentation, "tp");
        this.fp = StringUtils.findVariableVal(textRepresentation, "fp");
        this.fn = StringUtils.findVariableVal(textRepresentation, "fn");
        this.tn = StringUtils.findVariableVal(textRepresentation, "tn");
        this.n = StringUtils.findVariableVal(textRepresentation, "N");
    }

    @Override
    public String toString() {
        return "N:" + this.n + " tp:" + this.tp + " fp:" + this.fp + " tn:" + this.tn + " fn:" + this.fn;
    }

    /**
     * Computes recall from the stored values.
     * @return recall
     */
    public double getPrec() {
        return (double) tp / (tp + fp);
    }

    /**
     * Computes precision from the stored values.
     * @return precision
     */
    public double getRecall() {
        return (double) tp / (tp + fn);
    }

    /**
     * Computes the F-measure from the stored values.
     * @return the F-measure
     */
    public double getF1() {
        return (this.getPrec() + this.getRecall()) / 2.0;
    }

    /**
     * Adds the values of the other statistics to this one.
     * @param other the other values to be added
     */
    void add(Stats other) {

        this.n += other.n;
        this.fn += other.fn;
        this.fp += other.fp;
        this.tp += other.tp;
        this.tn += other.tn;
    }

    /**
     * Returns the accuracy computed from the stored values.
     * @return the accuracy.
     */
    public double getAcc() {
        return (tp + tn) / (double)n;
    }

    /**
     * This returns the value of the given measure (supported values are accuracy, precision, recall and f1).
     * @param measure the name of the measure to get
     * @return the value of the given measure
     */
    public double getMeasure(String measure) throws Exception {

        if (measure.equals("accuracy")){
            return this.getAcc();
        }
        else if (measure.equals("precision")){
            return this.getPrec();
        }
        else if (measure.equals("recall")){
            return this.getRecall();
        }
        else if (measure.equals("f1")){
            return this.getF1();
        }
        throw new Exception("Invalid measure name.");
    }

}
