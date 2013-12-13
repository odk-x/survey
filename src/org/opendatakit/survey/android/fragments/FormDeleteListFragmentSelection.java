package org.opendatakit.survey.android.fragments;

import android.os.Parcel;
import android.os.Parcelable;

public class FormDeleteListFragmentSelection implements Parcelable{

  public final String formId;
  public final String formName;
  public final String formVersion;

  public FormDeleteListFragmentSelection(String formId, String formName, String formVersion) {
    this.formId = formId;
    this.formName = formName;
    this.formVersion = formVersion;
  }
		
  public int describeContents(){
    return 0;
  }

  public void writeToParcel(Parcel dest, int flags) {
    dest.writeStringArray(new String[] {this.formId, this.formName, this.formVersion});
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
    String[] data = new String[3];

    in.readStringArray(data);
    this.formId = data[0];
    this.formName = data[1];
    this.formVersion = data[2];
  }
	    
  public boolean equals(Object obj) {
    if (obj == null || this.getClass() != obj.getClass()) {
      return false;
    }

    final FormDeleteListFragmentSelection other = (FormDeleteListFragmentSelection) obj;
	        
    // Check if formId is equivalent
    if (this.formId == null) {
      if (other.formId != null) {
        return false;
      }
    } else if (!this.formId.equals(other.formId)){
      return false;
    }
	        
    // Check if formName is equivalent
    if (this.formName == null) {
      if (other.formName != null) {
	    return false;
      }
    } else if (!this.formName.equals(other.formName)){
	  return false;
    }
	        
    // Check if formVersion is equivalent
    if (this.formVersion == null) {
      if (other.formVersion != null) {
        return false;
      }
    } else if (!this.formVersion.equals(other.formVersion)){
      return false;
    }
	        
    return true;
  }

  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result
            + ((this.formId == null) ? 0 : this.formId.hashCode())
            + ((this.formName == null) ? 0 : this.formName.hashCode())
            + ((this.formVersion == null) ? 0 : this.formVersion.hashCode());
    return result;
  }
}
