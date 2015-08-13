/* *********************************************************************** *
 * project: org.matsim.*
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2015 by the members listed in the COPYING,        *
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

package playground.johannes.synpop.sim;

import gnu.trove.TIntDoubleHashMap;
import gnu.trove.TIntDoubleIterator;
import gnu.trove.TIntIntHashMap;
import playground.johannes.gsv.synPop.sim3.Hamiltonian;
import playground.johannes.sna.math.Discretizer;
import playground.johannes.synpop.data.Person;
import playground.johannes.synpop.data.PlainPerson;
import playground.johannes.synpop.sim.data.CachedPerson;
import playground.johannes.synpop.sim.data.Converters;
import playground.johannes.synpop.sim.util.DynamicDoubleArray;
import playground.johannes.synpop.sim.util.DynamicIntArray;

import java.util.Set;

/**
 * @author johannes
 */
public class MultivariatMean implements Hamiltonian, AttributeChangeListener {

    private Object xDataKey;

    private Object yDataKey;

    private Discretizer xDataDiscr;

    private DynamicDoubleArray referenceValues;

    private DynamicDoubleArray bucketSums;

    private DynamicIntArray bucketCounts;

    private double hamiltonianValue;

    public MultivariatMean(Set<? extends Person> refPersons, Set<CachedPerson> simPersons, String xAttrKey, String
            yAttrKey, Discretizer xDataDiscr) {
        this.xDataKey = Converters.getObjectKey(xAttrKey);
        this.yDataKey = Converters.getObjectKey(yAttrKey);
        this.xDataDiscr = xDataDiscr;

        initReferenceValues(refPersons, xAttrKey, yAttrKey);
        initSimulationValues(simPersons, xAttrKey, yAttrKey);

        // Calculate the initial hamiltonian value.
        hamiltonianValue = 0;
        int size = Math.max(referenceValues.size(), bucketCounts.size());
        for(int i = 0; i < size; i++) {
            hamiltonianValue += calculateDiff(i);
        }
    }

    private void initReferenceValues(Set<? extends Person> persons, String xAttrKey, String yAttrKey) {
        referenceValues = new DynamicDoubleArray(100, Double.NaN);

        DynamicDoubleArray sums = new DynamicDoubleArray(100, Double.NaN);
        DynamicIntArray counts = new DynamicIntArray(100, -1);

        calculateBuckets(persons, sums, counts, xAttrKey, yAttrKey);

        for(int i = 0; i < sums.size(); i++) {
            double sum = sums.get(i);
            int cnt = counts.get(i);
            if(!Double.isNaN(sum) && cnt > 0) {
                referenceValues.set(i, sum/(double)cnt);
            }
        }
    }

    private void initSimulationValues(Set<CachedPerson> persons, String xAttrKey, String yAttrKey) {
        bucketSums = new DynamicDoubleArray(100, Double.NaN);
        bucketCounts = new DynamicIntArray(100, -1);

        calculateBuckets(persons, bucketSums, bucketCounts, xAttrKey, yAttrKey);
    }

    private void calculateBuckets(Set<? extends Person> persons, DynamicDoubleArray sums, DynamicIntArray counts,
                                  String xAttrKey, String yAttrKey) {
        TIntDoubleHashMap sumBuckets = new TIntDoubleHashMap();
        TIntIntHashMap countBuckets = new TIntIntHashMap();

        for(Person person : persons) {
            String xValStr = person.getAttribute(xAttrKey);
            String yValStr = person.getAttribute(yAttrKey);

            if(xValStr != null && yValStr != null) {
                double xVal = Double.parseDouble(xValStr);
                double yVal = Double.parseDouble(yValStr);

                int bucketIdx = xDataDiscr.index(xVal);

                sumBuckets.adjustOrPutValue(bucketIdx, yVal, yVal);
                countBuckets.adjustOrPutValue(bucketIdx, 1, 1);
            }
        }

        TIntDoubleIterator it = sumBuckets.iterator();
        for(int i = 0; i < sumBuckets.size(); i++) {
            it.advance();
            int bucketIndex = it.key();
            double sum = it.value();
            int cnt = countBuckets.get(bucketIndex);

            sums.set(bucketIndex, sum);
            counts.set(bucketIndex, cnt);
        }
    }

    @Override
    public void onChange(Object dataKey, double oldValue, double newValue, CachedPerson person) {
        if(dataKey.equals(yDataKey)) {
            onYValueChange(oldValue, newValue, person);
        } else if(dataKey.equals(xDataKey)) {
            onXValueChange(oldValue, newValue, person);
        }
    }

    private void onXValueChange(double oldValue, double newValue, CachedPerson person) {
        Double yVal = (Double)person.getData(yDataKey);
        int oldBucketIndex = xDataDiscr.index(oldValue);
        double hValue1 = changeBucketContent(oldBucketIndex, yVal, false);

        int newBucketIndex = xDataDiscr.index(newValue);
        double hValue2 = changeBucketContent(newBucketIndex, yVal, true);

        hamiltonianValue += (hValue1 + hValue2);
    }

    private double changeBucketContent(int bucketIndex, double value, boolean add) {
        double oldDiff = calculateDiff(bucketIndex);

        double sum = bucketSums.get(bucketIndex);
        int count = bucketCounts.get(bucketIndex);

        if(add) {
            bucketSums.set(bucketIndex, sum + value);
            bucketCounts.set(bucketIndex, count + 1);
        } else {
            bucketSums.set(bucketIndex, sum - value);
            bucketCounts.set(bucketIndex, count - 1);
        }

        double newDiff = calculateDiff(bucketIndex);

        return newDiff - oldDiff;
    }

    private void onYValueChange(double oldValue, double newValue, CachedPerson person) {
        Double xVal = (Double)person.getData(xDataKey);
        int bucketIndex = xDataDiscr.index(xVal);

        double oldDiff = calculateDiff(bucketIndex);

        double delta = newValue - oldValue;
        double sum = bucketSums.get(bucketIndex);
        bucketSums.set(bucketIndex, sum + delta);

        double newDiff = calculateDiff(bucketIndex);

        hamiltonianValue += newDiff - oldDiff;
    }

    @Override
    public double evaluate(PlainPerson person) {
        return hamiltonianValue;
    }

    private double calculateDiff(int bucketIndex) {
        double refValue = referenceValues.get(bucketIndex);
        if(Double.isNaN(refValue)) {
            // There is no reference value for this bucket. We cannot do any comparison.
            return 0.0;
        } else {
            double sum = bucketSums.get(bucketIndex);
            int cnt = bucketCounts.get(bucketIndex);

            if(!Double.isNaN(sum) && cnt > 0) {
                double simValue = sum/(double)cnt;

                return Math.abs(refValue - simValue);
            } else {
                return 0.0;
            }
        }
    }
}
