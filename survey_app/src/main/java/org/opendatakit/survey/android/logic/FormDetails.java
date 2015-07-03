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

package org.opendatakit.survey.android.logic;

import java.io.Serializable;

/**
 * Information about the forms available for download from the OpenRosa Form
 * Discovery API for legacy interaction with ODK Aggregate (assumes all those
 * forms have new-style formDef.json)
 *
 * @author mitchellsundt@gmail.com
 *
 */
public class FormDetails implements Serializable {
  /**
     *
     */
  private static final long serialVersionUID = 1L;

  public final String errorStr;

  public final String formName;
  public final String downloadUrl;
  public final String manifestUrl;
  public final String hash;
  public final String formID;
  public final String formVersion;

  public FormDetails(String error) {
    manifestUrl = null;
    downloadUrl = null;
    formName = null;
    hash = null;
    formID = null;
    formVersion = null;
    errorStr = error;
  }

  public FormDetails(String name, String url, String manifest, String id, String version,
      String hash) {
    manifestUrl = manifest;
    downloadUrl = url;
    formName = name;
    formID = id;
    formVersion = version;
    this.hash = hash;
    errorStr = null;
  }

}
