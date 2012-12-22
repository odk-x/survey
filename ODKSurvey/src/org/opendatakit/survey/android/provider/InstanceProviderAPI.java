/*
 * Copyright (C) 2007 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.opendatakit.survey.android.provider;

import android.net.Uri;
import android.provider.BaseColumns;

/**
 * Convenience definitions for NotePadProvider
 */
public final class InstanceProviderAPI {
    public static final String AUTHORITY = "org.opendatakit.survey.android.provider.instances";

    // This class cannot be instantiated
    private InstanceProviderAPI() {}

    // saved status from row in data table:
    public static final String STATUS_INCOMPLETE = "INCOMPLETE";
    public static final String STATUS_COMPLETE = "COMPLETE";

    // xmlPublishStatus from instances db:
    public static final String STATUS_SUBMITTED = "submitted";
    public static final String STATUS_SUBMISSION_FAILED = "submissionFailed";

    /**
     * Notes table
     */
    public static final class InstanceColumns implements BaseColumns {
        // This class cannot be instantiated
        private InstanceColumns() {}

        public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/");
        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/vnd.opendatakit.instance";
        public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/vnd.opendatakit.instance";

        // These are the only things needed for an insert
        // _ID is the index on the table maintained for ODK Survey purposes
        // DATA_TABLE_INSTANCE_ID ****MUST MATCH**** value used in javascript
        public static final String DATA_TABLE_INSTANCE_ID = "id"; // join on data table...
        public static final String XML_PUBLISH_TIMESTAMP = "xmlPublishTimestamp";
        public static final String XML_PUBLISH_STATUS = "xmlPublishStatus";
        public static final String DISPLAY_NAME = "displayName";
        public static final String DISPLAY_SUBTEXT = "displaySubtext";
    }
}
