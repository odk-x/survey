/*
 * Copyright (C) 2014 University of Washington
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.opendatakit.common.android.database.DataModelDatabaseHelperFactory;
import org.opendatakit.common.android.utilities.ODKDatabaseUtils;
import org.opendatakit.common.android.utilities.ODKFileUtils;
import org.opendatakit.common.android.utilities.WebLogger;

import android.annotation.SuppressLint;
import android.app.Service;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Binder;
import android.os.IBinder;

/**
 * This service holds a single-threaded Executor and
 * a work item queue to sequence a series of W3C SQL
 * interactions from a WebKit.  The SQL commands are
 * executed on the executor's thread and the results
 * are transmitted back through the UI thread.
 *
 * @author mitchellsundt@gmail.com
 *
 */
public class DbShimService extends Service {

  public interface DbShimCallback {
    public void fireCallback(final String fullCommand);
  }

  private static final String t = "DbShimService";

  /**
   * Open database transactions by appName and database generation.
   */
  private class DbShimAppContext {
    String appName;
    String generation;
    Map<Integer, SQLiteDatabase> transactions = new HashMap<Integer, SQLiteDatabase>();
  }

  /**
   * Map of appName to open database transactions
   */
  private Map<String, DbShimAppContext> appContexts = new HashMap<String, DbShimAppContext>();

  /**
   * Type of action to be performed on an appName and open database
   */
  private enum Action { INITIALIZE, ROLLBACK, COMMIT, STMT };

  /**
   * Action and its parameters
   */
  private class DbAction {
    String appName;
    String generation;
    DbShimCallback callback;
    Action theAction;
    int transactionGeneration;
    int actionIdx;
    String sqlStmt;
    String strBinds;
  };

  /**
   * Queue of actions to be performed
   */
  private final LinkedBlockingQueue<DbAction> actions = new LinkedBlockingQueue<DbAction>(10);

  /**
   * Work unit that is enqueued on the worker single-threaded executor service.
   * Picks an action off the actions queue and processes it.
   */
  private final Runnable workUnit = new Runnable() {
    @Override
    public void run() {
      try {
        DbAction action;
        action = actions.poll();
        if ( action != null ) {
          processAction(action);
        }
      } catch (Exception e) {
        e.printStackTrace();
      }
    }
  };

  /**
   * The single-threaded executor service that manages the processing of the actions.
   */
  private final ExecutorService worker = Executors.newSingleThreadExecutor();

  /**
   * Report a SQLError outcome related to a transaction
   *
   * @param generation
   * @param transactionGeneration
   * @param actionIdx
   * @param code
   * @param message
   */
  private void errorResult(String generation, int transactionGeneration, int code,
      String message, DbShimCallback callback) {
    Map<String, Object> outcome = new HashMap<String, Object>();
    outcome.put("error", message);
    outcome.put("errorCode", code);
    String quotedResultString = null;
    try {
      String resultString = ODKFileUtils.mapper.writeValueAsString(outcome);
      quotedResultString = ODKFileUtils.mapper.writeValueAsString(resultString);
    } catch (Exception e) {
      quotedResultString = "\'{\"error\":\"Internal Error\",\"errorCode\":\"0\"}\'";
    }
    final String fullCommand = "javascript:window.dbif.dbshimTransactionCallback(\"" + generation + "\","
        + transactionGeneration + "," + quotedResultString + ");";

    callback.fireCallback(fullCommand);
  }

  /**
   * Report a SQLError outcome related to a sqlStatement
   *
   * @param generation
   * @param transactionGeneration
   * @param actionIdx
   * @param code
   * @param message
   */
  private void errorResult(String generation, int transactionGeneration, int actionIdx, int code,
      String message, DbShimCallback callback) {
    Map<String, Object> outcome = new HashMap<String, Object>();
    outcome.put("error", message);
    outcome.put("errorCode", code);
    String quotedResultString = null;
    try {
      String resultString = ODKFileUtils.mapper.writeValueAsString(outcome);
      quotedResultString = ODKFileUtils.mapper.writeValueAsString(resultString);
    } catch (Exception e) {
      quotedResultString = "\'{\"error\":\"Internal Error\",\"errorCode\":\"0\"}\'";
    }
    final String fullCommand = "javascript:window.dbif.dbshimCallback(\"" + generation + "\","
        + transactionGeneration + "," + actionIdx + "," + quotedResultString + ");";

    callback.fireCallback(fullCommand);
  }

  /**
   * Assert that the database connections for the appName are
   * for the specified generation. If not, flush them and make
   * it so.
   *
   * @param appName
   * @param thisGeneration
   * @param contextName
   * @return
   */
  private DbShimAppContext assertGeneration(String appName, String thisGeneration, String contextName) {
    WebLogger log = WebLogger.getLogger(appName);

    DbShimAppContext appContext = appContexts.get(appName);
    if ( appContext != null ) {
      if (!appContext.generation.equals(thisGeneration)) {
        String oldGeneration = appContext.generation;
        Map<Integer, SQLiteDatabase> thisTransactions = new HashMap<Integer, SQLiteDatabase>();
        thisTransactions.putAll(appContext.transactions);
        appContext.transactions.clear();
        for (SQLiteDatabase db : thisTransactions.values()) {
          try {
            log.e(t, contextName + " -- Wrong Generation! gen: " + thisGeneration + " purging transaction!");
            db.endTransaction();
            db.close();
          } catch (Exception e) {
            log.e(t, contextName + " -- exception: " + e.toString());
          }
        }
        appContext.generation = thisGeneration;
        log.i(t, contextName + " -- updating DbShimAppContext gen: " + thisGeneration + " prior gen: " + oldGeneration);
      }
    } else {
      appContext = new DbShimAppContext();
      appContext.appName = appName;
      appContext.generation = thisGeneration;
      appContexts.put(appName, appContext);
      log.i(t, contextName + " -- creating DbShimAppContext gen: " + thisGeneration);
    }

    return appContext;
  }

  /**
   * Called to clear any database connections for appName that do not match
   * the generation.
   *
   * @param appName
   * @param generation
   * @param callback
   */
  private void initializeDatabaseConnections(String appName, String generation, DbShimCallback callback) {
    WebLogger log = WebLogger.getLogger(appName);
    log.i(t, "initializeDatabaseConnections -- gen: " + generation);

    String oldGeneration = "-";
    DbShimAppContext appContext = appContexts.get(appName);
    if ( appContext != null ) {
      oldGeneration = appContext.generation;
      if ( oldGeneration.equals(generation) ) {
        oldGeneration = "-";
      }
    }

    appContext = assertGeneration( appName, generation, "initializeDatabaseConnections");

    String fullCommand = "javascript:window.dbif.dbshimCleanupCallback(\"" + oldGeneration + "\");";
    callback.fireCallback(fullCommand);
  }

  /**
   * Rolls back the indicated transaction.
   *
   * @param appName
   * @param thisGeneration
   * @param thisTransactionGeneration
   * @param callback
   */
  private void runRollback(String appName, String thisGeneration, int thisTransactionGeneration, DbShimCallback callback) {
    WebLogger log = WebLogger.getLogger(appName);

    DbShimAppContext appContext = assertGeneration( appName, thisGeneration, "runRollback");

    SQLiteDatabase db = appContext.transactions.get(thisTransactionGeneration);
    if (db != null) {
      log.i(t, "rollback gen: " + thisGeneration + " transaction: " + thisTransactionGeneration);
      try {
        db.endTransaction();
        db.close();
        appContext.transactions.remove(thisTransactionGeneration);
      } catch (Exception e) {
        log.e(t, "rollback gen: " + thisGeneration + " transaction: " + thisTransactionGeneration
            + " - exception: " + e.toString());
        errorResult(thisGeneration, thisTransactionGeneration, 0, "rollback - exception: " + e.toString(), callback);
        return;
      }
    } else {
      log.w(t, "rollback -- Transaction Not Found! gen: " + thisGeneration + " transaction: "
          + thisTransactionGeneration);
    }

    String fullCommand = "javascript:window.dbif.dbshimTransactionCallback(\"" + thisGeneration + "\","
        + thisTransactionGeneration + ", '{}');";

    callback.fireCallback(fullCommand);
  }

  /**
   * Commits the indicated transaction.
   *
   * @param appName
   * @param thisGeneration
   * @param thisTransactionGeneration
   * @param callback
   */
  private void runCommit(String appName, String thisGeneration, int thisTransactionGeneration, DbShimCallback callback) {
    WebLogger log = WebLogger.getLogger(appName);

    DbShimAppContext appContext = assertGeneration( appName, thisGeneration, "runCommit");

    SQLiteDatabase db = appContext.transactions.get(thisTransactionGeneration);
    if (db != null) {
      log.i(t, "commit gen: " + thisGeneration + " transaction: " + thisTransactionGeneration);
      try {
        db.setTransactionSuccessful();
        db.endTransaction();
        db.close();
        appContext.transactions.remove(thisTransactionGeneration);
      } catch (Exception e) {
        log.e(t, "commit gen: " + thisGeneration + " transaction: " + thisTransactionGeneration
            + " - exception: " + e.toString());
        errorResult(thisGeneration, thisTransactionGeneration, 0, "commit - exception: " + e.toString(), callback);
        return;
      }
    } else {
      log.w(t, "commit -- Transaction Not Found! gen: " + thisGeneration + " transaction: "
          + thisTransactionGeneration);
    }

    String fullCommand = "javascript:window.dbif.dbshimTransactionCallback(\"" + thisGeneration + "\","
        + thisTransactionGeneration + ", '{}');";

    callback.fireCallback(fullCommand);
  }

  /**
   * Execute an arbitrary SQL statement.
   *
   * This either calls back with a SQLError (via errorResult(...)) or
   * calls back with a SQLResultSet.
   *
   * NOTE: the SQLResultSet does not properly record insertId or rowsAffected.
   */
  @SuppressLint("NewApi")
  private void runStmt(String appName, String thisGeneration, int thisTransactionGeneration,
      int thisActionIdx, String sqlStmt, String strBinds, DbShimCallback callback) {
    WebLogger log = WebLogger.getLogger(appName);

    sqlStmt = sqlStmt.trim();
    // doesn't matter...
    String sqlVerb = sqlStmt.substring(0, sqlStmt.indexOf(' ')).toUpperCase(Locale.US);

    DbShimAppContext appContext = assertGeneration( appName, thisGeneration, "runStmt");

    log.i(t, "executeSqlStmt -- gen: " + thisGeneration + " transaction: "
        + thisTransactionGeneration + " action: " + thisActionIdx + " sqlVerb: " + sqlVerb);

    String[] bindArray = null;
    try {
      if (strBinds != null) {
        ArrayList<Object> binds = new ArrayList<Object>();
        binds = ODKFileUtils.mapper.readValue(strBinds, binds.getClass());
        bindArray = new String[binds.size()];
        // convert the bindings to string values for SQLiteDatabase interface
        for (int i = 0 ; i < binds.size() ; ++i ) {
          Object o = binds.get(i);
          if ( o == null ) {
            bindArray[i] = null;
          } else {
            bindArray[i] = o.toString();
          }
        }
      }
    } catch (Exception e) {
      e.printStackTrace();
      log.e(t, "executeSqlStmt - exception parsing binds: " + e.toString());
      errorResult(thisGeneration, thisTransactionGeneration, thisActionIdx, 0,
          "exception parsing binds!", callback);
      return;
    }

    SQLiteDatabase db = appContext.transactions.get(thisTransactionGeneration);
    if (thisActionIdx == 0) {
      if (db != null) {
        log.e(t, "executeSqlStmt - unexpectedly matching transactionGen with actionIdx of zero!");
        errorResult(thisGeneration, thisTransactionGeneration, thisActionIdx, 0,
            "unexpectedly matching transactionGen with actionIdx of zero!", callback);
        return;
      }

      db = DataModelDatabaseHelperFactory.getDatabase(DbShimService.this.getApplicationContext(), appName);
      appContext.transactions.put(thisTransactionGeneration, db);
      db.beginTransaction();
    } else {
      if (db == null) {
        log.e(t,
            "executeSqlStmt - could not find matching transactionGen with non-zero actionIdx!");
        errorResult(thisGeneration, thisTransactionGeneration, thisActionIdx, 0,
            "could not find matching transactionGen with non-zero actionIdx!", callback);
        return;
      }
    }

    if (sqlVerb.equals("SELECT")) {
      try {
        Cursor c = db.rawQuery(sqlStmt, bindArray);
        Map<String, Object> resultSet = new HashMap<String, Object>();
        ArrayList<Map<String, Object>> rowSet = new ArrayList<Map<String, Object>>();
        resultSet.put("rowsAffected", 0);
        resultSet.put("rows", rowSet);

        while (c.moveToNext()) {
          Map<String, Object> row = new HashMap<String, Object>();
          int nCols = c.getColumnCount();
          for (int i = 0; i < nCols; ++i) {
            String name = c.getColumnName(i);

            Object v = ODKDatabaseUtils.get().getIndexAsType(c, ODKDatabaseUtils.get().getIndexDataType(c, i), i);
            row.put(name, v);
          }
          rowSet.add(row);
        }

        String resultString = ODKFileUtils.mapper.writeValueAsString(resultSet);
        String quotedResultString = ODKFileUtils.mapper.writeValueAsString(resultString);
        StringBuilder b = new StringBuilder();
        b.append("javascript:window.dbif.dbshimCallback(\"").append(thisGeneration).append("\",")
         .append(thisTransactionGeneration).append(",").append(thisActionIdx).append(",")
         .append(quotedResultString).append(");");
        String fullCommand = b.toString();
        log.i(t, "executeSqlStmt return sqlVerb: " + sqlVerb);
        callback.fireCallback(fullCommand);
        return;
      } catch (Exception e) {
        log.e(t, "executeSqlStmt - exception: " + e.toString());
        errorResult(thisGeneration, thisTransactionGeneration, thisActionIdx, 0, "exception: "
            + e.toString(), callback);
        return;
      }
    } else {
      try {
        db.execSQL(sqlStmt, bindArray);
        Map<String, Object> resultSet = new HashMap<String, Object>();
        String resultString = ODKFileUtils.mapper.writeValueAsString(resultSet);
        String quotedResultString = ODKFileUtils.mapper.writeValueAsString(resultString);
        StringBuilder b = new StringBuilder();
        b.append("javascript:window.dbif.dbshimCallback(\"").append(thisGeneration).append("\",")
         .append(thisTransactionGeneration).append(",").append(thisActionIdx).append(",")
         .append(quotedResultString).append(");");
        String fullCommand = b.toString();
        log.i(t, "executeSqlStmt return sqlVerb: " + sqlVerb);
        callback.fireCallback(fullCommand);
      } catch (Exception e) {
        log.e(t, "executeSqlStmt - exception: " + e.toString());
        errorResult(thisGeneration, thisTransactionGeneration, thisActionIdx, 0, "exception: "
            + e.toString(), callback);
        return;
      }
    }
  }

  private synchronized void processAction(DbAction actionDefn ) {
    switch ( actionDefn.theAction ) {
    case INITIALIZE:
      initializeDatabaseConnections(actionDefn.appName, actionDefn.generation, actionDefn.callback);
      break;
    case ROLLBACK:
      runRollback(actionDefn.appName, actionDefn.generation, actionDefn.transactionGeneration, actionDefn.callback);
      break;
    case COMMIT:
      runCommit(actionDefn.appName, actionDefn.generation, actionDefn.transactionGeneration, actionDefn.callback);
      break;
    case STMT:
      runStmt(actionDefn.appName, actionDefn.generation, actionDefn.transactionGeneration,
              actionDefn.actionIdx, actionDefn.sqlStmt, actionDefn.strBinds, actionDefn.callback);
      break;
    }
  }

  public class DbShimBinder extends Binder {
    public void initializeDatabaseConnections(String appName, String generation, DbShimCallback callback) {
      DbAction action = new DbAction();
      action.appName = appName;
      action.generation = generation;
      action.callback = callback;
      action.theAction = Action.INITIALIZE;
      // throws exception if queue length would be exceeded
      actions.add(action);
      worker.execute(workUnit);
    }
    public void runRollback(String appName, String generation, int transactionGeneration, DbShimCallback callback) {
      DbAction action = new DbAction();
      action.appName = appName;
      action.generation = generation;
      action.transactionGeneration = transactionGeneration;
      action.callback = callback;
      action.theAction = Action.ROLLBACK;
      // throws exception if queue length would be exceeded
      actions.add(action);
      worker.execute(workUnit);
    }

    public void runCommit(String appName, String generation, int transactionGeneration, DbShimCallback callback) {
      DbAction action = new DbAction();
      action.appName = appName;
      action.generation = generation;
      action.transactionGeneration = transactionGeneration;
      action.callback = callback;
      action.theAction = Action.COMMIT;
      // throws exception if queue length would be exceeded
      actions.add(action);
      worker.execute(workUnit);
    }

    public void runStmt(String appName, String generation, int transactionGeneration,
        int actionIdx, String sqlStmt, String strBinds, DbShimCallback callback) {
      DbAction action = new DbAction();
      action.appName = appName;
      action.generation = generation;
      action.transactionGeneration = transactionGeneration;
      action.actionIdx = actionIdx;
      action.sqlStmt = sqlStmt;
      action.strBinds = strBinds;
      action.callback = callback;
      action.theAction = Action.STMT;
      // throws exception if queue length would be exceeded
      actions.add(action);
      worker.execute(workUnit);
    }

  };

  private final IBinder binder = new DbShimBinder();

  @Override
  public IBinder onBind(Intent intent) {
    return binder;
  }

  @Override
  public void onDestroy() {
    // clear out the work items
    actions.clear();
    // drain the active work queue
    worker.shutdown();
    try {
      worker.awaitTermination(2000L, TimeUnit.MILLISECONDS);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
  }
}
