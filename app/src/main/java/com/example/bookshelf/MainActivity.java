package com.example.bookshelf;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.FragmentManager;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.Toast;

import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;

import edu.temple.audiobookplayer.AudiobookService;

public class MainActivity extends AppCompatActivity implements BookListFragment.BookSelectedInterface {

    private static final String BOOKS_KEY = "books";
    private static final String SELECTED_BOOK_KEY = "selectedBook";
    FragmentManager fm;
    boolean twoPane;
    BookListFragment bookListFragment;
    BookDetailsFragment bookDetailsFragment;
    ArrayList<Book> books;
    RequestQueue requestQueue;
    Book selectedBook;
    EditText searchEditText;
    Button playButton, pauseButton, stopButton;
    private AudiobookService player = new AudiobookService();
    AudiobookService.MediaControlBinder binder = null;
    boolean serviceBound = false;
    SeekBar seekbar;
    Handler handler;

    private final String SEARCH_API = "https://kamorris.com/lab/abp/booksearch.php?search=";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        searchEditText = findViewById(R.id.searchEditText);
        AudiobookService.MediaControlBinder.getCallingUid();
        Context context=getApplication();
        Intent intent = new Intent(this,AudiobookService.class);

        bindService(intent,serviceConnection,Context.BIND_AUTO_CREATE);
        handler = new Handler();
        seekbar = (SeekBar) findViewById(R.id.seekBar);
        seekbar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if(fromUser){
                    binder.seekTo(progress);
                }
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });
        playButton= findViewById(R.id.playbutton);
        pauseButton= findViewById(R.id.pausebutton);
        stopButton= findViewById(R.id.stopbutton);
        playButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onPlaySelected();
            }
        });
        pauseButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onPauseSelected();
            }
        });
        stopButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onStopSelected();
            }
        });
        findViewById(R.id.searchButton).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                fetchBooks(searchEditText.getText().toString());
            }
        });
        if (savedInstanceState != null) {
            books = savedInstanceState.getParcelableArrayList(BOOKS_KEY);
            selectedBook = savedInstanceState.getParcelable(SELECTED_BOOK_KEY);
        }
        else
            books = new ArrayList<Book>();
            twoPane = findViewById(R.id.container2) != null;
            fm = getSupportFragmentManager();
            requestQueue = Volley.newRequestQueue(this);
            bookListFragment = BookListFragment.newInstance(books);
            fm.beginTransaction()
                .replace(R.id.container1, bookListFragment)
            .commit();
        if (twoPane) {
            if (selectedBook != null) {
                bookDetailsFragment = BookDetailsFragment.newInstance(selectedBook);
            }else
                bookDetailsFragment = new BookDetailsFragment();
                fm.beginTransaction()
                    .replace(R.id.container2, bookDetailsFragment)
                    .commit();
        } else {
            if (selectedBook != null) {
                fm.beginTransaction()
                        .replace(R.id.container1, BookDetailsFragment.newInstance(selectedBook))
                        // Transaction is reversible
                        .addToBackStack(null)
                        .commit();
            }
        }
    }

    private void fetchBooks(String searchString) {
        JsonArrayRequest jsonArrayRequest = new JsonArrayRequest(SEARCH_API + searchString, new Response.Listener<JSONArray>() {
            @Override
            public void onResponse(JSONArray response) {
                if (response.length() > 0) {
                    books.clear();
                    for (int i = 0; i < response.length(); i++) {
                        try {
                            JSONObject bookJSON;
                            bookJSON = response.getJSONObject(i);
                            books.add(new Book (bookJSON.getInt(Book.JSON_ID),
                                    bookJSON.getString(Book.JSON_TITLE),
                                    bookJSON.getString(Book.JSON_AUTHOR),
                                    bookJSON.getString(Book.JSON_COVER_URL)));
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }
                    updateBooksDisplay();
                } else {
                    Toast.makeText(MainActivity.this, getString(R.string.search_error_message), Toast.LENGTH_SHORT).show();
                }
            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {

            }
        });
        requestQueue.add(jsonArrayRequest);
    }
    private void updateBooksDisplay() {
        if (fm.findFragmentById(R.id.container1) instanceof BookDetailsFragment)
            fm.popBackStack();
        bookListFragment.updateBooksDisplay(books);
    }
    @Override
    public void bookSelected(int index) {
        selectedBook = books.get(index);
        if (twoPane) {
            bookDetailsFragment.displayBook(selectedBook);
        }else {
            fm.beginTransaction()
                    .replace(R.id.container1, BookDetailsFragment.newInstance(selectedBook))
                    .addToBackStack(null)
                    .commit();
        }
    }
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelableArrayList(BOOKS_KEY, books);
        outState.putParcelable(SELECTED_BOOK_KEY, selectedBook);
    }
    private ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            AudiobookService.MediaControlBinder binder = (AudiobookService.MediaControlBinder) service;
            serviceBound = true;
        }
        @Override
        public void onServiceDisconnected(ComponentName name) {
            serviceBound = false;
        }
    };

    @Override
    protected void onResume(){
        super.onResume();
        player.onBind(null);
    }
    @Override
    protected void onDestroy(){
        super.onDestroy();
        player.onDestroy();
    }

    private void onPlaySelected(){
        binder.play(0 );

    }
    private void onPauseSelected(){
        binder.pause();
    }
    private void onStopSelected(){
        binder.stop();
        serviceBound = false;
        player.onDestroy();
    }

}
