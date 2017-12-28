package com.zhy.lierdafridge.bean;

import org.litepal.crud.DataSupport;

/**
 * 日程提醒
 */

public class RemindBean extends DataSupport{

    private long triggerAtMillis;
    private String msg;

    public long getTriggerAtMillis() {
        return triggerAtMillis;
    }

    public void setTriggerAtMillis(long triggerAtMillis) {
        this.triggerAtMillis = triggerAtMillis;
    }

    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }
}
