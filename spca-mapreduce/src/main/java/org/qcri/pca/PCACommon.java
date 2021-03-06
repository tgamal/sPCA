/**
 * QCRI, sPCA LICENSE
 * sPCA is a scalable implementation of Principal Component Analysis (PCA) on of Spark and MapReduce
 *
 * Copyright (c) 2015, Qatar Foundation for Education, Science and Community Development (on
 * behalf of Qatar Computing Research Institute) having its principle place of business in Doha,
 * Qatar with the registered address P.O box 5825 Doha, Qatar (hereinafter referred to as "QCRI")
 *
*/

package org.qcri.pca;

import java.io.IOException;
import java.util.Iterator;
import java.util.Random;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.SequenceFile;
import org.apache.mahout.common.Pair;
import org.apache.mahout.common.iterator.sequencefile.SequenceFileIterator;
import org.apache.mahout.math.DenseMatrix;
import org.apache.mahout.math.DenseVector;
import org.apache.mahout.math.Matrix;
import org.apache.mahout.math.MatrixSlice;
import org.apache.mahout.math.Vector;
import org.apache.mahout.math.VectorWritable;
import org.apache.mahout.math.function.DoubleFunction;
import org.apache.mahout.math.hadoop.DistributedRowMatrix;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.io.Closeables;

/**
 * This class includes the utility functions that is used by multiple PCA
 * classes
 * 
 * @author maysam yabandeh
 */
class PCACommon {
  private static final Logger log = LoggerFactory.getLogger(PCACommon.class);

  /**
   * We use a single random object to help reproducing the erroneous scenarios
   */
  static Random random = new Random(0);
  // Random random = new Random(System.currentTimeMillis());

  /**
   * @return random initialization for variance
   */
  static double randSS() {
    return random.nextDouble();
  }
  /**
	 * @return hard-coded initialization variance used for validation 
  */
	static double randValidationSS() {
		// return random.nextDouble();
		return 0.9644868606768501;
	}
	
  /**
   * A randomly initialized matrix
   * @param rows
   * @param cols
   * @return
   */
  static Matrix randomMatrix(int rows, int cols) {
    Matrix randM = new DenseMatrix(rows, cols);
    randM.assign(new DoubleFunction() {
      @Override
      public double apply(double arg1) {
        return random.nextDouble();
      }
    });
    return randM;
  }

  /** A hard-coded initialization matrix used for validation
	 * 
	 * @param rows
	 * @param cols
	 * @return
  */

	static Matrix randomValidationMatrix(int rows, int cols) {
		double randomArray[][] = {
				{ 0.730967787376657, 0.24053641567148587, 0.6374174253501083 },
				{ 0.5504370051176339, 0.5975452777972018, 0.3332183994766498 },
				{ 0.3851891847407185, 0.984841540199809, 0.8791825178724801 },
				{ 0.9412491794821144, 0.27495396603548483, 0.12889715087377673 },
				{ 0.14660165764651822, 0.023238122483889456, 0.5467397571984656 } };
		DenseMatrix matrix = new DenseMatrix(randomArray);
		return matrix;
	}
	
  /**
   * should it pass a record during sampling
   * @param sampleRate
   * @return pass it or not
   */
  static boolean pass(double sampleRate) {
    double selectionChance = random.nextDouble();
    boolean pass = (selectionChance > sampleRate);
    return pass;
  }

  /**
   * @param m matrix
   * @return m.viewDiagonal().zSum()
   */
  static double trace(Matrix m) {
    Vector d = m.viewDiagonal();
    return d.zSum();
  }

  /***
   * If the matrix is small, we can convert it to an in memory representation
   * and then run efficient centralized operations
   * 
   * @param origMtx
   * @return a dense matrix including the data
   */
  static DenseMatrix toDenseMatrix(DistributedRowMatrix origMtx) {
    DenseMatrix mtx = new DenseMatrix(origMtx.numRows(), origMtx.numCols());
    Iterator<MatrixSlice> sliceIterator = origMtx.iterateAll();
    while (sliceIterator.hasNext()) {
      MatrixSlice slice = sliceIterator.next();
      mtx.viewRow(slice.index()).assign(slice.vector());
    }
    return mtx;
  }

  static Vector sparseVectorTimesMatrix(Vector vector, Matrix matrix,
      DenseVector resVector) {
    int nCols = matrix.numCols();
    for (int c = 0; c < nCols; c++) {
      Double resDouble = vector.dot(matrix.viewColumn(c));
      resVector.set(c, resDouble);
    }
    return resVector;
  }

  static Vector denseVectorTimesMatrix(DenseVector vector, Matrix matrix,
      DenseVector resVector) {
    int nRows = matrix.numRows();
    int nCols = matrix.numCols();
    for (int c = 0; c < nCols; c++) {
      double dotres = 0;
      for (int r = 0; r < nRows; r++)
        dotres += vector.getQuick(r) * matrix.getQuick(r, c);
      resVector.set(c, dotres);
    }
    return resVector;
  }

  static Vector vectorTimesMatrixTranspose(Vector vector,
      Matrix matrix, DenseVector resVector) {
    int nRows = matrix.numRows();
    int nCols = matrix.numCols();
    for (int r = 0; r < nRows; r++) {
      double dotres = 0;
      for (int c = 0; c < nCols; c++)
        dotres += vector.getQuick(c) * matrix.getQuick(r, c);
      resVector.set(r, dotres);
    }
    return resVector;
  }

  static Path toDistributedVector(Vector vector, Path outputDir, String label,
      Configuration conf) throws IOException {
    Path outputFile = new Path(outputDir, "Vector-" + label);
    FileSystem fs = FileSystem.get(outputDir.toUri(), conf);
    if (fs.exists(outputFile)) {
      log.warn("----------- OVERWRITE " + outputFile + " already exists");
      fs.delete(outputFile, false);
    }
    SequenceFile.Writer writer = new SequenceFile.Writer(fs, conf, outputFile,
        IntWritable.class, VectorWritable.class);
    VectorWritable vectorWritable = new VectorWritable();
    vectorWritable.set(vector);
    writer.append(new IntWritable(0), vectorWritable);
    writer.close();
    return outputFile;
  }

  static DenseVector toDenseVector(Path vectorFile, Configuration conf)
      throws IOException {
    SequenceFileIterator<IntWritable, VectorWritable> iterator = new SequenceFileIterator<IntWritable, VectorWritable>(
        vectorFile, true, conf);
    DenseVector vector;
    try {
      Pair<IntWritable, VectorWritable> next;
      next = iterator.next();
      vector = new DenseVector(next.getSecond().get());
    } finally {
      Closeables.close(iterator, false);
    }
    return vector;
  }

  /**
   * Convert an in-memory representation of a matrix to a distributed version It
   * then can be used in distributed jobs
   * 
   * @param oriMatrix
   * @return path that contains the matrix files
   * @throws IOException
   */
  static DistributedRowMatrix toDistributedRowMatrix(Matrix origMatrix,
      Path outPath, Path tmpPath, String label) throws IOException {
    Configuration conf = new Configuration();
    Path outputDir = new Path(outPath, label + origMatrix.numRows() + "x"
        + origMatrix.numCols());
    FileSystem fs = FileSystem.get(outputDir.toUri(), conf);
    if (!fs.exists(outputDir)) {
      Path outputFile = new Path(outputDir, "singleSliceMatrix");
      SequenceFile.Writer writer = new SequenceFile.Writer(fs, conf,
          outputFile, IntWritable.class, VectorWritable.class);
      VectorWritable vectorWritable = new VectorWritable();
      try {
        for (int r = 0; r < origMatrix.numRows(); r++) {
          Vector vector = origMatrix.viewRow(r);
          vectorWritable.set(vector);
          writer.append(new IntWritable(r), vectorWritable);
        }
      } finally {
        writer.close();
      }
    } else {
      log.warn("----------- Skip matrix " + outputDir + " - already exists");
    }
    DistributedRowMatrix dMatrix = new DistributedRowMatrix(outputDir, tmpPath,
        origMatrix.numRows(), origMatrix.numCols());
    dMatrix.setConf(conf);
    return dMatrix;
  }

  static void printMemUsage() {
    int mb = 1024 * 1024;
    // Getting the runtime reference from system
    Runtime runtime = Runtime.getRuntime();
    System.out.println("##### Heap utilization statistics [MB] #####");
    // Print used memory
    System.out.print("Used Memory:"
        + (runtime.totalMemory() - runtime.freeMemory()) / mb);
    // Print free memory
    System.out.print(" Free Memory:" + runtime.freeMemory() / mb);
    // Print total available memory
    System.out.print(" Total Memory:" + runtime.totalMemory() / mb);
    // Print Maximum available memory
    System.out.print(" Max Memory:" + runtime.maxMemory() / mb);
  }
}
