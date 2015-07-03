/*
 * Copyright (C) 2014 University of Washington
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.opendatakit.survey.android.fragments;

import android.os.Parcel;
import android.os.Parcelable;

public class FormDeleteListFragmentSelection implements Parcelable {

  public final String tableId;
  public final String formId;
  public final String formName;
  public final String formVersion;

  public FormDeleteListFragmentSelection(String tableId, String formId, String formName,
      String formVersion) {
    this.tableId = tableId;
    this.formId = formId;
    this.formName = formName;
    this.formVersion = formVersion;
  }

  public int describeContents() {
    return 0;
  }

  public void writeToParcel(Parcel dest, int flags) {
    dest.writeStringArray(new String[] { this.tableId, this.formId, this.formName, this.formVersion });
  }

  public static final Parcelable.Creator<FormDeleteListFragmentSelection> CREATOR = new Parcelable.Creator<FormDeleteListFragmentSelection>() {
    public FormDeleteListFragmentSelection createFromParcel(Parcel in) {
      return new FormDeleteListFragmentSelection(in);
    }

    public FormDeleteListFragmentSelection[] newArray(int size) {
      return new FormDeleteListFragmentSelection[size];
    }
  };

  public FormDeleteListFragmentSelection(Parcel in) {
    String[] data = new String[4];

    in.readStringArray(data);
    this.tableId = data[0];
    this.formId = data[1];
    this.formName = data[2];
    this.formVersion = data[3];
  }

  public boolean equals(Object obj) {
    if (obj == null || this.getClass() != obj.getClass()) {
      return false;
    }

    final FormDeleteListFragmentSelection other = (FormDeleteListFragmentSelection) obj;

    // Check if formId is equivalent
    if (this.tableId == null) {
      if (other.tableId != null) {
        return false;
      }
    } else if (!this.tableId.equals(other.tableId)) {
      return false;
    }

    // Check if formId is equivalent
    if (this.formId == null) {
      if (other.formId != null) {
        return false;
      }
    } else if (!this.formId.equals(other.formId)) {
      return false;
    }

    // Check if formName is equivalent
    if (this.formName == null) {
      if (other.formName != null) {
        return false;
      }
    } else if (!this.formName.equals(other.formName)) {
      return false;
    }

    // Check if formVersion is equivalent
    if (this.formVersion == null) {
      if (other.formVersion != null) {
        return false;
      }
    } else if (!this.formVersion.equals(other.formVersion)) {
      return false;
    }

    return true;
  }

  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result
        + prime * ((this.tableId == null) ? 0 : this.tableId.hashCode())
        + prime * ((this.formId == null) ? 0 : this.formId.hashCode())
        + prime * ((this.formName == null) ? 0 : this.formName.hashCode())
        + prime * ((this.formVersion == null) ? 0 : this.formVersion.hashCode());
    return result;
  }
}
