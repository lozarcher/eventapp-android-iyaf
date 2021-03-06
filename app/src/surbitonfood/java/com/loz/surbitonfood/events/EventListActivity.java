package com.loz.surbitonfood.events;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.annotation.UiThread;
import android.support.design.widget.TabLayout;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import com.crashlytics.android.Crashlytics;
import com.crashlytics.android.answers.Answers;
import com.crashlytics.android.answers.ContentViewEvent;
import com.loz.R;
import com.loz.iyaf.events.EventActivity;
import com.loz.iyaf.events.EventNotification;
import com.loz.iyaf.feed.CategoryData;
import com.loz.iyaf.feed.CategoryType;
import com.loz.iyaf.feed.EventData;
import com.loz.iyaf.feed.EventList;
import com.loz.iyaf.feed.EventappService;
import com.loz.iyaf.imagehelpers.JsonCache;

import java.io.ObjectInput;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeMap;

import retrofit.Call;
import retrofit.Callback;
import retrofit.JacksonConverterFactory;
import retrofit.Response;
import retrofit.Retrofit;


public class EventListActivity extends AppCompatActivity  {

    private ArrayList<EventData> eventRows = new ArrayList<>();
    private EventList eventList;
    private Set<String> favourites;
    private static final int SHOW_EVENT_DETAIL = 0;
    private CategoryData selectedCategory;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_eventlist);

        ListView listView = findViewById(R.id.listView);
        TextView emptyList = findViewById(R.id.emptyList);

        listView.setEmptyView(emptyList);

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("https://eventapp.lozarcher.co.uk")
                .addConverterFactory(JacksonConverterFactory.create())
                .build();

        TabLayout tabLayout = findViewById(R.id.tabLayout);
        tabLayout.setTabMode(TabLayout.MODE_SCROLLABLE);

        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                selectedCategory = (CategoryData)tab.getTag();

                switch (selectedCategory.getCategoryType()) {
                    case FAVOURITES:
                        emptyList.setText(R.string.emptyFavouritesList);
                        break;
                    default:
                        emptyList.setText(R.string.emptyEventsList);
                }
                processEventList(eventList);
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {

            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {

            }
        });


        EventappService eventappService = retrofit.create(EventappService.class);
        Call<EventList> call = eventappService.getEvents();
        Log.d("LOZ", "Starting call to /v4//events... Hope it works...");
        spinner(true);
        call.enqueue(new Callback<EventList>() {
            @Override
            public void onResponse(Response<EventList> response, Retrofit retrofit) {
                Log.d("LOZ", "Got response: "+response.body().toString());
                eventList = response.body();
                readFavourites();
                JsonCache.writeToCache(getApplicationContext(), eventList, "eventsv4");
                spinner(false);
                processCategories();
                processEventList(eventList);
            }

            @Override
            public void onFailure(Throwable t) {
                // something went completely south (like no internet connection)
                Log.d("Error", t.getMessage());
                Crashlytics.logException(t);
                spinner(false);
                ObjectInput oi = JsonCache.readFromCache(getApplicationContext(), "eventsv4");
                if (oi != null) {
                    try {
                        eventList = (EventList) oi.readObject();
                        readFavourites();
                    } catch (Exception e) {
                        e.printStackTrace();
                        Log.e("cache", e.getMessage());
                        Crashlytics.logException(e);
                    }
                    processCategories();
                    processEventList(eventList);
                }
            }
        });
        Answers.getInstance().logContentView(new ContentViewEvent()
                .putContentName("Event List")
                .putContentType("Events")
                .putContentId("events"));
    }

    @UiThread
    void spinner(boolean show){
        findViewById(R.id.listView).setVisibility(show ? View.INVISIBLE : View.VISIBLE);
        findViewById(R.id.emptyList).setVisibility(show ? View.INVISIBLE : View.VISIBLE);
        findViewById(R.id.spinner).setVisibility(show ? View.VISIBLE : View.INVISIBLE);
    }


    private void processEventList(EventList eventList) {
        if (eventList != null) {
            ArrayList<String> eventNames = new ArrayList<>();
            ArrayList<EventData> events = new ArrayList<>();
            for (EventData event : eventList.getEvents()) {
                events.add(event);
                eventNames.add(event.getName());
                Log.d("LOZ", "Got event " + event.getName());
            }
            ListView listView = findViewById(R.id.listView);

            EventListAdapter adapter = new EventListAdapter(EventListActivity.this);
            listView.setAdapter(adapter);
            listView.setOnItemClickListener(mEventClickedHandler);

            TreeMap<Date, ArrayList<EventData>> map = getEventsByDay(events);
            for (Date date : map.keySet()) {
                SimpleDateFormat sdf = new SimpleDateFormat("EEE d MMMM yyyy");
                eventRows.add(new EventData());
                adapter.addSectionHeaderItem(sdf.format(date));
                for (EventData event : map.get(date)) {
                    adapter.addItem(event);
                    eventRows.add(event);
                }
            }


        }

    }

    private void processCategories() {
        TabLayout tabLayout = findViewById(R.id.tabLayout);
        tabLayout.removeAllTabs();
        Iterable<CategoryData> categoryList = eventList.getCategories();
        for (CategoryData categoryData : categoryList) {
            TabLayout.Tab tab = tabLayout.newTab().setText(categoryData.getCategory());
            tab.setTag(categoryData);
            tabLayout.addTab(tab);

            if (categoryData.getCategoryType().equals(CategoryType.ALL)) {
                selectedCategory = categoryData;
            }
        }
    }

    private TreeMap<Date, ArrayList<EventData>> getEventsByDay(ArrayList<EventData> events) {
        TreeMap<Date, ArrayList<EventData>> map = new TreeMap<>();
        for (EventData event : events) {
            Calendar cal = GregorianCalendar.getInstance();
            cal.setTime(event.getStartTime());
            cal.set(Calendar.HOUR_OF_DAY, 0);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);
            Date dateAtMidnight = cal.getTime();

            ArrayList<EventData> eventsOnDay = map.get(dateAtMidnight);
            if (eventsOnDay == null) {
                eventsOnDay = new ArrayList<EventData>();
            }
            if (shouldShowEvent(event)) {
                eventsOnDay.add(event);
            }
            if (eventsOnDay.size() > 0) {
                map.put(dateAtMidnight, eventsOnDay);
            }
        }
        return map;
    }

    private AdapterView.OnItemClickListener mEventClickedHandler = new AdapterView.OnItemClickListener() {
        public void onItemClick(AdapterView parent, View v, int position, long id) {
            EventData event = (EventData)parent.getAdapter().getItem(position);
            Intent intent = new Intent(EventListActivity.this, EventActivity.class);
            Bundle b = new Bundle();
            if (event.getId() != null) {
                b.putSerializable("event", event);
                intent.putExtras(b);
                startActivityForResult(intent, SHOW_EVENT_DETAIL);
            }
        }
    };

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == SHOW_EVENT_DETAIL) {
            if (resultCode == RESULT_OK) {
                Bundle b = data.getExtras();
                EventData viewedEvent = (EventData) b.getSerializable("event");
                if (viewedEvent != null) {
                    boolean favouritesChanged = amendEventList(viewedEvent);
                    if (favouritesChanged)  {
                        if (viewedEvent.isFavourite()) {
                            Log.d("LOZ", "Added favourite "+viewedEvent.getName());
                            this.favourites.add(viewedEvent.getId().toString());
                            EventNotification.setNotification(viewedEvent, this);
                        } else {
                            this.favourites.remove(viewedEvent.getId().toString());
                            EventNotification.removeNotification(viewedEvent, this);
                        }
                        saveFavourites();
                        processEventList(eventList);
                    }
                }
            }
        }
    }

    private boolean amendEventList(EventData amendedEvent) {
        boolean favouritesChanged = false;
        for (EventData event : eventList.getEvents()) {
            if (event.getId().equals(amendedEvent.getId())) {
                if (amendedEvent.isFavourite() != event.isFavourite()) {
                    favouritesChanged = true;
                }
                event.setFavourite(amendedEvent.isFavourite());
            }
        }
        return favouritesChanged;
    }

    public void setFavouriteIcon(ImageView icon, EventData event) {
        if (event.isFavourite()) {
            icon.setImageResource(R.drawable.ic_favorite_set);
        } else {
            icon.setImageResource(R.drawable.ic_favorite_unset);
        }
    }

    protected void setFavourite(ImageView view, EventData event) {
        event.setFavourite(!event.isFavourite());
        setFavouriteIcon(view, event);
        if (event.isFavourite()) {
            this.favourites.add(event.getId().toString());
            EventNotification.setNotification(event, this);
        } else {
            this.favourites.remove(event.getId().toString());
            EventNotification.removeNotification(event, this);
        }
        saveFavourites();
        if (selectedCategory.equals(CategoryType.FAVOURITES)) {
            processEventList(eventList);
        }
    }

    private void readFavourites() {
        String preferencesKey = getString(R.string.favourites_pref_key);
        SharedPreferences sharedPref = getApplication().getSharedPreferences(preferencesKey,Context.MODE_PRIVATE);

        this.favourites = sharedPref.getStringSet(preferencesKey, null);
        if (favourites == null) {
            SharedPreferences.Editor editor = sharedPref.edit();
            this.favourites = new HashSet<String>();
            editor.putStringSet(preferencesKey, this.favourites);
            Log.d("LOZ", "Recreated favourites: "+preferencesKey);
            editor.commit();
        } else {
            for (EventData event : eventList.getEvents()) {
                event.setFavourite(this.favourites.contains(event.getId().toString()));
            }
            Log.d("LOZ", "Read favourites: "+this.favourites+ " with key "+preferencesKey);
        }
    }

    private void saveFavourites() {
        String preferencesKey = getString(R.string.favourites_pref_key);
        SharedPreferences sharedPref = getApplication().getSharedPreferences(preferencesKey,Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.clear();
        editor.putStringSet(preferencesKey, this.favourites);
        Log.d("LOZ", "Saved favourites: "+this.favourites+ " with key "+preferencesKey);
        editor.commit();
    }

    private boolean shouldShowEvent(EventData eventData) {
        switch (selectedCategory.getCategoryType()) {
            case FAVOURITES: return eventData.isFavourite();
            case FILTER: return ((ArrayList)eventData.getCategories()).contains(selectedCategory.getId());
            default: return true;

        }
    }

}
