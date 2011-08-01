/****************************************************************************************
 * Copyright (c) 2009 Edu Zamora <edu.zasu@gmail.com>                                   *
 * Copyright © 2011 Robert Siemer <Robert.Siemer-pankigit@backsla.sh>
 *                                                                                      *
 * This program is free software; you can redistribute it and/or modify it under        *
 * the terms of the GNU General Public License as published by the Free Software        *
 * Foundation; either version 3 of the License, or (at your option) any later           *
 * version.                                                                             *
 *                                                                                      *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY      *
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A      *
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.             *
 *                                                                                      *
 * You should have received a copy of the GNU General Public License along with         *
 * this program.  If not, see <http://www.gnu.org/licenses/>.                           *
 ****************************************************************************************/

package com.ichi2.anki;

import android.content.Context;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.net.Uri;
import android.util.Log;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

public class Sound {
    private MediaPlayer mMediaPlayer;
    private List<URI> mPlaylist;
    private final URI mBase;
    private final Context mContext;


    public Sound(Context context, URI base) {
        mContext = context;
        mBase = base;
        mMediaPlayer = new MediaPlayer();
        mMediaPlayer.release();
    }


    public void setPlaylist(List<URI> uris) {
        mPlaylist = uris;
    }


    public void appendPlaylist(List<URI> uris) {
        assert mPlaylist.addAll(uris);
    }


    public void play() {
        play(new ArrayList<URI>(mPlaylist));
    }


    public void stop() {
        play(Collections.EMPTY_LIST);
    }

    public void play(List<URI> uris) {
        Log.i("AnkiAudio: play", Thread.currentThread().getName());
        mMediaPlayer.release();
        mMediaPlayer = new MediaPlayer();
        final Iterator<URI> iterator = uris.iterator();
        OnCompletionListener listener = new OnCompletionListener() {
            public void onCompletion(MediaPlayer mp) {
                Log.i("AnkiAudio: onCompletion", Thread.currentThread().getName());
                while (iterator.hasNext()) {
                    try {
                        URI uri = iterator.next();
                        mp.reset();
                        mp.setDataSource(mContext, Uri.parse(mBase.resolve(uri).toString()));
                        mp.prepare();
                        mp.start();
                        return;
                    } catch (IOException e) { // which I get from setDataSource() and prepare() only
                    }
                }
                mp.release();
            }
        };
        mMediaPlayer.setOnCompletionListener(listener);

        // start playing by simulating a completion (which goes forward to the next in the list: the first)

        listener.onCompletion(mMediaPlayer);
    }
}
