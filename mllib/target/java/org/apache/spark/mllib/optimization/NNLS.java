package org.apache.spark.mllib.optimization;
// no position
/**
 * Object used to solve nonnegative least squares problems using a modified
 * projected gradient method.
 */
public  class NNLS {
  static public  class Workspace {
    public  int n () { throw new RuntimeException(); }
    // not preceding
    public   Workspace (int n) { throw new RuntimeException(); }
    public  org.jblas.DoubleMatrix scratch () { throw new RuntimeException(); }
    public  org.jblas.DoubleMatrix grad () { throw new RuntimeException(); }
    public  org.jblas.DoubleMatrix x () { throw new RuntimeException(); }
    public  org.jblas.DoubleMatrix dir () { throw new RuntimeException(); }
    public  org.jblas.DoubleMatrix lastDir () { throw new RuntimeException(); }
    public  org.jblas.DoubleMatrix res () { throw new RuntimeException(); }
    public  void wipe () { throw new RuntimeException(); }
  }
  static public  org.apache.spark.mllib.optimization.NNLS.Workspace createWorkspace (int n) { throw new RuntimeException(); }
  /**
   * Solve a least squares problem, possibly with nonnegativity constraints, by a modified
   * projected gradient method.  That is, find x minimising ||Ax - b||_2 given A^T A and A^T b.
   * <p>
   * We solve the problem
   *   min_x      1/2 x^T ata x^T - x^T atb
   *   subject to x >= 0
   * <p>
   * The method used is similar to one described by Polyak (B. T. Polyak, The conjugate gradient
   * method in extremal problems, Zh. Vychisl. Mat. Mat. Fiz. 9(4)(1969), pp. 94-112) for bound-
   * constrained nonlinear programming.  Polyak unconditionally uses a conjugate gradient
   * direction, however, while this method only uses a conjugate gradient direction if the last
   * iteration did not cause a previously-inactive constraint to become active.
   */
  static public  double[] solve (org.jblas.DoubleMatrix ata, org.jblas.DoubleMatrix atb, org.apache.spark.mllib.optimization.NNLS.Workspace ws) { throw new RuntimeException(); }
}
