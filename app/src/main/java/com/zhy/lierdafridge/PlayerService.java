package com.zhy.lierdafridge;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

import com.zhy.lierdafridge.bean.PlayEvent;
import com.zhy.lierdafridge.player.MusicPlayer;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;



/**
 * Created by Administrator on 2017/12/22 0022.
 */

public class PlayerService extends Service {

    @Override
    public IBinder onBind(Intent intent) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public void onCreate() {
        super.onCreate();
        EventBus.getDefault().register(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return super.onStartCommand(intent, flags, startId);
    }

    //接收EventBus post过来的PlayEvent
    @Subscribe
    public void onEvent(PlayEvent playEvent) {
        switch (playEvent.getAction()) {
            case PLAY:
                MusicPlayer.getPlayer().setQueue(playEvent.getQueue(), 0);
                break;
            case PAUSE:
                MusicPlayer.getPlayer().pause();
                break;
            case STOP:
                MusicPlayer.getPlayer().stop();
                MusicPlayer.getPlayer().release();
                break;
            case RESUME:
                MusicPlayer.getPlayer().resume();
                break;
        }
    }
}
