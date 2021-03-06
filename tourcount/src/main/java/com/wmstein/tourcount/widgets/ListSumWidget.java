package com.wmstein.tourcount.widgets;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.wmstein.tourcount.R;

import java.util.Objects;

/****************************************************
 * ListSumWidget shows count totals area for
 * ListSpeciesActivity that shows the result page
 * Created for TourCount by wmstein on 2017-05-27,
 * last edited on 2020-04-17
 */
public class ListSumWidget extends LinearLayout
{
    private TextView sumSpecies;
    private TextView sumIndividuals;
    
    public ListSumWidget(Context context, AttributeSet attrs)
    {
        super(context, attrs);

        LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        Objects.requireNonNull(inflater).inflate(R.layout.widget_sum_species, this, true);
        sumSpecies = findViewById(R.id.sumSpecies);
        sumIndividuals = findViewById(R.id.sumIndividuals);
    }

    public void setSum(int sumsp, int sumind)
    {
        sumSpecies.setText(String.valueOf(sumsp));
        sumIndividuals.setText(String.valueOf(sumind));
    }
    
}
