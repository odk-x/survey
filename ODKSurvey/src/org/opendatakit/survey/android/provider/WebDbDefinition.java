/*
 * Copyright (C) 2012 University of Washington
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package org.opendatakit.survey.android.provider;

import java.io.File;

/**
 * Description of the tables available in the WebSQL-controlled databases list.
 *
 * @author mitchellsundt@gmail.com
 *
 */
class WebDbDefinition {
	final String shortName;
	final String displayName;
	final Integer estimatedSize;
	final File dbFile;
	WebDbDefinition(String shortName, String displayName, Integer estimatedSize, File dbFile ) {
		this.shortName = shortName;
		this.displayName = displayName;
		this.estimatedSize = estimatedSize;
		this.dbFile = dbFile;
	}
}