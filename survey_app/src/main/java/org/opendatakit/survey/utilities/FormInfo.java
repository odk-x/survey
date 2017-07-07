package org.opendatakit.survey.utilities;

/**
 * @author mitchellsundt@gmail.com
 */
public final class FormInfo {
  /**
   * The id of the table that the form adds rows to
   */
  public final String tableId;
  /**
   * The form id for the form
   */
  public final String formId;
  /**
   * The form version
   */
  public final String formVersion;
  /**
   * The display name of the form
   */
  public final String formDisplayName;
  /**
   * The text to display under the display name
   */
  public final String formDisplaySubtext;

  /**
   * Simple constructor that just saves its arguments
   *
   * @param tableId            The id of the table that the form adds rows to
   * @param formId             The form id for the form
   * @param formVersion        The form version
   * @param formDisplayName    The display name of the form
   * @param formDisplaySubtext The text to display under the display name
   */
  FormInfo(String tableId, String formId, String formVersion, String formDisplayName,
      String formDisplaySubtext) {
    this.tableId = tableId;
    this.formId = formId;
    this.formVersion = formVersion;
    this.formDisplayName = formDisplayName;
    this.formDisplaySubtext = formDisplaySubtext;
  }
}
