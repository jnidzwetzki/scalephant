/*******************************************************************************
 *
 *    Copyright (C) 2015-2021 the BBoxDB project
 *  
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *  
 *      http://www.apache.org/licenses/LICENSE-2.0
 *  
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License. 
 *    
 *******************************************************************************/
package org.bboxdb;

import org.bboxdb.commons.math.Hyperrectangle;
import org.bboxdb.tools.helper.RandomQueryRangeGenerator;
import org.junit.Assert;
import org.junit.Test;

public class TestRandomQueryRangeGenerator {
	
	protected final static float EQUALS_DELTA = 0.001f;

	
	/**
	 * Test the rectangles in 2 dimensions
	 */
	@Test(timeout=60000)
	public void test2Dimension() {
		
		final Hyperrectangle completeSpace = new Hyperrectangle(0d, 100d, 0d, 500d);
		
		for(int i = 0; i < 1000; i++) {
			final Hyperrectangle queryHyperrectangle = RandomQueryRangeGenerator.getRandomQueryRange(completeSpace, 0.01);			
			Assert.assertEquals(completeSpace.getVolume() / 100, queryHyperrectangle.getVolume(), EQUALS_DELTA);
			Assert.assertTrue(completeSpace.isCovering(queryHyperrectangle));
		}
		
	}
	
	/**
	 * Test the rectangles in 3 dimensions
	 */
	@Test(timeout=60000)
	public void test3Dimension() {
		
		final Hyperrectangle completeSpace = new Hyperrectangle(0d, 100d, 0d, 500d, 0d, 600d);
		
		for(int i = 0; i < 1000; i++) {
			final Hyperrectangle queryHyperrectangle = RandomQueryRangeGenerator.getRandomQueryRange(completeSpace, 0.01);			
			Assert.assertEquals(completeSpace.getVolume() / 100, queryHyperrectangle.getVolume(), EQUALS_DELTA);
			Assert.assertTrue(completeSpace.isCovering(queryHyperrectangle));
		}
		
	}
}
