package interpolation;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Coord;

import playground.tnicolai.matsim4opus.gis.SpatialGrid;

public class Interpolation {

	private static final Logger log = Logger.getLogger(Interpolation.class);
	
	public static final int BILINEAR = 0;
	public static final int BICUBIC = 1;
	public static final int INVERSE_DISTANCE_WEIGHTING = 2;
	
	private SpatialGrid sg = null;
	private double[][] flip_sg = null;
	
	private double exp = 1.;
	private int interpolationMethod = -1;
	
	/**
	 * constructor
	 * 
	 * @param sg the SpatialGrid to interpolate
	 * @param method the interpolation method
	 */
	public Interpolation(SpatialGrid sg, final int method ){
	
		this(sg, method, 1);
	}
	
	/**
	 * constructor
	 * 
	 * @param sg the SpatialGrid to interpolate
	 * @param method the interpolation method
	 * @param exp the exponent for weights, only necessary in the inverse distance weighting method
	 */
	public Interpolation(SpatialGrid sg, final int method, final double exp ){
		
		this.sg = sg;
		this.interpolationMethod = method;
		this.exp = exp;
		if(this.interpolationMethod == BICUBIC){
			log.info("Mirroring the Spatial grid for interpolation ...");
			this.flip_sg = flip(this.sg.getMatrix());
		}
	}

	/**
	 * calculates the value at the given coordinate
	 * 
	 * @param coord
	 * @return the interpolated value
	 */
	public double interpolate(Coord coord){
		if(sg != null && coord != null)
			return interpolate(coord.getX(), coord.getY());
		log.warn("ERROR");
		return Double.NaN;
	}
	
	/**
	 * interpolates the value at the given coordinate (x,y)
	 * 
	 * @param x the x-coordinate
	 * @param y the y-coordinate
	 * @return the interpolated value at (x,y)
	 */
	public double interpolate(double x, double y){
		
		switch(this.interpolationMethod){
		case 0: return MyBiLinearInterpolator.myBiLinearValueInterpolation(this.sg, x, y);
		case 1: log.warn("BiCubic interpolation not tested yet!"); //TODO
				return BiCubicInterpolator.biCubicInterpolation(this.sg, this.flip_sg, x, y);
		case 2: log.warn("IDW interpolation not usefull for our needs.");
				return MyInverseDistanceWeighting.my4NeighborsIDW(this.sg, x, y, exp);		
		}
		return Double.NaN;
	}
	
	/**
	 * flips the given matrix horizontal
	 * 
	 * @param matrix
	 * @return the horizontal mirrored matrix
	 */
	private static double[][] flip(double[][] matrix) {
		double[][] flip= new double[matrix.length][matrix[0].length];
		for (int i=0; i<flip.length; i++){
			for (int j=0; j<flip[0].length; j++){
				flip[i][j]= matrix[matrix.length-1-i][j];
			}
		}
		return flip;
	}
	
	/**
	 * for testing
	 * @param args
	 */
	public static void main(String args[]){
		
//		Interpolation i = new Interpolation(Interpolation.BICUBIC);
		
	}
	
	
}
