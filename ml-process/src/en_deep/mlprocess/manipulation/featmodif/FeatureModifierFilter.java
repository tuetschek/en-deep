/*
 *    This program is free software; you can redistribute it and/or modify
 *    it under the terms of the GNU General Public License as published by
 *    the Free Software Foundation; either version 2 of the License, or
 *    (at your option) any later version.
 *
 *    This program is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *    GNU General Public License for more details.
 *
 *    You should have received a copy of the GNU General Public License
 *    along with this program; if not, write to the Free Software
 *    Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 */

/*
 *    NominalToBinary.java
 *    Copyright (C) 1999 University of Waikato, Hamilton, New Zealand
 *
 */


package en_deep.mlprocess.manipulation.featmodif;

import en_deep.mlprocess.utils.MathUtils;
import java.util.ArrayList;
import java.util.BitSet;
import weka.core.Attribute;
import weka.core.Capabilities;
import weka.core.FastVector;
import weka.core.Instance;
import weka.core.DenseInstance;
import weka.core.Instances;
import weka.core.Option;
import weka.core.OptionHandler;
import weka.core.Range;
import weka.core.RevisionUtils;
import weka.core.SparseInstance;
import weka.core.Utils;
import weka.core.Capabilities.Capability;
import weka.filters.Filter;
import weka.filters.UnsupervisedFilter;

import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Vector;
import weka.core.AttributeLocator;
import weka.core.RelationalLocator;
import weka.core.StringLocator;

/**
 <!-- globalinfo-start -->
 * Converts all nominal attributes into binary numeric attributes. An attribute with k values is transformed
 * into k binary attributes if the class is nominal (using the one-attribute-per-value approach).
 * Binary attributes are left binary, if option '-A' is not given.
 * If the class is numeric, you might want to use the supervised version of this filter.
 * <p/>
 <!-- globalinfo-end -->
 *
 <!-- options-start -->
 * Valid options are: <p/>
 *
 * <pre> -R &lt;col1,col2-col4,...&gt;
 *  Specifies list of columns to act on. First and last are
 *  valid indexes.
 *  (default: first-last)</pre>
 *
 * <pre> -V
 *  Invert matching sense of column indexes.</pre>
 *
 * <pre> -O
 *  Operating class name.</pre>
 *
 <!-- options-end -->
 *
 */
public class FeatureModifierFilter
  extends Filter
  implements UnsupervisedFilter, OptionHandler {

  /** Stores which columns to act on */
  protected Range m_Columns = new Range();

  /** The name of the operating class */
  private String m_OperClassName = null;

  /** The actual operating class */
  private FeatureModifier m_OperClass = null;

  /** Preserve original columns */
  private boolean m_PreserveOriginals = false;

  /** List of source string attributes, excluding the affected ones */
  private AttributeLocator m_StringToCopySrc = null;
  /** List of target string attributes, excluding the affected ones */
  private AttributeLocator m_StringToCopyDst = null;



  /** Constructor - initialises the filter */
  public FeatureModifierFilter() {

    setAttributeIndices("first-last");
  }

  /**
   * Returns a string describing this filter
   *
   * @return a description of the filter suitable for
   * displaying in the explorer/experimenter gui
   */
  public String globalInfo() {

    return "Applies text transformations on feature values.";
  }

  /**
   * Returns the Capabilities of this filter.
   *
   * @return            the capabilities of this object
   * @see               Capabilities
   */
  public Capabilities getCapabilities() {
    Capabilities result = super.getCapabilities();
    result.disableAll();

    // attributes
    result.enableAllAttributes();
    result.enable(Capability.MISSING_VALUES);

    // class
    result.enableAllClasses();
    result.enable(Capability.MISSING_CLASS_VALUES);
    result.enable(Capability.NO_CLASS);

    return result;
  }

  /**
   * Sets the format of the input instances.
   *
   * @param instanceInfo an Instances object containing the input
   * instance structure (any instances contained in the object are
   * ignored - only the structure is required).
   * @return true if the outputFormat may be collected immediately
   * @throws Exception if the input format can't be set
   * successfully
   */
  public boolean setInputFormat(Instances instanceInfo)
       throws Exception {

    super.setInputFormat(instanceInfo);

    m_Columns.setUpper(instanceInfo.numAttributes() - 1);
    if (this.m_OperClassName == null || (this.m_OperClass = FeatureModifier.createHandler(m_OperClassName)) == null){
        throw new Exception("The operating class must be set and a name of an existing filter class.");
    }
    setOutputFormat();
    return true;
  }

  /**
   * Input an instance for filtering. Filter requires all
   * training instances be read before producing output.
   *
   * @param instance the input instance
   * @return true if the filtered instance may now be
   * collected with output().
   * @throws IllegalStateException if no input format has been set
   */
  public boolean input(Instance instance) {

    if (getInputFormat() == null) {
      throw new IllegalStateException("No input instance format defined");
    }
    if (m_NewBatch) {
      resetQueue();
      m_NewBatch = false;
    }

    convertInstance(instance);
    return true;
  }

  /**
   * Returns an enumeration describing the available options.
   *
   * @return an enumeration of all the available options.
   */
  public Enumeration listOptions() {

    Vector newVector = new Vector(3);

    newVector.addElement(new Option(
	"\tSpecifies list of columns to act on. First and last are \n"
	+ "\tvalid indexes.\n"
	+ "\t(default: first-last)",
	"R", 1, "-R <col1,col2-col4,...>"));

    newVector.addElement(new Option(
	"\tInvert matching sense of column indexes.",
	"V", 0, "-V"));

    newVector.addElement(new Option(
        "\tThe requested operation (class).",
        "O", 1, "-O <class_name>"));

    newVector.addElement(new Option(
	"\tPreserve original columns as well.",
	"P", 0, "-P"));

    return newVector.elements();
  }


  /**
   * Parses a given list of options. <p/>
   *
   <!-- options-start -->
   * Valid options are: <p/>
   *
   * <pre> -R &lt;col1,col2-col4,...&gt;
   *  Specifies list of columns to act on. First and last are
   *  valid indexes.
   *  (default: first-last)</pre>
   *
   * <pre> -V
   *  Invert matching sense of column indexes.</pre>
   *
   * <pre> -O &lt;class_name&gt;
   *  Operating class name.</pre>
   *
   * <pre> -P
   *  Preserve original columns.</pre>
   *
   <!-- options-end -->
   *
   * @param options the list of options as an array of strings
   * @throws Exception if an option is not supported
   */
  public void setOptions(String[] options) throws Exception {

    String convertList = Utils.getOption('R', options);
    if (convertList.length() != 0) {
      setAttributeIndices(convertList);
    } else {
      setAttributeIndices("first-last");
    }
    setInvertSelection(Utils.getFlag('V', options));

    setOperClass(Utils.getOption('O', options));

    setPreserveOriginal(Utils.getFlag('P', options));

    if (getInputFormat() != null)
      setInputFormat(getInputFormat());
  }

  /**
   * Gets the current settings of the filter.
   *
   * @return an array of strings suitable for passing to setOptions
   */
  @Override
  public String [] getOptions() {

    String [] options = new String [7];
    int current = 0;

    if (!getAttributeIndices().equals("")) {
      options[current++] = "-R"; options[current++] = getAttributeIndices();
    }
    if (getInvertSelection()) {
      options[current++] = "-V";
    }

    if (getOperClass() != null){
        options[current++] = "-S"; options[current++] = getOperClass();
    }

    if (getPreserveOriginals()){
        options[current++] = "-P";
    }

    while (current < options.length) {
      options[current++] = "";
    }
    return options;
  }

  /**
   * Returns the tip text for this property
   *
   * @return tip text for this property suitable for
   * displaying in the explorer/experimenter gui
   */
  public String invertSelectionTipText() {

    return "Set attribute selection mode. If false, only selected"
      + " (numeric) attributes in the range will be discretized; if"
      + " true, only non-selected attributes will be discretized.";
  }

  /**
   * Gets whether the supplied columns are to be removed or kept
   *
   * @return true if the supplied columns will be kept
   */
  public boolean getInvertSelection() {

    return m_Columns.getInvert();
  }

  /**
   * Sets whether selected columns should be removed or kept. If true the
   * selected columns are kept and unselected columns are deleted. If false
   * selected columns are deleted and unselected columns are kept.
   *
   * @param invert the new invert setting
   */
  public void setInvertSelection(boolean invert) {

    m_Columns.setInvert(invert);
  }

  /**
   * Returns the tip text for this property
   *
   * @return tip text for this property suitable for
   * displaying in the explorer/experimenter gui
   */
  public String attributeIndicesTipText() {
    return "Specify range of attributes to act on."
      + " This is a comma separated list of attribute indices, with"
      + " \"first\" and \"last\" valid values. Specify an inclusive"
      + " range with \"-\". E.g: \"first-3,5,6-10,last\".";
  }

  /**
   * Gets the current range selection
   *
   * @return a string containing a comma separated list of ranges
   */
  public String getAttributeIndices() {

    return m_Columns.getRanges();
  }

  /**
   * Sets which attributes are to be acted on.
   *
   * @param rangeList a string representing the list of attributes. Since
   * the string will typically come from a user, attributes are indexed from
   * 1. <br>
   * eg: first-3,5,6-last
   * @throws IllegalArgumentException if an invalid range list is supplied
   */
  public void setAttributeIndices(String rangeList) {

    m_Columns.setRanges(rangeList);
  }

  /**
   * Set the output format if the class is nominal.
   */
  private void setOutputFormat() {

    FastVector newAtts;
    int newClassIndex;
    Instances outputFormat;

    newClassIndex = getInputFormat().classIndex();
    newAtts = new FastVector();

    BitSet attrSrc = new BitSet(), attrDest = new BitSet();

    int attSoFar = 0;

    for (int j = 0; j < getInputFormat().numAttributes(); j++) {

      Attribute att = getInputFormat().attribute(j);

      if (!m_Columns.isInRange(j)) {
	newAtts.addElement(att.copy());

        attrSrc.set(j);
        attrDest.set(attSoFar++);

      } else {

          ArrayList<Attribute> valueAttrs = getAttributeOutputFormat(att);

          if (newClassIndex >= 0 && j < getInputFormat().classIndex()) {
	    newClassIndex += valueAttrs.size() - 1;
	  }
          newAtts.addAll(valueAttrs);

          if (m_PreserveOriginals){
              attrSrc.set(j);
              attrDest.set(attSoFar);
          }
          attSoFar += valueAttrs.size();
      }
    }

    outputFormat = new Instances(getInputFormat().relationName(),
				 newAtts, 0);
    outputFormat.setClassIndex(newClassIndex);    
    setOutputFormat(outputFormat);

    m_StringToCopySrc = new AttributeLocator(getInputFormat(), Attribute.STRING, MathUtils.findTrue(attrSrc));
    m_StringToCopyDst = new AttributeLocator(outputFormat, Attribute.STRING, MathUtils.findTrue(attrDest));
  }

  /**
   * Convert a single instance over if the class is nominal. The converted
   * instance is added to the end of the output queue.
   *
   * @param instance the instance to convert
   */
  private void convertInstance(Instance instance) {

    double [] vals = new double [outputFormatPeek().numAttributes()];
    String [] stringVals = new String [vals.length];
    int attSoFar = 0;

    for(int j = 0; j < getInputFormat().numAttributes(); j++) {
      Attribute att = instance.attribute(j);
      if (!m_Columns.isInRange(j)) {
	vals[attSoFar] = instance.value(j);
	attSoFar++;
      } else {
          // store new string values, make double values "missing" for now (if some string
          // values are missing, the double values will remain missing)
          attSoFar += getAttributeOutputValue(att, instance.value(j), vals, stringVals, attSoFar);
      }
    }
    Instance inst = null;
    if (instance instanceof SparseInstance) {
      inst = new SparseInstance(instance.weight(), vals);
    } else {
      inst = new DenseInstance(instance.weight(), vals);
    }

    inst.setDataset(getOutputFormat());
    copyValues(inst, false, instance.dataset(), getOutputFormat());

    // add new string values to the output data set and to the instance
    for (int i = 0; i < stringVals.length; ++i){ 
        if (stringVals[i] != null){
            vals[i] = inst.dataset().attribute(i).addStringValue(stringVals[i]);
        }
    }
    inst.replaceMissingValues(vals);

    inst.setDataset(getOutputFormat());
    push(inst);
  }

  /**
   * Returns the revision string.
   *
   * @return		the revision
   */
  public String getRevision() {
    return RevisionUtils.extract("$Revision: 5987 $");
  }

  /**
   * Main method for testing this class.
   *
   * @param argv should contain arguments to the filter:
   * use -h for help
   */
  public static void main(String [] argv) {
    runFilter(new FeatureModifierFilter(), argv);
  }

    /**
     * Returns the separator string for set values.
     * @return the currently set separator string for set values
     */
    public String getOperClass() {
        return m_OperClassName;
    }


    /**
     * Sets a new separator string for set values.
     */
    public void setOperClass(String className) {
        m_OperClassName = className;
    }

    /**
     * Return a list of new attributes for the given attribute, when the filter class will be applied to it.
     *
     * @param att the attribute to be converted
     * @return a list of output attributes for this attribute
     */
    private ArrayList<Attribute> getAttributeOutputFormat(Attribute att){

        ArrayList newAtts = new ArrayList<Attribute>();

        if (this.m_PreserveOriginals){
            newAtts.add(att.copy());
        }

        String [] outputNames = this.m_OperClass.getOutputFeatsList(att.name());

        for (int i = 0; i < outputNames.length; ++i){
            newAtts.add(new Attribute(outputNames[i], (List<String>) null));
        }

        return newAtts;
    }

    /**
     * Retrieves the values for all output attributes relating to the given source attribute.
     * 
     * @param att the source attribute
     * @param attVal the attribute value
     * @param stringValArr the field where the double values are to be set to missing
     * @param stringValArr the field where the string values are to be stored
     * @param offset the offset where the values for this attribute should begin
     * @return the number of attribute values written
     */
    private int getAttributeOutputValue(Attribute att, double attVal, double [] valArr, String [] stringValArr, int offset) {

        if (this.m_PreserveOriginals){
            valArr[offset] = attVal;
            offset++;
        }

        String [] outVals = this.m_OperClass.getOutputValues(att.value((int) attVal));
        System.arraycopy(outVals, 0, stringValArr, offset, outVals.length);

        for (int i = 0; i < outVals.length; ++i){
            valArr[offset + i] = Utils.missingValue();
        }

        return outVals.length + (this.m_PreserveOriginals ? 1 : 0);
    }

    @Override
    protected void copyValues(Instance instance, boolean instSrcCompat, Instances srcDataset, Instances destDataset) {
        
        RelationalLocator.copyRelationalValues(instance, instSrcCompat, srcDataset, m_InputRelAtts,
                destDataset, m_OutputRelAtts);

        StringLocator.copyStringValues(instance, instSrcCompat, srcDataset, m_StringToCopySrc, destDataset, m_StringToCopyDst);
    }

    /**
     * Set the new policy on preserving original columns.
     * @param preserveOriginals true if original columns should be preserved
     */
    private void setPreserveOriginal(boolean preserveOriginals) {
        this.m_PreserveOriginals = preserveOriginals;
    }

    /**
     * Returns the current setting regarding preservation of original columns.
     * @return true if original columns are to be preserved
     */
    private boolean getPreserveOriginals() {
        return this.m_PreserveOriginals;
    }
}
