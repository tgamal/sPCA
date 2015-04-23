/**
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.qcri.sparkpca;

import org.apache.spark.AccumulatorParam;

/**
 * This class supports Accumulator of type double[]. It implements an element-by-element comparison operation between
 * two double[] and it returns the a double[] that contains the minimum from each two corresponding elements 
 * 
 * @author Tarek Elgamal
 *
 */
public class VectorAccumulatorMinParam implements AccumulatorParam<double[]> {



	public double[] addInPlace(double[] arg0, double[] arg1) {
		for(int i=0; i< arg0.length; i++)
		{
			if(arg1[i] < arg0[i])
				arg0[i]=arg1[i];
		}
		return arg0;
	}
	public double[] zero(double[] arg0) {
		for(int i=0; i< arg0.length; i++)
			arg0[i]=Double.POSITIVE_INFINITY;
		return arg0;
	}

	public double[] addAccumulator(double[] arg0, double[] arg1) {
		return addInPlace(arg0, arg1);
	}

}