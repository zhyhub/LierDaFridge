package com.zhy.lierdafridge.bean;

/**
 * Created by Administrator on 2017/12/15 0015.
 */

public class ZigbeeBean {

    private String sourceId;
    private int serialNum;
    private String requestType;
    private String id;
    private AttributesBean attributes;

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

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public AttributesBean getAttributes() {
        return attributes;
    }

    public void setAttributes(AttributesBean attributes) {
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
