
package com.battlelancer.seriesguide.appwidget;

import com.battlelancer.seriesguide.R;
import com.battlelancer.seriesguide.ui.UpcomingRecentActivity;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.RemoteViews;

public class ListWidgetProvider extends AppWidgetProvider {
    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        // update each of the widgets with the remote adapter
        for (int i = 0; i < appWidgetIds.length; ++i) {

            // Here we setup the intent which points to the StackViewService
            // which will provide the views for this collection.
            Intent intent = new Intent(context, ListWidgetService.class);
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetIds[i]);
            // When intents are compared, the extras are ignored, so we need to
            // embed the extras into the data so that the extras will not be
            // ignored.
            intent.setData(Uri.parse(intent.toUri(Intent.URI_INTENT_SCHEME)));
            RemoteViews rv = new RemoteViews(context.getPackageName(), R.layout.appwidget);
            rv.setRemoteAdapter(appWidgetIds[i], R.id.list_view, intent);

            // The empty view is displayed when the collection has no items. It
            // should be a sibling of the collection view.
            rv.setEmptyView(R.id.list_view, R.id.empty_view);

            // Create an Intent to launch Upcoming
            Intent pi = new Intent(context, UpcomingRecentActivity.class);
            pi.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, pi, 0);
            rv.setOnClickPendingIntent(R.id.widget_title, pendingIntent);

            appWidgetManager.updateAppWidget(appWidgetIds[i], rv);
        }
        super.onUpdate(context, appWidgetManager, appWidgetIds);
    }
}
