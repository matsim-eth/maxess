/* *********************************************************************** *
 * project: org.matsim.*
 * VertexSamplingCounter.java
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2010 by the members listed in the COPYING,        *
 *                   LICENSE and WARRANTY file.                            *
 * email           : info at matsim dot org                                *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU General Public License as published by  *
 *   the Free Software Foundation; either version 2 of the License, or     *
 *   (at your option) any later version.                                   *
 *   See also COPYING, LICENSE and WARRANTY file                           *
 *                                                                         *
 * *********************************************************************** */
package playground.johannes.socialnetworks.snowball2.sim;

import gnu.trove.TDoubleArrayList;
import gnu.trove.TDoubleDoubleHashMap;
import gnu.trove.TDoubleDoubleIterator;
import gnu.trove.TIntIntHashMap;
import gnu.trove.TIntObjectHashMap;

import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.matsim.contrib.sna.graph.Edge;
import org.matsim.contrib.sna.graph.Graph;
import org.matsim.contrib.sna.graph.Vertex;
import org.matsim.contrib.sna.graph.analysis.Degree;
import org.matsim.contrib.sna.graph.analysis.FixedSizeRandomPartition;
import org.matsim.contrib.sna.graph.analysis.RandomPartition;
import org.matsim.contrib.sna.graph.io.SparseGraphMLReader;
import org.matsim.contrib.sna.math.Distribution;
import org.matsim.contrib.sna.snowball.SampledVertexDecorator;
import org.matsim.contrib.sna.snowball.sim.ProbabilityEstimator;
import org.matsim.contrib.sna.snowball.sim.Sampler;
import org.matsim.contrib.sna.snowball.sim.SamplerListener;
import org.matsim.core.config.Config;


/**
 * @author illenberger
 *
 */
public class EstimatorTest implements SamplerListener {
	
	private static final Logger logger = Logger.getLogger(EstimatorTest.class);

	private int lastIteration;
	
	private Map<Vertex, int[]> vertexCounts;
	
	private Map<Vertex, double[]> vertexProbas;
	
	private int[] nSimulations = new int[INIT_CAPACITY];
	
	private int[] nSampledVertices = new int[INIT_CAPACITY];
	
	private ProbabilityEstimator estimator;
	
	private static final int INIT_CAPACITY = 40;
	
	private int maxIteration = 0;
	
	public EstimatorTest(Graph graph) {
		vertexCounts = new HashMap<Vertex, int[]>();
		vertexProbas = new HashMap<Vertex, double[]>();
		for(Vertex v : graph.getVertices()) {
			vertexCounts.put(v, new int[INIT_CAPACITY]);
			vertexProbas.put(v, new double[INIT_CAPACITY]);
		}
		
	}
	
	public void reset(Graph graph, String estimtype) {
		lastIteration = 0;
		int N = graph.getVertices().size();
		if("estim1a".equalsIgnoreCase(estimtype))
			estimator = new Estimator1(N);
		else if("estim1b".equalsIgnoreCase(estimtype))
			estimator = new NormalizedEstimator(new Estimator1(N), N);
		else {
			logger.warn(String.format("Estimator type %1$s unkown!", estimtype));
			System.exit(-1);
		}
	}
	
	@Override
	public boolean afterSampling(Sampler<?, ?, ?> sampler, SampledVertexDecorator<?> vertex) {
		return true;
	}

	@Override
	public boolean beforeSampling(Sampler<?, ?, ?> sampler, SampledVertexDecorator<?> vertex) {
		if(sampler.getIteration() > lastIteration) {
			int it = sampler.getIteration();
			lastIteration = it;
			maxIteration = Math.max(maxIteration, it - 1);
			nSimulations[it-1]++;
			nSampledVertices[it-1] += sampler.getNumSampledVertices();
			
			estimator.update(sampler.getSampledGraph());
			
			for(SampledVertexDecorator<?> v : sampler.getSampledGraph().getVertices()) {
				if (v.isSampled()) {
					Vertex delegate = v.getDelegate();
					vertexCounts.get(delegate)[it - 1]++;

					double[] probas = vertexProbas.get(delegate);
					probas[it - 1] += estimator.getProbability(v);
				}
			}
		}
		
		return true;
	}

	@Override
	public void endSampling(Sampler<?, ?, ?> sampler) {
		beforeSampling(sampler, null);
	}

	private void analyze(Graph graph, String output) throws IOException {
		final int N = graph.getVertices().size();
		/*
		 * initialize arrays
		 */
		TDoubleDoubleHashMap kHist = new Degree().distribution(graph.getVertices()).absoluteDistribution();
		TDoubleDoubleHashMap[] pObs_k = new TDoubleDoubleHashMap[maxIteration + 1];
		TDoubleDoubleHashMap[] pEstim_k = new TDoubleDoubleHashMap[maxIteration + 1];
		double[] mse = new double[maxIteration + 1];
		double[] bias = new double[maxIteration + 1];

		int[] samples = new int[maxIteration + 1];
		TIntIntHashMap[] samples_k = new TIntIntHashMap[maxIteration + 1];
		/*
		 * boxplot for probability to sample k
		 */
		List<TIntObjectHashMap<TDoubleArrayList>> pObsList_k = new ArrayList<TIntObjectHashMap<TDoubleArrayList>>(maxIteration + 1);
		for (int i = 0; i < maxIteration + 1; i++) {
			pObsList_k.add(new TIntObjectHashMap<TDoubleArrayList>());
		}
		int maxListSize = 0;
		/*
		 * iterate over all vertices
		 */
		for(Vertex v : graph.getVertices()) {
			int k = v.getNeighbours().size();
			int[] vertexCount = vertexCounts.get(v);
			double[] estimProba = vertexProbas.get(v);
			/*
			 * iterate over all snowball iterations
			 */
			for(int i = 0; i <= maxIteration; i++) {
				int n_i = vertexCount[i];
				double pObs_i = n_i / (double) nSimulations[i];
				double pRnd_i = nSampledVertices[i] / ((double)nSimulations[i] * N);
				/*
				 * initialize arrays
				 */
				if(pObs_k[i] == null) {
					samples_k[i] = new TIntIntHashMap();
					pObs_k[i] = new TDoubleDoubleHashMap();
					pEstim_k[i] = new TDoubleDoubleHashMap();
				}
				/*
				 * accumulate samples
				 */
				pObs_k[i].adjustOrPutValue(k, pObs_i, pObs_i);
				if (n_i > 0) {
					samples[i]++;
					
					double pEstimMean = estimProba[i] / (double) n_i;
					double diff = pObs_i - pEstimMean;
					
					mse[i] += diff * diff;
					bias[i] += Math.abs(pObs_i - pRnd_i);
					
					pEstim_k[i].adjustOrPutValue(k, pEstimMean, pEstimMean);
					samples_k[i].adjustOrPutValue(k, 1, 1);
				}
				/*
				 * boxplot for probability to sample k
				 */
				TIntObjectHashMap<TDoubleArrayList> k_table = pObsList_k.get(i);
				TDoubleArrayList list = k_table.get(k);
				
				if(list == null) {
					list = new TDoubleArrayList(nSimulations[i]);
					k_table.put(k, list);
				}
				
				list.add(pObs_i);
				maxListSize = Math.max(maxListSize, list.size());
			}
		}
		/*
		 * calculate averages
		 */
		for(int i = 0; i <= maxIteration; i++) {
			nSampledVertices[i] = (int) (nSampledVertices[i] / (double)nSimulations[i]);
			mse[i] = mse[i] / (double)samples[i];
			bias[i] = bias[i] / (double)samples[i];
			
			if(pObs_k[i] != null) {
				TDoubleDoubleIterator it = pObs_k[i].iterator();
				for(int k = 0; k < pObs_k[i].size(); k++) {
					it.advance();
					it.setValue(it.value() / (double)kHist.get((int)it.key()));
				}
			}
			
			if(pEstim_k[i] != null) {
				TDoubleDoubleIterator it = pEstim_k[i].iterator();
				for(int k = 0; k < pEstim_k[i].size(); k++) {
					it.advance();
					it.setValue(it.value() / (double)samples_k[i].get((int)it.key()));
				}
			}
		}
		/*
		 * write data
		 */
		writeIntArray(nSampledVertices, String.format("%1$s/n_sampled.txt", output), "it\tn");
		writeDoubleArray(mse, String.format("%1$s/mse.txt", output), "it\tmse");
		writeDoubleArray(bias, String.format("%1$s/bias.txt", output), "it\tbias");
		writeHistogramArray(pObs_k, output, "pobs");
		writeHistogramArray(pEstim_k, output, "pestim");
		/*
		 * boxplot
		 */
		for(int i = 0; i < pObsList_k.size(); i++) {
			BufferedWriter writer = new BufferedWriter(new FileWriter(String.format("%1$s/%2$s.pObsBoxplot.txt", output, i)));
			TIntObjectHashMap<TDoubleArrayList> k_table = pObsList_k.get(i);
			int[] keys = k_table.keys();
			Arrays.sort(keys);
			
			for(int k : keys) {
				writer.write(String.valueOf(k));
				writer.write("\t");
			}
			writer.newLine();
			
			for(int j = 0; j < maxListSize; j++) {
				for(int k : keys) {
					TDoubleArrayList list = k_table.get(k);
					if(list != null) {
						if(j < list.size()) {
							writer.write(String.valueOf(list.get(j)));
						}
						writer.write("\t");
					}
				}
				writer.newLine();
			}
			writer.close();
		}
		/*
		 * weighted estim diff
		 */
		for (int i = 0; i <= maxIteration; i++) {
			BufferedWriter writer = new BufferedWriter(new FileWriter(String.format("%1$s/%2$s.wdiff.txt", output, i)));
			writer.write("k\twdiff");
			writer.newLine();

			double ks[] = pObs_k[i].keys();
			Arrays.sort(ks);

			for (int j = 0; j < ks.length; j++) {
				double k = ks[j];
				double diff = (Math.abs(pEstim_k[i].get(k) - pObs_k[i].get(k))) * samples_k[i].get((int) k);
				writer.write(String.valueOf(k));
				writer.write("\t");
				writer.write(String.valueOf(diff));
				writer.newLine();
			}
			writer.close();

		}
	}
	
	private void writeIntArray(int[] array, String filename, String header) throws IOException {
		BufferedWriter writer = new BufferedWriter(new FileWriter(filename));
		writer.write(header);
		writer.newLine();
		for(int i = 0; i < array.length; i++) {
			writer.write(String.valueOf(i));
			writer.write("\t");
			writer.write(String.valueOf(array[i]));
			writer.newLine();
		}
		writer.close();
	}
	
	private void writeDoubleArray(double[] array, String filename, String header) throws IOException {
		BufferedWriter writer = new BufferedWriter(new FileWriter(filename));
		writer.write(header);
		writer.newLine();
		for(int i = 0; i < array.length; i++) {
			writer.write(String.valueOf(i));
			writer.write("\t");
			writer.write(String.valueOf(array[i]));
			writer.newLine();
		}
		writer.close();
	}
	
	private void writeHistogramArray(TDoubleDoubleHashMap[] array, String basedir, String filename) throws FileNotFoundException, IOException {
		for(int i = 0; i < array.length; i++) {
			Distribution.writeHistogram(array[i], String.format("%1$s/%2$s.%3$s.txt", basedir, i, filename));
		}
	}
	
	public static void main(String args[]) throws IOException {
		final String MODULE_NAME = "estimatortest";
		
		Config config = Loader.loadConfig(args[0]);
		
		SparseGraphMLReader reader = new SparseGraphMLReader();
		Graph graph = reader.readGraph(config.getParam(MODULE_NAME, "graphfile"));
		
		int nSims = Integer.parseInt(config.findParam(MODULE_NAME, "simulations"));
		int seeds = Integer.parseInt(config.findParam(MODULE_NAME, "seeds"));
		double proba = Double.parseDouble(config.findParam(MODULE_NAME, "responserate"));
		String output = config.getParam(MODULE_NAME, "output");
		String estimtype = config.getParam(MODULE_NAME, "estimtype");
		
		Level level = Logger.getRootLogger().getLevel();
		Logger.getRootLogger().setLevel(Level.WARN);
		
		EstimatorTest counter = new EstimatorTest(graph);
		for(int i = 0; i < nSims; i++) {
			Sampler<Graph, Vertex, Edge> sampler = new Sampler<Graph, Vertex, Edge>();
			sampler.setSeedGenerator(new FixedSizeRandomPartition<Vertex>(seeds, (long) (Math.random() * nSims)));
			sampler.setResponseGenerator(new RandomPartition<Vertex>(proba, (long) (Math.random() * nSims)));
			counter.reset(graph, estimtype);
			sampler.setListener(counter);
			sampler.run(graph);
			if(i % 10 == 0) {
				Logger.getRootLogger().setLevel(level);
				logger.info(String.format("%1$s simulations done.", i));
				Logger.getRootLogger().setLevel(Level.WARN);
			}
		}
		Logger.getRootLogger().setLevel(level);
		logger.info("Analyzing...");
		counter.analyze(graph, output);
		logger.info("Done.");
	}
	
}
