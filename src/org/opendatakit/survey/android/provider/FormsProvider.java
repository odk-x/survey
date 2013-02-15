/*
 * Copyright (C) 2007 The Android Open Source Project
 * Copyright (C) 2011-2013 University of Washington
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

import org.opendatakit.common.android.provider.impl.FormsProviderImpl;
import org.opendatakit.survey.android.application.Survey;

import android.net.Uri;

/**
 *
 */
public class FormsProvider extends FormsProviderImpl {

	public String getWebDbPath() {
		return Survey.WEBDB_PATH;
	}

	public String getFormsPath() {
		return Survey.FORMS_PATH;
	}

	public String getStaleFormsPath() {
		return Survey.STALE_FORMS_PATH;
	}

	public String getFormsAuthority() {
		return FormsProviderAPI.AUTHORITY;
	}

	public Uri getFormsContentUri() {
		return FormsProviderAPI.CONTENT_URI;
	}
}
