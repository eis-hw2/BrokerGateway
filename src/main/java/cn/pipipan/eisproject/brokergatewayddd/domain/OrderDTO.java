package cn.pipipan.eisproject.brokergatewayddd.domain;

public interface OrderDTO {
    public void setId(String id);
    public void setCreationTime(String time);
    public void setTraderName(String traderName);
    public String getTraderName();
    public String getId();
    public String getMarketDepthId();
}
