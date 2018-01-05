package com.zhy.lierdafridge.bean;

import java.util.List;

/**
 * Created by Administrator on 2018/1/5 0005.
 */

public class ZigbeeSBean {
    private String sourceId;
    private int serialNum;
    private String requestType;
    private List<String> id;
    private ZigbeeSBean.AttributesBean attributes;

    public List<String> getId() {
        return id;
    }

    public void setId(List<String> id) {
        this.id = id;
    }

    public String getSourceId() {
        return sourceId;
    }

    public void setSourceId(String sourceId) {
        this.sourceId = sourceId;
    }

    public int getSerialNum() {
        return serialNum;
    }

    public void setSerialNum(int serialNum) {
        this.serialNum = serialNum;
    }

    public String getRequestType() {
        return requestType;
    }

    public void setRequestType(String requestType) {
        this.requestType = requestType;
    }

    public ZigbeeSBean.AttributesBean getAttributes() {
        return attributes;
    }

    public void setAttributes(ZigbeeSBean.AttributesBean attributes) {
        this.attributes = attributes;
    }

    public static class AttributesBean {

        private String LEV;
        private String SWI;
        private String TYP;
        private String WIN;

        public String getWIN() {
            return WIN;
        }

        public void setWIN(String WIN) {
            this.WIN = WIN;
        }

        public String getTYP() {
            return TYP;
        }

        public void setTYP(String TYP) {
            this.TYP = TYP;
        }

        public String getLEV() {
            return LEV;
        }

        public void setLEV(String LEV) {
            this.LEV = LEV;
        }

        public String getSWI() {
            return SWI;
        }

        public void setSWI(String SWI) {
            this.SWI = SWI;
        }
    }
}
