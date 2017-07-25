package org.opendatakit.survey.utilities;

/**
 * @author mitchellsundt@gmail.com
 */
public final class FormInfo {
  public final String tableId;
  public final String formId;
  public final String formVersion;
  public final String formDisplayName;
  public final String formDisplaySubtext;

  FormInfo(String tableId, String formId, String formVersion, String formDisplayName, String
      formDisplaySubtext) {
    this.tableId = tableId;
    this.formId = formId;
    this.formVersion = formVersion;
    this.formDisplayName = formDisplayName;
    this.formDisplaySubtext = formDisplaySubtext;
  }
}
