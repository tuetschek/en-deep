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
import weka.filters.unsupervised.attribute.NominalToBinary;

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
 * <pre> -M &lt;repl&gt;
 *  Replacement value -- "(missing)" is the default.</pre>
 *
 *
 <!-- options-end -->
 *
 */
public class ReplaceMissing
  extends Filter
  implements UnsupervisedFilter, OptionHandler {

  /** Stores which columns to act on */
  protected Range m_Columns = new Range();

  /** The replacement value */
  private String m_ReplVal = "(missing)";

  /** The actual operating class */
  private FeatureModifier m_OperClass = null;

  /** List of source string attributes, excluding the affected ones */
  private AttributeLocator m_StringToCopy = null;
  /** List of target string attributes, excluding the affected ones */
  private AttributeLocator m_StringToCopyDst = null;



  /** Constructor - initialises the filter */
  public ReplaceMissing() {

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

    newVector.addElement(new Option("\tThe replacement value", "O", 1, "-M <repl>"));

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
   * <pre> -M &lt;repl&gt;
   *  Replacement value -- "(missing)" is the default.</pre>
   *
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

    setReplacementValue(Utils.getOption('M', options));

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

    if (getReplacementValue() != null){
        options[current++] = "-M"; options[current++] = getReplacementValue();
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
    Instances outputFormat;

    newAtts = new FastVector();

    BitSet attrSrc = new BitSet();

    for (int j = 0; j < getInputFormat().numAttributes(); j++) {

      Attribute att = null;
      Attribute srcAtt = getInputFormat().attribute(j);

      if (!m_Columns.isInRange(j) || srcAtt.indexOfValue(m_ReplVal) >= 0) {
          att = (Attribute) srcAtt.copy();
      }
      else if (srcAtt.isNominal()){

          Enumeration<String> valsEnum = srcAtt.enumerateValues();
          ArrayList<String> valsList = new ArrayList<String>();

          while (valsEnum.hasMoreElements()){
              valsList.add(valsEnum.nextElement());
          }
          valsList.add(m_ReplVal);

          att = new Attribute(srcAtt.name(), valsList);
      }
      else { // string attributes
          att = (Attribute) srcAtt.copy();
          att.addStringValue(m_ReplVal);
      }

      newAtts.addElement(att);
      attrSrc.set(j);
    }

    outputFormat = new Instances(getInputFormat().relationName(),newAtts, 0);
    outputFormat.setClassIndex(getInputFormat().classIndex());

    setOutputFormat(outputFormat);

    m_StringToCopy = new AttributeLocator(getInputFormat(), Attribute.STRING, MathUtils.findTrue(attrSrc));
  }

  /**
   * Convert a single instance over if the class is nominal. The converted
   * instance is added to the end of the output queue.
   *
   * @param instance the instance to convert
   */
  private void convertInstance(Instance instance) {

    // create a copy of the input instance
    Instance inst = null;

    if (instance instanceof SparseInstance) {
      inst = new SparseInstance(instance.weight(), instance.toDoubleArray());
    } else {
      inst = new DenseInstance(instance.weight(), instance.toDoubleArray());
    }

    // copy the string values from this instance as well (only the existing ones)
    inst.setDataset(getOutputFormat());
    copyValues(inst, false, instance.dataset(), getOutputFormat()); // beware of weird behavior of this function (see source)!!
    inst.setDataset(getOutputFormat());

    // find the missing values to be filled + the double values for the new "missing" label and store it
    double [] vals = instance.toDoubleArray();

    for (int j = 0; j < getInputFormat().numAttributes(); j++) {

      Attribute att = instance.attribute(j);

      if (m_Columns.isInRange(j) && instance.isMissing(j)) {
          // find the "missing" value in the output nominal attribute
          if (att.isNominal()){
            vals[j] = inst.dataset().attribute(j).indexOfValue(m_ReplVal);
          }
          // add a string value for the new "missing" label
          else if (att.isString()){
            vals[j] = inst.dataset().attribute(j).addStringValue(m_ReplVal);
          }
      }
    }

    // fill in the missing values found
    inst.replaceMissingValues(vals);

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
    runFilter(new ReplaceMissing(), argv);
  }

    /**
     * Returns the replacement for missing values.
     * @return the currently set replacement value
     */
    public String getReplacementValue() {
        return m_ReplVal;
    }


    /**
     * Sets a new replacement for missing values.
     */
    public void setReplacementValue(String replVal) {
        m_ReplVal = replVal;
    }

}
