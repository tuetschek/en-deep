Given a configuration file that lists the pre-programmed tasks (a _scenario file_, with each task corresponding to one Java class), this determines the dependencies among them and processes all of them in the right order. If multiple threads or program instances (which use the same scenario file) are used, parallel processing is possible.

The program is also capable of processing multiple files in the same way (using wildcard patterns).

See the detailed description of the configuration file [here](http://en-deep.googlecode.com/svn/trunk/xml/scenario-description.pdf).

---

## Requirements
This software requires Java Runtime Environment 1.6 or higher, and the following
publicly available libraries:
  * `lib/google-collect-1.0.jar` -- Google Collections, version 1.0, available [here](http://code.google.com/p/google-collections/)
  * `lib/java-getopt-1.0.13.jar` -- Java Get-Opt, version 1.0.13, available [here](http://www.urbanophile.com/~arenn/hacking/getopt/gnu.getopt.Getopt.html)
  * `lib/weka-3.7.6.jar` -- WEKA ML environment, version 3.7.6, available [here](http://www.cs.waikato.ac.nz/ml/weka/)

The following further libraries are needed by the machine learning tasks implemented
in our SRL system:
  * WEKA classifier/evaluator wrapper packages, all available [here](http://sourceforge.net/projects/weka/files/weka-packages/):
    * `weka-liblinear-1.8.jar` -- LibLINEAR WEKA wrapper, version 1.8
    * `chisquared-attr-eval-1.0.2` -- Chi-Squared Attribute Evaluation, version 1.0.2
    * `significance-attr-eval-1.0.1` -- Probabilistic Significance Attribute Evaluator, version 1.0.1
  * `lib/liblinear-1.8.jar` -- LibLINEAR classifiers (Java version), version 1.8, available [here](http://www.bwaldvogel.de/liblinear-java/)
  * `lib/lpsolve55j.jar` -- LP\_Solve, version 5.5, available     [here](http://lpsolve.sourceforge.net/5.5/)


---

## Installation
> Copy the ML-Process JAR binary into any directory, then create a `lib/` subdirectory
> and place all the required libraries there. If their file names do not match the
> ones listed above, of if you prefer to place them in a different directory,
> please use the `-cp` parameter for invoking.


---

## Usage
`java -jar ml-process.jar [parameters] scenario-file`

(see scenario file description [here](http://en-deep.googlecode.com/svn/trunk/xml/scenario-description.pdf))
### Available parameters
  * `--threads` (`-t`): number of Worker threads for this Process instance (default: `1`)
  * `--verbosity` (`-v`): the desired verbosity level (`0-4`, default: 0 - i.e. no messages)
  * `--retrieve_count` (`-c`): the number of tasks that should be retrieved by one thread/instance at one time (default: `10`)
  * `--parse_only` (`-p`): if set, the program will just parse the scenario file, report any problems and end.
  * `--workdir` (`-d`): specifies the working directory (if not the same as that of the plan file).


---

**Author**: Ondřej Dušek.

This program has been created to conduct the experiments needed for my master's thesis, _Deep Automatic Analysis of English_.
