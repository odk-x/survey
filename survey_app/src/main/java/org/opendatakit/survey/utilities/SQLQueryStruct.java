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
package org.opendatakit.survey.utilities;

import org.opendatakit.database.queries.BindArgs;

/**
 * Basic holder for the components of a SQL query.
 *
 * @author sudar.sam@gmail.com
 */
public class SQLQueryStruct {

    /**
     * A sql clause that narrows down the list of returned rows
     */
    public String whereClause;
    /**
     * TODO
     */
    public BindArgs selectionArgs;
    /**
     * A list of columns to group by
     */
    public String[] groupBy;
    /**
     * A SQL having clause
     */
    public String having;
    /**
     * The column id of the column to sort the results by
     */
    public String orderByElementKey;
    /**
     * the direction to sort by, ASC for ascending, DESC for descending
     */
    public String orderByDirection;

    /**
     * A simple constructor that stores its properties
     *
     * @param whereClause       A sql clause that narrows down the list of returned rows
     * @param selectionArgs     TODO
     * @param groupBy           A list of columns to group by
     * @param having            A SQL having clause
     * @param orderByElementKey The column id of the column to sort the results by
     * @param orderByDirection  the direction to sort by, ASC for ascending, DESC for descending
     */
    public SQLQueryStruct(String whereClause, BindArgs selectionArgs, String[] groupBy, String having,
                          String orderByElementKey, String orderByDirection) {
        this.whereClause = whereClause;
        this.selectionArgs = selectionArgs;
        this.groupBy = groupBy;
        this.having = having;
        this.orderByElementKey = orderByElementKey;
        this.orderByDirection = orderByDirection;
    }

}
