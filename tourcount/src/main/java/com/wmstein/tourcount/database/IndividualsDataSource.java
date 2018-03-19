package com.wmstein.tourcount.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;

import java.util.ArrayList;
import java.util.List;

/*************************************************
 * Created by wmstein for TourCount on 20.04.2016.
 */
public class IndividualsDataSource
{
    // Database fields
    private SQLiteDatabase database;
    private final DbHelper dbHandler;
    private final String[] allColumns = {
        DbHelper.I_ID,
        DbHelper.I_COUNT_ID,   // ID of table count
        DbHelper.I_NAME,       // species name
        DbHelper.I_COORD_X,    // latitude
        DbHelper.I_COORD_Y,    // longitude
        DbHelper.I_COORD_Z,    // height
        DbHelper.I_UNCERT,     // uncertainty
        DbHelper.I_DATE_STAMP, // date
        DbHelper.I_TIME_STAMP, // time
        DbHelper.I_LOCALITY,   // locality
        DbHelper.I_SEX,        // sexus
        DbHelper.I_STADIUM,    // stadium
        DbHelper.I_STATE_1_6,  // state
        DbHelper.I_NOTES,      // notes
        DbHelper.I_ICOUNT      // individual count
    };

    public IndividualsDataSource(Context context)
    {
        dbHandler = new DbHelper(context);
    }

    public void open() throws SQLException
    {
        database = dbHandler.getWritableDatabase();
    }

    public void close()
    {
        dbHandler.close();
    }

    public Individuals createIndividuals(int count_id, String name, double latitude, double longitude, double height, String uncert, String datestamp, String timestamp)
    {
        ContentValues values = new ContentValues();
        values.put(DbHelper.I_COUNT_ID, count_id);
        values.put(DbHelper.I_NAME, name);
        values.put(DbHelper.I_COORD_X, latitude);
        values.put(DbHelper.I_COORD_Y, longitude);
        values.put(DbHelper.I_COORD_Z, height);
        values.put(DbHelper.I_UNCERT, uncert);
        values.put(DbHelper.I_DATE_STAMP, datestamp);
        values.put(DbHelper.I_TIME_STAMP, timestamp);
        values.put(DbHelper.I_LOCALITY, "");
        values.put(DbHelper.I_SEX, "");
        values.put(DbHelper.I_STADIUM, "");
        values.put(DbHelper.I_STATE_1_6, 0);
        values.put(DbHelper.I_NOTES, "");
        values.put(DbHelper.I_ICOUNT, 0);

        int insertId = (int) database.insert(DbHelper.INDIVIDUALS_TABLE, null, values);
        Cursor cursor = database.query(DbHelper.INDIVIDUALS_TABLE,
            allColumns, DbHelper.I_ID + " = " + insertId, null, null, null, null);
        cursor.moveToFirst();
        Individuals newIndividuals = cursorToIndividuals(cursor);
        cursor.close();
        return newIndividuals;
    }

    public void deleteIndividualById(int id)
    {
        database.delete(DbHelper.INDIVIDUALS_TABLE, DbHelper.I_ID + " = " + id, null);
    }

    public void decreaseIndividual(int id, int newicount)
    {
        ContentValues dataToInsert = new ContentValues();
        dataToInsert.put(DbHelper.I_ICOUNT, newicount);
        String where = DbHelper.I_ID + " = ?";
        String[] whereArgs = {String.valueOf(id)};
        database.update(DbHelper.INDIVIDUALS_TABLE, dataToInsert, where, whereArgs);
    }
    
    private Individuals cursorToIndividuals(Cursor cursor)
    {
        Individuals newindividuals = new Individuals();
        newindividuals.id = cursor.getInt(cursor.getColumnIndex(DbHelper.I_ID));
        newindividuals.count_id = cursor.getInt(cursor.getColumnIndex(DbHelper.I_COUNT_ID));
        newindividuals.name = cursor.getString(cursor.getColumnIndex(DbHelper.I_NAME));
        newindividuals.coord_x = cursor.getDouble(cursor.getColumnIndex(DbHelper.I_COORD_X));
        newindividuals.coord_y = cursor.getDouble(cursor.getColumnIndex(DbHelper.I_COORD_Y));
        newindividuals.coord_z = cursor.getDouble(cursor.getColumnIndex(DbHelper.I_COORD_Z));
        newindividuals.uncert = cursor.getString(cursor.getColumnIndex(DbHelper.I_UNCERT));
        newindividuals.date_stamp = cursor.getString(cursor.getColumnIndex(DbHelper.I_DATE_STAMP));
        newindividuals.time_stamp = cursor.getString(cursor.getColumnIndex(DbHelper.I_TIME_STAMP));
        newindividuals.locality = cursor.getString(cursor.getColumnIndex(DbHelper.I_LOCALITY));
        newindividuals.sex = cursor.getString(cursor.getColumnIndex(DbHelper.I_SEX));
        newindividuals.stadium = cursor.getString(cursor.getColumnIndex(DbHelper.I_STADIUM));
        newindividuals.state_1_6 = cursor.getInt(cursor.getColumnIndex(DbHelper.I_STATE_1_6));
        newindividuals.notes = cursor.getString(cursor.getColumnIndex(DbHelper.I_NOTES));
        newindividuals.icount = cursor.getInt(cursor.getColumnIndex(DbHelper.I_ICOUNT));
        return newindividuals;
    }

    public int saveIndividual(Individuals individuals)
    {
        ContentValues dataToInsert = new ContentValues();
        dataToInsert.put(DbHelper.I_COUNT_ID, individuals.count_id);
        dataToInsert.put(DbHelper.I_NAME, individuals.name);
        dataToInsert.put(DbHelper.I_COORD_X, individuals.coord_x);
        dataToInsert.put(DbHelper.I_COORD_Y, individuals.coord_y);
        dataToInsert.put(DbHelper.I_COORD_Z, individuals.coord_z);
        dataToInsert.put(DbHelper.I_UNCERT, individuals.uncert);
        dataToInsert.put(DbHelper.I_DATE_STAMP, individuals.date_stamp);
        dataToInsert.put(DbHelper.I_TIME_STAMP, individuals.time_stamp);
        dataToInsert.put(DbHelper.I_LOCALITY, individuals.locality);
        dataToInsert.put(DbHelper.I_SEX, individuals.sex);
        dataToInsert.put(DbHelper.I_STADIUM, individuals.stadium);
        dataToInsert.put(DbHelper.I_STATE_1_6, individuals.state_1_6);
        dataToInsert.put(DbHelper.I_NOTES, individuals.notes);
        dataToInsert.put(DbHelper.I_ICOUNT, individuals.icount);
        String where = DbHelper.I_ID + " = ?";
        String[] whereArgs = {String.valueOf(individuals.id)};
        database.update(DbHelper.INDIVIDUALS_TABLE, dataToInsert, where, whereArgs);
        return individuals.id;
    }

    public int readLastIndividual(int c_Id)
    {
        Individuals individuals;
        Cursor cursor = database.query(DbHelper.INDIVIDUALS_TABLE, allColumns, DbHelper.I_COUNT_ID + " = ?", new String[]{String.valueOf(c_Id)}, null, null, null);
        cursor.moveToLast();
        // check for entries in individuals table, which are not there when bulk counts are entered
        if (!cursor.isAfterLast())
        {
            individuals = cursorToIndividuals(cursor);
            cursor.close();
            return individuals.id;
        }
        else
        {
            cursor.close();
            return -1;
        }
    }

    public Individuals getIndividual(int indiv_id)
    {
        Individuals individuals;
        Cursor cursor = database.query(DbHelper.INDIVIDUALS_TABLE, allColumns, DbHelper.I_ID 
            + " = ?", new String[]{String.valueOf(indiv_id)}, null, null, null);
        cursor.moveToFirst();
        individuals = cursorToIndividuals(cursor);
        cursor.close();
        return individuals;
    }

    public int getIndividualCount(int indiv_id)
    {
        Individuals individuals;
        Cursor cursor = database.query(DbHelper.INDIVIDUALS_TABLE, allColumns, DbHelper.I_ID
            + " = ?", new String[]{String.valueOf(indiv_id)}, null, null, null);
        cursor.moveToFirst();
        individuals = cursorToIndividuals(cursor);
        cursor.close();
        return individuals.icount;
    }

    // Used by ListSpeciesActivity
    public List<Individuals> getIndividualsByName(String iname)
    {
        List<Individuals> indivs = new ArrayList<>();

        String slct = "select * from " + DbHelper.INDIVIDUALS_TABLE + " WHERE " + DbHelper.I_NAME + " = ?";
        Cursor cursor = database.rawQuery(slct, new String[] {iname});

        cursor.moveToFirst();
        while (!cursor.isAfterLast())
        {
            Individuals individuals = cursorToIndividuals(cursor);
            indivs.add(individuals);
            cursor.moveToNext();
        }
        cursor.close();
        return indivs;
    }

    public List<Individuals> getIndividuals()
    {
        List<Individuals> individs = new ArrayList<>();

        String slct = "select * from " + DbHelper.INDIVIDUALS_TABLE + " WHERE " + DbHelper.I_COORD_X + " != 0";
        Cursor cursor = database.rawQuery(slct, null);

        cursor.moveToFirst();
        while (!cursor.isAfterLast())
        {
            Individuals individuals = cursorToIndividuals(cursor);
            individs.add(individuals);
            cursor.moveToNext();
        }
        cursor.close();
        return individs;
    }

}

