/*
 * Copyright (C) 2012-2013 University of Washington
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
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.codehaus.jackson.JsonGenerationException;
import org.codehaus.jackson.JsonParseException;
import org.codehaus.jackson.map.JsonMappingException;

/**
 * Holds the files required for a submission to the ODK Aggregate legacy
 * interface
 *
 * @author mitchellsundt@gmail.com
 */
public class FileSet {
	private static final String APPLICATION_XML = "application/xml";
	private static final String URI = "uri";
	private static final String CONTENT_TYPE = "contentType";
	public File instanceFile = null;

	public static class MimeFile {
		public File file;
		public String contentType;
	};

	public ArrayList<MimeFile> attachmentFiles = new ArrayList<MimeFile>();

	public FileSet() {
	}

	public void addAttachmentFile(File file, String contentType) {
		MimeFile f = new MimeFile();
		f.file = file;
		f.contentType = contentType;
		attachmentFiles.add(f);
	}

	public String serialize() throws JsonGenerationException,
			JsonMappingException, IOException {
		ArrayList<HashMap<String, String>> str = new ArrayList<HashMap<String, String>>();

		HashMap<String, String> map;
		map = new HashMap<String, String>();
		map.put(URI, FileProvider.getAsUrl(instanceFile));
		map.put(CONTENT_TYPE, APPLICATION_XML);
		str.add(map);
		for (MimeFile f : attachmentFiles) {
			map = new HashMap<String, String>();
			map.put(URI, FileProvider.getAsUrl(f.file));
			map.put(CONTENT_TYPE, f.contentType);
			str.add(map);
		}

		String serializedString = DataModelDatabaseHelper.mapper
				.writeValueAsString(str);
		return serializedString;
	}

	public static final FileSet parse(InputStream src)
			throws JsonParseException, JsonMappingException, IOException {
		@SuppressWarnings("unchecked")
		ArrayList<Map<String, String>> str = DataModelDatabaseHelper.mapper
				.readValue(src, ArrayList.class);

		FileSet fs = new FileSet();
		Map<String, String> map;
		map = (Map<String, String>) str.get(0);
		fs.instanceFile = FileProvider.getAsFile(map.get(URI));
		for (int i = 1; i < str.size(); ++i) {
			map = (Map<String, String>) str.get(i);
			MimeFile f = new MimeFile();
			f.file = FileProvider.getAsFile(map.get(URI));
			f.contentType = map.get(CONTENT_TYPE);
			fs.attachmentFiles.add(f);
		}
		return fs;
	}

}