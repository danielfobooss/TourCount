package com.wmstein.tourcount.widgets;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.RelativeLayout;

import com.wmstein.tourcount.R;

/**********************************
 * Created by wmstein on 2018-02-24
 * last edited on 2019-01-27
 */
public class ListLineWidget extends RelativeLayout
{
    public ListLineWidget(Context context, AttributeSet attrs)
    {
        super(context, attrs);

        LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        assert inflater != null;
        inflater.inflate(R.layout.widget_list_line, this, true);
    }

}
