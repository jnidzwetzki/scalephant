/*******************************************************************************
 *
 *    Copyright (C) 2015-2018 the BBoxDB project
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
package org.bboxdb.network.query.transformation;

import org.bboxdb.commons.math.Hyperrectangle;
import org.bboxdb.network.query.entity.TupleAndBoundingBox;

public class EnlargeTransformation implements TupleTransformation {
	
	/**
	 * The enlargement factor
	 */
	private int factor;

	public EnlargeTransformation(final int factor) {
		this.factor = factor;
	}

	@Override
	public TupleAndBoundingBox apply(final TupleAndBoundingBox input) {
		
		final Hyperrectangle inputBox = input.getBoundingBox();
		final Hyperrectangle resultBox = inputBox.enlarge(factor);	
		
		return new TupleAndBoundingBox(input.getTuple(), resultBox);
	}
}
