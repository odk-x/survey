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
import java.io.FileNotFoundException;
import java.io.IOException;

import org.opendatakit.survey.android.application.Survey;

import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

/**
 * The WebKit does better if there is a content provider vending files to it.
 * This provider vends files under the Forms and Instances directories (only).
 *
 * @author mitchellsundt@gmail.com
 *
 */
public class FileProvider extends ContentProvider {
	public static final String FORM_FILE_AUTHORITY = "org.opendatakit.survey.android.provider.file.forms";
	public static final String INSTANCE_FILE_AUTHORITY = "org.opendatakit.survey.android.provider.file.instances";

	public static final String FORMS_URL_PREFIX = ContentResolver.SCHEME_CONTENT
			+ "://" + FileProvider.FORM_FILE_AUTHORITY;
	public static final String INSTANCES_URL_PREFIX = ContentResolver.SCHEME_CONTENT
			+ "://" + FileProvider.INSTANCE_FILE_AUTHORITY;

	public static String getFormOriginString() {
		return ContentResolver.SCHEME_CONTENT + "_" + FORM_FILE_AUTHORITY
				+ "_0";
	}

	public static File getAsFile(String uri) {
		if (uri.startsWith(FORMS_URL_PREFIX)) {
			return new File(Survey.FORMS_PATH, uri.substring(FORMS_URL_PREFIX
					.length()));
		} else if (uri.startsWith(INSTANCES_URL_PREFIX)) {
			return new File(Survey.INSTANCES_PATH,
					uri.substring(INSTANCES_URL_PREFIX.length()));
		} else {
			throw new IllegalArgumentException("Not a valid uri: " + uri);
		}
	}

	public static String getAsUrl(File filePath) {

		String fullPath = filePath.getAbsolutePath();
		if (fullPath.startsWith(Survey.FORMS_PATH)) {
			fullPath = fullPath.substring(Survey.FORMS_PATH.length());
			fullPath = FORMS_URL_PREFIX + fullPath;
		} else if (fullPath.startsWith(Survey.INSTANCES_PATH)) {
			fullPath = fullPath.substring(Survey.INSTANCES_PATH.length());
			fullPath = INSTANCES_URL_PREFIX + fullPath;
		} else {
			throw new IllegalArgumentException("Invalid file access: "
					+ filePath.getAbsolutePath());
		}
		return fullPath;
	}

	@Override
	public ParcelFileDescriptor openFile(Uri uri, String mode)
			throws FileNotFoundException {
		String path = uri.getPath();

		File pathFile;
		if (uri.getAuthority().equalsIgnoreCase(FORM_FILE_AUTHORITY)) {
			pathFile = new File(Survey.FORMS_PATH);
		} else {
			pathFile = new File(Survey.INSTANCES_PATH);
		}

		File realFile = new File(pathFile, path);

		try {
			String parentPath = pathFile.getCanonicalPath();
			String fullPath = realFile.getCanonicalPath();
			if (!fullPath.startsWith(parentPath)) {
				throw new FileNotFoundException("Canonical path violation: "
						+ realFile.getAbsolutePath());
			}
		} catch (FileNotFoundException e) {
			e.printStackTrace();
			throw e;
		} catch (IOException e) {
			e.printStackTrace();
			throw new FileNotFoundException("Canonical path violation: "
					+ realFile.getAbsolutePath());
		}

		return ParcelFileDescriptor.open(realFile,
				ParcelFileDescriptor.MODE_READ_ONLY);
	}

	@Override
	public int delete(Uri uri, String selection, String[] selectionArgs) {
		return 0;
	}

	@Override
	public String getType(Uri uri) {
		return null;
	}

	@Override
	public Uri insert(Uri uri, ContentValues values) {
		return null;
	}

	@Override
	public boolean onCreate() {
		return true;
	}

	@Override
	public Cursor query(Uri uri, String[] projection, String selection,
			String[] selectionArgs, String sortOrder) {
		return null;
	}

	@Override
	public int update(Uri uri, ContentValues values, String selection,
			String[] selectionArgs) {
		return 0;
	}

}
