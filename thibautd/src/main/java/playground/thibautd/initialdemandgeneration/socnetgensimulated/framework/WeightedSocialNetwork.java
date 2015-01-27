/* *********************************************************************** *
 * project: org.matsim.*
 * WeightedSocialNetwork.java
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
package playground.thibautd.initialdemandgeneration.socnetgensimulated.framework;

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Identifiable;
import org.matsim.api.core.v01.population.Person;

/**
 * Stores ties and their utilities, if they are over a pre-defined threshold.
 * This allows to only consider the most relevant potential contacts from iteration
 * 2 of the calibration: contacts with a low utility are discarded forever,
 * and never looked back at.
 * @author thibautd
 */
public class WeightedSocialNetwork {
	private static final Logger log =
		Logger.getLogger(WeightedSocialNetwork.class);

	private final Map<Id<Person>, WeightedFriends> altersMap = new ConcurrentHashMap< >();
	private final double lowestAllowedWeight;

	private final int initialSize;

	public WeightedSocialNetwork(
			final int initialSize,
			final double lowestWeight ) {
		this.initialSize = initialSize;
		this.lowestAllowedWeight = lowestWeight;
	}

	public WeightedSocialNetwork( final double lowestWeight ) {
		this( 20 , lowestWeight );
	}

	public void addEgo( final Id<Person> ego ) {
		altersMap.put( ego , new WeightedFriends( initialSize ) );
	}

	public void addEgosIds( final Collection<Id<Person>> egos ) {
		for ( Id<Person> ego : egos ) {
			altersMap.put( ego , new WeightedFriends( initialSize ) );
		}
	}

	public void addEgosIdentifiable( final Collection<? extends Identifiable<Person>> egos ) {
		for ( Identifiable<Person> ego : egos ) {
			altersMap.put( ego.getId() , new WeightedFriends( initialSize ) );
		}
	}

	public void clear() {
		altersMap.clear();
	}

	public void addBidirectionalTie(
			final Id<Person> ego,
			final Id<Person> alter,
			final double weight ) {
		if ( weight < lowestAllowedWeight ) return;
		altersMap.get( ego ).add( alter , weight );
		altersMap.get( alter ).add( ego , weight );
	}

	/*
	public void addMonodirectionalTie(
			final Id<Person> ego,
			final Id<Person> alter,
			final double weight ) {
		if ( weight < lowestAllowedWeight ) return;
		altersMap.get( ego ).add( alter , weight );
	}
	*/


	public Set<Id<Person>> getAltersOverWeight(
			final Id<Person> ego,
			final double weight ) {
		if ( weight < lowestAllowedWeight ) throw new IllegalArgumentException( "weight "+weight+" is lower than lowest stored weight "+lowestAllowedWeight );
		return altersMap.get( ego ).getAltersOverWeight( weight );
	}

	/* unused
	public Set<Id<Person>> getAltersInWeightInterval(
			final Id<Person> ego,
			final double low,
			final double high ) {
		if ( low < lowestAllowedWeight ) throw new IllegalArgumentException( "weight "+low+" is lower than lowest stored weight "+lowestAllowedWeight );
		if ( low > high ) throw new IllegalArgumentException( "lower bound "+low+" is higher than higher bound "+high );
		return altersMap.get( ego ).getAltersIn( low , high );
	}
	*/

	// for tests
	/*package*/ int getSize( final Id<Person> ego ) {
		return altersMap.get( ego ).size;
	}

	private static final class WeightedFriends {
		private Id[] friends = new Id[ 20 ];
		// use float instead of double for saving memory.
		// TODO: check robustness of the results facing this...
		private float weights[] = new float[ 20 ];
		// as such, using short here might look as overdoing...
		// but we have one such structure per agent: this might make sense.
		private short size = 0;

		public WeightedFriends( final int initialSize ) {
			this.friends = new Id[ initialSize ];
			this.weights = new float[ initialSize ];
			Arrays.fill( weights , Float.POSITIVE_INFINITY  );
		}

		public synchronized void add( final Id<Person> friend , final double weight ) {
			final float fweight = (float) weight; // TODO check overflow?
			final int insertionPoint = getInsertionPoint( fweight );
			insert( friend, insertionPoint );
			insert( fweight, insertionPoint );
			size++;
			assert size <= friends.length;
			assert weights.length == friends.length;
		}

		public Set<Id<Person>> getAltersOverWeight( final double weight ) {
			final Set<Id<Person>> alters = new LinkedHashSet<Id<Person>>();
			for ( int i = size - 1;
					i >= 0 && weights[ i ] >= weight;
					i-- ) {
				alters.add( friends[ i ] );
			}
			return alters;
		}

		/*
		public Set<Id<Person>> getAltersIn( final double low , final double high ) {
			final int lowInsertionPoint = getInsertionPoint( low );
			final int highInsertionPoint = getInsertionPoint( high , lowInsertionPoint );
			assert highInsertionPoint >= lowInsertionPoint;

			final Set<Id<Person>> alters = new LinkedHashSet<Id<Person>>();
			for ( int i = lowInsertionPoint; i < highInsertionPoint; i++ ) {
				alters.add( friends[ i ] );
			}
			return alters;
		}
		*/

		private int getInsertionPoint( final float weight ) {
			return getInsertionPoint( weight , 0 );
		}

		private int getInsertionPoint( final float weight , final int from ) {
			// only search the range actually filled with values.
			// lower index can be specified, if known
			final int index = Arrays.binarySearch( weights , from , size , weight );
			return index >= 0 ? index : - 1 - index;
		}

		private void insert( Id<Person> friend , int insertionPoint ) {
			if ( log.isTraceEnabled() ) {
				log.trace( "insert "+friend+" at "+insertionPoint+" in array of size "+friends.length+" with data size "+size );
			}

			if ( size == friends.length ) {
				friends = Arrays.copyOf( friends , size * 2 );
			}

			for ( int i = size; i > insertionPoint; i-- ) {
				friends[ i ] = friends[ i - 1 ];
			}

			friends[ insertionPoint ] = friend;
		}

		private void insert( float weight , int insertionPoint ) {
			if ( log.isTraceEnabled() ) {
				log.trace( "insert "+weight+" at "+insertionPoint+" in array of size "+weights.length+" with data size "+size );
			}

			if ( size == weights.length ) {
				weights = Arrays.copyOf( weights , size * 2 );
				for ( int i = size; i < weights.length; i++ ) weights[ i ] = Float.POSITIVE_INFINITY;
			}

			for ( int i = size; i > insertionPoint; i-- ) {
				weights[ i ] = weights[ i - 1 ];
			}

			weights[ insertionPoint ] = weight;
		}
	}
}

